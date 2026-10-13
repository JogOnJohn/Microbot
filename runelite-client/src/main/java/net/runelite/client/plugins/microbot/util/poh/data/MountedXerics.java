package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.poh.PohMenuInteraction;
import net.runelite.client.plugins.worldmap.TeleportLocationData;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;


@Getter
@RequiredArgsConstructor
public enum MountedXerics implements PohTeleport {
    LOOKOUT(ObjectID.POH_AMULET_XERIC_LOOKOUT, "Xeric's Lookout", TeleportLocationData.XERICS_LOOKOUT.getLocation()),
    GLADE(ObjectID.POH_AMULET_XERIC_GLADE, "Xeric's Glade", TeleportLocationData.XERICS_GLADE.getLocation()),
    INFERNO(ObjectID.POH_AMULET_XERIC_INFERNO, "Xeric's Inferno", TeleportLocationData.XERICS_INFERNO.getLocation()),
    HEART(ObjectID.POH_AMULET_XERIC_HEART, "Xeric's Heart", TeleportLocationData.XERICS_HEART.getLocation()),
//    HONOUR(ObjectID.POH_AMULET_XERIC_GLADE, "Xeric's Honour", TeleportLocationData.XERICS_HONOUR.getLocation()), //Require's Ancient Tablet to be used on Xeric's Talisman
    ;

    private final int objectId;
    private final String destinationName;
    private final WorldPoint destination;

    private final int duration = 4;

    @Override
    public boolean execute() {
        var reachable = net.runelite.client.plugins.microbot.Microbot.getRs2TileObjectCache().query()
                .fromWorldView().withIds(java.util.Arrays.stream(IDS).mapToInt(Integer::intValue).toArray())
                .nearestReachable();
        if (reachable != null) {
            if (reachable.getId() == objectId) return reachable.click(destinationName);
            return PohMenuInteraction.select(destinationName, () -> reachable.click("Teleport menu"),
                    () -> new Widget[]{Rs2Widget.getWidget(InterfaceID.MENU, 3),
                            Rs2Widget.getWidget(InterfaceID.MENU, 0), Rs2Widget.getWidget(InterfaceID.MENU_NEW, 1)});
        }
        DecorativeObject talisman = getObject();
        if (talisman == null) {
            return false;
        }
        if (talisman.getId() == objectId) {
            //The correct id for the object means it has the right left click option, so we can just use that.
            return Rs2GameObject.interact(talisman, destinationName);
        }
        return PohMenuInteraction.select(destinationName,
                () -> Rs2GameObject.interact(talisman, "Teleport menu"),
                () -> new Widget[]{Rs2Widget.getWidget(InterfaceID.MENU, 3),
                        Rs2Widget.getWidget(InterfaceID.MENU, 0),
                        Rs2Widget.getWidget(InterfaceID.MENU_NEW, 1)});
    }

    public static final Integer[] IDS = {ObjectID.POH_AMULET_XERIC, ObjectID.POH_AMULET_XERIC_LOOKOUT, ObjectID.POH_AMULET_XERIC_GLADE, ObjectID.POH_AMULET_XERIC_INFERNO, ObjectID.POH_AMULET_XERIC_HEART, ObjectID.POH_AMULET_XERIC_HONOUR};

    public static DecorativeObject getObject() {
        return Rs2GameObject.getDecorativeObject(IDS);
    }

    public static boolean isMountedXerics(DecorativeObject go) {
        if (go == null) return false;
        for (int objId : IDS) {
            if (objId == go.getId()) return true;
        }
        return false;
    }

    @Override
    public String displayInfo() {
        return getClass().getSimpleName() + " -> " + destinationName;
    }

}
