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
    private static final int    BATCH_SIZE = 50; // MediaWiki API max titles per request

    private final Map<String, BufferedImage> cache   = new ConcurrentHashMap<>();
    private final Set<String>               pending  = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String>               failed   = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Batch-prefetches images for a list of NPC names. Makes ceil(N/50) API
     * calls to resolve thumbnail URLs, then downloads all images concurrently.
     * {@code onEachLoad} is called via invokeLater each time an image arrives.
     */
    public void prefetchBatch(List<String> names, Runnable onEachLoad) {
        List<String> toFetch = names.stream()
                .filter(n -> !cache.containsKey(n) && !failed.contains(n) && pending.add(n))
                .collect(Collectors.toList());
        if (toFetch.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            // Resolve thumbnail URLs in batches of BATCH_SIZE
            Map<String, String> urlsByName = new LinkedHashMap<>();
            for (int i = 0; i < toFetch.size(); i += BATCH_SIZE) {
                List<String> chunk = toFetch.subList(i, Math.min(i + BATCH_SIZE, toFetch.size()));
                try {
                    urlsByName.putAll(fetchThumbUrlBatch(chunk));
                } catch (Exception e) {
                    log.warn("WikiImageService: batch URL fetch failed (chunk {})", i / BATCH_SIZE, e);
                }
                // Mark names with no URL as failed
                for (String n : chunk) {
                    if (!urlsByName.containsKey(n)) {
                        failed.add(n);
                        pending.remove(n);
                    }
                }
            }

            // Download images concurrently (one CF per image, CDN handles load fine)
            List<CompletableFuture<Void>> downloads = new ArrayList<>();
            for (Map.Entry<String, String> entry : urlsByName.entrySet()) {
                String npcName  = entry.getKey();
                String imageUrl = entry.getValue();
                downloads.add(CompletableFuture.runAsync(() -> {
                    try {
                        BufferedImage img = downloadImage(imageUrl);
                        if (img != null) {
                            cache.put(npcName, img);
                            SwingUtilities.invokeLater(onEachLoad);
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
            // Wait for all downloads (so logs show completion clearly)
            CompletableFuture.allOf(downloads.toArray(new CompletableFuture[0])).join();
            log.debug("WikiImageService: prefetch complete — {} cached, {} failed",
                    cache.size(), failed.size());
        });
    }

    /**
     * Asynchronously fetches a single NPC image. If already cached, {@code onLoad}
     * runs immediately on the EDT. Otherwise fetches in background and calls
     * {@code onLoad} via invokeLater when ready.
     */
    public void requestImage(String npcName, Runnable onLoad) {
        if (cache.containsKey(npcName)) {
            onLoad.run();
            return;
        }
        if (failed.contains(npcName))  return;
        if (!pending.add(npcName))     return;

        CompletableFuture.runAsync(() -> {
            try {
                String thumbUrl = fetchThumbUrlSingle(npcName);
                if (thumbUrl == null) { failed.add(npcName); return; }
                BufferedImage img = downloadImage(thumbUrl);
                if (img == null)     { failed.add(npcName); return; }
                cache.put(npcName, img);
                SwingUtilities.invokeLater(onLoad);
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
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Resolves thumbnail URLs for up to 50 NPC names in a single API request.
     * Handles MediaWiki title normalisation so "Giant rat" matches page "Giant rat".
     *
     * @return map of npcName → thumbnail URL (only for names that have a wiki image)
     */
    private Map<String, String> fetchThumbUrlBatch(List<String> names) throws Exception {
        // Case-insensitive lookup: lowercaseName → original npcName
        Map<String, String> lowerToName = new LinkedHashMap<>();
        for (String n : names) {
            lowerToName.put(n.toLowerCase(), n);
        }

        // Build pipe-separated titles param (%7C = URL-encoded |)
        StringBuilder titlesParam = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) titlesParam.append("%7C");
            titlesParam.append(URLEncoder.encode(names.get(i), StandardCharsets.UTF_8.name()));
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

        // Apply normalisation mappings so "giant_rat" → "Giant rat" still resolves
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

        // Extract thumbnail URLs from page objects
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

    /** Single-name variant of the URL lookup (used by requestImage). */
    @Nullable
    private String fetchThumbUrlSingle(String npcName) throws Exception {
        Map<String, String> result = fetchThumbUrlBatch(Collections.singletonList(npcName));
        return result.get(npcName);
    }

    @Nullable
    private BufferedImage downloadImage(String imageUrl) throws Exception {
        HttpURLConnection conn = openConnection(imageUrl);
        if (conn.getResponseCode() != 200) { conn.disconnect(); return null; }
        BufferedImage img;
        try (InputStream is = conn.getInputStream()) {
            img = ImageIO.read(is);
        }
        conn.disconnect();
        return img;
    }

    private HttpURLConnection openConnection(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        return conn;
    }
}
