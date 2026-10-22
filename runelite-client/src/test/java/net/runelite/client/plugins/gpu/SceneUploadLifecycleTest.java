package net.runelite.client.plugins.gpu;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class SceneUploadLifecycleTest
{
	@Test public void rendererHandoffWaitsForCleanupCompletion() throws Exception
	{
		CompletableFuture<Void> cleanup = new CompletableFuture<>();
		CountDownLatch entered = new CountDownLatch(1);
		FutureTask<Void> stop = new FutureTask<>(() ->
		{
			entered.countDown();
			SceneUploadLifecycle.awaitRendererStop(cleanup);
		}, null);
		Thread manager = new Thread(stop);
		manager.start();
		try
		{
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertFalse(stop.isDone());
		}
		finally { cleanup.complete(null); }
		stop.get(5, TimeUnit.SECONDS);
		manager.join(5000);
		assertFalse(manager.isAlive());
	}

	@Test public void shutdownInvalidatesQueuedStartupsIncludingRapidRestarts()
	{
		SceneUploadLifecycle lifecycle = new SceneUploadLifecycle();
		long first = lifecycle.requestStart();
		assertTrue(lifecycle.currentStart(first));
		lifecycle.close();
		assertFalse(lifecycle.currentStart(first));
		lifecycle.reset();
		assertTrue(lifecycle.begin());
		lifecycle.close();
		long restart = lifecycle.requestStart();
		assertTrue(lifecycle.currentStart(restart));
		assertFalse(lifecycle.drained());
		lifecycle.close();
		lifecycle.end();
		assertTrue(lifecycle.drained());
		assertFalse(lifecycle.currentStart(restart));
		long latest = lifecycle.requestStart();
		lifecycle.reset();
		assertTrue(lifecycle.currentStart(latest));
		assertFalse(lifecycle.currentStart(first));
	}

	@Test public void teardownWaitsForQueuedGlAndCpuWriterEvenWhenInterrupted() throws Exception
	{
		SceneUploadLifecycle lifecycle = new SceneUploadLifecycle();
		CountDownLatch queued = new CountDownLatch(1);
		CountDownLatch writing = new CountDownLatch(1);
		CountDownLatch finishWriting = new CountDownLatch(1);
		AtomicReference<Runnable> glWork = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		AtomicBoolean interruptRestored = new AtomicBoolean();
		Thread uploader = new Thread(() ->
		{
			try
			{
				assertTrue(lifecycle.begin());
				try
				{
					SceneUploadLifecycle.dispatchAndWait(work ->
					{
						glWork.set(work);
						queued.countDown();
					}, () -> { });
					interruptRestored.set(Thread.interrupted());
					writing.countDown();
					assertTrue(finishWriting.await(5, TimeUnit.SECONDS));
				}
				finally
				{
					lifecycle.end();
				}
			}
			catch (Throwable error) { failure.set(error); }
		});
		uploader.start();
		try
		{
			assertTrue(queued.await(5, TimeUnit.SECONDS));
			lifecycle.close();
			uploader.interrupt();
			assertFalse(lifecycle.drained());
			assertFalse(lifecycle.begin());
			glWork.get().run();
			assertTrue(writing.await(5, TimeUnit.SECONDS));
			assertFalse(lifecycle.drained());
			assertTrue(interruptRestored.get());
		}
		finally
		{
			finishWriting.countDown();
			Runnable pending = glWork.get();
			if (pending != null) pending.run();
			uploader.join(5000);
		}
		assertFalse(uploader.isAlive());
		assertNull(failure.get());
		assertTrue(lifecycle.drained());
		lifecycle.reset();
		assertTrue(lifecycle.begin());
		lifecycle.end();
	}

	@Test public void nestedUploadsMustAllFinishBeforeRestart()
	{
		SceneUploadLifecycle lifecycle = new SceneUploadLifecycle();
		assertTrue(lifecycle.begin());
		assertTrue(lifecycle.begin());
		lifecycle.close();
		lifecycle.end();
		assertFalse(lifecycle.drained());
		try
		{
			lifecycle.reset();
			fail("Restart must wait for the final writer");
		}
		catch (IllegalStateException expected) { }
		lifecycle.end();
		assertTrue(lifecycle.drained());
		lifecycle.reset();
		assertTrue(lifecycle.begin());
		lifecycle.end();
	}

	@Test public void glFailurePropagatesAndUploadCanReleaseItsLifetime()
	{
		SceneUploadLifecycle lifecycle = new SceneUploadLifecycle();
		assertTrue(lifecycle.begin());
		RuntimeException failure = new RuntimeException("allocation failed");
		try
		{
			SceneUploadLifecycle.dispatchAndWait(Runnable::run, () -> { throw failure; });
			fail("Missing GL failure");
		}
		catch (IllegalStateException error)
		{
			assertSame(failure, error.getCause());
		}
		finally { lifecycle.end(); }
		lifecycle.close();
		assertTrue(lifecycle.drained());
	}
}
