package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderBackend;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import org.junit.Test;
import org.mockito.MockedStatic;
import static net.runelite.client.plugins.microbot.util.walker.WalkerApiAssertions.assertThrows;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TypedPlanningActiveRouteIsolationTest {
    private static final WorldPoint ACTIVE = new WorldPoint(3000, 3300, 0);

    private static Field field(String name) throws Exception {
        Field result = Rs2Walker.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
        final Pathfinder previousSearch = Rs2PathApi.getPathfinder();
        final Future<?> previousFuture = Rs2PathApi.getPathfinderFuture();
        final ExecutorService previousExecutor = Rs2PathApi.getPathfindingExecutor();
        final WorldMapPoint previousMarker = Rs2PathApi.getMarker();
        final boolean previousStart = Rs2PathApi.isStartPointSet();
        final Field config = planningConfigField();
        final Object previousConfig = config.get(null);
        final Field target = field("currentTarget");
        final WorldPoint previousTarget = (WorldPoint) target.get(null);
        @SuppressWarnings("unchecked")
        final AtomicReference<InteractionRequest> reservation =
                (AtomicReference<InteractionRequest>) field("activeInteraction").get(null);
        final InteractionRequest previousOwner = reservation.get();
        final Pathfinder activeSearch = mock(Pathfinder.class);
        final Future<?> activeFuture = mock(Future.class);
        final ExecutorService activeExecutor = mock(ExecutorService.class);
        final WorldMapPoint activeMarker = mock(WorldMapPoint.class);
        final InteractionRequest owner = InteractionRequest.npc(1, "active", ACTIVE, "Talk-to", () -> false);

        Fixture() throws Exception {
            owner.bind(ACTIVE);
            owner.ownedTarget.set(ACTIVE);
            reservation.set(owner);
            target.set(null, ACTIVE);
            Rs2PathApi.setPathfinder(activeSearch);
            Rs2PathApi.setPathfinderFuture(activeFuture);
            Rs2PathApi.setPathfindingExecutor(activeExecutor);
            Rs2PathApi.setMarker(activeMarker);
            Rs2PathApi.setStartPointSet(true);
        }

        void unchanged() throws Exception {
            assertSame(activeSearch, Rs2PathApi.getPathfinder());
            assertSame(activeFuture, Rs2PathApi.getPathfinderFuture());
            assertSame(activeExecutor, Rs2PathApi.getPathfindingExecutor());
            assertSame(activeMarker, Rs2PathApi.getMarker());
            assertTrue(Rs2PathApi.isStartPointSet());
            assertEquals(ACTIVE, target.get(null));
            assertSame(owner, reservation.get());
            assertEquals(ACTIVE, owner.ownedTarget.get());
            assertTrue(owner.active());
            assertEquals(0, owner.lifecycle.attempts());
            verifyNoInteractions(activeSearch, activeFuture, activeExecutor, activeMarker);
        }

        @Override public void close() throws Exception {
            Rs2PathApi.setPathfinder(previousSearch);
            Rs2PathApi.setPathfinderFuture(previousFuture);
            Rs2PathApi.setPathfindingExecutor(previousExecutor);
            Rs2PathApi.setMarker(previousMarker);
            Rs2PathApi.setStartPointSet(previousStart);
            target.set(null, previousTarget);
            reservation.set(previousOwner);
            config.set(null, previousConfig);
            microbot.close();
        }

        private static Field planningConfigField() throws Exception {
            Field result = ShortestPathPlugin.class.getDeclaredField("pathfinderConfig");
            result.setAccessible(true);
            return result;
        }
    }

    private static Rs2RouteRequest query() {
        return Rs2RouteRequest.to(TypedPlanningFixture.START, TypedPlanningFixture.LONG)
                .withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER);
    }

    @Test public void bothEnginesLeaveTheEntirePublishedActiveRouteAndOwnerUntouched() throws Exception {
        for (PathfinderBackend backend : PathfinderBackend.values()) {
            try (Fixture f = new Fixture()) {
                PathfinderConfig source = TypedPlanningFixture.config(backend, true, false);
                Object originalEdges = source.getTransports().get(TypedPlanningFixture.START);
                Set<?> originalTeleports = Set.copyOf(source.getUsableTeleports());
                f.config.set(null, source);
                Rs2RouteResult result = Rs2PathApi.plan(query());
                assertEquals(Rs2RouteTermination.TARGET_REACHED, result.getTerminationReason());
                assertSame(source, Rs2PathApi.getPathfinderConfig());
                assertSame(originalEdges, source.getTransports().get(TypedPlanningFixture.START));
                assertEquals(originalTeleports, source.getUsableTeleports());
                assertFalse(source.isUseBankItems());
                f.unchanged();
            }
        }
    }

    @Test public void interruptedQueryCannotCancelOrClearThePublishedRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            PathfinderConfig source = mock(PathfinderConfig.class);
            Thread.currentThread().interrupt();
            try {
                assertEquals(Rs2RouteTermination.CANCELLED,
                        TypedRoutePlanning.planWithConfig(query(), source).getTerminationReason());
                assertTrue(Thread.currentThread().isInterrupted());
                verifyNoInteractions(source);
                f.unchanged();
            } finally { Thread.interrupted(); }
        }
    }

    @Test public void captureFailureCannotRestoreOrTearDownThePublishedRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            PathfinderConfig source = mock(PathfinderConfig.class);
            when(source.copyForPlanning()).thenThrow(new IllegalStateException("capture unavailable"));
            assertThrows(IllegalStateException.class, () -> TypedRoutePlanning.planWithConfig(query(), source));
            verify(source, never()).setUseBankItems(anyBoolean());
            f.unchanged();
        }
    }

    @Test public void ownerReplacementDuringCaptureIsNeverRolledBackByTheProbe() throws Exception {
        try (Fixture f = new Fixture()) {
            InteractionRequest replacement = InteractionRequest.npc(2, "replacement", ACTIVE.dx(1), "Talk-to", () -> false);
            replacement.bind(ACTIVE.dx(1));
            replacement.ownedTarget.set(ACTIVE.dx(1));
            Pathfinder nextSearch = mock(Pathfinder.class);
            Future<?> nextFuture = mock(Future.class);
            PathfinderConfig source = mock(PathfinderConfig.class);
            when(source.copyForPlanning()).thenAnswer(call -> {
                f.reservation.set(replacement);
                f.target.set(null, ACTIVE.dx(1));
                Rs2PathApi.setPathfinder(nextSearch);
                Rs2PathApi.setPathfinderFuture(nextFuture);
                return TypedPlanningFixture.config(PathfinderBackend.LEGACY, true, false).copyForPlanning();
            });
            assertEquals(Rs2RouteTermination.TARGET_REACHED,
                    TypedRoutePlanning.planWithConfig(query(), source).getTerminationReason());
            assertSame(replacement, f.reservation.get());
            assertEquals(ACTIVE.dx(1), f.target.get(null));
            assertSame(nextSearch, Rs2PathApi.getPathfinder());
            assertSame(nextFuture, Rs2PathApi.getPathfinderFuture());
            assertSame(f.activeExecutor, Rs2PathApi.getPathfindingExecutor());
            assertSame(f.activeMarker, Rs2PathApi.getMarker());
            verifyNoInteractions(f.activeSearch, f.activeFuture, f.activeExecutor, f.activeMarker, nextSearch, nextFuture);
        }
    }
}
