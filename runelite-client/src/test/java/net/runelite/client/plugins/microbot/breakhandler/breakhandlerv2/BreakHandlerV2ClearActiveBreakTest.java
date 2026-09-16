package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class BreakHandlerV2ClearActiveBreakTest {
    @Mock
    private ConfigManager configManager;

    @After
    public void resetGlobalBreakState() {
        BreakHandlerV2Script.resetActiveBreakState();
    }

    @Test
    public void clearPersistedBreakStateUnsetsBothBreakKeys() {
        BreakHandlerV2Script.clearPersistedBreakState(configManager);

        verify(configManager).unsetConfiguration(BreakHandlerV2Config.configGroup, "persistedBreakEnd");
        verify(configManager).unsetConfiguration(BreakHandlerV2Config.configGroup, "persistedBreakLogout");
    }

    @Test
    public void clearActiveBreakConfigDefaultsToFalse() {
        BreakHandlerV2Config config = new BreakHandlerV2Config() {};

        assertFalse(config.clearActiveBreak());
    }

    @Test
    public void resetActiveBreakStateReturnsToWaitingAndUnpausesScripts() {
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGGED_OUT);
        Microbot.pauseAllScripts.set(true);

        BreakHandlerV2Script.resetActiveBreakState();

        assertEquals(BreakHandlerV2State.WAITING_FOR_BREAK, BreakHandlerV2State.getCurrentState());
        assertFalse(Microbot.pauseAllScripts.get());
    }
}
