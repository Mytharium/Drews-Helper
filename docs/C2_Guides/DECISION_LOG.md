# Decision Log

## D-0001: Drew Uses Shortest Path As The Solver For Now

Date: 2026-08-06

Drew's Helper currently treats Shortest Path as the authoritative pathfinder and uses plugin messages to request route telemetry. Drew should not claim that a route has rerouted unless Shortest Path's actual marker/path was recalculated.

## D-0002: Minigame Unlocks Are UI-Scanned Ground Truth

Date: 2026-08-06

Per-destination minigame teleport availability comes from the live Grouping/minigame UI, not from one global checkbox. `UNKNOWN` destinations stay usable until scanned. `AVAILABLE` and `LOCKED` statuses persist and refresh when the menu is opened again.

## D-0003: Hide Debug Counters From The Player Overlay

Date: 2026-08-06

The backend may keep scan/cache counters for debugging, but the normal overlay should only show the useful player-facing state: `Minigame Teleports: X/18 Unlocked`.

## D-0004: Exact Locked-Route Rerouting Needs A Solver Hook

Date: 2026-08-06

Shortest Path's current plugin-message bridge accepts start, target, and existing config overrides. It does not expose a per-transport blocked list. Exact rerouting around individual locked routes requires patching Shortest Path or adding a Drew-owned route solver.

## D-0005: Use `blockedTransportKeys` As The Exact Reroute Contract

Date: 2026-08-06

Drew remains a Shortest Path client rather than owning a full route graph. Exact locked-route rerouting uses a small Shortest Path fork patch: Drew sends `config.blockedTransportKeys` values like `teleportation_minigames:nightmare_zone`, and patched Shortest Path filters matching transports before building usable pathfinder edges. The stock Shortest Path jar ignores the unknown key, so exact rerouting is only active once the patched Shortest Path build is installed.

## D-0006: Stock Shortest Path Uses A Broad Minigame Fallback

Date: 2026-08-06

Until the patched Shortest Path build is active, Drew may force a real stock-jar reroute by escalating from exact `blockedTransportKeys` to `useTeleportationMinigames=false` when the same locked minigame route is posted again. This fallback is intentionally broad and must be described as blocking the whole minigame teleport category, not as exact Nightmare-Zone-only routing.

## D-0007: Drew Must Arbitrate Incoming Shortest Path Requests

Date: 2026-08-06

When Drew owns an active locked-route fallback, it must apply that policy to incoming `shortestpath/path` plugin messages before Shortest Path consumes them instead of only posting a competing request afterward. This includes target-bearing route requests and targetless config-only path refreshes, because Shortest Path replaces its static config override from any non-empty `config` map. Use high-priority `PluginMessage` handling to merge Drew's config overrides into the existing request, preserve the external request's target/start data, and suppress stale locked transport snapshots while the fallback signature is active.

## D-0008: Transport Telemetry Is Not A Route Target

Date: 2026-08-06

`shortestpath/transports` destinations are intermediate transport step destinations, not the final route target. Drew may restore/replay a route only from a real captured `shortestpath/path` target. If no real target is known, Drew must send config-only requests so Shortest Path reuses its own current target set.

## D-0009: Use A Custom Shortest Path Jar For Exact Locked Routes

Date: 2026-08-06

The stock Plugin Hub Shortest Path jar kept fighting Drew's overlay/fallback loop. The active RuneLite plugin-cache jar is now replaced with a C2-built fork from `Skretzo/shortest-path@8551e6016d053aa5930bb16485069a6997718da3` that consumes `config.blockedTransportKeys` and filters matching transports before path edges are built. Keep the stock jar backup outside `.runelite\plugins` to avoid duplicate plugin loading. Roll back by copying the backup jar over the active `shortest-path_*.jar` only if the custom jar fails to load or route correctly.

## D-0010: Drew Path Replaces Plugin Hub Shortest Path

Date: 2026-08-06

Supersedes D-0001 and D-0009 for active runtime. Myth clarified that the target is not a patched install of the Plugin Hub Shortest Path mod. The pathfinder source/resources are now vendored into `Drews Helper` and loaded by `gradlew.bat run` as visible plugin `Drew Path` alongside `Drew's Helper`. Keep the `shortestpath` plugin-message namespace for Quest Helper / Drew Helper compatibility, but keep Plugin Hub `shortest-path_*.jar` files out of `.runelite\plugins` while testing Drew Path. Exact locked-route filtering uses `blockedTransportKeys`; do not use the broad stock-jar `useTeleportationMinigames=false` fallback as normal behavior.

## D-0011: Drew's Shortest Path Is A Feature, Not A Second Addon

Date: 2026-08-06

Supersedes the visible-plugin part of D-0010. Myth clarified that there should be one visible RuneLite plugin: `Drew's Helper`. The vendored `shortestpath` source remains as the internal Drew's Shortest Path route engine, but `runelite-plugin.properties` and the dev launcher must load only `com.drewshelper.DrewsHelperPlugin`. `DrewsHelperPlugin` owns the route engine lifecycle, manually registers the internal engine's event subscribers, and keeps the `shortestpath` plugin-message namespace only as a compatibility wire for Quest Helper and existing Drew route requests.

## D-0012: Internal Route Overlays Must Be Lazy-Created

Date: 2026-08-06

