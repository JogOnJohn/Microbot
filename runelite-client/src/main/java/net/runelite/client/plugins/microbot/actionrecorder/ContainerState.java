package net.runelite.client.plugins.microbot.actionrecorder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.actionrecorder.model.ItemDelta;
import net.runelite.client.plugins.microbot.actionrecorder.model.ItemSnapshot;

final class ContainerState
{
	private final List<ItemSnapshot> items;
	private final Map<Integer, Long> quantities;
	private final Map<Integer, ItemSnapshot> metadata;

	ContainerState(List<ItemSnapshot> items)
	{
		this.items = Collections.unmodifiableList(new ArrayList<>(items));
		this.quantities = new HashMap<>();
		this.metadata = new HashMap<>();
		for (ItemSnapshot item : items)
		{
			quantities.merge(item.getItemId(), (long) item.getQuantity(), Long::sum);
			metadata.putIfAbsent(item.getItemId(), item);
		}
	}

	List<ItemSnapshot> getItems()
	{
		return items;
	}

	int getOccupiedSlots()
	{
		return items.size();
	}

	static List<ItemDelta> diff(ContainerState before, ContainerState after)
	{
		if (before == null)
		{
			return Collections.emptyList();
		}

		Set<Integer> itemIds = new LinkedHashSet<>();
		itemIds.addAll(before.quantities.keySet());
		itemIds.addAll(after.quantities.keySet());

		List<ItemDelta> deltas = new ArrayList<>();
		for (int itemId : itemIds)
		{
			long beforeQuantity = before.quantities.getOrDefault(itemId, 0L);
			long afterQuantity = after.quantities.getOrDefault(itemId, 0L);
			if (beforeQuantity == afterQuantity)
			{
				continue;
			}

			ItemSnapshot item = after.metadata.getOrDefault(itemId, before.metadata.get(itemId));
			deltas.add(new ItemDelta(
				itemId,
				item.getName(),
				beforeQuantity,
				afterQuantity,
				afterQuantity - beforeQuantity,
				item.isNoted(),
				item.getLinkedNoteId(),
				item.isPlaceholder(),
				item.getLinkedPlaceholderId()));
		}
		return deltas;
	}
}
