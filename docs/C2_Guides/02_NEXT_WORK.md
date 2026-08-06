# Next Work

Last updated: 2026-08-06.

## Priority 1: Exact Locked-Route Rerouting

Goal: when `Hide Locked Teleports` is enabled and Shortest Path chooses a locked route, Drew should make the actual Shortest Path route recalculate through the next best valid option: walking, boats, ships, spirit trees, fairy rings, another unlocked teleport, or another supported transport.

Current implementation status:
- Drew-side outbound support is implemented. Locked minigame statuses are converted into `blockedTransportKeys`, included in `ShortestPathBridge.buildConfigOverride`, and sent on normal route refresh/replay.
- Drew replays the saved/current target once when posted Shortest Path telemetry still contains a locked transport, with a signature guard so the old jar is not spammed if it ignores the new key.
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
- When a posted route contains a locked route, replay the saved/current target with the blocked list.
- Add tests that verify blocked keys are sent only when `Hide Locked Teleports` is enabled.

Next concrete steps:
- Apply `docs/patches/shortest-path-blocked-transport-keys.patch` to a clean `Skretzo/shortest-path` checkout at commit `9953d52745f711a38c9cdd4a00bb1d0d57d1fdea`.
- Build that patched Shortest Path jar.
- With Myth approval, install/test the patched jar in the RuneLite dev profile.
- Test with Nightmare Zone locked and another minigame available. Expected: Shortest Path no longer selects Nightmare Zone, and Drew's overlay transport list reflects the recalculated route from Shortest Path itself.

## Temporary Fallback

If a full Shortest Path patch is too much for one pass, a blunt fallback can temporarily disable a whole category when the current route uses a locked transport. Example: a locked minigame route can trigger `useTeleportationMinigames=false`.

This is not exact enough for the final behavior because it also blocks minigame teleports that Drew has already confirmed unlocked.

## Priority 2: Quest Helper Resume

Current route-target replay works for Quest Helper paths because Quest Helper sends `shortestpath/path` with a target. Full quest resume still needs a Quest Helper bridge that can restore or reopen the active quest helper task itself.

Do not fake Quest Helper clicks until a clean API/message path is identified.
