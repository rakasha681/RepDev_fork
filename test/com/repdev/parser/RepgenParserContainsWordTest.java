package com.repdev.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RepgenParser#containsWord(String, String)}.
 *
 * Contract: whole-word containment — a match is valid only when neither the
 * character immediately before nor the character immediately after the matched
 * substring is a letter or digit (per {@link Character#isLetterOrDigit}).
 * Both inputs are assumed to already be lowercased when a case-insensitive
 * search is wanted.
 */
class RepgenParserContainsWordTest {

    // 1. Empty haystack → false
    @Test
    void emptyHaystackReturnsFalse() {
        assertFalse(RepgenParser.containsWord("", "foo"),
                "empty haystack cannot contain any word");
    }

    // 2. Empty word → false
    @Test
    void emptyWordReturnsFalse() {
        assertFalse(RepgenParser.containsWord("foo bar", ""),
                "empty word string is not a valid search target");
    }

    // 3. Null haystack → false (must not throw)
    @Test
    void nullHaystackReturnsFalse() {
        assertFalse(RepgenParser.containsWord(null, "foo"),
                "null haystack should return false without throwing");
    }

    // 4. Null word → false
    @Test
    void nullWordReturnsFalse() {
        assertFalse(RepgenParser.containsWord("foo bar", null),
                "null word should return false without throwing");
    }

    // 5. Word at start of haystack
    @Test
    void wordAtStart() {
        assertTrue(RepgenParser.containsWord("foo bar", "foo"),
                "word at the start of haystack should match");
    }

    // 6. Word at end of haystack
    @Test
    void wordAtEnd() {
        assertTrue(RepgenParser.containsWord("bar foo", "foo"),
                "word at the end of haystack should match");
    }

    // 7. Word in the middle
    @Test
    void wordInMiddle() {
        assertTrue(RepgenParser.containsWord("a foo b", "foo"),
                "word surrounded by spaces in the middle should match");
    }

    // 8. Word as substring of a larger alphanumeric token → false
    @Test
    void wordEmbeddedInAlphanumericToken() {
        assertFalse(RepgenParser.containsWord("foobar", "foo"),
                "alphanumeric neighbor ('b') must block the match");
    }

    // 9. Word with leading and trailing punctuation
    @Test
    void wordSurroundedByPunctuation() {
        assertTrue(RepgenParser.containsWord(".foo,", "foo"),
                "non-alphanumeric neighbors '.' and ',' should count as word boundaries");
    }

    // 10a. Word immediately followed by a digit → false
    @Test
    void wordFollowedByDigit() {
        assertFalse(RepgenParser.containsWord("foo1", "foo"),
                "digit neighbor after word must block the match");
    }

    // 10b. Word immediately preceded by a digit → false
    @Test
    void wordPrecededByDigit() {
        assertFalse(RepgenParser.containsWord("1foo", "foo"),
                "digit neighbor before word must block the match");
    }

    // 11. Word appears multiple times; only the last occurrence has valid boundaries
    @Test
    void multipleCandidatesOnlyOneValid() {
        assertTrue(RepgenParser.containsWord("foofoo foo", "foo"),
                "third occurrence 'foo' (space neighbors) should yield true even though earlier occurrences are blocked");
    }

    // 12a. Unicode: non-ASCII surrounding text, but word 'foo' has space boundary → true
    @Test
    void unicodeHaystackWithSpaceBoundary() {
        assertTrue(RepgenParser.containsWord("héllo foo", "foo"),
                "non-ASCII chars elsewhere in haystack should not affect a properly bounded 'foo'");
    }

    // 12b. Word immediately following a Unicode letter → false
    //      'é' is a letter per Character.isLetterOrDigit, so it blocks the match.
    @Test
    void wordPrecededByUnicodeLetter() {
        assertFalse(RepgenParser.containsWord("éfoo", "foo"),
                "Unicode letter 'é' is a letter per Character.isLetterOrDigit and must block the match");
    }

    // 12c. Word whose right neighbor is an alphanumeric ASCII char → false
    //      Separate from 12b to keep Unicode and ASCII boundary cases distinct.
    @Test
    void wordFollowedByAlphaInUnicodeHaystack() {
        assertFalse(RepgenParser.containsWord("héllofoo", "foo"),
                "'o' (alpha) neighbor on the left from 'héllo' must block match of 'foo' — wait, 'foo' starts after 'o', so left neighbor is 'o' which is alpha → false");
    }

    // 13. Single-character word
    @Test
    void singleCharWord() {
        assertTrue(RepgenParser.containsWord("a b a c", "a"),
                "single-char word surrounded by spaces should match");
    }

    // 14. Underscore neighbor — underscore is NOT alphanumeric per Character.isLetterOrDigit,
    //     so it IS treated as a word boundary. This may surprise readers who expect
    //     underscore to bind to an identifier (as in many regex \w definitions), but the
    //     implementation uses Character.isLetterOrDigit which excludes '_'.
    @Test
    void underscoreNeighborIsWordBoundary() {
        assertTrue(RepgenParser.containsWord("foo_bar", "foo"),
                "underscore is not alphanumeric per Character.isLetterOrDigit, so '_' counts as a word boundary");
    }
}
