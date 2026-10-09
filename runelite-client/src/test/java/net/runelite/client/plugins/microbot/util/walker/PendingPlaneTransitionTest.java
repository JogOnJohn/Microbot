package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class PendingPlaneTransitionTest {
    private final WorldPoint origin = new WorldPoint(3000, 3300, 0);
    private final WorldPoint landing = new WorldPoint(3000, 3300, 1);

    @Test public void rejectedClickKeepsTheShortRetryBound() {
        PendingPlaneTransition pending = new PendingPlaneTransition(origin, landing, 1000, 6800);
        assertFalse(pending.observe(origin, false, false, false, false, 2799));
        assertTrue(pending.observe(origin, false, false, false, false, 2800));
        assertFalse(pending.landed());
    }

    @Test public void arrivalRequiresExpectedFloorProximityAndLoadedScene() {
        PendingPlaneTransition pending = new PendingPlaneTransition(origin, landing, 1000, 6800);
        assertFalse(pending.observe(origin, true, false, true, false, 1100));
        assertFalse(pending.observe(new WorldPoint(3100, 3400, 1), false, false, true, false, 1200));
        assertFalse(pending.observe(landing, false, false, false, false, 1300));
        assertTrue(pending.observe(landing, true, true, true, false, 1400));
        assertTrue(pending.landed());
    }

    @Test public void activityDoesNotExtendDeadlineOrAllowRedispatch() {
        PendingPlaneTransition pending = new PendingPlaneTransition(origin, landing, 1000, 6800);
        assertFalse(pending.observe(origin, true, true, false, false, 7799));
        assertTrue(pending.observe(origin, true, true, false, false, 7800));
        assertFalse(pending.landed());
        assertTrue(pending.observe(landing, false, false, true, false, 7900));
        assertFalse(pending.landed());
    }

    @Test public void cancellationWinsOverLanding() {
        PendingPlaneTransition pending = new PendingPlaneTransition(origin, landing, 1000, 6800);
        assertTrue(pending.observe(landing, false, false, true, true, 1100));
        assertFalse(pending.landed());
    }

    @Test public void nextFloorGetsItsOwnLandingCheck() {
        PendingPlaneTransition next = new PendingPlaneTransition(landing,
                new WorldPoint(3000, 3300, 2), 1000, 6800);
        assertFalse(next.observe(landing, false, false, true, false, 1000));
        assertFalse(next.landed());
    }
}