When `shortestpath.ShortestPathPlugin` is loaded as an internal Drew's Helper feature, do not field-inject its overlays directly. Several overlays inject the route engine back for path state, so direct field injection creates a Guice construction cycle and prevents `DrewsHelperPlugin` from appearing in RuneLite's plugin list. The route engine should inject overlay providers, create overlay instances during its internal startup after construction is complete, and reuse those instances for removal.

## D-0013: Drew Owns The Transportation Config Language

Date: 2026-08-06

The vendored Shortest Path route engine may keep internal config keys for compatibility, but the visible RuneLite settings must use Drew's Helper language and grouping. Normal pass-through/paid/local travel belongs under `Transportation` with `Unlocked: ...` labels. Account-unlock, teleport, minigame, wilderness, seasonal, and POH systems belong under `Advanced Transportation`. Manual transport unlock toggles must be sent to the internal route engine even when `Hide Locked Teleports` is disabled; that setting only controls scanned unavailable-destination filtering such as locked minigame keys.

## D-0014: Baseline Travel Networks Are Not Frontend Unlocks

Date: 2026-08-06

Supersedes the broad "normal pass-through/paid/local travel belongs under `Transportation`" part of D-0013. Gates/passages, ordinary ships/ferries, charter ships, magic carpets, and minecarts are baseline route networks, not player unlock toggles, so Drew's Helper should keep them enabled internally by default while Drew's Shortest Path is active and not expose them as `Unlocked: ...` settings. Keep player-facing Transportation options for actual unlock/progress/preference choices such as agility/grapple shortcuts, boats/canoes, gliders, balloons, mushtrees, and quetzals. Do not expose a `Passenger Ships` setting; the internal `ships.tsv` data remains usable as ordinary ship/ferry travel.

## D-0015: Drew Transport Menus Use Basic / Advanced / Other

Date: 2026-08-06

Supersedes the remaining menu-shape parts of D-0013 and D-0014. The visible route transport sections are `Basic Transportation`, `Advanced Transportation`, and `Other Transportation`. Base Drew's Shortest Path transports are enabled internally without frontend unlock toggles: gates/passages, boats, ordinary ships/ferries, charter ships, magic carpets, minecarts, home teleports, teleport levers, fixed teleport portals, spellbook teleports, and minigame teleports. Scanned locked minigames still produce exact `blockedTransportKeys` so the solver avoids known locked minigame destinations even though minigames are base-on. `Hide Locked Teleports` controls Drew overlay warning visibility, not whether known locked minigames are sent to the solver as blocked.

## D-0016: The Internal Route Engine Must Not Expose Stock Settings

Date: 2026-08-06

Drew's Shortest Path is an internal feature, not a second plugin or a copied Shortest Path config panel. `ShortestPathPlugin` must stay hidden, and `DrewsHelperPlugin` must not provide `ConfigManager.getConfig(ShortestPathConfig.class)`. The internal engine should use runtime defaults plus Drew-owned override messages from `ShortestPathBridge`; visible route settings belong in `DrewsHelperConfig`.

## D-0017: Manual Route Targets Need Engine-Side Persistence

Date: 2026-08-06

Quest Helper route targets arrive as `shortestpath/path` messages, but manual right-click/shift-click targets are set directly inside the internal route engine. Drew's Helper must sync the active target from `ShortestPathPlugin` into `DrewsHelperSessionState` and must clear the saved target/snapshot when the internal route is cleared, otherwise logout/restart replay either has no real target or replays stale route text.

## D-0018: Hide Locked Teleports Is Route Policy

Date: 2026-08-06

Supersedes the hide-toggle behavior stated in D-0015. Drew should keep scanning and remembering minigame lock state regardless of the toggle, but `Hide Locked Teleports` controls whether those scanned locks are sent to the route engine as exact `blockedTransportKeys`. When enabled, known locked minigames such as `teleportation_minigames:nightmare_zone` are excluded and route/hint displays must move to the first available route step. When disabled, Drew keeps the scan cache but sends no blocked keys, and the active route must refresh so the base solver can use Nightmare Zone again if it prefers that path.

## D-0019: Drew-Origin Route Refreshes Bypass Plugin Messages

Date: 2026-08-06

Supersedes the Drew-owned route-refresh part of D-0007 and D-0018. Drew's own config changes, saved-target replays, and locked-route reroutes must call the internal route engine directly with the current Drew config override and `blockedTransportKeys`. Do not post Drew-origin `shortestpath/path` messages to the same event bus and rely on subscriber priority; that creates a race where the internal route engine can route with stale config while Drew's HUD later filters the telemetry. Keep `shortestpath/path` only as the external Quest Helper compatibility message and keep `shortestpath/transports` as route telemetry.

## D-0020: Manual Internal Routes Must Re-enter Drew Policy

Date: 2026-08-06

Manual right-click/shift-click routes are created inside the internal path engine, not through Drew's external `shortestpath/path` bridge. Saving the target is not enough: when Drew observes a changed internal target during gameplay, it must immediately replay that target through Drew's current config override and scanned locked-minigame keys. The hidden internal route config must default `postTransports=true`; otherwise the internal engine can draw map/minimap/ground tiles while Drew's HUD receives no transport telemetry.

## D-0021: Route Policy Changes Are Target-Stable Replays

Date: 2026-08-07

Changing Drew route policy must replay the active route even when the route target has not changed. This includes `Hide Locked Teleports` toggles and any config change that changes the internal route-engine override. Drew should track a stable signature for the active target plus override map, clear stale HUD telemetry when the visible config changes, and keep requesting the active target until the internal engine has accepted the current signature.

