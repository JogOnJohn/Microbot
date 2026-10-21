package net.runelite.client.plugins.microbot.util.events;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.Microbot;

final class CachedClientCheck
{
	private final BooleanSupplier onClientThread;
	private final AtomicBoolean refreshQueued = new AtomicBoolean();
	private volatile boolean value;

	CachedClientCheck(BooleanSupplier onClientThread)
	{
		this.onClientThread = onClientThread;
	}

	boolean get()
	{
		if (!Microbot.isLoggedIn())
		{
			value = false;
			return false;
		}
		if (refreshQueued.compareAndSet(false, true))
		{
			Microbot.getClientThread().invokeLater(() ->
			{
				try
				{
					value = onClientThread.getAsBoolean();
				}
				catch (RuntimeException e)
				{
					value = false;
				}
				finally
				{
					refreshQueued.set(false);
				}
				return true;
			});
		}
		return value;
	}
}
