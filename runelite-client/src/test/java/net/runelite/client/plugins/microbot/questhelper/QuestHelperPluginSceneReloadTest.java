package net.runelite.client.plugins.microbot.questhelper;

import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestBankManager;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestManager;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestHelperPluginSceneReloadTest
{
	@Test
	public void sceneRefreshWaitsForStableLoggedInTick() throws Exception
	{
		QuestHelperPlugin plugin = new QuestHelperPlugin();
		Client client = mock(Client.class);
		QuestManager questManager = mock(QuestManager.class);
		QuestBankManager questBankManager = mock(QuestBankManager.class);
		setField(plugin, "client", client);
		setField(plugin, "questManager", questManager);
		setField(plugin, "questBankManager", questBankManager);

		GameStateChanged loading = new GameStateChanged();
		loading.setGameState(GameState.LOADING);
		plugin.onGameStateChanged(loading);

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(null);
		plugin.onGameTick(new GameTick());
		verify(questManager, never()).refreshAfterSceneLoad();

		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		when(client.getTopLevelWorldView()).thenReturn(mock(WorldView.class));
		plugin.onGameTick(new GameTick());
		plugin.onGameTick(new GameTick());

		verify(questManager, times(1)).refreshAfterSceneLoad();
		verify(questManager, times(3)).updateQuestState();
	}

	private static void setField(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
