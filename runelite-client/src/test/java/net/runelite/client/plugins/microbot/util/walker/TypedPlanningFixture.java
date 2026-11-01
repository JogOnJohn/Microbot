package net.runelite.client.plugins.microbot.util.walker;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderBackend;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;

/** Single-row deterministic walking graph. No scene, user learned-edge file or account is consulted. */
final class TypedPlanningFixture
{
	static final WorldPoint START = point(1601);
	static final WorldPoint SHORT = point(1609);
	static final WorldPoint LONG = point(4001);
	private static final SplitFlagMap TERRAIN = terrain();

	private TypedPlanningFixture() { }

	static WorldPoint point(int x) { return new WorldPoint(x, 3201, 0); }

	static PathfinderConfig config(PathfinderBackend backend, boolean transport, boolean banked)
	{
		ShortestPathConfig policy = new ShortestPathConfig()
		{
			@Override public PathfinderBackend pathfinderBackend() { return backend; }
		};
		Map<WorldPoint, Set<Transport>> catalog = new HashMap<>();
		if (transport)
		{
			Transport edge = new Transport(START, LONG, "fixture", TransportType.TRANSPORT, false, 1);
			catalog.put(START, new LinkedHashSet<>(List.of(edge)));
		}
		PathfinderConfig config = new PathfinderConfig(TERRAIN, catalog, Collections.emptyList(), null, policy, false);
		config.setUseBankItems(banked);
		catalog.forEach((origin, edges) -> {
			config.getTransports().put(origin, new LinkedHashSet<>(edges));
			config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(origin), config.getTransports().get(origin));
		});
		set(config, "calculationCutoffMillis", 5_000L);
		return config;
	}

	static void set(Object target, String name, Object value)
	{
		try
		{
			java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		}
		catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
	}

	private static SplitFlagMap terrain()
	{
		// Initialize the normal extents without shrinking the global extents to a test-only archive.
		SplitFlagMap.fromResources();
		SplitFlagMap map = new SplitFlagMap(Collections.emptyMap())
		{
			@Override public boolean get(int x, int y, int plane, int flag)
			{
				return plane == 0 && flag == 1 && y == 3201 && x >= START.getX() && x < LONG.getX();
			}
			@Override public boolean hasRegion(int x, int y)
			{
				return x >= 1600 && x < 4032 && y >= 3200 && y < 3264;
			}
		};
		SplitFlagMap.RegionExtent extent = SplitFlagMap.getRegionExtents();
		if (START.getX() / 64 < extent.minX || LONG.getX() / 64 > extent.maxX)
			throw new IllegalStateException("fixture must fit the pinned collision extent");
		for (int x = START.getX() / 64; x <= LONG.getX() / 64; x++)
			map.getRegionMapPlaneCounts()[x - extent.minX + (3201 / 64 - extent.minY) * (extent.getWidth() + 1)] = 1;
		return map;
	}
}
