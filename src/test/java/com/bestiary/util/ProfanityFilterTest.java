package com.bestiary.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the nickname profanity gate. Blocked cases use masked/leet inputs so the
 * test file stays clean; the pass cases make sure ordinary names aren't caught.
 */
public class ProfanityFilterTest {

    @Test
    public void blocksMaskedProfanity() {
        assertTrue(ProfanityFilter.isProfane("sh1t"));
        assertTrue(ProfanityFilter.isProfane("b!tch"));
        assertTrue(ProfanityFilter.isProfane("b0llocks"));
        assertTrue(ProfanityFilter.isProfane("xX b!tch Xx"));
    }

    @Test
    public void catchesLeetAndSymbols() {
        assertTrue(ProfanityFilter.isProfane("a$$"));
        assertTrue(ProfanityFilter.isProfane("@ss"));
    }

    @Test
    public void blocksStandaloneTokenButNotEmbedded() {
        assertTrue(ProfanityFilter.isProfane("my @ss hurts"));
        assertFalse(ProfanityFilter.isProfane("assassin"));
        assertFalse(ProfanityFilter.isProfane("raccoon"));
    }

    @Test
    public void allowsCleanNames() {
        assertFalse(ProfanityFilter.isProfane(null));
        assertFalse(ProfanityFilter.isProfane(""));
        assertFalse(ProfanityFilter.isProfane("  "));
        assertFalse(ProfanityFilter.isProfane("Fluffy"));
        assertFalse(ProfanityFilter.isProfane("Sir Reginald"));
        assertFalse(ProfanityFilter.isProfane("Nigeria"));
        assertFalse(ProfanityFilter.isProfane("Dickens"));
        assertFalse(ProfanityFilter.isProfane("grape"));
    }
}
