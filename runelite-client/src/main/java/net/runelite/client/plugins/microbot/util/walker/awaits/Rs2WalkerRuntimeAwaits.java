package net.runelite.client.plugins.microbot.util.walker.awaits;

import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.function.BooleanSupplier;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public final class Rs2WalkerRuntimeAwaits {
    private static final int PATHFINDER_POLL_MS = 25;
    private Rs2WalkerRuntimeAwaits() {
    }

    public static boolean awaitPathfinderDone(Pathfinder pathfinder, int timeoutMs) {
        if (pathfinder == null) {
            return false;
        }
        // Search completion is an in-memory flag, not a game action. A shorter poll
        // releases the ready route without changing movement or interaction timing.
        return sleepUntilTrue(pathfinder::isDone, PATHFINDER_POLL_MS, timeoutMs);
    }

    public static boolean awaitCondition(BooleanSupplier condition, int pollMs, int timeoutMs) {
        if (condition == null) {
            return false;
        }
        return sleepUntilTrue(condition, pollMs, timeoutMs);
    }

    public static boolean awaitMovementSettled(int pollMs, int timeoutMs) {
        return sleepUntilTrue(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), pollMs, timeoutMs);
    }
}
