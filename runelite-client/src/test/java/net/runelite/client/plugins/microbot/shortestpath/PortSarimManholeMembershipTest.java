package net.runelite.client.plugins.microbot.shortestpath;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PortSarimManholeMembershipTest {
    private static List<Transport> rows() {
        return Transport.loadAllFromResources().values().stream().flatMap(Set::stream)
                .filter(t -> (t.getObjectId() == 10321 && new WorldPoint(3018, 3231, 0).equals(t.getOrigin()))
                        || (t.getObjectId() == 10309 && new WorldPoint(2962, 9650, 0).equals(t.getOrigin())))
                .collect(Collectors.toList());
    }

    @Test
    public void manholeAndLadderRemainInCatalogButRequireMembership() {
        List<Transport> rows = rows();
        assertEquals(2, rows.size());
        for (Transport row : rows) assertTrue(row.isMembers());
    }

    @Test
    public void freeWorldRejectsBothEdges() throws Exception {
        Client client = mock(Client.class);
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
        PathfinderConfig config = new PathfinderConfig(null, new HashMap<>(), Collections.emptyList(), client, null);
        Method useTransport = PathfinderConfig.class.getDeclaredMethod("useTransport", Transport.class);
        useTransport.setAccessible(true);
        List<Transport> rows = rows();
        assertEquals(2, rows.size());
        for (Transport row : rows) assertFalse((Boolean) useTransport.invoke(config, row));
    }
}
