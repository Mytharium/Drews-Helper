# Current State

Last updated: 2026-08-07.

## Current Runtime Reset

As of the 2026-08-07 UI-only reset plus Myth's waypoint follow-up, Drew's Helper is intentionally reduced to the visible plugin UI/config shell plus five Drew-owned world-map waypoints.

Runtime shape now:
- `DrewsHelperPlugin` is the only visible RuneLite plugin entry.
- `DrewsHelperConfig` keeps the player-facing settings/buttons surface.
- `DrewsHelperPlugin` owns five persistent world-map waypoint slots, world-map right-click menu entries, and `WorldMapPoint` marker registration.
- `DrewsHelperOverlay` keeps the in-client overlay panel and reports placed waypoint count/coordinates.
- `JewelleryBoxTier` and `PortalNexusTier` remain only because the config UI dropdowns need those enum values.
- The vendored `shortestpath` route engine, pathfinder, map/minimap/tile overlays, transport resources, minigame scanner, teleport highlighter, route telemetry bridge, route diagnostics, saved route state, and route behavior tests have been removed.
- `run-drews-helper-dev.bat` launches the plain Gradle dev client again; the route-diagnostic tee/collector tools are no longer part of the project.

Everything below this note is historical context from the removed route-engine attempt unless a future guide entry explicitly revives it. The active exception is the waypoint marker surface, which is newly rebuilt without restoring the old route engine.

## Active Reference Analysis

Myth requested a deep analysis of Runemoro's upstream `shortest-path` after the UI-only reset. The analysis is now captured in:

```text
docs/C2_Guides/RUNEMORO_SHORTEST_PATH_DEEP_DIVE.md
```

Current takeaways:
- Runemoro's working shape is a single route owner: target, pathfinder, loaded resources, marker, and overlays all live under `ShortestPathPlugin`.
- The solver is breadth-first search over collision-map tile edges plus transport edges.
- Runtime parses only the first six integers from `transports.txt`; labels, object IDs, requirements, and comments are ignored.
- A Drew variant should use Runemoro as an architecture reference, not as a direct restoration of the removed integration.
- Future route work should start with a typed route model, structured transport metadata, worker version tokens, route result statuses, and one immutable snapshot consumed by map/minimap/tile/HUD/highlighter views.

## Working

- Drew's Helper launches as a RuneLite external plugin through `gradlew.bat run`.
- `gradlew.bat run` now loads one visible RuneLite plugin: `Drew's Helper`; a dev-launch probe confirmed `DrewsHelperPlugin` loads and starts.
- Drew's Shortest Path is vendored under `src/main/java/shortestpath/**` with resources under `src/main/resources/**`; `DrewsHelperPlugin` starts it internally and it no longer depends on the Plugin Hub Shortest Path jar being installed.
- The internal route engine lazy-creates its map/minimap/tile/debug overlays after plugin construction to avoid a Guice dependency cycle when loaded as a Drew's Helper feature.
- The internal route engine is hidden and uses `DrewShortestPathInternalConfig` runtime defaults instead of `ConfigManager.getConfig(ShortestPathConfig.class)`, so the copied Shortest Path `Settings` / `Transport Thresholds` panel should not be player-facing.
- The overlay receives Drew's Shortest Path transport telemetry through a direct internal engine listener; the retained `shortestpath/transports` protocol is still posted for compatibility.
- The overlay no longer uses the narrow right-column `Next` display for route text.
- Quest Helper route targets sent through `shortestpath/path` are captured and replayed after login/startup.
- Drew passes whole-category unlock settings for spirit trees, fairy rings, and owned POH features into the internal route engine, so those supported categories can trigger real path recalculation.
- The last route snapshot and the real route target are restored after plugin toggle, logout, world hop, or client restart. Manual right-click/shift-click targets are synced from the internal route engine; Quest Helper targets are still captured from `shortestpath/path` messages.
- The minigame/grouping teleport UI scanner works against the real Grouping UI and walks all RuneLite widget child arrays.
- Minigame destination highlighting is clipped to visible widget bounds, so scrolled-off entries should not leave boxes stuck at the top/bottom of the panel.
- The highlighter draws one outer row box for a minigame destination instead of also boxing the text child.
- Per-destination minigame statuses persist across logout, world hop, and client restart.
- The in-game overlay now reports minigame state as `Minigame Teleports: X/18 Unlocked`.
- Drew converts scanned locked minigames into stable transport keys such as `teleportation_minigames:nightmare_zone`, sends them as `config.blockedTransportKeys`, and replays a real captured route target once when posted telemetry still contains a locked route.
- Drew's Shortest Path consumes `config.blockedTransportKeys` directly and filters matching transports before path edges are built.
- Drew's Helper now owns the player-facing transport config split:
  - Base Drew's Shortest Path transports: gates/passages, boats, ordinary ships/ferries, charter ships, magic carpets, minecarts, home teleports, teleport levers, fixed teleport portals, spellbook teleports, and minigame teleports are enabled internally while Drew's Shortest Path is running, so they are not shown as player unlocks.
  - `Basic Transportation`: account-progress or preference-based travel networks in this order: agility shortcuts, canoes, quetzals, gnome gliders, grapple shortcuts, magic mushtrees, and hot-air balloons.
  - `Advanced Transportation`: spirit trees, fairy rings, mounted glory, portal chamber, portal nexus tier, and jewelry box tier.
  - `Other Transportation`: standard/ancient/lunar/Arceuus/other tablets, 1-use items, teleport scrolls, achievement diary items, combat achievement items, skill capes, quest related items, and other items.
