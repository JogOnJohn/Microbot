package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.poh.World330HostedHouseTransport;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.event.Level;

@Slf4j
public class Pathfinder implements Runnable {
    /**
     * Detailed pathfinder traces — set logger {@code net.runelite.client.plugins.microbot.shortestpath.pathfinder}
     * to DEBUG, or use {@link Microbot#log(org.slf4j.event.Level, String, Object...)} routing via Microbot.
     */
    private static void pathfinderDiag(String format, Object... args) {
        Microbot.log(Level.DEBUG, "[PathfinderDiag] " + format, args);
    }

    private static final Comparator<Node> NODE_ORDER = Comparator
            .comparingInt(Node::fCost)
            .thenComparingInt(n -> n.cost)
            .thenComparingInt(n -> n.tiebreaker);

    /**
     * Bidirectional search only for single-target routes at least this Chebyshev distance apart.
     * Medium-range paths often expand fewer nodes unidirectionally; very long routes (e.g. surface↔underground)
     * benefit from meet-in-the-middle. Wilderness {@code refreshTeleports} stays forward-only.
     */
    private static final int BIDIRECTIONAL_MIN_CHEBYSHEV = 2000;
    private PathfinderStats stats;
    @Getter
    private volatile boolean done = false;
    private volatile boolean cancelled = false;

    private final int start;
    private final Set<Integer> targets;
    // Primitive view of targets — iterated on every popped node in run().
    // Avoids autoboxing vs. iterating Set<Integer>.
    private final int[] targetsPacked;
    // Parallel to targetsPacked: Chebyshev radius at which a target counts as reached. 0 for
    // walkable goals (exact tile required). Goals on blocked tiles (object/NPC tiles — no cardinal
    // exit in the collision map) can never be popped by the search, so exact-match-only made every
    // such pathfind flood the entire reachable map until time-cutoff (observed: 1.4-2.1M nodes with
    // bestLast already adjacent to the goal), and let teleport-chain partials beat the trivial
    // walk-up. For those goals the radius is the smallest ring around the goal containing any
    // walkable tile, so the search exits the moment it genuinely stands next to the target.
    private final int[] targetAcceptRadius;
    private static final int MAX_BLOCKED_TARGET_ACCEPT_RADIUS = 3;

    private final PathfinderConfig config;
    private CollisionMap map;
    private final boolean targetInWilderness;

    // Walking subgraph uses A* (boundary is a PQ keyed on f = g + Chebyshev heuristic),
    // so among walking nodes the search picks the most promising direction first.
    // Transports stay in a separate PQ keyed on g-cost only — they're picked when their
    // travel cost is cheaper than any frontier walking node's g-cost, preserving the
    // existing "try cheap transports before walking farther" selection behavior.
    //
    // Comparator chain is (fCost, gCost, tiebreaker):
    //   1. fCost — standard A* primary ordering.
    //   2. gCost — required for correctness under early-discovery. addNeighbors() marks
    //      a neighbor visited at insert time (not at pop), so a node only ever enters
    //      the PQ once. If two equal-fCost nodes have different gCost, popping the
    //      higher-gCost one first would fix their shared neighbor's gCost to a
    //      suboptimal value (because visited is already set when the lower-g node later
    //      tries to discover the same neighbor). Preferring lower gCost on ties keeps
    //      early-discovery optimal.
    //   3. tiebreaker — per-node random. Among nodes with identical (f, g) — common in
    //      open-grid regions where many tiles share the same distance-from-start and
    //      distance-to-goal — this rotates the exploration order each run so paths
    //      diverge tile-by-tile between successive searches with the same endpoints.
    //      Kills the deterministic "identical route every trip" fingerprint.
    private final Queue<Node> boundary = new PriorityQueue<>(4096, NODE_ORDER);
    private final Queue<Node> pending = new PriorityQueue<>(256);
    private final Queue<Node> boundaryBackward = new PriorityQueue<>(4096, NODE_ORDER);
    private final Queue<Node> pendingBackward = new PriorityQueue<>(256);
    private VisitedTiles visited;

    private volatile List<WorldPoint> path = Collections.emptyList();
    private volatile List<WorldPoint> smoothedPath = Collections.emptyList();
    private volatile boolean pathNeedsUpdate = false;
    private volatile boolean smoothed = false;
    private volatile Node bestLastNode;
    private int focusedTeleportsEnqueued;
    private int focusedTeleportsExpanded;
    /** When set, {@link #getPath()} returns this list (bidirectional join or early exact hit). */
    private volatile List<WorldPoint> joinedPath;
    /**
     * Teleportation transports are updated when this changes.
     * Can be either:
     * 0 = all teleports can be used (e.g. Chronicle)
     * 20 = most teleports can be used (e.g. Varrock Teleport)
     * 30 = some teleports can be used (e.g. Amulet of Glory)
     * 31 = no teleports can be used
     */
    private int wildernessLevel;

