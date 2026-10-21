package net.runelite.client.plugins.gpu;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Temporary bounded breadcrumbs; survives a process crash, not a machine/power failure. */
final class GpuCallJournal
{
    static final int HEADER = 4096;
    static final int RECORD = 80;
    static final int CAPACITY = 524288;
    private static final Logger LOG = LoggerFactory.getLogger(GpuCallJournal.class);
    private static final ThreadLocal<Integer> NATIVE_THREAD = ThreadLocal.withInitial(
        () -> Platform.isWindows() ? Kernel32.INSTANCE.GetCurrentThreadId() : 0);
    private static volatile boolean failed;

    enum Site
    {
        VBO_STORE, VBO_MAP, VBO_FLUSH, VBO_UNMAP, VBO_DELETE,
        ALPHA_STORE, ALPHA_DRAW, OPAQUE_DRAW, DYNAMIC_ALPHA_DRAW, STATIC_ALPHA_DRAW,
        PBO_STORE, PBO_MAP, PBO_UNMAP, UI_TEXTURE_UPLOAD, UI_DRAW, SWAP,
        UNIFORM_STORE, AA_COLOR_STORE, AA_DEPTH_STORE, UI_TEXTURE_STORE, BLIT,
        TEXTURE_ARRAY_STORE, TEXTURE_ARRAY_IMAGE, TEXTURE_UPLOAD, TEXTURE_MIPMAP,
        UI_VBO_STORE
    }

    static final class Ring
    {
        private final ByteBuffer data;
        private final int capacity;
        private long sequence;

        Ring(ByteBuffer data, int capacity)
        {
            if (capacity <= 0 || data.capacity() < HEADER + capacity * RECORD)
            {
                throw new IllegalArgumentException("Invalid GPU journal capacity");
            }
            this.data = data.order(ByteOrder.LITTLE_ENDIAN);
            this.capacity = capacity;
            data.putInt(0, 0x4750554a);
            data.putInt(4, 1);
            data.putInt(16, RECORD);
            data.putInt(20, capacity);
        }

        private int offset(long token)
        {
            return HEADER + (int) ((token - 1) % capacity) * RECORD;
        }

        synchronized long begin(Site site, int object, long amount, int nativeThread)
        {
            long token = ++sequence;
            int at = offset(token);
            data.putLong(at, 0); // Invalidate the old slot before publishing its replacement.
            data.putLong(at + 8, System.nanoTime());
            data.putLong(at + 16, 0);
            data.putLong(at + 24, System.currentTimeMillis());
            data.putLong(at + 32, Thread.currentThread().getId());
            data.putLong(at + 40, nativeThread & 0xffffffffL);
            data.putInt(at + 48, site.ordinal());
            data.putInt(at + 52, object);
            data.putLong(at + 56, amount);
            data.putInt(at + 64, 0); // 0=in flight, 1=returned, 2=Java exception.
            data.putLong(at + 72, 0);
            data.putLong(at, token);
            data.putLong(8, token);
            return token;
        }

        synchronized void end(long token, boolean returned)
        {
            if (token <= 0) return;
            int at = offset(token);
            if (data.getLong(at) != token) return; // A long-running call may outlive a wrap.
            data.putLong(at + 16, System.nanoTime());
            data.putInt(at + 64, returned ? 1 : 2);
            data.putLong(at + 72, token); // Completion marker written last.
        }
    }

    private static final class Holder
    {
        static final Ring RING = open();
    }

    private static Ring open()
    {
        String fallback = Paths.get(System.getProperty("user.home"), ".runelite",
            "gpu-calls-" + ProcessHandle.current().pid() + ".bin").toString();
        Path path = Paths.get(System.getProperty("microbot.gpu.callJournal", fallback)).toAbsolutePath();
        try
        {
            Files.createDirectories(path.getParent());
            // CREATE_NEW prevents accidentally overwriting an earlier crash's evidence.
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ, StandardOpenOption.WRITE))
            {
                Ring ring = new Ring(channel.map(FileChannel.MapMode.READ_WRITE, 0,
                    HEADER + (long) CAPACITY * RECORD), CAPACITY);
                LOG.info("gpu_call_journal | path={} bytes={} recordBytes={} sites={}",
                    path, HEADER + (long) CAPACITY * RECORD, RECORD, java.util.Arrays.toString(Site.values()));
                return ring;
            }
        }
        catch (IOException | RuntimeException ex)
        {
            LOG.warn("GPU call journal unavailable; rendering unchanged", ex);
            return null;
        }
    }

    static long begin(Site site, int object, long amount)
    {
        if (!GpuMemoryDiagnostics.ENABLED || failed) return 0;
        try
        {
            Ring ring = Holder.RING;
            return ring == null ? 0 : ring.begin(site, object, amount, NATIVE_THREAD.get());
        }
        catch (RuntimeException | LinkageError ex)
        {
            failed = true;
            LOG.warn("GPU call journal disabled after diagnostic failure", ex);
            return 0;
        }
    }

    static void end(long token, boolean returned)
    {
        if (token == 0 || failed) return;
        try
        {
            Holder.RING.end(token, returned);
        }
        catch (RuntimeException ex)
        {
            failed = true;
            LOG.warn("GPU call journal disabled after diagnostic failure", ex);
        }
    }
}
