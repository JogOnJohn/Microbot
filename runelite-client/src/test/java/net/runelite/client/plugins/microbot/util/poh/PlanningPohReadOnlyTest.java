package net.runelite.client.plugins.microbot.util.poh;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.util.poh.data.MountedXerics;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlanningPohReadOnlyTest
{
    @Test public void predictedObservationDoesNotChangeGenerationRejectionOrRecovery() {
        HostedHouseContext context = new HostedHouseContext();
        context.observe(true);
        context.reject();
        long generation = context.generation();
        assertFalse(context.facilitiesEligibleFor(true));
        assertTrue(context.facilitiesEligibleFor(false));
        assertEquals(generation, context.generation());
        assertFalse(context.facilitiesEligible());
        context.beginRecovery();
        assertFalse(context.facilitiesEligibleFor(false));
        assertEquals(generation, context.generation());
    }

    @Test @SuppressWarnings("unchecked") public void cooldownQueryDoesNotDeleteExpiredEntries() throws Exception {
        Field field = PohTeleports.class.getDeclaredField("failedTeleportBlockUntil");
        field.setAccessible(true);
        Map<String, Long> cooldowns = (Map<String, Long>) field.get(null);
        Map<String, Long> before = new HashMap<>(cooldowns);
        try {
            cooldowns.put(MountedXerics.LOOKOUT.displayInfo(), 0L);
            assertFalse(PohTeleports.isTeleportBlockedReadOnly(MountedXerics.LOOKOUT));
            assertEquals(Long.valueOf(0L), cooldowns.get(MountedXerics.LOOKOUT.displayInfo()));
            cooldowns.put(MountedXerics.LOOKOUT.displayInfo(), Long.MAX_VALUE);
            assertTrue(PohTeleports.isTeleportBlockedReadOnly(MountedXerics.LOOKOUT));
            assertEquals(Long.valueOf(Long.MAX_VALUE), cooldowns.get(MountedXerics.LOOKOUT.displayInfo()));
        } finally { cooldowns.clear(); cooldowns.putAll(before); }
    }
}
