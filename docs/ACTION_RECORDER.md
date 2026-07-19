# Action Recorder

Action Recorder is a Microbot client plugin for capturing structured operator demonstrations. Its output is an observation bundle for review and handoff to a Microbot Hub script project; it does not replay mouse coordinates or generate a client-side automation script.

## Session workflow

1. Enable **Action Recorder** in the Microbot plugin list.
2. Start a session from the plugin configuration or `POST /action-recorder/start`.
3. Add operator markers at useful phase boundaries such as `BANK_PREP`, `FALADOR_PATCH`, or `BIRDHOUSE_1`.
4. Perform the demonstration normally.
5. Stop the session and wait until `/action-recorder/status` reports `stopped: true`.
6. Hand the completed session directory to the owning Microbot Hub project for review and state-machine development.

Sessions are written under `~/.runelite/action-recordings/<timestamp>-<name>-<session>/`:

- `events.jsonl` contains the lossless ordered event stream.
- `manifest.json` contains counts, timestamps, marker order, and schema metadata.
- `handoff.md` describes the client-to-Hub boundary and candidate flow guidance.

## Record contract

Every JSONL line uses the same envelope:

```json
{
  "schemaVersion": 4,
  "sessionId": "...",
  "sequence": 42,
  "type": "INTERACTION",
  "occurredAtEpochMs": 1784412345678,
  "offsetMs": 15320,
  "gameTick": 48120,
  "location": {
    "worldX": 3052,
    "worldY": 3307,
    "plane": 0,
    "regionId": 12083,
    "sceneX": 48,
    "sceneY": 51,
    "worldViewId": 0,
    "instanced": false,
    "templateX": 3052,
    "templateY": 3307,
    "templatePlane": 0,
    "templateRegionId": 12083
  },
  "payload": {}
}
```

The client targets Java 11, so the implementation uses immutable value objects rather than the Java `record` keyword. Payloads are typed objects for session lifecycle, operator markers, interactions, deferred walk destinations, game ticks, container changes, animations, graphics, widgets, varbits, stats, game messages, game state, object state, ground items, keyboard context, and camera changes.

Schema version 4 adds interaction IDs, deferred walk destinations, configurable object-capture modes, click-time object state plus event-driven after-state, clock-variable suppression, opt-in keyboard context, and an independent camera stream. Capture settings are frozen when the session starts; configuration edits made during a recording apply to the next session rather than silently changing the current trace.

## Configuration

All capture categories default to enabled for a full operator demonstration. Disable categories only when a narrower trace is intentional.

| Setting | Default | Effect |
|---------|---------|--------|
| Interactions | on | Menu entry, target, canvas click, player state, and target location |
| Game tick snapshots | on | Periodic player, movement, animation, graphic, and location state |
| Game tick interval | `1` | Capture one snapshot every N game ticks |
| Inventory changes | on | Deltas plus complete inventory after-state |
| Equipment changes | on | Deltas plus complete equipment after-state |
| Bank changes | on | Deltas after a count-only baseline; never a full bank export |
| Animation changes | on | Local-player animation and pose changes |
| Graphic changes | on | Full local-player spot-animation details |
| Widget lifecycle | on | Widget group load and close events |
| Varbit changes | on | Varp/varbit ID and value changes |
| Include clock variables | off | Include noisy varbits `12391`, `12392`, and varp `3079`; varbit `12393` is never suppressed |
| Stat changes | on | XP, real level, and boosted level changes |
| Game messages | on | System/dialogue categories only; player chat is excluded |
| Game state changes | on | Login, loading, hopping, and connection transitions |
| Object capture mode | `ACTIONABLE_NEARBY` | `OFF`, clicked-object `INTERACTION_FOCUSED`, actionable nearby objects, or all nearby objects |
| Ground-item changes | on | Ground-item spawn, despawn, and quantity evidence |
| Owned ground items only | on | Exclude unrelated public and static ground-item spawns |
| Nearby capture radius | `16` | Maximum same-plane distance for object and ground-item changes |
| Keyboard context | off | Opt-in key press/release identity capture while logged in; never records typed characters |
| Keyboard capture mode | `ALLOWLIST` | Use the configurable semantic-key allowlist or opt in to `ALL_KEYS` |
| Keyboard allowlist | movement/navigation/hotkeys | Comma-separated `KeyEvent` names used by allowlist mode |
| Camera changes | on | Changed yaw/pitch and their targets, sampled independently on game ticks |
| Flush every N records | `25` | Periodic JSONL disk flush; session start and markers flush immediately |

## Interaction and effect evidence

An `INTERACTION` records a session-local `interactionId`, the complete menu entry, canvas click, player state, event location, and target location when the target can be resolved. Widget interactions include the clicked component ID, parent ID, direct text/name/actions, and up to eight bounded text-bearing widgets from the clicked component's immediate context. `WALK` targets are always blanked, because a player name may occupy the hovered menu target and the menu parameters are canvas coordinates rather than a destination. A subsequent `WALK_DESTINATION` uses the same `interactionId` to record the client destination after it updates, with an explicit resolution status. Canvas coordinates remain diagnostic evidence only.

