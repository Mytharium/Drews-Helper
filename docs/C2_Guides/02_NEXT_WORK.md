# Next Work

Last updated: 2026-08-06.

## Priority 1: Live-Test Drew Path Exact Rerouting

Goal: when `Hide Locked Teleports` is enabled and Drew Path would choose a locked route, Drew should recalculate through the next best valid option: walking, boats, ships, spirit trees, fairy rings, another unlocked teleport, or another supported transport.

Current implementation status:
- Drew-side outbound support is implemented. Locked minigame statuses are converted into `blockedTransportKeys`, included in `ShortestPathBridge.buildConfigOverride`, and sent on normal route refresh/replay.
- Drew Path is vendored directly into `Drews Helper` under `src/main/java/shortestpath/**` with resources under `src/main/resources/**`.
- `gradlew.bat run` loads `shortestpath.ShortestPathPlugin` as visible plugin `Drew Path`, then loads `Drew's Helper`.
- Drew Path keeps the `shortestpath/path` and `shortestpath/transports` plugin-message namespace for Quest Helper / Drew Helper compatibility.
- Drew Path uses config group `drewpath`, not the stock Shortest Path config group.
- Drew Path consumes `config.blockedTransportKeys` directly and filters matching transports before path edges are built.
- The old active Plugin Hub `shortest-path_*.jar` was moved out of `.runelite\plugins` and backed up under `.runelite\plugins-c2-backups`.
- The broad stock-jar fallback (`useTeleportationMinigames=false` after exact keys fail) is retired for normal routing. Exact filtering should work or be debugged directly.

## Compatibility Protocol

Drew Path intentionally accepts the same route message shape:

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

Drew Path solver behavior:
- Adds `ShortestPathPlugin.overrideStringSet("blockedTransportKeys")`.
- Stores the override set on `PathfinderConfig.refresh()`.
- Normalizes each `Transport` as `<transport_tsv_name>:<destination_slug>`, e.g. `teleportation_minigames:nightmare_zone`.
- Filters matching transports inside `useTransport(...)` before usable transport edges are built.
- Keeps category toggles working.
- Continues posting transport telemetry so Drew's overlay reflects the actual recalculated route.

Drew-side work completed:
- Convert locked minigame statuses into blocked transport keys.
- Include those keys in `ShortestPathBridge.buildConfigOverride`.
- When a posted route contains a locked route, replay the saved/current target with the blocked list.
- Merge active Drew policy into incoming `shortestpath/path` messages before Drew Path consumes them, including config-only messages without a target.
- Do not replay from `shortestpath/transports` destinations. Those are intermediate transport steps, not the final route target.
- Tests cover blocked-key sending, override parsing, and minigame transport-key generation.

## Test Path

- Fully close normal RuneLite.
- From `C:\Users\drews\OneDrive\Documents\My Games\RuneScape\Drews Helper`, run `gradlew.bat run`.
- Confirm `Drew Path` and `Drew's Helper` are enabled.
- Confirm Plugin Hub Shortest Path is not enabled and no active `shortest-path_*.jar` is in `C:\Users\drews\.runelite\plugins`.
- In Drew's Helper, keep `Hide Locked Teleports` enabled.
- Open the Grouping/minigame teleport UI and confirm Drew has scanned `Nightmare Zone` as locked while at least one other useful minigame teleport is available.
- Request the same route that previously selected Nightmare Zone.
- Watch 10-15 seconds.

Expected: Drew Path no longer selects `Nightmare Zone Minigame Teleport`, the overlay reflects the recalculated route, and it does not bounce every ~2 seconds between old and corrected routes. Other available minigame teleports should still be allowed.

## Priority 2: Quest Helper Resume

Current route-target replay works for Quest Helper paths because Quest Helper sends `shortestpath/path` with a target. Full quest resume still needs a Quest Helper bridge that can restore or reopen the active quest helper task itself.

Do not fake Quest Helper clicks until a clean API/message path is identified.
