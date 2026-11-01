package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathStep;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderBackend;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.runelite.client.plugins.microbot.util.walker.WalkerApiAssertions.assertThrows;
import static org.mockito.Mockito.*;

public class TypedRoutePlanningTest
{
	@Test public void preparedProbePreservesBothBackendsAndSourceCatalog()
	{
		for (PathfinderBackend backend : PathfinderBackend.values())
		{
			PathfinderConfig source = TypedPlanningFixture.config(backend, true, false);
			Set<Transport> before = new LinkedHashSet<>(source.getTransports().get(TypedPlanningFixture.START));
			Rs2RouteResult result = TypedRoutePlanning.planWithConfig(Rs2RouteRequest.to(
				TypedPlanningFixture.START, TypedPlanningFixture.LONG).withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER), source);
			assertEquals(backend.toString(), Rs2RouteTermination.TARGET_REACHED, result.getTerminationReason());
			assertTrue(result.isTargetReached(0));
			assertTrue(backend.toString(), result.isExactTransportSelectionAvailable());
			assertEquals(before, source.getTransports().get(TypedPlanningFixture.START));
			assertFalse(source.isUseBankItems());
			assertSame(before.iterator().next(), TransportEdgeAdapter.toLegacy(result.getTransportSteps().get(0).getTransport().orElseThrow()));
		}
	}

	@Test public void indexedSnapshotsRetainDifferentSelectionsOnRepeatedDirectedPair()
	{
		WorldPoint a = TypedPlanningFixture.START, b = TypedPlanningFixture.SHORT;
		Transport first = new Transport(a, b, "first", TransportType.TRANSPORT, false, 1);
		Transport second = new Transport(a, b, "second", TransportType.TRANSPORT, false, 2);
		Pathfinder search = mock(Pathfinder.class);
		when(search.getPath()).thenReturn(List.of(a, b, a, b));
		when(search.isExactSelectionAvailable()).thenReturn(true);
		when(search.getTerminationReason()).thenReturn("reached-goal");
		when(search.getSelectedSteps()).thenReturn(List.of(new PathStep(WorldPointUtil.packWorldPoint(a), false),
			new PathStep(WorldPointUtil.packWorldPoint(b), false, first),
			new PathStep(WorldPointUtil.packWorldPoint(a), false),
			new PathStep(WorldPointUtil.packWorldPoint(b), false, second)));
		Rs2RouteResult result = TypedRoutePlanning.snapshot(Rs2RouteRequest.to(a, b), search);
		assertSame(first, TransportEdgeAdapter.toLegacy(result.getTransportSteps().get(0).getTransport().orElseThrow()));
		assertSame(second, TransportEdgeAdapter.toLegacy(result.getTransportSteps().get(1).getTransport().orElseThrow()));
		assertTrue("endpoint-only lookup must refuse repeated pairs", result.getTransportEdge(a, b).isEmpty());
		verify(search, never()).getTransportForStep(any(), any());
	}

	@Test public void legacySelectionIsUnavailableNotAssumedWalking()
	{
		Pathfinder search = mock(Pathfinder.class);
		when(search.getPath()).thenReturn(List.of(TypedPlanningFixture.START, TypedPlanningFixture.SHORT));
		when(search.getTerminationReason()).thenReturn("reached-goal");
		Rs2RouteResult result = TypedRoutePlanning.snapshot(Rs2RouteRequest.to(TypedPlanningFixture.START, TypedPlanningFixture.SHORT), search);
		assertFalse(result.isExactTransportSelectionAvailable());
		assertTrue(result.getSteps().isEmpty());
		assertThrows(IllegalStateException.class, result::getTransportSteps);
		verify(search, never()).getSelectedSteps();
		verify(search, never()).getTransportForStep(any(), any());
	}

	@Test public void completedSearchAndEndpointReachDoNotImplyPhysicalArrival()
	{
		for (Rs2RouteTermination termination : List.of(Rs2RouteTermination.SEARCH_EXHAUSTED, Rs2RouteTermination.CUTOFF_REACHED))
		{
			Rs2RouteResult result = new Rs2RouteResult(TypedPlanningFixture.START, Set.of(TypedPlanningFixture.LONG),
				List.of(TypedPlanningFixture.START), Collections.emptyList(), termination, metrics());
			assertTrue(result.isSearchCompleted());
			assertFalse(result.isTargetReached(0));
		}
		assertEquals(Rs2RouteTermination.FAILED, TypedRoutePlanning.termination(null));
		assertEquals(Rs2RouteTermination.FAILED, TypedRoutePlanning.termination("unknown"));
		assertEquals(Rs2RouteTermination.SEARCH_EXHAUSTED, TypedRoutePlanning.termination("queues-drained"));
	}

	@Test public void cancelledBeforeCaptureKeepsInterruptAndDoesNotTouchConfig()
	{
		PathfinderConfig config = mock(PathfinderConfig.class);
		Thread.currentThread().interrupt();
		try
		{
			Rs2RouteResult result = TypedRoutePlanning.planWithConfig(Rs2RouteRequest.to(TypedPlanningFixture.START, TypedPlanningFixture.SHORT), config);
			assertEquals(Rs2RouteTermination.CANCELLED, result.getTerminationReason());
			assertFalse(result.isSearchCompleted());
			assertTrue(Thread.currentThread().isInterrupted());
			verifyNoInteractions(config);
		}
		finally { Thread.interrupted(); }
	}

	@Test public void valuesDefensivelyCopyAndValidateTargetsStepsAndTolerance()
	{
		Set<WorldPoint> targets = new LinkedHashSet<>(List.of(TypedPlanningFixture.SHORT));
		Rs2RouteRequest request = Rs2RouteRequest.toAny(TypedPlanningFixture.START, targets);
		targets.clear();
		assertEquals(Set.of(TypedPlanningFixture.SHORT), request.getTargets());
		List<WorldPoint> path = new ArrayList<>(List.of(TypedPlanningFixture.START));
		Rs2RouteResult result = new Rs2RouteResult(request.getStart(), request.getTargets(), path, List.of(), Rs2RouteTermination.SEARCH_EXHAUSTED, metrics());
		path.clear();
		assertEquals(1, result.getPath().size());
		assertThrows(IllegalArgumentException.class, () -> result.isTargetReached(-1));
		assertThrows(IllegalArgumentException.class, () -> Rs2RouteRequest.toAny(request.getStart(), Set.of()));
	}

	private static Rs2RouteMetrics metrics() { return new Rs2RouteMetrics(0, -1, 0, 0); }
}
