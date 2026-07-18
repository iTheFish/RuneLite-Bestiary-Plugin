package net.runelite.client.plugins.bestiary.model;

public enum DevCaptureMode {
    NORMAL("Normal"),
    FORCE_100("Force 100%"),
    FORCE_0("Force 0%");

    public final String label;
    DevCaptureMode(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
