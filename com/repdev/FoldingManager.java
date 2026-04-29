/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 *  http://repdev.org/ <support@repdev.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.repdev;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.function.IntUnaryOperator;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;

import com.repdev.parser.HiddenTextProvider;
import com.repdev.parser.RepgenParser;
import com.repdev.parser.Token;

/**
 * Code folding for the RepDev editor. Foldable regions come from the RepGen
 * parser's existing block matcher: every head/end pair (DEFINE..END, SETUP..END,
 * DO..END, PROCEDURE..END, HEADERS..END, bracket comments [...], etc.) that
 * spans more than one line can be folded.
 *
 * Folding is implemented by physically removing the hidden lines from the
 * StyledText buffer and caching them in-memory. On save, getUnfoldedText()
 * reassembles the full source.
 */
public class FoldingManager implements HiddenTextProvider {

	/** Width in pixels of the gutter column that holds the fold triangle. */
	public static final int FOLD_COLUMN_WIDTH = 18;

	/** A currently-collapsed region. */
	public static class FoldRegion {
		public final int headerLine;
		public final String hiddenText;
		/**
		 * The batch-t=0 headerLine for this fold — preserved through shifts.
		 * During a fold-all batch, {@link #headerLine} drifts as siblings
		 * collapse above this entry, but originalHeaderLine stays anchored so
		 * nested-check math stays in a consistent coord system.
		 * Outside a batch this equals {@link #headerLine}.
		 */
		public final int originalHeaderLine;

		FoldRegion(int headerLine, String hiddenText) {
			this(headerLine, hiddenText, headerLine);
		}

		FoldRegion(int headerLine, String hiddenText, int originalHeaderLine) {
			this.headerLine = headerLine;
			this.hiddenText = hiddenText;
			this.originalHeaderLine = originalHeaderLine;
		}
	}

	/** A precomputed head/end pair in the current buffer that can be collapsed. */
	public static class FoldableRange {
		public final int headerLine;
		public final int endLine;
		public final int endTokenOffset;
		public final boolean bracket;

		FoldableRange(int headerLine, int endLine, int endTokenOffset, boolean bracket) {
			this.headerLine = headerLine;
			this.endLine = endLine;
			this.endTokenOffset = endTokenOffset;
			this.bracket = bracket;
		}
	}

	private final StyledText txt;
	private final RepgenParser parser;
	// F3 step 1: narrowed from EditorComposite to EditorFoldHost so the fold
	// engine can be exercised against a fake host in tests, without standing
	// up a full SWT editor.
	private final EditorFoldHost editor;

	private final ArrayList<FoldRegion> folded = new ArrayList<FoldRegion>();
	private final ArrayList<FoldableRange> foldable = new ArrayList<FoldableRange>();

	private final Color markerColor;
	private boolean inFoldOp = false;
	// True while a fold-all is iterating. Individual collapse/expand ops suppress
	// their per-op parser.reparseAll() + recomputeRanges() (which triggers the
	// "Parsing vars for ..." pass and is O(N) per fold). One reparse runs at
	// the end of the batch, covering all collapses at once.
	private boolean batchMode = false;
	// Snapshot of hiddenText strings present in `folded` at batch start. Used
	// in collapseInternal to distinguish nested folds that existed before the
	// batch (user pre-folds — their lines are NOT reflected in the snapshot
	// range's endLine) from those the batch itself created (whose lines ARE in
	// the snapshot). Lets us delta-adjust the range without a mid-op reparse.
	private HashSet<String> preBatchHiddenTexts = null;

	public FoldingManager(EditorFoldHost editor, StyledText txt, RepgenParser parser) {
		this.editor = editor;
		this.txt = txt;
		this.parser = parser;
		this.markerColor = new Color(txt.getDisplay(), new RGB(90, 90, 90));
		install();
	}

