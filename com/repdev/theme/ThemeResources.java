/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.theme;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;

import com.repdev.AppZoom;
import com.repdev.Config;
import com.repdev.Style;

/**
 * Immutable snapshot of all resources belonging to a single loaded theme. An
 * instance is built once at load time; consumers read from it until
 * {@link ThemeService#applyTheme(String)} swaps in a new instance, at which
 * point the old one is scheduled for disposal.
 *
 * Never mutate fields after construction.
 */
public class ThemeResources {

	public final String themeId;

	// Editor / main-surface colors
	public final Color editorForeground;
	public final Color editorBackground;
	public final Color lineHighlight;      // "editor.line"
	public final Color blockMatch;         // "editor.token"
	public final Color bulletColor;        // "linenumber.fgColor"
	public final Color gutterBackground;    // "editor.gutterBg"
	public final Color findMatchBackground;
	public final Color findMatchActiveBackground;
	public final Color editorSelectionBackground;
	public final Color editorSelectionForeground;

	public final String fontName;
	public final int fontSize;
	public final Font editorFont;
	public final Font uiFont;

	// Token styles (keyed by legacy XML section name)
	public final EditorStyle normal;
	public final EditorStyle comments;
	public final EditorStyle variables;
	public final EditorStyle functions;
	public final EditorStyle keywords;
	public final EditorStyle task;
	public final EditorStyle typeChar;
	public final EditorStyle typeDate;
	public final EditorStyle struct1;
	public final EditorStyle struct2;
	public final EditorStyle struct1Invalid;
	public final EditorStyle struct2Invalid;

	// Optional chrome/UI tokens — fall back to editor colors when absent from XML.
	public final Color surfaceBackground;   // <surface bgColor="..."/>
	public final Color surfaceForeground;   // <surface fgColor="..."/>
	public final Color buttonBackground;    // <button bgColor="..."/> — null = keep native
	public final Color buttonForeground;
	public final Color selectionBackground; // <selection bgColor="..."/>
	public final Color selectionForeground;
	public final Color tabSelectionBackground; // <tabStyle selBg="..."/>
	public final Color tabSelectionForeground;
	public final Color tabInactiveBackground;
	public final Color tabInactiveForeground;
	public final Color sashColor;
	public final Color borderColor;
	/** <tabStyle selThickness="N"/> — pixels; -1 = don't touch (SWT default). */
	public final int tabSelectionBarThickness;

	// Rainbow-bracket palette for the syntax highlighter. Fixed length 6, each
	// slot guaranteed non-null (missing slots are backfilled from the default
	// palette in loadHardcodedDefaults). Depth N picks slot N % 6.
	public static final int RAINBOW_SLOTS = 6;
	public final Color[] rainbowPalette;

	private ThemeResources(Builder b) {
		this.themeId = b.themeId;
		this.editorForeground = b.editorForeground;
		this.editorBackground = b.editorBackground;
		this.lineHighlight = b.lineHighlight;
		this.blockMatch = b.blockMatch;
		this.bulletColor = b.bulletColor;
		this.gutterBackground = b.gutterBackground;
		this.findMatchBackground = b.findMatchBackground;
		this.findMatchActiveBackground = b.findMatchActiveBackground;
		this.editorSelectionBackground = b.editorSelectionBackground;
		this.editorSelectionForeground = b.editorSelectionForeground;
		this.fontName = b.fontName;
		this.fontSize = b.fontSize;
		this.editorFont = b.editorFont;
		this.uiFont = b.uiFont;
		this.normal = b.normal;
		this.comments = b.comments;
		this.variables = b.variables;
		this.functions = b.functions;
		this.keywords = b.keywords;
		this.task = b.task;
		this.typeChar = b.typeChar;
		this.typeDate = b.typeDate;
		this.struct1 = b.struct1;
		this.struct2 = b.struct2;
		this.struct1Invalid = b.struct1Invalid;
		this.struct2Invalid = b.struct2Invalid;
		this.surfaceBackground = b.surfaceBackground;
		this.surfaceForeground = b.surfaceForeground;
		this.buttonBackground = b.buttonBackground;
		this.buttonForeground = b.buttonForeground;
		this.selectionBackground = b.selectionBackground;
		this.selectionForeground = b.selectionForeground;
		this.tabSelectionBackground = b.tabSelectionBackground;
		this.tabSelectionForeground = b.tabSelectionForeground;
		this.tabInactiveBackground = b.tabInactiveBackground;
		this.tabInactiveForeground = b.tabInactiveForeground;
		this.sashColor = b.sashColor;
		this.borderColor = b.borderColor;
		this.tabSelectionBarThickness = b.tabSelectionBarThickness;
		this.rainbowPalette = b.rainbowPalette;
	}

