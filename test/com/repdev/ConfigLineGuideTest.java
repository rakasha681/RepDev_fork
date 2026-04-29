package com.repdev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for Config line-guide getters/setters.
 *
 * Config is a static singleton. Each test snapshots state in {@link #snapshot()}
 * and restores it in {@link #restore()} so tests do not leak mutations into one
 * another or into anything else that observes Config during the same JVM.
 */
class ConfigLineGuideTest {

	private boolean savedShowSoft;
	private boolean savedShowHard;
	private int savedSoftCol;
	private int savedHardCol;
	private String savedHardColor;

	@BeforeEach
	void snapshot() {
		savedShowSoft = Config.getShowSoftLineGuide();
		savedShowHard = Config.getShowHardLineGuide();
		savedSoftCol = Config.getSoftLineGuideColumn();
		savedHardCol = Config.getHardLineGuideColumn();
		savedHardColor = Config.getHardLineGuideColor();
	}

	@AfterEach
	void restore() {
		Config.setShowSoftLineGuide(savedShowSoft);
		Config.setShowHardLineGuide(savedShowHard);
		Config.setSoftLineGuideColumn(savedSoftCol);
		Config.setHardLineGuideColumn(savedHardCol);
		Config.setHardLineGuideColor(savedHardColor);
	}

	@Test
	void lineGuideDefaults() {
		assertTrue(Config.getShowSoftLineGuide(), "soft line guide should default on");
		assertFalse(Config.getShowHardLineGuide(), "hard line guide should default off");
		assertEquals(Config.DEFAULT_SOFT_LINE_GUIDE_COLUMN, Config.getSoftLineGuideColumn(),
				"soft line guide column should default to " + Config.DEFAULT_SOFT_LINE_GUIDE_COLUMN);
		assertEquals(Config.DEFAULT_HARD_LINE_GUIDE_COLOR, Config.getHardLineGuideColor(),
				"hard line guide color should default to " + Config.DEFAULT_HARD_LINE_GUIDE_COLOR);
	}

	@Test
	void softLineGuideColumnSanitization() {
		Config.setSoftLineGuideColumn(-12);
		assertEquals(Config.DEFAULT_SOFT_LINE_GUIDE_COLUMN, Config.getSoftLineGuideColumn(),
				"soft line guide column should clamp invalid values");

		Config.setSoftLineGuideColumn(96);
		assertEquals(96, Config.getSoftLineGuideColumn(),
				"soft line guide column should preserve valid values");
	}

	@Test
	void hardLineGuideColumnSanitization() {
		Config.setHardLineGuideColumn(-5);
		assertEquals(Config.DEFAULT_HARD_LINE_GUIDE_COLUMN, Config.getHardLineGuideColumn(),
				"hard line guide column should clamp invalid values");

		Config.setHardLineGuideColumn(108);
		assertEquals(108, Config.getHardLineGuideColumn(),
				"hard line guide column should preserve valid values");
	}

	@Test
	void hardLineGuideColorSanitization() {
		Config.setHardLineGuideColor("GGGGGG");
		assertEquals(Config.DEFAULT_HARD_LINE_GUIDE_COLOR, Config.getHardLineGuideColor(),
				"invalid hard line guide colors should reset to default");

		Config.setHardLineGuideColor("11aa22");
		assertEquals("11AA22", Config.getHardLineGuideColor(),
				"valid hard line guide colors should normalize to uppercase");
	}
}
