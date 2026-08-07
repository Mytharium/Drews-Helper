# Changelog Agent Notes

## 2026-08-06

- Built Drew's Helper RuneLite plugin scaffold and local Gradle run path.
- Added Shortest Path route-feed bridge using plugin messages and `postTransports=true`.
- Added overlay rendering for route transport telemetry.
- Fixed overlay width/formatting so long route text is not squeezed into the narrow right column.
- Added locked-teleport availability checks for Drew-owned display/highlight behavior.
- Replaced the broad "all minigame teleports unlocked" model with per-destination Grouping/minigame UI scanning.
- Added Grouping UI widget walking across children, dynamic children, static children, and nested children.
- Added visible-bounds clipping so scrolled-off minigame rows are not highlighted.
- Added per-destination minigame status persistence through RuneLite config.
- Fixed the persistence bug where render-time scanning updated memory without saving config.
- Added saved route snapshot and Shortest Path target replay across plugin toggle, logout, world hop, and client restart.
- Updated overlay to show `Current Route Step X/Y`, the full numbered transport list, `Minigame Teleports`, and numbered `Locked Routes`.
- Passed spirit-tree and fairy-ring unlock settings through to Shortest Path category-level config overrides.
- Added Drew-side exact blocked-transport config support: scanned locked minigames now become `blockedTransportKeys` such as `teleportation_minigames:nightmare_zone`.
- Added a guarded reroute replay when posted Shortest Path telemetry still contains a locked transport.
- Added `docs/patches/shortest-path-blocked-transport-keys.patch`, a source patch for Shortest Path `1.20.6` / `Skretzo/shortest-path@9953d52745f711a38c9cdd4a00bb1d0d57d1fdea`.
- Adjusted the overlay so `Current Route Step` stays left/white and the `X/Y` step count renders right/orange like the other stat rows.
- Added a stock-jar fallback for locked minigame routes: after one exact `blockedTransportKeys` replay is ignored, Drew sends `useTeleportationMinigames=false` for the active reroute signature so Shortest Path recalculates without minigame teleports.
- Added a bridge test for the minigame-category fallback override.
- Fixed route priority fighting with Shortest Path/Quest Helper refreshes: Drew now runs its `PluginMessage` handler at high priority, merges active locked-teleport overrides into incoming `shortestpath/path` configs before Shortest Path consumes them, and suppresses stale locked snapshots while the fallback is active.
- Added bridge tests for merging Drew overrides into existing path requests and detecting Drew-owned path request markers.
- Fixed another Shortest Path route fight source: Drew no longer treats the last `shortestpath/transports` destination as a replay target. If no real path target was captured, Drew sends config-only fallback requests so Shortest Path keeps its current target set, and it reasserts the fallback when a stale locked snapshot leaks through.
- Fixed targetless Shortest Path config refresh arbitration: Drew now recognizes config-only `shortestpath/path` messages as path requests and merges the active fallback into them instead of only handling target-bearing route requests.
- Built and installed a C2-owned Shortest Path fork from `Skretzo/shortest-path@8551e6016d053aa5930bb16485069a6997718da3`; the active RuneLite Shortest Path jar now consumes Drew's exact `blockedTransportKeys` exclusions.
- Added `docs/patches/shortest-path-blocked-transport-keys-current.patch` for the current installed fork, including the two new tests and the upstream collision-data test baseline correction.
- Backed up the stock Shortest Path jar outside the active plugin folder at `C:\Users\drews\.runelite\plugins-c2-backups\shortest-path_j65TV2lGDTkVcJlwg4jIvqU_Z2mHP1lUWx9t9lfkfRY.stock-20260806-163457.jar`.
- Vendored the patched pathfinder source/resources into `Drews Helper` as Drew-owned `Drew Path`, loaded by the existing `gradlew.bat run` dev launcher alongside `Drew's Helper`.
- Renamed the visible pathfinder plugin/config identity to `Drew Path` / `drewpath` while keeping the `shortestpath` plugin-message namespace for Quest Helper and Drew Helper compatibility.
- Removed the automatic broad minigame-category fallback from Drew's route loop; exact `blockedTransportKeys` filtering is now the normal path.
- Moved the remaining active `shortest-path_*.jar` out of `.runelite\plugins` to `.runelite\plugins-c2-backups\shortest-path_j65TV2lGDTkVcJlwg4jIvqU_Z2mHP1lUWx9t9lfkfRY.removed-for-drewpath-20260806-165054.jar`.
- Added Drew Helper tests for pathfinder `blockedTransportKeys` parsing and minigame transport-key generation.

## Open Technical Note

Exact locked-route rerouting is integrated but not yet live-validated. The active runtime is now Drew Helper's internal Drew's Shortest Path feature, not a Plugin Hub Shortest Path jar. Do not infer a route target from `shortestpath/transports`; those destinations are intermediate route steps. Do treat targetless `shortestpath/path` messages with `config` as authoritative route refreshes, because they can replace the route engine's static config override.

## 2026-08-06 Phase 1 Update

- Wrote the Drew's Shortest Path build plan into `02_NEXT_WORK.md` with six phases: architecture collapse, core route feature, locked teleport integration, config parity, improvements beyond stock Shortest Path, and live validation.
- Collapsed the visible plugin seam for Phase 1: `runelite-plugin.properties` and the dev launcher now load only `Drew's Helper`.
- Made `DrewsHelperPlugin` own the vendored path engine lifecycle by starting/stopping the internal `shortestpath.ShortestPathPlugin` instance and registering its event subscribers through Drew's Helper.
- Scoped the vendored route engine singleton so overlays, hotkeys, route state, and Drew's Helper use the same internal pathfinder instance.
- Added `run-drews-helper-dev.bat` and made the dev launcher force RuneLite `--developer-mode --debug` args so the local Drew's Helper source plugin is visible only through the dev client path, not the normal RuneLite shortcut.
- Fixed the Phase 1 dev-load failure where RuneLite could not show `Drew's Helper`: the internal route engine now lazy-creates its path overlays through providers, breaking the Guice cycle `DrewsHelperPlugin -> ShortestPathPlugin -> overlay -> ShortestPathPlugin`.
- Verified by controlled dev-launch probe that `DrewsHelperPlugin` loads, starts, and reaches `Plugin DrewsHelperPlugin is now running`.
- Pulled the transportation config shape into Drew's style: added Drew-owned `Transportation` and `Advanced Transportation` sections, changed route toggles to `Unlocked: ...` labels, mapped those settings into the internal route-engine config override, and added a real `useTransports` hook for generic gates/passages.
