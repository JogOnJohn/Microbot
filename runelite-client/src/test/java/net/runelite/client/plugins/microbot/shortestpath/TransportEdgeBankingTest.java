package net.runelite.client.plugins.microbot.shortestpath;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.ItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.TransportItems;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.Unlock;
import net.runelite.client.plugins.microbot.util.walker.Rs2TerminalTravelMode;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportItemRequirement;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import net.runelite.client.plugins.microbot.util.walker.TransportEdgeAdapter;
import net.runelite.client.plugins.microbot.util.walker.TransportEdgeBanking;
import net.runelite.client.plugins.microbot.util.walker.TransportEdgeBanking.Snapshot;
import org.junit.Test;

import static org.junit.Assert.*;

public class TransportEdgeBankingTest
{
	@Test
	public void richProviderCorrelationsCannotBeProjectedToGlobalProviders()
	{
		Rs2TransportItemRequirement rich = Rs2TransportItemRequirement.fromBranches(List.of(
			branch(Set.of(100), Set.of(300), Set.of(), 0),
			branch(Set.of(200), Set.of(), Set.of(400), 5)));
		expect(IllegalStateException.class, rich::getAlternatives);
		assertEquals(Set.of(300), rich.getBranches().get(0).getStaffIds());
		assertTrue(rich.getBranches().get(1).getStaffIds().isEmpty());
		Rs2TransportItemRequirement exact = Rs2TransportItemRequirement.fromBranches(List.of(
			branch(Set.of(100), Set.of(300), Set.of(), 2),
			branch(Set.of(200), Set.of(300), Set.of(), 5)));
		assertEquals(Map.of(100, 2, 200, 5), exact.getAlternatives());
		Rs2TransportItemRequirement providerOnly = Rs2TransportItemRequirement.fromBranches(List.of(
			branch(Set.of(), Set.of(300), Set.of(), 1)));
		expect(IllegalStateException.class, providerOnly::getAlternatives);
		Rs2TransportItemRequirement positiveOnly = Rs2TransportItemRequirement.fromBranches(List.of(
			branch(Set.of(100), Set.of(300), Set.of(), 2),
			branch(Set.of(200), Set.of(), Set.of(), 5)));
		expect(IllegalStateException.class, positiveOnly::getAlternatives);
	}

	@Test
	public void unknownUnlockNamesAreRejectedRatherThanSilentlySatisfied()
	{
		expect(IllegalArgumentException.class, () -> new Rs2TransportItemRequirement.Branch(
			Set.of(), Set.of(), Set.of(), 1, "TYPO_CANOE_AXE"));
		assertEquals("CANOE_AXE", new Rs2TransportItemRequirement.Branch(
			Set.of(), Set.of(), Set.of(), 1, "CANOE_AXE").getUnlock());
	}

	@Test
	public void alternativeQuantitiesChooseStockThatActuallyCoversTheBranch()
	{
		Rs2TransportEdge edge = edge("1854=1|995=5&554=2", TransportType.TRANSPORT, false);
		Rs2TransportLoadout pass = plan(List.of(edge), Map.of(554, 1), Map.of(1854, 1, 995, 4, 554, 1));
		assertTrue(pass.isSatisfiable());
		assertEquals(Map.of(1854, 1, 554, 1), pass.getWithdrawals());
		Rs2TransportLoadout coins = plan(List.of(edge), Map.of(554, 2), Map.of(995, 5));
		assertTrue(coins.isSatisfiable());
		assertEquals(Map.of(995, 5), coins.getWithdrawals());
		assertUnavailable(plan(List.of(edge), Map.of(554, 2), Map.of(995, 4)));
	}

	@Test
	public void repeatedFaresSumEvenWhenEachEdgeIndividuallyFitsCarriedCoins()
	{
		Rs2TransportEdge fare = fare("coins", 5);
		Snapshot state = snapshot(Map.of(995, 6), Map.of(995, 4));
		Rs2TransportLoadout loadout = TransportEdgeBanking.getMissingLoadout(List.of(fare, fare), state);
		assertTrue(loadout.isSatisfiable());
		assertEquals(Map.of(995, 4), loadout.getWithdrawals());
		List<Rs2TransportEdge> missing = TransportEdgeBanking.getMissingEdges(List.of(fare, fare), state);
		assertEquals(2, missing.size());
		assertSame(fare, missing.get(0));
		assertSame(fare, missing.get(1));
		assertUnavailable(plan(List.of(fare, fare), Map.of(995, 6), Map.of(995, 3)));
	}

