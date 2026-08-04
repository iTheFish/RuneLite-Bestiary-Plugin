package com.bestiary.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Filters card nicknames for profanity. The blocklist is kept as salted, truncated
 * SHA-256 digests rather than plaintext, so nothing offensive lands in the source.
 *
 * <p>Input is normalised (lowercase, common leet/symbol swaps, non-letters to spaces),
 * then candidate tokens and substrings are hashed and looked up. Strong terms match
 * anywhere inside a word; milder ones only as a whole word, which keeps ordinary names
 * from tripping it. It's intentionally simple — enough for a 20-char label.
 */
public final class ProfanityFilter {

    private ProfanityFilter() {}

    private static final String SALT = "bestiary-v1:";

    private static final int STRONG_MIN_LEN = 4;
    private static final int STRONG_MAX_LEN = 12;

    private static final Set<String> STRONG_HASHES = new HashSet<>(Arrays.asList(
        "ee2cf18920bd3456", "ae6ae3267ba320da", "e2b2acf4cbafc6f1", "cf4c540c85ba0e35",
        "8f5cc1648db9eba5", "4c6edcbe9620ca7b", "3263117e2ab8b95a", "f5abb67727cfb402",
        "c2c178ea97ec0313", "de957dc7a0301b93", "b267be2709d6c521", "1e7cb0b69bfa0bff",
        "57c2a54a5835ea17", "b269981d732754e7", "a23eff6b3fc64aae", "780191f3abaa1f90",
        "97e140fab15e8f25", "58c2666662f7dd6b", "195d4c76735af9f6", "634f8d7810413f54",
        "3573740bdf9580b2", "621db227bd4e8492", "6afb19ef81d6f4d8", "fdc3eb023b908299",
        "2e55b472438c08af", "eb41480f6d71b527"
    ));

    private static final Set<String> TOKEN_HASHES = new HashSet<>(Arrays.asList(
        "09f2f8f822c8ba99", "b77f86d54fae08b1", "e416b576ee2fa4a1", "611f78b13b6b9ec5",
        "0a7ff95edae67b70", "2733b465b0e26d88", "2f41d99b609e4082", "dd0b5731ab6f4672",
        "d2a3eaf09fe36d46", "1c1ba6d8ac3ee6a1", "45addc79a5a3fdad", "30e8d1456c47c0a9",
        "cb79e206ca1a06f8", "6c50bbae6e93079a", "49faa09feee144cc", "ebe60e0c3c7667c1",
        "33551932ab12ca03", "50fc0baa09d14715", "f13dc48a1c600d31", "684b4b5573b63a57",
        "f47ed61c5e4bab46", "b932a17aba07ce87", "d86b38efcbf47add", "115bedc5ea2d2ea2",
        "303284dc3fc3cb72", "c27340f141ec2057", "f13dba26ae68cff7", "3bf0aa8965121884",
        "40c7e1fbcbb95469", "89dd3e7777e80daa"
    ));

    /** Returns true when the nickname should be rejected. Null/blank is always clean. */
    public static boolean isProfane(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String norm = normalise(raw);
        if (norm.isBlank()) return false;

        for (String token : norm.split(" ")) {
            if (token.isEmpty()) continue;
            if (TOKEN_HASHES.contains(hash(token))) return true;
            int n = token.length();
            int max = Math.min(STRONG_MAX_LEN, n);
            for (int len = STRONG_MIN_LEN; len <= max; len++) {
                for (int start = 0; start + len <= n; start++) {
                    if (STRONG_HASHES.contains(hash(token.substring(start, start + len)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Lowercases, swaps common leet characters back to letters, and blanks out the rest. */
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

    /** First 16 hex chars of SHA-256(salt + word) — matches the stored digests. */
    private static String hash(String word) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((SALT + word).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return ""; // SHA-256 is guaranteed present; never matches a stored digest
        }
    }
}
