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
- Added Drew-owned baseline transport routing from selected maintained Shortest Path transport TSVs: click objects/gates/gangplanks, ordinary ships/ferries/boats, charter ships, magic carpets, and minecarts are built into `drewshelper-transports.tsv`; Wilderness levers and Wilderness obelisks are gated behind one `Use: Wilderness Transports` checkbox under `Other Transportation`.
- Removed the `Route Diagnostics` config item, updated config tooltips away from the old diagnostics/vendored-Shortest-Path wording, changed overlay distance wording to `Route Steps`, and kept the old `src/main/java/shortestpath/**` package absent.
- Added dotted transport-jump connectors to the Drew-owned route overlays: non-adjacent/cross-plane route segments now draw as dotted world-map lines, with matching minimap lines when both endpoints are locally projectable.
- Increased zoomed-out world-map route readability: normal route tiles now keep at least a 4px screen footprint on the world map, matching the fixed-screen-size idea already used by waypoint icons and dotted transport connectors.
- Added BFS as a Drew-owned route solver mode beside the existing A* solver, plus opt-in `Benchmark Movement` logging. When enabled, route calculation solves both strategies, records solve time/expanded nodes, and logs `DREW_ROUTE_BENCH` first-step, 5-tick, 10-tick, full-sequence, path-length, lateral-deviation, and turn-count comparisons against actual player movement.
- Added coordinate-level `DREW_ROUTE_BENCH` trace fields for route start, target, first 10 primary/alternate predicted path tiles, and first 10 actual movement tiles so live misses can be separated into start/target/click-tile alignment versus solver movement-order issues.
- Extended `DREW_ROUTE_BENCH` with first-divergence diagnostics: `primaryDivergence`, `alternateDivergence`, predicted/actual path windows around the fork, and legal candidate moves from the fork tile with predicted and actual choices marked. This gives live evidence for movement-order tie tuning instead of guessing from the overlay shape.
- Tuned walking-route tie selection from Myth's three-route benchmark: when one axis is longer, cardinal moves toward either target axis are considered before the diagonal move, A* keeps checking same-shortest-cost candidates on short walking segments before publishing a path, and the old fewest-turn preference no longer wins equal-distance path shape.
- Added a final client-style shortest-path ranking pass for short Drew route segments: after A*/BFS finds the segment length, the engine computes reverse distances from the target and reconstructs an exact-shortest path by the same legal candidate order used in `DREW_ROUTE_BENCH`. Also added reverse transport-edge lookup for that ranking pass. Follow-up probing showed Myth's remaining Path 1 / Path 3 forks are likely collision-map/live-client disagreements, not solver search-order disagreements.
- Removed the temporary BFS route solver mode and `Route Solver` config dropdown after Myth's live tests showed BFS was slower and did not match client movement better. `Benchmark Movement` remains as a single-route overlay-vs-client trace for `DREW_ROUTE_BENCH`, and next route work should validate collision-map/live-client edge disagreements before adding local overrides.
- Added collision-edge validator logging to `DREW_ROUTE_BENCH`: when actual movement diverges from the displayed route, Drew now logs `edgeValidation` with observed-edge legality, graph continuation distance/delta, whether the continuation is longer, session repeat count, and an `overrideCandidate` flag. This is diagnostic only; no collision override is applied yet.
- Added target-aware local walking overrides for Myth's repeated Path 1 and Path 3 live-client branches. The overrides participate in Drew's existing legal-step ordering and reverse-distance final path ranking, so they affect only the confirmed target/fork windows and do not globally replace Runemoro collision data.

- Added the next Path 1 target-aware tail preference after Myth's exact `(2932,3214,0)` run: the route now prefers `(2935,3218,0) -> (2934,3217,0)` for that target, while keeping Path 3's confirmed fixed override unchanged.

### 2026-08-07 - D-0046 Path 1 final-tail override
- Added the next observed Path 1 final-tail override for target (2932,3214,0).
- Route tests now assert the full final live-observed tail: (2934,3217,0) -> (2933,3216,0) -> (2932,3215,0) -> (2932,3214,0).
- Verified focused route-engine tests and full Gradle build after upload to MythPC.

### 2026-08-07 - D-0047 benchmark pending-start and shape diagnostics
- Added pending-start benchmark capture so off-route pre-start/return movement is ignored instead of poisoning the next sample as `idx=0`.
- Added `shape={...}` diagnostics to `DREW_ROUTE_BENCH` reports for completed target samples.
- Added focused tests for stale-start discard, delayed capture start, and route-shape diagnostic formatting.


## 2026-08-07 - D-0048 route overlay continuity after event jumps
- After Myth's random-event test, preserve the previous route path while a fresh route recalculates so waypoint markers do not remain without connector tiles during transient recalculation.
- Added snapshot coverage for calculating snapshots carrying a previous path.

## 2026-08-07 - D-0049 segment-aware chained benchmark diagnostics
- Checked Myth's post-D-0048 route collection. Point 3 completed clean, but Point 1 and Point 2 were not separate completed benchmark samples because all three control waypoints were active at once.
- The five-waypoint random chain completed and exposed a benchmark diagnostic bug: `edgeValidation` and `shape` were judging an early first-leg fork against the final waypoint target.
- Updated `DREW_ROUTE_BENCH` capture so multi-waypoint routes map the first divergence to the active segment waypoint before logging candidates, edge validation, and shape diagnostics.
- Added focused capture coverage for a two-waypoint chain where a first-segment divergence must log the first waypoint as `target` and the route endpoint as `finalTarget`.

## 2026-08-07 - D-0050 no-override shadow route diagnostics
- Added `DrewsHelperWalkingRouteEngine.solveWithoutLocalWalkingOverrides(...)` so benchmark diagnostics can compute the current route without the Path 1 / Path 3 target-aware local overrides.
- Added `shadow={...}` to completed `DREW_ROUTE_BENCH` reports. It compares the no-override shadow route against actual movement and reports whether the local overrides changed the visible route plus which route fit live movement better.
- Added formatter and route-engine tests for the shadow diagnostic, including the loaded collision-resource Path 1 / Path 3 baseline branches.
- This remains diagnostic-only; the visible route still uses the active local overrides.

## 2026-08-07 - D-0051 shape-shadow route diagnostics
- Checked Myth's D-0050 live run. Point 1, Point 2, and the corrected Point 3 control all completed cleanly; Path 1 and Path 3 still reported `overridesMatter=true winner=visible`, while Point 2 was a no-override tie.
- The five-waypoint random chain produced one legal equal-length segment fork from `(2996,3288,0)` where the displayed route chose `(2997,3287,0)` and the client chose `(2995,3287,0)`. Segment shape scoring favored the actual client path.
- Added `DrewsHelperWalkingRouteEngine.solveWithShapeRankingWithoutLocalWalkingOverrides(...)` and a completed-report `shapeShadow={...}` diagnostic so future samples can compare the visible route, the no-override baseline, and a no-override segment-shape-ranked route.
- Kept `shapeShadow` diagnostic-only. The first unit probe showed full-route line-shape ranking can overcorrect before the observed fork, so it is not ready for visible route promotion.


## 2026-08-08 - D-0052 merge-back divergence diagnostics
- Checked Myth's post-D-0051 five-waypoint ordered chain. The same-square double-click did not restart capture, but the completed chain produced a local segment divergence where the client walked `(2977,3252,0)` instead of displayed `(2977,3251,0)` and then merged back onto the displayed route shortly afterward.
- Added `mergeBack={...}` to `DREW_ROUTE_BENCH` divergence formatting so local step-order permutations can be separated from routes that truly stay off the displayed path.
- Added benchmark formatter coverage for a divergent path that rejoins the expected path with `stepDelta=0`.
- This remains diagnostic-only; no visible route ranking, local walking override, waypoint, or capture behavior changed.

