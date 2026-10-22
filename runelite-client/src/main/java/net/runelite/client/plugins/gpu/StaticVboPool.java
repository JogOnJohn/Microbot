package net.runelite.client.plugins.gpu;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import static org.lwjgl.opengl.GL33C.*;

/** Idle static storage belongs to one GL context, including buffers awaiting a fence. */
@Slf4j
final class StaticVboPool
{
	static final String PROPERTY = "microbot.gpu.staticVboReuse";
	static final int QUANTUM = 64 * 1024;
	static final int MAX_BUFFER_BYTES = 8 * 1024 * 1024;
	static final long MAX_CACHED_BYTES = 256L * 1024 * 1024;
	static final int MAX_CACHED_BUFFERS = 4096;
	static final int MAX_POLLS = 8;

	interface Backend
	{
		void checkContext();
		int create(int capacity);
		void delete(int id, int capacity);
		long fence();
		int poll(long fence);
		void deleteFence(long fence);
		void cached(int id);
	}

	static final class Storage
	{
		final int id, capacity;
		private final StaticVboPool owner;
		private long fence;
		private boolean cached, deleted;

		private Storage(StaticVboPool owner, int id, int capacity)
		{
			this.owner = owner;
			this.id = id;
			this.capacity = capacity;
		}
	}

	private final Backend backend;
	private final long byteLimit;
	private final int countLimit;
	private final TreeMap<Integer, ArrayDeque<Storage>> buckets = new TreeMap<>();
	private final LinkedHashMap<Integer, Storage> idle = new LinkedHashMap<>();
	private boolean closed;
	long cachedBytes, peakCachedBytes, hits, misses, createdBytes, reusedBytes, evictions, pendingPolls, fenceFailures;
	private long nextReport;

	StaticVboPool(Backend backend, long byteLimit, int countLimit)
	{
		if (byteLimit < 0 || countLimit < 0)
		{
			throw new IllegalArgumentException("Negative pool limit");
		}
		this.backend = backend;
		this.byteLimit = byteLimit;
		this.countLimit = countLimit;
	}

