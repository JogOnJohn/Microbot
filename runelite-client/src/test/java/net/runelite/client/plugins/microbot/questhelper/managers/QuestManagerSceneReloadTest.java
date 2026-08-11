package net.runelite.client.plugins.microbot.questhelper.managers;

import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.questhelper.panel.QuestHelperPanel;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestManagerSceneReloadTest
{
	@Test
	public void refreshReinitializesStepAndReappliesPanelHighlight() throws Exception
	{
		QuestManager manager = new QuestManager();
		Client client = mock(Client.class);
		QuestHelperPanel panel = mock(QuestHelperPanel.class);
		QuestHelper selectedQuest = mock(QuestHelper.class);
		QuestStep currentStep = mock(QuestStep.class);
		QuestStep activeStep = mock(QuestStep.class);
		panel.questActive = true;
		manager.client = client;
		manager.startUp(panel);
		setField(manager, "selectedQuest", selectedQuest);
		setField(manager, "lastStep", activeStep);
		when(selectedQuest.getCurrentStep()).thenReturn(currentStep);
		when(currentStep.getActiveStep()).thenReturn(activeStep);

		manager.refreshAfterSceneLoad();
		manager.updateQuestState();

		verify(activeStep).refreshAfterSceneLoad();
		verify(panel).updateHighlight(client, activeStep);
	}

	private static void setField(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
