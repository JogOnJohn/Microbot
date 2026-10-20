package net.runelite.client.plugins.microbot.util.walker.lifecycle;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.transport.TransportRefusalLedger;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class Rs2WalkerLifecycleCancellationTest {
    private static final WorldPoint START = new WorldPoint(1940, 7050, 1);
    private static final WorldPoint END = new WorldPoint(3047, 3343, 0);
    private ExecutorService previousExecutor;
    private Future<?> previousFuture;
    private Pathfinder previousPathfinder;
    private PathfinderConfig previousConfig, config;
    private WorldPoint previousTarget;
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private final AtomicReference<Runnable> work = new AtomicReference<>();

    @Before public void setUp() throws Exception {
        previousExecutor = ShortestPathPlugin.pathfindingExecutor;
        previousFuture = ShortestPathPlugin.pathfinderFuture;
        previousPathfinder = ShortestPathPlugin.pathfinder;
        previousConfig = ShortestPathPlugin.pathfinderConfig;
        previousTarget = Rs2Walker.getCurrentTarget();
        microbot = mockStatic(Microbot.class);
        player = mockStatic(Rs2Player.class);
        config = mock(PathfinderConfig.class);
        when(config.getTransportRefusals()).thenReturn(new TransportRefusalLedger());
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(inv -> {
            work.set(inv.getArgument(0));
            return mock(Future.class);
        });
        ShortestPathPlugin.pathfindingExecutor = executor;
        ShortestPathPlugin.pathfinderFuture = null;
        ShortestPathPlugin.pathfinder = null;
        ShortestPathPlugin.pathfinderConfig = config;
    }

    @After public void tearDown() throws Exception {
        Thread.interrupted();
        ShortestPathPlugin.pathfindingExecutor = previousExecutor;
        ShortestPathPlugin.pathfinderFuture = previousFuture;
        ShortestPathPlugin.pathfinder = previousPathfinder;
        ShortestPathPlugin.pathfinderConfig = previousConfig;
        Field target = Rs2Walker.class.getDeclaredField("currentTarget");
        target.setAccessible(true);
        target.set(null, previousTarget);
        player.close();
        microbot.close();
    }

    private Future<?> requestWithInterruptSensitiveFuture() {
        assertTrue(Rs2WalkerLifecycleRuntime.restartPathfinding(START, END));
        Future<?> future = ShortestPathPlugin.pathfinderFuture;
        when(future.cancel(anyBoolean())).thenAnswer(inv -> {
            if (inv.getArgument(0, Boolean.class)) Thread.currentThread().interrupt();
            return true;
        });
        return future;
    }

    @Test public void walkerRestartDoesNotInterruptOrRunAnObsoleteRefresh() {
        Future<?> old = requestWithInterruptSensitiveFuture();
        Runnable running = work.get();
        doAnswer(inv -> {
            assertTrue(Rs2WalkerLifecycleRuntime.restartPathfinding(START, END));
            assertFalse("Client-thread reads must not become empty cancellation fallbacks",
                    Thread.currentThread().isInterrupted());
            return null;
        }).when(config).refresh(any(WorldPoint.class));
        running.run();
        verify(old).cancel(false);
        verify(config, never()).getMap();
    }

    @Test public void routeClearDoesNotInterruptSharedRefreshOrRunItsObsoleteSearch() {
        Future<?> old = requestWithInterruptSensitiveFuture();
        Runnable running = work.get();
        doAnswer(inv -> {
            Rs2Walker.setTarget(null, "test:route-clear-during-refresh");
            assertFalse(Thread.currentThread().isInterrupted());
            return null;
        }).when(config).refresh(any(WorldPoint.class));
        running.run();
        verify(old).cancel(false);
        assertNull(ShortestPathPlugin.pathfinder);
        assertNull(ShortestPathPlugin.pathfinderFuture);
        verify(config, never()).getMap();
    }

    @Test public void supersededRefreshFailureCannotStartFallbackSearch() {
        requestWithInterruptSensitiveFuture();
        Runnable running = work.get();
        doAnswer(inv -> {
            Rs2WalkerLifecycleRuntime.restartPathfinding(START, END);
            throw new IllegalStateException("obsolete refresh");
        }).when(config).refresh(any(WorldPoint.class));
        running.run();
        verify(config, never()).getMap();
    }

    @Test public void interruptedRefreshFailureCannotStartFallbackSearch() {
        requestWithInterruptSensitiveFuture();
        doAnswer(inv -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("shutdown interrupted refresh");
        }).when(config).refresh(any(WorldPoint.class));
        work.get().run();
        verify(config, never()).getMap();
    }

    @Test public void supersededCaveSearchCannotStartWalkingOnlyComparison() {
        player.when(Rs2Player::isInCave).thenReturn(true);
        try (MockedConstruction<Pathfinder> created = mockConstruction(Pathfinder.class,
                (pending, context) -> doAnswer(inv -> {
                    ShortestPathPlugin.pathfinder = mock(Pathfinder.class);
                    return null;
                }).when(pending).run())) {
            requestWithInterruptSensitiveFuture();
            work.get().run();
            assertEquals(1, created.constructed().size());
            verify(created.constructed().get(0)).run();
            verify(config, never()).setIgnoreTeleportAndItems(true);
        }
    }

    @Test public void currentUninterruptedRefreshFailureRetainsExistingFallback() {
        try (MockedConstruction<Pathfinder> created = mockConstruction(Pathfinder.class)) {
            requestWithInterruptSensitiveFuture();
            doThrow(new IllegalStateException("ordinary refresh failure"))
                    .when(config).refresh(any(WorldPoint.class));
            work.get().run();
            assertEquals(1, created.constructed().size());
            verify(created.constructed().get(0)).run();
        }
    }
}
