package net.runelite.client.plugins.microbot.shortestpath;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Optional;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/** Draws a Quest Helper-style blue cue around the teleport control selected by the route. */
public final class PreferredTeleportOverlay extends Overlay
{
	private static final Color BLUE_FILL = new Color(0, 110, 255, 65);
	private static final Color BLUE_BORDER = new Color(35, 145, 255);
	private static final BasicStroke BORDER_STROKE = new BasicStroke(2F);

	private final ShortestPathConfig config;
	private final PreferredTeleportAssistant assistant;

	@Inject
	PreferredTeleportOverlay(ShortestPathConfig config, PreferredTeleportAssistant assistant)
	{
		this.config = config;
		this.assistant = assistant;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(OverlayPriority.HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightPreferredTeleport())
		{
			return null;
		}

		Optional<PreferredTeleportAssistant.HighlightTarget> resolved = assistant.resolveCurrentTarget();
		if (resolved.isEmpty())
		{
			return null;
		}

		PreferredTeleportAssistant.HighlightTarget target = resolved.get();
		Rectangle bounds = target.getWidget().getBounds();
		Rectangle rootBounds = target.getRoot() == null ? null : target.getRoot().getBounds();
		if (PreferredTeleportAssistant.hasArea(bounds)
			&& (rootBounds == null || !PreferredTeleportAssistant.hasArea(rootBounds) || rootBounds.intersects(bounds)))
		{
			drawBlueBox(graphics, bounds);
			return null;
		}

		// Portal Nexus rows can be outside the current scroll viewport. In that case, outline the
		// interface and show the exact destination/hotkey instead of drawing an off-screen box.
		if (PreferredTeleportAssistant.hasArea(rootBounds))
		{
			drawBlueBox(graphics, rootBounds);
			String suffix = target.getShortcut() == null ? "" : " [" + target.getShortcut() + "]";
			String text = "Preferred: " + target.getLabel() + suffix;
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, rootBounds.x + 9, rootBounds.y + 17);
			graphics.setColor(BLUE_BORDER);
			graphics.drawString(text, rootBounds.x + 8, rootBounds.y + 16);
		}
		return null;
	}

	private static void drawBlueBox(Graphics2D graphics, Rectangle bounds)
	{
		java.awt.Stroke previousStroke = graphics.getStroke();
		graphics.setColor(BLUE_FILL);
		graphics.fill(bounds);
		graphics.setColor(BLUE_BORDER);
		graphics.setStroke(BORDER_STROKE);
		graphics.draw(bounds);
		graphics.setStroke(previousStroke);
	}
}
