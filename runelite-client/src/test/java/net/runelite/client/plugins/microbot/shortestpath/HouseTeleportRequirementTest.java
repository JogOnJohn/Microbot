package net.runelite.client.plugins.microbot.shortestpath;

import java.util.Map;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.policy.TransportRequirementPolicy;
import org.junit.Test;
import static org.junit.Assert.*;

public class HouseTeleportRequirementTest {
    @Test public void outsideDefaultRejectsImportedInsideSpellAndTablet() {
        for (TransportType type : new TransportType[]{TransportType.TELEPORTATION_SPELL, TransportType.TELEPORTATION_ITEM}) {
            String name = type == TransportType.TELEPORTATION_ITEM ? "Teleport to House tablet" : "Teleport to House";
            Transport inside = row(name + " (Inside)", "4744=1", type);
            Transport outside = row(name + " (Outside)", "4744=0;2187=1", type);
            assertFalse(TransportRequirementPolicy.varbitChecks(inside, id -> 1));
            assertTrue(TransportRequirementPolicy.varbitChecks(outside, id -> 1));
            assertTrue(TransportRequirementPolicy.varbitChecks(inside, id -> 0));
            assertFalse(TransportRequirementPolicy.varbitChecks(outside, id -> id == 4744 ? 0 : 1));
            assertFalse(TransportRequirementPolicy.varbitChecks(outside, id -> id == 4744 ? 1 : 2));
        }
    }

    @Test public void otherSpellRequirementsAreUnchanged() {
        Transport spell = row("Falador Teleport", "4070=0;1234>2", TransportType.TELEPORTATION_SPELL);
        assertTrue(TransportRequirementPolicy.varbitChecks(spell, id -> id == 4070 ? 0 : 3));
        assertFalse(TransportRequirementPolicy.varbitChecks(spell, id -> id == 4070 ? 0 : 2));
    }

    private static Transport row(String label, String varbits, TransportType type) {
        return new Transport(Map.of("Destination", "1858 7051 0", "Display info", label, "Varbits", varbits), type);
    }
}
