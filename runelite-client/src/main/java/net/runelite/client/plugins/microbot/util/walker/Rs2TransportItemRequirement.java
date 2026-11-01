package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.Unlock;

/** One immutable AND-clause whose item/quantity alternatives are OR-ed. */
public final class Rs2TransportItemRequirement
{
	private final Map<Integer, Integer> alternatives;
	private final Set<Integer> staffAlternatives;
	private final Set<Integer> offhandAlternatives;
	private final boolean runeOnly;
	private final List<Branch> branches;
	private final boolean flatProjection;

	public Rs2TransportItemRequirement(Map<Integer, Integer> alternatives)
	{
		this(alternatives, Collections.emptySet(), Collections.emptySet(), false);
	}

	public Rs2TransportItemRequirement(
		Map<Integer, Integer> alternatives,
		Set<Integer> staffAlternatives,
		Set<Integer> offhandAlternatives,
		boolean runeOnly)
	{
		if (alternatives == null || alternatives.isEmpty())
		{
			throw new IllegalArgumentException("item requirement must contain an alternative");
		}
		Map<Integer, Integer> copy = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> alternative : alternatives.entrySet())
		{
			Integer itemId = Objects.requireNonNull(alternative.getKey(), "itemId");
			Integer quantity = Objects.requireNonNull(alternative.getValue(), "quantity");
			if (itemId <= 0 || quantity < 0)
			{
				throw new IllegalArgumentException("invalid item requirement: " + alternative);
			}
			copy.put(itemId, quantity);
		}
		this.alternatives = Collections.unmodifiableMap(copy);
		this.staffAlternatives = immutablePositiveIds(staffAlternatives, "staff");
		this.offhandAlternatives = immutablePositiveIds(offhandAlternatives, "offhand");
		this.runeOnly = runeOnly;
		List<Branch> branchCopy = new ArrayList<>();
		copy.forEach((id, quantity) -> branchCopy.add(new Branch(
			Set.of(id), this.staffAlternatives, this.offhandAlternatives, quantity, null)));
		this.branches = List.copyOf(branchCopy);
		this.flatProjection = true;
	}

	/** Preserves branch-local providers, quantities and declared unlock alternatives. */
	public static Rs2TransportItemRequirement fromBranches(List<Branch> branches)
	{
		return new Rs2TransportItemRequirement(branches);
	}

	private Rs2TransportItemRequirement(List<Branch> branches)
	{
		if (branches == null || branches.isEmpty())
		{
			throw new IllegalArgumentException("item requirement must contain a branch");
		}
		this.branches = List.copyOf(branches);
		Map<Integer, Integer> items = new LinkedHashMap<>();
		Set<Integer> staves = new LinkedHashSet<>();
		Set<Integer> offhands = new LinkedHashSet<>();
		boolean representable = true;
		for (Branch branch : this.branches)
		{
			representable &= branch.unlock == null;
			representable &= !branch.itemIds.isEmpty()
				&& branch.staffIds.equals(this.branches.get(0).staffIds)
				&& branch.offhandIds.equals(this.branches.get(0).offhandIds);
			for (Integer id : branch.itemIds)
			{
				Integer previous = items.putIfAbsent(id, branch.quantity);
				representable &= previous == null || previous == branch.quantity;
			}
			staves.addAll(branch.staffIds);
			offhands.addAll(branch.offhandIds);
		}
		this.alternatives = Collections.unmodifiableMap(items);
		this.staffAlternatives = Collections.unmodifiableSet(staves);
		this.offhandAlternatives = Collections.unmodifiableSet(offhands);
		this.runeOnly = false;
		this.flatProjection = representable;
	}

	public List<Branch> getBranches() { return branches; }

	/** Immutable OR branch; unlock names must match the local declared-unlock enum. */
	public static final class Branch
	{
		private final Set<Integer> itemIds;
		private final Set<Integer> staffIds;
		private final Set<Integer> offhandIds;
		private final int quantity;
		private final String unlock;

		public Branch(Set<Integer> itemIds, Set<Integer> staffIds, Set<Integer> offhandIds,
			int quantity, String unlock)
		{
			if (quantity < 0 || (unlock != null && (unlock.isBlank() || quantity == 0)))
			{
				throw new IllegalArgumentException("invalid branch quantity or unlock");
			}
			this.itemIds = immutablePositiveIds(itemIds, "item");
			this.staffIds = immutablePositiveIds(staffIds, "staff");
			this.offhandIds = immutablePositiveIds(offhandIds, "offhand");
			this.quantity = quantity;
			this.unlock = unlock == null ? null : Unlock.valueOf(unlock).name();
			if (unlock != null && (!this.itemIds.isEmpty() || !this.staffIds.isEmpty()
				|| !this.offhandIds.isEmpty()))
			{
				throw new IllegalArgumentException("unlock branch must not contain items or providers");
			}
		}

		public Set<Integer> getItemIds() { return itemIds; }
		public Set<Integer> getStaffIds() { return staffIds; }
		public Set<Integer> getOffhandIds() { return offhandIds; }
		public int getQuantity() { return quantity; }
		public String getUnlock() { return unlock; }

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof Branch)) return false;
			Branch that = (Branch) other;
			return quantity == that.quantity && itemIds.equals(that.itemIds)
				&& staffIds.equals(that.staffIds) && offhandIds.equals(that.offhandIds)
				&& Objects.equals(unlock, that.unlock);
		}

		@Override
		public int hashCode() { return Objects.hash(itemIds, staffIds, offhandIds, quantity, unlock); }
	}

	private static Set<Integer> immutablePositiveIds(Set<Integer> itemIds, String label)
	{
		if (itemIds == null || itemIds.isEmpty())
		{
			return Collections.emptySet();
		}
		LinkedHashSet<Integer> copy = new LinkedHashSet<>();
		for (Integer itemId : itemIds)
		{
			if (itemId == null || itemId <= 0)
			{
				throw new IllegalArgumentException(label + " item id must be positive: " + itemId);
			}
			copy.add(itemId);
		}
		return Collections.unmodifiableSet(copy);
	}

	/** Counted-item projection only. Use branches for provider association and unlock semantics. */
	public Map<Integer, Integer> getAlternatives()
	{
		if (!flatProjection)
		{
			throw new IllegalStateException("requirement cannot be flattened; use getBranches()");
		}
		return alternatives;
	}

	public Set<Integer> getStaffAlternatives() { return staffAlternatives; }
	public Set<Integer> getOffhandAlternatives() { return offhandAlternatives; }
	public boolean isRuneOnly() { return runeOnly; }

	public boolean isSatisfiedBy(IntUnaryOperator availableQuantity)
	{
		Objects.requireNonNull(availableQuantity, "availableQuantity");
		return isSatisfiedBy(availableQuantity, Collections.emptySet());
	}

	/** Counted items and explicit unlocks only; equipment selection is a separate contract. */
	public boolean isSatisfiedBy(IntUnaryOperator availableQuantity, Set<String> unlocks)
	{
		Objects.requireNonNull(availableQuantity, "availableQuantity");
		Objects.requireNonNull(unlocks, "unlocks");
		return branches.stream().anyMatch(branch ->
		{
			if (branch.unlock != null) return unlocks.contains(branch.unlock);
			return branch.itemIds.stream().anyMatch(id ->
			{
				int available = Math.max(0, availableQuantity.applyAsInt(id));
				return branch.quantity == 0 ? available == 0 : available >= branch.quantity;
			});
		});
	}

	private boolean isSatisfiedBy(IntUnaryOperator availableQuantity, int staffItemId, int offhandItemId)
	{
		return isSatisfiedBy(availableQuantity) || branches.stream().anyMatch(branch ->
			branch.unlock == null && (branch.quantity > 0
				? branch.staffIds.contains(staffItemId) || branch.offhandIds.contains(offhandItemId)
				: branch.staffIds.stream().anyMatch(id -> availableQuantity.applyAsInt(id) <= 0)
					|| branch.offhandIds.stream().anyMatch(id -> availableQuantity.applyAsInt(id) <= 0)));
	}

	public static Optional<ProviderSelection> selectProviders(
		List<Rs2TransportItemRequirement> requirements,
		IntUnaryOperator availableQuantity,
		IntPredicate staffAvailable,
		IntPredicate offhandAvailable)
	{
		return selectProviders(
			requirements, availableQuantity, staffAvailable, offhandAvailable, false);
	}

	/**
	 * Select equipment for provider-bearing clauses while deferring ordinary missing items to the
	 * withdrawal/purchase planner. This prevents an unbanked purchasable pass from rejecting the
	 * equipment phase before its fare fallback can be evaluated.
	 */
	public static Optional<ProviderSelection> selectEquipmentProviders(
		List<Rs2TransportItemRequirement> requirements,
		IntUnaryOperator availableQuantity,
		IntPredicate staffAvailable,
		IntPredicate offhandAvailable)
	{
		return selectProviders(
			requirements, availableQuantity, staffAvailable, offhandAvailable, true);
	}

	private static Optional<ProviderSelection> selectProviders(
		List<Rs2TransportItemRequirement> requirements,
		IntUnaryOperator availableQuantity,
		IntPredicate staffAvailable,
		IntPredicate offhandAvailable,
		boolean deferOrdinaryRequirements)
	{
		if (requirements == null || requirements.isEmpty())
		{
			return Optional.of(ProviderSelection.NONE);
		}
		TreeSet<Integer> staffs = new TreeSet<>();
		TreeSet<Integer> offhands = new TreeSet<>();
		for (Rs2TransportItemRequirement requirement : requirements)
		{
			requirement.staffAlternatives.stream().filter(staffAvailable::test).forEach(staffs::add);
			requirement.offhandAlternatives.stream().filter(offhandAvailable::test).forEach(offhands::add);
		}
		List<Integer> staffCandidates = new ArrayList<>();
		staffCandidates.add(ProviderSelection.NO_ITEM);
		staffCandidates.addAll(staffs);
		List<Integer> offhandCandidates = new ArrayList<>();
		offhandCandidates.add(ProviderSelection.NO_ITEM);
		offhandCandidates.addAll(offhands);
		for (Integer staff : staffCandidates)
		{
			for (Integer offhand : offhandCandidates)
			{
				boolean satisfied = true;
				for (Rs2TransportItemRequirement requirement : requirements)
				{
					if (deferOrdinaryRequirements
						&& requirement.staffAlternatives.isEmpty()
						&& requirement.offhandAlternatives.isEmpty()
						&& requirement.branches.stream().noneMatch(branch -> branch.unlock != null))
					{
						continue;
					}
					if (!requirement.isSatisfiedBy(availableQuantity, staff, offhand))
					{
						satisfied = false;
						break;
					}
				}
				if (satisfied)
				{
					return Optional.of(new ProviderSelection(staff, offhand));
				}
			}
		}
		return Optional.empty();
	}

	public static final class ProviderSelection
	{
		private static final int NO_ITEM = -1;
		private static final ProviderSelection NONE = new ProviderSelection(NO_ITEM, NO_ITEM);

		private final int staffItemId;
		private final int offhandItemId;

		private ProviderSelection(int staffItemId, int offhandItemId)
		{
			this.staffItemId = staffItemId;
			this.offhandItemId = offhandItemId;
		}

		public int getStaffItemId() { return staffItemId; }
		public int getOffhandItemId() { return offhandItemId; }
		public boolean hasStaff() { return staffItemId > 0; }
		public boolean hasOffhand() { return offhandItemId > 0; }
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof Rs2TransportItemRequirement))
		{
			return false;
		}
		Rs2TransportItemRequirement that = (Rs2TransportItemRequirement) other;
		return new LinkedHashSet<>(branches).equals(new LinkedHashSet<>(that.branches))
			&& runeOnly == that.runeOnly;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(new LinkedHashSet<>(branches), runeOnly);
	}
}
