package net.runelite.client.plugins.microbot.util.leaguetransport;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import static net.runelite.client.plugins.microbot.util.walker.WalkerApiAssertions.assertThrows;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LeaguesPlanningSnapshotTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void knownAreaAndCatalogAreInjectedWithoutCalibrationPruningOrCacheWrites() throws Exception {
        Path directory = temp.newFolder().toPath();
        String row = "{\"kind\":\"catalog-transport\",\"schema\":2,\"requiredRegion\":\"MISTHALIN\","
                + "\"transportType\":\"TRANSPORT\",\"origin\":{\"x\":3201,\"y\":3201,\"p\":0},"
                + "\"destination\":{\"x\":3210,\"y\":3201,\"p\":0},\"displayInfo\":\"known\"}";
        Files.write(directory.resolve("leagues-transport-catalog.jsonl"), row.getBytes(StandardCharsets.UTF_8));
        try (State state = new State(directory);
             MockedStatic<LeaguesTransportTeleport> calibration = mockStatic(LeaguesTransportTeleport.class)) {
            state.landings.put(LeaguesRegion.MISTHALIN, new WorldPoint(3222, 3218, 0));
            Rs2LeaguesTransport.LeaguesContext context = context();
            Object beforeUnlockPublication = value(LeaguesTransportInjection.class, "lastInjectedUnlockedForBlacklistPrune");
            Map<?, ?> cache = (Map<?, ?>) value(LeaguesTransportObservations.class, "CATALOG_FILE_PARSE_CACHE");
            Map<?, ?> cacheBefore = new HashMap<>(cache);
            byte[] fileBefore = Files.readAllBytes(directory.resolve("leagues-transport-catalog.jsonl"));
            LeaguesPlanningSnapshot snapshot = LeaguesPlanningSnapshot.capture(context);
            PathfinderConfig config = mock(PathfinderConfig.class);
            when(config.isTransportUsableWithLeaguesContext(any(), same(context))).thenReturn(true);
            Set<Transport> teleports = new HashSet<>();
            Map<WorldPoint, Set<Transport>> graph = new HashMap<>();
            PrimitiveIntHashMap<Set<Transport>> packed = new PrimitiveIntHashMap<>(4);
            snapshot.inject(config, context, teleports, graph, packed, new HashMap<TransportType, int[]>());
            assertEquals(1, teleports.size());
            assertEquals("Leagues Area: Misthalin", teleports.iterator().next().getDisplayInfo());
            WorldPoint origin = new WorldPoint(3201, 3201, 0);
            assertEquals(1, graph.get(origin).size());
            assertSame(graph.get(origin), packed.get(WorldPointUtil.packWorldPoint(origin)));
            calibration.verifyNoInteractions();
            assertSame(beforeUnlockPublication, value(LeaguesTransportInjection.class, "lastInjectedUnlockedForBlacklistPrune"));
            assertEquals(cacheBefore, cache);
            assertArrayEquals(fileBefore, Files.readAllBytes(directory.resolve("leagues-transport-catalog.jsonl")));
        }
    }

    @Test public void existingBlacklistIsNotPrunedEvenWhenRegionIsUnlocked() throws Exception {
        try (State state = new State(temp.newFolder().toPath())) {
            WorldPoint landing = new WorldPoint(3222, 3218, 0);
            int destination = WorldPointUtil.packWorldPoint(landing);
            state.landings.put(LeaguesRegion.MISTHALIN, landing);
            state.blocked.add(destination);
            state.regions.put(destination, LeaguesRegion.MISTHALIN);
            LeaguesPlanningSnapshot snapshot = LeaguesPlanningSnapshot.capture(context());
            Transport candidate = new Transport(landing, "blocked", TransportType.SEASONAL_TRANSPORT, true, 31, Set.of());
            assertFalse(snapshot.allows(candidate));
            assertTrue(state.blocked.contains(destination));
            assertEquals(LeaguesRegion.MISTHALIN, state.regions.get(destination));
            state.blocked.clear(); state.regions.clear();
            assertFalse("captured blacklist must not change after capture", snapshot.allows(candidate));
        }
    }

    @Test public void uninitializedActiveKnowledgeFailsRatherThanLazyLoadingOrSilentlyOmitting() throws Exception {
        Field loaded = field(LeaguesTransportPersistence.class, "persistLoaded");
        Object before = loaded.get(null);
        loaded.set(null, false);
        try {
            assertThrows(IllegalStateException.class, () -> LeaguesPlanningSnapshot.capture(context()));
            assertFalse(loaded.getBoolean(null));
        } finally { loaded.set(null, before); }
    }

    private static Rs2LeaguesTransport.LeaguesContext context() {
        Rs2LeaguesTransport.LeaguesContext context = mock(Rs2LeaguesTransport.LeaguesContext.class);
        when(context.isActive()).thenReturn(true);
        when(context.getUnlockedRegions()).thenReturn(EnumSet.of(LeaguesRegion.MISTHALIN));
        return context;
    }

    private static final class State implements AutoCloseable {
        final Map<LeaguesRegion, WorldPoint> landings;
        final Set<Integer> blocked;
        final Map<Integer, LeaguesRegion> regions;
        final Map<LeaguesRegion, WorldPoint> previousLandings;
        final Set<Integer> previousBlocked;
        final Map<Integer, LeaguesRegion> previousRegions;
        final Object previousLoaded, previousDirectory;
        @SuppressWarnings("unchecked") State(Path directory) throws Exception {
            previousLoaded = value(LeaguesTransportPersistence.class, "persistLoaded");
            previousDirectory = value(LeaguesTransportPersistence.class, "cachedVersionDir");
            landings = (Map<LeaguesRegion, WorldPoint>) value(LeaguesTransportPersistence.class, "PERSIST_REGION_LANDINGS");
            blocked = (Set<Integer>) value(LeaguesTransportPersistence.class, "PERSIST_BLOCKED_DESTS");
            regions = (Map<Integer, LeaguesRegion>) value(LeaguesTransportPersistence.class, "PERSIST_BLOCKED_DEST_REGIONS");
            previousLandings = new HashMap<>(landings); previousBlocked = new HashSet<>(blocked); previousRegions = new HashMap<>(regions);
            landings.clear(); blocked.clear(); regions.clear();
            field(LeaguesTransportPersistence.class, "persistLoaded").set(null, true);
            field(LeaguesTransportPersistence.class, "cachedVersionDir").set(null, directory);
        }
        @Override public void close() throws Exception {
            landings.clear(); landings.putAll(previousLandings); blocked.clear(); blocked.addAll(previousBlocked);
            regions.clear(); regions.putAll(previousRegions);
            field(LeaguesTransportPersistence.class, "persistLoaded").set(null, previousLoaded);
            field(LeaguesTransportPersistence.class, "cachedVersionDir").set(null, previousDirectory);
        }
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Object value(Class<?> owner, String name) throws Exception { return field(owner, name).get(null); }
}
