/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import com.repdev.theme.ThemeResources;
import com.repdev.theme.ThemeService;

/**
 * Owns line-number gutter painting and line-length guide painting for an
 * {@code EditorComposite}'s {@link StyledText}. Extracted in F3 step 3 to
 * narrow {@code EditorComposite}'s surface — the editor no longer owns the
 * cached soft/hard guide colors, the two paint listeners, or the
 * margin-width math; it just delegates.
 */
public final class GutterRenderer {

	/**
	 * Source of the fold/highlighter state the renderer needs but does not
	 * own. Implemented inline by {@code EditorComposite} so the renderer
	 * stays decoupled from the FoldingManager + SyntaxHighlighter concrete
	 * types and is unit-testable against fakes.
	 */
	public interface Context {
		/** Fold-triangle column width in pixels; 0 when no folding. */
		int getExtraFoldWidth();
		/** Effective line count for sizing the number column (unfolded count). */
		int getEffectiveLineCount();
		/** 1-based display line number for the given 0-based logical line. */
		int getDisplayLineNumber(int line);
		/** Bullet color from highlighter; null/disposed → number paint is skipped. */
		Color getBulletColor();
	}

	private final StyledText txt;
	private final Context ctx;
	private boolean showLineNumbers;

	private Color softLineGuideColor;
	private String softLineGuideColorKey;
	private Color hardLineGuideColor;
	private String hardLineGuideColorKey;

	public GutterRenderer(StyledText txt, Context ctx) {
		this.txt = txt;
		this.ctx = ctx;
		this.showLineNumbers = Config.getViewLineNumbers();
	}

