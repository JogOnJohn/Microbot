# Tithe release update - 2026-10-26

Client baseline: a783eff314, branch codex/tithe-prehover-client-20261025.
Reviewed upstream development: 454c6e766d (delta from c9e2bbaf43).
Microbot 2.6.43; RuneLite remains 1.13.4.

This is a selective compatibility port, not a complete merge of upstream development.
Imported: terminal-logout handling in AutoLogin/Break Handlers; negative widget-id validation;
actor world-view null handling; inventory-setup null filtering; equipment failure propagation;
live tab reads; widget traversal and clickbox client-thread safety; console text-layout fix;
Quest Helper bank-tab and Romeo/Juliet door fixes. Relevant upstream regression tests included.

Deferred: walker/pathfinder/POH refactors, retired game-object API migration and dependent bank/player
migration, new dashboard/bug-report features, Agent Server ergonomics and CLI additions. These
span dependencies absent from this protected playable tree. Retained current CLI endpoint contract.

Protected walker, shortestpath and POH paths are byte-identical to baseline. Tithe's prepared-pointer
API and tested instance reachability/interaction implementation are retained. No transport data changed.

Validation: :client:compileJava and :client:microbotReleaseJar passed. Selected runUnitTests passed,
including plugin-lock deferral, V2 login recovery, terminal logout, tabs, widgets, equipment and actor
world-location checks. Full test-source compilation also passed; no full-suite claim.
Hub upstream 0bb52201 merged into the Tithe branch without changes to its implementation. Focused
Tithe JAR build and six tests passed against the exact new client JAR. Plugin remains 1.1.29 with SHA256
A6ACA0DD3A8658320C46331BA44EF990A81692824E155E50BC033BA0152790BB.
Client release SHA256: 35D5AE0956B37BC9D338BEF54EDF764D1E16BA5460EF9242CFD8986F7531F052.

Guest deployment is for next launch; no client is running at the pre-install audit. Preserve the
2.6.41 custom client as rollback. Source synchronization uses new clean guest worktrees and preserves
the older dirty Tithe checkout. No origin push or PR requested in this turn.
