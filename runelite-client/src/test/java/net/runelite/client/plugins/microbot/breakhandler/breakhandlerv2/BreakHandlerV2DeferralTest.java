package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.BreakPreparation;
import org.junit.After;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BreakHandlerV2DeferralTest {
    @After
    public void clearPluginLock() {
        BreakHandlerScript.setLockState(false);
        BreakPreparation.finishBreak();
    }

    @Test
    public void clearingBreakClearsCooperativeRequest() {
        try (BreakPreparation.Handle handle = BreakPreparation.register("test")) {
            assertTrue(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
            BreakHandlerV2Script.resetActiveBreakState();
            assertFalse(handle.isRequested());
        }
    }

    @Test
    public void requestsPreparationEvenWhenLegacyLockIsHeld() {
        try (BreakPreparation.Handle handle = BreakPreparation.register("test")) {
            BreakHandlerScript.setLockState(true);
            assertTrue(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
            assertTrue(handle.isRequested());
            handle.ready();
            assertTrue(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
            BreakHandlerScript.setLockState(false);
            assertFalse(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
        }
    }

    @Test
    public void defersRequestedBreakWhilePluginLockIsHeld() {
        BreakHandlerScript.setLockState(true);

        assertTrue(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
    }

    @Test
    public void doesNotDeferNewBreakWhenPluginLockIsReleased() {
        BreakHandlerScript.setLockState(false);

        assertFalse(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
    }

    @Test
    public void doesNotDeferActiveNoLogoutBreakWhenPluginLockIsHeld() {
        BreakHandlerScript.setLockState(true);

        assertFalse(BreakHandlerV2Script.shouldDeferRequestedBreak(Instant.now()));
    }
}
