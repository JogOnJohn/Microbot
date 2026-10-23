package net.runelite.client.plugins.gpu;

import com.sun.jna.Platform;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.WGL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.runelite.client.plugins.gpu.GpuMemoryDiagnostics.Kind;
import static org.lwjgl.opengl.GL33C.*;

/** Temporary metadata-only audit. Never retains Java scene objects or mapped buffers. */
final class GpuResourceAudit
{
    private static final Logger LOG = LoggerFactory.getLogger(GpuResourceAudit.class);
    private static final Ledger LEDGER = new Ledger(16384, 256);
    private static long expectedContext;
    private static Object expectedCapabilities;
    private static final long[] checks = new long[Kind.values().length];
    private static int reports;

    static final class Resource
    {
        final Kind kind;
        final int id;
        final long serial, context, generation, scene;
        final String creation;
        final Map<Integer, Resource> references = new HashMap<>();
        long owner, bytes;
        int refCount;
        boolean deleted, probedDeletion;

        Resource(Kind kind, int id, long serial, long context, long generation, long scene, long owner, String creation)
        {
            this.kind = kind; this.id = id; this.serial = serial; this.context = context;
            this.generation = generation; this.scene = scene; this.owner = owner; this.creation = creation;
        }

        String describe()
        {
            return kind + ":" + id + "#" + serial + " ctx=" + context + " gen=" + generation
                + " scene=" + scene + " owner=" + owner + " bytes=" + bytes + " deleted=" + deleted
                + " created=" + creation;
        }
    }

    /** Pure state model, also used by tests without loading an OpenGL context. */
    static final class Ledger
    {
        final Map<Long, Resource> live = new LinkedHashMap<>();
        final Map<Long, Resource> pending = new LinkedHashMap<>();
        final ArrayDeque<String> history = new ArrayDeque<>();
        final int limit, historyLimit;
        long serial, generation, scene, overflow, violations, deletedWithReferences, resolvedReferences;

        Ledger(int limit, int historyLimit) { this.limit = limit; this.historyLimit = historyLimit; }
        Resource get(Kind kind, int id) { return live.get(GpuMemoryDiagnostics.Ledger.key(kind, id)); }

        void create(Kind kind, int id, long context, long owner, String creation)
        {
            if (id <= 0) return;
            if (live.size() + pending.size() >= limit) { overflow++; return; }
            long key = GpuMemoryDiagnostics.Ledger.key(kind, id);
            if (live.containsKey(key)) { violations++; return; }
            live.put(key, new Resource(kind, id, ++serial, context, generation, scene, owner,
                creation.substring(0, Math.min(768, creation.length()))));
        }

        boolean reference(int vao, int slot, int buffer)
        {
            Resource v = get(Kind.VERTEX_ARRAY, vao);
            Resource b = get(Kind.BUFFER, buffer);
            if (v == null || slot < 0 || slot > 16 || (buffer != 0 && b == null))
            {
                violations++; return false;
            }
            Resource old = buffer == 0 ? v.references.remove(slot) : v.references.put(slot, b);
            if (old != null) releaseReference(old);
            if (b != null) b.refCount++;
            return true;
        }

        int references(Resource buffer)
        {
            return buffer.refCount;
        }

        Resource delete(Kind kind, int id)
        {
            if (id <= 0) return null;
            Resource r = live.remove(GpuMemoryDiagnostics.Ledger.key(kind, id));
            if (r == null) { violations++; return null; }
            r.deleted = true;
            for (Resource buffer : r.references.values()) releaseReference(buffer);
            r.references.clear();
            if (kind == Kind.BUFFER && references(r) > 0)
            {
                pending.put(r.serial, r); deletedWithReferences++;
            }
            remember(r.describe());
            return r;
        }

        private void releaseReference(Resource r)
        {
            r.refCount--;
            if (r.deleted && r.refCount == 0 && pending.remove(r.serial) != null) resolvedReferences++;
        }

        int owned(long owner)
        {
            if (owner == 0) return 0;
            int count = 0;
            for (Resource r : live.values()) if (r.owner == owner) count++;
            for (Resource r : pending.values()) if (r.owner == owner) count++;
            return count;
        }

