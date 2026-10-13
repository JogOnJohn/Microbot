package net.runelite.client.plugins.microbot.util.walker;

import org.junit.Test;
import static org.junit.Assert.*;

public class Rs2WalkerHandoffFailureTest {
    @Test public void onlyPositiveOwnedExitCompletionSuppressesFailure() {
        assertFalse(Rs2Walker.shouldReportBankWalkFailure(WalkerState.EXIT, true));
        assertTrue(Rs2Walker.shouldReportBankWalkFailure(WalkerState.EXIT, false));
        assertTrue(Rs2Walker.shouldReportBankWalkFailure(WalkerState.UNREACHABLE, true));
        assertTrue(Rs2Walker.shouldReportBankWalkFailure(WalkerState.UNREACHABLE, false));
        assertFalse(Rs2Walker.shouldReportBankWalkFailure(WalkerState.MOVING, false));
        assertFalse(Rs2Walker.shouldReportBankWalkFailure(WalkerState.ARRIVED, false));
    }
}