- Manual `Unlocked: ...` and `Use: ...` transport settings are sent to the internal route engine. Baseline transport networks are sent as enabled without frontend toggles. Scanned locked minigames become blocked transport keys only while `Hide Locked Teleports` is enabled; the scanner still remembers lock state when the toggle is off.
- Route config changes mark the active route policy dirty, clear stale HUD telemetry, and replay the saved/current route target directly into the internal route engine with Drew's current config override. The internal route engine still refreshes its active path when it receives a config-only external `shortestpath/path` message, but Drew-origin refreshes no longer depend on event-bus delivery order.
- Manual right-click/shift-click targets are not just saved now: when Drew observes a changed internal route target during gameplay, it immediately replays that target through Drew's current config and locked-minigame policy. The hidden internal route config defaults `postTransports=true` so manual internal routes still publish HUD telemetry.
- Route telemetry publication is tied to the exact completed `Pathfinder` instance. Stale/cancelled pathfinder completions are ignored, and duplicate same-signature route refreshes are suppressed while a request is pending so the refresh burst does not cancel the path before the HUD/highlighter snapshot posts.
- Runemoro `shortest-path` comparison corrected the route ownership model: the internal route engine must apply Drew's current config override before every `restartPathfinding()` rebuild, including manual right-click/shift-click routes, instead of first building an internal route and asking Drew to replay it afterward. Drew's HUD/highlighter may also pull the current completed engine path snapshot when no request is pending.
- Drew's route-policy override now explicitly keeps the original Shortest Path visual contract enabled: `drawMap=true`, `drawMinimap=true`, `drawTiles=true`, `showTransportInfo=true`, and `postTransports=true`. Cancelled/non-done pathfinder instances must not publish telemetry.
- Route diagnostics are now opt-in through Drew's Helper config item `Route Diagnostics`. When enabled, the plugin writes `DREW_ROUTE_DIAG` lines to RuneLite's `client.log` covering Drew policy, active target, config override, blocked keys, pathfinder lifecycle, map/tile overlay render skips, transport telemetry, HUD snapshot state, and first route/minigame steps. Use `tools\collect-route-diagnostics.ps1` after a failed test to extract only those lines.
- The first captured dev diagnostic run on 2026-08-07 produced only startup lines through `LOGIN_SCREEN`: no route request, pathfinder submit, map/tile render, HUD snapshot, config-toggle, or menu-click events. Diagnostics now also cover game-state changes, game ticks, route menu-entry injection, route-menu clicks, selected packed target, and duplicate menu-entry skips so the next run can prove whether the original Shortest Path input path fires.
- Drew's HUD and minigame highlighter use the same availability rule: while `Hide Locked Teleports` is enabled, locked minigame transports are hidden from the main route step list and the highlighter follows the first available minigame step. When the toggle is off, cached locked minigames are treated as usable/highlightable.
- Compared against the OSRS wiki Transportation page on 2026-08-06: outstanding categories needing a Myth decision are wilderness obelisks, POH fairy ring, POH spirit tree, POH wilderness obelisk, and exact subtype filtering for teleport items/tablets/scrolls/capes.
- Drew's `PluginMessage` subscriber runs at high priority and merges active locked-teleport policy into incoming external `shortestpath/path` requests before the internal route engine consumes them, including config-only path refreshes with no target. Drew-owned refreshes bypass that bus and call the internal engine directly.
- The old broad stock-jar fallback (`useTeleportationMinigames=false` after exact reroute fails) is no longer part of the normal route loop. Exact keys are the expected behavior.
- If Drew has not captured a real external `shortestpath/path` target, it should prefer the internal engine's active route target before falling back to saved state. Drew must not treat a transport destination from `shortestpath/transports` as the final route target.
- A source patch for Shortest Path `1.20.6` / `Skretzo/shortest-path@9953d52745f711a38c9cdd4a00bb1d0d57d1fdea` is staged at `docs/patches/shortest-path-blocked-transport-keys.patch`.
- A custom Shortest Path fork was previously built from `Skretzo/shortest-path@8551e6016d053aa5930bb16485069a6997718da3`; that source has now been vendored into `Drews Helper` as Drew's Shortest Path.
- The current-head patch for the installed fork is staged at `docs/patches/shortest-path-blocked-transport-keys-current.patch`.
- The old active `shortest-path_*.jar` was moved out of `C:\Users\drews\.runelite\plugins` to `C:\Users\drews\.runelite\plugins-c2-backups\shortest-path_j65TV2lGDTkVcJlwg4jIvqU_Z2mHP1lUWx9t9lfkfRY.removed-for-drewpath-20260806-165054.jar`.

