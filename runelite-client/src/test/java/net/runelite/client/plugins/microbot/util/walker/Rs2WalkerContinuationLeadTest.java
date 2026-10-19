package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerContinuationLeadTest {
    private final WorldPoint player = new WorldPoint(3200, 3200, 0);

    @Test
    public void preparesAtRunningLeadBeforeFiveTileCheckpointClears() {
        assertTrue(Rs2Walker.shouldPrepareRouteContinuation(new WorldPoint(3208, 3200, 0),
                player, true, Rs2Walker.interimPreclickTiles(true)));
    }

    @Test
    public void walkingUsesShorterLead() {
        assertFalse(Rs2Walker.shouldPrepareRouteContinuation(new WorldPoint(3207, 3200, 0),
                player, true, Rs2Walker.interimPreclickTiles(false)));
        assertTrue(Rs2Walker.shouldPrepareRouteContinuation(new WorldPoint(3206, 3200, 0),
                player, true, Rs2Walker.interimPreclickTiles(false)));
    }

    @Test
    public void stoppedOrWrongPlaneIsNotAnEarlyContinuation() {
        assertFalse(Rs2Walker.shouldPrepareRouteContinuation(player, player, false, 8));
        assertFalse(Rs2Walker.shouldPrepareRouteContinuation(new WorldPoint(3200, 3200, 1), player, true, 8));
        assertFalse(Rs2Walker.shouldPrepareRouteContinuation(null, player, true, 8));
        assertFalse(Rs2Walker.shouldPrepareRouteContinuation(player, null, true, 8));
    }

    @Test
    public void stationaryCheckpointClearMustNotStarveClosedDoorRecovery() {
        assertFalse(Rs2Walker.shouldAttemptRouteContinuation(true, false, false));
        assertFalse(Rs2Walker.shouldAttemptRouteContinuation(true, true, false));
        assertTrue(Rs2Walker.shouldAttemptRouteContinuation(true, false, true));
        assertTrue(Rs2Walker.shouldAttemptRouteContinuation(false, true, true));
    }

    @Test
    public void continuationCannotUndoCollisionRejectionWithSmoothedFallback() {
        assertTrue(Rs2Walker.shouldRejectRouteMovementFallback(null, false));
        assertFalse(Rs2Walker.shouldRejectRouteMovementFallback(new WorldPoint(3208, 3200, 0), false));
        assertFalse(Rs2Walker.shouldRejectRouteMovementFallback(null, true));
    }
}