## 2026-08-08 - D-0053 merge-aware diagnostic scoring
- Checked Myth's post-D-0052 rerun and confirmed the same fork rejoined on schedule with `mergeBack stepDelta=0`.
- Added divergence classification fields: `classification=<...>` and `benign=<...>`, with `sameTimePermutation` marking local step-order swaps that merge back at the same index.
- Updated `shadow` and `shapeShadow` winner scoring to use merge-aware route-fit penalties, so same-time permutations are not treated like hard no-merge drift.
- Added benchmark formatter coverage for merge-aware diagnostic winner behavior.
- This remains diagnostic-only; visible route selection did not change.

## 2026-08-08 - D-0054 post-merge divergence summary
- Checked Myth's D-0053 five-waypoint chain and confirmed the waypoint 2 -> 3 mismatch was the same benign merge-back class, but the completed route still reported `full=false lenDelta=-1`.
- Added `additionalDivergences={...}` inside `divergence={...}` so the benchmark report can expose the next mismatch or length-only difference after a benign merge-back.
- Added benchmark formatter coverage for a path that merges back cleanly and then ends with a length difference.
- This remains diagnostic-only; visible route selection, local overrides, `shapeShadow`, waypoint behavior, and capture lifecycle did not change.

### D-0055 - Additional divergence detail for post-merge forks
- Date: 2026-08-08
- Added `additionalDivergenceDetail={...}` to completed benchmark reports so a later post-merge mismatch gets segment-aware candidate and edge-validation diagnostics.
- The visible route, local Path 1 / Path 3 overrides, `shadow`, and `shapeShadow` behavior remain unchanged.
- Focused benchmark/capture tests and full package build passed after the change.

### D-0056 - Fork-rank telemetry for post-merge candidate selection
- Date: 2026-08-08
- Checked Myth's repeated D-0055 five-waypoint chain. The later `idx=52` fork repeated, and the actual client edge was legal with `delta=0` and `longer=false`.
- Added `forkRank={...}` inside `additionalDivergenceDetail={...}` for completed benchmark reports. It validates and ranks all legal neighboring candidates at the later fork, marking predicted and actual ranks.
- This is telemetry only. Visible route selection, local Path 1 / Path 3 overrides, `shadow`, `shapeShadow`, waypoint behavior, and capture lifecycle remain unchanged.

### D-0057 - Route diagnostics closeout
- Date: 2026-08-08
- Checked Myth's final Point 1 / Point 2 / Point 3 control rerun after the D-0056 random-chain samples. All three visible routes completed with `full=true`, `lenDelta=0`, `maxDev=0`, and `divergence={none}`.
- The old same-chain fork where `actualRank=1` was promising but did not generalize across new random chains; usable random-chain misses were mostly `sameTimePermutation benign=true`, and the contaminated short-click run is not promotion evidence.
- No code behavior changed for this closeout. Updated the guide state so future work starts from "route behavior unchanged, diagnostics available" instead of another required rerun.

### D-0058 - Basic Transportation checkboxes now gate the route graph
- Date: 2026-08-09
- The Basic Transportation checkboxes were cosmetic. They now select which transport families the router may use, via a new `DrewsHelperTransportPolicy` (immutable enabled-family set plus a stable `signature()` for cache keys).
- Added `DrewsHelperTransportCategory` with nine families: `BASELINE`, `WILDERNESS`, `AGILITY_SHORTCUT`, `GRAPPLE_SHORTCUT`, `CANOE`, `GNOME_GLIDER`, `HOT_AIR_BALLOON`, `MAGIC_MUSHTREE`, `QUETZAL`. `BASELINE` is always enabled and cannot be switched off.
- Added `DrewsHelperPlayerCapability`, an immutable snapshot of real (unboosted) skill levels and carried items, built on the client thread and safe to read from the solver thread.
- Edges now pass two gates: the family is enabled by the policy, and the account currently meets the edge's skill and item requirements.

### D-0059 - Transport resource regenerated with requirement data
- Date: 2026-08-09
- `drewshelper-transports.tsv` went from 4 columns to 10: `category, source, destination, label, duration, skills, quests, items, varbits, varplayers`.
- Rebuilt from upstream: 5,683 edges to 7,331. Every pre-existing edge verified still present, zero regressions.
- New families recovered: 557 agility shortcuts, 269 balloon, 182 quetzal, 103 glider, 45 canoe, 29 mushtree, 15 grapple. `WILDERNESS` 325 to 331, `BASELINE` 5,358 to 5,800 (the extra baseline rows carry requirements the old format had to discard).
- Added `DrewsHelperItemVariation`, mapping 17 symbolic item names to RuneLite `ItemID` arrays. The other 10 symbols in the data are already raw item ids.
- The generator now lives permanently at `tools/generate-drewshelper-transports.ps1` with `tools/README.md`. Verified it reproduces the shipped resource byte-for-byte from its new location.

### D-0060 - Travel time estimate on the HUD
- Date: 2026-08-09
- Added `DrewsHelperTravelEstimate`: a tick-by-tick run-energy simulation over the finished path, producing total ETA, per-waypoint leg times, and which transport families the route uses.
- Energy cannot live inside A*, because what a tile costs depends on the energy you have when you reach it, which depends on the whole path taken to get there. The search keeps fixed costs; the estimate walks the finished path forward.
- Fixed a real routing bug found on the way: transports were priced as one step regardless of duration, and the reverse-distance ranking pass used uniform-cost BFS, which is wrong once edges have different weights. Transport steps now cost `2 x durationTicks` and the reverse pass is Dijkstra with relaxation.
- Validated in game: a 343-tile route predicted 2:25 and arrived at 2:25 on a stopwatch.

### D-0061 - Toggle latency fixes
- Date: 2026-08-09
- The transport resource was being re-read and re-parsed on every checkbox toggle, inventory change, or coin pickup, because account state is part of the engine cache key. It is now parsed once into an immutable master list and filtered in memory per policy and capability.
- `onStatChanged` narrowed to 13 route-relevant skills. A Cooking level cannot open or close a transport, so it no longer costs a rebuild.
- The overlay keeps the last known Route Steps and ETA on screen, greyed, while a new solve is in flight. They previously vanished, which made a slow solve look like the plugin had died.

### D-0062 - Run-energy model reads real gear and run state
- Date: 2026-08-09
- Added live reads for graceful, stamina, ring of endurance, and the run toggle.
- Graceful is per-piece, not all-or-nothing: hood 3, top 4, legs 4, gloves 3, boots 3, cape 3 for 20, and the complete set adds 10 more for 30. Matched on item name containing "graceful" per slot, because 147 item ids across colour variants makes id-matching unmaintainable.
- **Bug fixed:** stamina and the ring of endurance were being applied multiplicatively (x0.3 then x0.85). The wiki is explicit that the ring's passive does not stack with the stamina effect, so the ring is now only applied when stamina is inactive.
- Added `autoRunThresholdPercent` from `VarbitID.RUNENERGY_AUTOENABLE`. Run is now a live state inside the simulation rather than a constant, so an account with the re-enable threshold set resumes running once energy climbs back over it instead of forecasting a walk that never happens.

### D-0063 - Predicted-versus-actual ETA logging
- Date: 2026-08-09
- Hung ETA verification off the existing benchmark movement lifecycle rather than building a parallel system. Reuses the `routeBenchmarkEnabled` config toggle and the `DREW_ROUTE_BENCH` log prefix.
- On benchmark start, logs the forecast plus every energy-model input and the two derived rates via `DrewsHelperTravelEstimate.describeEnergyModel(...)`. On arrival, logs predicted versus actual ticks with the delta and percentage error.
- The clock starts on the first tick the player actually moves, so time spent standing at the start does not count against the forecast.
- The forecast is snapshotted at start. `refreshTravelEstimate` recomputes from the player's current position every tick, so by arrival it reads zero and would be useless as a comparison baseline.

