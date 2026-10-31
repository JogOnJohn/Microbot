# Implementation Handoff

## Prepared, Not Migrated

Only docs, inventory outputs and a standalone audit tool are added to the client
prep branch. Hub prep contains caller reports and guidance. No production edits,
dependency changes, merges into playable, releases, pushes, installation or runtime
validation belong to this preparation checkpoint.

The local workspace manifest and raw AST evidence are outside both repositories:
`operator-work/output/audits/walker-api-synthesis-20261031/` under the MBOT workspace.
`workspace.json` pins commits and local JDK/repository paths. `refs-before.json`
records protected local refs. Neither is a portable build configuration.

## Next Implementation Sequence

1. Reconcile canonical versus runtime/data-sync interaction API deliberately, with
   tests for ownership, retained request identity, cancellation and observed effects.
   Do not merge unrelated runtime changes as a shortcut.
2. Implement one small query/adapter family per reversible commit behind the
   current facade. Keep legacy source compatibility. Prefer existing upstream route
   types over competing request/result definitions.
3. Add typed transport conversion only after explicit tests for alternatives,
   grouped item requirements, quantities, banked inventory and converter round-trip.
4. Add planner integration only after proving policy/snapshot isolation, cancellation
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
