package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.HostedHouseContext;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public enum World330HostedHouse implements PohTeleport {
    ADVERTISED_HOUSE;

    private static final int HOUSE_ADVERTISEMENT_WIDGET = 3407875;
    private static final int SORT_ARROW_WIDGET = 3407877;
    private static final int NAMES_GROUP = 52;
    private static final int NAMES_CHILD = 9;
    private static final int ENTER_GROUP = 52;
    private static final int ENTER_CHILD = 19;
    private static final int ENTER_CONTAINER_WIDGET = 3407891;
    private static final int SORT_ASCENDING_SPRITE = 1050;
    private static final int MAX_ADVERTISED_HOST_ATTEMPTS = 8;
    private static final Set<String> EXCLUDED_ADVERTISED_HOSTS = Set.of("v 3", "jaecob");
    private static final Map<String, Long> unsuitableHosts = new ConcurrentHashMap<>();
    private static final Set<String> sessionUnsuitableHosts = ConcurrentHashMap.newKeySet();
    private static volatile String currentAdvertisedHost;
    private static final HostedHouseContext houseContext = new HostedHouseContext();
    public static final WorldPoint POH_INSTANCE_ANCHOR = new WorldPoint(1877, 7052, 1);

    enum HouseTeleportAction {
        NONE,
        TABLET_DEFAULT,
        TABLET_OUTSIDE,
        SPELL_DEFAULT,
        SPELL_OUTSIDE
    }

    @Override
    public boolean execute() {
        boolean wasInHostedHouse = isInHostedHouse();
        log.info("[W330POH] execute start inHosted={} player={}",
                wasInHostedHouse, Rs2Player.getWorldLocation());
        if (!wasInHostedHouse && !enterHostedHouse()) {
            log.info("[W330POH] failed to enter hosted house player={}",
                    Rs2Player.getWorldLocation());
            return false;
        }
        if (!wasInHostedHouse && PohTeleports.shouldUseWalkerPool()) {
            boolean poolLoaded = sleepUntil(PohTeleports::hasLoadedOrnateRejuvenationPool, 3000);
            log.info("[W330POH] post-entry ornate pool loaded={} player={}",
                    poolLoaded, Rs2Player.getWorldLocation());
        }
        boolean poolUsed = PohTeleports.useOrnateRejuvenationPoolIfPresent();
        boolean inHostedHouse = isInHostedHouse();
        log.info("[W330POH] execute end poolUsed={} inHosted={} player={} facilityId={}",
                poolUsed, inHostedHouse, Rs2Player.getWorldLocation(),
                inHostedHouse ? PohTeleports.firstLoadedHostedHouseFacilityId() : -1);
        return inHostedHouse;
    }

    @Override
    public WorldPoint getDestination() {
        return getRoutingAnchor();
    }

    @Override
    public int getDuration() {
        // Includes casting outside, reaching the board and entering the advertised house.
        return 24;
    }

    @Override
    public String displayInfo() {
        return "W330 hosted house";
    }

    private boolean enterHostedHouse() {
        return enterHostedHouse(() -> false, Long.MAX_VALUE);
    }

    private boolean enterHostedHouse(BooleanSupplier cancelled, long deadline) {
        if (recoveryStopped(cancelled, deadline)) return false;
        if (!isNearHouseAdvertisement() && !teleportOutsideToAdvertisement(cancelled, deadline)) {
            return false;
        }
        if (!isAdvertisementWidgetOpen() && !openAdvertisementBoard(cancelled, deadline)) {
            return false;
        }
        if (recoveryStopped(cancelled, deadline) || !selectAdvertisedHouse(cancelled, deadline)) {
            return false;
        }
        return awaitRecovery(this::isInHostedHouse, 16000, cancelled, deadline);
    }

    public boolean switchUnsuitableHouse() {
        return switchUnsuitableHouse(() -> false, System.currentTimeMillis() + 60_000L);
    }

    public boolean switchUnsuitableHouse(BooleanSupplier cancelled, long deadline) {
        if (!sceneReady() || !isInHostedHouse() || recoveryStopped(cancelled, deadline)) {
            return false;
        }
        houseContext.beginRecovery();
        invalidateHousePreparation();
        try {
            if (currentAdvertisedHost != null) {
                excludeUnsuitableHost(currentAdvertisedHost, System.currentTimeMillis());
            }
            var exit = Microbot.getRs2TileObjectCache().query()
                    .withId(net.runelite.api.gameval.ObjectID.POH_EXIT_PORTAL).nearest();
            boolean reachable = exit != null && exit.isReachable();
            log.info("[W330POH] house_switch phase=exit_lookup generation={} found={} reachable={} at={} actions={}",
                    houseContext.generation(), exit != null, reachable,
                    exit == null ? null : exit.getWorldLocation(),
                    exit == null ? null : Microbot.getClientThread().runOnClientThreadOptional(() ->
                            exit.getObjectComposition() == null ? "unknown"
                                    : Arrays.toString(exit.getObjectComposition().getActions())).orElse("unavailable"));
            if (recoveryStopped(cancelled, deadline)) return false;
            boolean clicked = reachable && exit.click("Enter");
            log.info("[W330POH] house_switch phase=exit_dispatch clicked={}", clicked);
            boolean exited = clicked && awaitRecovery(
                    () -> !isInHostedHouse() && isNearHouseAdvertisement(), 12000, cancelled, deadline);
            if (!exited && !recoveryStopped(cancelled, deadline)) {
                exited = leaveHouseViaOptions(cancelled, deadline);
            }
            if (!exited && !recoveryStopped(cancelled, deadline)) {
                // Reuse the verified Outside policy when the portal cannot be used.
                log.info("[W330POH] house_switch phase=outside_fallback");
                exited = teleportOutsideToAdvertisement(cancelled, deadline)
                        && !isInHostedHouse() && isNearHouseAdvertisement();
            }
            if (!exited || recoveryStopped(cancelled, deadline)) {
                log.warn("[W330POH] house_switch phase=exit_failed generation={} at={}",
                        houseContext.generation(), Rs2Player.getWorldLocation());
                return false;
            }
            currentAdvertisedHost = null;
            invalidateHousePreparation();
            boolean entered = enterHostedHouse(cancelled, deadline);
            log.info("[W330POH] house_switch phase={} generation={}",
                    entered ? "entered" : "entry_failed", houseContext.generation());
            return entered;
        } catch (RuntimeException ex) {
            log.warn("[W330POH] house_switch exception generation={}", houseContext.generation(), ex);
            throw ex;
        } finally {
            houseContext.endRecovery();
            invalidateHousePreparation();
        }
    }

    public boolean facilitiesEligible() {
        if (!sceneReady()) return false;
        isInHostedHouse(); // Observe departure before deciding whether an old rejection still applies.
        return houseContext.facilitiesEligible();
    }

    public long houseGeneration() { return houseContext.generation(); }

    public boolean sceneReady() {
        return Microbot.getClientThread() != null && Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient() != null && Microbot.getClient().getGameState() == net.runelite.api.GameState.LOGGED_IN
                        && Microbot.getClient().getLocalPlayer() != null).orElse(false);
    }

    public void rejectCurrentHouse() {
        if (!sceneReady() || !isInHostedHouse()) return;
        houseContext.reject();
        if (currentAdvertisedHost != null)
            excludeUnsuitableHost(currentAdvertisedHost, System.currentTimeMillis());
        invalidateHousePreparation();
    }

    public void excludeCurrentHouseForSession(String facility) {
        if (!sceneReady() || !isInHostedHouse()) return;
        houseContext.reject();
        String host = currentAdvertisedHost;
        excludeHostForSession(host);
        log.warn("[W330POH] house_session_exclusion host={} recorded={} facility={} generation={}",
                host, host != null && !Text.standardize(host).isEmpty(), facility, houseContext.generation());
        invalidateHousePreparation();
    }

    static void excludeHostForSession(String host) {
        if (host != null && !Text.standardize(host).isEmpty())
            sessionUnsuitableHosts.add(Text.standardize(host));
    }

    public void blacklistUnreachableHouse(String facility) {
        if (!sceneReady() || !isInHostedHouse()) return;
        houseContext.reject();
        String host = currentAdvertisedHost;
        if (host != null && Microbot.getConfigManager() != null) {
            String before = Microbot.getConfigManager().getConfiguration(
                    ShortestPathPlugin.CONFIG_GROUP, "blockedWorld330Hosts");
            Microbot.getConfigManager().setConfiguration(ShortestPathPlugin.CONFIG_GROUP,
                    "blockedWorld330Hosts", addBlockedHost(before, host));
        }
        log.warn("[W330POH] house_blacklist host={} persistent={} facility={} generation={} at={}",
                host, host != null && Microbot.getConfigManager() != null, facility,
                houseContext.generation(), Rs2Player.getWorldLocation());
        invalidateHousePreparation();
    }

    private boolean leaveHouseViaOptions(BooleanSupplier cancelled, long deadline) {
        if (recoveryStopped(cancelled, deadline) || !Rs2Tab.switchToSettingsTab()) return false;
        if (!Rs2Widget.isWidgetVisible(InterfaceID.SettingsSide.HOUSEOPTIONS)) {
            if (recoveryStopped(cancelled, deadline)
                    || !Rs2Widget.clickWidget(InterfaceID.SettingsSide.TAB_1_1_BACKING)
                    || !awaitRecovery(() -> Rs2Widget.isWidgetVisible(InterfaceID.SettingsSide.HOUSEOPTIONS),
                            2500, cancelled, deadline)) return false;
        }
        if (recoveryStopped(cancelled, deadline)
                || !Rs2Widget.clickWidget(InterfaceID.SettingsSide.HOUSEOPTIONS)
                || !awaitRecovery(() -> Rs2Widget.isWidgetVisible(InterfaceID.PohOptions.LEAVE_HOUSE),
                        2500, cancelled, deadline)) return false;
        if (recoveryStopped(cancelled, deadline)) return false;
        boolean clicked = Rs2Widget.clickWidget(InterfaceID.PohOptions.LEAVE_HOUSE);
        boolean exited = clicked && awaitRecovery(() -> !isInHostedHouse() && isNearHouseAdvertisement(),
                8000, cancelled, deadline);
        log.info("[W330POH] house_switch phase=leave_house_widget clicked={} exited={}", clicked, exited);
        return exited;
    }

    private static void invalidateHousePreparation() {
        if (Rs2PathApi.getPathfinderConfig() != null)
            Rs2PathApi.getPathfinderConfig().invalidateTransportRefreshCache();
    }

    private static boolean recoveryStopped(BooleanSupplier cancelled, long deadline) {
        return Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()
                || System.currentTimeMillis() >= deadline;
    }

    private static boolean awaitRecovery(BooleanSupplier condition, int timeout,
            BooleanSupplier cancelled, long deadline) {
        if (recoveryStopped(cancelled, deadline)) return false;
        int remaining = (int) Math.min(timeout, Math.max(1L, deadline - System.currentTimeMillis()));
        sleepUntil(() -> recoveryStopped(cancelled, deadline) || condition.getAsBoolean(), remaining);
        return !recoveryStopped(cancelled, deadline) && condition.getAsBoolean();
    }

    public boolean isInHostedHouse() {
        if (Microbot.getClient() == null) return false;
        if (Microbot.getClient().getWorld() != 330) {
            houseContext.observe(false);
            currentAdvertisedHost = null;
            return false;
        }
        boolean present = isOnHostedHouseTemplate();
        if (Microbot.getClient().getGameState() == net.runelite.api.GameState.LOGGED_IN
                && Microbot.getClient().getLocalPlayer() != null) {
            houseContext.observe(present);
            if (!present) currentAdvertisedHost = null;
        }
        return present;
    }

    public WorldPoint getRoutingAnchor() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (isInHostedHouse() && playerLocation != null) {
            return playerLocation;
        }
        return POH_INSTANCE_ANCHOR;
    }

    public boolean canReachAdvertisementBoard(boolean includeBankItems) {
        return hasAdvertisementAccess(isNearHouseAdvertisement(),
                () -> Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE),
                includeBankItems, () -> hasHouseTablet(Rs2Bank.getAll()),
                () -> Rs2Magic.canCast(Rs2Spells.TELEPORT_TO_HOUSE));
    }

    static boolean hasAdvertisementAccess(boolean nearAdvertisement, BooleanSupplier inventoryTablet,
            boolean includeBankItems, BooleanSupplier bankTablet, BooleanSupplier spellCastable) {
        return nearAdvertisement || inventoryTablet.getAsBoolean()
                || (includeBankItems && bankTablet.getAsBoolean()) || spellCastable.getAsBoolean();
    }

    static boolean hasHouseTablet(Stream<Rs2ItemModel> bankItems) {
        // This fixed, unnoted tablet id needs no fuzzy-name/linked-note fallback. A missing id
        // through Rs2Bank.hasItem would load every cold bank item's metadata on the client thread.
        return bankItems.anyMatch(item -> item.getId() == ItemID.POH_TABLET_TELEPORTTOHOUSE
                && item.getQuantity() > 0);
    }

    static boolean hasAdvertisementAccess(boolean nearAdvertisement, boolean inventoryTablet,
            boolean bankTablet, boolean spellCastable) {
        return nearAdvertisement || inventoryTablet || bankTablet || spellCastable;
    }

    private boolean isNearHouseAdvertisement() {
        return Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.HOUSE_ADVERTISEMENT)
                .nearest() != null;
    }

    public boolean isWithinAdvertisementApproach() {
        var board = Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.HOUSE_ADVERTISEMENT).nearest();
        return isWithinAdvertisementApproach(Rs2Player.getWorldLocation(),
                board == null ? null : board.getWorldLocation());
    }

    static boolean isWithinAdvertisementApproach(WorldPoint player, WorldPoint board) {
        return player != null && board != null && player.getPlane() == board.getPlane()
                && player.distanceTo2D(board) <= 12;
    }

    private boolean teleportOutsideToAdvertisement() {
        return teleportOutsideToAdvertisement(() -> false, Long.MAX_VALUE);
    }

    private boolean teleportOutsideToAdvertisement(BooleanSupplier cancelled, long deadline) {
        if (recoveryStopped(cancelled, deadline)) return false;
        boolean inventoryTablet = Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE);
        boolean spellCastable = Rs2Magic.canCast(Rs2Spells.TELEPORT_TO_HOUSE);
        boolean outsideIsDefault = Microbot.getVarbitValue(VarbitID.POH_TELE_TOGGLE) == 1;
        HouseTeleportAction action = chooseHouseTeleportAction(
                inventoryTablet, spellCastable, outsideIsDefault);

        if (recoveryStopped(cancelled, deadline)) return false;
        boolean teleportStarted;
        switch (action) {
            case TABLET_DEFAULT:
                teleportStarted = Rs2Inventory.interact(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Break");
                break;
            case TABLET_OUTSIDE:
                teleportStarted = Rs2Inventory.interact(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Outside");
                break;
            case SPELL_DEFAULT:
                teleportStarted = Rs2Magic.cast(Rs2Spells.TELEPORT_TO_HOUSE);
                break;
            case SPELL_OUTSIDE:
                teleportStarted = Rs2Magic.cast(Rs2Spells.TELEPORT_TO_HOUSE, "outside", 2);
                break;
            default:
                return false;
        }
        return teleportStarted && awaitRecovery(
                () -> !isInHostedHouse() && isNearHouseAdvertisement(), 12000, cancelled, deadline);
    }

    static HouseTeleportAction chooseHouseTeleportAction(boolean inventoryTablet,
            boolean spellCastable, boolean outsideIsDefault) {
        if (spellCastable) {
            return outsideIsDefault ? HouseTeleportAction.SPELL_DEFAULT : HouseTeleportAction.SPELL_OUTSIDE;
        }
        if (inventoryTablet) {
            return outsideIsDefault ? HouseTeleportAction.TABLET_DEFAULT : HouseTeleportAction.TABLET_OUTSIDE;
        }
        return HouseTeleportAction.NONE;
    }

    private boolean openAdvertisementBoard() {
        return openAdvertisementBoard(() -> false, Long.MAX_VALUE);
    }

    private boolean openAdvertisementBoard(BooleanSupplier cancelled, long deadline) {
        if (recoveryStopped(cancelled, deadline)) return false;
        boolean clicked = Microbot.getRs2TileObjectCache().query()
                .interact(ObjectID.HOUSE_ADVERTISEMENT, "View");
        if (!clicked) {
            return false;
        }
        return awaitRecovery(this::isAdvertisementWidgetOpen, 5000, cancelled, deadline);
    }

    private boolean isAdvertisementWidgetOpen() {
        return Rs2Widget.isWidgetVisible(HOUSE_ADVERTISEMENT_WIDGET);
    }

    private boolean selectAdvertisedHouse(BooleanSupplier cancelled, long deadline) {
        Set<String> failedAdvertisedHosts = new HashSet<>();
        for (int attempt = 0; attempt < MAX_ADVERTISED_HOST_ATTEMPTS; attempt++) {
            if (recoveryStopped(cancelled, deadline)) return false;
            if (!isAdvertisementWidgetOpen() && !openAdvertisementBoard(cancelled, deadline)) {
                return false;
            }
            if (!ensureBestHouseSort()) {
                return false;
            }

            Widget containerNames = Rs2Widget.getWidget(NAMES_GROUP, NAMES_CHILD);
            Widget containerEnter = Rs2Widget.getWidget(ENTER_GROUP, ENTER_CHILD);
            if (containerNames == null || containerNames.getChildren() == null
                    || containerEnter == null || containerEnter.getChildren() == null) {
                return false;
            }

            List<String> advertisedHosts = advertisedHouseNames(containerNames);
            for (int row = 0; row < advertisedHosts.size(); row++) {
                if (recoveryStopped(cancelled, deadline)) return false;
                String houseOwner = advertisedHosts.get(row);
                if (isExcludedAdvertisedHost(houseOwner) || failedAdvertisedHosts.contains(houseOwner)) {
                    continue;
                }
                Widget enter = matchingEnterWidget(containerEnter, houseOwner);
                if (enter == null) {
                    failedAdvertisedHosts.add(houseOwner);
                    continue;
                }

                log.info("[W330POH] trying advertised host={} row={} skippedHosts={}",
                        Text.standardize(houseOwner), row, failedAdvertisedHosts.size());
                Rs2Widget.clickChildWidget(ENTER_CONTAINER_WIDGET, enter.getIndex());
                boolean entered = awaitRecovery(this::isInHostedHouse, 9000, cancelled, deadline);
                if (entered) {
                    currentAdvertisedHost = houseOwner;
                    invalidateHousePreparation();
                    return true;
                }

                failedAdvertisedHosts.add(houseOwner);
                log.info("[W330POH] advertised host row={} did not enter skippedHosts={}",
                        row, failedAdvertisedHosts.size());
                if (!isAdvertisementWidgetOpen() && !openAdvertisementBoard(cancelled, deadline)) {
                    return false;
                }
                break;
            }
        }
        return false;
    }

    private boolean ensureBestHouseSort() {
        Widget containerNames = Rs2Widget.getWidget(NAMES_GROUP, NAMES_CHILD);
        Widget containerEnter = Rs2Widget.getWidget(ENTER_GROUP, ENTER_CHILD);
        if (containerNames == null || containerNames.getChildren() == null
                || containerEnter == null || containerEnter.getChildren() == null) {
            return false;
        }

        Widget toggleArrow = Rs2Widget.getWidget(SORT_ARROW_WIDGET);
        if (toggleArrow != null && toggleArrow.getSpriteId() == SORT_ASCENDING_SPRITE) {
            Rs2Widget.clickWidget(SORT_ARROW_WIDGET);
            sleepUntil(() -> {
                Widget refreshedArrow = Rs2Widget.getWidget(SORT_ARROW_WIDGET);
                return refreshedArrow == null || refreshedArrow.getSpriteId() != SORT_ASCENDING_SPRITE;
            }, 2000);
            containerNames = Rs2Widget.getWidget(NAMES_GROUP, NAMES_CHILD);
            containerEnter = Rs2Widget.getWidget(ENTER_GROUP, ENTER_CHILD);
            if (containerNames == null || containerNames.getChildren() == null
                    || containerEnter == null || containerEnter.getChildren() == null) {
                return false;
            }
        }
        return true;
    }

    static boolean isExcludedAdvertisedHost(String houseOwner) {
        return isExcludedAdvertisedHost(houseOwner, System.currentTimeMillis());
    }

    static boolean isExcludedAdvertisedHost(String houseOwner, long now) {
        if (houseOwner == null) return false;
        String name = Text.standardize(houseOwner);
        Long until = unsuitableHosts.get(name);
        String blocked = Microbot.getConfigManager() == null ? null
                : Microbot.getConfigManager().getConfiguration(ShortestPathPlugin.CONFIG_GROUP, "blockedWorld330Hosts");
        return EXCLUDED_ADVERTISED_HOSTS.contains(name) || blockedHosts(blocked).contains(name)
                || sessionUnsuitableHosts.contains(name)
                || (until != null && now < until);
    }

    static Set<String> blockedHosts(String value) {
        Set<String> hosts = new LinkedHashSet<>();
        if (value != null) for (String host : value.split("[,\\r\\n]+")) {
            String normalized = Text.standardize(host);
            if (!normalized.isEmpty()) hosts.add(normalized);
        }
        return hosts;
    }

    static String addBlockedHost(String value, String host) {
        Set<String> hosts = blockedHosts(value);
        if (host != null && !Text.standardize(host).isEmpty()) hosts.add(Text.standardize(host));
        return String.join(", ", hosts);
    }

    static void excludeUnsuitableHost(String houseOwner, long now) {
        if (houseOwner != null) {
            unsuitableHosts.put(Text.standardize(houseOwner), now + 10 * 60_000L);
        }
    }

    private List<String> advertisedHouseNames(Widget containerNames) {
        List<Widget> nameWidgets = new ArrayList<>();
        for (Widget child : containerNames.getChildren()) {
            if (child == null || child.getText() == null || child.getText().isEmpty()) {
                continue;
            }
            nameWidgets.add(child);
        }
        nameWidgets.sort(Comparator.comparingInt(Widget::getOriginalY));
        List<String> names = new ArrayList<>();
        for (Widget child : nameWidgets) {
            names.add(child.getText());
        }
        return names;
    }

    private Widget matchingEnterWidget(Widget containerEnter, String houseOwner) {
        for (Widget child : containerEnter.getChildren()) {
            if (child == null || child.getOnOpListener() == null) {
                continue;
            }
            boolean matches = Arrays.stream(child.getOnOpListener())
                    .filter(Objects::nonNull)
                    .anyMatch(obj -> obj.toString().replace('\u00A0', ' ').contains(houseOwner));
            if (matches) {
                return child;
            }
        }
        return null;
    }

    private boolean isOnHostedHouseTemplate() {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null
                && location.getY() >= 7000
                && location.getY() <= 8000;
    }
}
