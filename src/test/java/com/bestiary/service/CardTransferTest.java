package com.bestiary.service;

import com.bestiary.model.CapturedCreature;
import com.google.gson.Gson;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end guard for intra-profile card transfer (#50): a card moves out of the active account's
 * file into the target account's file, currentOwner follows while originalOwner is preserved, and
 * you can't send to yourself.
 */
public class CardTransferTest {

    @Test
    public void transferMovesCardBetweenAccountFiles() throws Exception {
        Path tmpHome = Files.createTempDirectory("bestiary-transfer-home");
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", tmpHome.toString());
        try {
            BestiaryStore store = new BestiaryStore(new Gson());
            BestiaryDataService ds = new BestiaryDataService(new ProgressionService(), store);

            // Register the Alt account, then play as Main and catch two cards.
            ds.switchAccount(222L, "AltRSN");
            ds.switchAccount(111L, "MainRSN");
            CapturedCreature keep = CapturedCreature.builder().npcName("Goblin").playerName("MainRSN").build();
            CapturedCreature send = CapturedCreature.builder().npcName("Cow").playerName("MainRSN").build();
            ds.addCapture(keep);
            ds.addCapture(send);
            assertEquals(2, ds.getCollection().totalCaptures());
            assertEquals("both catches count toward lifetime", 2L, ds.getCollection().lifetimeCaptures);

            // Alt is the only other known account.
            List<BestiaryStore.AccountRef> others = ds.listOtherAccounts();
            assertEquals(1, others.size());
            assertEquals(222L, others.get(0).hash);

            // Send one card to Alt.
            int moved = ds.transferCards(Collections.singletonList(send), 222L, "AltRSN");
            assertEquals(1, moved);

            // Left Main's collection…
            assertEquals(1, ds.getCollection().totalCaptures());
            assertTrue(ds.getCollection().creatures.contains(keep));
            assertFalse(ds.getCollection().creatures.contains(send));
            // …but "Caught" (lifetime) is unchanged — sending a card away isn't un-catching it.
            assertEquals(2L, ds.getCollection().lifetimeCaptures);
            assertEquals(1L, ds.getCollection().lifetimeCardsSent);

            // …ownership moved but provenance kept…
            assertEquals("AltRSN",  send.currentOwner);
            assertEquals("MainRSN", send.originalOwner);

            // …and landed in Alt's file.
            BestiaryStore.StoreData alt = store.readAccount(222L);
            assertEquals(1, alt.captures.size());
            assertEquals("Cow",     alt.captures.get(0).npcName);
            assertEquals("AltRSN",  alt.captures.get(0).currentOwner);
            assertEquals("MainRSN", alt.captures.get(0).originalOwner);

            // Switching to Alt loads the received card — held, tagged traded-in, but NOT "caught".
            ds.switchAccount(222L, "AltRSN");
            assertEquals(1, ds.getCollection().totalCaptures());
            assertEquals("received cards don't count as caught", 0L, ds.getCollection().lifetimeCaptures);
            assertEquals(1L, ds.getCollection().tradedInCount());

            // Can't send to the account you're on.
            assertEquals(0, ds.transferCards(
                    ds.getCollection().creatures, 222L, "AltRSN"));

            store.close();
        } finally {
            if (oldHome != null) System.setProperty("user.home", oldHome);
        }
    }
}
