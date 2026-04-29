/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev;

/** Small pure helper for app-level zoom preferences. SWT still handles OS DPI
 *  zoom; this scales RepDev's editor/navigation fonts on top of that. */
public final class AppZoom {
	public static final int MIN_PERCENT = 80;
	public static final int MAX_PERCENT = 150;
	public static final int DEFAULT_PERCENT = 100;
	public static final int STEP_PERCENT = 10;

	private AppZoom() { }

	public static int clampPercent(int percent) {
		if (percent < MIN_PERCENT) return MIN_PERCENT;
		if (percent > MAX_PERCENT) return MAX_PERCENT;
		return percent;
	}

	public static int zoomIn(int percent) {
		return clampPercent(percent + STEP_PERCENT);
	}

	public static int zoomOut(int percent) {
		return clampPercent(percent - STEP_PERCENT);
	}

	public static int reset() {
		return DEFAULT_PERCENT;
	}

	public static int scaleFontHeight(int baseHeight, int percent) {
		if (baseHeight <= 0) return baseHeight;
		return Math.max(8, Math.round((baseHeight * clampPercent(percent)) / 100.0f));
	}
}
