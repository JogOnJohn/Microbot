package net.runelite.client.plugins.gpu;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.lwjgl.opengl.GL33C.*;

public class StaticVboPoolTest
{
	private static final int Q = StaticVboPool.QUANTUM;

	private static void assertThrows(Class<? extends RuntimeException> type, Runnable action)
	{
		try
		{
			action.run();
		}
		catch (RuntimeException error)
		{
			assertTrue(error.toString(), type.isInstance(error));
			return;
		}
		fail("Expected " + type.getSimpleName());
	}

	private static final class Fake implements StaticVboPool.Backend
	{
		int nextId, nextFence, creates, polls;
		boolean wrongContext, failFence;
		final Set<Integer> deleted = new HashSet<>();
		final Set<Long> deletedFences = new HashSet<>();
		final Map<Long, Integer> status = new HashMap<>();

		public void checkContext()
		{
			if (wrongContext) throw new IllegalStateException("Wrong context");
		}
		public int create(int capacity) { creates++; return ++nextId; }
		public void delete(int id, int capacity) { assertTrue("double delete", deleted.add(id)); }
		public long fence()
		{
			if (failFence) return 0;
			long fence = ++nextFence;
			status.put(fence, GL_TIMEOUT_EXPIRED);
			return fence;
		}
		public int poll(long fence) { polls++; return status.get(fence); }
		public void deleteFence(long fence) { assertTrue("double sync delete", deletedFences.add(fence)); }
		public void cached(int id) { }
		void signalAll() { status.replaceAll((fence, state) -> GL_ALREADY_SIGNALED); }
	}

