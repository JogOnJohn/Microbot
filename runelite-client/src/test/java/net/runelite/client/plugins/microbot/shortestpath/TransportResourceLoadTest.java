package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class TransportResourceLoadTest
{
	@Test
	public void loadsUpstreamTransportResources()
	{
		Map<WorldPoint, Set<Transport>> transportsByOrigin = Transport.loadAllFromResources();
		long transportCount = transportsByOrigin.values().stream()
			.flatMap(Collection::stream)
			.count();

		assertTrue("expected merged upstream transport catalog to load", transportCount > 6_000);
		assertTrue("expected upstream POH portal rows to load",
			containsDisplayInfo(transportsByOrigin, "Ape Atoll Dungeon Portal"));
		assertTrue("expected upstream quetzal whistle rows to load",
			containsDisplayInfo(transportsByOrigin, "Quetzal whistle: Aldarin"));
		assertTrue("expected upstream teleportation box rows to load",
			containsDisplayInfo(transportsByOrigin, "Edgeville"));
		assertTrue("expected upstream home teleport rows to load",
			containsDisplayInfo(transportsByOrigin, "Lumbridge Home Teleport"));
	}

	private static boolean containsDisplayInfo(Map<WorldPoint, Set<Transport>> transportsByOrigin, String displayInfo)
	{
		return transportsByOrigin.values().stream()
			.flatMap(Collection::stream)
			.anyMatch(transport -> displayInfo.equals(transport.getDisplayInfo()));
	}
}
