package net.runelite.client.plugins.gpu;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GpuZoneAllocationTest
{
	@Test public void failedSecondBufferReleasesFirstMappedBuffer()
	{
		Zone zone = mock(Zone.class);
		zone.sizeO = zone.sizeA = 1;
		VBO first = mock(VBO.class);
		AtomicInteger calls = new AtomicInteger();
		RuntimeException failure = new RuntimeException("alpha allocation failed");
		try
		{
			GpuPlugin.allocateZone(zone, size ->
			{
				assertEquals(Zone.VERT_SIZE * 3, size);
				if (calls.incrementAndGet() == 2) throw failure;
				return first;
			});
			fail("Missing failure");
		}
		catch (RuntimeException error) { assertSame(failure, error); }
		verify(zone).free();
		verify(first).destroy();
		verify(zone, never()).init(any(), any());
	}

	@Test public void failedVaoSetupReleasesBothAcquiredBuffers()
	{
		Zone zone = mock(Zone.class);
		zone.sizeO = zone.sizeA = 1;
		VBO first = mock(VBO.class), second = mock(VBO.class);
		RuntimeException failure = new RuntimeException("VAO failed");
		doThrow(failure).when(zone).init(first, second);
		AtomicInteger calls = new AtomicInteger();
		try
		{
			GpuPlugin.allocateZone(zone, size -> calls.incrementAndGet() == 1 ? first : second);
			fail("Missing failure");
		}
		catch (RuntimeException error) { assertSame(failure, error); }
		verify(zone).free();
		verify(first).destroy();
		verify(second).destroy();
	}

	@Test public void failedBatchReleasesNewZonesButPreservesReusedSceneZones()
	{
		Zone previous = mock(Zone.class);
		previous.initialized = true;
		Zone uploaded = mock(Zone.class), failed = mock(Zone.class);
		GpuPlugin.freeUnpublishedZones(new Zone[][]{{previous, uploaded, failed}});
		verify(previous, never()).free();
		verify(uploaded).free();
		verify(failed).free();
	}
}
