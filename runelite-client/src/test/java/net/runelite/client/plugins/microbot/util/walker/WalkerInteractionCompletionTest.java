package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class WalkerInteractionCompletionTest {
    @Test public void bankLegResumesWithoutEvaluatingFinalInteraction() throws Exception {
        WorldPoint target = new WorldPoint(3000, 3300, 0);
        WorldPoint bank = new WorldPoint(3100, 3300, 0);
        Field field = Rs2Walker.class.getDeclaredField("currentTarget");
        field.setAccessible(true);
        Object previous = field.get(null);
        try (MockedStatic<Microbot> client = mockStatic(Microbot.class);
             MockedStatic<InputArbiter> input = mockStatic(InputArbiter.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            field.set(null, bank);
            AtomicInteger polls = new AtomicInteger();
            assertEquals(WalkerState.MOVING, Rs2Walker.walkWithBankedTransportsUntil(target, 4,
                new java.util.concurrent.atomic.AtomicReference<>(bank),
                () -> { polls.incrementAndGet(); return true; }));
            assertEquals(bank, field.get(null));
            assertEquals(0, polls.get());
        } finally { field.set(null, previous); }
    }

    @Test public void failedCallbackDoesNotLeakItsContextOrLock() throws Exception {
        WorldPoint target = new WorldPoint(3000, 3300, 0);
        Field field = Rs2Walker.class.getDeclaredField("currentTarget");
        field.setAccessible(true);
        Object previous = field.get(null);
        try (MockedStatic<Microbot> client = mockStatic(Microbot.class);
             MockedStatic<InputArbiter> input = mockStatic(InputArbiter.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            field.set(null, null);
            assertEquals(WalkerState.MOVING, Rs2Walker.walkWithBankedTransportsUntil(target, 4,
                () -> { throw new IllegalStateException("test"); }));
            assertEquals(WalkerState.ARRIVED, Rs2Walker.walkWithBankedTransportsUntil(target, 4, () -> true));
        } finally { field.set(null, previous); }
    }
    @Test public void ordinaryArrivalDoesNotWaitForWalkingPoseToClear() throws Exception {
        WorldPoint target = new WorldPoint(3000, 3300, 0);
        Field field = Rs2Walker.class.getDeclaredField("currentTarget");
        field.setAccessible(true);
        Object previous = field.get(null);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<InputArbiter> input = mockStatic(InputArbiter.class);
             MockedStatic<Global> waits = mockStatic(Global.class)) {
            field.set(null, target);
            player.when(Rs2Player::getWorldLocation).thenReturn(target);
            player.when(Rs2Player::isMoving).thenReturn(true);
            AtomicInteger polls = new AtomicInteger();
            waits.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenAnswer(call -> {
                polls.incrementAndGet();
                assertTrue(((BooleanSupplier) call.getArgument(0)).getAsBoolean());
                return true;
            });
            Rs2Walker.waitUntilIdleAfterSceneWalk(target, 10000, target, 1);
            assertEquals(1, polls.get());
        } finally { field.set(null, previous); }
    }

    @Test public void wrongPlaneAndShortWalkKeepWaitingButCancellationReleases() throws Exception {
        WorldPoint target = new WorldPoint(3000, 3300, 0);
        Field field = Rs2Walker.class.getDeclaredField("currentTarget");
        field.setAccessible(true);
        Object previous = field.get(null);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<InputArbiter> input = mockStatic(InputArbiter.class);
             MockedStatic<Global> waits = mockStatic(Global.class)) {
            field.set(null, target);
            player.when(Rs2Player::isMoving).thenReturn(true);
            waits.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenAnswer(call -> {
                BooleanSupplier condition = call.getArgument(0);
                player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3000, 3300, 1));
                assertFalse(condition.getAsBoolean());
                player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3003, 3300, 0));
                assertFalse(condition.getAsBoolean());
                field.set(null, null);
                assertTrue(condition.getAsBoolean());
                return true;
            });
            Rs2Walker.waitUntilIdleAfterSceneWalk(target, 10000, target, 1);
        } finally { field.set(null, previous); }
    }

    @Test public void bankedHandoffCannotCancelAnotherRouteOrHumanInput() throws Exception {
        WorldPoint target = new WorldPoint(3000, 3300, 0);
        WorldPoint other = new WorldPoint(3100, 3300, 0);
        Field field = Rs2Walker.class.getDeclaredField("currentTarget");
        field.setAccessible(true);
        Object previous = field.get(null);
        try (MockedStatic<Microbot> client = mockStatic(Microbot.class);
             MockedStatic<InputArbiter> input = mockStatic(InputArbiter.class)) {
            field.set(null, other);
            AtomicInteger polls = new AtomicInteger();
            assertEquals(WalkerState.EXIT, Rs2Walker.walkWithBankedTransportsUntil(target, 4,
                () -> { polls.incrementAndGet(); return true; }));
            assertEquals(other, field.get(null));
            assertEquals(0, polls.get());
            field.set(null, target);
            input.when(InputArbiter::isHuman).thenReturn(true);
            assertEquals(WalkerState.EXIT, Rs2Walker.walkWithBankedTransportsUntil(target, 4, () -> true));
            assertEquals(target, field.get(null));
        } finally { field.set(null, previous); }
    }
}
