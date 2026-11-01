package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LegacyRouteStepsTest
{
    private static final WorldPoint START = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint MEET = new WorldPoint(3210, 3210, 0);
    private static final WorldPoint END = new WorldPoint(3220, 3220, 0);

    @Test
    public void forwardKeepsExactSameEndpointSelectionAndWalkingIndices()
    {
        Transport first = transport(START, MEET, "first", TransportType.TRANSPORT);
        Transport second = transport(START, MEET, "second", TransportType.TRANSPORT);
        Node root = new Node(START, null);
        Node firstRoute = new TransportNode(MEET, root, 4, first);
        Node selected = new TransportNode(MEET, root, 4, second);
        Node end = new Node(MEET.dx(1), selected);

        assertSame(first, LegacyRouteSteps.forward(firstRoute).get(1).getTransport());
        List<PathStep> steps = LegacyRouteSteps.forward(end);
        assertPositions(steps, START, MEET, MEET.dx(1));
        assertNull(steps.get(0).getTransport());
        assertSame(second, steps.get(1).getTransport());
        assertNull(steps.get(2).getTransport());
        assertFalse(steps.stream().anyMatch(PathStep::isBankVisited));
        try { steps.clear(); fail("Selected steps must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test
    public void joinedShiftsBackwardTransportToArrivalAndDoesNotDuplicateMeeting()
    {
        Transport incoming = transport(START, MEET, "incoming", TransportType.TRANSPORT);
        Transport outgoing = transport(MEET, END, "outgoing", TransportType.TRANSPORT);
        Node forward = new TransportNode(MEET, new Node(START, null), 4, incoming);
        Node backwardRoot = new Node(END.dx(1), null);
        Node backwardArrival = new Node(END, backwardRoot);
        Node backwardMeet = new TransportNode(MEET, backwardArrival, 4, outgoing);

        List<PathStep> steps = LegacyRouteSteps.joined(forward, backwardMeet);
        assertPositions(steps, START, MEET, END, END.dx(1));
        assertNull(steps.get(0).getTransport());
        assertSame(incoming, steps.get(1).getTransport());
        assertSame(outgoing, steps.get(2).getTransport());
        assertNull(steps.get(3).getTransport());
    }

    @Test
    public void joinedPreservesMultipleReverseTransportsAndIntermediateWalking()
    {
        WorldPoint middle = MEET.dx(1);
        Transport first = transport(START, MEET, "first", TransportType.TRANSPORT);
        Transport second = transport(middle, END, "second", TransportType.TRANSPORT);
        Node backwardEnd = new Node(END, null);
        Node backwardMiddle = new TransportNode(middle, backwardEnd, 4, second);
        Node backwardWalk = new Node(MEET, backwardMiddle);
        Node backwardMeet = new TransportNode(START, backwardWalk, 4, first);

        List<PathStep> steps = LegacyRouteSteps.joined(new Node(START, null), backwardMeet);
        assertPositions(steps, START, MEET, middle, END);
        assertNull(steps.get(0).getTransport());
        assertSame(first, steps.get(1).getTransport());
        assertNull(steps.get(2).getTransport());
        assertSame(second, steps.get(3).getTransport());
    }

    @Test
    public void originlessTeleportUsesSelectedIdentityWithoutOriginMatching()
    {
        Transport teleport = transport(null, MEET, "teleport", TransportType.TELEPORTATION_SPELL);
        Node arrival = new TransportNode(MEET, new Node(START, null), 9, teleport);
        List<PathStep> forward = LegacyRouteSteps.forward(arrival);
        assertPositions(forward, START, MEET);
        assertSame(teleport, forward.get(1).getTransport());
        List<PathStep> joined = LegacyRouteSteps.joined(arrival, new Node(MEET, new Node(END, null)));
        assertPositions(joined, START, MEET, END);
        assertSame(teleport, joined.get(1).getTransport());
        assertNull(joined.get(2).getTransport());
    }

    @Test
    public void emptyAndSingleNodeRoutesRemainIndexedWithoutTransport()
    {
        assertTrue(LegacyRouteSteps.forward(null).isEmpty());
        Node root = new Node(START, null);
        assertPositions(LegacyRouteSteps.forward(root), START);
        List<PathStep> joined = LegacyRouteSteps.joined(root, new Node(START, null));
        assertPositions(joined, START);
        assertNull(joined.get(0).getTransport());
    }

    @Test
    public void legacyConstructorDescriptorAndCostsRemainUnchanged() throws Exception
    {
        assertNotNull(TransportNode.class.getConstructor(WorldPoint.class, Node.class, int.class));
        Node previous = new Node(pack(START), null, 17);
        WorldPoint otherPlane = new WorldPoint(START.getX(), START.getY(), 1);
        Transport selected = transport(START, otherPlane, "stairs", TransportType.TRANSPORT);
        TransportNode old = new TransportNode(otherPlane, previous, 0);
        TransportNode identified = new TransportNode(otherPlane, previous, 0, selected);
        assertEquals(17, old.cost);
        assertEquals(old.cost, identified.cost);
        assertNull(old.transport);
        assertSame(selected, identified.transport);
        assertEquals(22, new TransportNode(MEET, previous, 5, selected).cost);
    }

    @Test
    public void unknownTransportNodesFailClosedOnBothSidesOfJoin()
    {
        Node unknown = new TransportNode(MEET, new Node(START, null), 4);
        expectUnavailable(() -> LegacyRouteSteps.forward(unknown));
        expectUnavailable(() -> LegacyRouteSteps.forward(new Node(END, unknown)));
        expectUnavailable(() -> LegacyRouteSteps.joined(unknown, new Node(MEET, null)));
        Node reverseUnknown = new TransportNode(MEET, new Node(END, null), 4);
        expectUnavailable(() -> LegacyRouteSteps.joined(new Node(MEET, null), reverseUnknown));
    }

    @Test
    public void malformedMeetingsAndTransportRootsFailClosed()
    {
        Node root = new Node(START, null);
        expectUnavailable(() -> LegacyRouteSteps.joined(null, root));
        expectUnavailable(() -> LegacyRouteSteps.joined(root, null));
        expectUnavailable(() -> LegacyRouteSteps.joined(root, new Node(END, null)));
        Transport selected = transport(START, MEET, "transport", TransportType.TRANSPORT);
        Node malformedRoot = new TransportNode(MEET, null, 4, selected);
        expectUnavailable(() -> LegacyRouteSteps.forward(malformedRoot));
        expectUnavailable(() -> LegacyRouteSteps.joined(new Node(MEET, null), malformedRoot));
    }

    @Test
    public void collisionExpansionCarriesActualTransportInBothDirectionsWithoutCostChanges()
    {
        CollisionMap map = CollisionMap.frozenSnapshot(SplitFlagMap.fromResources(), null);
        PathfinderConfig config = mock(PathfinderConfig.class);
        when(config.getDistanceBeforeUsingTeleport()).thenReturn(7);
        when(config.getRestrictedPointsPacked()).thenReturn(Set.of());
        when(config.getCustomRestrictions()).thenReturn(List.of());
        PrimitiveIntHashMap<Set<Transport>> transports = new PrimitiveIntHashMap<>(1);
        when(config.getTransportsPacked()).thenReturn(transports);
        Transport ordinary = transport(START, MEET, "ordinary", TransportType.TRANSPORT);
        Transport teleport = transport(START, MEET, "teleport", TransportType.TELEPORTATION_SPELL);
        Transport originless = transport(null, MEET, "originless", TransportType.TELEPORTATION_SPELL);
        Set<Transport> selected = new LinkedHashSet<>(Arrays.asList(ordinary, teleport, originless));
        transports.put(pack(START), selected);
        Node root = new Node(pack(START), null, 11);
        List<Node> forward = new ArrayList<>(map.getNeighbors(root, new VisitedTiles(map), config, Set.of(pack(MEET))));
        assertExpanded(forward, ordinary, 15);
        assertExpanded(forward, teleport, 22);
        assertExpanded(forward, originless, 22);

        Node backwardRoot = new Node(pack(MEET), null, 13);
        List<Node> backward = map.getReverseNeighbors(backwardRoot, new VisitedTiles(map), config, Set.of(),
            Map.of(pack(MEET), selected));
        assertExpanded(backward, ordinary, 17);
        assertExpanded(backward, teleport, 24);
        assertEquals(2, backward.stream().filter(node -> node instanceof TransportNode).count());
    }

    private static void assertExpanded(List<Node> nodes, Transport transport, int cost)
    {
        TransportNode selected = (TransportNode) nodes.stream()
            .filter(node -> node instanceof TransportNode && ((TransportNode) node).transport == transport)
            .findFirst().orElseThrow(() -> new AssertionError("Selected transport identity missing"));
        assertEquals(cost, selected.cost);
    }

    private static Transport transport(WorldPoint origin, WorldPoint destination, String label, TransportType type)
    {
        return new Transport(origin, destination, label, type, false, 4);
    }

    private static int pack(WorldPoint point) { return WorldPointUtil.packWorldPoint(point); }

    private static void assertPositions(List<PathStep> steps, WorldPoint... points)
    {
        assertEquals(points.length, steps.size());
        for (int i = 0; i < points.length; i++) assertEquals("step " + i, pack(points[i]), steps.get(i).getPackedPosition());
    }

    private static void expectUnavailable(Runnable action)
    {
        try { action.run(); fail("Unknown provenance must fail closed"); }
        catch (IllegalStateException expected) { }
    }
}
