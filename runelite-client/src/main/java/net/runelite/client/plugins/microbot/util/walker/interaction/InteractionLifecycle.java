package net.runelite.client.plugins.microbot.util.walker.interaction;

import net.runelite.api.coords.WorldPoint;

/** Pure, monotonic operation state. No client reads, input, sleeps or route ownership. */
public final class InteractionLifecycle {
    public enum Decision { APPROACH, DISPATCH, WAIT, SUCCEEDED, CANCELLED, FAILED }
    private static final long OPERATION_MS = 120_000;
    private static final long EFFECT_MS = 8_000;
    private static final long RETRY_MS = 1_200;
    private long started = -1;
    private long dispatched = -1;
    private long retryAfter;
    private long lastProgress;
    private WorldPoint lastPosition;
    private int attempts;
    private boolean awaiting;
    private Decision terminal;

    public synchronized Decision update(long now, WorldPoint position, boolean sceneReady,
            boolean moving, boolean ready, boolean confirmed, boolean cancelled) {
        if (terminal != null) return terminal;
        if (cancelled) return terminal = Decision.CANCELLED;
        if (started < 0) { started = lastProgress = now; lastPosition = position; }
        if (now < started || now - started >= OPERATION_MS) return terminal = Decision.FAILED;
        if (attempts > 0 && confirmed) return terminal = Decision.SUCCEEDED;
        if (!sceneReady) return Decision.WAIT;
        if (position != null && !position.equals(lastPosition)) {
            lastPosition = position;
            lastProgress = now;
        }
        if (awaiting) {
            if (now - dispatched < EFFECT_MS && (moving || now - lastProgress < 2_400)) return Decision.WAIT;
            awaiting = false;
            retryAfter = now + RETRY_MS;
            if (attempts >= 3) return terminal = Decision.FAILED;
            return Decision.APPROACH;
        }
        if (now < retryAfter) return Decision.WAIT;
        return ready ? Decision.DISPATCH : Decision.APPROACH;
    }

    public synchronized void dispatched(long now) {
        attempts++;
        dispatched = lastProgress = now;
        awaiting = true;
    }

    public synchronized void rejected(long now) { retryAfter = now + RETRY_MS; }
    public synchronized void cancel() { terminal = Decision.CANCELLED; }
    public synchronized boolean active() { return terminal == null; }
    public synchronized boolean awaiting() { return awaiting; }
    public synchronized int attempts() { return attempts; }
}
