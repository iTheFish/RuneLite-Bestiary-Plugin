package net.runelite.client.plugins.bestiary.util;

import net.runelite.client.plugins.bestiary.model.CreatureQuality;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * Locks in the core-identity rule: Agility (stat index 5), like Prayer, is a utility stat
 * rolled at HALF scale — not on the full combat-stat scale.
 */
public class RarityRollerTest {

    @Test
    public void utilityBandIsTighterThanCombatBand() {
        int[] combat  = RarityRoller.statBand(50, CreatureRarity.MYTHIC);
        int[] utility = RarityRoller.utilityBand(50, CreatureRarity.MYTHIC);
        assertTrue("utility hi should sit below combat hi", utility[1] < combat[1]);
        assertTrue("utility centre should sit below combat centre",
                RarityRoller.utilityCentre(50, CreatureRarity.MYTHIC)
                        < RarityRoller.statCentre(50, CreatureRarity.MYTHIC));
    }

    @Test
    public void agilityRollsOnUtilityScale() {
        int[] bases = {50, 50, 50, 50, 50, 50};
        int[] utility = RarityRoller.utilityBand(50, CreatureRarity.MYTHIC);
        int[] combat  = RarityRoller.statBand(50, CreatureRarity.MYTHIC);
        Random rng = new Random(42);
        for (int t = 0; t < 300; t++) {
            CreatureQuality q = RarityRoller.generateQuality(null, CreatureRarity.MYTHIC, bases, rng, false);
            // Agility must land in the (lower) utility band...
            assertTrue("agility " + q.agility + " within utility band",
                    q.agility >= utility[0] && q.agility <= utility[1]);
            // ...while a full combat stat (attack) uses the higher combat band.
            assertTrue("attack " + q.attack + " within combat band",
                    q.attack >= combat[0] && q.attack <= combat[1]);
        }
    }
}
