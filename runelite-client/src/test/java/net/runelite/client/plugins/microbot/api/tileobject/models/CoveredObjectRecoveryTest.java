package net.runelite.client.plugins.microbot.api.tileobject.models;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.misc.SceneClickSafety;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.*;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

public class CoveredObjectRecoveryTest {
    private MockedStatic<Microbot> bot;
    private MockedStatic<Rs2Camera> camera;
    private MockedStatic<SceneClickSafety> safety;
    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<InputArbiter> input;
    private Client client;
    private TileObject object;
    private final Rectangle area = new Rectangle(20, 20, 10, 10);
    private boolean oldPause;
    private final WorldPoint target = new WorldPoint(3010, 3000, 0);

    @Before public void setup() throws Exception {
        oldPause = Microbot.pauseAllScripts.get();
        Microbot.pauseAllScripts.set(false);
        client = mock(Client.class);
        object = mock(TileObject.class);
        WorldView view = mock(WorldView.class);
        Scene scene = mock(Scene.class);
        Player player = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldView()).thenReturn(view);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3000, 3000, 0));
        when(object.getWorldView()).thenReturn(view);
        when(object.getWorldLocation()).thenReturn(target);
        when(view.getScene()).thenReturn(scene);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(inv -> Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
        bot = mockStatic(Microbot.class);
        bot.when(Microbot::getClient).thenReturn(client);
        bot.when(Microbot::getClientThread).thenReturn(thread);
        camera = mockStatic(Rs2Camera.class);
        safety = mockStatic(SceneClickSafety.class);
        walker = mockStatic(Rs2Walker.class);
        input = mockStatic(InputArbiter.class);
    }

    @After public void teardown() {
        input.close(); walker.close(); safety.close(); camera.close(); bot.close();
        Microbot.pauseAllScripts.set(oldPause);
        Thread.interrupted();
    }

    @Test public void tighterTurnCanExposeTargetWithoutWalking() {
        safety.when(() -> SceneClickSafety.forObject(object, null)).thenReturn(area);
        assertSame(area, Rs2TileObjectModel.recoverCoveredClick(object, () -> true));
        camera.verify(() -> Rs2Camera.turnTo(object, 5));
        walker.verify(() -> Rs2Walker.walkWithStateTry(target, 2, 0), never());
    }

    @Test public void nestedWalkerDoesNotStartRecovery() {
        walker.when(Rs2Walker::isWalkingOnCurrentThread).thenReturn(true);
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, () -> true));
        camera.verifyNoInteractions();
    }

    @Test public void cancelledOwnerDoesNotTurnOrWalk() {
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, () -> false));
        camera.verifyNoInteractions();
    }

    @Test public void cancellationDuringTurnPreventsWalking() {
        AtomicBoolean valid = new AtomicBoolean(true);
        camera.when(() -> Rs2Camera.turnTo(object, 5)).thenAnswer(inv -> { valid.set(false); return null; });
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, valid::get));
        walker.verify(() -> Rs2Walker.walkWithStateTry(target, 2, 0), never());
    }

    @Test public void cancellationDuringWalkPreventsFinalGeometry() {
        AtomicBoolean valid = new AtomicBoolean(true);
        walker.when(() -> Rs2Walker.walkWithStateTry(target, 2, 0)).thenAnswer(inv -> { valid.set(false); return WalkerState.ARRIVED; });
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, valid::get));
        safety.verify(() -> SceneClickSafety.forObject(object, null), times(1));
    }

    @Test public void vanishedTargetIsNotClickedAfterCloserWalk() {
        walker.when(() -> Rs2Walker.walkWithStateTry(target, 2, 0)).thenReturn(WalkerState.ARRIVED);
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, () -> true));
        walker.verify(() -> Rs2Walker.walkWithStateTry(target, 2, 0));
        safety.verify(() -> SceneClickSafety.forObject(object, null), times(1));
    }

    @Test public void recoveryDoesNotReenterForDoorClicks() {
        walker.when(() -> Rs2Walker.walkWithStateTry(target, 2, 0)).thenAnswer(inv -> {
            assertNull(Rs2TileObjectModel.recoverCoveredClick(object, () -> true));
            return WalkerState.EXIT;
        });
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, () -> true));
        camera.verify(() -> Rs2Camera.turnTo(object, 5), times(1));
    }

    @Test public void clientThreadAndHumanInputFailClosed() {
        when(client.isClientThread()).thenReturn(true);
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, () -> true));
        when(client.isClientThread()).thenReturn(false);
        input.when(InputArbiter::isHuman).thenReturn(true);
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, () -> true));
        camera.verifyNoInteractions();
    }

    @Test public void pausedAndInterruptedFailClosed() {
        Microbot.pauseAllScripts.set(true);
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, () -> true));
        Microbot.pauseAllScripts.set(false);
        Thread.currentThread().interrupt();
        assertNull(Rs2TileObjectModel.recoverCoveredClick(object, () -> true));
        camera.verifyNoInteractions();
    }
}
