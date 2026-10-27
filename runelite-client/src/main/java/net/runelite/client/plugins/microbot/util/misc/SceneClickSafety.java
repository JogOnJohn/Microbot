package net.runelite.client.plugins.microbot.util.misc;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

/** Fresh scene-click geometry; unavailable snapshots never authorize input. */
public final class SceneClickSafety {
    private static final int[] OVERLAYS = {
        InterfaceID.ToplevelOsrsStretch.SIDE_MENU,
        InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER,
        InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER,
        InterfaceID.ToplevelPreEoc.SIDE_CONTAINER,
        InterfaceID.ToplevelPreEoc.CHAT_CONTAINER,
        InterfaceID.ToplevelPreEoc.MAP_CONTAINER
    };
    private static final Map<Integer, Long> coveredWarnings = new LinkedHashMap<Integer, Long>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Long> entry) {
            return size() > 64;
        }
    };

    private SceneClickSafety() { }

    public static boolean appliesTo(NewMenuEntry entry) {
        return entry != null && (entry.getGameObject() != null || entry.getActor() != null);
    }

    public static Rectangle forEntry(NewMenuEntry entry, Point preferred) {
        if (entry == null) return null;
        return entry.getGameObject() != null ? forObject(entry.getGameObject(), preferred)
            : forActor(entry.getActor(), preferred);
    }

    public static Rectangle forObject(TileObject object, Point preferred) {
        return object == null ? null : snapshot(object::getClickbox, preferred);
    }

    public static Rectangle forActor(Actor actor, Point preferred) {
        if (actor == null) return null;
        return snapshot(() -> {
            LocalPoint local = actor.getLocalLocation();
            WorldPoint world = actor.getWorldLocation();
            Model model = actor.getModel();
            Client client = Microbot.getClient();
            if (local == null || world == null || model == null || client.getTopLevelWorldView() == null) return null;
            return Perspective.getClickbox(client, client.getTopLevelWorldView(), model,
                actor.getCurrentOrientation(), local.getX(), local.getY(),
                Perspective.getTileHeight(client, local, world.getPlane()));
        }, preferred);
    }

    public static boolean contains(Rectangle area, Point point) {
        return area != null && point != null && area.contains(point.getX(), point.getY());
    }

    public static boolean permits(NewMenuEntry entry, Point point) {
        return contains(forEntry(entry, point), point);
    }

    private static Rectangle snapshot(Supplier<Shape> shapeSupplier, Point preferred) {
        if (Microbot.getClient() == null || Microbot.getClientThread() == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            Shape shape = shapeSupplier.get();
            if (shape == null) return null;
            Rectangle viewport = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
                client.getViewportWidth(), client.getViewportHeight());
            List<Rectangle> overlays = new ArrayList<>();
            for (int id : OVERLAYS) {
                Widget widget = client.getWidget(id);
                if (widget == null || widget.isHidden()) continue;
                Rectangle bounds = widget.getBounds();
                if (bounds == null) return null;
                overlays.add(new Rectangle(bounds));
            }
            return clearArea(shape.getBounds(), viewport, overlays, preferred);
        }).orElse(null);
    }

    static Rectangle clearArea(Rectangle clickbox, Rectangle viewport,
                               List<Rectangle> overlays, Point preferred) {
        if (clickbox == null || viewport == null || overlays == null || clickbox.isEmpty() || viewport.isEmpty()) return null;
        Rectangle visible = clickbox.intersection(viewport);
        if (visible.isEmpty()) return null;
        List<Rectangle> clear = new ArrayList<>();
        clear.add(visible);
        for (Rectangle bounds : overlays) {
            if (bounds == null) return null;
            if (bounds.isEmpty()) continue;
            Rectangle cover = new Rectangle(bounds);
            cover.grow(4, 4);
            List<Rectangle> next = new ArrayList<>();
            for (Rectangle area : clear) {
                if (!area.intersects(cover)) {
                    next.add(area);
                    continue;
                }
                add(next, new Rectangle(area.x, area.y, cover.x - area.x, area.height));
                add(next, new Rectangle(cover.x + cover.width, area.y,
                    area.x + area.width - cover.x - cover.width, area.height));
                add(next, new Rectangle(area.x, area.y, area.width, cover.y - area.y));
                add(next, new Rectangle(area.x, cover.y + cover.height, area.width,
                    area.y + area.height - cover.y - cover.height));
            }
            clear = next;
        }
        Rectangle largest = null;
        for (Rectangle area : clear) {
            // (0,0) and (1,1) are legacy fallback/resting-point sentinels.
            if (Rs2UiHelper.isFallbackRectangle(area)) {
                int right = area.x + area.width, bottom = area.y + area.height;
                area = new Rectangle(2, 2, right - 2, bottom - 2);
                if (area.isEmpty()) continue;
            }
            if (contains(area, preferred)) return area;
            if (largest == null || (long) area.width * area.height > (long) largest.width * largest.height) largest = area;
        }
        return largest;
    }

    private static void add(List<Rectangle> areas, Rectangle area) {
        if (!area.isEmpty() && !areas.contains(area)) areas.add(area);
    }

    public static synchronized boolean shouldWarnCovered(int objectId, long now) {
        Long previous = coveredWarnings.get(objectId);
        if (previous != null && now >= previous && now - previous < 10_000) return false;
        coveredWarnings.put(objectId, now);
        return true;
    }
}
