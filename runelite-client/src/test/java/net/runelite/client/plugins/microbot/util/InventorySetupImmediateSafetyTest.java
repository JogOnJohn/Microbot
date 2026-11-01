package net.runelite.client.plugins.microbot.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InventorySetupImmediateSafetyTest
{
	private static InventorySetupsItem emptied(int slot)
	{
		return new InventorySetupsItem(-1, "", 0, true, InventorySetupsStackCompareID.Standard, false, slot);
	}

	private static InventorySetup setup(List<InventorySetupsItem> inventory, List<InventorySetupsItem> equipment)
	{
		InventorySetup setup = mock(InventorySetup.class);
		when(setup.getInventory()).thenReturn(inventory);
		when(setup.getEquipment()).thenReturn(equipment);
		when(setup.getAdditionalFilteredItems()).thenReturn(Collections.emptyMap());
		return setup;
	}

	private static Rs2ItemModel held(int id, String name)
	{
		Rs2ItemModel item = mock(Rs2ItemModel.class);
		when(item.getId()).thenReturn(id);
		when(item.getName()).thenReturn(name);
		return item;
	}

	@Test
	public void emptiedEquipmentDoesNotRequireGearOrOpenTheBank()
	{
		InventorySetupsItem empty = emptied(3);
		empty.setLocked(true);
		InventorySetup setup = setup(Collections.emptyList(), Collections.singletonList(empty));
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			 MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			 MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
			 MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
		{
			inventory.when(Rs2Inventory::items).thenAnswer(call -> Stream.empty());
			Rs2InventorySetup loader = new Rs2InventorySetup(setup, null);
			assertTrue(loader.doesEquipmentMatch());
			assertTrue(loader.loadEquipment());
			bank.verify(Rs2Bank::openBank, never());
			assertTrue(empty.isLocked());
			assertEquals(3, empty.getSlot());
		}
	}

	@Test
	public void emptiedFuzzyRowsDoNotHideForeignInventoryOrEquipment()
	{
		InventorySetupsItem shark = new InventorySetupsItem(ItemID.SHARK, "Shark", 1, false, InventorySetupsStackCompareID.None);
		Rs2ItemModel bones = held(ItemID.BONES, "Bones");
		Rs2ItemModel boots = held(ItemID.GRACEFUL_BOOTS, "Graceful boots");
		Rs2ItemModel carriedShark = held(ItemID.SHARK, "Shark");
		InventorySetup setup = setup(Arrays.asList(shark, emptied(5)), Collections.singletonList(emptied(10)));
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			 MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class))
		{
			inventory.when(Rs2Inventory::all).thenReturn(Arrays.asList(carriedShark, bones));
			equipment.when(Rs2Equipment::all).thenAnswer(call -> Stream.of(boots));
			Rs2InventorySetup loader = new Rs2InventorySetup(setup, null);
			assertEquals(Collections.singletonList(bones), loader.getItemsNotInInventorySetup(false));
			assertEquals(Collections.singletonList(boots), loader.getEquipmentNotInSetup(false));
		}
	}

	@Test
	public void loadingAnEmptiedFuzzyRowDoesNotMoveOrWithdrawAnItem()
	{
		InventorySetup setup = setup(Collections.singletonList(emptied(5)), Collections.emptyList());
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			 MockedStatic<Global> global = mockStatic(Global.class);
			 MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			 MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
		{
			inventory.when(Rs2Inventory::items).thenAnswer(call -> Stream.empty());
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			bank.when(() -> Rs2Bank.verifyBankMirrorAfterOpen(true, 0)).thenReturn(true);
			assertTrue(new Rs2InventorySetup(setup, null).loadInventory(false));
			inventory.verify(() -> Rs2Inventory.moveItemToSlot(any(Rs2ItemModel.class), anyInt()), never());
			inventory.verify(() -> Rs2Inventory.itemQuantity(anyString()), never());
			bank.verify(() -> Rs2Bank.hasBankItem(anyInt(), anyInt()), never());
		}
	}

	@Test
	public void runePouchLoadExcludesUnknownRunesAndKeepsKnownQuantities()
	{
		InventorySetupsItem law = new InventorySetupsItem(ItemID.LAWRUNE, "Law rune", 100, false, InventorySetupsStackCompareID.None);
		InventorySetupsItem unknown = new InventorySetupsItem(ItemID.AETHERRUNE, "Aether rune", 500, false, InventorySetupsStackCompareID.None);
		InventorySetup setup = setup(Collections.emptyList(), Collections.emptyList());
		when(setup.getRune_pouch()).thenReturn(Arrays.asList(unknown, law, InventorySetupsItem.getDummyItem()));
		assertNull(Runes.byItemId(unknown.getId()));
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			 MockedStatic<Global> global = mockStatic(Global.class);
			 MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			 MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
			 MockedStatic<Rs2RunePouch> pouch = mockStatic(Rs2RunePouch.class))
		{
			inventory.when(Rs2Inventory::items).thenAnswer(call -> Stream.empty());
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			bank.when(() -> Rs2Bank.verifyBankMirrorAfterOpen(true, 0)).thenReturn(true);
			pouch.when(() -> Rs2RunePouch.loadFromInventorySetup(anyMap())).thenReturn(true);
			pouch.when(() -> Rs2RunePouch.contains(anyMap(), anyBoolean())).thenReturn(true);
			assertTrue(new Rs2InventorySetup(setup, null).loadInventory(false));
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<Runes, InventorySetupsItem>> requested = ArgumentCaptor.forClass(Map.class);
			pouch.verify(() -> Rs2RunePouch.loadFromInventorySetup(requested.capture()));
			assertEquals(Collections.singletonMap(Runes.LAW, law), requested.getValue());
			assertEquals(100, requested.getValue().get(Runes.LAW).getQuantity());
		}
	}
}