The HUD/highlighter availability contract is tied to `TeleportAvailabilityService`: while `Hide Locked Teleports` is enabled, locked minigame transports are removed from the primary route step list and the highlighter follows the first available minigame step. When the toggle is disabled, cached locked destinations remain cached but are treated as route-usable and highlightable.

## D-0022: Transport Telemetry Must Come From The Current Pathfinder

Date: 2026-08-07

Drew's HUD, locked-route list, and minigame UI highlighter must not depend only on the legacy `shortestpath/transports` event-bus message. The internal route engine should publish the same transport snapshot directly to Drew's Helper when the active pathfinder completes, then also post the legacy plugin message for compatibility.

Stale/cancelled pathfinder completions must be ignored by identity, because otherwise an old Nightmare Zone path can publish after a corrected blocked-key path starts. Drew should also treat a route request signature as pending until a direct transport snapshot arrives, and avoid restarting the same pending signature during refresh bursts; repeated same-route restarts can starve the HUD/highlighter of completed telemetry while map tiles continue drawing from the engine's private path state.

## D-0023: Apply Drew Policy Before Engine Rebuilds

Date: 2026-08-07

Supersedes the replay-after-the-fact part of D-0020 through D-0022. Runemoro `shortest-path` keeps route target, pathfinder completion, and overlays under one route-engine owner. Drew's internal route engine must follow that same ownership shape: before any `restartPathfinding()` rebuild, including manual right-click/shift-click targets and config refreshes, the engine asks Drew's Helper for the current config override and applies it before `PathfinderConfig.refresh()`.

Drew's Helper remains the visible policy/UI owner, but it must not depend on noticing a completed internal route and then replaying the target to fix policy afterward. `blockedTransportKeys` must be present in every Drew override, using an empty list when `Hide Locked Teleports` is disabled, so stale static config cannot keep or drop Nightmare Zone incorrectly across toggle changes.

## D-0024: Drew Policy Must Preserve Route Visuals

Date: 2026-08-07

Drew's Helper may apply policy overrides to the internal Shortest Path engine, but those overrides must not accidentally disable the proven upstream visual path. Every Drew route override must explicitly keep `drawMap`, `drawMinimap`, `drawTiles`, `showTransportInfo`, and `postTransports` enabled unless Myth deliberately adds visible settings for those controls later.

Cancelled or non-done `Pathfinder` instances are not valid route telemetry. Completion publication must require the callback pathfinder to still be current and complete before Drew's HUD/highlighter or the legacy `shortestpath/transports` message consume it.

## D-0025: Route Regressions Need First-Class Diagnostics

Date: 2026-08-07

After multiple live regressions where map tiles, world-map path drawing, Drew's HUD, and tab/minigame highlighting disagreed, do not keep patching from visual symptoms alone. When route logs are insufficient, enable Drew's Helper `Route Diagnostics` and use the `DREW_ROUTE_DIAG` trace as the next source of truth.

The diagnostic contract must cover the whole route handoff: Drew config changes, blocked transport keys, requested target/signature, engine policy override, pathfinder submit/completion/stale-skip state, path length/endpoints, transport telemetry counts/first step, map/tile overlay skip reasons, HUD snapshot acceptance, and current-path fallback results. Keep diagnostics opt-in and state-change/de-duped so normal users are not flooded.

## D-0026: Dev Route Diagnostics Come From Captured STDOUT

Date: 2026-08-07

The Drew Helper Gradle dev launcher runs on the test runtime classpath, which includes `logback-test.xml` and writes plugin logs to STDOUT. Do not assume route diagnostics land in `C:\Users\drews\.runelite\logs\client.log` during `gradlew run`.

`run-drews-helper-dev.bat` must capture the dev console stream to `logs\drews-helper-dev-*.log`, and `tools\collect-route-diagnostics.ps1` should default to the newest captured dev log before falling back to the normal RuneLite client log. If no captured dev log exists after a test, the client was not launched through the current repo launcher or the launcher exited before producing output.

## D-0027: Startup-Only Diagnostics Are Not Route Evidence

Date: 2026-08-07

A captured diagnostic log that only reaches `LOGIN_SCREEN` startup lines does not prove anything about map route drawing, locked minigame filtering, or HUD/highlighter state. Treat it as missing repro evidence, not as a route-engine result.

Route diagnostics must prove the input path before route fixes continue: game-state transitions to `LOGGED_IN`, game ticks, route menu-entry injection, route-menu click callback, selected packed target, target-set call, pathfinder submit, and overlay render state. If `engine.menu.add` never appears, the original Shortest Path menu injection is not active. If `engine.menu.click` never appears, the menu item was not selected or the callback is not wired. If those fire but `engine.pathfinder.submit` does not, debug `setTargets()`/local-player/start handling next.

## D-0028: UI-Only Reset

Date: 2026-08-07

Myth ordered Drew's Helper reduced to only the plugin UI element and UI buttons. The vendored `shortestpath` engine, route/pathfinder behavior, path resources, map/minimap/tile overlays, minigame scanner, teleport highlighter, route telemetry bridge, route diagnostics, and route behavior tests are no longer part of the active mod.

Do not continue patching route regressions from the removed implementation. Future route/path/highlight behavior should be rebuilt deliberately from the preserved UI shell only if Myth explicitly asks for a new implementation.

