# Current State

Last updated: 2026-08-09.

## Current Runtime Reset

As of the 2026-08-07 UI-only reset plus Myth's waypoint/route follow-ups, Drew's Helper is the visible plugin UI/config shell, five Drew-owned world-map waypoints, and a Drew-owned route graph built from walking collision plus selected baseline transport edges.

## 2026-08-14 Batch A Route-Shape Validation

Batch A closed the question of whether the Falador southeast fix was only a local tree-pocket
issue. It was not. Myth hand-walked six longer routes with the benchmark recorder on, and the
completed `DREW_ROUTE_BENCH reason=target` rows showed multiple non-Falador route mismatches:

- A1 Varrock to Grand Exchange: `exp=73`, `actual=74`, `full=false`, `lenDelta=1`, `maxDev=7`;
  first miss was legal but longer from `(3212,3424,0)` toward `(3165,3484,0)`.
- A2 Lumbridge to Draynor bank: `exp=137`, `actual=155`, `lenDelta=18`, `maxDev=5`; Myth also
  saw a Lumbridge dining-table leak and had to open doors manually.
- A3 Draynor bank to Draynor Manor: `exp=111`, `actual=125`, `lenDelta=14`, `maxDev=5`; Myth
  reported dead-tree leaks near the Manor approach.
- A4 Lumbridge east to Al Kharid bank: `exp=109`, `actual=109`, `lenDelta=0`, `maxDev=2`; mostly
  benign route-shape noise.
- A5 Falador square to Barbarian Village: `exp=135`, `actual=139`, `lenDelta=4`, `maxDev=2`;
  mild long-route shape miss.
- A6 Varrock east bank to Sawmill: `exp=88`, `actual=94`, `lenDelta=6`, `maxDev=6`; a non-benign
  legal shape miss where `shapeShadow` looked better than visible.

Interpretation: do not keep adding one-off route windows for Batch A. The route system needs a
segment-aware/passive recorder before the next behavioral patch so we can separate player
multi-click segmentation from solver ranker misses, object-profile misses, door/traversal-state
requirements, and true collision-map errors. Tree/dead-tree/table profile changes remain gated by
live route pins and should not ship from raw Batch A notes alone.

## 2026-08-14 Segment-Aware Route Validation

D-0184 adds a second default-OFF route diagnostic: `Settings` -> `Log Route Segments`.
D-0185 adds an interruption-aware label so normal re-click cadence does not look like a route bug.

When enabled, Drew watches RuneLite's local walking destination and records each clicked walking
segment against the displayed current-leg route slice that was visible when the click started. It
writes `DREW_ROUTE_SEGMENT v1` rows to `%USERPROFILE%\.runelite\drews-route-segments.txt` and the
Gradle log.

The segment row includes:

- `start`, `clickDest`, and `routeTarget`.
- `routeStart` / `routeDest` anchors showing whether the segment endpoints were exact, near, or
  off the displayed route.
- `expectedPath` as the displayed route slice for that one click, not the whole waypoint route.
- `actualPath` as the tiles the player walked before reaching the click destination, stopping,
  clearing the destination, hitting the tick limit, or clicking a new destination.
- `completed=true|false`, where `false` means the player clicked again, stopped, cleared the
  destination, or hit the diagnostic limit before the original click destination was reached.
- `route={...}`, `divergence={...}`, and `edgeValidation={...}` diagnostics.
- `classification` values such as `match`, `click-destination-off-route`,
  `legal-detour-or-object-pressure`, `legal-route-ranker-or-click-shape`, and
  `static-map-disagrees-with-live-step`. If the player clicks again before the segment completes,
  the row is labeled `interrupted-reclick-clean-prefix` or
  `interrupted-reclick-after-divergence` instead of being treated as object/profile evidence by
  default.

This is evidence-only. It does not change path selection, promote `shapeShadow`, add object
profiles, or write transport/collision rows. Its job is to split Batch A's whole-route mismatches
into concrete click segments before table/dead-tree/tree profile work or route-ranker tuning.

