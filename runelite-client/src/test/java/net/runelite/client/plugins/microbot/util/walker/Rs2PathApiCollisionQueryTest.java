package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Rs2PathApiCollisionQueryTest {
    private static final WorldPoint TARGET = new WorldPoint(3200, 3200, 2);

    @Test
    public void missingConfigOrMapUsesAbstentionResults() {
        PathfinderConfig previous = ShortestPathPlugin.pathfinderConfig;
        PathfinderConfig config = mock(PathfinderConfig.class);
        try {
            ShortestPathPlugin.pathfinderConfig = null;
            assertTrue(Rs2PathApi.hasWalkableTileWithin(TARGET, 1));
            assertNull(Rs2PathApi.nearestWalkableTile(TARGET, 1));
            ShortestPathPlugin.pathfinderConfig = config;
            assertTrue(Rs2PathApi.hasWalkableTileWithin(TARGET, 1));
            assertNull(Rs2PathApi.nearestWalkableTile(TARGET, 1));
            verify(config, times(2)).getMap();
            verifyNoMoreInteractions(config);
        } finally {
            ShortestPathPlugin.pathfinderConfig = previous;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void delegatesWithoutRefreshingOrMutatingActiveRouteOrPolicy() throws Exception {
        PathfinderConfig previousConfig = ShortestPathPlugin.pathfinderConfig;
        Pathfinder previousPathfinder = ShortestPathPlugin.pathfinder;
        Future<?> previousFuture = ShortestPathPlugin.pathfinderFuture;
        Field overridesField = ShortestPathPlugin.class.getDeclaredField("configOverride");
        overridesField.setAccessible(true);
        Map<String, Object> overrides = (Map<String, Object>) overridesField.get(null);
        Map<String, Object> previousOverrides = new HashMap<>(overrides);
        Field distanceField = ShortestPathPlugin.class.getDeclaredField("reachedDistance");
        distanceField.setAccessible(true);
        int previousDistance = distanceField.getInt(null);
        PathfinderConfig config = mock(PathfinderConfig.class);
        CollisionMap map = mock(CollisionMap.class);
        Pathfinder activeRoute = mock(Pathfinder.class);
        Future<?> activeFuture = mock(Future.class);
        when(config.getMap()).thenReturn(map);
        when(map.hasRegion(anyInt(), anyInt())).thenReturn(true);
        when(map.isBlocked(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(map.isBlocked(3199, 3199, 2)).thenReturn(false);
        try {
            ShortestPathPlugin.pathfinderConfig = config;
            ShortestPathPlugin.pathfinder = activeRoute;
            ShortestPathPlugin.pathfinderFuture = activeFuture;
            overrides.put("useTransports", false);
            Map<String, Object> expectedOverrides = new HashMap<>(overrides);
            distanceField.setInt(null, 7);

            assertFalse(Rs2PathApi.hasWalkableTileWithin(TARGET, 0));
            assertTrue(Rs2PathApi.hasWalkableTileWithin(TARGET, 1));
            assertEquals(new WorldPoint(3199, 3199, 2), Rs2PathApi.nearestWalkableTile(TARGET, 1));
            assertTrue(Rs2PathApi.hasWalkableTileWithin(null, 1));
            assertNull(Rs2PathApi.nearestWalkableTile(null, 1));

            assertSame(config, ShortestPathPlugin.pathfinderConfig);
            assertSame(activeRoute, ShortestPathPlugin.pathfinder);
            assertSame(activeFuture, ShortestPathPlugin.pathfinderFuture);
            assertEquals(expectedOverrides, overrides);
            assertEquals(7, distanceField.getInt(null));
            // getMap is the only allowed config interaction, including no refresh overloads.
            verify(config, atLeastOnce()).getMap();
            verifyNoMoreInteractions(config, activeRoute, activeFuture);
        } finally {
            ShortestPathPlugin.pathfinderConfig = previousConfig;
            ShortestPathPlugin.pathfinder = previousPathfinder;
            ShortestPathPlugin.pathfinderFuture = previousFuture;
            overrides.clear();
            overrides.putAll(previousOverrides);
            distanceField.setInt(null, previousDistance);
        }
    }
}
