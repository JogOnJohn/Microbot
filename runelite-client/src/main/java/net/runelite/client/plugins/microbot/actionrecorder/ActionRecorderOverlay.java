package net.runelite.client.plugins.microbot.actionrecorder;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class ActionRecorderOverlay extends OverlayPanel
{
	private final ActionRecorderPlugin plugin;

	@Inject
	private ActionRecorderOverlay(ActionRecorderPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ActionRecorderPlugin.RecorderStatus status = plugin.getRecorderStatus();
		if (!status.isRecording() && !status.isStopping())
		{
			return null;
		}

		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Action Recorder")
			.right(status.isStopping() ? "Finishing" : "Recording")
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Observations")
			.right(Long.toString(status.getAcceptedObservationCount()))
			.build());
		if (status.getDroppedObservationCount() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Dropped")
				.right(Long.toString(status.getDroppedObservationCount()))
				.build());
		}

		return super.render(graphics);
	}
}
