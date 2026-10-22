# Experimental static scene VBO reuse

Enable at client startup with `-Dmicrobot.gpu.staticVboReuse=true`, before `-jar`.
The default is false. Removing the flag or setting it to false restores the
existing per-zone storage allocation policy. Full code rollback uses the previous
release JAR; the startup/upload lifecycle safeguards remain with either flag value.

## Purpose

VMware allocation traces associated growing native graphics commitment with
`VBO.init(GL_STATIC_DRAW)` during scene-zone uploads, including pages associated
with objects whose tracked GL names had been deleted. This experiment reduces
allocation churn. It does not assume that retained pages contain old buffer data
or establish a driver defect.

## Storage and lifetime

- Main scene loads, zone rebuilds and subscene loads use one pool per GL context.
- New pooled stores round capacity up to 64 KiB. Requested mapping size and draw
  length remain unchanged; capacity beyond the requested data is not drawn.
- Best-fit reuse permits at most twice the rounded requested capacity.
- Stores larger than 8 MiB bypass retention and retain their exact requested size.
- Cached storage, including pending GPU work, is capped at 256 MiB and 4096
  buffers. Active scene storage is additional and governed by the scene itself.
- On retirement, zone VAOs are deleted first, mapped views are cleared, and a
  fence follows the last submitted use. Reuse requires an already-signalled or
  condition-satisfied fence. At most eight zero-timeout polls occur per acquire.
- A pending fence causes a miss. Failed fence creation/wait deletes the store.
  There is no positive-duration wait, `glFinish`, unsynchronised mapping, storage
  orphaning or new `glBufferData` on a cache hit.
- Budget pressure evicts the oldest cached store. Normal GL deletion semantics
  apply even if its fence is pending. Every fence is deleted on reuse/eviction.
- All pool operations validate the owning thread and LWJGL capabilities object.
- GPU shutdown rejects new uploads and retries cleanup on later client-thread
  callbacks until existing uploads finish. Queued GL allocation work is drained
  even if its waiting worker is interrupted; failures propagate to that worker.
- Startup requests have generations so a later disable cancels stale callbacks.
- PluginManager's off-client-thread stop waits for cleanup completion before
  handing off to a conflicting renderer; a 30-second failure aborts the handoff.
  An opt-in PluginStopBarrier also gates retries after the old plugin was removed
  from the active list, and gates restarting that plugin until teardown completes.
  Cleanup checks context identity before touching global render state or GL names.
- Failed zone allocation/upload releases partially acquired stores and unpublished
  zones, retaining any zones reused from the current scene.
- Shutdown drains cached stores, root/subscene/pending-zone stores, then destroys
  the context. Active stores returned after pool close are deleted directly.

The graphics API's zero-timeout sync behavior is documented in the
[Khronos sync reference](https://wikis.khronos.org/opengl/GLAPI/glClientWaitSync).

## Measurement

Every 30 seconds, `gpu_static_vbo_pool` reports cumulative hits, misses,
createdBytes, reusedBytes, evictions, pendingPolls, fenceFailures, cachedBytes,
cachedBuffers and peakCachedBytes. Existing GPU diagnostics continue counting
cached GL objects as live and attribute their storage to `static-vbo-cache`.
That bounded retained capacity is intentional and must be included when
interpreting the GL ledger.

Evaluate repeated scene changes after warm-up, including W330 houses, outside
teleports, floors, normal zone rebuilding, and a GPU disable/re-enable when ready
for a controlled lifecycle test. Compare native process and GPU commitment growth
per scene, actual storage-allocation misses, and fence failures. A useful result
is sustained high reuse and plateauing native/GPU commitment with correct visuals
and no new stalls. XP/hour alone does not validate this change.

Unit tests use a fake GL backend to cover fences, limits, best-fit sizing,
context rejection, duplicate returns, shutdown, and a 100-scene storage plateau.
Lifecycle tests cover queued GL work, active CPU writers, interruption, failure
propagation and cancelled startups. They cannot establish real-driver correctness
or prove the memory leak fixed; a live trial remains necessary.
