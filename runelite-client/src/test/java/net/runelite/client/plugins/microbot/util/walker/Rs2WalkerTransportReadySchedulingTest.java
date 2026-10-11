package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerTransportReadySchedulingTest {
    private static final WorldPoint ORIGIN = new WorldPoint(3035, 3345, 0);

    @Test
    public void readyLadderOriginDoesNotRequireIdleNudgeOrMovementGraceExpiry() {
        assertTrue(ready(ORIGIN, true, false, false, false, false, false));
        assertFalse(Rs2Walker.shouldRunActiveRouteIdleNudge(false, true));
        assertFalse(Rs2Walker.shouldRunActiveRouteIdleNudge(true, true));
    }

    @Test
    public void adjacentVisibleOrWrongFloorObjectsCannotGetEarlyDispatch() {
        assertFalse(ready(new WorldPoint(3036, 3345, 0), true, false, false, false, false, false));
        assertFalse(ready(new WorldPoint(3035, 3345, 1), true, false, false, false, false, false));
        assertFalse(ready(null, true, false, false, false, false, false));
        assertFalse(Rs2Walker.shouldScheduleReadyRouteTransport(null, null,
                true, false, false, false, false, false));
    }

    @Test
    public void unplannedObjectsAndNonObjectTransportsKeepExistingDispatch() {
        assertFalse(ready(ORIGIN, false, false, false, false, false, false));
    }

    @Test
    public void movementAnimationInteractionAndActiveInterimRetainOwnership() {
        assertFalse(ready(ORIGIN, true, true, false, false, false, false));
        assertFalse(ready(ORIGIN, true, false, true, false, false, false));
    }

    @Test
    public void doorAndTransportSettlingStillBlockScheduling() {
        assertFalse(ready(ORIGIN, true, false, false, true, false, false));
    }

    @Test
    public void cancellationAndHostedHouseInstancesKeepExistingPath() {
        assertFalse(ready(ORIGIN, true, false, false, false, false, true));
        assertFalse(ready(ORIGIN, true, false, false, false, true, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void readySchedulingReusesSharedEdgeCooldownAndAllowsExpiredRetry() throws Exception {
        Field field = Rs2Walker.class.getDeclaredField("recentCurrentTileTransportByEdge");
        field.setAccessible(true);
        Map<String, Long> attempts = (Map<String, Long>) field.get(null);
        Map<String, Long> previous = new HashMap<>(attempts);
        Method mark = Rs2Walker.class.getDeclaredMethod("markCurrentTileTransportAttempt",
                WorldPoint.class, WorldPoint.class);
        Method throttle = Rs2Walker.class.getDeclaredMethod("shouldThrottleCurrentTileTransportAttempt",
                WorldPoint.class, WorldPoint.class);
        mark.setAccessible(true);
        throttle.setAccessible(true);
        WorldPoint destination = new WorldPoint(3035, 3345, 1);
        try {
            attempts.clear();
            assertFalse((Boolean) throttle.invoke(null, ORIGIN, destination));
            mark.invoke(null, ORIGIN, destination);
            assertTrue((Boolean) throttle.invoke(null, ORIGIN, destination));
            assertTrue((Boolean) throttle.invoke(null, destination, ORIGIN));
            attempts.replaceAll((key, value) -> System.currentTimeMillis() - 10_000L);
            assertFalse((Boolean) throttle.invoke(null, ORIGIN, destination));
        } finally {
            attempts.clear();
            attempts.putAll(previous);
        }
    }

    private static boolean ready(WorldPoint player, boolean planned, boolean busy,
                                 boolean interim, boolean settling, boolean instance, boolean cancelled) {
        return Rs2Walker.shouldScheduleReadyRouteTransport(ORIGIN, player,
                planned, busy, interim, settling, instance, cancelled);
    }
}
