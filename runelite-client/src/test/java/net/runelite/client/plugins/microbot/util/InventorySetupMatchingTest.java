package net.runelite.client.plugins.microbot.util;

import java.util.*;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.*;
import org.junit.*;
import org.mockito.MockedStatic;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InventorySetupMatchingTest {
    private final List<Rs2ItemModel> inventory = new ArrayList<>();
    private MockedStatic<Microbot> bot;
    private MockedStatic<Global> global;
    private MockedStatic<Rs2Inventory> inv;
    private MockedStatic<Rs2Equipment> equipment;
    private MockedStatic<Rs2Bank> bank;
    private MockedStatic<Rs2RunePouch> pouch;

    @Before public void setupMocks() throws Exception {
        bot = mockStatic(Microbot.class);
        global = mockStatic(Global.class);
        inv = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
        inv.when(Rs2Inventory::items).thenAnswer(call -> new ArrayList<>(inventory).stream());
        equipment = mockStatic(Rs2Equipment.class, CALLS_REAL_METHODS);
        equipment.when(Rs2Equipment::items).thenReturn(Collections.emptyList());
        bank = mockStatic(Rs2Bank.class);
        pouch = mockStatic(Rs2RunePouch.class);
        bank.when(Rs2Bank::isOpen).thenReturn(true);
        bank.when(() -> Rs2Bank.verifyBankMirrorAfterOpen(true, 0)).thenReturn(true);
        bank.when(() -> Rs2Bank.isInventoryItemRetainedForSetupDeposit(any(), any(), any())).thenReturn(true);
        inv.when(() -> Rs2Inventory.moveItemToSlot(any(), anyInt())).thenReturn(false);
        inv.when(() -> Rs2Inventory.waitForInventoryChanges(anyInt())).thenReturn(true);
        Client client = mock(Client.class);
        when(client.getItemDefinition(anyInt())).thenAnswer(call -> mock(ItemComposition.class));
        for (int id : new int[]{ItemID.Cert.SHARK, ItemID.Cert.BONES}) {
            ItemComposition definition = mock(ItemComposition.class);
            when(definition.getName()).thenReturn(id == ItemID.Cert.SHARK ? "Shark" : "Bones");
            when(definition.getNote()).thenReturn(799);
            when(definition.isStackable()).thenReturn(true);
            doReturn(definition).when(client).getItemDefinition(id);
        }
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call -> Optional.ofNullable(((Callable<?>)call.getArgument(0)).call()));
        bot.when(Microbot::getClient).thenReturn(client);
        bot.when(Microbot::getClientThread).thenReturn(thread);
    }

    @After public void cleanup() { pouch.close(); bank.close(); equipment.close(); inv.close(); global.close(); bot.close(); }

    private Rs2InventorySetup loader(int id, String name) {
        List<InventorySetupsItem> rows = emptyContainer(28);
        rows.set(27, item(id, name, 100, -1));
        return new Rs2InventorySetup(setup("Supplies", rows, emptyContainer(14)), null);
    }

    private void hasThirtyBones() {
        Rs2ItemModel held = mock(Rs2ItemModel.class);
        when(held.getId()).thenReturn(ItemID.Cert.BONES);
        when(held.getName()).thenReturn("Bones");
        when(held.getQuantity()).thenReturn(30);
        when(held.getSlot()).thenReturn(27);
        inventory.add(held);
    }

    private void noNameLookup() {
        bank.verify(() -> Rs2Bank.hasBankItem(anyString(), anyInt(), anyBoolean()), never());
        bank.verify(() -> Rs2Bank.withdrawX(anyString(), anyInt()), never());
        bank.verify(() -> Rs2Bank.withdrawX(anyString(), anyInt(), anyBoolean()), never());
    }

    @Test public void notedRowIsCheckedAndWithdrawnByItsId() {
        bank.when(() -> Rs2Bank.hasBankItem(ItemID.Cert.SHARK, 100)).thenReturn(true);
        loader(ItemID.Cert.SHARK, "Shark").loadInventory(false);
        bank.verify(() -> Rs2Bank.setWithdrawAs(true));
        bank.verify(() -> Rs2Bank.withdrawX(ItemID.Cert.SHARK, 100));
        noNameLookup();
    }

    @Test public void missingSupplyDoesNotTakeSimilarlyNamedItem() {
        bank.when(() -> Rs2Bank.hasBankItem("bones", 100, false)).thenReturn(true);
        List<String> missing = new ArrayList<>();
        assertFalse(loader(ItemID.Cert.BONES, "Bones").withMissingSupplyHandler(missing::add).loadInventory(false));
        assertEquals(Collections.singletonList("Bones"), missing);
        bank.verify(() -> Rs2Bank.withdrawX(anyInt(), anyInt()), never());
        noNameLookup();
    }

    @Test public void partlyStockedNotedRowWithdrawsOnlyRemainder() {
        hasThirtyBones();
        bank.when(() -> Rs2Bank.hasBankItem(ItemID.Cert.BONES, 70)).thenReturn(true);
        loader(ItemID.Cert.BONES, "Bones").loadInventory(false);
        bank.verify(() -> Rs2Bank.withdrawX(ItemID.Cert.BONES, 70));
        noNameLookup();
    }

    @Test public void partialSupplyCanContinueWhenBankIsEmpty() {
        hasThirtyBones();
        List<String> missing = new ArrayList<>();
        loader(ItemID.Cert.BONES, "Bones").withMissingSupplyHandler(missing::add).loadInventory(false);
        assertTrue(missing.isEmpty());
        bank.verify(() -> Rs2Bank.withdrawX(anyInt(), anyInt()), never());
        noNameLookup();
    }
}
