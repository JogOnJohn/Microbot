package net.runelite.client.plugins.microbot.shortestpath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.World330HostedHouseTransport;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportItemRequirement;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import net.runelite.client.plugins.microbot.util.walker.TransportEdgeAdapter;
import org.junit.Test;

import static org.junit.Assert.*;

public class TransportEdgeAdapterTest
{
	private static final WorldPoint ORIGIN = new WorldPoint(3200, 3200, 0);
	private static final WorldPoint DESTINATION = new WorldPoint(3210, 3210, 0);

	@Test
	public void groupedAlternativesKeepBranchQuantitiesAndAndBoundaries()
	{
		Transport source = row("1854=1|995=5&554=2", TransportType.TRANSPORT);
		Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(source);
		List<Rs2TransportItemRequirement> groups = edge.getItemRequirements();
		assertEquals(2, groups.size());
		assertEquals(Map.of(1854, 1, 995, 5), groups.get(0).getAlternatives());
		assertEquals(Map.of(554, 2), groups.get(1).getAlternatives());
		assertFalse(satisfied(edge, Map.of(995, 4, 554, 2), Set.of()));
		assertFalse(satisfied(edge, Map.of(1854, 1), Set.of()));
		assertTrue(satisfied(edge, Map.of(995, 5, 554, 2), Set.of()));
		assertTrue(satisfied(edge, Map.of(1854, 1, 554, 2), Set.of()));
		assertSame(source, TransportEdgeAdapter.toLegacy(edge));
	}

	@Test
	public void runeProvidersStayOnTheirDeclaredAlternative()
	{
		Transport source = row("AIR_RUNE=3|995=5&FIRE_RUNE=2", TransportType.TELEPORTATION_SPELL);
		Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(source);
		assertEquals(2, edge.getItemRequirements().size());
		List<Rs2TransportItemRequirement.Branch> branches = edge.getItemRequirements().get(0).getBranches();
		assertEquals(2, branches.size());
		assertEquals(3, branches.get(0).getQuantity());
		assertFalse(branches.get(0).getStaffIds().isEmpty());
		assertEquals(5, branches.get(1).getQuantity());
		assertTrue(branches.get(1).getStaffIds().isEmpty());
		assertTrue(branches.get(1).getOffhandIds().isEmpty());
		assertEquals(Set.of(995), branches.get(1).getItemIds());
		assertSame(source, TransportEdgeAdapter.toLegacy(edge));
	}

	@Test
	public void zeroIsAbsenceAndDuplicateItemQuantitiesAreNotCollapsed()
	{
		Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(row("995=0|995=5", TransportType.TRANSPORT));
		Rs2TransportItemRequirement group = edge.getItemRequirements().get(0);
		assertEquals(2, group.getBranches().size());
		assertTrue(satisfied(edge, Map.of(), Set.of()));
		assertFalse(satisfied(edge, Map.of(995, 1), Set.of()));
		assertTrue(satisfied(edge, Map.of(995, 5), Set.of()));
		expect(IllegalStateException.class, group::getAlternatives);
	}

	@Test
	public void unlockReliefAndPureUnlockGatesRemainSeparate()
	{
		Transport source = row("1351=1|UNLOCK_CANOE_AXE=1&UNLOCK_XERICS_HONOUR=1", TransportType.CANOE);
		Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(source);
		assertTrue(edge.isStateGated());
		assertFalse(satisfied(edge, Map.of(1351, 1), Set.of()));
		assertFalse(satisfied(edge, Map.of(), Set.of("CANOE_AXE")));
		assertTrue(satisfied(edge, Map.of(), Set.of("CANOE_AXE", "XERICS_HONOUR")));
		assertTrue(satisfied(edge, Map.of(1351, 1), Set.of("XERICS_HONOUR")));
		expect(IllegalStateException.class, () -> edge.getItemRequirements().get(0).getAlternatives());
		assertFalse(Rs2TransportItemRequirement.selectEquipmentProviders(edge.getItemRequirements(),
			id -> 0, id -> false, id -> false).isPresent());
		assertSame(source, TransportEdgeAdapter.toLegacy(edge));
	}

