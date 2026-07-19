package net.runelite.client.plugins.microbot.actionrecorder;

import net.runelite.client.plugins.microbot.actionrecorder.model.KeyboardCaptureMode;
import net.runelite.client.plugins.microbot.actionrecorder.model.ObjectCaptureMode;
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
		assertFalse(config.includeClockVariables());
		assertEquals(ObjectCaptureMode.ACTIONABLE_NEARBY, config.objectCaptureMode());
		assertTrue(config.captureGroundItems());
		assertTrue(config.ownedGroundItemsOnly());
		assertFalse(config.captureKeyboardContext());
		assertEquals(KeyboardCaptureMode.ALLOWLIST, config.keyboardCaptureMode());
		assertTrue(config.captureCameraChanges());
		assertEquals(1, config.gameTickInterval());
		assertEquals(16, config.nearbyObjectRadius());
		assertEquals(25, config.flushEveryRecords());
	}
}
