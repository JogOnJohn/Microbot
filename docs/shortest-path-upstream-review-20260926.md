# Shortest-path upstream review, 2026-09-26

Reviewed Skretzo/shortest-path master from 6ca996a41a to
3086b0bb178dde72c9622709a701aee75ba8337f. The paired official tooling revision is
4376662bb0cb47ac38a7533d7e6889999f72ec9f. Collision SHA-256:
dbd8d549b7c5056f11630c523109e0bb4dc050e16292a6d5a372d19aab2a1c25.

## Adopted

Updated the paired collision map and nine transport categories: agility shortcuts,
boats, charter ships, ships, teleportation boxes/items/minigames/spells and generic
transports. The semantic review found 292 additions, 11 removals and 185 changes
(181 requirement changes, two endpoint moves, no duration or adjacency changes).
Examples include the Lumbridge stepping stone coordinates, Ancient Cavern entrance,
Iban's Lair anchors, Blast Furnace and Mount Karuulm landing points, Mage Arena
levers, Kharazi axe/machete requirements, boat access requirements and additional
stairs/ladders/bridges. Display labels now match Grand Exchange and Entrana actions.

All 37 existing local overrides survive. A 38th override repairs the Villa Lucens
Pass-through Entryway edge (1425 2933 0 -> 1427 2933 0) with object ID 54707.
The initial candidate excluded this ID-less upstream row. Subsequent cache research
confirmed Entryway 54707 has Pass-through and is placed at 1426 2933 0, directly
between the endpoints. The PATCH preserves upstream's Death on the Isle completion
requirement, one-way direction and two-tick duration. Evidence:
https://raw.githubusercontent.com/mejrs/data_osrs/refs/heads/master/location_configs/54707.json
and https://raw.githubusercontent.com/mejrs/data_osrs/refs/heads/master/locations/54707.json.
The unchanged categories and Microbot-only
blocked_edges, dangerous_tiles, npcs and restrictions files were preserved.

The standalone converter and the vendored manifest/overrides use the same pins.
Post-adoption conversion yields zero additions, removals or changes.

## Code changes requiring separate adaptation

Upstream's multi-target unreachable acceptance changes apply to its NodeGraph
engine; this fork uses a different protected engine with live collision and
sealed-target handling. Direct transplantation would change route termination.

POH per-portal/mounted-item settings and live Nexus keybind overlays require
adaptation to this fork's custom Portal Nexus value mapping, hosted W330 houses,
PohPanel and PreferredTeleportAssistant. They were not transplanted.

Combination-staff satisfaction and inventory/bank/spell highlights use upstream's
TransportItems, ItemVariations and BankPickupRequirements classes. This fork uses
Microbot inventory/equipment/magic utilities and its own requirement policy.
They need a separate implementation and regression review rather than copied files.
The upstream cooldown change adds a testable clock parameter without changing the
existing cooldown rule, so it was not needed for this data adoption.

No walker, W330 routing, Nexus mapping, manual-run control or minimap behavior was
changed by this update. The playable branch includes the prior maximum-zoom patch.

## Validation

The standalone converter's 15 tests pass. Candidate parser and collision endpoint
checks pass against the pre-adoption catalog. The first candidate was rejected for
the ID-less Entryway; the ID repair fixes that incompatibility without weakening checks.
Final :client:compileJava and :client:validateTransportSync pass. All 159 selected
Java tests pass with no skips: golden-route baseline (3 tests containing the route
corpus), transport resource loading (1), W330 merging (4), Portal Nexus config (2),
preferred teleport hints (11), and walker regressions (138).
:client:microbotReleaseJar produced microbot-2.6.24.jar on Bizza.
Live movement remains to be checked after launch.
