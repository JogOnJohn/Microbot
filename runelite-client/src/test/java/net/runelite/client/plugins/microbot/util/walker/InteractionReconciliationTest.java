package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.lifecycle.Rs2WalkerLifecycleRuntime;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionLifecycle;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionResult;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionTargetRuntime;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InteractionReconciliationTest {
    private static final WorldPoint GOAL = new WorldPoint(3000, 3300, 0);

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static InteractionRequest request() {
        return InteractionRequest.npc(1, "A", GOAL, "Talk-to", () -> false);
    }

    private static InteractionTargetRuntime.Snapshot snapshot(boolean ready, boolean confirmed) throws Exception {
        Constructor<InteractionTargetRuntime.Snapshot> constructor = InteractionTargetRuntime.Snapshot.class
            .getDeclaredConstructor(WorldPoint.class, boolean.class, boolean.class, boolean.class, boolean.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(GOAL, true, false, ready, confirmed, 123L);
    }

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Microbot> micro = mockStatic(Microbot.class);
        final MockedStatic<InputArbiter> input = mockStatic(InputArbiter.class);
        final MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
        final MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS);
        final MockedStatic<Rs2WalkerLifecycleRuntime> routes = mockStatic(Rs2WalkerLifecycleRuntime.class);
        final Field target = field(Rs2Walker.class, "currentTarget");
        @SuppressWarnings("unchecked")
        final AtomicReference<InteractionRequest> reservation = (AtomicReference<InteractionRequest>)
            field(Rs2Walker.class, "activeInteraction").get(null);
        @SuppressWarnings("unchecked")
        final ThreadLocal<InteractionRequest> context = (ThreadLocal<InteractionRequest>)
            field(Rs2Walker.class, "interactionOwner").get(null);
        final ReentrantLock lock = (ReentrantLock) field(Rs2Walker.class, "walkerLock").get(null);
        final InteractionRequest previous = reservation.getAndSet(null);
        final InteractionRequest previousContext = context.get();
        final Object previousTarget = target.get(null);
        final boolean previousPause = Microbot.pauseAllScripts.getAndSet(false);
        final Client client = mock(Client.class);
        final InteractionRequest owner = request();
        final InteractionTargetRuntime runtime = mock(InteractionTargetRuntime.class);

        Fixture() throws Exception {
            target.set(null, null);
            context.remove();
            micro.when(Microbot::getClient).thenReturn(client);
            field(InteractionRequest.class, "runtime").set(owner, runtime);
            when(runtime.probe(anyBoolean())).thenReturn(snapshot(false, false));
        }

        InteractionResult poll(boolean banked, WorldPoint destination, InteractionRequest operation) {
            return banked ? Rs2Walker.walkWithBankedTransportsAndState(destination, 0, operation)
                : Rs2Walker.walkTo(destination, operation);
        }

        void reserve(WorldPoint owned, WorldPoint current) throws Exception {
            assertTrue(owner.bind(GOAL));
            owner.ownedTarget.set(owned);
            target.set(null, current);
            reservation.set(owner);
        }

        void isolatedCleanup() {
            walker.when(() -> Rs2Walker.clearWalkingRoute(anyString())).thenAnswer(call -> {
                assertSame("Cleanup must run before releasing the reservation", owner, reservation.get());
                target.set(null, null);
                return null;
            });
            walker.clearInvocations();
        }

        @Override public void close() throws Exception {
            reservation.set(previous);
            target.set(null, previousTarget);
            if (previousContext == null) context.remove(); else context.set(previousContext);
            Microbot.pauseAllScripts.set(previousPause);
            routes.close(); walker.close(); player.close(); input.close(); micro.close();
        }
    }

    @Test public void retainedRequestAndGoalSurviveRepeatedArrivalPolls() throws Exception {
        for (boolean banked : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                f.walker.when(() -> Rs2Walker.walkWithStateUntil(eq(GOAL), eq(0), any(BooleanSupplier.class)))
                    .thenReturn(WalkerState.ARRIVED);
                f.walker.when(() -> Rs2Walker.walkWithBankedTransportsUntil(eq(GOAL), eq(0),
                    same(f.owner.ownedTarget), any(BooleanSupplier.class))).thenReturn(WalkerState.ARRIVED);
                long id = f.owner.id;
                assertEquals(InteractionResult.IN_PROGRESS, f.poll(banked, GOAL, f.owner));
                assertEquals(InteractionResult.IN_PROGRESS, f.poll(banked, GOAL, f.owner));
                assertEquals(id, f.owner.id);
                assertEquals(GOAL, f.owner.destination());
                assertSame(f.owner, f.reservation.get());
                assertEquals(0, f.owner.lifecycle.attempts());
                verify(f.runtime, never()).dispatch(any(), any());
                assertNull(f.context.get());
            }
        }
    }

    @Test public void competingRequestCannotStealEvenTheSameCoordinate() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(GOAL, GOAL);
            InteractionRequest other = request();
            for (boolean banked : new boolean[]{false, true}) {
                assertEquals(InteractionResult.BLOCKED, f.poll(banked, GOAL, other));
                assertSame(f.owner, f.reservation.get());
                assertEquals(GOAL, f.target.get(null));
            }
            verifyNoInteractions(f.runtime);
        }
    }

    @Test public void nonOwnerSetTargetCannotClearOrReplaceReservedRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(GOAL, GOAL);
            Rs2Walker.setTarget(null, "foreign-cancel");
            Rs2Walker.setTarget(GOAL.dx(1));
            assertEquals(GOAL, f.target.get(null));
            assertSame(f.owner, f.reservation.get());
            f.routes.verifyNoInteractions();
        }
    }

    @Test public void nonOwnerRecalculationCannotRestartReservedRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(GOAL, GOAL);
            Rs2Walker.recalculatePath();
            assertEquals(GOAL, f.target.get(null));
            assertSame(f.owner, f.reservation.get());
            f.routes.verifyNoInteractions();
        }
    }

    @Test public void earlyPredicateCannotClearAnotherOwnersRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(GOAL, GOAL);
            assertEquals(WalkerState.MOVING, Rs2Walker.walkWithStateUntil(GOAL, 0, () -> true));
            assertEquals(WalkerState.MOVING, Rs2Walker.walkWithBankedTransportsUntil(GOAL, 0, () -> true));
            assertEquals(GOAL, f.target.get(null));
            assertSame(f.owner, f.reservation.get());
            f.walker.verify(() -> Rs2Walker.clearWalkingRoute(anyString()), never());
        }
    }

    @Test public void cancellingWrongRequestDoesNotTouchReservationOrRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(GOAL, GOAL);
            InteractionRequest other = request();
            other.ownedTarget.set(GOAL);
            Rs2Walker.cancelInteraction(other);
            assertFalse(other.active());
            assertTrue(f.owner.active());
            assertSame(f.owner, f.reservation.get());
            assertEquals(GOAL, f.target.get(null));
            f.walker.verify(() -> Rs2Walker.clearWalkingRoute(anyString()), never());
        }
    }

    @Test public void cancellationPreservesForeignCurrentTarget() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(GOAL, GOAL.dx(1));
            Rs2Walker.cancelInteraction(f.owner);
            assertFalse(f.owner.active());
            assertNull(f.reservation.get());
            assertEquals(GOAL.dx(1), f.target.get(null));
            f.walker.verify(() -> Rs2Walker.clearWalkingRoute(anyString()), never());
        }
    }

    @Test public void cancellationWithoutOwnedRouteCannotDestroyCurrentRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(null, GOAL);
            Rs2Walker.cancelInteraction(f.owner);
            assertNull(f.reservation.get());
            assertEquals(GOAL, f.target.get(null));
            f.walker.verify(() -> Rs2Walker.clearWalkingRoute(anyString()), never());
        }
    }

    @Test public void cancellationClearsOwnedRouteBeforeDroppingReservation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(GOAL, GOAL);
            f.isolatedCleanup();
            Rs2Walker.cancelInteraction(f.owner);
            f.walker.verify(() -> Rs2Walker.clearWalkingRoute(anyString()), times(1));
            assertNull(f.target.get(null));
            assertNull(f.reservation.get());
            assertNull(f.owner.ownedTarget.get());
        }
    }

    @Test public void contendedCancellationMarksImmediatelyAndDefersRouteCleanup() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(GOAL, GOAL);
            f.isolatedCleanup();
            CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
            Thread holder = new Thread(() -> {
                f.lock.lock();
                try {
                    held.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Lock holder timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally { f.lock.unlock(); }
            });
            holder.start();
            try {
                assertTrue(held.await(2, TimeUnit.SECONDS));
                assertEquals(InteractionResult.IN_PROGRESS, f.poll(false, GOAL, f.owner));
                Rs2Walker.cancelInteraction(f.owner);
                assertFalse(f.owner.active());
                assertSame(f.owner, f.reservation.get());
                assertEquals(GOAL, f.target.get(null));
                f.walker.verify(() -> Rs2Walker.clearWalkingRoute(anyString()), never());
            } finally {
                release.countDown();
                holder.join(2000);
            }
            assertFalse(holder.isAlive());
            Rs2Walker.cancelInteraction(f.owner);
            assertNull(f.reservation.get());
            assertNull(f.target.get(null));
        }
    }

    @Test public void nonBankedApproachRetainsGoalBeforeMovementStarts() throws Exception {
        try (Fixture f = new Fixture()) {
            f.walker.when(() -> Rs2Walker.walkWithStateUntil(eq(GOAL), eq(0), any(BooleanSupplier.class)))
                .thenAnswer(call -> {
                    assertEquals(GOAL, f.owner.ownedTarget.get());
                    assertSame(f.owner, f.reservation.get());
                    assertSame(f.owner, f.context.get());
                    f.target.set(null, GOAL);
                    return WalkerState.MOVING;
                });
            assertEquals(InteractionResult.IN_PROGRESS, f.poll(false, GOAL, f.owner));
            assertEquals(GOAL, f.owner.ownedTarget.get());
        }
    }

    @Test public void failedApproachDoesNotAdoptOrClearAnExternalRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            f.walker.when(() -> Rs2Walker.walkWithStateUntil(eq(GOAL), eq(0), any(BooleanSupplier.class)))
                .thenAnswer(call -> {
                    f.target.set(null, GOAL.dx(1));
                    return WalkerState.EXIT;
                });
            assertEquals(InteractionResult.BLOCKED, f.poll(false, GOAL, f.owner));
            assertEquals("route-replaced", f.owner.lifecycle.reason());
            assertEquals(InteractionResult.BLOCKED, f.poll(false, GOAL, f.owner));
            assertEquals(GOAL.dx(1), f.target.get(null));
            assertNull(f.reservation.get());
            assertNotEquals(GOAL.dx(1), f.owner.ownedTarget.get());
            f.walker.verify(() -> Rs2Walker.clearWalkingRoute(anyString()), never());
        }
    }

    @Test public void routeExitAndUnreachableRemainBlockedRatherThanCancelled() throws Exception {
        for (WalkerState state : new WalkerState[]{WalkerState.EXIT, WalkerState.UNREACHABLE}) {
            try (Fixture f = new Fixture()) {
                f.walker.when(() -> Rs2Walker.walkWithStateUntil(eq(GOAL), eq(0), any(BooleanSupplier.class)))
                    .thenReturn(state);
                assertEquals(InteractionResult.BLOCKED, f.poll(false, GOAL, f.owner));
                String reason = state == WalkerState.EXIT ? "route-exit" : "route-unreachable";
                assertEquals(reason, f.owner.lifecycle.reason());
                Rs2Walker.cancelInteraction(f.owner);
                assertEquals(InteractionResult.BLOCKED, f.poll(false, GOAL, f.owner));
                assertEquals(reason, f.owner.lifecycle.reason());
                assertNull(f.reservation.get());
            }
        }
    }

    @Test public void arrivedNotReadyFailureRemainsBlockedAcrossPolls() throws Exception {
        try (Fixture f = new Fixture()) {
            long now = System.nanoTime() / 1_000_000;
            f.owner.lifecycle.update(now - 6000, GOAL, true, false, false, false, false);
            assertTrue(f.owner.lifecycle.stalledAtArrival(now));
            for (boolean banked : new boolean[]{false, true}) {
                assertEquals(InteractionResult.BLOCKED, f.poll(banked, GOAL, f.owner));
                assertEquals("arrived-not-ready", f.owner.lifecycle.reason());
                assertNull(f.reservation.get());
            }
        }
    }

    @Test public void invalidDestinationsAndClientThreadAreRejectedBeforeBinding() throws Exception {
        try (Fixture f = new Fixture()) {
            for (boolean banked : new boolean[]{false, true}) {
                for (WorldPoint invalid : new WorldPoint[]{null, new WorldPoint(-1, 3300, 0), new WorldPoint(3000, 3300, 4)}) {
                    InteractionRequest operation = request();
                    assertEquals(InteractionResult.FAILED, f.poll(banked, invalid, operation));
                    assertNull(operation.destination());
                    assertNull(f.reservation.get());
                }
                assertEquals(InteractionResult.FAILED, f.poll(banked, GOAL, null));
                when(f.client.isClientThread()).thenReturn(true);
                InteractionRequest operation = request();
                assertEquals(InteractionResult.FAILED, f.poll(banked, GOAL, operation));
                assertNull(operation.destination());
                when(f.client.isClientThread()).thenReturn(false);
            }
            verifyNoInteractions(f.runtime);
        }
    }

    @Test public void requestCannotRebindToAnotherGoal() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.owner.bind(GOAL));
            for (boolean banked : new boolean[]{false, true}) {
                assertEquals(InteractionResult.FAILED, f.poll(banked, GOAL.dx(1), f.owner));
                assertEquals(GOAL, f.owner.destination());
                assertNull(f.reservation.get());
            }
            verifyNoInteractions(f.runtime);
        }
    }

    @Test public void deadlineFailureRemainsFailedOnSubsequentPollsAndCancellation() throws Exception {
        try (Fixture f = new Fixture()) {
            long start = System.nanoTime() / 1_000_000 - 120001;
            f.owner.lifecycle.update(start, GOAL, true, false, false, false, false);
            for (boolean banked : new boolean[]{false, true}) {
                assertEquals(InteractionResult.FAILED, f.poll(banked, GOAL, f.owner));
                assertEquals("operation-deadline", f.owner.lifecycle.reason());
                Rs2Walker.cancelInteraction(f.owner);
                assertEquals(InteractionResult.FAILED, f.poll(banked, GOAL, f.owner));
                assertNull(f.reservation.get());
            }
        }
    }

    @Test public void observedSuccessAndExplicitCancellationAreStableAcrossOverloads() throws Exception {
        try (Fixture f = new Fixture()) {
            long now = System.nanoTime() / 1_000_000;
            f.owner.lifecycle.update(now, GOAL, true, false, true, false, false);
            f.owner.lifecycle.dispatched(now);
            assertEquals(InteractionLifecycle.Decision.SUCCEEDED,
                f.owner.lifecycle.update(now + 1, GOAL, true, false, false, true, false));
            Rs2Walker.cancelInteraction(f.owner);
            InteractionRequest cancelled = request();
            cancelled.cancel();
            for (boolean banked : new boolean[]{false, true}) {
                assertEquals(InteractionResult.SUCCEEDED, f.poll(banked, GOAL, f.owner));
                assertEquals(InteractionResult.CANCELLED, f.poll(banked, GOAL, cancelled));
                assertNull(f.reservation.get());
            }
        }
    }

    @Test public void instanceSnapshotCannotConfirmAnAlreadyDispatchedRequest() throws Exception {
        try (Fixture f = new Fixture()) {
            long now = System.nanoTime() / 1_000_000;
            f.owner.lifecycle.update(now, GOAL, true, false, true, false, false);
            f.owner.lifecycle.dispatched(now);
            InteractionTargetRuntime.Snapshot instance = snapshot(false, true);
            field(InteractionTargetRuntime.Snapshot.class, "confirmationAllowed").setBoolean(instance, false);
            when(f.runtime.probe(anyBoolean())).thenReturn(instance);
            assertEquals(InteractionResult.IN_PROGRESS, f.poll(false, GOAL, f.owner));
            assertTrue(f.owner.lifecycle.active());
            assertSame(f.owner, f.reservation.get());
        }
    }

    @Test public void legacyPredicateAndRecalculationStillWorkWithoutReservation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.target.set(null, GOAL);
            Rs2Walker.recalculatePath();
            f.routes.verify(() -> Rs2WalkerLifecycleRuntime.applyWalkerDestination(GOAL));
            f.walker.when(() -> Rs2Walker.clearWalkingRoute(anyString())).thenAnswer(call -> {
                f.target.set(null, null);
                return null;
            });
            assertEquals(WalkerState.ARRIVED, Rs2Walker.walkWithStateUntil(GOAL, 0, () -> true));
            assertNull(f.target.get(null));
            assertNull(f.reservation.get());
        }
    }

    @Test public void legacyRouteMutationDefersReservationWithoutProbingOrStealing() throws Exception {
        java.util.concurrent.atomic.AtomicInteger mutations = (java.util.concurrent.atomic.AtomicInteger)
            field(Rs2Walker.class, "routeMutations").get(null);
        int previous = mutations.getAndSet(1);
        try (Fixture f = new Fixture()) {
            assertEquals(InteractionResult.IN_PROGRESS, f.poll(false, GOAL, f.owner));
            assertEquals(InteractionResult.IN_PROGRESS, f.poll(true, GOAL, f.owner));
            assertNull(f.reservation.get());
            assertNull(f.target.get(null));
            verifyNoInteractions(f.runtime);
        } finally { mutations.set(previous); }
    }

    @Test public void bankedIntermediateOwnershipSurvivesRepeatedPolls() throws Exception {
        try (Fixture f = new Fixture()) {
            WorldPoint bank = GOAL.dx(20);
            f.walker.when(() -> Rs2Walker.walkWithBankedTransportsUntil(eq(GOAL), eq(0),
                same(f.owner.ownedTarget), any(BooleanSupplier.class))).thenAnswer(call -> {
                    f.owner.ownedTarget.set(bank);
                    f.target.set(null, bank);
                    return WalkerState.MOVING;
                });
            assertEquals(InteractionResult.IN_PROGRESS, f.poll(true, GOAL, f.owner));
            assertEquals(InteractionResult.IN_PROGRESS, f.poll(true, GOAL, f.owner));
            assertEquals(bank, f.owner.ownedTarget.get());
            assertEquals(bank, f.target.get(null));
            assertSame(f.owner, f.reservation.get());
            assertEquals(WalkerState.MOVING, Rs2Walker.walkWithStateTry(GOAL, 0, 0));
            verify(f.runtime, never()).dispatch(any(), any());
        }
    }

    @Test public void cleanupFailureStillReleasesReservationAndContext() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reserve(GOAL, GOAL);
            f.walker.when(() -> Rs2Walker.clearWalkingRoute(anyString()))
                .thenThrow(new IllegalStateException("test cleanup failure"));
            f.walker.clearInvocations();
            Rs2Walker.cancelInteraction(f.owner);
            assertNull(f.reservation.get());
            assertNull(f.owner.ownedTarget.get());
            assertNull(f.context.get());
            assertFalse(f.owner.active());
            assertEquals(GOAL, f.target.get(null));
        }
    }

    @Test public void cancellationDuringDispatchReturnsTerminalWithoutRecordingLateAttempt() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.runtime.probe(anyBoolean())).thenReturn(snapshot(true, false));
            when(f.runtime.dispatch(any(), any())).thenAnswer(call -> { f.owner.cancel(); return true; });
            assertEquals(InteractionResult.CANCELLED, f.poll(false, GOAL, f.owner));
            assertEquals(0, f.owner.lifecycle.attempts());
            assertNull(f.reservation.get());
        }
    }

    @Test public void cancelledBankLegStopsEvenThoughCompletionGoalIsDifferent() throws Exception {
        try (Fixture f = new Fixture()) {
            WorldPoint bank = GOAL.dx(20);
            f.reserve(bank, bank);
            f.context.set(f.owner);
            var cancelled = Rs2Walker.class.getDeclaredMethod("isWalkCancelled", WorldPoint.class);
            cancelled.setAccessible(true);
            assertFalse((boolean) cancelled.invoke(null, bank));
            f.owner.cancel();
            assertTrue((boolean) cancelled.invoke(null, bank));
        }
    }

    @Test public void cancellationDuringApproachIsNotMisreportedAsRouteFailure() throws Exception {
        try (Fixture f = new Fixture()) {
            f.walker.when(() -> Rs2Walker.walkWithStateUntil(eq(GOAL), eq(0), any(BooleanSupplier.class)))
                .thenAnswer(call -> { f.owner.cancel(); return WalkerState.EXIT; });
            assertEquals(InteractionResult.CANCELLED, f.poll(false, GOAL, f.owner));
            assertEquals("cancelled", f.owner.lifecycle.reason());
            assertNull(f.reservation.get());
        }
    }
}
