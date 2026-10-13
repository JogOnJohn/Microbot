/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.util.player;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;

/** Keeps terminal logout separate from notification; the host pauses gameplay until completed. */
public final class TerminalLogoutPrompt {
    private String message;
    private long nextAttemptAt;
    private boolean suppressionRequested;

    public void begin(String text) {
        if (message == null) {
            message = text;
            suppressionRequested = false;
        }
    }

    public boolean active() { return message != null; }
    public String message() { return message; }
    public void reset() { message = null; nextAttemptAt = 0L; suppressionRequested = false; }

    public boolean tick() {
        long now = System.currentTimeMillis();
        if (now < nextAttemptAt) return false;
        nextAttemptAt = now + 600L;
        return tick(Rs2Player::logoutWithoutAutoLoginStep, Microbot::showMessage,
                () -> Microbot.getEventBus().post(new AutoLoginSuppressionRequest()));
    }

    public boolean tick(Supplier<Rs2Player.TerminalLogoutState> logout, Consumer<String> notify) {
        return tick(ignored -> logout.get(), notify, () -> {});
    }

    boolean tick(Function<Runnable, Rs2Player.TerminalLogoutState> logout, Consumer<String> notify,
                 Runnable requestSuppression) {
        if (message == null) return false;
        Rs2Player.TerminalLogoutState state = logout.apply(() -> {
            if (!suppressionRequested) {
                requestSuppression.run();
                suppressionRequested = true;
            }
        });
        if (state != Rs2Player.TerminalLogoutState.LOGGED_OUT) return false;
        String completedMessage = message;
        message = null;
        notify.accept(completedMessage);
        return true;
    }
}