## D-0029: Use Runemoro As Architecture Reference, Not A Restored Vendor Drop

Date: 2026-08-07

Myth asked for a deep analysis of Runemoro's original `shortest-path` so Drew's Helper can develop its own variant. The reference commit is `655f5a24cd1a08984d824fb0692fa29b3b7185f8`, and the persistent notes live in `docs/C2_Guides/RUNEMORO_SHORTEST_PATH_DEEP_DIVE.md`.

Future route work should keep Runemoro's successful ownership shape: one route owner controls target, pathfinder, resources, marker, and route outputs, while views render from that single output. Do not revive the deleted replay/telemetry split where HUD/highlighter state and map/tile path state can disagree.

The Drew variant should not copy upstream blindly. It needs typed route policy, structured transport metadata, route result statuses, stale worker rejection, route-step explanations, diagnostics, and tests before live UI/highlighter integration.

## D-0030: Waypoint Placement Is The First Rebuilt Map Surface

Date: 2026-08-07

Myth asked to place five waypoints on the map after the UI-only reset. This does not revive the deleted route engine. Drew's Helper now owns five persistent waypoint slots directly in `DrewsHelperPlugin`, stores them as hidden config keys (`waypointNPosition` encoded as `x,y,plane`), and renders them as RuneLite `WorldMapPoint` markers using the five waypoint colours from `DrewsHelperConfig`.

World-map right-click placement is the active input seam for the future route variant. Future path drawing should consume these saved waypoints as ordered destinations, but pathfinding, transport scoring, minimap/tile drawing, Quest Helper routing, minigame scanning, and teleport highlighting remain removed until rebuilt deliberately.

## D-0031: First Route Rebuild Is Walking-Only From Waypoints

Date: 2026-08-07

Myth explicitly approved rebuilding route guidance after the UI-only reset, but only as shortest walking distance from the player through placed waypoints. This revives route drawing deliberately without restoring the old vendored `shortestpath` package.

Current route ownership:
- `DrewsHelperPlugin` owns waypoint state, player-start capture, route worker lifecycle, and the active immutable `DrewsHelperRouteSnapshot`.
- `com.drewshelper.routing/**` owns the walking route model, Runemoro collision-map loader, and A* solver.
- `com.drewshelper.routing.ui/**` renders the one route snapshot on the world map, minimap, and in-scene base tiles.

Intentional exclusions for this phase:
- No teleports, fast travel, transports, ladders/stairs, Quest Helper route requests, minigame scanner, teleport highlighter, plugin-message bridge, or route diagnostics.
- No `src/main/java/shortestpath/**` restoration.
- `src/main/resources/collision-map.zip` is allowed as a third-party data resource from Runemoro's BSD-licensed project, and `THIRD_PARTY_NOTICES.md` must stay with it.

The route colour is `DrewsHelperConfig.pathColor()` defaulting to `#800020`. Waypoint #1 marker colour is now `#A9A9A9`.

## D-0032: Walking Route Ties Prefer Player-Like Diagonal Progress

Date: 2026-08-07

Myth reported that the first straight-line tie-breaker made the highlighted walking path look closer, but the character still walked a slightly different route to the same waypoint. Runemoro's reference `shortest-path` uses collision-map graph edges as the source of truth and lets route rendering consume the pathfinder result directly; it does not apply a cosmetic target-line penalty after shortest distance.

Drew's walking solver should keep exact shortest walking distance first, with no teleports/transports in this phase. Among equal-cost walking paths, prefer legal diagonal progress toward the waypoint before sideways/cardinal cleanup, then use fewest turns and stable order. Do not reintroduce the older target-line deviation penalty unless Myth explicitly chooses prettier line distribution over matching the route the player is likely to walk.

Waypoint endpoint rendering should reuse the shared numbered circle waypoint icon on world-map/minimap/scene surfaces instead of text-only `WP1` labels, so all waypoint displays identify the same slot in the same visual language.

## D-0033: Walking Routes Are Committed Until The Player Strays

Date: 2026-08-07

Myth reported that the highlighted route still sometimes diverged from the path the character actually walked after a click, and that the highlighted route disappeared in chunks while moving. The root maintenance bug was continuous route rebuilding from the current player tile: every step changed the route signature, so the overlay replaced the path instead of advancing the committed route.

Drew's walking route should now behave like a committed click route. Calculate from the player to ordered waypoints when waypoints/config change or when no valid route exists. While the player remains on any tile in the committed path, do not re-solve; consume only the leading path tile so route highlights disappear progressively. If the player is no longer on the committed path, treat that as a real stray and recalculate from the player's actual tile.

RuneLite exposes the local click destination through `Client#getLocalDestinationLocation()`, but not the complete private walk queue. Drew cannot directly read the exact queued client/server path, so committed-route tracking plus stray detection is the current practical contract until a live-scene collision/queue proxy is added.

## D-0034: Walking Route Ties Prefer Primary-Axis Forward Movement

Date: 2026-08-07

Supersedes the equal-cost movement preference part of D-0032. Myth's live testing showed the player appears to prefer forward/cardinal progress along the longer remaining axis before using diagonal movement as the tie-breaker. The previous diagonal-first rule could therefore still draw a slightly different line than the character walked, even when both paths had the same walking distance.

