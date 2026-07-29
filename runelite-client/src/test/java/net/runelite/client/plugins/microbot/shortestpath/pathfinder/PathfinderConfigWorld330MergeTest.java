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