### D-0102 / D-0103 - Teleport routing handoff plan
- Date: 2026-08-09
- Docs-only update at Myth's request before pausing for tomorrow.
- Added a current-state teleport-routing plan covering home teleports, magic-tab spell teleports from carried supplies, bank-aware teleports, minigames, bulk teleport families, cooldown handling, and retirement of dead teleport placeholder toggles.
- Updated `02_NEXT_WORK.md` so tomorrow's first active handoff is home teleports, not the old reset-era route notes.
- Appended D-0102 to supersede stale D-0101 Wilderness wording. The shipped rule is the derived bounded Wilderness box with start/waypoint escape hatches.
- Appended D-0103 to pin the teleport plan: cooldowns are locked state, destination-only rows become `ANYWHERE` edges, Lumbridge home-teleport variants must not dedup away, carried supplies precede bank routing, and bank contents become useful only through a real bank graph step.
- No code changed and no jar was rebuilt in this pass.

### D-0104 - Home teleports are live originless route edges
- Date: 2026-08-09
- Ingested upstream `teleportation_spells_home.tsv` into `drewshelper-transports.tsv`; generated resource now carries 16 `BASELINE` home-teleport rows with source `-1,-1,0`.
- Added originless-edge support to the Drew-owned route graph/engine: home teleports are offered only at waypoint leg starts, including fresh offers for later legs in a multi-waypoint route.
- Added cooldown support for `@` var terms. Active cooldowns lock the edge, unknown cooldown vars lock the edge, and ordinary unknown quest/var requirements remain permissive.
- `DrewsHelperPlugin` stamps capability snapshots with the current epoch minute and marks routes dirty once per minute while waypoints exist, so an expired cooldown can become routable without a manual config change.
- `DrewsHelperTravelEstimate` now treats originless jumps as real `Actions` rows with upstream label/duration while adjacent walking onto the same destination tile remains walking.
- Added focused tests for cooldown terms, originless leg-start routing, home resource rows, cooldown graph filtering, and ETA/action labeling. Full Gradle test/build passed.

### D-0105 - Regeneration proof for the home-teleport slice, and a CRLF fix

- Date: 2026-08-09
- Verification pass over the generator behaviour, plus one corrected defect. No routing or engine logic changed here.
- Proved row survival with a controlled A/B on the same generator: run A used the full upstream dir, run B withheld `teleportation_spells_home.tsv`. That file was the only variable, and `tools/transport-overrides.tsv` merged identically into both runs because the generator resolves it from `$PSCommandPath`.
- Result: 12,404 rows with the home file, 12,388 without. Full-row set diff across every non-originless row: 0 lost, 0 changed. Per-category deltas were 0 everywhere except `BASELINE` at +16.
- Run B produced 0 originless rows, so all 16 `-1,-1,0` rows are attributable solely to the home file, and no other transport family currently reaches the destination-only fallback branch.
- Lumbridge kept 4 home-teleport variants, all 4 with distinct duration/varbit/varplayer keys.
- The requirement-aware dedup widening is gated behind `$Source -eq $ORIGINLESS_SOURCE`, so every non-originless row still keys on `category|source|destination|label` exactly as before.
- Against the last commit: 1,505 rows differ, all by label only - the earlier hub fix appending interactable ids, e.g. `8: Mount Quidamortem` became `8: Mount Quidamortem 28835`. Rows whose `category|source|destination` edge has no match in the live resource: 0. All 4 override rows still present.
- Fixed a defect from the previous pass: the generated resource had been CRLF-normalized after upload. The generator writes LF and the committed blob is LF, so the file was replaced with the generator's exact output - sha256 `B58A2006...`, 955,471 bytes, LF, no BOM, 12,404 data rows.
- Rebuilt after the fix: `clean test build` BUILD SUCCESSFUL, 152 tests, 0 failures, 0 errors, 0 skips, jar 1,042,701 bytes.
- Open item for Myth: `tools/transport-overrides.tsv` is still untracked in git. The generated resource depends on it, so a clone without that file regenerates and silently drops the 4 verified override edges.

### Parked-items list opened

- Date: 2026-08-09
- Myth asked that suggested changes and fixes raised mid-build be logged rather than actioned, so the numbered build backlog is not interrupted.
- Added a `Parked Items` section to `02_NEXT_WORK.md`, placed inside the active-handoff region directly above the history divider so it stays findable rather than being buried under the older route-diagnostic sections.
- Seeded with 6 confirmed items (each with file/line evidence) and 1 carried-over item marked unconfirmed: untracked `transport-overrides.tsv`, the category-wide destination-only fallback, the unpromoted A/B regen harness, the fixed-column TSV whitespace artifact, duration-weighted transports bypassing A* tie refinement, the stale `Not yet fixed` wording in D-0101, and the un-banked route-speed baseline.
- Standing convention from here: new side-findings get appended to that section as they come up, and struck through when cleared. No code changed in this pass.

### D-0106 - Verification pass over cooldown gating, originless routing and Wilderness avoidance

- Date: 2026-08-09
- Myth asked for plan items 3-6 to be built. Audit found all four already shipped in the home-teleport slice, so this pass verified each sub-bullet against live code and tests rather than rewriting working behaviour. No source file changed.
- Cooldown gating: `meetsVarTerm` (`DrewsHelperPlayerCapability.java` 261-298) parses `@` alongside `=`, `>`, `<` and `&`. Line 295 `currentEpochMinute - actual > operand` locks an active cooldown; line 281 `return operator != '@'` is the single line that locks an unknown cooldown var while keeping ordinary unknown vars permissive, which is the D-0103 grammar inversion.
- Originless routing: leg-start gate at engine line 472 (`node.previous == null && node.point.equals(context.segmentStart)`); per-leg re-solve at 287/294/320 gives each waypoint leg a fresh offer; forward legal-step generation at 917-921; edge-legality path at 1207-1209; reverse relaxation back to the real segment start at 782-786; ETA at `DrewsHelperTravelEstimate.java` 360.
- Wilderness: `isWildernessEntryToAvoid` (engine 1013-1018) implements D-0102 exactly - avoid only when the destination is inside and neither the origin nor the segment target is. `originlessTransportAllowed` (1021-1027) additionally requires `!isInWilderness(from)`, which is the under-offer-from-Wilderness-starts rule.
- Tests confirmed present and covering the named cases: `homeTeleportRowsAreOriginlessAndVariantsStayDistinct`, `homeTeleportCooldownFiltersTheLoadedGraph` (expired/active/unknown at graph level), `cooldownVarTermsTreatUnknownAsLockedAndExpiredAsUsable` (expired/boundary/active/unknown/ordinary-var at capability level), `originlessTransportIsOfferedAtRouteLegStart`, `originlessTransportIsOfferedAgainAtEachWaypointLegStart`, `originlessTransportsCostTheirDurationAndAreReported`, `adjacentWalkOntoOriginlessDestinationDoesNotLookLikeATeleport`, plus five in `DrewsHelperWildernessAvoidanceTest`.
- Investigated and dismissed: the ETA test asserting `getTransportTicks() == 0` for a 23-tick teleport is correct, not an attribution bug. That map is arrival time, documented at `DrewsHelperTravelEstimate.java` 79-84 and consumed as `arrivals` at `DrewsHelperOverlay.java` 162, so a teleport cast at route start correctly renders `0:00` in the Actions column while the 23 ticks are paid into `getTotalTicks()` at line 253.
- Ran `.\gradlew.bat --no-daemon --console=plain clean test build`: BUILD SUCCESSFUL, 152 tests, 0 failures/errors/skips, jar 1,042,701 bytes.
- One judgment call parked rather than changed: the strictly-greater cooldown boundary. See `02_NEXT_WORK.md` Parked Items item 7.

