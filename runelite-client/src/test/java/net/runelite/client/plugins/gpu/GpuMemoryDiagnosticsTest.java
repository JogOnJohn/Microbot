package net.runelite.client.plugins.gpu;

import org.junit.Test;
import static net.runelite.client.plugins.gpu.GpuMemoryDiagnostics.Kind.*;
import static org.junit.Assert.*;

public class GpuMemoryDiagnosticsTest
{
    @Test public void liveAccountingHasAnExplicitCapacityLimit()
    {
        var ledger = new GpuMemoryDiagnostics.Ledger();
        for (int i = 1; i <= GpuMemoryDiagnostics.Ledger.MAX_RESOURCES + 5; i++)
            ledger.create(BUFFER, i, "capacity-test", i);
        assertEquals(GpuMemoryDiagnostics.Ledger.MAX_RESOURCES, ledger.resources.size());
        assertEquals(5, ledger.overflow);
        assertTrue(ledger.summary().contains("overflow=5"));
    }
    @Test public void storageReplacementCountsTrafficWithoutInventingResidentBytes()
    {
        var ledger = new GpuMemoryDiagnostics.Ledger();
        ledger.create(BUFFER, 1, "pbo", 0);
        ledger.store(BUFFER, 1, 4096);
        ledger.store(BUFFER, 1, 4096);
        assertEquals(4096, ledger.liveBytes());
        assertEquals(8192, ledger.trafficBytes);
        assertEquals(2, ledger.stores);
        ledger.store(BUFFER, 1, 2048);
        assertEquals(2048, ledger.liveBytes());
    }
    @Test public void objectNamespacesAndReusedHandlesRemainDistinct()
    {
        var ledger = new GpuMemoryDiagnostics.Ledger();
        ledger.create(BUFFER, 1, "buffer", 1);
        ledger.create(TEXTURE, 1, "texture", 2);
        ledger.store(BUFFER, 1, 100);
        ledger.store(TEXTURE, 1, 200);
        ledger.delete(BUFFER, 1);
        ledger.generation++;
        ledger.create(BUFFER, 1, "new-buffer", 3);
        ledger.store(BUFFER, 1, 50);
        assertEquals(250, ledger.liveBytes());
        assertEquals(1, ledger.resources.get(GpuMemoryDiagnostics.Ledger.key(BUFFER, 1)).generation);
        assertEquals(0, ledger.anomalies);
    }
    @Test public void mapAndDeleteBalancesExposeMissingUnmap()
    {
        var ledger = new GpuMemoryDiagnostics.Ledger();
        ledger.create(BUFFER, 1, "dynamic", 1);
        ledger.mapping(1, true, true);
        ledger.mapping(1, false, false);
        assertEquals(1, ledger.invalidations);
        assertEquals(ledger.maps, ledger.unmaps);
        ledger.delete(BUFFER, 1);
        assertEquals(0, ledger.anomalies);
        ledger.create(BUFFER, 2, "dynamic", 2);
        ledger.mapping(2, true, false);
        ledger.delete(BUFFER, 2);
        assertEquals(1, ledger.anomalies);
    }
    @Test public void duplicateAndUnknownOperationsAreDiagnosticNotExceptions()
    {
        var ledger = new GpuMemoryDiagnostics.Ledger();
        ledger.create(BUFFER, 0, "invalid", 0);
        ledger.create(BUFFER, 1, "first", 1);
        ledger.create(BUFFER, 1, "duplicate", 2);
        ledger.store(BUFFER, 99, 42);
        ledger.delete(TEXTURE, 1);
        assertEquals(4, ledger.anomalies);
    }
    @Test public void ownershipAndRequestedByteTotalsAreSnapshotVisible()
    {
        var ledger = new GpuMemoryDiagnostics.Ledger();
        ledger.create(BUFFER, 1, "vbo", 1);
        ledger.adopt(BUFFER, 1, "zone-opaque", 2);
        ledger.store(BUFFER, 1, 256);
        assertEquals(2, ledger.resources.get(GpuMemoryDiagnostics.Ledger.key(BUFFER, 1)).owner);
        assertTrue(ledger.summary().contains("zone-opaque=256"));
        ledger.delete(BUFFER, 1);
        assertEquals(0, ledger.liveBytes());
    }
}
