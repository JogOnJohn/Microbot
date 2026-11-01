package net.runelite.client.plugins.microbot.shortestpath;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.util.walker.TransportEdgeAdapter;
import net.runelite.client.plugins.microbot.util.walker.TransportEdgeBanking;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TransportEdgeBankingCaptureTest
{
	@Test
	public void liveQuantitiesAreCapturedOnlyInsideOneClientThreadCallback() throws Exception
	{
		Client client = mock(Client.class);
		ClientThread thread = mock(ClientThread.class);
		boolean[] inCallback = {false};
		ItemContainer inventory = container(new Item(100, 2), new Item(-1, 0));
		ItemContainer equipment = container();
		when(client.getGameState()).thenAnswer(call -> { assertTrue(inCallback[0]); return GameState.LOGGED_IN; });
		when(client.getItemContainer(InventoryID.INV)).thenAnswer(call -> { assertTrue(inCallback[0]); return inventory; });
		when(client.getItemContainer(InventoryID.WORN)).thenAnswer(call -> { assertTrue(inCallback[0]); return equipment; });
		when(thread.runOnClientThreadOptional(any())).thenAnswer(call -> {
			inCallback[0] = true;
			try { return Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()); }
			finally { inCallback[0] = false; }
		});
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
		{
			microbot.when(Microbot::getClient).thenReturn(client);
			microbot.when(Microbot::getClientThread).thenReturn(thread);
			Rs2TransportLoadout result = TransportEdgeBanking.getMissingLoadout(List.of(edge("100=2")));
			assertTrue(result.isSatisfiable());
			assertTrue(result.isEmpty());
			verify(thread, times(1)).runOnClientThreadOptional(any());
			verify(client).getGameState();
			verify(client).getItemContainer(InventoryID.INV);
			verify(client).getItemContainer(InventoryID.WORN);
			verifyNoMoreInteractions(client);
			bank.verify(Rs2Bank::hasBankMirrorSnapshot);
			bank.verifyNoMoreInteractions();
		}
	}

	@Test
	public void missingInventoryOrEquipmentCannotMasqueradeAsObservedAbsence() throws Exception
	{
		for (boolean missingInventory : List.of(true, false))
		{
			Client client = mock(Client.class);
			ClientThread thread = executingThread();
			ItemContainer present = container();
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			when(client.getItemContainer(InventoryID.INV)).thenReturn(missingInventory ? null : present);
			when(client.getItemContainer(InventoryID.WORN)).thenReturn(missingInventory ? present : null);
			try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
				MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
			{
				microbot.when(Microbot::getClient).thenReturn(client);
				microbot.when(Microbot::getClientThread).thenReturn(thread);
				Rs2TransportEdge absence = edge("100=0");
				assertFalse(TransportEdgeBanking.getMissingLoadout(List.of(absence)).isSatisfiable());
				assertEquals(List.of(absence), TransportEdgeBanking.getMissingEdges(List.of(absence)));
				bank.verifyNoInteractions();
			}
		}
	}

	@Test
	public void observedEmptyContainersAreKnownButNullItemArraysAreNot() throws Exception
	{
		Client client = mock(Client.class);
		ClientThread thread = executingThread();
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ItemContainer inventory = container();
		ItemContainer equipment = container();
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
		{
			microbot.when(Microbot::getClient).thenReturn(client);
			microbot.when(Microbot::getClientThread).thenReturn(thread);
			assertTrue(TransportEdgeBanking.getMissingLoadout(List.of(edge("100=0"))).isSatisfiable());
			when(inventory.getItems()).thenReturn(null);
			assertFalse(TransportEdgeBanking.getMissingLoadout(List.of(edge("100=0"))).isSatisfiable());
		}
	}

	@Test
	public void timeoutCannotReturnAReadyLoadoutFromUnobservedEmptyContainers() throws Exception
	{
		Client client = mock(Client.class);
		ClientThread thread = mock(ClientThread.class);
		when(thread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
		{
			microbot.when(Microbot::getClient).thenReturn(client);
			microbot.when(Microbot::getClientThread).thenReturn(thread);
			Rs2TransportEdge absence = edge("100=0");
			assertFalse(TransportEdgeBanking.getMissingLoadout(List.of(absence)).isSatisfiable());
			assertEquals(List.of(absence), TransportEdgeBanking.getMissingEdges(List.of(absence)));
			verifyNoInteractions(client);
			bank.verifyNoInteractions();
		}
	}

	@Test
	public void loggedOutCaptureFailsBeforeReadingContainersOrBank() throws Exception
	{
		Client client = mock(Client.class);
		ClientThread thread = executingThread();
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
		{
			microbot.when(Microbot::getClient).thenReturn(client);
			microbot.when(Microbot::getClientThread).thenReturn(thread);
			assertFalse(TransportEdgeBanking.getMissingLoadout(List.of(edge("100=0"))).isSatisfiable());
			verify(client, never()).getItemContainer(anyInt());
			bank.verifyNoInteractions();
		}
	}

	private static ItemContainer container(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		return container;
	}

	private static ClientThread executingThread() throws Exception
	{
		ClientThread thread = mock(ClientThread.class);
		when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
			Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
		return thread;
	}

	private static Rs2TransportEdge edge(String items)
	{
		return TransportEdgeAdapter.toEdge(new Transport(Map.of("Origin", "3200 3200 0",
			"Destination", "3210 3210 0", "Items", items), TransportType.TRANSPORT));
	}
}
