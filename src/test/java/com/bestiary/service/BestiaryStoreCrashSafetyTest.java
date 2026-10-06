package com.bestiary.service;

import com.google.gson.Gson;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the durable write path: saves still round-trip, and a stale temp file left behind by a
 * crash mid-save is fully replaced (no trailing bytes from the old, longer content).
 */
public class BestiaryStoreCrashSafetyTest {

    @Test
    public void staleTempFileFromACrashIsFullyReplaced() throws Exception {
        Path home = Files.createTempDirectory("bestiary-crash-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Path accounts = home.resolve(".runelite").resolve("bestiary").resolve("accounts");
            Files.createDirectories(accounts);
            // A crash mid-save left a large, garbage temp file behind.
            byte[] junk = new byte[64 * 1024];
            Arrays.fill(junk, (byte) 'x');
            Files.write(accounts.resolve("7.json.tmp"), junk);

            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.resolve(".runelite").toFile());
            store.setActiveAccount(7L, "Player");

            BestiaryStore.StoreData first = new BestiaryStore.StoreData();
            first.credits = 123;
            store.saveNow(first);
            assertEquals(123L, store.load().credits);

            BestiaryStore.StoreData second = new BestiaryStore.StoreData();
            second.credits = 456;
            store.saveNow(second);
            assertEquals(456L, store.load().credits);

            // The backup holds the previous complete save, and no temp file is left over.
            String bak = new String(Files.readAllBytes(accounts.resolve("7.json.bak")), StandardCharsets.UTF_8);
            assertTrue(bak.replaceAll("\\s", "").contains("\"credits\":123,"));
            assertFalse(Files.exists(accounts.resolve("7.json.tmp")));
        } finally {
            executor.shutdownNow();
        }
    }

    /** A crash-damaged save that still has data is kept; an all-zero one has nothing to recover. */
    @Test
    public void damagedSavesWithDataAreKeptButEmptyOnesAreNot() throws Exception {
        Path home = Files.createTempDirectory("bestiary-wipe-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Path accounts = home.resolve(".runelite").resolve("bestiary").resolve("accounts");
            Files.createDirectories(accounts);
            byte[] truncated = "{\"version\":2,\"captures\":[{\"npcName\":\"Rat\"".getBytes(StandardCharsets.UTF_8);
            Files.write(accounts.resolve("7.json"), truncated);              // cut off mid-write
            Files.write(accounts.resolve("7.json.bak"), new byte[600 * 1024]); // zero-filled by a crash

            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.resolve(".runelite").toFile());
            store.setActiveAccount(7L, "Player");
            assertEquals(0L, store.load().totalXp);
            store.saveNow(new BestiaryStore.StoreData());   // the save that used to destroy everything

            List<Path> kept = safetyCopies(accounts);
            assertEquals("only the copy with data is kept", 1, kept.size());
            assertTrue(sameBytes(kept.get(0), truncated));
        } finally {
            executor.shutdownNow();
        }
    }

    /** The level-106-to-0 scenario: save + backup both unreadable → load the best safety copy instead. */
    @Test
    public void bothFilesUnreadableFallsBackToTheBestSafetyCopy() throws Exception {
        Path home = Files.createTempDirectory("bestiary-fallback-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Path accounts = home.resolve(".runelite").resolve("bestiary").resolve("accounts");
            Files.createDirectories(accounts);
            Files.write(accounts.resolve("7.safety-20200101-000000.json"), save(5_000_000));   // the real progress
            Files.write(accounts.resolve("7.safety-20200102-000000.json"), save(1_000));       // newer, less progress
            Files.write(accounts.resolve("7.json"), new byte[4096]);
            Files.write(accounts.resolve("7.json.bak"), new byte[4096]);

            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.resolve(".runelite").toFile());
            store.setActiveAccount(7L, "Player");
            assertEquals(5_000_000L, store.load().totalXp);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void brandNewAccountNeverLoadsASafetyCopy() throws Exception {
        Path home = Files.createTempDirectory("bestiary-new-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Path accounts = home.resolve(".runelite").resolve("bestiary").resolve("accounts");
            Files.createDirectories(accounts);
            Files.write(accounts.resolve("7.safety-20200101-000000.json"), save(5_000_000));

            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.resolve(".runelite").toFile());
            store.setActiveAccount(7L, "Player");
            assertEquals("no save existed, so start fresh", 0L, store.load().totalXp);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Lots of low-progress copies (repeated crash resets) can never push out the real save. */
    @Test
    public void theBestCopyIsNeverPrunedByLowProgressChurn() throws Exception {
        Path home = Files.createTempDirectory("bestiary-pin-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Path accounts = home.resolve(".runelite").resolve("bestiary").resolve("accounts");
            Files.createDirectories(accounts);
            Path real = accounts.resolve("7.safety-20200101-000000.json");
            Files.write(real, save(5_000_000));

            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.resolve(".runelite").toFile());
            store.setActiveAccount(7L, "Player");
            for (int round = 0; round < 12; round++) {
                store.saveNow(withKills(3000 + round));       // a 10KB+ save with no XP...
                store.saveNow(new BestiaryStore.StoreData()); // ...that keeps getting reset
            }

            assertTrue("the real save survives", Files.exists(real));
            assertEquals("newest 5 + the best one", BestiaryStore.KEEP_SAFETY_COPIES + 1,
                    safetyCopies(accounts).size());
        } finally {
            executor.shutdownNow();
        }
    }

    private static byte[] save(long totalXp) {
        return ("{\"version\":" + BestiaryStore.VERSION + ",\"totalXp\":" + totalXp + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void bigSaveShrinkingByOverHalfKeepsASafetyCopy() throws Exception {
        Path home = Files.createTempDirectory("bestiary-shrink-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Path accounts = home.resolve(".runelite").resolve("bestiary").resolve("accounts");
            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.resolve(".runelite").toFile());
            store.setActiveAccount(7L, "Player");

            // Normal growth never makes safety copies.
            for (int n = 100; n <= 3000; n += 100) store.saveNow(withKills(n));
            assertTrue(safetyCopies(accounts).isEmpty());
            byte[] big = Files.readAllBytes(accounts.resolve("7.json"));
            assertTrue(big.length >= BestiaryStore.SHRINK_GUARD_MIN_BYTES);

            store.saveNow(new BestiaryStore.StoreData());   // suddenly almost empty
            List<Path> kept = safetyCopies(accounts);
            assertEquals(1, kept.size());
            assertTrue(sameBytes(kept.get(0), big));
            assertTrue("shows as a plain .json file", kept.get(0).getFileName().toString()
                    .matches("7\\.safety-\\d{8}-\\d{6}\\.json"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void onlyTheNewestFiveSafetyCopiesAreKeptPerAccount() throws Exception {
        Path home = Files.createTempDirectory("bestiary-cap-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Path accounts = home.resolve(".runelite").resolve("bestiary").resolve("accounts");
            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.resolve(".runelite").toFile());

            // Another account's safety copy must never be pruned by this one's.
            Files.createDirectories(accounts);
            Files.write(accounts.resolve("70.safety-20200101-000000.json"), new byte[] { 1 });

            // 12 mass-discard-style shrinks in a row (well within the same second).
            store.setActiveAccount(7L, "Player");
            byte[] newestBig = null;
            for (int round = 0; round < 12; round++) {
                store.saveNow(withKills(3000 + round));
                newestBig = Files.readAllBytes(accounts.resolve("7.json"));
                store.saveNow(new BestiaryStore.StoreData());
            }

            List<Path> kept = safetyCopies(accounts).stream()
                    .filter(p -> p.getFileName().toString().startsWith("7.safety-"))
                    .collect(Collectors.toList());
            assertEquals(BestiaryStore.KEEP_SAFETY_COPIES, kept.size());
            final byte[] expected = newestBig;
            assertTrue("the newest copy survives pruning", kept.stream().anyMatch(p -> sameBytes(p, expected)));
            assertTrue("other accounts' copies are untouched",
                    Files.exists(accounts.resolve("70.safety-20200101-000000.json")));
        } finally {
            executor.shutdownNow();
        }
    }

    private static BestiaryStore.StoreData withKills(int n) {
        BestiaryStore.StoreData d = new BestiaryStore.StoreData();
        for (int i = 0; i < n; i++) d.killCounts.put("Monster " + i, i);
        return d;
    }

    private static List<Path> safetyCopies(Path accounts) throws Exception {
        try (Stream<Path> s = Files.list(accounts)) {
            return s.filter(p -> p.getFileName().toString().contains(".safety-")).collect(Collectors.toList());
        }
    }

    private static boolean sameBytes(Path p, byte[] expected) {
        try {
            return Arrays.equals(Files.readAllBytes(p), expected);
        } catch (Exception e) {
            return false;
        }
    }
}
