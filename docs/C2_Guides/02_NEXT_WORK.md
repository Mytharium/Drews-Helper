# Next Work

Last updated: 2026-08-06.

## Drew's Shortest Path Build Plan

Goal: Drew's Helper should own Shortest Path-style routing as one integrated feature. There should be one visible RuneLite plugin, `Drew's Helper`, with Drew's Shortest Path inside it.

Phases:
1. Collapse the architecture: remove the separate visible path plugin seam, load only `Drew's Helper`, and start the vendored route engine internally.
2. Own the core route feature: route target state, world-map right-click destination, shift-right-click tile destination, clear route control, and route drawing on map/minimap/ground/HUD.
3. Integrate locked teleport state: feed Drew's Teleport Options and scanned minigame statuses into the solver, block exact keys such as `teleportation_minigames:nightmare_zone`, and surface unreachable/blocked-route warnings.
4. Merge config parity: keep guidance controls in Teleport Options, expose Drew-owned transport unlocks under Basic Transportation / Advanced Transportation / Other Transportation, add remaining route-specific controls under Routing Options, and keep the inherited `ShortestPathConfig` panel hidden/runtime-only.
5. Improve beyond stock Shortest Path: prefer known unlocked routes, explain rejected transports, support route quality modes, add quest-prep routes, use cooldown-aware rerouting, and show clearer route reasoning in the HUD.
6. Live validation: test without Plugin Hub Shortest Path installed, verify manual routes, Quest Helper routes, locked Nightmare Zone exclusion, other minigame teleport availability, and no route bouncing.

Current phase:
- Phase 1 is complete, build-verified, and dev-launch probe verified. `Drew's Helper` is the only visible plugin target, and `DrewsHelperPlugin` owns the internal route-engine lifecycle.
- The missing-plugin-list issue was a Guice construction cycle in the internal route overlays; `shortestpath.ShortestPathPlugin` now lazy-creates those overlays through providers after the route engine itself is constructed.
- Part of Phase 4 was pulled forward by Myth's UI direction: player-facing transport unlocks now belong to Drew's own `Basic Transportation`, `Advanced Transportation`, and `Other Transportation` sections, not the copied Shortest Path `Settings` bucket. Baseline travel networks with no meaningful account unlock are default-on internally instead of shown as `Unlocked: ...` toggles.
- The copied Shortest Path config surface is no longer ConfigManager-backed. The internal engine uses `DrewShortestPathInternalConfig`, and `ShortestPathPlugin` is marked hidden so the visible config should be Drew's Helper only.
- Manual right-click/shift-click route targets are now synced from the internal engine into `DrewsHelperSessionState`; route clear also clears the saved target/snapshot so stale routes are not replayed.
- Next coding phase is Phase 2: expose the core route controls through Drew's Helper and validate map/minimap/ground/HUD drawing from the single-plugin runtime.

## Priority 1: Live-Test Drew's Shortest Path Exact Rerouting

Goal: when `Hide Locked Teleports` is enabled and Drew's Shortest Path would choose a locked route, Drew should recalculate through the next best valid option: walking, boats, ships, spirit trees, fairy rings, another unlocked teleport, or another supported transport.

Current implementation status:
- Drew-side outbound support is implemented. Locked minigame statuses are converted into `blockedTransportKeys`, included in `ShortestPathBridge.buildConfigOverride`, and sent on normal route refresh/replay.
- Drew's Shortest Path is vendored directly into `Drews Helper` under `src/main/java/shortestpath/**` with resources under `src/main/resources/**`.
- `gradlew.bat run` loads only visible plugin `Drew's Helper`; `DrewsHelperPlugin` starts the vendored route engine internally.
- Drew's Shortest Path keeps the `shortestpath/path` and `shortestpath/transports` plugin-message namespace for Quest Helper / Drew Helper compatibility.
- Drew's Shortest Path uses hidden runtime defaults for remaining inherited display/debug/threshold behavior. Add Drew-owned config items later only when Myth wants those controls visible.
- Drew's Helper now owns the transportation unlock menu shape:
  - Base Drew's Shortest Path transports: gates/passages, boats, ordinary ships/ferries, charter ships, magic carpets, minecarts, home teleports, teleport levers, fixed teleport portals, spellbook teleports, and minigame teleports are always enabled internally.
  - `Basic Transportation`: agility shortcuts, canoes, quetzals, gnome gliders, grapple shortcuts, magic mushtrees, and hot-air balloons.
  - `Advanced Transportation`: spirit trees, fairy rings, mounted glory, portal chamber, portal nexus tier, and jewelry box tier.
  - `Other Transportation`: standard/ancient/lunar/Arceuus/other tablets, 1-use items, teleport scrolls, achievement diary items, combat achievement items, skill capes, quest related items, and other items.
