# Implementation Handoff

This is the historical preparation handoff. For completed implementation, exact
artifact pins and remaining live-test limits, use [the final handoff](FINAL-HANDOFF.md).

## Preparation Checkpoint

At the preparation checkpoint, only docs, inventory outputs and a standalone audit
tool were added to the client prep branch. Hub prep contains caller reports and
guidance. No production edits,
dependency changes, merges into playable, releases, pushes, installation or runtime
validation belonged to that preparation checkpoint. Isolated implementation has
since begun; see [the contract and current stage record](IMPLEMENTATION-CONTRACT.md).

The local workspace manifest and raw AST evidence are outside both repositories:
`operator-work/output/audits/walker-api-synthesis-20261031/` under the MBOT workspace.
`workspace.json` pins commits and local JDK/repository paths. `refs-before.json`
records protected local refs. Neither is a portable build configuration.

## Next Implementation Sequence

1. Start with collision-query facade adapters, then readiness-only object helpers,
   one reversible commit per family behind the current facade. Preserve destination
   rejection, client-thread reads and legacy source compatibility. No clicks, camera
   changes, active-route publication or automatic snapping belong in these helpers.
2. Reconcile canonical versus runtime/data-sync interaction API before publishing
   interaction adapters, with tests for ownership, retained request identity,
   cancellation and observed effects. This is not a prerequisite for independent
   query-only additions. Do not merge unrelated runtime changes as a shortcut.
3. Add typed transport conversion only after explicit tests for alternatives,
   grouped item requirements, quantities, banked inventory and converter round-trip.
4. Reuse upstream route types rather than competing request/result definitions.
   Add planner integration only after proving policy/snapshot isolation, cancellation
   and route ownership. No automatic replacement of the executor or destination policy.
5. Compile affected Hub consumers against the exact candidate client artifact,
   including active private branches rather than only Hub main.

## Protected Behaviour

- Own-PoH detections, including mounted Xeric's; W330 hosting takes precedence only
  when enabled and actually on world 330. Preserve spell/house/hosted routing rules.
- Golden routes, banked transport ownership, recovery, cancellation, learned edges,
  scene-click safety, client-thread reads and fail-closed unavailable widgets.
- Existing shortest-path sync tool/resource formats and transport requirement groups.
- Do not alter camera policy or fold in unrelated renderer/GPU work.
- Keep current running client and dirty unrelated worktrees untouched.
- Mahogany Homes work is finished; do not launch it or use it for live validation.

## Future Validation Gates

Use JDK 17 for the client and the established JDK 11 Hub build setup. Pin local
candidate artifacts explicitly; never silently fall back to a remotely published
client or latest.integration. Run builds sequentially within a checkout.

Client: compile and use `:client:runUnitTests` (not the potentially skipped
`:client:test`), then the full existing golden-route/PoH/transport/interaction suites.
Hub: focused plugin task plus matching `-PpluginList=<PluginClassName>`, tests and
consumer compile checks. Compare route destination, chosen transports and failure
states, not just path existence. A full route pass is required before promotion.

Build success is separate from deployment and live operation. Obtain explicit
authorization before installing, replacing, launching or enabling plugins. Validate
the then-current workload (currently Ardougne Agility), visible client and backend
independently. Promote via the normal selective development/playable/data-sync flow
only after validation; never reset protected branches onto this worktree.

## Preparation Review And First Adapter Acceptance

Independent read-only client/Hub subagent reviews verified the semantic findings
and tightened route-versus-lock ownership, planner restoration limits and the actual
private Homes overload attribution. No application tests were run for this review.

Collision-query adapters need absent/unknown map and region, zero/negative radius,
same-plane/ring ordering and disconnected-but-standable tile cases. Assert no
destination, config or active-route mutation. Readiness-only helpers need null or
despawned objects, unavailable client-thread results, wrong plane, range/offscreen
boundaries and closed-door separation. Assert no clicks or camera changes and
never report interaction success from readiness.

Existing regression sources to retain include ShortestPathGoldenRouteBaselineTest,
WalkerRouteCorpusTest, PersonalPohSelectionTest, World330HostedHouseTransportTest,
World330HostedHouseAccessTest, BankedTransportItemPlanningTest,
WalkerInteractionCompletionTest, Rs2WalkerHandoffFailureTest,
Rs2WalkerPartialRouteSafetyTest and Rs2WalkerPathEndpointArrivalTest. Discovery of
these tests is not execution. Keep full route coverage before promotion, not merely
passing a small adapter filter. Converter validation has a separate task:

```powershell
.\gradlew.bat :client:validateTransportSync -PtransportSyncGeneratedDir=<fresh-converter-output>
```

Do not regenerate regression/guardrail baselines simply to make a changed route or
threading contract pass. Hub's report now records its focused source and binary
compatibility matrix, including expected canonical incompatibility for historical
interaction consumers.
