# Camera

## 1. Wait for camera motion to settle after key release

Arrow-key release does not immediately stop yaw or pitch easing. Rs2Camera's
holdUntil waits at most 600 ms for stable yaw and pitch samples from distinct
client cycles before returning. Callers should re-query canvas positions after
the turn rather than caching them before it or adding their own fixed sleeps.

Camera samples run on the client thread. getPitch and getYaw retain their
existing direct-read fallback when the optional client-thread read is unavailable.
Client-thread camera turns remain a no-op rather than blocking that thread.

**Defensive check:** Rs2CameraSettleTest covers coasting, timeouts, same-cycle
samples, equal-pitch no-ops and the direct-read fallbacks.
