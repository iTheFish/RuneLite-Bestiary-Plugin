package net.runelite.client.plugins.bestiary.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
public class WikiImageService {

    private static final String API_BASE   = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "RuneLite Bestiary Plugin 1.0";
    private static final int    THUMB_W    = 130;
    private static final int    TIMEOUT_MS = 8000;

    private final Map<String, BufferedImage> cache   = new ConcurrentHashMap<>();
    private final Set<String>               pending  = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String>               failed   = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Asynchronously fetches the NPC sprite from the OSRS Wiki. If already cached,
     * {@code onLoad} runs immediately on the EDT. Otherwise it fetches in the background
     * and calls {@code onLoad} via {@code SwingUtilities.invokeLater} when ready.
     */
    public void requestImage(String npcName, Runnable onLoad) {
        if (cache.containsKey(npcName)) {
            onLoad.run();
            return;
        }
        if (failed.contains(npcName))  return;
        if (!pending.add(npcName))     return; // already in flight

        CompletableFuture.runAsync(() -> {
            try {
                String thumbUrl = fetchThumbUrl(npcName);
                if (thumbUrl == null) {
                    failed.add(npcName);
                    return;
                }
                BufferedImage img = downloadImage(thumbUrl);
                if (img == null) {
                    failed.add(npcName);
                    return;
                }
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

    @Nullable
    private String fetchThumbUrl(String npcName) throws Exception {
        String encoded = URLEncoder.encode(npcName, StandardCharsets.UTF_8.name());
        String urlStr  = API_BASE + "?action=query&titles=" + encoded
                       + "&prop=pageimages&piprop=thumbnail&pithumbsize=" + THUMB_W + "&format=json";

        HttpURLConnection conn = openConnection(urlStr);
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            return null;
        }

        String json;
        try (InputStream is = conn.getInputStream()) {
            json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        conn.disconnect();

        JsonObject root  = new JsonParser().parse(json).getAsJsonObject();
        JsonObject query = root.getAsJsonObject("query");
        if (query == null) return null;
        JsonObject pages = query.getAsJsonObject("pages");
        if (pages == null) return null;

        for (Map.Entry<String, JsonElement> entry : pages.entrySet()) {
            JsonObject page = entry.getValue().getAsJsonObject();
            if (page.has("thumbnail")) {
                return page.getAsJsonObject("thumbnail").get("source").getAsString();
            }
        }
        return null;
    }

    @Nullable
    private BufferedImage downloadImage(String imageUrl) throws Exception {
        HttpURLConnection conn = openConnection(imageUrl);
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            return null;
        }
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
