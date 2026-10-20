package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.client.plugins.microbot.shortestpath.PohPoolMode;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import org.junit.Test;
import static org.junit.Assert.*;

public class PohPoolPolicyTest {
    private boolean auto(int prayer, int hp, int energy, int special) {
        return PohPoolPolicy.shouldUse(PohPoolMode.AUTO, true, true, true, true,
            prayer, 99, hp, 100, energy, special);
    }
    @Test public void defaultsPreserveCurrentPoolFirstBehaviour() {
        ShortestPathConfig config = new ShortestPathConfig() { };
        assertEquals(PohPoolMode.ALWAYS, config.pohPoolMode());
        assertTrue(config.pohPoolPrayer() && config.pohPoolHealth()
            && config.pohPoolEnergy() && config.pohPoolSpecial());
        assertTrue(PohPoolPolicy.shouldUse(PohPoolMode.ALWAYS, false, false, false, false,
            99, 99, 100, 100, 10000, 1000));
    }
    @Test public void offSkipsEvenWhenEveryResourceIsMissing() {
        assertFalse(PohPoolPolicy.shouldUse(PohPoolMode.OFF, true, true, true, true,
            0, 99, 1, 100, 0, 0));
    }
    @Test public void exactThresholdsAndOverhealingDoNotTriggerAuto() {
        assertFalse(auto(99, 90, 8500, 1000));
        assertFalse(auto(110, 120, 10000, 1000));
    }
    @Test public void eachKnownDeficitCanIndependentlyTriggerAuto() {
        assertTrue(auto(98, 100, 10000, 1000));
        assertTrue(auto(99, 89, 10000, 1000));
        assertTrue(auto(99, 100, 8499, 1000));
        assertTrue(auto(99, 100, 10000, 999));
    }
    @Test public void disabledChecksCannotTriggerAuto() {
        assertFalse(PohPoolPolicy.shouldUse(PohPoolMode.AUTO, false, false, false, false,
            0, 99, 1, 100, 0, 0));
        assertFalse(PohPoolPolicy.shouldUse(PohPoolMode.AUTO, false, false, true, false,
            0, 99, 1, 100, 8500, 0));
    }
    @Test public void unavailableValuesAreNotInventedDeficits() {
        assertFalse(PohPoolPolicy.shouldUse(PohPoolMode.AUTO, true, true, true, true,
            -1, 0, -1, 0, -1, -1));
    }
}