        void remember(String value)
        {
            if (historyLimit <= 0) return;
            if (history.size() == historyLimit) history.removeFirst();
            history.addLast(value);
        }

        void clearContext()
        {
            live.clear(); pending.clear(); history.clear(); generation++;
        }

        String summary()
        {
            long pendingBytes = 0;
            for (Resource r : pending.values()) pendingBytes += r.bytes;
            return "live=" + live.size() + " pendingDeleted=" + pending.size() + " pendingBytes=" + pendingBytes
                + " deletedWithRefs=" + deletedWithReferences + " resolved=" + resolvedReferences
                + " violations=" + violations + " overflow=" + overflow + " history=" + history.size();
        }
    }

    @Name("microbot.GpuResourceLifecycle")
    @Label("GPU resource ownership")
    @Category({"Microbot", "Diagnostics"})
    @StackTrace(true)
    static final class Lifecycle extends Event
    {
        String operation, site, detail, resourceKind;
        int objectId;
        long serial, generation, scene, birthScene, owner, context, bytes, monoNanos, wallMillis;
    }

    private static boolean enabled() { return GpuMemoryDiagnostics.ENABLED; }

    private static long currentContext()
    {
        // Bizza is Windows; capabilities identity is a weaker fallback on other platforms.
        return Platform.isWindows() ? WGL.wglGetCurrentContext() : System.identityHashCode(GL.getCapabilities());
    }

    private static boolean context(String site)
    {
        try
        {
            long actual = currentContext();
            if (sameContext(actual, GL.getCapabilities(), expectedContext, expectedCapabilities)) return true;
            violation("wrong-context", site, null, "expected=" + expectedContext + " actual=" + actual);
        }
        catch (IllegalStateException ex)
        {
            violation("missing-context", site, null, ex.toString());
        }
        return false;
    }

    static boolean sameContext(long actual, Object capabilities, long expected, Object expectedCapabilities)
    {
        return actual != 0 && actual == expected && capabilities != null && capabilities == expectedCapabilities;
    }

    static synchronized void contextCreated()
    {
        if (!enabled()) return;
        if (!LEDGER.live.isEmpty() || !LEDGER.pending.isEmpty())
            violation("context-replaced-with-records", "context-created", null, LEDGER.summary());
        LEDGER.clearContext(); reports = 0;
        java.util.Arrays.fill(checks, 0);
        expectedCapabilities = GL.getCapabilities(); expectedContext = currentContext();
        emit("context-created", "context-created", null, "nativeHandle=" + Platform.isWindows());
    }

    static synchronized void created(Kind kind, int id, long owner, String site)
    {
        if (!enabled() || (kind != Kind.BUFFER && kind != Kind.VERTEX_ARRAY)) return;
        if (!context(site)) return;
        Resource previous = LEDGER.get(kind, id);
        if (previous != null)
        {
            violation("duplicate-live-name", site, previous, "newOwner=" + owner);
            return;
        }
        long overflow = LEDGER.overflow;
        LEDGER.create(kind, id, expectedContext, owner, trace());
        if (LEDGER.overflow != overflow)
            violation("tracking-capacity-exceeded", site, null, "coverage-incomplete " + LEDGER.summary());
        Resource r = LEDGER.get(kind, id);
        emit("create", site, r, "");
    }

    static synchronized void adopt(Kind kind, int id, long owner)
    {
        if (!enabled()) return;
        Resource r = LEDGER.get(kind, id);
        if (r != null) { r.owner = owner; emit("adopt", "zone-adopt", r, ""); }
    }

    static synchronized void store(Kind kind, int id, long bytes)
    {
        if (!enabled()) return;
        Resource r = LEDGER.get(kind, id);
        if (r != null) { r.bytes = bytes; capture("store", r, null, -1, 0, "store"); }
    }

    static synchronized void mapping(int id, boolean mapped, int flags)
    {
        if (!GpuReplayCapture.ENABLED) return;
        capture(mapped ? "map" : "unmap", LEDGER.get(Kind.BUFFER, id), null, -1, flags, "vbo-map");
    }

    static synchronized void draw(int vao)
    {
        if (!GpuReplayCapture.ENABLED) return;
        capture("draw", LEDGER.get(Kind.VERTEX_ARRAY, vao), null, -1, 0, "synthetic-draw-boundary");
    }

