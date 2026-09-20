package com.bestiary.service;

import com.bestiary.BestiaryConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class WikiImageService {

    private static final String API_BASE   = "https://oldschool.runescape.wiki/api.php";
    /** The ONLY host this plugin downloads from. Image URLs arrive in the wiki API response,
     *  so each is validated against this fixed host before being requested, keeping the
     *  plugin's network destinations statically verifiable (plugin-hub requirement). */
    private static final String WIKI_HOST  = "oldschool.runescape.wiki";
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
        m.put("Dark warrior",        "Dark Warrior");
        // NOTE: "Ice warrior"/"Ice spider" are already the canonical wiki titles (lowercase
        // second word) — no mapping needed; forcing "Ice Warrior"/"Ice Spider" was a redirect
        // that pageimages can't resolve, so those were left out on purpose.
        // Name/structure differences
        m.put("Vampyre",             "Feral Vampyre");      // race overview page, not a monster
        // "Warrior" (in-game) = the Fremennik warriors of Rellekka, NOT the Al Kharid warriors.
        // Al Kharid warriors are a separate roster monster ("Al Kharid warrior") whose page title
        // matches its name, so it needs no mapping here.
        m.put("Warrior",             "Warrior (Rellekka)"); // disambiguation
        m.put("Wyvern",              "Skeletal Wyvern");     // disambiguation
        m.put("Kalphite",            "Kalphite Worker");     // disambiguation
        m.put("Maiden of Sugadinti", "The Maiden of Sugadinti");
        // Redirect targets / case fixes — pageimages doesn't follow redirects, so map to canonical.
        m.put("Hill giant",          "Hill Giant");
        m.put("Crazy Archaeologist", "Crazy archaeologist");
        m.put("Deranged Archaeologist","Deranged archaeologist");
        m.put("Hueycoatl",           "The Hueycoatl");
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

    /**
     * Bump this whenever a {@link #WIKI_IMAGE_NAMES} mapping changes so previously downloaded
     * (now-wrong) art is purged from disk and re-fetched. Without this, the disk cache — checked
     * before the network — keeps serving the old image forever.
     *   gen 2: "Warrior" repointed from the Al Kharid warrior to the Fremennik warrior (Rellekka).
     */
    private static final int CACHE_GENERATION = 2;
    /** Disk-cached images (by NPC name) to delete when {@link #CACHE_GENERATION} advances. */
    private static final List<String> STALE_ON_UPGRADE = Collections.singletonList("Warrior");

    private final OkHttpClient httpClient;
    private final BestiaryConfig config;
    /** RuneLite's shared executor — client-owned, so we submit tasks but never shut it down. */
    private final ScheduledExecutorService executor;

    @Inject
    public WikiImageService(OkHttpClient httpClient, BestiaryConfig config, ScheduledExecutorService executor) {
        // Reuse RuneLite's shared OkHttp client, with our shorter timeouts.
        this.httpClient = httpClient.newBuilder()
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
        this.config = config;
        this.executor = executor;
        imageCacheDir = new File(System.getProperty("user.home"),
                ".runelite" + File.separator + "bestiary" + File.separator + "images");
        purgeStaleImages();
    }

    /**
     * One-time-per-upgrade cleanup: if the stored cache generation is older than
     * {@link #CACHE_GENERATION}, delete the images whose wiki mapping has since changed so they
     * re-download with the correct art, then record the new generation. A tiny marker file in the
     * cache dir tracks the last-seen generation, so this runs at most once per user per bump.
     */
    private void purgeStaleImages() {
        File marker = new File(imageCacheDir, ".cache-generation");
        int stored = 0;
        try {
            if (marker.isFile()) {
                stored = Integer.parseInt(
                        new String(java.nio.file.Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim());
            }
        } catch (Exception e) {
            stored = 0;   // unreadable/corrupt marker → treat as oldest, re-run the purge
        }
        if (stored >= CACHE_GENERATION) return;

        for (String name : STALE_ON_UPGRADE) {
            File f = diskFile(name);
            if (f.isFile() && !f.delete()) {
                log.debug("WikiImageService: could not delete stale cached image '{}'", f);
            }
        }
        try {
            imageCacheDir.mkdirs();
            java.nio.file.Files.write(marker.toPath(),
                    String.valueOf(CACHE_GENERATION).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("WikiImageService: could not write cache-generation marker: {}", e.getMessage());
        }
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

        // Respect the user's wiki-fetch toggle — no network when disabled.
        if (!config.wikiImages()) {
            toFetch.forEach(pending::remove);
            return;
        }

        // One short background task on RuneLite's shared executor resolves the thumbnail URLs (a few
        // batched calls), then hands the actual image downloads to OkHttp's async dispatcher via
        // enqueue(). The dispatcher runs several downloads concurrently (capped per-host) on the
        // shared client's own threads — so we get parallelism back without owning any threads, using
        // the common ForkJoinPool, or blocking one of the shared executor's threads for the whole run.
        executor.execute(() -> {
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

            for (Map.Entry<String, String> entry : urlsByName.entrySet()) {
                downloadImageAsync(entry.getKey(), entry.getValue(), onEachLoad);
            }
        });
    }

    /**
     * Downloads one image via OkHttp's async dispatcher (RuneLite's shared client). Concurrency and
     * per-host limits are managed by the dispatcher, so many of these can be in flight at once without
     * the plugin creating threads or holding a shared executor thread. The callback lands the image
     * into the cache and repaints as each one arrives; {@code pending} is always cleared.
     */
    private void downloadImageAsync(String npcName, String imageUrl, Runnable onEachLoad) {
        if (!isAllowedImageUrl(imageUrl)) {   // never call a host we didn't hardcode
            failed.add(npcName);
            pending.remove(npcName);
            return;
        }
        Request request = new Request.Builder().url(imageUrl).header("User-Agent", USER_AGENT).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                failed.add(npcName);
                pending.remove(npcName);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response resp = response) {
                    BufferedImage img = null;
                    if (resp.isSuccessful() && resp.body() != null) {
                        try (InputStream is = resp.body().byteStream()) {
                            img = ImageIO.read(is);
                        }
                    }
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
            }
        });
    }

    /**
     * Asynchronously fetches a single NPC image. Checks memory cache, then disk
     * cache, then network. {@code onLoad} is called via invokeLater when ready.
     */
    /** Whether wiki images are enabled by config. When false, cards fall back to placeholders. */
    public boolean isEnabled() {
        return config.wikiImages();
    }

    public void requestImage(String npcName, Runnable onLoad) {
        if (cache.containsKey(npcName)) {
            onLoad.run();
            return;
        }
        if (failed.contains(npcName)) return;

        // Disk cache is always available — even when the fetch toggle is off — so previously
        // synced images keep showing after a player opts out.
        BufferedImage disk = loadFromDisk(npcName);
        if (disk != null) {
            cache.put(npcName, disk);
            onLoad.run();
            return;
        }

        // Anything not already on disk requires a network fetch — only when the user has opted in.
        if (!config.wikiImages()) return;

        if (!pending.add(npcName)) {
            pendingCallbacks.computeIfAbsent(npcName, k -> Collections.synchronizedList(new ArrayList<>())).add(onLoad);
            return;
        }

        executor.execute(() -> {
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
        // Always show images we already have — the fetch toggle governs NETWORK access, not display.
        // Disk-cached art is local data, so a player can sync once, opt out, and keep their images
        // (and opt back in later to fetch newly added monsters).
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

    /**
     * True only for https URLs on the hardcoded wiki host. Thumbnail URLs are read from the
     * wiki API response, so every one passes through here before any request is made — the
     * plugin can therefore only ever contact {@link #WIKI_HOST}, verifiable by static review.
     */
    private static boolean isAllowedImageUrl(String url) {
        HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
        return parsed != null && "https".equals(parsed.scheme()) && WIKI_HOST.equals(parsed.host());
    }

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

        String json;
        Request request = new Request.Builder().url(urlStr).header("User-Agent", USER_AGENT).build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return Collections.emptyMap();
            json = resp.body().string();
        }

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
                String source = page.getAsJsonObject("thumbnail").get("source").getAsString();
                if (isAllowedImageUrl(source)) {
                    result.put(origName, source);
                } else {
                    log.warn("WikiImageService: ignoring off-host image URL for '{}': {}", origName, source);
                }
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
        if (!isAllowedImageUrl(imageUrl)) return null;   // never call a host we didn't hardcode
        Request request = new Request.Builder().url(imageUrl).header("User-Agent", USER_AGENT).build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            try (InputStream is = resp.body().byteStream()) {
                return ImageIO.read(is);
            }
        }
    }

    private void firePendingCallbacks(String npcName) {
        List<Runnable> callbacks = pendingCallbacks.remove(npcName);
        if (callbacks != null) {
            for (Runnable cb : callbacks) SwingUtilities.invokeLater(cb);
        }
    }
}
