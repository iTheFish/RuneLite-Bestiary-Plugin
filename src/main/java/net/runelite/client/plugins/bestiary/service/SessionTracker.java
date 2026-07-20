package net.runelite.client.plugins.bestiary.service;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds captures made during the current login session.
 * Cleared on each LOGGED_IN state; never persisted to disk.
 */
@Singleton
public class SessionTracker {

    private final List<CapturedCreature> captures = new ArrayList<>();

    public void add(CapturedCreature c) { captures.add(c); }

    public void clear() { captures.clear(); }

    public boolean isEmpty() { return captures.isEmpty(); }

    public List<CapturedCreature> getCaptures() {
        return Collections.unmodifiableList(captures);
    }
}
