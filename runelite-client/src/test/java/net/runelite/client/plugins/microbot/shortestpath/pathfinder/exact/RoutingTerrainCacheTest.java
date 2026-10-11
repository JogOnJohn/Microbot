package net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RoutingTerrainCacheTest {
    private static final int A = WorldPointUtil.packWorldPoint(10, 10, 0);
    private static final int B = WorldPointUtil.packWorldPoint(11, 10, 0);
    private static final int C = WorldPointUtil.packWorldPoint(30, 10, 0);

    private SyntheticCollisionMap world(Object key, boolean connected) {
        SyntheticCollisionMap world = spy(new SyntheticCollisionMap(Set.of(A, B, C), connected
                ? SyntheticCollisionMap.symmetricAdjacency(new int[][]{{A, B}}) : Map.of(), 1));
        when(world.exactSnapshotKey()).thenReturn(key);
        return world;
    }

    @Test public void changedTransportEndpointsRebuildReachabilityButReuseTerrain() throws Exception {
        SyntheticCollisionMap world = world(new Object(), true);
        RoutingStaticBuilder.TerrainCache cache = new RoutingStaticBuilder.TerrainCache();
        RoutingStaticBuilder.Result first = RoutingStaticBuilder.build(world, Map.of(), Set.of(), new int[0], A, cache);
        assertFalse(first.diagnostics.terrainReused);
        assertEquals(2, first.routingStatic.searchTileCount());
        Transport edge = new ExactTransportFixture.Builder().origin(B).destination(C).duration(1).build();
        Map<Integer, Set<Transport>> edges = Map.of(B, Set.of(edge));
        RoutingStaticBuilder.Result cached = RoutingStaticBuilder.build(world, edges, Set.of(C), new int[0], A, cache);
        RoutingStaticBuilder.Result uncached = RoutingStaticBuilder.build(world, edges, Set.of(C), new int[0], A);
        assertTrue(cached.diagnostics.terrainReused);
        assertEquals(0, cached.diagnostics.enumerateWalkableNanos);
        assertEquals(0, cached.diagnostics.naturalComponentsNanos);
        assertEquals(0, cached.diagnostics.routingComponentsNanos);
        assertEquals(3, cached.routingStatic.searchTileCount());
        assertEquals(uncached.routingStatic.searchTileCount(), cached.routingStatic.searchTileCount());
        assertEquals(uncached.routingStatic.siteCount(), cached.routingStatic.siteCount());
        for (int i = 0; i < cached.routingStatic.siteCount(); i++)
            assertEquals(uncached.routingStatic.siteTile(i), cached.routingStatic.siteTile(i));
    }

    @Test public void changedSeedStillRecomputesStructuralReachability() throws Exception {
        SyntheticCollisionMap world = world(new Object(), true);
        RoutingStaticBuilder.TerrainCache cache = new RoutingStaticBuilder.TerrainCache();
        RoutingStaticBuilder.build(world, Map.of(), Set.of(), new int[0], A, cache);
        RoutingStaticBuilder.Result result = RoutingStaticBuilder.build(world, Map.of(), Set.of(), new int[0], C, cache);
        assertTrue(result.diagnostics.terrainReused);
        assertEquals(1, result.routingStatic.searchTileCount());
        assertTrue(result.routingStatic.searchIndex(C) >= 0);
        assertEquals(-1, result.routingStatic.searchIndex(A));
    }

    @Test public void collisionAndSeparatorChangesInvalidateTerrain() throws Exception {
        Object key = new Object();
        SyntheticCollisionMap first = world(key, true);
        RoutingStaticBuilder.TerrainCache cache = new RoutingStaticBuilder.TerrainCache();
        RoutingStaticBuilder.build(first, Map.of(), Set.of(), new int[0], A, cache);
        int[] cuts = {A, B};
        RoutingStaticBuilder.Result cut = RoutingStaticBuilder.build(first, Map.of(), Set.of(), cuts, A, cache);
        assertFalse(cut.diagnostics.terrainReused);
        assertEquals(1, cut.diagnostics.crossingCount);
        assertTrue(RoutingStaticBuilder.build(first, Map.of(), Set.of(), cuts.clone(), A, cache).diagnostics.terrainReused);
        SyntheticCollisionMap changed = world(new Object(), false);
        RoutingStaticBuilder.Result collision = RoutingStaticBuilder.build(changed, Map.of(), Set.of(), cuts, A, cache);
        assertFalse(collision.diagnostics.terrainReused);
        assertEquals(0, collision.diagnostics.crossingCount);
        assertEquals(1, collision.routingStatic.searchTileCount());
    }

    @Test public void cancelledTerrainIsNeverPublished() throws Exception {
        SyntheticCollisionMap world = world(new Object(), true);
        RoutingStaticBuilder.TerrainCache cache = new RoutingStaticBuilder.TerrainCache();
        AtomicBoolean cancelled = new AtomicBoolean();
        doAnswer(call -> { cancelled.set(true); return call.callRealMethod(); }).when(world).ordinaryWalkingMask(anyInt());
        ExactCancellation.begin(cancelled::get);
        try {
            try { RoutingStaticBuilder.build(world, Map.of(), Set.of(), new int[0], A, cache); fail(); }
            catch (CancellationException expected) { }
            doCallRealMethod().when(world).ordinaryWalkingMask(anyInt());
            cancelled.set(false);
            assertFalse(RoutingStaticBuilder.build(world, Map.of(), Set.of(), new int[0], A, cache).diagnostics.terrainReused);
        } finally { ExactCancellation.end(); }
    }
}
