package net.runelite.client.plugins.microbot.actionrecorder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActionRecorderConfigTest
{
	private final ActionRecorderConfig config = new ActionRecorderConfig()
	{
	};

	@Test
	public void fullCaptureProfileIsTheDefault()
	{
		assertFalse(config.recordingEnabled());
		assertTrue(config.captureInteractions());
		assertTrue(config.captureGameTicks());
		assertTrue(config.captureInventory());
		assertTrue(config.captureEquipment());
		assertTrue(config.captureBank());
		assertTrue(config.captureAnimations());
		assertTrue(config.captureGraphics());
		assertTrue(config.captureWidgets());
		assertTrue(config.captureVarbits());
		assertTrue(config.captureStats());
		assertTrue(config.captureGameMessages());
		assertTrue(config.captureGameState());
		assertTrue(config.captureGameObjects());
		assertTrue(config.actionableObjectsOnly());
		assertTrue(config.captureGroundItems());
		assertTrue(config.ownedGroundItemsOnly());
		assertEquals(1, config.gameTickInterval());
		assertEquals(16, config.nearbyObjectRadius());
		assertEquals(25, config.flushEveryRecords());
	}
}
