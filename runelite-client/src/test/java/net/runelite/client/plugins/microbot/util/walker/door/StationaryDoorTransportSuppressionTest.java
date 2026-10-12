package net.runelite.client.plugins.microbot.util.walker.door;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StationaryDoorTransportSuppressionTest {
    @Test
    public void recentlyOpenedNormanDoorDoesNotSuppressUpstairsLadder() {
        Map<WorldPoint, Long> doors = new HashMap<>();
        doors.put(new WorldPoint(3037, 3347, 0), System.currentTimeMillis());
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                new WorldPoint(3035, 3345, 0), new WorldPoint(3035, 3345, 1)));
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                new WorldPoint(3035, 3345, 1), new WorldPoint(3035, 3345, 0)));
    }

    @Test
    public void nearbyDoorStillSuppressesAdjacentCrossingButNotDistantTransport() {
        Map<WorldPoint, Long> doors = new HashMap<>();
        WorldPoint origin = new WorldPoint(3037, 3347, 0);
        doors.put(origin, System.currentTimeMillis());
        assertTrue(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                origin, new WorldPoint(3037, 3346, 0)));
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                origin, new WorldPoint(3050, 3347, 0)));
        doors.replaceAll((point, time) -> time - 20_000);
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                origin, new WorldPoint(3037, 3346, 0)));
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000, origin, null));
    }
}