### D-0107 - Pre-test readiness check

- Date: 2026-08-09
- Read-only pass before Myth's first live test of the home-teleport slice. No source file changed.
- Confirmed the live-client link the unit tests cannot reach: `DrewsHelperPlugin` 909-915 snapshots var ids from `DrewsHelperTransportGraph.requiredVarbitIds()` / `requiredVarPlayerIds()` rather than a hardcoded whitelist, so varplayer 892 is picked up automatically. `varIds()` at 152-182 consumes leading digits and stops at the first non-digit, so `892@30` yields 892 without `@` appearing in any operator list.
- Confirmed the artefact is current: jar built 14:27:49 against newest source 14:09:10. Launch is `gradlew.bat run` per `00_START_HERE.md`; there is no separate deploy step.
- Added a live test checklist under the completed home-teleport slice, including the two opposite failure signatures that distinguish a varp-semantics problem from a varp-not-read problem.
- Parked one comment-only nit as item 9: the `varIds()` comment omits the `@` operator.

### D-0108 - Transport tile markers are labelled, and the home-teleport slice passed live test

- Date: 2026-08-09
- Myth live-tested the home-teleport slice: offered on a long route, correctly withheld straight after casting, and offered again by itself about thirty minutes later. That third result closes the one thing static analysis could not settle, because an unread varplayer 892 would have locked the edge and shown nothing at all.
- Feature requested during that test: the cyan transport marker is a bare square with nothing saying what it means. For an originless teleport there is no NPC and no scene object to outline, so `drawTransportEndpoints` falls through to the tile fallback and the player gets an unexplained highlight next to them.
- Added `DrewsHelperTravelEstimate.transportLabel(graph, from, to)` as a public sibling of `targetId(...)`. It reuses the existing `findTransport` lookup and `displayLabel` cleanup rather than duplicating either, so a marker in the world and its `Actions` row can never disagree about a hop's name. `displayLabel` is package-private and the overlay lives in `...routing.ui`, which is why the public accessor was needed.
- `DrewsHelperRouteTileOverlay.drawTransportTile` now takes the label and renders it in white above the tile via `Perspective.getCanvasTextLocation` plus `OverlayUtil.renderTextLocation`, at `TILE_LABEL_HEIGHT` 40. Both markers for a hop get it - the origin square you cast from and the landing square you arrive on. Interactable outlines (NPC/object) are deliberately left unlabelled; the request was specifically about the tile.
- Added `transportLabelNamesTheHopForTheWorldMarker` covering both the originless hop and a plain walking step, which must stay unlabelled.
- Note for future edits: these two source files differ in line endings - `DrewsHelperTravelEstimate.java` is CRLF, `DrewsHelperRouteTileOverlay.java` is LF. The patch detected each file's own newline rather than assuming.
- Ran `.\gradlew.bat --no-daemon --console=plain clean test build`: BUILD SUCCESSFUL, 153 tests, 0 failures/errors/skips, jar 1,043,075 bytes.
- Not yet seen in game: the label itself needs a relaunch to confirm placement and readability.

### D-0109 - Wilderness teleport limit is real data we are discarding

- Date: 2026-08-09
- Myth asked why a home teleport is refused from the Wilderness. Checked rather than answered from memory, and he was right to ask.
- Upstream `teleportation_spells_home.tsv` carries a `Wilderness level` column and every Lumbridge home-teleport row sets it to `20`. Upstream also models the thresholds properly in `AbstractNodeKind.fromWildernessLevel` (buckets at >0, >20, >30) and stores it per transport as `Transport.maxWildernessLevel`, defaulting to -1 for no limit.
- So the game permits a home teleport anywhere in Wilderness levels 1-20 and blocks it above 20. Our engine refuses the whole box, because the generator never carries the column through: the generated resource is 10 columns wide with no wilderness level, leaving `originlessTransportAllowed` nothing to test but `!isInWilderness(from)`.
- That guard was the correct conservative choice while the data was absent - under-offering beats suggesting a spell the game rejects - but it is now provably over-restrictive. Logged as Parked Item 10 with the fix path.
- Second finding while reasoning about his two-waypoint test: cooldowns are filtered once at graph-build time, and the engine keeps no consumed-transport state between legs, so a route with two long legs could tell the player to cast the same 30-minute teleport twice. Logged as Parked Item 11.
- Live results recorded: the tile label passes, and the two-waypoint route confirms per-leg transport selection (home teleport on leg 1, spirit tree on leg 2) without isolating an originless offer on a later leg.
- No code changed in this pass.

### D-0110 - Wilderness teleport cap carried through and enforced

- Date: 2026-08-09
- Closes Parked Item 10. Myth confirmed the intent: never route INTO the Wilderness unless the toggle is on, but a player already standing in it must be able to teleport out whenever the game actually permits it.
- Those are two different questions and the code now treats them that way. Entering stays owned by `isWildernessEntryToAvoid`, which every home teleport passes anyway because all four spellbook destinations sit outside the box. Leaving is now gated by the transport's own cap instead of the blanket `!isInWilderness(from)` placeholder.
- Generator carries upstream's `Wilderness level` column through as an 11th field. Absent or unparseable becomes -1, matching upstream's own "no cap" sentinel rather than inventing a second convention. Split rows take `Math.max` of both ends, mirroring `Transport.maxWildernessLevel`, on the principle that an edge is only as usable as its more restricted half. The cap also joins the originless dedup key so two rows differing only by cap cannot silently collapse.
- Regenerated: still 12,404 rows, now 11 columns. 16 rows carry cap 20 (every home teleport), 12,388 carry -1. Row count unchanged proves the widened key dropped nothing.
- `DrewsHelperTransportEdge` gained `maxWildernessLevel` plus `NO_WILDERNESS_LIMIT`. The existing 10-argument constructor now delegates with -1, so no existing call site or test needed touching.
- `DrewsHelperWalkingRouteEngine.wildernessLevelAt` copies upstream's overlapping-box model rather than a per-tile formula: 0 outside, 20 for levels 1-20, 30 for 21-30, 31 deeper, above ground and underground. Upstream's safe-zone carve-outs (Ferox Enclave, the Edgeville strip) are deliberately not modelled - they only separate level 0 from levels 1-20, and every cap in the data treats those identically.
- Tests added: the level bands including underground, a home teleport escaping levels 1-20, the same teleport refused at y 3800, and the shipped resource carrying cap 20 on every home row.
- Ran `.\gradlew.bat --no-daemon --console=plain clean test build`: BUILD SUCCESSFUL, 157 tests (up from 153), 0 failures/errors/skips, jar 1,044,201 bytes.
- Line endings differ across the five touched files (generator CRLF, edge/graph/engine CRLF, wilderness test LF); each patch detected the file's own style rather than assuming. The regenerated resource stays LF per D-0105 and was not normalized.

### D-0111 - Action rows show what a hop costs, not when you reach it

- Date: 2026-08-09
- Myth reported the Lumbridge Home Teleport showing `0:00` in the `Actions` column and expected roughly fifteen seconds of cast time. He was right about the display; the number was already correct data shown under the wrong meaning.
- `getTransportTicks()` is arrival tick - the moment you reach the thing you have to click. For a teleport cast at the start of a route that is genuinely 0, but it reads as "this hop is free" next to a total ETA of 0:15.
- Added `getTransportDurations()` and pointed the overlay at it. Arrival is kept and still tested; it just is not what the action rows show. The waypoint rows above already carry the cumulative clock, so durations here are non-redundant rather than a second copy of the same number.
- Durations are summed per label, not first-use, because a transport taken twice costs twice and the row already collapses to a single `x2`.
- Real values: Lumbridge Home Teleport is 23 ticks = `0:14`, and it varies with the teleport-animation varplayer 4560 - the four variants are 18, 21 and 23 ticks (`0:11`, `0:13`, `0:14`).
- Extended the existing originless ETA test rather than adding a parallel one: same fixture now asserts arrival 0 AND duration 23, which pins both meanings against each other.
- Ran `.\gradlew.bat --no-daemon --console=plain clean test build`: BUILD SUCCESSFUL, 157 tests, 0 failures/errors/skips, jar 1,044,295 bytes.
- Also logged Parked Item 12 covering missing transport rows (the Taverley-gate class of problem) with a detector proposal, and recorded that the Wilderness escape fix is shipped but still unverified in game because the teleport was on cooldown.