    public Pathfinder(PathfinderConfig config, int start, Set<Integer> targets) {
        stats = new PathfinderStats();
        this.config = config;
        this.start = start;
        this.targets = targets;
        this.targetsPacked = new int[targets.size()];
        this.targetAcceptRadius = new int[targets.size()];
        int idx = 0;
        for (Integer t : targets) {
            this.targetsPacked[idx++] = t;
        }
        targetInWilderness = PathfinderConfig.isInWildernessPackedPoint(targets);
        wildernessLevel = 31;
        WebWalkLog.pf("created src={} dst={} config={}",
                WorldPointUtil.toString(this.start),
                WorldPointUtil.toString(this.targets),
                config
        );
    }

    /**
     * The start node's actual wilderness band, using the same ratchet checks as the search loop.
     * Searches previously seeded {@code refreshTeleports(start, 31)} ("assume deepest wilderness")
     * and immediately re-refreshed at the real level on the first expanded node — a wasted double
     * teleport refresh on EVERY pathfind, and the confusing tp_candidates wilderness=31
     * allowed=false -> wilderness=0 allowed=true log pairs on all non-wilderness walks. Computing
     * the start band once up front is identical to what the loop's first iteration produced.
     */
    private int initialWildernessLevel(int startPacked) {
        if (!PathfinderConfig.isInWilderness(startPacked)) {
            return 0;
        }
        if (!config.isInLevel20Wilderness(startPacked)) {
            return 20;
        }
        if (!config.isInLevel30Wilderness(startPacked)) {
            return 30;
        }
        return 31;
    }

