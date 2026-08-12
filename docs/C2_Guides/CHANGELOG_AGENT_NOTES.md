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

D-0126 (2026-08-10) - Door highlights never drew because the overlay threw every frame.
  ObjectComposition.getImpostor() throws on any object with no impostor configuration, and
  isDoorLike called it unguarded. RuneLite's OverlayRenderer.safeRender swallowed the throw,
  so there was no visible symptom beyond "nothing is highlighted" - 29,921 "Error during
  overlay rendering" lines in the dev console, every one of them ours. Everything drawn after
  the throw point in that overlay was also lost, which silently included the waypoint
  endpoint markers. Fixed by guarding with getImpostorIds() != null, exactly as the
  pre-existing matchesObjectId already does two methods further down - the codebase had the
  answer in it the whole time. Build green, 168 tests.

D-0127 (2026-08-10) - First cache-derived rows promoted into active routing.
  10 crossings / 20 rows, all Falador Castle doors, moved from cache-derived-gates-proven.tsv
  into tools/transport-overrides.tsv with a full evidence block. Rule 2 of that file was
  checked properly: the whole bounding box x 2955-2985, y 3330-3348 returns 14 upstream rows
  and every one is a staircase - upstream models the castle stairs and not one of its doors.
  Then regenerated src/main/resources/drewshelper-transports.tsv, because that resource is
  what the router actually loads (see D-0114). Verified with the README's own acceptance
  test: 12,275 edges before, 12,295 after, ZERO pre-existing edges lost, 20/20 new crossings
  present, and the original Taverley gate override still present as a regression canary.

D-0128 (2026-08-10) - Every leaf of a doorway is highlighted, not just the one crossed.
  drawDoorSteps only ever inspected edges the route physically crosses, so a two-tile doorway
  lit one leaf and left the other dark - the second leaf sits on a tile the path never steps
  on. Added outlineDoorwayLeaves, which walks outward along the wall axis (perpendicular to
  the step, via the new pure helpers doorwayRunDx/doorwayRunDy) and outlines each contiguous
  door-like leaf, stopping at the first tile with no leaf so it cannot run away down a wall of
  unrelated doors. MAX_DOORWAY_WIDTH = 3 either side covers large gates.
  The inline edge-key maths moved into a pure static doorEdgeKey so the neighbour scan keys
  edges identically and the property is unit-tested. Leniency is inherited, not assumed: an
  open double door has BOTH leaves swung so neither records an orientation across the path,
  but the neighbour scan only drops the orientation test when the primary leaf itself was
  found that way - dropping it unconditionally starts matching doors on the perpendicular
  walls beside the opening. Build green, 171 tests (was 168), 0 failures.

D-0129 (2026-08-10) - Double-door highlighting confirmed working in game.
  Verified by Mytharium on the Falador Castle double door and independently on the Taverley
  Wall Gate - both leaves outline in cyan. The outlineDoorwayLeaves walk generalises beyond
  the one doorway it was written against, which was the open question.

D-0130 (2026-08-10) - Item 3 scoped against the real region diff.
  Compared the shipped collision map's 1,524 zip entries against the 1,190 regions that
  contain access points: 867 covered, 323 missing (3,055 access points), of which only 80 are
  surface regions holding 640 access points. The rest is dungeon and instance space. The
  earlier "1,425 missing regions" figure counts empty terrain and overstates the prize.
  Recorded because this number will otherwise be re-derived every time item 3 comes up.

D-0131 (2026-08-10) - ProofEdgeClassifier: measured what the cache says about every proof edge.
  New diagnostic in src/cachetools plus one gradle task, classifyProofEdges. Reads the 2,248
  live-proven edges and asks the cache what sits on each one. Deliberately reports wall
  PLACEMENTS and models no collision at all - the moment it starts modelling tile settings and
  bridges it stops being a measurement and becomes a second implementation to debug.
  Result: NOTHING 1444 (64.2%), OPENABLE 17 (0.8%), SOLID 787 (35.0%), zero edges outside the
  cache. Writes tools/proof-edge-classification.txt. Ran clean on the real 226 MB cache in 6s.
  The generated report's own closing paragraph overstates the SOLID finding as a decode
  disagreement - see 02_NEXT_WORK for why that reading is premature.

D-0132 (2026-08-10) - Orientation-facing split resolves most of the SOLID bucket.
  ProofEdgeClassifier now records each wall placement's locType and orientation, tracks which
  of the two tiles it came from, and tests whether it faces the crossed edge (near tile: same
  direction; far tile: opposite). Mapping copied from AccessPointRowGenerator, not re-derived.
  Result: SOLID_NOT_FACING 493 vs SOLID_FACING 294; OPENABLE_FACING 10 vs OPENABLE_NOT_FACING
  7; zero invalid orientations. 92% of SOLID_FACING placements are locType 9/1/3 corners and
  diagonals where the test is not rigorous, leaving ~35 locType-0 placements genuinely
  unexplained. The test's own false-negative rate on confirmed doors is 41%, which is recorded
  in the report rather than hidden. Ran clean on the real cache; build green, 171 tests.

D-0133 (2026-08-10) - LocTypeShapeProbe: the collision shape table, derived from data.
  New cachetools diagnostic plus the probeLocTypeShapes task. Cross-tabs (locType, orientation,
  openable) against the shipped map's four edge flags, over single-placement tiles in covered
  regions only, with a 250,000-tile no-wall null baseline at ~22%.
  Three guards make the result trustworthy and all three were required: uncovered regions
  excluded (an absent region reads as fully blocked and would have forced every rule to 100%),
  multi-placement tiles excluded (a blocked edge cannot be attributed to one of two placements),
  and a null baseline so an absolute percentage means something.
  Results: locType 0 and 3 are single-edge on {0:W,1:N,2:E,3:S}; locType 2 is a two-edge corner;
  locType 9 blocks the whole tile with orientation irrelevant; locType 1 shows no directional
  signal at all and is carried as UNKNOWN. Openable placements peak on the same direction at
  ~60% vs ~93%, which is the open-door state showing through.
  Nothing about the expected answer was hard-coded - the table is the output, not the input.

D-0134 (2026-08-10) - Full-scene live flag dump. One trip, permanent ground truth.
  New public DrewsHelperMapValidator.liveBlockedMask(flags, sx, sy) returning bit 0 = north
  blocked, bit 1 = east blocked, by negating the existing liveCanMoveNorth/liveCanMoveEast
  rather than duplicating collision semantics. DrewsHelperPlugin now dumps the complete live
  blocked-state of every measurable tile edge in a validated scene to
  RUNELITE_DIR\drews-live-flags.txt, once per scene key per session, gated on the EXISTING
  validateMapData toggle. Runs before the coverage-hole and empty-mismatch early returns, so a
  scene with zero mismatches still yields data.
  Format: a header line per scene then "<x>,<y>,<plane> <N><E>" for tiles with a blocked edge;
  absence inside the covered area means passable. Only N and E are stored because south and
  west are the neighbour tile's north and east - the same reason the shipped map format does it.
  CORRECTION APPLIED DURING REVIEW: the generated version swept the full 104x104 scene, but the
  final row and column have no neighbour to consult, so their mask silently read as passable.
  Since absence means passable, that would have injected a ring of false ground truth. The loop
  now stops one short on both axes and the header carries an explicit "covered" bound.
  Why this exists: every round of item 3 so far cost another in-game trip because the validator
  only recorded one-sided OURS_BLOCKS_LIVE_OPEN rows. Future questions are now offline
  cross-tabs. Build green, 176 tests (was 171).

D-0135 (2026-08-10) - CollisionMapBuilder: v2 built, and it works.
  New cachetools program plus the buildCollisionMapV2 task. Cache -> per-region flag maps at
  FLAG_COUNT = 4 -> collision-map-v2.zip, byte-compatible with the EXISTING DrewsHelperFlagMap
  (16-byte big-endian minX/minY/maxX/maxY header + BitSet.toByteArray, gzipped per region,
  entries named regionX_regionY). src/main was not touched - v1 keeps working.
  Uses the measured D-0133 shape table as named constants, with locType 1 blocking all four
  edges per D-0120 rather than guessing a direction.
  SELF-VERIFICATION: every written entry is read back and decoded with an index formula
  identical to DrewsHelperFlagMap.index. "ROUND TRIP OK 6 regions" - the format is right.
  Also asserts PASSABLE and DOOR are never both set on an edge.

  FIRST REAL RESULT, against the 2,248-edge proof file (all of which the shipped map blocks,
  so the baseline is 0% correct):
      passable in v2        1466   (65.2%)
      door in v2              10   ( 0.4%)
      still blocked in v2    772   (34.3%)
      outside built regions    0
  1,476 of 2,248 fixed, 65.7%, from a baseline of zero.

  CROSS-VALIDATION - this is the part worth trusting. ProofEdgeClassifier, written earlier and
  sharing no code path with the builder, independently classified the same edges as
  NOTHING 1444 / OPENABLE 17 / SOLID 787. The builder produced 1466 / 10 / 772. Two independent
  implementations landing within ~1.5% of each other is meaningful corroboration that the
  shape table and the decode are both right.

  Build statistics: 6 regions (auto-derived from the proof file coordinates, not hard-coded),
  177,424 edges made passable, 85 door edges, 4,909 terrain-blocked tiles, 909 locType-1
  UNKNOWN placements, 17,465 non-wall placements ignored, 48 bridge-branch tiles, 181
  out-of-region neighbour writes skipped. Zip is 5,912 bytes for 6 regions.

  ONE COMPILE FIX APPLIED: ProofEdge.regionId() called the outer two-arg regionId(x, y)
  unqualified. The nested no-arg method shadows it, so it resolved to itself and would not
  compile. Qualified as CollisionMapBuilder.regionId(...) with a comment.

