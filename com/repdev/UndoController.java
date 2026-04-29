/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev;

import java.util.Stack;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import com.repdev.parser.RepgenParser;

/**
 * Owns the editor's undo/redo state and replay logic. Extracted from
 * {@code EditorComposite} as F3 step 2.
 *
 * <p>Two stacks of {@link TextChange} entries record both regular text edits
 * and fold/unfold operations. Each "user-visible action" is bracketed by
 * commit markers so the undo replay loop pops back through to the previous
 * commit and stops. Ctrl+Z reverses one bracket; Ctrl+Y replays it forward.</p>
 *
 * <p>Three operating modes:
 * <ul>
 *   <li>{@link #MODE_INACTIVE} — no recording (during init, dialog replay,
 *       or other transient buffer mutations that must not pollute history)</li>
 *   <li>{@link #MODE_RECORDING} — normal user editing; modify-text events
 *       push to {@code undos}</li>
 *   <li>{@link #MODE_REPLAYING_AS_REDO} — set during {@link #undo()}: the
 *       buffer mutations that result from the replay get pushed to
 *       {@code redos} so they can be redone</li>
 * </ul></p>
 *
 * <p>Cross-cutting dependencies are passed via setters because they are
 * resolved lazily relative to the editor's bring-up order: the parser is
 * created after the UndoController, and the folding manager later still.</p>
 */
public final class UndoController {

	public static final int MODE_INACTIVE = 0;
	public static final int MODE_RECORDING = 1;
	public static final int MODE_REPLAYING_AS_REDO = 2;

	public static final int FOLD_OP_NONE = 0;
	public static final int FOLD_OP_COLLAPSE = 1;       // single range collapsed at foldLine
	public static final int FOLD_OP_EXPAND = 2;         // single region expanded at foldLine
	public static final int FOLD_OP_COLLAPSE_ALL = 3;   // fold-all (foldLine unused)
	public static final int FOLD_OP_EXPAND_ALL = 4;     // unfold-all (foldLine unused)

	private static final int UNDO_LIMIT = 1000;

	private final Stack<TextChange> undos = new Stack<TextChange>();
	private final Stack<TextChange> redos = new Stack<TextChange>();
	private int mode = MODE_INACTIVE;

	private final StyledText txt;
	private RepgenParser parser;
	private FoldingManager folding;
	private Runnable afterReplay;
	private Shell errorShell;

	public UndoController(StyledText txt) {
		this.txt = txt;
	}

	public void setParser(RepgenParser parser) { this.parser = parser; }
	public void setFoldingManager(FoldingManager folding) { this.folding = folding; }

	/** Optional callback invoked after a successful undo or redo replay (e.g. lineHighlight). */
	public void setAfterReplay(Runnable r) { this.afterReplay = r; }

	/** Shell used to host the error MessageBox if replay throws. */
	public void setErrorShell(Shell s) { this.errorShell = s; }

	public int getMode() { return mode; }
	public void setMode(int m) { this.mode = m; }
	public void activate() { this.mode = MODE_RECORDING; }

	public boolean hasUndo() { return undos.size() > 0; }
	public boolean hasRedo() { return redos.size() > 0; }

	/**
	 * Push a regular text edit onto the active stack ({@code undos} when
	 * recording, {@code redos} during undo replay). No-op while
	 * {@link #MODE_INACTIVE}.
	 *
	 * <p>The caller decides whether the edit is undoable; in particular
	 * fold-op edits are filtered out at the call site so they don't pollute
	 * the regular edit history (they have their own {@link #pushFoldUndo}
	 * entries).</p>
	 */
	public void recordTextChange(int start, int length, String replacedText, int topIndex) {
		if (mode == MODE_INACTIVE) return;
		Stack<TextChange> stack = (mode == MODE_RECORDING) ? undos : redos;
		stack.push(new TextChange(start, length, replacedText, topIndex));
		if (stack.size() > UNDO_LIMIT) stack.remove(0);
	}

	/** Insert a commit boundary so the next undo replay stops here. */
	public void commitUndo() {
		if (undos.size() == 0 || !undos.peek().isCommit())
			undos.add(new TextChange(true));
	}

	/**
	 * Record a fold/unfold as its own undoable step. Bracketed with commit
	 * markers so it's an atomic undo unit, independent of any text edits
	 * before or after.
	 */
	public void pushFoldUndo(int op, int line) {
		if (mode != MODE_RECORDING) return;
		commitUndo();
		undos.push(new TextChange(op, line));
		undos.push(new TextChange(true));
		if (undos.size() > UNDO_LIMIT) undos.remove(0);
	}

