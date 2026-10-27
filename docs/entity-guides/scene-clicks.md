# Scene Click Safety

Object and actor clickboxes can overlap the chatbox, side panel or minimap even
when their tiles are on-screen. Use SceneClickSafety's client-thread snapshot,
not canvas bounds alone. An unavailable clickbox or widget snapshot is not
permission to click the resting point.

Recompute after natural mouse travel, and recheck again after quick-tap settling
immediately before arming or pressing. A fully covered target cancels dispatch.
Preserve existing route ownership and cancellation predicates alongside the
geometry check. Do not apply scene clipping to inventory, widgets or menus.

Keep a safe existing prehover position rather than moving merely to the largest
remaining rectangle. Geometry rectangles at (0,0) or (1,1) must not be interpreted
as legacy fallback sentinels. Camera recovery is at most one turn per call.

Regression coverage: SceneClickSafetyTest and InputEmissionTest, including
interface appearance during natural travel and quick-tap settling.
