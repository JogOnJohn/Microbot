package net.runelite.client.plugins.microbot;

import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class CantReachTargetStateTest {
    @Test
    public void resetPreservesPauseAndReturnsOriginalAttemptCount() {
        boolean savedLatch = Microbot.cantReachTarget;
        int savedRetries = Microbot.cantReachTargetRetries;
        boolean savedPause = Microbot.pauseAllScripts.get();
        try {
            Microbot.cantReachTarget = true;
            Microbot.cantReachTargetRetries = 5;
            Microbot.pauseAllScripts.set(true);
            assertEquals(5, Microbot.resetCantReachTargetState());
            assertFalse(Microbot.cantReachTarget);
            assertEquals(0, Microbot.cantReachTargetRetries);
            assertTrue(Microbot.pauseAllScripts.get());
            assertEquals(0, Microbot.resetCantReachTargetState());
        } finally {
            Microbot.cantReachTarget = savedLatch;
            Microbot.cantReachTargetRetries = savedRetries;
            Microbot.pauseAllScripts.set(savedPause);
        }
    }

    @Test
    public void exhaustedNpcClickClearsLatchAndStillPauses() {
        boolean savedLatch = Microbot.cantReachTarget;
        int savedRetries = Microbot.cantReachTargetRetries;
        boolean savedPause = Microbot.pauseAllScripts.get();
        boolean savedDetection = Microbot.isCantReachTargetDetectionEnabled;
        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class);
             MockedStatic<Rs2Random> random = Mockito.mockStatic(Rs2Random.class)) {
            microbot.when(Microbot::resetCantReachTargetState).thenCallRealMethod();
            random.when(() -> Rs2Random.between(3, 5)).thenReturn(3);
            Rs2NpcModel npc = spy(new Rs2NpcModel(mock(NPC.class)));
            doReturn("Test NPC").when(npc).getName();
            doReturn(false).when(npc).hasLineOfSight();
            Microbot.isCantReachTargetDetectionEnabled = true;
            Microbot.cantReachTarget = true;
            Microbot.cantReachTargetRetries = 5;
            Microbot.pauseAllScripts.set(false);

            assertFalse(npc.click("Talk-to"));
            assertFalse(Microbot.cantReachTarget);
            assertEquals(0, Microbot.cantReachTargetRetries);
            assertTrue(Microbot.pauseAllScripts.get());
            microbot.verify(() -> Microbot.showMessage(contains("5 times")));
        } finally {
            Microbot.cantReachTarget = savedLatch;
            Microbot.cantReachTargetRetries = savedRetries;
            Microbot.pauseAllScripts.set(savedPause);
            Microbot.isCantReachTargetDetectionEnabled = savedDetection;
        }
    }
}
