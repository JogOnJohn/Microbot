package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.HashSet;
import java.util.Set;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class OrdinaryWalkingMaskTest {
    private static long edge(int x, int y, int flag) {
        return ((long) WorldPointUtil.packWorldPoint(x, y, 3) << 1) | flag;
    }

    private static class Edges extends CollisionMap {
        final Set<Long> open = new HashSet<>();
        int reads;
        Edges() { super(null); }
        @Override public boolean n(int x, int y, int z) {
            reads++;
            assertEquals(3, z);
            return open.contains(edge(x, y, 0));
        }
        @Override public boolean e(int x, int y, int z) {
            reads++;
            assertEquals(3, z);
            return open.contains(edge(x, y, 1));
        }
    }

    private static int reference(CollisionMap map) {
        int x = 10, y = 10, z = 3;
        if (map.isBlocked(x, y, z)) return 0;
        int mask = 0;
        if (map.n(x, y, z)) mask |= 1;
        if (map.n(x, y, z) && map.e(x, y + 1, z) && map.e(x, y, z) && map.n(x + 1, y, z)) mask |= 2;
        if (map.e(x, y, z)) mask |= 4;
        if (map.s(x, y, z) && map.e(x, y - 1, z) && map.e(x, y, z) && map.s(x + 1, y, z)) mask |= 8;
        if (map.s(x, y, z)) mask |= 16;
        if (map.s(x, y, z) && map.w(x, y - 1, z) && map.w(x, y, z) && map.s(x - 1, y, z)) mask |= 32;
        if (map.w(x, y, z)) mask |= 64;
        if (map.n(x, y, z) && map.w(x, y + 1, z) && map.w(x, y, z) && map.n(x - 1, y, z)) mask |= 128;
        return mask;
    }

    @Test public void all4096LocalEdgeCombinationsMatchOriginalCornerRules() {
        long[] edges = {edge(10, 10, 0), edge(10, 10, 1), edge(10, 9, 0), edge(9, 10, 1),
                edge(10, 11, 1), edge(11, 10, 0), edge(9, 11, 1), edge(9, 10, 0),
                edge(10, 9, 1), edge(11, 9, 0), edge(9, 9, 1), edge(9, 9, 0)};
        Edges map = new Edges();
        for (int combination = 0; combination < 4096; combination++) {
            map.open.clear();
            for (int bit = 0; bit < edges.length; bit++) {
                if ((combination & (1 << bit)) != 0) map.open.add(edges[bit]);
            }
            int expected = reference(map);
            map.reads = 0;
            assertEquals("edges=" + combination, expected,
                    Byte.toUnsignedInt(map.ordinaryWalkingMask(WorldPointUtil.packWorldPoint(10, 10, 3))));
            assertTrue("at most four cardinals plus eight corner edges", map.reads <= 12);
        }
    }
}