	public void dispose() {
		disposeColor(editorForeground);
		disposeColor(editorBackground);
		disposeColor(lineHighlight);
		disposeColor(blockMatch);
		disposeColor(bulletColor);
		disposeColor(gutterBackground);
		disposeColor(findMatchBackground);
		disposeColor(findMatchActiveBackground);
		disposeColor(editorSelectionBackground);
		disposeColor(editorSelectionForeground);
		disposeColor(surfaceBackground);
		disposeColor(surfaceForeground);
		disposeColor(buttonBackground);
		disposeColor(buttonForeground);
		disposeColor(selectionBackground);
		disposeColor(selectionForeground);
		disposeColor(tabSelectionBackground);
		disposeColor(tabSelectionForeground);
		disposeColor(tabInactiveBackground);
		disposeColor(tabInactiveForeground);
		disposeColor(sashColor);
		disposeColor(borderColor);
		if (editorFont != null && !editorFont.isDisposed()) editorFont.dispose();
		if (uiFont != null && !uiFont.isDisposed()) uiFont.dispose();
		if (normal != null) normal.dispose();
		if (comments != null) comments.dispose();
		if (variables != null) variables.dispose();
		if (functions != null) functions.dispose();
		if (keywords != null) keywords.dispose();
		if (task != null) task.dispose();
		if (typeChar != null) typeChar.dispose();
		if (typeDate != null) typeDate.dispose();
		if (struct1 != null) struct1.dispose();
		if (struct2 != null) struct2.dispose();
		if (struct1Invalid != null) struct1Invalid.dispose();
		if (struct2Invalid != null) struct2Invalid.dispose();
		if (rainbowPalette != null)
			for (Color c : rainbowPalette) disposeColor(c);
	}

	private static void disposeColor(Color c) {
		if (c != null && !c.isDisposed()) c.dispose();
	}

	private static Color optColor(Device device, RGB rgb) {
		return (rgb != null) ? new Color(device, rgb) : null;
	}

	// Fallback palette used for slots a theme didn't specify, and for themes
	// that fail to load entirely. Tuned for decent contrast on both light and
	// dark surfaces.
	private static final RGB[] DEFAULT_RAINBOW = {
		new RGB(0xFF, 0xD7, 0x00),
		new RGB(0xDA, 0x70, 0xD6),
		new RGB(0x00, 0xBF, 0xFF),
		new RGB(0x32, 0xCD, 0x32),
		new RGB(0xFF, 0x63, 0x47),
		new RGB(0xFF, 0xA5, 0x00)
	};

	private static Color[] buildRainbowPalette(Device device, Style style) {
		Color[] out = new Color[RAINBOW_SLOTS];
		for (int i = 0; i < RAINBOW_SLOTS; i++) {
			RGB rgb = (style != null) ? style.getColor("rainbow", "c" + (i + 1)) : null;
			if (rgb == null) rgb = DEFAULT_RAINBOW[i];
			out[i] = new Color(device, rgb);
		}
		return out;
	}

	private static Color[] buildDefaultRainbowPalette(Device device) {
		return buildRainbowPalette(device, null);
	}