    /**
     * 0 when the goal tile is walkable (exact match required). For blocked goal tiles, the
     * smallest Chebyshev ring around the goal containing at least one walkable same-plane tile,
     * capped at {@link #MAX_BLOCKED_TARGET_ACCEPT_RADIUS} (multi-tile objects put the clicked
     * tile up to a ring or two inside the footprint). Acceptance only fires on tiles the search
     * actually pops, so a walkable-but-unreachable ring tile can never produce a false arrival.
     */
    private int computeTargetAcceptRadius(int targetPacked) {
        int tx = WorldPointUtil.unpackWorldX(targetPacked);
        int ty = WorldPointUtil.unpackWorldY(targetPacked);
        int tz = WorldPointUtil.unpackWorldPlane(targetPacked);
        if (!map.isBlocked(tx, ty, tz)) {
            return 0;
        }
        for (int r = 1; r <= MAX_BLOCKED_TARGET_ACCEPT_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    if (!map.isBlocked(tx + dx, ty + dy, tz)) {
                        WebWalkLog.pf("goal_tile_blocked target={} acceptRadius={}",
                                WorldPointUtil.toString(targetPacked), r);
                        return r;
                    }
                }
            }
        }
        WebWalkLog.pf("goal_tile_blocked target={} no walkable ring within r={}; exact match required",
                WorldPointUtil.toString(targetPacked), MAX_BLOCKED_TARGET_ACCEPT_RADIUS);
        return 0;
    }

    /**
     * True when the tile and its entire Chebyshev-1 ring are blocked in the collision map — i.e.
     * nothing can ever walk out of or into it, not even via the puzzleAllow reverse-entry
     * exception. Raw instance coordinates (e.g. a POH start at (13211,285,1)) are off-map, so
     * every probe returns blocked and the tile reads as isolated.
     */
    private boolean isIsolatedInCollisionMap(int packed) {
        int x = WorldPointUtil.unpackWorldX(packed);
        int y = WorldPointUtil.unpackWorldY(packed);
        int z = WorldPointUtil.unpackWorldPlane(packed);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (!map.isBlocked(x + dx, y + dy, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Pathfinder(PathfinderConfig config, WorldPoint start, Set<WorldPoint> targets) {
        this(config, WorldPointUtil.packWorldPoint(start), targets.stream().map(WorldPointUtil::packWorldPoint).collect(Collectors.toSet()));
    }

    public Pathfinder(PathfinderConfig config, WorldPoint start, WorldPoint target) {
        this(config, start, Set.of(target));
    }

    public WorldPoint getStart() {
        return WorldPointUtil.unpackWorldPoint(start);
    }

    public Set<WorldPoint> getTargets() {
        return targets.stream().map(WorldPointUtil::unpackWorldPoint).collect(Collectors.toSet());
    }

    public void cancel() {
        cancelled = true;
    }

    public PathfinderStats getStats() {
        if (stats.started && stats.ended) {
            return stats;
        }

        // Don't give incomplete results
        return null;
    }

    public List<WorldPoint> getPath() {
        List<WorldPoint> joined = joinedPath;
        if (joined != null) {
            return joined;
        }
        Node lastNode = bestLastNode; // For thread safety, read bestLastNode once
        if (lastNode == null) {
            return path;
        }

        if (pathNeedsUpdate) {
            path = lastNode.getPath();
            pathNeedsUpdate = false;
            smoothed = false;
        }

        return path;
    }

    /**
     * Smoothed view of {@link #getPath()} for walker consumption. Collapses
     * straight-line runs of adjacent tiles using line-of-sight checks so that
     * the walker's main loop iterates fewer waypoints and issues fewer
     * minimap clicks. Falls back to the raw path while the pathfinder is
     * still running (smoothing only runs once after completion).
     */
    public List<WorldPoint> getWalkablePath() {
        List<WorldPoint> raw = getPath();
        if (!done || raw == null || raw.isEmpty()) {
            return raw;
        }
        if (!smoothed) {
            smoothedPath = PathSmoother.smooth(raw, map, buildTransportAnchors(raw), config.getBlockedTransportEdgesPacked());
            smoothed = true;
        }
        return smoothedPath;
    }

    // Tiles in the raw path that are transport origins or destinations. The smoother
    // must not collapse across these — some gates (e.g., Tutorial Island rat-cage
    // gates) aren't encoded as collision walls, so canStep would otherwise glide
    // past the transport edge and hide the gate from the walker.
    private Set<WorldPoint> buildTransportAnchors(List<WorldPoint> path) {
        Map<WorldPoint, Set<Transport>> transports = config.getTransports();
        if (transports == null || transports.isEmpty() || path == null || path.isEmpty()) {
            return Collections.emptySet();
        }
        Set<WorldPoint> anchors = new HashSet<>();
        for (WorldPoint p : path) {
            Set<Transport> fromP = transports.get(p);
            if (fromP == null) continue;
            anchors.add(p);
            for (Transport t : fromP) {
                WorldPoint dest = t.getDestination();
                if (dest != null) anchors.add(dest);
            }
        }
        return anchors;
    }

    private void addNeighbors(Node node) {
        List<Node> nodes = map.getNeighbors(node, visited, config, targets);
        boolean afterTransport = node instanceof TransportNode;
        for (Node neighbor : nodes) {
            if (config.avoidWilderness(node.packedPosition, neighbor.packedPosition, targetInWilderness)) {
                continue;
            }

            visited.set(neighbor.packedPosition);
            if (neighbor instanceof TransportNode) {
                pending.add(neighbor);
                ++stats.transportsChecked;
                logFocusedTeleportQueue("enqueued", node, neighbor,
                        boundary.peek() == null ? null : boundary.peek().cost);
            } else {
                neighbor.heuristic = afterTransport ? 0 : heuristicToNearestTarget(neighbor.packedPosition);
                boundary.add(neighbor);
                ++stats.nodesChecked;
            }
        }
    }

    private void logFocusedTeleportQueue(String event, Node from, Node to, Integer walkFrontierCost) {
        if (from == null || to == null) {
            return;
        }
        WorldPoint fromPoint = WorldPointUtil.unpackWorldPoint(from.packedPosition);
        List<Transport> matches = config.getTransports().getOrDefault(fromPoint, Collections.emptySet()).stream()
                .filter(transport -> transport.getOrigin() == null)
                .filter(transport -> transport.getDestination() != null
                        && WorldPointUtil.packWorldPoint(transport.getDestination()) == to.packedPosition)
                .filter(transport -> transport.getType() == TransportType.TELEPORTATION_SPELL
                        || transport instanceof World330HostedHouseTransport)
                .sorted(Comparator.comparing(Transport::getDisplayInfo,
                        Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            return;
        }
        if ("enqueued".equals(event)) {
            focusedTeleportsEnqueued++;
        } else if ("expanded".equals(event)) {
            focusedTeleportsExpanded++;
        }
        List<String> descriptions = matches.stream()
                .map(transport -> transport.getDisplayInfo()
                        + " type=" + transport.getType()
                        + " duration=" + transport.getDuration()
                        + " maxWild=" + transport.getMaxWildernessLevel())
                .collect(Collectors.toList());
        WebWalkLog.teleportQueue("event={} goal={} from={} to={} nodeCost={} walkFrontierCost={} candidates={}",
                event, WorldPointUtil.toString(targets), WorldPointUtil.toString(from.packedPosition),
                WorldPointUtil.toString(to.packedPosition), to.cost,
                walkFrontierCost == null ? "none" : walkFrontierCost, descriptions);
    }

    // Admissible A* heuristic: Chebyshev 2D to the nearest target, with a modulo-6400
    // fallback for the surface↔underground Y-offset convention (OSRS shifts underground
    // coords by +6400 on the Y axis, so Varrock sewers live at y≈9800 while Varrock sits
    // at y≈3400). Plain Chebyshev would claim ~6200 tiles to any underground point, which
    // misdirects A* into expanding the surface southward instead of routing through a
    // nearby ladder/stairs transport. Taking min(direct, mod-6400) stays admissible
    // because reaching a y-mirrored underground point still requires ≥ one transport
    // (cost ≥ 0) on top of the mod-6400 walking distance. The band-aware distance lives in
    // WorldPointUtil.undergroundAwareDistance so the walker uses the same metric.

    private int heuristicToNearestTarget(int packedPos) {
        return applyLandmarks(packedPos, baseHeuristicToNearestTarget(packedPos),
                fwdLandmark, fwdLandmarkResidual);
    }

    private int baseHeuristicToNearestTarget(int packedPos) {
        int posX = WorldPointUtil.unpackWorldX(packedPos);
        int posY = WorldPointUtil.unpackWorldY(packedPos);
        int best = Integer.MAX_VALUE;
        for (int target : targetsPacked) {
            int tx = WorldPointUtil.unpackWorldX(target);
            int ty = WorldPointUtil.unpackWorldY(target);
            int h = WorldPointUtil.undergroundAwareDistance(posX, posY, tx, ty);
            if (h < best) {
                best = h;
            }
        }
        return best;
    }

    private int heuristicFromStart(int packedPos) {
        return applyLandmarks(packedPos, baseHeuristicFromStart(packedPos),
                backLandmark, backLandmarkResidual);
    }

    private int baseHeuristicFromStart(int packedPos) {
        int posX = WorldPointUtil.unpackWorldX(packedPos);
        int posY = WorldPointUtil.unpackWorldY(packedPos);
        int sx = WorldPointUtil.unpackWorldX(start);
        int sy = WorldPointUtil.unpackWorldY(start);
        return WorldPointUtil.undergroundAwareDistance(posX, posY, sx, sy);
    }

    // --- Network-transport-aware heuristic ---------------------------------------------------
    //
    // Network transports (fairy rings, spirit trees, gnome gliders, quetzals) are fully-connected
    // hubs: reaching ANY origin lets you hop to ANY destination of that network for ~free. Plain
    // Chebyshev is blind to this — a node next to the Ardougne fairy ring reads "~1350 tiles from
    // the Farming Guild" by straight line, so A* buries the (optimal) cloak->fairy->CIR chain under
    // a single direct teleport that the heuristic makes look closer. We fold the hubs into the
    // heuristic as landmarks: for each enabled network whose destinations reach near the goal, every
    // network origin is a landmark with residual = min(dest -> goal). Then
    //     h(node) = min(directWalk, dist(node, nearestOrigin) + residual).
    // Each landmark term is a true lower bound (walking to the origin, a free-ish hop, then the
    // residual walk to goal), so taking min with the admissible Chebyshev keeps the result both
    // admissible AND consistent (the landmark set is fixed for the whole search). A* optimality is
    // therefore preserved, while the search is now pulled toward useful hubs instead of ignoring
    // them. The backward (bidirectional) arrays are symmetric: landmarks are destinations, residual
    // is min(origin -> start). Unlike the reverted chain-bridge injection this adds no graph edges
    // (so it can never teleport the player out of a building), and unlike the reverted post-transport
    // cascade it never zeroes the heuristic (so it can never collapse into a whole-map Dijkstra).
    private static final EnumSet<TransportType> NETWORK_HEURISTIC_TYPES = EnumSet.of(
            TransportType.FAIRY_RING, TransportType.SPIRIT_TREE,
            TransportType.GNOME_GLIDER, TransportType.QUETZAL);

    private int[] fwdLandmark = null;          // packed network origins (reach a hub -> hop toward target)
    private int[] fwdLandmarkResidual = null;  // parallel: that network's min(dest -> nearest target) Chebyshev
    private int[] backLandmark = null;         // packed network destinations (symmetric, for backward search)
    private int[] backLandmarkResidual = null; // parallel: that network's min(origin -> start) Chebyshev

    private int applyLandmarks(int packedPos, int base, int[] landmarks, int[] residuals) {
        if (landmarks == null || landmarks.length == 0) {
            return base;
        }
        int px = WorldPointUtil.unpackWorldX(packedPos);
        int py = WorldPointUtil.unpackWorldY(packedPos);
        int best = base;
        for (int i = 0; i < landmarks.length; i++) {
            int lx = WorldPointUtil.unpackWorldX(landmarks[i]);
            int ly = WorldPointUtil.unpackWorldY(landmarks[i]);
            int viaHub = Math.max(Math.abs(px - lx), Math.abs(py - ly)) + residuals[i];
            if (viaHub < best) {
                best = viaHub;
            }
        }
        return best;
    }

    /**
     * Builds {@link #fwdLandmark}/{@link #backLandmark} once per pathfind from the enabled network
     * transports. A network only contributes landmarks if it gets you strictly closer to the goal
     * (resp. start) than you already are — otherwise it is pure heuristic overhead with no benefit.
     */
    private void computeNetworkLandmarks() {
        Map<WorldPoint, Set<Transport>> all = config.getTransports();
        if (all == null || all.isEmpty()) {
            return;
        }

        EnumMap<TransportType, Set<Integer>> originsByType = new EnumMap<>(TransportType.class);
        EnumMap<TransportType, Set<Integer>> destsByType = new EnumMap<>(TransportType.class);
        for (Set<Transport> set : all.values()) {
            if (set == null) {
                continue;
            }
            for (Transport t : set) {
                TransportType type = t.getType();
                if (type == null || !NETWORK_HEURISTIC_TYPES.contains(type)) {
                    continue;
                }
                WorldPoint o = t.getOrigin();
                WorldPoint d = t.getDestination();
                if (o == null || d == null) {
                    continue;
                }
                originsByType.computeIfAbsent(type, k -> new HashSet<>()).add(WorldPointUtil.packWorldPoint(o));
                destsByType.computeIfAbsent(type, k -> new HashSet<>()).add(WorldPointUtil.packWorldPoint(d));
            }
        }
        if (originsByType.isEmpty()) {
            return;
        }

        int startToGoal = minChebyshevStartToAnyTarget();
        List<int[]> fwd = new ArrayList<>();   // {originPacked, residual}
        List<int[]> back = new ArrayList<>();  // {destPacked, residual}
        for (Map.Entry<TransportType, Set<Integer>> e : originsByType.entrySet()) {
            Set<Integer> origins = e.getValue();
            Set<Integer> dests = destsByType.getOrDefault(e.getKey(), Collections.emptySet());
            if (origins.isEmpty() || dests.isEmpty()) {
                continue;
            }

            int residualFwd = Integer.MAX_VALUE;
            for (int d : dests) {
                residualFwd = Math.min(residualFwd, baseHeuristicToNearestTarget(d));
            }
            if (residualFwd < startToGoal) {
                for (int o : origins) {
                    fwd.add(new int[]{o, residualFwd});
                }
            }

            int residualBack = Integer.MAX_VALUE;
            for (int o : origins) {
                residualBack = Math.min(residualBack, baseHeuristicFromStart(o));
            }
            if (residualBack < startToGoal) {
                for (int d : dests) {
                    back.add(new int[]{d, residualBack});
                }
            }
        }

        fwdLandmark = packLandmarkPositions(fwd);
        fwdLandmarkResidual = packLandmarkResiduals(fwd);
        backLandmark = packLandmarkPositions(back);
        backLandmarkResidual = packLandmarkResiduals(back);
    }

    private static int[] packLandmarkPositions(List<int[]> landmarks) {
        int[] out = new int[landmarks.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = landmarks.get(i)[0];
        }
        return out;
    }

    private static int[] packLandmarkResiduals(List<int[]> landmarks) {
        int[] out = new int[landmarks.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = landmarks.get(i)[1];
        }
        return out;
    }

    private int minChebyshevStartToAnyTarget() {
        int best = Integer.MAX_VALUE;
        for (int t : targetsPacked) {
            int d = Math.max(
                    Math.abs(WorldPointUtil.unpackWorldX(start) - WorldPointUtil.unpackWorldX(t)),
                    Math.abs(WorldPointUtil.unpackWorldY(start) - WorldPointUtil.unpackWorldY(t)));
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    private void buildIncomingByDestination(Map<Integer, Set<Transport>> out) {
        out.clear();
        for (Map.Entry<WorldPoint, Set<Transport>> e : config.getTransports().entrySet()) {
            for (Transport t : e.getValue()) {
                if (t.getDestination() == null || t.getOrigin() == null) {
                    continue;
                }
                int dp = WorldPointUtil.packWorldPoint(t.getDestination());
                out.computeIfAbsent(dp, k -> new HashSet<>()).add(t);
            }
        }
    }

    private static void maybeImproveMeeting(Node forwardNode, Node backwardNode, long[] bestCost, Node[] bestForward, Node[] bestBackward) {
        long sum = (long) forwardNode.cost + (long) backwardNode.cost;
        if (sum < bestCost[0]) {
            bestCost[0] = sum;
            bestForward[0] = forwardNode;
            bestBackward[0] = backwardNode;
        }
    }

    private List<WorldPoint> combineBidirectionalPath(Node forwardAtMeet, Node backwardAtMeet) {
        List<WorldPoint> head = forwardAtMeet.getPath();
        List<WorldPoint> full = new ArrayList<>(head.size() + 64);
        full.addAll(head);
        for (Node n = backwardAtMeet.previous; n != null; n = n.previous) {
            full.add(WorldPointUtil.unpackWorldPoint(n.packedPosition));
        }
        return full;
    }

    private void addNeighborsForwardWithMeet(Node node, Map<Integer, Node> forwardAt, Map<Integer, Node> backwardAt,
            long[] bestMeetingCost, Node[] meetF, Node[] meetB) {
        List<Node> nodes = map.getNeighbors(node, visited, config, targets);
        boolean afterTransport = node instanceof TransportNode;
        for (Node neighbor : nodes) {
            if (config.avoidWilderness(node.packedPosition, neighbor.packedPosition, targetInWilderness)) {
                continue;
            }

            visited.set(neighbor.packedPosition);
            if (neighbor instanceof TransportNode) {
                pending.add(neighbor);
                ++stats.transportsChecked;
                logFocusedTeleportQueue("enqueued", node, neighbor,
                        boundary.peek() == null ? null : boundary.peek().cost);
            } else {
                neighbor.heuristic = afterTransport ? 0 : heuristicToNearestTarget(neighbor.packedPosition);
                boundary.add(neighbor);
                ++stats.nodesChecked;
            }
            forwardAt.putIfAbsent(neighbor.packedPosition, neighbor);
            Node b = backwardAt.get(neighbor.packedPosition);
            if (b != null) {
                maybeImproveMeeting(neighbor, b, bestMeetingCost, meetF, meetB);
            }
        }
    }

    private void addNeighborsBackwardWithMeet(Node node, VisitedTiles visitedB, Map<Integer, Set<Transport>> incoming,
            Set<Integer> puzzleAllow, Map<Integer, Node> forwardAt, Map<Integer, Node> backwardAt,
            long[] bestMeetingCost, Node[] meetF, Node[] meetB) {
        List<Node> nodes = map.getReverseNeighbors(node, visitedB, config, puzzleAllow, incoming);
        boolean afterTransport = node instanceof TransportNode;
        for (Node pred : nodes) {
            if (config.avoidWilderness(pred.packedPosition, node.packedPosition, targetInWilderness)) {
                continue;
            }

            visitedB.set(pred.packedPosition);
            if (pred instanceof TransportNode) {
                pendingBackward.add(pred);
                ++stats.transportsChecked;
            } else {
                pred.heuristic = afterTransport ? 0 : heuristicFromStart(pred.packedPosition);
                boundaryBackward.add(pred);
                ++stats.nodesChecked;
            }
            backwardAt.putIfAbsent(pred.packedPosition, pred);
            Node f = forwardAt.get(pred.packedPosition);
            if (f != null) {
                maybeImproveMeeting(f, pred, bestMeetingCost, meetF, meetB);
            }
        }
    }

    private void runUnidirectional() {
        Node startNode = new Node(start, null);
        startNode.heuristic = heuristicToNearestTarget(start);
        boundary.add(startNode);

        int bestDistance = Integer.MAX_VALUE;
        long bestHeuristic = Integer.MAX_VALUE;
        long cutoffDurationMillis = config.getCalculationCutoffMillis();
        long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
        wildernessLevel = initialWildernessLevel(start);
        config.refreshTeleports(start, wildernessLevel);
        boolean reachedGoal = false;
        boolean timedOut = false;
        while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty())) {
            Node b = boundary.peek();
            Node p = pending.peek();
            Node node;
            boolean expandingTransport = p != null && (b == null || p.cost < b.cost);
            if (expandingTransport) {
                node = pending.poll();
                logFocusedTeleportQueue("expanded", node.previous, node, b == null ? null : b.cost);
            } else {
                node = boundary.poll();
            }

            if (wildernessLevel > 0) {
                boolean update = false;

                if (wildernessLevel > 30 && !config.isInLevel30Wilderness(node.packedPosition)) {
                    wildernessLevel = 30;
                    update = true;
                }
                if (wildernessLevel > 20 && !config.isInLevel20Wilderness(node.packedPosition)) {
                    wildernessLevel = 20;
                    update = true;
                }
                if (wildernessLevel > 0 && !PathfinderConfig.isInWilderness(node.packedPosition)) {
                    wildernessLevel = 0;
                    update = true;
                }
                if (update) {
                    config.refreshTeleports(node.packedPosition, wildernessLevel);
                }
            }

            final int nodePos = node.packedPosition;
            boolean reached = false;
            for (int i = 0; i < targetsPacked.length; i++) {
                int target = targetsPacked[i];
                if (nodePos == target
                        || (targetAcceptRadius[i] > 0
                        && WorldPointUtil.distanceBetween(nodePos, target) <= targetAcceptRadius[i])) {
                    bestLastNode = node;
                    pathNeedsUpdate = true;
                    reached = true;
                    break;
                }
                int distance = WorldPointUtil.distanceBetween(nodePos, target);
                long heuristic = distance + (long) WorldPointUtil.distanceBetween(nodePos, target, 2);
                if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {
                    bestLastNode = node;
                    pathNeedsUpdate = true;
                    bestDistance = distance;
                    bestHeuristic = heuristic;
                    cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
                }
            }
            if (reached) {
                reachedGoal = true;
                break;
            }

            if (System.currentTimeMillis() > cutoffTimeMillis) {
                timedOut = true;
                WebWalkLog.pf("cutoff bestDist={} nodes={}", bestDistance, stats.getNodesChecked());
                break;
            }

            addNeighbors(node);
        }

        String uniExit = cancelled ? "cancelled"
                : reachedGoal ? "reached-goal"
                : timedOut ? "time-cutoff"
                : (boundary.isEmpty() && pending.isEmpty()) ? "queues-drained" : "loop-ended";
        pathfinderDiag("uni finished exit=%s cancelled=%s boundaryEmpty=%s pendingEmpty=%s bestLastNode=%s cutoffMs=%d",
                uniExit,
                cancelled,
                boundary.isEmpty(),
                pending.isEmpty(),
                bestLastNode == null ? "null" : WorldPointUtil.toString(bestLastNode.packedPosition),
                config.getCalculationCutoffMillis());

        WebWalkLog.pf("uni_loop_exit cancelled={} bEmpty={} pEmpty={} bestLast={}",
                cancelled, boundary.isEmpty(), pending.isEmpty(),
                bestLastNode == null ? "null" : WorldPointUtil.toString(bestLastNode.packedPosition));
        WebWalkLog.teleportQueue("exit mode=uni goal={} reason={} nodes={} transports={} focusedEnqueued={} focusedExpanded={} pending={} bestLast={}",
                WorldPointUtil.toString(targets), uniExit, stats.getNodesChecked(), stats.getTransportsChecked(),
                focusedTeleportsEnqueued, focusedTeleportsExpanded, pending.size(),
                bestLastNode == null ? "null" : WorldPointUtil.toString(bestLastNode.packedPosition));
    }

    private void runBidirectional() {
        int goalPacked = targetsPacked[0];
        Map<Integer, Set<Transport>> incoming = new HashMap<>(512);
        buildIncomingByDestination(incoming);

        Set<Integer> puzzleAllow = new HashSet<>(targets.size() + 1);
        for (int t : targetsPacked) {
            puzzleAllow.add(t);
        }
        puzzleAllow.add(start);

        VisitedTiles visitedB = new VisitedTiles(map);
        Map<Integer, Node> forwardAt = new HashMap<>(4096);
        Map<Integer, Node> backwardAt = new HashMap<>(4096);
        long[] bestMeetingCost = new long[]{Long.MAX_VALUE};
        Node[] meetF = new Node[1];
        Node[] meetB = new Node[1];

        Node startNode = new Node(start, null);
        startNode.heuristic = heuristicToNearestTarget(start);
        boundary.add(startNode);
        forwardAt.put(start, startNode);

        Node goalNode = new Node(goalPacked, null);
        goalNode.heuristic = heuristicFromStart(goalPacked);
        boundaryBackward.add(goalNode);
        backwardAt.put(goalPacked, goalNode);

        int bestDistance = Integer.MAX_VALUE;
        long bestHeuristic = Integer.MAX_VALUE;
        long cutoffDurationMillis = config.getCalculationCutoffMillis();
        long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
        wildernessLevel = initialWildernessLevel(start);
        config.refreshTeleports(start, wildernessLevel);

        // Once one side's frontier is dead and its anchor tile is isolated in the collision map,
        // no join can ever form — the other side would flood to time-cutoff for nothing (live:
        // POH instance start (13211,285,1) with no usable teleports left the backward search
        // expanding 785k nodes before giving up with an empty path).
        final boolean startIsolated = isIsolatedInCollisionMap(start);
        final boolean goalIsolated = isIsolatedInCollisionMap(goalPacked);

        while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty() || !boundaryBackward.isEmpty() || !pendingBackward.isEmpty())) {
            if (bestMeetingCost[0] == Long.MAX_VALUE) {
                if (startIsolated && boundary.isEmpty() && pending.isEmpty()) {
                    WebWalkLog.pf("bidir_abort reason=forward-dead-isolated-start start={} nodes={}",
                            WorldPointUtil.toString(start), stats.getNodesChecked());
                    break;
                }
                if (goalIsolated && boundaryBackward.isEmpty() && pendingBackward.isEmpty()) {
                    WebWalkLog.pf("bidir_abort reason=backward-dead-isolated-goal goal={} nodes={}",
                            WorldPointUtil.toString(goalPacked), stats.getNodesChecked());
                    break;
                }
            }
            if (!boundary.isEmpty() || !pending.isEmpty()) {
                Node b = boundary.peek();
                Node p = pending.peek();
                Node node;
                boolean expandingTransport = p != null && (b == null || p.cost < b.cost);
                if (expandingTransport) {
                    node = pending.poll();
                    logFocusedTeleportQueue("expanded", node.previous, node, b == null ? null : b.cost);
                } else {
                    node = boundary.poll();
                }

                if (wildernessLevel > 0) {
                    boolean update = false;
                    if (wildernessLevel > 30 && !config.isInLevel30Wilderness(node.packedPosition)) {
                        wildernessLevel = 30;
                        update = true;
                    }
                    if (wildernessLevel > 20 && !config.isInLevel20Wilderness(node.packedPosition)) {
                        wildernessLevel = 20;
                        update = true;
                    }
                    if (wildernessLevel > 0 && !PathfinderConfig.isInWilderness(node.packedPosition)) {
                        wildernessLevel = 0;
                        update = true;
                    }
                    if (update) {
                        config.refreshTeleports(node.packedPosition, wildernessLevel);
                    }
                }

                final int nodePos = node.packedPosition;
                if (nodePos == goalPacked
                        || (targetAcceptRadius[0] > 0
                        && WorldPointUtil.distanceBetween(nodePos, goalPacked) <= targetAcceptRadius[0])) {
                    joinedPath = node.getPath();
                    pathNeedsUpdate = false;
                    bestLastNode = null;
                    WebWalkLog.pf("bidir forward_hit_goal");
                    break;
                }

                for (int target : targetsPacked) {
                    int distance = WorldPointUtil.distanceBetween(nodePos, target);
                    long heuristic = distance + (long) WorldPointUtil.distanceBetween(nodePos, target, 2);
                    if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {
                        bestLastNode = node;
                        pathNeedsUpdate = true;
                        bestDistance = distance;
                        bestHeuristic = heuristic;
                        cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
                    }
                }

                addNeighborsForwardWithMeet(node, forwardAt, backwardAt, bestMeetingCost, meetF, meetB);
            }

            if (joinedPath != null) {
                break;
            }

            if (!boundaryBackward.isEmpty() || !pendingBackward.isEmpty()) {
                Node b = boundaryBackward.peek();
                Node p = pendingBackward.peek();
                Node node;
                if (p != null && (b == null || p.cost < b.cost)) {
                    node = pendingBackward.poll();
                } else {
                    node = boundaryBackward.poll();
                }

                if (node.packedPosition == start) {
                    joinedPath = combineBidirectionalPath(forwardAt.get(start), node);
                    pathNeedsUpdate = false;
                    bestLastNode = null;
                    WebWalkLog.pf("bidir backward_hit_start");
                    break;
                }

                addNeighborsBackwardWithMeet(node, visitedB, incoming, puzzleAllow, forwardAt, backwardAt, bestMeetingCost, meetF, meetB);
            }

            if (System.currentTimeMillis() > cutoffTimeMillis) {
                WebWalkLog.pf("bidir_cutoff nodes={}", stats.getNodesChecked());
                break;
            }
        }

        if (joinedPath == null && meetF[0] != null && meetB[0] != null && bestMeetingCost[0] < Long.MAX_VALUE) {
            joinedPath = combineBidirectionalPath(meetF[0], meetB[0]);
            pathNeedsUpdate = false;
            bestLastNode = null;
            WebWalkLog.pf("bidir meet_at={} cost={}",
                    WorldPointUtil.toString(meetF[0].packedPosition), bestMeetingCost[0]);
        }

        pathfinderDiag("bidir finished joinedPath=%s meetCost=%s forwardFrontier=%d/%d backwardFrontier=%d/%d cancelled=%s",
                joinedPath == null ? "null" : joinedPath.size(),
                bestMeetingCost[0] == Long.MAX_VALUE ? "n/a" : bestMeetingCost[0],
                boundary.size(),
                pending.size(),
                boundaryBackward.size(),
                pendingBackward.size(),
                cancelled);

        WebWalkLog.pf("bidir_exit joined={} meetCost={}",
                joinedPath == null ? "null" : Integer.toString(joinedPath.size()),
                bestMeetingCost[0] == Long.MAX_VALUE ? "n/a" : Long.toString(bestMeetingCost[0]));
        WebWalkLog.teleportQueue("exit mode=bidir goal={} joined={} meetCost={} nodes={} transports={} focusedEnqueued={} focusedExpanded={} pending={}",
                WorldPointUtil.toString(targets), joinedPath == null ? "null" : joinedPath.size(),
                bestMeetingCost[0] == Long.MAX_VALUE ? "n/a" : bestMeetingCost[0],
                stats.getNodesChecked(), stats.getTransportsChecked(), focusedTeleportsEnqueued,
                focusedTeleportsExpanded, pending.size());
    }

    @Override
    public void run() {
        if (Microbot.getClientThread() != null && Microbot.getClientThread().isClientThread()) {
            path = Collections.emptyList();
            smoothedPath = Collections.emptyList();
            joinedPath = null;
            stats.start();
            stats.end();
            done = true;
            log.error("[Pathfinder] Refusing to run route search on the client thread");
            return;
        }
        WebWalkLog.pf("run_start src={} dst={} cutoffMs={}",
                WorldPointUtil.toString(start), WorldPointUtil.toString(targets), config.getCalculationCutoffMillis());
        joinedPath = null;
        // Pathfinder instances are commonly constructed on the client thread and submitted to the
        // shortest-path executor. Resolve both ThreadLocal-backed objects here so the collision map,
        // visited state and pinned live snapshot all belong to the search thread for this run.
        map = config.getMap();
        for (int i = 0; i < targetsPacked.length; i++) {
            targetAcceptRadius[i] = computeTargetAcceptRadius(targetsPacked[i]);
        }
        visited = new VisitedTiles(map);
        // Pin the live-collision snapshot for this whole search so a mid-search swap on the client
        // thread cannot mix two scenes into one path. No-op when live collision is disabled.
        map.beginSearch();
        try {
            stats.start();
            computeNetworkLandmarks();
            int minCheb = minChebyshevStartToAnyTarget();
            boolean useBidir = targetsPacked.length == 1
                    && minCheb >= BIDIRECTIONAL_MIN_CHEBYSHEV;
            pathfinderDiag("run mode decision useBidir=%s minCheb=%d bidirThreshold=%d targetsPacked=%d cutoffMs=%d cancelAlready=%s",
                    useBidir,
                    minCheb,
                    BIDIRECTIONAL_MIN_CHEBYSHEV,
                    targetsPacked.length,
                    config.getCalculationCutoffMillis(),
                    cancelled);
            if (useBidir) {
                WebWalkLog.pf("mode bidir cheb>={}", BIDIRECTIONAL_MIN_CHEBYSHEV);
                runBidirectional();
            } else {
                runUnidirectional();
            }
        } catch (Exception e) {
            log.error("[Pathfinder] Exception in run(): ", e);
        } finally {
            done = !cancelled;

            boundary.clear();
            pending.clear();
            boundaryBackward.clear();
            pendingBackward.clear();
            visited.clear();

            stats.end();

            WebWalkLog.pf("run_done done={} cancelled={} stats={}",
                    done, cancelled, getStats() != null ? getStats().toString() : "null");
        }
    }

    public static class PathfinderStats {
        @Getter
        private int nodesChecked = 0, transportsChecked = 0;
        private long startNanos, endNanos;
        private volatile boolean started = false, ended = false;

        public int getTotalNodesChecked() {
            return nodesChecked + transportsChecked;
        }

        public long getElapsedTimeNanos() {
            return endNanos - startNanos;
        }

        private void start() {
            started = true;
            nodesChecked = 0;
            transportsChecked = 0;
            startNanos = System.nanoTime();
        }

        private void end() {
            endNanos = System.nanoTime();
            ended = true;
        }

        @Override
        public String toString() {
            return String.format("PathfinderStats(nodes=%d,transports=%d,time=%dms)", nodesChecked, transportsChecked, getElapsedTimeNanos() / 1_000_000);
        }
    }
}
