/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.theme;

public enum ThemeMode {
	LIGHT,
	DARK,
	HIGH_CONTRAST,
	SYSTEM,
	CUSTOM;

	public static ThemeMode fromString(String s) {
		if (s == null) return CUSTOM;
		try { return ThemeMode.valueOf(s.trim().toUpperCase()); }
		catch (IllegalArgumentException ex) { return CUSTOM; }
	}
}
