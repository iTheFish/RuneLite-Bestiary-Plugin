package com.bestiary.service;

import com.bestiary.model.CapturedCreature;

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
    private long xpGained      = 0;
    private long creditsGained = 0;
    private int  killCount     = 0;

    public void add(CapturedCreature c) { captures.add(c); }
    public void addXp(long xp)          { xpGained      += xp; }
    public void addCredits(long c)      { creditsGained += c; }
    public void addKill()               { killCount++; }

    public void clear() {
        captures.clear();
        xpGained      = 0;
        creditsGained = 0;
        killCount     = 0;
    }

    public boolean isEmpty()             { return captures.isEmpty(); }
    public long    getXpGained()         { return xpGained; }
    public long    getCreditsGained()    { return creditsGained; }
    public int     getKillCount()        { return killCount; }

    public List<CapturedCreature> getCaptures() {
        return Collections.unmodifiableList(captures);
    }
}
