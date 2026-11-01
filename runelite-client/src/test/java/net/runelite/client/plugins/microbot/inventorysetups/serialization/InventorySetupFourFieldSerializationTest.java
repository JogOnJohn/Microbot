package net.runelite.client.plugins.microbot.inventorysetups.serialization;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.ArrayList;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsCache;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsPersistentDataManager;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class InventorySetupFourFieldSerializationTest
{
	private final Gson gson = new Gson();

	@Test
	public void nullFieldsKeepLegacyDefaults()
	{
		InventorySetupItemSerializable item = gson.fromJson(
			"{\"id\":null,\"q\":null,\"f\":null,\"sc\":null}", InventorySetupItemSerializable.class);
		assertEquals(-1, item.getId());
		assertNull(item.getQ());
		assertNull(item.getF());
		assertNull(item.getSc());
		InventorySetupsItem restored = InventorySetupItemSerializable.convertToInventorySetupItem(item);
		assertEquals(1, restored.getQuantity());
		assertFalse(restored.isFuzzy());
		assertEquals(InventorySetupsStackCompareID.None, restored.getStackCompare());
		assertFalse(restored.isLocked());
		assertEquals(-1, restored.getSlot());
	}

	@Test(timeout = 2000)
	public void unknownNestedFieldsAndNewerSlotFieldsAreSkipped()
	{
		InventorySetupItemSerializable item = gson.fromJson(
			"{\"future\":{\"nested\":[1,null,{}]},\"l\":true,\"s\":7,\"id\":42,\"q\":5}",
			InventorySetupItemSerializable.class);
		assertEquals(42, item.getId());
		assertEquals(Integer.valueOf(5), item.getQ());
		assertEquals("{\"id\":42,\"q\":5}", gson.toJson(item));
	}

	@Test
	public void applicationGsonPortableImportUsesItemDefaults()
	{
		InventorySetupPortable imported = RuneLiteAPI.GSON.fromJson(
			"{\"setup\":{\"inv\":[{\"id\":42,\"q\":null},null],\"eq\":[],\"name\":\"Legacy\"}}",
			InventorySetupPortable.class);
		InventorySetupsItem item = imported.getSerializedSetup().getInventory().get(0);
		assertEquals(42, item.getId());
		assertEquals(1, item.getQuantity());
		assertEquals(-1, item.getSlot());
		assertTrue(InventorySetupsItem.itemIsDummy(imported.getSerializedSetup().getInventory().get(1)));
	}

	@Test
	public void writingKeepsFourFieldsAndDoesNotMutateLocksOrSlots()
	{
		InventorySetupsItem source = new InventorySetupsItem(42, "Test item", 5, true,
			InventorySetupsStackCompareID.Less_Than, true, 7);
		InventorySetupItemSerializable serialized = InventorySetupItemSerializable.convertFromInventorySetupItem(source);
		assertEquals("{\"id\":42,\"q\":5,\"f\":true,\"sc\":\"Less_Than\"}", gson.toJson(serialized));
		assertTrue(source.isLocked());
		assertEquals(7, source.getSlot());
		assertEquals("{\"id\":42}", gson.toJson(new InventorySetupItemSerializable(42, null, null, null)));
	}

	@Test
	public void nullItemsAndCanonicalDummiesKeepTheirRepresentation()
	{
		assertNull(gson.fromJson("null", InventorySetupItemSerializable.class));
		assertEquals("null", gson.toJson(null, InventorySetupItemSerializable.class));
		assertNull(InventorySetupItemSerializable.convertFromInventorySetupItem(InventorySetupsItem.getDummyItem()));
	}

	@Test
	public void persistenceUsesTheSameAnnotatedAdapterWithoutExtraRegistration() throws Exception
	{
		InventorySetupsPersistentDataManager manager = new InventorySetupsPersistentDataManager(
			mock(MInventorySetupsPlugin.class), mock(ConfigManager.class), mock(InventorySetupsCache.class),
			gson, new ArrayList<>(), new ArrayList<>());
		Field field = InventorySetupsPersistentDataManager.class.getDeclaredField("gson");
		field.setAccessible(true);
		Gson storage = (Gson) field.get(manager);
		String json = "{\"id\":42,\"q\":null,\"s\":9}";
		assertEquals(gson.fromJson(json, InventorySetupItemSerializable.class),
			storage.fromJson(json, InventorySetupItemSerializable.class));
		assertEquals("{\"id\":42}", storage.toJson(storage.fromJson(json, InventorySetupItemSerializable.class)));
	}
}
