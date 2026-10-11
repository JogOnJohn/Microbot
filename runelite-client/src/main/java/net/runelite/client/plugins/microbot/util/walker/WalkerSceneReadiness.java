package net.runelite.client.plugins.microbot.util.walker;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import static net.runelite.client.plugins.microbot.util.Global.sleep;

final class WalkerSceneReadiness {
    enum Result { READY, LOGGED_OUT, TIMED_OUT, CANCELLED }

    private WalkerSceneReadiness() { }

    static GameState gameState() {
        if (Microbot.getClient() == null || Microbot.getClientThread() == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getGameState()).orElse(null);
    }

    static boolean hasPlayer() { return Rs2Player.getWorldLocation() != null; }

    static void pause(long ms) { sleep((int) ms); }

    static boolean inInstance() {
        if (Microbot.getClient() == null || Microbot.getClientThread() == null) return true;
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getTopLevelWorldView() == null
                || Microbot.getClient().getTopLevelWorldView().isInstance()).orElse(true);
    }

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
