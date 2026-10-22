package net.runelite.client.plugins.gpu;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Coordinate mapped-memory writers with context teardown without blocking the GL thread. */
final class SceneUploadLifecycle
{
	private int active;
	private boolean closing;
	private long startupGeneration;

	synchronized long requestStart()
	{
		return ++startupGeneration;
	}

	synchronized boolean currentStart(long generation)
	{
		return generation == startupGeneration;
	}

	synchronized boolean begin()
	{
		if (closing)
		{
			return false;
		}
		active++;
		return true;
	}

	synchronized void end()
	{
		if (active == 0)
		{
			throw new IllegalStateException("Unbalanced scene upload");
		}
		active--;
	}

	synchronized void close()
	{
		closing = true;
		startupGeneration++;
	}

	synchronized boolean drained()
	{
		return active == 0;
	}

	synchronized void reset()
	{
		if (active != 0)
		{
			throw new IllegalStateException("Scene upload still owns mapped storage");
		}
		closing = false;
	}

	static void dispatchAndWait(Consumer<Runnable> dispatcher, Runnable action)
	{
		FutureTask<Void> task = new FutureTask<>(action, null);
		dispatcher.accept(task);
		boolean interrupted = false;
		try
		{
			while (true)
			{
				try
				{
					task.get();
					return;
				}
				catch (InterruptedException e)
				{
					// A queued GL callback must finish before its upload releases the context.
					interrupted = true;
				}
				catch (ExecutionException e)
				{
					throw new IllegalStateException("Scene GL operation failed", e.getCause());
				}
			}
		}
		finally
		{
			if (interrupted)
			{
				Thread.currentThread().interrupt();
			}
		}
	}

	static void awaitRendererStop(CompletableFuture<Void> completion)
	{
		try
		{
			completion.get(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while stopping GPU renderer", e);
		}
		catch (ExecutionException | TimeoutException e)
		{
			throw new IllegalStateException("GPU teardown did not complete; renderer handoff aborted", e);
		}
	}
}
