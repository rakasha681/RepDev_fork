/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.repdev.Config;

/**
 * Golden-file tests for {@link Formatter}.
 *
 * <p>These pin the formatter's output for representative RepGen inputs so
 * the indent / spacing / blank-line behavior introduced on this branch
 * (commit 0f8089a — split-indent DO blocks, blank-line-after-END toggle,
 * DO-vs-division END differentiation, then/else single-statement bumps)
 * stays stable across future edits. Without this, the only signal that
 * Formatter has regressed is a user opening a file in the IDE.</p>
 *
 * <p>The full {@code RepgenParser} cannot be exercised headlessly — it
 * depends on a live SWT {@code StyledText}, the IDE's {@code RepDevMain}
 * shell, and a number of singletons. The relevant lexer logic for
 * comment-free, string-free, date-free input is faithfully replicated as
 * a small {@link MiniTokenizer} below — it walks the source character by
 * character, matches identifiers as {@code [a-z0-9#@]+} (the exact set
 * the production lexer uses), recognizes the {@code :(} two-char token,
 * and emits a single-char token for every other non-whitespace
 * character. {@code setNearTokens} wires up the {@code getAfter()} chain
 * the formatter walks. {@code cdepth=0}, {@code inString=false}, and
 * {@code inDate=false} on every token, which is exactly what the
 * production tokenizer produces for inputs that contain no
 * {@code [...]} comments, {@code "..."} strings, or {@code '...'}
 * dates.</p>
 *
 * <p>Each test snapshots Config because the formatter reads
 * {@code splitIndentDoBlocks}, {@code blankLineAfterEnd},
 * {@code tabSize}, and {@code spacesForTabs}. Restoring per-test keeps
 * mutations from leaking into other tests in the same JVM.</p>
 */
class FormatterGoldenTest {

	// ---- Config snapshot/restore (mirrors the ConfigLineGuideTest pattern) ----

	private boolean savedSplitIndent;
	private boolean savedBlankLineAfterEnd;
	private int savedTabSize;
	private boolean savedSpacesForTabs;

	@BeforeEach
	void snapshot() {
		savedSplitIndent = Config.getSplitIndentDoBlocks();
		savedBlankLineAfterEnd = Config.getBlankLineAfterEnd();
		savedTabSize = Config.getTabSize();
		savedSpacesForTabs = Config.getSpacesForTabs();

		// Default test bed: 2-space soft tabs, no split-indent, blank line
		// after END on. Individual tests override what they need.
		Config.setTabSize(2);
		Config.setSpacesForTabs(true);
		Config.setSplitIndentDoBlocks(false);
		Config.setBlankLineAfterEnd(true);
	}

	@AfterEach
	void restore() {
		Config.setSplitIndentDoBlocks(savedSplitIndent);
		Config.setBlankLineAfterEnd(savedBlankLineAfterEnd);
		Config.setTabSize(savedTabSize);
		Config.setSpacesForTabs(savedSpacesForTabs);
	}

	// ---- Tests ----

	// Golden expectations below pin the *actual* current formatter
	// output, including its known quirks (trailing space after every
	// non-no-space-after token, an extra blank line between back-to-back
	// DO heads, the "blank line after END" emitted as two newlines so
	// the visible result is one blank line). Capturing actual behavior
	// is the point — these tests fire when a future edit changes the
	// formatter's output silently, even unintentionally.

	@Test
	@DisplayName("simple do/end block indents the body by one tab")
	void simpleDoEnd() {
		// "do " (trailing space, then \n + indent) → body → "end " then
		// the configured 2 newlines for blankLineAfterEnd=true.
		String input = "do\nx=1\nend";
		String expected = "do \n  x=1 \nend \n\n";
		assertFormatsTo(input, expected);
	}

	@Test
	@DisplayName("nested do/end blocks each add a tab of indent and a blank line between DO heads")
	void nestedDoEnd() {
		// Two back-to-back DO heads produce a blank line between them
		// because "do" is in newLineAfter (1 NL) AND "do" as the next
		// token triggers newLineBefore (1 NL).
		String input = "do\ndo\nx=1\nend\nend";
		String expected = "do \n  \n  do \n    x=1 \n  end \n\nend \n\n";
		assertFormatsTo(input, expected);
	}

	@Test
	@DisplayName("blankLineAfterEnd=false drops the trailing blank line after END")
	void blankLineAfterEndOff() {
		Config.setBlankLineAfterEnd(false);
		String input = "do\nx=1\nend";
		String expected = "do \n  x=1 \nend \n";
		assertFormatsTo(input, expected);
	}

	@Test
	@DisplayName("splitIndentDoBlocks=true puts the matching END at half-tab indent")
	void splitIndentDoBlocks() {
		Config.setSplitIndentDoBlocks(true);
		Config.setTabSize(4); // halfTab = 2 spaces, fullTab = 4 spaces
		String input = "do\nx=1\nend";
		// Body indented by full tab (4 spaces); the END line gets
		// half-tab indent (2 spaces) thanks to the split-indent rule
		// for tokens whose `after` just popped a DO.
		String expected = "do \n    x=1 \n  end \n\n";
		assertFormatsTo(input, expected);
	}

	@Test
	@DisplayName("tabSize=4 with hard tabs uses \\t for indentation")
	void hardTabIndent() {
		Config.setSpacesForTabs(false);
		Config.setTabSize(4);
		String input = "do\nx=1\nend";
		String expected = "do \n\tx=1 \nend \n\n";
		assertFormatsTo(input, expected);
	}

