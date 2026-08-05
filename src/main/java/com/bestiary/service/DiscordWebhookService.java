package com.bestiary.service;

import com.bestiary.model.CapturedCreature;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Posts a capture alert to a user-supplied Discord channel webhook. Fires only when the user has
 * pasted a webhook URL (blank = disabled) and a capture clears the configured rarity threshold.
 *
 * <p>The card image is attached directly to the webhook as a PNG (multipart {@code files[0]}) and
 * referenced from an embed via {@code attachment://card.png}, so no external image hosting is
 * needed. The network call runs asynchronously on OkHttp's dispatcher — never on the game thread.
 */
@Slf4j
@Singleton
public class DiscordWebhookService {

    private static final MediaType PNG = MediaType.parse("image/png");

    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public DiscordWebhookService(OkHttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    /** True if {@code url} looks like a usable Discord webhook endpoint. */
    public static boolean looksLikeWebhook(String url) {
        if (url == null) return false;
        String u = url.trim();
        return u.startsWith("https://") && u.contains("/api/webhooks/");
    }

    /**
     * Sends a capture alert with the rendered card attached. No-op if the URL is blank/invalid or
     * the image is null. Safe to call from any thread — the HTTP request is dispatched async.
     */
    public void sendCaptureAlert(String webhookUrl, CapturedCreature capture, BufferedImage cardImage) {
        if (capture == null || cardImage == null) return;
        if (!looksLikeWebhook(webhookUrl)) {
            log.debug("Discord webhook skipped — URL blank or not a webhook");
            return;
        }

        final byte[] png;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(cardImage, "png", out);
            png = out.toByteArray();
        } catch (IOException e) {
            log.warn("Discord webhook: failed to encode card image", e);
            return;
        }

        String payloadJson = gson.toJson(buildPayload(capture));
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", payloadJson)
                .addFormDataPart("files[0]", "card.png", RequestBody.create(PNG, png))
                .build();

        Request request = new Request.Builder()
                .url(webhookUrl.trim())
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Discord webhook: send failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        log.warn("Discord webhook: HTTP {} — {}", r.code(), r.message());
                    }
                }
            }
        });
    }

    /** Builds the Discord message payload: one embed, rarity-coloured, with the card attached. */
    private JsonObject buildPayload(CapturedCreature capture) {
        String player = capture.playerName != null && !capture.playerName.isEmpty()
                ? capture.playerName : "Someone";
        String shinyPrefix = capture.isShiny() ? "shiny " : "";

        JsonObject embed = new JsonObject();
        embed.addProperty("title",
                (capture.isShiny() ? "✨ " : "") + capture.rarity.label + " capture!");
        embed.addProperty("description",
                "**" + player + "** captured a " + shinyPrefix + capture.rarity.label
                        + " **" + capture.npcName + "**!");
        embed.addProperty("color", capture.rarity.displayColor.getRGB() & 0xFFFFFF);

        JsonArray fields = new JsonArray();
        fields.add(field("Power Level", String.valueOf(capture.powerLevel()), true));
        if (capture.regionName != null && !capture.regionName.isEmpty()) {
            fields.add(field("Location", capture.regionName, true));
        }
        embed.add("fields", fields);

        JsonObject image = new JsonObject();
        image.addProperty("url", "attachment://card.png");
        embed.add("image", image);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "OSRS | Bestiary");
        embed.add("footer", footer);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        return payload;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }
}
