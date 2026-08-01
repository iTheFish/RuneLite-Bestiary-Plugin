package com.bestiary.service;

import com.bestiary.model.DevCaptureMode;

import javax.inject.Singleton;

/**
 * Developer-only capture override, set from the dev-gated "Catch" control in the panel and read by
 * {@link CaptureService}. Only added to the panel in RuneLite developer mode, so for live users it
 * always stays at its default (no effect). Volatile because the panel mutates it on the EDT while
 * captures read it on the client thread.
 *
 * <p>The heavier dev cheats (seed data, +credits, rarity/shiny overrides) deliberately live only on
 * the {@code dev} branch, never in the released build.
 */
@Singleton
public class DevOptions {
    public volatile DevCaptureMode captureMode = DevCaptureMode.NORMAL;
}
