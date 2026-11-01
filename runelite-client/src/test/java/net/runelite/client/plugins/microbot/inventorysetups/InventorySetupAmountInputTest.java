package net.runelite.client.plugins.microbot.inventorysetups;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class InventorySetupAmountInputTest
{
	@Test
	public void emptyAndBareSuffixInputsDefaultToOne()
	{
		for (String input : new String[]{null, "", "k", "M", "b"})
		{
			assertEquals(1, InventorySetupUtilities.parseTextInputAmount(input));
		}
	}

	@Test
	public void validAmountsKeepScalingClampingAndMinimum()
	{
		assertEquals(12, InventorySetupUtilities.parseTextInputAmount("12"));
		assertEquals(2000, InventorySetupUtilities.parseTextInputAmount("2K"));
		assertEquals(3000000, InventorySetupUtilities.parseTextInputAmount("3m"));
		assertEquals(1000000000, InventorySetupUtilities.parseTextInputAmount("1b"));
		assertEquals(Integer.MAX_VALUE, InventorySetupUtilities.parseTextInputAmount("3b"));
		assertEquals(1, InventorySetupUtilities.parseTextInputAmount("0"));
	}

	@Test
	public void malformedNumericInputIsStillRejected()
	{
		try
		{
			InventorySetupUtilities.parseTextInputAmount("12..3");
			fail("Expected a malformed amount to be rejected");
		}
		catch (NumberFormatException expected)
		{
			// The empty-input fallback must not broaden numeric parsing.
		}
	}
}