Drew's walking solver should still keep exact shortest walking distance first and should still avoid teleports/transports in this phase. Among equal-cost walking paths, if one axis has more remaining distance than the other, prefer the cardinal move along that primary axis first, then the diagonal toward the target, then secondary-axis cleanup. If both axes are tied, diagonal movement remains first because it is required to keep the shortest Chebyshev walking distance.

## D-0035: Committed Route Progress Uses Exact Trimming And A 10-Tile Stray Window

Date: 2026-08-07

Supersedes the trim/recalculate threshold details in D-0033. Myth's run-speed testing showed that consuming only one leading path tile per game tick lags behind when the character advances two tiles before the next plugin update.

Drew's walking route should keep one committed route until waypoints/config change or the player genuinely strays. If the player is standing on a later tile in the committed route, consume every leading route tile before that current tile and leave the current tile highlighted. This accounts for both walk-speed and run-speed progress without rebuilding the route.

Do not recalculate just because the player is a little off the highlighted route. If the player is within 10 Chebyshev tiles of any same-plane committed route tile, preserve the committed route. Recalculate from the real player tile only when the player is more than 10 tiles away from the committed route, or when waypoint/config input changes. Future UX may expose that 10-tile tolerance as a config control after more live testing.

## D-0036: Baseline Physical Transports Are Built Into Drew Routing

Date: 2026-08-07

Myth asked to add the basic click/pay/default OSRS transport families without adding one frontend toggle per family. Drew's route graph should therefore include baseline physical transport edges by default: click objects/gates/gangplanks, ordinary ships/ferries/boats, charter ships, magic carpets, and minecarts after filtering out rows with explicit skill or quest requirements in the maintained Shortest Path TSV data.

The only visible transport toggle added in this pass is `Other Transportation` -> `Use: Wilderness Transports`, default OFF. It controls both Wilderness levers and Wilderness obelisks because those are dangerous/different-enough routes. It does not control ordinary click objects such as gangplanks, ship boarding edges, gates, ladders, or paid non-Wilderness travel.

The active implementation remains Drew-owned: generated resource `src/main/resources/drewshelper-transports.tsv`, loader/graph classes in `com.drewshelper.routing`, and existing route overlays consuming one `DrewsHelperRouteSnapshot`. Do not restore `src/main/java/shortestpath/**`, plugin-message telemetry, teleport highlighter behavior, or route diagnostics to support these transports unless Myth explicitly asks for a new Drew-owned rebuild.

## D-0037: Transport Jumps Render As Dotted Route Connectors

Date: 2026-08-07

Myth confirmed baseline transports work, but the route looked clunky when ships, minecarts, carpets, or other terrain-crossing transports appeared only as separated highlighted tiles. Drew should render those non-walking route segments as dotted connectors so the player can see where the transport takes them.

Do not add a separate transport overlay or independent transport state. The committed `DrewsHelperRouteSnapshot` remains the route source of truth. A segment is considered a transport jump for rendering when two consecutive route points are not normal same-plane one-tile movement: either their planes differ or their Chebyshev tile distance is greater than one. Normal walking cardinal and diagonal steps must not be dotted.

World-map rendering should draw dotted connectors for visible transport jumps using the configured route colour. Minimap rendering may draw the same dotted connector only when both jump endpoints can be projected into minimap range. Transport labels/action text remain future polish; this decision only covers visual connectors.

## D-0038: World-Map Routes Keep A Minimum Screen Footprint

Date: 2026-08-07

Myth reported that waypoint icons and dotted transport connectors remain easy to locate when the world map is zoomed out, but normal route tiles shrink with the map and become harder to see. Drew's world-map route rendering should keep the actual committed route data unchanged while giving each route tile a minimum screen-space footprint.

The active rule is: close zoom still uses the projected map tile size; when that projected tile is smaller than 4px, render a centered 4px route marker clipped to the world-map widget. This applies only to the world-map route overlay. Do not change route solving, minimap route size, scene-tile highlights, waypoint icons, or transport jump detection for this polish pass.

## D-0039: BFS Is A Benchmarkable Solver Mode, Not The Default Yet

Date: 2026-08-07

Myth asked to test whether Runemoro-style breadth-first search matches real OSRS character walking better than Drew's current A* tie-breaks. Drew's route system should therefore expose BFS as a selectable solver mode and add a live benchmark path, but A* remains the default until in-game movement data proves BFS produces a better route shape.

Benchmark mode must solve both selected and alternate strategies for the same waypoint route, preserve the selected solver as the displayed route, and compare both predicted paths against actual player movement captured on game ticks. The required comparison fields are first-step direction, first 5 and 10 movement ticks, full tile-sequence match, path length, max lateral deviation, turn count, solve time, and expanded node count.

Do not restore the deleted `src/main/java/shortestpath/**` package for this. BFS belongs inside `com.drewshelper.routing` and must use the same Drew-owned collision map, transport graph, immutable snapshot contract, route worker cancellation, and overlays as A*. Future optimization may add packed tile keys or bidirectional BFS, but the first pass prioritizes correctness, deterministic tie order, and benchmark visibility.

## D-0040: Walking Route Ties Delay Diagonal Forks When Cardinal Is Also Shortest

Date: 2026-08-07

Supersedes the detailed movement-order part of D-0034. Myth's clean A* benchmark samples showed the first 10 movement tiles matching, then the client diverging later at equal-length fork points. The repeated pattern was not BFS versus A*: both solvers produced the same highlighted route, while the client chose a cardinal branch and rejoined at the same final path length.

