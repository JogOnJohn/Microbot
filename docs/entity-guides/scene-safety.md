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