	@Test
	public void metadataAndGateFlagsAreDescriptiveNotEvaluated()
	{
		Map<String, String> fields = fields("995=50");
		fields.put("Skills", "50 Agility;Max Total;Max Quest;70 Combat");
		fields.put("Quests", "Cabin Fever=FINISHED");
		fields.put("Varbits", "123=1;124>2");
		fields.put("Varplayers", "125<3");
		fields.put("Currency", "50 coins");
		fields.put("menuOption menuTarget objectID", "Travel;Captain;1234");
		fields.put("Duration", "8");
		fields.put("Wilderness level", "19");
		fields.put("Consumable", "yes");
		fields.put("isMembers", "Y");
		Transport source = new Transport(fields, TransportType.SHIP);
		Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(source);
		assertTrue(edge.isSkillGated());
		assertTrue(edge.isQuestGated());
		assertTrue(edge.isStateGated());
		assertTrue(edge.isMembers());
		assertTrue(edge.isConsumable());
		assertFalse(edge.isTeleport());
		assertEquals(19, edge.getMaxWildernessLevel());
		assertEquals(8, edge.getDuration());
		assertEquals("coins", edge.getCurrencyName());
		assertEquals(50, edge.getCurrencyAmount());
		assertEquals("Travel", edge.getAction());
		assertEquals("Captain", edge.getTarget());
		assertEquals(1234, edge.getObjectId());
		assertSame(source, TransportEdgeAdapter.toLegacy(edge));
		assertEquals(2, source.getVarbits().size());
		assertEquals(1, source.getVarplayers().size());
		assertEquals(1, source.getQuests().size());
	}

	@Test
	public void individualGateKindsAndUngatedTransportAreDistinguished()
	{
		Rs2TransportEdge plain = TransportEdgeAdapter.toEdge(row("", TransportType.TRANSPORT));
		assertFalse(plain.isSkillGated());
		assertFalse(plain.isQuestGated());
		assertFalse(plain.isStateGated());
		for (String[] gate : new String[][]{{"Skills", "Max Total"}, {"Skills", "70 Combat"},
			{"Skills", "50 Agility"}, {"Skills", "Max Quest"}, {"Quests", "Cabin Fever"},
			{"Varbits", "123=1"}, {"Varplayers", "125<3"}})
		{
			Map<String, String> fields = fields("");
			fields.put(gate[0], gate[1]);
			Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(new Transport(fields, TransportType.TRANSPORT));
			assertEquals(gate[1], gate[0].equals("Skills") && !gate[1].equals("Max Quest"), edge.isSkillGated());
			assertEquals(gate[1], gate[0].equals("Quests") || gate[1].equals("Max Quest"), edge.isQuestGated());
			assertEquals(gate[1], gate[0].startsWith("Var"), edge.isStateGated());
		}
	}

	@Test
	public void legacyRequirementsStayAndOfOrWithUnitQuantities()
	{
		Transport source = new Transport(DESTINATION, "legacy", TransportType.TELEPORTATION_ITEM,
			true, 19, Set.of(Set.of(100, 101), Set.of(200)));
		Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(source);
		assertEquals(2, edge.getItemRequirements().size());
		assertFalse(satisfied(edge, Map.of(100, 1), Set.of()));
		assertTrue(satisfied(edge, Map.of(101, 1, 200, 1), Set.of()));
		assertTrue(edge.isTeleport());
		assertSame(source, TransportEdgeAdapter.toLegacy(edge));
	}

