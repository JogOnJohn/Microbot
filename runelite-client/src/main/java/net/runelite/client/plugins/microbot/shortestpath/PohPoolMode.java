package net.runelite.client.plugins.microbot.shortestpath;

public enum PohPoolMode {
    ALWAYS("Always"), AUTO("Auto"), OFF("Off");
    private final String label;
    PohPoolMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
