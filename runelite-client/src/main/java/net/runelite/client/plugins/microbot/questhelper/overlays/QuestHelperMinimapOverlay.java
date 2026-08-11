package net.runelite.client.plugins.microbot.questhelper.overlays;

import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;

public class QuestHelperMinimapOverlay extends Overlay
{
	private final QuestHelperPlugin plugin;

	@Inject
	public QuestHelperMinimapOverlay(QuestHelperPlugin plugin)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(OverlayPriority.HIGH);
		this.plugin = plugin;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		QuestHelper quest = plugin.getSelectedQuest();
		if (quest == null)
		{
			return null;
		}

		QuestStep currentStep = quest.getCurrentStep();
		if (currentStep == null)
		{
			return null;
		}

		QuestStep activeStep = currentStep.getActiveStep();
		if (activeStep != null)
		{
			activeStep.makeDirectionOverlayHint(graphics, plugin);
		}
		return null;
	}
}
