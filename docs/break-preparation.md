# Cooperative Break Preparation

Custom client API: `BreakPreparation.register(owner)` returns an identity-scoped
`AutoCloseable` handle. The automation polls `isRequested()` from its own normal
script loop, prepares a safe location, then calls `ready()`. It must stop gameplay
while the request remains active, including the interval before global pause or
logout. Once the handler finishes/cancels the request it may resume.

Close the handle on every shutdown and error path. Closing an unready requested
handle cancels the current break instead of claiming safety. Replacing an owner
registration does not let an old handle remove the replacement. Do not use this
API to clear `BreakHandlerScript`'s shared legacy lock.

Both legacy and V2 handlers defer new breaks for preparation. V2 also defers its
configured pre-break plugin stop so the automation can still prepare. A request
that remains unready for 120 seconds is cancelled and rescheduled; no forced
logout is claimed safe by this API. Existing legacy locks and combat checks still
apply. Enable only one Break Handler at a time. The API does not coordinate the
separate humanizer's own breaks.

Blackjack 1.1.17 uses this API and therefore requires this custom client, not just
any public client with the same 2.6.26 version. Gameplay validation must cover a
scheduled break in the tent, a request during wine restocking, cancellation, and
return after logout/no-logout breaks.
