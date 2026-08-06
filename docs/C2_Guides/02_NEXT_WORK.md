# Next Work

Last updated: 2026-08-06.

## Priority 1: Exact Locked-Route Rerouting

Goal: when `Hide Locked Teleports` is enabled and Shortest Path chooses a locked route, Drew should make the actual Shortest Path route recalculate through the next best valid option: walking, boats, ships, spirit trees, fairy rings, another unlocked teleport, or another supported transport.

## Important Constraint

The installed Shortest Path plugin-message handler accepts `shortestpath/path` with:

- `start`
- `target`
- `config`

The `config` map overrides Shortest Path's existing settings only. Current exposed settings are category-level, not per-destination. For example, Shortest Path can disable all minigame teleports with `useTeleportationMinigames=false`, but there is no exposed key for "disable Nightmare Zone while still allowing Bounty Hunter."

Drew currently uses the safe category-level hook for spirit trees, fairy rings, and owned POH features. That is real rerouting for those whole categories, but it is not enough for individual minigame destinations.

## Recommended Implementation Path

Patch or fork Shortest Path to add a real exclusion API, then have Drew send it.

Suggested message shape:

```text
namespace: shortestpath
name: path
data:
  start: <packed world point or WorldPoint>
  target: <packed world point or WorldPoint>
  config:
    postTransports: true
    blockedTransportKeys:
      - teleportation_minigames:nightmare_zone
      - teleportation_minigames:blast_furnace
```

Shortest Path-side work:
- Add a config-override key such as `blockedTransportKeys`.
- Normalize each `Transport` into a stable key from type plus object/display text or TSV source ID.
- Filter blocked transports before the pathfinder builds usable transport edges.
- Keep category toggles working as they do now.
- Continue posting transport telemetry so Drew's overlay reflects the actual recalculated route.

Drew-side work after that exists:
- Convert locked minigame statuses into blocked transport keys.
- Include those keys in `ShortestPathBridge.buildConfigOverride`.
- When a posted route contains a locked route, replay the saved target with the blocked list.
- Add tests that verify blocked keys are sent only when `Hide Locked Teleports` is enabled.

## Acceptable Temporary Fallback

If a full Shortest Path patch is too much for one pass, a blunt fallback can temporarily disable a whole category when the current route uses a locked transport. Example: a locked minigame route can trigger `useTeleportationMinigames=false`.

This is not exact enough for the final behavior because it also blocks minigame teleports that Drew has already confirmed unlocked.

## Priority 2: Quest Helper Resume

Current route-target replay works for Quest Helper paths because Quest Helper sends `shortestpath/path` with a target. Full quest resume still needs a Quest Helper bridge that can restore or reopen the active quest helper task itself.

Do not fake Quest Helper clicks until a clean API/message path is identified.
