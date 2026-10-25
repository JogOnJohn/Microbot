package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;

final class WalkerSceneClickSafety {
    private static final int[] OVERLAYS = {
            InterfaceID.ToplevelOsrsStretch.SIDE_MENU,
            InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER,
            InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER,
            InterfaceID.ToplevelPreEoc.SIDE_CONTAINER,
            InterfaceID.ToplevelPreEoc.CHAT_CONTAINER,
            InterfaceID.ToplevelPreEoc.MAP_CONTAINER
    };

    private WalkerSceneClickSafety() { }

    static Rectangle clickArea(Point point) {
        return point == null ? null : new Rectangle(point.getX() - 4, point.getY() - 4, 8, 8);
    }

    static boolean isSafe(Point point) {
        if (point == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            if (client == null) return false;
            Rectangle viewport = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
                    client.getViewportWidth(), client.getViewportHeight());
            List<Rectangle> overlays = new ArrayList<>();
            for (int id : OVERLAYS) {
                Widget widget = client.getWidget(id);
                if (widget == null || widget.isHidden()) continue;
                Rectangle bounds = widget.getBounds();
                if (bounds == null) return false;
                overlays.add(bounds);
            }
            return isSafePoint(point, viewport, overlays);
        }).orElse(false);
    }

    static boolean isSafePoint(Point point, Rectangle viewport, List<Rectangle> overlays) {
        Rectangle area = clickArea(point);
        if (area == null || viewport == null || !viewport.contains(area)) return false;
        if (overlays != null) {
            for (Rectangle bounds : overlays) {
                if (bounds != null && !bounds.isEmpty() && bounds.intersects(area)) return false;
            }
        }
        return true;
    }
}