### D-0112 - Standing in the Wilderness no longer switches avoidance off

- Date: 2026-08-10
- Myth set a waypoint inside the Wilderness, then one at Lumbridge, with his home teleport on cooldown and Wilderness transports off. The route came back `Cross Wilderness Ditch` -> `Teleport Mage of Zamorak` -> `Enter Passage` -> `Operate Appendage`: it sent him deeper in to reach the Abyss and out the far side.
- Evidence from the shipped resource: all four rows are `BASELINE`. `Teleport Mage of Zamorak 2581` runs from 3106,3559 which is inside the Wilderness box, and `Operate Appendage 27027` lands at 3221,3219 (Lumbridge). The `WILDERNESS` category contains only 324 obelisk destinations plus 7 `Pull Lever`, so the toggle was never involved.
- Root cause: `isWildernessEntryToAvoid` carried two escape hatches. The second one, `!isInWilderness(from)`, meant that once the player was over the ditch the rule short-circuited and every Wilderness-side transport became legal, including ones heading further in.
- Fix: dropped that clause and the now-unused parameter; both call sites updated. Leaving is still free because a destination outside the box never trips the rule, and walking is untouched because the only two call sites are `addTransportStep` and `originlessTransportAllowed` - transport steps only, so the solver can always walk itself out.
- **Test discipline note, worth keeping.** The first test written for this used real geography (start inside the Wilderness, target Lumbridge, assert no Abyss detour) and it PASSED before the fix. From most Wilderness tiles A* has no reason to prefer the Abyss, so the test proved nothing; Myth's leg 2 only started ~30 tiles from the Mage of Zamorak, which is what made it attractive. That test was deleted rather than kept as decoration. The replacement models the shape instead - one transport deeper in, one transport out, on an open movement map - and was verified red-green: it FAILS against the pre-fix engine and passes after.
- Ran `.\gradlew.bat --no-daemon --console=plain clean test build`: BUILD SUCCESSFUL, 158 tests, 0 failures/errors/skips, jar 1,044,285 bytes.
- Also this pass: Wilderness teleport escape confirmed working live by Myth; the OSRS wiki Gate page checked and ruled out as a data source (disambiguation page, no coordinates or ids, no Falador west wall gate); Parked Item 13 opened on the mis-scoped Wilderness toggle.

### D-0113 - Chokepoint detector designed against the real API; Abyss question answered

- Date: 2026-08-10
- Myth asked for the list of places he needs to check for missing gates, offered mapgenie as a bulk source, and asked whether the Abyss should be folded into the Wilderness definition.
- **Abyss: no.** Checked the wiki rather than reasoning from the route shape. "While the run to the Mage of Zamorak is in a PVP area, the Abyss itself is not." It is Abyssal Space around y 4800, multicombat against NPCs, not Wilderness and not player-versus-player. The dangerous leg is the walk to the Mage of Zamorak at 3106,3559, which already sits inside the Wilderness box and is already refused by D-0112 - so that route is correctly blocked today without any change. Widening the box would mislabel a non-PvP area.
- **mapgenie: no.** Interactive commercial map, markers served from its own API rather than the page, access points hand-placed by editors. That is the same class of possibly-incomplete third-party data the whole exercise is meant to stop depending on, plus a licensing question we do not need.
- **Detector design is now concrete.** `DrewsHelperCollisionMap` exposes `canMoveNorth/South/East/West(x, y, plane)` and diagonals over 64x64 regions, which is sufficient for a whole-map offline scan. Three phases: enumerate tiles in regions that actually exist; keep adjacent pairs where movement is blocked but both tiles are otherwise open; BFS around each seam with a step cap and keep the ones with no short way round. Subtract seams already covered by a transport row and the remainder is a coordinate-bearing candidate list.
- Sequencing recorded explicitly in Parked Item 12: the scan runs first and produces the list. Myth has nothing to visit until it has, which is the answer to "give me all the areas I need to check".
- No code changed this pass.

### D-0114 - Wilderness avoidance now looks at where a transport is used FROM

- Date: 2026-08-10
- Myth retested D-0112 and reported the router STILL sending him to the Mage of Zamorak. It did, and D-0112 was never going to stop it.
- Root cause, measured from the shipped resource: `Teleport Mage of Zamorak 2581` runs from 3106,3559 to 3035,4852. The source is inside the Wilderness box (x 2944..3392, y 3522..3968); the destination, in Abyssal Space, is far outside it. The rule only ever tested the DESTINATION, so that edge was invisible to it - before D-0112 and after. D-0112 closed a real but different hole.
- Myth stated the missing rule himself: reaching the Mage of Zamorak requires being in the Wilderness, so the transport should need Wilderness access. Generalised, that is a source-side test, and it is the half the rule never had.
- New model, three cases rather than one. ENTERING (source outside, destination inside) is refused as before. LEAVING or MOVING ABOUT INSIDE (source inside) is refused UNLESS the move is a short physical crossing - 16 tiles or fewer on both axes. NEITHER END INSIDE is never refused. The escape hatch is unchanged: a segment target inside the Wilderness refuses nothing.
- The 16-tile exemption is what stops the fix from walling the player in. The 668 `Cross Wilderness Ditch` rows move 3 tiles, and gates, webs and ladders move fewer, so all of them stay legal and a route can always physically leave. The Mage of Zamorak teleport moves over 1,200 tiles and does not qualify. Originless transports are exempt by construction (their source is the ANYWHERE sentinel, outside the box), so escaping by home teleport is untouched. Walking was never filtered and still is not.
- Also established: the `Use: Wilderness Transports` checkbox DOES govern this, through `DrewsHelperPlugin:729` passing `!transportPolicy.allowsWilderness()` in as `avoidWilderness`. The earlier note that the toggle "was never involved" was about the category filter only and was stated too broadly - see the correction on Parked Item 13.
- Red-green proven, and deliberately as a BEHAVIOUR test rather than a compile failure: the rule body was swapped back to the old destination-only logic while keeping the new signature, so the tests still compiled. Result: `wildernessPreferenceSeparatesEntryFromExitAndNetworkHops FAILED` and `longRangeWildernessTransportIsRefusedWhenLeaving FAILED`, 2 of 28. Both pass with the fix.
- Process note: the first red-green script left the engine reverted, because gradle wrote to stderr, PowerShell promoted it to a terminating error under `$ErrorActionPreference = 'Stop'`, and the restore line never ran. Restores around a deliberate revert belong in a `finally` block. Fixed and re-run; engine verified back to the fixed version afterwards.
- Ran `.\gradlew.bat --no-daemon --console=plain clean test build`: BUILD SUCCESSFUL, 160 tests, 0 failures/errors/skips, jar 1,044,452 bytes.
- Also this pass, answering Myth's question on whether `collision-map.zip` can be trusted: 1,524 regions, all mainland landmark regions present, but 1,567 of 24,792 transport endpoints fall in regions it does not contain (1,403 overworld across 38 regions, mostly Zeah). Recorded with the detector step plan on Parked Item 12.

### D-0115 - Measured the chokepoint detector before building it, and the numbers killed the first plan

