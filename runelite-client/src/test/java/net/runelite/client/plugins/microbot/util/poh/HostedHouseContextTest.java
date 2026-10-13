package net.runelite.client.plugins.microbot.util.poh;

import org.junit.Test;
import static org.junit.Assert.*;

public class HostedHouseContextTest {
    @Test public void failedDepartureCannotReenableOtherFacilitiesInTheSameHouse() {
        HostedHouseContext context = new HostedHouseContext();
        context.observe(true);
        long house = context.generation();
        context.beginRecovery();
        assertFalse(context.facilitiesEligible());
        context.observe(true);
        context.endRecovery();
        assertFalse(context.facilitiesEligible());
        assertEquals(house, context.generation());
    }

    @Test public void differentHostAtSameTemplateCoordinatesHasFreshIdentity() {
        HostedHouseContext context = new HostedHouseContext();
        context.observe(true);
        long first = context.generation();
        context.beginRecovery();
        context.observe(false);
        assertFalse("Pending entry cannot expose an old route", context.facilitiesEligible());
        context.observe(true);
        assertFalse(context.facilitiesEligible());
        context.endRecovery();
        assertTrue(context.facilitiesEligible());
        assertTrue(context.generation() > first);
        context.reject();
        assertFalse("The last unsuitable host is rejected too", context.facilitiesEligible());
    }

    @Test public void recoveryFailuresNeverBlockAGoodDestinationGlobally() {
        assertFalse(PohExecutionResult.HOUSE_RECOVERY_FAILED.shouldBlockDestination());
        assertFalse(PohExecutionResult.CANCELLED.shouldBlockDestination());
        assertFalse(PohExecutionResult.SCENE_PENDING.shouldBlockDestination());
        assertFalse(PohExecutionResult.SUCCESS.shouldBlockDestination());
        assertTrue(PohExecutionResult.DESTINATION_UNAVAILABLE.shouldBlockDestination());
        assertTrue(PohExecutionResult.TRANSIENT_FAILURE.shouldBlockDestination());
    }
}
