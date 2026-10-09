# House teleport hints: 2026-10-06

The upstream house spell variants include `(Inside)` and `(Outside)` in their display labels.
Those labels did not match Rs2Spells or MagicAction. `Transport.getSpellName()` now normalizes
only these house variants and preserves the original labels, destinations and varbit gates.
Availability checks, banking, spell execution and spell-widget highlighting share that name.

When eligible transports share the same destination, the manual hint prefers the house spell
over a house tablet, independently of Set iteration order. A different inside/outside destination
cannot substitute for the planned landing. No transport costs or hosted-house automation policy
were changed.

White text now shares PreferredTeleportAssistant's upcoming choices with the blue highlight.
On W330, a retained Nexus/jewellery-box choice survives an empty house-instance recalculation
for the same target. It clears on a new target, explicit cancellation or a completed non-house
route. A missing pathfinder is tolerated for at most eight ticks inside the hosted-house template.
Completed path edges are skipped; remaining labels retain path order.

## Validation

- All 15 PreferredTeleportAssistant tests pass, including four new regressions.
- Clean full suite: 1727 tests, 1720 passed, six skipped, one failed.
- Sole failure: ClientThreadGuardrailTest, also reproduced on pristine commit
  `ada108254a466f067a87d1dece062f50544cd51c` in an isolated checkout.
- All 59 reported guardrail additions/removals are identical between pristine and patched builds.
  The guardrail and its baseline were not changed or suppressed.
- Release JAR must be built separately because that pre-existing suite failure stops a combined
  tests/build invocation. A built artifact is not live validation; do not restart the active client.
