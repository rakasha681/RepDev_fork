/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GutterRenderer}'s pure gutter-width arithmetic.
 * The instance API depends on a live SWT {@code StyledText} (no headless
 * Display in CI), so the math itself is exposed as package-private static
 * helpers and tested here. Covers F3 step 3 extraction (line-number column
 * sizing + total gutter margin width).
 */
public class GutterRendererWidthTest {

	@Test
	@DisplayName("number column is 0 when line numbers are off")
	public void numberColumnZeroWhenOff() {
		assertEquals(0, GutterRenderer.numberColumnWidth(false, 1));
		assertEquals(0, GutterRenderer.numberColumnWidth(false, 99999));
	}

	@Test
	@DisplayName("number column reserves a single-digit width for an empty buffer")
	public void numberColumnEmptyBuffer() {
		// Brand-new buffer: getEffectiveLineCount() may be 0. "0".length() == 1
		// → 1 digit slot. Pin so future refactors can't silently regress to 0
		// (which would make the column visually disappear on first paint).
		assertEquals(1 * 12 + 6, GutterRenderer.numberColumnWidth(true, 0));
	}

	@Test
	@DisplayName("number column scales with the digit count of the last line")
	public void numberColumnScalesWithDigits() {
		// formula: digitCount * 12 + 6
		assertEquals(1 * 12 + 6, GutterRenderer.numberColumnWidth(true, 1));     // "1"
		assertEquals(1 * 12 + 6, GutterRenderer.numberColumnWidth(true, 9));     // "9"
		assertEquals(2 * 12 + 6, GutterRenderer.numberColumnWidth(true, 10));    // "10"
		assertEquals(2 * 12 + 6, GutterRenderer.numberColumnWidth(true, 99));    // "99"
		assertEquals(3 * 12 + 6, GutterRenderer.numberColumnWidth(true, 100));   // "100"
		assertEquals(4 * 12 + 6, GutterRenderer.numberColumnWidth(true, 1000));  // "1000"
		assertEquals(5 * 12 + 6, GutterRenderer.numberColumnWidth(true, 12345)); // "12345"
	}

	@Test
	@DisplayName("total gutter = number column + fold column when line numbers are on")
	public void totalGutterWithLineNumbers() {
		// 100-line file, 12px fold column → numberCol=42 + foldCol=12 = 54
		assertEquals(3 * 12 + 6 + 12, GutterRenderer.totalGutterWidth(true, 100, 12));
		// 9-line file, no fold column (non-RepGen) → numberCol=18 + 0
		assertEquals(1 * 12 + 6 + 0, GutterRenderer.totalGutterWidth(true, 9, 0));
		// 10000-line file with fold column
		assertEquals(5 * 12 + 6 + 12, GutterRenderer.totalGutterWidth(true, 10000, 12));
	}

	@Test
	@DisplayName("total gutter collapses to 12px reservation + fold column when line numbers are off")
	public void totalGutterWithoutLineNumbers() {
		// no line numbers, no folding (non-RepGen) → 12 + 0
		assertEquals(12, GutterRenderer.totalGutterWidth(false, 100, 0));
		// no line numbers, fold column reserved → 12 + 12
		assertEquals(24, GutterRenderer.totalGutterWidth(false, 100, 12));
		// effective line count is ignored when line numbers are off
		assertEquals(12, GutterRenderer.totalGutterWidth(false, 99999, 0));
	}

	@Test
	@DisplayName("fold column width adds linearly to total")
	public void foldColumnAddsLinearly() {
		int base = GutterRenderer.totalGutterWidth(true, 100, 0);
		assertEquals(base + 8,  GutterRenderer.totalGutterWidth(true, 100, 8));
		assertEquals(base + 16, GutterRenderer.totalGutterWidth(true, 100, 16));
	}
}