	@Test public void reuseRequiresSignaledFenceAndDoesNotAllocateAgain()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 8L * Q, 8);
		var first = pool.acquire(100);
		pool.release(first);
		var pendingMiss = pool.acquire(100);
		assertNotEquals(first.id, pendingMiss.id);
		assertEquals(1, pool.pendingPolls);
		gl.signalAll();
		assertSame(first, pool.acquire(100));
		assertEquals(2, gl.creates);
		assertEquals(1, pool.hits);
		assertEquals(0, pool.cachedBytes);
		assertEquals(1, gl.deletedFences.size());
	}

	@Test public void readinessPollingIsBounded()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 32L * Q, 32);
		StaticVboPool.Storage[] stores = new StaticVboPool.Storage[20];
		for (int i = 0; i < stores.length; i++) stores[i] = pool.acquire(100);
		for (var store : stores) pool.release(store);
		pool.acquire(100);
		assertEquals(StaticVboPool.MAX_POLLS, gl.polls);
		assertEquals(21, gl.creates);
	}

	@Test public void bestFitIsSufficientButNeverExcessivelyOversized()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 16L * Q, 16);
		var larger = pool.acquire(Q + 1);
		var huge = pool.acquire(5 * Q);
		pool.release(larger);
		pool.release(huge);
		gl.signalAll();
		assertSame(larger, pool.acquire(Q));
		var small = pool.acquire(10);
		assertEquals(Q, small.capacity);
		assertNotEquals(huge.id, small.id);
		assertEquals(Q * 5L, pool.cachedBytes);
	}

	@Test public void byteAndCountCapsIncludeUnsignaledStorageAndEvictOldest()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 3L * Q, 2);
		var first = pool.acquire(Q);
		var second = pool.acquire(Q);
		var third = pool.acquire(2 * Q);
		pool.release(first);
		pool.release(second);
		pool.release(third);
		assertTrue(gl.deleted.contains(first.id));
		assertEquals(3L * Q, pool.cachedBytes);
		assertEquals(1, pool.evictions);
		assertEquals(1, gl.deletedFences.size());
		pool.close();
		assertEquals(3, gl.deleted.size());
		assertEquals(3, gl.deletedFences.size());
		assertEquals(0, pool.cachedBytes);
	}

	@Test public void countCapIsIndependentOfByteCap()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 100L * Q, 1);
		var first = pool.acquire(Q);
		var second = pool.acquire(Q);
		pool.release(first);
		pool.release(second);
		assertTrue(gl.deleted.contains(first.id));
		assertEquals(Q, pool.cachedBytes);
	}

	@Test public void failedFenceNeverPermitsReuse()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 8L * Q, 8);
		var first = pool.acquire(Q);
		pool.release(first);
		gl.status.put(1L, GL_WAIT_FAILED);
		assertNotEquals(first.id, pool.acquire(Q).id);
		assertTrue(gl.deleted.contains(first.id));
		assertEquals(1, pool.fenceFailures);
		gl.failFence = true;
		var second = pool.acquire(Q);
		pool.release(second);
		assertTrue(gl.deleted.contains(second.id));
		assertEquals(0, pool.cachedBytes);
		assertEquals(2, pool.fenceFailures);
	}

	@Test public void shutdownDrainsCacheAndDeletesLateReturnsWithoutFencing()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 8L * Q, 8);
		var cached = pool.acquire(Q);
		var active = pool.acquire(Q);
		pool.release(cached);
		pool.close();
		pool.release(active);
		assertEquals(2, gl.deleted.size());
		assertEquals(1, gl.nextFence);
		pool.close();
		assertThrows(IllegalStateException.class, () -> pool.acquire(Q));
		assertThrows(IllegalStateException.class, () -> pool.release(cached));
	}

	@Test public void rejectsWrongContextAndForeignOrDuplicateReturns()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 8L * Q, 8);
		var store = pool.acquire(Q);
		StaticVboPool other = new StaticVboPool(gl, 8L * Q, 8);
		assertThrows(IllegalStateException.class, () -> other.release(store));
		gl.wrongContext = true;
		assertThrows(IllegalStateException.class, () -> pool.release(store));
		assertThrows(IllegalStateException.class, () -> pool.acquire(Q));
		assertThrows(IllegalStateException.class, pool::close);
		assertTrue(gl.deleted.isEmpty());
		gl.wrongContext = false;
		pool.release(store);
		assertThrows(IllegalStateException.class, () -> pool.release(store));
	}

	@Test public void oversizedAndZeroBudgetStorageIsDeletedDirectly()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, StaticVboPool.MAX_CACHED_BYTES, 8);
		var huge = pool.acquire(StaticVboPool.MAX_BUFFER_BYTES + 1);
		assertEquals(StaticVboPool.MAX_BUFFER_BYTES + 1, huge.capacity);
		pool.release(huge);
		assertTrue(gl.deleted.contains(huge.id));
		assertEquals(0, gl.nextFence);
		StaticVboPool disabled = new StaticVboPool(gl, 0, 0);
		var store = disabled.acquire(Q);
		disabled.release(store);
		assertTrue(gl.deleted.contains(store.id));
		assertEquals(0, disabled.cachedBytes);
	}

	@Test public void vboHandoffClearsJavaViewsAndPreservesRequestedSize()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 8L * Q, 8);
		VBO first = new VBO(100, pool);
		first.init(GL_STATIC_DRAW);
		int id = first.bufId;
		first.vb = IntBuffer.allocate(25);
		first.len = 25;
		first.destroy();
		first.destroy();
		assertNull(first.vb);
		assertEquals(0, first.len);
		assertEquals(0, first.bufId);
		gl.signalAll();
		VBO second = new VBO(80, pool);
		second.init(GL_STATIC_DRAW);
		assertEquals(id, second.bufId);
		assertEquals(80, second.size);
		assertEquals(1, gl.creates);
	}

	@Test public void repeatedSceneCyclesReachAStoragePlateau()
	{
		Fake gl = new Fake();
		StaticVboPool pool = new StaticVboPool(gl, 64L * Q, 64);
		for (int cycle = 0; cycle < 100; cycle++)
		{
			StaticVboPool.Storage[] scene = new StaticVboPool.Storage[32];
			for (int i = 0; i < scene.length; i++) scene[i] = pool.acquire(100 + cycle);
			for (var store : scene) pool.release(store);
			gl.signalAll();
		}
		assertEquals(32, gl.creates);
		assertEquals(99 * 32, pool.hits);
		assertEquals(32L * Q, pool.cachedBytes);
		pool.close();
		assertEquals(32, gl.deleted.size());
		assertEquals(3200, gl.deletedFences.size());
	}
}
