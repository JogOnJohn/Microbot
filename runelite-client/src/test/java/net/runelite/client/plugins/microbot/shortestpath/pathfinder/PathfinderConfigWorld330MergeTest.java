package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.World330HostedHouseTransport;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathfinderConfigWorld330MergeTest {
    @Test
    public void outsideTeleportCannotContinueThroughPersonalHomePortalOnW330() {
        WorldPoint outside = new WorldPoint(2953, 3224, 0);
        WorldPoint inside = new WorldPoint(1858, 7051, 0);
        Transport spell = new Transport(null, outside, "Teleport to House (Outside)",
                TransportType.TELEPORTATION_SPELL, true, 4);
        Transport enter = new Transport(outside, inside, "Home Portal",
                TransportType.TELEPORTATION_PORTAL, true, 4);
        Transport exit = new Transport(inside, outside, "Home Portal",
                TransportType.TELEPORTATION_PORTAL, true, 4);
        assertTrue(PathfinderConfig.allowsPersonalHouseEntry(spell, true));
        assertFalse(PathfinderConfig.allowsPersonalHouseEntry(enter, true));
        assertTrue(PathfinderConfig.allowsPersonalHouseEntry(enter, false));
        assertTrue(PathfinderConfig.allowsPersonalHouseEntry(exit, true));
        assertTrue(PathfinderConfig.allowsPersonalHouseEntry(new World330HostedHouseTransport(inside), true));
        assertFalse(PathfinderConfig.allowsPersonalHouseEntry(new Transport(null, inside,
                "Construction cape: Tele to POH", TransportType.TELEPORTATION_ITEM, true, 4), true));
    }

    @Test
    public void shortW330TripNeverFallsBackToPersonalHouse() {
        assertTrue(PathfinderConfig.shouldSkipWorld330ForLocalTrip(
                new WorldPoint(3167, 3488, 0), new WorldPoint(3047, 3343, 0), 150));
        for (TransportType type : new TransportType[]{TransportType.TELEPORTATION_SPELL, TransportType.TELEPORTATION_ITEM}) {
            Transport inside = new Transport(null, new WorldPoint(1858, 7051, 0),
                    "Teleport to House (Inside)", type, true, 4);
            assertFalse(PathfinderConfig.allowsPersonalHouseEntry(inside, true));
            assertTrue(PathfinderConfig.allowsPersonalHouseEntry(inside, false));
        }
        assertTrue(PathfinderConfig.allowsPersonalHouseEntry(
                new World330HostedHouseTransport(new WorldPoint(1877, 7052, 1)), true));
        assertTrue(PathfinderConfig.allowsPersonalHouseEntry(
                teleport(new WorldPoint(2964, 3378, 0), TransportType.TELEPORTATION_SPELL), true));
    }

    @Test
    public void targetlessRefreshPreservesPendingConcreteTarget() {
        WorldPoint pending = new WorldPoint(2476, 3463, 0);

        assertEquals(pending, PathfinderConfig.selectRefreshTarget(null, null, pending));
        assertEquals(new WorldPoint(2389, 3513, 0), PathfinderConfig.selectRefreshTarget(
                null, new WorldPoint(2389, 3513, 0), pending));
    }

    @Test
    public void localStrongholdTripSkipsHostedHouseRoute() {
        WorldPoint player = new WorldPoint(2389, 3514, 0);
        WorldPoint target = new WorldPoint(2476, 3463, 0);

        assertTrue(PathfinderConfig.shouldSkipWorld330ForLocalTrip(player, target, 150));
        assertFalse(PathfinderConfig.shouldSkipWorld330ForLocalTrip(
                player, new WorldPoint(2476, 3463, 1), 150));
        assertFalse(PathfinderConfig.shouldSkipWorld330ForLocalTrip(
                player, new WorldPoint(2600, 3514, 0), 150));
    }

    @Test
    public void outsideHostedHouseKeepsSpellItemAndWorld330Teleports() {
        Transport spell = teleport(new WorldPoint(2965, 3379, 0), TransportType.TELEPORTATION_SPELL);
        Transport item = teleport(new WorldPoint(3087, 3496, 0), TransportType.TELEPORTATION_ITEM);
        Transport world330 = new World330HostedHouseTransport(new WorldPoint(2954, 3224, 0));

        Map<WorldPoint, Set<Transport>> merged = new HashMap<>();
        merged.put(null, new HashSet<>(Set.of(spell, item)));
        Map<WorldPoint, Set<Transport>> world330Transports = new HashMap<>();
        world330Transports.put(null, Set.of(world330));

        PathfinderConfig.mergeWorld330Transports(merged, world330Transports, false);

        assertEquals(3, merged.get(null).size());
        assertTrue(merged.get(null).contains(spell));
        assertTrue(merged.get(null).contains(item));
        assertTrue(merged.get(null).contains(world330));
    }

    @Test
    public void insideHostedHouseRemovesGlobalTeleportsAndDoesNotReAddEntryTeleport() {
        Transport spell = teleport(new WorldPoint(2965, 3379, 0), TransportType.TELEPORTATION_SPELL);
        Transport item = teleport(new WorldPoint(3087, 3496, 0), TransportType.TELEPORTATION_ITEM);
        Transport world330 = new World330HostedHouseTransport(new WorldPoint(2954, 3224, 0));
        WorldPoint houseObject = new WorldPoint(1000, 1000, 0);
        Transport houseFacility = new Transport(houseObject, new WorldPoint(2965, 3379, 0),
                "Jewellery box", TransportType.POH, true, 1);

        Map<WorldPoint, Set<Transport>> merged = new HashMap<>();
        merged.put(null, new HashSet<>(Set.of(spell, item)));
        Map<WorldPoint, Set<Transport>> world330Transports = new HashMap<>();
        world330Transports.put(null, Set.of(world330));
        world330Transports.put(houseObject, Set.of(houseFacility));

        PathfinderConfig.mergeWorld330Transports(merged, world330Transports, true);

        assertFalse(merged.containsKey(null));
        assertTrue(merged.get(houseObject).contains(houseFacility));
    }

    private static Transport teleport(WorldPoint destination, TransportType type) {
        return new Transport(null, destination, type.name(), type, true, 4);
    }
}
