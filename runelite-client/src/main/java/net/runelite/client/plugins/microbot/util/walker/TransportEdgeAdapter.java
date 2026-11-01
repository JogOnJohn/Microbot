package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.ItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.TransportItems;

/**
 * Pure snapshot conversion, not route admission or execution. No live unlock, inventory, house,
 * world or client state is consulted. Gate flags describe requirements, never their satisfaction.
 * All executor mappings remain explicitly unsupported until independently validated; converting
 * an edge does not activate the upstream executor or change the legacy dispatch policy.
 */
public final class TransportEdgeAdapter
{
	private TransportEdgeAdapter() { }

	public static Rs2TransportEdge toEdge(Transport transport)
	{
		Objects.requireNonNull(transport, "transport");
		List<Rs2TransportItemRequirement> requirements = new ArrayList<>();
		boolean unlockGated = false;
		TransportItems parsed = transport.getParsedItemRequirements();
		if (parsed != null)
		{
			// The legacy item-id projection discards quantities and unlocks. Never combine it
			// with the authoritative parsed requirements or use the flattened legacy arrays.
			for (ItemRequirement requirement : parsed.getRequirements())
			{
				List<Rs2TransportItemRequirement.Branch> branches = new ArrayList<>();
				for (ItemRequirement.Branch branch : requirement.getBranches())
				{
					String unlock = branch.getUnlock() == null ? null : branch.getUnlock().name();
					unlockGated |= unlock != null;
					branches.add(new Rs2TransportItemRequirement.Branch(ids(branch.getItemIds()),
						ids(branch.getStaffIds()), ids(branch.getOffhandIds()), branch.getQuantity(), unlock));
				}
				requirements.add(Rs2TransportItemRequirement.fromBranches(branches));
			}
		}
		else
		{
			for (Set<Integer> alternatives : transport.getItemIdRequirements())
			{
				requirements.add(Rs2TransportItemRequirement.fromBranches(List.of(
					new Rs2TransportItemRequirement.Branch(alternatives, Set.of(), Set.of(), 1, null))));
			}
		}
		Rs2TransportType type = transport.getType() == null ? Rs2TransportType.UNKNOWN
			: Rs2TransportType.valueOf(transport.getType().name());
		boolean skillGated = transport.isRequiresMaximumTotalLevel()
			|| transport.getRequiredCombatLevel() > 0
			|| Arrays.stream(transport.getSkillLevels()).anyMatch(level -> level > 0);
		boolean questGated = transport.isRequiresMaximumQuestPoints() || !transport.getQuests().isEmpty();
		boolean stateGated = unlockGated || !transport.getVarbits().isEmpty()
			|| !transport.getVarplayers().isEmpty();
		return new Rs2TransportEdge(transport.getOrigin(), transport.getDestination(), type,
			Rs2TransportExecutor.UNSUPPORTED, Rs2TerminalTravelMode.UNSUPPORTED,
			transport.getDisplayInfo(), transport.getAction(), transport.getName(), transport.getObjectId(),
			transport.getDuration(), TransportType.isTeleport(transport.getType(), transport.getOrigin()),
			transport.isConsumable(), transport.isMembers(), transport.getMaxWildernessLevel(),
			transport.getCurrencyName(), transport.getCurrencyAmount(), requirements,
			skillGated, questGated, stateGated, new Source(transport));
	}

	/**
	 * Returns the exact original object, including subtype and mutable legacy gates. This is not
	 * reconstruction from public metadata: a fabricated edge cannot safely supply local behaviour.
	 * Subsequent legacy mutations do not change the edge's immutable descriptive snapshot.
	 */
	public static Transport toLegacy(Rs2TransportEdge edge)
	{
		Objects.requireNonNull(edge, "edge");
		if (!(edge.getSourceIdentity() instanceof Source))
		{
			throw new IllegalArgumentException("edge was not created by TransportEdgeAdapter");
		}
		return ((Source) edge.getSourceIdentity()).transport;
	}

	private static Set<Integer> ids(int[] ids)
	{
		Set<Integer> result = new LinkedHashSet<>();
		if (ids != null) Arrays.stream(ids).forEach(result::add);
		return result;
	}

	private static final class Source
	{
		private final Transport transport;
		private Source(Transport transport) { this.transport = transport; }
	}
}
