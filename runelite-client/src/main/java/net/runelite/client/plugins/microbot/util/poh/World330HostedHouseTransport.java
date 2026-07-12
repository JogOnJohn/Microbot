package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse;

import java.util.Set;

public class World330HostedHouseTransport extends Transport {
    public World330HostedHouseTransport(WorldPoint destination) {
        super(destination, "W330 hosted house: Teleport to house tablet", TransportType.TELEPORTATION_ITEM, true, 19,
                Set.of(Set.of(ItemID.POH_TABLET_TELEPORTTOHOUSE)));
    }

    public boolean execute() {
        return World330HostedHouse.ADVERTISED_HOUSE.execute();
    }
}
