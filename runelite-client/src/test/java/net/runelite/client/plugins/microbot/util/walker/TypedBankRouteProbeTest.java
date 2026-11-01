package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.banking.Rs2WalkerBankingPlanner;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static net.runelite.client.plugins.microbot.util.walker.WalkerApiAssertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TypedBankRouteProbeTest {
    private static final WorldPoint A = new WorldPoint(3000, 3300, 0);
    private static final WorldPoint B = A.dx(10);
    private static final Rs2RouteMetrics METRICS = new Rs2RouteMetrics(1, -1, 1, 1);

    @Test public void probeRetainsOrderedSelectionsForRepeatedEndpointPairs() {
        Transport first = new Transport(A, B, "first", TransportType.TRANSPORT, false, 1);
        Transport second = new Transport(A, B, "second", TransportType.TRANSPORT, false, 2);
        Rs2TransportEdge edge1 = TransportEdgeAdapter.toEdge(first), edge2 = TransportEdgeAdapter.toEdge(second);
        Rs2RouteResult result = new Rs2RouteResult(A, Set.of(B), List.of(A, B, A, B), List.of(
                Rs2RouteStep.transport(A, B, edge1), Rs2RouteStep.walk(B, A),
                Rs2RouteStep.transport(A, B, edge2)), Rs2RouteTermination.TARGET_REACHED, METRICS);
        try (MockedStatic<Rs2PathApi> api = mockStatic(Rs2PathApi.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(A);
            api.when(() -> Rs2PathApi.plan(any())).thenAnswer(call -> {
                Rs2RouteRequest request = call.getArgument(0);
                assertEquals(A, request.getStart());
                assertEquals(Set.of(B), request.getTargets());
                assertEquals(Boolean.TRUE, request.getUseBankItems());
                return result;
            });
            List<Rs2TransportEdge> edges = Rs2WalkerBankingPlanner.getTransportEdgesForDestination(B, true);
            assertEquals(List.of(edge1, edge2), edges);
            assertSame(first, TransportEdgeAdapter.toLegacy(edges.get(0)));
            assertSame(second, TransportEdgeAdapter.toLegacy(edges.get(1)));
            api.verify(() -> Rs2PathApi.plan(any()), times(1));
            api.verifyNoMoreInteractions();
        }
    }

    @Test public void completedPartialAndWrongPlaneProbesCannotBecomePreparedJourneys() {
        for (Rs2RouteTermination reason : Rs2RouteTermination.values()) {
            WorldPoint endpoint = reason == Rs2RouteTermination.TARGET_REACHED ? B.dz(1) : A;
            Rs2RouteResult result = new Rs2RouteResult(A, Set.of(B), List.of(endpoint), List.of(), reason, METRICS);
            try (MockedStatic<Rs2PathApi> api = mockStatic(Rs2PathApi.class);
                 MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
                player.when(Rs2Player::getWorldLocation).thenReturn(A);
                api.when(() -> Rs2PathApi.plan(any())).thenReturn(result);
                assertThrows(IllegalStateException.class,
                        () -> Rs2WalkerBankingPlanner.getTransportEdgesForDestination(B, false));
            }
        }
    }

    @Test public void unavailableLegacySelectionsCannotLookLikeANoTransportJourney() {
        Rs2RouteResult result = new Rs2RouteResult(A, Set.of(B), List.of(A, B), List.of(),
                Rs2RouteTermination.TARGET_REACHED, METRICS, false);
        try (MockedStatic<Rs2PathApi> api = mockStatic(Rs2PathApi.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(A);
            api.when(() -> Rs2PathApi.plan(any())).thenReturn(result);
            assertThrows(UnsupportedOperationException.class,
                    () -> Rs2WalkerBankingPlanner.getTransportEdgesForDestination(B, true));
        }
    }

    @Test public void missingDestinationOrPlayerFailsBeforePlanning() {
        try (MockedStatic<Rs2PathApi> api = mockStatic(Rs2PathApi.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            assertThrows(IllegalArgumentException.class,
                    () -> Rs2WalkerBankingPlanner.getTransportEdgesForDestination(null, true));
            assertThrows(IllegalStateException.class,
                    () -> Rs2WalkerBankingPlanner.getTransportEdgesForDestination(B, true));
            api.verifyNoInteractions();
        }
    }
}