## Current Overlay Layout

Expected route overlay shape:

```text
Drew's Helper
Current Route Step       1/3
1. Pest Control Minigame Teleport
2. Spirit tree -> Tree Gnome Village
Minigame Teleports      7/18 Unlocked
Locked Routes           1
1. Nightmare Zone Minigame Teleport
```

`Stored Scan` is intentionally hidden from the player-facing overlay now that persistence works. The backend still tracks known/cache counts through `MinigameTeleportUnlockState`.

## Minigame Cache Meaning

- `AVAILABLE`: the Grouping/minigame UI exposed the row without locked/requirement text.
- `LOCKED`: the UI row had requirement text such as quest, boss-completion, or NPC prereq text.
- `UNKNOWN`: no saved decision. Unknown destinations are not treated as locked.

The cache refreshes whenever the menu exposes the row again.

## Drew's Shortest Path Runtime

Active source:
- Project: `C:\Users\drews\OneDrive\Documents\My Games\RuneScape\Drews Helper`
- Internal route engine: `src/main/java/shortestpath/**`
- Vendored resources: `src/main/resources/**`
- Dev launcher: `src/test/java/com/drewshelper/DrewsHelperPluginTest.java`
- Dev console logs: `run-drews-helper-dev.bat` tees Gradle/RuneLite STDOUT into `logs\drews-helper-dev-*.log`; route diagnostics are collected from the newest captured dev log.

Plugin identity:
- Visible RuneLite plugin name: `Drew's Helper`
- Internal route-engine config: hidden runtime defaults through `DrewShortestPathInternalConfig`; player-facing settings live in `DrewsHelperConfig`
- Compatibility message namespace: `shortestpath`

There should be no active Plugin Hub Shortest Path jar in:

```text
C:\Users\drews\.runelite\plugins
```

Drew's Shortest Path consumes:
- `start`
- `target`
- `config` overrides
- `config.blockedTransportKeys`

Expected exact key shape: `teleportation_minigames:nightmare_zone`.

## Known Limitations

Drew's Shortest Path is integrated and build-verified, but the in-game route behavior still needs live testing after the 2026-08-07 policy-refresh patch. Expected behavior: when `Nightmare Zone Minigame Teleport` is scanned as locked and `Hide Locked Teleports` is enabled, the route engine should exclude only `teleportation_minigames:nightmare_zone` and still allow other valid minigame teleports. Turning `Hide Locked Teleports` off should keep the scan cache, stop sending blocked minigame keys, and highlight/use Nightmare Zone again if the solver prefers it.

