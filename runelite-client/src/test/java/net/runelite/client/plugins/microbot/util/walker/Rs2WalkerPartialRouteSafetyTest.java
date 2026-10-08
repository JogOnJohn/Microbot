package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerPartialRouteSafetyTest {
    @Test
    public void rejectsMariahUpstairsPartialEndingOnBoat() {
        assertTrue(Rs2Walker.rejectsLocalFloorChangePartial(
                new WorldPoint(1764, 3622, 0), new WorldPoint(1766, 3620, 1),
                new WorldPoint(1811, 3675, 1)));
    }

    @Test
    public void rejectsNearbyFloorChangeWithNoHorizontalProgress() {
        assertTrue(Rs2Walker.rejectsLocalFloorChangePartial(
                new WorldPoint(1766, 3620, 0), new WorldPoint(1766, 3620, 1),
                new WorldPoint(1766, 3620, 0)));
    }

    @Test
    public void retainsProgressingAndDistantPartialRoutes() {
        assertFalse(Rs2Walker.rejectsLocalFloorChangePartial(
                new WorldPoint(1760, 3620, 0), new WorldPoint(1766, 3620, 1),
                new WorldPoint(1765, 3620, 0)));
        assertFalse(Rs2Walker.rejectsLocalFloorChangePartial(
                new WorldPoint(1700, 3620, 0), new WorldPoint(1766, 3620, 1),
                new WorldPoint(1800, 3620, 1)));
        assertFalse(Rs2Walker.rejectsLocalFloorChangePartial(
                new WorldPoint(1764, 3622, 0), new WorldPoint(1766, 3620, 0),
                new WorldPoint(1811, 3675, 0)));
        assertFalse(Rs2Walker.rejectsLocalFloorChangePartial(null,
                new WorldPoint(1766, 3620, 1), new WorldPoint(1811, 3675, 1)));
    }

    @Test
    public void prefetchesWhileApproachingButNotAtPartialEndpoint() {
        assertTrue(Rs2Walker.shouldPrefetchPartialContinuation(8, 8, 3));
        assertTrue(Rs2Walker.shouldPrefetchPartialContinuation(20, 8, 3));
        assertFalse(Rs2Walker.shouldPrefetchPartialContinuation(1, 1, 3));
        assertFalse(Rs2Walker.shouldPrefetchPartialContinuation(3, 4, 3));
        assertFalse(Rs2Walker.shouldPrefetchPartialContinuation(0, 5, 0));
        assertFalse(Rs2Walker.shouldPrefetchPartialContinuation(2, 1, 0));
        assertFalse(Rs2Walker.shouldPrefetchPartialContinuation(20, 20, 3));
    }
}
