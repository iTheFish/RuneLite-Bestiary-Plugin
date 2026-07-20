package net.runelite.client.plugins.bestiary;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.bestiary.model.ChatNotifyMode;
import net.runelite.client.plugins.bestiary.model.DetailSectionDefault;
import net.runelite.client.plugins.bestiary.model.DevCaptureMode;
import net.runelite.client.plugins.bestiary.model.DevRarityOverride;
import net.runelite.client.plugins.bestiary.model.OverlayPos;

@ConfigGroup("bestiary")
public interface BestiaryConfig extends Config {

    @ConfigSection(
            name = "Developer Tools",
            description = "Testing options — leave off for normal play",
            position = 20,
            closedByDefault = true
    )
    String devSection = "devSection";

    @ConfigItem(
            keyName = "captureEnabled",
            name = "Capture Enabled",
            description = "Enable or disable creature capture attempts on kill",
            position = 0
    )
    default boolean captureEnabled() {
        return true;
    }

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
            keyName = "detailSectionDefault",
            name = "Detail Rarity Sections",
            description = "Whether rarity sections in the capture detail dialog start expanded or collapsed",
            position = 11
    )
    default DetailSectionDefault detailSectionDefault() {
        return DetailSectionDefault.EXPANDED;
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

    // --- Developer section ---

    @ConfigItem(
            keyName = "devForceRarity",
            name = "Force Rarity",
            description = "When set, every capture will be assigned this rarity instead of a random roll.",
            position = 21,
            section = "devSection"
    )
    default DevRarityOverride devForceRarity() {
        return DevRarityOverride.NONE;
    }

    @ConfigItem(
            keyName = "devCaptureMode",
            name = "Capture Rate Override",
            description = "Override the catch rate for testing. Normal = config-based rate.",
            position = 22,
            section = "devSection"
    )
    default DevCaptureMode devCaptureMode() {
        return DevCaptureMode.NORMAL;
    }
}