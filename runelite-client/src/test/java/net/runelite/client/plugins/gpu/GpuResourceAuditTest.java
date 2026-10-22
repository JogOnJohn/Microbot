package net.runelite.client.plugins.gpu;

import org.junit.Test;
import static net.runelite.client.plugins.gpu.GpuMemoryDiagnostics.Kind.*;
import static org.junit.Assert.*;

public class GpuResourceAuditTest
{
    private GpuResourceAudit.Ledger ledger() { return new GpuResourceAudit.Ledger(32, 4); }

    @Test public void vaoFirstDeletionReleasesReferencesWithoutPendingBuffers()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 7, "buffer");
        l.create(VERTEX_ARRAY, 2, 10, 7, "opaque");
        l.create(VERTEX_ARRAY, 3, 10, 7, "alpha");
        l.reference(2, 0, 1); l.reference(2, 1, 1);
        l.reference(3, 0, 1);
        var buffer = l.get(BUFFER, 1);
        l.delete(VERTEX_ARRAY, 2);
        assertEquals(1, l.references(buffer));
        l.delete(VERTEX_ARRAY, 3);
        assertEquals(0, l.references(buffer));
        assertTrue(l.pending.isEmpty());
        l.delete(BUFFER, 1);
        assertTrue(l.pending.isEmpty());
        assertEquals(0, l.resolvedReferences);
        assertEquals(0, l.owned(7));
        assertEquals(0, l.violations);
    }

    @Test public void bufferDeletionIsPendingUntilAllVaoReferencesAreReleased()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 7, "buffer-create");
        l.create(VERTEX_ARRAY, 2, 10, 7, "vao-create");
        l.reference(2, 0, 1); l.reference(2, 1, 1); l.reference(2, 2, 1);
        var buffer = l.get(BUFFER, 1); buffer.bytes = 4096;
        l.delete(BUFFER, 1);
        assertEquals(1, l.pending.size());
        assertEquals(3, l.references(buffer));
        assertEquals(2, l.owned(7));
        assertTrue(l.summary().contains("pendingBytes=4096"));
        l.delete(VERTEX_ARRAY, 2);
        assertEquals(0, l.pending.size());
        assertEquals(0, l.owned(7));
        assertEquals(1, l.resolvedReferences);
        assertEquals(0, l.violations);
    }

    @Test public void reusedBufferIdDoesNotRetargetAnOldVaoReference()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 7, "old");
        l.create(VERTEX_ARRAY, 2, 10, 7, "vao");
        l.reference(2, 0, 1);
        var old = l.get(BUFFER, 1);
        l.delete(BUFFER, 1);
        l.create(BUFFER, 1, 10, 8, "reused");
        var replacement = l.get(BUFFER, 1);
        assertNotEquals(old.serial, replacement.serial);
        assertSame(old, l.get(VERTEX_ARRAY, 2).references.get(0));
        l.delete(VERTEX_ARRAY, 2);
        assertTrue(l.pending.isEmpty());
        assertSame(replacement, l.get(BUFFER, 1));
        assertEquals(0, replacement.refCount);
    }

    @Test public void sharedBufferSurvivesUntilTheLastVaoIsDeleted()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 9, "shared");
        l.create(VERTEX_ARRAY, 2, 10, 7, "a");
        l.create(VERTEX_ARRAY, 3, 10, 8, "b");
        l.reference(2, 0, 1); l.reference(3, 0, 1);
        l.delete(BUFFER, 1); l.delete(VERTEX_ARRAY, 2);
        assertEquals(1, l.pending.size());
        l.delete(VERTEX_ARRAY, 3);
        assertTrue(l.pending.isEmpty());
    }

    @Test public void detachingOrReplacingAnAttributeReleasesItsReference()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 7, "old");
        l.create(BUFFER, 3, 10, 7, "new");
        l.create(VERTEX_ARRAY, 2, 10, 7, "vao");
        l.reference(2, 0, 1); l.reference(2, 1, 1);
        l.delete(BUFFER, 1);
        l.reference(2, 0, 3);
        assertEquals(1, l.pending.size());
        l.reference(2, 1, 0);
        assertTrue(l.pending.isEmpty());
        assertEquals(1, l.get(BUFFER, 3).refCount);
        l.reference(2, 0, 3);
        assertEquals(1, l.get(BUFFER, 3).refCount);
    }

    @Test public void elementBindingIsTrackedAndItsUnbindDoesNotAccumulateHistory()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 7, "element");
        l.create(VERTEX_ARRAY, 2, 10, 7, "vao");
        for (int i = 0; i < 10000; i++)
        {
            l.reference(2, 16, 1); l.reference(2, 16, 0);
        }
        assertTrue(l.history.isEmpty());
        assertEquals(0, l.get(BUFFER, 1).refCount);
        assertTrue(l.get(VERTEX_ARRAY, 2).references.isEmpty());
    }

    @Test public void buffersAndVaosHaveSeparateNamespaces()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 7, "buffer");
        l.create(VERTEX_ARRAY, 1, 10, 7, "vao");
        l.reference(1, 0, 1);
        l.delete(VERTEX_ARRAY, 1);
        assertNotNull(l.get(BUFFER, 1));
        assertEquals(0, l.get(BUFFER, 1).refCount);
    }

    @Test public void capacityIncludesDeletedButReferencedResources()
    {
        var l = new GpuResourceAudit.Ledger(2, 1);
        l.create(BUFFER, 1, 10, 7, "buffer");
        l.create(VERTEX_ARRAY, 2, 10, 7, "vao");
        l.reference(2, 0, 1); l.delete(BUFFER, 1);
        l.create(BUFFER, 3, 10, 7, "rejected");
        assertNull(l.get(BUFFER, 3));
        assertEquals(1, l.overflow);
        l.delete(VERTEX_ARRAY, 2);
        l.create(BUFFER, 3, 10, 7, "accepted");
        assertNotNull(l.get(BUFFER, 3));
    }

    @Test public void historyAndCreationTraceAreBounded()
    {
        var l = ledger();
        for (int i = 1; i <= 50; i++)
        {
            l.create(BUFFER, i, 10, 7, "x".repeat(2000));
            assertEquals(768, l.get(BUFFER, i).creation.length());
            l.delete(BUFFER, i);
        }
        assertEquals(4, l.history.size());
        assertTrue(l.live.isEmpty()); assertTrue(l.pending.isEmpty());
    }

    @Test public void contextResetReleasesMetadataAndDoesNotReuseSerials()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 7, "old");
        long serial = l.get(BUFFER, 1).serial;
        l.clearContext();
        l.create(BUFFER, 1, 20, 8, "new");
        assertEquals(1, l.generation);
        assertEquals(20, l.get(BUFFER, 1).context);
        assertTrue(l.get(BUFFER, 1).serial > serial);
        assertTrue(l.history.isEmpty());
    }

    @Test public void invalidReferenceAndDuplicateCreateAreNotSilent()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 7, "first");
        l.create(BUFFER, 1, 10, 7, "duplicate");
        assertFalse(l.reference(99, 0, 1));
        assertEquals(2, l.violations);
        assertEquals("first", l.get(BUFFER, 1).creation);
    }

    @Test public void missingNativeContextOrStaleCapabilitiesCannotPassContextCheck()
    {
        Object caps = new Object();
        assertTrue(GpuResourceAudit.sameContext(10, caps, 10, caps));
        assertFalse(GpuResourceAudit.sameContext(0, caps, 0, caps));
        assertFalse(GpuResourceAudit.sameContext(11, caps, 10, caps));
        assertFalse(GpuResourceAudit.sameContext(10, new Object(), 10, caps));
        assertFalse(GpuResourceAudit.sameContext(10, null, 10, null));
    }

    @Test public void referenceSlotsCannotGrowWithoutBound()
    {
        var l = ledger();
        l.create(BUFFER, 1, 10, 7, "buffer");
        l.create(VERTEX_ARRAY, 2, 10, 7, "vao");
        assertFalse(l.reference(2, -1, 1));
        assertFalse(l.reference(2, 17, 1));
        assertTrue(l.get(VERTEX_ARRAY, 2).references.isEmpty());
    }
}
