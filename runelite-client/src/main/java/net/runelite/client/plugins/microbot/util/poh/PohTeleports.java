package net.runelite.client.plugins.microbot.util.poh;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/**
 * Contains all the functionality for your POH Teleports
 * TODO:
 * 1. Check if the Basic jewellery box & Fancy jewellery box use the same interface as
 * the ornate jewellery box.
 * 2. add fortis colosseum location in jewelleryLocationEnum
 * 3. Add configuration to allow the user to choose between teleports in wilderness or not
 */
@Slf4j
public class PohTeleports {
    // Runtime blocklist for POH teleports whose execute() FAILED (e.g. a jewellery box option the
    // game refuses that the data layer didn't know was locked). Blocked teleports are filtered out
    // of transport assembly (PohPanel) for a cooldown so the pathfinder falls back to the next
    // best route instead of re-attempting a dead option on every recalc.
    private static final Map<String, Long> failedTeleportBlockUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long FAILED_TELEPORT_BLOCK_MS = 10 * 60_000L;

    public static void blockFailedTeleport(net.runelite.client.plugins.microbot.util.poh.data.PohTeleport teleport, String reason) {
        if (teleport == null || Thread.currentThread().isInterrupted()) {
            return;
        }
        long cooldown = failedTeleportCooldownMs(PohMenuInteraction.lastResult());
        if (cooldown == 0L) return;
        failedTeleportBlockUntil.put(teleport.displayInfo(), System.currentTimeMillis() + cooldown);
        Microbot.log("[W330POH] blocking teleport for " + (cooldown / 1000) + "s: "
                + teleport.displayInfo() + " (" + reason + ")");
    }

    static long failedTeleportCooldownMs(PohMenuInteraction.Result result) {
        if (result == PohMenuInteraction.Result.CANCELLED) return 0L;
        return result == PohMenuInteraction.Result.UNAVAILABLE ? FAILED_TELEPORT_BLOCK_MS : 15_000L;
    }

