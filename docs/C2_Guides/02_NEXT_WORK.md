# Next Work

Last updated: 2026-08-06.

## Priority 1: Exact Locked-Route Rerouting

Goal: when `Hide Locked Teleports` is enabled and Shortest Path chooses a locked route, Drew should make the actual Shortest Path route recalculate through the next best valid option: walking, boats, ships, spirit trees, fairy rings, another unlocked teleport, or another supported transport.

Current implementation status:
- Drew-side outbound support is implemented. Locked minigame statuses are converted into `blockedTransportKeys`, included in `ShortestPathBridge.buildConfigOverride`, and sent on normal route refresh/replay.
- Drew first replays a real captured target with the exact blocked list when posted Shortest Path telemetry still contains a locked transport. If no real target is known, Drew sends a config-only request and lets Shortest Path reuse its current target set.
- If the same locked minigame route survives that exact replay, Drew escalates to the stock Shortest Path category hook `useTeleportationMinigames=false` for that reroute signature. This is a real reroute with the active jar, but it disables all minigame teleports.
- Once the stock-jar fallback is active, Drew merges that policy into later incoming `shortestpath/path` requests at high event-bus priority, including config-only path refreshes with no target, suppresses stale locked transport snapshots, and immediately reasserts the fallback if a locked snapshot leaks through.
- The Shortest Path-side source patch is staged at `docs/patches/shortest-path-blocked-transport-keys.patch` against `Skretzo/shortest-path@9953d52745f711a38c9cdd4a00bb1d0d57d1fdea` / Plugin Hub Shortest Path `1.20.6`.

## Important Constraint

The installed Shortest Path plugin-message handler accepts `shortestpath/path` with:

- `start`
- `target`
- `config`

The `config` map overrides Shortest Path's existing settings only. Current exposed settings are category-level, not per-destination. For example, Shortest Path can disable all minigame teleports with `useTeleportationMinigames=false`, but there is no exposed key for "disable Nightmare Zone while still allowing Bounty Hunter."

Drew currently uses the safe category-level hook for spirit trees, fairy rings, and owned POH features. That is real rerouting for those whole categories. For individual minigame destinations, Drew now sends `blockedTransportKeys`, but the active stock Shortest Path jar will ignore that key until the staged patch/fork is installed.

## Patch Install/Test Path

Patch or fork Shortest Path to add a real exclusion API. Drew already sends the message shape below.

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

Shortest Path-side patch contents:
- Adds `ShortestPathPlugin.overrideStringSet("blockedTransportKeys")`.
- Stores the override set on `PathfinderConfig.refresh()`.
- Normalizes each `Transport` as `<transport_tsv_name>:<destination_slug>`, e.g. `teleportation_minigames:nightmare_zone`.
- Filters matching transports inside `useTransport(...)` before usable transport edges are built.
- Keeps category toggles working as they do now.
- Continues posting transport telemetry so Drew's overlay reflects the actual recalculated route.

Drew-side work completed:
- Convert locked minigame statuses into blocked transport keys.
- Include those keys in `ShortestPathBridge.buildConfigOverride`.
- When a posted route contains a locked route, replay the saved/current target with the blocked list first.
- When stock Shortest Path ignores that exact list and posts the same locked minigame route again, send `useTeleportationMinigames=false` as the temporary broad fallback.
- Merge the active Drew policy into incoming `shortestpath/path` messages before Shortest Path consumes them, including config-only messages without a target, so repeated Quest Helper/Shortest Path refreshes inherit the fallback instead of overwriting it.
- Do not replay from `shortestpath/transports` destinations. Those are intermediate transport steps, not the final route target. Use the saved `shortestpath/path` target when present; otherwise send config-only requests.
- Add tests that verify blocked keys are sent only when `Hide Locked Teleports` is enabled.

Next concrete steps:
- Apply `docs/patches/shortest-path-blocked-transport-keys.patch` to a clean `Skretzo/shortest-path` checkout at commit `9953d52745f711a38c9cdd4a00bb1d0d57d1fdea`.
- Build that patched Shortest Path jar.
- With Myth approval, install/test the patched jar in the RuneLite dev profile.
- Test with Nightmare Zone locked and another minigame available. Expected: Shortest Path no longer selects Nightmare Zone, and Drew's overlay transport list reflects the recalculated route from Shortest Path itself.

## Temporary Fallback

Implemented for minigame teleports. If the exact `blockedTransportKeys` replay does not change the route and the same locked minigame route is posted again, Drew temporarily disables the whole minigame teleport category with `useTeleportationMinigames=false`.

The fallback is now applied as an arbitration policy as well as an outbound replay: Drew mutates later `shortestpath/path` request configs before Shortest Path sees them, including targetless config refreshes that otherwise replace Shortest Path's static config override, refuses to save stale locked snapshots while that fallback signature is active, and reasserts the fallback once per game tick if a stale locked snapshot leaks through.

This is not exact enough for the final behavior because it also blocks minigame teleports that Drew has already confirmed unlocked.

## Priority 2: Quest Helper Resume

Current route-target replay works for Quest Helper paths because Quest Helper sends `shortestpath/path` with a target. Full quest resume still needs a Quest Helper bridge that can restore or reopen the active quest helper task itself.

Do not fake Quest Helper clicks until a clean API/message path is identified.
