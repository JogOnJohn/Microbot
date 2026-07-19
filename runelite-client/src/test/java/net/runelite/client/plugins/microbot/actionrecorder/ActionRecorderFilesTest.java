package net.runelite.client.plugins.microbot.actionrecorder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActionRecorderFilesTest
{
	@Test
	public void safeNameProducesPortableBoundedDirectoryComponent()
	{
		String value = ActionRecorderFiles.safeName("  Farm & Birdhouse: Rune / Fossil Island?  ");

		assertEquals("farm-birdhouse-rune-fossil-island", value);
		assertFalse(value.contains("/"));
		assertTrue(value.length() <= 48);
	}

	@Test
	public void safeNameFallsBackForBlankInput()
	{
		assertEquals("session", ActionRecorderFiles.safeName("***"));
	}
}