- Date: 2026-08-10
- Myth asked two things: how would we build our own collision map, and what is he actually looking for when he visits candidates - "just those main gates? you can see all the doors for houses and stuff right?". Rather than answer from the design, a throwaway prototype was written to measure the real scale, then deleted.
- Scan over all 1,524 regions, plane 0: 2,611,645 walkable tiles, 63,602 blocked seams, only 2,340 already covered by a transport row, so 61,262 uncovered. BFS detour filter at a 40-step cap left 72 chokepoints in Lumbridge, 139 in Varrock, 55 in Falador, 61 in Draynor - extrapolating to roughly 14,500 globally. The "ranked list Myth walks to" plan does not survive that. Recorded rather than quietly rescoped.
- **The approach itself is sound though - Phase 0 fixture #1 passes.** The Falador west wall gate is detected: `SEAM 2935,3450 -> 2936,3450` and `2935,3451 -> 2936,3451`, detour beyond 400 steps. x 2935 is blocked east for y 3448..3452 and only 3450/3451 have an open tile opposite, so the two-tile gap is the gate.
- **A run-length filter would have thrown that gate away.** Discarding collinear runs as "walls" is the obvious optimisation and it is wrong - the gate is a run of two. Measured isolated-vs-run: Lumbridge 13/59, Varrock 24/115, Falador 13/42, Draynor 5/56. Logged specifically so nobody adds that filter later thinking it is free.
- What does discriminate is detour MAGNITUDE - a house wall has its door a few tiles away, the missing gate did not. Not yet measured at a large cap; no list size should be promised until it is.
- **Ceiling identified: the collision map stores blocked/open, not object identity, so it can never tell a wall from a shut door.** That is what redirects the work to object identity.
- API facts verified directly against `runelite-api-1.12.35.jar` rather than recalled: `Client.getCollisionMaps()`, `CollisionData` and `CollisionDataFlag` all exist. This CORRECTS the earlier note (D-0111) that no live collision API could be confirmed - that was an honest unknown then, and it is now resolved as present. `Tile.getWallObject()/getGameObjects()/getDecorativeObject()/getGroundObject()` exist and the project already calls them at `DrewsHelperRouteTileOverlay.java:234,246,312`.
- The OSRS cache is present at `C:\Users\drews\.runelite\jagexcache`, so an offline whole-map decode is also possible - bigger job, and the XTEA question on map location archives must be verified rather than assumed.
- No production code changed. Both diagnostic test files were removed from the tree after reading; suite re-verified at 160 tests, 0 failures.

### D-0116 - Route B (cache decode) is the build, Route A is the check, B is also the refresh

- Date: 2026-08-10
- Myth asked whether we could do Route B first and then Route A "for game updates and stuff". Correct on the order; the reasoning needed one fix, and Route B turned out to be far more viable than the previous entry suggested.
- Evidence gathered rather than reasoned: `net.runelite:cache` is already a declared testImplementation of the sibling upstream project and `cache-1.12.35.jar` (434 KB) is already on disk in the gradle cache. Myth's OSRS cache is complete and current - `main_file_cache.dat2` 215.78 MB plus idx0..idx20 under `.runelite\jagexcache\oldschool\LIVE\`, all written the same day.
- A source-wide grep shows NOTHING upstream imports that library - no `net.runelite.cache`, no `RegionLoader`, no `ObjectManager`. So upstream ships no generator and `collision-map.zip` is inherited pre-built from Runemoro, which is precisely why it can be stale and cannot be repaired in place. Upstream's README instead points at `github.com/osrs-pathfinding/shortest-path-tooling` for OSRS cache dumpers - check that before writing a decoder.
- Jar contents confirm the toolchain is complete: `region/RegionLoader`, `region/Region`, `region/Location`, `definitions/loaders/LocationsLoader`, `definitions/loaders/MapLoader`, `definitions/MapDefinition`, `ObjectManager` and `definitions/ObjectDefinition`. The last two carry object NAME and ACTION list, so Route B produces the whole-map openable-object candidate list automatically - which is the direct answer to the 63,602-seam problem from D-0115.
- Blocker, stated as a blocker rather than glossed: the jar also ships `util/XteaKeyManager`, `util/XteaKey` and `util/Xtea`, and no key file exists anywhere on the machine. Assume map location archives need XTEA keys and confirm empirically first thing in the slice. Object definitions (names/actions) are in the unencrypted config index; only placements need keys. A published key file is not the same class of dependency as a curated third-party dataset - the map data still comes from Myth's own cache, so this does not reintroduce the mapgenie objection.
- **Correction to Myth's framing, worth keeping.** Route A is not the update path - Route B is. A game update refreshes the cache and B regenerates from it in one command. Route A's value is as a VALIDATOR: diff what B generated against what the live client reports through `Client.getCollisionMaps()` and `Tile.getWallObject()`, and flag the disagreements. That converts a Falador-gate-class bug from a routing symptom into a data alarm. A also covers instanced content and decode errors, which B cannot.
- Agreed order: B builds it, A checks it, B refreshes it. No code changed this pass.

### D-0117 - Route B and Route A both built; XTEA turned out not to be required

- Date: 2026-08-10
- Myth said "confirm that and then build it out so we have A and B". Both are now built, and the confirmation came first because everything depended on it.
- **XTEA: not required.** Probed the live cache through `net.runelite:cache` with a KeyProvider returning all zeros - a zero key means no decryption is applied, so anything that parses is genuinely unencrypted. Result: `loadMapDef` ok=2747 threw=0, `loadLocDef` ok=2747 threw=0, 4,829,650 object placements decoded. No key file, no community dataset, nothing but Myth's own cache. The earlier "believed XTEA-encrypted" caveat is now closed as wrong.
- **Route B shipped as `gradlew.bat dumpAccessPoints`.** New `cachetools` source set in `build.gradle` (LF preserved) so the cache-decoding dependency is build-time only and cannot reach the shipped jar or the test suite; `run` and `shadowJar` are untouched. `CacheAccessPointDumper` decodes every region, joins object placements to their definitions, cross-references our transport TSV and writes `tools/cache-access-points.tsv` plus a summary.
- First run: 62,401 object definitions of which 5,122 carry a movement action; 4,980,697 placements scanned; **2,936 regions in the cache versus 1,524 in our shipped collision map, so 1,425 regions we simply do not have** - that is the Zeah coverage hole from D-0114, quantified. 14,048 openable placements, 2,438 already covered, 11,610 not.
- **Acceptance fixture passes cold**, which is what makes the output trustworthy: the Falador west wall gate is found with no hint of where to look - `2935,3450 id=1728 Gate action=Open` and `2935,3451 id=1727 Gate action=Open`. That simultaneously validates the world-coordinate maths (`LocationsDefinition` holds REGION-LOCAL positions; world = region base + local) and the object filter.
- **Route A shipped as `DrewsHelperMapValidator` + a `Validate Map Data` toggle**, off by default. Runs from `onGameTick` but throttled to once per scene key, diffs `client.getCollisionMaps()` against our shipped map, and logs `DREW_MAP_VALIDATE`. It lists only the we-block-but-the-game-allows half, because that is the Falador-gate shape and the half that turns into a transport override row. An absent region is reported once as a coverage hole instead of ten thousand mismatches - essential with 1,425 such regions.
- Design note worth keeping: `DrewsHelperMovementMap` is package-private, so a public method taking it would be uncallable from `com.drewshelper`. The validator therefore exposes `validate(..., DrewsHelperCollisionMap)` publicly and keeps the interface-typed `validateScene` package-private for tests to pass fakes. No visibility of Myth's existing types was changed.
- Two traps hit and worth remembering. First, `EntityOpsDefinition.Op` has a public `text` field, no getter and no `toString`, so printing the ops list yields object hashes - an early probe reported 0 openable objects purely because of that. Second, `config` is not a field on the plugin; it is a private `config()` accessor at L2693.
- Ran `.\gradlew.bat --no-daemon --console=plain clean test build`: BUILD SUCCESSFUL, **165 tests** (160 + 5 validator tests), 0 failures/errors, jar 1,050,328 bytes.
- Parked items 16 (the movement-verb filter also matches chests and drawers, so 11,610 is an upper bound) and 17 (decide whether the 638 KB generated dump belongs in git) opened rather than actioned.

