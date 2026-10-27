package com.bestiary.service;

import com.google.gson.Gson;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Guards the non-blocking capture save: saveSoon writes promptly on the executor (not the caller's
 * thread), and a background write that lands after a newer synchronous one never overwrites it.
 */
public class BestiaryStoreSaveSoonTest {

    private static BestiaryStore.StoreData data(long credits) {
        BestiaryStore.StoreData d = new BestiaryStore.StoreData();
        d.version = BestiaryStore.VERSION;
        d.credits = credits;
        return d;
    }

    @Test
    public void saveSoonWritesOnTheExecutorWithoutDebounce() throws Exception {
        Path home = Files.createTempDirectory("bestiary-savesoon-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.toFile());
            store.setActiveAccount(7L, "Player");

            // Hold the executor so we can see the write hasn't happened on the caller's thread.
            CountDownLatch release = new CountDownLatch(1);
            executor.execute(() -> { try { release.await(); } catch (InterruptedException ignored) { } });

            store.saveSoon(data(42));
            assertEquals("not written synchronously", 0L, store.load().credits);

            release.countDown();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);   // drain: the 0-delay flush ran first
            assertEquals(42L, store.load().credits);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void aStaleBackgroundWriteNeverOverwritesANewerSave() throws Exception {
        Path home = Files.createTempDirectory("bestiary-stale-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            BestiaryStore store = new BestiaryStore(new Gson(), executor, home.toFile());
            store.setActiveAccount(7L, "Player");

            CountDownLatch release = new CountDownLatch(1);
            executor.execute(() -> { try { release.await(); } catch (InterruptedException ignored) { } });

            store.save(data(1));       // debounced, older
            store.saveNow(data(2));    // newer, written synchronously; cancels the buffered one
            store.saveSoon(data(3));   // newest, queued behind the held executor
            store.saveNow(data(4));    // newer still — the queued saveSoon must not clobber it
            release.countDown();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

            assertEquals(4L, store.load().credits);
            assertTrue(Files.exists(home.resolve("bestiary/accounts/7.json")));
        } finally {
            executor.shutdownNow();
        }
    }
}
