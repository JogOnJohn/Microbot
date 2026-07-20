package net.runelite.client.plugins.microbot.util.inventory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class Rs2RunePouchFingerprintTest {

    @Test
    public void fingerprintsEveryRuneAndQuantityVarbit() {
        Map<Integer, Integer> values = new HashMap<>();
        List<Integer> requested = new ArrayList<>();
        int baseline = Rs2RunePouch.computeVarbitFingerprint(id -> {
            requested.add(id);
            return values.getOrDefault(id, 0);
        });

        Set<Integer> distinctVarbits = new LinkedHashSet<>(requested);
        assertEquals(8, requested.size());
        assertEquals(8, distinctVarbits.size());

        for (int varbitId : distinctVarbits) {
            values.put(varbitId, 1);
            int changed = Rs2RunePouch.computeVarbitFingerprint(id -> values.getOrDefault(id, 0));
            assertNotEquals("Varbit " + varbitId + " must invalidate the transport cache", baseline, changed);
            values.clear();
        }
    }
}
