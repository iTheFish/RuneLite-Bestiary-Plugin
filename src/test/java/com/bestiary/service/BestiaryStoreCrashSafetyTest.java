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

    /** The level-106-to-0 scenario: save + backup both unreadable, so the account loads empty. */
    @Test
    public void unreadableSaveIsKeptBeforeAnEmptyCollectionOverwritesIt() throws Exception {
        Path home = Files.createTempDirectory("bestiary-wipe-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Path accounts = home.resolve(".runelite").resolve("bestiary").resolve("accounts");
            Files.createDirectories(accounts);
            byte[] damaged = new byte[600 * 1024];   // a 600KB save zero-filled by a crash
            Files.write(accounts.resolve("7.json"), damaged);
            Files.write(accounts.resolve("7.json.bak"), "{\"version\":".getBytes(StandardCharsets.UTF_8));

            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.resolve(".runelite").toFile());
            store.setActiveAccount(7L, "Player");
            assertEquals(0L, store.load().credits);
            store.saveNow(new BestiaryStore.StoreData());   // the save that used to destroy everything

            List<Path> kept = safetyCopies(accounts);
            assertTrue("the damaged save is kept byte-for-byte", kept.stream().anyMatch(p -> sameBytes(p, damaged)));
        } finally {
            executor.shutdownNow();
        }
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
