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
- Folded no-unlock baseline transport networks out of the frontend: gates/passages, ordinary ships/ferries, charter ships, magic carpets, and minecarts now stay enabled internally by default while Drew's Shortest Path is active. Removed the player-facing `Passenger Ships` setting because that was not a useful OSRS unlock category.
- Renamed the frontend transport section to `Basic Transportation` and ordered it per Myth's list: agility shortcuts, canoes, quetzals, gnome gliders, grapple shortcuts, magic mushtrees, hot-air balloons.
- Moved boats, home teleports, minigame teleports, teleport levers, fixed teleport portals, and spellbook teleports into Drew's base route model instead of exposing them as Advanced unlock toggles.
- Added `Other Transportation` with the wiki item/tablet families: standard/ancient/lunar/Arceuus/other tablets, 1-use items, teleport scrolls, achievement diary items, combat achievement items, skill capes, quest related items, and other items.
- Changed minigame filtering semantics: scanned locked minigames still produce exact blocked transport keys even when `Hide Locked Teleports` is off; that setting now controls Drew overlay warning visibility rather than route legality.
- Compared Myth's requested menu split against the OSRS wiki Transportation page and recorded open decisions for wilderness obelisks, POH fairy ring, POH spirit tree, POH wilderness obelisk, and exact item-subtype filtering.
- Hid the internal Drew's Shortest Path engine and removed the ConfigManager-backed `ShortestPathConfig` provider so the copied Shortest Path settings panel does not leak into the player-facing config UI.
- Added manual route target persistence: Drew's Helper now syncs the internal engine's active target and clears saved target/snapshot state when the route is cleared.
- Corrected the `Hide Locked Teleports` route policy after live testing: scanned minigame locks are cached continuously, but blocked minigame transport keys are only sent while the toggle is enabled. Config changes now replay the saved/current target, the internal route engine refreshes active paths on config-only messages, and minigame hints prefer the first available route transport instead of stale locked candidates.
- Removed the event-bus dependency from Drew-origin route refreshes/reroutes: Drew now calls the internal route engine directly with the current config override and blocked transport keys, while keeping `shortestpath/path` for external Quest Helper requests and `shortestpath/transports` telemetry.
- Fixed the manual-route telemetry/policy gap: the hidden internal route config now defaults `postTransports=true`, and newly observed manual right-click/shift-click route targets are immediately replayed through Drew's current config plus blocked minigame keys.

## 2026-08-07

