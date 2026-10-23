package net.runelite.client.plugins.gpu;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Temporary metadata capture only; never changes GL ordering or waits for the GPU. */
final class GpuReplayCapture
{
    static final boolean ENABLED = Boolean.getBoolean("microbot.gpu.lifecycleCapture");
    private static final Window WINDOW = new Window(1_000_000,
        Math.max(10, Math.min(300, Integer.getInteger("microbot.gpu.lifecycleCapture.seconds", 120))) * 1_000_000_000L);

    static final class Window
    {
        final int limit;
        final long duration;
        long start, sequence;
        boolean started, finished;

        Window(int limit, long duration) { this.limit = limit; this.duration = duration; }

        String accept(String operation, long now)
        {
            if (finished) return null;
            if (!started)
            {
                started = true; start = now;
                if (!operation.equals("context-created")) { finished = true; return "INCOMPLETE"; }
                return "BEGIN";
            }
            if (operation.equals("context-created") || operation.equals("context-destroyed"))
            { finished = true; return "INCOMPLETE"; }
            if (sequence >= limit) { finished = true; return "INCOMPLETE"; }
            if (now - start >= duration) { finished = true; return "END"; }
            return operation;
        }
    }

    @Name("microbot.GpuReplay")
    @Category({"Microbot", "Diagnostics"})
    @Enabled(false)
    @StackTrace(false)
    static final class Operation extends Event
    {
        String operation, kind, site;
        long sequence, nanos, serial, peer, bytes, context;
        int slot, flags;
    }

    static synchronized void record(String operation, String kind, String site, long serial,
        long peer, long bytes, long context, int slot, int flags)
    {
        if (!ENABLED) return;
        Operation event = new Operation();
        if (!event.isEnabled()) return;
        long now = System.nanoTime();
        String accepted = WINDOW.accept(operation, now);
        if (accepted == null) return;
        event.operation = accepted; event.kind = kind; event.site = site;
        event.sequence = ++WINDOW.sequence; event.nanos = now; event.serial = serial;
        event.peer = peer; event.bytes = bytes; event.context = context;
        event.slot = slot; event.flags = flags; event.commit();
    }

    private GpuReplayCapture() { }
}
