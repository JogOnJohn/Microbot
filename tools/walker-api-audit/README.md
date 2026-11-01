# Walker API Audit Tool

Dependency-free JDK 17 source parser using the standard compiler-tree API. It reads
exact Git blobs, never compiles application code and does not change refs or fetch.

```powershell
& '<JDK17>/bin/java.exe' .\tools\walker-api-audit\Inventory.java --self-test
.\tools\walker-api-audit\Run.ps1 -WorkspaceFile '<local-evidence>/workspace.json'
```

The prepared local manifest is in MBOT's
`operator-work/output/audits/walker-api-synthesis-20261031/workspace.json`.
It defines clientRepo/hubRepo, clientPath/hubPath output worktrees, evidencePath,
jdk17, apiVariants (name/commit) and consumerVariants (repo/name/commit and optional
changedFrom). Preserve full commit pins when rerunning. A run replaces generated
reports in the two prep worktrees and XML evidence outside Git.

Standalone scanner:

```text
java Inventory.java REPO COMMIT api OUTPUT.xml
java Inventory.java REPO COMMIT consumers OUTPUT.xml [CHANGED_FROM_COMMIT]
```

API scope: production sources under util/walker, shortestpath, util/poh,
api/tileobject and util/gameobject. Rows retain visibility, flags, annotations,
source parameter spelling, throws, owner, file, line and blob. Enum constants
are fields. Counts use external=true; private declarations remain in raw exports.

Consumer scanning records relevant imports, static-owner calls, method references
and selected constructors. Private branch reports cover changed surviving Java files
only, not a semantic call diff. Main is scanned without a changed-file restriction.

This is syntax parsing, not javac symbol analysis. It cannot prove overload binding,
binary compatibility, inherited/generated APIs, instance receiver types, reflection
or behaviour. Source type spellings are not canonical resolved types. Wildcard static
imports are labelled candidates. call-availability.csv joins by type/name/arity only:
candidate-present is not a successful compile. Missing type can mean outside scope.
Two-argument walkTo is the concrete overload collision documented in the inventory.

The self-test covers implicit interface visibility, varargs, nested declarations,
constructors, explicit static imports and method references. It is not application
test coverage. All generated findings require focused compilation and semantic tests
before implementation is promoted.

## Candidate Validation

`Summarize-Tests.ps1` archives actual JUnit XML and reports failures, errors and
skips separately. `candidate-tests.init.gradle` bounds test forks and optionally
forwards `-PwalkerGeneratedDir` to the generated-resource tests. It also registers
the offline `:client:walkerApiBenchmark` fixture harness; it never launches a client.

`Validate-Candidate.ps1` requires a clean exact commit and explicit fresh converter
output/evidence/artifact paths. It runs the complete client suite, insists that
the named route/PoH/banking/interaction/planning/threading suites execute without
skips, runs the dedicated generated validator, checks the documented offline
benchmark threshold, then builds and hashes a versioned test JAR. It refuses to
overwrite an artifact or evidence directory. Hub compilation/linkage, installation,
live validation, branch promotion and pushes remain separate gates.

`Build-Dispositions.ps1` combines a new pinned candidate XML inventory with the
historical CSVs. It accounts for each original and added declaration, and refuses
to silently drop a playable declaration. Its output remains syntax-only evidence,
not a substitute for exact JAR compilation, symbolic linkage or semantic tests.
