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
import java.util.Arrays;
import java.util.Stack;

import org.eclipse.swt.custom.ExtendedModifyEvent;
import org.eclipse.swt.custom.ExtendedModifyListener;
import org.eclipse.swt.custom.LineBackgroundEvent;
import org.eclipse.swt.custom.LineBackgroundListener;
import org.eclipse.swt.custom.LineStyleEvent;
import org.eclipse.swt.custom.LineStyleListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

import com.repdev.parser.FunctionLayout;
import com.repdev.parser.RepgenParser;
import com.repdev.parser.Token;
import com.repdev.parser.Token.SpecialBackgroundReason;
import com.repdev.theme.ThemeResources;
import com.repdev.theme.ThemeService;


/**
 * Adds the right listeners to a StyledText object to colorize repgens. Uses
 * RepgenParser for the tokenization. Theme state (colors/fonts/styles) is
 * sourced from {@link ThemeService} — this class holds no theme state of its
 * own, so switching themes at runtime is just a matter of reapplying the
 * StyledText foreground/background/font and triggering a redraw.
 *
 * @author Jake Poznanski
 */
public class SyntaxHighlighter implements ExtendedModifyListener, LineStyleListener, LineBackgroundListener {

	private RepgenParser parser;
	private StyledText txt;
	private SymitarFile file;
	private int sym;

	// Custom line background, used by the compare composite interface (not themed)
	private int[] customLines = null;
	private Color compareLineColor;


	public SyntaxHighlighter(RepgenParser parser) {
		this.parser = parser;
		this.txt = parser.getTxt();
		this.file = parser.getFile();
		this.sym = parser.getSym();

		applySurfaceColors();
		if (parser.getSym() == Config.getLiveSym() && !this.file.isLocal())
			txt.setBackground(new Color(Display.getCurrent(), getRGB(Config.getLiveSymColor())));
		txt.addExtendedModifyListener(this);
		txt.addLineStyleListener(this);

		if (current().editorFont != null)
			txt.setFont(current().editorFont);
	}

	/**
	 * Compare-view constructor: attaches a per-instance custom line-background
	 * color (for diff highlighting) on top of the normal theme.
	 */
	public SyntaxHighlighter(RepgenParser parser, Color customLineColor, int[] customLines) {
		this.parser = parser;
		this.txt = parser.getTxt();
		this.file = parser.getFile();
		this.sym = parser.getSym();

		this.compareLineColor = customLineColor;
		this.customLines = customLines;

		applySurfaceColors();
		txt.addExtendedModifyListener(this);
		txt.addLineBackgroundListener(this);
		txt.addLineStyleListener(this);
		if (current().editorFont != null)
			txt.setFont(current().editorFont);
	}

	private void applySurfaceColors() {
		ThemeResources r = current();
		txt.setForeground(r.editorForeground);
		txt.setBackground(r.editorBackground);

		// Item 6: Selection colors
		if (r.editorSelectionBackground != null) txt.setSelectionBackground(r.editorSelectionBackground);
		if (r.editorSelectionForeground != null) txt.setSelectionForeground(r.editorSelectionForeground);
	}

	public void refreshThemeResources() {
		applySurfaceColors();
		ThemeResources r = current();
		if (r.editorFont != null && !r.editorFont.isDisposed()) {
			txt.setFont(r.editorFont);
		}
	}

	private ThemeResources current() {
		return ThemeService.getInstance().getCurrent();
	}

	public void highlight() {
		txt.removeExtendedModifyListener(this);
		txt.removeLineBackgroundListener(this);
		txt.removeLineStyleListener(this);
	}

	/**
	 * Legacy entry point. Delegates to {@link ThemeService#applyTheme(String)}
	 * so any caller that still uses this path transparently participates in the
	 * new runtime-apply pipeline.
	 */
	public static void loadStyle(String styleName) {
		System.out.println("Loading theme " + styleName + ".xml");
		ThemeService.getInstance().applyTheme(styleName);
	}

	/** Current "line" (current-line highlight) color from the active theme. */
	public static Color getLineColor() {
		ThemeResources r = ThemeService.getInstance().getCurrent();
		return r != null ? r.lineHighlight : null;
	}

