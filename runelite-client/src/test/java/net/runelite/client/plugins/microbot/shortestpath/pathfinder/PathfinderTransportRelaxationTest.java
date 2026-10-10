package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PathfinderTransportRelaxationTest {
    private static final int START = WorldPointUtil.packWorldPoint(3200, 3200, 0);
    private static final int MID = WorldPointUtil.packWorldPoint(3201, 3200, 0);

    @Test public void cheaperChainCanReplaceAnAlreadyQueuedDestination() { assertChain(3202, 3200); }
    @Test public void bothSearchDirectionsCanRelaxTransportDestinations() { assertChain(3200, 9600); }
    @Test public void exactBackendIsOptIn() {
        assertEquals(PathfinderBackend.LEGACY, new ShortestPathConfig() { }.pathfinderBackend());
    }

    private void assertChain(int x, int y) {
        int goal = WorldPointUtil.packWorldPoint(x, y, 0);
        CollisionMap map = mock(CollisionMap.class);
        when(map.getPlanes()).thenReturn(SplitFlagMap.fromResources().getRegionMapPlaneCounts());
        PathfinderConfig config = mock(PathfinderConfig.class);
        when(config.getMap()).thenReturn(map);
        when(config.getTransports()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
        when(config.getCalculationCutoffMillis()).thenReturn(1000L);
        Map<Integer, int[][]> edges = Map.of(START, new int[][]{{goal, 100}, {MID, 5}},
                MID, new int[][]{{goal, 1}, {START, 1}}, goal, new int[0][]);
        when(map.getNeighbors(any(), any(), any(), any())).thenAnswer(call -> {
            Node n = call.getArgument(0);
            VisitedTiles visited = call.getArgument(1);
            List<Node> result = new ArrayList<>();
            for (int[] e : edges.getOrDefault(n.packedPosition, new int[0][])) {
                assertFalse("enqueue must not close an expensive destination", visited.get(e[0]));
                result.add(new TransportNode(WorldPointUtil.unpackWorldPoint(e[0]), n, e[1]));
            }
            return result;
        });
        when(map.getReverseNeighbors(any(), any(), any(), any(), any())).thenAnswer(call -> {
            Node n = call.getArgument(0);
            List<Node> result = new ArrayList<>();
            edges.forEach((origin, outgoing) -> {
                for (int[] e : outgoing) if (e[0] == n.packedPosition)
                    result.add(new TransportNode(WorldPointUtil.unpackWorldPoint(origin), n, e[1]));
            });
            return result;
        });
        Pathfinder finder = new Pathfinder(config, START, Set.of(goal));
        finder.run();
        assertTrue(finder.isDone());
        assertEquals(Arrays.asList(WorldPointUtil.unpackWorldPoint(START), WorldPointUtil.unpackWorldPoint(MID),
                new WorldPoint(x, y, 0)), finder.getPath());
    }

    @Test public void exactFailureDoesNotSilentlyRunLegacy() {
        CollisionMap map = mock(CollisionMap.class);
        when(map.frozenCopy()).thenThrow(new IllegalStateException("fixture failure"));
        PathfinderConfig config = mock(PathfinderConfig.class);
        when(config.getMap()).thenReturn(map);
        when(config.getBackend()).thenReturn(PathfinderBackend.EXACT);
        when(config.exactAvailability()).thenReturn(TransportAvailabilityFixture.of());
        Pathfinder finder = new Pathfinder(config, START, Set.of(MID));
        finder.run();
        assertEquals("backend-failure", finder.getTerminationReason());
        assertTrue(finder.getPath().isEmpty());
        verify(map, never()).getNeighbors(any(), any(), any(), any());
    }
}