	@Test
	public void runesSumButReusableToolsTakeMaximumAndCarriedStockIsSubtractedOnce()
	{
		Rs2TransportEdge spell = edge("554=3", TransportType.TELEPORTATION_SPELL, false);
		Rs2TransportEdge tool = edge("100=2", TransportType.TRANSPORT, false);
		Rs2TransportLoadout loadout = plan(List.of(spell, tool, spell, tool),
			Map.of(554, 2, 100, 1), Map.of(554, 4, 100, 1));
		assertTrue(loadout.isSatisfiable());
		assertEquals(Map.of(554, 4, 100, 1), loadout.getWithdrawals());
	}

	@Test
	public void explicitConsumablesAndCurrencyItemClausesSum()
	{
		Rs2TransportEdge tablet = edge("8007=1", TransportType.TELEPORTATION_ITEM, true);
		Rs2TransportEdge toll = edge("995=5", TransportType.TRANSPORT, false);
		Rs2TransportLoadout result = plan(List.of(tablet, tablet, toll, toll),
			Map.of(8007, 1, 995, 3), Map.of(8007, 1, 995, 7));
		assertTrue(result.isSatisfiable());
		assertEquals(Map.of(8007, 1, 995, 7), result.getWithdrawals());
		Rs2TransportEdge pass = edge("1854=1", TransportType.TRANSPORT, false);
		Rs2TransportLoadout passes = plan(List.of(pass, pass), Map.of(1854, 1), Map.of(1854, 1));
		assertTrue(passes.isSatisfiable());
		assertEquals(Map.of(1854, 1), passes.getWithdrawals());
	}

	@Test
	public void reusableAndConsumedDemandsReserveBothConservatively()
	{
		Rs2TransportEdge reusable = edge("100=2", TransportType.TRANSPORT, false);
		Rs2TransportEdge consumed = edge("100=3", TransportType.TRANSPORT, true);
		Rs2TransportLoadout result = plan(List.of(reusable, consumed), Map.of(100, 1), Map.of(100, 4));
		assertTrue(result.isSatisfiable());
		assertEquals(Map.of(100, 4), result.getWithdrawals());
	}

	@Test
	public void zeroMeansAbsenceAndCannotBeRepairedByWithdrawalOrDeposit()
	{
		Rs2TransportEdge absence = edge("100=0", TransportType.TRANSPORT, false);
		assertTrue(plan(List.of(absence), Map.of(), Map.of(100, 20)).isSatisfiable());
		assertUnavailable(plan(List.of(absence), Map.of(100, 1), Map.of()));
		Rs2TransportEdge positive = edge("100=1", TransportType.TRANSPORT, false);
		assertUnavailable(plan(List.of(absence, positive), Map.of(), Map.of(100, 1)));
		Rs2TransportEdge alternative = edge("100=0|200=2", TransportType.TRANSPORT, false);
		assertEquals(Map.of(200, 2), plan(List.of(alternative), Map.of(100, 1), Map.of(200, 2)).getWithdrawals());
	}

	@Test
	public void declaredUnlockReliefDoesNotEraseOtherAndClauses()
	{
		Rs2TransportEdge edge = edge("1351=1|UNLOCK_CANOE_AXE=1&UNLOCK_XERICS_HONOUR=1", TransportType.CANOE, false);
		assertUnavailable(plan(List.of(edge), Map.of(1351, 1), Map.of()));
		Snapshot declared = new Snapshot(Map.of(), Map.of(), Map.of(),
			Set.of(Unlock.CANOE_AXE, Unlock.XERICS_HONOUR), false);
		Rs2TransportLoadout result = TransportEdgeBanking.getMissingLoadout(List.of(edge), declared);
		assertTrue(result.isSatisfiable());
		assertTrue(result.isEmpty());
		Snapshot axeOnly = new Snapshot(Map.of(), Map.of(), Map.of(), Set.of(Unlock.CANOE_AXE), true);
		assertUnavailable(TransportEdgeBanking.getMissingLoadout(List.of(edge), axeOnly));
	}