	static StaticVboPool create(GLCapabilities capabilities)
	{
		Thread thread = Thread.currentThread();
		long owner = GpuMemoryDiagnostics.owner();
		StaticVboPool pool = new StaticVboPool(new Backend()
		{
			public void checkContext()
			{
				if (Thread.currentThread() != thread || GL.getCapabilities() != capabilities)
				{
					throw new IllegalStateException("Static VBO pool used outside its owning GL context/thread");
				}
			}
			public int create(int capacity) { return VBO.createBuffer(capacity, GL_STATIC_DRAW, owner); }
			public void delete(int id, int capacity) { VBO.deleteBuffer(id, capacity); }
			public long fence() { return glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0); }
			public int poll(long fence) { return glClientWaitSync(fence, GL_SYNC_FLUSH_COMMANDS_BIT, 0); }
			public void deleteFence(long fence) { glDeleteSync(fence); }
			public void cached(int id)
			{
				GpuMemoryDiagnostics.adopt(GpuMemoryDiagnostics.Kind.BUFFER, id, "static-vbo-cache", owner);
			}
		}, MAX_CACHED_BYTES, MAX_CACHED_BUFFERS);
		log.info("gpu_static_vbo_pool | enabled=true byteLimit={} countLimit={}", MAX_CACHED_BYTES, MAX_CACHED_BUFFERS);
		return pool;
	}

	Storage acquire(int requested)
	{
		backend.checkContext();
		if (closed || requested <= 0)
		{
			throw new IllegalStateException("Closed pool or invalid static VBO size");
		}
		int capacity = capacity(requested);
		int polls = 0;
		// Limit both oversizing and work when the GPU has not finished recent draws.
		Integer key = buckets.ceilingKey(capacity);
		while (key != null && key <= Math.min(MAX_BUFFER_BYTES, (long) capacity * 2) && polls < MAX_POLLS)
		{
			ArrayDeque<Storage> bucket = buckets.get(key);
			int candidates = bucket.size();
			for (int i = 0; i < candidates && polls < MAX_POLLS; i++, polls++)
			{
				Storage storage = bucket.removeFirst();
				int status = backend.poll(storage.fence);
				if (status == GL_TIMEOUT_EXPIRED)
				{
					pendingPolls++;
					bucket.addLast(storage);
					continue;
				}
				idle.remove(storage.id);
				cachedBytes -= storage.capacity;
				storage.cached = false;
				backend.deleteFence(storage.fence);
				storage.fence = 0;
				if (bucket.isEmpty())
				{
					buckets.remove(key);
				}
				if (status == GL_ALREADY_SIGNALED || status == GL_CONDITION_SATISFIED)
				{
					hits++;
					reusedBytes += requested;
					return storage;
				}
				fenceFailures++;
				delete(storage);
			}
			key = buckets.higherKey(key);
		}
		misses++;
		int id = backend.create(capacity);
		createdBytes += capacity;
		return new Storage(this, id, capacity);
	}

	void release(Storage storage)
	{
		backend.checkContext();
		if (storage.owner != this || storage.cached || storage.deleted)
		{
			throw new IllegalStateException("Static VBO returned twice or to another context");
		}
		if (closed || storage.capacity > MAX_BUFFER_BYTES || storage.capacity > byteLimit || countLimit == 0)
		{
			delete(storage);
			return;
		}
		long fence = backend.fence();
		if (fence == 0)
		{
			fenceFailures++;
			delete(storage);
			return;
		}
		while (cachedBytes + storage.capacity > byteLimit || idle.size() >= countLimit)
		{
			Storage oldest = idle.values().iterator().next();
			remove(oldest);
			delete(oldest);
			evictions++;
		}
		storage.fence = fence;
		storage.cached = true;
		buckets.computeIfAbsent(storage.capacity, ignored -> new ArrayDeque<>()).addLast(storage);
		idle.put(storage.id, storage);
		cachedBytes += storage.capacity;
		peakCachedBytes = Math.max(peakCachedBytes, cachedBytes);
		backend.cached(storage.id);
	}

	private void remove(Storage storage)
	{
		ArrayDeque<Storage> bucket = buckets.get(storage.capacity);
		bucket.remove(storage);
		if (bucket.isEmpty())
		{
			buckets.remove(storage.capacity);
		}
		idle.remove(storage.id);
		cachedBytes -= storage.capacity;
		storage.cached = false;
	}

	private void delete(Storage storage)
	{
		if (storage.fence != 0)
		{
			backend.deleteFence(storage.fence);
			storage.fence = 0;
		}
		backend.delete(storage.id, storage.capacity);
		storage.deleted = true;
	}

	void close()
	{
		backend.checkContext();
		closed = true;
		for (Storage storage : idle.values())
		{
			delete(storage);
			storage.cached = false;
		}
		idle.clear();
		buckets.clear();
		cachedBytes = 0;
		report("closed");
	}

	void reportIfDue()
	{
		long now = System.nanoTime();
		if (now >= nextReport)
		{
			nextReport = now + 30_000_000_000L;
			report("periodic");
		}
	}

	private void report(String phase)
	{
		log.info("gpu_static_vbo_pool | phase={} hits={} misses={} createdBytes={} reusedBytes={} cachedBytes={} cachedBuffers={} peakCachedBytes={} evictions={} pendingPolls={} fenceFailures={}",
			phase, hits, misses, createdBytes, reusedBytes, cachedBytes, idle.size(), peakCachedBytes, evictions, pendingPolls, fenceFailures);
	}

	static int capacity(int requested)
	{
		return requested > MAX_BUFFER_BYTES ? requested : (requested + QUANTUM - 1) / QUANTUM * QUANTUM;
	}
}
