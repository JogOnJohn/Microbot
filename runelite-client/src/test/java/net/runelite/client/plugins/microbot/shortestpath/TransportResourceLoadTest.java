package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import org.junit.Test;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TransportResourceLoadTest
{
	@Test
	public void resourceLoaderPreservesRowOrderAcrossReloads()
	{
		Map<WorldPoint, Set<Transport>> first = Transport.loadAllFromResources();
		Map<WorldPoint, Set<Transport>> second = Transport.loadAllFromResources();
		for (WorldPoint origin : first.keySet())
		{
			assertTrue(first.get(origin) instanceof LinkedHashSet);
			assertEquals(first.get(origin).stream().map(TransportResourceLoadTest::routeIdentity).collect(Collectors.toList()),
				second.get(origin).stream().map(TransportResourceLoadTest::routeIdentity).collect(Collectors.toList()));
		}
	}

	@Test
	public void seaCrossingsLandOnCorrectGroundWithOriginalRequirements()
	{
		Map<WorldPoint, Set<Transport>> catalog = Transport.loadAllFromResources();
		Transport north = catalog.get(new WorldPoint(3810, 3048, 0)).stream()
			.filter(t -> t.getObjectId() == 62410).findFirst().orElse(null);
		Transport south = catalog.get(new WorldPoint(3811, 3056, 0)).stream()
			.filter(t -> t.getObjectId() == 62411).findFirst().orElse(null);
		assertNotNull(north);
		assertNotNull(south);
		assertEquals(new WorldPoint(3811, 3056, 0), north.getDestination());
		assertEquals(new WorldPoint(3810, 3048, 0), south.getDestination());
		assertEquals(QuestState.FINISHED, north.getQuests().get(Quest.CABIN_FEVER));
		assertEquals(QuestState.FINISHED, south.getQuests().get(Quest.CABIN_FEVER));
		Transport bridge = catalog.get(new WorldPoint(2803, 2727, 2)).stream()
			.filter(t -> t.getObjectId() == 4745).findFirst().orElse(null);
		assertNotNull(bridge);
		assertEquals(new WorldPoint(2806, 2724, 0), bridge.getDestination());
		assertEquals(4, bridge.getDuration());
	}

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

	@Test
	public void villaLucensEntrywayIsExecutableAndRetainsUpstreamQuestGate()
	{
		WorldPoint origin = new WorldPoint(1425, 2933, 0);
		WorldPoint destination = new WorldPoint(1427, 2933, 0);
		Transport entryway = Transport.loadAllFromResources().get(origin).stream()
			.filter(transport -> destination.equals(transport.getDestination())
				&& transport.getObjectId() == 54707)
			.findFirst().orElse(null);
		assertNotNull("Villa Lucens must have an executable Entryway edge", entryway);
		assertEquals("Pass-through", entryway.getAction());
		assertEquals("Entryway", entryway.getName());
		assertEquals(2, entryway.getDuration());
		assertEquals(1, entryway.getQuests().size());
		assertEquals(QuestState.FINISHED, entryway.getQuests().get(Quest.DEATH_ON_THE_ISLE));
	}

	private static boolean containsDisplayInfo(Map<WorldPoint, Set<Transport>> transportsByOrigin, String displayInfo)
	{
		return transportsByOrigin.values().stream()
			.flatMap(Collection::stream)
			.anyMatch(transport -> displayInfo.equals(transport.getDisplayInfo()));
	}

	private static String routeIdentity(Transport transport)
	{
		return transport.getDestination() + ":" + transport.getType() + ":" + transport.getObjectId()
			+ ":" + transport.getAction() + ":" + transport.getDisplayInfo() + ":" + transport.getDuration();
	}
}