	@Test
	public void exactPersonalAndHostedSubtypesRoundTripWithoutExecuting()
	{
		PohTeleport facility = new PohTeleport()
		{
			public boolean execute() { throw new AssertionError("must not execute"); }
			public boolean isUnlockedForPlayer() { throw new AssertionError("must not query live gates"); }
			public WorldPoint getDestination() { return DESTINATION; }
			public int getDuration() { return 4; }
			public String displayInfo() { return "fixture facility"; }
			public String name() { return "FIXTURE"; }
		};
		PohTransport personal = new PohTransport(ORIGIN, facility);
		PohTransport hosted = new PohTransport(ORIGIN, facility, true);
		World330HostedHouseTransport entry = new World330HostedHouseTransport(DESTINATION);
		for (Transport source : List.of(personal, hosted, entry))
		{
			Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(source);
			assertSame(source, TransportEdgeAdapter.toLegacy(edge));
			assertSame(source.getClass(), TransportEdgeAdapter.toLegacy(edge).getClass());
			assertEquals(Rs2TransportExecutor.UNSUPPORTED, edge.getExecutor());
		}
		assertSame(facility, ((PohTransport) TransportEdgeAdapter.toLegacy(TransportEdgeAdapter.toEdge(personal))).getTeleport());
		assertFalse(personal.isHostedHouse());
		assertTrue(hosted.isHostedHouse());
	}

	@Test
	public void everyLocalTypeIsDescribedButNoExecutorIsActivated()
	{
		for (TransportType type : TransportType.values())
		{
			Transport source = new Transport(ORIGIN, DESTINATION, "fixture", type, false, 3);
			Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(source);
			assertEquals(Rs2TransportType.valueOf(type.name()), edge.getType());
			assertEquals(Rs2TransportExecutor.UNSUPPORTED, edge.getExecutor());
			assertSame(source, TransportEdgeAdapter.toLegacy(edge));
		}
		Transport anchored = new Transport(ORIGIN, DESTINATION, "seasonal", TransportType.SEASONAL_TRANSPORT, true, 3);
		Transport originless = new Transport(null, DESTINATION, "seasonal", TransportType.SEASONAL_TRANSPORT, true, 3);
		assertFalse(TransportEdgeAdapter.toEdge(anchored).isTeleport());
		assertTrue(TransportEdgeAdapter.toEdge(originless).isTeleport());
	}

	@Test
	public void snapshotCopiesMutableLegacyRequirementsWithoutReplacingSource()
	{
		Transport source = row("554=2", TransportType.TRANSPORT);
		Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(source);
		source.getParsedItemRequirements().getRequirements().get(0).getBranches().get(0).getItemIds()[0] = 999;
		source.setAction("Changed");
		assertEquals(Map.of(554, 2), edge.getItemRequirements().get(0).getAlternatives());
		assertNull(edge.getAction());
		assertSame(source, TransportEdgeAdapter.toLegacy(edge));
		assertEquals("Changed", TransportEdgeAdapter.toLegacy(edge).getAction());
		expect(UnsupportedOperationException.class, () -> edge.getItemRequirements().clear());
	}

	private static Transport row(String items, TransportType type)
	{
		return new Transport(fields(items), type);
	}

	private static void expect(Class<? extends RuntimeException> type, Runnable action)
	{
		try
		{
			action.run();
			fail("expected " + type.getSimpleName());
		}
		catch (RuntimeException exception)
		{
			assertTrue(exception.toString(), type.isInstance(exception));
		}
	}

	private static Map<String, String> fields(String items)
	{
		Map<String, String> fields = new HashMap<>();
		fields.put("Origin", "3200 3200 0");
		fields.put("Destination", "3210 3210 0");
		fields.put("Items", items);
		return fields;
	}

	private static boolean satisfied(Rs2TransportEdge edge, Map<Integer, Integer> counts, Set<String> unlocks)
	{
		return edge.getItemRequirements().stream().allMatch(group ->
			group.isSatisfiedBy(id -> counts.getOrDefault(id, 0), unlocks));
	}
}
