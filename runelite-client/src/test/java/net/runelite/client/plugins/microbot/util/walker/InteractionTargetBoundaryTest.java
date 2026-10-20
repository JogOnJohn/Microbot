package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionResult;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class InteractionTargetBoundaryTest {
    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Microbot> micro = mockStatic(Microbot.class);
        final MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
        final MockedStatic<Rs2Tile> tiles = mockStatic(Rs2Tile.class);
        final MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
        final MockedStatic<InputArbiter> input = mockStatic(InputArbiter.class);
        final Client c = mock(Client.class);
        final ClientThread ct = mock(ClientThread.class);
        final WorldView view = mock(WorldView.class);
        final WorldPoint p = new WorldPoint(3242, 3450, 0), n = p.dy(2);
        final NPC npc = mock(NPC.class);
        final NPCComposition definition = mock(NPCComposition.class);
        final Rs2NpcModel model = mock(Rs2NpcModel.class);
        final AtomicReference<Rs2NpcModel> selected = new AtomicReference<>(model);
        final HashMap<WorldPoint, Integer> reachable = new HashMap<>();
        final InteractionRequest request = InteractionRequest.npc(10415, "Jeff", n, "Talk-to", () -> false);
        Fixture() throws Exception {
            when(ct.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
            micro.when(Microbot::getClient).thenReturn(c);
            micro.when(Microbot::getClientThread).thenReturn(ct);
            when(c.getTopLevelWorldView()).thenReturn(view);
            when(view.getScene()).thenReturn(mock(Scene.class));
            when(c.getLocalPlayer()).thenReturn(mock(Player.class));
            when(c.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(c.getTickCount()).thenReturn(100);
            when(c.getViewportWidth()).thenReturn(100);
            when(c.getViewportHeight()).thenReturn(100);
            player.when(Rs2Player::getWorldLocation).thenReturn(p);
            when(npc.getWorldView()).thenReturn(view);
            when(npc.getWorldLocation()).thenReturn(n);
            when(npc.getConvexHull()).thenReturn(new Rectangle(10, 10, 20, 20));
            when(definition.getActions()).thenReturn(new String[]{"Talk-to"});
            when(npc.getTransformedComposition()).thenReturn(definition);
            when(model.getNpc()).thenReturn(npc);
            when(model.click(eq("Talk-to"), any())).thenAnswer(call ->
                ((BooleanSupplier) call.getArgument(1)).getAsBoolean());
            Rs2NpcCache cache = mock(Rs2NpcCache.class, RETURNS_DEEP_STUBS);
            micro.when(Microbot::getRs2NpcCache).thenReturn(cache);
            when(cache.query().withId(10415).where(any()).nearest()).thenAnswer(call -> selected.get());
            reachable.put(n, 2);
            tiles.when(() -> Rs2Tile.getReachableTilesFromTile(p, 10)).thenReturn(reachable);
        }
        @Override public void close() {
            input.close(); dialogue.close(); tiles.close(); player.close(); micro.close();
        }
    }

    @Test public void wrongPlaneWorldViewMissingActionAndOffscreenCannotDispatch() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.npc.getWorldLocation()).thenReturn(f.n.dz(1));
            assertFalse(f.request.runtime.probe(true).ready);
            when(f.npc.getWorldLocation()).thenReturn(f.n);
            when(f.npc.getWorldView()).thenReturn(mock(WorldView.class));
            assertFalse(f.request.runtime.probe(true).ready);
            when(f.npc.getWorldView()).thenReturn(f.view);
            when(f.definition.getActions()).thenReturn(new String[]{"Examine"});
            assertFalse(f.request.runtime.probe(true).ready);
            when(f.definition.getActions()).thenReturn(new String[]{"Talk-to"});
            when(f.npc.getConvexHull()).thenReturn(new Rectangle(200, 200, 20, 20));
            assertFalse(f.request.runtime.probe(true).ready);
            verify(f.model, never()).click(anyString(), any());
        }
    }

    @Test public void recycledNpcIsRejectedButSafelyMovingNpcCanBeReacquired() throws Exception {
        try (Fixture f = new Fixture()) {
            var ready = f.request.runtime.probe(false);
            NPC replacement = mock(NPC.class);
            when(replacement.getWorldView()).thenReturn(f.view);
            when(replacement.getWorldLocation()).thenReturn(f.n);
            when(replacement.getConvexHull()).thenReturn(new Rectangle(10, 10, 20, 20));
            when(replacement.getTransformedComposition()).thenReturn(f.definition);
            when(f.model.getNpc()).thenReturn(replacement);
            assertFalse(f.request.runtime.dispatch(ready, () -> true));
            verify(f.model, never()).click(anyString(), any());
            when(f.model.getNpc()).thenReturn(f.npc);
            when(f.npc.getWorldLocation()).thenReturn(f.n.dx(1));
            f.reachable.put(f.n.dx(1), 3);
            assertTrue(f.request.runtime.dispatch(ready, () -> true));
        }
    }

    @Test public void disappearanceAndSceneReplacementInvalidateMemoizedReadiness() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.request.runtime.probe(false).ready);
            f.selected.set(null);
            assertFalse(f.request.runtime.probe(true).ready);
            f.selected.set(f.model);
            assertTrue(f.request.runtime.probe(true).ready);
            f.reachable.clear();
            when(f.view.getScene()).thenReturn(mock(Scene.class));
            assertFalse(f.request.runtime.probe(false).ready);
        }
    }

    @Test public void doorClosesOrOwnerCancelsDuringPointerApproach() throws Exception {
        try (Fixture f = new Fixture()) {
            var ready = f.request.runtime.probe(false);
            when(f.model.click(eq("Talk-to"), any())).thenAnswer(call -> {
                f.reachable.clear();
                return ((BooleanSupplier) call.getArgument(1)).getAsBoolean();
            });
            assertFalse(f.request.runtime.dispatch(ready, () -> true));
            f.reachable.put(f.n, 2);
            AtomicBoolean owned = new AtomicBoolean(true);
            when(f.model.click(eq("Talk-to"), any())).thenAnswer(call -> {
                owned.set(false);
                return ((BooleanSupplier) call.getArgument(1)).getAsBoolean();
            });
            assertFalse(f.request.runtime.dispatch(ready, owned::get));
        }
    }

    @Test public void unrelatedDialogueAndSelectedWidgetBlockApproachWithoutConfirmation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(true);
            Widget name = mock(Widget.class);
            when(name.getText()).thenReturn("Amy");
            when(f.c.getWidget(WidgetInfo.DIALOG_NPC_NAME)).thenReturn(name);
            var snapshot = f.request.runtime.probe(true);
            assertTrue(snapshot.inputBlocked); assertFalse(snapshot.ready); assertFalse(snapshot.confirmed);
            when(name.getText()).thenReturn("<col=ffffff>Jeff</col>");
            assertTrue(f.request.runtime.probe(true).confirmed);
            f.dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);
            when(f.c.isWidgetSelected()).thenReturn(true);
            assertTrue(f.request.runtime.probe(true).inputBlocked);
            assertFalse(f.request.runtime.probe(true).ready);
        }
    }

    @Test public void probesMeasureClientWorkSeparatelyAndMemoizationAvoidsRepeatedCollision() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.request.runtime.probe(true).ready);
            assertTrue(f.request.runtime.probe(false).ready);
            assertEquals(2, f.request.runtime.clientReadSamples);
            assertEquals(1, f.request.runtime.targetQueries);
            assertEquals(1, f.request.runtime.collisionProbes);
            assertEquals(1, f.request.runtime.cacheHits);
            assertTrue(f.request.runtime.probeNanos >= f.request.runtime.clientReadNanos);
            assertTrue(f.request.runtime.clientP95UpperMicros() <= f.request.runtime.maxClientReadNanos / 1000);
        }
    }

    @Test public void readyTargetUsesNoApproachRouteAndConfirmationReleasesBothOverloads() throws Exception {
        for (boolean banked : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(); MockedStatic<Rs2PathApi> path = mockStatic(Rs2PathApi.class)) {
                Field ownerField = Rs2Walker.class.getDeclaredField("activeInteraction");
                Field targetField = Rs2Walker.class.getDeclaredField("currentTarget");
                ownerField.setAccessible(true); targetField.setAccessible(true);
                @SuppressWarnings("unchecked") AtomicReference<InteractionRequest> owner =
                    (AtomicReference<InteractionRequest>) ownerField.get(null);
                InteractionRequest previousOwner = owner.getAndSet(null);
                Object previousTarget = targetField.get(null); targetField.set(null, null);
                try {
                    assertEquals(InteractionResult.IN_PROGRESS, banked
                        ? Rs2Walker.walkWithBankedTransportsAndState(f.n, 0, f.request)
                        : Rs2Walker.walkTo(f.n, f.request));
                    assertSame(f.request, owner.get());
                    InteractionRequest competing = InteractionRequest.npc(10415, "Jeff", f.n, "Talk-to", () -> false);
                    assertEquals(InteractionResult.BLOCKED, Rs2Walker.walkTo(f.n, competing));
                    f.dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(true);
                    Widget name = mock(Widget.class); when(name.getText()).thenReturn("Jeff");
                    when(f.c.getWidget(WidgetInfo.DIALOG_NPC_NAME)).thenReturn(name);
                    when(f.c.getTickCount()).thenReturn(101);
                    assertEquals(InteractionResult.SUCCEEDED, banked
                        ? Rs2Walker.walkWithBankedTransportsAndState(f.n, 0, f.request)
                        : Rs2Walker.walkTo(f.n, f.request));
                    assertNull(owner.get());
                    verify(f.model, times(1)).click(eq("Talk-to"), any());
                    path.verifyNoInteractions();
                } finally {
                    Rs2Walker.cancelInteraction(f.request);
                    owner.set(previousOwner); targetField.set(null, previousTarget);
                }
            }
        }
    }
}
