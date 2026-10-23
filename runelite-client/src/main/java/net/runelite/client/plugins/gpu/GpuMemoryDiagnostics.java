package net.runelite.client.plugins.gpu;

import java.util.HashMap;
import java.util.Map;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Temporary opt-in accounting. Requested GL storage is not physical driver memory. */
final class GpuMemoryDiagnostics
{
    static final boolean ENABLED = Boolean.getBoolean("microbot.gpu.memoryDiagnostics");
    private static final Logger LOG = LoggerFactory.getLogger(GpuMemoryDiagnostics.class);
    private static final Ledger LEDGER = new Ledger();
    private static long nextReport;

    enum Kind { BUFFER, TEXTURE, RENDERBUFFER, VERTEX_ARRAY, FRAMEBUFFER }

    static final class Resource
    {
        final Kind kind;
        final int id;
        final long generation;
        String site;
        long owner, bytes;
        boolean mapped;

        Resource(Kind kind, int id, long generation, String site, long owner)
        {
            this.kind = kind; this.id = id; this.generation = generation;
            this.site = site; this.owner = owner;
        }
    }

    static final class Ledger
    {
        static final int MAX_RESOURCES = 16384;
        final Map<Long, Resource> resources = new HashMap<>();
        long generation, scene, frames, nextOwner;
        long creates, deletes, stores, trafficBytes, maps, unmaps, invalidations, anomalies, overflow;

        static long key(Kind kind, int id) { return ((long) kind.ordinal() << 32) | (id & 0xffffffffL); }
        synchronized long owner() { return ++nextOwner; }
        synchronized void create(Kind kind, int id, String site, long owner)
        {
            if (id <= 0) { anomalies++; return; }
            if (resources.size() >= MAX_RESOURCES && !resources.containsKey(key(kind, id))) { overflow++; return; }
            if (resources.put(key(kind, id), new Resource(kind, id, generation, site, owner)) != null) anomalies++;
            creates++;
        }
        synchronized void adopt(Kind kind, int id, String site, long owner)
        {
            Resource r = resources.get(key(kind, id));
            if (r == null) { anomalies++; return; }
            r.site = site; r.owner = owner;
        }
        synchronized void store(Kind kind, int id, long bytes)
        {
            Resource r = resources.get(key(kind, id));
            if (r == null || bytes < 0) { anomalies++; return; }
            r.bytes = bytes; stores++; trafficBytes += bytes;
        }
        synchronized void mapping(int id, boolean mapped, boolean invalidated)
        {
            Resource r = resources.get(key(Kind.BUFFER, id));
            if (r == null) { anomalies++; return; }
            if (r.mapped == mapped) anomalies++;
            r.mapped = mapped;
            if (mapped) maps++; else unmaps++;
            if (invalidated) invalidations++;
        }
        synchronized void delete(Kind kind, int id)
        {
            if (id <= 0) return;
            Resource removed = resources.remove(key(kind, id));
            if (removed == null) { anomalies++; return; }
            if (removed.mapped) anomalies++;
            deletes++;
        }
        synchronized long liveBytes()
        {
            long bytes = 0;
            for (Resource r : resources.values()) bytes += r.bytes;
            return bytes;
        }
        synchronized String summary()
        {
            Map<String, Long> bySite = new HashMap<>();
            long mapped = 0;
            for (Resource r : resources.values())
            {
                bySite.merge(r.site, r.bytes, Long::sum);
                if (r.mapped) mapped++;
            }
            return "generation=" + generation + " scene=" + scene + " frames=" + frames
                + " liveObjects=" + resources.size() + " liveRequestedBytes=" + liveBytes()
                + " creates=" + creates + " deletes=" + deletes + " stores=" + stores
                + " requestedTrafficBytes=" + trafficBytes + " maps=" + maps + " unmaps=" + unmaps
                + " mappedObjects=" + mapped + " invalidations=" + invalidations
                + " anomalies=" + anomalies + " overflow=" + overflow + " bySite=" + bySite;
        }
    }

