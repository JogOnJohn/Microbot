package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BreakHandlerV2DeferralTest {
    @After
    public void clearPluginLock() {
        BreakHandlerScript.setLockState(false);
    }

    @Test
    public void defersRequestedBreakWhilePluginLockIsHeld() {
        BreakHandlerScript.setLockState(true);

        assertTrue(BreakHandlerV2Script.shouldDeferRequestedBreak());
    }

    @Test
    public void proceedsWhenPluginLockIsReleased() {
        BreakHandlerScript.setLockState(false);

        assertFalse(BreakHandlerV2Script.shouldDeferRequestedBreak());
    }
}
