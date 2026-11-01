# Independent Final Review

2026-11-01. Two independent read-only reviews inspected candidate validation
commit `29855fe83dd0d1995a32f1927264894cc9b20028`. The parent verified the affected
code paths. These are static findings, not executed reproductions. No production
fix, new build or live test was performed in this review.

**Current disposition: hold live testing/promotion until the following are fixed.**
The existing offline results and exact JAR remain valid evidence of their stated
scope, but did not cover these failure cases. The JAR has not been replaced.

## Findings

1. **P1 - Planning refresh can emit gameplay input.**
   `PathfinderConfig.refreshForPlanning` calls legacy `doRefresh` (line 665).
   Its item filtering reaches `hasChronicleCharges`, which calls inventory or
   equipment `Check charges` when Chronicle charges are unknown (lines 2433-2440).
   Carry/equip a Chronicle with an unset charge cache and request item-enabled
   planning with refresh/bank policy: evaluating the catalog can click even if the
   resulting route would not use Chronicle. This violates query isolation.
   Required fix: planning uses known charges only, with unknown unavailable;
   retain existing legacy behavior. Test inventory and equipment, zero/positive/
   unknown charges, and assert no dispatch or profile mutation by planning.
2. **P2 - Required varbits are not an availability-aware query snapshot.**
   The requirement batch (lines 1155-1170) captures varplayers, but only prewarms
   varbits through `Microbot.getVarbitValue`. `Rs2PlayerStateCache.updateVarbitValue`
   (line 191) maps a failed raw read to zero. A zero-sensitive gate may therefore
   pass on unavailable data. Filtering also rereads the shared cache rather than
   a fixed query-owned map. Required fix: directly capture required varbits with
   availability on the client thread; use that snapshot for transport and
   restriction checks. Test genuine observed zero separately from failed reads
   and changes after capture. Do not change unrelated legacy cache semantics.
3. **P2 - walkCanvas has no final ownership guard.**
   `Rs2Walker.walkCanvas` checks ownership at entry (line 4578), then uses raw
   `mouse.click(point)` (line 4588). A caller admitted before another request
   reserves ownership can still emit after that reservation. Required fix: use
   the existing guarded input path and preserve scene-click safety. Add a
   deterministic reservation-between-admission-and-emission regression test.

Both reviewers identified findings 1 and 2 independently. The interaction reviewer
also identified finding 3. No additional concrete defect was found in grouped
transport conversion, conservative banking preparation, selected-edge reversal,
or personal-PoH/W330 precedence. That is bounded review evidence, not exhaustive
proof or gameplay validation.

## Next Acceptance Pass

Implement the three fixes in reversible isolated commits and add reproductions.
Rerun focused and full client gates, converter validation and the query benchmark.
Because production bytecode will change, build a new versioned JAR with a new
manifest/hash and rerun exact-candidate Hub source and binary compatibility gates;
do not reuse the old artifact's compatibility result as proof for a new hash.
Only then consider a separately authorized visible test using the current workload.

Historical checkpoint documents now link to the final handoff rather than leaving
stale pending-validation prose as the apparent current state. No VM operation,
protected branch movement, installation or push occurred.
