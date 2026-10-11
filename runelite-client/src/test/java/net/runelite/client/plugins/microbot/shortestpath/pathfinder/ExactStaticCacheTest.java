package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact.RoutingStatic;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact.ExactCancellation;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExactStaticCacheTest {
    private CollisionMap collision(Object key) {
        CollisionMap map = mock(CollisionMap.class);
        when(map.exactSnapshotKey()).thenReturn(key);
        return map;
    }

    private Transport edge(int x) {
        Transport edge = mock(Transport.class);
        when(edge.getOrigin()).thenReturn(new WorldPoint(x, 3300, 0));
        when(edge.getDestination()).thenReturn(new WorldPoint(x + 1, 3300, 1));
        return edge;
    }

    private Map<Integer, Set<Transport>> raw(Transport... edges) {
        return Map.of(0, new LinkedHashSet<>(Arrays.asList(edges)));
    }

    @Test public void bankInventoryAlternationReusesStructuralSuperset() throws Exception {
        AtomicInteger builds = new AtomicInteger();
        RoutingStatic stat = mock(RoutingStatic.class);
        when(stat.searchIndex(anyInt())).thenReturn(0);
        ExactStaticCache cache = new ExactStaticCache((map, raw, start) -> {
            assertEquals(builds.incrementAndGet(), raw.values().stream().mapToInt(Set::size).sum());
            return stat;
        });
        Object key = new Object();
        CollisionMap map = collision(key);
        Transport direct = edge(3000), bank = edge(3100);
        assertFalse(cache.prepare(map, raw(direct), 1).reused);
        assertFalse(cache.prepare(map, raw(direct, bank), 1).reused);
        ExactStaticCache.Prepared subset = cache.prepare(collision(key), raw(edge(3000)), 1);
        assertTrue(subset.reused);
        assertSame(map, subset.map);
        assertTrue(cache.prepare(map, raw(direct, bank), 1).reused);
        assertEquals(2, builds.get());
    }

    @Test public void collisionChangesAndUncoveredStartsRebuild() throws Exception {
        AtomicInteger builds = new AtomicInteger();
        RoutingStatic stat = mock(RoutingStatic.class);
        when(stat.searchIndex(99)).thenReturn(-1);
        when(stat.siteIndex(99)).thenReturn(-1);
        ExactStaticCache cache = new ExactStaticCache((map, raw, start) -> {
            builds.incrementAndGet(); return stat;
        });
        CollisionMap first = collision(new Object());
        Map<Integer, Set<Transport>> raw = raw(edge(3000));
        assertFalse(cache.prepare(first, raw, 1).reused);
        assertFalse(cache.prepare(first, raw, 99).reused);
        assertFalse(cache.prepare(collision(new Object()), raw, 1).reused);
        assertEquals(3, builds.get());
    }

    @Test public void failedBuildIsNotPublished() throws Exception {
        AtomicInteger builds = new AtomicInteger();
        RoutingStatic stat = mock(RoutingStatic.class);
        ExactStaticCache cache = new ExactStaticCache((map, raw, start) -> {
            if (builds.incrementAndGet() == 1) throw new CancellationException();
            return stat;
        });
        CollisionMap map = collision(new Object());
        Map<Integer, Set<Transport>> raw = raw(edge(3000));
        try { cache.prepare(map, raw, 1); fail("Expected cancellation"); }
        catch (CancellationException expected) { }
        assertFalse(cache.prepare(map, raw, 1).reused);
        assertEquals(2, builds.get());
    }

    @Test public void cancellationAfterBuildDoesNotPublishTheGraph() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger builds = new AtomicInteger();
        RoutingStatic stat = mock(RoutingStatic.class);
        ExactStaticCache cache = new ExactStaticCache((map, raw, start) -> {
            cancelled.set(builds.incrementAndGet() == 1);
            return stat;
        });
        CollisionMap map = collision(new Object());
        Map<Integer, Set<Transport>> raw = raw(edge(3000));
        ExactCancellation.begin(cancelled::get);
        try {
            try { cache.prepare(map, raw, 1); fail("Expected cancellation"); }
            catch (CancellationException expected) { }
            cancelled.set(false);
            assertFalse(cache.prepare(map, raw, 1).reused);
            assertEquals(2, builds.get());
        } finally { ExactCancellation.end(); }
    }
}
