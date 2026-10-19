package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class Rs2WalkerDirectLocalApproachTest {
    private final WorldPoint outside = new WorldPoint(2658, 3323, 0);
    private final WorldPoint target = new WorldPoint(2659, 3322, 0);

    @Test public void noellaLongDetourMustUseNormalDoorAwareRoute() {
        assertFalse(Rs2Walker.directLocalPathWithinBudget(outside, target, 21));
        assertFalse(Rs2Walker.directLocalPathWithinBudget(outside, target, null));
        assertTrue(Rs2Walker.directLocalPathWithinBudget(outside, target, 1));
    }

    @Test public void smallCornersRemainEligibleButUnknownAndWrongPlaneDoNot() {
        assertTrue(Rs2Walker.directLocalPathWithinBudget(outside, target, 5));
        assertFalse(Rs2Walker.directLocalPathWithinBudget(outside, target, 6));
        assertFalse(Rs2Walker.directLocalPathWithinBudget(outside, target, -1));
        assertFalse(Rs2Walker.directLocalPathWithinBudget(outside, new WorldPoint(2659, 3322, 1), 1));
        assertFalse(Rs2Walker.directLocalPathWithinBudget(null, target, 1));
        assertFalse(Rs2Walker.directLocalPathWithinBudget(outside, null, 1));
    }

    @Test public void stationaryAcceptedClickFallsThroughRatherThanStarvingRouteExecution() {
        assertTrue(Rs2Walker.directLocalClickMadeNoProgress(outside, outside, false));
        assertTrue(Rs2Walker.directLocalClickMadeNoProgress(outside, null, false));
        assertFalse(Rs2Walker.directLocalClickMadeNoProgress(outside, outside, true));
        assertFalse(Rs2Walker.directLocalClickMadeNoProgress(outside, target, false));
    }
}