- Fixed the `Hide Locked Teleports` ON refresh path after live testing: Drew now treats route config/blocked-key changes as a dirty active route policy even when the manual target did not change, clears stale HUD telemetry, and replays the current target through the internal route engine.
- Moved Drew's main HUD route list to the same availability contract as locked-route policy: locked minigame steps are hidden from the primary route list while filtering is enabled, but remain visible under `Locked Routes`.
- Restored minigame UI guidance for allowed routes: the highlighter now follows the first available minigame transport step directly, highlights the magic tab/spell before the minigame UI opens, and treats cached locked destinations as green/usable when `Hide Locked Teleports` is off.
- Tightened `ShortestPathBridge.buildConfigOverride` so blocked transport keys are not emitted when `Hide Locked Teleports` is off, even if a caller accidentally passes a stale blocked-key collection.
- Fixed the route telemetry split found by Myth's live test: the internal route engine now publishes transport snapshots directly to Drew's Helper in addition to the legacy `shortestpath/transports` plugin message, ignores stale/cancelled pathfinder completions, and suppresses duplicate same-signature refresh restarts while a route request is pending.
- Compared the vendored route engine against Runemoro `shortest-path` and corrected Drew's integration toward the reference single-owner route model: Drew's current config override is now applied inside the internal route engine before every pathfinder rebuild, including manual map/shift-click targets.
- Changed `blockedTransportKeys` to be explicit on every Drew override. Filtering ON sends the exact locked keys; filtering OFF sends an empty list, preventing stale static engine config from surviving across toggle changes.
- Added a current-engine-path snapshot fallback for Drew's HUD/highlighter when no route request is pending, so missed telemetry no longer leaves the helper blank while the tile overlay still has a completed path.
- Fixed the post-Runemoro-comparison visual regression: Drew's route-policy override now forces `drawMap`, `drawMinimap`, `drawTiles`, `showTransportInfo`, and `postTransports` on, and the internal engine rejects cancelled/non-done pathfinder completions before publishing transport telemetry.
- Added opt-in route diagnostics after Myth reported the map still did not draw: Drew's Helper now has a `Route Diagnostics` config item, route/pathfinder/map/tile/HUD handoff points write `DREW_ROUTE_DIAG` lines to RuneLite `client.log`, and `tools\collect-route-diagnostics.ps1` extracts those lines for review.
- Corrected the diagnostic capture path after live checking showed the Gradle dev run uses test logback and writes to STDOUT, not `.runelite\logs\client.log`. `run-drews-helper-dev.bat` now tees dev console output to `logs\drews-helper-dev-*.log`, and the collector reads the newest captured dev log before falling back to `client.log`.
- Checked Myth's first captured diagnostic run: it produced only six startup lines through `LOGIN_SCREEN`, so the route failure was not yet observable. Added diagnostic probes for game-state changes, game ticks, route menu-entry injection, route-menu clicks, selected packed targets, and duplicate route menu entries.
- Per Myth's reset instruction, removed the mod behavior down to the UI shell only: deleted the vendored `shortestpath` engine, path/transport resources, route/minigame/highlighter/diagnostic helpers, route tests, diagnostic tools/log artifacts, and rewired `DrewsHelperPlugin` to only register the preserved overlay/config UI.
- Deep-analyzed Runemoro `shortest-path` at upstream commit `655f5a24cd1a08984d824fb0692fa29b3b7185f8` and wrote persistent architecture notes to `docs/C2_Guides/RUNEMORO_SHORTEST_PATH_DEEP_DIVE.md`. Captured startup flow, target/menu flow, BFS/pathfinder behavior, collision-map format, transport file semantics, overlay contracts, upstream limitations, and a clean Drew-owned variant architecture.
- Added a `Settings` config section below `Other Transportation` with five native RuneLite waypoint path colour controls and defaults: `#D55E00`, `#0072B2`, `#009E73`, `#CC79A7`, and `#E69F00`.
- Changed Waypoint #1's default colour to `#800020` and added five persistent world-map waypoint slots. The world-map right-click menu now exposes `Set -> Waypoint #1` through `Set -> Waypoint #5`, markers use the configured colours, hidden config persists `waypointNPosition` as `x,y,plane`, and the overlay shows placed waypoint count/coordinates.
- Adjusted waypoint map-menu behavior after live UI testing: entries now display in visible order `Waypoint #1` through `Waypoint #5`, and each placed slot changes from `Set -> Waypoint #X` to `Cancel -> Waypoint #X` so that one waypoint can be cleared without using `Clear -> All Waypoints`.
- Rebuilt first-pass Drew-owned route guidance from the waypoint surface: added `Path Colour` default `#800020`, changed Waypoint #1 marker default to `#A9A9A9`, added walking-only route snapshots/A* solver/collision-map loader, added world-map/minimap/scene tile overlays, added route status/walking distance to the overlay, restored only Runemoro's `collision-map.zip` resource with `THIRD_PARTY_NOTICES.md`, and kept the old `shortestpath` package/transport systems removed.
- Corrected first-pass walking-route shape after Myth's screenshot showed an equal-distance route stepping sideways before heading toward a nearby waypoint. The solver now tie-breaks equal shortest walking paths toward the target line, the overlay status says `Ready` instead of counting the starting tile as route length, and in-scene tile highlights now draw waypoint endpoint badges `WP1` through `WP5` in their configured colours.
- Rechecked Runemoro `shortest-path` at commit `655f5a24cd1a08984d824fb0692fa29b3b7185f8` after Myth reported the player still walked a slightly different line than the highlight. Drew's solver now drops the visual target-line penalty and prefers legal diagonal progress toward the waypoint among equal shortest routes, and waypoint endpoints render as shared numbered circle icons on scene tiles/minimap instead of `WP1` text.
- Changed walking-route maintenance from continuous per-player-tile recalculation to committed-route progress tracking. While the player remains on the committed route, Drew consumes only one leading path tile per tick; if the player leaves the committed route, Drew recalculates from the real player tile to the ordered waypoints.
- Adjusted the walking-route equal-cost preference from diagonal-first to Myth's observed player behavior: primary-axis forward/cardinal movement wins while one axis has more remaining distance, diagonal movement is the next tie-breaker, and diagonal remains first only when both axes are tied.
- Tightened committed-route maintenance after Myth's run-speed test: exact on-route movement now consumes every leading tile before the player's current route tile, nearby movement variance within 10 tiles preserves the committed route, and Drew only recalculates when the player is more than 10 tiles away from the committed route or waypoints/config change.
