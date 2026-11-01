package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.runelite.client.plugins.microbot.util.walker.WalkerApiAssertions.assertThrows;
import static org.mockito.Mockito.*;

public class PlanningConfigCaptureTest
{
    private static final WorldPoint A = new WorldPoint(3201, 3201, 0);
    private static final WorldPoint B = new WorldPoint(3210, 3201, 0);

    @Test public void copiedCatalogIndexesRestrictionsAndLearnedEdgesAreIndependent() throws Exception {
        Transport edge = new Transport(A, B, "test", TransportType.TRANSPORT, false, 1);
        PathfinderConfig source = config(null, new ShortestPathConfig() { });
        source.getAllTransports().put(A, new LinkedHashSet<>(Set.of(edge)));
        source.getTransports().put(A, new LinkedHashSet<>(Set.of(edge)));
        source.getTransportsPacked().put(WorldPointUtil.packWorldPoint(A), source.getTransports().get(A));
        source.getUsableTeleports().add(edge);
        long blocked = PathfinderConfig.transportEdgeKey(WorldPointUtil.packWorldPoint(A), WorldPointUtil.packWorldPoint(B));
        source.getBlockedTransportEdgesPacked().add(blocked);
        setField(source, "learnedBlockedEdgeKeys", Set.class).add(blocked);
        setField(source, "internalRestrictedPointsPacked", Set.class).add(WorldPointUtil.packWorldPoint(B));
        PathfinderConfig copy = source.copyForPlanning();
        assertNotSame(source, copy);
        assertEquals(source.getBackend(), copy.getBackend());
        assertTrue(copy.getBlockedTransportEdgesPacked().contains(blocked));
        assertTrue(copy.getRestrictedPointsPacked().contains(WorldPointUtil.packWorldPoint(B)));
        copy.getTransports().get(A).clear();
        copy.getAllTransports().get(A).clear();
        copy.getUsableTeleports().clear();
        copy.getBlockedTransportEdgesPacked().clear();
        copy.setUseBankItems(true);
        assertEquals(Set.of(edge), source.getTransports().get(A));
        assertEquals(Set.of(edge), source.getAllTransports().get(A));
        assertEquals(Set.of(edge), source.getUsableTeleports());
        assertTrue(source.getBlockedTransportEdgesPacked().contains(blocked));
        assertFalse(source.isUseBankItems());
    }

    @Test public void clientThreadIsRejectedBeforeTakingAnySnapshotLock() {
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        PathfinderConfig source = config(client, null);
        assertThrows(IllegalStateException.class, source::copyForPlanning);
        verify(client, never()).getWorld();
        verify(client, never()).getGameState();
    }

    @Test public void teleportPublicationDoesNotMutatePreviouslyCapturedSet() {
        PathfinderConfig source = config(null, null);
        Transport edge = new Transport(A, B, "walk-edge", TransportType.TRANSPORT, false, 1);
        Transport teleport = new Transport(B, "snapshot teleport", TransportType.TELEPORTATION_ITEM, false, 31, Set.of());
        Set<Transport> original = new LinkedHashSet<>(Set.of(edge));
        source.getTransports().put(A, original);
        source.getUsableTeleports().add(teleport);
        source.refreshTeleports(WorldPointUtil.packWorldPoint(A), 0);
        assertEquals(Set.of(edge), original);
        assertNotSame(original, source.getTransports().get(A));
        assertEquals(Set.of(edge, teleport), source.getTransports().get(A));
    }

    @Test public void completedPublicationDuringCaptureTriggersRetryAndCoherentPackedIndex() {
        PathfinderConfig source = config(null, null);
        Transport edge = new Transport(A, B, "edge", TransportType.TRANSPORT, false, 1);
        Transport teleport = new Transport(B, "snapshot teleport", TransportType.TELEPORTATION_ITEM, false, 31, Set.of());
        java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
        Set<Transport> racing = new LinkedHashSet<Transport>() {
            @Override public java.util.Iterator<Transport> iterator() {
                if (once.compareAndSet(false, true)) source.refreshTeleports(WorldPointUtil.packWorldPoint(A), 0);
                return super.iterator();
            }
        };
        racing.add(edge);
        source.getTransports().put(A, racing);
        source.getUsableTeleports().add(teleport);
        PathfinderConfig copy = source.copyForPlanning();
        assertTrue(once.get());
        assertEquals(Set.of(edge, teleport), copy.getTransports().get(A));
        assertSame(copy.getTransports().get(A), copy.getTransportsPacked().get(WorldPointUtil.packWorldPoint(A)));
        assertEquals(Set.of(edge), racing);
    }

    @Test public void inProgressCatalogPublicationFailsWithoutBlocking() throws Exception {
        PathfinderConfig source = config(null, null);
        java.util.concurrent.atomic.AtomicInteger publishing = setField(source, "catalogPublishers", java.util.concurrent.atomic.AtomicInteger.class);
        publishing.incrementAndGet();
        try { assertThrows(IllegalStateException.class, source::copyForPlanning); }
        finally { publishing.decrementAndGet(); }
        assertNotNull(source.copyForPlanning());
    }

    @Test public void contendedRefreshCaptureFailsWithoutWaiting() throws Exception {
        PathfinderConfig source = config(null, null);
        ReentrantLock lock = setField(source, "refreshLock", ReentrantLock.class);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock();
            try { held.countDown(); release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { lock.unlock(); }
        });
        holder.start();
        try {
            assertTrue(held.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, source::copyForPlanning);
            assertEquals("capture must not wait for unlock", 1L, release.getCount());
        } finally {
            release.countDown(); holder.join(5_000);
            assertFalse(holder.isAlive());
        }
    }

    @Test public void frozenCollisionCannotBeResetByEitherBeginSearchCall() {
        SplitFlagMap terrain = SplitFlagMap.fromResources();
        CollisionMap frozen = CollisionMap.frozenSnapshot(terrain, (x, y, plane, flag) -> false);
        assertFalse(frozen.n(3201, 3201, 0));
        frozen.beginSearch();
        CollisionMap second = frozen.frozenCopy();
        second.beginSearch();
        assertFalse(frozen.n(3201, 3201, 0));
        assertFalse(second.n(3201, 3201, 0));
        assertEquals(frozen.exactSnapshotKey(), second.exactSnapshotKey());
    }

    @Test public void refreshOnUnownedConfigRefusesBeforeChangingBankFlag() {
        PathfinderConfig source = config(null, null);
        assertThrows(IllegalStateException.class, () -> source.refreshForPlanning(B, true));
        assertFalse(source.isUseBankItems());
    }

    private static PathfinderConfig config(Client client, ShortestPathConfig policy) {
        return new PathfinderConfig(SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), client, policy, false);
    }

    @SuppressWarnings("unchecked")
    private static <T> T setField(Object object, String name, Class<T> type) throws Exception {
        Field field = PathfinderConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(object);
    }
}
