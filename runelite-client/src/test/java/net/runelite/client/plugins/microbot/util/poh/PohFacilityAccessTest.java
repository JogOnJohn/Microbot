package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.poh.data.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.runelite.client.plugins.microbot.util.poh.PohFacilityAccess.Access.*;

public class PohFacilityAccessTest {
    @Test public void unrelatedClosedDoorCannotTurnAnAbsentFacilityIntoObstruction() {
        assertEquals(MISSING, PohFacilityAccess.classify(true, 0, false, false, true));
        assertEquals(PENDING, PohFacilityAccess.classify(false, 0, false, false, true));
        assertEquals(PENDING, PohFacilityAccess.classify(true, 0, false, true, true));
        assertEquals(OBSTRUCTED, PohFacilityAccess.classify(true, 1, false, false, true));
    }

    @Test public void loadedButDisconnectedIsNotAMissingFacility() {
        assertEquals(UNREACHABLE, PohFacilityAccess.classify(true, 2, false, false, false));
        assertEquals(MISSING, PohFacilityAccess.classify(true, 0, false, false, false));
    }
    @Test public void oneReachableCopyIsEnoughAndNeverBlacklistsTheHouse() {
        assertEquals(AVAILABLE, PohFacilityAccess.classify(true, 3, true, false, false));
    }
    @Test public void loadingMovingAndClosedDoorsAreNotPersistentTrapEvidence() {
        assertEquals(PENDING, PohFacilityAccess.classify(false, 3, false, false, false));
        assertEquals(PENDING, PohFacilityAccess.classify(true, 3, false, true, false));
        assertEquals(OBSTRUCTED, PohFacilityAccess.classify(true, 3, false, false, true));
    }
    @Test public void coversPrimaryAndMountedFacilitiesButNotHouseEntry() {
        assertTrue(PohFacilityAccess.facilityIds(MountedXerics.GLADE).length > 0);
        assertTrue(PohFacilityAccess.facilityIds(JewelleryBox.GRAND_EXCHANGE).length > 0);
        assertTrue(PohFacilityAccess.facilityIds(NexusPortal.VARROCK).length > 0);
        assertTrue(PohFacilityAccess.facilityIds(MountedGlory.EDGEVILLE).length > 0);
        assertTrue(PohFacilityAccess.facilityIds(MountedDigsite.values()[0]).length > 0);
        assertTrue(PohFacilityAccess.facilityIds(MountedMythical.MYTHS_GUILD).length > 0);
        assertEquals(0, PohFacilityAccess.facilityIds(World330HostedHouse.ADVERTISED_HOUSE).length);
    }
    @Test public void dispatchWithoutLandingAndWrongPlaneDoNotComplete() {
        WorldPoint destination = new WorldPoint(1754, 3564, 0);
        assertFalse(PohTransport.hasLanded(new WorldPoint(2033, 7087, 2), destination));
        assertFalse(PohTransport.hasLanded(new WorldPoint(1754, 3564, 1), destination));
        assertFalse(PohTransport.hasLanded(null, destination));
        assertFalse(PohTransport.hasLanded(new WorldPoint(1764, 3564, 0), destination));
        assertTrue(PohTransport.hasLanded(new WorldPoint(1755, 3564, 0), destination));
    }
}
