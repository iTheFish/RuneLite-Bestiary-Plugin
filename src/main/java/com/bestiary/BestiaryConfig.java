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
            description = "Chat messages for captures, achievements and level-ups",
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
            description = "<html>Which captures produce a chat notification:<br>"
                        + "all of them, or only a chosen rarity and above.<br>"
                        + "Shinies always notify regardless.</html>",
            section = notificationsSection,
            position = 1
    )
    default CaptureNotifyFilter notifyRarityFilter() {
        return CaptureNotifyFilter.ALL;
    }

    @ConfigItem(
            keyName = "chatNotifyMode",
            name = "Chat Notification Mode",
            description = "<html>Verbose: one message per capture, with the kill<br>"
                        + "number so identical captures aren't dropped.<br>"
                        + "Batched: rolls up captures of the same monster<br>"
                        + "over a 9s lull into one count message.<br>"
                        + "Shinies always post individually either way.</html>",
            section = notificationsSection,
            position = 2
    )
    default ChatNotifyMode chatNotifyMode() {
        return ChatNotifyMode.VERBOSE;
    }

    @ConfigItem(
            keyName = "notifyOnAchievement",
            name = "Notify on Achievement",
            description = "<html>Show a chat message when you unlock<br>"
                        + "an achievement</html>",
            section = notificationsSection,
            position = 3
    )
    default boolean notifyOnAchievement() {
        return true;
    }

    @ConfigItem(
            keyName = "notifyOnLevelUp",
            name = "Notify on Level Up",
            description = "<html>Show a chat message when the Capture<br>"
                        + "Level increases</html>",
            section = notificationsSection,
            position = 4
    )
    default boolean notifyOnLevelUp() {
        return true;
    }

    // --- Overlay & animation ---

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Capture Overlay",
            description = "<html>Display an in-game overlay notification<br>"
                        + "when a creature is captured</html>",
            section = overlaySection,
            position = 0
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "showCaptureAnimation",
            name = "Show Capture Animation",
            description = "<html>Play the capture animation on each kill<br>"
                        + "attempt (shows before the capture result)</html>",
            section = overlaySection,
            position = 1
    )
    default boolean showCaptureAnimation() {
        return true;
    }

    @ConfigItem(
            keyName = "animationShowMisses",
            name = "Animate Failed Catches",
            description = "<html>Show the animation even when the capture<br>"
                        + "attempt fails (requires animation enabled)</html>",
            section = overlaySection,
            position = 2
    )
    default boolean animationShowMisses() {
        return false;
    }

    @ConfigItem(
            keyName = "showLevelUpOverlay",
            name = "Show Level-Up Banner",
            description = "<html>Play the on-screen level-up banner when<br>"
                        + "your Capture Level increases</html>",
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
            description = "<html>How opaque the capture overlay's background is,<br>"
                        + "as a percentage (20–100). Lower is more<br>"
                        + "see-through, so it blocks less of the screen.</html>",
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
            description = "<html>Automatically play the foil shimmer on Epic+<br>"
                        + "cards in the Album view every 10 seconds</html>",
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