    @Name("microbot.GpuMemoryOperation")
    @Label("GPU memory operation")
    @Category({"Microbot", "Diagnostics"})
    @StackTrace(false)
    static final class Operation extends Event
    {
        String operation, kind, site;
        int objectId;
        long generation, scene, owner, bytes, callStartNanos, elapsedNanos, wallMillis;
    }

    static long owner() { return ENABLED ? LEDGER.owner() : 0; }
    static long startCall() { return ENABLED ? System.nanoTime() : 0; }

    static void event(String operation, Kind kind, int id, String site, long bytes, long start)
    {
        if (!ENABLED) return;
        Operation e = new Operation();
        if (!e.isEnabled()) return;
        e.operation = operation; e.kind = kind == null ? "context" : kind.name();
        e.objectId = id; e.site = site; e.bytes = bytes;
        synchronized (LEDGER)
        {
            e.generation = LEDGER.generation; e.scene = LEDGER.scene;
            Resource r = kind == null ? null : LEDGER.resources.get(Ledger.key(kind, id));
            e.owner = r == null ? 0 : r.owner;
        }
        e.callStartNanos = start; e.elapsedNanos = start == 0 ? 0 : System.nanoTime() - start;
        e.wallMillis = System.currentTimeMillis(); e.commit();
    }
    static void create(Kind kind, int id, String site, long owner)
    {
        if (!ENABLED) return;
        LEDGER.create(kind, id, site, owner); event("create", kind, id, site, 0, 0);
        GpuResourceAudit.created(kind, id, owner, site);
    }
    static void adopt(Kind kind, int id, String site, long owner)
    {
        if (!ENABLED) return;
        LEDGER.adopt(kind, id, site, owner); event("adopt", kind, id, site, 0, 0);
        GpuResourceAudit.adopt(kind, id, owner);
    }
    static void store(Kind kind, int id, long bytes, String site, long start)
    {
        if (!ENABLED) return;
        LEDGER.store(kind, id, bytes); event("store", kind, id, site, bytes, start);
        GpuResourceAudit.store(kind, id, bytes);
    }
    static void mapping(int id, boolean mapped, boolean invalidated, String site, long start)
    {
        if (!ENABLED) return;
        LEDGER.mapping(id, mapped, invalidated);
        event(mapped ? (invalidated ? "map-invalidate" : "map") : "unmap", Kind.BUFFER, id, site, 0, start);
    }
    static void delete(Kind kind, int id, String site)
    {
        if (!ENABLED) return;
        event("delete-request", kind, id, site, 0, 0); LEDGER.delete(kind, id);
        GpuResourceAudit.deleted(kind, id, site);
    }
    static void contextCreated()
    {
        if (!ENABLED) return;
        synchronized (LEDGER) { LEDGER.generation++; }
        GpuResourceAudit.contextCreated();
        checkpoint("context-created");
    }
    static void contextDestroyed()
    {
        if (!ENABLED) return;
        checkpoint("context-destroyed-before-ledger-clear");
        synchronized (LEDGER) { LEDGER.resources.clear(); }
        GpuResourceAudit.contextDestroyed();
    }
    static void scene(String phase, int worldView)
    {
        if (!ENABLED) return;
        synchronized (LEDGER) { LEDGER.scene++; }
        GpuResourceAudit.scene();
        checkpoint(phase + ":wv=" + worldView);
    }
    static void checkpoint(String phase)
    {
        if (!ENABLED) return;
        LOG.info("gpu_memory | phase={} wallMillis={} monoNanos={} javaThread={} {}",
            phase, System.currentTimeMillis(), System.nanoTime(), Thread.currentThread().getId(), LEDGER.summary());
        event("checkpoint", null, 0, phase, LEDGER.liveBytes(), 0);
    }
    static void frame()
    {
        if (!ENABLED) return;
        if (GpuReplayCapture.ENABLED) GpuResourceAudit.frame();
        synchronized (LEDGER) { LEDGER.frames++; }
        long now = System.nanoTime();
        if (now >= nextReport) { nextReport = now + 5_000_000_000L; checkpoint("periodic"); }
    }
}
