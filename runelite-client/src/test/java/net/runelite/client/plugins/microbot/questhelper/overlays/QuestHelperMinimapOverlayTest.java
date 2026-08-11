package net.runelite.client.plugins.microbot.questhelper.overlays;

import java.awt.Graphics2D;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestHelperMinimapOverlayTest
{
	@Test
	public void currentStepIsReadOnceDuringQuestTransition()
	{
		QuestHelperPlugin plugin = mock(QuestHelperPlugin.class);
		QuestHelper quest = mock(QuestHelper.class);
		QuestStep currentStep = mock(QuestStep.class);
		QuestStep activeStep = mock(QuestStep.class);
		Graphics2D graphics = mock(Graphics2D.class);
		when(plugin.getSelectedQuest()).thenReturn(quest);
		when(quest.getCurrentStep()).thenReturn(currentStep, (QuestStep) null);
		when(currentStep.getActiveStep()).thenReturn(activeStep);

		new QuestHelperMinimapOverlay(plugin).render(graphics);

		verify(quest, times(1)).getCurrentStep();
		verify(activeStep).makeDirectionOverlayHint(graphics, plugin);
	}
}
