package net.runelite.client.plugins.microbot.util.walker.banking;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectBankBoundTest {
    @Test public void provenBoundAvoidsBankLookupButCutoffStillChecksBankAlternatives() {
        for (String reason : List.of("reached-goal", "time-cutoff", "changed-availability")) {
            PathfinderConfig config = mock(PathfinderConfig.class);
            AtomicBoolean banked = new AtomicBoolean(false);
            when(config.isUseBankItems()).thenAnswer(call -> banked.get());
            doAnswer(call -> { banked.set(call.getArgument(0)); return null; })
                    .when(config).setUseBankItems(anyBoolean());
            WorldPoint start = new WorldPoint(3000, 3300, 0), target = new WorldPoint(3010, 3300, 0);
            if ("changed-availability".equals(reason))
                when(config.transportDecisionStamp(target)).thenReturn(List.of(1), List.of(2));
            else when(config.transportDecisionStamp(target)).thenReturn(List.of(1));
            try (MockedStatic<Rs2PathApi> api = mockStatic(Rs2PathApi.class);
                 MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
                 MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
                 MockedConstruction<Pathfinder> probes = mockConstruction(Pathfinder.class, (probe, context) -> {
                     assertFalse(banked.get());
                     when(probe.getPath()).thenReturn(List.of(start, target));
                     when(probe.isDone()).thenReturn(true);
                     when(probe.getTerminationReason()).thenReturn(
                             "changed-availability".equals(reason) ? "reached-goal" : reason);
                 })) {
                api.when(Rs2PathApi::getPathfinderConfig).thenReturn(config);
                walker.when(() -> Rs2Walker.getTotalTilesFromPath(anyList(), eq(target))).thenReturn(50);
                TransportRouteAnalysis result = Rs2WalkerBankingPlanner.compareRoutes(start, target, 24, 80, true);
                assertEquals("reached-goal".equals(reason), result.isBankComparisonSkipped());
                if (result.isBankComparisonSkipped()) bank.verifyNoInteractions();
                else bank.verify(() -> Rs2Bank.getPathAndBankToNearestBank(start, 20));
                assertFalse(banked.get());
            }
        }
    }
    @Test public void cancelledAndCutoffSearchesAreNotCompleteQuotes() {
        assertTrue(Rs2WalkerBankingPlanner.completedProbeReason(null));
        assertTrue(Rs2WalkerBankingPlanner.completedProbeReason("reached-goal"));
        assertFalse(Rs2WalkerBankingPlanner.completedProbeReason("time-cutoff"));
        assertFalse(Rs2WalkerBankingPlanner.completedProbeReason("unreachable"));
        assertFalse(Rs2WalkerBankingPlanner.completedProbeReason("cancelled"));
        assertFalse(Rs2WalkerBankingPlanner.completedProbeReason("backend-failure"));
    }
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
