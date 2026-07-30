package com.bestiary.service;

import com.google.gson.Gson;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the per-account storage (#47): each accountHash gets its own isolated file, nothing is
 * written while logged out, and the pre-#47 global file is archived (never loaded) on first run.
 */
public class BestiaryStorePerAccountTest {

    @Test
    public void perAccountIsolationAndLegacyArchive() throws Exception {
        Path tmpHome = Files.createTempDirectory("bestiary-test-home");
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", tmpHome.toString());
        try {
            Path bestiaryDir = tmpHome.resolve(".runelite").resolve("bestiary");
            Files.createDirectories(bestiaryDir);
            // A legacy global collection that must be archived (not loaded) when the store starts.
            Files.write(bestiaryDir.resolve("bestiary.json"), "{\"version\":1,\"credits\":9999}".getBytes());

            BestiaryStore store = new BestiaryStore(new Gson());

            // Legacy file moved aside to a dated archive; original gone.
            assertFalse("legacy global file must be archived",
                    Files.exists(bestiaryDir.resolve("bestiary.json")));
            try (Stream<Path> s = Files.list(bestiaryDir)) {
                assertTrue("a bestiary.legacy-* archive must exist",
                        s.anyMatch(p -> p.getFileName().toString().startsWith("bestiary.legacy-")));
            }

            // Logged out: load is empty and saves are dropped.
            assertFalse(store.hasActiveAccount());
            assertTrue(store.load().captures.isEmpty());
            BestiaryStore.StoreData dropped = new BestiaryStore.StoreData();
            dropped.credits = 500;
            store.saveNow(dropped);
            assertTrue("save while logged out must not create any account file",
                    !Files.exists(bestiaryDir.resolve("accounts")) ||
                    isEmptyDir(bestiaryDir.resolve("accounts")));

            // Account A saves and reloads its own data.
            store.setActiveAccount(111L, "AccountA");
            assertTrue(store.hasActiveAccount());
            BestiaryStore.StoreData a = new BestiaryStore.StoreData();
            a.credits = 111;
            store.saveNow(a);
            assertEquals(111L, store.load().credits);

            // Account B is a fresh, independent collection.
            store.setActiveAccount(222L, "AccountB");
            assertEquals("switching to a new account loads an empty collection", 0L, store.load().credits);
            BestiaryStore.StoreData b = new BestiaryStore.StoreData();
            b.credits = 222;
            store.saveNow(b);
            assertEquals(222L, store.load().credits);

            // Switching back to A still has A's data (never crossed into B).
            store.setActiveAccount(111L, "AccountA");
            assertEquals(111L, store.load().credits);

            // Registry recorded both accounts for the future switcher (#48).
            assertTrue(Files.exists(bestiaryDir.resolve("accounts").resolve("index.json")));

            store.close();
        } finally {
            if (oldHome != null) System.setProperty("user.home", oldHome);
        }
    }

    private static boolean isEmptyDir(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            // Only the registry index (written on setActiveAccount) may exist — no <hash>.json yet.
            return s.noneMatch(p -> p.getFileName().toString().endsWith(".json")
                    && !p.getFileName().toString().equals("index.json"));
        }
    }
}
