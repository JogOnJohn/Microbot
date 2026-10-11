package net.runelite.client.plugins.microbot.util.poh;

import java.util.List;
import java.util.Comparator;
import java.util.function.Predicate;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.AnimationID;

final class PohPoolInteraction {
    private PohPoolInteraction() { }

    static GameObject nearest(List<GameObject> pools, LocalPoint player,
            Predicate<GameObject> reachable) {
        if (player == null) return null;
        return pools.stream()
                .filter(pool -> pool.getSceneMinLocation() != null && pool.getSceneMaxLocation() != null)
                .sorted(Comparator.comparingInt(pool -> distance(pool, player)))
                .filter(reachable).findFirst().orElse(null);
    }

    static int distance(GameObject pool, LocalPoint player) {
        Point min = pool.getSceneMinLocation(), max = pool.getSceneMaxLocation();
        int x = player.getSceneX(), y = player.getSceneY();
        return Math.max(0, Math.max(Math.max(min.getX() - x, x - max.getX()),
                Math.max(min.getY() - y, y - max.getY())));
    }

    static boolean isDrinking(int animation, int distance, boolean sameHouse) {
        return sameHouse && distance <= 2 && distance >= 0
                && (animation == AnimationID.POH_POOL_DRINK || animation == AnimationID.POH_POOL_DRINK_SILENT);
    }

    static boolean isFinished(int animation, int distance, boolean sameHouse, boolean moving) {
        return sameHouse && distance >= 0 && distance <= 2 && animation == -1 && !moving;
    }
}
