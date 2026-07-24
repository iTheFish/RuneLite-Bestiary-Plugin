package net.runelite.client.plugins.bestiary.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class WikiImageService {

    private static final String API_BASE   = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "RuneLite Bestiary Plugin 1.0";
    private static final int    THUMB_W    = 130;
    private static final int    TIMEOUT_MS = 8000;
    private static final int    BATCH_SIZE = 50;

    /**
     * Maps our in-game NPC name to the correct OSRS Wiki page title when they differ.
     * The wiki API is case-sensitive on non-first characters, so "Rock crab" won't find
     * "Rock Crab". Disambiguation pages ("Troll", "Wyvern") also need directing.
     */
    private static final java.util.Map<String, String> WIKI_IMAGE_NAMES;
    static {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        // Capitalisation differences (second word capitalised on wiki)
        m.put("Rock crab",           "Rock Crab");
        m.put("Sand crab",           "Sand Crab");
        m.put("Swamp crab",          "Swamp Crab");
        m.put("Gemstone crab",       "Gemstone Crab");
        m.put("King scorpion",       "King Scorpion");
        m.put("Twisted banshee",     "Twisted Banshee");
        m.put("Mutated bloodveld",   "Mutated Bloodveld");
        m.put("Greater nechryael",   "Greater Nechryael");
        m.put("Warped jelly",        "Warped Jelly");
        m.put("Basilisk knight",     "Basilisk Knight");
        m.put("Black knight",        "Black Knight");
        m.put("White knight",        "White Knight");
        m.put("Desert lizard",       "Desert Lizard");
        m.put("Infernal mage",       "Infernal Mage");
        m.put("Skeletal wyvern",     "Skeletal Wyvern");
        m.put("Ancient wyvern",      "Ancient Wyvern");
        m.put("Warped tortoise",     "Warped Tortoise");
        m.put("Feral vampyre",       "Feral Vampyre");
        m.put("Kalphite soldier",    "Kalphite Soldier");
        m.put("Kalphite guardian",   "Kalphite Guardian");
        m.put("Kalphite worker",     "Kalphite Worker");
        m.put("Ice warrior",         "Ice Warrior");
        m.put("Ice spider",          "Ice Spider");
        m.put("Dark warrior",        "Dark Warrior");
        // Name/structure differences
        m.put("Rockslugs",           "Rockslug");
        m.put("Fleshcrawler",        "Flesh Crawler");
        m.put("Vampyre",             "Feral Vampyre");      // race overview page, not a monster
        m.put("Warrior",             "Al Kharid warrior");  // disambiguation
        m.put("Bear",                "Grizzly bear");        // disambiguation
        m.put("Wyvern",              "Skeletal Wyvern");     // disambiguation
        m.put("Kalphite",            "Kalphite Worker");     // disambiguation
        m.put("Fossil island wyvern","Long-tailed Wyvern");  // no generic page
        m.put("Scurrius",            "Scurrius, the Rat King");
        m.put("Maiden of Sugadinti", "The Maiden of Sugadinti");
        // "Troll" has no usable representative page — omit so it gets no image gracefully
        WIKI_IMAGE_NAMES = java.util.Collections.unmodifiableMap(m);
    }

    private final Map<String, BufferedImage>  cache            = new ConcurrentHashMap<>();
    private final Set<String>                pending          = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String>                failed           = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** Callbacks registered while an image is mid-download; fired when the image lands. */
    private final Map<String, List<Runnable>> pendingCallbacks = new ConcurrentHashMap<>();

    /** Disk cache directory: ~/.runelite/bestiary/images/ — shared across all profiles. */
    private final File imageCacheDir;

    public WikiImageService() {
        imageCacheDir = new File(System.getProperty("user.home"),
                ".runelite" + File.separator + "bestiary" + File.separator + "images");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Batch-prefetches images for a list of NPC names. Loads from disk cache
     * first; only fetches from network what isn't on disk. {@code onEachLoad}
     * is called via invokeLater each time an image arrives.
     */
    public void prefetchBatch(List<String> names, Runnable onEachLoad) {
        // Load from disk synchronously (fast — just file I/O) before scheduling network
        List<String> diskMisses = new ArrayList<>();
        for (String name : names) {
            if (cache.containsKey(name) || failed.contains(name) || pending.contains(name)) continue;
            BufferedImage img = loadFromDisk(name);
            if (img != null) {
                cache.put(name, img);
                SwingUtilities.invokeLater(onEachLoad);
            } else {
                diskMisses.add(name);
            }
        }

        List<String> toFetch = diskMisses.stream()
                .filter(n -> !cache.containsKey(n) && !failed.contains(n) && pending.add(n))
                .collect(Collectors.toList());
        if (toFetch.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            Map<String, String> urlsByName = new LinkedHashMap<>();
            for (int i = 0; i < toFetch.size(); i += BATCH_SIZE) {
                List<String> chunk = toFetch.subList(i, Math.min(i + BATCH_SIZE, toFetch.size()));
                try {
                    urlsByName.putAll(fetchThumbUrlBatch(chunk));
                } catch (Exception e) {
                    log.warn("WikiImageService: batch URL fetch failed (chunk {})", i / BATCH_SIZE, e);
                }
                for (String n : chunk) {
                    if (!urlsByName.containsKey(n)) {
                        failed.add(n);
                        pending.remove(n);
                    }
                }
            }

            List<CompletableFuture<Void>> downloads = new ArrayList<>();
            for (Map.Entry<String, String> entry : urlsByName.entrySet()) {
                String npcName  = entry.getKey();
                String imageUrl = entry.getValue();
                downloads.add(CompletableFuture.runAsync(() -> {
                    try {
                        BufferedImage img = downloadImage(imageUrl);
                        if (img != null) {
                            cache.put(npcName, img);
                            saveToDisk(npcName, img);
                            SwingUtilities.invokeLater(onEachLoad);
                            firePendingCallbacks(npcName);
                        } else {
                            failed.add(npcName);
                        }
                    } catch (Exception e) {
                        failed.add(npcName);
                    } finally {
                        pending.remove(npcName);
                    }
                }));
            }
            CompletableFuture.allOf(downloads.toArray(new CompletableFuture[0])).join();
            log.debug("WikiImageService: prefetch complete — {} cached, {} failed",
                    cache.size(), failed.size());
        });
    }

    /**
     * Asynchronously fetches a single NPC image. Checks memory cache, then disk
     * cache, then network. {@code onLoad} is called via invokeLater when ready.
     */
    public void requestImage(String npcName, Runnable onLoad) {
        if (cache.containsKey(npcName)) {
            onLoad.run();
            return;
        }
        if (failed.contains(npcName)) return;

        // Check disk cache before hitting the network
        BufferedImage disk = loadFromDisk(npcName);
        if (disk != null) {
            cache.put(npcName, disk);
            onLoad.run();
            return;
        }

        if (!pending.add(npcName)) {
            pendingCallbacks.computeIfAbsent(npcName, k -> Collections.synchronizedList(new ArrayList<>())).add(onLoad);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String thumbUrl = fetchThumbUrlSingle(npcName);
                if (thumbUrl == null) { failed.add(npcName); return; }
                BufferedImage img = downloadImage(thumbUrl);
                if (img == null) { failed.add(npcName); return; }
                cache.put(npcName, img);
                saveToDisk(npcName, img);
                SwingUtilities.invokeLater(onLoad);
                firePendingCallbacks(npcName);
            } catch (Exception e) {
                log.warn("WikiImageService: failed to fetch image for '{}'", npcName, e);
                failed.add(npcName);
            } finally {
                pending.remove(npcName);
            }
        });
    }

    @Nullable
    public BufferedImage getImage(String npcName) {
        return cache.get(npcName);
    }

    // -------------------------------------------------------------------------
    // Disk cache helpers
    // -------------------------------------------------------------------------

    private File diskFile(String npcName) {
        // Sanitize name to a safe filename; spaces → underscores, drop non-safe chars
        String safe = npcName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return new File(imageCacheDir, safe + ".png");
    }

    @Nullable
    private BufferedImage loadFromDisk(String npcName) {
        File f = diskFile(npcName);
        if (!f.exists()) return null;
        try {
            return ImageIO.read(f);
        } catch (Exception e) {
            log.debug("WikiImageService: disk cache read failed for '{}': {}", npcName, e.getMessage());
            return null;
        }
    }

    private void saveToDisk(String npcName, BufferedImage img) {
        try {
            imageCacheDir.mkdirs();
            ImageIO.write(img, "PNG", diskFile(npcName));
        } catch (Exception e) {
            log.debug("WikiImageService: disk cache write failed for '{}': {}", npcName, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private Map<String, String> fetchThumbUrlBatch(List<String> names) throws Exception {
        // Translate NPC names to wiki page titles; keep a reverse map for results
        Map<String, String> lowerToName = new LinkedHashMap<>();
        List<String> queryTitles = new ArrayList<>();
        for (String n : names) {
            String wikiTitle = WIKI_IMAGE_NAMES.getOrDefault(n, n);
            queryTitles.add(wikiTitle);
            lowerToName.put(wikiTitle.toLowerCase(), n);
            lowerToName.put(n.toLowerCase(), n); // keep original as fallback
        }

        StringBuilder titlesParam = new StringBuilder();
        for (int i = 0; i < queryTitles.size(); i++) {
            if (i > 0) titlesParam.append("%7C");
            titlesParam.append(URLEncoder.encode(queryTitles.get(i), StandardCharsets.UTF_8.name()));
        }

        String urlStr = API_BASE + "?action=query&titles=" + titlesParam
                + "&prop=pageimages&piprop=thumbnail&pithumbsize=" + THUMB_W + "&format=json";

        HttpURLConnection conn = openConnection(urlStr);
        if (conn.getResponseCode() != 200) { conn.disconnect(); return Collections.emptyMap(); }

        String json;
        try (InputStream is = conn.getInputStream()) {
            json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        conn.disconnect();

        JsonObject root  = new JsonParser().parse(json).getAsJsonObject();
        JsonObject query = root.has("query") ? root.getAsJsonObject("query") : null;
        if (query == null) return Collections.emptyMap();

        if (query.has("normalized")) {
            JsonArray normalised = query.getAsJsonArray("normalized");
            for (JsonElement el : normalised) {
                JsonObject norm = el.getAsJsonObject();
                String from = norm.get("from").getAsString().toLowerCase();
                String to   = norm.get("to").getAsString().toLowerCase();
                String originalName = lowerToName.get(from);
                if (originalName != null) {
                    lowerToName.put(to, originalName);
                }
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        JsonObject pages = query.has("pages") ? query.getAsJsonObject("pages") : null;
        if (pages == null) return result;

        for (Map.Entry<String, JsonElement> entry : pages.entrySet()) {
            JsonObject page = entry.getValue().getAsJsonObject();
            if (page.has("missing")) continue;
            String pageTitle = page.get("title").getAsString();
            String origName  = lowerToName.get(pageTitle.toLowerCase());
            if (origName != null && page.has("thumbnail")) {
                result.put(origName, page.getAsJsonObject("thumbnail").get("source").getAsString());
            }
        }
        return result;
    }

    @Nullable
    private String fetchThumbUrlSingle(String npcName) throws Exception {
        Map<String, String> result = fetchThumbUrlBatch(Collections.singletonList(npcName));
        return result.get(npcName);
    }

    @Nullable
    private BufferedImage downloadImage(String imageUrl) throws Exception {
        HttpURLConnection conn = openConnection(imageUrl);
        try {
            if (conn.getResponseCode() != 200) return null;
            try (InputStream is = conn.getInputStream()) {
                return ImageIO.read(is);
            }
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection openConnection(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        return conn;
    }

    private void firePendingCallbacks(String npcName) {
        List<Runnable> callbacks = pendingCallbacks.remove(npcName);
        if (callbacks != null) {
            for (Runnable cb : callbacks) SwingUtilities.invokeLater(cb);
        }
    }
}
