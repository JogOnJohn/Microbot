package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExactAdapterRealDataTest {
    @Test(timeout = 120000) public void packagedWorldBuildsAndRoutesWithTheExistingFacade() {
        CollisionMap collision = new CollisionMap(SplitFlagMap.fromResources());
        PathfinderConfig config = mock(PathfinderConfig.class);
        when(config.getBackend()).thenReturn(PathfinderBackend.EXACT);
        when(config.getMap()).thenReturn(collision);
        when(config.getCalculationCutoffMillis()).thenReturn(5000L);
        when(config.getAllTransports()).thenReturn(Transport.loadAllFromResources());
        when(config.getTransports()).thenReturn(new ConcurrentHashMap<>());
        when(config.exactAvailability()).thenReturn(TransportAvailabilityFixture.of());
        int start = WorldPointUtil.packWorldPoint(3221, 3218, 0);
        int goal = WorldPointUtil.packWorldPoint(3222, 3218, 0);
        long began = System.nanoTime();
        Pathfinder first = new Pathfinder(config, start, Set.of(goal));
        first.run();
        long coldMs = (System.nanoTime() - began) / 1_000_000;
        assertEquals("reached-goal", first.getTerminationReason());
        assertEquals(new WorldPoint(3222, 3218, 0), first.getPath().get(first.getPath().size() - 1));
        began = System.nanoTime();
        Pathfinder second = new Pathfinder(config, start, Set.of(goal));
        second.run();
        assertEquals("reached-goal", second.getTerminationReason());
        System.out.println("EXACT_ADAPTER_TIMING coldMs=" + coldMs + " warmMs=" + ((System.nanoTime() - began) / 1_000_000));
    }
}
