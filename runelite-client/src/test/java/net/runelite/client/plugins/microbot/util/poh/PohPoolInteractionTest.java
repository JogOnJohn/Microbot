package net.runelite.client.plugins.microbot.util.poh;

import java.util.List;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PohPoolInteractionTest {
    private GameObject pool(int x, int y, int plane) {
        GameObject pool = mock(GameObject.class);
        when(pool.getPlane()).thenReturn(plane);
        when(pool.getSceneMinLocation()).thenReturn(new Point(x, y));
        when(pool.getSceneMaxLocation()).thenReturn(new Point(x + 1, y + 1));
        return pool;
    }

    @Test public void nearbyPoolWinsRegardlessOfSceneEnumerationOrder() {
        GameObject far = pool(20, 70, 0), near = pool(50, 52, 0);
        LocalPoint player = LocalPoint.fromScene(50, 50);
        assertSame(near, PohPoolInteraction.nearest(List.of(far, near), player, p -> true));
        verify(near, never()).getWorldLocation();
    }

    @Test public void blockedNearbyPoolIsExcludedFromCurrentPlaneCandidates() {
        GameObject blocked = pool(50, 52, 0), usable = pool(55, 50, 0);
        assertSame(usable, PohPoolInteraction.nearest(List.of(blocked, usable),
                LocalPoint.fromScene(50, 50), p -> p != blocked));
        assertNull(PohPoolInteraction.nearest(List.of(blocked), LocalPoint.fromScene(50, 50), p -> false));
    }

    @Test public void unrelatedAnimationAndHouseExitCannotConfirmDrink() {
        assertTrue(PohPoolInteraction.isDrinking(AnimationID.POH_POOL_DRINK, 1, true));
        assertTrue(PohPoolInteraction.isDrinking(AnimationID.POH_POOL_DRINK_SILENT, 2, true));
        assertFalse(PohPoolInteraction.isDrinking(714, 1, true));
        assertFalse(PohPoolInteraction.isDrinking(AnimationID.POH_POOL_DRINK, 20, true));
        assertFalse(PohPoolInteraction.isDrinking(AnimationID.POH_POOL_DRINK, 1, false));
    }

    @Test public void leavingHouseOrPoolCannotConfirmCompletion() {
        assertTrue(PohPoolInteraction.isFinished(-1, 1, true, false));
        assertFalse(PohPoolInteraction.isFinished(-1, 1, false, false));
        assertFalse(PohPoolInteraction.isFinished(-1, 20, true, false));
        assertFalse(PohPoolInteraction.isFinished(-1, 1, true, true));
        assertFalse(PohPoolInteraction.isFinished(714, 1, true, false));
    }
}
