package com.bestiary.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight profanity gate for user-entered card nicknames.
 *
 * <p>Deliberately simple and predictable, not exhaustive: card nicknames are short
 * ({@literal <=}20 chars) and low-stakes, so the goal is to block casual slurs and
 * strong profanity (including basic leet/symbol obfuscation), while avoiding
 * sensitive false positives (e.g. it must NOT flag "Nigeria"). A determined user
 * can still smuggle something past this; that only affects their own card's label.
 *
 * <p>Two tiers of matching:
 * <ul>
 *   <li>{@link #SUBSTRING} — strong, unambiguous terms blocked anywhere in the text
 *       (catches "xXcuntXx"). Kept to words unlikely to appear inside clean ones.</li>
 *   <li>{@link #WORD} — short or embeddable terms blocked only as a standalone token,
 *       to dodge the Scunthorpe problem ("ass" in "assassin", "coon" in "raccoon").</li>
 * </ul>
 */
public final class ProfanityFilter {

    private ProfanityFilter() {}

    private static final Set<String> SUBSTRING = new HashSet<>(Arrays.asList(
        "fuck", "shit", "cunt", "nigger", "nigga", "faggot", "retard",
        "bitch", "bastard", "wanker", "bollock", "twat", "pussy", "whore",
        "slut", "asshole", "arsehole", "dildo", "jizz", "tranny", "wank",
        "motherfucker", "bullshit", "clit", "cocksucker", "cumshot"
    ));

    private static final Set<String> WORD = new HashSet<>(Arrays.asList(
        "ass", "arse", "damn", "crap", "piss", "prick", "dick", "cock",
        "hoe", "tit", "tits", "fag", "cum", "sex", "coon", "spic", "chink",
        "paki", "kike", "dyke", "gook", "wop", "homo", "nazi", "rape",
        "porn", "anal", "milf", "thot", "skank"
    ));

    /** Returns true when the nickname should be rejected. Null/blank is always clean. */
    public static boolean isProfane(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String norm = normalise(raw);
        if (norm.isBlank()) return false;

        for (String bad : SUBSTRING) {
            if (norm.contains(bad)) return true;
        }
        for (String token : norm.split(" ")) {
            if (WORD.contains(token)) return true;
        }
        return false;
    }

    /**
     * Lowercases, maps common leet/symbol substitutions to letters, and replaces any
     * non-letter with a space (so word-token checks stay clean). No repeat-collapsing:
     * that would create sensitive false positives (e.g. "Nigeria" -&gt; "niger").
     */
    private static String normalise(String s) {
        s = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '@': case '4': c = 'a'; break;
                case '0': c = 'o'; break;
                case '1': case '!': case '|': c = 'i'; break;
                case '3': c = 'e'; break;
                case '5': case '$': c = 's'; break;
                case '7': c = 't'; break;
                case '9': c = 'g'; break;
                case '8': c = 'b'; break;
                default: break;
            }
            sb.append(c >= 'a' && c <= 'z' ? c : ' ');
        }
        return sb.toString().trim();
    }
}
