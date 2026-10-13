package net.runelite.client.plugins.microbot.util.walker.banking;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectBankBoundTest {
    @Test public void nearbyPartialOrWrongPlaneDoesNotProveCompletion() {
        WorldPoint start = new WorldPoint(3000, 3300, 0), target = new WorldPoint(3010, 3300, 0);
        assertTrue(Rs2WalkerBankingPlanner.completeDirectProbe(List.of(start, target), start, target));
        assertFalse(Rs2WalkerBankingPlanner.completeDirectProbe(List.of(start,
                new WorldPoint(3009, 3300, 0)), start, target));
        assertFalse(Rs2WalkerBankingPlanner.completeDirectProbe(List.of(start,
                new WorldPoint(3010, 3300, 1)), start, target));
        assertFalse(Rs2WalkerBankingPlanner.completeDirectProbe(List.of(target), start, target));
        assertFalse(Rs2WalkerBankingPlanner.completeDirectProbe(List.of(), start, target));
    }

    @Test public void skippedComparisonIsNotReportedAsNoBankFound() {
        TransportRouteAnalysis quote = TransportRouteAnalysis.directByBound(List.of(), 50);
        assertTrue(quote.isBankComparisonSkipped());
        assertEquals(50, quote.getDirectDistance());
        assertTrue(quote.getAnalysis().contains("lower bound"));
    }
}