    static synchronized void frame()
    {
        if (GpuReplayCapture.ENABLED) capture("frame", null, null, -1, 0, "frame");
    }

    static synchronized void attribute(int slot, int expectedBuffer, String site)
    {
        if (!enabled() || !context(site)) return;
        int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int bound = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int actual = glGetVertexAttribi(slot, GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
        if (bound != expectedBuffer || actual != expectedBuffer)
            violation("attribute-binding-mismatch", site, LEDGER.get(Kind.VERTEX_ARRAY, vao),
                "slot=" + slot + " expected=" + expectedBuffer + " bound=" + bound + " actual=" + actual);
        LEDGER.reference(vao, slot, actual);
        Resource buffer = LEDGER.get(Kind.BUFFER, actual);
        capture("attribute", LEDGER.get(Kind.VERTEX_ARRAY, vao), buffer, slot, 0, site);
        emit("attribute-capture", site, LEDGER.get(Kind.VERTEX_ARRAY, vao), "slot=" + slot
            + " buffer=" + actual + " bufferSerial=" + (buffer == null ? 0 : buffer.serial));
    }

    static synchronized void element(int vao, int buffer)
    {
        if (!enabled()) return;
        // Observe the actual bind/unbind call; query driver state only at teardown.
        LEDGER.reference(vao, 16, buffer);
    }

    static synchronized void beforeDelete(Kind kind, int id, String site)
    {
        if (!enabled() || id <= 0 || !context(site)) return;
        // Query only lifecycle boundaries: first eight deletions, then one in 64.
        long count = ++checks[kind.ordinal()];
        if (count > 8 && count % 64 != 0) return;
        Resource r = LEDGER.get(kind, id);
        if (r != null) r.probedDeletion = true;
        boolean exists = kind == Kind.BUFFER ? glIsBuffer(id) : glIsVertexArray(id);
        // glGenBuffers reserves a name; glIsBuffer may be false until its first bind.
        if (!exists && r != null && r.bytes > 0)
            violation("missing-before-delete", site, r, "id=" + id);
        int boundVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        emit("pre-delete-probe", site, r, "exists=" + exists + " boundVao=" + boundVao
            + " boundArray=" + glGetInteger(GL_ARRAY_BUFFER_BINDING));
        if (kind != Kind.VERTEX_ARRAY || !exists || r == null) return;
        // GL 3.3 has no DSA query. Restore the exact VAO, including its element binding.
        glBindVertexArray(id);
        try
        {
            for (int slot = 0; slot < 3; slot++)
                verifyReference(r, slot, glGetVertexAttribi(slot, GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING), site);
            verifyReference(r, 16, glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING), site);
        }
        finally { glBindVertexArray(boundVao); }
    }

    private static void verifyReference(Resource vao, int slot, int actual, String site)
    {
        Resource buffer = vao.references.get(slot);
        if (actual != (buffer == null ? 0 : buffer.id))
            violation("teardown-reference-mismatch", site, vao, "slot=" + slot + " actual=" + actual
                + " expected=" + (buffer == null ? "none" : buffer.describe()));
    }

    static synchronized void deleted(Kind kind, int id, String site)
    {
        if (!enabled() || (kind != Kind.BUFFER && kind != Kind.VERTEX_ARRAY)) return;
        if (!context(site)) return;
        if (kind == Kind.BUFFER)
        {
            // Deleting a buffer detaches it from the currently bound VAO, not all VAOs.
            int bound = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            Resource vao = LEDGER.get(Kind.VERTEX_ARRAY, bound);
            Resource buffer = LEDGER.get(kind, id);
            if (vao != null && buffer != null)
            {
                for (int slot = 0; slot <= 16; slot++)
                {
                    if (vao.references.get(slot) != buffer) continue;
                    int actual = slot == 16 ? glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING)
                        : glGetVertexAttribi(slot, GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
                    if (actual != id) LEDGER.reference(bound, slot, actual);
                }
            }
        }
        Resource r = LEDGER.delete(kind, id);
        emit("delete-returned", site, r, r == null ? "untracked" : "attributeRefs=" + LEDGER.references(r));
        if (r != null && r.probedDeletion)
            emit("post-delete-probe", site, r, "nameExists="
                + (kind == Kind.BUFFER ? glIsBuffer(id) : glIsVertexArray(id)));
    }

