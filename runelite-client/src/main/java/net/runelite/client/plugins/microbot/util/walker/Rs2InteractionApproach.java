package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.misc.SceneClickSafety;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

/**
 * Read-only object handoff checks, not interaction execution or confirmation.
 * Callers must re-query and retain their normal dispatch guards before acting.
 * Instances and secondary WorldViews fail closed until local reachability supports
 * a consistent coordinate space. No existing walker consumer is redirected here.
 */
public final class Rs2InteractionApproach {
    private static final int APPROACH_DISTANCE = 16;

    private Rs2InteractionApproach() {
    }

    public static boolean isReady(GameObject object) {
        return isReadyObject(object);
    }

    public static boolean isReady(WallObject object) {
        return isReadyObject(object);
    }

    private static boolean isReadyObject(TileObject object) {
        if (object == null || Thread.currentThread().isInterrupted()) return false;
        ClientThread clientThread = Microbot.getClientThread();
        if (clientThread == null) return false;
        try {
            return queryReadiness(object, clientThread);
        } catch (RuntimeException unavailableScene) {
            return false;
        }
    }

    private static boolean queryReadiness(TileObject object, ClientThread clientThread) {
        return clientThread.runOnClientThreadOptional(() -> {
            try {
                Client client = Microbot.getClient();
                if (client == null || client.getGameState() != GameState.LOGGED_IN) return false;
                WorldView view = object instanceof GameObject
                        ? ((GameObject) object).getWorldView() : ((WallObject) object).getWorldView();
                Player actor = client.getLocalPlayer();
                if (view == null || actor == null || actor.getWorldView() != view
                        || client.getTopLevelWorldView() != view || client.getWorldView(view.getId()) != view) return false;
                Scene scene = view.getScene();
                if (scene == null || scene.isInstance()) return false;
                WorldPoint player = actor.getWorldLocation();
                WorldPoint target = object.getWorldLocation();
                if (!withinRange(player, target) || target.getPlane() != view.getPlane()
                        || !isLiveObject(object, scene, view.getPlane())) return false;

                Rs2TileObjectCache cache = Microbot.getRs2TileObjectCache();
                TileObjectType type = object instanceof GameObject ? TileObjectType.GAME : TileObjectType.WALL;
                int id = object.getId();
                long hash = object instanceof GameObject
                        ? ((GameObject) object).getHash() : ((WallObject) object).getHash();
                if (cache == null || !cache.getStream().anyMatch(candidate -> candidate.getTileObjectType() == type
                        && candidate.getWorldView() == view && candidate.getId() == id
                        && candidate.getHash() == hash)) return false;
                Rectangle clearArea = SceneClickSafety.forObject(object, null);
                if (clearArea == null || clearArea.isEmpty()) return false;

                Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(player, APPROACH_DISTANCE);
                if (reachable == null || reachable.isEmpty()) return false;
                if (object instanceof GameObject) {
                    Rs2WorldPoint approach = Rs2Tile.getNearestWalkableTile((GameObject) object);
                    WorldPoint point = approach == null ? null : approach.getWorldPoint();
                    if (point == null || point.getPlane() != player.getPlane()) return false;
                    Integer distance = reachable.get(point);
                    return distance != null && distance >= 0 && distance <= APPROACH_DISTANCE;
                }
                return hasReachableWallApproach(target, reachable.keySet());
            } catch (RuntimeException unavailableScene) {
                return false;
            }
        }).orElse(false);
    }

    private static boolean isLiveObject(TileObject object, Scene scene, int plane) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            try {
                int x;
                int y;
                if (object instanceof GameObject) {
                    net.runelite.api.Point origin = ((GameObject) object).getSceneMinLocation();
                    if (origin == null) return false;
                    x = origin.getX();
                    y = origin.getY();
                } else {
                    LocalPoint point = ((WallObject) object).getLocalLocation();
                    if (point == null) return false;
                    x = point.getSceneX();
                    y = point.getSceneY();
                }
                // Inspect only the object's live anchor tile, not a second full scene scan.
                Tile[][][] tiles = scene.getTiles();
                if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null
                        || x < 0 || x >= tiles[plane].length || tiles[plane][x] == null
                        || y < 0 || y >= tiles[plane][x].length) return false;
                Tile tile = tiles[plane][x][y];
                if (tile == null) return false;
                if (object instanceof WallObject) return tile.getWallObject() == object;
                GameObject[] objects = tile.getGameObjects();
                return objects != null && Arrays.stream(objects).anyMatch(candidate -> candidate == object);
            } catch (RuntimeException unavailableScene) {
                return false;
            }
        }).orElse(false);
    }

    static boolean hasReachableWallApproach(WorldPoint target, Collection<WorldPoint> reachable) {
        return target != null && reachable != null && reachable.stream().anyMatch(tile -> tile != null
                && tile.getPlane() == target.getPlane()
                // Diagonal proximity alone can be a blocked corner, not wall access.
                && Math.abs(tile.getX() - target.getX()) + Math.abs(tile.getY() - target.getY()) <= 1);
    }

    static boolean withinRange(WorldPoint player, WorldPoint target) {
        return player != null && target != null && player.getPlane() == target.getPlane()
                && player.distanceTo2D(target) <= 12;
    }
}
