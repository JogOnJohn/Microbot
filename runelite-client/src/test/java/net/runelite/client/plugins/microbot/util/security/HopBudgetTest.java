package net.runelite.client.plugins.microbot.util.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HopBudgetTest {
    private static final long ONE_TOKEN_MILLIS = (long) Math.ceil(TimeUnit.HOURS.toMillis(1) / 93.0);

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private HopBudget budget(int capacity, int reserve) {
        HopBudget budget = new HopBudget(clock::get, new HopBudget.MemoryStore());
        budget.configure(true, capacity, 93, reserve);
        return budget;
    }

    private static void hop(HopBudget budget, int times) {
        for (int i = 0; i < times; i++) {
            budget.recordHop();
        }
    }

    @Test public void freshBudgetStartsFullAndAllowsHops() {
        HopBudget budget = budget(390, 20);
        assertEquals(390, budget.available());
        assertEquals(0L, budget.millisUntilHopAllowed());
    }

    @Test public void hopsStopAtTheReserveAndResumeAfterOneTokenRefills() {
        HopBudget budget = budget(30, 5);
        hop(budget, 24);
        assertEquals(6, budget.available());
        assertEquals(0L, budget.millisUntilHopAllowed());
        hop(budget, 1);
        assertEquals(5, budget.available());
        long wait = budget.millisUntilHopAllowed();
        assertTrue(Math.abs(wait - ONE_TOKEN_MILLIS) <= 1);
        clock.addAndGet(wait - 2);
        assertTrue(budget.millisUntilHopAllowed() > 0);
        clock.addAndGet(2);
        assertEquals(0L, budget.millisUntilHopAllowed());
    }

    @Test public void refillNeverExceedsCapacity() {
        HopBudget budget = budget(30, 5);
        hop(budget, 10);
        clock.addAndGet(TimeUnit.HOURS.toMillis(5));
        assertEquals(30, budget.available());
    }

    @Test public void lockoutEmptiesTheBudget() {
        HopBudget budget = budget(390, 20);
        budget.recordLockout();
        assertEquals(0, budget.available());
        assertTrue(budget.millisUntilHopAllowed() >= 20 * ONE_TOKEN_MILLIS);
    }

    @Test public void disabledBudgetNeverWaitsButStillCounts() {
        HopBudget budget = budget(10, 2);
        budget.configure(false, 10, 93, 2);
        hop(budget, 10);
        assertEquals(0, budget.available());
        assertEquals(0L, budget.millisUntilHopAllowed());
    }

    @Test public void reserveIsClampedBelowCapacity() {
        HopBudget budget = budget(5, 50);
        assertEquals(0L, budget.millisUntilHopAllowed());
        hop(budget, 1);
        assertTrue(budget.millisUntilHopAllowed() > 0);
    }

    @Test public void clockGoingBackwardsDoesNotDrainOrRefill() {
        HopBudget budget = budget(30, 5);
        hop(budget, 10);
        clock.addAndGet(-TimeUnit.HOURS.toMillis(1));
        assertEquals(20, budget.available());
    }

    @Test public void fileStoreIsSharedBetweenClientsOnTheSameNetwork() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        HopBudget first = budget(30, 5);
        first.persistTo(file, "direct");
        HopBudget second = budget(30, 5);
        second.persistTo(file, "direct");
        HopBudget proxied = budget(30, 5);
        proxied.persistTo(file, "proxy.10.0.0.1");

        hop(first, 4);
        hop(second, 3);
        assertEquals(23, first.available());
        assertEquals(23, second.available());
        assertEquals(30, proxied.available());

        HopBudget restarted = budget(30, 5);
        restarted.persistTo(file, "direct");
        assertEquals(23, restarted.available());
    }

    @Test public void interruptedThreadStillUsesTheSharedFileAndKeepsItsInterrupt() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        HopBudget first = budget(30, 5);
        first.persistTo(file, "direct");
        HopBudget second = budget(30, 5);
        second.persistTo(file, "direct");

        Thread.currentThread().interrupt();
        try {
            first.recordHop();
            assertEquals(29, first.available());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(29, second.available());
    }

    @Test public void corruptFileIsTreatedAsAFullBudget() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        Files.write(file, "direct.tokens=abc\ndirect.updatedAt=\n".getBytes(StandardCharsets.UTF_8));
        HopBudget budget = budget(30, 5);
        budget.persistTo(file, "direct");
        assertEquals(30, budget.available());
        budget.recordHop();
        assertEquals(29, budget.available());
    }

    @Test public void unwritableFileFallsBackToThisClientOnly() throws Exception {
        Path directory = folder.newFolder("blocked").toPath();
        HopBudget budget = budget(30, 5);
        hop(budget, 2);
        budget.persistTo(directory, "direct");
        assertEquals(28, budget.available());
        budget.recordHop();
        assertEquals(27, budget.available());
    }

    @Test public void networkKeyFollowsTheSocksProxy() {
        String previous = System.getProperty("socksProxyHost");
        try {
            System.clearProperty("socksProxyHost");
            assertEquals("direct", HopBudget.networkKey());
            System.setProperty("socksProxyHost", "proxy.example.com");
            assertEquals("proxy.proxy.example.com", HopBudget.networkKey());
            System.setProperty("socksProxyHost", "fe80::1");
            assertEquals("proxy.fe80__1", HopBudget.networkKey());
        } finally {
            if (previous == null) {
                System.clearProperty("socksProxyHost");
            } else {
                System.setProperty("socksProxyHost", previous);
            }
        }
    }
}
