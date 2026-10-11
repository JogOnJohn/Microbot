package net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.TransportAvailability;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.TransportAvailabilityFixture;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderBackend;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.transport.TransportRefusalLedger;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExactTransportRefusalTest {
    @Test public void actualExactFacadeFiltersRefusedTransportEvenBeforeCatalogRefresh() {
        WorldPoint origin = new WorldPoint(1, 1, 0), landing = new WorldPoint(10, 1, 0);
        int from = WorldPointUtil.packWorldPoint(origin), to = WorldPointUtil.packWorldPoint(landing);
        Transport failed = new Transport(origin, landing, "Broken", TransportType.TRANSPORT, false, 1);
        Transport alternative = new Transport(origin, landing, "Working", TransportType.TRANSPORT, false, 4);
        SyntheticCollisionMap collision = spy(new SyntheticCollisionMap(Set.of(from, to), Map.of(), 1));
        doReturn(collision).when(collision).frozenCopy();
        doReturn(new Object()).when(collision).exactSnapshotKey();
        TransportRefusalLedger ledger = new TransportRefusalLedger();
        ledger.beginWalk(landing);
        PathfinderConfig config = mock(PathfinderConfig.class);
        when(config.getBackend()).thenReturn(PathfinderBackend.EXACT);
        when(config.getMap()).thenReturn(collision);
        when(config.getAllTransports()).thenReturn(Map.of(origin, Set.of(failed, alternative)));
        java.util.concurrent.ConcurrentHashMap<WorldPoint, Set<Transport>> catalog = new java.util.concurrent.ConcurrentHashMap<>();
        catalog.put(origin, Set.of(failed, alternative));
        when(config.getTransports()).thenReturn(catalog);
        when(config.exactAvailability()).thenReturn(TransportAvailabilityFixture.of(failed, alternative));
        when(config.getTransportRefusals()).thenReturn(ledger);
        when(config.isWalkScopedTransportBlocked(any())).thenCallRealMethod();
        when(config.getCalculationCutoffMillis()).thenReturn(5000L);
        assertSelected(config, from, to, failed, origin, landing);
        ledger.failure(ledger.generation(), failed, origin, origin, true, true, 1000);
        ledger.failure(ledger.generation(), failed, origin, origin, true, true, 2000);
        assertSelected(config, from, to, alternative, origin, landing);
        ledger.beginWalk(null);
        assertSelected(config, from, to, failed, origin, landing);
    }

    private void assertSelected(PathfinderConfig config, int from, int to, Transport expected,
                                WorldPoint origin, WorldPoint landing) {
        Pathfinder finder = new Pathfinder(config, from, Set.of(to));
        finder.run();
        assertEquals("reached-goal", finder.getTerminationReason());
        assertSame(expected, finder.getTransportForStep(origin, landing));
    }

    @Test public void refusalRebuildsWarmExactGraphAndRetainsWorkingAlternative() throws Exception {
        WorldPoint origin = new WorldPoint(1, 1, 0), landing = new WorldPoint(10, 1, 0);
        int from = WorldPointUtil.packWorldPoint(origin), to = WorldPointUtil.packWorldPoint(landing);
        Transport failed = new Transport(origin, landing, "Broken", TransportType.TRANSPORT, false, 1);
        Transport alternative = new Transport(origin, landing, "Working", TransportType.TRANSPORT, false, 4);
        SyntheticCollisionMap collision = new SyntheticCollisionMap(Set.of(from, to), Map.of(), 1);
        RoutingStatic stat = RoutingStaticBuilder.build(collision,
                Map.of(from, new LinkedHashSet<>(Arrays.asList(failed, alternative))), Set.of(), new int[0], from).routingStatic;
        TransportAvailability availability = TransportAvailabilityFixture.of(failed, alternative);
        TransportRefusalLedger ledger = new TransportRefusalLedger();
        ledger.beginWalk(landing);
        ExactRoutingSession session = new ExactRoutingSession();
        SiteGraph before = session.graph(stat, account(availability)).value();
        assertSame(failed, session.target(before, collision, to).value().search(from, () -> false, 1).path().get(1).getTransport());
        ledger.failure(ledger.generation(), failed, origin, origin, true, true, 1000);
        ledger.failure(ledger.generation(), failed, origin, origin, true, true, 2000);
        var next = session.graph(stat, account(availability.excluding(ledger::isBlocked)));
        assertFalse(next.reused());
        assertEquals(0, session.cachedTargetCount());
        var result = session.target(next.value(), collision, to).value().search(from, () -> false, 1);
        assertTrue(result.reached());
        assertSame(alternative, result.path().get(1).getTransport());
        assertEquals(4, result.cost());
        ledger.beginWalk(null);
        var restored = session.graph(stat, account(availability.excluding(ledger::isBlocked)));
        assertFalse(restored.reused());
        assertSame(failed, session.target(restored.value(), collision, to).value().search(from, () -> false, 1).path().get(1).getTransport());
    }

    private PreparedRoutingAccount account(TransportAvailability availability) {
        return PreparedRoutingAccount.compile(availability, availability, false, Set.of(), 0, true, t -> 0);
    }
}
