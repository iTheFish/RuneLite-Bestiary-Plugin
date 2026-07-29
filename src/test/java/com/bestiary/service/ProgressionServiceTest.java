package com.bestiary.service;

import com.bestiary.model.Achievement;
import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.DifficultyTier;
import com.bestiary.model.MonsterRoster;
import com.bestiary.util.XpTable;
import net.runelite.api.NPC;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProgressionServiceTest {

    @Mock private NPC npc;

    private ProgressionService service;
    private BestiaryCollection collection;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(npc.getName()).thenReturn("Goblin");
        when(npc.getId()).thenReturn(100);
        when(npc.getCombatLevel()).thenReturn(2);

        service    = new ProgressionService();
        collection = new BestiaryCollection();
        service.init(new ProgressionService.ProgressionState(), collection);
    }

    // --- XP table ---

    @Test
    public void level1RequiresZeroXp() {
        assertEquals(0, XpTable.xpForLevel(1));
    }

    @Test
    public void level2XpIsPositive() {
        assertTrue(XpTable.xpForLevel(2) > 0);
    }

    @Test
    public void xpTableIsStrictlyIncreasing() {
        for (int lvl = 2; lvl <= 126; lvl++) {
            assertTrue("XP for " + lvl + " should exceed XP for " + (lvl - 1),
                    XpTable.xpForLevel(lvl) > XpTable.xpForLevel(lvl - 1));
        }
    }

    @Test
    public void levelForZeroXpIsOne() {
        assertEquals(1, XpTable.levelForXp(0));
    }

    @Test
    public void levelForMaxXpIs126() {
        assertEquals(126, XpTable.levelForXp(XpTable.maxXp()));
    }

    // --- Kill XP ---

    @Test
    public void killXpMatchesDifficultyTier() {
        assertEquals(5L,  ProgressionService.killXp(DifficultyTier.BEGINNER));
        assertEquals(10L, ProgressionService.killXp(DifficultyTier.EASY));
        assertEquals(15L, ProgressionService.killXp(DifficultyTier.MEDIUM));
        assertEquals(20L, ProgressionService.killXp(DifficultyTier.HARD));
        assertEquals(25L, ProgressionService.killXp(DifficultyTier.ELITE));
        assertEquals(30L, ProgressionService.killXp(DifficultyTier.BOSS));
    }

    @Test
    public void recordKillAwardsTierXp() {
        long before = service.getTotalXp();
        service.recordKill(npc); // "Goblin", combat level 2
        long gained = service.getTotalXp() - before;
        DifficultyTier tier = MonsterRoster.getDifficulty("Goblin", 2);
        assertEquals(ProgressionService.killXp(tier), gained);
    }

    // --- Capture XP ---

    @Test
    public void commonCaptureAwardsBaseXp() {
        CapturedCreature c = CapturedCreature.builder()
                .npcId(1).npcName("Rat").npcCombatLevel(1)
                .rarity(CreatureRarity.COMMON).build();
        collection.addCapture(c);
        service.recordCapture(c, true);
        // Kill XP for combat level 1 = max(10, 1*10) = 10; * 1.0 = 10
        assertEquals(10L, service.getTotalXp());
    }

    @Test
    public void rareCaptureAwardsHigherXpThanCommon() {
        CapturedCreature rare = CapturedCreature.builder()
                .npcId(1).npcName("Dragon").npcCombatLevel(100)
                .rarity(CreatureRarity.RARE).build();
        collection.addCapture(rare);
        service.recordCapture(rare, true);

        long rareXp = service.getTotalXp();

        service.init(new ProgressionService.ProgressionState(), collection);

        CapturedCreature common = CapturedCreature.builder()
                .npcId(1).npcName("Dragon").npcCombatLevel(100)
                .rarity(CreatureRarity.COMMON).build();
        collection.addCapture(common);
        service.recordCapture(common, true);

        long commonXp = service.getTotalXp();

        assertTrue("Rare should award more XP than Common", rareXp > commonXp);
    }

    // --- Achievements ---

    @Test
    public void firstCatchUnlocksOnFirstCapture() {
        CapturedCreature c = CapturedCreature.builder()
                .npcId(1).npcName("Rat").npcCombatLevel(1)
                .rarity(CreatureRarity.COMMON).build();
        collection.addCapture(c);

        List<Achievement> newAchievements = service.recordCapture(c, true);
        assertTrue(newAchievements.contains(Achievement.FIRST_CATCH));
    }

    @Test
    public void rareCatchAchievementUnlocksOnRareCapture() {
        CapturedCreature c = CapturedCreature.builder()
                .npcId(1).npcName("Dragon").npcCombatLevel(100)
                .rarity(CreatureRarity.RARE).build();
        collection.addCapture(c);

        List<Achievement> newAchievements = service.recordCapture(c, true);
        assertTrue(newAchievements.contains(Achievement.RARE_CATCH));
    }

    @Test
    public void firstCatchNotAwardedTwice() {
        CapturedCreature c1 = CapturedCreature.builder().npcId(1).npcName("A").npcCombatLevel(1)
                .rarity(CreatureRarity.COMMON).build();
        collection.addCapture(c1);
        service.recordCapture(c1, true);

        CapturedCreature c2 = CapturedCreature.builder().npcId(2).npcName("B").npcCombatLevel(1)
                .rarity(CreatureRarity.COMMON).build();
        collection.addCapture(c2);
        List<Achievement> second = service.recordCapture(c2, true);
        assertFalse(second.contains(Achievement.FIRST_CATCH));
    }

    // --- Level tracking ---

    @Test
    public void startAtLevel1() {
        assertEquals(1, service.getLevel());
    }

    @Test
    public void levelIncreaseWhenXpThresholdCrossed() {
        long xpNeeded = XpTable.xpForLevel(2);
        long perKill  = ProgressionService.killXp(MonsterRoster.getDifficulty("Goblin", 2));
        long kills    = xpNeeded / perKill + 1;
        for (long i = 0; i < kills; i++) service.recordKill(npc);
        assertTrue(service.getLevel() >= 2);
    }

    // --- Progression credits (#58) ---

    @Test
    public void achievementUnlockAwardsItsCreditReward() {
        long[] awarded = {0};
        service.setCreditAwarder(amount -> awarded[0] += amount);

        CapturedCreature c = CapturedCreature.builder()
                .npcId(1).npcName("Rat").npcCombatLevel(1)
                .rarity(CreatureRarity.COMMON).build();
        collection.addCapture(c);
        service.recordCapture(c, false); // no XP -> only the FIRST_CATCH achievement bounty

        assertEquals(Achievement.FIRST_CATCH.creditReward, awarded[0]);
    }

    @Test
    public void levelUpAwardsPerLevelCredits() {
        long[] awarded = {0};
        service.setCreditAwarder(amount -> awarded[0] += amount);

        long xpNeeded = XpTable.xpForLevel(2);
        long perKill  = ProgressionService.killXp(MonsterRoster.getDifficulty("Goblin", 2));
        long kills    = xpNeeded / perKill + 1;
        for (long i = 0; i < kills; i++) service.recordKill(npc);

        assertTrue(service.getLevel() >= 2);
        assertTrue("level-up should grant level x 10 credits",
                awarded[0] >= ProgressionService.levelUpCredits(2));
    }
}

