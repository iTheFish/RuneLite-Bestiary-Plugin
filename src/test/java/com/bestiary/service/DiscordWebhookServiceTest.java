package com.bestiary.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiscordWebhookServiceTest {

    @Test
    public void acceptsOfficialDiscordWebhookUrls() {
        assertTrue(DiscordWebhookService.looksLikeWebhook(
                "https://discord.com/api/webhooks/123/abc"));
        assertTrue(DiscordWebhookService.looksLikeWebhook(
                "https://discordapp.com/api/webhooks/123/abc"));
        assertTrue(DiscordWebhookService.looksLikeWebhook(
                "https://ptb.discord.com/api/webhooks/123/abc"));
        assertTrue("surrounding whitespace is trimmed",
                DiscordWebhookService.looksLikeWebhook("  https://discord.com/api/webhooks/1/x  "));
    }

    @Test
    public void rejectsNonDiscordOrMalformedUrls() {
        assertFalse("blank = disabled", DiscordWebhookService.looksLikeWebhook(""));
        assertFalse(DiscordWebhookService.looksLikeWebhook(null));
        assertFalse("http, not https", DiscordWebhookService.looksLikeWebhook(
                "http://discord.com/api/webhooks/1/x"));
        assertFalse("arbitrary host must not be a POST target",
                DiscordWebhookService.looksLikeWebhook("https://evil.com/api/webhooks/1/x"));
        assertFalse("lookalike host is not a Discord domain",
                DiscordWebhookService.looksLikeWebhook("https://discord.com.evil.com/api/webhooks/1/x"));
        assertFalse("right host, wrong path", DiscordWebhookService.looksLikeWebhook(
                "https://discord.com/api/oauth2/authorize"));
    }
}