Runtime shape now:
- `DrewsHelperPlugin` is the only visible RuneLite plugin entry.
- `DrewsHelperConfig` keeps the player-facing settings/buttons surface.
- `DrewsHelperPlugin` owns five persistent world-map waypoint slots, world-map right-click menu entries, and `WorldMapPoint` marker registration.
- `DrewsHelperPlugin` owns route worker lifecycle, committed route progress, and the active immutable `DrewsHelperRouteSnapshot`.
- `com.drewshelper.routing/**` owns collision loading, baseline/Wilderness transport graph loading, and the A* route solver.
- `com.drewshelper.routing.ui/**` owns world-map, minimap, and in-scene route rendering from that one snapshot.
- `DrewsHelperOverlay` keeps the in-client overlay panel and reports route status, route steps, placed waypoint count, and coordinates.
- `JewelleryBoxTier` and `PortalNexusTier` remain only because the config UI dropdowns need those enum values.
- The vendored `shortestpath` route engine, pathfinder, old map/minimap/tile overlays, old transport resources, minigame scanner, teleport highlighter, route telemetry bridge, route diagnostics, saved route state, and old route behavior tests have been removed.
- `run-drews-helper-dev.bat` launches the plain Gradle dev client again; the route-diagnostic tee/collector tools are no longer part of the project.

## Active Home Teleport Routing

As of 2026-08-09, the Drew-owned route graph includes home teleports from `teleportation_spells_home.tsv`.

- Home teleport rows are `BASELINE` transport edges with source `-1,-1,0` (`ANYWHERE`), so they are innate routing options rather than a frontend checkbox.
- Originless edges are offered only at the start of each waypoint leg. A multi-waypoint route may use a home teleport at leg 1, leg 2, etc., but not halfway through walking a leg.
- Cooldown requirements use upstream's `@` var syntax, e.g. `892@30`. A cooldown is usable only when `(currentEpochMinute - storedEpochMinute) > cooldownMinutes`.
- Unknown cooldown vars are locked. Unknown ordinary quest/var requirements remain permissive.
- The generated resource contains 16 home-teleport rows, including all four Lumbridge Home Teleport variants with distinct `VarPlayers` requirements.
- Until Wilderness teleport-level rules are modeled, originless teleports are under-offered from Wilderness starts.
- `DrewsHelperTravelEstimate` recognizes originless route jumps as real `Actions` rows with the upstream duration and label, not as walking.

Everything below this note is historical context from the removed route-engine attempt unless a future guide entry explicitly revives it. The active exceptions are the waypoint marker surface and route-guidance sections rebuilt without restoring the old route engine.

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

The waypoint values color the matching Drew waypoint marker on the world map. `Path Colour` colors the Drew route overlays.

`Other Transportation` now includes one active route toggle below `Use: Other Items`: `Use: Wilderness Transports`. It controls both Wilderness levers and Wilderness obelisks. Ordinary click/pay/default transport edges are built into the graph and do not have individual frontend toggles.

## 2026-08-07 Waypoint Placement

Drew's Helper now provides the first non-route rebuild surface after the reset:
- Open the world map and right-click inside the map bounds.
- The plugin adds waypoint menu entries in visible order `Waypoint #1` through `Waypoint #5`.
- Empty slots show `Set -> Waypoint #X`; placed slots show `Cancel -> Waypoint #X` and clear only that slot.
- Selecting one stores that map tile as a hidden Drew config value (`waypointNPosition`) encoded as `x,y,plane`.
- The plugin registers a colored `WorldMapPoint` marker for each stored waypoint and reloads those markers on plugin startup.
- If at least one waypoint exists, the world-map menu also offers `Clear -> All Waypoints`.
- The overlay displays `Waypoints X/5` and the coordinates of each placed waypoint.

This is now the input surface for Drew route guidance. Quest Helper integration, minigame scanning, teleport highlighter behavior, route diagnostics, and the old vendored route stack remain removed.

## 2026-08-07 Route Guidance

