package net.runelite.client.plugins;

/** Opt-in barrier for plugins whose resource teardown can outlast stopPlugin. */
public interface PluginStopBarrier
{
	void awaitStopCompletion();
}
