/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Indenter}'s pure helpers — F3 step 4 extraction.
 * The {@code groupIndent} method touches a live {@code StyledText} so it
 * cannot be exercised headlessly in CI; the testable surface is
 * {@link Indenter#getTabStr()} (Config-driven) and
 * {@link Indenter#leadingWhitespace(String)} (pure string math).
 *
 * <p>Config is a static singleton. Snapshot/restore around each test so
 * mutations don't leak into other tests in the same JVM (mirrors the
 * {@code ConfigLineGuideTest} pattern).</p>
 */
class IndenterTest {

	private int savedTabSize;
	private boolean savedSpacesForTabs;

	@BeforeEach
	void snapshot() {
		savedTabSize = Config.getTabSize();
		savedSpacesForTabs = Config.getSpacesForTabs();
	}

	@AfterEach
	void restore() {
		Config.setTabSize(savedTabSize);
		Config.setSpacesForTabs(savedSpacesForTabs);
	}

	// ---- getTabStr ----

	@Test
	@DisplayName("getTabStr returns a hard tab when spacesForTabs=false")
	void getTabStrHardTab() {
		Config.setSpacesForTabs(false);
		Config.setTabSize(4);
		assertEquals("\t", Indenter.getTabStr());
	}

	@Test
	@DisplayName("getTabStr returns a hard tab when tabSize <= 0 (regardless of spacesForTabs)")
	void getTabStrZeroTabSize() {
		Config.setSpacesForTabs(true);
		Config.setTabSize(0);
		assertEquals("\t", Indenter.getTabStr());
	}

	@Test
	@DisplayName("getTabStr returns N spaces when spacesForTabs=true and tabSize=N")
	void getTabStrSpaces() {
		Config.setSpacesForTabs(true);

		Config.setTabSize(2);
		assertEquals("  ", Indenter.getTabStr());

		Config.setTabSize(4);
		assertEquals("    ", Indenter.getTabStr());

		Config.setTabSize(8);
		assertEquals("        ", Indenter.getTabStr());
	}

	// ---- leadingWhitespace ----

	@Test
	@DisplayName("leadingWhitespace handles null and empty input")
	void leadingWhitespaceNullEmpty() {
		assertEquals("", Indenter.leadingWhitespace(null));
		assertEquals("", Indenter.leadingWhitespace(""));
	}

	@Test
	@DisplayName("leadingWhitespace returns empty string when first char is non-whitespace")
	void leadingWhitespaceNoIndent() {
		assertEquals("", Indenter.leadingWhitespace("foo"));
		assertEquals("", Indenter.leadingWhitespace("x  "));
	}

	@Test
	@DisplayName("leadingWhitespace captures leading spaces, tabs, and mixtures")
	void leadingWhitespaceMixed() {
		assertEquals("    ", Indenter.leadingWhitespace("    code()"));
		assertEquals("\t", Indenter.leadingWhitespace("\tcode()"));
		assertEquals("\t\t", Indenter.leadingWhitespace("\t\tcode()"));
		assertEquals("  \t ", Indenter.leadingWhitespace("  \t code()"));
	}

	@Test
	@DisplayName("leadingWhitespace returns the entire string when it is all whitespace")
	void leadingWhitespaceAllWhitespace() {
		assertEquals("    ", Indenter.leadingWhitespace("    "));
		assertEquals("\t\t", Indenter.leadingWhitespace("\t\t"));
		assertEquals(" \t \t", Indenter.leadingWhitespace(" \t \t"));
	}

	@Test
	@DisplayName("leadingWhitespace stops at the first non-tab, non-space character")
	void leadingWhitespaceStopsAtNonWhitespace() {
		// Newline is neither space nor tab → not captured
		assertEquals("", Indenter.leadingWhitespace("\nfoo"));
		// Carriage return is neither → not captured
		assertEquals("", Indenter.leadingWhitespace("\rfoo"));
	}
}
