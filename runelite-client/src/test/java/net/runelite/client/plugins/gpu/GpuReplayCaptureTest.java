package net.runelite.client.plugins.gpu;

import org.junit.Test;
import static org.junit.Assert.*;

public class GpuReplayCaptureTest
{
    @Test public void onlyAContextStartCanBeginACompleteCapture()
    {
        var window = new GpuReplayCapture.Window(5, 100);
        assertEquals("INCOMPLETE", window.accept("create", 10));
        assertNull(window.accept("context-created", 11));
    }
    @Test public void deadlineProducesOneEndWithoutChangingGpuWork()
    {
        var window = new GpuReplayCapture.Window(5, 100);
        assertEquals("BEGIN", window.accept("context-created", 10));
        assertEquals("create", window.accept("create", 109));
        assertEquals("END", window.accept("draw", 110));
        assertNull(window.accept("draw", 111));
    }
    @Test public void overflowAndContextReplacementInvalidateCapture()
    {
        var limit = new GpuReplayCapture.Window(5, 100);
        limit.accept("context-created", 10); limit.sequence = 5;
        assertEquals("INCOMPLETE", limit.accept("draw", 11));
        var context = new GpuReplayCapture.Window(5, 100);
        context.accept("context-created", 10);
        assertEquals("INCOMPLETE", context.accept("context-created", 11));
    }
}