	@Test
	public void solverBacktracksAcrossConsumableAlternativesInsteadOfDoubleSpendingStock()
	{
		Rs2TransportEdge either = edge("100=1|200=1", TransportType.TRANSPORT, true);
		Rs2TransportEdge only = edge("100=1", TransportType.TRANSPORT, true);
		Rs2TransportLoadout result = plan(List.of(either, only), Map.of(), Map.of(100, 1, 200, 1));
		assertTrue(result.isSatisfiable());
		assertEquals(Map.of(100, 1, 200, 1), result.getWithdrawals());
	}

	@Test
	public void oneCommonStaffIsReusedAndMustBeEquipped() throws Exception
	{
		Rs2TransportEdge first = provider(100, 300, 3, false);
		Rs2TransportEdge second = provider(200, 300, 5, false);
		Snapshot banked = snapshot(Map.of(), Map.of(300, 1));
		Rs2TransportLoadout result = TransportEdgeBanking.getMissingLoadout(List.of(first, second), banked);
		assertTrue(result.isSatisfiable());
		assertEquals(Map.of(300, 1), result.getWithdrawals());
		assertEquals(List.of(300), result.getEquipmentItemIds());
		expect(IllegalStateException.class, () -> TransportEdgeBanking.getMissingItemQuantities(List.of(first, second), banked));
		Snapshot carried = snapshot(Map.of(300, 1), Map.of());
		Rs2TransportLoadout equipOnly = TransportEdgeBanking.getMissingLoadout(List.of(first), carried);
		assertTrue(equipOnly.isSatisfiable());
		assertTrue(equipOnly.getWithdrawals().isEmpty());
		assertFalse(equipOnly.isEmpty());
		assertEquals(List.of(300), equipOnly.getEquipmentItemIds());
		Snapshot equipped = new Snapshot(Map.of(), Map.of(300, 1), Map.of(), Set.of(), false);
		assertTrue(TransportEdgeBanking.getMissingEdges(List.of(first, second), equipped).isEmpty());
	}

	@Test
	public void conflictingStaffsAndUnsupportedOffhandsNeverLookReady() throws Exception
	{
		Rs2TransportEdge first = provider(100, 300, 3, false);
		Rs2TransportEdge second = provider(200, 400, 5, false);
		assertUnavailable(plan(List.of(first, second), Map.of(), Map.of(300, 1, 400, 1)));
		assertUnavailable(plan(List.of(provider(100, 500, 3, true)), Map.of(), Map.of(500, 1)));
		Snapshot otherGear = new Snapshot(Map.of(), Map.of(600, 1), Map.of(300, 1), Set.of(), true);
		assertUnavailable(TransportEdgeBanking.getMissingLoadout(List.of(first), otherGear));
		assertUnavailable(plan(List.of(provider(100, 300, 0, false)), Map.of(), Map.of(300, 1)));
	}

	@Test
	public void unavailableIsNotReadyAndMapQueriesCannotSilentlyReturnEmpty()
	{
		Rs2TransportEdge edge = edge("100=2", TransportType.TRANSPORT, false);
		Snapshot state = snapshot(Map.of(), Map.of(100, 1));
		assertUnavailable(TransportEdgeBanking.getMissingLoadout(List.of(edge), state));
		assertEquals(List.of(edge), TransportEdgeBanking.getMissingEdges(List.of(edge), state));
		expect(IllegalStateException.class, () -> TransportEdgeBanking.getMissingItemQuantities(List.of(edge), state));
		Snapshot unknownBank = new Snapshot(Map.of(), Map.of(), Map.of(100, 2), Set.of(), false);
		assertUnavailable(TransportEdgeBanking.getMissingLoadout(List.of(edge), unknownBank));
		assertTrue(TransportEdgeBanking.getMissingLoadout(List.of(), unknownBank).isSatisfiable());
	}

