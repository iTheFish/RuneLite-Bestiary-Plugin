package com.bestiary.service;

import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.game.NpcUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KillTrackerTest {

    private NpcUtil npcUtil;
    private KillTracker tracker;
    private NPC npc;

    @Before
    public void setUp() {
        npcUtil = mock(NpcUtil.class);
        tracker = new KillTracker(mock(Client.class), npcUtil);
        npc = mock(NPC.class);
        when(npc.getIndex()).thenReturn(42);
        when(npc.getName()).thenReturn("Kurask");
    }

    private void hit(int amount) {
        Hitsplat hs = mock(Hitsplat.class);
        when(hs.isMine()).thenReturn(true);
        when(hs.getAmount()).thenReturn(amount);
        HitsplatApplied e = new HitsplatApplied();
        e.setActor(npc);
        e.setHitsplat(hs);
        tracker.onHitsplatApplied(e);
    }

    private void die() {
        when(npc.isDead()).thenReturn(true); // RuneLite sets isDead() as the health bar hits 0
        tracker.onActorDeath(new ActorDeath(npc));
    }

    private void despawn() {
        tracker.onNpcDespawned(new NpcDespawned(npc)).ifPresent(k -> {
            throw new AssertionError("despawn credited an already-settled kill");
        });
    }

    @Test
    public void hitThenDeathOrder_creditsOnceWithFullDamage() {
        // RuneLite <= 1.12.39: killing hitsplat, then ActorDeath.
        hit(60);
        hit(37);
        die();
        List<KillTracker.Kill> kills = tracker.onGameTick();
        assertEquals(1, kills.size());
        assertEquals(97, kills.get(0).damage);
    }

    @Test
    public void deathThenHitOrder_includesKillingBlow() {
        // RuneLite 1.13.0: ActorDeath arrives before the killing hitsplat in the same tick.
        hit(87);
        die();
        hit(10);
        List<KillTracker.Kill> kills = tracker.onGameTick();
        assertEquals(1, kills.size());
        assertEquals(97, kills.get(0).damage);

        when(npcUtil.isDying(npc)).thenReturn(true);
        despawn();
    }

    @Test
    public void oneHitKillWithDeathFirst_stillCredited() {
        die();
        hit(40);
        assertEquals(40, tracker.onGameTick().get(0).damage);
    }

    @Test
    public void hitDuringDeathAnimation_doesNotCreditTwice() {
        hit(97);
        die();
        assertEquals(1, tracker.onGameTick().size());

        // Next attack / cannon ball lands on the corpse a tick later, then it despawns "dying".
        hit(12);
        when(npcUtil.isDying(npc)).thenReturn(true);
        despawn();
        assertTrue(tracker.onGameTick().isEmpty());
    }

    @Test
    public void transformInPlace_secondFormCreditedAgain() {
        // Kalphite Queen style: phase 1 dies, then the same NPC refills and fights on (no despawn).
        hit(255);
        die();
        assertEquals(255, tracker.onGameTick().get(0).damage);

        when(npc.isDead()).thenReturn(false); // health bar refilled -> RuneLite clears isDead()
        hit(100);
        hit(155);
        die();
        List<KillTracker.Kill> kills = tracker.onGameTick();
        assertEquals(1, kills.size());
        assertEquals(255, kills.get(0).damage);
    }

    @Test
    public void deathWithoutPlayerDamage_isNotCredited() {
        die();
        assertTrue(tracker.onGameTick().isEmpty());
    }

    @Test
    public void finisherKill_stillCreditedOnDespawn() {
        hit(40);
        when(npcUtil.isDying(npc)).thenReturn(true);
        KillTracker.Kill kill = tracker.onNpcDespawned(new NpcDespawned(npc)).orElse(null);
        assertTrue(kill != null);
        assertEquals(40, kill.damage);
    }

    @Test
    public void deathAndDespawnInSameTick_creditedOnce() {
        hit(40);
        die();
        KillTracker.Kill kill = tracker.onNpcDespawned(new NpcDespawned(npc)).orElse(null);
        assertTrue(kill != null);
        assertEquals(40, kill.damage);
        assertTrue(tracker.onGameTick().isEmpty());
    }

    @Test
    public void reusedIndex_isTrackedAfterDespawn() {
        hit(97);
        die();
        tracker.onGameTick();
        tracker.onNpcDespawned(new NpcDespawned(npc));

        // A fresh NPC spawns into the same index and is killed normally.
        when(npc.isDead()).thenReturn(false);
        hit(50);
        die();
        List<KillTracker.Kill> kills = tracker.onGameTick();
        assertFalse(kills.isEmpty());
        assertEquals(50, kills.get(0).damage);
    }
}
