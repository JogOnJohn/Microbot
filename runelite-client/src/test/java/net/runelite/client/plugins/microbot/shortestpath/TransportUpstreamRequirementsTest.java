package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.Unlock;
import net.runelite.client.plugins.microbot.util.walker.banking.Rs2WalkerBankingPlanner;
import org.junit.Test;
import java.util.Map;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class TransportUpstreamRequirementsTest {
    private static Transport transport(String items) {
        return new Transport(Map.of("Origin", "1 1 0", "Destination", "2 2 0", "Items", items), TransportType.TRANSPORT);
    }

    @Test public void axeAndMacheteNeedBothItems() {
        Transport t = transport("AXE=1&MACHETE=1");
        assertEquals(2, t.getItemIdRequirements().size());
        assertFalse(t.getParsedItemRequirements().isSatisfiedBy(Map.of(ItemID.BRONZE_AXE, 1), Set.of(), Integer.MAX_VALUE));
        assertTrue(t.getParsedItemRequirements().isSatisfiedBy(Map.of(ItemID.BRONZE_AXE, 1, ItemID.MACHETTE, 1), Set.of(), Integer.MAX_VALUE));
    }

    @Test public void ectoTokenTollRetainsAmountAndOptionalUnlock() {
        Transport t = transport("ECTO_TOKEN=25|UNLOCK_DRAGONTOOTH=1");
        assertEquals(25, t.requiredItemQuantity(ItemID.ECTOTOKEN));
        assertFalse(t.getParsedItemRequirements().isSatisfiedBy(Map.of(ItemID.ECTOTOKEN, 24), Set.of(), Integer.MAX_VALUE));
        assertTrue(t.getParsedItemRequirements().isSatisfiedBy(Map.of(ItemID.ECTOTOKEN, 25), Set.of(), Integer.MAX_VALUE));
        assertTrue(t.getParsedItemRequirements().isSatisfiedBy(Map.of(), Set.of(), Integer.MAX_VALUE, Set.of(Unlock.DRAGONTOOTH)));
    }

    @Test public void mergedTransportKeepsLegacyAndNewRequirements() {
        Transport legacy = new Transport(Map.of("Origin", "1 1 0", "Items", "946"), TransportType.TRANSPORT);
        Transport merged = new Transport(legacy, transport("AXE=1&MACHETE=1"));
        assertFalse(merged.getParsedItemRequirements().isSatisfiedBy(Map.of(ItemID.BRONZE_AXE, 1, ItemID.MACHETTE, 1), Set.of(), Integer.MAX_VALUE));
        assertTrue(merged.getParsedItemRequirements().isSatisfiedBy(Map.of(946, 1, ItemID.BRONZE_AXE, 1, ItemID.MACHETTE, 1), Set.of(), Integer.MAX_VALUE));
    }

    @Test public void maximumSkillsAreNotSilentlyDropped() {
        Transport t = new Transport(Map.of("Skills", "Max Total;Max Quest;40 Combat"), TransportType.TELEPORTATION_ITEM);
        assertTrue(t.isRequiresMaximumTotalLevel());
        assertTrue(t.isRequiresMaximumQuestPoints());
        assertEquals(40, t.getRequiredCombatLevel());
    }

    @Test public void declarationsDefaultOff() {
        ShortestPathConfig config = new ShortestPathConfig() {};
        assertFalse(config.unlockCanoeAxe());
        assertFalse(config.unlockDragontoothPassage());
        assertFalse(config.unlockXericsHonour());
    }

    @Test public void bankingCollectsEveryRequiredGroupWithItsQuantity() {
        assertEquals(Map.of(946, 1, 954, 2), Rs2WalkerBankingPlanner.getMissingTransportItemIdsWithQuantities(
                List.of(transport("946=1&954=2"))));
    }

    @Test public void bankingKeepsEctoTokenFareQuantity() {
        assertEquals(Map.of(ItemID.ECTOTOKEN, 25), Rs2WalkerBankingPlanner.getMissingTransportItemIdsWithQuantities(
                List.of(transport("ECTO_TOKEN=25|UNLOCK_DRAGONTOOTH=1"))));
    }

    @Test(expected = IllegalArgumentException.class) public void unknownItemCannotRemoveAGate() {
        transport("NOT_AN_ITEM=1");
    }
}
