package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.ItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.TransportItems;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.Unlock;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

/**
 * Read-only, conservative preparation for an exact ordered transport chain. No route/config refresh,
 * withdrawal, purchase or equipment action is performed. Satisfiable means supplies can be prepared,
 * NOT that a route is admitted, a bank snapshot is live, or an interaction is ready.
 *
 * <p>Supported: grouped item alternatives, declared unlocks, absence (zero), pure known-currency
 * fares, explicit spell requirements, reusable items and one shared unlimited staff. Fares, coins,
 * ecto-tokens, Shantay passes, spell items and explicitly consumable edges sum; ordinary reusable items/providers
 * take their maximum. Mixed reusable/consumed demand reserves both (conservative, not optimal).</p>
 *
 * <p>Explicitly unsupported: fabricated/stale edges, custom Transport subclasses (including PoH),
 * implicit fairy-ring/minigame/seasonal/etc. requirements, quest/skill/var gates, spells without
 * explicit parsed requirements, nonconsumable item teleports (charges), offhand/charged providers,
 * zero-quantity provider branches, currency metadata combined with item clauses (ambiguous toll),
 * purchases, deposits, noted-id conversion, gear replacement, or more than one selected staff.
 * Zero clauses constrain the whole prepared inventory; consumption-before-absence is not modeled.
 * Search is bounded to 4096 candidate examinations globally and 128 clauses; exceeding either
 * returns unavailable. Failed stock/provider candidates also consume the examination budget.</p>
 *
 * <p>Live overloads observe inventory and equipment containers together on the client thread;
 * missing containers or capture timeout are unavailable, not an observed empty inventory.
 * Bank supplies use the available bank mirror. Private planner unlock declarations have no safe
 * read-only accessor, so they are NOT guessed: use captured-input overloads with known declarations.
 * Always inspect isSatisfiable before isEmpty: unavailable is also empty. Map-only queries throw on
 * unavailable rather than returning an empty map that could be mistaken for ready.</p>
 */
public final class TransportEdgeBanking
{
	private TransportEdgeBanking() { }

	/** Conservative complete-chain candidates, retaining order, multiplicity and exact identities. */
	public static List<Rs2TransportEdge> getMissingEdges(List<Rs2TransportEdge> edges)
	{
		if (edges != null && edges.isEmpty()) return List.of();
		return getMissingEdges(edges, capture());
	}

	public static Map<Integer, Integer> getMissingItemQuantities(List<Rs2TransportEdge> edges)
	{
		return quantities(getMissingLoadout(edges));
	}

	public static Rs2TransportLoadout getMissingLoadout(List<Rs2TransportEdge> edges)
	{
		if (edges != null && edges.isEmpty()) return Rs2TransportLoadout.empty();
		try { return getMissingLoadout(edges, capture()); }
		catch (RuntimeException unavailable) { return Rs2TransportLoadout.unavailable(); }
	}

	/**
	 * Returns the whole chain when preparation is missing OR unavailable. Do not filter a chain to
	 * individually missing edges: doing so loses already-carried contributions to repeated fares/runes.
	 */
	public static List<Rs2TransportEdge> getMissingEdges(List<Rs2TransportEdge> edges, Snapshot snapshot)
	{
		Objects.requireNonNull(edges, "edges");
		List<Rs2TransportEdge> copy = List.copyOf(edges);
		Rs2TransportLoadout loadout = getMissingLoadout(copy, snapshot);
		return loadout.isSatisfiable() && loadout.isEmpty() ? List.of() : copy;
	}

	public static Map<Integer, Integer> getMissingItemQuantities(List<Rs2TransportEdge> edges, Snapshot snapshot)
	{
		return quantities(getMissingLoadout(edges, snapshot));
	}

	private static Map<Integer, Integer> quantities(Rs2TransportLoadout loadout)
	{
		if (!loadout.isSatisfiable()) throw new IllegalStateException("transport preparation unavailable");
		if (!loadout.getEquipmentItemIds().isEmpty())
		{
			throw new IllegalStateException("equipment preparation required; use getMissingLoadout");
		}
		return loadout.getWithdrawals();
	}