	@Test
	public void unsupportedSourcesAndAmbiguousTollsAreExplicitlyUnavailable()
	{
		assertUnavailable(plan(List.of(fare("unknown-currency", 10)), Map.of(), Map.of(995, 100)));
		Map<String, String> fields = fields("100=1", false);
		fields.put("Currency", "5 coins");
		Rs2TransportEdge ambiguous = TransportEdgeAdapter.toEdge(new Transport(fields, TransportType.TRANSPORT));
		assertUnavailable(plan(List.of(ambiguous), Map.of(100, 1, 995, 5), Map.of()));
		fields = fields("", false);
		fields.put("Quests", "Cabin Fever");
		assertUnavailable(plan(List.of(TransportEdgeAdapter.toEdge(new Transport(fields, TransportType.TRANSPORT))), Map.of(), Map.of()));
		assertUnavailable(plan(List.of(edge("100=1", TransportType.TELEPORTATION_ITEM, false)), Map.of(100, 1), Map.of()));
		assertUnavailable(plan(List.of(edge("", TransportType.FAIRY_RING, false)), Map.of(), Map.of()));
		Rs2TransportEdge fabricated = new Rs2TransportEdge(null, new WorldPoint(3200, 3200, 0),
			Rs2TransportType.TRANSPORT, Rs2TransportExecutor.UNSUPPORTED, Rs2TerminalTravelMode.UNSUPPORTED,
			"fabricated", null, null, 0, 1, false, false, false, -1, "", 0, List.of());
		assertUnavailable(plan(List.of(fabricated), Map.of(), Map.of()));
	}

	@Test
	public void staleSourceAndOverflowFailClosed()
	{
		Transport source = new Transport(fields("100=1", false), TransportType.TRANSPORT);
		Rs2TransportEdge stale = TransportEdgeAdapter.toEdge(source);
		source.getParsedItemRequirements().getRequirements().get(0).getBranches().get(0).getItemIds()[0] = 200;
		assertUnavailable(plan(List.of(stale), Map.of(100, 1), Map.of(200, 1)));
		Rs2TransportEdge huge = edge("995=2147483647", TransportType.TRANSPORT, false);
		assertUnavailable(plan(List.of(huge, huge), Map.of(), Map.of(995, Integer.MAX_VALUE)));
	}

	@Test
	public void chargedBryophytaProviderIsNotAnUnlimitedNatureRuneSupply() throws Exception
	{
		Rs2TransportEdge staff = provider(561, ItemID.NATURE_STAFF_CHARGED, 3, false);
		assertUnavailable(plan(List.of(staff), Map.of(), Map.of(ItemID.NATURE_STAFF_CHARGED, 1)));
		assertUnavailable(plan(List.of(staff), Map.of(ItemID.NATURE_STAFF_CHARGED, 1), Map.of()));
		Snapshot equipped = new Snapshot(Map.of(), Map.of(ItemID.NATURE_STAFF_CHARGED, 1), Map.of(), Set.of(), false);
		assertUnavailable(TransportEdgeBanking.getMissingLoadout(List.of(staff), equipped));
		Rs2TransportLoadout runes = plan(List.of(staff, staff), Map.of(ItemID.NATURE_STAFF_CHARGED, 1, 561, 2), Map.of(561, 4));
		assertTrue(runes.isSatisfiable());
		assertEquals(Map.of(561, 4), runes.getWithdrawals());
		assertTrue(runes.getEquipmentItemIds().isEmpty());
	}

	@Test
	public void unknownInventoryOrEquipmentCannotSatisfyAbsenceOrWithdrawalPlanning()
	{
		Rs2TransportEdge absence = edge("100=0", TransportType.TRANSPORT, false);
		Rs2TransportEdge supply = edge("100=1", TransportType.TRANSPORT, false);
		for (boolean inventoryKnown : List.of(false, true))
		{
			for (boolean equipmentKnown : List.of(false, true))
			{
				if (inventoryKnown && equipmentKnown) continue;
				Snapshot unknown = new Snapshot(Map.of(), Map.of(), Map.of(100, 1), Set.of(), inventoryKnown, equipmentKnown, true);
				assertUnavailable(TransportEdgeBanking.getMissingLoadout(List.of(absence), unknown));
				assertUnavailable(TransportEdgeBanking.getMissingLoadout(List.of(supply), unknown));
				assertEquals(List.of(absence), TransportEdgeBanking.getMissingEdges(List.of(absence), unknown));
				expect(IllegalStateException.class, () -> TransportEdgeBanking.getMissingItemQuantities(List.of(supply), unknown));
			}
		}
		Snapshot observedEmpty = new Snapshot(Map.of(), Map.of(), Map.of(), Set.of(), true, true, false);
		assertTrue(TransportEdgeBanking.getMissingLoadout(List.of(absence), observedEmpty).isSatisfiable());
	}