If every minigame teleport disappears from the route, treat that as evidence that an old fallback path or stale Plugin Hub plugin is active. The normal Drew-owned route loop is exact-key only now.
## 2026-08-07 Waypoint Colour Settings

The config surface has a separate `Settings` section directly below `Other Transportation`. It owns one native RuneLite `Color` config control for the route path plus five native RuneLite `Color` controls for waypoint marker colours:
- Path Colour: Burgundy `#800020`
- Waypoint #1: Dark Gray `#A9A9A9`
- Waypoint #2: Blue `#0072B2`
- Waypoint #3: Green/Teal `#009E73`
- Waypoint #4: Magenta/Purple `#CC79A7`
- Waypoint #5: Orange `#E69F00`

The waypoint values color the matching Drew waypoint marker on the world map. `Path Colour` colors the Drew walking route overlays.

## 2026-08-07 Waypoint Placement

Drew's Helper now provides the first non-route rebuild surface after the reset:
- Open the world map and right-click inside the map bounds.
- The plugin adds waypoint menu entries in visible order `Waypoint #1` through `Waypoint #5`.
- Empty slots show `Set -> Waypoint #X`; placed slots show `Cancel -> Waypoint #X` and clear only that slot.
- Selecting one stores that map tile as a hidden Drew config value (`waypointNPosition`) encoded as `x,y,plane`.
- The plugin registers a colored `WorldMapPoint` marker for each stored waypoint and reloads those markers on plugin startup.
- If at least one waypoint exists, the world-map menu also offers `Clear -> All Waypoints`.
- The overlay displays `Waypoints X/5` and the coordinates of each placed waypoint.

This is now the input surface for Drew walking route guidance. Fast travel, transport scoring, Quest Helper integration, minigame scanning, and teleport highlighter behavior remain removed.

## 2026-08-07 Walking Route Guidance

Myth explicitly asked to rebuild route guidance from placed waypoints, walking only:
- The old `src/main/java/shortestpath/**` package remains absent.
- Drew's new route owner is `com.drewshelper.routing/**`.
- `DrewsHelperPlugin` reads the player location and ordered non-empty waypoint slots, then calculates player -> waypoint #1 -> waypoint #2 and onward.
- Empty waypoint slots are skipped, preserving ordered route intent without forcing all five slots to exist.
- The first solver uses A* over Runemoro's `collision-map.zip` walking collision data and includes no transports, teleports, plugin messages, minigame scanning, Quest Helper targets, or teleport UI highlighter.
- Route work runs on a single background worker; waypoint/player changes cancel stale work through a request id before publishing one immutable `DrewsHelperRouteSnapshot`.
- `DrewsHelperRouteMapOverlay`, `DrewsHelperRouteMinimapOverlay`, and `DrewsHelperRouteTileOverlay` all render that same route snapshot using `DrewsHelperConfig.pathColor()`.
- The walking solver keeps shortest distance as the first priority, then orders equal-cost choices toward the waypoint with Myth's observed primary-axis forward/cardinal preference before diagonal cleanup when one axis is longer. Do not reintroduce the older target-line penalty; it looked prettier but could disagree with how the player actually walks after clicking the endpoint.
- `DrewsHelperRouteTileOverlay` draws placed waypoint endpoint badges on in-scene tiles using the same numbered circle icon style as the waypoint map/minimap marker, using each waypoint's configured marker colour over the shared path colour.
- `DrewsHelperRouteMinimapOverlay` also draws nearby placed waypoint endpoint icons on top of the route squares.
- The Drew overlay now reports route status and walking distance in tiles.
- `THIRD_PARTY_NOTICES.md` records the BSD 2-Clause notice for the copied Runemoro collision-map resource.

Known first-pass limits:
- Plane changes are not routed yet because ladders/stairs/transports are intentionally excluded.
- There is no partial path display; if an exact walking segment cannot be found, the overlay reports no walking path for that segment.
- The route is committed after calculation. Exact on-route player movement trims every leading route tile before the player's current tile, so walk and run speed both leave the current tile highlighted. Nearby movement variance within 10 tiles of the committed route preserves the route without recalculating. A new background route is submitted only when waypoints/config change or the player is more than 10 tiles away from the committed route.