	/** Current "token" (block-match background) color from the active theme. */
	public static Color getBlockMatchColor() {
		ThemeResources r = ThemeService.getInstance().getCurrent();
		return r != null ? r.blockMatch : null;
	}

	public Color getBulletColor() {
		ThemeResources r = current();
		return r != null ? r.bulletColor : null;
	}

	public void modifyText(ExtendedModifyEvent e) {
		parser.textModified(e.start, e.length, e.replacedText);
	}

	public StyleRange getStyle(Token tok) {
		ThemeResources r = current();
		boolean isVar = false;
		StyleRange range = null;

		if (tok.getCDepth() != 0) {
			range = r.comments.getRange(tok.getStart(), tok.length());
			for (String taskType : RepgenParser.taskTokens)
				if (tok.getStr().equals(taskType) && (tok.getAfter() != null && tok.getAfter().getStr().equals(":")))
					range = r.task.getRange(tok.getStart(), tok.length());

		} else if (tok.inString())
			range = r.typeChar.getRange(tok.getStart(), tok.length());
		else if (tok.inDate())
			range = r.typeDate.getRange(tok.getStart(), tok.length());
		// Token is a Record before the colon
		else if (tok.getAfter() != null && tok.getAfter().getStr().equals(":")) {
			if (tok.dbRecordValid())
				range = r.struct1.getRange(tok.getStart(), tok.length());
			else
				range = r.struct1Invalid.getRange(tok.getStart(), tok.length());
		// Token is a Field (or Field with no sub-field if next is :()
		} else if (tok.getBefore() != null && tok.getBefore().getStr().equals(":")) {
			if (tok.dbFieldValid(RepgenParser.getDb().getTreeRecords()) || (tok.dbFieldValidNoSubFld(RepgenParser.getDb().getTreeRecords())))
				range = r.struct2.getRange(tok.getStart(), tok.length());
			else
				range = r.struct2Invalid.getRange(tok.getStart(), tok.length());
		} else if (FunctionLayout.getInstance().containsName(tok.getStr()) && tok.getAfter() != null && tok.getAfter().getStr().equals("("))
			range = r.functions.getRange(tok.getStart(), tok.length());
		else if (RepgenParser.getKeywords().contains(tok.getStr()))
			range = r.keywords.getRange(tok.getStart(), tok.length());
		else if (RepgenParser.getSpecialvars().contains(tok.getStr()))
			range = r.variables.getRange(tok.getStart(), tok.length());
		// O(1) name lookup via parser's cached HashSet view of lvars — replaces
		// the prior O(V) ArrayList scan that ran per token per paint and was
		// the dominant cost on large files with many local variables.
		if (parser.getLvarNames().contains(tok.getStr()))
			isVar = true;

		if (range == null && isVar)
			range = r.variables.getRange(tok.getStart(), tok.length());
		else if (range == null) {
			range = r.normal.getRange(tok.getStart(), tok.length());
		}

		if (tok.getSpecialBackground() != null)
			range.background = tok.getSpecialBackground();

		return range;
	}

	private Color rainbowColor(int depth) {
		Color[] palette = current().rainbowPalette;
		if (palette == null || palette.length == 0) return null;
		int n = palette.length;
		return palette[((depth % n) + n) % n];
	}

