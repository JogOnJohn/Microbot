package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionLifecycle;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.runelite.client.plugins.microbot.util.walker.interaction.InteractionLifecycle.Decision.*;

public class InteractionLifecycleTest {
    private final WorldPoint p = new WorldPoint(3000, 3300, 0);
    @Test public void readyDispatchIsNotSuccessAndDuplicatePollsDoNotClick() {
        InteractionLifecycle s = new InteractionLifecycle();
        assertEquals(DISPATCH, s.update(100, p, true, false, true, true, false));
        s.dispatched(100);
        assertEquals(WAIT, s.update(700, p, true, false, true, false, false));
        assertEquals(SUCCEEDED, s.update(900, p, true, false, true, true, false));
        assertEquals(SUCCEEDED, s.update(1000, p, true, false, true, false, false));
    }
    @Test public void closedDoorApproachesAndDoorClosingAfterDispatchRecovers() {
        InteractionLifecycle s = new InteractionLifecycle();
        assertEquals(APPROACH, s.update(0, p, true, false, false, false, false));
        assertEquals(DISPATCH, s.update(100, p, true, false, true, false, false));
        s.dispatched(100);
        assertEquals(APPROACH, s.update(2600, p, true, false, false, false, false));
        assertEquals(WAIT, s.update(2700, p, true, false, true, false, false));
        assertEquals(APPROACH, s.update(3900, p, true, false, false, false, false));
    }
    @Test public void movementDoesNotRenewOperationOrAttemptDeadlines() {
        InteractionLifecycle s = new InteractionLifecycle();
        s.update(0, p, true, true, true, false, false); s.dispatched(0);
        assertEquals(WAIT, s.update(7999, p.dx(1), true, true, true, false, false));
        assertEquals(APPROACH, s.update(8000, p.dx(2), true, true, true, false, false));
        assertEquals(FAILED, s.update(120000, p.dx(3), true, true, true, false, false));
    }
    @Test public void threeUnconfirmedAttemptsHaveBoundedFailure() {
        InteractionLifecycle s = new InteractionLifecycle();
        for (int i = 0; i < 3; i++) {
            long t = i * 10000;
            s.update(t, p, true, false, true, false, false); s.dispatched(t);
            assertEquals(i == 2 ? FAILED : APPROACH, s.update(t + 8000, p, true, false, true, false, false));
        }
    }
    @Test public void pauseAndCancellationCannotDispatchLate() {
        InteractionLifecycle s = new InteractionLifecycle();
        assertEquals(WAIT, s.update(0, null, false, false, true, false, false));
        assertEquals(CANCELLED, s.update(1, p, true, false, true, false, true));
        assertFalse(s.active());
        assertEquals(CANCELLED, s.update(2, p, true, false, true, false, false));
    }
    @Test public void requestsAreIndependentEvenForSameCoordinatesAndCannotChangeGoal() {
        InteractionRequest a = InteractionRequest.npc(1, "A", p, "Talk-to", () -> false);
        InteractionRequest b = InteractionRequest.npc(1, "A", p, "Talk-to", () -> false);
        assertNotEquals(a.id, b.id); assertTrue(a.bind(p)); assertFalse(a.bind(p.dx(1)));
        a.cancel(); assertFalse(a.active()); assertTrue(b.active());
    }
}
