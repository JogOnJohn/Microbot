package net.runelite.client.plugins.gpu;

import org.junit.Test;
import static org.junit.Assert.*;

public class GpuAlphaIndexStorageTest
{
    @Test public void storageIsAllocatedOnceAcrossChangingBatchSizes()
    {
        var storage = new GpuAlphaIndexStorage(19500);
        assertEquals(78000, storage.capacityBytes);
        assertTrue(storage.needsAllocation(3));
        storage.allocated();
        assertFalse(storage.needsAllocation(19500));
        assertFalse(storage.needsAllocation(0));
        assertFalse(storage.needsAllocation(6));
    }

    @Test public void contextRecreationRequiresFreshAllocation()
    {
        var storage = new GpuAlphaIndexStorage(12);
        storage.allocated();
        storage.reset();
        assertTrue(storage.needsAllocation(12));
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversizedUploadIsRejected() { new GpuAlphaIndexStorage(12).needsAllocation(13); }

    @Test(expected = IllegalArgumentException.class)
    public void negativeUploadIsRejected() { new GpuAlphaIndexStorage(12).needsAllocation(-1); }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCapacityIsRejected() { new GpuAlphaIndexStorage(0); }
}
