package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.misc.SceneClickSafety;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionTargetRuntime;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class InteractionTargetRuntimeSafetyTest {
    private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint TARGET = new WorldPoint(3201, 3200, 0);

    @Test public void referenceGenerationsDistinguishReplacementsWithoutRetainingEntities() throws Exception {
        Class<?> type = Class.forName(InteractionTargetRuntime.class.getName() + "$WeakReferenceIdentity");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object stamp = constructor.newInstance();
        var of = type.getDeclaredMethod("of", Object.class);
        of.setAccessible(true);
        Object first = new Object(), replacement = new Object();
        long a = (long) of.invoke(stamp, first);
        assertEquals(a, (long) of.invoke(stamp, first));
        long b = (long) of.invoke(stamp, replacement);
        assertNotEquals(a, b);
        long c = (long) of.invoke(stamp, first);
        assertNotEquals(a, c);
        var reference = type.getDeclaredField("reference");
        reference.setAccessible(true);
        java.lang.ref.WeakReference<?> weak = (java.lang.ref.WeakReference<?>) reference.get(stamp);
        assertSame(first, weak.get());
        weak.clear();
        assertNotEquals(c, (long) of.invoke(stamp, first));
    }

    @Test public void freshActorPositionNotCachedPlayerStateControlsReadiness() {
        try (Fixture f = new Fixture()) {
            f.players.when(Rs2Player::getWorldLocation).thenReturn(PLAYER.dx(40));
            assertTrue(f.request.runtime.probe(true).ready);
            doAnswer(f.read(PLAYER.dx(40))).when(f.player).getWorldLocation();
            assertFalse(f.request.runtime.probe(false).ready);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicBoolean onThread = new AtomicBoolean(true);
        final Client client = mock(Client.class);
        final ClientThread thread = mock(ClientThread.class);
        final WorldView view = mock(WorldView.class);
        final Scene scene = mock(Scene.class);
        final Player player = mock(Player.class);
        final NPC npc = mock(NPC.class);
        final NPCComposition composition = mock(NPCComposition.class);
        @SuppressWarnings("unchecked")
        final IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
        final Rs2NpcModel model = mock(Rs2NpcModel.class);
        final Rs2NpcCache cache = mock(Rs2NpcCache.class, RETURNS_DEEP_STUBS);
        final MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
        final MockedStatic<Rs2Player> players = mockStatic(Rs2Player.class);
        final MockedStatic<Rs2Dialogue> dialogues = mockStatic(Rs2Dialogue.class);
        final MockedStatic<InputArbiter> input = mockStatic(InputArbiter.class);
        final MockedStatic<Rs2Tile> tiles = mockStatic(Rs2Tile.class);
        final MockedStatic<SceneClickSafety> clicks = mockStatic(SceneClickSafety.class);
        final boolean paused = Microbot.pauseAllScripts.getAndSet(false);
        final InteractionRequest request = InteractionRequest.npc(100, "Guide", TARGET, "Talk-to", () -> false);

        Fixture() {
            when(thread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
                boolean previous = onThread.getAndSet(true);
                try { return Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()); }
                finally { onThread.set(previous); }
            });
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
            when(client.getGameState()).thenAnswer(read(GameState.LOGGED_IN));
            when(client.getLocalPlayer()).thenAnswer(read(player));
            when(client.getTopLevelWorldView()).thenAnswer(read(view));
            when(client.getWorldView(0)).thenAnswer(read(view));
            when(client.getTickCount()).thenAnswer(read(42));
            when(player.getWorldView()).thenAnswer(read(view));
            when(player.getWorldLocation()).thenAnswer(read(PLAYER));
            when(view.getScene()).thenAnswer(read(scene));
            when(view.getPlane()).thenAnswer(read(0));
            when(view.getBaseX()).thenAnswer(read(3200));
            when(view.getBaseY()).thenAnswer(read(3200));
            when(view.isInstance()).thenAnswer(read(false));
            when(scene.isInstance()).thenAnswer(read(false));
            doAnswer(read(npcs)).when(view).npcs();
            when(npcs.byIndex(3)).thenAnswer(read(npc));
            when(npc.getIndex()).thenAnswer(read(3));
            when(npc.getId()).thenAnswer(read(100));
            when(npc.getWorldView()).thenAnswer(read(view));
            when(npc.getWorldLocation()).thenAnswer(read(TARGET));
            when(npc.getTransformedComposition()).thenAnswer(read(composition));
            when(composition.getActions()).thenAnswer(read(new String[]{"Talk-to"}));
            when(model.getNpc()).thenAnswer(read(npc));
            when(cache.query().withId(100).where(any()).nearest()).thenReturn(model);
            players.when(Rs2Player::getWorldLocation).thenReturn(PLAYER);
            clicks.when(() -> SceneClickSafety.forActor(npc, null)).thenAnswer(read(new Rectangle(20, 20, 8, 8)));
            tiles.when(() -> Rs2Tile.getReachableTilesFromTile(PLAYER, 10))
                    .thenReturn(new java.util.HashMap<>(Collections.singletonMap(TARGET, 1)));
            clearInvocations(cache);
            onThread.set(false);
        }

        <T> Answer<T> read(T value) {
            return inv -> { assertTrue("Raw scene read escaped CT callback", onThread.get()); return value; };
        }

        @Override public void close() {
            Microbot.pauseAllScripts.set(paused);
            clicks.close(); tiles.close(); input.close(); dialogues.close(); players.close(); microbot.close();
        }
    }

    @Test public void sameTickNpcDespawnCannotReuseReadySnapshot() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.request.runtime.probe(false).ready);
            doReturn(null).when(f.npcs).byIndex(3);
            InteractionTargetRuntime.Snapshot stale = f.request.runtime.probe(false);
            assertFalse(stale.ready);
            assertEquals(0, stale.identity);
            verify(f.cache, times(2)).query();
            assertTrue(f.request.runtime.cacheHits > 0);
            assertFalse(f.onThread.get());
        }
    }

    @Test public void sameTickOverlayChangeAndUnavailableGeometryFailClosed() {
        try (Fixture f = new Fixture()) {
            InteractionTargetRuntime.Snapshot expected = f.request.runtime.probe(false);
            assertTrue(expected.ready);
            assertTrue(expected.confirmationAllowed);
            f.clicks.when(() -> SceneClickSafety.forActor(f.npc, null)).thenReturn(null);
            assertFalse(f.request.runtime.probe(false).ready);
            assertFalse(f.request.runtime.dispatch(expected, () -> true));
            verify(f.model, never()).click(anyString(), any());
        }
    }

    @Test public void replacementNpcAtSameIndexCannotAuthorizeOldDispatch() {
        try (Fixture f = new Fixture()) {
            InteractionTargetRuntime.Snapshot expected = f.request.runtime.probe(true);
            assertTrue(expected.ready);
            doReturn(mock(NPC.class)).when(f.npcs).byIndex(3);
            assertFalse(f.request.runtime.dispatch(expected, () -> true));
            verify(f.model, never()).click(anyString(), any());
        }
    }

    @Test public void instancesRemainApproachableButCannotConfirm() {
        try (Fixture f = new Fixture()) {
            doReturn(true).when(f.view).isInstance();
            InteractionTargetRuntime.Snapshot instance = f.request.runtime.probe(false);
            assertEquals(PLAYER, instance.position);
            assertTrue(instance.sceneReady);
            assertFalse(instance.ready);
            assertFalse(instance.confirmationAllowed);
            verify(f.cache, never()).query();
        }
    }

    @Test public void unavailableAndThrowingCallbacksInvalidateReadiness() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.request.runtime.probe(false).ready);
            doReturn(Optional.empty()).when(f.thread).runOnClientThreadOptional(any());
            assertFalse(f.request.runtime.probe(false).confirmationAllowed);
            assertFalse(InteractionTargetRuntime.objectEdgeReachable(null, Collections.emptySet(), f.view));
            doThrow(new IllegalStateException("unavailable")).when(f.thread).runOnClientThreadOptional(any());
            assertFalse(f.request.runtime.probe(false).sceneReady);
            f.microbot.when(Microbot::getClientThread).thenReturn(null);
            assertFalse(f.request.runtime.probe(false).ready);
        }
    }

    @Test public void staleObjectModelIsRejectedDespiteMatchingIdAndHash() {
        try (Fixture f = new Fixture()) {
            Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class, RETURNS_DEEP_STUBS);
            GameObject original = mock(GameObject.class), replacement = mock(GameObject.class);
            Rs2TileObjectModel model = mock(Rs2TileObjectModel.class);
            Tile tile = mock(Tile.class);
            Tile[][][] sceneTiles = new Tile[1][2][1];
            sceneTiles[0][1][0] = tile;
            f.microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            when(cache.query().withId(100).where(any()).first()).thenReturn(model);
            when(model.getWorldView()).thenAnswer(f.read(f.view));
            when(model.getWorldLocation()).thenAnswer(f.read(TARGET));
            when(model.getHash()).thenAnswer(f.read(123L));
            when(model.represents(any())).thenAnswer(inv -> inv.getArgument(0) == original);
            when(f.scene.getTiles()).thenAnswer(f.read(sceneTiles));
            when(tile.getGameObjects()).thenAnswer(f.read(new GameObject[]{original}));
            when(original.getId()).thenReturn(100);
            when(original.getWorldView()).thenAnswer(f.read(f.view));
            when(replacement.getId()).thenReturn(100);
            when(original.getHash()).thenReturn(123L);
            when(replacement.getHash()).thenReturn(123L);
            InteractionRequest request = InteractionRequest.object(100, TARGET, PLAYER, "Open", () -> false);
            assertEquals(123L, request.runtime.probe(false).identity);
            doReturn(new GameObject[]{replacement}).when(tile).getGameObjects();
            assertFalse(request.runtime.probe(false).ready);
            assertEquals(0, request.runtime.probe(false).identity);
            f.clicks.verify(() -> SceneClickSafety.forObject(replacement, null), never());
            assertTrue(new Rs2TileObjectModel(original).represents(original));
            assertFalse(new Rs2TileObjectModel(original).represents(replacement));
        }
    }

    @Test public void footprintReadsStayOnClientThreadAndRejectOversizedObjects() {
        try (Fixture f = new Fixture()) {
            GameObject object = mock(GameObject.class);
            when(object.getWorldView()).thenAnswer(f.read(f.view));
            when(object.getSceneMinLocation()).thenAnswer(f.read(new net.runelite.api.Point(1, 1)));
            when(object.sizeX()).thenAnswer(f.read(2));
            when(object.sizeY()).thenAnswer(f.read(3));
            when(f.view.getSizeX()).thenAnswer(f.read(104));
            when(f.view.getSizeY()).thenAnswer(f.read(104));
            Rs2TileObjectModel model = new Rs2TileObjectModel(object);
            net.runelite.api.coords.WorldArea footprint = model.getInteractionFootprint();
            assertNotNull(footprint);
            assertEquals(3201, footprint.getX());
            assertEquals(2, footprint.getWidth());
            assertEquals(3, footprint.getHeight());
            doReturn(9).when(object).sizeX();
            assertNull(model.getInteractionFootprint());
            assertFalse(f.onThread.get());
        }
    }
}
