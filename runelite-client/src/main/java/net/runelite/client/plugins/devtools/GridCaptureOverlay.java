package net.runelite.client.plugins.devtools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Collection;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Renders the tile rectangle selected via the devtools "Grid Capture" mode.
 */
class GridCaptureOverlay extends Overlay
{
	private static final Color CORNER_COLOR = Color.CYAN;
	private static final Color BORDER_COLOR = new Color(0, 255, 255, 100);
	private static final int MAX_BORDER_TILES = 400;

	private final Client client;
	private final DevToolsPlugin plugin;

	@Inject
	GridCaptureOverlay(Client client, DevToolsPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.getGridCapture().isActive())
		{
			return null;
		}

		WorldPoint c1 = plugin.getGridCorner1();
		WorldPoint c2 = plugin.getGridCorner2();

		graphics.setStroke(new BasicStroke(2));

		if (c1 != null)
		{
			drawTile(graphics, c1, CORNER_COLOR);
		}
		if (c2 != null)
		{
			drawTile(graphics, c2, CORNER_COLOR);
		}

		if (c1 != null && c2 != null)
		{
			int minX = Math.min(c1.getX(), c2.getX());
			int maxX = Math.max(c1.getX(), c2.getX());
			int minY = Math.min(c1.getY(), c2.getY());
			int maxY = Math.max(c1.getY(), c2.getY());
			int plane = c1.getPlane();

			int drawn = 0;
			for (int x = minX; x <= maxX && drawn < MAX_BORDER_TILES; x++)
			{
				for (int y = minY; y <= maxY && drawn < MAX_BORDER_TILES; y++)
				{
					if (x != minX && x != maxX && y != minY && y != maxY)
					{
						continue;
					}
					drawTile(graphics, new WorldPoint(x, y, plane), BORDER_COLOR);
					drawn++;
				}
			}
		}

		return null;
	}

	private void drawTile(Graphics2D graphics, WorldPoint point, Color color)
	{
		if (point.getPlane() != client.getPlane())
		{
			return;
		}

		// grid corners are stored instance-translated; map back into the local scene
		Collection<WorldPoint> localInstances = WorldPoint.toLocalInstance(client, point);
		for (WorldPoint wp : localInstances)
		{
			LocalPoint lp = LocalPoint.fromWorld(client, wp);
			if (lp == null)
			{
				continue;
			}

			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				graphics.setColor(color);
				graphics.drawPolygon(poly);
			}
		}
	}
}
