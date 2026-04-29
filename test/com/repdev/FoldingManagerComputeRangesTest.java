package com.repdev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.repdev.parser.Token;

/**
 * Unit tests for {@link FoldingManager#computeRangesFromTokens}.
 *
 * Tokens are built with a helper that sets commentDepth=0, inString=false,
 * inDate=false so that isRealHead()/isRealEnd() work naturally for block-level
 * keywords and bracket pairs.
 *
 * The lineAtOffset lambda uses {@code offset / 10} so every 10 characters
 * equals one line, keeping assertions easy to read: offset 0-9 → line 0,
 * 10-19 → line 1, 20-29 → line 2, etc.
 */
class FoldingManagerComputeRangesTest {

    /** Map offset to line: every 10 chars = 1 line. */
    private static final IntUnaryOperator LINE_AT = offset -> {
        if (offset < 0) return -1;
        return offset / 10;
    };

    private static final Set<Integer> NO_FOLDED  = Collections.emptySet();
    private static final Set<Integer> NO_ORPHANS = Collections.emptySet();

    /**
     * Build a minimal Token suitable for isRealHead()/isRealEnd() checks.
     * commentDepth=0, inString=false, inDate=false → real head/end for
     * all ordinary block keywords and bracket pairs.
     */
    private static Token tok(String str, int pos) {
        // Token(str, pos, commentDepth, afterDepth, inString, afterString, inDefs, inDate, afterDate)
        return new Token(str, pos, 0, 0, false, false, false, false, false);
    }

    // -----------------------------------------------------------------------
    // Test 1: simple head/end pair on different lines → 1 FoldableRange
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("simple head/end pair on different lines produces one range")
    void simplePairDifferentLines() {
        // "procedure" at offset 0 (line 0), "end" at offset 20 (line 2)
        List<Token> tokens = Arrays.asList(tok("procedure", 0), tok("end", 20));
        int charCount = 30;

        List<FoldingManager.FoldableRange> ranges =
                FoldingManager.computeRangesFromTokens(tokens, charCount, LINE_AT, NO_FOLDED, NO_ORPHANS);

        assertEquals(1, ranges.size(), "expected exactly one foldable range");
        FoldingManager.FoldableRange r = ranges.get(0);
        assertEquals(0, r.headerLine, "header line");
        assertEquals(2, r.endLine,    "end line");
        assertEquals(20, r.endTokenOffset, "end token offset");
        assertTrue(!r.bracket, "should not be a bracket fold");
    }

    // -----------------------------------------------------------------------
    // Test 2: same-line head/end → 0 ranges (el - hl < 1)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("head/end on same line produces no range")
    void sameLine() {
        // "do" at offset 0 (line 0), "end" at offset 5 (line 0)
        List<Token> tokens = Arrays.asList(tok("do", 0), tok("end", 5));
        int charCount = 10;

        List<FoldingManager.FoldableRange> ranges =
                FoldingManager.computeRangesFromTokens(tokens, charCount, LINE_AT, NO_FOLDED, NO_ORPHANS);

        assertEquals(0, ranges.size(), "same-line pair must not produce a range");
    }

    // -----------------------------------------------------------------------
    // Test 3: nested DEFINE...END inside PROCEDURE...END → 2 ranges
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("nested define/end inside procedure/end produces two ranges")
    void nestedFolds() {
        // procedure @ 0 (line 0)
        //   define  @ 10 (line 1)
        //   end     @ 20 (line 2)  ← closes define
        // end       @ 30 (line 3)  ← closes procedure
        List<Token> tokens = Arrays.asList(
                tok("procedure", 0),
                tok("define",    10),
                tok("end",       20),
                tok("end",       30)
        );
        int charCount = 40;

        List<FoldingManager.FoldableRange> ranges =
                FoldingManager.computeRangesFromTokens(tokens, charCount, LINE_AT, NO_FOLDED, NO_ORPHANS);

        assertEquals(2, ranges.size(), "expected two ranges (inner + outer)");
        // Inner: define(line1) - end(line2)
        FoldingManager.FoldableRange inner = ranges.get(0);
        assertEquals(1, inner.headerLine);
        assertEquals(2, inner.endLine);
        // Outer: procedure(line0) - end(line3)
        FoldingManager.FoldableRange outer = ranges.get(1);
        assertEquals(0, outer.headerLine);
        assertEquals(3, outer.endLine);
    }