Drew's walking solver should keep exact shortest route length first. When one axis is longer, legal cardinal movement toward either target axis now outranks the diagonal move; the primary-axis cardinal still ranks first, but secondary-axis cardinal is allowed to beat diagonal when the full path length stays equal. Diagonal remains first only when both axes are tied, because a cardinal step from a tied open-field position lengthens the Chebyshev route.

A* should not return the first target path immediately for short walking segments. After the first target is found, it may continue through same-shortest-cost candidates within a bounded refinement window and choose the path with the better client-style move preference. The old fewest-turn preference is not a deciding tie-breaker for route shape anymore; live evidence showed it favored visually smooth diagonals that the client did not always walk.

## D-0041: Final Walking Path Ranking Uses Reverse Distances, Not First Target Hit

Date: 2026-08-07

Supersedes the implementation mechanism in `D-0040` while keeping its intent. Continuing A* after the first target hit was not enough because equal-length alternatives could be lost before the final target comparison. Drew's walking solver should now separate shortest-distance discovery from route-shape selection on short route segments: first find the shortest segment length, then compute bounded reverse distances from the target and reconstruct an exact-shortest path by the same legal client-style candidate order used in benchmark diagnostics.

This keeps shortest route length as the hard contract while making displayed equal-length forks deterministic. A* remained the default during the short BFS experiment. D-0042 later retired BFS as a benchmark mode after live samples showed it was slower and did not match client movement better.

Myth's post-`D-0040` Path 1 and Path 3 tests revealed a different issue: the live client walked continuations that the static collision graph does not rank as equally short. Specifically, `(2939,3222,0) -> (2938,3221,0)` toward `(2932,3214,0)` and `(2967,3231,0) -> (2968,3230,0)` toward `(2970,3229,0)` are legal live steps, but Drew's graph continuation from those tiles is longer than the client path. Treat those samples as collision-map/live-client disagreement until a collision override or updated collision resource is validated; do not keep tuning A* versus BFS for them.

## D-0042: Retire BFS Test Mode And Keep One Visible Route Solver

Date: 2026-08-07

Supersedes D-0039. Myth's live benchmark samples showed BFS did not match client movement better than the Drew A* solver and cost substantially more node expansion. The route UI should no longer expose a `Route Solver` dropdown, and Drew's route engine should not carry BFS as an alternate strategy for normal waypoint guidance.

The live route contract is now one Drew-owned solver: A* over Runemoro walking collision plus Drew's transport graph, followed by the bounded client-style final path ranking from D-0041. `Benchmark Movement` stays as an opt-in diagnostics toggle, but it compares the displayed route against actual player movement only. It must not solve or report an alternate BFS path.

The next useful improvement is collision validation, not another solver-mode comparison. For repeated live divergences, log the observed client edge, whether Drew's collision graph allows that single edge, whether the continuation remains shortest, and whether repeated observations justify a local collision override.

## D-0043: Collision Edge Validation Logs Before Overrides

Date: 2026-08-07

Myth's repeated Path 1 and Path 3 samples showed stable late-route divergence after A*/BFS and tie-order work were already ruled out. Drew should not apply collision overrides blindly from one observed walk. The next step is diagnostic validation: while `Benchmark Movement` is enabled, `DREW_ROUTE_BENCH` logs `edgeValidation` at the first real divergence.

`edgeValidation` must report the actual live edge from the fork tile, whether Drew's collision graph treats that edge as legal walking/transport movement, the shortest graph continuation from the actual tile to the target, the delta versus the displayed route's remaining distance from the fork, and a session-local repeat count keyed by `from -> actual -> target`. A repeated illegal edge or repeated longer continuation can be marked `overrideCandidate=true`, but no route override is applied by this decision.

Future local collision overrides should require clean repeated samples with Run OFF, ground-click-only movement, no stale `idx=0` target captures, and matching observed edge keys. Do not add permanent collision overrides from stale return legs, object clicks, run-speed samples, or one-off route variance.

## D-0044: Local Walking Overrides Are Target-Aware And Evidence-Scoped

Date: 2026-08-07

Myth repeated the Path 1 and Path 3 tests after the collision-edge validator was built. Even though restarting the dev client reset the session-local repeat counter, the live logs repeated the same clean divergence evidence across sessions: Drew's graph treated the observed cardinal branch as legal but one step longer, while the OSRS client reached the target in the same total number of movement ticks.

Drew may now carry target-aware local walking overrides for those two confirmed route windows only. The Path 1 override is keyed to target `(2932,3214,0)` and starts at fork `(2939,3223,0) -> (2939,3222,0)` with the observed southwest continuation window. The Path 3 override is keyed to target `(2970,3229,0)` and starts at fork `(2966,3231,0) -> (2967,3231,0)` with the full observed northeast finish. These overrides participate in the existing legal-step list and reverse-distance final ranking, so the displayed route can choose the live branch while normal unrelated targets still use the base collision map and client-style ordering.

Do not convert this into a broad "trust live movement" system. Add future local overrides only after clean repeated `DREW_ROUTE_BENCH` samples identify exact target/fork keys, and prefer replacing/updating the collision resource if the same map-region disagreement becomes widespread.

## D-0045: Path 1 Tail Preference Extends The Existing Target-Aware Override

Date: 2026-08-07