	/**
	 * Load a theme from the styles/ directory. Falls back to hardcoded default
	 * values if the file is missing/malformed (matches the legacy
	 * SyntaxHighlighter.loadStyle behavior).
	 */
	public static ThemeResources load(Device device, String themeId) {
		Builder b = new Builder();
		b.themeId = themeId;

		try {
			Style style = loadStyleRecursive(themeId);
			b.fontName = style.getFontValue("editor", "font");
			b.fontSize = style.getFontSize("editor", "fontSize");
			RGB bg = style.getColor("editor", "bgColor");
			RGB fg = style.getColor("editor", "fgColor");
			b.editorForeground = new Color(device, fg);
			b.editorBackground = new Color(device, bg);
			b.lineHighlight = new Color(device, style.getColor("editor", "line"));
			b.blockMatch = new Color(device, style.getColor("editor", "token"));
			try { b.bulletColor = new Color(device, style.getColor("linenumber", "fgColor")); }
			catch (Exception ex) { b.bulletColor = new Color(device, new RGB(127, 127, 127)); }

			// Item 1: Gutter background
			RGB gutterBg = style.getColor("editor", "gutterBg");
			b.gutterBackground = (gutterBg != null) ? new Color(device, gutterBg) : new Color(device, bg);

			// Item 6: Selection and find highlights
			b.editorSelectionBackground = optColor(device, style.getColor("editor", "selectionBg"));
			b.editorSelectionForeground = optColor(device, style.getColor("editor", "selectionFg"));
			b.findMatchBackground = optColor(device, style.getColor("editor", "findMatchBg"));
			b.findMatchActiveBackground = optColor(device, style.getColor("editor", "findMatchActiveBg"));

			b.normal = EditorStyle.of(device, null, null);
			b.comments = EditorStyle.of(device, style.getColor("comments", "fgColor"), style.getColor("comments", "bgColor"), style.getStyle("comments"));
			b.variables = EditorStyle.of(device, style.getColor("variables", "fgColor"), style.getColor("variables", "bgColor"), style.getStyle("variables"));
			b.functions = EditorStyle.of(device, style.getColor("functions", "fgColor"), style.getColor("functions", "bgColor"), style.getStyle("functions"));
			b.keywords = EditorStyle.of(device, style.getColor("keywords", "fgColor"), style.getColor("keywords", "bgColor"), style.getStyle("keywords"));
			b.task = EditorStyle.of(device, style.getColor("task", "fgColor"), style.getColor("task", "bgColor"), style.getStyle("task"));
			b.typeChar = EditorStyle.of(device, style.getColor("typeChar", "fgColor"), style.getColor("typeChar", "bgColor"), style.getStyle("typeChar"));
			b.typeDate = EditorStyle.of(device, style.getColor("typeDate", "fgColor"), style.getColor("typeDate", "bgColor"), style.getStyle("typeDate"));
			b.struct1 = EditorStyle.of(device, style.getColor("struct1", "fgColor"), style.getColor("struct1", "bgColor"), style.getStyle("struct1"));
			b.struct2 = EditorStyle.of(device, style.getColor("struct2", "fgColor"), style.getColor("struct2", "bgColor"), style.getStyle("struct2"));
			b.struct1Invalid = EditorStyle.of(device, style.getColor("struct1Inv", "fgColor"), style.getColor("struct1Inv", "bgColor"), style.getStyle("struct1Inv"));
			b.struct2Invalid = EditorStyle.of(device, style.getColor("struct2Inv", "fgColor"), style.getColor("struct2Inv", "bgColor"), style.getStyle("struct2Inv"));

			// Optional chrome tokens — null when absent so callers know to fall back.
			b.surfaceBackground   = optColor(device, style.getColor("surface",   "bgColor"));
			b.surfaceForeground   = optColor(device, style.getColor("surface",   "fgColor"));
			b.buttonBackground    = optColor(device, style.getColor("button",    "bgColor"));
			b.buttonForeground    = optColor(device, style.getColor("button",    "fgColor"));
			b.selectionBackground = optColor(device, style.getColor("selection", "bgColor"));
			b.selectionForeground = optColor(device, style.getColor("selection", "fgColor"));
			b.tabSelectionBackground = optColor(device, style.getColor("tabStyle", "selBg"));
			b.tabSelectionForeground = optColor(device, style.getColor("tabStyle", "selFg"));

			// Item 2: Tab inactive colors
			b.tabInactiveBackground = optColor(device, style.getColor("tabStyle", "inactiveBg"));
			b.tabInactiveForeground = optColor(device, style.getColor("tabStyle", "inactiveFg"));

			// Item 4: Sash and Border colors
			b.sashColor = optColor(device, style.getColor("surface", "sashColor"));
			RGB borderColor = style.getColor("surface", "borderColor");
			if (borderColor == null) borderColor = style.getColor("tabStyle", "borderColor");
			b.borderColor = optColor(device, borderColor);

			// Tab selection-bar thickness (VS Code-style accent under selected tab).
			String selThickStr = style.getValue("tabStyle", "selThickness");
			if (selThickStr != null && selThickStr.length() > 0) {
				try { b.tabSelectionBarThickness = Integer.parseInt(selThickStr.trim()); }
				catch (NumberFormatException ex) { /* keep default -1 */ }
			}

			b.rainbowPalette = buildRainbowPalette(device, style);

		} catch (Exception ex) {
			System.out.println("Invalid theme '" + themeId + "', using default");
			loadHardcodedDefaults(device, b);
		}

		try {
			b.editorFont = new Font(device, b.fontName, AppZoom.scaleFontHeight(b.fontSize, Config.getEditorZoomPercent()), SWT.NORMAL);
		} catch (Exception ex) {
			b.editorFont = null;
		}
		b.uiFont = createUiFont(device);

		return new ThemeResources(b);
	}

