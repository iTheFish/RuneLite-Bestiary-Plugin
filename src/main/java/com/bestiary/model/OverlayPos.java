package com.bestiary.model;

public enum OverlayPos {
    TOP_LEFT("Top Left"),
    TOP_CENTER("Top Center"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right");

    private final String label;

    OverlayPos(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