	/** Pure captured-input core. Bank quantities exclude carried quantities; all inputs are copied. */
	public static Rs2TransportLoadout getMissingLoadout(List<Rs2TransportEdge> edges, Snapshot snapshot)
	{
		try
		{
			Objects.requireNonNull(snapshot, "snapshot");
			if (!snapshot.inventoryKnown || !snapshot.equipmentKnown) return Rs2TransportLoadout.unavailable();
			List<Rs2TransportEdge> copy = List.copyOf(edges);
			List<Clause> clauses = new ArrayList<>();
			List<TransportItems> authorities = new ArrayList<>();
			long declaredCandidates = 0;
			for (Rs2TransportEdge edge : copy)
			{
				Transport source = TransportEdgeAdapter.toLegacy(edge);
				Rs2TransportEdge fresh = TransportEdgeAdapter.toEdge(source);
				if (!supported(source) || !sameRequirements(edge, fresh)) return Rs2TransportLoadout.unavailable();
				List<ItemRequirement> legacy = new ArrayList<>();
				for (Rs2TransportItemRequirement group : edge.getItemRequirements())
				{
					if (clauses.size() == 128) return Rs2TransportLoadout.unavailable();
					List<ItemRequirement.Branch> branches = new ArrayList<>();
					for (Rs2TransportItemRequirement.Branch branch : group.getBranches())
					{
						declaredCandidates += branch.getUnlock() != null ? 1
							: (long) branch.getItemIds().size() + branch.getStaffIds().size();
						// Every clause must be examined at least once. Bound copying/ordering too.
						if (declaredCandidates > 4096) return Rs2TransportLoadout.unavailable();
						if (!branch.getOffhandIds().isEmpty()
							|| (branch.getQuantity() == 0 && !branch.getStaffIds().isEmpty()))
							return Rs2TransportLoadout.unavailable();
						branches.add(new ItemRequirement.Branch(
							branch.getUnlock() == null ? array(branch.getItemIds()) : null,
							array(branch.getStaffIds()), array(branch.getOffhandIds()), branch.getQuantity(),
							branch.getUnlock() == null ? null : Unlock.valueOf(branch.getUnlock())));
					}
					legacy.add(new ItemRequirement(branches));
					clauses.add(new Clause(group, edge.isConsumable() || edge.getType() == Rs2TransportType.TELEPORTATION_SPELL));
				}
				if (edge.getCurrencyAmount() > 0)
				{
					if (clauses.size() == 128 || ++declaredCandidates > 4096) return Rs2TransportLoadout.unavailable();
					if (!legacy.isEmpty()) return Rs2TransportLoadout.unavailable();
					int currency = currencyId(edge.getCurrencyName());
					if (currency <= 0) return Rs2TransportLoadout.unavailable();
					Rs2TransportItemRequirement fare = new Rs2TransportItemRequirement(Map.of(currency, edge.getCurrencyAmount()));
					clauses.add(new Clause(fare, true));
					legacy.add(new ItemRequirement(new int[]{currency}, null, null, edge.getCurrencyAmount()));
				}
				authorities.add(new TransportItems(legacy));
			}
			Budget budget = new Budget();
			// Exhaust carried-only paths before spending bank supplies; both passes share one cap.
			Rs2TransportLoadout carried = solve(clauses, authorities, snapshot, 0, new Demand(), budget, true);
			return carried.isSatisfiable() || budget.exhausted ? carried
				: solve(clauses, authorities, snapshot, 0, new Demand(), budget, false);
		}
		catch (IllegalArgumentException | NullPointerException | ArithmeticException unavailable)
		{
			return Rs2TransportLoadout.unavailable();
		}
	}

