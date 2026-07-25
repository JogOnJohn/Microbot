package net.runelite.client.plugins.microbot.actionrecorder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.plugins.microbot.actionrecorder.model.ItemDelta;
import net.runelite.client.plugins.microbot.actionrecorder.model.ItemSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContainerStateTest
{
	@Test
	public void diffAggregatesSlotsAndPreservesNotedMetadata()
	{
		ContainerState before = new ContainerState(Arrays.asList(
			item(0, 100, "Logs", 3, false, 101),
			item(1, 100, "Logs", 2, false, 101),
			item(2, 201, "Barley seed", 10, true, 200)));
		ContainerState after = new ContainerState(Arrays.asList(
			item(0, 100, "Logs", 1, false, 101),
			item(2, 201, "Barley seed", 15, true, 200)));

		List<ItemDelta> deltas = ContainerState.diff(before, after);

		assertEquals(2, deltas.size());
		ItemDelta logs = find(deltas, 100);
		assertEquals(5, logs.getBeforeQuantity());
		assertEquals(1, logs.getAfterQuantity());
		assertEquals(-4, logs.getDelta());

		ItemDelta seeds = find(deltas, 201);
		assertEquals(5, seeds.getDelta());
		assertTrue(seeds.isNoted());
		assertEquals(200, seeds.getLinkedNoteId());
	}

	@Test
	public void firstSnapshotEstablishesBaselineWithoutSyntheticDeltas()
	{
		ContainerState after = new ContainerState(Collections.singletonList(
			item(0, 100, "Logs", 10, false, 101)));

		assertTrue(ContainerState.diff(null, after).isEmpty());
	}

	private static ItemDelta find(List<ItemDelta> deltas, int itemId)
	{
		return deltas.stream().filter(delta -> delta.getItemId() == itemId).findFirst().orElseThrow(AssertionError::new);
	}

	private static ItemSnapshot item(int slot, int itemId, String name, int quantity, boolean noted, int linkedNoteId)
	{
		return new ItemSnapshot(slot, itemId, name, quantity, noted, linkedNoteId, false, -1, true);
	}
}
