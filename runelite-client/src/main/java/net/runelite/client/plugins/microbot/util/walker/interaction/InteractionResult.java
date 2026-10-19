package net.runelite.client.plugins.microbot.util.walker.interaction;

/** Coordinate arrival alone never completes an interaction-aware walk. */
public enum InteractionResult {
    IN_PROGRESS, SUCCEEDED, BLOCKED, CANCELLED, FAILED
}
