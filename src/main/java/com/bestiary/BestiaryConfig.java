package com.bestiary;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import com.bestiary.model.ChatNotifyMode;
import com.bestiary.model.OverlayPos;

@ConfigGroup("bestiary")
public interface BestiaryConfig extends Config {

    @ConfigItem(
            keyName = "notifyOnCapture",
            name = "Notify on Capture",
            description = "Show a chat message when a creature is captured",
            position = 3
    )
    default boolean notifyOnCapture() {
        return true;
    }

    @ConfigItem(
            keyName = "notifyRareOnly",
            name = "Notify Rare+ Only",
            description = "Only show capture notifications for Rare rarity and above",
            position = 4
    )
    default boolean notifyRareOnly() {
        return false;
    }

    @ConfigItem(
            keyName = "notifyOnLevelUp",
            name = "Notify on Level Up",
            description = "Show a chat message when the Capture Level increases",
            position = 5
    )
    default boolean notifyOnLevelUp() {
        return true;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Capture Overlay",
            description = "Display an in-game overlay notification when a creature is captured",
            position = 6
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "showCaptureAnimation",
            name = "Show Capture Animation",
            description = "Pokeball-style shake animation on each kill attempt (shows before capture result)",
            position = 7
    )
    default boolean showCaptureAnimation() {
        return false;
    }

    @ConfigItem(
            keyName = "animationShowMisses",
            name = "Animate Failed Catches",
            description = "Show the animation even when the capture attempt fails (requires animation enabled)",
            position = 8
    )
    default boolean animationShowMisses() {
        return false;
    }

    @ConfigItem(
            keyName = "captureXpEnabled",
            name = "Capture XP Enabled",
            description = "Award bonus XP on capture in addition to kill XP",
            position = 9
    )
    default boolean captureXpEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "chatNotifyMode",
            name = "Chat Notification Mode",
            description = "Verbose: one message per capture with kill# (prevents duplicates). "
                        + "Batched: accumulates captures over 30s then posts a count.",
            position = 10
    )
    default ChatNotifyMode chatNotifyMode() {
        return ChatNotifyMode.VERBOSE;
    }

    @ConfigItem(
            keyName = "overlayPosition",
            name = "Overlay Position",
            description = "Where on screen the capture overlay appears",
            position = 13
    )
    default OverlayPos overlayPosition() {
        return OverlayPos.TOP_CENTER;
    }

    @Range(min = 150, max = 300)
    @ConfigItem(
            keyName = "overlayWidth",
            name = "Overlay Width",
            description = "Width of the capture overlay panel in pixels (150–300)",
            position = 14
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
            position = 15
    )
    default int overlayOpacity() {
        return 75;
    }

    @ConfigItem(
            keyName = "autoShimmer",
            name = "Auto-shimmer EPIC+ Cards",
            description = "Automatically play the foil shimmer on Epic+ cards in the Album view every 10 seconds",
            position = 16
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
            position = 17
    )
    default boolean wikiImages() {
        return false;
    }

}