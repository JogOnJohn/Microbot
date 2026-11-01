package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.client.plugins.microbot.shortestpath.Transport;

/** Indexed legacy route provenance, without endpoint matching or executor selection. */
public final class LegacyRouteSteps
{
    private LegacyRouteSteps() { }

    /** The transport on a forward node produced the edge arriving at that node. */
    public static List<PathStep> forward(Node terminal)
    {
        List<PathStep> steps = new ArrayList<>();
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node = terminal; node != null; node = node.previous)
        {
            if (!seen.add(node)) throw new IllegalStateException("Cyclic forward node chain");
            steps.add(new PathStep(node.packedPosition, false, selectedTransport(node)));
        }
        Collections.reverse(steps);
        return List.copyOf(steps);
    }

    /**
     * Both meeting nodes represent the same tile. A backward transport node describes the
     * forward edge from itself to its previous node, so its identity moves to that arrival step.
     */
    public static List<PathStep> joined(Node forwardAtMeet, Node backwardAtMeet)
    {
        if (forwardAtMeet == null || backwardAtMeet == null
            || forwardAtMeet.packedPosition != backwardAtMeet.packedPosition)
        {
            throw new IllegalStateException("Missing or mismatched legacy meeting nodes");
        }
        List<PathStep> steps = new ArrayList<>(forward(forwardAtMeet));
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node = backwardAtMeet; node != null; node = node.previous)
        {
            if (!seen.add(node)) throw new IllegalStateException("Cyclic backward node chain");
            Transport transport = selectedTransport(node);
            if (node.previous != null)
            {
                steps.add(new PathStep(node.previous.packedPosition, false, transport));
            }
        }
        return List.copyOf(steps);
    }

    private static Transport selectedTransport(Node node)
    {
        if (!(node instanceof TransportNode)) return null;
        Transport transport = ((TransportNode) node).transport;
        if (transport == null || node.previous == null)
        {
            throw new IllegalStateException("Legacy transport node has no selected edge or predecessor");
        }
        return transport;
    }
}
