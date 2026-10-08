package net.runelite.client.plugins.microbot.util.magic;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HouseTeleportActionTest {
    @Test public void outsideDefaultUsesActualPrimaryAction() {
        assertEquals(0, Rs2Magic.houseTeleportActionIndex(new String[]{"Outside", "Inside"}, "cast", true));
        assertEquals(1, Rs2Magic.houseTeleportActionIndex(new String[]{"Inside", "Outside"}, "Outside", false));
    }

    @Test public void explicitInsideDoesNotFollowOutsideDefault() {
        assertEquals(1, Rs2Magic.houseTeleportActionIndex(new String[]{"Outside", "Inside"}, "Inside", true));
    }

    @Test public void legacyCastIsOnlyTheConfiguredDestination() {
        assertEquals(0, Rs2Magic.houseTeleportActionIndex(new String[]{"Cast", "Inside"}, "Outside", true));
        assertEquals(0, Rs2Magic.houseTeleportActionIndex(new String[]{"Cast", "Outside"}, "Inside", false));
        assertEquals(-1, Rs2Magic.houseTeleportActionIndex(new String[]{"Cast"}, "Outside", false));
    }

    @Test public void missingActionFailsWithoutClickingADifferentOption() {
        assertEquals(-1, Rs2Magic.houseTeleportActionIndex(null, "Outside", true));
        assertEquals(-1, Rs2Magic.houseTeleportActionIndex(new String[]{null, "Configure"}, "cast", true));
    }
}
