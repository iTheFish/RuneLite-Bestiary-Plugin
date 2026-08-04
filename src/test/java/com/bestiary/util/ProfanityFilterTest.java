package com.bestiary.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the nickname profanity gate: it must block obvious profanity (including
 * basic leet obfuscation) while NOT flagging innocent names — especially the
 * sensitive Scunthorpe-style false positives ("Nigeria", "assassin", "raccoon").
 */
public class ProfanityFilterTest {

    @Test
    public void blocksObviousProfanity() {
        assertTrue(ProfanityFilter.isProfane("fuck you"));
        assertTrue(ProfanityFilter.isProfane("what a cunt"));
        assertTrue(ProfanityFilter.isProfane("xXbitchXx"));
        assertTrue(ProfanityFilter.isProfane("retard"));
    }

    @Test
    public void catchesBasicLeetAndSymbols() {
        assertTrue(ProfanityFilter.isProfane("sh1t"));   // 1 -> i
        assertTrue(ProfanityFilter.isProfane("a$$"));     // $ -> s
        assertTrue(ProfanityFilter.isProfane("b!tch"));   // ! -> i
        assertTrue(ProfanityFilter.isProfane("b0llocks")); // 0 -> o
    }

    @Test
    public void blocksStandaloneWordButNotEmbedded() {
        assertTrue(ProfanityFilter.isProfane("ass"));
        assertTrue(ProfanityFilter.isProfane("my ass hurts"));
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
        assertFalse(ProfanityFilter.isProfane("Nigeria"));   // not "nigger" (no double-g)
        assertFalse(ProfanityFilter.isProfane("Dickens"));   // "dick" is a token match only
        assertFalse(ProfanityFilter.isProfane("grape"));     // "rape" is a token match only
    }

    // NOTE: strong terms are matched as substrings, so place names that embed one
    // (e.g. "Scunthorpe" contains "cunt") ARE blocked. Accepted trade-off — casual
    // profanity is far likelier than that as a card nickname.
}
