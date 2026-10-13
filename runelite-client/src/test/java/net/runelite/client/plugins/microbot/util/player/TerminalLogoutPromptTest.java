/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.util.player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;
import org.junit.Test;
import static org.junit.Assert.*;

public class TerminalLogoutPromptTest {
    @Test public void waitsThroughBankClosureAndBlockedLogoutBeforeOneNotification() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        List<String> messages = new ArrayList<>();
        prompt.begin("Ran out of supplies: box traps");
        for (Rs2Player.TerminalLogoutState state : new Rs2Player.TerminalLogoutState[]{
                Rs2Player.TerminalLogoutState.BANK_CLOSING, Rs2Player.TerminalLogoutState.INPUT_BLOCKED,
                Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED}) {
            assertFalse(prompt.tick(() -> state, messages::add));
            assertTrue(prompt.active());
            assertTrue(messages.isEmpty());
        }
        assertTrue(prompt.tick(() -> Rs2Player.TerminalLogoutState.LOGGED_OUT, messages::add));
        assertFalse(prompt.active());
        assertEquals(List.of("Ran out of supplies: box traps"), messages);
        assertFalse(prompt.tick(() -> { throw new AssertionError("completed stop retried"); }, messages::add));
    }

    @Test public void explicitRestartCancelsOldLogoutAndMessage() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        prompt.begin("Old stop");
        prompt.reset();
        assertFalse(prompt.tick(() -> { throw new AssertionError("old stop continued"); },
                text -> { throw new AssertionError("old popup appeared"); }));
    }

    @Test public void suppressionIsRequestedOnceAcrossRetriesAndAgainForANewStop() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        int[] requests = {0};
        List<String> messages = new ArrayList<>();
        prompt.begin("First stop");
        for (int i = 0; i < 3; i++) {
            assertFalse(prompt.tick(request -> {
                request.run();
                return Rs2Player.TerminalLogoutState.BANK_CLOSING;
            }, messages::add, () -> requests[0]++, () -> { throw new AssertionError("renewed without delay"); }));
        }
        assertEquals(1, requests[0]);
        assertTrue(prompt.tick(request -> {
            request.run();
            return Rs2Player.TerminalLogoutState.LOGGED_OUT;
        }, messages::add, () -> requests[0]++, () -> { throw new AssertionError("renewed without delay"); }));
        assertEquals(1, requests[0]);
        prompt.begin("Second stop");
        assertTrue(prompt.tick(request -> {
            request.run();
            return Rs2Player.TerminalLogoutState.LOGGED_OUT;
        }, messages::add, () -> requests[0]++, () -> { throw new AssertionError("renewed without delay"); }));
        assertEquals(2, requests[0]);
        assertEquals(List.of("First stop", "Second stop"), messages);
    }

    @Test public void delayedLogoutKeepsRenewingSuppressionUntilTheLoginScreen() {
        AtomicLong clock = new AtomicLong();
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt(clock::get);
        int[] requests = {0};
        long[] lastSuppressionAt = {Long.MIN_VALUE};
        List<String> messages = new ArrayList<>();
        prompt.begin("Ran out of supplies");
        long logoutAt = AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS * 3;
        for (; clock.get() < logoutAt; clock.addAndGet(TerminalLogoutPrompt.RETRY_MILLIS)) {
            assertFalse(prompt.throttledTick(request -> {
                request.run();
                return Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED;
            }, messages::add, () -> {
                requests[0]++;
                lastSuppressionAt[0] = clock.get();
            }, () -> lastSuppressionAt[0] = clock.get()));
            assertTrue(clock.get() - lastSuppressionAt[0] < AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS);
        }
        assertEquals(1, requests[0]);
        assertTrue(prompt.throttledTick(request -> Rs2Player.TerminalLogoutState.LOGGED_OUT, messages::add,
                () -> requests[0]++, () -> { throw new AssertionError("renewed after logout"); }));
        assertTrue(clock.get() - lastSuppressionAt[0] < AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS);
        assertEquals(List.of("Ran out of supplies"), messages);
    }

    @Test public void throttleUsesTheInjectedClockEvenWhenItStartsNegative() {
        AtomicLong clock = new AtomicLong(-1_000_000L);
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt(clock::get);
        int[] attempts = {0};
        prompt.begin("Stop");
        Runnable tick = () -> prompt.throttledTick(request -> {
            attempts[0]++;
            return Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED;
        }, text -> {}, () -> {}, () -> {});
        tick.run();
        assertEquals(1, attempts[0]);
        clock.addAndGet(TerminalLogoutPrompt.RETRY_MILLIS - 1);
        tick.run();
        assertEquals(1, attempts[0]);
        clock.incrementAndGet();
        tick.run();
        assertEquals(2, attempts[0]);
        prompt.reset();
        prompt.begin("Next stop");
        tick.run();
        assertEquals(3, attempts[0]);
    }

    @Test public void restartFromAnotherThreadDuringLogoutIsNotCompletedOrSuppressedByTheOldTick() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        int[] requests = {0};
        List<String> messages = new ArrayList<>();
        prompt.begin("Old stop");
        assertFalse(prompt.tick(request -> {
            Thread restart = new Thread(() -> {
                prompt.reset();
                prompt.begin("New stop");
            });
            restart.start();
            try {
                restart.join(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertFalse(restart.isAlive());
            request.run();
            return Rs2Player.TerminalLogoutState.LOGGED_OUT;
        }, messages::add, () -> requests[0]++, () -> requests[0]++));
        assertEquals(0, requests[0]);
        assertTrue(messages.isEmpty());
        assertEquals("New stop", prompt.message());
        assertTrue(prompt.tick(request -> {
            request.run();
            return Rs2Player.TerminalLogoutState.LOGGED_OUT;
        }, messages::add, () -> requests[0]++, () -> { throw new AssertionError("renewed a new stop"); }));
        assertEquals(1, requests[0]);
        assertEquals(List.of("New stop"), messages);
    }
}