    static synchronized void ownerReleased(long owner, String site)
    {
        if (!enabled()) return;
        int remaining = LEDGER.owned(owner);
        if (remaining != 0)
        {
            boolean detailed = reports < 32;
            violation("owner-release-with-resources", site, null, "owner=" + owner + " remaining=" + remaining);
            if (!detailed) return;
            int emitted = 0;
            for (Resource r : LEDGER.live.values())
                if (r.owner == owner && emitted++ < 16) emit("owner-survivor", site, r, "live");
            for (Resource r : LEDGER.pending.values())
                if (r.owner == owner && emitted++ < 16) emit("owner-survivor", site, r, "deleted-but-referenced");
        }
    }

    static synchronized void checkpoint(String site, boolean finalContext)
    {
        if (!enabled()) return;
        if (!context(site)) return;
        for (int i = 0; i < 8; i++)
        {
            int error = glGetError();
            if (error == GL_NO_ERROR) break;
            glError(error);
        }
        if (finalContext && (!LEDGER.live.isEmpty() || !LEDGER.pending.isEmpty()))
            violation("context-teardown-with-resources", site, null, LEDGER.summary());
        LOG.info("gpu_ownership | phase={} monoNanos={} wallMillis={} context={} {}", site,
            System.nanoTime(), System.currentTimeMillis(), expectedContext, LEDGER.summary());
        emit("checkpoint", site, null, LEDGER.summary());
    }

    static synchronized void contextDestroyed()
    {
        if (!enabled()) return;
        emit("context-destroyed", "context-destroyed", null, LEDGER.summary());
        LEDGER.clearContext(); expectedCapabilities = null; expectedContext = 0;
    }

    static synchronized void glError(int error)
    {
        if (enabled()) violation("gl-error", "existing-checkGLErrors", null, "error=" + error);
    }

    private static String trace()
    {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder text = new StringBuilder();
        for (int i = 3; i < stack.length && i < 9; i++) text.append(stack[i]).append(';');
        return text.substring(0, Math.min(768, text.length()));
    }

    private static void violation(String reason, String site, Resource resource, String detail)
    {
        LEDGER.violations++;
        if (reports++ < 32)
        {
            LOG.warn("gpu_ownership_violation | reason={} site={} resource={} detail={} teardown={}",
                reason, site, resource == null ? "none" : resource.describe(), detail, trace());
            emit(reason, site, resource, detail);
        }
    }

    private static void emit(String operation, String site, Resource resource, String detail)
    {
        if (operation.equals("create") || operation.equals("delete-returned")
            || operation.equals("context-created") || operation.equals("context-destroyed")
            || operation.equals("checkpoint"))
            capture(operation, resource, null, -1, 0, site);
        Lifecycle e = new Lifecycle();
        if (!e.isEnabled()) return;
        e.operation = operation; e.site = site; e.detail = detail;
        e.resourceKind = resource == null ? "context" : resource.kind.name();
        e.generation = LEDGER.generation; e.scene = LEDGER.scene; e.context = expectedContext;
        if (resource != null)
        {
            e.objectId = resource.id; e.serial = resource.serial; e.owner = resource.owner;
            e.birthScene = resource.scene;
            e.bytes = resource.bytes; e.context = resource.context;
        }
        e.monoNanos = System.nanoTime(); e.wallMillis = System.currentTimeMillis(); e.commit();
    }

    private static void capture(String operation, Resource resource, Resource peer, int slot, int flags, String site)
    {
        if (!GpuReplayCapture.ENABLED) return;
        GpuReplayCapture.record(operation, resource == null ? "context" : resource.kind.name(), site,
            resource == null ? 0 : resource.serial, peer == null ? 0 : peer.serial,
            resource == null ? 0 : resource.bytes, currentContext(), slot, flags);
    }

    static synchronized void scene() { if (enabled()) LEDGER.scene++; }
    private GpuResourceAudit() {}
}
