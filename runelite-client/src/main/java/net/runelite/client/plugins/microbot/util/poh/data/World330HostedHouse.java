package net.runelite.client.plugins.microbot.util.poh.data;

import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.Objects;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

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
    public static final WorldPoint POH_INSTANCE_ANCHOR = new WorldPoint(1877, 7052, 1);

    @Override
    public boolean execute() {
        if (!isInHostedHouse() && !enterHostedHouse()) {
            return false;
        }
        PohTeleports.useOrnateRejuvenationPoolIfPresent();
        return isInHostedHouse();
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
        if (!isNearHouseAdvertisement() && !breakHouseTablet()) {
            return false;
        }
        if (!isAdvertisementWidgetOpen() && !openAdvertisementBoard()) {
            return false;
        }
        if (!selectTopAdvertisedHouse()) {
            return false;
        }
        return sleepUntil(this::isInHostedHouse, 16000);
    }

    public boolean isInHostedHouse() {
        return Microbot.getClient() != null
                && Microbot.getClient().getWorld() == 330
                && (PohTeleports.isInHouse() || Rs2Player.IsInInstance());
    }

    public WorldPoint getRoutingAnchor() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (isInHostedHouse() && playerLocation != null) {
            return playerLocation;
        }
        return POH_INSTANCE_ANCHOR;
    }

    private boolean isNearHouseAdvertisement() {
        return Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.HOUSE_ADVERTISEMENT)
                .nearest() != null;
    }

    private boolean breakHouseTablet() {
        if (!Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE)) {
            return false;
        }
        if (!Rs2Inventory.interact(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Break")) {
            return false;
        }
        return sleepUntil(this::isNearHouseAdvertisement, 12000);
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

    private boolean selectTopAdvertisedHouse() {
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

        String houseOwner = topAdvertisedHouseName(containerNames);
        if (houseOwner == null) {
            return false;
        }

        Widget enter = matchingEnterWidget(containerEnter, houseOwner);
        if (enter == null) {
            return false;
        }

        Rs2Widget.clickChildWidget(ENTER_CONTAINER_WIDGET, enter.getIndex());
        Rs2Player.waitForWalking();
        return true;
    }

    private String topAdvertisedHouseName(Widget containerNames) {
        String houseOwner = null;
        int smallestOriginalY = Integer.MAX_VALUE;
        for (Widget child : containerNames.getChildren()) {
            if (child == null || child.getText() == null || child.getText().isEmpty()) {
                continue;
            }
            if (child.getOriginalY() < smallestOriginalY) {
                houseOwner = child.getText();
                smallestOriginalY = child.getOriginalY();
            }
        }
        return houseOwner;
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
}