Myth explicitly asked to rebuild route guidance from placed waypoints, then add baseline click/pay physical transports:
- The old `src/main/java/shortestpath/**` package remains absent.
- Drew's new route owner is `com.drewshelper.routing/**`.
- `DrewsHelperPlugin` reads the player location and ordered non-empty waypoint slots, then calculates player -> waypoint #1 -> waypoint #2 and onward.
- Empty waypoint slots are skipped, preserving ordered route intent without forcing all five slots to exist.
- The solver defaults to A* over Runemoro's `collision-map.zip` walking collision data plus Drew's `drewshelper-transports.tsv` transport graph.
- `Routing Options` no longer exposes a route-solver selector. The temporary BFS test mode was removed after Myth's live samples showed it was slower and did not match the client route shape better than the Drew A* solver.
- `Settings` -> `Log Benchmark Movement` remains default OFF as an opt-in overlay-vs-client diagnostic. It logs `DREW_ROUTE_BENCH` movement comparisons while the player walks the displayed route, including the complete displayed `expectedPath=[...]` at route start and the completed `actualPath=[...]` after the walk. It no longer solves or logs an alternate BFS path.
- Benchmark reports compare first-step direction, first 5 and 10 movement ticks, full tile-sequence equality, path-length delta, max lateral deviation, turn-count delta, solve time, and expanded-node count. They also log coordinate traces for route start, target, first 10 expected path tiles, first 10 actual movement tiles, first divergence index, predicted/actual windows around the divergence, legal candidate moves from the fork tile with predicted/actual choices marked, and `edgeValidation` for the observed live edge. Use those logs to identify start/target/click-tile alignment, movement-order tie behavior, and collision-map/live-client disagreement.
- Baseline click/pay/default transport edges are always available in the route graph. This includes selected maintained Shortest Path rows for click objects/gates/gangplanks, ordinary ships/ferries/boats, charter ships, magic carpets, and minecarts after filtering out rows with explicit skill/quest requirements.
- Wilderness levers and Wilderness obelisks are the only transport family in this pass behind a visible setting: `Other Transportation` -> `Use: Wilderness Transports`, default OFF.
- Teleports, Quest Helper targets, minigame scanning, teleport UI highlighter, and route diagnostics remain removed.
- Route work runs on a single background worker; waypoint/player changes cancel stale work through a request id before publishing one immutable `DrewsHelperRouteSnapshot`.
- `DrewsHelperRouteMapOverlay`, `DrewsHelperRouteMinimapOverlay`, and `DrewsHelperRouteTileOverlay` all render that same route snapshot using `DrewsHelperConfig.pathColor()`.
- The walking solver keeps shortest distance as the first priority, then orders equal-cost choices toward the waypoint with Myth's observed cardinal-before-diagonal preference when one axis is longer. Short route segments now do a bounded reverse-distance pass from the target and reconstruct the displayed path by client-style legal step order, so the displayed route does not publish the first diagonal-shaped shortest route when a better equal-length cardinal fork exists. Diagonal remains first only when both axes are tied because a cardinal first step would lengthen an open-field Chebyshev route. Do not reintroduce the older target-line penalty; it looked prettier but could disagree with how the player actually walks after clicking the endpoint.
- `DrewsHelperRouteTileOverlay` draws placed waypoint endpoint badges on in-scene tiles using the same numbered circle icon style as the waypoint map/minimap marker, using each waypoint's configured marker colour over the shared path colour.
- `DrewsHelperRouteMinimapOverlay` also draws nearby placed waypoint endpoint icons on top of the route squares.
- Non-adjacent consecutive route points are treated as transport jumps and render as dotted connectors on the world map, plus on the minimap when both endpoints are in minimap range. Normal one-tile walking steps remain filled path tiles, with a minimum 4px world-map footprint so routes stay visible when zoomed out.
- The Drew overlay now reports route status and route steps. Transport jumps count as one graph step, so this is no longer labelled as pure walking distance.
- `THIRD_PARTY_NOTICES.md` records the BSD 2-Clause notice for the copied Runemoro collision-map resource and generated Skretzo transport-derived resource.

