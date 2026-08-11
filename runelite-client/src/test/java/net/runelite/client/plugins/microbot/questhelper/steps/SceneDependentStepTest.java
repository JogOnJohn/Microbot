package net.runelite.client.plugins.microbot.questhelper.steps;

import net.runelite.api.Client;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SceneDependentStepTest
{
	@Test
	public void objectSpawnIsIgnoredWhileLocalPlayerIsUnavailable()
	{
		Client client = mock(Client.class);
		when(client.getLocalPlayer()).thenReturn(null);
		TestObjectStep step = new TestObjectStep(mock(QuestHelper.class));
		step.setClient(client);

		step.handle(mock(TileObject.class));

		assertTrue(step.getObjects().isEmpty());
	}

	@Test
	public void npcScanIsSafeWhileSceneIsUnavailable()
	{
		Client client = mock(Client.class);
		when(client.getTopLevelWorldView()).thenReturn(null);
		TestNpcStep step = new TestNpcStep(mock(QuestHelper.class));
		step.setClient(client);

		step.scanForNpcs();

		assertTrue(step.npcs.isEmpty());
	}

	private static class TestObjectStep extends ObjectStep
	{
		private TestObjectStep(QuestHelper questHelper)
		{
			super(questHelper, 1, "test");
		}

		private void setClient(Client client)
		{
			this.client = client;
		}

		private void handle(TileObject object)
		{
			handleObjects(object);
		}
	}

	private static class TestNpcStep extends NpcStep
	{
		private TestNpcStep(QuestHelper questHelper)
		{
			super(questHelper, 1, "test");
		}

		private void setClient(Client client)
		{
			this.client = client;
		}
	}
}
