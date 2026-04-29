/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 *  http://repdev.org/ <support@repdev.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 */

package com.repdev.theme;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.RGB;

/**
 * A single named editor style (foreground/background/font-style triple).
 * Owns its Color resources; dispose() releases them.
 *
 * Replaces the package-private EStyle inner class that lived in SyntaxHighlighter.
 *
 * <p>Record-style: the canonical components ({@code fg}, {@code bg}, {@code
 * fontStyle}) are immutable and the only state. Construction from RGB triples
 * happens via the static factory {@link #of(Device, RGB, RGB, int)} which
 * materializes the {@link Color}s on the given {@link Device}.</p>
 */
public record EditorStyle(Color fg, Color bg, int fontStyle) {

	/** Construct an EditorStyle, materializing fg/bg as new {@link Color}s on
	 *  the given device. Pass {@code null} for either RGB to skip that side. */
	public static EditorStyle of(Device device, RGB frgb, RGB bgrgb, int fontStyle) {
		Color f = (frgb != null) ? new Color(device, frgb) : null;
		Color b = (bgrgb != null) ? new Color(device, bgrgb) : null;
		return new EditorStyle(f, b, fontStyle);
	}

	public static EditorStyle of(Device device, RGB frgb, RGB bgrgb) {
		return of(device, frgb, bgrgb, SWT.NORMAL);
	}

	public StyleRange getRange(int start, int len) {
		return new StyleRange(start, len, fg, bg, fontStyle);
	}

	public void dispose() {
		if (fg != null && !fg.isDisposed()) fg.dispose();
		if (bg != null && !bg.isDisposed()) bg.dispose();
	}
}