    public static boolean isTeleportBlocked(net.runelite.client.plugins.microbot.util.poh.data.PohTeleport teleport) {
        if (teleport == null) {
            return false;
        }
        Long until = failedTeleportBlockUntil.get(teleport.displayInfo());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            failedTeleportBlockUntil.remove(teleport.displayInfo());
            return false;
        }
        return true;
    }

    private static final long ORNATE_POOL_REUSE_COOLDOWN_MS = 60_000L;
    private static final int ORNATE_REJUVENATION_POOL = 29241;
    private static final int FIERY_ORNATE_REJUVENATION_POOL = 49993;
    private static final int PORTAL_NEXUS = 33410;
    private static final int ORNATE_JEWELLERY_BOX = 37540;
    private static final int MOUNTED_DIGSITE = 33420;
    private static final int MOUNTED_XERICS = 33419;
    private static final Integer[] ORNATE_REJUVENATION_POOL_IDS = new Integer[]{
            ORNATE_REJUVENATION_POOL,
            FIERY_ORNATE_REJUVENATION_POOL
    };
    private static final Integer[] HOSTED_HOUSE_FACILITY_OBJECT_IDS = new Integer[]{
            ORNATE_REJUVENATION_POOL,
            FIERY_ORNATE_REJUVENATION_POOL,
            PORTAL_NEXUS,
            ORNATE_JEWELLERY_BOX,
            MOUNTED_DIGSITE,
            MOUNTED_XERICS
    };
    private static final Integer[] HOSTED_HOUSE_OBJECT_IDS = new Integer[]{
            ObjectID.POH_EXIT_PORTAL,
            ORNATE_REJUVENATION_POOL,
            FIERY_ORNATE_REJUVENATION_POOL,
            PORTAL_NEXUS,
            ORNATE_JEWELLERY_BOX,
            MOUNTED_DIGSITE,
            MOUNTED_XERICS
    };
    private static volatile long lastOrnatePoolUseMs;

    /**
     * Checks if the player is in their house
     * based on the purple portal and if the player is
     * in an instance
     *
     * @return
     */
    public static boolean isInHouse() {
        if (World330HostedHouse.ADVERTISED_HOUSE.isInHostedHouse()) return true;
        if (!Rs2Player.IsInInstance()) return false;
        // Use the tile-object cache rather than Rs2GameObject.getGameObject; the latter
        // routes through Rs2Player.getWorldLocation() as a scene anchor which returns the
        // overworld-template tile inside a POH instance and breaks the scene lookup.
        return Microbot.getRs2TileObjectCache()
                .query()
                .withId(ObjectID.POH_EXIT_PORTAL)
                .nearest() != null;
    }

    /**
     * Checks if a player is in their house
     * sends a microbot log if the player is not in their house
     *
     * @return
     */
    public static boolean checkIsInHouse() {
        if (!isInHouse()) {
            Microbot.log("You do not seem to be in a POH.");
            return false;
        }
        return true;
    }

    public static boolean useOrnateRejuvenationPoolIfPresent() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastOrnatePoolUseMs;
        if (elapsed >= 0 && elapsed < ORNATE_POOL_REUSE_COOLDOWN_MS) {
            log.info("[POH] ornate pool skipped; used {}ms ago", elapsed);
            return true;
        }
        PoolTarget target = findNearestReachablePool();
        if (target == null) {
            log.info("[POH] ornate pool not found in loaded scene at {}", Rs2Player.getWorldLocation());
            return false;
        }
        if (!observePool(target).sameHouse || Thread.currentThread().isInterrupted()) return false;
        log.info("[POH] selected ornate pool id={} sceneMin={} sceneMax={} plane={} distance={} candidates={}",
                target.id, target.min, target.max,
                target.plane, target.distance, target.candidates);
        boolean clicked = Rs2GameObject.interact(target.pool, "Drink");
        if (!clicked) {
            log.info("[POH] ornate pool click returned false");
            return false;
        }
        boolean[] observedDrink = {false};
        sleepUntil(() -> {
            PoolObservation state = observePool(target);
            observedDrink[0] = PohPoolInteraction.isDrinking(state.animation, state.distance, state.sameHouse);
            return observedDrink[0] || !state.sameHouse || Thread.currentThread().isInterrupted();
        }, 5000);
        boolean started = observedDrink[0] && !Thread.currentThread().isInterrupted();
        if (!started) {
            log.info("[POH] ornate pool drink never started player={}", Rs2Player.getWorldLocation());
            return false;
        }
        boolean[] observedFinish = {false};
        sleepUntil(() -> {
            PoolObservation state = observePool(target);
            observedFinish[0] = PohPoolInteraction.isFinished(state.animation, state.distance, state.sameHouse, state.moving);
            return observedFinish[0] || !state.sameHouse || Thread.currentThread().isInterrupted();
        }, 8000);
        boolean finished = observedFinish[0] && !Thread.currentThread().isInterrupted();
        log.info("[POH] ornate pool interaction finished={} player={}", finished, Rs2Player.getWorldLocation());
        if (finished) {
            lastOrnatePoolUseMs = System.currentTimeMillis();
        }
        return finished;
    }

    private static PoolTarget findNearestReachablePool() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.Client client = Microbot.getClient();
            if (client == null || client.getLocalPlayer() == null) return null;
            Scene scene = client.getTopLevelWorldView().getScene();
            int plane = client.getTopLevelWorldView().getPlane();
            List<GameObject> pools = new ArrayList<>();
            Set<GameObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (net.runelite.api.Tile[] row : scene.getTiles()[plane]) {
                for (net.runelite.api.Tile tile : row) {
                    if (tile == null) continue;
                    for (GameObject pool : tile.getGameObjects()) {
                        if (pool != null && Arrays.asList(ORNATE_REJUVENATION_POOL_IDS).contains(pool.getId())
                                && seen.add(pool)) pools.add(pool);
                    }
                }
            }
            GameObject pool = PohPoolInteraction.nearest(pools, client.getLocalPlayer().getLocalLocation(),
                    PohTeleports::poolReachable);
            return pool == null ? null : new PoolTarget(pool, scene, plane,
                    PohPoolInteraction.distance(pool, client.getLocalPlayer().getLocalLocation()), pools.size());
        }).orElse(null);
    }

    private static boolean poolReachable(GameObject pool) {
        Point min = pool.getSceneMinLocation(), max = pool.getSceneMaxLocation();
        return Rs2Tile.isSceneAreaReachable(min.getX(), min.getY(), max.getX(), max.getY());
    }

    static final class PoolTarget {
        final GameObject pool;
        final Scene scene;
        final Point min, max;
        final int id;
        final int plane, distance, candidates;
        PoolTarget(GameObject pool, Scene scene, int plane, int distance, int candidates) {
            this.pool = pool; this.scene = scene; this.plane = plane;
            this.id = pool.getId(); this.min = pool.getSceneMinLocation(); this.max = pool.getSceneMaxLocation();
            this.distance = distance; this.candidates = candidates;
        }
    }

    static final class PoolObservation {
        final boolean sameHouse, moving;
        final int animation, distance;
        PoolObservation(boolean sameHouse, boolean moving, int animation, int distance) {
            this.sameHouse = sameHouse; this.moving = moving;
            this.animation = animation; this.distance = distance;
        }
    }

    private static PoolObservation observePool(PoolTarget target) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> observePoolOnClient(Microbot.getClient(), target))
                .orElse(new PoolObservation(false, false, -1, 999));
    }

    static PoolObservation observePoolOnClient(net.runelite.api.Client client, PoolTarget target) {
        if (client == null || client.getLocalPlayer() == null || client.getLocalPlayer().getLocalLocation() == null
                || client.getTopLevelWorldView().getScene() != target.scene
                || client.getTopLevelWorldView().getPlane() != target.plane) {
            return new PoolObservation(false, false, -1, 999);
        }
        Point min = target.pool.getSceneMinLocation();
        net.runelite.api.Tile[][][] tiles = target.scene.getTiles();
        if (target.plane < 0 || target.plane >= tiles.length || min == null
                || min.getX() < 0 || min.getX() >= tiles[target.plane].length
                || min.getY() < 0 || min.getY() >= tiles[target.plane][min.getX()].length) {
            return new PoolObservation(false, false, -1, 999);
        }
        net.runelite.api.Tile tile = tiles[target.plane][min.getX()][min.getY()];
        boolean same = tile != null && Arrays.stream(tile.getGameObjects()).anyMatch(object -> object == target.pool);
        return new PoolObservation(same, Rs2Player.isMoving(), client.getLocalPlayer().getAnimation(),
                PohPoolInteraction.distance(target.pool, client.getLocalPlayer().getLocalLocation()));
    }

    public static boolean hasLoadedOrnateRejuvenationPool() {
        return findPohObjectAnywhere(ORNATE_REJUVENATION_POOL_IDS) != null;
    }

    /**
     * Checks if the player has a house
     *
     * @return true if a house location is found
     */
    public static boolean hasHouse() {
        return HouseLocation.getHouseLocation() != null;
    }

    public static boolean hasLoadedPohObject() {
        return findPohObjectAnywhere(HOSTED_HOUSE_OBJECT_IDS) != null;
    }

    public static boolean hasLoadedHostedHouseFacility() {
        return findPohObjectAnywhere(HOSTED_HOUSE_FACILITY_OBJECT_IDS) != null;
    }

    public static int firstLoadedPohObjectId() {
        GameObject object = findPohObjectAnywhere(HOSTED_HOUSE_OBJECT_IDS);
        return object != null ? object.getId() : -1;
    }

    public static int firstLoadedHostedHouseFacilityId() {
        GameObject object = findPohObjectAnywhere(HOSTED_HOUSE_FACILITY_OBJECT_IDS);
        return object != null ? object.getId() : -1;
    }

    /**
     * Interacts with the jewelllerybox in a players house
     * The reason we use JewelleryLocationEnum is because it contains all the data we need for
     * jewellery teleports, so there was no need to add a seperate jewellerybox enum for the locations
     * JewelleryLocationEnum is also used for teleporting with jewellery that the player is wearing
     * or has in his inventory
     * Teleport currently not added: Fortis Colosseum.
     * Requirements: Hero	12,000	Ability to teleport to the Colosseum via the ring of dueling
     *
     * @return
     */
    public static boolean useJewelleryBox(JewelleryLocationEnum jewelleryLocationEnum) {

        if (jewelleryLocationEnum == JewelleryLocationEnum.FORTIS_COLOSSEUM) {
            Microbot.log("This teleport has not been added. If you have the coordinates for this teleport please add it in the JewelleryLocationEnum.");
            return false;
        }

        if (!checkIsInHouse()) return false;

        var reachable = Microbot.getRs2TileObjectCache().query().fromWorldView()
                .withIds(Arrays.stream(JewelleryBoxType.getJewelleryBoxIds()).mapToInt(Integer::intValue).toArray())
                .nearestReachable();

        return PohMenuInteraction.select(jewelleryLocationEnum.getDestination(),
                () -> reachable != null ? reachable.click("Teleport Menu")
                        : Rs2GameObject.interact(JewelleryBoxType.getObject(), "Teleport Menu"),
                () -> new Widget[]{getJewelleryBoxInterface()});
    }

    /**
     * Checks if the jewellerybox interface is open
     *
     * @return
     */
    public static Widget getJewelleryBoxInterface() {
        return Rs2Widget.getWidget(InterfaceID.POH_JEWELLERY_BOX, 0);
    }

    /**
     * Interact with the jewellerybox widget based on the
     * JewelleryLocationEnum destination description
     *
     * @param jewelleryLocationEnum
     * @return
     */
    public static boolean interactWithJewelleryBoxWidget(JewelleryLocationEnum jewelleryLocationEnum) {
        Widget mainWidget = getJewelleryBoxInterface();

        if (mainWidget == null) return false;

        Widget widget = Rs2Widget.findWidget(jewelleryLocationEnum.getDestination().toLowerCase(), Arrays.stream(mainWidget.getStaticChildren()).collect(Collectors.toList()));

        if (widget == null) {
            Microbot.log(jewelleryLocationEnum.getDestination() + " widget not found in jewellery box");
            return false;
        }

        boolean isTeleportDisabled = widget.getText().contains("<str>");

        if (isTeleportDisabled) {
            Microbot.log(jewelleryLocationEnum.getDestination() + " teleport is not unlocked.");
            return false;
        }

        if (!Rs2Widget.clickWidget(widget)) return false;

        Rs2Player.waitForAnimation();

        return true;
    }

    /**
     * Will click on the nexus and interact with the widget
     *
     * @param nexusPortal
     * @return
     */
    public static boolean usePortalNexus(NexusPortal nexusPortal) {
        //TODO: Add config here to inform the user if the teleport is a wilderness teleport
        GameObject portal = findPohObjectAnywhere(NexusPortal.PORTAL_IDS);
        var reachable = Microbot.getRs2TileObjectCache().query().fromWorldView()
                .withIds(Arrays.stream(NexusPortal.PORTAL_IDS).mapToInt(Integer::intValue).toArray())
                .nearestReachable();
        if (getPortalNexusInterface() == null) {
            if (reachable != null) {
                reachable.click("Teleport Menu");
            } else if (portal != null) {
                Rs2GameObject.interact(portal, "Teleport Menu");
            } else {
                log.warn("Portal nexus not found");
            }
        }

        sleepUntil(() -> getPortalNexusInterface() != null);

        return interactWithPortalNexusWidget(nexusPortal);
    }

    public static Widget getPortalNexusInterface() {
        return Rs2Widget.getWidget(InterfaceID.TELENEXUS_TELEPORT, 0);
    }

    /**
     * Will interact with the portal nexus widget if it's open
     *
     * @param nexusPortal
     * @return
     */
    public static boolean interactWithPortalNexusWidget(NexusPortal nexusPortal) {
        Widget portalNexusWidget = getPortalNexusInterface();
        if (portalNexusWidget == null) return false;

        Widget widget = Rs2Widget.findWidget(nexusPortal.getText().toLowerCase(), Arrays.stream(portalNexusWidget.getStaticChildren()).collect(Collectors.toList()));

        if (widget == null) return false;

        // Regular expression to capture text between <col=ffffff> and </col>
        String regex = "<col=ffffff>(.*?)</col>";

        // Use regex to extract the letter O
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(widget.getText());

        if (!matcher.find()) return false;

        // Extract the matched text
        String shortKey = matcher.group(1);
        // The reason we use the shortkeys instead of clicking the menu is to avoid scrolling
        // Some of the teleports are not visible in the ui to click on
        // Using shortkeys should always work even if the teleport is not visible on the screen
        Rs2Keyboard.typeString(String.valueOf(shortKey));

        boolean isWildernessInterfaceOpen = sleepUntilTrue(Rs2Widget::isWildernessInterfaceOpen, 100, 1000);

        if (isWildernessInterfaceOpen) {
            Rs2Widget.enterWilderness();
        }

        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(nexusPortal.getLocation()) < 10);

        return true;
    }

    private static final List<Integer> POH_SPIRIT_RING_IDS = Rs2GameObject.getObjectIdsByName("poh_spirit_ring");
    private static final Set<Integer> FAIRY_RING_IDS = fairyRingIds();
    private static final Set<Integer> SPIRIT_TREE_IDS = spiritTreeIds();

    private static Set<Integer> fairyRingIds() {
        Set<Integer> ids = new LinkedHashSet<>(POH_SPIRIT_RING_IDS);
        ids.addAll(Rs2GameObject.getObjectIdsByName("poh_fairy_ring"));
        return ids;
    }

    private static Set<Integer> spiritTreeIds() {
        Set<Integer> ids = new LinkedHashSet<>(POH_SPIRIT_RING_IDS);
        ids.addAll(Rs2GameObject.getObjectIdsByName("poh_spirit_tree"));
        return ids;
    }

    public static GameObject getFairyRings() {
        return Rs2GameObject.getGameObject(PohTeleports::isFairyRing);
    }

    public static GameObject getSpiritTree() {
        return Rs2GameObject.getGameObject(PohTeleports::isSpiritTree);
    }

    public static boolean isFairyRing(TileObject tileObject) {
        return FAIRY_RING_IDS.contains(tileObject.getId());
    }

    public static boolean isSpiritTree(TileObject tileObject) {
        return SPIRIT_TREE_IDS.contains(tileObject.getId());
    }

    /**
     * Scans the entire loaded scene for any GameObject matching the given ids, bypassing the
     * {@link Rs2Player#getWorldLocation()} anchor that breaks every Rs2GameObject search when
     * the player is in a POH instance.
     *
     * <p>Inside a POH, {@code Rs2Player.getWorldLocation()} returns the overworld-template tile
     * (e.g. (1877, 7052, 1)), which isn't present in the actual loaded scene grid, so any
     * search routed through that anchor converts to a null LocalPoint and returns nothing
     * even when the object is plainly visible. This helper walks the raw
     * {@code Scene.getTiles()[][][]} structure directly on the client thread, which is always
     * valid regardless of instance.</p>
     */
    private static GameObject findPohObjectAnywhere(Integer[] ids) {
        java.util.Set<Integer> idSet = java.util.Arrays.stream(ids).collect(java.util.stream.Collectors.toSet());
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.Client client = Microbot.getClient();
            if (client == null) return null;
            net.runelite.api.Scene scene = client.getTopLevelWorldView().getScene();
            net.runelite.api.Tile[][][] tiles = scene.getTiles();
            for (net.runelite.api.Tile[][] plane : tiles) {
                for (net.runelite.api.Tile[] row : plane) {
                    for (net.runelite.api.Tile tile : row) {
                        if (tile == null) continue;
                        for (GameObject go : tile.getGameObjects()) {
                            if (go != null && idSet.contains(go.getId())) return go;
                        }
                    }
                }
            }
            return null;
        }).orElse(null);
    }
}
