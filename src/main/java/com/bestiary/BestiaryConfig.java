package com.bestiary;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import com.bestiary.model.CaptureNotifyFilter;
import com.bestiary.model.ChatNotifyMode;
import com.bestiary.model.OverlayPos;

@ConfigGroup("bestiary")
public interface BestiaryConfig extends Config {

    @ConfigSection(
            name = "Notifications",
            description = "Chat messages for captures and level-ups",
            position = 0
    )
    String notificationsSection = "notifications";

    @ConfigSection(
            name = "Overlay & animation",
            description = "The on-screen capture overlay, capture animation and level-up banner",
            position = 1
    )
    String overlaySection = "overlay";

    @ConfigSection(
            name = "Album & images",
            description = "Album card shimmer and monster artwork",
            position = 2
    )
    String albumSection = "album";

    // --- Notifications ---

    @ConfigItem(
            keyName = "notifyOnCapture",
            name = "Notify on Capture",
            description = "Show a chat message when a creature is captured",
            section = notificationsSection,
            position = 0
    )
    default boolean notifyOnCapture() {
        return true;
    }

    @ConfigItem(
            keyName = "notifyRarityFilter",
            name = "Notify for Rarity",
            description = "Which captures produce a chat notification: all of them, or only a chosen "
                        + "rarity and above. Shinies always notify regardless of this setting.",
            section = notificationsSection,
            position = 1
    )
    default CaptureNotifyFilter notifyRarityFilter() {
        return CaptureNotifyFilter.ALL;
    }

    @ConfigItem(
            keyName = "chatNotifyMode",
            name = "Chat Notification Mode",
            description = "Verbose: one message per capture with kill# (prevents duplicates). "
                        + "Batched: accumulates captures of the same monster over a 5s lull, "
                        + "then posts a single count. Shinies always post individually.",
            section = notificationsSection,
            position = 2
    )
    default ChatNotifyMode chatNotifyMode() {
        return ChatNotifyMode.VERBOSE;
    }

    @ConfigItem(
            keyName = "notifyOnLevelUp",
            name = "Notify on Level Up",
            description = "Show a chat message when the Capture Level increases",
            section = notificationsSection,
            position = 3
    )
    default boolean notifyOnLevelUp() {
        return true;
    }

    // --- Overlay & animation ---

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Capture Overlay",
            description = "Display an in-game overlay notification when a creature is captured",
            section = overlaySection,
            position = 0
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "showCaptureAnimation",
            name = "Show Capture Animation",
            description = "Pokeball-style shake animation on each kill attempt (shows before capture result)",
            section = overlaySection,
            position = 1
    )
    default boolean showCaptureAnimation() {
        return true;
    }

    @ConfigItem(
            keyName = "animationShowMisses",
            name = "Animate Failed Catches",
            description = "Show the animation even when the capture attempt fails (requires animation enabled)",
            section = overlaySection,
            position = 2
    )
    default boolean animationShowMisses() {
        return false;
    }

    @ConfigItem(
            keyName = "showLevelUpOverlay",
            name = "Show Level-Up Banner",
            description = "Play the on-screen level-up banner when your Capture Level increases",
            section = overlaySection,
            position = 3
    )
    default boolean showLevelUpOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "overlayPosition",
            name = "Overlay Position",
            description = "Where on screen the capture overlay appears",
            section = overlaySection,
            position = 4
    )
    default OverlayPos overlayPosition() {
        return OverlayPos.TOP_CENTER;
    }

    @Range(min = 150, max = 300)
    @ConfigItem(
            keyName = "overlayWidth",
            name = "Overlay Width",
            description = "Width of the capture overlay panel in pixels (150–300)",
            section = overlaySection,
            position = 5
    )
    default int overlayWidth() {
        return 200;
    }

    @Range(min = 20, max = 100)
    @ConfigItem(
            keyName = "overlayOpacity",
            name = "Overlay Opacity",
            description = "How opaque the capture overlay's background panel is, as a percentage "
                        + "(20–100). Lower is more see-through, so it blocks less of the screen.",
            section = overlaySection,
            position = 6
    )
    default int overlayOpacity() {
        return 75;
    }

    // --- Album & images ---

    @ConfigItem(
            keyName = "autoShimmer",
            name = "Auto-shimmer EPIC+ Cards",
            description = "Automatically play the foil shimmer on Epic+ cards in the Album view every 10 seconds",
            section = albumSection,
            position = 0
    )
    default boolean autoShimmer() {
        return true;
    }

    @ConfigItem(
            keyName = "wikiImages",
            name = "Fetch NPC images from the Wiki",
            description = "<html>Downloads monster artwork from the OSRS Wiki<br>"
                        + "(oldschool.runescape.wiki) to show on cards and in the album.<br>"
                        + "Only the monster's name is requested — no account or personal<br>"
                        + "data is sent — and images are cached to disk.<br>"
                        + "<b>Off by default</b> — turn it on for the best album experience.</html>",
            section = albumSection,
            position = 1
    )
    default boolean wikiImages() {
        return false;
    }

}
