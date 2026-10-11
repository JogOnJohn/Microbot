package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.transport.TransportRefusalLedger;
import net.runelite.client.plugins.microbot.shortestpath.transport.TransportRefusalLedgerTest;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TransportRefusalRoutingTest {
    @Test public void legacyForwardAndReverseExpansionRetainAlternativeWithSameEndpoints() {
        Transport failed = TransportRefusalLedgerTest.transport(100), alternative = TransportRefusalLedgerTest.transport(101);
        WorldPoint origin = failed.getOrigin(), destination = failed.getDestination();
        int from = WorldPointUtil.packWorldPoint(origin), to = WorldPointUtil.packWorldPoint(destination);
        TransportRefusalLedger ledger = new TransportRefusalLedger();
        ledger.beginWalk(destination);
        ledger.failure(ledger.generation(), failed, origin, origin, true, true, 1000);
        ledger.failure(ledger.generation(), failed, origin, origin, true, true, 2000);
        CollisionMap map = new CollisionMap(SplitFlagMap.fromResources());
        PathfinderConfig config = mock(PathfinderConfig.class);
        net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap<Set<Transport>> packed =
                new net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap<>(1);
        packed.put(from, new LinkedHashSet<>(Arrays.asList(failed, alternative)));
        when(config.getTransportsPacked()).thenReturn(packed);
        when(config.getTransportRefusals()).thenReturn(ledger);
        when(config.isWalkScopedTransportBlocked(any())).thenCallRealMethod();
        when(config.getBlockedTransportEdgesPacked()).thenReturn(Set.of(PathfinderConfig.transportEdgeKey(from, to)));
        List<Node> forward = new ArrayList<>(map.getNeighbors(new Node(from, null), new VisitedTiles(map), config, Set.of(to)));
        assertEquals(1, forward.stream().filter(n -> n instanceof TransportNode && n.packedPosition == to).count());
        List<Node> reverse = map.getReverseNeighbors(new Node(to, null), new VisitedTiles(map), config, Set.of(),
                Map.of(to, new LinkedHashSet<>(Arrays.asList(failed, alternative))));
        assertEquals(1, reverse.stream().filter(n -> n instanceof TransportNode && n.packedPosition == from).count());
        ledger.beginWalk(null);
        forward = map.getNeighbors(new Node(from, null), new VisitedTiles(map), config, Set.of(to));
        assertEquals(2, forward.stream().filter(n -> n instanceof TransportNode && n.packedPosition == to).count());
    }
}
