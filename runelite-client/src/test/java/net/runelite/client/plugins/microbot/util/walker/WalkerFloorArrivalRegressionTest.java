package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Method;
import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WalkerFloorArrivalRegressionTest {
    @Test public void rossUpstairsArrivalDoesNotScanOrClickAnotherLadder() throws Exception {
        WorldPoint target = new WorldPoint(2616, 3315, 1);
        WorldPoint landing = new WorldPoint(2617, 3316, 1);
        Method method = Rs2Walker.class.getDeclaredMethod("tryDirectShortWalk", WorldPoint.class,
                int.class, List.class, List.class, boolean.class);
        method.setAccessible(true);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Microbot> client = mockStatic(Microbot.class);
             MockedStatic<Rs2PathApi> paths = mockStatic(Rs2PathApi.class);
             MockedStatic<Rs2GameObject> objects = mockStatic(Rs2GameObject.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(landing);
            paths.when(Rs2PathApi::getPathfinderMutex).thenReturn(new Object());
            List<WorldPoint> route = Arrays.asList(landing, target);
            assertEquals(WalkerState.ARRIVED, method.invoke(null, target, 2, route, route, false));
            objects.verifyNoInteractions();
        }
    }

    @Test public void nearbyCrossFloorRouteCanSkipBankComparisonButWrongLandingCannot() {
        WorldPoint start = new WorldPoint(2614, 3318, 0);
        WorldPoint target = new WorldPoint(2616, 3315, 1);
        assertTrue(Rs2Walker.isShortDirectBankRoute(start, target, Arrays.asList(start, target)));
        assertFalse(Rs2Walker.isShortDirectBankRoute(start, target, Collections.singletonList(start)));
        assertFalse(Rs2Walker.isShortDirectBankRoute(start, target, Collections.emptyList()));
        assertFalse(Rs2Walker.isShortDirectBankRoute(start, target,
                Collections.singletonList(new WorldPoint(2700, 3315, 1))));
        assertFalse(Rs2Walker.isShortDirectBankRoute(start, target, Collections.nCopies(700, target)));
    }
}
