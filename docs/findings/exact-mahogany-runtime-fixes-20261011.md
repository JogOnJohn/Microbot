# EXACT Mahogany Homes runtime fixes

Scope: `spike/shortest-path-data-sync`, based on `c386fc08fd`.
Playable source and release remain separate. No live Ross scene capture was made;
the user closed the client before capture. Coordinates below are log evidence,
not a newly exported transport or collision mapping.

## Evidence

- Bob's floor journey at 19:21:04: bank comparison 19,825 ms, then route readiness
  6,509 ms, with a six-tile direct route versus an 81-tile bank route.
- GE to Ross at 19:32:57: comparison 28,434 ms, then readiness 8,711 ms.
  Static preparation repeatedly took roughly 6-8 seconds and enumerated
  7,730,195 tiles; post-preparation search/rewrite was only around 6-10 ms.
  Structural counts alternated between 9,181 and 9,132 sites.
- Ross: goal `(2616,3315,1)`, landing `(2617,3316,1)`, upward ladder 16683,
  downward ladder 16679. At 19:34:57 the route climbed up correctly; at 19:35:01
  `arrival_scene_transition` clicked Climb-down. The sequence repeated.

## Changes

1. Remove implicit arrival scene transitions. A nearby ladder is not an instruction
   to change floor. Explicit route transports and plugin interactions remain intact.
2. Use horizontal proximity for the short bank-comparison gate, including floor
   journeys. Still require a finite short inventory-only route ending near the
   goal on its actual plane. Forced banking, long detours and wrong-plane/empty
   results retain comparison behavior.
3. Retain a structural superset of discovered transport endpoints for a collision
   snapshot. Alternating bank/inventory catalogs no longer evicts that graph.
   Account availability, costs and refusal exclusions still compile independently;
   retained structural endpoints cannot authorize an unavailable transport.
   Changed collision snapshots or an uncovered start still rebuild. A failed or
   cancelled build is not published. Only one structural graph is retained.

## Validation and testing boundary

Regression coverage includes Ross arrival without an object interaction, correct
versus wrong-floor short routes, long detours, alternating endpoint catalogs,
changed collision, uncovered starts, cancellation, and capability removal through
the actual EXACT Pathfinder facade. Existing W330/POH and route/refusal tests must
also pass before staging the release.

Host JDK 17 validation: 2,023 unit tests, zero failures/errors, six skips;
the separate `validateTransportSync` task passed against the preserved converter
export generated for base `c386fc08fd`. No transport resources changed in this fix.
The threading baseline update contains only compiler lambda renumberings and six
removed violations belonging to the deleted arrival helpers, not new exemptions.
The packaged-world adapter smoke measured cold preparation at 5,671 ms and a warm
repeat at 23 ms; those are offline measurements, not live contract timings.

This does not eliminate cold preparation after genuine collision changes. Check
`exact_static_cache reused=...`, `exact_static_build`, `exact_done preparationMs`,
`skip_compare_short_distance`, and the absence of unsolicited floor changes in
the next bounded live trial. Automated regression success is not gameplay proof.