After the first Path 1 target-aware override build, Myth reran the exact `(2932,3214,0)` target. The old fork at `(2939,3223,0)` was fixed, but the live client made one later equal-length tail choice from `(2935,3218,0)` to `(2934,3217,0)` while Drew still highlighted `(2935,3217,0)`. `edgeValidation` reported `legal=true`, `delta=0`, and `longer=false`, so this is a route-shape tie preference, not a collision disagreement.

Drew may extend the existing Path 1 target-aware override with that single tail preference. Keep it scoped to target `(2932,3214,0)` and do not treat it as evidence for a broader collision-map override.

### D-0046 - Path 1 final-tail overrides stay target-aware
- Date: 2026-08-07
- Decision: The final Path 1 tail mismatch is handled as another target-aware local walking override, not as a global collision or solver-order change.
- Evidence: Myth's post-D-0045 run reached (2932,3214,0) with lenDelta=0, maxDev=1, and divergence from (2934,3217,0) where the client chose (2933,3216,0).
- Constraint: Override applies only toward target (2932,3214,0). One-tile-different targets such as (2932,3215,0) must not inherit the preference.
- Follow-up: Myth should rerun Path 1 once. Expected result is divergence={none} and maxDev=0.

### D-0047 - Benchmark capture must sync to route start before shape collection
- Date: 2026-08-07
- Decision: `Benchmark Movement` must wait for the player to reach the displayed route start, or one of the first few displayed route tiles, before recording actual movement. Off-route pre-start movement should be ignored as `reason=stale-start`, not logged as a route divergence.
- Evidence: During Path 1/Path 3 override testing, return legs repeatedly poisoned the next outbound sample as `idx=0` stale-target noise even when the active route patch was loaded and correct.
- Decision: Add diagnostic-only route-shape scoring to completed `DREW_ROUTE_BENCH` reports before changing path selection. Shape diagnostics may compare displayed versus actual movement by line-error, diagonal/cardinal distribution, and turn count, but they must not influence route selection until Path 1, Path 2, Path 3, and additional random nearby routes confirm the scoring matches live client behavior.
- Constraint: Keep the D-0044 through D-0046 target-aware overrides active while collecting shape data. Do not delete them or promote the shape ranker into the solver until the diagnostic evidence is broad enough.

### D-0048 - Route recalculation preserves last visible path
- Date: 2026-08-07
- Decision: When Drew submits a fresh route calculation, the CALCULATING snapshot should retain the previous path for overlay continuity instead of clearing the connector tiles immediately.
- Evidence: Myth's random-event test kept waypoint markers visible but lost the highlighted tiles between waypoints after a Pillory Guard event and location jump.
- Constraint: This is overlay continuity only. The preserved path is replaced by the new route once calculation publishes, and it must not change walking solver output or benchmark scoring.

### D-0049 - Chained benchmark diagnostics use the active waypoint segment
- Date: 2026-08-07
- Decision: In multi-waypoint routes, `DREW_ROUTE_BENCH` candidate traces, edge validation, and route-shape diagnostics should judge the first divergence against the waypoint segment being walked, not blindly against the final route endpoint.
- Evidence: Myth's five-waypoint random chain completed with an early first-leg divergence, but the old `edgeValidation` target was the fifth waypoint. That made continuation distance and shape scoring misleading even though the displayed route and actual movement were otherwise useful.
- Constraint: This is diagnostic-only. Route solving, local walking overrides, waypoint ordering, overlay rendering, and benchmark capture lifecycle stay unchanged. Single-target benchmark reports keep their existing whole-route shape output.

### D-0050 - Shadow route diagnostics compare against no-override baseline
- Date: 2026-08-07
- Decision: Before removing the Path 1 / Path 3 target-aware local overrides, Drew must log a shadow route solved with those local overrides disabled. The shadow result is diagnostic-only and must not alter the displayed route.
- Evidence: Myth's clean control and five-waypoint random-chain samples showed the current route now matches live movement, but because the local overrides were active, those samples could not prove whether the general route ranker would have selected the same branches on its own.
- Constraint: `shadow={...}` may be used to judge whether overrides still matter, but not to change route selection automatically. Removing or replacing local overrides requires clean repeated control data where the no-override shadow route is equal or better for Path 1 and Path 3, plus no regression on nearby random chains.

### D-0051 - Shape-shadow ranker remains diagnostic-only
- Date: 2026-08-07
- Decision: Drew may log a second shadow route, `shapeShadow={...}`, solved without target-aware local walking overrides and with segment line-shape tie ranking, but it must not affect the visible route yet.
- Evidence: Myth's D-0050 control run showed Path 1 and Path 3 still need the existing local overrides (`overridesMatter=true winner=visible`), while the random five-waypoint chain exposed a legal equal-length fork where segment shape metrics favored the live client branch over the displayed branch.
- Constraint: `shapeShadow` is not sufficient evidence for promotion by itself. A first route-level probe showed line-shape ranking can overcorrect earlier than the client. Future promotion needs repeated live samples where `shapeShadow` beats or ties visible movement on controls and random chains without introducing new early divergence.