    // -----------------------------------------------------------------------
    // Test 4: orphan head — a head whose line is in foldedHeaderLines is
    //         skipped; the outer pair still matches correctly.
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("head on foldedHeaderLine is skipped; outer pair still matches")
    void orphanHead() {
        // procedure @ 0  (line 0)
        //   define  @ 10 (line 1)  ← already folded, so line 1 in foldedHeaderLines
        // end        @ 30 (line 3) ← would have closed define, now closes procedure
        // end        @ 40 (line 4) ← unmatched (stack empty)
        List<Token> tokens = Arrays.asList(
                tok("procedure", 0),
                tok("define",    10),
                tok("end",       30),
                tok("end",       40)
        );
        int charCount = 50;
        Set<Integer> foldedHeaders = new HashSet<>(Arrays.asList(1)); // line 1 is folded

        List<FoldingManager.FoldableRange> ranges =
                FoldingManager.computeRangesFromTokens(tokens, charCount, LINE_AT, foldedHeaders, NO_ORPHANS);

        // define at line 1 is skipped; first "end" (line 3) closes procedure (line 0)
        assertEquals(1, ranges.size(), "only the outer procedure/end pair should match");
        assertEquals(0, ranges.get(0).headerLine);
        assertEquals(3, ranges.get(0).endLine);
    }

    // -----------------------------------------------------------------------
    // Test 5: orphan bracket closer — a ']' whose line is in orphanCloserLines
    //         is skipped, so an outer PROCEDURE...END pair stays intact.
    //         This is the regression covered by commit a5a50f1.
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("orphan ] on orphanCloserLines is skipped; outer pair unaffected")
    void orphanBracketCloser() {
        // procedure @ 0  (line 0)
        //   [       @ 10 (line 1)  ← header of a collapsed bracket fold
        //   ]       @ 11 (line 1)  ← orphan closer left at headerLine+1 = line 2
        //                             (we place it at offset 20, i.e. line 2)
        // end       @ 30 (line 3)  ← real end for procedure
        List<Token> tokens = Arrays.asList(
                tok("procedure", 0),
                tok("]",         20),  // orphan closer at line 2
                tok("end",       30)   // real end at line 3
        );
        int charCount = 40;
        Set<Integer> orphanClosers = new HashSet<>(Arrays.asList(2)); // line 2

        List<FoldingManager.FoldableRange> ranges =
                FoldingManager.computeRangesFromTokens(tokens, charCount, LINE_AT, NO_FOLDED, orphanClosers);

        // The ']' at line 2 is skipped; procedure(line0)/end(line3) pair survives.
        assertEquals(1, ranges.size(), "orphan ] must not consume the procedure head");
        assertEquals(0, ranges.get(0).headerLine);
        assertEquals(3, ranges.get(0).endLine);
        assertTrue(!ranges.get(0).bracket, "should not be a bracket fold");
    }

    // -----------------------------------------------------------------------
    // Test 6: excluded openers — '"', '\'', '(', ':(' do NOT create folds
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("excluded openers do not create folds even with matching closers")
    void excludedOpeners() {
        // Pairs: " ... "    (isRealHead / isRealEnd for quote)
        //        ' ... '
        //        ( ... )
        //        :( ... )
        // All should be ignored by computeRangesFromTokens.
        List<Token> tokens = Arrays.asList(
                tok("\"",  0),  tok("\"",  19),
                tok("'",  20),  tok("'",   39),
                tok("(",  40),  tok(")",   59),
                tok(":(", 60),  tok(")",   79)
        );
        int charCount = 90;

        List<FoldingManager.FoldableRange> ranges =
                FoldingManager.computeRangesFromTokens(tokens, charCount, LINE_AT, NO_FOLDED, NO_ORPHANS);

        assertEquals(0, ranges.size(), "excluded openers must produce no foldable ranges");
    }

    // -----------------------------------------------------------------------
    // Test 7 (bonus): bracket head '[' / end ']' produces a bracket range
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("bracket pair [ ... ] on different lines produces bracket=true range")
    void bracketPair() {
        // '[' at offset 0 (line 0), ']' at offset 20 (line 2)
        List<Token> tokens = Arrays.asList(tok("[", 0), tok("]", 20));
        int charCount = 30;

        List<FoldingManager.FoldableRange> ranges =
                FoldingManager.computeRangesFromTokens(tokens, charCount, LINE_AT, NO_FOLDED, NO_ORPHANS);

        assertEquals(1, ranges.size());
        assertTrue(ranges.get(0).bracket, "bracket pair must produce bracket=true range");
        assertEquals(0, ranges.get(0).headerLine);
        assertEquals(2, ranges.get(0).endLine);
    }
}
