package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InteractionWalkCanvasGuardTest {
    private static final WorldPoint GOAL = new WorldPoint(3000, 3300, 0);
    private static final Point CLICK = new Point(50, 50);

    private static Field field(String name) throws Exception {
        Field field = Rs2Walker.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Microbot> micro = mockStatic(Microbot.class);
        final MockedStatic<InputArbiter> input = mockStatic(InputArbiter.class);
        final MockedStatic<Perspective> projection = mockStatic(Perspective.class);
        final MockedStatic<LocalPoint> local = mockStatic(LocalPoint.class);
        final MockedStatic<PointerState> pointer = mockStatic(PointerState.class);
        final MockedStatic<AwtEmitter> events = mockStatic(AwtEmitter.class);
        final Client client = mock(Client.class);
        final ClientThread thread = mock(ClientThread.class);
        final WorldView view = mock(WorldView.class);
        final LocalPoint tile = mock(LocalPoint.class);
        final VirtualMouse mouse = new VirtualMouse();
        @SuppressWarnings("unchecked")
        final AtomicReference<InteractionRequest> reservation =
                (AtomicReference<InteractionRequest>) field("activeInteraction").get(null);
        @SuppressWarnings("unchecked")
        final ThreadLocal<InteractionRequest> context =
                (ThreadLocal<InteractionRequest>) field("interactionOwner").get(null);
        final InteractionRequest previous = reservation.getAndSet(null);
        final InteractionRequest previousContext = context.get();
        final Object previousTarget = field("currentTarget").get(null);
        final NaturalMouse previousNaturalMouse = Microbot.naturalMouse;
        final boolean previousPause = Microbot.pauseAllScripts.getAndSet(false);
        final InteractionRequest owner = InteractionRequest.npc(1, "A", GOAL, "Talk-to", () -> false);

        Fixture() throws Exception {
            context.remove();
            // A different active route keeps this test in the owner's movement phase without scene probing.
            field("currentTarget").set(null, GOAL.dx(1));
            owner.bind(GOAL);
            Microbot.naturalMouse = null;
            micro.when(Microbot::getClient).thenReturn(client);
            micro.when(Microbot::getClientThread).thenReturn(thread);
            micro.when(Microbot::getMouse).thenReturn(mouse);
            when(client.getTopLevelWorldView()).thenReturn(view);
            when(client.getViewportWidth()).thenReturn(100);
            when(client.getViewportHeight()).thenReturn(100);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                    Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
            local.when(() -> LocalPoint.fromWorld(view, GOAL)).thenReturn(tile);
            projection.when(() -> Perspective.localToCanvas(client, tile, 0)).thenReturn(CLICK);
        }

        void assertNoPress() {
            events.verify(() -> AwtEmitter.pressed(anyInt(), anyInt(), anyInt()), never());
            events.verify(() -> AwtEmitter.clicked(anyInt(), anyInt(), anyInt()), never());
        }

        void assertClick() {
            events.verify(() -> AwtEmitter.pressed(50, 50, MouseEvent.BUTTON1));
            events.verify(() -> AwtEmitter.released(50, 50, MouseEvent.BUTTON1));
            events.verify(() -> AwtEmitter.clicked(50, 50, MouseEvent.BUTTON1));
        }

        @Override public void close() throws Exception {
            reservation.set(previous);
            if (previousContext == null) context.remove(); else context.set(previousContext);
            field("currentTarget").set(null, previousTarget);
            Microbot.naturalMouse = previousNaturalMouse;
            Microbot.pauseAllScripts.set(previousPause);
            events.close(); pointer.close(); local.close(); projection.close(); input.close(); micro.close();
        }
    }

    @Test public void reservationDuringProjectionRejectsPreviouslyUnreservedClick() throws Exception {
        try (Fixture f = new Fixture()) {
            f.projection.when(() -> Perspective.localToCanvas(f.client, f.tile, 0)).thenAnswer(call -> {
                f.reservation.set(f.owner);
                return CLICK;
            });
            assertNull(Rs2Walker.walkCanvas(GOAL));
            f.assertNoPress();
        }
    }

    @Test public void reservationAfterMovementRejectsClickAtFinalEmission() throws Exception {
        try (Fixture f = new Fixture()) {
            f.events.when(() -> AwtEmitter.moved(50, 50)).thenAnswer(call -> {
                f.reservation.set(f.owner);
                return null;
            });
            assertNull(Rs2Walker.walkCanvas(GOAL));
            f.events.verify(() -> AwtEmitter.moved(50, 50));
            f.assertNoPress();
        }
    }

    @Test public void currentOwnerAndUnreservedLegacyCallerCanClick() throws Exception {
        for (boolean owned : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                if (owned) { f.reservation.set(f.owner); f.context.set(f.owner); }
                assertSame(GOAL, Rs2Walker.walkCanvas(GOAL));
                f.assertClick();
            }
        }
    }

    @Test public void replacementOwnerAfterAdmissionRejectsClick() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reservation.set(f.owner);
            f.context.set(f.owner);
            f.events.when(() -> AwtEmitter.moved(50, 50)).thenAnswer(call -> {
                f.reservation.set(InteractionRequest.npc(2, "B", GOAL, "Talk-to", () -> false));
                return null;
            });
            assertNull(Rs2Walker.walkCanvas(GOAL));
            f.assertNoPress();
        }
    }

    @Test public void anotherOwnersReservationRejectsAtAdmission() throws Exception {
        try (Fixture f = new Fixture()) {
            f.reservation.set(f.owner);
            assertNull(Rs2Walker.walkCanvas(GOAL));
            f.projection.verifyNoInteractions();
            f.assertNoPress();
        }
    }

    @Test public void coveredOrUnavailableWidgetSnapshotFailsClosed() throws Exception {
        for (int overlay : new int[]{InterfaceID.ToplevelOsrsStretch.SIDE_MENU,
                InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER, InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER,
                InterfaceID.ToplevelPreEoc.SIDE_CONTAINER, InterfaceID.ToplevelPreEoc.CHAT_CONTAINER,
                InterfaceID.ToplevelPreEoc.MAP_CONTAINER}) {
            try (Fixture f = new Fixture()) {
                Widget widget = mock(Widget.class);
                when(widget.getBounds()).thenReturn(new Rectangle(40, 40, 20, 20));
                when(f.client.getWidget(overlay)).thenReturn(widget);
                assertNull(Rs2Walker.walkCanvas(GOAL));
                f.assertNoPress();
                when(widget.getBounds()).thenReturn(null);
                assertNull(Rs2Walker.walkCanvas(GOAL));
                f.assertNoPress();
            }
        }
        try (Fixture f = new Fixture()) {
            doReturn(Optional.empty()).when(f.thread).runOnClientThreadOptional(any());
            assertNull(Rs2Walker.walkCanvas(GOAL));
            f.assertNoPress();
        }
    }

    @Test public void widgetAppearingAfterMovementFailsClosed() throws Exception {
        try (Fixture f = new Fixture()) {
            Widget widget = mock(Widget.class);
            when(widget.getBounds()).thenReturn(new Rectangle(40, 40, 20, 20));
            f.events.when(() -> AwtEmitter.moved(50, 50)).thenAnswer(call -> {
                when(f.client.getWidget(InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER)).thenReturn(widget);
                return null;
            });
            assertNull(Rs2Walker.walkCanvas(GOAL));
            f.assertNoPress();
        }
    }
}