- Locked minigames are scanner-filtered by exact `blockedTransportKeys` while `Hide Locked Teleports` is enabled, even though Minigame Teleports are a base-on category. Turning that toggle off keeps the scan cache but stops sending blocked keys so the base solver can use those routes again.
- Config changes now replay the saved/current target, and targetless config-only `shortestpath/path` messages refresh the internal engine's current path. This is required for toggles to update the active route instead of waiting for a new destination.
- Minigame hint overlays now prefer the first available route transport, so a locked Nightmare Zone hint should not remain over the path tile while Drew's HUD is showing Pest Control as the active route step.
- Wiki comparison open decisions: whether to expose wilderness obelisks, POH fairy ring, POH spirit tree, and POH wilderness obelisk in Advanced/Other; and whether to add exact transport-item subtype filtering beyond the internal broad `useTeleportationItems` mode.
- Drew's Shortest Path consumes `config.blockedTransportKeys` directly and filters matching transports before path edges are built.
- The old active Plugin Hub `shortest-path_*.jar` was moved out of `.runelite\plugins` and backed up under `.runelite\plugins-c2-backups`.
- The broad stock-jar fallback (`useTeleportationMinigames=false` after exact keys fail) is retired for normal routing. Exact filtering should work or be debugged directly.

## Compatibility Protocol

Drew's Shortest Path intentionally accepts the same route message shape:

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

Drew's Shortest Path solver behavior:
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
- Merge active Drew policy into incoming `shortestpath/path` messages before the internal route engine consumes them, including config-only messages without a target.
- Do not replay from `shortestpath/transports` destinations. Those are intermediate transport steps, not the final route target.
- Tests cover blocked-key sending, override parsing, and minigame transport-key generation.

## Test Path

- Fully close normal RuneLite.
- From `C:\Users\drews\OneDrive\Documents\My Games\RuneScape\Drews Helper`, run `run-drews-helper-dev.bat` or `gradlew.bat run`.
- Do not use the normal RuneLite shortcut for this test. The normal launcher cannot see Drew's local source plugin.
- Confirm only `Drew's Helper` is enabled from this project; there should be no separate `Drew Path` plugin entry.
- Confirm Plugin Hub Shortest Path is not enabled and no active `shortest-path_*.jar` is in `C:\Users\drews\.runelite\plugins`.
- In Drew's Helper, keep `Hide Locked Teleports` enabled.
- Open the Grouping/minigame teleport UI and confirm Drew has scanned `Nightmare Zone` as locked while at least one other useful minigame teleport is available.
- Request the same route that previously selected Nightmare Zone.
- Watch 10-15 seconds.

Expected with `Hide Locked Teleports` on: Drew's Shortest Path no longer selects `Nightmare Zone Minigame Teleport`, the overlay reflects the recalculated route, and it does not bounce every ~2 seconds between old and corrected routes. Other available minigame teleports should still be allowed.

Expected after turning `Hide Locked Teleports` off: Drew keeps the saved scan result, stops sending `teleportation_minigames:nightmare_zone` as a blocked key, and refreshes the active route so Nightmare Zone can be used again if the solver prefers it.

## Priority 2: Quest Helper Resume

Current route-target replay works for Quest Helper paths because Quest Helper sends `shortestpath/path` with a target. Full quest resume still needs a Quest Helper bridge that can restore or reopen the active quest helper task itself.

Do not fake Quest Helper clicks until a clean API/message path is identified.
