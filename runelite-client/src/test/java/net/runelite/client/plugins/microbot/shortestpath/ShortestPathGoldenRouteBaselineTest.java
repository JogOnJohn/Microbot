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
				1, 10, TransportType.TRANSPORT, "TRANSPORT:Open:Colony gate:12723"),
			new RouteCase("falador-crumbling-wall", point(2936, 3355, 0), point(2934, 3355, 0),
				1, 15, TransportType.AGILITY_SHORTCUT, "AGILITY_SHORTCUT:Climb-over:Crumbling wall:24222"),
			new RouteCase("port-sarim-to-musa-point", point(3029, 3217, 0), point(2956, 3146, 0),
				1, 60, TransportType.SHIP, "SHIP:Musa:Point Captain Tobias:14979"),
			new RouteCase("lumbridge-to-musa-point-multi-leg", point(3222, 3218, 0), point(2956, 3146, 0),
				80, 400, TransportType.SHIP, "SHIP:Musa:Point Captain Tobias:14979"),
			new RouteCase("edgeville-canoe-to-champions-guild", point(3132, 3510, 0), point(3199, 3344, 0),
				1, 150, TransportType.CANOE, "CANOE:Paddle:Canoe Canoe Station:12166"),
			new RouteCase("ge-minecart-to-keldagrim", point(3141, 3504, 0), point(2909, 10174, 0),
				1, 40, TransportType.MINECART, "MINECART:Travel:Trapdoor:16168"),
			new RouteCase("phasmatys-energy-barrier", point(3659, 3509, 0), point(3659, 3507, 0),
				1, 10, TransportType.TRANSPORT, "TRANSPORT:Pay-toll(2-Ecto):Energy Barrier:16105"),
			new RouteCase("village-spirit-tree-to-grand-exchange", point(2543, 3167, 0), point(3185, 3508, 0),
				1, 30, TransportType.SPIRIT_TREE, "SPIRIT_TREE:Travel:Spirit tree:26261"),
			new RouteCase("fairy-ring-to-zanaris", point(2700, 3247, 0), point(2412, 4434, 0),
				1, 30, TransportType.FAIRY_RING, "FAIRY_RING:Configure:Fairy ring:29495"),
			new RouteCase("grand-tree-glider-to-al-kharid", point(2465, 3501, 3), point(3284, 3210, 0),
				1, 30, TransportType.GNOME_GLIDER, "GNOME_GLIDER:Glider:Captain Errdo:10467"),
			new RouteCase("ardougne-lever-to-deep-wilderness", point(2561, 3311, 0), point(3154, 3924, 0),
				1, 30, TransportType.TELEPORTATION_LEVER, "TELEPORTATION_LEVER:Pull:Lever:1814"),
			new RouteCase("sea-slug-boat-to-fishing-platform", point(2720, 3301, 0), point(2782, 3273, 0),
				1, 40, TransportType.BOAT, "BOAT:Travel:Holgart:7789"),
			new RouteCase("lumbridge-to-al-kharid-toll-gate", point(3222, 3218, 0), point(3269, 3167, 0),
				40, 250, TransportType.TRANSPORT, "TRANSPORT:Open:Gate:4405"),
			new RouteCase("marim-stairs", point(2795, 2793, 0), point(2795, 2797, 1),
				1, 10, TransportType.TRANSPORT, "TRANSPORT:Climb-up:Stairs:4756")
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

	/**
	 * Live incident 2026-07-15 18:17: goals on blocked tiles (object/NPC tiles with no cardinal
	 * exit) could never be popped, so every such pathfind flooded 1.4-2.1M nodes to time-cutoff
	 * with bestLast already adjacent to the goal, and from-anywhere teleports (Falador etc.) were
	 * enqueued for a 30-tile walk. Blocked goals now accept the surrounding walkable ring.
	 */
	@Test
	public void blockedGoalTileExitsAtAdjacentRingInsteadOfFlooding()
	{
		PathfinderConfig config = configFor(TransportType.TRANSPORT);
		WorldPoint start = point(2629, 2997, 0);
		WorldPoint goal = point(2601, 2967, 0);
		assertTrue("incident goal tile should be blocked in the collision map",
			config.getMap().isBlocked(goal.getX(), goal.getY(), goal.getPlane()));

		Pathfinder pathfinder = new Pathfinder(config, start, goal);
		pathfinder.run();
		List<WorldPoint> path = pathfinder.getPath();
		WorldPoint last = path.isEmpty() ? null : path.get(path.size() - 1);
		String evidence = "path=" + path.size() + " last=" + last
			+ " nodes=" + pathfinder.getStats().getNodesChecked();

		assertTrue(evidence, pathfinder.isDone());
		assertFalse(evidence, path.isEmpty());
		assertTrue(evidence, last.distanceTo(goal) >= 1 && last.distanceTo(goal) <= 3);
		assertTrue("blocked-goal pathfind should exit at the ring, not flood the map: " + evidence,
			pathfinder.getStats().getNodesChecked() < 20_000);
	}

	/**
	 * Live incident 2026-07-17 18:10-18:12: quest helper posted a path request while the player
	 * stood inside their POH, so the pathfind started from a raw instance coordinate that is
	 * off the collision map. The forward frontier died instantly, but the bidirectional backward
	 * search flooded 617k-785k nodes to time-cutoff before returning an empty path. A dead
	 * frontier anchored on an isolated tile now aborts the search immediately.
	 */
	@Test
	public void isolatedInstanceStartAbortsBidirectionalSearchInsteadOfFlooding()
	{
		PathfinderConfig config = configFor(TransportType.TRANSPORT);
		WorldPoint instanceStart = point(13211, 285, 1);
		WorldPoint goal = point(3491, 3230, 0);

		Pathfinder pathfinder = new Pathfinder(config, instanceStart, goal);
		pathfinder.run();
		String evidence = "nodes=" + pathfinder.getStats().getNodesChecked()
			+ " path=" + pathfinder.getPath().size();

		assertTrue(evidence, pathfinder.isDone());
		assertTrue("isolated-start bidir search should abort, not flood the map: " + evidence,
			pathfinder.getStats().getNodesChecked() < 10_000);
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
			collisionMap, filtered, Collections.emptyList(), null, null, false);
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
