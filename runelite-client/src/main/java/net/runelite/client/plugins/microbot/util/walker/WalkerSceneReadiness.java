package net.runelite.client.plugins.microbot.util.walker;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.GameState;

final class WalkerSceneReadiness {
    enum Result { READY, LOGGED_OUT, TIMED_OUT, CANCELLED }

    private WalkerSceneReadiness() { }

    static Result await(Supplier<GameState> state, BooleanSupplier hasPlayer,
                        BooleanSupplier cancelled, LongSupplier nanoTime,
                        LongConsumer pause, long timeoutMs) {
        long started = nanoTime.getAsLong();
        while (true) {
            if (cancelled.getAsBoolean()) return Result.CANCELLED;
            GameState current = state.get();
            if (current == GameState.LOGGED_IN && hasPlayer.getAsBoolean()) return Result.READY;
            if (current != GameState.LOADING && current != GameState.LOGGED_IN) return Result.LOGGED_OUT;
            if ((nanoTime.getAsLong() - started) / 1_000_000L >= timeoutMs) return Result.TIMED_OUT;
            pause.accept(100L);
        }
    }
}
