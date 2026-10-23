package com.bestiary.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Time-limited launch event: the Fractured Archive raid bosses are a guaranteed catch until
 * {@link #ENDS}. After that they fall back to the normal Boss-tier catch rate automatically —
 * no code change needed to switch it off.
 */
public final class CatchEvent {

    public static final String NAME = "Fractured Archive";

    /** Exclusive end — the event covers everything up to the end of 30 Nov 2026 (UTC). */
    public static final Instant ENDS = Instant.parse("2026-12-01T00:00:00Z");

    private static final Set<String> BOSSES;
    static {
        Set<String> s = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        s.addAll(Arrays.asList(
            "Archive Guardian", "Ezekiel", "Bork", "The Smorks", "Moia",
            "Dreadnoth the Jellied", "Dagon'hai Elder", "Dagon'hai Monk", "Xarth", "Ocellus"
        ));
        BOSSES = Collections.unmodifiableSet(s);
    }

    private CatchEvent() {}

    /** True while the event is running at {@code at}. */
    public static boolean isActive(Instant at) {
        return at != null && at.isBefore(ENDS);
    }

    /** True if a kill of {@code npcName} at {@code at} is a guaranteed catch. */
    public static boolean guaranteed(String npcName, Instant at) {
        return npcName != null && isActive(at) && BOSSES.contains(npcName);
    }

    /** Last day of the event for display, e.g. "30 Nov". */
    public static String lastDayLabel() {
        return DateTimeFormatter.ofPattern("d MMM").withZone(ZoneOffset.UTC).format(ENDS.minusSeconds(1));
    }
}