### D-0052 - Divergence diagnostics report merge-back before route promotion
- Date: 2026-08-08
- Decision: `DREW_ROUTE_BENCH` divergence output should report whether actual movement rejoins the displayed path after the first divergent tile. The merge-back record includes expected index, actual index, step delta, and the merge tile.
- Evidence: Myth's ordered five-waypoint chain after D-0051 had `first=match`, then a segment fork where the displayed route chose `(2977,3251,0)` and the client chose `(2977,3252,0)`. The live movement rejoined the displayed route shortly afterward, while edge validation alone classified the first actual edge as longer.
- Constraint: Merge-back is diagnostic-only. Do not change visible route selection, remove Path 1 / Path 3 target-aware overrides, promote `shapeShadow`, or add a new local override from one merge-back sample. Repeated clean samples should decide whether a divergence is a stable client step-order preference, a collision-resource disagreement, or input/click noise.

### D-0053 - Same-time merge-back is benign diagnostic evidence
- Date: 2026-08-08
- Decision: A divergence with `mergeBack stepDelta=0` is classified as `sameTimePermutation benign=true` and should be scored as a low-penalty diagnostic fit, not as hard route drift.
- Evidence: Myth's post-D-0052 rerun repeated the `(2976,3252,0)` fork. The displayed route chose `(2977,3251,0)`, the client chose `(2977,3252,0)`, and both paths rejoined at `(2979,3250,0)` on the same movement index. The old single-edge `longer=true` readout overstated the problem because it ignored that immediate rejoin.
- Constraint: This is still diagnostic-only. Visible route selection, local Path 1 / Path 3 overrides, `shapeShadow` solving, waypoint behavior, and capture lifecycle stay unchanged. Promotion requires repeated clean samples and a separate explicit route-ranker change.

### D-0054 - Post-merge differences must be visible before ranking changes
- Date: 2026-08-08
- Decision: `DREW_ROUTE_BENCH` divergence output should report `additionalDivergences={...}` after the first merge-back when the route still does not fully match. A benign first fork is not enough evidence to promote or change route ranking if the completed route still has `full=false` or a non-zero `lenDelta`.
- Evidence: Myth's D-0053 five-waypoint chain visibly differed between waypoint 2 and waypoint 3. The first logged mismatch was `classification=sameTimePermutation benign=true`, but the route completed with `lenDelta=-1`, meaning the first-divergence-only report could hide a later mismatch or length-only shortcut.
- Constraint: This is diagnostic-only. Do not promote `shapeShadow`, add a local override, remove Path 1 / Path 3 overrides, or change visible route selection from this sample until the post-merge difference is classified by fresh D-0054 logs.

### D-0055 - Post-merge forks use the existing segment validator

Date: 2026-08-08

Decision: When `additionalDivergences={idx=...}` reports a later mismatch after the first merge-back, Drew should also log `additionalDivergenceDetail={idx=... candidates={...} edgeValidation={...}}`. The detail must reuse the existing segment-aware candidate trace and observed-edge validator instead of creating another route-comparison path.

Evidence: Myth's post-D-0054 five-waypoint chain completed with the first waypoint 2 -> 3 mismatch classified as `sameTimePermutation benign=true`, but the final report still had `full=false lenDelta=-1` and `additionalDivergences={idx=52 ... classification=earlyMerge benign=false}`. The old first-fork `candidates` and `edgeValidation` still described idx 39, so the later shortcut could not be judged from the log.

Constraint: D-0055 is diagnostic-only. Visible route selection, local Path 1 / Path 3 overrides, `shapeShadow` solving, waypoint behavior, and capture lifecycle stay unchanged. Do not add an override or promote a ranker until repeated clean samples classify the later fork consistently.

### D-0056 - Post-merge forks need candidate rank telemetry before promotion

Date: 2026-08-08

Decision: When `additionalDivergenceDetail={idx=...}` reports a later post-merge fork, completed benchmark reports should also include `forkRank={...}`. The rank trace validates every legal neighboring candidate at that fork, reports each candidate's continuation total/delta, and marks `predictedRank` plus `actualRank`.

Evidence: Myth reran the same five-waypoint chain after D-0055. The later fork repeated at `idx=52`: Drew displayed `(2983,3239,0)`, the client walked `(2984,3239,0)`, and edge validation said the client branch was legal, found continuation, `delta=0`, and `longer=false`. That is enough to justify richer rank telemetry, but not enough to change visible routing.

Constraint: D-0056 is diagnostic-only. Visible route selection, local Path 1 / Path 3 overrides, no-override `shadow`, `shapeShadow`, waypoint behavior, and capture lifecycle stay unchanged. Do not promote a local ranker, add broad overrides, or remove existing fixed-control overrides until repeated chains show the same rank outcome and the Point 1 / Point 2 / Point 3 controls still pass.

### D-0057 - Close the current route-diagnostics phase without behavior changes

Date: 2026-08-08

Decision: Close the current diagnostic/ranker investigation with visible route behavior unchanged. Keep the Path 1 / Path 3 target-aware local walking overrides active, keep `shapeShadow` and `forkRank` as telemetry, and do not promote broad local ranking from the current sample set.

Evidence: Myth's final fixed-control rerun after D-0056 completed Point 1, Point 2, and Point 3 with `full=true`, `lenDelta=0`, `maxDev=0`, and `divergence={none}`. New random five-waypoint chains did not repeat the old same-chain `actualRank=1` signal; usable random-chain differences were mostly `sameTimePermutation benign=true`, while the second random-chain run was contaminated by a one-tile-short click on waypoint #4.

Constraint: Future route-ranking changes need fresh explicit evidence from completed benchmark reports. Use the existing diagnostic fields first: `classification`, `additionalDivergences`, `additionalDivergenceDetail`, and `forkRank`.
