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
    @org.junit.Test
    public void advertisementMustBeNearbyAndOnTheSamePlane() {
        net.runelite.api.coords.WorldPoint board = new net.runelite.api.coords.WorldPoint(2953, 3224, 0);
        org.junit.Assert.assertTrue(World330HostedHouse.isWithinAdvertisementApproach(
                new net.runelite.api.coords.WorldPoint(2953, 3221, 0), board));
        org.junit.Assert.assertFalse(World330HostedHouse.isWithinAdvertisementApproach(
                new net.runelite.api.coords.WorldPoint(2953, 3211, 0), board));
        org.junit.Assert.assertFalse(World330HostedHouse.isWithinAdvertisementApproach(
                new net.runelite.api.coords.WorldPoint(2953, 3224, 1), board));
        org.junit.Assert.assertFalse(World330HostedHouse.isWithinAdvertisementApproach(board, null));
    }


    @Test
    public void unsuitableHostIsSkippedAcrossSelectionsButCooldownExpires() {
        World330HostedHouse.excludeUnsuitableHost(" <col=ffffff>Recovery test host</col> ", 1000);
        assertTrue(World330HostedHouse.isExcludedAdvertisedHost("recovery test host", 1001));
        assertFalse(World330HostedHouse.isExcludedAdvertisedHost("different test host", 1001));
        assertFalse(World330HostedHouse.isExcludedAdvertisedHost("recovery test host", 601000));
        assertTrue(World330HostedHouse.isExcludedAdvertisedHost("V 3", 601000));
    }

    @Test
    public void permanentlyExcludesTrollHostRegardlessOfWidgetFormatting() {
        assertTrue(World330HostedHouse.isExcludedAdvertisedHost("V 3"));
        assertTrue(World330HostedHouse.isExcludedAdvertisedHost("v 3"));
        assertTrue(World330HostedHouse.isExcludedAdvertisedHost(" <col=ffffff>V\u00A03</col> "));
    }

    @Test
    public void hostExclusionDoesNotMatchOtherNames() {
        assertFalse(World330HostedHouse.isExcludedAdvertisedHost("V 30"));
        assertFalse(World330HostedHouse.isExcludedAdvertisedHost("AV 3"));
        assertFalse(World330HostedHouse.isExcludedAdvertisedHost("Another host"));
        assertFalse(World330HostedHouse.isExcludedAdvertisedHost(null));
    }

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
