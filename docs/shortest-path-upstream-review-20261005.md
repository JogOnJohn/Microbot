# Shortest-path upstream adoption: 2026-10-05

## Official pair

- Skretzo/shortest-path: `18983976962d5f0d28c4a232ce0da7b44b4fbadd` (1.20.8).
- osrs-pathfinding/shortest-path-tooling: `76027cf82ee96c76db956cc5314b33a40e5e2dab`.
- Paired collision SHA-256: `c258238954e84e80cd9403fbd1acd78aef8cee81955cbd2446f36a3c70b0118b`.

## Adopted

Twenty semantically changed transport categories and the paired collision map were converted,
reviewed and adopted. Pre-adoption comparison: 803 added, 182 removed, 1614 changed records;
819 duration changes, 676 requirement changes, 139 endpoint moves and no adjacency delta.
Counts include conditional variants and coin-field normalization, not just physical connections.
Regeneration against the adopted resources produces zero semantic changes.

Upstream item variants, requirement parser and evaluator are adapted to our Transport API.
AND groups, alternative quantities, zero-quantity exclusions and optional unlocks survive parsing.
Standalone coin fares become the existing Currency field, preserving walker fare handling.
Maximum total level, maximum quest points and combat gates are enforced rather than discarded.
Declared canoe axe, Dragontooth and Xeric's Honour unlocks default off.
Bank planning retains quantities and collects all required groups. Minimap rotation uses UNIT14.

## Preserved and deferred

Our pathfinder engine, walker execution, W330/Nexus handling, automatic-run default, minimap
zoom policy and QuestHelper behavior are preserved. No wholesale upstream bank-routing engine,
overlay, POH execution or config replacement was merged.

All existing overrides, including Entryway 54707, are retained. Two new Stalker Den railing
origins (1638,3808,1 and 1638,3806,2) are deferred via explicit DELETE overrides: upstream permits
interaction at range, but these origins have sealed cardinal rings in the paired collision map
and our graph cannot enter them. These are valid upstream exceptions, not bad upstream data.
The other reachable railing rows remain. There are 40 durable overrides in total.
Microbot-owned blocked_edges, dangerous_tiles, npcs and restrictions remain byte-identical.

## Validation and artifacts

The converter Python suite, client unit suite and fresh generated-resource validator are required.
The validator checks paired collision data, requirement/quest fidelity, local-only files and
payload provenance hashes. The Tempoross partial-route fixture now uses the actual three-tile
arrival limit: the refreshed map gets within five tiles, but still does not reach the target.

Produce `:client:microbotReleaseJar` from the committed playable source. Record the resulting
source commit and JAR SHA-256 outside the tracked source. A build is not live gameplay validation.
Do not restart the running client or overwrite its pinned interactive runtime artifact.