	private static Font createUiFont(Device device) {
		try {
			FontData[] data = device.getSystemFont().getFontData();
			if (data == null || data.length == 0) return null;
			FontData fd = data[0];
			fd.setHeight(AppZoom.scaleFontHeight(Math.max(fd.getHeight() + 1, 10), Config.getEditorZoomPercent()));
			return new Font(device, fd);
		} catch (Exception ex) {
			return null;
		}
	}

	private static Style loadStyleRecursive(String themeId) {
		String stylesDir = com.repdev.RepDevMain.installRoot() + "styles" + File.separator;
		File f = new File(stylesDir + themeId + ".xml");
		if (!f.isFile()) {
			File legacy = new File(stylesDir + "legacy-" + themeId + ".xml");
			if (legacy.isFile()) f = legacy;
		}
		Style style = new Style(f);
		if (style.baseTheme != null && !style.baseTheme.isEmpty() && !style.baseTheme.equals(themeId)) {
			Style base = loadStyleRecursive(style.baseTheme);
			style = new Style(f, base);
		}
		return style;
	}

	private static void loadHardcodedDefaults(Device device, Builder b) {
		b.fontName = "Courier New";
		b.fontSize = 11;
		RGB bg = new RGB(255, 255, 255);
		RGB fg = new RGB(0, 0, 0);
		b.editorForeground = new Color(device, fg);
		b.editorBackground = new Color(device, bg);
		b.lineHighlight = new Color(device, new RGB(232, 242, 254));
		b.blockMatch = new Color(device, new RGB(192, 192, 192));
		b.bulletColor = new Color(device, new RGB(127, 127, 127));
		b.gutterBackground = b.editorBackground;

		b.normal = EditorStyle.of(device, null, null);
		b.comments = EditorStyle.of(device, new RGB(127, 127, 127), null);
		b.variables = EditorStyle.of(device, new RGB(0, 0, 0), null, SWT.BOLD);
		b.functions = EditorStyle.of(device, new RGB(0, 0, 255), null, SWT.BOLD);
		b.keywords = EditorStyle.of(device, new RGB(0, 0, 255), null);
		b.task = EditorStyle.of(device, new RGB(64, 64, 64), null, SWT.BOLD);
		b.typeChar = EditorStyle.of(device, new RGB(255, 0, 0), null);
		b.typeDate = EditorStyle.of(device, new RGB(255, 0, 0), null, SWT.BOLD);
		b.struct1 = EditorStyle.of(device, new RGB(255, 0, 255), null);
		b.struct2 = EditorStyle.of(device, new RGB(255, 128, 255), null);
		b.struct1Invalid = EditorStyle.of(device, new RGB(255, 0, 255), new RGB(128, 0, 0), SWT.NONE);
		b.struct2Invalid = EditorStyle.of(device, new RGB(255, 128, 255), new RGB(128, 0, 0), SWT.NONE);
		b.rainbowPalette = buildDefaultRainbowPalette(device);
	}

	private static class Builder {
		String themeId;
		Color editorForeground;
		Color editorBackground;
		Color lineHighlight;
		Color blockMatch;
		Color bulletColor;
		Color gutterBackground;
		Color findMatchBackground;
		Color findMatchActiveBackground;
		Color editorSelectionBackground;
		Color editorSelectionForeground;
		String fontName = "";
		int fontSize = 0;
		Font editorFont;
		Font uiFont;
		EditorStyle normal;
		EditorStyle comments;
		EditorStyle variables;
		EditorStyle functions;
		EditorStyle keywords;
		EditorStyle task;
		EditorStyle typeChar;
		EditorStyle typeDate;
		EditorStyle struct1;
		EditorStyle struct2;
		EditorStyle struct1Invalid;
		EditorStyle struct2Invalid;
		Color surfaceBackground;
		Color surfaceForeground;
		Color buttonBackground;
		Color buttonForeground;
		Color selectionBackground;
		Color selectionForeground;
		Color tabSelectionBackground;
		Color tabSelectionForeground;
		Color tabInactiveBackground;
		Color tabInactiveForeground;
		Color sashColor;
		Color borderColor;
		int tabSelectionBarThickness = -1;
		Color[] rainbowPalette;
	}
}