### D-0118 - Session close 2026-08-10: map-data work built, not yet wired in

- Date: 2026-08-10, ~02:00. Myth called it for the night and asked for everything written down.
- **The arc of the day.** Started from one symptom - the router took a long detour around a Falador gate its collision map recorded as solid wall. Ended with a tool that can list every gate, door, ladder and gap in the game from Myth's own machine, plus a validator that catches this class of bug the moment it appears.
- **Three approaches were tried and rejected before the one that worked**, and each rejection was evidence-based rather than a hunch: the OSRS wiki Gate page (disambiguation page - names only, no coordinates, and the Falador gate was not even listed); mapgenie (hand-placed editor markers, same incomplete-third-party-data problem we were escaping); and scanning our own shipped collision map (actually built and run - 63,602 blocked seams across 1,524 regions, unusable). That third one failed for a structural reason worth never forgetting: the shipped map stores only blocked/not-blocked, so it can NEVER distinguish a stone wall from a shut door. That is a hard ceiling, not a filter that needs tuning.
- **The unlock was measuring an assumed blocker instead of designing around it.** Map archives were believed XTEA-encrypted. A probe with a KeyProvider returning all zeros (zero key = no decryption applied, so anything that parses is genuinely unencrypted) returned terrain ok=2747 threw=0, objects ok=2747 threw=0, 4,829,650 placements decoded. No keys needed, no key file, no community dataset - just Myth's own cache.
- **Shipped: Route B** (`gradlew.bat dumpAccessPoints`), in its own `cachetools` source set so the cache dependency is build-time only and cannot reach the shipped jar or the test suite. 62,401 object definitions, 5,122 with a movement action, 4,980,697 placements scanned, 14,048 openable placements written to `tools/cache-access-points.tsv`. Acceptance fixture passes cold - it finds `2935,3450 id=1728 Gate action=Open` with no hint of where to look, which validates the world-coordinate maths and the object filter simultaneously.
- **Shipped: Route A** (`DrewsHelperMapValidator` + the `Validate Map Data` toggle, off by default). Once per scene it diffs `client.getCollisionMaps()` against the shipped map and logs `DREW_MAP_VALIDATE`. Lists only the we-block-but-the-game-allows half, because that is the Falador-gate shape and the half that becomes an override row. An absent region reports once as a coverage hole rather than ten thousand bogus mismatches - essential given 1,425 such regions exist.
- **Build state at close: BUILD SUCCESSFUL, 165 tests, 0 failures, jar 1,050,328 bytes.** Nothing is half-applied; the tree is clean to resume from.
- **The most important thing to carry into tomorrow: NONE of this has changed a single route yet.** Route B produces a report. No plugin code reads it. The routing improvement is step 2 of the new NEXT SESSION list, not something already delivered. Do not let the volume of work done today read as "the gates are fixed".
- Two honest caveats recorded rather than buried: the movement-verb filter over-collects containers (so 11,610 is an upper bound, not a gap count), and bulk-importing all 1,793 doors would make routing worse than today because locked/quest doors would be treated as passable.
- **Also learned and worth keeping:** upstream never built `collision-map.zip` either - it was inherited pre-built from Runemoro and nothing upstream imports a cache library, which is precisely why it goes stale and cannot be repaired in place. Upstream's README:32 points at `github.com/osrs-pathfinding/shortest-path-tooling` for community cache dumpers.
- **Repo gotchas that cost round-trips today**, recorded so they do not cost them again: `DrewsHelperMovementMap` is package-private (a public method taking it is uncallable from `com.drewshelper`); `config` is not a field on `DrewsHelperPlugin`, it is a `config()` accessor at L2693; and `EntityOpsDefinition.Op` has a public `text` field with no getter and no `toString`, so printing the ops list yields object hashes - that alone made an early probe report zero openable objects.
- **Process note that paid off twice.** A newly added passing test proves nothing until it has been seen to FAIL against the old code. Also: when a script deliberately reverts a file to prove red-green, the restore belongs in a `finally` block - gradle writing to stderr aborted one script mid-run and left the engine reverted.
- Guides could not be updated at the moment Myth asked - mythpc was unreachable (`connect to host 100.72.26.85 port 22: Connection timed out`, three attempts across ~30 minutes, likely asleep or a Tailscale drop). This entry and the NEXT SESSION list were prepared off-machine and applied once the host answered.
- No code changed in this pass. An explicit ordered to-do was added to `02_NEXT_WORK.md` above the Parked Items section so the next session starts from a plan rather than 17 parked findings.

### D-0119 - The access-point filter fixed, and the predicted fix was wrong

- Date: 2026-08-10
- To-do #1. The dumper matched any object with an "open"-ish verb, so it collected 640 Chests, 226 Drawers and 145 Wardrobes alongside the real doors. The 11,610 uncovered figure was an upper bound, not a gap count.
- **The fix recorded in Parked Item 16 does NOT work, and this is worth keeping.** That note said to intersect the verb with the object "actually obstructing movement" via `getInteractType()`, `getBlockingMask()` or `getWallOrDoor()`. All three were measured against known-keep and known-drop names and all three overlap COMPLETELY: keep=[2,1,0] drop=[2,1,0] on interactType, keep=[0,27,23,29,30] drop=[30,27,0,29,23] on blockingMask, keep=[1] drop=[1] on wallOrDoor. Obvious in hindsight - a chest genuinely is a solid object with an Open action, and ObjectDefinition never encodes "you can get through this". Do not retry that approach.
- **The discriminator is on the PLACEMENT, not the definition.** `Location.getType()`: wall-mounted types are 0,1,2,3,9. Measured: Gate 393/402 and Door 1850/1913 are type 0, Large door 188/188 is type 0, and not one container placement is a wall type - Chest, Drawers, Wardrobe, Cupboard, Barrel and Coffin are all type 10.
- Placement type alone is still not enough, because ladders, staircases, the Wilderness Ditch and gaps are also type 10. The verb splits that half: on non-wall placements `Open` is 1,674 rows and every top name is a container, while Climb-up (2,306), Climb-down (1,762), Cross (878), Climb (837), Enter (665), Jump, Pass, Squeeze-through and Exit are all genuine movement.
- **Final rule: keep if the placement is set into a wall, OR the verb is anything other than Open/Close.** Neither half works alone; together they separate cleanly.
- First run of that rule silently dropped 42 Trapdoor and 23 Door placements along with the chests - a trapdoor is genuinely floor-mounted and genuinely a way through, and geometry cannot see that. Added a deliberately short name-hint list (`door`, `gate`, `hatch`, `grate`) that only applies in the ambiguous-verb case. It rescued 100 rows and every one is a real passage: Trapdoor 42, Door 23, Wooden doors 6, Frozen Door 4, Ardougne Wall Door 4, Tree Door 4, Gate 3, Ancient Gate 2, Doorway 2, Bamboo Gate 2, Vault Door, Bone door, Magic door, Mahogany trapdoor, Secret Door, Imposing doors, Old battered door. No container came back.
- The dumper now PRINTS both the drop tally and the rescue tally. That is deliberate: the name-hint list is the one part of this that can rot, and every entry is a chance to re-admit a container, so its effect has to stay visible rather than silent.
- Result: 14,048 -> 12,474 rows. 1,574 dropped, 100 rescued, uncovered 11,610 -> 10,081. Acceptance fixture still passes cold - Falador gate `2935,3450 id=1728` and `2935,3451 id=1727` found with no hint of where to look.
- Ran `.\gradlew.bat --no-daemon --console=plain clean test build`: BUILD SUCCESSFUL, 165 tests, 0 failures. The cachetools source set is separate, so the plugin suite is untouched by this change.
- Housekeeping: 93 stale `.pre-*` backup files deleted at Myth's request after he committed `2b18d7f`. The temporary `ObjectFieldProbe` class and its gradle task were removed after use.
- Known small tail left open as Parked Item 18: Manhole (6), Cave (3), Tomb exit (2) are still dropped. Left as a deliberate decision rather than reflexively widening the hint list.

