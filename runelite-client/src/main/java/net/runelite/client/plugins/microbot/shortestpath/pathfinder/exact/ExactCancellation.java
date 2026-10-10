package net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Preparation loops participate in the same cancellation as the forward search. */
public final class ExactCancellation {
    private static final ThreadLocal<BooleanSupplier> ACTIVE = new ThreadLocal<>();
    private ExactCancellation() { }
    public static void begin(BooleanSupplier cancelled) { ACTIVE.set(cancelled); }
    public static void end() { ACTIVE.remove(); }
    public static void check() {
        BooleanSupplier active = ACTIVE.get();
        if (Thread.currentThread().isInterrupted() || (active != null && active.getAsBoolean())) {
            throw new CancellationException("Exact routing cancelled");
        }
    }
}
