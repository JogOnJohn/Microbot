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
        Map<String, Long> doors = new HashMap<>();
        Rs2DoorHandler.markStationaryDoorCrossingOpened(doors,
                new WorldPoint(3037, 3347, 0), new WorldPoint(3037, 3346, 0));
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                new WorldPoint(3035, 3345, 0), new WorldPoint(3035, 3345, 1)));
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                new WorldPoint(3035, 3345, 1), new WorldPoint(3035, 3345, 0)));
    }

    @Test
    public void nearbyDoorStillSuppressesAdjacentCrossingButNotDistantTransport() {
        Map<String, Long> doors = new HashMap<>();
        WorldPoint origin = new WorldPoint(3037, 3347, 0);
        Rs2DoorHandler.markStationaryDoorCrossingOpened(doors, origin, new WorldPoint(3037, 3346, 0));
        assertTrue(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                origin, new WorldPoint(3037, 3346, 0)));
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                origin, new WorldPoint(3050, 3347, 0)));
        doors.replaceAll((point, time) -> time - 20_000);
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                origin, new WorldPoint(3037, 3346, 0)));
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000, origin, null));
    }

    @Test
    public void jessBedroomDoorDoesNotSuppressBathroomOrSharedCornerCrossing() {
        Map<String, Long> doors = new HashMap<>();
        WorldPoint bedroomFrom = new WorldPoint(2617, 3294, 1);
        WorldPoint bedroomTo = new WorldPoint(2616, 3294, 1);
        Rs2DoorHandler.markStationaryDoorCrossingOpened(doors, bedroomFrom, bedroomTo);
        assertTrue(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000, bedroomTo, bedroomFrom));
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                new WorldPoint(2615, 3293, 1), new WorldPoint(2615, 3292, 1)));
        assertFalse(Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(doors, 10_000,
                bedroomTo, new WorldPoint(2616, 3293, 1)));
    }

    @Test
    public void markingOneObjectDoesNotMarkOtherDoorsAndInvalidCrossingsAreIgnored() {
        Map<WorldPoint, Long> objects = new HashMap<>();
        WorldPoint door = new WorldPoint(2617, 3294, 1);
        Rs2DoorHandler.markStationaryDoorOpened(objects, door);
        assertTrue(objects.containsKey(door));
        assertFalse(objects.containsKey(new WorldPoint(2615, 3292, 1)));
        assertTrue(Rs2DoorProbe.wasDoorOpenedRecently(objects, door, 10_000));
        assertFalse(Rs2DoorProbe.wasDoorOpenedRecently(objects, new WorldPoint(2615, 3292, 1), 10_000));
        Map<String, Long> edges = new HashMap<>();
        Rs2DoorHandler.markStationaryDoorCrossingOpened(edges, door, door);
        Rs2DoorHandler.markStationaryDoorCrossingOpened(edges, door, new WorldPoint(2617, 3294, 0));
        Rs2DoorHandler.markStationaryDoorCrossingOpened(edges, door, null);
        assertTrue(edges.isEmpty());
    }
}