Known first-pass limits:
- Transport-step labels are not rendered yet; dotted connectors show where a transport goes, but they do not yet show text such as "Charter ship" or "Minecart".
- There is no partial path display; if an exact segment cannot be found, the overlay reports no route for that segment.
- The route is committed after calculation. Exact on-route player movement trims every leading route tile before the player's current tile, so walk and run speed both leave the current tile highlighted. Nearby movement variance within 10 tiles of the committed route preserves the route without recalculating. A new background route is submitted only when waypoints/config change or the player is more than 10 tiles away from the committed route.
- Myth's repeated clean Path 1 / Path 3 samples proved target-specific live-client branches that Drew's static collision graph or equal-length route ranking did not choose. The route engine now has scoped local walking overrides for those target paths: Path 1 toward `(2932,3214,0)` prefers the observed southwest branch through `(2939,3222,0) -> (2938,3221,0)` and the final equal-length tail `(2935,3218,0) -> (2934,3217,0)`; Path 3 toward `(2970,3229,0)` prefers the observed northeast branch through `(2967,3231,0) -> (2968,3230,0)`. These are target-aware route-shape overrides only; they do not replace the collision map globally.
- D-0177/D-0178 add the same kind of evidence-scoped correction for the Falador southeast tree-line target `(2951,3208,0)`, but as a forced local route window from Myth's completed benchmark trace. D-0180 extends that window to the east-pressure start `(2946,3239,0)` and adds the reverse target `(2942,3243,0)` from Myth's creative-control pass. Forced windows are exact observed one-tile route segments, not global map data; this is not broad tree blocking, not `shapeShadow` promotion, and not a global ranker change.

### 2026-08-07 21:05 UTC - Path 1 final-tail override added
- Myth reran Path 1 to (2932,3214,0) after D-0045.
- The old (2935,3218,0) -> (2934,3217,0) tail preference worked, but live movement diverged one step later.
- Added a target-aware final-tail sequence for (2934,3217,0) -> (2933,3216,0) -> (2932,3215,0) -> (2932,3214,0).
- This remains local to target (2932,3214,0) and does not modify global collision data.

### 2026-08-07 21:28 UTC - Benchmark capture lifecycle and shape diagnostics
- Myth confirmed Path 1 toward (2932,3214,0) and Path 3 toward (2970,3229,0) now match the live client exactly with no divergence.
- `Log Benchmark Movement` capture starts in a pending-start state. It waits until the player reaches the displayed route start, or one of the first few route tiles, before recording actual movement.
- Off-route pre-start movement is discarded as `reason=stale-start ignored={...}` instead of being reported as a false `idx=0` divergence.
- `DREW_ROUTE_BENCH` reports now include `shape={...}` diagnostics for completed target samples. The shape diagnostic compares expected route shape against actual client movement using line-error, diagonal/cardinal step distribution, turn count, and a diagnostic-only `winner`.
- The shape diagnostic is not used for route selection yet. The current route selection still uses A* plus client-style final ranking and the target-aware local overrides from D-0044 through D-0046.

### 2026-08-07 22:20 UTC - Multi-waypoint benchmark diagnostics are segment-aware
- Myth's five-waypoint random chain completed, but the old diagnostic treated the final waypoint as the `edgeValidation` target for a fork that happened on the first leg.
- `DREW_ROUTE_BENCH` now maps a divergence index to the active waypoint segment before logging `candidates`, `edgeValidation`, and multi-waypoint `shape`.
- Chained route reports now show the current segment target plus `finalTarget` when those differ. This keeps local edge validation and shape scoring meaningful for waypoint chains.
- This is diagnostic-only. It does not change route solving, waypoint behavior, local overrides, or overlay rendering.

### 2026-08-07 22:45 UTC - No-override shadow route diagnostics
- Myth's clean Path 1, Path 2, Path 3, and five-waypoint random-chain sample all matched the current displayed route, which proved the active solver plus local overrides are stable but did not prove the local overrides can be removed.
- `DREW_ROUTE_BENCH` reports now include `shadow={...}` on completed samples. The shadow route is solved with target-aware local walking overrides disabled, then compared against the actual movement.
- Use `shadow={status=ready overridesMatter=true ... winner=visible}` as evidence that the current local override still explains live movement better than the override-free baseline.
- Use `shadow={status=ready overridesMatter=true ... winner=shadow}` or repeated `winner=tie` on the old Path 1 / Path 3 control routes as evidence that the general route ranker may be ready to replace the local overrides.
- This is diagnostic-only. The visible route still uses the active solver and the target-aware local overrides from D-0044 through D-0046.