	private void install() {
		txt.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				paintMarkers(e);
			}
		});

		txt.addMouseListener(new MouseAdapter() {
			public void mouseDown(MouseEvent e) {
				if (e.button != 1 || txt.getLineHeight() == 0) return;
				int gutter = editor.calcWidth();
				int foldColStart = gutter - FOLD_COLUMN_WIDTH;
				if (foldColStart < 0) foldColStart = 0;
				if (e.x < foldColStart || e.x > gutter) return;
				int line = txt.getTopIndex() + (e.y / txt.getLineHeight());
				if (line < 0 || line >= txt.getLineCount()) return;
				toggleAtLine(line);
			}
		});
	}

	public int getExtraGutterWidth() { return FOLD_COLUMN_WIDTH; }

	public boolean isInFoldOp() { return inFoldOp; }

	public boolean hasActiveFolds() { return !folded.isEmpty(); }

	/**
	 * Defensive snapshot of the currently-collapsed regions. Callers (e.g.
	 * Find/Replace, goto-definition) need to inspect hidden text without being
	 * able to mutate the manager's own list.
	 */
	public ArrayList<FoldRegion> getFoldedRegions() {
		return new ArrayList<FoldRegion>(folded);
	}

	/**
	 * Hidden text segments suitable for usage scans (e.g. unused-variable
	 * detection). Skips folds whose body would yield false positives:
	 * <ul>
	 *   <li>{@code DEFINE...END} bodies — appearances there are declarations,
	 *       not usages, so counting them as references would mask a genuinely
	 *       unused variable.</li>
	 *   <li>Bracket comment bodies ({@code [...]}) — comments don't count as
	 *       usages either, matching the live parser's {@code tok.getCDepth() > 0}
	 *       filter on visible tokens.</li>
	 * </ul>
	 */
	public Iterable<String> getUsageSearchableHiddenText() {
		ArrayList<String> result = new ArrayList<String>();
		for (int i = 0; i < folded.size(); i++) {
			FoldRegion fr = folded.get(i);
			String header = headerLineText(fr.headerLine);
			if (header == null) continue;
			String trimmed = header.trim();
			String lower = trimmed.toLowerCase();
			// Skip folded DEFINE blocks: their hidden body is declarations.
			if (lower.equals("define")
					|| lower.startsWith("define ")
					|| lower.startsWith("define\t")) continue;
			// Skip bracket comment folds: header line opens with '['.
			if (trimmed.length() > 0 && trimmed.charAt(0) == '[') continue;
			result.add(fr.hiddenText);
		}
		return result;
	}

	private String headerLineText(int line) {
		if (line < 0 || line >= txt.getLineCount()) return null;
		try { return txt.getLine(line); }
		catch (IllegalArgumentException ex) { return null; }
	}

	public int getDisplayLineNumber(int visibleLine) {
		return displayLineNumberFor(visibleLine, folded);
	}

	public int getUnfoldedLineCount() {
		return unfoldedLineCount(txt.getLineCount(), folded);
	}

	/**
	 * Shift the headerLine of every fold whose header is strictly below the
	 * given edit line. Called after a user edit changes the newline count above
	 * folded regions, so they keep pointing at the right buffer lines.
	 */
	public void shiftFoldsBelow(int editLine, int delta) {
		if (delta == 0) return;
		for (int i = 0; i < folded.size(); i++) {
			FoldRegion fr = folded.get(i);
			if (fr.headerLine > editLine) {
				folded.set(i, new FoldRegion(fr.headerLine + delta, fr.hiddenText, fr.originalHeaderLine));
			}
		}
	}

	/**
	 * Recompute foldable ranges from the current token list. Must be called
	 * after the parser has caught up with the current buffer state.
	 */
	public void recomputeRanges() {
		foldable.clear();
		if (parser == null) return;
		ArrayList<Token> tokens = parser.getLtokens();
		if (tokens == null || tokens.isEmpty()) { redrawGutter(); return; }

		// Head tokens on folded header lines are orphans: their matching end
		// has been removed from the buffer, so pushing them onto the stack
		// would mispair outer regions. Collect those lines up front and skip.
		// Bracket folds also leave an orphan ']' in the buffer at headerLine+1
		// (the bracket fold preserves the closer so the comment highlighter
		// still sees it). Without skipping it too, the ']' pops whatever outer
		// head is on the stack — e.g. a PROCEDURE that contains the folded
		// bracket — and produces a bogus FoldableRange that ends at the ']'
		// instead of the real END. That misclick then strands the inner
		// folds (DO/END etc.) outside the new fold's hiddenText, leaving them
		// as ghost entries with headerLines past the visible buffer.
		HashSet<Integer> foldedHeaderLines = new HashSet<Integer>();
		HashSet<Integer> orphanCloserLines = new HashSet<Integer>();
		for (int i = 0; i < folded.size(); i++) {
			FoldRegion fr = folded.get(i);
			foldedHeaderLines.add(fr.headerLine);
			String header = headerLineText(fr.headerLine);
			if (header != null && header.trim().startsWith("[")) {
				orphanCloserLines.add(fr.headerLine + 1);
			}
		}

		int charCount = txt.getCharCount();
		IntUnaryOperator lineAtOffset = offset -> {
			if (offset < 0 || offset > charCount) return -1;
			try { return txt.getLineAtOffset(offset); }
			catch (IllegalArgumentException ex) { return -1; }
		};
		foldable.addAll(computeRangesFromTokens(tokens, charCount, lineAtOffset,
				foldedHeaderLines, orphanCloserLines));
		// Only the gutter triangles depend on `foldable`; the line text itself
		// hasn't changed here, so a full-canvas redraw would just thrash the
		// syntax highlighter for no visible effect. Scope to the gutter strip.
		redrawGutter();
	}

	/**
	 * Pure algorithmic core of range computation. Extracted from
	 * {@link #recomputeRanges()} so it can be unit-tested without SWT or a live
	 * parser.
	 *
	 * @param tokens           the full token list from the parser
	 * @param charCount        total character count of the buffer (used for bounds
	 *                         checks)
	 * @param lineAtOffset     maps a character offset to its line number; must
	 *                         return -1 for out-of-range offsets
	 * @param foldedHeaderLines lines that are the header of an already-collapsed
	 *                          fold — head tokens on these lines are skipped
	 * @param orphanCloserLines lines that hold an orphan {@code ]} left over from a
	 *                          collapsed bracket fold — such closers are skipped
	 * @return list of {@link FoldableRange} covering spans of at least 1 line
	 */
	static List<FoldableRange> computeRangesFromTokens(
			List<com.repdev.parser.Token> tokens,
			int charCount,
			IntUnaryOperator lineAtOffset,
			Set<Integer> foldedHeaderLines,
			Set<Integer> orphanCloserLines) {

		List<FoldableRange> result = new ArrayList<FoldableRange>();
		Stack<Integer> stack = new Stack<Integer>();

		for (int i = 0; i < tokens.size(); i++) {
			com.repdev.parser.Token t = tokens.get(i);
			String s = t.getStr();
			// Only consider block-level head/end pairs: skip string/date/paren openers
			// which produce noisy single-line folds.
			if (t.isRealHead() && !"\"".equals(s) && !"'".equals(s)
					&& !"(".equals(s) && !":(".equals(s)) {
				int tStart = t.getStart();
				if (tStart < 0 || tStart >= charCount) continue;
				int tLine = lineAtOffset.applyAsInt(tStart);
				if (tLine < 0) continue;
				if (foldedHeaderLines.contains(tLine)) continue; // orphan head inside a collapsed region
				stack.push(i);
			} else if (t.isRealEnd() && !")".equals(s) && !"\"".equals(s) && !"'".equals(s)) {
				if ("]".equals(s) && !orphanCloserLines.isEmpty()) {
					int tStart = t.getStart();
					if (tStart >= 0 && tStart < charCount) {
						int tLine = lineAtOffset.applyAsInt(tStart);
						if (tLine >= 0 && orphanCloserLines.contains(tLine)) continue;
					}
				}
				if (!stack.isEmpty()) {
					int headIdx = stack.pop();
					com.repdev.parser.Token head = tokens.get(headIdx);
					int hStart = head.getStart();
					int eStart = t.getStart();
					if (hStart < 0 || eStart < 0 || hStart >= charCount || eStart > charCount) continue;
					int hl = lineAtOffset.applyAsInt(hStart);
					int el = lineAtOffset.applyAsInt(eStart);
					if (hl < 0 || el < 0) continue;
					boolean isBracket = "[".equals(head.getStr());
					if (el - hl >= 1) result.add(new FoldableRange(hl, el, eStart, isBracket));
				}
			}
		}
		return result;
	}

	/** Invalidate just the gutter strip (where fold triangles live). */
	private void redrawGutter() {
		if (txt == null || txt.isDisposed()) return;
		int gw = editor.calcWidth();
		if (gw <= 0) return;
		Rectangle ca = txt.getClientArea();
		if (ca.height <= 0) return;
		txt.redraw(0, 0, gw, ca.height, false);
	}

	public FoldableRange foldableAtLine(int line) {
		for (int i = 0; i < foldable.size(); i++) {
			FoldableRange r = foldable.get(i);
			if (r.headerLine == line) return r;
		}
		return null;
	}

	public FoldRegion foldedAtLine(int line) {
		for (int i = 0; i < folded.size(); i++) {
			FoldRegion r = folded.get(i);
			if (r.headerLine == line) return r;
		}
		return null;
	}

	public boolean isFoldableLine(int line) {
		return foldableAtLine(line) != null || foldedAtLine(line) != null;
	}

	/** Toggle the fold at the given line; returns true if it's now collapsed. */
	public boolean toggleAtLine(int line) {
		FoldRegion existing = foldedAtLine(line);
		if (existing != null) {
			expand(existing);
			return false;
		}
		FoldableRange fr = foldableAtLine(line);
		if (fr == null) return false;
		collapse(fr);
		return true;
	}

	/** Collapse the innermost foldable region that contains the caret. */
	public void collapseAtCaret() {
		int caretLine = txt.getLineAtOffset(txt.getCaretOffset());
		FoldableRange best = null;
		for (int i = 0; i < foldable.size(); i++) {
			FoldableRange r = foldable.get(i);
			if (r.headerLine <= caretLine && caretLine <= r.endLine) {
				if (best == null || r.headerLine > best.headerLine) best = r;
			}
		}
		if (best != null && foldedAtLine(best.headerLine) == null) collapse(best);
	}

	public void expandAtCaret() {
		int line = txt.getLineAtOffset(txt.getCaretOffset());
		FoldRegion fr = foldedAtLine(line);
		if (fr != null) expand(fr);
	}

	public void collapseAll() { collapseAllInternal(true); }

	/** Undo-replay entry point — collapses without pushing a new undo marker. */
	public void collapseAllSilent() { collapseAllInternal(false); }

	private void collapseAllInternal(boolean pushUndo) {
		// Fold from bottom up so earlier line numbers stay stable during iteration.
		ArrayList<FoldableRange> ranges = new ArrayList<FoldableRange>(foldable);
		Collections.sort(ranges, new Comparator<FoldableRange>() {
			public int compare(FoldableRange a, FoldableRange b) { return b.headerLine - a.headerLine; }
		});
		boolean prevBatch = batchMode;
		batchMode = true;
		HashSet<String> prevSnapshot = preBatchHiddenTexts;
		preBatchHiddenTexts = new HashSet<String>();
		for (int i = 0; i < folded.size(); i++) preBatchHiddenTexts.add(folded.get(i).hiddenText);
		// Anchor the t=0 coord system for this batch: every fold already in
		// `folded` gets originalHeaderLine = its current headerLine. Batch-
		// added folds will set originalHeaderLine from their snapshot range's
		// headerLine, which is also a t=0 coord. That keeps nested-check math
		// consistent regardless of shift accumulation.
		for (int i = 0; i < folded.size(); i++) {
			FoldRegion fr = folded.get(i);
			folded.set(i, new FoldRegion(fr.headerLine, fr.hiddenText, fr.headerLine));
		}
		boolean any = false;
		try {
			for (int i = 0; i < ranges.size(); i++) {
				FoldableRange r = ranges.get(i);
				FoldableRange fresh = foldableAtLine(r.headerLine);
				if (fresh != null && foldedAtLine(fresh.headerLine) == null) {
					collapseInternal(fresh, false);
					any = true;
				}
			}
		} finally {
			batchMode = prevBatch;
			preBatchHiddenTexts = prevSnapshot;
		}
		if (any) {
			// One reparse + foldable rebuild to leave the parser and range cache
			// consistent after suppressing per-op reparses during the batch.
			if (parser != null) parser.reparseAll();
			recomputeRanges();
			if (pushUndo) editor.pushFoldUndo(EditorComposite.FOLD_OP_COLLAPSE_ALL, -1);
		}
	}

	public void expandAll() { expandAllInternal(true); }

	/** Undo-replay entry point — expands without pushing a new undo marker. */
	public void expandAllSilent() { expandAllInternal(false); }

	private void expandAllInternal(boolean pushUndo) {
		if (folded.isEmpty()) return;
		String expanded = expandAllText(txt.getText(), folded);

		inFoldOp = true;
		try {
			if (parser != null) parser.setReparse(false);
			txt.setRedraw(false);
			txt.replaceTextRange(0, txt.getCharCount(), expanded);
		} finally {
			txt.setRedraw(true);
			if (parser != null) {
				parser.setReparse(true);
				parser.reparseAll();
			}
			inFoldOp = false;
		}

		folded.clear();
		recomputeRanges();
		if (pushUndo) editor.pushFoldUndo(EditorComposite.FOLD_OP_EXPAND_ALL, -1);
	}

	/** Undo-replay: collapse the range whose header is at {@code line}, without pushing undo. */
	public void collapseAtLineSilent(int line) {
		FoldableRange fr = foldableAtLine(line);
		if (fr != null && foldedAtLine(line) == null) collapseInternal(fr, false);
	}

	/** Undo-replay: expand the fold whose header is at {@code line}, without pushing undo. */
	public void expandAtLineSilent(int line) {
		FoldRegion fr = foldedAtLine(line);
		if (fr != null) expandInternal(fr, false);
	}

	private void collapse(FoldableRange range) { collapseInternal(range, true); }

	private void collapseInternal(FoldableRange range, boolean pushUndo) {
		// Expand any nested folds inside this range so the captured hidden text is complete.
		// In batch mode, prior iterations have shifted folded entries' *current*
		// headerLines upward as siblings collapsed above them, so a sibling below
		// this range can end up with a current headerLine inside the stale
		// range.endLine. Use originalHeaderLine — the batch-t=0 coord — so the
		// test stays consistent with the snapshot range's own t=0 bounds.
		ArrayList<FoldRegion> nested = new ArrayList<FoldRegion>();
		for (int i = 0; i < folded.size(); i++) {
			FoldRegion fr = folded.get(i);
			int probe = batchMode ? fr.originalHeaderLine : fr.headerLine;
			if (probe > range.headerLine && probe <= range.endLine) nested.add(fr);
		}
		Collections.sort(nested, new Comparator<FoldRegion>() {
			public int compare(FoldRegion a, FoldRegion b) { return b.headerLine - a.headerLine; }
		});

		// Count lines/chars of nested folds that existed BEFORE the batch
		// started (user pre-folds). The batch-collapse snapshot `range` already
		// reflects a buffer state where those lines were hidden, so restoring
		// them via expandInternal pushes range.endLine/endTokenOffset down by
		// exactly these totals. Batch-added nested folds do NOT count — the
		// snapshot pre-dates their collapse, so expanding them just returns
		// the buffer to the snapshot's state.
		int preBatchLines = 0;
		int preBatchChars = 0;
		if (batchMode && preBatchHiddenTexts != null) {
			for (int i = 0; i < nested.size(); i++) {
				FoldRegion nfr = nested.get(i);
				if (preBatchHiddenTexts.contains(nfr.hiddenText)) {
					preBatchLines += countNewlines(nfr.hiddenText);
					preBatchChars += nfr.hiddenText.length();
				}
			}
		}

		for (int i = 0; i < nested.size(); i++) expandInternal(nested.get(i), false);

		if (batchMode) {
			// Adjust the snapshot range in place — avoids the expensive
			// parser.reparseAll() + recomputeRanges() + foldableAtLine re-fetch
			// that non-batch mode relies on.
			if (preBatchLines > 0 || preBatchChars > 0) {
				range = new FoldableRange(
					range.headerLine,
					range.endLine + preBatchLines,
					range.endTokenOffset + preBatchChars,
					range.bracket);
			}
		} else {
			// Non-batch: each expandInternal already recomputed foldable, so
			// re-fetching returns the up-to-date range.
			FoldableRange fresh = foldableAtLine(range.headerLine);
			if (fresh == null) return;
			range = fresh;
		}

		int lineCount = txt.getLineCount();
		if (range.headerLine + 1 >= lineCount) return;
		int sliceStart = txt.getOffsetAtLine(range.headerLine + 1);
		int sliceEnd;
		if (range.bracket) {
			// Keep the ']' visible so the comment highlighter still sees the close.
			sliceEnd = range.endTokenOffset;
		} else if (range.endLine + 1 >= lineCount) {
			sliceEnd = txt.getCharCount();
		} else {
			sliceEnd = txt.getOffsetAtLine(range.endLine + 1);
		}
		if (sliceEnd <= sliceStart) return;

		String hidden = txt.getText(sliceStart, sliceEnd - 1);
		int linesBefore = txt.getLineCount();

		inFoldOp = true;
		try {
			if (parser != null) parser.setReparse(false);
			txt.setRedraw(false);
			txt.replaceTextRange(sliceStart, sliceEnd - sliceStart, "");
		} finally {
			txt.setRedraw(true);
			if (parser != null) {
				parser.setReparse(true);
				if (!batchMode) parser.reparseAll();
			}
			inFoldOp = false;
		}

		int removedLines = linesBefore - txt.getLineCount();
		// Fail-fast on mismatch: a divergence between captured-text newlines and
		// the buffer's actual line-count drop means every downstream shift is
		// off by that delta, silently corrupting fold positions and (after the
		// next save) the user's file. Rather than log-and-continue, roll the
		// edit back, log loudly, and abandon the collapse so the buffer is left
		// in its pre-collapse state and the user can retry.
		int hiddenNL = countNewlines(hidden);
		if (hiddenNL != removedLines) {
			System.err.println("FoldingManager.collapse: removedLines/hiddenNL mismatch — removed=" + removedLines
					+ " hiddenNL=" + hiddenNL + " header=" + range.headerLine + " end=" + range.endLine
					+ " bracket=" + range.bracket + "; rolling back collapse to avoid silent buffer corruption");
			inFoldOp = true;
			try {
				if (parser != null) parser.setReparse(false);
				txt.setRedraw(false);
				// Re-insert the captured text at the same offset to restore the
				// pre-collapse buffer; sliceStart is still valid because no
				// other edits have happened.
				txt.replaceTextRange(sliceStart, 0, hidden);
			} finally {
				txt.setRedraw(true);
				if (parser != null) {
					parser.setReparse(true);
					if (!batchMode) parser.reparseAll();
				}
				inFoldOp = false;
			}
			if (!batchMode) recomputeRanges();
			return;
		}
		// Compute the original last visible line that moved up by removedLines.
		// After collapse: the "]" (bracket case) or nothing extra stays at headerLine+1.
		int breakLine = range.bracket ? range.headerLine : range.endLine;
		for (int i = 0; i < folded.size(); i++) {
			FoldRegion fr = folded.get(i);
			if (fr.headerLine > breakLine) {
				folded.set(i, new FoldRegion(fr.headerLine - removedLines, fr.hiddenText, fr.originalHeaderLine));
			}
		}
		folded.add(new FoldRegion(range.headerLine, hidden));

		if (!batchMode) recomputeRanges();
		if (pushUndo) editor.pushFoldUndo(EditorComposite.FOLD_OP_COLLAPSE, range.headerLine);
	}

	private void expand(FoldRegion region) { expandInternal(region, true); }

	private void expandInternal(FoldRegion region, boolean pushUndo) {
		int origHeaderLine = region.headerLine;
		int lineCount = txt.getLineCount();
		int insertOffset = (region.headerLine + 1 >= lineCount)
				? txt.getCharCount()
				: txt.getOffsetAtLine(region.headerLine + 1);

		inFoldOp = true;
		try {
			if (parser != null) parser.setReparse(false);
			txt.setRedraw(false);
			txt.replaceTextRange(insertOffset, 0, region.hiddenText);
		} finally {
			txt.setRedraw(true);
			if (parser != null) {
				parser.setReparse(true);
				if (!batchMode) parser.reparseAll();
			}
			inFoldOp = false;
		}

		folded.remove(region);
		int addedLines = countNewlines(region.hiddenText);
		for (int i = 0; i < folded.size(); i++) {
			FoldRegion fr = folded.get(i);
			if (fr.headerLine > region.headerLine) {
				folded.set(i, new FoldRegion(fr.headerLine + addedLines, fr.hiddenText, fr.originalHeaderLine));
			}
		}

		if (!batchMode) recomputeRanges();
		if (pushUndo) editor.pushFoldUndo(EditorComposite.FOLD_OP_EXPAND, origHeaderLine);
	}

	/**
	 * Reassembles the complete source by re-inserting all hidden regions. Used
	 * at save time so folded files persist correctly.
	 */
	public String getUnfoldedText() {
		if (folded.isEmpty()) return txt.getText();
		return expandAllText(txt.getText(), folded);
	}

	static String expandAllText(String visibleText, ArrayList<FoldRegion> foldedRegions) {
		ArrayList<FoldRegion> sorted = new ArrayList<FoldRegion>(foldedRegions);
		// Insert from bottom up so earlier insert offsets stay valid.
		Collections.sort(sorted, new Comparator<FoldRegion>() {
			public int compare(FoldRegion a, FoldRegion b) { return b.headerLine - a.headerLine; }
		});
		StringBuilder sb = new StringBuilder(visibleText);
		for (int i = 0; i < sorted.size(); i++) {
			FoldRegion fr = sorted.get(i);
			int offset = offsetAtLineStart(sb, fr.headerLine + 1);
			sb.insert(offset, fr.hiddenText);
		}
		return sb.toString();
	}

	private static int offsetAtLineStart(CharSequence cs, int line) {
		if (line <= 0) return 0;
		int found = 0;
		for (int i = 0; i < cs.length(); i++) {
			if (cs.charAt(i) == '\n') {
				found++;
				if (found == line) return i + 1;
			}
		}
		// Diagnostic: reaching here means a fold's stored headerLine exceeds the
		// visible text's line count. Appending at EOF matches the historical
		// "sections ended up at the bottom of the file, out of order" symptom.
		// Log so upstream shift bugs (in shiftFoldsBelow / collapse / modifyText)
		// surface instead of silently corrupting the file.
		System.err.println("FoldingManager.offsetAtLineStart: requested line " + line
				+ " but visible text only has " + found + " newline(s); appending at EOF");
		return cs.length();
	}

	private static int countNewlines(String s) {
		int n = 0;
		for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
		return n;
	}

	static int displayLineNumberFor(int visibleLine, ArrayList<FoldRegion> foldedRegions) {
		int line = visibleLine + 1;
		for (int i = 0; i < foldedRegions.size(); i++) {
			FoldRegion fr = foldedRegions.get(i);
			if (fr.headerLine < visibleLine) line += countNewlines(fr.hiddenText);
		}
		return line;
	}

	static int unfoldedLineCount(int visibleLineCount, ArrayList<FoldRegion> foldedRegions) {
		int lineCount = visibleLineCount;
		for (int i = 0; i < foldedRegions.size(); i++) {
			lineCount += countNewlines(foldedRegions.get(i).hiddenText);
		}
		return lineCount;
	}

	// Re-used polygon buffers for the fold-triangle markers. Allocating int[6]
	// per visible foldable line per paint was a measurable allocation pressure
	// point on large files; six ints overwritten in place is free.
	private final int[] markerPolyFolded = new int[6];
	private final int[] markerPolyExpanded = new int[6];

	private void paintMarkers(PaintEvent e) {
		if (txt.isDisposed() || txt.getLineHeight() == 0) return;
		int lh = txt.getLineHeight();
		int topLine = txt.getTopIndex();
		int clientH = txt.getClientArea().height;
		int maxLine = topLine + (clientH / lh) + 2;
		if (maxLine > txt.getLineCount()) maxLine = txt.getLineCount();

		int gutterW = editor.calcWidth();
		int cx = gutterW - (FOLD_COLUMN_WIDTH / 2) - 1;

		// O(R) one-shot index build replaces the prior O(R) linear scan per
		// visible line. With ~100 visible lines and ~50 ranges, that's a ~50x
		// reduction in per-paint comparisons. Keys are header-line numbers.
		java.util.HashSet<Integer> foldedHeaderLines = new java.util.HashSet<Integer>(folded.size() * 2 + 1);
		for (int i = 0; i < folded.size(); i++) foldedHeaderLines.add(folded.get(i).headerLine);
		java.util.HashSet<Integer> foldableHeaderLines = new java.util.HashSet<Integer>(foldable.size() * 2 + 1);
		for (int i = 0; i < foldable.size(); i++) foldableHeaderLines.add(foldable.get(i).headerLine);

		GC gc = e.gc;
		Color oldFg = gc.getForeground();
		Color oldBg = gc.getBackground();
		gc.setForeground(markerColor);
		gc.setBackground(markerColor);

		for (int line = topLine; line < maxLine; line++) {
			boolean isFolded = foldedHeaderLines.contains(line);
			boolean isFoldable = isFolded || foldableHeaderLines.contains(line);
			if (!isFoldable) continue;
			int y;
			try {
				y = txt.getLocationAtOffset(txt.getOffsetAtLine(line)).y;
			} catch (IllegalArgumentException ex) { continue; }
			int cy = y + (lh / 2);
			int s = 6;
			if (isFolded) {
				// Right-pointing triangle (region is collapsed).
				markerPolyFolded[0] = cx - (s - 2); markerPolyFolded[1] = cy - s;
				markerPolyFolded[2] = cx + (s - 1); markerPolyFolded[3] = cy;
				markerPolyFolded[4] = cx - (s - 2); markerPolyFolded[5] = cy + s;
				gc.fillPolygon(markerPolyFolded);
			} else {
				// Down-pointing triangle (region is expanded, click to collapse).
				markerPolyExpanded[0] = cx - s;   markerPolyExpanded[1] = cy - (s - 3);
				markerPolyExpanded[2] = cx + s;   markerPolyExpanded[3] = cy - (s - 3);
				markerPolyExpanded[4] = cx;       markerPolyExpanded[5] = cy + (s - 1);
				gc.fillPolygon(markerPolyExpanded);
			}
		}
		gc.setForeground(oldFg);
		gc.setBackground(oldBg);
	}

	public void dispose() {
		if (markerColor != null && !markerColor.isDisposed()) markerColor.dispose();
	}
}