	// Walk the token stream once and record the rainbow depth for every
	// (, ), [, ], do, and DO-closing end. Non-rainbow tokens get -1. We only
	// run this when the feature is on, so compute cost is zero otherwise.
	private static int[] computeRainbowDepths(ArrayList<Token> ltokens) {
		int[] depths = new int[ltokens.size()];
		Arrays.fill(depths, -1);

		int parenDepth = 0;
		Stack<String> blockStack = new Stack<String>();

		for (int i = 0; i < ltokens.size(); i++) {
			Token t = ltokens.get(i);
			String s = t.getStr();

			// Comment brackets: the tokenizer already tracks nesting in cDepth,
			// so depth-1 gives us 0-based rainbow depth.
			if ((s.equals("[") || s.equals("]")) && !t.inString() && !t.inDate()) {
				depths[i] = Math.max(0, t.getCDepth() - 1);
				continue;
			}

			// Skip anything not in the normal expression context — strings,
			// dates, and tokens inside comment brackets have their own colors.
			if (t.getCDepth() != 0 || t.inString() || t.inDate()) continue;

			if (s.equals("(")) {
				depths[i] = parenDepth;
				parenDepth++;
			} else if (s.equals(":(")) {
				// :(FIELD) is a sub-field accessor — count it for depth so the
				// closing ) lines up, but don't paint :( itself.
				parenDepth++;
			} else if (s.equals(")")) {
				parenDepth = Math.max(0, parenDepth - 1);
				depths[i] = parenDepth;
			} else if (s.equals("do") && t.isRealHead()) {
				depths[i] = blockStack.size();
				blockStack.push("do");
			} else if (t.isRealHead()) {
				// procedure / setup / select / define / headers / total /
				// print title / sort — push so end pops match up, but don't
				// color them.
				blockStack.push(s);
			} else if (s.equals("end") && t.isRealEnd()) {
				if (!blockStack.isEmpty()) {
					String popped = blockStack.pop();
					if (popped.equals("do"))
						depths[i] = blockStack.size();
				}
			}
		}
		return depths;
	}

	public void lineGetStyle(LineStyleEvent event) {
		ArrayList<Token> ltokens = parser.getLtokens();
		ArrayList<StyleRange> ranges = new ArrayList<StyleRange>();

		int[] rainbowDepths = null;
		if (Config.getRainbowBrackets() && !ltokens.isEmpty())
			rainbowDepths = computeRainbowDepths(ltokens);

		int line = txt.getLineAtOffset(event.lineOffset);

		int ftoken;
		for (ftoken = 0; ftoken < ltokens.size(); ftoken++)
			if (ltokens.get(ftoken).getEnd() >= event.lineOffset)
				break;

		int ltoken = ltokens.size();
		if (line + 1 < txt.getLineCount()) {
			int pos = txt.getOffsetAtLine(line + 1);

			for (ltoken = ftoken; ltoken < ltokens.size(); ltoken++)
				if (ltokens.get(ltoken).getStart() > pos)
					break;
		}

		for (int i = ftoken; i < ltoken; i++) {
			StyleRange range = getStyle(ltokens.get(i));
			if (rainbowDepths != null && rainbowDepths[i] >= 0) {
				Color rc = rainbowColor(rainbowDepths[i]);
				if (rc != null) range.foreground = rc;
			}
			ranges.add(range);

			// Bridge the background-highlight over whitespace between adjacent
			// snippet-variable tokens so the highlight appears contiguous.
			if (ltokens.get(i).getBackgroundReason() == SpecialBackgroundReason.CODE_SNIPPET)
				if (i + 1 < ltoken && ltokens.get(i + 1).getBackgroundReason() == SpecialBackgroundReason.CODE_SNIPPET && ltokens.get(i + 1).getSnippetVar() == ltokens.get(i).getSnippetVar())
					ranges.add(new StyleRange(ltokens.get(i).getEnd(), ltokens.get(i + 1).getStart() - ltokens.get(i).getEnd(), null, ltokens.get(i).getSpecialBackground()));
		}

		StyleRange[] rangesArray = new StyleRange[ranges.size()];
		event.styles = ranges.toArray(rangesArray);
	}

	public void setCustomLines(int[] lines) {
		customLines = lines;
	}

	public void lineGetBackground(LineBackgroundEvent event) {
		if (customLines == null) return;
		boolean go = false;
		for (int i : customLines)
			if (i == txt.getLineAtOffset(event.lineOffset)) {
				go = true;
				break;
			}
		if (go) {
			event.lineBackground = compareLineColor;
		}
	}

	private RGB getRGB(String rgbHex) {
		int[] rgb = {0, 0, 0};
		rgb[0] = Integer.parseInt(rgbHex.substring(0, 2), 16);
		rgb[1] = Integer.parseInt(rgbHex.substring(2, 4), 16);
		rgb[2] = Integer.parseInt(rgbHex.substring(4), 16);
		return new RGB(rgb[0], rgb[1], rgb[2]);
	}
}