	private static Rs2TransportLoadout solve(List<Clause> clauses, List<TransportItems> authorities,
		Snapshot snapshot, int index, Demand demand, Budget budget, boolean carriedOnly)
	{
		if (budget.exhausted) return Rs2TransportLoadout.unavailable();
		if (index == clauses.size()) return finish(authorities, snapshot, demand);
		if (budget.remaining == 0)
		{
			budget.exhausted = true;
			return Rs2TransportLoadout.unavailable();
		}
		Clause clause = clauses.get(index);
		List<Candidate> choices = new ArrayList<>();
		for (Rs2TransportItemRequirement.Branch branch : clause.group.getBranches())
		{
			if (branch.getUnlock() != null)
			{
				if (!budget.spend()) return Rs2TransportLoadout.unavailable();
				if (snapshot.unlocks.contains(Unlock.valueOf(branch.getUnlock())))
					choices.add(new Candidate(demand, 0));
				continue;
			}
			for (Integer id : branch.getItemIds())
			{
				if (!budget.spend()) return Rs2TransportLoadout.unavailable();
				Demand next = new Demand(demand);
				if (branch.getQuantity() == 0)
				{
					if (snapshot.carried(id) != 0 || next.total(id) != 0) continue;
					next.absent.add(id);
				}
				else
				{
					if (next.absent.contains(id)) continue;
					boolean consumed = clause.consumed || id == ItemID.COINS || id == ItemID.ECTOTOKEN
						|| id == ItemID.SHANTAY_PASS;
					if (consumed && snapshot.equipment.getOrDefault(id, 0) > 0) continue;
					if (consumed) next.consumed.merge(id, branch.getQuantity(), Math::addExact);
					else next.reused.merge(id, branch.getQuantity(), Math::max);
					if (carriedOnly ? next.total(id) > snapshot.carried(id) : !snapshot.covers(id, next.total(id))) continue;
				}
				choices.add(new Candidate(next, next.total(id) <= snapshot.carried(id) ? 0 : 2));
			}
			for (Integer staff : branch.getStaffIds())
			{
				if (!budget.spend()) return Rs2TransportLoadout.unavailable();
				// This provider stores finite nature runes. Item ownership does not prove charges.
				if (staff == ItemID.NATURE_STAFF_CHARGED) continue;
				if (branch.getQuantity() <= 0 || (demand.staff != 0 && demand.staff != staff)
					|| demand.absent.contains(staff) || demand.consumed.containsKey(staff)) continue;
				if (!snapshot.equipment.isEmpty() && snapshot.equipment.getOrDefault(staff, 0) == 0) continue;
				Demand next = new Demand(demand);
				next.staff = staff;
				next.reused.merge(staff, 1, Math::max);
				if (carriedOnly ? next.total(staff) > snapshot.carried(staff) : !snapshot.covers(staff, next.total(staff))) continue;
				int priority = snapshot.equipment.getOrDefault(staff, 0) > 0 ? 0
					: next.total(staff) <= snapshot.carried(staff) ? 1 : 2;
				choices.add(new Candidate(next, priority));
			}
		}
		// Rank the entire OR clause, not merely variations within the first declared branch.
		choices.sort(Comparator.comparingInt(choice -> choice.priority));
		for (Candidate choice : choices)
		{
			if (budget.exhausted) return Rs2TransportLoadout.unavailable();
			Rs2TransportLoadout result = solve(clauses, authorities, snapshot, index + 1, choice.demand, budget, carriedOnly);
			if (result.isSatisfiable()) return result;
		}
		return Rs2TransportLoadout.unavailable();
	}

	private static Rs2TransportLoadout finish(List<TransportItems> authorities, Snapshot snapshot, Demand demand)
	{
		Map<Integer, Integer> withdrawals = new LinkedHashMap<>();
		Map<Integer, Integer> prepared = new HashMap<>(snapshot.inventory);
		snapshot.equipment.forEach((id, quantity) -> prepared.merge(id, quantity, Math::addExact));
		Set<Integer> ids = new TreeSet<>(demand.consumed.keySet());
		ids.addAll(demand.reused.keySet());
		for (Integer id : ids)
		{
			if (id == demand.staff && demand.consumed.containsKey(id)) return Rs2TransportLoadout.unavailable();
			int missing = Math.max(0, demand.total(id) - snapshot.carried(id));
			if (missing > 0)
			{
				if (!snapshot.bankKnown || snapshot.bank.getOrDefault(id, 0) < missing) return Rs2TransportLoadout.unavailable();
				withdrawals.put(id, missing);
				prepared.merge(id, missing, Math::addExact);
			}
		}
		for (TransportItems authority : authorities)
		{
			if (!authority.isSatisfiedBy(prepared, Set.of(), Integer.MAX_VALUE, snapshot.unlocks))
				return Rs2TransportLoadout.unavailable();
		}
		List<Integer> equip = demand.staff != 0 && snapshot.equipment.getOrDefault(demand.staff, 0) == 0
			? List.of(demand.staff) : List.of();
		return new Rs2TransportLoadout(withdrawals, equip, true);
	}