`CONTAINER_CHANGE` records inventory, equipment, and bank effects. Inventory and equipment include the after-state by slot. Bank events establish a private baseline without exporting the entire bank, then record item deltas. Item records include the exact ID, name, quantity, noted state and linked note ID, placeholder state and linked placeholder ID.

Game-object interactions include a canonical click-time object snapshot: base and resolved IDs, semantic name/actions, exact location, transform-controller varbit or varp, controller value, and bounded recent object context from the clicked tile and its immediate surroundings. An event-driven five-game-tick watch emits `OBJECT_TARGET_STATE` when the resolved object or controller value changes, or an explicit `unchanged_timeout` result. It never sleeps or blocks the client thread.

`ACTIONABLE_NEARBY` resolves active transformed definitions and omits scenery without actions, such as vegetation. `ALL_NEARBY` emits every radius-limited object observation. `INTERACTION_FOCUSED` keeps the same nearby observations only in a bounded 15-second memory buffer, then attaches relevant context to clicked objects instead of writing unrelated spawn/despawn churn. This mode cannot create a gap for an interacted object because its canonical click snapshot and after-state watch are always emitted. `OFF` disables both streams. `GROUND_ITEM_CHANGE` remains independently configurable and records owned or group-owned item changes by default.

`KEYBOARD_INPUT` is opt-in and captures press/release identity, modifiers, key location, repeat state, and original event time only while logged in. It deliberately ignores `keyTyped`, so literal chat, bank-search text, and credentials are never written; `ALL_KEYS` can still reconstruct many inputs from key identities and should be enabled only for an intentional private recording. `CAMERA_CHANGE` is independent of keyboard capture, so mouse-drag camera movement and remapped WASD camera movement share the same semantic camera evidence.

The Hub review should correlate each interaction with subsequent location, container, animation, graphic, widget, varbit, message, and object records. Correlation is evidence for a proposed state transition, not proof that every observed action belongs in the final script.

## Operator API

The Agent Server must be enabled, authenticated normally, and the Action Recorder plugin must be active.

| Method | Path | Body | Purpose |
|--------|------|------|---------|
| `GET` | `/action-recorder/status` | - | Current plugin and session state |
| `POST` | `/action-recorder/start` | `{"name":"farm-birdhouse","notes":"normal route"}` or empty | Start a session using the configured capture profile |
| `POST` | `/action-recorder/marker` | `{"label":"BANK_PREP","notes":"optional"}` | Add a candidate phase boundary |
| `POST` | `/action-recorder/stop` | `{"reason":"demonstration_complete"}` or empty | Request asynchronous stop and artifact finalization |
| `GET` | `/action-recorder/sessions` | - | List local session directories, newest first |

`POST /action-recorder/stop` returns `202` because disk finalization happens on the recorder writer thread. Poll status until `stopping` becomes false and `stopped` becomes true before handing off the directory.

Disabling the plugin also finalizes an active recording. That normal path writes `SESSION_END.reason` as `plugin_shutdown`; an explicit stop preserves the supplied operator reason.

## Recording totals

- `acceptedObservationCount` counts records accepted from the session start and subscribed capture events. It excludes the writer-generated `SESSION_END` record.
- `writtenRecordCount` counts every JSONL line, including `SESSION_END`.
- `droppedObservationCount` counts observations rejected because the bounded queue was full. Written sequence numbers remain contiguous when observations are dropped.
- `eventCounts` includes every written record type and therefore sums to `writtenRecordCount`.

## Capture and privacy boundaries

- Event subscribers only take small immutable snapshots; JSONL and handoff files are written on a bounded daemon writer queue.
- Event subscribers return before constructing payloads when no session or category is active, so leaving the plugin enabled but idle does not perform full tick snapshots.
- JSONL is flushed every configured record interval and immediately after session start or an operator marker, reducing loss during an abnormal client exit.
- Player chat is excluded. Captured system/dialogue text has the local display name replaced with `<local-player>`, and player interaction targets are replaced with `<player-target>`.
- The initial bank event establishes a count-only baseline. The recorder does not export the full bank contents.
- Nearby object records are actionable-only and radius-limited by default. Choose **All nearby** only when non-interactive scenery changes are deliberately needed; choose **Interaction focused** for a quieter trace with clicked-object context.
- The date-millisecond/date-second varbits (`12391`, `12392`) and map-clock varp (`3079`) are suppressed by default. Contextual varbit `12393` remains captured. The manifest records whether clock-variable inclusion was enabled.
- Keyboard capture is disabled by default, never runs on login screens, and never writes `keyTyped` characters. The manifest freezes the mode and allowlist used for the session.
- Ground-item records are self/group-owned and radius-limited by default. Disable **Owned ground items only** only when public or static spawns are relevant.
- Exact repeated interactions remain in the lossless trace. Hub review should collapse retries and double-clicks according to observed effects rather than treating each click as a required script action.
- Queue saturation is reported as `droppedObservationCount`; a session with dropped observations should be treated as incomplete evidence.

## Microbot Hub handoff boundary

The client plugin owns capture fidelity, schema stability, artifact finalization, and operator session controls. The Hub project owns pruning accidental observations, grouping interactions into phases, choosing state entry/success/recovery conditions, and implementing the final state machine with Microbot query and interaction APIs.