### 2026-08-08 01:45 UTC - D-0052 merge-back route diagnostics
- Myth's latest five-waypoint ordered chain stayed on one benchmark capture even after a same-square double-click. The completed sample diverged on the segment toward `(2983,3246,0)` from `(2976,3252,0)`: Drew displayed `(2977,3251,0)` while the client walked `(2977,3252,0)`.
- The live path merged back onto the displayed path two tiles later, so this class of sample needs explicit merge-back reporting before it is used for shape-ranker promotion or local override decisions.
- `DREW_ROUTE_BENCH` divergence strings now include `mergeBack={...}` with expected index, actual index, step delta, and merge tile when the actual path rejoins the displayed route after a divergence.
- This is diagnostic-only. Visible route behavior, `shadow`, `shapeShadow`, local walking overrides, waypoint ordering, and capture lifecycle are unchanged.

### 2026-08-08 01:57 UTC - D-0053 merge-aware diagnostic scoring
- Myth's post-D-0052 rerun confirmed the same local fork class with `mergeBack={expectedIdx=41 actualIdx=41 stepDelta=0 point=(2979,3250,0)}`. The first observed edge still reported `longer=true`, but the full movement rejoined the displayed route on schedule.
- `DREW_ROUTE_BENCH` divergence strings now include `classification=<...>` and `benign=<...>`. `classification=sameTimePermutation benign=true` means the client took a different local tile order but rejoined the displayed route at the same movement index.
- `shadow={...}` and `shapeShadow={...}` now include `fit={...}` and use merge-aware route-fit scoring for their `winner`. Exact matches still win, no-merge drift still loses hard, and same-time permutations are scored as low-penalty diagnostics instead of hard route failures.
- This remains diagnostic-only. The visible route, local Path 1 / Path 3 overrides, `shapeShadow` route solving, waypoint ordering, and benchmark capture lifecycle are unchanged.

### 2026-08-14 00:00 UTC - Falador southeast benchmark trace patched into visible route
- Myth's completed `DREW_ROUTE_BENCH reason=target` sample for `2942,3243,0 -> 2951,3208,0` showed the visible route took 36 points while the live client walked 39 points and stayed east of the drawn line until merging back near `(2952,3209,0)`.
- The route engine now has a forced target-aware local route window for target `(2951,3208,0)` using that completed walked tile sequence. This makes the visible route follow the observed client path through the tree-line pocket instead of selecting the shorter-looking equal-legal route shape.
- The correction remains narrow: it applies only when solving toward `(2951,3208,0)`, uses exact one-tile steps from the completed live trace, and leaves the old control route toward `(2962,3214,0)` unchanged.

### 2026-08-14 00:55 UTC - Falador southeast live rerun validated
- Myth reran `2942,3243,0 -> 2951,3208,0` after the forced local route window patch. The completed `DREW_ROUTE_BENCH reason=target` row showed displayed `expectedPath` and walked `actualPath` as the same 39-point route.
- The live result was exact: `full=true`, `lenDelta=0`, `maxDev=0`, `turnDelta=0`, and `divergence={none}`. The corrected visible route includes the client-observed fork through `(2943,3235,0)` and no longer cuts tight through the tree-line pocket on this pinned route.
- Next validation, if Myth wants to keep the game up, is creative route-shape control data with in-game run OFF: reverse `2951,3208,0 -> 2942,3243,0`, fork isolate `2942,3236,0 -> 2951,3208,0`, and east pressure `2946,3239,0 -> 2951,3208,0`. Next coding work after that is the separate tree/tree-stump object-profile proof pass.

### 2026-08-14 01:25 UTC - Falador southeast creative controls patched
- Myth's creative-control pass showed fork isolate already clean, but reverse `2951,3208,0 -> 2942,3243,0` still diverged and east pressure `2946,3239,0 -> 2951,3208,0` still tried to cut through the tree-line pocket.
- The reverse benchmark row included one manual east/back wobble at the start while `Log Benchmark Movement` was left on. That wobble was treated as staging noise, not a route to force. The patched reverse route uses the clean walked sequence after returning to `(2951,3208,0)`.
- The east-pressure row was a multi-waypoint loop because logging stayed enabled while walking to the start tile; the patched target-aware route uses the `2946,3239,0 -> 2951,3208,0` segment from that row.
- Next live validation should rerun only reverse and east pressure with `Log Benchmark Movement` OFF while staging to the start tile and ON only for the measured route.

