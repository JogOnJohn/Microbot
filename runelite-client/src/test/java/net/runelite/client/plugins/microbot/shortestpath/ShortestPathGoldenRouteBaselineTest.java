package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline golden routes for detecting transport-chain and path-length drift before live walker tests.
 */
public class ShortestPathGoldenRouteBaselineTest
{
	private static SplitFlagMap collisionMap;

	@BeforeClass
	public static void loadCollisionMap()
	{
		collisionMap = SplitFlagMap.fromResources();
		assertNotNull(collisionMap);
	}

	@Test
	public void representativeTransportChainsStayStable()
	{
		List<RouteCase> routes = List.of(
			new RouteCase("gate-190", point(2461, 3385, 0), point(2461, 3382, 0),
				1, 20, TransportType.TRANSPORT, "TRANSPORT:Open:Gate:190"),
			new RouteCase("paterdomus-trapdoor", point(3405, 3506, 0), point(3405, 9894, 0),
				2, 80, TransportType.TRANSPORT, "TRANSPORT:Climb-down:Trapdoor:1581"),
			new RouteCase("lumbridge-bank-stairs", point(3222, 3218, 0), point(3208, 3220, 2),
				10, 120, TransportType.TRANSPORT, "TRANSPORT:Climb-up:Staircase"),
			new RouteCase("rimmington-to-aldarin-charter", point(2957, 3214, 0), point(1455, 2968, 0),
				20, 500, TransportType.CHARTER_SHIP, "CHARTER_SHIP:Charter:Trader Crewmember:1330"),
			new RouteCase("paterdomus-holy-barrier", point(3440, 9887, 0), point(3423, 3485, 0),
				1, 30, TransportType.TRANSPORT, "TRANSPORT:Pass-through:Holy barrier:3443"),
			new RouteCase("elemental-workshop-wall", point(2709, 3495, 0), point(2709, 3496, 0),
				1, 10, TransportType.TRANSPORT, "TRANSPORT:Open:Odd-looking wall:18505"),
			new RouteCase("piscatoris-colony-gate", point(2343, 3662, 0), point(2343, 3663, 0),
				1, 10, TransportType.TRANSPORT, "TRANSPORT:Open:Colony gate:12723")
		);

		for (RouteCase route : routes)
		{
			PathfinderConfig config = configFor(route.focusType);
			Pathfinder pathfinder = new Pathfinder(config, route.start, route.destination);
			pathfinder.run();
			List<WorldPoint> path = pathfinder.getPath();
			List<String> chain = transportChain(route.start, path, config.getTransports());
			String evidence = route.id + " path=" + path.size() + " chain=" + chain;

			assertTrue(evidence, pathfinder.isDone());
			assertFalse(evidence, path.isEmpty());
			assertTrue(evidence, path.get(path.size() - 1).distanceTo(route.destination) <= 2);
			assertTrue(evidence, path.size() >= route.minPathLength);
			assertTrue(evidence, path.size() <= route.maxPathLength);
			assertTrue(evidence + " expected=" + route.expectedChainFragment,
				chain.stream().anyMatch(entry -> entry.startsWith(route.expectedChainFragment)));
		}
	}

	private static PathfinderConfig configFor(TransportType focusType)
	{
		HashMap<WorldPoint, Set<Transport>> all = Transport.loadAllFromResources();
		HashMap<WorldPoint, Set<Transport>> filtered = new HashMap<>();
		for (Map.Entry<WorldPoint, Set<Transport>> entry : all.entrySet())
		{
			Set<Transport> usable = entry.getValue().stream()
				.filter(transport -> transport.getType() == TransportType.TRANSPORT ||
					transport.getType() == focusType)
				.collect(Collectors.toSet());
			if (!usable.isEmpty())
			{
				filtered.put(entry.getKey(), usable);
			}
		}
		PathfinderConfig config = new PathfinderConfig(
			collisionMap, filtered, Collections.emptyList(), null, null);
		try
		{
			Field cutoff = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
			cutoff.setAccessible(true);
			cutoff.setLong(config, 15_000L);
			for (Map.Entry<WorldPoint, Set<Transport>> entry : filtered.entrySet())
			{
				if (entry.getKey() != null)
				{
					config.getTransports().put(entry.getKey(), entry.getValue());
					config.getTransportsPacked().put(
						WorldPointUtil.packWorldPoint(entry.getKey()), entry.getValue());
				}
			}
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError("Unable to configure golden-route pathfinder", e);
		}
		return config;
	}

	private static List<String> transportChain(
		WorldPoint start, List<WorldPoint> path, Map<WorldPoint, Set<Transport>> transports)
	{
		List<String> chain = new ArrayList<>();
		List<WorldPoint> pathWithStart = new ArrayList<>(path.size() + 1);
		pathWithStart.add(start);
		pathWithStart.addAll(path);
		for (int i = 0; i + 1 < pathWithStart.size(); i++)
		{
			WorldPoint from = pathWithStart.get(i);
			WorldPoint to = pathWithStart.get(i + 1);
			for (Transport transport : transports.getOrDefault(from, Collections.emptySet()))
			{
				if (to.equals(transport.getDestination()))
				{
					chain.add(transport.getType() + ":" + value(transport.getAction()) + ":" +
						value(transport.getName()) + ":" + transport.getObjectId());
				}
			}
		}
		return chain;
	}

	private static String value(String value)
	{
		return value == null ? "" : value;
	}

	private static WorldPoint point(int x, int y, int plane)
	{
		return new WorldPoint(x, y, plane);
	}

	private static final class RouteCase
	{
		private final String id;
		private final WorldPoint start;
		private final WorldPoint destination;
		private final int minPathLength;
		private final int maxPathLength;
		private final TransportType focusType;
		private final String expectedChainFragment;

		private RouteCase(String id, WorldPoint start, WorldPoint destination,
			int minPathLength, int maxPathLength, TransportType focusType,
			String expectedChainFragment)
		{
			this.id = id;
			this.start = start;
			this.destination = destination;
			this.minPathLength = minPathLength;
			this.maxPathLength = maxPathLength;
			this.focusType = focusType;
			this.expectedChainFragment = expectedChainFragment;
		}
	}
}
