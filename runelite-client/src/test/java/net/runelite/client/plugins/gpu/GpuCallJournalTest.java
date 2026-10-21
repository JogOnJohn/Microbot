package net.runelite.client.plugins.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuCallJournalTest
{
    private ByteBuffer data(int capacity)
    {
        return ByteBuffer.allocate(GpuCallJournal.HEADER + capacity * GpuCallJournal.RECORD)
            .order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test public void entryExistsBeforeCompletion()
    {
        ByteBuffer data = data(2);
        GpuCallJournal.Ring ring = new GpuCallJournal.Ring(data, 2);
        long token = ring.begin(GpuCallJournal.Site.ALPHA_DRAW, 42, 300, 1234);
        int at = GpuCallJournal.HEADER;
        assertEquals(token, data.getLong(at));
        assertTrue(data.getLong(at + 8) > 0);
        assertEquals(0, data.getLong(at + 16));
        assertEquals(1234, data.getLong(at + 40));
        assertEquals(42, data.getInt(at + 52));
        assertEquals(300, data.getLong(at + 56));
        assertEquals(0, data.getLong(at + 72));
        ring.end(token, true);
        assertTrue(data.getLong(at + 16) >= data.getLong(at + 8));
        assertEquals(1, data.getInt(at + 64));
        assertEquals(token, data.getLong(at + 72));
    }

    @Test public void javaExceptionIsNotMislabelledAsNativeCrash()
    {
        ByteBuffer data = data(1);
        GpuCallJournal.Ring ring = new GpuCallJournal.Ring(data, 1);
        long token = ring.begin(GpuCallJournal.Site.VBO_MAP, 1, 128, 3);
        ring.end(token, false);
        assertEquals(2, data.getInt(GpuCallJournal.HEADER + 64));
        assertEquals(token, data.getLong(GpuCallJournal.HEADER + 72));
    }

    @Test public void lateCompletionCannotCorruptAnOverwrittenSlot()
    {
        ByteBuffer data = data(1);
        GpuCallJournal.Ring ring = new GpuCallJournal.Ring(data, 1);
        long old = ring.begin(GpuCallJournal.Site.VBO_MAP, 1, 32, 3);
        long current = ring.begin(GpuCallJournal.Site.VBO_STORE, 2, 64, 3);
        ring.end(old, true);
        assertEquals(current, data.getLong(GpuCallJournal.HEADER));
        assertEquals(0, data.getLong(GpuCallJournal.HEADER + 72));
        assertEquals(2, data.getInt(GpuCallJournal.HEADER + 52));
    }

    @Test public void ringIsFixedSizeAndHeaderTracksWraps()
    {
        ByteBuffer data = data(2);
        GpuCallJournal.Ring ring = new GpuCallJournal.Ring(data, 2);
        for (int i = 0; i < 100; i++)
        {
            long token = ring.begin(GpuCallJournal.Site.SWAP, 0, 0, 3);
            ring.end(token, true);
        }
        assertEquals(100, data.getLong(8));
        assertEquals(2, data.getInt(20));
        assertEquals(99, data.getLong(GpuCallJournal.HEADER));
        assertEquals(100, data.getLong(GpuCallJournal.HEADER + GpuCallJournal.RECORD));
    }

    @Test public void mappedEntryIsReadableWithoutCleanJournalShutdown() throws Exception
    {
        Path file = Files.createTempFile("gpu-call-journal-test", ".bin");
        // Keep the mapping bounded and delete on JVM exit (Windows retains active mappings).
        file.toFile().deleteOnExit();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE))
        {
            GpuCallJournal.Ring ring = new GpuCallJournal.Ring(channel.map(FileChannel.MapMode.READ_WRITE,
                0, GpuCallJournal.HEADER + GpuCallJournal.RECORD), 1);
            ring.begin(GpuCallJournal.Site.PBO_STORE, 7, 4096, 3);
        }
        ByteBuffer copy = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, copy.getLong(GpuCallJournal.HEADER));
        assertEquals(0, copy.getLong(GpuCallJournal.HEADER + 72));
        assertEquals(4096, copy.getLong(GpuCallJournal.HEADER + 56));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesUndersizedStorage()
    {
        new GpuCallJournal.Ring(ByteBuffer.allocate(8), 2);
    }
}
