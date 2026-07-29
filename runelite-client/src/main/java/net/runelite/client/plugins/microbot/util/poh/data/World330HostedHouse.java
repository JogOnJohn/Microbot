package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        if (!wasInHostedHouse) {
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
        return 12;
    }

    @Override
    public String displayInfo() {
        return "W330 hosted house";
    }

    private boolean enterHostedHouse() {
        if (!isNearHouseAdvertisement() && !teleportOutsideToAdvertisement()) {
            return false;
        }
        if (!isAdvertisementWidgetOpen() && !openAdvertisementBoard()) {
            return false;
        }
        if (!selectAdvertisedHouse()) {
            return false;
        }
        return sleepUntil(this::isInHostedHouse, 16000);
    }

    public boolean isInHostedHouse() {
        if (Microbot.getClient() == null || Microbot.getClient().getWorld() != 330) {
            return false;
        }
        return isOnHostedHouseTemplate();
    }

    public WorldPoint getRoutingAnchor() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (isInHostedHouse() && playerLocation != null) {
            return playerLocation;
        }
        return POH_INSTANCE_ANCHOR;
    }

    public boolean canReachAdvertisementBoard(boolean includeBankItems) {
        boolean nearAdvertisement = isNearHouseAdvertisement();
        boolean inventoryTablet = Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE);
        boolean bankTablet = includeBankItems && Rs2Bank.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE);
        boolean spellCastable = Rs2Magic.canCast(Rs2Spells.TELEPORT_TO_HOUSE);
        return hasAdvertisementAccess(nearAdvertisement, inventoryTablet, bankTablet, spellCastable);
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

    private boolean teleportOutsideToAdvertisement() {
        boolean inventoryTablet = Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE);
        boolean spellCastable = Rs2Magic.canCast(Rs2Spells.TELEPORT_TO_HOUSE);
        boolean outsideIsDefault = Microbot.getVarbitValue(VarbitID.POH_TELE_TOGGLE) == 1;
        HouseTeleportAction action = chooseHouseTeleportAction(
                inventoryTablet, spellCastable, outsideIsDefault);

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
        return teleportStarted && sleepUntil(this::isNearHouseAdvertisement, 12000);
    }

    static HouseTeleportAction chooseHouseTeleportAction(boolean inventoryTablet,
            boolean spellCastable, boolean outsideIsDefault) {
        if (inventoryTablet) {
            return outsideIsDefault ? HouseTeleportAction.TABLET_DEFAULT : HouseTeleportAction.TABLET_OUTSIDE;
        }
        if (spellCastable) {
            return outsideIsDefault ? HouseTeleportAction.SPELL_DEFAULT : HouseTeleportAction.SPELL_OUTSIDE;
        }
        return HouseTeleportAction.NONE;
    }

    private boolean openAdvertisementBoard() {
        boolean clicked = Microbot.getRs2TileObjectCache().query()
                .interact(ObjectID.HOUSE_ADVERTISEMENT, "View");
        if (!clicked) {
            return false;
        }
        return sleepUntil(this::isAdvertisementWidgetOpen, 5000);
    }

    private boolean isAdvertisementWidgetOpen() {
        return Rs2Widget.isWidgetVisible(HOUSE_ADVERTISEMENT_WIDGET);
    }

    private boolean selectAdvertisedHouse() {
        Set<String> failedAdvertisedHosts = new HashSet<>();
        for (int attempt = 0; attempt < MAX_ADVERTISED_HOST_ATTEMPTS; attempt++) {
            if (!isAdvertisementWidgetOpen() && !openAdvertisementBoard()) {
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
                String houseOwner = advertisedHosts.get(row);
                if (failedAdvertisedHosts.contains(houseOwner)) {
                    continue;
                }
                Widget enter = matchingEnterWidget(containerEnter, houseOwner);
                if (enter == null) {
                    failedAdvertisedHosts.add(houseOwner);
                    continue;
                }

                log.info("[W330POH] trying advertised host row={} skippedHosts={}",
                        row, failedAdvertisedHosts.size());
                Rs2Widget.clickChildWidget(ENTER_CONTAINER_WIDGET, enter.getIndex());
                boolean entered = sleepUntil(this::isInHostedHouse, 9000);
                if (entered) {
                    return true;
                }

                failedAdvertisedHosts.add(houseOwner);
                log.info("[W330POH] advertised host row={} did not enter skippedHosts={}",
                        row, failedAdvertisedHosts.size());
                if (!isAdvertisementWidgetOpen() && !openAdvertisementBoard()) {
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
