package net.runelite.client.plugins.microbot.util.poh;

/** A failed house switch must not be treated as an unavailable teleport destination. */
public enum PohExecutionResult {
    SUCCESS, CANCELLED, SCENE_PENDING, TRANSIENT_FAILURE, DESTINATION_UNAVAILABLE, HOUSE_RECOVERY_FAILED;

    public boolean shouldBlockDestination() {
        return this == TRANSIENT_FAILURE || this == DESTINATION_UNAVAILABLE;
    }
}
