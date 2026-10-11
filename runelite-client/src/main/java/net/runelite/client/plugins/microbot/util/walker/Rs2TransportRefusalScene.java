package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

final class Rs2TransportRefusalScene {
    private Rs2TransportRefusalScene() { }

    static boolean isLoaded(WorldPoint origin) {
        if (origin == null || Microbot.getClientThread() == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            WorldView view = client == null ? null : client.getTopLevelWorldView();
            if (view == null || view.isInstance() || view.getPlane() != origin.getPlane()) return false;
            LocalPoint local = LocalPoint.fromWorld(view, origin);
            if (local == null || view.getCollisionMaps() == null
                    || origin.getPlane() >= view.getCollisionMaps().length
                    || view.getCollisionMaps()[origin.getPlane()] == null) return false;
            int[][] flags = view.getCollisionMaps()[origin.getPlane()].getFlags();
            int x = local.getSceneX(), y = local.getSceneY();
            // Objects may occupy a blocked tile; only reject an unloaded scene tile here.
            return flags != null && x >= 0 && x < flags.length && flags[x] != null
                    && y >= 0 && y < flags[x].length && (flags[x][y] & 0x1000000) == 0;
        }).orElse(false);
    }
}
