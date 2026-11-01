package net.runelite.client.plugins.microbot.inventorysetups;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class InventorySetupsItemCopyTest
{
	@Test
	public void copyPreservesSettingsWithoutSharingMutableState()
	{
		InventorySetupsItem source = new InventorySetupsItem(42, "Test item", 8, true,
			InventorySetupsStackCompareID.Greater_Than, true, 9);
		InventorySetupsItem first = source.copyToSlot(1);
		InventorySetupsItem second = source.copyToSlot(2);
		assertNotSame(source, first);
		assertNotSame(first, second);
		assertEquals(42, first.getId());
		assertEquals("Test item", first.getName());
		assertEquals(8, first.getQuantity());
		assertTrue(first.isFuzzy());
		assertTrue(first.isLocked());
		assertEquals(InventorySetupsStackCompareID.Greater_Than, first.getStackCompare());
		assertEquals(1, first.getSlot());
		first.setQuantity(1);
		first.setLocked(false);
		first.setFuzzy(false);
		assertEquals(8, second.getQuantity());
		assertTrue(second.isLocked());
		assertTrue(second.isFuzzy());
		assertEquals(2, second.getSlot());
		assertEquals(9, source.getSlot());
	}

	@Test
	public void legacyConstructorKeepsUnlockedUnslottedDefaults()
	{
		InventorySetupsItem item = new InventorySetupsItem(42, "Test item", 1, false, InventorySetupsStackCompareID.None);
		assertFalse(item.isLocked());
		assertEquals(-1, item.getSlot());
	}

	@Test
	public void bulkReplacementAssignsIndependentItemsToEachMatchingPosition() throws Exception
	{
		MInventorySetupsPlugin plugin = new MInventorySetupsPlugin();
		Field ammo = MInventorySetupsPlugin.class.getDeclaredField("ammoHandler");
		ammo.setAccessible(true);
		ammo.set(plugin, mock(InventorySetupsAmmoHandler.class));
		InventorySetupsItem old = new InventorySetupsItem(1, "Old", 1, false, InventorySetupsStackCompareID.None);
		InventorySetupsItem untouched = new InventorySetupsItem(2, "Other", 1, false, InventorySetupsStackCompareID.None);
		InventorySetupsItem replacement = new InventorySetupsItem(42, "New", 8, false,
			InventorySetupsStackCompareID.Standard, true, 7);
		List<InventorySetupsItem> container = new ArrayList<>(Arrays.asList(old, untouched, old));
		Method update = MInventorySetupsPlugin.class.getDeclaredMethod("updateAllInstancesInContainerSetupWithNewItem",
			InventorySetup.class, List.class, InventorySetupsItem.class, InventorySetupsItem.class, InventorySetupsSlotID.class);
		update.setAccessible(true);
		assertEquals(true, update.invoke(plugin, mock(InventorySetup.class), container, old, replacement, InventorySetupsSlotID.INVENTORY));
		assertEquals(0, container.get(0).getSlot());
		assertEquals(2, container.get(2).getSlot());
		assertSame(untouched, container.get(1));
		assertNotSame(replacement, container.get(0));
		assertNotSame(container.get(0), container.get(2));
		assertTrue(container.get(0).isLocked());
		assertTrue(container.get(2).isLocked());
		container.get(0).setQuantity(1);
		assertEquals(8, container.get(2).getQuantity());
		assertEquals(7, replacement.getSlot());
	}
}