	@Test
	public void removingAGateFromLegacySourceDoesNotMakeAnOldEdgeFresh()
	{
		for (String[] gate : new String[][]{{"Skills", "50 Agility"}, {"Quests", "Cabin Fever"}, {"Varbits", "123=1"}})
		{
			Map<String, String> fields = fields("100=1", false);
			fields.put(gate[0], gate[1]);
			Transport source = new Transport(fields, TransportType.TRANSPORT);
			Rs2TransportEdge stale = TransportEdgeAdapter.toEdge(source);
			if (gate[0].equals("Skills")) java.util.Arrays.fill(source.getSkillLevels(), 0);
			else if (gate[0].equals("Quests")) source.getQuests().clear();
			else source.getVarbits().clear();
			assertUnavailable(plan(List.of(stale), Map.of(100, 1), Map.of()));
			assertTrue(plan(List.of(TransportEdgeAdapter.toEdge(source)), Map.of(100, 1), Map.of()).isSatisfiable());
		}
	}

	@Test
	public void laterCarriedOrBranchWinsOverEarlierBankedAlternative()
	{
		for (String alternatives : List.of("100=1|200=3", "200=3|100=1"))
		{
			Rs2TransportEdge edge = edge(alternatives, TransportType.TRANSPORT, false);
			Snapshot snapshot = snapshot(Map.of(200, 3), Map.of(100, 1));
			Rs2TransportLoadout result = TransportEdgeBanking.getMissingLoadout(List.of(edge), snapshot);
			assertTrue(result.isSatisfiable());
			assertTrue(result.isEmpty());
			assertTrue(TransportEdgeBanking.getMissingEdges(List.of(edge), snapshot).isEmpty());
		}
	}

	@Test
	public void carriedOnlyChainIsPreferredBeforeAnyWithdrawalPath()
	{
		Rs2TransportEdge either = edge("100=1|200=1", TransportType.TRANSPORT, true);
		Rs2TransportEdge only = edge("100=1", TransportType.TRANSPORT, true);
		Rs2TransportLoadout result = plan(List.of(either, only), Map.of(100, 1, 200, 1), Map.of(100, 1));
		assertTrue(result.isSatisfiable());
		assertTrue(result.isEmpty());
	}

	@Test
	public void failedItemCandidatesConsumeTheGlobalBudget() throws Exception
	{
		int[] alternatives = java.util.stream.IntStream.rangeClosed(10000, 14096).toArray();
		Rs2TransportEdge many = parsed(new TransportItems(List.of(new ItemRequirement(alternatives, null, null, 1))));
		// Last variation is carried; the 4097th examination is still over budget.
		assertUnavailable(plan(List.of(many), Map.of(14096, 1), Map.of()));
	}

	@Test
	public void rejectedProviderCandidatesConsumeTheGlobalBudget() throws Exception
	{
		int[] providers = java.util.stream.IntStream.rangeClosed(30000, 34095).toArray();
		Rs2TransportEdge many = parsed(new TransportItems(List.of(new ItemRequirement(new int[]{100}, providers, null, 3))));
		// Item candidate plus 4096 staff candidates exceed the same global budget.
		assertUnavailable(plan(List.of(many), Map.of(34095, 1), Map.of()));
	}

	@Test
	public void failedCandidatesAcrossBacktrackingExhaustBudgetBeforeBankFallback() throws Exception
	{
		int[] firstOptions = java.util.stream.IntStream.rangeClosed(10000, 10063).toArray();
		int[] secondOptions = java.util.stream.IntStream.rangeClosed(20000, 20063).toArray();
		Rs2TransportEdge first = parsed(new TransportItems(List.of(new ItemRequirement(firstOptions, null, null, 1))));
		Rs2TransportEdge second = parsed(new TransportItems(List.of(new ItemRequirement(secondOptions, null, null, 1))));
		Map<Integer, Integer> carried = new HashMap<>();
		for (int id : firstOptions) carried.put(id, 1);
		// Only 128 declared options, but repeated failed examinations exceed the shared cap.
		// A fresh bank-pass budget would incorrectly hide that exhaustion and return a plan.
		assertUnavailable(plan(List.of(first, second), carried, Map.of(20000, 1)));
	}

	@Test
	public void budgetIsSharedAcrossClausesAndAllowsExactly4096Examinations() throws Exception
	{
		Rs2TransportEdge first = parsed(new TransportItems(List.of(new ItemRequirement(
			java.util.stream.IntStream.rangeClosed(10000, 12047).toArray(), null, null, 1))));
		Rs2TransportEdge second = parsed(new TransportItems(List.of(new ItemRequirement(
			java.util.stream.IntStream.rangeClosed(20000, 22047).toArray(), null, null, 1))));
		Rs2TransportLoadout boundary = plan(List.of(first, second), Map.of(12047, 1, 22047, 1), Map.of());
		assertTrue(boundary.isSatisfiable());
		assertTrue(boundary.isEmpty());
		Rs2TransportEdge extra = edge("100=1", TransportType.TRANSPORT, false);
		assertUnavailable(plan(List.of(first, second, extra), Map.of(12047, 1, 22047, 1, 100, 1), Map.of()));
	}

