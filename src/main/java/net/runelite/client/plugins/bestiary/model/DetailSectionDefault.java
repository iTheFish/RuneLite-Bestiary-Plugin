package net.runelite.client.plugins.bestiary.model;

public enum DetailSectionDefault {
    EXPANDED("Expanded"),
    COLLAPSED("Collapsed");

    private final String label;

    DetailSectionDefault(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