	private static boolean supported(Transport source)
	{
		if (source.getClass() != Transport.class || source.getType() == null
			|| source.getCurrencyAmount() < 0
			|| !source.getQuests().isEmpty() || !source.getVarbits().isEmpty() || !source.getVarplayers().isEmpty()
			|| source.isRequiresMaximumQuestPoints() || source.isRequiresMaximumTotalLevel()
			|| source.getRequiredCombatLevel() > 0) return false;
		for (int level : source.getSkillLevels()) if (level > 0) return false;
		switch (source.getType())
		{
			case TRANSPORT: case CANOE: case BOAT: case CHARTER_SHIP: case SHIP:
			case MINECART: case MAGIC_CARPET: return true;
			case TELEPORTATION_SPELL:
				return source.getParsedItemRequirements() != null && source.getParsedItemRequirements().size() > 0;
			case TELEPORTATION_ITEM: return source.isConsumable();
			default: return false;
		}
	}

	private static boolean sameRequirements(Rs2TransportEdge edge, Rs2TransportEdge fresh)
	{
		return edge.getType() == fresh.getType() && edge.isConsumable() == fresh.isConsumable()
			&& Objects.equals(edge.getOrigin(), fresh.getOrigin()) && edge.getDestination().equals(fresh.getDestination())
			&& Objects.equals(edge.getDisplayInfo(), fresh.getDisplayInfo())
			&& Objects.equals(edge.getAction(), fresh.getAction()) && Objects.equals(edge.getTarget(), fresh.getTarget())
			&& edge.getObjectId() == fresh.getObjectId() && edge.getDuration() == fresh.getDuration()
			&& edge.isMembers() == fresh.isMembers() && edge.getMaxWildernessLevel() == fresh.getMaxWildernessLevel()
			&& edge.isTeleport() == fresh.isTeleport() && edge.isSkillGated() == fresh.isSkillGated()
			&& edge.isQuestGated() == fresh.isQuestGated() && edge.isStateGated() == fresh.isStateGated()
			&& edge.getCurrencyName().equals(fresh.getCurrencyName()) && edge.getCurrencyAmount() == fresh.getCurrencyAmount()
			&& edge.getItemRequirements().equals(fresh.getItemRequirements());
	}

	private static int currencyId(String currency)
	{
		switch (currency.trim().toLowerCase(Locale.ROOT))
		{
			case "coins": return ItemID.COINS;
			case "ecto-token": return ItemID.ECTOTOKEN;
			default: return -1;
		}
	}

	private static int[] array(Set<Integer> ids) { return ids.stream().mapToInt(Integer::intValue).toArray(); }

