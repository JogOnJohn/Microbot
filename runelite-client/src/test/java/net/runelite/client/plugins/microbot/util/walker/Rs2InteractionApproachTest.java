package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.misc.SceneClickSafety;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class Rs2InteractionApproachTest {
    private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint TARGET = new WorldPoint(3202, 3200, 0);
    private static final WorldPoint APPROACH = new WorldPoint(3201, 3200, 0);

    private static final class Fixture implements AutoCloseable {
        final AtomicBoolean onClientThread = new AtomicBoolean(true);
        final Client client = mock(Client.class);
        final ClientThread thread = mock(ClientThread.class);
        final Player player = mock(Player.class);
        final WorldView view = mock(WorldView.class);
        final Scene scene = mock(Scene.class);
        final Tile tile = mock(Tile.class);
        final GameObject game = mock(GameObject.class);
        final WallObject wall = mock(WallObject.class);
        final Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        final Rs2TileObjectModel gameModel = mock(Rs2TileObjectModel.class);
        final Rs2TileObjectModel wallModel = mock(Rs2TileObjectModel.class);
        final Rs2WorldPoint approach = mock(Rs2WorldPoint.class);
        final HashMap<WorldPoint, Integer> reachable = new HashMap<>();
        final Tile[][][] tiles = new Tile[1][2][3];
        final MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
        final MockedStatic<Rs2Tile> tileApi = mockStatic(Rs2Tile.class);
        final MockedStatic<SceneClickSafety> clicks = mockStatic(SceneClickSafety.class);
        final MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
        final MockedStatic<Rs2PathApi> paths = mockStatic(Rs2PathApi.class);

        Fixture() {
            when(thread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
                boolean previous = onClientThread.getAndSet(true);
                try { return Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()); }
                finally { onClientThread.set(previous); }
            });
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            when(client.getGameState()).thenAnswer(read(GameState.LOGGED_IN));
            when(client.getLocalPlayer()).thenAnswer(read(player));
            when(client.getTopLevelWorldView()).thenAnswer(read(view));
            when(client.getWorldView(7)).thenAnswer(read(view));
            when(player.getWorldView()).thenAnswer(read(view));
            when(player.getWorldLocation()).thenAnswer(read(PLAYER));
            when(view.getId()).thenAnswer(read(7));
            when(view.getPlane()).thenAnswer(read(0));
            when(view.getScene()).thenAnswer(read(scene));
            when(scene.isInstance()).thenAnswer(read(false));
            when(scene.getTiles()).thenAnswer(read(tiles));
            tiles[0][1][2] = tile;
            when(tile.getGameObjects()).thenAnswer(read(new GameObject[]{null, game}));
            when(tile.getWallObject()).thenAnswer(read(wall));
            when(game.getWorldView()).thenAnswer(read(view));
            when(game.getWorldLocation()).thenAnswer(read(TARGET));
            when(game.getSceneMinLocation()).thenAnswer(read(new Point(1, 2)));
            when(game.getId()).thenAnswer(read(100));
            when(game.getHash()).thenAnswer(read(1000L));
            when(wall.getWorldView()).thenAnswer(read(view));
            when(wall.getWorldLocation()).thenAnswer(read(TARGET));
            when(wall.getLocalLocation()).thenAnswer(read(LocalPoint.fromScene(1, 2)));
            when(wall.getId()).thenAnswer(read(200));
            when(wall.getHash()).thenAnswer(read(2000L));
            when(gameModel.getTileObjectType()).thenAnswer(read(TileObjectType.GAME));
            when(gameModel.getWorldView()).thenAnswer(read(view));
            when(gameModel.getId()).thenAnswer(read(100));
            when(gameModel.getHash()).thenAnswer(read(1000L));
            when(wallModel.getTileObjectType()).thenAnswer(read(TileObjectType.WALL));
            when(wallModel.getWorldView()).thenAnswer(read(view));
            when(wallModel.getId()).thenAnswer(read(200));
            when(wallModel.getHash()).thenAnswer(read(2000L));
            when(cache.getStream()).thenAnswer(inv -> {
                assertTrue(onClientThread.get());
                return Stream.of(gameModel, wallModel);
            });
            clicks.when(() -> SceneClickSafety.forObject(game, null)).thenReturn(new Rectangle(20, 20, 8, 8));
            clicks.when(() -> SceneClickSafety.forObject(wall, null)).thenReturn(new Rectangle(20, 20, 8, 8));
            reachable.put(APPROACH, 1);
            tileApi.when(() -> Rs2Tile.getReachableTilesFromTile(PLAYER, 16)).thenReturn(reachable);
            tileApi.when(() -> Rs2Tile.getNearestWalkableTile(game)).thenReturn(approach);
            when(approach.getWorldPoint()).thenAnswer(read(APPROACH));
            onClientThread.set(false);
        }

        private <T> Answer<T> read(T value) {
            return inv -> { assertTrue("Live read outside client-thread callback", onClientThread.get()); return value; };
        }

        void bothFalse() {
            assertFalse("game", Rs2InteractionApproach.isReady(game));
            assertFalse("wall", Rs2InteractionApproach.isReady(wall));
        }

        @Override public void close() {
            try { walker.verifyNoInteractions(); paths.verifyNoInteractions(); }
            finally {
                paths.close(); walker.close(); clicks.closeOnDemand(); tileApi.close(); microbot.close();
            }
        }
    }

    @Test public void bothTypesSucceedWithAllLiveReadsOnClientThreadAndFreshCacheStreams() {
        try (Fixture f = new Fixture()) {
            assertTrue(Rs2InteractionApproach.isReady(f.game));
            assertTrue(Rs2InteractionApproach.isReady(f.wall));
            assertTrue(Rs2InteractionApproach.isReady(f.game));
            verify(f.cache, times(3)).getStream();
            assertFalse(f.onClientThread.get());
        }
    }

    @Test public void nullObjectsFailWithoutClientThreadOrOtherReads() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            assertFalse(Rs2InteractionApproach.isReady((GameObject) null));
            assertFalse(Rs2InteractionApproach.isReady((WallObject) null));
            microbot.verifyNoInteractions();
        }
    }

    @Test public void missingClientThreadFailsClosed() {
        try (Fixture f = new Fixture()) {
            f.microbot.when(Microbot::getClientThread).thenReturn(null);
            f.bothFalse();
            verifyNoInteractions(f.game, f.wall);
        }
    }

    @Test public void unavailableClientThreadCallbackFailsClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(Optional.empty()).when(f.thread).runOnClientThreadOptional(any());
            f.bothFalse();
            verifyNoInteractions(f.game, f.wall);
        }
    }

    @Test public void clientThreadSchedulingRuntimeFailureFailsClosed() {
        try (Fixture f = new Fixture()) {
            doThrow(new IllegalStateException("client unavailable")).when(f.thread).runOnClientThreadOptional(any());
            f.bothFalse();
        }
    }

    @Test public void missingClientFailsClosed() {
        try (Fixture f = new Fixture()) {
            f.microbot.when(Microbot::getClient).thenReturn(null);
            f.bothFalse();
        }
    }

    @Test public void loadingAndLoggedOutClientsFailClosed() {
        try (Fixture f = new Fixture()) {
            for (GameState state : new GameState[]{GameState.LOADING, GameState.LOGIN_SCREEN}) {
                doReturn(state).when(f.client).getGameState();
                f.bothFalse();
            }
        }
    }

    @Test public void missingPlayerFailsClosed() {
        try (Fixture f = new Fixture()) { doReturn(null).when(f.client).getLocalPlayer(); f.bothFalse(); }
    }

    @Test public void missingObjectWorldViewFailsClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(null).when(f.game).getWorldView(); doReturn(null).when(f.wall).getWorldView(); f.bothFalse();
        }
    }

    @Test public void playerWorldViewMustBeTheExactObjectView() {
        try (Fixture f = new Fixture()) { doReturn(mock(WorldView.class)).when(f.player).getWorldView(); f.bothFalse(); }
    }

    @Test public void missingOrSecondaryTopLevelViewFailsClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(null).when(f.client).getTopLevelWorldView(); f.bothFalse();
            doReturn(mock(WorldView.class)).when(f.client).getTopLevelWorldView(); f.bothFalse();
        }
    }

    @Test public void missingOrReplacedActiveWorldViewFailsClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(null).when(f.client).getWorldView(7); f.bothFalse();
            doReturn(mock(WorldView.class)).when(f.client).getWorldView(7); f.bothFalse();
        }
    }

    @Test public void missingSceneAndInstancesFailClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(null).when(f.view).getScene(); f.bothFalse();
            doReturn(f.scene).when(f.view).getScene();
            doReturn(true).when(f.scene).isInstance(); f.bothFalse();
        }
    }

    @Test public void missingPlayerOrTargetPositionFailsClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(null).when(f.player).getWorldLocation(); f.bothFalse();
            doReturn(PLAYER).when(f.player).getWorldLocation();
            doReturn(null).when(f.game).getWorldLocation(); doReturn(null).when(f.wall).getWorldLocation(); f.bothFalse();
        }
    }

    @Test public void wrongViewPlaneFailsClosed() {
        try (Fixture f = new Fixture()) { doReturn(1).when(f.view).getPlane(); f.bothFalse(); }
    }

    @Test public void differentPlayerPlaneOrDistantPlayerFailsClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(new WorldPoint(3200, 3200, 1)).when(f.player).getWorldLocation(); f.bothFalse();
            doReturn(new WorldPoint(3189, 3200, 0)).when(f.player).getWorldLocation(); f.bothFalse();
        }
    }

    @Test public void staleSceneObjectsAreRejectedDespiteMatchingCachedModels() {
        try (Fixture f = new Fixture()) {
            doReturn(new GameObject[0]).when(f.tile).getGameObjects();
            doReturn(null).when(f.tile).getWallObject(); f.bothFalse();
        }
    }

    @Test public void sameIdAndHashReplacementDoesNotSatisfySceneIdentity() {
        try (Fixture f = new Fixture()) {
            GameObject replacement = mock(GameObject.class);
            WallObject replacementWall = mock(WallObject.class);
            when(replacement.getId()).thenReturn(100); when(replacement.getHash()).thenReturn(1000L);
            when(replacementWall.getId()).thenReturn(200); when(replacementWall.getHash()).thenReturn(2000L);
            doReturn(new GameObject[]{replacement}).when(f.tile).getGameObjects();
            doReturn(replacementWall).when(f.tile).getWallObject(); f.bothFalse();
        }
    }

    @Test public void nullAndRaggedSceneArraysFailClosed() {
        try (Fixture f = new Fixture()) {
            Tile[][][][] invalid = {null, new Tile[0][][], new Tile[1][][],
                new Tile[][][]{new Tile[0][]}, new Tile[][][]{new Tile[2][]},
                new Tile[][][]{new Tile[][]{null, new Tile[0]}},
                new Tile[][][]{new Tile[][]{null, new Tile[2]}}, new Tile[1][2][3]};
            for (Tile[][][] tiles : invalid) { doReturn(tiles).when(f.scene).getTiles(); f.bothFalse(); }
        }
    }

    @Test public void missingAnchorOrNegativeAndOutsideSceneCoordinatesFailClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(null).when(f.game).getSceneMinLocation(); doReturn(null).when(f.wall).getLocalLocation(); f.bothFalse();
            for (int x : new int[]{-1, 2, 104}) {
                doReturn(new Point(x, 2)).when(f.game).getSceneMinLocation();
                doReturn(LocalPoint.fromScene(x, 2)).when(f.wall).getLocalLocation(); f.bothFalse();
            }
        }
    }

    @Test public void nullGameObjectArrayFailsClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(null).when(f.tile).getGameObjects(); assertFalse(Rs2InteractionApproach.isReady(f.game));
        }
    }

    @Test public void missingSingletonCacheFailsClosed() {
        try (Fixture f = new Fixture()) { f.microbot.when(Microbot::getRs2TileObjectCache).thenReturn(null); f.bothFalse(); }
    }

    @Test public void cacheIsRequeriedRatherThanReusingPriorMatch() {
        try (Fixture f = new Fixture()) {
            assertTrue(Rs2InteractionApproach.isReady(f.game));
            doAnswer(inv -> Stream.empty()).when(f.cache).getStream(); f.bothFalse();
        }
    }

    @Test public void cacheRequiresMatchingTypeIdHashAndWorldView() {
        try (Fixture f = new Fixture()) {
            doReturn(TileObjectType.WALL).when(f.gameModel).getTileObjectType();
            doReturn(TileObjectType.GAME).when(f.wallModel).getTileObjectType(); f.bothFalse();
            doReturn(TileObjectType.GAME).when(f.gameModel).getTileObjectType();
            doReturn(TileObjectType.WALL).when(f.wallModel).getTileObjectType();
            doReturn(-1).when(f.gameModel).getId(); doReturn(-1).when(f.wallModel).getId(); f.bothFalse();
            doReturn(100).when(f.gameModel).getId(); doReturn(200).when(f.wallModel).getId();
            doReturn(-1L).when(f.gameModel).getHash(); doReturn(-1L).when(f.wallModel).getHash(); f.bothFalse();
            doReturn(1000L).when(f.gameModel).getHash(); doReturn(2000L).when(f.wallModel).getHash();
            doReturn(mock(WorldView.class)).when(f.gameModel).getWorldView();
            doReturn(mock(WorldView.class)).when(f.wallModel).getWorldView(); f.bothFalse();
        }
    }

    @Test public void offscreenCoveredOrEmptyClickAreasFailClosed() {
        try (Fixture f = new Fixture()) {
            for (Rectangle area : new Rectangle[]{null, new Rectangle(), new Rectangle(10, 10, -1, 4)}) {
                f.clicks.when(() -> SceneClickSafety.forObject(f.game, null)).thenReturn(area);
                f.clicks.when(() -> SceneClickSafety.forObject(f.wall, null)).thenReturn(area); f.bothFalse();
            }
        }
    }

    @Test public void sceneReadRuntimeFailureFailsClosed() {
        try (Fixture f = new Fixture()) { doThrow(new IllegalStateException("transition")).when(f.scene).getTiles(); f.bothFalse(); }
    }

    @Test public void cacheReadRuntimeFailureFailsClosed() {
        try (Fixture f = new Fixture()) { doThrow(new IllegalStateException("transition")).when(f.cache).getStream(); f.bothFalse(); }
    }

    @Test public void missingOrEmptyReachabilityFailsClosedBeforeNearestApproach() {
        try (Fixture f = new Fixture()) {
            f.reachable.clear(); f.bothFalse();
            f.tileApi.when(() -> Rs2Tile.getReachableTilesFromTile(PLAYER, 16)).thenReturn(null); f.bothFalse();
            f.tileApi.verify(() -> Rs2Tile.getNearestWalkableTile(f.game), never());
        }
    }

    @Test public void nullApproachOrNullApproachPointFailsClosed() {
        try (Fixture f = new Fixture()) {
            doReturn(null).when(f.approach).getWorldPoint(); assertFalse(Rs2InteractionApproach.isReady(f.game));
            f.tileApi.when(() -> Rs2Tile.getNearestWalkableTile(f.game)).thenReturn(null);
            assertFalse(Rs2InteractionApproach.isReady(f.game));
        }
    }

    @Test public void unreachableNegativeNullAndOverBudgetApproachDistancesFailClosed() {
        try (Fixture f = new Fixture()) {
            f.reachable.clear(); f.reachable.put(PLAYER, 0);
            assertFalse(Rs2InteractionApproach.isReady(f.game));
            for (Integer distance : new Integer[]{null, -1, 17}) {
                f.reachable.put(APPROACH, distance); assertFalse(Rs2InteractionApproach.isReady(f.game));
            }
            for (int distance : new int[]{0, 16}) {
                f.reachable.put(APPROACH, distance); assertTrue(Rs2InteractionApproach.isReady(f.game));
            }
        }
    }

    @Test public void reachableApproachOnAnotherPlaneIsRejected() {
        try (Fixture f = new Fixture()) {
            WorldPoint upstairs = new WorldPoint(3201, 3200, 1);
            doReturn(upstairs).when(f.approach).getWorldPoint(); f.reachable.put(upstairs, 1);
            assertFalse(Rs2InteractionApproach.isReady(f.game));
        }
    }

    @Test public void wallRequiresReachableSamePlaneAdjacentTileNotNearestGameObjectHelper() {
        try (Fixture f = new Fixture()) {
            f.reachable.clear(); f.reachable.put(PLAYER, 0); assertFalse(Rs2InteractionApproach.isReady(f.wall));
            f.reachable.put(new WorldPoint(3202, 3200, 1), 1); assertFalse(Rs2InteractionApproach.isReady(f.wall));
            f.reachable.put(new WorldPoint(3203, 3200, 0), 2); assertTrue(Rs2InteractionApproach.isReady(f.wall));
            f.tileApi.verify(() -> Rs2Tile.getNearestWalkableTile(f.game), never());
        }
    }

    @Test public void withinRangeRequiresSamePlaneAndIncludesTwelveTileBoundary() {
        assertTrue(Rs2InteractionApproach.withinRange(PLAYER, new WorldPoint(3212, 3212, 0)));
        assertFalse(Rs2InteractionApproach.withinRange(PLAYER, new WorldPoint(3213, 3200, 0)));
        assertFalse(Rs2InteractionApproach.withinRange(PLAYER, new WorldPoint(3200, 3200, 1)));
        assertFalse(Rs2InteractionApproach.withinRange(null, TARGET));
        assertFalse(Rs2InteractionApproach.withinRange(PLAYER, null));
    }

    @Test public void wallHelperHandlesNullsEmptyWrongPlaneAndCardinalAdjacency() {
        assertFalse(Rs2InteractionApproach.hasReachableWallApproach(null, Collections.singleton(TARGET)));
        assertFalse(Rs2InteractionApproach.hasReachableWallApproach(TARGET, null));
        assertFalse(Rs2InteractionApproach.hasReachableWallApproach(TARGET, Collections.emptyList()));
        assertFalse(Rs2InteractionApproach.hasReachableWallApproach(TARGET,
            Arrays.asList(null, PLAYER, new WorldPoint(3202, 3200, 1))));
        assertTrue(Rs2InteractionApproach.hasReachableWallApproach(TARGET, Collections.singleton(TARGET)));
        assertTrue(Rs2InteractionApproach.hasReachableWallApproach(TARGET,
            Arrays.asList(null, new WorldPoint(3203, 3200, 0))));
    }

    @Test public void diagonalOnlyReachabilityCannotAuthorizeABlockedCornerWallApproach() {
        try (Fixture f = new Fixture()) {
            f.reachable.clear();
            f.reachable.put(new WorldPoint(3203, 3201, 0), 2);
            assertFalse(Rs2InteractionApproach.isReady(f.wall));
            assertFalse(Rs2InteractionApproach.hasReachableWallApproach(TARGET, f.reachable.keySet()));
            f.reachable.put(new WorldPoint(3203, 3200, 0), 3);
            assertTrue(Rs2InteractionApproach.isReady(f.wall));
        }
    }

    @Test public void interruptedCallerFailsWithoutSchedulingAndRetainsInterruptFlag() {
        try (Fixture f = new Fixture()) {
            boolean previouslyInterrupted = Thread.currentThread().isInterrupted();
            try {
                Thread.currentThread().interrupt();
                f.bothFalse();
                assertTrue(Thread.currentThread().isInterrupted());
                verify(f.thread, never()).runOnClientThreadOptional(any());
                verifyNoInteractions(f.game, f.wall, f.cache);
            } finally {
                Thread.interrupted();
                if (previouslyInterrupted) Thread.currentThread().interrupt();
            }
        }
    }

    @Test public void realSceneClickSafetyRejectsCoveredUnknownAndEmptyGeometry() {
        try (Fixture f = new Fixture()) {
            f.clicks.close();
            when(f.game.getClickbox()).thenAnswer(f.read(new Rectangle(20, 20, 8, 8)));
            when(f.wall.getClickbox()).thenAnswer(f.read(new Rectangle(20, 20, 8, 8)));
            when(f.client.getViewportWidth()).thenAnswer(f.read(800));
            when(f.client.getViewportHeight()).thenAnswer(f.read(600));
            when(f.client.getViewportXOffset()).thenAnswer(f.read(0));
            when(f.client.getViewportYOffset()).thenAnswer(f.read(0));
            when(f.client.getWidget(anyInt())).thenAnswer(f.read(null));
            assertTrue(Rs2InteractionApproach.isReady(f.game));
            assertTrue(Rs2InteractionApproach.isReady(f.wall));

            Widget cover = mock(Widget.class);
            when(cover.isHidden()).thenAnswer(f.read(false));
            when(cover.getBounds()).thenAnswer(f.read(new Rectangle(0, 0, 100, 100)));
            doAnswer(f.read(cover)).when(f.client).getWidget(anyInt());
            f.bothFalse();
            doReturn(null).when(cover).getBounds();
            f.bothFalse();
            doAnswer(f.read(null)).when(f.client).getWidget(anyInt());
            doReturn(0).when(f.client).getViewportWidth();
            f.bothFalse();
            assertFalse(f.onClientThread.get());
        }
    }
}
