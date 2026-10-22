package net.runelite.client.plugins;

import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.task.Scheduler;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PluginStopBarrierTest
{
	@PluginDescriptor(name = "Old renderer", conflicts = {"New renderer"})
	public static class OldRenderer extends Plugin implements PluginStopBarrier
	{
		boolean pending;
		@Override protected void shutDown()
		{
			pending = true;
			throw new IllegalStateException("Simulated teardown timeout");
		}
		@Override public void awaitStopCompletion()
		{
			if (pending) { throw new IllegalStateException("Teardown pending"); }
		}
	}

	@PluginDescriptor(name = "New renderer")
	public static class NewRenderer extends Plugin
	{
		boolean started;
		@Override protected void startUp() { started = true; }
	}

	@Test
	public void retryCannotBypassTimedOutTeardown() throws Exception
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getConfiguration(anyString(), anyString())).thenReturn("true");
		PluginManager manager = new PluginManager(false, mock(EventBus.class), mock(Scheduler.class), config, () -> null, null);
		OldRenderer old = new OldRenderer();
		NewRenderer replacement = new NewRenderer();
		manager.add(old);
		manager.add(replacement);
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				assertTrue(manager.startPlugin(old));
				for (int attempt = 0; attempt < 2; attempt++)
				{
					try { manager.startPlugin(replacement); fail("Must reject pending teardown"); }
					catch (PluginInstantiationException expected) { }
					assertFalse(replacement.started);
				}
				try { manager.startPlugin(old); fail("Must also reject own restart"); }
				catch (PluginInstantiationException expected) { }
				old.pending = false;
				assertTrue(manager.startPlugin(replacement));
				assertTrue(replacement.started);
			}
			catch (PluginInstantiationException ex) { throw new AssertionError(ex); }
		});
	}
}
