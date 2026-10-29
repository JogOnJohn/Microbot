package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class SamePlaneObjectLandingTest {
    private final WorldPoint destination = new WorldPoint(3000, 3000, 0);
    private final WorldPoint origin = new WorldPoint(2800, 2800, 0);

    @Test public void acceptsNearbyInstantLandingWithoutWalking() {
        assertTrue(Rs2Walker.hasReachedSamePlaneObjectLanding(origin,
                new WorldPoint(3003, 3003, 0), destination));
    }

    @Test public void rejectsLandingOutsideTolerance() {
        assertFalse(Rs2Walker.hasReachedSamePlaneObjectLanding(origin,
                new WorldPoint(3004, 3000, 0), destination));
    }

    @Test public void adjacentDoorApproachIsNotAlreadyALanding() {
        WorldPoint near = new WorldPoint(2999, 3000, 0);
        assertFalse(Rs2Walker.hasReachedSamePlaneObjectLanding(near, near, destination));
    }

    @Test public void rejectsWrongPlanes() {
        assertFalse(Rs2Walker.hasReachedSamePlaneObjectLanding(origin,
                new WorldPoint(3000, 3000, 1), destination));
        assertFalse(Rs2Walker.hasReachedSamePlaneObjectLanding(new WorldPoint(2800, 2800, 1),
                new WorldPoint(3001, 3000, 0), destination));
    }

    @Test public void exactArrivalStillWorksWithoutOrigin() {
        assertTrue(Rs2Walker.hasReachedSamePlaneObjectLanding(null, destination, destination));
    }

    @Test public void absentSnapshotsFailClosed() {
        assertFalse(Rs2Walker.hasReachedSamePlaneObjectLanding(origin, null, destination));
        assertFalse(Rs2Walker.hasReachedSamePlaneObjectLanding(origin, destination, null));
        assertFalse(Rs2Walker.hasReachedSamePlaneObjectLanding(null,
                new WorldPoint(3001, 3000, 0), destination));
    }
}
