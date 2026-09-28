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

    @Test
    public void hitDuringDeathAnimationDoesNotCreditKillTwice() {
        hit(60);
        hit(37);
        assertTrue(tracker.onActorDeath(new ActorDeath(npc)).isPresent());
        assertEquals(97, tracker.getLastKillDamage());

        // Next attack lands on the corpse mid death-animation, then it despawns while "dying".
        hit(12);
        when(npcUtil.isDying(npc)).thenReturn(true);
        assertFalse(tracker.onNpcDespawned(new NpcDespawned(npc)).isPresent());
        assertEquals(97, tracker.getLastKillDamage());
    }

    @Test
    public void finisherKillStillCreditedOnDespawn() {
        hit(40);
        when(npcUtil.isDying(npc)).thenReturn(true);
        assertTrue(tracker.onNpcDespawned(new NpcDespawned(npc)).isPresent());
        assertEquals(40, tracker.getLastKillDamage());
    }

    @Test
    public void reusedIndexIsTrackedAfterDespawn() {
        hit(97);
        tracker.onActorDeath(new ActorDeath(npc));
        tracker.onNpcDespawned(new NpcDespawned(npc));

        // A fresh NPC spawns into the same index and is killed normally.
        hit(50);
        assertTrue(tracker.onActorDeath(new ActorDeath(npc)).isPresent());
        assertEquals(50, tracker.getLastKillDamage());
    }
}
