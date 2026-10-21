# Temporary GPU resource ownership audit

## Scope and status

Diagnostic-only follow-up to the Bizza native-memory captures. It does not pool
buffers, change storage allocation sizes, reorder deletion, insert fences, call
`glFinish`, or change the walker. Default is off. The current running process
does not gain these diagnostics until a separately authorized client restart.

Enable in a built client with `-Dmicrobot.gpu.memoryDiagnostics=true` before
`-jar`. Keep the existing NMT, JFR, GC log, call journal and process sampler
flags so private committed bytes, Java heap and GL lifecycle can be compared.

## What is tracked

- Buffer and VAO namespace, GL name, monotonically increasing creation serial,
  context generation, creation scene epoch, owner, requested bytes, and a compact
  creation stack. GL name reuse does not retarget old VAO references.
- Actual vertex attribute setup: capture the bound VAO, bound array buffer and
  driver-reported attribute buffer after each pointer call (zone, dynamic, UI).
- Element-buffer bind/unbind calls: metadata only in the draw loop. No extra GL
  queries, stack capture or per-frame event history is added there.
- Zone ownership is independent of VBO ownership. The existing
  `GpuMemoryOperation` owner semantics are preserved for older capture tools.
- Native WGL current-context handle plus LWJGL capabilities identity at resource
  creation/deletion and setup. Non-Windows uses the weaker capabilities identity
  fallback and logs that native handle coverage is unavailable.

The renderer currently deletes VBOs before VAOs. This order alone is not an
ownership violation. Deleted buffers are retained as **metadata** while tracked
VAO attributes still reference the old allocation. They disappear from pending
accounting when the final reference is detached or its VAO is deleted. The audit
also observes automatic detachments from the currently bound VAO after buffer
deletion. It does not retain actual VBOs, VAOs, scenes or mapped ByteBuffers.

## Queries and checkpoints

- Sample the first eight deletions per resource kind, then one in 64 per kind.
  Separate counters ensure buffer sampling cannot starve VAO sampling.
- Sampled deletion probes record `glIsBuffer`/`glIsVertexArray` before and after
  deletion, current array/VAO binding, and three attributes plus element binding
  for VAOs. GL 3.3 VAO queries require temporary binding: restore the original
  VAO in `finally`, thereby also restoring its element binding. Do not interpret
  `glIsBuffer == false` as evidence that native storage was returned to Windows.
- Zone release verifies no tracked live/pending resource remains under its owner.
- Completed scene swaps and pre-context-destroy emit `gpu_ownership` summaries.
  The final context checkpoint reports remaining buffer/VAO metadata before it
  is cleared. Scene-load/reuse does not imply all previous resources should vanish.
- Checkpoints read up to eight GL errors, even with debug logging disabled;
  existing debug error checks also forward errors. Error reads consume GL error
  state, and binding probes can perturb timing. This is diagnostic instrumentation,
  not a zero-overhead or completely observation-free reproduction.

## Output and bounds

`client.log`: `gpu_ownership` and `gpu_ownership_violation` (first 32 detailed
violations per context, with creation/release stacks). Counters continue afterward.
Detailed owner-release events include at most 16 survivor records per reported
release and stop with the same violation-report budget.

JFR: `microbot.GpuResourceLifecycle`, stack-enabled, records create, adopt,
attribute capture, sampled probes, delete-returned, survivors and checkpoints.
Fields include creation serial, resource kind, GL name, context, owner, birth
scene, event scene, bytes, monotonic time and wall time. JFR thread metadata
supplies the OS thread ID for joining to ETW/native allocations. Existing
`microbot.GpuMemoryOperation` and the binary call journal remain available.

```powershell
& "$env:JAVA_HOME\bin\jfr.exe" print --events microbot.GpuResourceLifecycle capture.jfr
```

The new ledger caps live plus pending records at 16,384, attribute slots at 17
per VAO, completed-history entries at 256 and stored creation traces at 768
characters. The older requested-storage ledger now also caps live records at
16,384. Overflow is explicitly counted and makes coverage incomplete: never
claim a clean lifecycle from an overflowed or truncated capture. JFR disk bounds
are controlled by the launch recording (`maxsize=256m,maxage=30m` in the current
helper). These limits do not cap renderer resources or process memory.

## Reading the evidence

1. Establish zero overflow and capture coverage. Look for wrong/missing context,
   duplicate live names, binding discrepancies, GL errors or owner-release
   survivors. Correlate the first violation, not later cascades.
2. Compare live and pending counts/bytes at matched scene checkpoints. Nonzero
   pending counts during VBO-before-VAO deletion are expected transiently.
3. Join lifecycle serials and thread/timestamps to store calls and native traces.
   Existing owner IDs are not a substitute for creation serials. JFR can lose
   early records due to rolling retention; record that boundary explicitly.
4. If all checks balance yet private committed memory grows, the instrumented
   ownership paths have stronger evidence of correctness. That does not prove
   a driver bug: sampled driver queries, uninstrumented GL features, asynchronous
   execution and shared native arenas remain limitations. ETW regions are not
   one-to-one GL object allocations.

This is not a global Java heap reachability scan or validation of GPU completion.
No changes to buffer lifetimes are justified merely by balanced counters.

## Validation and removal

Pure ledger tests cover deletion ordering, multiple VAO references, current-VAO
detach semantics, name reuse, namespaces, context identity/reset, ownership,
bounded history/slots/records and accounting overflow. These tests do not exercise
a real OpenGL context; live probe verification remains outstanding until the
diagnostic JAR is explicitly deployed.

Baseline before this patch: `c22c9aa644`. The ownership audit is isolated in a
separate commit. Revert that commit to remove the new hooks, audit and tests;
the earlier diagnostics remain. Disabling the JVM property suppresses both
sets of diagnostics but does not remove their code. Removing all older diagnostic
patches is separate work and must preserve intervening walker fixes.