	@Test
	@DisplayName("identifiers are emitted with their original source case")
	void preservesSourceCase() {
		// The token stream is internally lowercased for matching but the
		// formatter slices the original source string for output, so
		// MixedCase identifiers come back exactly as written.
		String input = "do\nMyVar=1\nend";
		String expected = "do \n  MyVar=1 \nend \n\n";
		assertFormatsTo(input, expected);
	}

	@Test
	@DisplayName("then on its own line bumps the next-line indent by one full tab")
	void thenSingleStatementIndent() {
		// 0f8089a: when `then` is followed by a newline, the line carrying
		// the nested statement gets +fullTab on top of the surrounding
		// indent. Outer do contributes 2 spaces, the then-bump adds 2
		// more, so b=1 lands at 4 spaces.
		String input = "do\nif a then\nb=1\nend";
		String expected = "do \n  if a then \n    b=1 \nend \n\n";
		assertFormatsTo(input, expected);
	}

	@Test
	@DisplayName("else on its own line bumps the next-line indent like then")
	void elseSingleStatementIndent() {
		// The else branch's body gets the same +fullTab as the then
		// branch. The `else` keyword itself sits at outer indent (2),
		// the `c=2` body at 4. (`else` followed by `if` would NOT bump
		// — that's the chained "else if" case the formatter avoids
		// double-bumping; not exercised here.)
		String input = "do\nif a then\nb=1\nelse\nc=2\nend";
		String expected = "do \n  if a then \n    b=1 \n  else \n    c=2 \nend \n\n";
		assertFormatsTo(input, expected);
	}

	@Test
	@DisplayName("END that closes a non-DO division (e.g. SETUP) skips the forced blank-line-after-END")
	void divisionEndSkipsBlankLine() {
		// 0f8089a's headline behavior: an END closing a SETUP/PROCEDURE/
		// SELECT/etc. division falls through to source-whitespace
		// handling rather than emitting two newlines. With no trailing
		// content after END in the source, the output ends with just
		// "end " (one space, no extra newlines).
		String input = "setup\nx=1\nend";
		String expected = "setup \n  x=1 \nend ";
		assertFormatsTo(input, expected);
	}

	@Test
	@DisplayName("operators in noSpaceAfter / noSpaceBefore lists join tightly")
	void tightOperatorBinding() {
		// '=' is in BOTH noSpaceAfter and noSpaceBefore; '+' is in both.
		// So a=b+c stays glued: no spaces inserted around the operators.
		// The trailing space after the final identifier "c" is the
		// default space-after every non-no-space-after token.
		String input = "do\na=b+c\nend";
		String expected = "do \n  a=b+c \nend \n\n";
		assertFormatsTo(input, expected);
	}

	// ---- Helpers ----

	private static void assertFormatsTo(String input, String expected) {
		ArrayList<Token> tokens = MiniTokenizer.tokenize(input);
		String actual = new Formatter(input, tokens).getFormattedFile();
		assertEquals(expected, actual,
				"Formatter output mismatch.\n--- input ---\n" + input
				+ "\n--- expected ---\n" + expected
				+ "\n--- actual ---\n" + actual);
	}

	/**
	 * Replicates the relevant subset of {@code RepgenParser.parse}'s
	 * lexer for comment-free, string-free, date-free RepGen input. The
	 * production lexer (line ~639 of RepgenParser) walks chars and
	 * accumulates an alphanumeric run as a single token whenever it
	 * sees a non-{@code [a-z0-9#@]} terminator; everything else (each
	 * single non-letter, non-digit, non-whitespace char) becomes a
	 * one-char token. The {@code :(} two-char token is recognized when
	 * the next char after a colon is an open paren.
	 *
	 * <p>This implementation produces tokens with {@code cdepth=0},
	 * {@code inString=false}, {@code inDate=false}, which matches what
	 * the production lexer emits for inputs without {@code [...]},
	 * {@code "..."}, or {@code '...'}. After token construction it calls
	 * {@link Token#setNearTokens} so {@code getAfter()} resolves the
	 * way the formatter expects.</p>
	 */
	private static final class MiniTokenizer {
		static ArrayList<Token> tokenize(String src) {
			// Production lexer pre-lowercases the char buffer (RepgenParser
			// line 609: `str.substring(charStart, charEnd).toLowerCase()`),
			// so every Token.str the formatter sees is lowercase even when
			// the source was uppercase. The original-case text is recovered
			// via getCorrectTokenString → oldFile.substring(start, end),
			// so we lowercase here too for keyword-match fidelity but keep
			// the source string untouched for Formatter's output slicing.
			String lower = src.toLowerCase();
			ArrayList<Token> out = new ArrayList<>();
			int i = 0;
			while (i < src.length()) {
				char c = lower.charAt(i);
				if (Character.isWhitespace(c)) {
					i++;
					continue;
				}
				if (isIdentChar(c)) {
					int start = i;
					while (i < src.length() && isIdentChar(lower.charAt(i))) i++;
					String tok = lower.substring(start, i);
					out.add(new Token(tok, start, 0, 0, false, false, false, false, false));
					continue;
				}
				if (c == ':' && i + 1 < src.length() && lower.charAt(i + 1) == '(') {
					out.add(new Token(":(", i, 0, 0, false, false, false, false, false));
					i += 2;
					continue;
				}
				out.add(new Token(String.valueOf(c), i, 0, 0, false, false, false, false, false));
				i++;
			}
			for (int p = 0; p < out.size(); p++) out.get(p).setNearTokens(out, p);
			return out;
		}

		private static boolean isIdentChar(char c) {
			// Mirrors RepgenParser line 642 — `chars` is already lowercased
			// so this is the literal predicate the production lexer applies.
			return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '#' || c == '@';
		}
	}
}