	public void undo() {
		try {
			TextChange change;
			if (!undos.empty()) {
				if (undos.peek().isCommit()) undos.pop();

				mode = MODE_REPLAYING_AS_REDO;

				txt.setRedraw(false);
				if (parser != null) parser.setReparse(false);

				while (!(undos.size() == 0 || (change = undos.pop()).isCommit())) {
					if (change.isFoldOp()) {
						applyFoldUndo(change);
						redos.push(change);
					} else {
						txt.replaceTextRange(change.getStart(), change.getLength(), change.getReplacedText());
						txt.setCaretOffset(change.getStart());
						txt.setTopIndex(change.getTopIndex());
					}
				}

				redos.push(new TextChange(true));
			}
		} catch (Exception e) {
			showReplayError("The Undo Manager has failed during an Undo!");
			e.printStackTrace();
		} finally {
			mode = MODE_RECORDING;
			txt.setRedraw(true);
			if (parser != null) {
				parser.setReparse(true);
				parser.reparseAll();
			}
			if (afterReplay != null) afterReplay.run();
		}
	}

	public void redo() {
		try {
			TextChange change;
			if (!redos.empty()) {
				if (redos.peek().isCommit()) redos.pop();

				mode = MODE_RECORDING;
				txt.setRedraw(false);
				if (parser != null) parser.setReparse(false);

				while (!(redos.size() == 0 || (change = redos.pop()).isCommit())) {
					if (change.isFoldOp()) {
						applyFoldRedo(change);
						undos.push(change);
					} else {
						txt.replaceTextRange(change.getStart(), change.getLength(), change.getReplacedText());
						txt.setCaretOffset(change.getStart());
						txt.setTopIndex(change.getTopIndex());
					}
				}
				undos.push(new TextChange(true));
			}
		} catch (Exception e) {
			showReplayError("The Undo Manager has failed during a Redo!");
			e.printStackTrace();
		} finally {
			mode = MODE_RECORDING;
			txt.setRedraw(true);
			if (parser != null) {
				parser.setReparse(true);
				parser.reparseAll();
			}
			if (afterReplay != null) afterReplay.run();
		}
	}

	private void applyFoldUndo(TextChange change) {
		if (folding == null) return;
		switch (change.getFoldOp()) {
			case FOLD_OP_COLLAPSE:     folding.expandAtLineSilent(change.getFoldLine()); break;
			case FOLD_OP_EXPAND:       folding.collapseAtLineSilent(change.getFoldLine()); break;
			case FOLD_OP_COLLAPSE_ALL: folding.expandAllSilent(); break;
			case FOLD_OP_EXPAND_ALL:   folding.collapseAllSilent(); break;
		}
	}

	private void applyFoldRedo(TextChange change) {
		if (folding == null) return;
		switch (change.getFoldOp()) {
			case FOLD_OP_COLLAPSE:     folding.collapseAtLineSilent(change.getFoldLine()); break;
			case FOLD_OP_EXPAND:       folding.expandAtLineSilent(change.getFoldLine()); break;
			case FOLD_OP_COLLAPSE_ALL: folding.collapseAllSilent(); break;
			case FOLD_OP_EXPAND_ALL:   folding.expandAllSilent(); break;
		}
	}

	private void showReplayError(String msg) {
		if (errorShell == null || errorShell.isDisposed()) return;
		MessageBox dialog = new MessageBox(errorShell, SWT.ICON_ERROR | SWT.OK);
		dialog.setMessage(msg);
		dialog.setText("ERROR!");
		dialog.open();
	}

	/** A single recorded edit or fold operation. Package-private — only the controller manipulates these. */
	static final class TextChange {
		private int start, length, topIndex;
		private String replacedText;
		private boolean commit;
		private int foldOp = FOLD_OP_NONE;
		private int foldLine;

		TextChange(boolean commit) {
			this.commit = commit;
		}

		TextChange(int start, int length, String replacedText, int topIndex) {
			this.start = start;
			this.length = length;
			this.replacedText = replacedText;
			this.topIndex = topIndex;
			this.commit = false;
		}

		TextChange(int foldOp, int foldLine) {
			this.foldOp = foldOp;
			this.foldLine = foldLine;
			this.commit = false;
		}

		int getTopIndex() { return topIndex; }
		boolean isCommit() { return commit; }
		int getStart() { return start; }
		int getLength() { return length; }
		String getReplacedText() { return replacedText; }
		boolean isFoldOp() { return foldOp != FOLD_OP_NONE; }
		int getFoldOp() { return foldOp; }
		int getFoldLine() { return foldLine; }
	}
}
