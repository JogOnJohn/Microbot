# Temporary GPU lifecycle capture

This diagnostic is disabled by default and does not change rendering, deletion
order, synchronization or buffer reuse. It is not a memory-leak fix.

Explicit startup properties:

```text
-Dmicrobot.gpu.memoryDiagnostics=true
-Dmicrobot.gpu.lifecycleCapture=true
-Dmicrobot.gpu.lifecycleCapture.seconds=120
```

Also enable the `microbot.GpuReplay` event in the startup JFR configuration.
It is annotated disabled by default. Capture must include initial GPU-context
creation: enabling only the recording halfway through a context is insufficient.
Use a bounded disk recording and preserve the entire beginning/end window.

The first context emits BEGIN. A 10-300 second window (120 default) emits END.
One million records, context replacement/destruction, or missing initial context
produce INCOMPLETE instead. Capture then stops emitting; rendering continues.
The bounded recorder does not cap driver memory or stop the client.

Events use the existing audit's unique resource serials (not reused GL names),
actual current GL context and JFR thread identity. They include storage, map flags,
unmap, VAO attributes, static draw boundaries, checkpoints and frame boundaries.
No geometry or account data is captured. Draw hooks are flag-guarded at call sites.
Synthetic replay must not be represented as an exact game/API/payload replay.

Tests: `GpuReplayCaptureTest` exercises complete-window admission, timeout,
overflow and context replacement without a GL context. The operator replay tool
has separate offline JFR/CSV ordering, lifetime, reference and policy tests.

Remove the flag (or use the preserved normal release) to disable this temporary
instrumentation. It has no dependency on the walker or Homes plugin.
