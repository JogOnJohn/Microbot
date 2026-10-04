package net.runelite.client.plugins.microbot.breakhandler;

import java.util.HashMap;
import java.util.Map;

/** Cooperative preparation before a handler pauses, stops, or logs out a script. */
public final class BreakPreparation
{
    private static final Map<String, Handle> OWNERS = new HashMap<>();
    private static boolean requested;
    private static long requestedAt;
    private static boolean aborted;

    private BreakPreparation() {}

    public static synchronized Handle register(String owner)
    {
        Handle handle = new Handle(owner);
        OWNERS.put(owner, handle);
        return handle;
    }

    public static synchronized boolean hasParticipants()
    {
        return !OWNERS.isEmpty();
    }

    public static synchronized boolean shouldDeferBreak()
    {
        if (!requested)
        {
            requestedAt = System.currentTimeMillis();
        }
        requested = true;
        return OWNERS.values().stream().anyMatch(handle -> !handle.ready);
    }

    public static synchronized boolean isAborted()
    {
        return aborted || (requested && System.currentTimeMillis() - requestedAt >= 120_000
                && OWNERS.values().stream().anyMatch(handle -> !handle.ready));
    }

    public static synchronized void finishBreak()
    {
        requested = false;
        aborted = false;
        OWNERS.values().forEach(handle -> handle.ready = false);
    }

    public static final class Handle implements AutoCloseable
    {
        private final String owner;
        private boolean ready;

        private Handle(String owner)
        {
            this.owner = owner;
        }

        public boolean isRequested()
        {
            synchronized (BreakPreparation.class)
            {
                return OWNERS.get(owner) == this && requested;
            }
        }

        public void ready()
        {
            synchronized (BreakPreparation.class)
            {
                if (OWNERS.get(owner) == this && requested)
                {
                    ready = true;
                }
            }
        }

        @Override
        public void close()
        {
            synchronized (BreakPreparation.class)
            {
                if (OWNERS.get(owner) == this && requested && !ready)
                {
                    aborted = true;
                }
                OWNERS.remove(owner, this);
            }
        }
    }
}
