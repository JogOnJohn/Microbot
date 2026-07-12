package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.World330HostedHouseTransport;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class PathfinderConfigWorld330SpellPreferenceTest
{
	private static final WorldPoint DESTINATION = new WorldPoint(3212, 3424, 0);

	@Test
	public void castableSpellAddsStrongPreferencePenaltyToWorld330Entry()
	{
		PathfinderConfig config = emptyConfig();
		World330HostedHouseTransport world330 = new World330HostedHouseTransport(DESTINATION);
		config.setUsableTeleports(Set.of(spellTeleport()));

		assertEquals(world330.getDuration() + PathfinderConfig.WORLD330_CASTABLE_SPELL_PENALTY,
			config.getTransportTravelCost(world330));
	}

	@Test
	public void world330KeepsNormalCostWhenNoSpellIsCastable()
	{
		PathfinderConfig config = emptyConfig();
		World330HostedHouseTransport world330 = new World330HostedHouseTransport(DESTINATION);
		config.setUsableTeleports(Collections.emptySet());

		assertEquals(world330.getDuration(), config.getTransportTravelCost(world330));
		assertEquals(19, world330.getDuration());
		assertEquals(Set.of(Set.of(ItemID.POH_TABLET_TELEPORTTOHOUSE)), world330.getItemIdRequirements());
	}

	@Test
	public void spellAvailabilityDoesNotPenalizeOrdinaryTeleportItems()
	{
		PathfinderConfig config = emptyConfig();
		config.setUsableTeleports(Set.of(spellTeleport()));
		Transport itemTeleport = new Transport(
			null, DESTINATION, "Test tablet", TransportType.TELEPORTATION_ITEM, true, 4);

		assertEquals(itemTeleport.getDuration(), config.getTransportTravelCost(itemTeleport));
	}

	private static Transport spellTeleport()
	{
		return new Transport(
			null, DESTINATION, "Varrock Teleport", TransportType.TELEPORTATION_SPELL, true, 4);
	}

	private static PathfinderConfig emptyConfig()
	{
		return new PathfinderConfig(
			null, new HashMap<>(), Collections.emptyList(), null, null);
	}
}
