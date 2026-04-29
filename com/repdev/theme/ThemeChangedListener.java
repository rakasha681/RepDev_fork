/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.theme;

/**
 * Listener for theme-change events fired by {@link ThemeService}. Implementations
 * must re-query colors/fonts from ThemeService on each call — they MUST NOT
 * cache the resources across calls, because the old ones are disposed shortly
 * after the event fires.
 */
public interface ThemeChangedListener {
	void themeChanged(ThemeResources newTheme);
}
