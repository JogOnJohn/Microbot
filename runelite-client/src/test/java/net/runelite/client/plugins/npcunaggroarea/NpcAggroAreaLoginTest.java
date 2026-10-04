package net.runelite.client.plugins.npcunaggroarea;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NpcAggroAreaLoginTest
{
	@Mock
	private Client client;

	@Mock
	private ConfigManager configManager;

	@InjectMocks
	private NpcAggroAreaPlugin plugin;

	@Test
	public void defersLoginUntilPlayerExistsAndRestoresSavedCentersOnce()
	{
		changeState(GameState.LOGGING_IN);
		changeState(GameState.LOGGED_IN);
		plugin.onGameTick(new GameTick());
		verifyNoInteractions(configManager);

		WorldPoint location = new WorldPoint(2643, 3670, 0);
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(location);
		when(configManager.getRSProfileConfiguration(NpcAggroAreaConfig.CONFIG_GROUP,
			NpcAggroAreaConfig.CONFIG_LOCATION, WorldPoint.class)).thenReturn(location);
		when(configManager.getRSProfileConfiguration(NpcAggroAreaConfig.CONFIG_GROUP,
			NpcAggroAreaConfig.CONFIG_CENTER1, WorldPoint.class)).thenReturn(location);
		when(configManager.getRSProfileConfiguration(NpcAggroAreaConfig.CONFIG_GROUP,
			NpcAggroAreaConfig.CONFIG_CENTER2, WorldPoint.class)).thenReturn(location);

		plugin.onGameTick(new GameTick());
		plugin.onGameTick(new GameTick());
		assertEquals(location, plugin.getSafeCenters()[0]);
		assertEquals(location, plugin.getSafeCenters()[1]);
		verify(configManager, times(1)).unsetRSProfileConfiguration(
			NpcAggroAreaConfig.CONFIG_GROUP, NpcAggroAreaConfig.CONFIG_LOCATION);
	}

	@Test
	public void abortedLoginDoesNotConsumeSavedConfiguration()
	{
		changeState(GameState.LOGGING_IN);
		changeState(GameState.LOGGED_IN);
		changeState(GameState.LOGIN_SCREEN);
		plugin.onGameTick(new GameTick());
		verifyNoInteractions(configManager);
		assertNull(plugin.getSafeCenters()[0]);
		assertNull(plugin.getSafeCenters()[1]);
	}

	private void changeState(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		plugin.onGameStateChanged(event);
	}
}
