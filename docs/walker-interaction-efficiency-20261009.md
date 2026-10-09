# Walker interaction efficiency

## Baseline

Source: preserved `mahogany-crash-20261009-114502.log`, morning of 9 October 2026
(AEDT). Compare successful routes from the same client build; exclude missing
transport data, POH recovery and timeout cascades after the 11:45 injected-client
crash. These observations are not measurements of the new implementation.

| Event | Observed timing |
| --- | --- |
| 11:41:11 ladder 16679 | 541 ms start, 574 ms plane wait, 1497 ms total |
| 11:40:44 ladder 16683 | 454 ms start, 1821 ms plane wait, 2415 ms total |
| 11:44:52 door (3242,3486,0) to (3241,3486,0) | 748 ms start, 1183 ms traversal; released by stable edge |

The difference between the ladder sub-waits and total includes the old random
settle. It is not a predicted saving for every interaction. Actual approach,
animation and server ticks are still necessary.

## Changes

- Ordinary scene-walk arrival ends at the same-plane distance threshold without
  an extra idle-only wait of up to four seconds.
- Doors can complete on positive live collision evidence for both sides of the
  exact cardinal crossing. Unknown collision retains conservative handling.
- A dispatched floor transition has one bounded state: dispatch, approach,
  expected landing with loaded collision, or failure. No-start retries retain
  their 1800 ms bound; movement cannot extend the overall 6800 ms deadline.
- A confirmed floor landing bypasses the subsequent settle floor. Other
  transports retain their existing timing and recovery policy.
- Mahogany Homes 1.0.6 pilots bank-aware completion conditions for contractors
  and homeowners. Correct action, plane, range, visible hull and live
  reachability are required. The synchronous walker returns and releases its
  lock before plugin interactions resume. Failed clicks resume approach with
  bounded attempts. Route ladders stay under walker control.

The pilot uses the new `walkWithBankedTransportsUntil` API. Use the matched client
release; the version number 2.6.30 alone does not establish that the API exists.

## Comparison markers

- Existing planning/startup and menu-action logs identify plan to first click.
- `transport_plane_change` records phase and dispatch-to-confirmed-landing time.
- `door_await` reports `live-edge-passable` when its fast path wins.
- `interaction_handoff` records early walker completion.
- `mahogany_travel_return` records return state, duration and position; compare
  the next menu-action timestamp for plugin dispatch latency.

Live acceptance requires a stocked Mahogany Homes session: Larry's ladder,
successive floors, an NPC behind a closed door, off-screen NPC approach and
normal contractor turn-in. Confirm one dispatch per transition, no reverse
ladder use, and continued W330/Nexus recovery. Offline tests do not prove a
runtime latency reduction. Agility need not be interrupted to build this pair.

Client-thread baseline verification: rebuilt untouched commit `81184a5e49` and
the changed source independently. Both scanners produced 1018 entries; after
normalizing compiler lambda sequence numbers, both contained the identical 994
entries. The refreshed baseline records existing drift, not new off-thread calls.
