/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;

import com.repdev.parser.RepgenParser;

/**
 * Indentation utilities extracted from {@code EditorComposite} (F3 step 4).
 * Owns the tab-string formatting, the multi-line group indent/dedent
 * operation, and the leading-whitespace probe used by auto-indent-on-newline.
 *
 * <p>All entry points are static — the class holds no state. Callers pass
 * the {@link StyledText} and {@link RepgenParser} as explicit arguments so
 * the logic is reachable from anywhere that has those handles, not just
 * from inside the editor composite.</p>
 */
public final class Indenter {

	private Indenter() {}

	/**
	 * Tab string per current Config: a tab character if hard tabs are
	 * configured (or tab size is non-positive), otherwise N spaces where
	 * N = {@code Config.getTabSize()}.
	 */
	public static String getTabStr() {
		int size = Config.getTabSize();
		if (size <= 0 || !Config.getSpacesForTabs()) return "\t";
		StringBuilder sb = new StringBuilder(size);
		for (int i = 0; i < size; i++) sb.append(' ');
		return sb.toString();
	}

	/**
	 * Return the leading whitespace prefix of {@code line} — every char up
	 * to the first non-space, non-tab. Used so a newline inherits the
	 * indentation of the line it came from.
	 */
	public static String leadingWhitespace(String line) {
		if (line == null || line.isEmpty()) return "";
		int end = 0;
		while (end < line.length()) {
			char c = line.charAt(end);
			if (c != ' ' && c != '\t') break;
			end++;
		}
		return (end == 0) ? "" : line.substring(0, end);
	}

	/**
	 * Indent ({@code direction>0}) or dedent ({@code direction<0}) every
	 * line in [{@code startLine}, {@code endLine}] using the current tab
	 * string. Suspends parser reparsing for the duration so a multi-line
	 * Tab/Shift-Tab burst doesn't trigger one reparse per line.
	 *
	 * @param txt        the editor's StyledText (selection is preserved
	 *                   across the operation, shifted by the indent delta)
	 * @param parser     the parser to suspend/resume; may be {@code null}
	 * @param direction  +1 to indent, -1 to dedent
	 * @param startLine  first line index, inclusive
	 * @param endLine    last line index, inclusive (clamped to line count)
	 */
	public static void groupIndent(StyledText txt, RepgenParser parser, int direction, int startLine, int endLine) {
		String tabStr = getTabStr();

		if (endLine > txt.getLineCount() - 1)
			endLine = Math.max(txt.getLineCount() - 1, startLine + 1);

		try {
			Point oldSelection = txt.getSelection();
			int offset = 0;

			if (parser != null)
				parser.setReparse(false);

			txt.setRedraw(false);
			for (int i = startLine; i <= endLine; i++) {
				int startOffset = txt.getOffsetAtLine(i);
				int endOffset;
				String line;

				if (i >= txt.getLineCount() - 1) {
					endOffset = txt.getCharCount();
				} else {
					endOffset = txt.getOffsetAtLine(i + 1);
				}

				if (endOffset - 1 <= startOffset)
					line = "\n";
				else
					line = txt.getText(startOffset, endOffset - 1);
				int spaces = tabStr.length();

				if (direction > 0) {
					txt.replaceTextRange(startOffset, endOffset - startOffset, tabStr + line);
				} else {
					for (int x = 0; x < Math.min(tabStr.length(), line.length()); x++) {
						if (line.charAt(x) > 32) {
							spaces = x;
							break;
						}
					}
					txt.replaceTextRange(startOffset, endOffset - startOffset, line.substring(Math.min(spaces, line.length())));
				}

				offset += spaces * direction;
			}

			if (parser != null)
				parser.setReparse(true);

			oldSelection.y += offset;
			oldSelection.x = Math.max(oldSelection.x + tabStr.length() * direction, txt.getOffsetAtLine(startLine));

			// TODO: This fails if you are right inbetween a /r and /n, better fix it ;)
			txt.setSelection(oldSelection);

		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			txt.setRedraw(true);
			if (parser != null) {
				parser.setReparse(true);
				parser.reparseAll();
			}
		}
	}
}
