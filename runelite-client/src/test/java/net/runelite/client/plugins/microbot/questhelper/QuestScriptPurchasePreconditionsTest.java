package net.runelite.client.plugins.microbot.questhelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class QuestScriptPurchasePreconditionsTest {
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Bank> bank;
    private MockedStatic<Rs2GrandExchange> ge;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Global> waits;
    private ItemComposition definition;
    private QuestScript script;

    @Before
    public void setUp() {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        definition = mock(ItemComposition.class);
        when(definition.isTradeable()).thenReturn(true);
        when(client.getItemDefinition(anyInt())).thenReturn(definition);
        when(client.getGrandExchangeOffers()).thenReturn(new GrandExchangeOffer[0]);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(inv ->
                Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(thread);
        bank = Mockito.mockStatic(Rs2Bank.class);
        ge = Mockito.mockStatic(Rs2GrandExchange.class);
        inventory = Mockito.mockStatic(Rs2Inventory.class);
        waits = Mockito.mockStatic(Global.class);
        bank.when(Rs2Bank::isOpen).thenReturn(true);
        bank.when(Rs2Bank::setWithdrawAsItem).thenReturn(true);
        ge.when(() -> Rs2GrandExchange.getPrice(anyInt())).thenReturn(100);
        script = mock(QuestScript.class, CALLS_REAL_METHODS);
    }

    @After
    public void tearDown() {
        waits.close(); inventory.close(); ge.close(); bank.close(); microbot.close();
    }

    private ItemRequirement item(int id) {
        ItemRequirement item = mock(ItemRequirement.class);
        when(item.getId()).thenReturn(id);
        when(item.getAllIds()).thenReturn(Collections.singletonList(id));
        when(item.getQuantity()).thenReturn(1);
        when(item.getName()).thenReturn("Fixture item");
        return item;
    }

    private void acquire(List<ItemRequirement> items) throws Exception {
        Method method = QuestScript.class.getDeclaredMethod("acquireMissingTradableItems", List.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(script, items));
    }

    private void assertNoPurchase() {
        ge.verify(() -> Rs2GrandExchange.buyItem(anyString(), anyInt(), anyInt()), never());
    }

    @Test
    public void affordabilityCoversAllMissingItemsBeforeTrimmingToOneSlot() throws Exception {
        List<ItemRequirement> items = new ArrayList<>();
        for (int id = 1000; id < 1009; id++) items.add(item(id));
        bank.when(() -> Rs2Bank.count(ItemID.COINS_995)).thenReturn(120);
        ge.when(Rs2GrandExchange::getAvailableSlotsCount).thenReturn(1);
        acquire(items);
        assertTrue(Microbot.status.contains("1,080"));
        ge.verify(Rs2GrandExchange::getAvailableSlotsCount, never());
        assertNoPurchase();
    }

    @Test
    public void coinsAreNotPricedOrBought() throws Exception {
        acquire(Collections.singletonList(item(ItemID.COINS_995)));
        assertTrue(Microbot.status.contains("Not enough coins"));
        ge.verify(() -> Rs2GrandExchange.getPrice(anyInt()), never());
        assertNoPurchase();
    }

    @Test
    public void unknownWorldTypeBlocksMembersOnlyPurchases() throws Exception {
        when(definition.isMembers()).thenReturn(true);
        acquire(Collections.singletonList(item(1000)));
        assertTrue(Microbot.status.contains("members-only"));
        assertNoPurchase();
    }

    @Test
    public void fullInventoryStopsBeforeBuyingMoreItems() throws Exception {
        bank.when(() -> Rs2Bank.count(1000)).thenReturn(1);
        inventory.when(Rs2Inventory::isFull).thenReturn(true);
        acquire(Collections.singletonList(item(1000)));
        assertTrue(Microbot.status.contains("Inventory is full"));
        assertNoPurchase();
    }

    @Test
    public void uncollectableFinishedOffersStopInsteadOfRetryingPurchases() throws Exception {
        bank.when(() -> Rs2Bank.count(ItemID.COINS_995)).thenReturn(1000);
        ge.when(Rs2GrandExchange::getAvailableSlotsCount).thenReturn(0);
        ge.when(Rs2GrandExchange::hasBoughtOffer).thenReturn(true);
        ge.when(Rs2GrandExchange::isOpen).thenReturn(true);
        acquire(Collections.singletonList(item(1000)));
        assertTrue(Microbot.status.contains("Could not collect finished"));
        ge.verify(Rs2GrandExchange::collectAllToBank);
        assertNoPurchase();
    }
}
