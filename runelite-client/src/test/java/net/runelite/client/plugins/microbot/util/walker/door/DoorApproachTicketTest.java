package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.door.model.AwaitTicket;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.runelite.client.plugins.microbot.util.walker.door.model.AwaitTicket.Progress.*;

public class DoorApproachTicketTest {
    private final WorldPoint from = new WorldPoint(1778, 3624, 0);
    private final WorldPoint to = new WorldPoint(1778, 3625, 0);
    private WorldPoint at(int x) { return new WorldPoint(x, 3624, 0); }

    @Test public void distantApproachDoesNotSpendTheConfirmationBudget() {
        AwaitTicket ticket = new AwaitTicket(1000, at(1766));
        assertEquals(WAITING, ticket.observe(at(1770), from, to, 2500));
        assertEquals(WAITING, ticket.observe(at(1774), from, to, 4000));
        assertEquals(WAITING, ticket.observe(at(1777), from, to, 5500));
        assertEquals(5500, ticket.confirmationStartedAtMs());
        assertEquals(WAITING, ticket.observe(at(1777), from, to, 8999));
        assertEquals(CONFIRMATION_TIMEOUT, ticket.observe(at(1777), from, to, 9000));
    }

    @Test public void movingAwayOrRepeatingPositionCannotRenewApproach() {
        AwaitTicket ticket = new AwaitTicket(1000, at(1766));
        assertEquals(WAITING, ticket.observe(at(1765), from, to, 2000));
        assertEquals(APPROACH_STALLED, ticket.observe(at(1766), from, to, 3400));
    }

    @Test public void continuedProgressStillHasAnAbsoluteCeiling() {
        AwaitTicket ticket = new AwaitTicket(1000, at(1700));
        for (int i = 1; i < 10; i++)
            assertEquals(WAITING, ticket.observe(at(1700 + i), from, to, 1000 + 1000 * i));
        assertEquals(DEADLINE_EXPIRED, ticket.observe(at(1710), from, to, 11000));
    }

    @Test public void changedSceneOrPlaneCannotBeDoorConfirmation() {
        AwaitTicket ticket = new AwaitTicket(1000, at(1766));
        assertEquals(INVALID, ticket.observe(null, from, to, 1500));
        assertEquals(INVALID, ticket.observe(new WorldPoint(1778, 3624, 1), from, to, 1500));
    }
}
