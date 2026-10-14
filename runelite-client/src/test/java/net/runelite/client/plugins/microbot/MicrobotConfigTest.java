package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.microbot.util.security.HopBudget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MicrobotConfigTest
{
	@Test
	public void inputYieldingRemainsEnabledByDefault()
	{
		MicrobotConfig config = new MicrobotConfig() { };

		assertFalse(config.disableInputYielding());
	}

	@Test
	public void hopLimitIsOptInWithTheEstimatedAllowance()
	{
		MicrobotConfig config = new MicrobotConfig() { };

		assertFalse(config.enableHopLimit());
		assertEquals(HopBudget.DEFAULT_CAPACITY, config.hopLimitCapacity());
		assertEquals(HopBudget.DEFAULT_REFILL_PER_HOUR, config.hopLimitRefillPerHour());
		assertEquals(HopBudget.DEFAULT_RESERVE, config.hopLimitReserve());
	}
}
