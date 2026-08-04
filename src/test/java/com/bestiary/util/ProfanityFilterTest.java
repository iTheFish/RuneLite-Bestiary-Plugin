package com.bestiary.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the nickname profanity gate. Blocked cases use masked/leet inputs on
 * purpose — it keeps fully-spelled profanity out of the repo and doubles as a
 * check that the normaliser de-obfuscates before matching. The clean-name cases
 * guard against Scunthorpe-style false positives (assassin, raccoon, Nigeria…).
 */
public class ProfanityFilterTest {

    @Test
    public void blocksMaskedProfanity() {
        assertTrue(ProfanityFilter.isProfane("sh1t"));      // 1 -> i
        assertTrue(ProfanityFilter.isProfane("b!tch"));     // ! -> i
        assertTrue(ProfanityFilter.isProfane("b0llocks"));  // 0 -> o, substring match
        assertTrue(ProfanityFilter.isProfane("xX b!tch Xx"));
    }

    @Test
    public void catchesLeetAndSymbols() {
        assertTrue(ProfanityFilter.isProfane("a$$"));  // $ -> s, token match
        assertTrue(ProfanityFilter.isProfane("@ss"));  // @ -> a, token match
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
        assertFalse(ProfanityFilter.isProfane("Nigeria"));  // not the slur (no double-g)
        assertFalse(ProfanityFilter.isProfane("Dickens"));  // token-only match protects this
        assertFalse(ProfanityFilter.isProfane("grape"));    // token-only match protects this
    }
}
