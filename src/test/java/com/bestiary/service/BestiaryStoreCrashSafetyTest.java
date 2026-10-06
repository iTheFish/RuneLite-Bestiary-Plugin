package com.bestiary.service;

import com.google.gson.Gson;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
}