	private static Snapshot capture()
	{
		if (Microbot.getClient() == null || Microbot.getClientThread() == null) return Snapshot.unknown();
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			if (Microbot.getClient() == null || Microbot.getClient().getGameState() != GameState.LOGGED_IN)
				return Snapshot.unknown();
			Map<Integer, Integer> inventory = containerCounts(Microbot.getClient().getItemContainer(InventoryID.INV));
			Map<Integer, Integer> equipment = containerCounts(Microbot.getClient().getItemContainer(InventoryID.WORN));
			if (inventory == null || equipment == null) return Snapshot.unknown();
			Map<Integer, Integer> bank = new HashMap<>();
			boolean bankKnown = Rs2Bank.hasBankMirrorSnapshot();
			if (bankKnown) Rs2Bank.bankItems().forEach(item -> bank.merge(item.getId(), item.getQuantity(), Math::addExact));
			return new Snapshot(inventory, equipment, bank, Set.of(), true, true, bankKnown);
		}).orElseGet(Snapshot::unknown);
	}

	/** Called only inside the capture callback; an absent/malformed container is not empty. */
	private static Map<Integer, Integer> containerCounts(ItemContainer container)
	{
		if (container == null) return null;
		Item[] items = container.getItems();
		if (items == null) return null;
		Map<Integer, Integer> counts = new HashMap<>();
		for (Item item : items)
		{
			if (item == null) return null;
			if (item.getId() == -1) continue;
			if (item.getId() <= 0 || item.getQuantity() < 0) return null;
			if (item.getQuantity() > 0) counts.merge(item.getId(), item.getQuantity(), Math::addExact);
		}
		return counts;
	}

	public static final class Snapshot
	{
		private final Map<Integer, Integer> inventory;
		private final Map<Integer, Integer> equipment;
		private final Map<Integer, Integer> bank;
		private final Set<Unlock> unlocks;
		private final boolean bankKnown;
		private final boolean inventoryKnown;
		private final boolean equipmentKnown;

		/** Explicitly supplied maps are trusted observations, including observed empty containers. */
		public Snapshot(Map<Integer, Integer> inventory, Map<Integer, Integer> equipment,
			Map<Integer, Integer> bank, Set<Unlock> unlocks, boolean bankKnown)
		{
			this(inventory, equipment, bank, unlocks, true, true, bankKnown);
		}

		/** Unknown container flags fail closed even when a map happens to contain quantities. */
		public Snapshot(Map<Integer, Integer> inventory, Map<Integer, Integer> equipment,
			Map<Integer, Integer> bank, Set<Unlock> unlocks,
			boolean inventoryKnown, boolean equipmentKnown, boolean bankKnown)
		{
			this.inventory = counts(inventory);
			this.equipment = counts(equipment);
			this.bank = counts(bank);
			this.unlocks = Set.copyOf(unlocks);
			this.bankKnown = bankKnown;
			this.inventoryKnown = inventoryKnown;
			this.equipmentKnown = equipmentKnown;
		}

		private static Snapshot unknown() { return new Snapshot(Map.of(), Map.of(), Map.of(), Set.of(), false, false, false); }

		private int carried(int id) { return Math.addExact(inventory.getOrDefault(id, 0), equipment.getOrDefault(id, 0)); }
		private boolean covers(int id, int required)
		{
			return (long) carried(id) + (bankKnown ? bank.getOrDefault(id, 0) : 0) >= required;
		}

		private static Map<Integer, Integer> counts(Map<Integer, Integer> input)
		{
			Map<Integer, Integer> copy = new HashMap<>();
			input.forEach((id, quantity) ->
			{
				if (id == null || id <= 0 || quantity == null || quantity < 0)
					throw new IllegalArgumentException("invalid captured item quantity");
				if (quantity > 0) copy.put(id, quantity);
			});
			return Collections.unmodifiableMap(copy);
		}
	}

	/** Candidate examination includes failures rejected before recursive descent. */
	private static final class Budget
	{
		private int remaining = 4096;
		private boolean exhausted;
		private boolean spend()
		{
			if (remaining == 0) { exhausted = true; return false; }
			remaining--;
			return true;
		}
	}

	private static final class Candidate
	{
		private final Demand demand;
		private final int priority;
		private Candidate(Demand demand, int priority) { this.demand = demand; this.priority = priority; }
	}

	private static final class Clause
	{
		private final Rs2TransportItemRequirement group;
		private final boolean consumed;
		private Clause(Rs2TransportItemRequirement group, boolean consumed) { this.group = group; this.consumed = consumed; }
	}

	private static final class Demand
	{
		private final Map<Integer, Integer> consumed = new HashMap<>();
		private final Map<Integer, Integer> reused = new HashMap<>();
		private final Set<Integer> absent = new HashSet<>();
		private int staff;
		private Demand() { }
		private Demand(Demand other)
		{
			consumed.putAll(other.consumed);
			reused.putAll(other.reused);
			absent.addAll(other.absent);
			staff = other.staff;
		}
		private int total(int id) { return Math.addExact(consumed.getOrDefault(id, 0), reused.getOrDefault(id, 0)); }
	}
}
