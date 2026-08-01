package com.bestiary.service;

import com.bestiary.model.DevCaptureMode;
import com.bestiary.model.DevRarityOverride;

import javax.inject.Singleton;

/**
 * Developer-only capture overrides, set from the dev-gated controls in the panel and read by
 * {@link CaptureService}. These controls are only added to the panel in RuneLite developer mode,
 * so for live users this always stays at its defaults (no effect). Fields are volatile because the
 * panel mutates them on the EDT while captures read them on the client thread.
 */
@Singleton
public class DevOptions {
    public volatile DevCaptureMode captureMode = DevCaptureMode.NORMAL;
    public volatile DevRarityOverride forceRarity = DevRarityOverride.NONE;
    public volatile boolean forceShiny = false;
}
