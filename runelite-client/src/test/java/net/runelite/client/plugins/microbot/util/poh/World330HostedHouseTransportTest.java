package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class World330HostedHouseTransportTest {

    @Test
    public void usesDeclaredHostedHouseDuration() {
        World330HostedHouseTransport transport = new World330HostedHouseTransport(
                World330HostedHouse.POH_INSTANCE_ANCHOR);

        assertEquals(World330HostedHouse.ADVERTISED_HOUSE.getDuration(), transport.getDuration());
    }
}
