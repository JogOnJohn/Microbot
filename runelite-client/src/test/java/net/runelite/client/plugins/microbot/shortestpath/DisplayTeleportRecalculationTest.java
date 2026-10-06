package net.runelite.client.plugins.microbot.shortestpath;

import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayTeleportRecalculationTest {
    private final WorldPoint start = new WorldPoint(2555, 3259, 0);
    private final WorldPoint landing = new WorldPoint(1858, 7051, 0);
    private final Transport teleport = new Transport(landing, "House tablet", TransportType.TELEPORTATION_ITEM,
            true, 3, Set.of(Set.of(8013)));

    @Test public void khazardWalkingDoesNotRecalculateEveryShortMovement() {
        assertTrue(ShortestPathPlugin.nearPendingTeleportStart(new WorldPoint(2534, 3250, 0),
                List.of(start, landing), Set.of(teleport), 10));
        assertFalse(ShortestPathPlugin.nearPendingTeleportStart(new WorldPoint(2494, 3240, 0),
                List.of(start, landing), Set.of(teleport), 10));
    }
    @Test public void ordinaryWalkingAndUnavailableTeleportsKeepExistingThreshold() {
        assertFalse(ShortestPathPlugin.nearPendingTeleportStart(start,
                List.of(start, new WorldPoint(2554, 3259, 0)), Set.of(teleport), 10));
        assertFalse(ShortestPathPlugin.nearPendingTeleportStart(start, List.of(start, landing), Set.of(), 10));
    }
    @Test public void landingAndPlaneChangeDoNotKeepTheOldAttachment() {
        assertFalse(ShortestPathPlugin.nearPendingTeleportStart(landing, List.of(start, landing), Set.of(teleport), 10));
        assertFalse(ShortestPathPlugin.nearPendingTeleportStart(new WorldPoint(2555, 3259, 1),
                List.of(start, landing), Set.of(teleport), 10));
    }
    @Test public void disabledRecalculationAndEmptyPathAreSafe() {
        assertFalse(ShortestPathPlugin.nearPendingTeleportStart(start, List.of(start, landing), Set.of(teleport), -1));
        assertFalse(ShortestPathPlugin.nearPendingTeleportStart(start, List.of(), Set.of(teleport), 10));
    }
}
