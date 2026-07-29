package net.runelite.client.plugins.microbot.util.poh.data;

import org.junit.Test;

import static net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse.HouseTeleportAction.NONE;
import static net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse.HouseTeleportAction.SPELL_DEFAULT;
import static net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse.HouseTeleportAction.SPELL_OUTSIDE;
import static net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse.HouseTeleportAction.TABLET_DEFAULT;
import static net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse.HouseTeleportAction.TABLET_OUTSIDE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class World330HostedHouseAccessTest {

    @Test
    public void advertisementAccessAcceptsEitherInventoryTabletOrCastableSpell() {
        assertTrue(World330HostedHouse.hasAdvertisementAccess(false, true, false, false));
        assertTrue(World330HostedHouse.hasAdvertisementAccess(false, false, false, true));
        assertTrue(World330HostedHouse.hasAdvertisementAccess(true, false, false, false));
        assertTrue(World330HostedHouse.hasAdvertisementAccess(false, false, true, false));
        assertFalse(World330HostedHouse.hasAdvertisementAccess(false, false, false, false));
    }

    @Test
    public void choosesOutsideVariantWhenInsideIsTheDefault() {
        assertEquals(TABLET_OUTSIDE,
                World330HostedHouse.chooseHouseTeleportAction(true, true, false));
        assertEquals(SPELL_OUTSIDE,
                World330HostedHouse.chooseHouseTeleportAction(false, true, false));
    }

    @Test
    public void usesDefaultActionWhenOutsideIsAlreadyTheDefault() {
        assertEquals(TABLET_DEFAULT,
                World330HostedHouse.chooseHouseTeleportAction(true, true, true));
        assertEquals(SPELL_DEFAULT,
                World330HostedHouse.chooseHouseTeleportAction(false, true, true));
        assertEquals(NONE,
                World330HostedHouse.chooseHouseTeleportAction(false, false, true));
    }
}
