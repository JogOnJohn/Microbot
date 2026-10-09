# Shortest-path upstream adoption: 2026-10-10

## Reviewed source

Reviewed Skretzo/shortest-path master from `18983976962d5f0d28c4a232ce0da7b44b4fbadd`
through `457f94f35b`. The official tooling checkout
`be0fdca3fdc029684cf19865510c418fdba5c11e` pins data revision
`3a55ac9be3f7481c7cac30d86255789aabb2b3ca`; the remaining master changes affect
only plugin version/author metadata. The adopted collision SHA-256 is
`b630dcb51c05b390e46ccf92c28e96c2eec293e27be9781f57d6d7ec14e62b01`.

## Adopted

- `1bb464890d`: Mos Le'Harmless stepping stones cross directly between valid
  ground endpoints, retaining 82 Agility and Cabin Fever requirements.
- `ca1a2dda30`: Ape Atoll bridge jump 4745 lands at (2806,2724,0), with its
  original duration and requirements.
- Updated collision archive together with its four-flags-per-tile reader stride.
  Movement remains flags 0/1; flags 2/3 preserve structural wall/door evidence.
  This format change is required by `7324ec3331`, even without its goal resolver.
- `aa502e75d6`: insertion-ordered transport resource loading and permutation
  expansion. Runtime filtering and the concurrent teleport pool retain their
  existing behavior; this is loader determinism, not deterministic walker paths.

Pre-adoption semantic delta: 2 added, 4 removed, 1 changed; 1 endpoint move,
no requirement, duration or adjacency changes. Generated output retains all
46 local overrides and all four Microbot-only resource files. Regeneration
after adoption gives zero semantic drift and matching collision hashes.

## Reviewed but deferred

- The optional exact backend and its routing cuts, canonicalized walking legs,
  click-point overlays, search lifecycle and debug/config integration require
  their own planner compatibility work. No new backend or routing-cuts resource
  is imported here.
- `afd9bd856d` delays transport visits until dequeue and gives pending transports
  priority on equal cost. It is a useful correctness fix for upstream's
  cost-ordered boundary, but our forward/backward A* queues, early walking visits
  and meeting logic differ. A direct transplant is not a proven equivalent;
  testing relaxation and both search directions belongs in a focused planner fix.
- `7324ec3331`/`3a55ac9be3` resolve blocked goals using wall boundaries and nearby
  connectivity. Our planner already has same-plane blocked-target acceptance
  and sealed-start handling. Replacing that policy would change route arrival
  behavior and requires wall-side and interaction-distance regression scenarios.
- TSV lint tooling, author metadata and upstream plugin versioning do not change
  the vendored runtime. Microbot's own release version remains unchanged.

## Validation and runtime boundary

Run both converter suites, fresh `validateTransportSync`, and the shortest-path,
walker, POH and client-thread tests on spike and playable. New regressions cover
the four-flag stride, plane counts, corrected transport landings and loader order.
The generated-catalog endpoint ratchet alone cannot detect a map stride mismatch;
the format tests prevent that false-positive validation.

The vendored converter's obsolete AXE/MACHETE test now verifies retention of the
AND expression expected by the current runtime parser, matching the standalone
converter contract. No converter production behavior changed.

Preserve the previous walker efficiency changes, W330/Nexus policy, minimap zoom,
optional auto-run, hosted-house recovery and Mahogany Homes handoff API. Integrate
this focused adoption from data-sync into playable without unrelated spike work.
Build from committed playable source and record the artifact hash separately.
No runtime installation, restart or gameplay validation is part of this adoption.
