package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathStep;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;

/** Synchronous read-only bank-route probes; never publishes an active route or changes the executor. */
public final class TypedRoutePlanning
{
	private TypedRoutePlanning() { }

	public static Rs2RouteResult plan(Rs2RouteRequest request)
	{
		rejectClientThread();
		return planWithConfig(request, Rs2PathApi.getPathfinderConfig());
	}

	/** Captures a separate config for each invocation, including headless prepared fixtures. */
	public static Rs2RouteResult planWithConfig(Rs2RouteRequest request, PathfinderConfig config)
	{
		rejectClientThread();
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(config, "config");
		// An explicit policy cannot be silently ignored by a local config-backed search.
		if (request.getPolicy().isPresent())
		{
			throw new UnsupportedOperationException("explicit route policy is not yet supported by local probes");
		}
		if (Thread.currentThread().isInterrupted()) return cancelled(request);
		try
		{
			PathfinderConfig owned = config.copyForPlanning();
			if (Thread.currentThread().isInterrupted()) return cancelled(request);
			Boolean bankItems = request.getUseBankItems();
			boolean refresh = bankItems != null
				|| request.getRefreshPolicy() == Rs2RouteRequest.RefreshPolicy.ALWAYS
				|| (request.getRefreshPolicy() == Rs2RouteRequest.RefreshPolicy.IF_TRANSPORTS_EMPTY
					&& owned.getTransports().isEmpty() && owned.getUsableTeleports().isEmpty());
			if (refresh)
			{
				WorldPoint target = request.getRefreshTarget();
				if (target == null && request.getTargets().size() == 1) target = request.getTargets().iterator().next();
				// Null is intentional for multi-target queries: never borrow a live walker's destination.
				owned.refreshForPlanning(target, bankItems);
			}
			if (Thread.currentThread().isInterrupted()) return cancelled(request);
			Pathfinder search = new Pathfinder(owned, request.getStart(), request.getTargets());
			search.setPlanningBudgetMillis(owned.getRemainingPlanningMillis());
			search.run();
			return snapshot(request, search);
		}
		catch (CancellationException ex)
		{
			return cancelled(request);
		}
		catch (PathfinderConfig.PlanningTimeoutException ex)
		{
			return empty(request, Rs2RouteTermination.CUTOFF_REACHED);
		}
	}

	static void rejectClientThread()
	{
		if (Microbot.getClientThread() != null && Microbot.getClientThread().isClientThread())
		{
			throw new IllegalStateException("synchronous route planning must not run on the client thread");
		}
	}

	static Rs2RouteResult snapshot(Rs2RouteRequest request, Pathfinder search)
	{
		List<WorldPoint> path = List.copyOf(search.getPath());
		boolean exact = search.isExactSelectionAvailable();
		List<Rs2RouteStep> steps = new ArrayList<>();
		if (exact)
		{
			List<PathStep> selected = search.getSelectedSteps();
			if (selected.size() != path.size()) throw new IllegalStateException("selected steps/path differ");
			for (int i = 0; i < selected.size(); i++)
			{
				if (!path.get(i).equals(WorldPointUtil.unpackWorldPoint(selected.get(i).getPackedPosition())))
					throw new IllegalStateException("selected steps/path positions differ");
				if (i == 0) continue;
				PathStep step = selected.get(i);
				steps.add(step.getTransport() == null
					? Rs2RouteStep.walk(path.get(i - 1), path.get(i))
					: Rs2RouteStep.transport(path.get(i - 1), path.get(i), TransportEdgeAdapter.toEdge(step.getTransport())));
			}
		}
		Pathfinder.PathfinderStats stats = search.getStats();
		Rs2RouteMetrics metrics = new Rs2RouteMetrics(
			stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getElapsedTimeNanos(),
			Rs2RouteMetrics.UNAVAILABLE,
			stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getNodesChecked(),
			stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getTransportsChecked());
		return new Rs2RouteResult(request.getStart(), request.getTargets(), path, steps,
			termination(search.getTerminationReason()), metrics, exact);
	}

	static Rs2RouteTermination termination(String reason)
	{
		if (reason == null) return Rs2RouteTermination.FAILED;
		switch (reason)
		{
			case "reached-goal": return Rs2RouteTermination.TARGET_REACHED;
			case "time-cutoff": return Rs2RouteTermination.CUTOFF_REACHED;
			case "queues-drained":
			case "unreachable": return Rs2RouteTermination.SEARCH_EXHAUSTED;
			case "cancelled": return Rs2RouteTermination.CANCELLED;
			default: return Rs2RouteTermination.FAILED;
		}
	}

	private static Rs2RouteResult cancelled(Rs2RouteRequest request)
	{
		return empty(request, Rs2RouteTermination.CANCELLED);
	}

	private static Rs2RouteResult empty(Rs2RouteRequest request, Rs2RouteTermination termination)
	{
		return new Rs2RouteResult(request.getStart(), request.getTargets(), Collections.emptyList(),
			Collections.emptyList(), termination,
			new Rs2RouteMetrics(Rs2RouteMetrics.UNAVAILABLE, Rs2RouteMetrics.UNAVAILABLE,
				Rs2RouteMetrics.UNAVAILABLE, Rs2RouteMetrics.UNAVAILABLE), false);
	}
}
