package net.runelite.client.plugins.gpu;

/** Opt-in storage-reuse experiment; no native objects or asynchronous work are owned here. */
final class GpuAlphaIndexStorage
{
    static final boolean REUSE = Boolean.getBoolean("microbot.gpu.reuseAlphaIndexBuffer");
    final long capacityBytes;
    private boolean allocated;

    GpuAlphaIndexStorage(int capacityInts)
    {
        if (capacityInts <= 0) throw new IllegalArgumentException("Invalid alpha index capacity");
        capacityBytes = (long) capacityInts * Integer.BYTES;
    }

    boolean needsAllocation(int count)
    {
        if (count < 0 || (long) count * Integer.BYTES > capacityBytes)
            throw new IllegalArgumentException("Alpha index upload exceeds capacity");
        return !allocated;
    }

    void allocated() { allocated = true; }
    void reset() { allocated = false; }
}
