package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse;

import java.util.Collections;

public class World330HostedHouseTransport extends Transport {
    public World330HostedHouseTransport(WorldPoint destination) {
        super(destination, "W330 hosted house: House teleport", TransportType.TELEPORTATION_ITEM, true, 19,
                Collections.emptySet(),
                World330HostedHouse.ADVERTISED_HOUSE.getDuration());
    }

    public boolean execute() {
        return World330HostedHouse.ADVERTISED_HOUSE.execute();
    }
}
