package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.PohHouseConfiguration;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.World330HostedHouseTransport;
import net.runelite.client.plugins.microbot.util.poh.data.MountedXerics;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PlanningHouseSnapshotTest
{
    private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint EXIT = new WorldPoint(1858, 7051, 0);
    private static final WorldPoint FAR = new WorldPoint(3500, 3500, 0);

    @Test public void detectedPersonalXericsRetainsExecutableSubtypeWithoutInventedFacilities() throws Exception {
        PohHouseConfiguration house = house();
        PathfinderConfig c = config(301, true, false);
        PathfinderConfig.PlanningHouse snapshot = snapshot(house, false, true, false, true, Set.of(MountedXerics.LOOKOUT));
        Map<WorldPoint, Set<Transport>> graph = snapshot.merge(c, FAR);
        List<Transport> facilities = List.copyOf(graph.get(EXIT));
        assertEquals(1, facilities.size());
        assertTrue(facilities.get(0) instanceof PohTransport);
        PohTransport selected = (PohTransport) facilities.get(0);
        assertSame(MountedXerics.LOOKOUT, selected.getTeleport());
        assertFalse(selected.isHostedHouse());
        assertTrue("merge must not change original catalog", c.getAllTransports().isEmpty());
    }

    @Test public void hostedPrecedenceRequiresWorld330AndNeverFallsBackToPersonalOnLocalTrip() throws Exception {
        PohHouseConfiguration house = house();
        PathfinderConfig c = config(330, true, true);
        c.setUseBankItems(true);
        PathfinderConfig.PlanningHouse snapshot = snapshot(house, false, true, false, true, Set.of(MountedXerics.LOOKOUT));
        assertTrue("short trip skips hosted and does not enable personal fallback", snapshot.merge(c, PLAYER).isEmpty());
        Map<WorldPoint, Set<Transport>> graph = snapshot.merge(c, FAR);
        assertTrue(graph.get(null).stream().anyMatch(World330HostedHouseTransport.class::isInstance));
        assertTrue(graph.values().stream().flatMap(Set::stream).filter(PohTransport.class::isInstance)
                .map(PohTransport.class::cast).allMatch(PohTransport::isHostedHouse));
        assertFalse(snapshot.merge(config(301, true, true), FAR).get(EXIT).isEmpty());
    }

    @Test public void hostedBankAccessIsExplicitAndUnavailableOrRejectedFacilitiesAreNotInvented() throws Exception {
        PathfinderConfig c = config(330, true, true);
        PathfinderConfig.PlanningHouse snapshot = snapshot(house(), false, true, false, true, Set.of(MountedXerics.LOOKOUT));
        c.setUseBankItems(false);
        assertTrue(snapshot.merge(c, FAR).isEmpty());
        c.setUseBankItems(true);
        assertFalse(snapshot.merge(c, FAR).isEmpty());
        PathfinderConfig.PlanningHouse rejected = snapshot(house(), false, false, true, true, Set.of(MountedXerics.LOOKOUT));
        assertTrue(rejected.merge(c, FAR).isEmpty());
    }

    @Test public void absentOrDisabledPersonalFacilityAndExplicitHouseGateStayAbsent() throws Exception {
        PathfinderConfig c = config(301, true, false);
        assertTrue(snapshot(house(), false, true, true, true, Set.of()).merge(c, FAR).getOrDefault(EXIT, Set.of()).isEmpty());
        assertTrue(snapshot(house(), false, true, true, true, Set.of(MountedXerics.LOOKOUT))
                .merge(config(301, false, false), FAR).isEmpty());
    }

    private static PohHouseConfiguration house() {
        PohHouseConfiguration house = mock(PohHouseConfiguration.class);
        when(house.exitPortal()).thenReturn(EXIT);
        when(house.teleports(anyBoolean())).thenReturn(Set.of(MountedXerics.LOOKOUT));
        return house;
    }

    private static PathfinderConfig.PlanningHouse snapshot(PohHouseConfiguration house, boolean hosted,
            boolean eligible, boolean carried, boolean banked, Set<PohTeleport> allowed) {
        return new PathfinderConfig.PlanningHouse(house, PLAYER, hosted, hosted, eligible, false,
                carried, banked, allowed, Collections.emptyMap());
    }

    private static PathfinderConfig config(int world, boolean own, boolean hosted) throws Exception {
        Client client = mock(Client.class);
        when(client.getWorld()).thenReturn(world);
        PathfinderConfig c = new PathfinderConfig(SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), client, null, false);
        for (String field : List.of("usePoh", "useWorld330MaxHouse")) {
            Field f = PathfinderConfig.class.getDeclaredField(field);
            f.setAccessible(true);
            f.setBoolean(c, field.equals("usePoh") ? own : hosted);
        }
        return c;
    }
}