	/**
	 * Attach paint listeners and reserve the initial left margin if there is
	 * a gutter to draw. Call once after construction.
	 */
	public void install() {
		txt.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) { paintLineGuides(e); }
		});
		txt.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) { paintLineNumbers(e); }
		});
		if (showLineNumbers || ctx.getExtraFoldWidth() > 0) {
			txt.setMargins(calcWidth(), 0, 0, 0);
		}
	}

	/**
	 * Re-read the {@code viewLineNumbers} config, refresh cached guide
	 * colors, and update the StyledText left margin. Repaints.
	 */
	public void refresh() {
		showLineNumbers = Config.getViewLineNumbers();
		if (txt == null || txt.isDisposed()) return;
		refreshLineGuideColors();
		if (showLineNumbers || ctx.getExtraFoldWidth() > 0) {
			txt.setMargins(calcWidth(), 0, 0, 0);
		} else {
			txt.setMargins(0, 0, 0, 0);
		}
		txt.redraw();
	}

	/** Refresh the cached soft + hard guide colors only (no redraw). */
	public void refreshLineGuideColors() {
		getSoftLineGuideColor();
		getHardLineGuideColor();
	}

	/** Width of just the line-number column (no fold column). */
	public int calcNumberColumnWidth() {
		return numberColumnWidth(showLineNumbers, ctx.getEffectiveLineCount());
	}

	/** Total gutter margin width (line numbers + fold column). */
	public int calcWidth() {
		return totalGutterWidth(showLineNumbers, ctx.getEffectiveLineCount(), ctx.getExtraFoldWidth());
	}

	/**
	 * Pure-arithmetic helper for unit tests: width of just the line-number
	 * column, given whether line numbers are shown and the largest line
	 * number that has to fit. 0 when line numbers are off.
	 */
	static int numberColumnWidth(boolean showLineNumbers, int effectiveLineCount) {
		if (!showLineNumbers) return 0;
		return (Integer.toString(effectiveLineCount).length() * 12) + 6;
	}

	/**
	 * Pure-arithmetic helper for unit tests: total gutter margin width
	 * (line-number column + fold-triangle column). When line numbers are
	 * off, the column collapses to a fixed 12 px reservation alongside any
	 * fold-column width.
	 */
	static int totalGutterWidth(boolean showLineNumbers, int effectiveLineCount, int extraFoldWidth) {
		if (showLineNumbers) {
			return numberColumnWidth(showLineNumbers, effectiveLineCount) + extraFoldWidth;
		}
		return 12 + extraFoldWidth;
	}

	public boolean isShowingLineNumbers() {
		return showLineNumbers;
	}

	/**
	 * After a buffer modification the line count may have grown, so re-reserve
	 * the gutter and scope a redraw to the visible gutter strip. Caller is
	 * responsible for deciding whether a buffer-changing event happened —
	 * this method just does the gutter side.
	 */
	public void postModifyRefresh() {
		if (txt == null || txt.isDisposed()) return;
		if (showLineNumbers || ctx.getExtraFoldWidth() > 0) {
			int w = calcWidth();
			txt.setMargins(w, 0, 0, 0);
			Rectangle ca = txt.getClientArea();
			txt.redraw(0, 0, w, ca.height, false);
		}
	}

	/** Dispose cached guide colors. Call from the StyledText dispose listener. */
	public void dispose() {
		if (softLineGuideColor != null) softLineGuideColor.dispose();
		if (hardLineGuideColor != null) hardLineGuideColor.dispose();
		softLineGuideColor = null;
		hardLineGuideColor = null;
	}

	// ---- paint listeners ----

	private void paintLineGuides(PaintEvent e) {
		boolean showSoft = Config.getShowSoftLineGuide();
		boolean showHard = Config.getShowHardLineGuide();
		if (!showSoft && !showHard) return;

		GC gc = e.gc;
		// Measure char advance the same way StyledText does. Using
		// gc.getAdvanceWidth('0') disagrees with StyledText on GTK
		// (Pango metrics vs GC metrics) and on DPI-scaled Windows,
		// producing guides offset by a constant factor (~1.5x). The
		// reliable approach is to set the GC font to the widget's font
		// and measure 100 '0' chars — textExtent over a long string
		// averages out per-glyph rounding that bites short measurements.
		Font prevFont = gc.getFont();
		gc.setFont(txt.getFont());
		final int sampleLen = 100;
		StringBuilder sb = new StringBuilder(sampleLen);
		for (int i = 0; i < sampleLen; i++) sb.append('0');
		int charWidth = gc.textExtent(sb.toString()).x / sampleLen;
		gc.setFont(prevFont);
		if (charWidth <= 0) return;

		int leftMargin = txt.getLeftMargin();
		int hScroll = txt.getHorizontalPixel();
		int clientW = txt.getClientArea().width;
		int clientH = txt.getClientArea().height;

		Color oldFg = gc.getForeground();
		int oldWidth = gc.getLineWidth();

		if (showSoft) {
			int xSoft = leftMargin + charWidth * Config.getSoftLineGuideColumn() - hScroll;
			if (xSoft >= leftMargin && xSoft < clientW) {
				gc.setForeground(getSoftLineGuideColor());
				gc.setLineWidth(1);
				gc.drawLine(xSoft, 0, xSoft, clientH);
			}
		}

		if (showHard) {
			int xHard = leftMargin + charWidth * Config.getHardLineGuideColumn() - hScroll;
			if (xHard >= leftMargin && xHard < clientW) {
				gc.setForeground(getHardLineGuideColor());
				gc.setLineWidth(2);
				gc.drawLine(xHard, 0, xHard, clientH);
			}
		}

		gc.setLineWidth(oldWidth);
		gc.setForeground(oldFg);
	}

	private void paintLineNumbers(PaintEvent e) {
		if (!showLineNumbers) return;
		int lh = txt.getLineHeight();
		if (lh == 0) return;
		int topLine = txt.getTopIndex();
		int clientH = txt.getClientArea().height;
		int maxLine = topLine + (clientH / lh) + 2;
		if (maxLine > txt.getLineCount()) maxLine = txt.getLineCount();

		int numberColW = calcNumberColumnWidth();
		GC gc = e.gc;
		Color oldFg = gc.getForeground();
		Color bulletColor = ctx.getBulletColor();
		if (bulletColor == null || bulletColor.isDisposed()) return;
		gc.setForeground(bulletColor);

		for (int line = topLine; line < maxLine; line++) {
			int y;
			try { y = txt.getLocationAtOffset(txt.getOffsetAtLine(line)).y; }
			catch (IllegalArgumentException ex) { continue; }
			int displayLine = ctx.getDisplayLineNumber(line);
			String num = String.valueOf(displayLine);
			int numW = gc.textExtent(num).x;
			int nx = numberColW - numW - 4;
			if (nx < 0) nx = 0;
			gc.drawString(num, nx, y, true);
		}
		gc.setForeground(oldFg);
	}

	// ---- guide-color cache ----

	private Color getSoftLineGuideColor() {
		String key = buildSoftLineGuideColorKey();
		if (softLineGuideColor == null || !key.equals(softLineGuideColorKey)) {
			if (softLineGuideColor != null) softLineGuideColor.dispose();
			softLineGuideColor = new Color(Display.getCurrent(), deriveSoftLineGuideRGB());
			softLineGuideColorKey = key;
		}
		return softLineGuideColor;
	}

	private Color getHardLineGuideColor() {
		String hex = Config.getHardLineGuideColor();
		if (hardLineGuideColor == null || !hex.equals(hardLineGuideColorKey)) {
			if (hardLineGuideColor != null) hardLineGuideColor.dispose();
			int r = Integer.parseInt(hex.substring(0, 2), 16);
			int g = Integer.parseInt(hex.substring(2, 4), 16);
			int b = Integer.parseInt(hex.substring(4, 6), 16);
			hardLineGuideColor = new Color(Display.getCurrent(), new RGB(r, g, b));
			hardLineGuideColorKey = hex;
		}
		return hardLineGuideColor;
	}

	private String buildSoftLineGuideColorKey() {
		RGB[] pair = resolveSoftGuidePair();
		RGB background = pair[0], foreground = pair[1];
		return background.red + "," + background.green + "," + background.blue + ":"
				+ foreground.red + "," + foreground.green + "," + foreground.blue;
	}

	private RGB deriveSoftLineGuideRGB() {
		RGB[] pair = resolveSoftGuidePair();
		return blend(pair[0], pair[1], 0.68f);
	}

	/**
	 * Resolve the (gutter background, bullet foreground) RGB pair the soft
	 * guide blends, falling back to the StyledText's own fg/bg when the
	 * theme has not provided them.
	 *
	 * @return {@code RGB[]{ background, foreground }}
	 */
	private RGB[] resolveSoftGuidePair() {
		ThemeResources theme = ThemeService.getInstance().getCurrent();
		RGB background = (theme != null && theme.gutterBackground != null && !theme.gutterBackground.isDisposed())
				? theme.gutterBackground.getRGB()
				: txt.getBackground().getRGB();
		RGB foreground = (theme != null && theme.bulletColor != null && !theme.bulletColor.isDisposed())
				? theme.bulletColor.getRGB()
				: txt.getForeground().getRGB();
		return new RGB[] { background, foreground };
	}

	private static RGB blend(RGB background, RGB foreground, float foregroundWeight) {
		float bgWeight = 1.0f - foregroundWeight;
		int red = Math.round(background.red * bgWeight + foreground.red * foregroundWeight);
		int green = Math.round(background.green * bgWeight + foreground.green * foregroundWeight);
		int blue = Math.round(background.blue * bgWeight + foreground.blue * foregroundWeight);
		return new RGB(red, green, blue);
	}
}
