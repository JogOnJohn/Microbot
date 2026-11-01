# Scene Safety

## 1. Scan the actual WorldView tile dimensions

Instance WorldViews can have smaller tile arrays than Constants.SCENE_SIZE.
Rs2GameObject.collectSceneObjects uses the scene array's actual X/Y dimensions
instead of indexing every view as a 104-by-104 surface.

## 2. Treat failed instance-coordinate conversion as a scene transition

LocalPoint.fromWorld can return null while instance bases change. Preserve the
captured local player, fall back to its local location, and return its world
location if neither local conversion is available. Do not pass null into
WorldPoint.fromLocalInstance. A later stable scene read can resolve the instance.

**Defensive checks:** Rs2GameObjectSmallWorldViewTest and
Rs2PlayerInstanceWorldLocationTest cover both boundaries.

## 3. Bound local points before canvas projection

Before projecting a local point, resolve its own WorldView and check both scene
coordinates against that view's actual dimensions on the client thread. Missing
views, null points and out-of-bounds coordinates are off screen, not safe inputs
to Perspective.getCanvasTilePoly. This prevents height-array indexing failures
during NPC/object interaction without changing route camera policy.

**Defensive check:** Rs2CameraTileOnScreenTest covers invalid and small views.

## 4. Default object clicks still require a visible composition

An omitted action selects the first option; it does not permit a hidden multiloc
or an object whose definition disappeared during a scene transition. Check the
visible composition for both named and default clicks. A caught interaction
exception must return false so the caller can re-query instead of assuming success.

**Defensive check:** Rs2TileObjectModelCompositionTest covers default-click
visibility, absent action arrays, first-option dispatch and scene exceptions.

## 5. A same-tick cache match does not prove an object is still in the scene

The tile-object singleton may reuse its list for the current tick. Re-query it,
but also check the exact raw object against its live anchor tile on the client
thread before declaring interaction readiness. A replacement with the same ID
and hash is not the original object. Validate the active WorldView and actual
tile-array dimensions, including null or ragged rows; do not rescan the whole
scene or invalidate the shared cache merely to check one target.

Readiness is a read-only snapshot, not a click authorization or confirmed effect.
The caller must re-query and apply the normal dispatch guards before acting.
When existing local-path helpers cannot represent a WorldView/instance in one
consistent coordinate space, return not-ready rather than mixing coordinates.

**Where this applies:** `Rs2InteractionApproach` and future handoff adapters.

**Defensive check:** `Rs2InteractionApproachTest` covers stale cached objects,
live-tile identity, unavailable scene state and client-thread-only reads.
