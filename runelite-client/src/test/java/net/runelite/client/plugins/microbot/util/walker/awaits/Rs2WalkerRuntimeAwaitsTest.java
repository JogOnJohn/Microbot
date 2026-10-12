package net.runelite.client.plugins.microbot.util.walker.awaits;

import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.Global;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Rs2WalkerRuntimeAwaitsTest {
    @Test public void nullPathfinderDoesNotWait() {
        try (MockedStatic<Global> global = mockStatic(Global.class)) {
            assertFalse(Rs2WalkerRuntimeAwaits.awaitPathfinderDone(null, 1200));
            global.verifyNoInteractions();
        }
    }

    @Test public void readyFlagUsesFastPollAndPreservesTheCallerTimeout() {
        Pathfinder pathfinder = mock(Pathfinder.class);
        when(pathfinder.isDone()).thenReturn(true);
        try (MockedStatic<Global> global = mockStatic(Global.class)) {
            global.when(() -> Global.sleepUntilTrue(any(BooleanSupplier.class), eq(25), eq(1200)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(0)).getAsBoolean());
            assertTrue(Rs2WalkerRuntimeAwaits.awaitPathfinderDone(pathfinder, 1200));
            global.verify(() -> Global.sleepUntilTrue(any(BooleanSupplier.class), eq(25), eq(1200)));
        }
    }

    @Test public void timeoutOrHumanCancellationIsNotReportedAsReady() {
        Pathfinder pathfinder = mock(Pathfinder.class);
        when(pathfinder.isDone()).thenReturn(true);
        try (MockedStatic<Global> global = mockStatic(Global.class)) {
            global.when(() -> Global.sleepUntilTrue(any(BooleanSupplier.class), eq(25), eq(500)))
                    .thenReturn(false);
            assertFalse(Rs2WalkerRuntimeAwaits.awaitPathfinderDone(pathfinder, 500));
        }
    }
}
