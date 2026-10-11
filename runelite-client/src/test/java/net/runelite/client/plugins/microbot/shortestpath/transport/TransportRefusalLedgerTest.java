package net.runelite.client.plugins.microbot.shortestpath.transport;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;
import static org.junit.Assert.*;

public class TransportRefusalLedgerTest {
    private static final WorldPoint ORIGIN = new WorldPoint(3222, 3218, 0);
    private static final WorldPoint DESTINATION = new WorldPoint(3222, 9618, 0);

    public static Transport transport(int objectId) {
        return new Transport(ORIGIN, DESTINATION, "Trapdoor", TransportType.TRANSPORT,
                false, "Climb-down", "Trapdoor", objectId);
    }

    private TransportRefusalLedger ledger() {
        TransportRefusalLedger ledger = new TransportRefusalLedger();
        ledger.beginWalk(DESTINATION);
        return ledger;
    }

    private boolean fail(TransportRefusalLedger ledger, Transport transport, boolean clicked, long now) {
        return ledger.failure(ledger.generation(), transport, ORIGIN, ORIGIN, clicked, true, now);
    }

    @Test public void twoRefusedInteractionsBlockOnce() {
        TransportRefusalLedger ledger = ledger();
        Transport transport = transport(100);
        assertFalse(fail(ledger, transport, true, 1000));
        assertTrue(fail(ledger, transport, true, 2000));
        assertTrue(ledger.isBlocked(transport));
        assertFalse(fail(ledger, transport, true, 3000));
        assertEquals(1, ledger.revision());
    }

    @Test public void fallthroughRequiresThreeIndependentAttempts() {
        TransportRefusalLedger ledger = ledger();
        Transport transport = transport(100);
        assertFalse(fail(ledger, transport, false, 1000));
        assertFalse(fail(ledger, transport, false, 2000));
        assertTrue(fail(ledger, transport, false, 3000));
    }

    @Test public void fallthroughAndClickedStrikesAreSeparate() {
        TransportRefusalLedger ledger = ledger();
        Transport transport = transport(100);
        assertFalse(fail(ledger, transport, false, 1000));
        assertFalse(fail(ledger, transport, true, 2000));
        assertFalse(fail(ledger, transport, false, 3000));
        assertTrue(fail(ledger, transport, true, 4000));
    }

    @Test public void repeatedObservationInOneTickDoesNotStrikeOut() {
        TransportRefusalLedger ledger = ledger();
        Transport transport = transport(100);
        for (long now = 1000; now < 1600; now += 100) assertFalse(fail(ledger, transport, true, now));
        assertTrue(fail(ledger, transport, true, 1600));
    }

    @Test public void sharedEndpointsDoNotBlockAlternativeObjectOrAction() {
        TransportRefusalLedger ledger = ledger();
        Transport transport = transport(100), otherObject = transport(101), otherAction = transport(100);
        otherAction.setAction("Enter");
        fail(ledger, transport, true, 1000);
        fail(ledger, transport, true, 2000);
        assertFalse(ledger.isBlocked(otherObject));
        assertFalse(ledger.isBlocked(otherAction));
        assertTrue("a refresh may recreate an equivalent transport row", ledger.isBlocked(transport(100)));
    }

    @Test public void successClearsBothStrikeCounters() {
        TransportRefusalLedger ledger = ledger();
        Transport transport = transport(100);
        fail(ledger, transport, true, 1000);
        fail(ledger, transport, false, 2000);
        ledger.success(transport);
        assertFalse(fail(ledger, transport, true, 3000));
        assertFalse(ledger.isBlocked(transport));
    }

    @Test public void recalcKeepsExclusionsButCancellationAndNewWalkClearThem() {
        TransportRefusalLedger ledger = ledger();
        Transport transport = transport(100);
        fail(ledger, transport, true, 1000);
        fail(ledger, transport, true, 2000);
        ledger.beginWalk(DESTINATION);
        assertTrue(ledger.isBlocked(transport));
        ledger.beginWalk(null);
        ledger.beginWalk(DESTINATION);
        assertFalse(ledger.isBlocked(transport));
        fail(ledger, transport, true, 3000);
        fail(ledger, transport, true, 4000);
        ledger.beginWalk(new WorldPoint(3200, 3200, 0));
        assertFalse(ledger.isBlocked(transport));
    }

    @Test public void cancelledAttemptCannotPoisonNewWalkWithSameGoal() {
        TransportRefusalLedger ledger = ledger();
        long oldGeneration = ledger.generation();
        ledger.beginWalk(null);
        ledger.beginWalk(DESTINATION);
        for (int i = 1; i <= 4; i++) assertFalse(ledger.failure(oldGeneration, transport(100),
                ORIGIN, ORIGIN, true, true, i * 1000));
        assertFalse(ledger.isBlocked(transport(100)));
    }

    @Test public void motionDialogueAndUnloadedSceneDoNotCount() {
        TransportRefusalLedger ledger = ledger();
        for (int i = 1; i <= 4; i++) assertFalse(ledger.failure(ledger.generation(), transport(100),
                ORIGIN, ORIGIN, true, false, i * 1000));
        assertFalse(fail(ledger, transport(100), true, 5000));
    }

    @Test public void progressAndFloorChangesDoNotCountAsRefusal() {
        TransportRefusalLedger ledger = ledger();
        for (WorldPoint after : new WorldPoint[] { DESTINATION, new WorldPoint(3224, 3218, 0),
                new WorldPoint(3222, 3218, 1) }) {
            for (int i = 1; i <= 3; i++) assertFalse(ledger.failure(ledger.generation(), transport(100),
                    ORIGIN, after, true, true, i * 1000));
        }
        assertFalse(fail(ledger, transport(100), true, 5000));
    }

    @Test public void approachingFromOutsideOriginDoesNotCount() {
        TransportRefusalLedger ledger = ledger();
        for (int i = 1; i <= 4; i++) assertFalse(ledger.failure(ledger.generation(), transport(100),
                new WorldPoint(3225, 3218, 0), ORIGIN, true, true, i * 1000));
    }

    @Test public void existingPohSpiritTreeAndSeasonalRecoveryKeepOwnership() {
        for (TransportType type : new TransportType[] {TransportType.POH, TransportType.SPIRIT_TREE,
                TransportType.SEASONAL_TRANSPORT, TransportType.TELEPORTATION_SPELL}) {
            assertFalse(TransportRefusalLedger.eligible(new Transport(ORIGIN, DESTINATION, "Protected",
                    type, false, 1)));
        }
        assertFalse(TransportRefusalLedger.eligible(new Transport(null, DESTINATION, "Global",
                TransportType.TELEPORTATION_ITEM, false, 1)));
    }
}
