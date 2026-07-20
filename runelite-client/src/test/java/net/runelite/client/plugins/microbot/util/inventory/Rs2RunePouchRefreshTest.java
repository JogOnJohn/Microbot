package net.runelite.client.plugins.microbot.util.inventory;

import net.runelite.client.plugins.microbot.util.magic.Runes;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Rs2RunePouchRefreshTest {

    @Test
    public void fullRefreshAtomicallyReplacesTheFourSlotSnapshot() {
        List<Integer> orderedVarbits = new ArrayList<>();
        Rs2RunePouch.computeVarbitFingerprint(id -> {
            orderedVarbits.add(id);
            return 0;
        });

        Map<Integer, Integer> values = new HashMap<>();
        values.put(orderedVarbits.get(0), Runes.LAW.getId());
        values.put(orderedVarbits.get(1), 250);
        Rs2RunePouch.replaceSlotsFromVarbits(id -> values.getOrDefault(id, 0));

        assertEquals(4, Rs2RunePouch.getSlots().size());
        assertEquals(Integer.valueOf(250), Rs2RunePouch.getRunes().get(Runes.LAW));

        Rs2RunePouch.replaceSlotsFromVarbits(id -> 0);
        Rs2RunePouch.replaceSlotsFromVarbits(id -> 0);

        assertEquals("Repeated empty refreshes must not append duplicate slots",
                4, Rs2RunePouch.getSlots().size());
        assertTrue(Rs2RunePouch.getRunes().isEmpty());

        try {
            Rs2RunePouch.getSlots().clear();
            fail("Published slot snapshots must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected: background readers cannot mutate the published snapshot.
        }
    }
}
