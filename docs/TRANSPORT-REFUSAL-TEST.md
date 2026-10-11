# Walk-scoped transport refusal recovery

Data-sync trial based on the current playable branch and the existing exact-backend
work. This narrowly adapts the intent of Microbot PR #1930 (695a75df80), not its
walker split or permanent endpoint-based blocked-edge implementation.

## Policy

- Two failed object interactions, or three handler/object fallthroughs, exclude
  that specific transport for the current walk and request a fresh route.
- Only settled attempts beside the origin count. Movement, animation, dialogue,
  human input, interruption, unloaded tiles and instance scenes do not count.
- Equivalent rows retain their exclusion after catalog refresh. Different
  objects/actions/display options with the same endpoints remain available.
- Retrying the same goal preserves exclusions; cancellation or a new goal clears
  them. Successful dispatch clears outstanding strike counters.
- POH, W330, spirit-tree, seasonal and originless teleport handlers keep their
  existing recovery ownership. Closed-object opening/transform waits do not
  themselves count as refusals.
- No learned walking-edge file, transport TSV, collision resource, converter pin
  or golden-route baseline is changed.

## Backends and testing

The legacy backend filters forward/reverse transport expansion and dispatch.
The exact backend filters the immutable account snapshot, rebuilding its cached
graph/targets when usable transports change. The raw static graph retains the
catalog so exclusions can be cleared without dropping valid alternatives.

Legacy remains the default. Choose `EXACT` in Shortest Path's `Pathfinder backend`
setting for exact testing; launch the test release with `-Xmx2g`. Compare both
backends on the same route and account/config state.

Runtime cases still requiring interactive validation:

- A genuinely unavailable ladder/boat/menu routes through a working alternative.
- Slow successful travel, opening a closed trapdoor, pending dialogue and human
  takeover do not create exclusions.
- The same destination retried after route cancellation can use the original
  transport again.
- W330 house entry/reanchor and POH/spirit-tree recovery remain unchanged.

Look for `transport_refused | walk-scoped exclusion` in the client log. Automated
checks cover strike accounting, lifecycle, legacy expansion, exact cache reuse
and the actual exact facade, alongside the existing route/W330 suite. They do
not establish live gameplay success.

The implementation is one isolated commit on `spike/shortest-path-data-sync`;
revert that commit to remove this trial without reverting the exact backend.
The versioned Bizza test JAR is separate from the active release/hot-reload JARs.