### 2026-08-14 01:35 UTC - Falador creative controls live-validated
- Myth reran reverse `2951,3208,0 -> 2942,3243,0` after D-0180. The completed benchmark row was exact: 39 expected points, 39 actual points, `full=true`, `lenDelta=0`, `maxDev=0`, `turnDelta=0`, and `divergence={none}`.
- Myth reran east pressure `2946,3239,0 -> 2951,3208,0` after D-0180. The completed benchmark row was exact: 35 expected points, 35 actual points, `full=true`, `lenDelta=0`, `maxDev=0`, `turnDelta=0`, and `divergence={none}`.
- The shadow diagnostics still prove the overrides matter: override-free/shadow routes diverged on both reverse and east pressure. That means D-0180 fixed the visible route for these measured routes, but it is not evidence that the general route-ranker problem is solved everywhere.
- Next work should be a broader route-shape validation sweep across longer routes and different areas before adding more Falador-specific route windows or shipping tree/tree-stump object profiles.

### 2026-08-14 02:18 UTC - Broader route-shape validation staged
- D-0182 stages six Batch A live routes in `02_NEXT_WORK.md`: Varrock city to Grand Exchange, Lumbridge to Draynor, Draynor bank to Draynor Manor, Lumbridge east side to Al Kharid bank, Falador square to Barbarian Village, and Varrock east bank to the Sawmill.
- The goal is system classification, not route-by-route tuning. Repeated legal equal-length route-shape misses point at the ranker; illegal/static-map disagreements or object-edge misses point at collision/object-profile work. Exact matches and benign same-time permutations do not justify route changes.

## 2026-08-09 Basic Transportation and Travel ETA

The Basic Transportation checkboxes are functional. Each one enables a transport family for the
router; `BASELINE` is always on and cannot be disabled.

Two gates decide whether an edge is usable:

1. **Policy** - the family's checkbox is enabled (`DrewsHelperTransportPolicy`).
2. **Capability** - the account currently meets the edge's skill and item requirements
   (`DrewsHelperPlayerCapability`).

Quests, discoveries and destination unlocks are **not** verified. Those are the user's
attestation via the checkbox. Skill levels are **real, not boosted**.

### Files

| File | Role |
|---|---|
| `DrewsHelperTransportCategory` | The nine families |
| `DrewsHelperTransportPolicy` | Which families are enabled, plus a cache signature |
| `DrewsHelperPlayerCapability` | Skills, items, and every energy-model input |
| `DrewsHelperItemVariation` | 17 symbolic item names to RuneLite `ItemID` arrays |
| `DrewsHelperTravelEstimate` | Run-energy simulation and ETA |
| `tools/generate-drewshelper-transports.ps1` | Regenerates the transport resource |

### Transport resource

`src/main/resources/drewshelper-transports.tsv`, 10 columns, 7,331 edges.

```
BASELINE 5,800   AGILITY_SHORTCUT 557   HOT_AIR_BALLOON 269   QUETZAL 182
GNOME_GLIDER 103   CANOE 45   MAGIC_MUSHTREE 29   GRAPPLE_SHORTCUT 15   WILDERNESS 331
```

Regenerate with the tool in `tools/`; see `tools/README.md` for the pitfalls, especially the
boarding/landing cross product.

### Travel estimate

The overlay shows total ETA, cumulative arrival time per waypoint, and which transport families
the route uses. Recomputed every tick, so it counts down as you move and drops if you stop and
let energy recover.

Energy is simulated over the finished path, not priced into A*. Per tick:

- running drains `floor(floor(60 + 67 * clamp(weight,0,64)/64) * (1 - agility/300))`
- stamina multiplies drain by 0.3, **or** the ring of endurance by 0.85 - these do not stack
- any non-running tick restores `floor(agility/10) + 15`, raised by the graceful percentage
- graceful is per-piece: 3/4/4/3/3/3 for 20, complete set adds 10 more, 30 maximum
- run off means walking, unless the auto re-enable threshold is set, in which case running
  resumes once energy climbs back over it

Validated in game at 343 tiles: predicted 2:25, arrived 2:25.

### Verifying the ETA

Enable the route benchmark. `DREW_ROUTE_BENCH` now logs an `eta predicted=...` line at route
start with every model input, and an `eta result predicted=... actual=... delta=...` line on
arrival. The clock starts on first movement.

### Known gaps