D-0136 (2026-08-10) - DIAGNOSED, NOT FIXED: the checkerboard route is a dead tie-break field.
  Reported by Mytharium on the Lumbridge castle roof and again on the Lumbridge bridge. Traced
  to the A* itself, and it is NOT a collision-data problem - v2 is not wired into the loader,
  so the router was running entirely on the old v1 map during his run.

  Chain of evidence:
    1. The engine supports all four diagonals (canMoveNorthEast / NorthWest / SouthEast /
       SouthWest at DrewsHelperWalkingRouteEngine lines 1284-1296).
    2. Every step costs the same: relax(predecessor, distance + 1, ...) at lines 712 and 739.
       A diagonal step costs 1, exactly like a cardinal step - which is correct for OSRS, where
       a diagonal move takes one tick.
    3. Therefore a straight run and a zigzag between the same two tiles have IDENTICAL cost and
       identical remaining distance. They tie completely.
    4. SearchNode.compareTo (line 2130) breaks ties in this order:
           priority -> remaining -> preferencePenalty -> sequence
       "sequence" is insertion order, which is arbitrary with respect to straightness.
    5. A "turns" counter EXISTS and is maintained correctly - incremented at lines 821-823
       whenever the direction changes, stored on the node at line 2110 - and is NEVER
       COMPARED. It was clearly built for exactly this and then not wired in.

  Why it looks like a checkerboard rather than a zigzag: consecutive tiles of a diagonal run
  touch only at their corners, so filling each step's tile draws a checkerboard.

  Important: the path is NOT longer. Every zigzag tile is a legitimate one-tick move, so the
  route is genuinely optimal in ticks - it is the presentation and the follow-along experience
  that suffer, not the ETA.

  Proposed fix (NOT APPLIED - awaiting Mytharium's call): insert turns into the tie-break chain
  ahead of sequence. It cannot lengthen a route, because it only ever chooses between paths
  that already tie on cost and remaining distance, and the field is already computed so there
  is no extra work per node.

D-0137 (2026-08-10) - Live ground truth captured, and it is substantial.
  drews-live-flags.txt: 57,993 rows, 928 KB, 14 scenes, planes 0/1/2 at BOTH castles plus the
  Lumbridge-to-Varrock corridor. Scene keys: 2896:3392:0, 2904:3352:0, 2912:3312:0/1/2,
  3168:3168:0/1/2, 3208:3176:0, 3192:3216:0, 3184:3256:0, 3152:3296:0, 3160:3336:0, 3160:3376:0.
  The dump mechanism works exactly as designed - one block per scene key, all three planes
  captured without the minute-long waits the old mismatch capture needed.

  Spot reads confirm the data is sane and readable:
    - Lumbridge upper floor (plane 2) shows dense, structured interior walls.
    - The Lum bridge crossing appears as a clean fully-passable east-west corridor at y=3225,
      sandwiched between solid water rows - exactly the shape a bridge should have. This is the
      ground truth needed to verify the terrain and bridge conventions flagged in D-0135.

  Tooling note: the file is 928 KB and lcl-ssh downloads truncate at ~200 KB, so it must be
  processed ON mythpc, never pulled whole. tools/grid rendering is done in place for this reason.

D-0138 (2026-08-10) - Turn tie-break SHIPPED, but it does NOT explain the reported checkerboard.
  Read this whole entry before believing the headline. The change is real and safe; the
  diagnosis it came from was only half right, and the half that was wrong matters.

  WHAT SHIPPED
  "turns" is now compared in both places that previously fell through to insertion order:
    - SearchNode.compareTo, ahead of the sequence tie-break (queue ordering)
    - isBetterPathToSamePointThan, ahead of the sequence tie-break (which stored path wins)
  Placement is deliberate and must not be raised. In isBetterPathToSamePointThan it sits BELOW
  compareClientMovePreference, which exists to make the drawn route match the path the game
  client actually walks. Straightening a line the client would not walk would make the overlay
  lie about where the player is going. Only the arbitrary insertion-order tie is replaced, so
  the change cannot lengthen a route and cannot override a deliberate preference.
  177 tests pass, including all eight path-shape / client-matching tests:
  keepsStraightAxisRouteStraight, keepsDiagonalWhenAxesAreTied,
  prefersPrimaryAxisForwardBeforeDiagonalProgress, prefersClientCardinalForkWhenSameLengthPathRejoins,
  matchesLiveClientForkTowardSoutheastWaypoints, clientStyleRankingWorksWithLoadedTransportGraph,
  appliesTargetAwareLocalOverridesForRepeatedLiveForks,
  shapeRankingShadowExposesDistinctSameLengthRandomChainRoute.

  WHAT COULD NOT BE PROVEN - and this corrects D-0136
  D-0136 stated the unused turns field was the cause of the checkerboard. That was a plausible
  reading of the comparator, not a demonstrated cause, and the attempt to demonstrate it FAILED:
    - A two-tile corridor repro (the shape of a bridge deck) was written and passes.
    - The engine change was then reverted with git checkout and the test re-run.
    - It STILL PASSED. The existing client-move-preference logic already resolves that shape.
  So there is no evidence the turn tie-break changes anything the player sees. The corridor test
  was kept, with its javadoc rewritten to say exactly this, because the property is worth
  pinning - but it is NOT a regression test for the tie-break and must never be cited as one.

  THE MORE LIKELY EXPLANATION, from the tests themselves
  keepsDiagonalWhenAxesAreTied asserts that (0,0) -> (3,3) produces (1,1), (2,2), (3,3) - a pure
  diagonal run, and it predates this work. A diagonal step covers both axes in one tick, so when
  a destination is offset on both axes a diagonal run is not merely allowed, it is the ONLY
  optimal path. Consecutive tiles of a diagonal run touch only at their corners, so filling each
  step's tile draws exactly a checkerboard.
  If that is what was reported - and the evidence points that way - then the route is correct and
  cannot be straightened without making it strictly longer. The problem would be one of
  RENDERING, not routing: the overlay should join diagonal steps so the path reads as continuous
  instead of as disconnected squares. That is a change to DrewsHelperRouteTileOverlay, not to the
  A*, and it has not been made or authorised.

  HOW TO SETTLE IT: capture the actual solved path for a route that shows the pattern and read
  its per-step deltas. A consistent 1/1, 1/1, 1/1 is a correct diagonal run. An alternating
  1/1, 1/-1, 1/1 is a genuine weave. Those are different bugs with different fixes, and no
  further change should be made until it is known which one this is.

D-0139 (2026-08-10) - The ~200 KiB transfer cap is REAL, and it SUCCEEDS while truncating.
  Measured, not assumed. Downloading the 928,448-byte drews-live-flags.txt through ssh_download
  produced exactly 204,800 bytes - 200 KiB to the byte - twice, deterministically, cut
  mid-line, and the tool reported "Downloaded ..." with no warning of any kind.

  The silent success is the actual hazard, well beyond the size limit. Any analysis run over a
  downloaded file is at risk of being computed on the first 22% of the data and reported with
  full confidence. The 57,993-row capture would have arrived as 12,792 rows and 3 of 14 scenes,
  and nothing would have said so.

  It is NOT in the lcl-ssh MCP server. That server (310 lines) shells out to plain scp via
  buildScpArgs and applies only a timeout; there is no byte limit in it, and runCommand only
  buffers stdout/stderr, not the transferred file. The local filesystem is not the cause either
  - writing and copying a 900,000-byte file in the same directory works. The cap is therefore
  enforced above the server, in fleet infrastructure this project does not own.

  STANDING RULE until it is raised: never trust a downloaded file's completeness. Either
  process the file in place on the remote host, or compare the local byte count against the
  remote one before using it. Compression is a practical workaround for text - this dataset
  compresses about 6:1, so the full 928 KB would gzip to roughly 150 KB and fit under the cap.

D-0140 (2026-08-10) - VERDICT: the checkerboard is ROUTING, not drawing. Measured.
  New RouteShapeProbe (task probeRouteShape) searches routes on the real shipped map and scores
  each by excessTurns = actualDirectionChanges - minimumDirectionChanges, where the minimum is 0
  for a pure straight or pure diagonal displacement (dx==0, dy==0 or |dx|==|dy|) and 1 otherwise.
  That formula is the whole discriminator: a long diagonal run has excessTurns 0 and merely
  DRAWS as a checkerboard, whereas a genuine zigzag shows excess.

  Result over 980 attempted / 594 solved pairs in two boxes (Lumbridge upper floor, Lum bridge):
      excessTurns: 0=219  1=18  2=68  3=81  4=47  5=29  6=29  7=12  8=19  9=6  10=8 ...
                   ... 25=3  27=1  28=2  29=2  31=1  33=2
      375 of 594 routes (63%) have excessTurns > 0.  maxExcessTurns = 33.
      99 routes are excessTurns == 0 WITH a pure diagonal run - the genuinely-fine case.
  Mytharium was right and the earlier "probably a correct diagonal run" reading was wrong. Both
  cases exist, but over-turning dominates almost two to one.

  KNOWN DEFECT IN THE PROBE, do not cite its top-15 block: the "Top 15 routes by excessTurns"
  section prints routes with excessTurns == 0, so its ordering is broken. The distribution and
  the summary line come from independent counters and ARE trustworthy. Consequence: we have the
  verdict but NOT yet a per-step delta string for an actual offender, which was the specific
  artefact requested. Fix the sort before quoting any example route.

D-0141 (2026-08-10) - The straightening fix already exists in the engine, unused.
  DrewsHelperWalkingRouteEngine has two ranking modes. Every real solve uses
  RouteRankingMode.CLIENT (lines 263, 272, 351, 360). RouteRankingMode.SHAPE is referenced by
  exactly one method, solveWithShapeRankingWithoutLocalWalkingOverrides (line 140), which is a
  diagnostic/shadow entry point used only by tests.

  Under CLIENT, isBetterShortestStep returns false immediately (line 572) and the loop breaks
  after the first legal candidate (lines 537-540) - the first legal step in a fixed move order
  wins at every tile, with no shape consideration at all.
  Under SHAPE, steps are scored:
      shapeStepScore = lineError * 10 + reversePenalty * 10 + turnPenalty * 2 + preferencePenalty
  where lineError is perpendicular distance from the straight start->target line (cross product
  over the major axis) and turnPenalty flags a direction change. That is precisely a
  hug-the-straight-line, penalise-turns ranker - exactly the requested behaviour, already built.

  THIS ALSO EXPLAINS D-0138. The turns tie-break added to the A* comparator tested inert because
  the visible path is not the raw A* path: it is rebuilt afterwards by this ranking pass. The
  comparator was the wrong lever. That was an honest miss and this is the correction.

  NOT CHANGED. Switching the visible route to SHAPE is not a one-line swap: the only existing
  SHAPE entry point ALSO disables local walking overrides, and CLIENT mode exists deliberately to
  mirror what the game client walks. Needs Mytharium's call plus a proper A/B before shipping.

D-0142 (2026-08-10) - Live cross-tab: terrain rule VERIFIED, locType 1 SOLVED, bridge still open.
  New LiveFlagCrossTab (task crossTabLiveFlags) reads the 57,979-row live capture (14 scenes) and
  cross-tabs it against the cache. Null baseline over no-wall covered tiles is ~25-27% per edge.

  POSITIVE CONTROL PASSES, so the harness can be trusted. locType 0 against LIVE data:
      orient 0 -> W 96.9%    orient 1 -> N 98.4%
      orient 2 -> E 99.0%    orient 3 -> S 97.5%      (all other edges 22-30%)
  Cleaner than the 93% the old-map probe gave, because live data carries no missing-region noise.
  This independently re-confirms DIRECTION_BY_ORIENTATION = {W, N, E, S} from a third source.

  Q1 locType 1 - it DOES have a directional signal, which the old-map probe could not see
  (that probe reported a flat 76-79% and we carried it as UNKNOWN under D-0120):
      orient 0   N 66.3%   E 17.5%   S 17.6%   W 15.8%     -> N
      orient 1   N 64.7%   E 63.8%   S 20.8%   W 21.4%     -> N + E
      orient 2   N 18.2%   E 61.0%   S 20.1%   W 19.1%     -> E
      orient 3   N 17.5%   E 17.8%   S 15.4%   W 15.5%     -> nothing above baseline
      1,408 single-placement tiles.
  Read it as a direction, not a certainty: the peaks are 61-66% against a ~25% baseline, a solid
  35-40pp lift but well short of locType 0's 70pp. locType 1 probably covers more than one
  underlying shape. Adopting this would replace blocking all four edges on 909 placements in the
  six proof regions alone, so it is the single biggest lever on the 772 still-blocked edges.

  Q2 terrain floor-blocking - VERIFIED, and this was explicitly unproven before:
      6,938 covered tiles marked blocked by the rule
      live agreement: N 91.8%  E 91.7%  S 100.0%  W 100.0%
      lift vs baseline: +65.2 / +65.0 / +74.4 / +74.4 pp
      overall edge agreement 26,518/27,665 = 95.9%
  The rule is PRECISE. But it is NOT COMPLETE, and the reverse direction says so loudly:
      live blocks all four edges on 16,801 covered tiles; 10,926 of them (65.0%) are not marked
      by the terrain rule, and 10,116 of those carry no wall placement at all.
  That is the dangerous direction - the router believing it can walk where the client cannot.
  Precision is solved; coverage is not. Do not read 95.9% as "terrain is done".

  Q2 bridge convention - STILL UNVERIFIED, zero usable samples:
      bridge-branch source tiles 132,865, but covered marks 0, and 69,065 negative-plane skips.
  Nothing in this capture exercised it. The large negative-plane skip count is itself suspicious:
  the branch computes realPlane = z - 1 and most hits are at plane 0, which is discarded. Worth
  checking whether the convention is being applied at the wrong plane before trusting it at all.

D-0143 (2026-08-11) - #24 NOT SHIPPED. SHAPE ranking measured WORSE than CLIENT. Do not switch.
  Mytharium authorised switching the live route to RouteRankingMode.SHAPE. The switch was NOT
  made, because measuring it first showed it would have made his complaint worse, not better.

  THE MEASUREMENT. Both modes solved for the same two routes, using coordinates already known
  good (they come from matchesLiveClientForkTowardSoutheastWaypoints, which asserts READY at 32
  and 38 tiles). Step counts were identical in every case, so this is purely about shape:

    (2942,3243) -> (2962,3214), 32 steps both modes
      CLIENT turns=7   0/-1 x7, 1/-1 run, 1/0 x3, 1/-1 run
      SHAPE  turns=18  0/-1 1/-1 1/-1 0/-1 1/-1 1/-1 0/-1 0/-1 0/-1 1/-1 1/-1 0/-1 0/-1 1/-1 ...

    (2942,3243) -> (2951,3208), 38 steps both modes
      CLIENT turns=7
      SHAPE  turns=11

  SHAPE more than doubled the direction changes on the first route. Its delta string is the
  textbook checkerboard - exactly the artefact being complained about.

  WHY, and this is the useful part. shapeStepScore is:
      lineError * 10 + reversePenalty * 10 + turnPenalty * 2 + preferencePenalty
  lineError is perpendicular distance from the straight start-to-target line, and it outweighs
  the turn penalty five to one. But a STAIRCASE TRACKS A DIAGONAL LINE MORE TIGHTLY THAN AN
  L-SHAPE DOES. So minimising line error actively PRODUCES the zigzag. The ranker named "SHAPE"
  optimises for the wrong shape. That was not obvious from reading it - it read like exactly the
  right fix - and only running it revealed the inversion.

  RE-WEIGHT EXPERIMENT (run, then fully reverted - it is NOT in the tree). Flipping the weights
  to turn-dominant, turnPenalty * 10 + reversePenalty * 10 + lineError * 1:
      route 1: CLIENT 7 -> 5 turns   (improved, and the deltas are clean runs)
      route 2: CLIENT 7 -> 8 turns   (worse)
  Promising but NOT a clean win on a two-route sample, and it broke a test that asserts SHAPE
  produces a distinct route. It needs the full 594-pair A/B before anyone considers shipping it.
  Two routes is not evidence; it is a hint.

  STATE: engine weights restored byte-for-byte, temporary diagnostic test removed, turns
  tie-break from D-0138 still present, 177 tests 0 failures, diff unchanged at 293 insertions
  across the same 4 files. Nothing from this experiment reached the tree.

  STANDING LESSON, and it is the third time this pattern has paid: a mechanism that reads like
  the obvious fix is not evidence that it fixes anything. The turns tie-break (D-0138) read
  right and was inert. SHAPE ranking read right and is actively harmful. Measure the change
  against ground truth BEFORE switching live behaviour, every time.

  NEXT: the A/B harness (probe measuring both modes on 594 pairs, plus the offender-sort fix
  from D-0140) is the instrument that can settle the re-weight properly. Until it has run, the
  live route stays on CLIENT.

D-0144 (2026-08-11) - The 30-minute SSH cap is HALF implemented. Measured, not assumed.
  Mytharium had Architect raise the SSH ceiling from 5 to 30 minutes and asked for confirmation.
  Two independent ceilings exist and only one moved:

    RAISED   the lcl-ssh server timeout cap, 300000 -> 1800000 ms. The tool schema now
             advertises max 1800s, and this session is running the patched server.
    NOT      /mnt/lcl/c2/ssh-watchdog.sh. The file on disk reads MAX_AGE=1800, but the RUNNING
    RAISED   process is PID 122, root-owned, etimes 200,818s (~56 hours) - it has never
             restarted. Its log still prints "(age: NNNNs > 300s)", which is the live value
             bash parsed into memory on 2026-08-08. Editing the file cannot reach a running
             process, and C2 is uid 1001 with no sudo, so it stays 300 until a container restart.

  Practical consequence: a BLOCKING ssh_exec still dies at roughly 300-360s regardless of the
  server cap. Anything longer must be detached on the remote host and polled.

D-0145 (2026-08-11) - Download truncation FIXED and verified end to end.
  The conditional scp -O patch works. Re-downloaded the 928,448-byte live capture:
      remote bytes 928448   sha256 A5246A17F461897C...
      local  bytes 928448   sha256 A5246A17F461897C...   MATCH
      57,993 lines, all 14 scene headers present.
  Previously this exact file came down at 204,800 bytes twice. The 200 KiB ceiling is gone on
  this path. Keep verifying size+hash after every transfer anyway - that rule stands regardless.

D-0146 (2026-08-11) - DETACHING A LONG JOB ON WINDOWS: only Task Scheduler survives.
  Three mechanisms tried against the same job, in order:
    1. cmd  start /b            - dies when the SSH session closes. No log, no java process.
    2. PowerShell Start-Process - same. Still a child of the session tree.
    3. Task Scheduler           - WORKS. Register-ScheduledTask + Start-ScheduledTask, principal
                                  New-ScheduledTaskPrincipal -LogonType Interactive.
                                  Verified state=Running, log growing, 3 fresh java processes.
  Windows OpenSSH tears down the session process tree on disconnect, so anything parented to the
  session goes with it. The scheduler service is a different parent, which is why it survives.
  Pattern: write a .bat that runs the job and writes a DONE marker with the exit code, register
  it as a one-shot task, start it, then poll the marker with short SSH calls.

D-0147 (2026-08-11) - Truncated-file audit. This lane has NO gap; older Fort Stewart work might.
  Eleven distinct local files sit at exactly 204,800 bytes, dated 2026-06-12 to 2026-08-05:
      ace_paks/circulation.pak, ace_paks/breathing.pak, tccc_data.pak, tccc_data2.pak,
      gtt_heightmap.asc, analysis/wb-index.txt, analysis/rhs-cp01.rdb, analysis/rhs-statusquo.rdb,
      medlog_0135.log, med0703_diag.txt, m320_live/M320.xob
  (Three more under workspace/xfer-test are deliberate control artefacts from the truncation
  investigation and are correct at that size.)

  THE COLLISION-MAP WORK IS CLEAN, and this is provable rather than reassuring: the live capture
  was never analysed from a downloaded copy. Every read happened ON mythpc - the grid renders ran
  through PowerShell in place, and LiveFlagCrossTab read the file on the remote disk. Its report
  says "rows parsed: 57979" and "scenes found: 14"; the full file is 57,993 lines with 14 scene
  headers, and 57993 - 14 = 57979 exactly. A truncated read would have produced 12,792 rows and
  3 scenes. The arithmetic closes, so no conclusion in items 2, 3 or 24 rests on partial data.

  AT RISK, and worth re-checking before being relied on again: the ACE/TCCC medical paks. The
  recorded conclusion that the Breathing and Circulation paks are "real and rich" was drawn from
  files that are exactly 204,800 bytes. That conclusion may have been formed on a fraction of
  each pak. The sources are NOT at either Workbench addons root on mythpc, so regathering needs
  the original location identified first - do not assume the earlier read was complete.

D-0148 (2026-08-11) - FULL A/B COMPLETE. SHAPE ranking is decisively worse. #24 stays closed.
  Ran the both-mode probe detached via Task Scheduler, 352 pairs where BOTH modes solved:

      CLIENT   mean excessTurns 5.41   median 3.00   zero-excess 71   max 33
      SHAPE    mean excessTurns 11.33  median 11.00  zero-excess 36   max 38

      SHAPE worsened   249 pairs
      SHAPE improved    14 pairs
      unchanged         89 pairs

  SHAPE roughly DOUBLES the mean and nearly quadruples the median. It loses on 249 pairs and
  wins on 14. The two-route sample in D-0143 was not a fluke - this confirms it at scale.
  RouteRankingMode.SHAPE must not be wired into the live route. Item 24 is closed on evidence.

  Correctness gate PASSED: SHAPE-LONGER count = 0. Neither mode ever produced a longer route,
  so this really is purely about shape, not distance. clientOnlySolved and shapeOnlySolved were
  both 0, so every measured pair is a true like-for-like comparison.

  Sort fix verified: the offender list now leads with excessTurns=33, which is the maximum. The
  self-check guard did not fire.

D-0149 (2026-08-11) - CORRECTION to D-0140: excessTurns overstates the ABSOLUTE problem.
  Reading the newly-correct offender rows exposed a bias in my own metric that the earlier
  summary did not account for. The top offender:

      start 3250,3210,0  ->  end 3250,3230,0   displacement 0/20   BUT clientSteps = 79

  A 20-tile displacement taking 79 steps is a route going the long way around a building. Its
  33 "excess" turns are mostly OBSTACLE-FORCED, not wasted - minimumDirectionChanges is computed
  for open ground (0 or 1), which is simply the wrong yardstick once walls are involved.

  So the earlier headline - "63% of routes take more direction changes than necessary" - is
  overstated as a measure of how much turning is avoidable. Many of those turns are the route
  correctly navigating geometry.

  What survives, and it is the part that actually mattered: the A/B COMPARISON is unaffected.
  Both modes face identical obstacles on identical pairs, so CLIENT 5.41 vs SHAPE 11.33 is a
  clean apples-to-apples result. The relative number is trustworthy; the absolute one is not.

  For any future attempt at the straightness problem, the metric needs an obstacle-aware
  baseline - compare against the minimum turns achievable ON THIS MAP between those two tiles,
  not against open-ground minimum. Until that exists, do not quote absolute excessTurns as
  evidence of how bad routing is.

D-0150 (2026-08-11) - locType 1 measured rule shipped into the builder. Proof PASSES.
  Replaced the D-0120 UNKNOWN placeholder (block all four edges) with the rule LiveFlagCrossTab
  measured against the live client capture:

      orient 0 -> {NORTH}            orient 1 -> {NORTH, EAST}
      orient 2 -> {EAST}             orient 3 -> {}  (blocks nothing)

  An out-of-range orientation falls back to blocking all four and is counted, because an unknown
  orientation is genuinely unknown - it does not silently become an empty edge set.

  PROOF RESULT, 2,248 edges, 6 auto-derived Falador regions:

      metric                  before    after
      passable in v2            1466     1485    (+19)
      door in v2                  10       10
      still blocked in v2        772      753    (-19)
      outside built regions        0        0
      ROUND TRIP OK 6 regions, mutual-exclusion assert intact

  still-blocked went DOWN and nothing regressed. That is the pass condition.

  READ THE HEADLINE CAREFULLY - 19 edges understates the change. The proof file is 2,248
  specific edges; the map covers 177,000+. What actually moved:

      locType 1 placements        909  (orient0 259, orient1 210, orient2 228, orient3 212)
      edges blocked BEFORE      3,636  (909 placements x 4 edges each)
      edges blocked AFTER         907
      invalid-orientation fallbacks  0

  That is 2,729 fewer wrongly-blocked edges across six regions - a 75% cut in locType-1
  over-blocking. The proof file barely samples those tiles, which is why it only sees 19. The
  routing benefit is real and much larger than the proof delta; do not quote 19 as the size of
  the win, and do not quote 2,729 as proven either. One is measured on ground truth, the other
  is a count of what the rule changed.

  THE RISKY PART HELD. Orientation 3 now blocks nothing on 212 placements, which moves edges
  from blocked to passable - the dangerous direction under D-0120. If that were wrong,
  still-blocked would have stayed flat while new routing-through-walls errors appeared. Instead
  still-blocked strictly decreased. That is consistent with the measurement (15-18% vs a ~25%
  baseline, i.e. measured-open) but it is NOT proof against a live client; the honest check is a
  fresh capture in those regions. Flagged, not claimed.

  STILL OPEN: 753 edges. Expected remaining contributors, in order - locType 9 diagonals that
  block whole tiles, doors standing open when the capture was taken, and the terrain rule, which
  D-0142 measured as precise (95.9%) but INCOMPLETE (it misses 65% of tiles the client blocks on
  all four edges). Terrain completeness is now the largest untouched lever, not locType 1.

D-0151 (2026-08-11) - Yardstick self-check fixed and PASSED. Straightness numbers are now quotable.

  See D-0122 for the rule. Gate output after the fix:

      openGroundSelfCheckPairsChecked:         36
      openGroundSelfCheckStraightLineBlocked: 114
      BASELINE SELF-CHECK FAILED count:         0
      BASELINE BUG negativeTrueExcessTurns:     0
      baselineUnreachable 0 - BASELINE_UNRESOLVED 0 - clientOnlySolved 0 - shapeOnlySolved 0

  CERTIFIED NUMBERS (CLIENT mode, obstacle-aware baseline):

      trueExcessTurns   0=105  1=71  2=65  3=45  4=20  5=18  6=7  7=8  8=8  9=1  10=3  12=1
      openGround        0=71   1=13  2=48  3=46  4=38  5=24 ...  max 33

  Worst case drops 33 -> 12 once obstacle-forced turns are removed from the count. Roughly two
  thirds of the old "excess" was geometry, not waste - this confirms the D-0148 correction with a
  real instrument instead of an argument.

  Worked example, offender #1:
      3240,3210 -> 3250,3230   displacement 10/20   75 steps
      minTurnsAmongShortestPaths = 20   <- forced by the building it threads
      actualDirectionChanges     = 32
      trueExcessTurns            = 12   (the open-ground metric said 31)
  20 turns are unavoidable, 12 are ours. That is the honest target for any future straightness
  work, and it replaces the retired "63% of routes over-turn" figure entirely.

  #24 stays closed on the better metric too: SHAPE worsened 249, improved 14, max blows out to 37.

  METHOD NOTE worth keeping: the gate caught a real problem even though the gate was the broken
  part. It forced a specific pair to be checked against ground truth instead of a number being
  trusted. A self-check that never fires is worse than none.

## D-0152 - Terrain completeness measured (tileSetting bit histogram)
Added a Q3 section to LiveFlagCrossTab: it records the raw tileSetting per covered
tile (putIfAbsent BEFORE the bit-0 filter, so there is no selection bias) and buckets
two sets - TARGET (live blocks all four AND not terrain-marked AND no wall) against
CONTROL (live blocks zero edges AND no wall). Anti-vacuous guard included.
RESULT: target 10116 / control 56343; missing tileSetting 0 in BOTH (non-vacuous).
  bit 4 (0x10): target 10.1% vs control 0.2%   delta +9.9pp   ~44x enrichment
  bit 2 (0x04): target  8.7% vs control 2.7%   delta +6.0pp   ~3.2x
  bit 3 (0x08): target  1.0% vs control 0.1%   delta +0.9pp   ~10x
  bit 1 (0x02): target  0.8% vs control 0.3%   delta +0.5pp   ~2.7x
  bits 5/6/7: zero in both sets.
DECISIVE NEGATIVE: 79.4% of target tiles carry tileSetting 0x00 (blank). At most
20.6% of the completeness gap is explainable from the terrain byte at all, so the
terrain byte is NOT where the remaining gap lives. No rule auto-selected, by design.
NOTE: the terrain rule marks on (tileSetting AND 1) != 0 - the code SKIPS when bit 0
is clear. Earlier shorthand had this inverted. bit 0 reading 0% in both sets is
definitional, not a defect.

## D-0153 - lcl-ssh truncation fix is INCOMPLETE for space-containing paths
Post-restart A/B on mythpc - same file, same host, same session:
  C:/Users/drews/c2tmp/spacefree_test.pak   -> 453682 bytes, SHA MATCH
  C:/Users/.../My Games/.../data.pak        -> 204800 bytes, TRUNCATED
The -O legacy-SCP flag is applied conditionally so it does not regress paths that
contain spaces; that branch still rides SFTP and still cuts at exactly 204800.
WORKAROUND: stage to a space-free path on the remote, then download.
Recovered and byte-verified through that route: breathing.pak 12110937,
circulation.pak 7311819, tccc data.pak 453682, rhs-statusquo.rdb 815471,
rhs-cp01.rdb 563256.

D-0154 (2026-08-11) - locType 9 measured. Strong signal, NO RULE SHIPPED - a confound must be
  ruled out first. Approved by Mytharium as the next lever after terrain measured out at <=20.6%.

  Builder state before this work: shapeFor() case 9 returns LOC_TYPE_9_EDGES = {N,E,S,W}, i.e.
  block all four edges on every locType 9 placement, orientation ignored entirely. Note for
  future readers: locType1EdgesByOrientation() also returns LOC_TYPE_9_EDGES as its
  invalid-orientation fallback. That is NOT a bug - it is reusing the constant as "all four" -
  but the name is misleading and cost a minute to rule out.

  Added a Q4 locType 9 table to LiveFlagCrossTab (field, populate branch, report call - 3
  anchors, 15 lines). The interpretation rule was written into the report text BEFORE the
  numbers were seen, deliberately, so the conclusion could not be fitted to the data.

  RESULT, 1,281 single-placement locType 9 tiles vs the no-wall baseline
  (baseline N 26.6% E 26.7% S 25.6% W 25.6%):

      orient 0  (315)   N 28.9%   E 25.1%   S 100.0%   W 100.0%
      orient 1  (321)   N 42.1%   E 43.6%   S 100.0%   W 100.0%
      orient 2  (280)   N 27.9%   E 31.8%   S 100.0%   W 100.0%
      orient 3  (365)   N 39.2%   E 38.4%   S  99.7%   W  99.7%
      combined (1281)   N 34.9%   E 35.0%   S  99.9%   W  99.9%

  So: S and W are essentially ALWAYS blocked; N and E sit at or near baseline. That is a clean
  split - but it is the WRONG KIND of split, and that is the whole finding.

  WHY NO RULE WAS SHIPPED. The split is orientation-INVARIANT. All four orientations give the
  same S+W answer. Orientation is what distinguishes one diagonal from another, so a genuine
  geometric rule MUST vary with it - locType 0 does exactly that (orient0 -> W 96.9%,
  orient1 -> N 98.4%, orient2 -> E 99.0%, orient3 -> S 97.5%). An N/E-versus-S/W split that
  ignores orientation is a split along the MEASUREMENT AXIS, not along the geometry.

  Two readings remain open and this table cannot separate them:
    (a) REAL - locType 9 blocks S and W only. Then the all-four rule over-blocks N and E on
        1,281 placements and there is a genuine win here.
    (b) CONFOUND - and this is the one I would bet on. In the collision format S and W are
        DERIVED from the neighbour tile (S of T = N of T.y-1, W of T = E of T.x-1). Diagonal
        walls are corner fillers: they sit where two walls meet. If the S and W neighbours are
        themselves wall tiles, the derived edges read blocked for reasons that have nothing to
        do with the locType 9 object. The single-placement filter at crossTab() guards the
        CENTRE tile only (placements.size() == 1 for that key) - it says nothing about
        neighbours, so this confound passes straight through it.

  Reading (b) also explains why N and E sit at baseline: those are read from the tile own flags
  and are not contaminated by neighbours.

  THE DISCRIMINATING TEST, for whoever picks this up: for each locType 9 tile, check whether the
  S neighbour (y-1) and W neighbour (x-1) carry their own wall placements. Split the table into
  neighbour-clean and neighbour-contaminated groups. If S/W stay ~100% on the neighbour-CLEAN
  subset, reading (a) survives and a narrower rule is justified. If S/W collapse toward baseline
  once contaminated neighbours are removed, the all-four rule is correct and locType 9 is closed
  as a dead lever - the same honest outcome terrain gave.

  DO NOT ship "block S and W only" off this table. Under D-0120 (UNKNOWN defaults to BLOCKED)
  unblocking N and E on 1,281 placements is the dangerous direction: a wrongly-passable edge
  makes the router plan through a wall and the player simply stops walking.

D-0155 (2026-08-11) - locType 9 neighbour-clean split. MY CONFOUND HYPOTHESIS WAS REFUTED.
  locType 9 largely CLOSES as a lever - the existing all-four rule is substantially correct.

  D-0154 predicted that the Q4 result (S/W ~100%, N/E at baseline, orientation-invariant) was a
  neighbour-derivation confound: S and W are derived from the neighbour tile, diagonal walls are
  corner fillers, so neighbouring walls would inflate S/W. Q5 split the sample on exactly that.

  Q5a NEIGHBOUR-CLEAN (neither S(y-1) nor W(x-1) neighbour carries any wall placement):
      orient 0   (22)   N 68.2%   E 81.8%   S 100.0%   W 100.0%
      orient 1   (40)   N 90.0%   E 85.0%   S 100.0%   W 100.0%
      orient 2   (15)   N 80.0%   E 73.3%   S 100.0%   W 100.0%
      orient 3  (312)   N 38.5%   E 37.8%   S  99.7%   W  99.7%
      combined  (389)   N 47.0%   E 46.5%   S  99.7%   W  99.7%

  Q5b NEIGHBOUR-CONTAMINATED (892 tiles): S 100.0%  W 100.0%  N 29.6%  E 29.9%
  no-wall baseline: N 26.6%  E 26.7%  S 25.6%  W 25.6%

  VERDICT 1 - THE CONFOUND IS DEAD. S and W hold at 99.7% on 389 neighbour-CLEAN tiles, far
  above the ~40-tile floor the pre-stated rule required. Neighbour walls are NOT what was
  producing the 100%. locType 9 genuinely blocks S and W. Blocking them is correct and must stay.

  VERDICT 2 - AND THE SPLIT ALSO KILLED THE WIN. The interesting part is what the filter did to
  N and E. On contaminated tiles N/E sit at baseline (29.6/29.9 vs 26.6/26.7). On CLEAN tiles
  they jump to 47% combined, and for orientations 0/1/2 to 68-90%. So N and E ARE substantially
  blocked on locType 9 after all - the Q4 reading of "N/E are free" was itself the artifact,
  produced by the contaminated majority (892 of 1,281) swamping the clean sample.

  So the honest position on the all-four rule: S/W proven correct; N/E supported on orientations
  0/1/2 (68-90%, but n = 22/40/15, at or below the pre-stated floor - NOT conclusive on its own);
  N/E weak on orientation 3 (38.5/37.8 vs 26.6 baseline, only +12pp, but n = 312 so the weakness
  is real and not noise). Nothing here justifies unblocking any edge. Under D-0120 unblocking is
  the dangerous direction, and +12pp is nowhere near the +70pp separation locType 0 gives.

  ONE NARROW LEAD LEFT, deliberately not taken: orientation 3 alone, N and E, 312 clean tiles at
  ~38%. That is the only cell with both a large sample and a low rate. If anyone revisits
  locType 9, that is the single question - and it needs its own 2,248-edge proof run, not this
  table. It is a small prize: 312 tiles x 2 edges in six regions.

  STRUCTURAL NOTE worth keeping: the clean/contaminated split is wildly unbalanced by
  orientation. Clean is 80% orientation 3 (312 of 389); contaminated is overwhelmingly
  orientations 0/1/2. Orientation 3 diagonals evidently do not sit against S/W walls the way the
  others do. Any future locType 9 sampling must not treat the orientations as interchangeable.

  METHOD NOTE - I got this wrong and the measurement caught me. D-0154 argued that
  orientation-invariance proved the result was an artifact. There WAS a confound distorting the
  table, so the suspicion was worth acting on, but my conclusion (that the whole S/W signal was
  false) was wrong. Splitting the sample was the right move and it refuted my own hypothesis in
  both directions at once: the confound was not driving S/W, and it WAS hiding real N/E blocking.
  Pre-stating the interpretation rule is what made this readable instead of arguable.

D-0156 (2026-08-11) - Waypoint coordinate text boxes shipped. Requested by Mytharium.

  Waypoints #1-#5 can now be typed as "x,y,plane" in the Waypoint Settings config section
  instead of only being placed by right-clicking a tile.

  WHY IT WAS SMALL: the storage already existed. Positions were ALREADY persisted to RuneLite
  config as "x,y,plane" under keys waypoint1Position..waypoint5Position in group "drewshelper",
  encoded/decoded by WaypointPositionCodec, and onConfigChanged already detected those keys.
  Declaring them as String @ConfigItem entries with the SAME keyName makes RuneLite render text
  boxes bound to the existing storage - no new persistence, no Swing panel, no sidebar.

  THE ONE REAL GAP that had to be closed: onConfigChanged only refreshed markers and marked the
  route dirty. It never re-read the new string into the in-memory waypoints[] array, so a typed
  coordinate would have updated the box and the config and moved nothing. The handler now decodes
  the current value and applies it through setWaypoint(), which means a typed coordinate gets the
  SAME traversability snapping a right-click gets.

  LOAD-BEARING DETAIL, do not "simplify" it away: the equality check before calling setWaypoint is
  not an optimisation. setWaypoint writes config, which fires another ConfigChanged. Without the
  guard that is an infinite loop. Snapping is idempotent, so the second pass compares equal and
  the recursion stops after one extra hop.

  DEFECT FOUND AND FIXED IN REVIEW: isWaypointPositionConfigKey only checks the prefix and suffix,
  so a key like waypoint9Position passes it but resolves to index -1. The delegated code indexed
  waypoints[-1] directly, which would throw inside an event handler. Guarded with an index >= 0
  check before any array access.

  ENCODING TRAP worth recording: these two files have DIFFERENT line endings.
      DrewsHelperConfig.java  = LF   (0 CRLF)
      DrewsHelperPlugin.java  = CRLF (2,888 after the change)
  Normalising either one rewrites the whole file in git and buries the real diff. Check per file,
  never per repo.

  VERIFIED: clean test build SUCCESSFUL, 177 tests, 0 failures, 23 suites. SHA match on both
  transfers. Config 16,768 bytes LF-only; Plugin 104,031 bytes CRLF-only; braces balanced; no BOM.

  NOT DONE - unit tests for waypointPositionIndex did NOT ship. The delegation only staged the two
  main source files, so the worker could not see the real test tree and wrote its assertions into
  an unrelated test class that was never uploaded. The feature is covered by compile plus the 177
  existing tests and by direct code review, but the three specific assertions I specified
  (index 0..4 for valid keys, -1 for colour keys / null / empty / out-of-range) are still owed.
  My delegation setup was at fault, not the worker.

  TOOLING NOTE: the codex output contract reported FAIL with marker-absent even though the marker
  was present 3x in the plugin. CODEX_EXPECT_MARKER appears to require the marker in EVERY path
  listed in CODEX_EXPECT_PATHS, and this marker only ever belonged in one of the two files. Pair a
  single-file marker with a single expected path, or the contract false-fails.

D-0157 (2026-08-11) - Fresh Falador capture: 74.5% of known over-blocking now fixed. But the
  capture CANNOT prove the orientation-3 change is safe, and that was the stated purpose.

  CAPTURE (Mytharium, 6 waypoints + Falador castle all three floors):
      drews-live-flags.txt    881,752 B   55,077 rows   13 scenes
      drews-map-validate.txt  749,436 B   13,148 rows   (the proof source)
  Proof-region coverage, percent of each 64x64 region inside a captured scene:
      region   plane0  plane1  plane2
      45_51     79.7%   46.9%   46.9%      <- waypoint 1 was unreachable; he stood nearby
      45_52     87.5%   53.7%   53.7%
      46_51      100%   74.2%   74.2%
      46_52      100%    100%    100%      <- Falador castle, all three floors
      47_51      100%   15.2%   15.2%
      47_52      100%   60.9%   60.9%
  46_52 at 100% on all three planes is the one that mattered - orientation 3 is indoor geometry.

  PROOF RESULT after swapping tools/route-a-live-mismatches.txt to the new capture:
      proof edges parsed    13,148     (was 2,248 - a 5.8x larger ground-truth set)
      passable in v2          9,799
      door in v2                  1
      still blocked in v2     3,348
      outside built regions       0
      PERCENTAGE FIXED        74.5%
      ROUND TRIP OK 24 regions  (selector auto-expanded from 6 - he walked further than the
                                 six target regions, so the build covers everything he touched)
  Do NOT compare 3,348 against the old 753. Completely different edge sets. The comparable
  figure is the fraction of v1 over-blocking that v2 fixes: 66.1% before (1,485/2,248) vs
  74.5% now (9,799/13,148) - and even that is across different ground.

  THE LIMITATION, and it is mine. The whole reason for the trip was to prove locType 1
  orientation 3 (which UNBLOCKS 738 placements) does not make the router plan through walls.
  The proof file cannot answer that. DrewsHelperMapValidator.Kind has both directions and the
  validator constructs both (lines 225, 232), but BOTH sinks discard the dangerous one:
      DrewsHelperPlugin.java:427  logger      if (kind != OURS_BLOCKS_LIVE_OPEN) continue;
      DrewsHelperPlugin.java:501  file writer if (kind != OURS_BLOCKS_LIVE_OPEN) continue;
  The comment at line 422 says so outright: "Only the we-block-but-the-game-allows half is
  listed." Zero OURS_OPEN_LIVE_BLOCKS rows exist in any log, so it is not recoverable from the
  run. Row cap is 50,000 and only 13,148 were written, so nothing was truncated - the omission
  is by design, not by overflow.

  NO RE-WALK IS NEEDED. drews-live-flags.txt records the raw blocked mask for every covered
  tile, which is complete ground truth in BOTH directions (absence inside the covered bound
  means passable, per the writer comment at lines 450-455). The dangerous set can therefore be
  computed offline: v2 says passable AND live says blocked. That is a cross-tab style pass over
  data already on disk, not another trip.

  METHOD NOTE: I sent Mytharium in-game for a proof the instrument does not record. I should
  have read the writer before writing the route, the same way reading the trigger first is what
  caught the default-off Validate Map Data gate. Check what a tool PERSISTS, not just what it
  computes, before designing a capture around it.

D-0158 (2026-08-11) - Dangerous-direction pass built and run. ORIENTATION 3 IS EXONERATED.
  A much larger finding surfaced alongside it and is NOT yet trustworthy as an absolute number.

  WHAT IT DOES. Post-build read-only pass in CollisionMapBuilder comparing the built v2 bits
  against the live capture, classifying every compared edge into exactly one bucket:
      DANGEROUS      v2 passable AND live blocked   <- router plans through a wall (D-0120)
      DOOR_SHUT      v2 door     AND live blocked   <- NOT dangerous, door was simply closed
      AGREE_BLOCKED / AGREE_OPEN / OVERBLOCK
  Two design choices that matter:
    - Only NORTH and EAST are compared. S/W are derived from the neighbour tile, so comparing
      them would count the same physical edge twice and let a neighbour contaminate this tile.
      That exact confound produced a false locType 9 result earlier the same night.
    - The covered bound is EXCLUSIVE. The final row/column has no neighbour to consult and was
      never measured; treating absence there as passable would invent a ring of false truth.
  The 3x factor and the 30-edge sample floor are hard-coded constants, fixed before the run, and
  the interpretation rule is printed into the report above the verdict.

  RESULT:
      comparedEdges          148,662        outsideBuiltRegions    0
      DANGEROUS               28,122        DOOR_SHUT             95
      AGREE_BLOCKED           15,432        AGREE_OPEN       102,411        OVERBLOCK   2,602
      dangerousRateAll        18.917%
      orient-3 tile count        738        orient-3 compared edges    422
      orient-3 dangerous          42        dangerousRateOrient3    9.953%
      verdict: EXONERATED - no worse than the map average, the rule stays

  ORIENTATION 3 VERDICT STANDS. Its danger rate is roughly HALF the surrounding rate, on a
  like-for-like population, and the absolute count is 42 edges across 738 placements. The
  D-0120 concern recorded in the CollisionMapBuilder comment at lines 92-100 - that unblocking
  those edges would route players into walls - is measured and does not hold. Do not revert it.

  DO NOT QUOTE 18.917% AS A MAP-WIDE DANGER RATE. The arithmetic gives the reason away:
  live unique covered tiles 74,331 x 2 edges = 148,662 = comparedEdges EXACTLY. The pass
  iterated only tiles PRESENT IN THE CAPTURE FILE, and the dump only writes a row when a tile
  has at least one blocked edge. So the population is selected for "live blocks something here".
  The rate is therefore inflated by construction. The COUNT (28,122) is real; the RATE is not a
  map-wide figure.

  The orientation-3 COMPARISON survives that bias because both rates are drawn from the same
  selected population, and a tile with no live-blocked edge cannot contribute to the numerator
  either way. Excluding those tiles changes both rates equally.

  TWO REASONS 28,122 IS ALSO AN OVERSTATEMENT, both measurable and neither yet measured:
    1. DOORS. v2 wrote only 312 door edges in 24 regions and DOOR_SHUT came back at just 95.
       Falador castle alone should produce more than that. A door v2 failed to classify as a
       door, standing closed at capture time, lands in DANGEROUS and is not a routing bug.
    2. OBSERVATION CONFLICTS. The capture reports 7,180 conflicting north observations and
       7,138 east - the same tile seen with different states in different scenes, which is what
       doors opening and closing during the walk looks like. That is ~14k conflicted
       observations against 28k dangerous edges; the overlap is unmeasured.
  The example DANGEROUS rows are also a contiguous run down x=2888 (every N and E from y=3272
  to y=3286), which reads like a systematic block rather than scattered errors and deserves
  looking at directly before anyone treats the number as a bug count.

  NEXT STEP IF THIS IS PURSUED: split DANGEROUS by whether the tile carries a door-capable
  placement, and by whether the tile had conflicting observations. Those two filters should be
  applied before the number is quoted anywhere. Same discipline as the locType 9 neighbour-clean
  split, which turned a headline 1,281-placement "win" into nothing once the confound was removed.

D-0159 (2026-08-11) - DANGEROUS split by cause. MY DOOR HYPOTHESIS WAS WRONG. A sharper lead
  replaced it and it is NOT yet proven.

  RESULT, splitting the 28,122 DANGEROUS edges (assertion printed OK, 28122 == 28122):
      DANGEROUS_DOOR_CAPABLE      52   (0.2%)
      DANGEROUS_CONFLICTED     5,516   (19.6%)
      DANGEROUS_UNEXPLAINED   22,554   (80.2%)
  Door-capable locType histogram: locType 10 (IGNORED by shapeFor) 30, locType 0 (HANDLED) 22.
  So "door-capable on IGNORED locType" is 57.7% OF 52 - the ignored-locType gap is real but the
  entire door population is 0.2% of the problem. It explains nothing.

  I PREDICTED the 312-door-edge count meant a door-classification gap accounting for a large
  share of the 28k. It does not. 52 edges. That prediction is dead and should not be revived;
  the ignored-locType door gap is a genuine but tiny defect worth its own small ticket, nothing
  more.

  CONFLICTS are real but partial: 5,516 edges, 19.6%, from 5,783 north-conflicted and 5,828
  east-conflicted tiles. Those are doors opening and closing during the walk. Correctly excused.
  Axis separation held - a north edge is only excused by a north conflict.

  THE NEW LEAD, evidence-backed but UNPROVEN. Every example DANGEROUS_UNEXPLAINED edge sits at
  x=2888, y=3272..3286, every N and every E. And 2888:3272:0 is EXACTLY a scene base from the
  capture scene list. So the unexplained edges cluster on the WEST column and SOUTH row of a
  loaded scene - the scene ORIGIN corner.

  Hypothesis: the client marks the outer border of the loaded 104x104 scene as blocked because
  it has no data beyond it, not because a wall exists. v2 correctly says passable, live says
  blocked, and the edge lands in DANGEROUS. This is the SAME CLASS of artifact the covered bound
  already guards - but the covered bound only excludes the NORTH row and EAST column (those edges
  need a neighbour that is off-scene). It does nothing about the WEST column and SOUTH row, whose
  own flags are border-affected.

  THE DISCRIMINATING TEST, for whoever picks this up: bucket the DANGEROUS rate by Chebyshev
  distance from the nearest scene border. Pre-state the rule before running it:
    - if the rate is concentrated in the first few rings and falls off sharply toward the scene
      interior, the border artifact is confirmed, the comparison needs a margin on ALL FOUR
      sides, and the 28k headline collapses the way the locType 9 headline did.
    - if the rate is roughly flat across distance, the border is NOT the cause and there are
      genuinely ~22k wrongly-passable edges, which is a serious map defect needing real work.
  Do not quote 28,122 or 22,554 as a defect count until that comes back.

  METHOD NOTE: this is the third time tonight a confident structural hypothesis died on contact
  with a measurement - SHAPE ranking, the locType 9 neighbour confound, and now doors. In all
  three the measurement was cheap and the hypothesis was expensive to act on. The pattern worth
  keeping is not the hypotheses, it is that each one was written down as a falsifiable split with
  its interpretation fixed in code BEFORE the run.

D-0160 (2026-08-11) - Border-distance histogram. Verdict INCONCLUSIVE, and that is the correct
  answer: the border effect is REAL and large, but it explains only about a third.

  maxBorderDistance (the primary metric - best observation a tile ever got):
      bucket   comparedEdges   DANGEROUS   rate      DANGEROUS_UNEXPLAINED
      0             3,952        3,534    89.42%          3,530
      1             3,904        1,890    48.41%          1,866
      2             3,856        1,831    47.48%          1,799
      3             3,808        1,843    48.40%          1,794
      4             3,760        1,122    29.84%          1,064
      5-9          18,080        2,069    11.44%          1,619
      10-19        32,626        3,686    11.30%          2,804
      20+          78,676       12,147    15.44%          8,078

  THE BORDER ARTIFACT IS REAL. The outermost ring is 89.4% dangerous against a 15.4% interior -
  a 5.8x effect, and rings 0-4 are all elevated (29-89%) against an ~11% mid-field. That is not
  subtle and it is not noise. The mechanism stands: the client marks the outer border of the
  loaded scene as blocked because it has no data past it, and the existing covered bound only
  guards the north row and east column, leaving west and south exposed.

  BUT THE VERDICT IS INCONCLUSIVE, BY THE RULE I FIXED BEFORE THE RUN, and it is right to be:
      rate test:  BORDER(0-2) = 7,255/11,712 = 61.9% vs INTERIOR 15.44% = 4.01x   PASS (>= 3x)
      share test: BORDER(0-2) holds 7,195 of 22,554 UNEXPLAINED = 31.9%           FAIL (< 40%)
  The effect size is overwhelming; the coverage is not. Rings 0-4 together still only account
  for 10,053 of 22,554 unexplained (44.6%). Roughly 12,500 unexplained edges sit at distance 5
  or more and the border cannot explain them.

  A SECOND ANOMALY worth chasing, visible only because the buckets were printed: the deep
  interior (20+) has a HIGHER dangerous rate (15.44%) than the mid-field 5-9 (11.44%) and 10-19
  (11.30%). If the border were the only artifact the curve should fall monotonically and flatten.
  It does not - it rises again in the deep interior. Something is concentrated far from scene
  edges. Building interiors are the obvious candidate (Falador castle sits deep inside its
  scene), but that is a hypothesis, not a finding.

  THE MIN-VS-MAX CHOICE WAS MATERIAL, not pedantry. 69,448 of 148,662 compared edges (47%) have
  minBorderDistance != maxBorderDistance, because tiles sit inside several of the 13 scenes at
  once. Using min would have put 9,822 edges in ring 0 instead of 3,952 - inflating the border
  attribution roughly 2.5x and manufacturing the CONFIRMED verdict I was expecting. Always use
  the best observation a tile ever received, and print both so the disagreement is visible.

  WHAT IS ACTIONABLE NOW: excluding rings 0-2 from the comparison is justified on the rate
  evidence alone (4x separation) and would remove ~7,200 false dangerous edges. That is a fix to
  the MEASUREMENT, not to the map, and it should be done before any dangerous count is quoted.
  It does not close the question - about 12,500 edges would remain.

  METHOD NOTE: this is the fourth structural hypothesis tested tonight and the first to come
  back partially true. The pre-stated two-part rule is what made that visible - a single
  rate test would have printed CONFIRMED at 4x and I would have declared the 28k dead. The
  share test caught that a big effect on a small slice is not an explanation.

SESSION CLOSE 2026-08-11 (overnight, ~02:00-06:40). Drew's Helper collision/routing.

  SHIPPED AND VERIFIED
    - Straightness yardstick CERTIFIED. Self-check gate rewritten from an assumption to a proof
      (walk the constant-direction line with the BFS own canMove). 20 failures -> 0, and the
      turn distributions came out byte-identical, proving gate-only change with no metric drift.
      Honest headline: 12 avoidable turns out of 32, not the retired "63% of routes over-turn".
    - Backlog #6 closed: v2 zip builds into gitignored build/ and is untracked. D-0121.
    - locType 1 orientation rule holds. The 2,248-edge proof passed, and the fresh 13,148-edge
      proof puts v2 at 74.5% of known over-blocking FIXED (was 66.1% on the smaller sample).
    - Five waypoint coordinate text boxes shipped into the plugin config (x,y,plane, blank
      clears, snaps to standable like a right-click). 177 tests green.
    - Fleet-wide SCP space-path truncation reported to Architect with a clean A/B.

  MEASURED AND CLOSED NEGATIVE - four hypotheses tested, three dead, one partial
    1. Terrain completeness: <=20.6% explainable. 79.4% of gap tiles carry a BLANK terrain byte.
       Dead as a lever. (I had named it the biggest lever the night before. It was not.)
    2. locType 9 N/E "win": REFUTED by the neighbour-clean split. On 389 clean tiles S/W hold at
       99.7% and N/E are 47-90% blocked. The all-four rule was right. Nothing shipped.
    3. Door-classification gap: REFUTED. 52 of 28,122 dangerous edges are door-capable (0.2%).
       The ignored-locType part is real but tiny (30 edges on locType 10) - its own small ticket.
    4. Scene-border artifact: PARTIAL / INCONCLUSIVE by the pre-stated rule. Effect is real and
       large (ring 0 = 89.4% dangerous vs 15.4% interior, 4.01x) but covers only 31.9% of the
       unexplained edges. This is the first confound with a FIX attached rather than a retraction.

  ORIENTATION 3 EXONERATED. 738 tiles, 422 compared edges, 42 dangerous = 9.953% against an
  18.917% surrounding rate - roughly HALF. The warning written into CollisionMapBuilder lines
  92-100 ("first thing to revert if the proof numbers worsen") is measured and does not hold.
  Do not revert it.

  WHERE THE 28,122 DANGEROUS EDGES ACTUALLY STAND (nothing here is a quotable defect count yet):
      DANGEROUS_DOOR_CAPABLE       52   0.2%    real, tiny
      DANGEROUS_CONFLICTED      5,516  19.6%    doors opened/closed mid-walk, correctly excused
      DANGEROUS_UNEXPLAINED    22,554  80.2%    of which ~7,200 sit in border rings 0-2
  And 18.917% is NOT a map-wide rate: 74,331 unique covered tiles x 2 = 148,662 = comparedEdges
  exactly, so the population is selected for "live blocks something here".

  SECOND ANOMALY, UNCHASED. The deep interior (border distance 20+) has a HIGHER dangerous rate
  (15.44%) than the mid-field (11.44% at 5-9, 11.30% at 10-19). If the border were the only
  artifact the curve should fall and flatten; instead it rises again. Building interiors are the
  obvious candidate since Falador castle sits deep inside its scene. Hypothesis, not a finding.

  CAPTURE. Mytharium ran the six-region route plus all three castle floors: 881,752 bytes,
  55,077 rows, 13 scenes. Four regions 100% on the ground; 46_52 (the castle) 100% on ALL three
  planes. Waypoint 1 was unreachable and he stood nearby - cost 45_52 ~12% of ground tiles,
  immaterial. Old capture parked as drews-live-flags.PARKED_20260811_032617.txt.

  MY ERRORS THIS SESSION, recorded so they are not repeated:
    - I sent Mytharium in-game for a proof the instrument does not persist. The validator
      DETECTS OURS_OPEN_LIVE_BLOCKS and both sinks discard it (plugin lines 427 and 501). Read
      what a tool WRITES, not just what it computes, before designing a capture around it.
    - I called doors my prime suspect on a plausibility argument (312 door edges across 24
      regions "must" be wrong). It was 0.2%.
    - I named terrain the biggest remaining lever the previous night; it measured out smallest.
    - A CODEX_EXPECT_MARKER false FAIL was my brief authoring fault - the marker must appear in
      EVERY listed expected path. Use one path per marker.
    - Codex unit tests for waypointPositionIndex never shipped because their file was not in my
      staging set. Stage every file the brief implies, including tests. STILL OWED.

D-0161 (2026-08-11) - Border-ring exclusion SHIPPED and verified. Step 2 of the session plan.

  WHAT CHANGED (CollisionMapBuilder.java, 96471 -> 99219 B, sha256 CB99F70D... then re-edited):
  Edges whose tile has maxBorderDistance <= BORDER_MAX_DISTANCE (0..2) are now withheld from every
  headline counter in the dangerous-direction comparison. The threshold REUSES the existing
  BORDER_MAX_DISTANCE constant rather than adding a new one, so the exclusion and the verdict rule
  that justifies it can never drift apart.
  splitDangerousEdge was split into a pure classifyDangerousEdge (no counter writes) plus the
  existing recording wrapper, so an excluded edge can still be classified for the histogram
  without contaminating the door-capable/conflicted/unexplained counters.
  NO BLOCKING RULE WAS TOUCHED. shapeFor, markSolid, markDoor and recordDoorCapablePlacement are
  byte-identical. This changes what the comparison COUNTS, not what the builder BLOCKS.

  RESULTS - every number was predicted from the histogram BEFORE the run and all hit exactly:
      borderExcludedEdges                 11712   (predicted 11712)
      borderExcludedDangerous              7255   (predicted 7255)
      borderExcludedDangerousUnexplained   7195   (predicted 7195)
      comparedEdges            148662 -> 136950   (predicted 136950)
      DANGEROUS                 28122 -> 20867    (predicted 20867)
      DANGEROUS_UNEXPLAINED     22554 -> 15359    (predicted 15359)
      dangerousRateAll        18.917% -> 15.237%  (predicted 15.237%)
  Both closure assertions hold: the five outcome buckets sum to comparedEdges (136950), and the
  three-way DANGEROUS split sums to DANGEROUS (20867).
  Both border histogram tables are BYTE-IDENTICAL to the pre-change run - zero diff lines across
  all 16 rows. The evidence that justified the exclusion survives the exclusion.

  20867 IS THE FIRST DANGEROUS COUNT WORTH QUOTING. The old 28122 headline was 26% capture
  artifact.

  BUG FOUND AND FIXED MID-TASK - see D-0126. The border verdict flipped INCONCLUSIVE -> CONFIRMED
  because its share test divided a histogram numerator (which still counts the border) by a
  headline denominator (which no longer does). It was confirming the border artifact using its own
  exclusion. Denominator repointed at the histogram total; verdict is back to INCONCLUSIVE and now
  matches the committed baseline exactly. My work order caused this - it said "withheld from every
  count below" without checking which downstream ratios read those counts.

  TWO PREVIOUSLY-QUOTED NUMBERS MOVED and both conclusions survive:
    - orientation 3: 9.953% vs 18.917% became 9.330% vs 15.237%. Still roughly 0.61x the map
      average, still EXONERATED on the pre-stated rule. The rule stays.
    - the ignored-locType door gap is 24 edges, not 30. Six of the original 30 were border
      artifacts. Item 5 of the next-work list should read 24.

  NEW SIGNAL, not chased: DANGEROUS_CONFLICTED barely moved (5516 -> 5465). Only 51 of 5516
  conflicted edges lived in rings 0-2, so live-capture conflicts are almost entirely an INTERIOR
  phenomenon, not a border one. Their share of DANGEROUS rose 19.6% -> 26.2% purely because the
  denominator shrank. Worth remembering when chasing the deep-interior rise.

  REMAINING: 15359 unexplained edges. The border cannot explain them - it has been removed.

D-0162 (2026-08-11) - Interior hypothesis: REFUTED as stated, but the proxy was too coarse to
  have tested it. Step 3 of the session plan. See D-0127.

  WHAT WAS ADDED (CollisionMapBuilder.java, 99,607 -> 119,970 B, sha256 EE909273...197AA3B9):
  A pure-addition interior measurement pass. New BuildStats.placementTileKeys records EVERY
  placement on a valid plane - populated before the locType filter, so it sees placements
  shapeFor() ignores. Three mutually exclusive buckets on post-exclusion edges only:
      UPPER            plane > 0
      UNDER_STRUCTURE  plane 0 with a placement directly above on plane 1
      OUTDOOR          everything else
  Thresholds REUSE the border hypothesis constants (3.0x / 1.5x / 40% / 500-edge floor) on
  purpose - this hypothesis had to clear the same bar. Rule and denominators printed above the
  numbers, per D-0126. Zero lines removed from the file; every step-2 value verified unchanged.

  RESULT - REFUTED on all three reads:
      bucket            comparedEdges  DANGEROUS   rate      UNEXPLAINED  share
      UPPER                    60,452     7,493   12.395%         5,454  35.5%
      UNDER_STRUCTURE          10,332     1,444   13.976%         1,044   6.8%
      OUTDOOR                  66,166    11,930   18.030%         8,861  57.7%
      INTERIOR (combined)      70,784     8,937   12.626%         6,498  42.3%
  rate(INTERIOR) is 0.70x rate(OUTDOOR) - not merely below the 3.0x confirm bar, below 1.0.
  Interiors as defined are SAFER than outdoors. All four closure assertions passed.

  BUT THE PER-REGION TABLE POINTS THE OTHER WAY. Region 46_52 - named in advance as the
  coverage-controlled read because it is the only region captured 100% on all three planes:
      46_52 plane 0    8,192 edges     683 dangerous    8.337%
      46_52 plane 1    8,120 edges   1,771 dangerous   21.810%
      46_52 plane 2    8,120 edges   1,915 dangerous   23.584%
  Same region, same capture, upstairs ~2.8x worse than its own ground floor. Meanwhile regions
  45_51, 45_52, 46_51 and 47_51 score 3.6-10.4% on their upper planes.
  Reason: comparedEdges per region are IDENTICAL across planes (45_51: 3330/3330, 46_52:
  8120/8120), which proves the capture dumps the whole scene grid per plane rather than only the
  walked footprint. Most upper-plane edges are therefore open sky over open ground, and they
  diluted the UPPER bucket until it inverted.

  LEADING HYPOTHESIS, NOT A FINDING: the real signal is OCCUPIED upper floors, not "plane > 0".
  Untested. The sharper proxy is now cheap because placementTileKeys already exists - split UPPER
  into "has a placement on its own tile" versus "does not". That is the next discriminating run.

  NOT CLAIMED: that empty upper-plane tiles are the dilution. It is consistent with the identical
  per-plane edge counts and the 3-6% rates, but it has not been measured directly. Measure before
  quoting it.

  UNCHANGED AND RE-VERIFIED after this addition: comparedEdges 136,950, DANGEROUS 20,867,
  DANGEROUS_UNEXPLAINED 15,359, dangerousRateAll 15.237%, all three borderExcluded counters,
  DOOR_SHUT 93, AGREE_BLOCKED 14,219, AGREE_OPEN 99,238, OVERBLOCK 2,533, border verdict
  INCONCLUSIVE, and both border histogram tables.

D-0163 (2026-08-12) - Occupied-upper-floor split: verdict INCONCLUSIVE. Empty-sky claim from
  D-0162 is now MEASURED AND CONFIRMED. See D-0128.

  WHAT WAS ADDED (CollisionMapBuilder.java 119,970 -> 134,948 B, sha256 526F105A...7F5720EE):
  Pure addition, one line replaced (the record() call gained a parameter). New
  NEAR_STRUCTURE_RADIUS = 1 and isNearStructure(): a tile counts as near structure if any tile in
  the same-plane 3x3 block around it carries a placement. Deliberately NOT "placement on its own
  tile" - placements are walls and objects, so a room-interior floor tile has none of its own and
  that definition would have discarded exactly the population under test.
  UPPER is SUBDIVIDED, not replaced: UPPER_NEAR_STRUCTURE + UPPER_OPEN. Same thresholds as the
  border and interior hypotheses.

  RESULT - INCONCLUSIVE:
      bucket                comparedEdges  DANGEROUS    rate     UNEXPL   share
      UPPER_NEAR_STRUCTURE         15,886      4,388   27.622%    4,078   26.6%
      UPPER_OPEN                   44,566      3,105    6.967%    1,376    9.0%
      OUTDOOR (unchanged)          66,166     11,930   18.030%    8,861   57.7%
  vs OUTDOOR: 1.53x - above the 1.5x refute floor, far below the 3.0x confirm bar; share 26.6%
  fails the 40% bar. INCONCLUSIVE is the honest answer and it is what the report prints.
  Secondary read, explicitly NOT the verdict: UPPER_NEAR_STRUCTURE vs UPPER_OPEN is 3.965x.
  All three closure assertions passed (15886+44566==60452, 4388+3105==7493, 4078+1376==5454).

  THE EMPTY-SKY CLAIM IS NOW A FACT, NOT A HYPOTHESIS. Occupancy census, post-exclusion edges:
      plane 0    76,498 edges    91.73% near structure     (6,328 not)
      plane 1    30,226 edges    35.24% near structure    (19,574 not)
      plane 2    30,226 edges    17.32% near structure    (24,992 not)
  Plane 2 is 82.7% empty and plane 1 is 64.8% empty. D-0162 flagged this as "leading hypothesis,
  not a finding" and refused to bank it. It is now measured. It fully accounts for the D-0162
  inversion: UPPER was 73.7% empty sky sitting at 6.97%, which dragged the combined UPPER rate
  down to 12.4% and put it below OUTDOOR.
  Region 46_52 (the castle, the only region captured 100% on all three planes) is denser than the
  global picture exactly as expected: 90.82% / 51.75% / 40.20% near structure by plane.

  THE REAL TAKEAWAY, AND IT IS NOT ABOUT BUILDINGS: OUTDOOR alone holds 8,861 of 15,359
  unexplained edges - 57.7%. Occupied upper floors hold 4,078 (26.6%). No building-based story
  can explain the majority of this defect because the majority of it is not in buildings. Three
  structural hypotheses are now tested: border (partial, ~32% coverage), interiors (refuted),
  occupied upper floors (inconclusive, 1.53x). The unexplained edges are DIFFUSE, not clustered.
  That should redirect the next round away from "where are they" and toward "what rule is wrong".

  UNCHANGED AND RE-VERIFIED: comparedEdges 136,950, DANGEROUS 20,867, DANGEROUS_UNEXPLAINED
  15,359, dangerousRateAll 15.237%, all three borderExcluded counters, DOOR_SHUT 93,
  AGREE_BLOCKED 14,219, AGREE_OPEN 99,238, OVERBLOCK 2,533, border verdict INCONCLUSIVE, the
  UPPER / UNDER_STRUCTURE / OUTDOOR rows, and the combined INTERIOR verdict REFUTED.

D-0164 (2026-08-12) - Phase 0 ignored-placement adjacency: verdict INCONCLUSIVE, largest effect
  measured in this investigation, falsification control PASSED. See D-0129.

  WHAT WAS ADDED (CollisionMapBuilder.java 134,948 -> 161,015 B, sha256 FAF09A83...DB482B20):
  Pure addition; one line replaced (appendDangerousDirectionComparison gained a BuildStats param
  so the census can print set sizes). shapeFor() is BYTE-IDENTICAL - verified by md5 on the
  method body, f2f4eb7d6013c28c0db6b6e819730515 before and after. New sets
  sceneryPlacementTileKeys (locType 10/11) and otherIgnoredTileKeys (any type shapeFor ignores,
  excluding 10, 11 and GROUND_DECOR_LOC_TYPE 22), populated at the same site as
  placementTileKeys, before the locType filter. Ignored-ness is decided by calling
  shapeForHandlesLocType() rather than re-listing the handled cases, so the two can never drift.
  Adjacency tests BOTH endpoints of the edge - a placement on either side can block it.
  Fourth hypothesis, same thresholds as the previous three, no new constant.

  RESULT:
      bucket             comparedEdges  DANGEROUS  UNEXPL  unexplRate  share   OVERBLOCK  obRate
      ADJ_SCENERY               10,792      5,555   5,405     50.083%  35.19%        127  1.177%
      ADJ_OTHER_IGNORED          6,117      3,504   3,429     56.057%  22.33%        125  2.043%
      NOT_ADJACENT             120,041     11,808   6,525      5.436%  42.48%      2,281  1.900%
  ADJ_SCENERY vs NOT_ADJACENT: rateRatio 9.214x - THREE TIMES the 3.0x confirm bar - but share
  35.19% misses the 40% bar, so the pre-stated verdict is INCONCLUSIVE and that is what prints.
  Secondary read ADJ_OTHER_IGNORED vs NOT_ADJACENT: 10.313x, share 22.33%, also INCONCLUSIVE.
  All five closure assertions passed, including OVERBLOCK (127 + 125 + 2281 == 2533).

  OVERBLOCK CONTROL PASSED - the single most important line in this run. Predicted before the run:
  missing objects cannot cause overblock. Measured: ADJ_SCENERY 1.177% vs NOT_ADJACENT 1.900%,
  i.e. LOWER. A generic "clutter is hard" confound would have lifted both error directions.

  CONCENTRATION: 16,909 edges (12.3% of the compared population) carry 8,834 of 15,359 unexplained
  edges (57.5%). Recorded as arithmetic on two pre-stated buckets, explicitly NOT as a verdict -
  the union was not the test fixed in code beforehand.

  CENSUS: 11,541 unique scenery tiles (exactly matching 11,260 type-10 + 281 type-11 placements -
  no tile collisions) and 14,955 other-ignored tiles. ADJ_SCENERY is overwhelmingly plane 0
  (9,847 of 10,792); ADJ_OTHER_IGNORED is mostly planes 1-2 (4,757 of 6,117), consistent with the
  roof locTypes 17/18 living upstairs.

  LIMITATION, printed in the report: adjacency is not causation. A tile beside a tree is also a
  tile in a cluttered place. This rules the theory OUT cheaply; it cannot alone prove the objects
  are what block those edges. The overblock control is what raises it above bare correlation.

  UNCHANGED AND RE-VERIFIED: zero removed lines in the committed report diff. comparedEdges
  136,950, DANGEROUS 20,867, DANGEROUS_UNEXPLAINED 15,359, rate 15.237%, DOOR_SHUT 93,
  AGREE_BLOCKED 14,219, AGREE_OPEN 99,238, OVERBLOCK 2,533, all borderExcluded counters, border
  verdict INCONCLUSIVE, both histogram tables, all interior and occupancy rows and verdicts.

  NEXT: the union bucket (adjacent to ANY ignored placement, excluding type 22) as a PRE-STATED
  run. Then Phase 1 - read footprint and blocking flag off the object definition.

D-0165 (2026-08-12) - Union bucket CONFIRMED (bookkeeping) and Phase 1 solid-flag split
  CONFIRMED (the real evidence). See D-0130.

  CollisionMapBuilder.java 161,015 -> 185,127 B, sha256 4EB4B2A4...7017E6F8. shapeFor() body
  BYTE-IDENTICAL, md5 f2f4eb7d6013c28c0db6b6e819730515 before and after. 17 diff removals were a
  refactor hoisting tileKey(x,y,plane) into a local and reusing it - both placementTileKeys and
  locType1Orientation3TileKeys are still populated at the same site, verified by grep and by every
  dependent number being unchanged. Zero removed lines in the committed report diff.

  PART A - UNION, exactly as predicted before the run (a mismatch would have meant a bucketing
  bug; there was none):
      ADJ_IGNORED 16,909 edges / 9,059 dangerous / 8,834 unexplained / 252 overblock
      rate 52.244%, ratio 9.611x, share 57.517%, overblock 1.490% -> CONFIRMED
  The report prints, above that verdict, that it is arithmetic on two already-measured buckets and
  is NOT new evidence.

  PART B - THE SOLID-FLAG SPLIT, first time the builder has read any ObjectDefinition field other
  than getId():
      ADJ_SOLID_FLAGGED   14,760   8,956 dang   8,792 unexpl   59.566%   10.958x   share 57.24%
      ADJ_NONSOLID_ONLY    2,149     103 dang      42 unexpl    1.954%    0.360x   share  0.27%
      NOT_ADJACENT       120,041  11,808 dang   6,525 unexpl    5.436%
  ADJ_SOLID_FLAGGED: CONFIRMED on both criteria (10.958x >= 3.0x, 57.24% >= 40%).
  ADJ_NONSOLID_ONLY is BELOW baseline at 0.360x. The clutter confound had a clean chance to win
  and lost by ~30x. All eight closure assertions passed.

  FLAG DISCRIMINATION TABLE:
      getInteractType() != 0   14,760 edges   59.566%   10.958x
      isBlocksProjectile()     12,731 edges   59.603%   10.965x
      isObstructsGround()           4 edges    0.000%    0.000x
  interactType distribution over ignored non-decor placements: 0 = 3,363, 1 = 617, 2 = 22,554.
  Also counted: isBlocksProjectile true 21,013; isObstructsGround true 35; getWallOrDoor() != 0
  9,216; getBlockingMask() != 0 546.

  SECOND DEFECT COUNTED: 2,873 ignored non-decor placements have a footprint larger than 1x1. The
  builder treats every placement as one tile. Independent of solidity; own ticket.

  REPORT BUG TO FIX (small, mine): the footprint histogram prints its header and then
  "suppressed 18 footprint rows with fewer placements" WITHOUT printing the top-12 rows it says it
  is showing. The 2,873 count is correct and came from a separate line; the histogram itself
  emitted nothing. Fix before quoting any per-size breakdown.

  UNCHANGED AND RE-VERIFIED: comparedEdges 136,950, DANGEROUS 20,867, DANGEROUS_UNEXPLAINED
  15,359, rate 15.237%, DOOR_SHUT 93, AGREE_BLOCKED 14,219, AGREE_OPEN 99,238, OVERBLOCK 2,533,
  all borderExcluded counters, border verdict INCONCLUSIVE, both histogram tables, all interior /
  occupancy / adjacency rows and verdicts.

  NEXT: Phase 2 writes object blocking. FIRST change in this whole investigation that alters what
  the builder BLOCKS rather than what it counts. It can only ADD blocking, so a wrong version
  closes routes that currently work. Needs its own go/no-go, OVERBLOCK as the tripwire, and a
  route re-test. Do not bundle the footprint fix into it - one change at a time.
