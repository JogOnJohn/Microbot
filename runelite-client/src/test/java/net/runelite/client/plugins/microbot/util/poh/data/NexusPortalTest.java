package net.runelite.client.plugins.microbot.util.poh.data;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import org.junit.Assert;
import org.junit.Test;

public class NexusPortalTest
{
	@Test
	public void lassarUsesItsAppendedNexusValue()
	{
		Assert.assertSame(NexusPortal.LASSAR, NexusPortal.fromVarbitValue(34));
		Assert.assertEquals(34, NexusPortal.LASSAR.varbitValue());
		Assert.assertEquals(new WorldPoint(3004, 3470, 0), NexusPortal.LASSAR.getDestination());
	}

	@Test
	public void existingValuesRemainStableAndUnknownValuesAreSkipped()
	{
		Assert.assertSame(NexusPortal.CIVITAS_ILLA_FORTIS, NexusPortal.fromVarbitValue(31));
		Assert.assertNull(NexusPortal.fromVarbitValue(99));
	}

	@Test
	public void allCurrentNexusSlotsAreRead()
	{
		Assert.assertEquals(45, NexusPortal.VARBITS.length);
		Assert.assertEquals(VarbitID.POH_NEXUS_TELE_45, NexusPortal.VARBITS[44]);
	}
}
