package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.state.LocalApproachState;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalApproachStateTest {
    private final WorldPoint goal = new WorldPoint(3237, 3484, 0);
    private final WorldPoint origin = new WorldPoint(3243, 3474, 0);

    @Test
    public void movingApproachSurvivesTicksWithoutNewClicksAndExpiresOnStaleProgress() {
        LocalApproachState state = new LocalApproachState();
        state.dispatched(goal, 4, origin, 1000);
        assertTrue(state.advancing(goal, 4, origin, true, true, 1500));
        WorldPoint progressed = new WorldPoint(3243, 3476, 0);
        assertTrue(state.advancing(goal, 4, progressed, true, true, 2500));
        assertFalse(state.advancing(goal, 4, progressed, true, true, 4300));
    }

    @Test
    public void stoppedBlockedChangedPlaneRadiusGoalAndCancellationReleaseApproach() {
        LocalApproachState state = new LocalApproachState();
        state.dispatched(goal, 4, origin, 1000);
        assertFalse(state.advancing(goal, 4, origin, false, true, 1200));
        state.dispatched(goal, 4, origin, 1000);
        assertFalse(state.advancing(goal, 4, origin, true, false, 1200));
        state.dispatched(goal, 4, origin, 1000);
        assertFalse(state.advancing(goal, 4, new WorldPoint(3243, 3474, 1), true, true, 1200));
        state.dispatched(goal, 4, origin, 1000);
        assertFalse(state.advancing(goal, 2, origin, true, true, 1200));
        state.dispatched(goal, 4, origin, 1000);
        assertFalse(state.advancing(origin, 4, origin, true, true, 1200));
        state.dispatched(goal, 4, origin, 1000);
        state.clear();
        assertFalse(state.advancing(goal, 4, origin, true, true, 1200));
    }

    @Test
    public void motionCannotKeepLocalOwnershipAliveIndefinitely() {
        LocalApproachState state = new LocalApproachState();
        state.dispatched(goal, 4, origin, 1000);
        assertFalse(state.advancing(goal, 4, goal, true, true, 9000));
        assertFalse(state.advancing(null, 4, origin, true, true, 1000));
    }
}
