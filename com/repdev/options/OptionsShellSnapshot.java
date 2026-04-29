/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.options;

import com.repdev.Config;
import com.repdev.RepDevMain;
import com.repdev.theme.ThemeMode;
import com.repdev.theme.ThemeService;

/**
 * Captures the subset of {@link Config} / {@link ThemeService} state that the
 * Options dialog applies live (theme, line-guide, line-numbers, large-icons),
 * so Cancel can revert without round-tripping through every individual widget.
 *
 * <p>F2 step 1: extracted from {@code OptionsShell} so the per-tab Composites
 * being introduced in subsequent steps don't each have to know about the
 * snapshot/revert flow — they hand their controls' "live-apply" Configs back to
 * a single {@link #revert} call against this immutable record.</p>
 *
 * <p>Tabs that only commit on Save (no live-apply) — Server, Developer,
 * Documentation, plus most Editor controls — don't need to participate here:
 * they simply don't write to {@code Config} until the user clicks Save, so
 * Cancel naturally needs no revert for them.</p>
 */
public final class OptionsShellSnapshot {

	private final String themeId;
	private final ThemeMode themeMode;
	private final boolean viewLineNumbers;
	private final boolean showSoftLineGuide;
	private final boolean showHardLineGuide;
	private final int softLineGuideColumn;
	private final int hardLineGuideColumn;
	private final String hardLineGuideColor;
	private final boolean largeIcons;

	private OptionsShellSnapshot(String themeId, ThemeMode themeMode,
			boolean viewLineNumbers, boolean showSoftLineGuide, boolean showHardLineGuide,
			int softLineGuideColumn, int hardLineGuideColumn, String hardLineGuideColor,
			boolean largeIcons) {
		this.themeId = themeId;
		this.themeMode = themeMode;
		this.viewLineNumbers = viewLineNumbers;
		this.showSoftLineGuide = showSoftLineGuide;
		this.showHardLineGuide = showHardLineGuide;
		this.softLineGuideColumn = softLineGuideColumn;
		this.hardLineGuideColumn = hardLineGuideColumn;
		this.hardLineGuideColor = hardLineGuideColor;
		this.largeIcons = largeIcons;
	}

	/** Read the live state right now. */
	public static OptionsShellSnapshot capture() {
		ThemeService ts = ThemeService.getInstance();
		return new OptionsShellSnapshot(
				ts.getCurrentThemeId(),
				ts.getMode(),
				Config.getViewLineNumbers(),
				Config.getShowSoftLineGuide(),
				Config.getShowHardLineGuide(),
				Config.getSoftLineGuideColumn(),
				Config.getHardLineGuideColumn(),
				Config.getHardLineGuideColor(),
				Config.getLargeIcons());
	}

	/**
	 * Reverse any live-applied changes back to the captured state. Mirrors the
	 * pre-extraction Cancel handler exactly — only writes a Config setter when
	 * the value actually drifted, and refreshes gutters / icon presentation
	 * whenever a write happens (matches the previous per-field branch logic).
	 */
	public void revert() {
		ThemeService ts = ThemeService.getInstance();
		if (themeMode != null && ts.getMode() != themeMode) {
			ts.setMode(themeMode);
		}
		if (themeId != null && !themeId.equals(ts.getCurrentThemeId())) {
			ts.applyTheme(themeId);
		}
		if (Config.getViewLineNumbers() != viewLineNumbers) {
			Config.setViewLineNumbers(viewLineNumbers);
			refreshGutters();
		}
		if (Config.getShowSoftLineGuide() != showSoftLineGuide) {
			Config.setShowSoftLineGuide(showSoftLineGuide);
			refreshGutters();
		}
		if (Config.getShowHardLineGuide() != showHardLineGuide) {
			Config.setShowHardLineGuide(showHardLineGuide);
			refreshGutters();
		}
		if (Config.getSoftLineGuideColumn() != softLineGuideColumn) {
			Config.setSoftLineGuideColumn(softLineGuideColumn);
			refreshGutters();
		}
		if (Config.getHardLineGuideColumn() != hardLineGuideColumn) {
			Config.setHardLineGuideColumn(hardLineGuideColumn);
			refreshGutters();
		}
		if (!Config.getHardLineGuideColor().equals(hardLineGuideColor)) {
			Config.setHardLineGuideColor(hardLineGuideColor);
			refreshGutters();
		}
		if (Config.getLargeIcons() != largeIcons) {
			Config.setLargeIcons(largeIcons);
			if (RepDevMain.mainShell != null) RepDevMain.mainShell.refreshIconPresentation();
		}
	}

	private static void refreshGutters() {
		if (RepDevMain.mainShell != null) RepDevMain.mainShell.refreshAllGutters();
	}
}
