package net.runelite.client.plugins.microbot.util.poh;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class PohTransportRecoveryTest {
    @Test
    public void doesNotLeaveEquippedHouse() {
        assertTrue(PohTransport.ensureFacility(() -> true, () -> {
            fail("Must retain an equipped house");
            return false;
        }));
    }

    @Test
    public void switchesUntilRequiredFacilityIsFound() {
        AtomicInteger switches = new AtomicInteger();
        assertTrue(PohTransport.ensureFacility(() -> switches.get() == 2,
                () -> { switches.incrementAndGet(); return true; }));
        assertEquals(2, switches.get());
    }

    @Test
    public void capsMissingFacilityRecoveryAtThreeHouseSwitches() {
        AtomicInteger switches = new AtomicInteger();
        assertFalse(PohTransport.ensureFacility(() -> false,
                () -> { switches.incrementAndGet(); return true; }));
        assertEquals(3, switches.get());
    }

    @Test
    public void failedExitOrEntryStopsRecovery() {
        AtomicInteger switches = new AtomicInteger();
        assertFalse(PohTransport.ensureFacility(() -> false,
                () -> { switches.incrementAndGet(); return false; }));
        assertEquals(1, switches.get());
    }

    @Test
    public void interruptionPreventsFurtherInteractions() {
        Thread.currentThread().interrupt();
        try {
            assertFalse(PohTransport.ensureFacility(() -> { fail(); return true; },
                    () -> { fail(); return true; }));
        } finally {
            Thread.interrupted();
        }
    }
}