	@Test
	public void capturedInputsAreCopiedAndNeverMutated()
	{
		Map<Integer, Integer> inventory = new HashMap<>(Map.of(100, 1));
		Map<Integer, Integer> bank = new HashMap<>(Map.of(100, 2));
		Set<Unlock> unlocks = new HashSet<>();
		Snapshot state = new Snapshot(inventory, Map.of(), bank, unlocks, true);
		inventory.clear();
		bank.clear();
		unlocks.add(Unlock.CANOE_AXE);
		Rs2TransportEdge edge = edge("100=3", TransportType.TRANSPORT, false);
		List<Rs2TransportEdge> edges = new ArrayList<>(List.of(edge));
		Rs2TransportLoadout result = TransportEdgeBanking.getMissingLoadout(edges, state);
		assertTrue(result.isSatisfiable());
		assertEquals(Map.of(100, 2), result.getWithdrawals());
		assertSame(edge, edges.get(0));
		assertEquals(result.getWithdrawals(), TransportEdgeBanking.getMissingItemQuantities(edges, state));
		expect(UnsupportedOperationException.class, () -> result.getWithdrawals().clear());
	}

	private static Rs2TransportItemRequirement.Branch branch(Set<Integer> items, Set<Integer> staffs, Set<Integer> offhands, int quantity)
	{
		return new Rs2TransportItemRequirement.Branch(items, staffs, offhands, quantity, null);
	}

	private static Rs2TransportEdge provider(int item, int provider, int quantity, boolean offhand) throws Exception
	{
		TransportItems parsed = new TransportItems(List.of(new ItemRequirement(new int[]{item},
			offhand ? null : new int[]{provider}, offhand ? new int[]{provider} : null, quantity)));
		return parsed(parsed);
	}

	private static Rs2TransportEdge parsed(TransportItems parsed) throws Exception
	{
		Transport source = new Transport(fields("", false), TransportType.TELEPORTATION_SPELL);
		// Isolate provider semantics from the changing production rune/staff catalog.
		Field field = Transport.class.getDeclaredField("parsedItemRequirements");
		field.setAccessible(true);
		field.set(source, parsed);
		return TransportEdgeAdapter.toEdge(source);
	}

	private static Rs2TransportEdge fare(String currency, int amount)
	{
		Map<String, String> fields = fields("", false);
		fields.put("Currency", amount + " " + currency);
		return TransportEdgeAdapter.toEdge(new Transport(fields, TransportType.BOAT));
	}

	private static Rs2TransportEdge edge(String items, TransportType type, boolean consumable)
	{
		return TransportEdgeAdapter.toEdge(new Transport(fields(items, consumable), type));
	}

	private static Map<String, String> fields(String items, boolean consumable)
	{
		Map<String, String> fields = new HashMap<>();
		fields.put("Origin", "3200 3200 0");
		fields.put("Destination", "3210 3210 0");
		fields.put("Items", items);
		fields.put("Consumable", consumable ? "yes" : "no");
		return fields;
	}

	private static Rs2TransportLoadout plan(List<Rs2TransportEdge> edges, Map<Integer, Integer> inventory, Map<Integer, Integer> bank)
	{
		return TransportEdgeBanking.getMissingLoadout(edges, snapshot(inventory, bank));
	}

	private static Snapshot snapshot(Map<Integer, Integer> inventory, Map<Integer, Integer> bank)
	{
		return new Snapshot(inventory, Map.of(), bank, Set.of(), true);
	}

	private static void assertUnavailable(Rs2TransportLoadout result)
	{
		assertFalse(result.isSatisfiable());
		assertTrue("empty does not imply ready", result.isEmpty());
	}

	private static void expect(Class<? extends RuntimeException> type, Runnable action)
	{
		try { action.run(); fail("expected " + type.getSimpleName()); }
		catch (RuntimeException exception) { assertTrue(exception.toString(), type.isInstance(exception)); }
	}
}
