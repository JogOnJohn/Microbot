package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

public class ShortestPathRefreshCancellationTest {
    private ExecutorService previousExecutor;
    private Future<?> previousFuture;
    private Pathfinder previousPathfinder;
    private PathfinderConfig previousConfig;
    private PathfinderConfig config;
    private final AtomicReference<Runnable> work = new AtomicReference<>();

    @Before
    public void setUp() {
        previousExecutor = ShortestPathPlugin.pathfindingExecutor;
        previousFuture = ShortestPathPlugin.pathfinderFuture;
        previousPathfinder = ShortestPathPlugin.pathfinder;
        previousConfig = ShortestPathPlugin.pathfinderConfig;
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            work.set(invocation.getArgument(0));
            return mock(Future.class);
        });
        config = mock(PathfinderConfig.class);
        ShortestPathPlugin.pathfindingExecutor = executor;
        ShortestPathPlugin.pathfinderFuture = null;
        ShortestPathPlugin.pathfinder = null;
        ShortestPathPlugin.pathfinderConfig = config;
    }

    @After
    public void tearDown() {
        Thread.interrupted();
        ShortestPathPlugin.pathfindingExecutor = previousExecutor;
        ShortestPathPlugin.pathfinderFuture = previousFuture;
        ShortestPathPlugin.pathfinder = previousPathfinder;
        ShortestPathPlugin.pathfinderConfig = previousConfig;
    }

    private void request() {
        new ShortestPathPlugin().restartPathfinding(new WorldPoint(1965, 7040, 3),
                Collections.singleton(new WorldPoint(3038, 3366, 0)), true);
        assertNotNull(work.get());
    }

    @Test
    public void replacementDuringRefreshDoesNotFilterOrSearchStaleRequest() {
        doAnswer(invocation -> {
            ShortestPathPlugin.pathfinder = mock(Pathfinder.class);
            return null;
        }).when(config).refresh(any(WorldPoint.class));
        request();
        work.get().run();
        verify(config, never()).filterLocations(any(), anyBoolean());
        verify(config, never()).getMap();
    }

    @Test
    public void interruptedRefreshFailureDoesNotRunFallbackSearch() {
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Refresh interrupted by replacement");
        }).when(config).refresh(any(WorldPoint.class));
        request();
        work.get().run();
        verify(config, never()).filterLocations(any(), anyBoolean());
        verify(config, never()).getMap();
    }
}
