package net.runelite.client.plugins.microbot.util.walker.banking;

import org.junit.Test;
import static org.junit.Assert.*;

public class BankRouteCostTest {
    @Test public void lowerBoundIsStrictAndOverflowSafe() {
        assertTrue(BankRouteCost.cannotBeatDirect(103, 24, 80));
        assertFalse(BankRouteCost.cannotBeatDirect(104, 24, 80));
        assertFalse(BankRouteCost.cannotBeatDirect(105, 24, 80));
        assertFalse(BankRouteCost.cannotBeatDirect(0, 0, 0));
        assertFalse(BankRouteCost.cannotBeatDirect(-1, 24, 80));
        assertFalse(BankRouteCost.cannotBeatDirect(Integer.MAX_VALUE, 24, 80));
        assertTrue(BankRouteCost.cannotBeatDirect(Integer.MAX_VALUE - 1,
                Integer.MAX_VALUE, Integer.MAX_VALUE));
        for (int cost = 0; cost < 104; cost++)
            for (int bank = 0; bank < 150; bank++)
                assertFalse(BankRouteCost.prefersBank(cost, bank, 24, 80, true));
    }
    @Test
    public void observedOverheadConvertsToRunningTilesAndDiscountsOpenBank() {
        assertEquals(24, BankRouteCost.interactionOverheadTiles(7, false));
        assertEquals(18, BankRouteCost.interactionOverheadTiles(7, true));
        assertEquals(0, BankRouteCost.interactionOverheadTiles(0, false));
        assertEquals(0, BankRouteCost.interactionOverheadTiles(-1, true));
        assertEquals(200, BankRouteCost.interactionOverheadTiles(Integer.MAX_VALUE, false));
    }

    @Test
    public void marginalBankDetourLosesButLargeSavingStillWins() {
        assertFalse(BankRouteCost.prefersBank(100, 90, 24, 0, false));
        assertTrue(BankRouteCost.prefersBank(162, 21, 24, 80, false));
        assertFalse(BankRouteCost.prefersBank(120, 21, 24, 80, false));
    }

    @Test
    public void tiesRespectPreferenceOnlyAfterOverheadAndMinimumSaving() {
        assertFalse(BankRouteCost.prefersBank(50, 50, 24, 0, true));
        assertTrue(BankRouteCost.prefersBank(50, 50, 0, 0, true));
        assertFalse(BankRouteCost.prefersBank(50, 50, 0, 0, false));
        assertFalse(BankRouteCost.prefersBank(50, 50, 0, 1, true));
    }

    @Test
    public void unavailableRoutesAndLargeCostsCannotOverflowIntoASaving() {
        assertFalse(BankRouteCost.prefersBank(100, -1, 24, 0, true));
        assertFalse(BankRouteCost.prefersBank(100, Integer.MAX_VALUE, 24, 0, true));
        assertTrue(BankRouteCost.prefersBank(-1, 100, 24, 80, false));
        assertFalse(BankRouteCost.prefersBank(Integer.MAX_VALUE - 1,
                Integer.MAX_VALUE - 2, 24, 0, false));
    }
}