### D-0120 - Transport-row generator built; orientation is real but too noisy to ship

- Date: 2026-08-10. To-do #2, first slice.
- Built `AccessPointRowGenerator` and the Gradle task `generateTransportRows`. The task writes `tools/cache-derived-gates.tsv` plus `tools/cache-derived-gates-summary.txt`; it does not touch `tools/transport-overrides.tsv`, so routing behaviour is unchanged.
- Why the generator exists: the cache access-point dump gives one tile, but a transport row needs a from tile and a to tile. For wall-mounted objects, placement orientation gives the candidate edge. Ground truth: the Falador gate is locType 0 orientation 2, and the hand-written override crosses `2935,3450 -> 2936,3450`, so orientation 2 is east in that fixture. Current mapping: `0=W, 1=N, 2=E, 3=S`.
- Funnel from the current dump: 4,077 wall placements considered; 733 have no collision data; 1,172 predicted edges are already walkable; 698 have an unstandable side; 192 are already covered upstream; 1,282 candidate crossings remain, emitted both directions as 2,564 review rows.
- The control is the key result: predicted edge blocked 65.0% (2172/3344), perpendicular edge blocked 40.1% (2680/6688). The gap proves orientation carries real signal. The 65% rate is still far too low to trust blind import.
- Decision: do not merge cache-derived rows on orientation alone. A bad row is worse than a missing row because the router will confidently path through a wall. Treat `tools/cache-derived-gates.tsv` as a diagnostic/ranking file until a stronger acceptance filter exists.
- Next filter should rank by detour severity, remove obvious instance/minigame/scenery names from the candidate set (`Cloud bank`, `Portal of Death`, `Oozing barrier`, `Wall of flame`, `Gate of War`, `Energy Barrier`), and prove survivors against the live client via Route A before moving rows into `transport-overrides.tsv`.

### D-0121 - Ranked cache-derived review queue built; active override file still untouched

- Date: 2026-08-10. To-do #2, second slice.
- `AccessPointRowGenerator` now applies the requested acceptance funnel before anything is reviewable: exact-name junk exclusions for obvious instance/minigame/scenery rows, then a 512-step walking-detour score, then an optional Route A proof match.
- Exact junk excluded this pass: Cloud bank, Portal of Death, Oozing barrier, Wall of flame, Gate of War, Energy Barrier, Neutral Barrier, Blue Barrier, Red Barrier and Alchemical door.
- Output split is now explicit:
  - `tools/cache-derived-gates.tsv` remains review-only copy-paste-shaped rows, sorted by rank and commented with detour/proof state.
  - `tools/cache-derived-gates-review.tsv` is the machine-readable queue: rank, proof state, detour, normalized edge key, from/to, direction, name, action and label.
  - `tools/cache-derived-gates-proven.tsv` contains only rows whose normalized edge matched Route A live validator proof.
- Route A proof input is deliberately explicit rather than hidden log scraping. Paste raw `DREW_MAP_VALIDATE   x,y,plane DIR OURS_BLOCKS_LIVE_OPEN` lines into `tools/route-a-live-mismatches.txt`, or write `x<TAB>y<TAB>plane<TAB>DIR` rows into `tools/route-a-live-mismatches.tsv`, then rerun `gradlew.bat generateTransportRows`.
- Red/green proof gate check: a temporary pasted Route A mismatch line for edge `2809,9313,0 N` marked exactly one crossing proven and wrote two bidirectional rows to `cache-derived-gates-proven.tsv`; removing the temp proof file and regenerating returned proven crossings to 0.
- Final run on the live repo: raw candidate crossings 1,282; obvious junk removed 337; review crossings 945 / 1,890 bidirectional review rows. Detour severity: 862 are `>512`, 5 are `65-512`, 18 are `17-64`, 60 are `2-16`. Route A proof files absent, so proven crossings 0.
- `tools/transport-overrides.tsv` was checked and has no diff. No route behaviour changed.


D-0122 (2026-08-10) - Doors on the route are highlighted in the world.
  DrewsHelperRouteTileOverlay gained drawDoorSteps, drawDoorWallObject, isDoorLike and
  the pure helpers crossedWallBit / oppositeWallBit. A door is an ordinary one-tile step,
  so drawTransportEndpoints could never see it - isTransportJump requires a plane change
  or a gap larger than one tile. drawDoorSteps walks the adjacent steps instead, resolves
  the crossed wall edge, and outlines the door with the existing cyan outline() helper.
  Resolution order is graph-backed first (a real transport row, via the existing
  drawInteractable and its impostor handling), then the scene wall object on the crossed
  edge from either side, then a lenient same-tile fallback because an open door has swung
  and no longer records an orientation across the path. Open and Close both count as
  door-like: the ask was to see every door the route threads, not only the shut ones.
  Per-frame work is capped at 64 outlines and edges are de-duplicated on a
  direction-normalised key. No config option was added and the UI action list is
  unchanged. Build green, 168 tests (was 165), 0 failures.

D-0123 (2026-08-10) - The Falador Castle proof run of item 2 is void, not negative.
  No DREW_MAP_VALIDATE line exists in any RuneLite log, ever. drewshelper.validateMapData
  is true in the profile, so the toggle was not the problem. The client session ran
  12:38:03 to 13:51:21 from the official launcher, and every external plugin it loaded is
  a hub plugin; the jar Drew's Helper is deployed into had been replaced by the stock
  Skretzo build. The observation that the castle doors are ignored is therefore an
  observation about the stock plugin and says nothing about our data. Re-run through
  run-drews-helper-dev.bat. Note also that the three target rows are plane 2, the castle
  upper floor - the validator only checks the player's current plane, so the run has to
  physically go upstairs.

D-0124 (2026-08-10) - Dev-run plugin output goes to the GRADLE DAEMON LOG, not client.log.
  src\test\resources\logback-test.xml sits on the classpath the `run` task uses, and it
  overrides the logback config shipped inside runelite-client.jar. The dev client therefore
  logs to the console only, and gradle captures that console into
  %USERPROFILE%\.gradle\daemon\<ver>\daemon-<pid>.out.log. `client.log` stays frozen at
  whatever the last launcher-run client wrote, which makes a perfectly good dev session look
  like it never happened. Search the daemon logs by mtime, not client.log.

D-0125 (2026-08-10) - Validator now writes uncapped proof rows to a file and re-validates.
  DrewsHelperPlugin: added writeValidationMismatches, which appends every
  OURS_BLOCKS_LIVE_OPEN row as "DREW_MAP_VALIDATE   <x,y,plane D KIND>" to
  RUNELITE_DIR\drews-map-validate.txt - the exact shape the cachetools proof parser already
  reads. Session-scoped de-duplication (emittedValidationLines) means a re-validation only
  appends what is genuinely new, so a door opened mid-session shows up as a handful of rows
  rather than re-emitting thousands. Guarded at MAX_VALIDATION_ROWS_WRITTEN = 50000 with a
  warn-once, all IO in try/catch so a disk error can never kill the game tick, and the file
  is deleted in startUp so each session is clean. The scene gate now also expires after
  VALIDATION_REVALIDATE_TICKS = 100 (~60s). The 25-row console cap is deliberately
  unchanged - the file is the evidence path now, the console is just for eyeballing.
  Build green, 168 tests, 0 failures.