- `VarbitID.RUNENERGY_AUTOENABLE` is read as a percentage but its units are unconfirmed. Safe
  for the common threshold of 1; confirm from a benchmark log before trusting other values.
- Ring of endurance charge count is not readable, so a ring under the 500-charge threshold
  still reads as active.
- Stamina potion **duration** is not forecast, only its current on/off state. A stamina
  expiring mid-route will make the ETA optimistic. The varbit unit is unverified.
- Canoes and grapple shortcuts are unverified in game - Myth has not unlocked them yet.

## 2026-08-09 Teleport Routing Plan

Planned, not yet built as of this note. Current jar remains the Drew-owned waypoint route system with physical transports, ETA/HUD improvements, transport highlighting, and Wilderness avoidance. Magic-tab teleports and home teleports are the next project.

Known facts from recon:
- Upstream teleport metadata already carries cooldowns in requirement syntax as `@`, for example `892@30`.
- `@` means cooldown in minutes. The stored var value is an epoch-minute timestamp, not a countdown: usable when `(nowMinutes - storedMinutes) > cooldownMinutes`.
- The home-teleport cooldown and animation-state requirements are in the VarPlayers column, not Varbits.
- Cooldown semantics are Myth's ruling: a cooldown-active teleport is treated as locked. No wait time is added to ETA.
- Unknown cooldown value must be treated as locked. The existing requirement grammar treats unknown vars as satisfied, which is correct for typo-prone quests but wrong for cooldowns.
- The spell/home TSVs have no origin column. They are destination-only teleports from the player's current tile.
- Destination-only rows currently fall into the generator's `$destOnly` bucket and are silently dropped unless a file also has origin rows to cross-product with.
- Lumbridge Home Teleport appears as multiple rows that differ by varplayer animation state and duration. Dedup must include requirement fields for originless teleport rows so those variants do not collapse into one broken row.

Staged implementation plan:

1. **Home teleports first.**
   - Add `teleportation_spells_home.tsv`.
   - Emit destination-only rows immediately with sentinel source `-1,-1,0` (`ANYWHERE`).
   - Offer `ANYWHERE` transports only at the start of each route leg.
   - Add `@` cooldown support with unknown cooldown value -> locked.
   - Preserve Lumbridge variants by including varbit/varplayer fields in the originless dedup key.
   - Fix all three transport lookup paths to consult the same originless helper: edge offering, edge legality, and travel-estimate/action labeling.

2. **Magic-tab spell teleports from carried supplies.**
   - Add `teleportation_spells.tsv` only after the rune model is wired.
   - Gate by real Magic level, spellbook/unlock var requirements, and carried cast supplies.
   - Expand symbolic rune names at generator time into existing item-requirement grammar, including combination runes and elemental staves where the upstream table proves the substitutions.
   - Count inventory and equipped gear. Staffs are normally equipped, not carried.
   - Add rune-pouch support separately because pouch contents live in vars, not ordinary item counts.
   - Do not count bank contents as castable from anywhere.

3. **Bank-aware teleport routing.**
   - Only consider bank supplies when RuneLite has a known bank cache because the player opened the bank this session/profile.
   - If bank contents are unknown, do not invent a bank route.
   - Add bank locations as real graph nodes/edges, using upstream bank tile data first. Ask Myth for missing tile data only if upstream is incomplete.
   - Search state becomes `(tile, bankedYet)`, not just `tile`.
   - A bank edge costs honest withdraw time. Highlight the needed rune/staff/item in the bank UI to make that cost lower and more predictable.
   - A bank route is chosen only if the normal shortest-route search proves it is faster than walking or another transport. No separate "bank if nearby" rule.

Operational rule after bank support:

```text
If carried supplies can cast it -> use teleport normally.
If carried supplies cannot cast it but known bank has supplies -> consider route-to-bank + withdraw + teleport.
If bank is unknown or lacks supplies -> treat teleport as locked.
If cooldown is active -> treat teleport as locked.
```

Later phases:
- Minigame teleports: each destination is already its own row, not a submenu tree. Add after the originless home/spell machinery works.
- Items, boxes, portals, POH portals, tablets, scrolls, capes: bulk ingest after the originless/cooldown/account gates are proven.
- Remove or repurpose the dead Teleport Options / Other Transportation placeholder toggles only after their families are innate in routing.
