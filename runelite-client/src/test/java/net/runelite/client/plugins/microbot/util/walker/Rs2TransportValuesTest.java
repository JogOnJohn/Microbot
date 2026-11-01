package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class Rs2TransportValuesTest
{
	@Test
	public void fabricatedEdgeCannotSupplyLegacyBehaviour()
	{
		Rs2TransportEdge edge = new Rs2TransportEdge(null, new WorldPoint(3200, 3200, 0),
			Rs2TransportType.POH, Rs2TransportExecutor.UNSUPPORTED, Rs2TerminalTravelMode.UNSUPPORTED,
			"synthetic", null, null, 0, 3, true, false, true, 19, null, 0, List.of());
		expect(IllegalArgumentException.class, () -> TransportEdgeAdapter.toLegacy(edge));
		expect(NullPointerException.class, () -> TransportEdgeAdapter.toLegacy(null));
		expect(NullPointerException.class, () -> TransportEdgeAdapter.toEdge(null));
	}

	@Test
	public void inconsistentTerminalExecutorAndModeAreRejected()
	{
		expect(IllegalArgumentException.class, () -> new Rs2TransportEdge(null,
			new WorldPoint(3200, 3200, 0), Rs2TransportType.SHIP, Rs2TransportExecutor.UNSUPPORTED,
			Rs2TerminalTravelMode.DIRECT, "synthetic", null, null, 0, 3, false, false,
			false, -1, "", 0, List.of()));
	}

	@Test
	public void originalItemValueApiKeepsQuantitiesProvidersAndDefensiveCopies()
	{
		Map<Integer, Integer> alternatives = new HashMap<>(Map.of(554, 2, 4694, 2));
		Set<Integer> staff = new HashSet<>(Set.of(1387));
		Rs2TransportItemRequirement requirement = new Rs2TransportItemRequirement(alternatives, staff, Set.of(20714), true);
		alternatives.clear();
		staff.clear();
		assertEquals(Map.of(554, 2, 4694, 2), requirement.getAlternatives());
		assertEquals(Set.of(1387), requirement.getStaffAlternatives());
		assertTrue(requirement.isRuneOnly());
		assertFalse(requirement.isSatisfiedBy(id -> 1));
		assertTrue(requirement.isSatisfiedBy(id -> 2));
		assertTrue(Rs2TransportItemRequirement.selectProviders(List.of(requirement),
			id -> 0, id -> id == 1387, id -> false).get().hasStaff());
		expect(UnsupportedOperationException.class, () -> requirement.getAlternatives().clear());
		expect(UnsupportedOperationException.class, () -> requirement.getStaffAlternatives().clear());
		expect(IllegalArgumentException.class, () -> new Rs2TransportItemRequirement(Map.of(554, -1)));
	}

	@Test
	public void branchProvidersAndNegativeTermsAreNotMixedAcrossAlternatives()
	{
		Rs2TransportItemRequirement requirement = Rs2TransportItemRequirement.fromBranches(List.of(
			new Rs2TransportItemRequirement.Branch(Set.of(554), Set.of(1387), Set.of(), 0, null),
			new Rs2TransportItemRequirement.Branch(Set.of(555), Set.of(), Set.of(20714), 5, null)));
		assertEquals(0, requirement.getBranches().get(0).getQuantity());
		assertEquals(5, requirement.getBranches().get(1).getQuantity());
		assertEquals(Set.of(1387), requirement.getBranches().get(0).getStaffIds());
		assertTrue(requirement.getBranches().get(1).getStaffIds().isEmpty());
		// Owning the forbidden item AND provider cannot satisfy the zero branch.
		assertFalse(Rs2TransportItemRequirement.selectProviders(List.of(requirement),
			id -> id == 554 || id == 1387 ? 1 : 0, id -> id == 1387, id -> false).isPresent());
		assertTrue(Rs2TransportItemRequirement.selectProviders(List.of(requirement),
			id -> id == 554 || id == 1387 ? 1 : 0, id -> false, id -> id == 20714).isPresent());
		expect(UnsupportedOperationException.class, () -> requirement.getBranches().clear());
		expect(UnsupportedOperationException.class, () -> requirement.getBranches().get(0).getItemIds().clear());
	}

	@Test
	public void oneSelectedStaffMustCoverEveryAndClause()
	{
		Rs2TransportItemRequirement fire = new Rs2TransportItemRequirement(Map.of(554, 2), Set.of(100, 300), Set.of(), true);
		Rs2TransportItemRequirement air = new Rs2TransportItemRequirement(Map.of(556, 3), Set.of(200, 300), Set.of(), true);
		assertFalse(Rs2TransportItemRequirement.selectProviders(List.of(fire, air), id -> 0,
			id -> id == 100 || id == 200, id -> false).isPresent());
		assertEquals(300, Rs2TransportItemRequirement.selectProviders(List.of(fire, air), id -> 0,
			id -> id == 300, id -> false).get().getStaffItemId());
	}

	@Test
	public void mapValueEqualityDoesNotDependOnDeclarationOrder()
	{
		Map<Integer, Integer> first = new LinkedHashMap<>();
		first.put(554, 2);
		first.put(555, 3);
		Map<Integer, Integer> second = new LinkedHashMap<>();
		second.put(555, 3);
		second.put(554, 2);
		Rs2TransportItemRequirement left = new Rs2TransportItemRequirement(first);
		Rs2TransportItemRequirement right = new Rs2TransportItemRequirement(second);
		assertEquals(left, right);
		assertEquals(left.hashCode(), right.hashCode());
	}

	@Test
	public void unavailableLoadoutIsDistinctFromEmptyAndCopiesItsInputs()
	{
		Map<Integer, Integer> withdrawals = new HashMap<>(Map.of(995, 50));
		List<Integer> equipment = new ArrayList<>(List.of(1387));
		Rs2TransportLoadout loadout = new Rs2TransportLoadout(withdrawals, equipment, true);
		withdrawals.clear();
		equipment.clear();
		assertEquals(Map.of(995, 50), loadout.getWithdrawals());
		assertEquals(List.of(1387), loadout.getEquipmentItemIds());
		assertFalse(loadout.isEmpty());
		assertTrue(Rs2TransportLoadout.empty().isSatisfiable());
		assertFalse(Rs2TransportLoadout.unavailable().isSatisfiable());
		expect(UnsupportedOperationException.class, () -> loadout.getWithdrawals().clear());
		expect(UnsupportedOperationException.class, () -> loadout.getEquipmentItemIds().clear());
		expect(IllegalArgumentException.class, () -> new Rs2TransportLoadout(Map.of(995, 0), List.of(), true));
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
}
