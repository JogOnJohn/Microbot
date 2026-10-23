package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.GameState;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Collision evidence, not a failed mouse dispatch, determines whether a house is blocked. */
final class PohFacilityAccess {
    enum Access { AVAILABLE, MISSING, UNREACHABLE, OBSTRUCTED, PENDING, UNKNOWN }

    static Access inspect(PohTeleport teleport) {
        if (Microbot.getClientThread() == null) return Access.PENDING;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> inspectOnClient(teleport)).orElse(Access.PENDING);
    }

    private static Access inspectOnClient(PohTeleport teleport) {
        if (!World330HostedHouse.ADVERTISED_HOUSE.isInHostedHouse()) return Access.PENDING;
        int[] ids = facilityIds(teleport);
        if (ids.length == 0) return Access.UNKNOWN;
        boolean collisionReady = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            if (client == null || client.getGameState() != GameState.LOGGED_IN
                    || client.getLocalPlayer() == null) return false;
            var view = client.getLocalPlayer().getWorldView();
            return view != null && view.getCollisionMaps() != null
                    && view.getPlane() < view.getCollisionMaps().length
                    && view.getCollisionMaps()[view.getPlane()] != null;
        }).orElse(false);
        if (!collisionReady) return Access.PENDING;
        List<Rs2TileObjectModel> objects = Microbot.getRs2TileObjectCache().query().fromWorldView().withIds(ids).toList();
        if (objects.size() > 16) return Access.UNKNOWN;
        boolean moving = Rs2Player.isMoving() || Rs2Player.isAnimating();
        // An unrelated door cannot obstruct a facility that is not in this scene.
        // Avoid querying every door composition in a large house for an absent object.
        if (objects.isEmpty()) return classify(true, 0, false, moving, false);
        boolean reachable = objects.stream().anyMatch(Rs2TileObjectModel::isReachable);
        // A normal closed door may explain disconnection. Never permanently label that a trap.
        // One client-thread snapshot avoids thousands of per-object name dispatches.
        // The cap is conservative: incomplete obstruction evidence must not blacklist.
        if (reachable) return Access.AVAILABLE;
        if (moving) return Access.PENDING;
        long stopAt = System.nanoTime() + 10_000_000L;
        Access obstruction = inspectDoors(Microbot.getRs2TileObjectCache().query().fromWorldView().toList(),
                () -> System.nanoTime() >= stopAt);
        if (obstruction == Access.UNKNOWN) return Access.UNKNOWN;
        return classify(collisionReady, objects.size(), reachable,
                moving, obstruction == Access.OBSTRUCTED);
    }

    static Access inspectDoors(List<Rs2TileObjectModel> objects, BooleanSupplier expired) {
        int inspected = 0;
        for (Rs2TileObjectModel object : objects) {
            if (inspected++ >= 1024 || expired.getAsBoolean()) return Access.UNKNOWN;
            var composition = object.getObjectComposition();
            if (composition == null || composition.getName() == null) return Access.UNKNOWN;
            if (composition.getName().toLowerCase(java.util.Locale.ROOT).contains("door")
                    && composition.getActions() != null
                    && Arrays.stream(composition.getActions()).anyMatch("Open"::equalsIgnoreCase))
                return Access.OBSTRUCTED;
        }
        return Access.UNREACHABLE;
    }

    static Access classify(boolean collisionReady, int candidates, boolean reachable,
            boolean moving, boolean closedDoor) {
        if (!collisionReady) return Access.PENDING;
        if (reachable) return Access.AVAILABLE;
        if (moving) return Access.PENDING;
        if (candidates == 0) return Access.MISSING;
        if (closedDoor) return Access.OBSTRUCTED;
        return Access.UNREACHABLE;
    }

    static int[] facilityIds(PohTeleport teleport) {
        if (teleport instanceof MountedXerics) return ints(MountedXerics.IDS);
        if (teleport instanceof JewelleryBox) return ints(JewelleryBoxType.getJewelleryBoxIds());
        if (teleport instanceof NexusPortal) return ints(NexusPortal.PORTAL_IDS);
        if (teleport instanceof MountedDigsite) return ints(MountedDigsite.IDS);
        if (teleport instanceof MountedGlory) return new int[]{ObjectID.POH_TROPHY_AMULETOFGLORY_4};
        if (teleport instanceof MountedMythical) return new int[]{MountedMythical.MYTHS_GUILD.getObjectId()};
        if (teleport instanceof PohPortal) return ints(((PohPortal) teleport).getObjectIds());
        return new int[0];
    }

    private static int[] ints(Integer[] ids) {
        return Arrays.stream(ids).mapToInt(Integer::intValue).toArray();
    }
}
