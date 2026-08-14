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

### D-0058 - Checkboxes are permission, account state is capability

Date: 2026-08-09

Decision: A transport edge is usable only if both gates pass. The checkbox says the user permits that family; the account snapshot says the game permits that edge. Quests, discoveries and destination unlocks are trusted from the checkbox and never verified. Skill levels and carried items are computed live from the client.

Evidence: Myth's instruction was lenient checking, but only behind an enabled checkbox: "Obviously someone shouldn't check that box if they haven't unlocked them. Since it's tied behind." Upstream data has full skill and item requirements but patchy quest data, and no requirement data at all for Magic Mushtrees, so verifying quests would reject edges that are genuinely available.

Constraint: Skill levels are **real, not boosted**. Myth's reason: "a shortcut can appear and then expire on you mid-route." Magic Mushtrees stay a pure trust box. Do not add quest verification without fresh upstream data, and do not switch to boosted levels.

### D-0059 - Regeneration is judged on "nothing lost", not on row counts

Date: 2026-08-09

Decision: The acceptance test for regenerating `drewshelper-transports.tsv` is that the set of `source -> destination` pairs from the previous file is a subset of the new one. Row counts are not the test.

Evidence: I originally set the acceptance test as "BASELINE must come out at exactly 5,358" and that was wrong. It came out at 5,800 because 440 of the extra rows carry requirements the old 4-column format had to discard. The subset test passed with zero pre-existing edges missing.

Constraint: The upstream files store boarding tiles and landing tiles as **separate rows** for gliders, balloons, mushtrees, quetzals and the wilderness obelisks. The real edges are the cross product, with requirements unioned and duration taken as the max, which is what upstream's `Transport.java` does. Treating one row as one edge produced zero edges for four families and collapsed the wilderness obelisk network from 325 to 7. Confirmed by arithmetic: 53 obelisk boarding tiles x 6 landings = 318, plus 7 levers = exactly 325.

### D-0060 - Energy is simulated after the search, not priced inside it

Date: 2026-08-09

Decision: A* keeps fixed edge costs. The run-energy model runs as a separate forward pass over the finished path to produce the ETA.

Evidence: What a tile costs in time depends on how much energy you have on arrival, which depends on the entire path taken to reach it. That is not a fixed edge weight and cannot be expressed as one without making the search inadmissible.

Constraint: Myth checked whether this drifts: "Wouldn't that give an inaccurate eta? shouldnt it calculate it all out at once." It does compute the whole forecast up front, including current energy, regen, weight, agility and gear. His 343-tile test predicted 2:25 and arrived at 2:25. Do not reintroduce energy as an A* edge weight.

### D-0061 - Parse the transport resource once

Date: 2026-08-09

Decision: `drewshelper-transports.tsv` is parsed once into an immutable master list and filtered in memory for each policy and capability combination.

Evidence: Myth reported toggles taking "quite a bit of time to update". The engine cache key includes account state, so every checkbox toggle, coin threshold crossing, or item change re-read and re-parsed all 7,331 rows. The original design said parse once; the first implementation re-read.

Constraint: The capability signature is deliberately coarse. It records only whether each item symbol is held at all, and buckets coins to the thresholds the data actually uses. Encoding an exact coin count would rebuild the route every time you picked up a coin. Do not make the signature more precise without a reason.

### D-0062 - The ring of endurance does not stack with stamina

Date: 2026-08-09

Decision: Apply the stamina potion's drain reduction, or the ring of endurance's 15% passive, but never both. The ring only applies when stamina is inactive.

Evidence: The wiki states the ring's passive effect "does not stack with the stamina potion effect on drain rate (which alone provides a 70% reduction)". The shipped code applied x0.3 then x0.85 multiplicatively, over-crediting by 15% for anyone wearing the ring with a stamina active.

Constraint: The ring's passive requires **500 or more charges**. The charged and uncharged rings are separate item ids, so charge state is readable but the exact count is not, and a ring below 500 charges still reads as active. This is a deliberate optimistic assumption: charges are bought in bulk and rarely sit under 500. If it ever matters, the options are reading the count from the "Check" chat message and caching it, or a config toggle - not guessing.

### D-0063 - Run is a live state in the simulation, not a constant

Date: 2026-08-09

Decision: Run toggled off means walking, per Myth's instruction. But the simulation re-enables running by itself once energy reaches `VarbitID.RUNENERGY_AUTOENABLE`, if that setting is non-zero.

Evidence: Myth pointed out that most players set the auto re-enable threshold to 1 energy, not full: "you start runnign as soon as you have the energy to run, even if it's just a little bit." For those accounts, run-off is a transient state lasting about one tick, not a decision. Holding `isRunning()` fixed for the whole route would forecast a full-length walk that never happens.

Constraint: The varbit's semantics are **not yet confirmed**. Only `RUNENERGY_AUTOENABLE = 11031` exists in the API, and the constant name alone does not say whether it holds a boolean or a percentage threshold. The value is treated as a percentage and clamped to 0-100, which behaves correctly under either reading: a boolean 1 and a threshold of 1% produce the same behaviour, which is exactly the setting Myth describes. The raw value is written to the `DREW_ROUTE_BENCH` start line so a live sample can settle it. Confirm before relying on any non-1 threshold.

### D-0064 - The run/walk duty cycle is what the ETA actually models

Date: 2026-08-09

Decision: The simulation runs only while energy covers a tick's drain, and walks otherwise. It is never assumed that an enabled run toggle means running the whole way. The long-run average converges on `(2*regen + drain) / (regen + drain)` tiles per tick, and the auto re-enable threshold changes the size of each run burst but not that rate.

Evidence: Myth asked directly whether auto re-enable being on is being mistaken for infinite energy. It is not - the `energy >= drain` gate produces the cycle. Two tests now assert it rather than leaving it to reasoning: `longRoutesAverageTheRunWalkDutyCycleNotFlatRunning` measures a 2,000-tile route against the analytical rate, and `autoRunThresholdChangesTheBurstPatternNotTheLongRunRate` shows thresholds of 1% and 50% converge within 0.05 tiles/tick over 6,000 tiles. The reason is conservation: running ticks x drain must equal walking ticks x regen, so the resume threshold cannot change the ratio.

Constraint: The threshold does still matter at the **start** of a route, where current energy and the first burst are not yet in steady state. Do not simplify the model to a flat average - short routes are dominated by the opening burst and Myth's 101-tile test (31s measured, 30.3s predicted) only passed because the sim runs the full bar down before cycling.

### D-0065 - A charged ring of endurance is treated as being over 500 charges

Date: 2026-08-09

Decision: Ratified by Myth. A worn charged ring of endurance is assumed to be above the 500-charge threshold and gets the 15% drain reduction, with no attempt to read the actual count.

Evidence: Myth's ruling: "I will assume that people using the ring are using it for that purpose and not using it to charge." Someone wearing the ring is wearing it for the passive, not mid-charging it. The exact count is not readable from the item id anyway - only charged versus uncharged.

Constraint: Still subject to D-0062 - the 15% never stacks with an active stamina potion. If this assumption ever needs revisiting the options are the "Check" chat message or a config toggle, not inference.

### D-0066 - ETA accuracy logging runs by default, not behind the benchmark

Date: 2026-08-09

Decision: Predicted-versus-actual ETA logging is gated on its own config item `etaDebugLogging`, which defaults to **on**, and no longer depends on `routeBenchmarkEnabled`. The ETA capture keeps its own movement clock and judges arrival against the route's own final tile rather than the benchmark's progress model.

Evidence: Myth asked for it to be on "so we can see if anything is hinky in the future". As first built it only fired when the movement benchmark was enabled, which meant it would only ever catch a drifting forecast if someone had remembered to turn the benchmark on beforehand - exactly the case where nobody is watching. It is two lines per journey, so the noise cost of leaving it on is negligible.

Constraint: The two systems are now independent and must stay that way. `startRouteBenchmarkIfNeeded` starts the ETA capture before its own benchmark gate, and `recordRouteBenchmarkPosition` runs the ETA clock before the `capture == null` return. Do not re-couple them for convenience. The capture is created only on a real re-solve - `advanceCommittedRouteIfNeeded` consumes leading tiles without restarting it, which is what keeps the elapsed clock honest across a long walk.

### D-0067 - Capability replaces the transport checkboxes

Date: 2026-08-09

Decision: Agility shortcuts, canoes, grapple shortcuts, gnome gliders, hot-air balloons and quetzals are always loaded and gated per edge by the account's real state - skills, carried items, completed quests, unlock varbits and varplayers. Their six config items and the whole "Basic Transportation" section are deleted. Two toggles survive, for two different reasons: Magic Mushtrees (upstream carries no requirement data, so the box is the user's attestation) and Wilderness (not a capability - you can always walk in; the box asks whether you want to be routed through it).

Evidence: Measured against the shipped resource. Every one of the 557 agility rows carries a skill requirement and every one of the 45 canoe rows carries both a skill and an item requirement - so those two families were already fully enforced and the checkbox was the only thing suppressing them. Gliders (103/103 quests), balloons (269/269 varbits) and quetzals (182 quests + 126 varplayers) are fully covered once quests and vars are read. Magic mushtrees have 29 rows with zero requirements of any kind. Wilderness has 325 of 331 rows with none. Quest names resolve: 39 of the 40 distinct names match RuneLite's `Quest` enum exactly.

Constraint: **Unresolvable requirements are treated as satisfied, never as blocking.** A checkbox fails permissively (offers a route you cannot take); auto-detection fails restrictively (silently deletes a route you can). The second is far harder to notice, so an unknown quest name or an id with no snapshotted value must pass. Unresolved quest names are logged once per session. The one known case is an upstream typo - the data says "Shadows of the Storm", the quest is "Shadow of the Storm" - affecting a single BASELINE row.

Sequencing note: the quest/varbit reader had to land in the SAME change as the checkbox deletion. Deleting the glider box without reading quests would have made gliders unconditionally available and routed the player through The Grand Tree without having done it.

### D-0068 - Var requirement grammar has four forms, not three

Date: 2026-08-09

Decision: Varbit and varplayer terms are parsed as `id=value`, `id>value`, `id<value` and `id&mask`, with `;` meaning AND. The fourth form is a **bit test** - `(actual & mask) != 0` - not a comparison.

Evidence: Scanning every var term in the resource turned up bare terms with no comparison operator: `4182&128`, `4182&64`, `4182&32`, `4182&256`, `4182&2048`, `4182&16384`. Reading `4182&128` as a plain id would have parsed the id as 4182 with no operand and silently passed every one of them. The scan is what caught it; assuming a three-operator grammar would have shipped a hole.

Constraint: The id and quest lists are derived from the data at load (`requiredQuestNames`, `requiredVarbitIds`, `requiredVarPlayerIds`) and snapshotted by the plugin - 50 varbit ids and 15 varplayer ids in the current data. Never hardcode an id table; upstream must be able to add a requirement without a code change.

### D-0069 - Magic Mushtrees placement in Advanced Transportation

Date: 2026-08-09

Decision: Magic Mushtrees sits at position 2 in Advanced Transportation - after Fairy Rings, before Mounted Glory. The POH entries shift down to 3-6.

Evidence: The section already has an implicit structure: two world-travel networks (Spirit Trees, Fairy Rings) followed by four player-owned-house transports ordered by Construction level. Mushtrees are a world network, so they belong in the first group. Within it, ordering by typical unlock time puts them last: spirit trees come from Tree Gnome Village (early, no skill gates), fairy rings from Fairytale II (mid-game, behind Lost City and Nature Spirit), and mushtrees behind Bone Voyage, which needs 100 Kudos at the Varrock Museum and lands later than either for most accounts.

### D-0070 - Reached waypoints clear themselves, in place

Date: 2026-08-09

Decision: A waypoint is deleted the moment the player stands on its tile - marker, saved position and route leg all go. The slot is cleared **in place**; the remaining waypoints keep their own numbers and colours rather than shifting up.

Evidence: Myth's ask - "when the player reaches a waypoint tile it deletes that waypoint from the map/route as the player reached it meaning we don't need to see route data to it anymore". In-place clearing is forced by the existing model: the user picks the slot explicitly from the right-click menu ("Waypoint #3"), and each slot owns a configured colour. Compacting would silently renumber and recolour the waypoints still ahead of the player, mid-journey.

Constraint: A waypoint only clears once **armed** - armed means the player has stood somewhere other than that tile since it was placed. Without that, dropping a waypoint on your own tile would delete itself on the next tick and look like the click did nothing. `setWaypoint` and `clearWaypoint` both reset the flag. The rule lives in the static `DrewsHelperPlugin.reachedWaypoint(...)` so it is testable without a client; `clearReachedWaypoints()` runs at the very top of `onGameTick`, before the dirty check, so arrival takes effect immediately instead of waiting on a queued solve.

### D-0071 - Route legs are numbered by waypoint slot, not by leg position

Date: 2026-08-09

Decision: The overlay labels and colours each ETA leg using `waypointSlotForLeg(legIndex)` - the slot of the Nth placed waypoint - not the leg's own index.

Evidence: Destinations are built as the non-null waypoints in slot order, so leg 0 is the first *occupied* slot. The overlay was labelling leg 0 as "WP1" in colour 0 unconditionally. That was already wrong for anyone who placed only Waypoint #3 - the leg read "WP1" in grey while the marker list below read "Waypoint #3" in its own colour. Pre-existing bug, found while adding D-0070, and one that auto-clearing would have made permanent rather than occasional.

Constraint: Any future feature that reorders or skips destinations must go through the same mapping. Do not reintroduce leg-index labelling.

### D-0072 - Waypoints snap to the nearest standable tile

Date: 2026-08-09

Decision: `setWaypoint` runs the requested tile through `DrewsHelperTraversableTiles.nearest(...)` before storing it. Clicking open water or the inside of a wall moves the waypoint to the closest tile the character can stand on, within 32 tiles and on the same plane.

Evidence: Myth's ask - a waypoint dropped in a river is unreachable, and the only symptom today is a route that silently fails to solve, with no hint as to why.

Constraint: "Standable" means the tile has at least one legal move out of it. The collision data exposes only directional moves (`canMoveNorth` and friends on the package-private `DrewsHelperMovementMap`), so a tile nothing can leave is one nothing can stand on. Three deliberate limits:
- **Same plane only.** Snapping between floors would move the waypoint somewhere the user cannot see.
- **Nearest by true squared distance**, not by ring order - a ring-3 corner is further away than a ring-4 edge, so ring-first would sometimes pick the wrong tile.
- **Standable is not the same as reachable.** A walkable tile inside a locked room still will not route. Testing reachability would need a full search from the player's current position; that is out of scope for placing a marker.

If nothing standable is within the radius, or the collision data has not loaded yet, the request is honoured unchanged. Snapping is a convenience and must never block placing a waypoint.

### D-0073 - Logging toggles live in Settings; new default colours

Date: 2026-08-09

Decision: `Benchmark Movement` is renamed **Log Benchmark Movement**, and both it and `Log ETA Accuracy` moved from Routing Options into the Settings section, below the six colour pickers (positions 6 and 7). Default colours changed to a spectrum: Path red `#FF0000`, WP1 orange `#FFA500`, WP2 yellow `#FFFF00`, WP3 green `#008000`, WP4 blue `#0000FF`, WP5 indigo `#4B0082`.

Evidence: Myth's request. Grouping the two log switches together under the display settings puts every "what do I see / what gets written down" control in one place, and the matching `Log ` prefix makes them read as a pair.

Constraint: Changing a default only affects users who have never set that value - RuneLite persists a config item the moment it is changed, and a stored value always wins over the interface default. Anyone who has already picked a colour keeps it until they reset. `DrewsHelperConfigTest` asserts all six defaults and the relocated toggle, so a future edit that drifts from this table fails the build.

### D-0074 - Colour picker width is a RuneLite client issue, not fixable in-plugin

Date: 2026-08-09

Decision: The ragged widths of the colour swatches in the config panel are not fixable from Drew's Helper. Do not re-attempt this from plugin code, and do not add per-item annotations hoping to influence it.

Evidence: Verified against `client-1.12.35.jar` with `javap -p -c`, not from memory.
- `net.runelite.client.ui.components.ColorJButton extends JButton` and declares only `<init>(String, Color)`, `setColor`, `paint` and `getColor`. It never sets a preferred size, so it inherits `JButton`'s natural sizing - text bounds plus insets.
- The button's text is the colour's own hex code, and RuneLite renders it in a proportional font, so `#FFFF00` and `#4B0082` are different pixel widths despite being the same character count. Myth's own guess ("expanding to fit extra characters") was correct.
- `ConfigPanel.createColorPicker(...)` builds the button, calls `setFocusable(false)`, attaches a mouse listener, and **never calls `setPreferredSize`** - while the same class pins every other widget it creates: plain buttons at `new Dimension(22, 0)`, `(18, 0)` and `(25, 0)`, and the combo box at `(width, 22)`. The colour picker is the one widget type RuneLite forgot to pin.
- Drew's Helper has no Swing panel of its own (27 main sources, none a `PluginPanel`), so this is the stock client config panel in every respect.

Constraint: The correct fix is one line upstream in `ConfigPanel.createColorPicker`, matching RuneLite's own convention:
`colorPickerBtn.setPreferredSize(new Dimension(100, 0));`

The only in-plugin alternative is runtime Swing surgery - walking the component tree and forcing a uniform size on every `ColorJButton` found. Rejected as the default because it would resize colour pickers in **every** plugin's settings, not just this one, and would break silently on a client update. Only do it if Myth explicitly accepts both costs.

### D-0075 - Waypoint auto-clear silently killed the ETA arrival log

Date: 2026-08-09

Decision: The ETA capture's tick-and-arrival handling runs at the very top of `onGameTick`, in its own `updateEtaDebugCapture()`, before `clearReachedWaypoints()` and before the `routeDirty` early return. It is independent of route state and of the movement benchmark.

Evidence: Myth's first real ETA log run produced **10 `eta predicted=` lines and zero `eta result=` lines**. Cause: `clearReachedWaypoints()` ran first, reaching the final waypoint cleared it, `clearWaypoint` called `markRouteDirty()`, and `onGameTick` then hit `if (routeDirty) { refreshRouteIfNeeded(); return; }` - returning before `recordRouteBenchmarkPosition()`, which is where arrival was detected. The re-solve then called `startRouteBenchmarkIfNeeded` -> `clearRouteBenchmark()` -> `etaDebugCapture = null`, destroying the capture. Arrival could never be observed.

Constraint: These were two features shipped an hour apart in the same session (D-0070 auto-clear, D-0063/D-0066 ETA logging) that conflicted only at the exact tick they overlap. Anything that observes the *end* of a journey must run before the waypoint clear, not after. Do not move ETA handling back inside the benchmark path or behind the dirty check.

### D-0076 - Off-path re-solve distance split from the benchmark stale-start distance

Date: 2026-08-09

Decision: `ROUTE_RECALCULATE_OFF_PATH_DISTANCE = 2` governs how far the player may stray before the route re-solves. `ROUTE_BENCHMARK_STALE_START_DISTANCE = 10` governs when a benchmark capture is discarded as stale. They were a single constant, `COMMITTED_ROUTE_RECALCULATE_DISTANCE = 10`.

Evidence: Myth asked for the re-solve to trigger at 2 tiles instead of 10. The same constant was also used in the benchmark's stale-start check, where lowering it to 2 would have made the benchmark discard usable captures far more aggressively - a behaviour change he did not ask for and would not have seen coming.

Constraint: Two numbers that happen to be equal are not the same number. Keep them separate.

### D-0077 - Route draw distance is the loaded scene, not a plugin setting

Date: 2026-08-09

Decision: There is no draw cap in plugin code and none should be added. `DrewsHelperRouteTileOverlay.render` iterates the entire path and calls `LocalPoint.fromWorld(client, point)` per tile; that returns null for anything outside the currently loaded scene, so distant tiles are simply skipped.

Evidence: Myth reported the drawn route ending partway, then extending once he neared the edge. RuneLite's scene is 104x104 tiles and is chunk-aligned rather than re-centred every step, so roughly 50 tiles of route are drawable ahead and the visible extent jumps when the scene reloads rather than creeping forward tile by tile. The route itself is solved end to end the whole time - only the tile drawing is bounded.

Constraint: This is an engine limit on world-space overlays and cannot be raised from a plugin. The world-map overlay uses a different projection and is the place to show a whole long route.

### D-0078 - Draw distance per surface, and why the minimap cap is not worth raising

Date: 2026-08-09

Decision: Amends D-0077 with the measured per-overlay position. Do not attempt to extend the ground path, and do not raise `MAX_MINIMAP_DISTANCE`.

Evidence: All three overlays read.
- **Ground tiles** (`DrewsHelperRouteTileOverlay`) - iterates the whole path, no cap of ours, gated per tile by `LocalPoint.fromWorld`. Bounded by the loaded scene only.
- **Minimap** (`DrewsHelperRouteMinimapOverlay`) - has our own `MAX_MINIMAP_DISTANCE = 50`, *and* calls `LocalPoint.fromWorld`. Since the scene gives roughly 52 tiles of radius, that 50 already sits at the engine limit; raising it would buy nothing because `fromWorld` returns null past the scene anyway.
- **World map** (`DrewsHelperRouteMapOverlay`) - iterates the entire path and never touches `LocalPoint`. It projects map coordinates directly, so it draws the **whole** route regardless of length, with no cap.

Constraint: The ground path cannot extend past the scene because there is no local coordinate and no terrain height for a tile the client has not loaded - it is not a drawing choice. The scene is chunk-aligned and reloads when the player nears its edge, which is why the visible extent jumps by tens of tiles rather than creeping forward. Myth's own description ("won't update till I'm pretty close to the edge, then it shows the next set") is a textbook scene reload and confirms the diagnosis. The world map is the surface for seeing a whole long route and already does so.

### D-0079 - Upstream Shortest Path has no draw-distance trick either

Date: 2026-08-09

Decision: Closes the question of whether Runemoro's Shortest Path plugin does something cleverer than us to draw the ground path further. It does not. Do not go looking for a trick again.

Evidence: Read `PathTileOverlay.java` in the upstream checkout at `..\Drew Shortest Path`. Its drawing path is mechanically identical to ours:
- `drawTile(...)` - `WorldPointUtil.toLocalPoint(client, point)` then `if (lp == null) continue;`, then `Perspective.getCanvasTilePoly(...)` then `if (poly == null) continue;`
- `drawLine(...)` - `toLocalPoint` for both endpoints, `if (lpStart == null || lpEnd == null)` skip, then `Perspective.getTileHeight` and `localToCanvas`
- `render(...)` iterates the whole path (`for (int i = 0; i < path.size(); i++)`), exactly as ours does.

Same null-gate, same scene bound, same result. The only real difference found is a `pathStyle` config offering `TileStyle.LINES` as well as tiles - an appearance option, not a distance one. A thin line reads as "continuing" where a chunky tile trail ends bluntly, which is the most likely reason the cut-off felt less noticeable in the original.

Constraint: Also worth recording because it corrects a misframing - **there is nothing stale to refresh.** RuneLite overlays re-render every frame, so newly loaded tiles appear on the very next frame after the scene loads them. "Reload the draw when the scene updates" is already the behaviour; the limit is data availability, not refresh timing. Do not add scene-change listeners or invalidation logic hoping to improve this.

### D-0080 - Stamina duration: measure the unit, never guess it

Date: 2026-08-09

Decision: The ETA models a stamina potion expiring partway through a route. The duration varbit's unit is **measured at runtime** rather than hardcoded: the gap in game ticks between two consecutive single-unit decrements *is* the unit. Once learned it is persisted to the config key `staminaTicksPerDurationUnit` and reused across sessions.

Evidence: `VarbitID.STAMINA_DURATION = 24` exists, but nothing in RuneLite converts it. Searched `client-1.12.35.jar`: no class references the constant by name, the only stamina-named class is `itemstats/potions/StaminaPotion` (which concerns the ring of endurance extending the *restore*, not the duration), and `VarbitID` carries no unit hint. So there was no authoritative source to read the unit from, and a wrong constant would put an unverified number straight into the ETA - which is why this was deferred twice rather than shipped on a guess.

Constraint:
- **Only a drop of exactly one unit calibrates.** A larger drop means ticks went unobserved (lag, world hop, logout) and would understate the interval. Intervals outside 1..100 ticks are rejected as observation gaps.
- **`staminaTicksRemaining == 0` means "unit not measured yet" and must keep the pre-forecast behaviour** - potion assumed up for the whole route. It must never be read as "no stamina", which would make the ETA worse than before the feature existed. Tested explicitly.
- **Not in the capability signature.** It changes every tick while a dose is up, so including it would rebuild the route constantly - the same reason current energy is excluded.
- Drain is now selected per simulated tick from whether the dose has expired, via `drainPerTick(capability, staminaActiveNow)`. The single-argument overload keeps the old meaning.

### D-0081 - Stamina duration unit measured: 1 unit = 10 ticks

Date: 2026-08-09

Decision: The runtime calibration from D-0080 fired on first use and the learned value is `staminaTicksPerDurationUnit=10`, persisted to the RuneLite profile.

Evidence: Gradle daemon log, 2026-08-09 00:19:12 - `DREW_ROUTE_BENCH stamina calibrated: 1 duration unit = 10 tick(s), current duration=19 (~190 ticks left)`, logged 6 seconds after `You drink some of your stamina potion`. Cross-check: a full dose reads 20 units, and 20 units x 10 ticks = 200 ticks = 120 seconds = the documented 2-minute stamina duration, exactly. The measured unit is therefore corroborated by an independent known quantity, not merely self-consistent. Subsequent `eta predicted=` lines carry a live `staminaTicks=` that decays 190 -> 150 -> 140 -> 130 as expected, and drain reads 34/tick against a no-stamina 114/tick (114 x 0.3 = 34.2, floored) confirming the stamina multiplier path.

Constraint: The unit is a persisted measurement, not a constant. If Jagex ever changes the tick rate of the duration varbit, deleting the config key re-measures it. Do not hardcode 10.

### D-0082 - Only 8 of 25 upstream transport files are ingested

Date: 2026-08-09

Decision: Recorded as a known, deliberate gap rather than a silent one. The generator consumes 8 of the 25 TSV files in the upstream `transports/` folder. 17 files totalling 2,085 rows are absent from `drewshelper-transports.tsv`.

Evidence: Generated file carries exactly 9 categories - BASELINE 5800, AGILITY_SHORTCUT 557, WILDERNESS 331, HOT_AIR_BALLOON 269, QUETZAL 182, GNOME_GLIDER 103, CANOE 45, MAGIC_MUSHTREE 29, GRAPPLE_SHORTCUT 15. Sourced from transports.tsv, agility_shortcuts.tsv, canoes.tsv, gnome_gliders.tsv, hot_air_balloons.tsv, magic_mushtrees.tsv, quetzals.tsv, wilderness_obelisks.tsv.
Absent: boats(154) charter_ships(242) fairy_rings(111) magic_carpets(13) minecarts(85) quetzal_whistle(13) seasonal_transports(440) ships(47) spirit_trees(169) teleportation_boxes(39) teleportation_items(338) teleportation_levers(6) teleportation_minigames(37) teleportation_portals(139) teleportation_portals_poh(136) teleportation_spells(98) teleportation_spells_home(18).

Constraint: **Six config items in Advanced Transportation are therefore dead controls** - `spiritTreesUnlocked`, `fairyRingsUnlocked`, `pohMountedGloryUnlocked`, `pohPortalChamberUnlocked`, `pohPortalNexusTier`, `pohJewelryBoxTier`. They persist values (the profile shows `spiritTreesUnlocked=true`) but no category exists for them to gate. A toggle with no data behind it is worse than no toggle, because it reads as a working feature. Either back them with data or delete them.

### D-0083 - Spirit trees and fairy rings are hub networks, not point-to-point edges

Date: 2026-08-09

Decision: Both files use the same boarding/landing split as `wilderness_obelisks.tsv`: a row with an origin and a blank destination is a **boarding tile**; a row with a blank origin and a populated destination is a **landing tile**. The edge set is the cross product, and the landing row carries the requirement.

Evidence: spirit_trees.tsv = 156 boarding rows + 14 landing rows; fairy_rings.tsv = 56 boarding + 56 landing. Every spirit tree landing row carries a quest (Tree Gnome Village on the base network, plus Song of the Elves for Prifddinas, The Path of Glouphrie for Poison Waste, Pandemonium for Laguna Aurorae). Fairy ring landing rows carry a code in Display info (AIQ, AIR, ...) and only 9 of 56 carry a quest.

Constraint:
- **This is the exact shape that collapsed the wilderness obelisks from 325 edges to 7** on the first generator pass. Any new hub family must be asserted on edge count, not just parsed.
- **Fairy rings have no base-unlock row.** Fairytale II - Cure a Queen gates the whole network and appears nowhere in the data, so ingesting the file as-is would offer rings to an account that cannot use any of them. The base gate must be added in code.
- **Planted spirit trees cannot be detected.** Port Sarim, Etceteria, Brimhaven, Hosidius, Farming Guild and Your House are player-grown; upstream defers them to `PathfinderConfig.isPlantedSpiritTreeAllowed`. Their landing rows carry only Tree Gnome Village, so quest state alone would wrongly claim them.

### D-0084 - Gates near Taverley are absent from upstream transport data

Date: 2026-08-09

Decision: A reported detour around a gate south of the Druids' Circle is most likely an upstream data gap, not a collision-map fault.

Evidence: `transports.tsv` carries 145 rows whose menu option mentions a Gate, so gates are modelled in general. A search of the box x 2850-3060, y 3330-3560 (which contains all of Taverley, the Druids' Circle and the Falador border) returns **zero** gate rows. The same box does contain ladders, doors, stiles, staircases and manholes, so the region is otherwise well covered.

Constraint: Our collision map decodes correctly and is not implicated - verified independently by decoding region 45_54 and confirming that (2900,3473) blocks eastward movement, matching the `Open Door 2861` row that upstream ships for exactly that tile. A collision map that agrees with the transport data at a known landmark is not the cause of a detour elsewhere in the same region.

### D-0085 - Collision map size comparisons against upstream are meaningless

Date: 2026-08-09

Decision: Do not diagnose our `collision-map.zip` by comparing entry sizes or bytes with upstream's. The two use different serialisations of the same logical data.

Evidence: Ours stores each region gzip-compressed inside the zip and pads the payload to a fixed 32,768 bytes (zip entry ~1.1 KB, `comp` approximately equal to `len` because it is already compressed). Upstream stores the raw `BitSet.toByteArray()` output, which truncates trailing zero bytes, and lets the zip deflate it (an all-clear region is `len=1024 comp=11`). A naive comparison reports every one of the 1,429 shared regions as "different" and every region as 2-3x smaller, both of which are artefacts.

Constraint:
- **A region absent from the zip is treated as fully impassable**, not fully open - `loadRegion` returns an empty `DrewsHelperFlagMap` when the entry is missing. 1,297 regions present upstream are absent from ours, concentrated in Zeah/Kourend (rx 16-33) and the eastern lands (rx 53-62). Routing into those areas will fail or detour, and this is the first place to look for any "cannot path to Kourend" report.
- Region lookup is `x / 64, y / 64`; the flag layout is `(plane * w * h + (y-minY) * w + (x-minX)) * flagCount + flag` over a little-endian BitSet, after a 16-byte big-endian header of minX, minY, maxX, maxY. Flag 0 is NORTH, flag 1 is EAST.

### D-0086 - CORRECTION to D-0082: 14 of 25 transport files are consumed, not 8

Date: 2026-08-09

Decision: D-0082 undercounted. The generator's `$FileCategories` consumes **14** upstream files; five of them (boats, charter_ships, ships, magic_carpets, minecarts) all map to `BASELINE`, which is why the category count is 9 while the file count is 14. Inferring the file count from the category count was the error.

Evidence: `$FileCategories` lists transports, boats, charter_ships, ships, magic_carpets, minecarts (BASELINE), teleportation_levers, wilderness_obelisks (WILDERNESS), agility_shortcuts (AGILITY/GRAPPLE), canoes, gnome_gliders, hot_air_balloons, magic_mushtrees, quetzals. BASELINE = 5800 rows against transports.tsv's own 5414 confirms the extra files fold in.

Constraint: After this change the genuinely un-ingested set is **11 files / 1,538 rows**: quetzal_whistle, seasonal_transports, teleportation_boxes, teleportation_items, teleportation_minigames, teleportation_portals, teleportation_portals_poh, teleportation_spells, teleportation_spells_home - plus fairy_rings and spirit_trees, which this session ingested. Never infer the consumed-file list from the category list again; read `$FileCategories`.

### D-0087 - Spirit trees and fairy rings are automatic; only player-grown trees keep a box

Date: 2026-08-09

Decision (Mytharium's ruling): both networks route by default, gated purely by quest state. The existing "Unlocked: Spirit Trees" control becomes "Unlocked: Planted Spirit Trees" and gates only the six player-grown trees. The "Unlocked: Fairy Rings" box is deleted outright.

Implementation:
- New categories `SPIRIT_TREE` (640 edges) and `FAIRY_RING` (3,024 edges) are in `ALWAYS_ENABLED`; `PLANTED_SPIRIT_TREE` (1,335 edges) is opt-in.
- **An edge is planted if EITHER end is planted.** Boarding a tree you grew to reach the Grand Exchange is just as impossible as landing at one, so the weaker end decides the category.
- **Fairytale II - Cure a Queen is injected into every FAIRY_RING edge by the generator** (`$NetworkQuest`). Upstream records the network's base unlock nowhere, so ingesting the file as-is would offer 3,024 edges to an account that cannot use one. Injecting it as data means the existing quest reader enforces it with no new code path.
- All 14 quest names used by the two networks were verified byte-exact against `Quest.class` in `runelite-api-1.12.35.jar`, including the injected Fairytale II string.

Constraint:
- **The planted split is read from upstream's own section annotation** (`planted spirit tree|player-owned house`), not a hardcoded list of destination names, so a future upstream planted tree is picked up automatically.
- **It is opt-in per file via `$PlantedSplitFiles`.** A section comment applies to every row after it, and `fairy_rings.tsv` has two stray comments and no section structure - an unguarded split tagged **594 unrelated rings** as player-built. Never enable the split for a file that is not actually sectioned.
- **A hub family must be asserted on edge count, never on parse success.** This is the shape that collapsed the wilderness obelisks from 325 to 7 while reporting success. `DrewsHelperSpiritTreeFairyRingTest` asserts floors on all three categories.
- Known gap, deliberately not enforced: fairy rings also need a Dramen or Lunar staff (waived by the elite Lumbridge & Draynor diary). Upstream does not record it and inventing an item requirement risks silently deleting usable routes, so we stay permissive and offer the route.

### D-0088 - Verified upstream data gaps get an overrides file, never a hand-edit

Date: 2026-08-09

Decision: `tools/transport-overrides.tsv` holds verified additions to upstream's data and is merged by the generator. Hand-editing the generated resource would lose the fix on the next regeneration.

First entry - the Taverley/Falador wall gate, reported by Mytharium as roughly doubling his travel time:
- The wall is one tile thick on the EAST edge of x=2935, spanning y=3448..3455. Decoded from our own `collision-map.zip` region 45_53: `canMoveEast(2935, y)` is false across that span.
- The archway is the two-tile opening at **y=3450 and y=3451** - y=3449 and y=3452 are solid gatehouse, and x=2936 is open eastward at both rows. The only barrier is the gate object on the 2935->2936 edge.
- Upstream ships 145 rows whose menu mentions a Gate, but a search of x 2920-2955, y 3430-3475 returns **zero rows of any kind**. Mytharium confirmed upstream's own Shortest Path detours here too.
- Fix: 4 rows (2 tiles x 2 directions). BASELINE goes 5,800 -> 5,804.

Constraint: every override row must cite its evidence in the file, prove the tile is blocked in the collision map, prove upstream has no row in the whole bounding box, and add both directions of every tile of the opening. Never invent a crossing that does not exist in game. The rules are written at the top of the overrides file itself.

### D-0089 - A renamed control gets a new config key when its meaning changes

Date: 2026-08-09

Decision: the planted spirit tree box uses key `plantedSpiritTreesUnlocked` (default false) rather than reusing `spiritTreesUnlocked`.

Evidence: Mytharium's profile carried `drewshelper.spiritTreesUnlocked=true`, set when the box meant "may I use spirit trees at all". The new box means "I have grown spirit trees". Reusing the key would have silently claimed six trees he never planted, and RuneLite always prefers a stored value over a changed default.

Constraint: when a control's question changes, change the key. The stale key is harmless; an inherited answer to a different question is not.

### D-0090 - Both logging controls removed; ETA logging is permanently on

Date: 2026-08-09

Decision (Mytharium's call): the "Log Benchmark Movement" and "Log ETA Accuracy" controls are deleted from Settings.

Implementation:
- **Movement benchmark: retired but not deleted.** It existed to prove the overlay's step model matched the client's own walking, and it has done that. It now sits behind `ROUTE_BENCHMARK_ENABLED = false` in `DrewsHelperPlugin`, so it is one line to revive if the walking model is ever changed again. Deleting `DrewsHelperRouteBenchmark` (1,152 lines) was deliberately NOT done - the cost of keeping it is zero and the cost of rewriting it is not.
- **ETA accuracy logging: kept, and made unconditional.** Two lines per journey. Its entire purpose is catching a forecast that drifts when nobody is watching, so a control it could be switched off by is a control that would leave it off.

Constraint: a config item removed from the interface leaves its stored value in the RuneLite profile. Harmless here (nothing reads those keys any more), but never re-add a key with the same name and a different meaning - see D-0089.

### D-0091 - Tree Gnome Village routes correctly; the maze is meant to be skipped

Date: 2026-08-09

Decision: no fix required. Reported as "our route can't find its way through the Tree Gnome Village maze"; the data and the collision map both say the router is doing the right thing.

Evidence, from an 8-direction flood fill of our own collision map over x 2440-2600, y 3100-3240:
- **The village interior is a sealed 357-tile pocket**, bounding box x 2514-2547, y 3158-3175. It contains the TGV spirit tree (2542,3167). Nothing outside it is reachable on foot, and it is reachable from nothing outside.
- The outer world component around it is 13,319 tiles.
- **Exactly ONE candidate crossing exists** between the two: (2515,3161) <-> (2515,3160).
- Upstream ships a transport row for precisely that crossing - `Squeeze-through Loose Railing 2186`, both directions, no requirements - and it IS in our generated data.
- The maze itself is crossed by `Follow Elkoy 4968`, (2504,3191) <-> (2515,3159), varplayer `111>0` (Tree Gnome Village started), duration 5. Also present in our data.
- Walking the maze the long way is 205 tiles; Elkoy is 5 ticks. Preferring Elkoy is correct, not a failure.

Constraint:
- **A dashed straight line across a maze is a transport hop being drawn, not the router giving up.** This is the second time that visual has been read as a bug. The HUD says "Using Transport x3" without naming them, which is what makes it ambiguous.
- The flood-fill-and-compare-components technique is the right first move for any "it won't route through X" report: it separates a sealed map from a failing solver in one pass. **Use 8 directions** - a cardinal-only fill gives false "sealed" answers wherever a gap needs a diagonal.

### D-0092 - Why route solving is slow: objects and hashing, not the algorithm

Date: 2026-08-09

Decision: recorded as the diagnosis behind any future performance work. The algorithm is not the problem; the representation is.

Evidence - our `DrewsHelperWalkingRouteEngine` hot loop:
- `PriorityQueue<SearchNode>` - object heap, one allocation per node, comparator dispatch per sift.
- `Map<WorldPoint, SearchNode> bestNodes` - a `WorldPoint` object hashed and equality-checked on every expansion.
- `new WorldPoint(...)` allocated *inside* the neighbour loop.
- Budget `MAX_EXPANDED_NODES_PER_SEGMENT = 2,000,000`.

Upstream `shortestpath/pathfinder/Pathfinder` over the same map:
- `IntDeque boundary`, `IntMinHeap pending`, `VisitedTiles visited` (bitset), `PrimitiveIntList` neighbours.
- Coordinates are **packed into a single int** throughout (`WorldPointUtil.packWorldPoint` / `unpackWorldX`), so a node is an `int`, not an object.
- Zero allocation and zero hashing in the hot loop.

That is the whole gap: they compare and index integers, we allocate two objects and hash a `WorldPoint` on every node expanded. At the hundreds of thousands of nodes a cross-map route touches, that is an order of magnitude, not a few percent.

Constraint:
- **`preferClientStyleShortestPath` is NOT the main cost.** It runs a second reverse Dijkstra per segment, but bails when the path exceeds `MAX_A_STAR_TIE_REFINEMENT_DISTANCE` (256), so it never fires on the long routes that feel slow. Do not "optimise" it first.
- The single highest-value change is replacing the `WorldPoint`-keyed map with a primitive int-keyed map over packed coordinates. That is most of the win without touching the search logic.

### D-0093 - CORRECTION to D-0091: the maze IS the intended route, and Elkoy is mispriced

Date: 2026-08-09

Correction: D-0091 concluded "no fix required" and described Tree Gnome Village as a sealed pocket. Both framings were wrong in a way that hid a real defect.

- **"Sealed" was the wrong word.** The village is not sealed - it is *mazed*. The maze is the intended way in for a player who has not unlocked spirit trees, and the loose railing at (2515,3160)<->(2515,3161) is the door at the end of it. Calling the component "sealed" made the maze walk look like an impossibility rather than the designed route, which is exactly why the real fault went unexamined.
- The 205-tile maze walk is not a fallback. It is what the game expects, and the router does produce it when waypoints force it (confirmed in game: 205 tiles, ETA 1:08, path tracing maze corridors).

Evidence for the real defect, from every transport edge with an endpoint in the village box (x 2514-2547, y 3158-3175):
- **`Follow Elkoy 4968` is the ONLY non-spirit-tree edge that leaves the village**, `2515,3159 -> 2504,3191`, gated on varplayer `111>0`, **duration 5**.
- Walking the maze between the same two areas is ~205 tiles, i.e. 100+ ticks even running.
- 5 ticks versus 100+ means **Elkoy wins every single village<->outside route**, unconditionally. It is not a tie-break; nothing else can ever be chosen.

Constraint:
- **A transport's duration is a claim about the real world, and an unrealistic one silently deletes every alternative.** Elkoy is an NPC escort - dialogue plus a scripted walk - and cannot plausibly be 3 seconds. Upstream's 5 is the value we inherited, not one anyone measured.
- **Do not "fix" this by inventing a duration.** The number has to be measured in game the same way the stamina varbit unit was, or the fix just replaces one unverified constant with another.
- The railing is NOT the teleport, despite reading that way in game. It is a one-tile step that happens to sit immediately before the Elkoy hop, so the dashed Elkoy line appears to start at the railing. This is the third time this session a drawn transport hop has been reported as a routing bug - see the HUD naming note in D-0091.

### D-0094 - CORRECTION to D-0093: Elkoy is priced correctly. There was never a routing defect.

Date: 2026-08-09

Correction: D-0093 concluded that `Follow Elkoy` at duration 5 was "obviously wrong" because an NPC escort "cannot plausibly be 3 seconds". Measured in game by Mytharium: **the escort takes 3 seconds each way.** At 0.6s per tick that is exactly **5 ticks**. Upstream's number is right and mine was the guess.

The gate is also exactly right. Mytharium confirmed Elkoy guides both directions **once Tree Gnome Village has been started** - which is precisely what varplayer `111>0` encodes (started, not completed). Every field on that edge matches the game.

So the Tree Gnome Village report resolves to: **no defect anywhere.** The collision map is right, the transport rows are right, the durations are right, the gate is right, and the router's preference for Elkoy over a 205-tile maze walk is correct.

Constraint - and this is the durable part, because it is the second time in one session:
- **Twice I reasoned "that number can't be right" about a game mechanic I had no way to verify, and twice the data was right.** First the collision-map size gap (an encoding artefact I nearly reported as data loss), then this. An intuition about how long an in-game action "should" take is not evidence.
- The rule that keeps working is the one used on the stamina varbit: **when the answer depends on a fact about the game, get it measured rather than reasoned about.** One question to the player settled this in a single round; two rounds of my own analysis had produced a wrong conclusion.
- Do not "fix" the Elkoy duration. It is correct.

### D-0095 - The real fault was visibility, and it is fixed at the overlay

Date: 2026-08-09

Decision: a transport hop is now named on the HUD and highlighted in the world. This is what the Tree Gnome Village episode actually needed - three rounds were spent on a router that was behaving correctly the entire time.

Implementation:
- **Naming.** `DrewsHelperTravelEstimate.displayLabel(edge)` replaces the family name ("Transport") with the real in-game menu text. Upstream labels carry a trailing object id ("Follow Elkoy 4968") and hub destinations a menu index ("1: Tree Gnome Village"); both are stripped, and hub networks are prefixed with the network so the destination alone cannot be mistaken for a walk ("Spirit tree: Grand Exchange"). A label that reduces to nothing, or to nothing but digits, falls back to the family so the cell is never empty.
- **Bounded.** The HUD shows the first 3 distinct names in route order then "+N more" - a long route can touch a dozen distinct shortcuts and the panel is a fixed 320px.
- **Highlighting.** `DrewsHelperRouteTileOverlay` marks the two tiles either side of every transport jump in cyan with a 2px outline. Cyan deliberately: it is the one strong colour absent from the configurable path/waypoint palette, so a highlight can never be mistaken for a route line the user chose.

Constraint:
- Detection reuses the existing `DrewsHelperRouteSnapshot.isTransportJump` (non-adjacent consecutive path tiles). No engine change was needed - the edge was already in hand at the point the HUD label is built, in the estimate's own transport loop.
- **A drawn straight line across terrain is indistinguishable from a router giving up.** That ambiguity cost three rounds here and had been reported twice before. Any future route surface that draws a non-walking movement must say what it is.
### D-0096 - Every hub transport family had lost the id needed to highlight it

Date: 2026-08-09

Finding: highlighting worked on gates, NPCs and agility shortcuts but never on spirit trees. The cause was not the overlay - it was the generator, and it affected **5,627 edges across eight families**, not just trees.

Counts before the fix:

| Family | with id | without id |
|---|---|---|
| Agility / grapple | 572 | 0 |
| Spirit tree + planted | 0 | 1,975 |
| Fairy ring | 0 | 3,024 |
| Glider / balloon / quetzal / canoe / mushtree | 0 | 628 |

Root cause: hub networks store **boarding** rows and **landing** rows separately, and the interactable id only ever appears on the boarding row. The cross-product that builds the edge kept the landing row's label and discarded the boarding row's:

```
boarding:  Travel Spirit tree 26261      <- the id is here
landing:   6: Prifddinas                 <- and the cross-product kept this one
```

So the edge came out labelled `6: Prifddinas` with no trailing id, `targetId` returned -1, and the overlay fell back to marking the tile - which for a spirit tree is a patch of grass beside it. Agility shortcuts were unaffected only because they are direct rows, where the single row carries both.

Implementation: `Get-TrailingId` / `Add-TrailingId` in the generator carry the boarding row's id onto the hop, and the record now also reads the id from the `menuoption menutarget objectid` column so families whose destination name won over the menu option (boats, canoes, balloons) are covered too. Rows 12,334 -> 12,388, because distinct interactables stopped collapsing as duplicates.

Constraint:
- **In any cross-product over two row shapes, name which side each field must come from.** The label and the id came from opposite rows and nothing in the code said so, which is why one silently won.
- A test now asserts that **every row in all 8 hub families ends in an id**. A regression here is invisible in game until someone tries to use that family, so it has to fail the build instead.

### D-0097 - Spirit trees are impostor objects; a scene id comparison can never match them

Date: 2026-08-09

Finding: with ids restored, spirit trees still did not highlight while gates and agility shortcuts did.

Root cause: objects whose appearance depends on game state are placed in the scene under a **base id** and swapped at runtime to one of several **impostor ids**. A spirit tree is exactly that kind of object. The transport data records the id the player *clicks* - the impostor - and the overlay was comparing it against the base id sitting in the scene. Those are never equal.

Gates, ladders and agility shortcuts are plain static objects, so their scene id *is* their real id. That is precisely why only trees failed, and why the symptom looked family-specific rather than structural.

Implementation: `matchesObjectId(sceneId, targetId)` in `DrewsHelperRouteTileOverlay` resolves `client.getObjectDefinition(sceneId)` and checks `getImpostorIds()`, then the currently active `getImpostor()` as a fallback. API presence verified against `runelite-api-1.12.35.jar` before writing it. Confirmed fixed in game by Mytharium.

Constraint:
- **An object id from data is not necessarily the id in the scene.** Any future scene lookup by id must go through the impostor resolution, not `==`.
- The overlay deliberately still does not try to decide whether a given id is an NPC or an object. Nothing in the data records which, so it looks for both and highlights whichever exists. That call was made in D-0095 and paid off here without needing a change - gliders turned out to carry an NPC id (`Glider Captain Errdo 10467`) while trees carry an object id.

### D-0098 - Route solve time was O(nodes x depth^2), caused by ArrayList.add(0, x)

Date: 2026-08-09

Finding: solve time was strongly superlinear - 1.2 us/node at 1,371 expanded nodes rising to 117 us/node at 82,434, with a worst measured solve of **9,674ms**. Two runs with near-identical node counts (8,956 -> 14.5ms vs 7,724 -> 139ms) differed 10x, proving `expandedNodes` was not what drove the clock.

Root cause, read out of the code rather than inferred:

```java
private List<Integer> clientMovePreferences(WorldPoint target)
{
    List<Integer> preferences = new ArrayList<>(distance);
    SearchNode node = this;
    while (node.previous != null)
    {
        preferences.add(0, movePreferencePenalty(   // insert at index 0 - shifts the whole array
            node.previous.point,
            new Move(node.directionX, node.directionY),
            target));
        node = node.previous;
    }
    return preferences;
}
```

`add(0, x)` shifts the entire backing array on every insert, so building one list is O(depth^2). `compareClientMovePreference` built **two** of them, and it is reached from `isBetterPathToSamePointThan` - called for **every neighbour of every expanded node**. The whole A* was therefore O(nodes x depth^2).

It hid because the comparison short-circuits on unequal distance, so the expensive branch only runs on **ties** - which are near-universal in a uniform-cost grid.

Implementation: the comparison now walks both chains backwards without materialising either sequence. Each node's sequence is its parent's plus one element, so once the chains reach a common ancestor (`a == b`) every earlier element is identical by construction and cannot hold the first difference; and walking backwards visits indices high-to-low, so the last difference seen is the earliest one, which is what lexicographic order wants. Depth is carried as a `steps` field - measuring it by walking would reintroduce the cost being removed. **O(depth^2) -> O(divergence).**

Measured, from Mytharium's own daemon log, same route straddling the relaunch:

```
BEFORE   expanded=116,043  ->  5,891.53ms   50.8 us/node
AFTER    expanded=116,282  ->    430.90ms    3.7 us/node
```

Subsequent solves ran 57-79ms at 56k-71k nodes, about **1.1 us/node** - the same order as the reverse Dijkstra (0.76 us/node) that was used as the control. Worst recorded solve went 9,674ms -> 78.69ms.

Constraint:
- **Five theories were proposed and disproved before this one**: packed-int representation, a `PriorityQueue.remove(Object)` hotspot (no such call), the ranking pass being uncounted (it is counted), `edgesTo()` being an unindexed scan (it is a proper HashMap index), and general allocation pressure. Every one was killed by measurement or by reading the code.
- **When per-node cost scales with route length, read the hot path before theorising.** The winning move was finding a same-file control - two loops over the same map differing 200x per node - which localised the cost to one function in a single step. Inference had produced five wrong answers first.

### D-0099 - The heuristic was inadmissible once transports exist, so teleports were pruned

Date: 2026-08-09

Finding: with waypoint #1 on the Grand Exchange and the player stood beside the Battlefield of Khazard spirit tree, the router produced a 517-tile route via the Ardougne docks and a boat, ignoring a one-hop spirit tree. The edge exists and is legal:

```
SPIRIT_TREE  2555,3259 -> 3185,3508  [4: Grand Exchange 26263]  quests=Tree Gnome Village
```

Root cause: three constants that are individually reasonable and jointly broken.

| | |
|---|---|
| Walking step | `costUnits = 1` per tile |
| Transport step | `max(1, 2 x durationTicks)` - a spirit tree hop costs **6** |
| Heuristic | `max(abs(dx), abs(dy))` - straight-line **tiles** |

A spirit tree carries you ~630 tiles for a cost of 6, but the heuristic standing at the tree still charges ~621. That is an over-estimate of roughly 24x, which makes the heuristic **inadmissible**, and the main loop's early break (`node.priority > bestTarget.distance`) then discards the teleport outright as soon as any cheaper-looking route reaches the target. The boat wins because crossing water collapses its heuristic and its f-value plunges; the tree sits beside you with its heuristic unchanged and never competes.

Implementation: one scalar per segment, over `transportGraph.allEdges()` (already policy-filtered at load):

```java
transportArrivalBound = min over edges e of ( transportCostUnits(e) + heuristic(e.destination, target) )
priority = distance + Math.min(heuristic(n, target), transportArrivalBound)
```

Any route either walks the whole way - at least Chebyshev - or uses a transport, and then its **last** hop alone already costs `cost(e) + chebyshev(e.destination, target)`. Minimising over all edges ignores the cost of *reaching* that transport, which only lowers the bound, so it remains valid. It is also consistent, so no node needs re-expanding. `remaining` deliberately keeps the raw Chebyshev value - it is only a tie-break, and capping it there would flatten ordering far from the target.

Measured A/B on identical start tiles, pre-fix engine vs fixed engine:

| Start | pre-fix | fixed |
|---|---|---|
| 2570,3245 | 418 steps, 2 hops, 131ms | 86 steps, 1 hop, 34ms |
| 2575,3250 | 404 steps, 2 hops, 73ms | 72 steps, 1 hop, 25ms |
| 2580,3255 | 403 steps, 2 hops, 69ms | 78 steps, 1 hop, 37ms |
| 2600,3260 | 382 steps, 2 hops, 61ms | 88 steps, 1 hop, 31ms |

Constraint:
- **This was never a Khazard problem.** Every long-range transport was systematically under-used whenever a moderately cheap walking or boat route existed. It surfaced there only because the player happened to be standing next to a tree.
- **A capped heuristic was predicted to be slower and is measurably faster** - 2x to 4x on these routes. Capping lets a cheap teleport route be found almost immediately instead of grinding out a long walk before the search can terminate. The prediction was wrong in the user's favour and is recorded here so it is not "corrected" back.
- **The first regression test written for this passed on the broken engine.** It started *on* the spirit tree, which is the one position that can never reproduce the fault: the start node is expanded first unconditionally, so its transport edges are offered before anything can out-compete them on f-value. A regression test must be shown to fail against the unfixed code before it is trusted - the A/B table above is that proof.

### D-0100 - A green build is not evidence that the build ran

Date: 2026-08-09

Finding: a `gradlew test ... | findstr ... | more` pipeline returned exit 0 while reporting nothing, because the exit code came from the last element of the pipeline rather than from Gradle, and `-q` had suppressed the test count. A subsequent run reported `Task :compileJava UP-TO-DATE`, which would also have been consistent with the edit never being compiled.

Constraint:
- **Capture build output to a file and read it.** Do not pipe it through a filter that can swallow the exit code.
- **Prove compilation from the artifact, not the report.** The check that settled it was the class file itself: `DrewsHelperWalkingRouteEngine$SearchNode.class` written 03:06:21 against a source written 03:06:11, containing the new `moveOf` and `steps` symbols and no `clientMovePreferences`, with the test results file written 03:06:23 - after both.
- Gradle does **not** put test stdout into the result XML by default. A diagnostic that needs to report values should write its own file rather than printing.

### D-0101 - The Wilderness toggle gates obelisks and levers only, not routing through the Wilderness

Date: 2026-08-09

Finding: Mytharium reported being routed through the Wilderness with "Use: Wilderness Transports" off. The setting is genuinely off - the key is absent from his profile and `wildernessTransportsEnabled()` defaults to `false` - and the policy is correctly excluding the `WILDERNESS` category. The category simply does not contain what the name suggests.

What `WILDERNESS` actually contains, all 331 rows:

```
   324  the six obelisk destinations (1: Level 13 ... 6: Level 50)
     7  Pull Lever
```

What is **not** in it:

```
   668  Cross Wilderness Ditch          -> BASELINE
 2,060  edges with both ends inside the Wilderness  -> BASELINE
   598  ... the same, fairy rings
   211  ... agility shortcuts
   210  ... planted spirit trees
    29  ... magic mushtrees
```

So walking into the Wilderness over the ditch, and every shortcut and fairy ring inside it, are ungated. The toggle is doing exactly what its description says ("Allow dangerous Wilderness lever and obelisk route edges") and nothing more; what the player expects from it is "do not route me through the Wilderness".

This surfaced now, and probably because of D-0099: routes built out of cheap teleports were previously pruned by the inadmissible heuristic, so a fairy-ring chain through the Wilderness would not have been chosen before.

Constraint:
- **Not yet fixed - the scope change is a decision for Mytharium, not an inference.** Widening the toggle to exclude every edge inside the Wilderness would also make a deliberate waypoint in the Wilderness unroutable, so the sane rule is "avoid unless the start or a destination is inside it".
- **Do not define the Wilderness boundary from memory.** The 668 ditch-crossing rows give the boundary line empirically; derive it from those rather than from a remembered coordinate.

### D-0102 - Wilderness routing avoidance shipped; D-0101 is superseded

Date: 2026-08-09

Correction to D-0101: the original "not yet fixed" note is stale, and its broad high-y Wilderness count was wrong. `y >= 3522` is not the Wilderness; it includes the northern half of the world, such as Zeah, Rellekka, Etceteria, Piscatoris, and instanced regions. A simple north-of-ditch rule would have excluded thousands of valid non-Wilderness edges.

The implemented boundary is:

```text
x 2944-3392
y 3522-3968
plane 0
```

That box was derived from the ditch/entry rows and then narrowed after checking a trap: Prifddinas's spirit tree lands at `(3274,6123)`, so an x-band without a y ceiling would falsely classify Prifddinas as Wilderness.

Implementation rule: when `Use: Wilderness Transports` is off, routes refuse transport edges that enter the Wilderness box from outside it. Two escape hatches remain:

- If the route starts inside the Wilderness, it may route out or move around.
- If a waypoint is inside the Wilderness, the player deliberately asked to go there, so the route may enter.

Constraint:
- Future Wilderness work must use the same derived bounded box unless live evidence proves it wrong. Do not replace it with `y >= 3522` or a memory-based level line.
- The toggle now means "avoid routing into the Wilderness unless I deliberately start or place a waypoint there", not only "allow obelisk/lever transports".

### D-0103 - Teleport spells become innate route edges, cooldowns are locked state, banking is a graph step

Date: 2026-08-09

Myth's direction: magic-tab teleport spells, home teleports, and later minigames/items should be innate routing options like canoes or agility shortcuts once the account can actually use them. The old separate "Teleport Options" buttons should not survive as independent route buttons after the supported families are built.

Decisions:

- Start with home teleports as the next code slice. They exercise originless teleport edges, spellbook/unlock vars, and the real cooldown gate without requiring a rune model.
- Treat cooldown-active teleports as locked. The router ignores them and picks the next shortest route, whether that is walking, another teleport, a spirit tree, a ship, or any other legal transport.
- Cooldown var syntax is upstream's `@`, for example `892@30`. The stored var value is an epoch-minute timestamp, not a countdown. The check is `(nowMinutes - storedMinutes) > cooldownMinutes`.
- Unknown cooldown var value means locked. This intentionally differs from ordinary quest/var requirements, where unknown is permissive to avoid silently deleting valid routes from typo-prone metadata.
- Destination-only teleport rows are originless edges. The generator must emit them immediately with sentinel source `-1,-1,0` (`ANYWHERE`) instead of leaving them in `$destOnly` to be silently dropped.
- Originless dedup must include requirement fields for these files. Lumbridge Home Teleport has multiple rows that differ by VarPlayers animation state/duration; collapsing them to one row makes the teleport work only in one state.
- Originless edges must be visible to every lookup path: step generation, edge legality, and travel-estimate/action labeling. Otherwise the route can take a teleport while the HUD reports walking or uses the wrong duration.

Rune/spell plan after home teleports:

- Add spell teleports only after carried-supply gating is wired.
- Use real Magic level, spellbook/unlock vars, inventory, equipped gear, and rune pouch contents.
- Expand symbolic rune requirements at generation time into the existing item-requirement grammar. Use upstream's rune/staff/combination-rune table, not OSRS wiki memory typed by hand.
- Staffs usually matter through equipment, not inventory. Rune pouch contents are vars, not ordinary item counts.
- Bank contents do not count as castable from anywhere.

Banking decision:

- Bank-aware teleports are allowed later, but only as a real route step. A bank route is not a shortcut around item requirements.
- RuneLite bank contents are usable only when RuneLite has a known bank cache because the player opened the bank. If the bank cache is unknown, do not invent a bank route.
- Bank tile data should come from upstream first. Ask Myth for missing coordinates only if upstream lacks a needed bank.
- The search state becomes `(tile, bankedYet)`. Bank access transitions from not-banked to banked at an honest withdraw cost.
- Highlight the exact needed runes/staff/items in the bank UI so the withdraw cost can be lower and more consistent.
- Let A* decide whether banking is worth it. The graph should select `spirit tree -> bank -> withdraw -> teleport -> walk` only when that whole route is shorter than the alternatives.

Operational rule once the bank slice exists:

```text
If carried supplies can cast it -> use teleport normally.
If carried supplies cannot cast it but known bank has supplies -> consider route-to-bank + withdraw + teleport.
If bank is unknown or lacks supplies -> treat teleport as locked.
If cooldown is active -> treat teleport as locked.
```

Constraint:
- Do not copy the old Drew Shortest Path/vendored route stack back into the plugin. Adapt the upstream metadata and proven concepts into the current Drew-owned route engine.
- Do not ship `teleportation_spells.tsv` before rune/equipment/pouch gating exists; false teleport offers are worse than under-offering.

### D-0105 - Generated resources keep the generator's own bytes, and regeneration is proved by set diff

Date: 2026-08-09.

`src/main/resources/drewshelper-transports.tsv` is generated, never hand-maintained. The generator writes UTF-8, no BOM, LF (`$LF = [string][char]10`), and the committed blob is LF. The post-upload CRLF normalization pass hit it anyway and produced a content-identical but byte-different file.

- Never line-ending-normalize a generated file. That pass exists for hand-edited sources that must match Windows repo style. Generated output matches its generator instead.
- Regeneration must be byte-idempotent. Regenerating from unchanged inputs must leave the file's sha256 unchanged. A difference is a defect in the writer or in post-processing, not noise to wave through.
- A CRLF'd copy is the worst kind of wrong here. It reads as a whole-file diff that buries the real change, and it silently reverts on the next regeneration.

Proving a regeneration is safe means an A/B on the same generator, not a row count. Withhold the one new input file, regenerate, and set-diff every pre-existing row. A count is compatible with losing N rows and gaining N others; a full-row set diff is not. Where labels are expected to change, fall back to the `category|source|destination` edge key to separate a relabel from a real loss.

- `tools/transport-overrides.tsv` is an input to the generated resource and is currently untracked. Until it is committed, regeneration on any other checkout silently drops those verified edges.

Cross-reference: `D-0104` in `CHANGELOG_AGENT_NOTES.md` is the build note for the home-teleport ship. Its durable rules live in D-0103.

### D-0106 - Entering and leaving the Wilderness are separate questions

Date: 2026-08-09.

The Wilderness preference answers "may a route take me in". It was never meant to answer "may I
teleport out", and conflating the two under one `!isInWilderness(from)` test made the router
refuse a spell the game would have allowed.

- Entering is owned by `isWildernessEntryToAvoid`: refuse only when the destination is inside and
  neither the origin nor the segment target is. Unchanged.
- Leaving is owned by the transport's own recorded cap. A home teleport caps at 20, so it is
  offered in levels 1-20 and refused above, which is the game's rule rather than ours.
- Every home teleport destination is outside the box, so an escape can never read as an entry.

Two conventions inherited from upstream rather than reinvented, because divergence here would be
silent: -1 means no cap recorded, and a split transport takes `Math.max` of its two ends.

The level is resolved as a band ceiling (0, 20, 30, 31) from overlapping boxes, not an exact
level from arithmetic. Upstream does the same, and no cap in the data needs finer resolution.

Cross-reference: build notes for this change are `D-0110` in `CHANGELOG_AGENT_NOTES.md`; the
numbering of the two files diverged earlier and is not expected to line up.

### D-0107 - Wilderness avoidance has one escape hatch, not two

Date: 2026-08-10.

The avoidance rule answers one question: may a route take me INTO the Wilderness. The only
legitimate override is the segment target being inside, because then the player asked to go.

Being inside already is NOT an override. It was, and the consequence was that crossing the ditch
disabled avoidance for the rest of the trip - so a route back out could route further in first to
reach Wilderness-side content. The reported case was the Abyss.

Two invariants make the narrower rule safe, and both should be preserved by any future change:

- A destination outside the box never trips the rule, so leaving is always permitted.
- Only transport steps are guarded. Walking is never filtered, so the solver can always walk out
  of the Wilderness under its own power and can never be stranded.

Related and deliberately NOT fixed here: the `Use: Wilderness Transports` toggle only covers the
`WILDERNESS` category, which is obelisks and levers. The ditch, the Abyss chain and every edge with
both ends inside are `BASELINE`. Avoidance is what keeps routes out of the Wilderness; the toggle is
not. Recategorising is a behaviour change for anyone who has the toggle on - see Parked Item 13.

Cross-reference: build notes are `D-0112` in `CHANGELOG_AGENT_NOTES.md`.

### D-0108 - A transport touches the Wilderness in three ways, not one

Date: 2026-08-10. Supersedes the framing in D-0106 and D-0107, which both assumed the only
question was the destination.

Asking only "does this transport END in the Wilderness" misses the transports that START there.
Their source tile can only be reached with Wilderness access, so they are Wilderness content no
matter where they drop you. `Teleport Mage of Zamorak 2581` is the case that proved it: source
3106,3559 inside the box, destination 3035,4852 in Abyssal Space, outside it. Two successive
fixes to the destination test could not see that edge at all.

The rule is therefore three-way:

- ENTERING - source outside, destination inside. Refused.
- LEAVING or MOVING INSIDE - source inside. Refused unless it is a short physical crossing.
- NEITHER END INSIDE. Never refused.

The short-physical-crossing exemption is not a convenience, it is the safety property. Walking
out of the Wilderness means passing the ditch and whatever gates, webs and ladders lie on the
way, and each of those is a transport row. Refuse them and the player is walled in. 16 tiles on
both axes separates them cleanly from network hops: the ditch moves 3 tiles, the Mage of Zamorak
teleport moves over 1,200.

Three invariants any future change must preserve:

- A segment target inside the Wilderness refuses nothing. The player asked to go there.
- Originless transports are exempt by construction, so escaping by home teleport always works.
- Only transport steps are guarded. Walking is never filtered, so the solver can always walk out.

Cross-reference: build notes are `D-0114` in `CHANGELOG_AGENT_NOTES.md`.

### D-0109 - The cache builds the map, the live client checks it

Date: 2026-08-10. Settles where our walking data comes from.

Upstream never built `collision-map.zip` either - it was inherited pre-built from Runemoro, and
nothing in either project imports a cache library. That is exactly why it can be stale and why
it cannot be repaired in place. It ships 1,524 regions; the game cache has 2,936.

So the division of labour is:

- **Route B, the OSRS cache, is the SOURCE.** It is authoritative, complete, offline, and it
  carries object identity - names and menu actions - which a collision bitmap structurally
  cannot. It is also the UPDATE path: a game update refreshes the cache, re-run the task.
- **Route A, the live client, is the CHECK.** Its job is to disagree. When our shipped data and
  the running game differ, that is a data bug and it should announce itself rather than surface
  weeks later as a strange route.

Route A is deliberately NOT the update mechanism. Harvesting by playing would be the slow way to
do something Route B does in one command.

Two properties to preserve in any future change:

- The cache dependency stays in the `cachetools` source set. It is a build-time tool and must
  never enter the shipped plugin jar or the test classpath.
- The dumper keeps a real acceptance fixture. It must find the Falador west wall gate at
  2935,3450 with no hint; if that ever stops passing, the output is not to be trusted.

No XTEA keys are needed - verified, not assumed. Map archives in the live cache decode with a
zero key across all 2,747 populated regions.

Cross-reference: build notes are `D-0117` in `CHANGELOG_AGENT_NOTES.md`.

### D-0110 - Cache-derived transport rows need explicit proof before activation

Date: 2026-08-10. Extends D-0109 for the transport-row import workflow.

Route B can generate and rank likely crossings from the OSRS cache, but it must not activate them
directly. Object identity, placement type, orientation, blocked-edge checks and detour severity are
enough to produce a review queue; they are not enough to make the router walk through the edge.

Activation rule:

- `AccessPointRowGenerator` may write review files and live-proven candidate files.
- It must never write `tools/transport-overrides.tsv`.
- A candidate becomes copyable only when its normalized edge key matches Route A live-client proof:
  `DREW_MAP_VALIDATE   x,y,plane DIR OURS_BLOCKS_LIVE_OPEN`.
- Even then, the row still needs human review and an evidence comment before being copied into the
  active override file.

Reason: a false positive transport row is worse than a missing shortcut. Missing rows cause detours;
false rows send the player straight into a wall, locked door or instance-only object.

Cross-reference: build notes are `D-0121` in `CHANGELOG_AGENT_NOTES.md`.

D-0111 (2026-08-10) - In-game verification only counts when the client was launched
through run-drews-helper-dev.bat.
  Rule: before accepting any in-game result as evidence, confirm the run actually
  contained our plugin. The cheap check is a DREW_ line in
  %USERPROFILE%\.runelite\logs\client.log; no DREW_ lines means no Drew's Helper, and the
  result is void rather than negative.
  Why: the plugin is installed by overwriting a plugin-hub jar, and RuneLite silently
  re-downloads the stock jar over it. A test can therefore run to completion, feel
  normal, and measure someone else's plugin. This burned a full proof run on 2026-08-10.
  Corollary for C2: never ask for a test result to be pasted back when the artefact is
  readable over SSH - read the log directly and check this first.

D-0112 (2026-08-10) - A closed door can never be Route A evidence.
  Rule: when hunting door/gate evidence with Validate Map Data, the door must be OPEN at
  the moment the scene is validated. A shut door is agreement, not a mismatch: live says
  blocked, our map says blocked.
  Why: this is not a bug in the validator, it is what the validator measures. It means
  "walk the building and open every door" only works if the scene is re-validated after the
  doors are open - which is why validation now repeats on an interval instead of firing once
  on arrival. It also means a door that is normally shut will only ever be provable by
  someone standing there with it open.

D-0113 (2026-08-10) - Never sample validator output; capture all of it.
  Rule: evidence intended to be matched against a candidate list must be complete. A cap
  that truncates in iteration order produces a spatially sorted sample, and matching that
  against a world-wide candidate list yields zero hits while looking like a clean negative.
  Why: 2,666 real mismatches were reduced to the first 25 per scene, all clustered on the
  west edge, and the generator honestly reported "0 proven". The data was fine; the
  sampling destroyed it. If output must be bounded, bound it randomly or by region, and say
  in the output what was dropped.

D-0114 (2026-08-10) - tools/transport-overrides.tsv is NOT read at runtime.
  Rule: adding rows to transport-overrides.tsv changes nothing on its own. The router loads
  src/main/resources/drewshelper-transports.tsv, and the override rows only reach it when
  tools/generate-drewshelper-transports.ps1 is re-run with -TransportDir pointing at the
  sibling "Drew Shortest Path" checkout's src/main/resources/transports folder.
  Why: promoting rows and stopping there produces a change that looks complete in git, passes
  every test, and has zero effect in game. Always regenerate, and always verify with the
  set-diff acceptance test in tools/README.md - old source->destination set must be a subset
  of the new one. Never verify a regeneration by row count.

D-0115 (2026-08-10) - An exception in an overlay is invisible except as "nothing drew".
  Rule: when something stops rendering in RuneLite, grep the dev console for "Error during
  overlay rendering" before touching the drawing logic. OverlayRenderer.safeRender catches
  the throw, so the client keeps running normally and the only symptom is absence.
  Corollary: everything after the throw point in that overlay's render() is lost too, so one
  bad call takes out unrelated features that happen to be drawn later in the same method.
  Why: a full render-order feature (door outlines) looked like a logic bug for a whole test
  cycle when it was a one-line unguarded API call.

D-0116 (2026-08-10) - Never report a background Codex job as "in flight".
  Rule: work is either verified and shipped, or it is not started. Do not tell the user a fix
  is building and will land shortly - either finish it inside the turn or say plainly that it
  has not been done.
  Why: a Codex job launched with run_in_background was killed by session teardown on
  2026-08-10 with no completion record. It was reported as "building", so the user re-ran the
  in-game test against unchanged code and reported the same bug back. That cost him a full
  test cycle and made a fix that had never existed look like a fix that had failed.
  Corollary: for edits of this size, do them inline. The Codex round trip is only worth it
  when the work is large enough that the handoff cost is recovered.

D-0117 (2026-08-10) - The collision map becomes door-aware, and a door costs 1 tick.
  Decision: rebuild to a 4-flag format carrying "blocked by an openable object" alongside
  "passable", and charge a door crossing 1 tick (= +2 cost units, since the router prices in
  half-ticks and a D-tick transport already costs 2*D).
  Why: rebuilding to the existing blocked/not-blocked format would fix coverage and wrong-wall
  errors but would not fix a single door - a shut door is indistinguishable from a stone wall
  in that format, so the manual prove-and-promote loop from item 2 would be needed region by
  region forever. The door bit retires that loop. The tick is not free because opening really
  does cost a tick, and a route that pretends otherwise will under-estimate every indoor ETA.
  Chosen by Mytharium 2026-08-10 after being shown the measured cost of each option.

D-0118 (2026-08-10) - interactType, blockingMask and wallOrDoor do NOT encode traversability.
  Rule: never reach for these three ObjectDefinition fields to decide whether something blocks
  movement or whether a thing is a way through. Measure instead.
  Why: twice now. D-0119 measured them against gates vs chests and found complete overlap. The
  SOLID-bucket split then found confirmed doors and unexplained SOLID edges are IDENTICAL on
  interactType (2) and blockingMask (0), with 1,159 of 1,170 SOLID placements sharing the
  doors' own value. A field that gives the same answer for a door, a chest and a wall is not
  a traversability field, whatever its name suggests.
  What to use instead: placement geometry - locType and orientation - plus live collision data
  from the validator. That combination is what actually worked for both item 1 and item 2.

D-0119 (2026-08-10) - STANDING PREFERENCE: do it right, not fast.
  Mytharium, in his own words: "My goal is to do things right, not quickly."
  This is a standing instruction for the whole project, not a comment on one task. When a
  correct-but-slower route and a quick-but-partial route both exist, take the correct one and
  say why. Do not offer speed as the headline trade unless he asks for it.
  In practice that means: measure before building, derive rules from data instead of memory,
  prove a change on ground truth before shipping it widely, and prefer the approach that
  covers the whole problem class over the one that covers today's example.
  Concrete application: for item 3's door bit he chose the shape-derived method over seeding
  from the known-openable list, explicitly on these grounds, even though seeding lands sooner.
  Corollary already learned the hard way: "quick" has repeatedly turned out to be slower here.
  The 25-row log cap, the getImpostor crash and the killed background job each cost a full
  test cycle. Careful has been the fast path on this project.

D-0120 (2026-08-10) - An UNKNOWN edge in the v2 collision map defaults to BLOCKED.
  Rule: when the shape table cannot determine what a placement blocks - today that is every
  locType 1 - the builder writes the edge as blocked, never as passable.
  Why: the two failure modes are not symmetric. A wrongly-blocked edge costs a detour, which
  is annoying and self-evident. A wrongly-passable edge makes the router plan a path through a
  solid wall, and the player just stops walking with no explanation. The second is the failure
  we already measured most of in item 3 (the "we allow, the game blocks" direction outnumbers
  the missing-door direction and grows with height), so guessing optimistically would make the
  exact problem we are fixing worse.
  Chosen by Mytharium 2026-08-10, consistent with D-0119.
  This is a floor, not a resting place. UNKNOWN edges are to be resolved with live ground
  truth, and the builder must count and report them every run so the number stays visible
  instead of quietly becoming permanent.

D-0121 (2026-08-11) - Backlog #6 resolved: generated map data is tracked when it SHIPS, not before.
  Approved by Mytharium 2026-08-11.

  The framing "generated artifacts do not belong in git" is WRONG for this file, and checking how
  v1 is handled is what settled it:
      src/main/resources/collision-map.zip   938,345 bytes   TRACKED
  v1 is not a build artifact - it is the map the plugin loads at runtime, so it must be in git.
  v2 will need exactly the same treatment. The real question is not "is it generated" but "is it
  shipped yet", and right now it is not: it holds six regions of experimental output and is
  regenerated on every builder run.

  RULE, two phases:
    While experimental - the builder writes to build/collision-map-v2.zip. build/ is already in
    .gitignore, so no tracked binary churns. DEFAULT_ZIP in CollisionMapBuilder carries this.
    At ship time - when the full 2,936-region rebuild passes the proof and the loader switches to
    v2, move the output to src/main/resources/collision-map-v2.zip and track it deliberately in
    one clean commit, exactly as v1 is tracked.

  Applied: DEFAULT_ZIP changed to build/collision-map-v2.zip (writeZip already creates the parent
  directory, so no other change was needed); git rm --cached on the stale root copy and the loose
  file removed, since it regenerates in about seven seconds and an untracked binary at the repo
  root would sit in git status forever.
  Verified after: build/collision-map-v2.zip 5,498 bytes, v1 untouched at 938,345, no untracked
  zip in git status, ROUND TRIP OK 6 regions, still-blocked 753 (unchanged - no regression).

D-0122 (2026-08-11) - The straightness yardstick is CERTIFIED. A self-check must prove its
  premise, not assume it.

  The first gate assumed: "steps == max(|dx|,|dy|) means no obstacle interfered, so the baseline
  must be 0". That is FALSE. For a 40/0 displacement every step must advance x, but dy is free, so
  (1,1),(1,-1),(1,1)... is also exactly 40 steps. Equal step count proves only that a
  minimal-LENGTH path exists - it says nothing about whether the STRAIGHT one is walkable.
  It produced 20 false failures out of 56 checked.

  Proven against the live capture rather than argued. For pair 3200,3200,2 -> 3240,3200,2 the row
  y=3200 is blocked at x=3226-3227 (east) and x=3228-3233 (solid), so minTurns=1 was CORRECT and
  the gate was the broken part. v1 over-blocks relative to live, so if live blocks it, v1 does too.

  THE RULE NOW: the gate walks the constant-direction line with the same canMove() the BFS uses.
  If that walk is legal for max(|dx|,|dy|) steps then it IS a shortest path (nothing beats the
  Chebyshev distance) AND it has zero direction changes, therefore minTurnsAmongShortestPaths MUST
  be 0. That is a certification with no assumption left in it. openGroundSelfCheckApplies takes the
  map and no longer references stepCount at all.

  ANTI-VACUOUS GUARD - this matters as much as the check. A gate that qualifies zero pairs reports
  "0 failures" and proves NOTHING, which would look identical to success. So the probe now prints
  openGroundSelfCheckStraightLineBlocked, and prints BASELINE SELF-CHECK VACUOUS when zero pairs
  qualify. Never read a 0-failure gate without reading its pairsChecked count.

  RESULT - the arithmetic closes exactly, which is what makes this trustworthy:
      before:  56 checked, 20 FAILED
      after:   36 checked,  0 FAILED, 114 excluded because the straight line was blocked
      56 - 20 = 36. The 20 old failures were EXACTLY the pairs whose straight line was blocked.
  Every trueExcessTurns distribution is byte-identical across the two runs, proving the change was
  gate-only with no metric drift.

D-0123 (2026-08-11) - NEVER compare a DERIVED edge against ground truth. Only N and E.

  In the live capture each tile stores only its own NORTH and EAST blocked bits. South and West
  are DERIVED from the neighbour (S of T = N of T.y-1, W of T = E of T.x-1). Comparing them
  counts the same physical edge twice AND lets a neighbouring object decide this tile verdict.
  That is exactly what produced the false locType 9 result on 2026-08-11: S/W read 100% blocked
  on every orientation, which looked like a 1,281-placement win and was an artifact.
  Every edge comparison from here compares N and E only. If a measurement seems to need S/W,
  it is measuring the neighbour tile and should be restated.

D-0124 (2026-08-11) - Write the interpretation rule into the code BEFORE the run, and make it
  two-part: effect size AND coverage.

  Practice adopted after four hypotheses were tested in one session. The rule text is emitted
  into the report ABOVE the numbers and the thresholds are hard-coded named constants, so a
  conclusion cannot be fitted to data that has already been seen.

  The two-part requirement is the load-bearing half and was learned the hard way. The border
  hypothesis passed its effect-size test at 4.01x (needed 3x) and would have printed CONFIRMED
  on a single-criterion rule - but it covered only 31.9% of the edges it claimed to explain
  (needed 40%), so it correctly printed INCONCLUSIVE. A large effect on a small slice is not an
  explanation. Pair every ratio test with a share-of-population test.

  Corollary: always print the denominator and an explicit VACUOUS line. A gate that qualifies
  zero cases reports "0 failures" and proves nothing while looking identical to success.

D-0125 (2026-08-11) - When a tile appears in several observation windows, score it by the BEST
  observation it ever received, never the nearest boundary.

  The capture holds 13 overlapping 104x104 scenes; 69,448 of 148,662 compared edges (47%) sit in
  more than one. Measuring distance-to-scene-border by the NEAREST containing scene would have
  put 9,822 edges in the outermost ring instead of 3,952 - inflating the border attribution ~2.5x
  and manufacturing the CONFIRMED verdict that was being looked for.
  Compute both min and max, use MAX for the verdict, and print both so any disagreement is
  visible rather than silently collapsed.

D-0126 (2026-08-11) - A FILTERED COUNTER MUST NEVER BE THE DENOMINATOR OF A TEST WHOSE
NUMERATOR COMES FROM THE UNFILTERED POPULATION.

  Earned the hard way while shipping the border-ring exclusion. The exclusion removed rings 0-2
  from every headline counter, exactly as specified. The border verdict then read:
      share = borderDangerousUnexplained (histogram, 7195 - still counts the border)
            / comparison.dangerousUnexplained (headline, 15359 - no longer counts the border)
  7195/15359 = 46.8%, over the 40% bar, and the verdict flipped INCONCLUSIVE -> CONFIRMED.

  The flip was pure artifact. The test was asking "of the unexplained edges REMAINING AFTER I
  REMOVED THE BORDER, what share is border?" - a question that can only inflate. Removing the
  border made the border look like a better explanation of what was left. Any exclusion big
  enough to matter will manufacture its own confirmation this way.

  Fix: the denominator now comes from the histogram (bucketedDangerousUnexplained = 22554),
  which counts every edge and is exclusion-independent. Share returns to 31.9%, verdict returns
  to INCONCLUSIVE, matching the pre-exclusion run exactly.

  THE GENERAL RULE: when a filter is added anywhere upstream, every ratio downstream must be
  re-checked for whether its numerator and denominator are still drawn from the SAME population.
  Filtering is not a local change - it silently re-bases every rate that reads a filtered counter.

  THE CHECK THAT CAUGHT IT: the pre-stated regression criterion "the histogram tables must be
  byte-identical after the change". The tables were identical - and the verdict line, computed
  from those identical tables, changed anyway. That contradiction is what exposed the bug. A
  derived value moving while all of its declared inputs hold still is proof of a hidden input.
  Always diff the CONCLUSIONS as well as the DATA; the data being right is not enough.

D-0127 (2026-08-11) - A PROXY THAT LOOKS DEFINITIONALLY TRUE STILL HAS TO BE VALIDATED.
MINE WAS FALSE, AND IT INVERTED THE VERDICT.

  I wrote into the interior work order, as justification for the design: "Every plane>0 tile is
  inside a structure." That sentence is wrong. The live capture dumps the ENTIRE scene grid for
  whatever plane the player stands on - not just the tiles under a roof. So plane 2 over an open
  field is also plane>0. It is empty sky, and it is outdoors.

  Consequence, measured: UPPER swallowed 60,452 of 136,950 compared edges (44%), most of them
  empty upper-plane grid over open ground. Regions whose upper planes are mostly sky score 3-6%
  dangerous. Region 46_52 - the one region captured at 100% on all three planes, which is why it
  was named as the coverage-controlled read BEFORE the run - scores 21.8% and 23.6% on planes 1
  and 2 against 8.3% on its own plane 0. Same region, same capture, ~2.8x worse upstairs.

  So the global verdict (REFUTED - interiors are SAFER, 12.6% vs 18.0%) and the within-region
  contrast point OPPOSITE WAYS, and the difference is entirely sample composition. A bad proxy
  did not merely weaken the test, it reversed its sign.

  THE VERDICT STILL STANDS AS REFUTED. The rule was fixed in code before the numbers existed and
  it returned REFUTED on all three reads - combined, UPPER alone, UNDER_STRUCTURE alone. I do not
  get to overturn a pre-stated rule after seeing the data. What I get to do is state that the
  proxy was too coarse to have tested what I claimed, and propose a sharper one.

  THE RULE: before a bucket is used as a proxy for a real-world property, prove the bucket is
  actually that property - do not argue it from the definition. Cheapest proof is a count: if
  "inside a building" holds 44% of a dataset gathered by walking around one castle, the label is
  wrong. A bucket that is far bigger or smaller than the real-world thing it names is a red flag
  on the label, not a discovery about the world.

  WHAT SAVED THIS: asking for the per-region-per-plane table in the same pass, and naming 46_52
  as the controlled read in advance. The headline verdict alone would have closed the interior
  hypothesis as dead. The breakdown showed the hypothesis was never actually tested.

D-0128 (2026-08-12) - A BIG RATIO INSIDE A SUBGROUP IS NOT THE SUBGROUP EXPLAINING THE PROBLEM.
AND WHEN YOU FIX A BROKEN PROXY, RE-RUN THE ORIGINAL RULE - DO NOT INVENT A KINDER ONE.

  Context: D-0127 recorded that the "upper floor" proxy was mostly empty sky and had inverted the
  interior verdict. The fix was to split UPPER by actual occupancy. The corrected test produced a
  spectacular-looking internal number and a mediocre real one:
      UPPER_NEAR_STRUCTURE vs UPPER_OPEN     3.97x   (27.62% vs 6.97%)   <- eye-catching
      UPPER_NEAR_STRUCTURE vs OUTDOOR        1.53x   (27.62% vs 18.03%)  <- the actual test
      share of all unexplained                26.6%  (4078/15359)        <- fails the 40% bar
  Verdict: INCONCLUSIVE. Barely clears the 1.5x refute floor, nowhere near the 3.0x confirm bar.

  THE TEMPTATION, NAMED SO IT CAN BE RESISTED: after two rounds of chasing this, 3.97x is exactly
  the number a person wants to report. It is real, it is clean, and it is the wrong comparison.
  It says occupied upper floors are worse than EMPTY upper floors - which is nearly a tautology,
  since empty sky has almost nothing to get wrong. What decides whether buildings explain the
  defect is the comparison against ordinary ground, and that is 1.53x.

  THE OTHER HALF OF THE RULE: the corrected test kept the SAME thresholds (3.0 / 1.5 / 40% / 500)
  as the border and interior hypotheses. Fixing a proxy is a chance to quietly re-baseline, and
  re-baselining after seeing a disappointing result is how a measurement programme rots. Third
  hypothesis, same bar, no exceptions.

  WHAT THE CORRECTED TEST ACTUALLY BOUGHT: not a confirmation - a correct negative. The interior
  hypothesis was not merely mis-measured in D-0127, it is genuinely weak. That is worth as much
  as a confirmation would have been, and it cost one short re-run.

D-0129 (2026-08-12) - A FALSIFICATION CONTROL THAT PASSES IS WORTH MORE THAN A BIG RATIO.
AND A BUCKET CAN FAIL BY BEING TOO NARROW, NOT ONLY BY BEING TOO WIDE.

  Phase 0 produced a 9.214x rate separation - by far the largest in this investigation; the
  border artifact, the previous record, was 4.01x. On effect size alone it is not close.
  It is still not the reason to believe it.

  THE REASON TO BELIEVE IT IS THE CONTROL. Before the run I stated: missing objects can ONLY make
  the builder say PASSABLE where the client says BLOCKED. They cannot cause OVERBLOCK, because no
  edge is written for them at all. So overblock must NOT concentrate next to ignored placements.
  It did not:  ADJ_SCENERY 1.177% (127/10792) vs NOT_ADJACENT 1.900% (2281/120041).
  Lower, not higher. That is the prediction that could have killed the theory and did not.
  A "cluttered areas are just harder" confound would have raised BOTH error directions. Only one
  moved, and it moved in exactly the direction the mechanism requires.

  Effect size says "something is here". A surviving falsification control says "and it is the
  thing I claimed". D-0127 and D-0128 both had impressive-looking ratios attached to proxies that
  did not survive scrutiny. This one had a way to be wrong and was not.

  THE SECOND HALF - MY BUCKET WAS TOO NARROW. The verdict is INCONCLUSIVE, on the share test:
  ADJ_SCENERY holds 35.19% of unexplained, under the 40% bar. But ADJ_OTHER_IGNORED - the
  secondary read - came back at 10.313x, an even larger separation. The effect is NOT specific to
  scenery. It is any placement shapeFor() ignores.
  D-0127 was a bucket too WIDE (empty sky diluting real buildings). This is the mirror image: a
  bucket too NARROW, splitting one real population across two rows so neither clears a share bar
  that the union clears comfortably.

  WHAT I AM NOT DOING: reading the two rows together and calling it CONFIRMED. 5405 + 3429 = 8834
  of 15359 is 57.5%, which would pass - and that arithmetic is a POST-HOC union, not the test I
  fixed in code beforehand. Same discipline as D-0128. The union is the next PRE-STATED run, not
  a re-reading of this one.

  THE RULE: state the falsification condition before the run and report whether it held, every
  time. When a pre-stated bucket turns out mis-drawn, the remedy is a new pre-stated run with the
  corrected bucket - never a wider reading of the run you already have.

D-0130 (2026-08-12) - CONFIRMED: the builder ignores solid objects, and that is what the
unexplained dangerous edges are. Four hypotheses died to reach this one; this is what a real
cause looks like next to those.

  THE DISCRIMINATING RESULT. Both sub-buckets sit in the SAME cluttered neighbourhoods - they are
  both "next to an ignored placement". The only difference is what the game data says the object
  IS. Split on getInteractType() != 0, read off the cache, not asserted by me:
      ADJ_SOLID_FLAGGED   14,760 edges   59.566% unexplained   10.958x baseline   CONFIRMED
      ADJ_NONSOLID_ONLY    2,149 edges    1.954% unexplained    0.360x baseline
      NOT_ADJACENT       120,041 edges    5.436% unexplained
  Objects the game marks solid: eleven times worse than open ground. Objects sitting right beside
  them that the game marks walk-through: BETTER than open ground. 8,792 of the 8,834 adjacent
  unexplained edges - 99.5% - are next to a solid-flagged object.

  WHY THIS IS A CAUSE AND NOT A CORRELATION. The prediction was written into the report ABOVE the
  numbers before the run: "if ADJ_NONSOLID_ONLY is just as bad as ADJ_SOLID_FLAGGED, then
  adjacency is tracking clutter, not solidity, and the object theory is weakened even though the
  union verdict passed." Clutter was the live alternative explanation and it had a clean way to
  win. It lost by a factor of thirty.
  The overblock control also still holds: ADJ_SOLID_FLAGGED 1.436% vs NOT_ADJACENT 1.900%. Two
  independent falsification conditions, both stated in advance, both survived.

  THE METHOD THAT GOT HERE, AFTER FOUR FAILURES: stop inventing the category. D-0127 died on a
  proxy I asserted ("plane>0 is inside a building"). D-0128 died on a bucket I drew. This time the
  category came out of the data - the cache carries a per-object solidity flag and we read it.
  The rule generalises: when the source data already answers the question, reading it beats any
  proxy you can construct, and it beats it by an order of magnitude in discriminating power.

  FLAG CHOICE, HONESTLY: isBlocksProjectile() scores 10.965x against interactType != 0 at
  10.958x. That is a tie, not a finding - and it covers fewer edges (12,731 vs 14,760). Do not
  read a preference into 0.007x. interactType stays the primary because it covers more of the
  population. isObstructsGround() is useless here (4 edges).

  SECOND DEFECT, NOW COUNTED, NOT YET FIXED: 2,873 ignored non-decor placements have a footprint
  LARGER THAN 1x1, and the builder treats every placement as a single tile. That is independent
  of the solidity gap and will still be wrong after solidity is fixed. Its own ticket.

D-0131 (2026-08-12) - Phase 2 object blocking uses a route-aware overblock gate, not the strict
one-way live N/E validator gate.

  Decision: for object-tile blocking, judge Phase 2 against route-aware overblock:

      routeAwareOverblock = OVERBLOCK - overblockSourceTileBlockedRaw

  Reason: the route map stores only N/E passable bits and derives S/W from the neighbouring tile:
  `canMoveSouth(x,y)` uses `canMoveNorth(x,y-1)` and `canMoveWest(x,y)` uses
  `canMoveEast(x-1,y)`. A solid object blocks entry into its tile from all sides, so a stored N/E
  edge on the object's own tile can be required for a south/west move from the neighbouring tile
  even if the live one-way N/E check from the object tile itself says open.

  Baseline on `drews-live-flags.FULL_20260811.txt` with Phase 2 disabled:

      strict OVERBLOCK 4,239
      source-tile live BLOCKED_TILE overblock 1,623
      route-aware OVERBLOCK 2,616

  Enabled Phase 2 result:

      strict OVERBLOCK 6,837
      source-tile live BLOCKED_TILE overblock 2,893
      route-aware OVERBLOCK 3,944
      route-aware rise 1,328, abort above 4,277

  Therefore the strict one-way gate remains visible in the report but is not the final Phase 2
  acceptance gate. The final gate is route-aware plus the existing AGREE_OPEN and net checks:
  DANGEROUS drop must exceed route-aware OVERBLOCK rise, and AGREE_OPEN drop must stay below 5,000.

  Do not use this decision to justify blocking roofs, other ignored locTypes, or larger footprints.
  Phase 2 is intentionally limited to ignored locType 10/11, `getInteractType() != 0`, 1x1
  placements. The 2,853 held-back larger footprints and other ignored locTypes need their own
  pre-stated gates.

D-0132 (2026-08-12) - `collision-map-v2.zip` is a patch archive, and the shipped runtime archive
must stay two-flag until the runtime reader changes.

  Decision: never replace `src/main/resources/collision-map.zip` wholesale with
  `build/collision-map-v2.zip` unless the builder was run for all runtime regions and that all-region
  output was separately verified. The normal Phase 2 output is a 24-region patch selected from the
  FULL proof capture. Runtime promotion means merge those 24 entries into the existing 1,524-entry
  `src/main/resources/collision-map.zip`.

  Decision: the shipped archive format remains two runtime flags per tile: north passability and
  east passability. The builder may keep N/E door flags in memory and in the report, but those door
  flags are report-only until a door-aware `DrewsHelperCollisionMap` reader exists.

  Reason: the current runtime loader constructs `DrewsHelperFlagMap(bytes, 2)`. A four-flag archive
  is not safely "ignored" by that reader; the bit stride changes, so every tile after the first is
  decoded against the wrong bits. The first promotion attempt proved the failure mode: several
  previously-ready Falador route fixtures turned into `NO_PATH`. After changing the builder to emit
  a two-flag archive, the `NO_PATH` failures disappeared and only expected exact-route fixture drift
  remained.

  Guardrail: every future runtime promotion must verify:

      patch entry count > 0
      runtime entry count remains the expected world count unless an all-region replacement is
      explicitly intended
      patch entries missing from runtime are reviewed, not silently accepted
      `clean test build` passes against `DrewsHelperCollisionMap.loadDefault()`

  If a future door-aware reader is added, make the archive version explicit rather than relying on
  inferred bitset length.

D-0133 (2026-08-12) - Live-flag polarity: `1` means BLOCKED, and a tile with NO row at all is
fully passable. And a blocked->open flip is not a regression until reachability is tested.

  Two durable rules, both of which cost real time in a single session.

  RULE 1 - the live-flag capture format. In `drews-live-flags*.txt` every data row is:

      x,y,plane <N><E> <rawFlagsDecimal>

  The polarity is `1` = BLOCKED, and a tile with NO ROW AT ALL is fully passable. Rows are emitted
  only when at least one direction is blocked, which is precisely why no `00` token ever appears
  anywhere in the file. That absence is the format working as designed, not data going missing.

  Self-check whenever the polarity is in doubt: read `1` as "passable" and both the old and the new
  map score WORSE THAN RANDOM against the same capture - 32.02% and 17.03% agreement. Read it the
  correct way and the same two maps score 67.98% and 82.97%. A decoding that makes a real map look
  worse than a coin flip is a decoding error, not a map defect.

  Corroborating detail: sentinel rows with `rawFlags = 16777215` (0xFFFFFF, every block bit set)
  carry token `11`. That only decodes sensibly as "both directions blocked". Under the inverted
  reading, an all-bits-set row would have to mean a fully open tile.

  RULE 2 - a tile flipping blocked->open in the collision map is NOT by itself a routing
  regression. Flipped regions must be tested for REACHABILITY FROM GENUINE LAND before any of them
  is called a defect. The discriminating test is an unbounded route solve from a real land tile
  into the flipped area: if it returns `NO_PATH`, the flip sits inside a sealed pocket and cannot
  change any route a player can actually walk.

  Reason: a byte-level diff of two collision archives cannot tell a cosmetic flag change in a
  sealed pocket apart from a genuine routable regression - both look identical in the diff. Only
  the reachability solve separates them. Applying this is what kept `5bddcf4` from being rolled
  back over coastal flips that no player can reach.

  Cross-reference: build notes are `D-0168` in `CHANGELOG_AGENT_NOTES.md`.

D-0134 (2026-08-12) - The terrain floor rule is VERIFIED, it must be measured against the
ISOLATED floor bit, and the client does not flag open ocean as no-floor either.

  Three durable rules out of one measurement pass over 49 live-client scenes. All three exist
  because a plausible-sounding "the terrain rule is wrong" report survived several sessions
  without anyone isolating the bit it was supposedly wrong about.

  RULE 1 - `tileSetting & 1` PLUS BRIDGE LOWERING IS VERIFIED. DO NOT "IMPROVE" IT. The rule in
  `CollisionMapBuilder.applyTerrain()` - block when `tileSetting & 1`, and lower the plane by one
  when `getTileSetting(1,x,y) & 2` - was measured against the client `BLOCK_MOVEMENT_FLOOR`
  (0x200000) bit over 192,061 usable observations containing 26,962 client no-floor tiles:

      predicate                                  TP      FP     FN       TN  precision    recall
      C1 CURRENT (bit0 + bridge lowering)     26962     526      0   164573    98.086%  100.000%
      C2 bit0 only, no bridge lowering        26954     852      8   164247    96.936%   99.970%
      C3 bit0 OR bit2 (0x04)                  26961   13828      1   151271    66.099%   99.996%
      C4 bit0 OR bit4 (0x10)                  26954    3304      8   161795    89.081%   99.970%
      C5 void tile (underlay==0&&overlay==0)  14680   63297  12282   101802    18.826%   54.447%
      C6 bit0 OR void tile                    26954   64118      8   100981    29.596%   99.970%
      C7 water overlay {442,445,448,451}       1136    8395  25826   156704    11.919%    4.213%
      C8 bit0 OR water overlay                26954    8814      8   156285    75.358%   99.970%
      C9 water overlay && underlay==0           283    4697  26679   160402     5.683%    1.050%

  C1 is the shipped rule and it has ZERO FALSE NEGATIVES. Every alternative measured buys no
  recall and costs precision, so any future proposal to widen this predicate is already refuted
  unless it arrives with a new measurement that beats 98.086% / 100.000% on the same captures.
  The bridge branch specifically is NOT vacuous - 862 covered tiles, 99.65% agreement with
  lowering versus 60.9% without - so it must not be deleted as dead code.

  RULE 2 - WHEN MEASURING THE FLOOR RULE, ISOLATE `BLOCK_MOVEMENT_FLOOR` (0x200000) OUT OF THE
  RAW FLAG COLUMN. Measuring the terrain rule against "the client blocks this tile on all four
  edges" conflates walls, scenery and objects with the floor, and none of those are the terrain
  rule's job. That conflation is exactly what produced the earlier `LiveFlagCrossTab` claim that
  65% of client-blocked tiles were unexplained by terrain and that 79.4% of them read
  `tileSetting == 0x00`. Against the isolated floor bit the same rule has 100% recall. That
  earlier finding is SUPERSEDED and must not be quoted again.

  RULE 3 - THE CLIENT DOES NOT FLAG OPEN OCEAN AS NO-FLOOR EITHER. It flags only a 1-2 tile
  coastline band. Measured in the Rimmington box (x2880-2919, y3200-3263, plane 0):

      usable tiles                                   1,893
      tiles carrying ZERO client collision flags      1,414
      tiles the client marks BLOCK_MOVEMENT_FLOOR       217
      cache/client agreement                   1,857/1,893 = 98.1%

  Therefore any future report of the form "the map thinks water is walkable" must check the
  client's OWN flags for those same tiles before it is treated as a disagreement. If the client
  does not flag the water either, the two agree and there is nothing to fix.

  Cross-reference: build notes are `D-0169` in `CHANGELOG_AGENT_NOTES.md`.

D-0135 (2026-08-12) - Missing regions are mostly ocean and are correctly blocked; never
bulk-build or bulk-rebuild collision regions.

  Five durable rules out of one measurement pass over the full cache region set. They exist
  because "1,425 regions are missing from the collision map" reads like a defect report, and it
  is not one.

  RULE 1 - A REGION ABSENT FROM `collision-map.zip` IS FULLY IMPASSABLE, NOT BROKEN.
  `DrewsHelperCollisionMap.loadRegion` hands back a `DrewsHelperFlagMap` whose BitSet is
  all-clear, and bit-set means PASSABLE, so all-clear means every edge blocked. Absence is a SAFE
  DEFAULT. Never treat "missing region" as "routing gap" without first checking whether the
  region is ocean.

  RULE 2 - NEVER BULK-BUILD THE MISSING REGIONS AND NEVER BULK-REBUILD THE LEGACY ONES. Measured:

      building all 1,425 missing regions would open roughly 800 ocean regions, 78 of which
      the shipped map deliberately keeps shut
      rebuilding all 1,323 legacy land entries opens a net 2,441,025 plane-0 edges - it
      closes plane-0 edges in 102 regions and opens them in 1,121

  Both are REGION-TARGETED jobs only, and every region included must be justified by a measured
  leak or a measured absence of content. Builder speed is not an argument for doing it in bulk -
  all 2,936 regions build in 7.61s - the behavioural blast radius is the argument against.

  RULE 3 - THE BUILDER'S REGION SELECTOR IS POSITIONAL, NOT `--regions`. Tokens are `rx_ry`, a
  bare 0-65535 id, or the literal `all`, taken from the non-option arguments after the output zip
  (`parseRequest`, `CollisionMapBuilder.java:219-284`). The only real flags are `--live-flags`
  and `--disable-phase2-solid-objects`. An explicit list is strict and throws on an unknown
  region; `all` skips silently.

  RULE 4 - THE PHASE 2 ROUTE-AWARE GATE IS ONLY MEANINGFUL FOR THE ORIGINAL 24 PROOF REGIONS. Its
  baselines are hard-coded absolutes from that build, so any other region set drives `agreeOpen`
  to 0 and makes the gate report ABORT falsely. Treat the verdict as N/A unless the region set
  matches the baseline's, and do not let a false ABORT block a slice. Making it
  region-set-relative, or marking it N/A explicitly, is the fix.

  RULE 5 - ZEAH/KOUREND IS SHIPPED, VARLAMORE IS THE GAP. Confirmed by landmark region for Great
  Kourend castle, Hosidius, Lovakengj, Shayzien, Port Piscarilius, Mount Karuulm, Arceuus and the
  Woodcutting Guild, and separately for Fossil Island, Lunar Isle, Prifddinas, Zanaris and Ape
  Atoll. The missing surface content is Varlamore, block rx17-29 / ry44-53. Correct any guide
  text that says otherwise.

  Cross-reference: build notes are `D-0170` in `CHANGELOG_AGENT_NOTES.md`.

D-0136 (2026-08-12) - Adopt the recorder-first navigation plan: verify one pilot region before
expanding, and make UNKNOWN a first-class state.

  Mytharium drafted an OSRS navigation-recorder plan and asked whether to adopt it. Verdict:
  ADOPT, with four of its targets restated and four gaps filled. It was written without the
  coverage measurements and independently reached the same conclusion those measurements force
  - stop expanding, start verifying - which is why it is adopted whole rather than mined for
  parts. These rules are the adapted form. Where this entry and the source plan disagree, this
  entry wins.

  RULE 1 IS SUPERSEDED AND WRONG - see D-0137. It is kept verbatim below as the record of a
  measurement error, not as guidance. Do not act on it. The figures it quotes are accurate; the
  interpretation placed on them is not.
  RULE 1 - UNKNOWN IS A FIRST-CLASS STATE. ABSENCE OF A ROW MEANS "NOT OBSERVED", NEVER
  "PASSABLE". This is the single highest-value change available to the project. D-0133 RULE 1
  records that the capture emits a row only when at least one direction is blocked, so the
  format cannot distinguish observed-open from never-loaded. Measured consequences:

      AGREE_OPEN                                        158,647 of 226,350 edges = 70.09%
      ...which is most of the headline 84.03% agreement
      edges the client POSITIVELY calls blocked           60,866
          we agree                                        31,565 = 51.86%
          we are wrong (DANGEROUS)                        29,154 = 47.90%
      edges OUR map blocks                                38,402
          client confirms                                 31,565 = 82.20%
          we over-block                                    6,837 = 17.80%

  51.86% is the honest accuracy figure; 84.03% is carried by silence. The fix is one change in
  the emitter - write a row for EVERY loaded tile. Capture files grow roughly 5-10x, which is
  cheap. Do this BEFORE any further accuracy work, because every number measured before it has
  to be re-derived by hand afterwards. This does not supersede D-0133's polarity rule, which is
  still correct; it changes what the capture is permitted to CONCLUDE from an absent row.

  RULE 2 - STOP EXPANDING THE MAP UNTIL ONE PILOT REGION IS VERIFIED. Measured state: 1,524
  regions shipped, 24 of them ours (1.57%), live-client ground truth on 64 regions (4.20% of
  shipped, 1.40% by plane-0 tile). Expanding coverage while 96% of it is unmeasured adds
  unverified geometry, not capability. The pilot area is Port Sarim / Draynor / southern
  Falador, roughly rx45-48 / ry49-52, chosen because it already sits inside both the capture
  footprint (rx45-52 / ry48-55) and the 24 rebuilt regions, so setup cost is near zero. NOT YET
  CONFIRMED: that region range is coordinate arithmetic, not a lookup. Confirm the exact overlap
  before committing work inside it.

  RULE 3 - COLLECT GROUND TRUTH PASSIVELY. DEDICATED CAPTURE WALKS ARE THE BOTTLENECK. 49 scenes
  across several sessions bought 4.20% coverage, because each walk is a deliberate errand that
  has to be chosen and scheduled. A listener that compares predicted against actual on every
  manual traversal accrues the same data at zero marginal cost during normal play, and it is the
  only route to object-interaction truth - what actually happens when a door is opened, as
  opposed to what the collision map says about that door's tile. NEVER automate movement or
  interaction to speed this up. That is a ban risk and the source plan is right to forbid it.

  RULE 4 - FOUR TARGETS IN THE SOURCE PLAN ARE NOT BUILDABLE AS WRITTEN. Restate, do not adopt:

      "static collision data >= 99.9% accurate" - no oracle and no denominator, and the plan's
          own opening paragraph forbids single-percentage targets. Restate as a PAIR: coverage%
          and agreement-on-observed%.
      "500-1,000 route tests physically completable in-game" - roughly 30 hours of walking, and
          automating it is correctly forbidden, so the target can never be measured. Substitute
          1,000 OFFLINE structural validations plus about 25 hand-walked.
      "whether the tile appears to be navigable Sailing water" - no such flag exists. Jagex
          rejected boat collision and steering is free-roam. Inferring it from no-land-and-no-
          collision is exactly the ASSUMED TRAVERSABLE the plan forbids. Sailing is modelled as
          dock-to-dock edges with NO water map (parked 30).
      "zero cases where the pathfinder recommends an unusable traversal" - achievable for STATIC
          requirements only (skill, quest, item, equipment, membership). Dynamic state such as a
          door another player shut, or world-hop instance state, is out of reach for any system.
          Scope the target to static or it stays permanently red.

  RULE 5 - THE CONFIDENCE LADDER NEEDS A RUNG BELOW INFERRED, CALLED INHERITED. 1,500 of the
  1,524 shipped entries (98.43%) are 2021 Runemoro data that we have never derived or checked,
  and parked item 29 puts 147 of the 171 known floor leaks inside it. Inherited data is
  measurably worse than our own derivation and must not share a tier with it. Related:
  provenance today is an ACCIDENT - the only discriminator between ours and inherited is the zip
  entry timestamp (1980-01-01 = rebuilt by us, 2021-04-23 = inherited). That is luck, not
  versioning. Record provenance explicitly and apply it retroactively to what already ships.

  RULE 6 - HYBRID SOURCING. THE CACHE IS THE BASELINE, OBSERVATION IS THE CORRECTOR. Recording
  from client flags has ZERO decode error by construction because it copies rather than derives;
  our 12.88% DANGEROUS rate exists precisely because we build from the cache and compare against
  the client. The two sources trade off cleanly:

      cache-derived   complete coverage immediately, unknown error rate, decode bugs
      observed        no decode error at all, coverage grows only at walking speed

  Do not choose between them. Cache-derived is the INFERRED baseline, observed is the CONFIRMED
  overlay, and the observed data feeds back as the corrector for the cache decoder. The existing
  live-flag pipeline is already this shape; only the confidence tiers were missing.

  RULE 7 - MOVEMENT MODES ARE A TWO-LAYER GRAPH, NOT A GENERAL STATE SPACE. The source plan's
  STATE + LOCATION -> ACTION -> NEW STATE + LOCATION + COST is the right frame, but implemented
  literally it is an exponential search. Practical form: mode is a small enum (WALK, SAIL) so the
  graph is layered, and account requirements are STATIC at route time so they are a pre-search
  filter. This preserves the already-approved shape - filter edges before search rather than
  adding requirement conditionals inside A*.

  RULE 8 - DECIDE THE MERGE-CONFLICT POLICY BEFORE FREEZING THE EXPORT FORMAT. The source plan
  wants recordings from multiple players and multiple sessions combined, but never says what
  happens when two recordings disagree about the same edge. Newest wins, highest confidence
  wins, or CONTRADICTED until re-observed? Left unanswered the format is not actually mergeable,
  and this cannot be retrofitted cheaply once recordings exist in the wild.

  WHAT ALREADY EXISTS - DO NOT REBUILD IT. The source plan's development order reads greenfield,
  and a literal reading restarts work that is largely done. Already shipped: eight-directional
  data (capture column 3 is the client's raw flag int, carrying all eight direction bits plus the
  object and floor bits - the <N><E> pair is only a summary), edge-based rather than tile-based
  storage, per-edge requirements with the two-gate family/capability filter, the
  false-positive-over-false-negative axis (DANGEROUS 12.88% vs OVERBLOCK 3.02%), object
  extraction via Route B (353 gates, 1,793 doors, 78 gaps, 192 wilderness ditch), and route
  rendering. The recorder is an EXTENSION of the existing live-flag capture, not a new plugin.

  ONE GAP THE PLAN LISTS BUT NEVER TESTS. Movement cost and optimality are named as metrics 5
  and 6, and the validation section checks neither. That is the old item 5 - turn count, measured
  trueMean 2.04 / trueMax 12 excess - and it is measurable offline today. It belongs inside the
  validation harness rather than standing as its own item.

  Cross-reference: the revised work sequence is THE LIST in `02_NEXT_WORK.md`.

D-0137 (2026-08-12) - CORRECTION: D-0136 RULE 1 is wrong. AGREE_OPEN is observed evidence, the
capture already distinguishes unobserved ground, and no emitter change is needed.

  D-0136 RULE 1 claimed that the capture cannot tell observed-open from never-loaded, that the
  158,647 AGREE_OPEN edges are therefore silence being scored as success, that 51.86% is the
  honest accuracy figure, and that the emitter must be changed to write a row for every loaded
  tile. All four claims are false. They were reached by reading D-0133's FORMAT rule - "a tile
  with no row at all is fully passable" - and assuming it propagates into the comparison. It
  does not. The code was written more carefully than that reading of it. RULE 1 of D-0136 is
  superseded in full; the remaining rules of D-0136 stand unchanged.

  RULE 1 - THE CAPTURE FORMAT ALREADY CARRIES THE OBSERVED REGION, PER SCENE. Every scene block
  opens with a header naming its exclusive covered bound:

      DREW_LIVE_FLAGS scene 2912:3160:0 size=104 covered=103

  The emitter writes it deliberately. Its own comment at `DrewsHelperPlugin.java:450-455` states
  that the final row and column are "excluded rather than reported as passable" precisely so a
  consumer cannot "silently ingest a ring of false ground truth". Absence is only ever meaningful
  strictly inside the covered bound, and the format states where that bound is.

  RULE 2 - THE BUILDER ALREADY PARSES AND ENFORCES IT. `LIVE_SCENE_HEADER` at
  `CollisionMapBuilder.java:123-124` captures the covered value, `:3545` parses it, and
  `:3591-3594` throws a hard IOException on any row falling outside the covered bound. A capture
  that violated the contract would fail the build rather than quietly pollute the numbers.

  RULE 3 - UNOBSERVED TILES NEVER ENTER THE COMPARISON AT ALL. This is the decisive fact.
  `capture.tiles` is populated ONLY inside `addLiveDataRow` (`:3663-3667`); nothing anywhere
  synthesizes a LiveTile for a coordinate that had no row. The comparison loop is
  `for (LiveTile tile : live.tiles.values())` at `:1123`. Every compared edge therefore descends
  from a tile the client positively reported on, and a never-observed tile contributes zero edges
  in either direction. There is no silence in the sample to remove.

  RULE 4 - AGREE_OPEN IS EVIDENCE, NOT SILENCE. A row is emitted when at least one of N/E is
  blocked - `:3596-3599` throws if neither is - so a tile with north blocked and east open
  contributes one blocked edge AND one genuinely observed open edge. AGREE_OPEN is the second
  kind. The arithmetic confirms the shape:

      compared 226,350 + outsideBuiltRegions 94,542 + border-excluded 19,482 = 340,374 edges
      340,374 / 2 = 170,187 tiles
      parsed rows 220,398 - sentinel rows 47,232 = 173,166 row instances, deduplicated by the
      TreeMap across overlapping scenes to approximately 170,187 unique tiles

  Compared edges equal exactly twice the unique row-bearing tile count. Nothing else is in the
  denominator.

  RULE 5 - THE CORRECTED READING OF THE FIGURES. The numbers quoted in D-0136 RULE 1 are all
  accurate; only their interpretation was wrong.

      84.03% overall agreement      CORRECT and defensible - this is the headline figure
      51.86%                        recall on the BLOCKED class only. A real weakness worth
                                    tracking, but NOT a replacement for the headline
      12.88% DANGEROUS              unchanged
      17.80% over-block             unchanged
      4.20% ground-truth coverage   unchanged - still the genuine bottleneck

  Quote 84.03% as the agreement rate. Quote 51.86% as blocked-edge recall. Never present the
  second as a correction of the first.

  RULE 6 - NO EMITTER CHANGE. Item A of THE LIST is closed with no code written. Writing a row
  for every loaded tile would grow capture files roughly 5-10x and buy nothing the format does
  not already provide. Item B - passive traversal verification - is untouched by this correction
  and becomes the top item.

  METHOD NOTE. This is the third consecutive LIST item to close on a false premise, and the only
  one where the false premise had already been written into a decision entry before it was
  checked. The cheap discriminating question - "does the CONSUMER actually do what the FORMAT
  permits?" - costs minutes and would have prevented the whole entry. Read the consumer before
  concluding anything about what a format implies.

  The genuinely open measurement question in this area is NOT absence handling. It is the border
  ring: 19,482 edges withheld from scoring, 11,120 of them DANGEROUS (57.08% of the withheld
  set), on the report's own verdict `border histogram verdict: INCONCLUSIVE` at
  `tools/collision-map-v2-report.txt:127`. We removed a set disproportionately full of our worst
  error class without proving we were entitled to.

  Cross-reference: supersedes RULE 1 of `D-0136` only. All other D-0136 rules stand.

D-0138 (2026-08-12) - The wall-crossing routes are an upper-plane defect: the shipped collision
map treats plane 1 as open ground, and the router walks across it.

  Reported three times over two days as "the route takes me through a wall", and diagnosed as a
  wall twice. Both diagnoses were wrong. The route does not cross the wall - it walks along the
  floor ABOVE it, and the world map draws every plane flat, so on screen the two are identical.
  Mytharium named it first, from the client: "it had me climb up a ladder and the route is now
  telling me to walk through the air. Maybe that is why it is bugging out with the wall."

  RULE 1 - THE EVIDENCE IS ONE ROUTE, TWO LEGS, 230 TILES APART. First run of the leg recorder,
  from `drews-route-legs.txt`:

      DREW_ROUTELEG v1 route tick=25 legs=2 path=327 dest=3026,3361,0
      DREW_ROUTELEG v1 #1 from=3262,3402,0 to=3262,3402,1 objId=11794 label=Climb-up Ladder
      DREW_ROUTELEG v1 #2 from=3032,3389,1 to=3032,3388,0 objId=17052 label=Jump Wall

  Two transport legs and nothing between them. The route climbs a ladder in Varrock onto plane 1,
  walks 230 tiles west entirely on plane 1, and drops off the Falador north wall. Both legs are
  legal rows in `drewshelper-transports.tsv`; the illegal part is the walk between them. The
  client answers `I can't reach that!`. A second route the same session recorded one leg (the
  same Jump Wall) at path=292, matching the HUD's "Route Length 291 tiles" exactly.

  RULE 2 - PLANE 1 IS 5.31x MORE PERMISSIVE THAN PLANE 0, MEASURED. Scene 3200:3376 (Varrock
  centre), both planes captured in one session:

      plane 0   tiles=10609  mismatches=3752  overblock=1469  underblock=2283   1.55x
      plane 1   tiles=10609  mismatches=4613  overblock= 731  underblock=3882   5.31x

  43% of plane-1 tiles disagree with the client. Interior tiles after stripping the scene border:
  plane 0 = 1,318 (332 void sentinel, 986 real blocking values); plane 1 = 1,985 (559 void
  sentinel, 1,426 real blocking values). One scene is not a rate - this is a direction, not a
  global figure, and the agreed next step is per-plane coverage across several cities BEFORE any
  rebuild.

  RULE 3 - IT IS THE DATA, NOT THE READER. `DrewsHelperFlagMap.get` returns false - cannot move -
  for any coordinate outside the region and any plane outside 0-3, and
  `DrewsHelperCollisionMap.loadRegion` returns an all-false map for a region absent from the zip.
  Absent data therefore BLOCKS, which is the safe direction. The wrong bits are physically inside
  `collision-map.zip` for the upper planes, so the fix belongs in the builder, not the router.

  RULE 4 - THE FLOOR-BIT HYPOTHESIS IS DEAD. DO NOT RETRY IT. The obvious theory was that the
  builder ignores `BLOCK_MOVEMENT_FLOOR` (0x200000). Measured across every interior under-block
  tile on both planes: ZERO carry that bit outside the 0xFFFFFF full sentinel. The client's
  "no floor here" signal, in what the capture actually records, IS the full sentinel. There is no
  separate bit being ignored.

  RULE 5 - A "ROUTE CROSSES A WALL" REPORT IS AN ELEVATION REPORT UNTIL PROVEN OTHERWISE. Read
  `drews-route-legs.txt` first and look at the plane in the from/to tiles. The world map cannot
  show the difference; the leg record can, and it settled this in a single run.

  RULE 6 - THE REQUIREMENTS GATE IS CORRECT AND IS NOT IMPLICATED. Investigated alongside this
  and cleared on evidence: Mytharium holds Agility 30 / Ranged 21 / Strength 50 against the
  Grapple Wall 17050 requirement of 11 / 19 / 37, but no crossbow and no mithril grapple, so
  `meetsItems("CROSSBOW=1&MITH_GRAPPLE=1")` fails and both GRAPPLE_SHORTCUT edges are correctly
  dropped. `buildCapability()` returns permissive only when not logged in, the routing graph uses
  the capability-aware `loadDefault` overload, and `countOf` returns 0 for unknown symbols.

  METHOD NOTE. Every finding here came from recording something the code already computed and
  then discarded. RULE 2 exists only because the OURS_OPEN_LIVE_BLOCKS filter at
  `DrewsHelperPlugin.java:438` and `:639` was removed; RULE 1 exists only because the transport
  hops are now written with their tiles instead of as bare labels. In both cases the diagnosis
  was blocked by a write-time filter, not by missing computation. Record raw, filter at read
  time: a discarded observation costs a re-collection, a recorded one costs a line.

  Cross-reference: root cause behind the Falador Park reports. Supersedes nothing; D-0136 and
  D-0137 stand.

D-0139 (2026-08-12) - Roofs are the upper-floor blocker, and phase 2 is the weaker change.

  RULE 1 - LOCTYPES 12, 13, 14, 16, 17, 18, 19 AND 21 ARE THE MISSING UPPER-FLOOR RULE. They are
  the OSRS roof shapes. Measured over the 62-region 2026-08-12 capture with phase 2 off and roofs
  the only difference between two builds:

      DANGEROUS_UNEXPLAINED   89651 -> 59953    -29698   (-33.1%)
      OVERBLOCK               11910 -> 15913     +4003
      route-aware OVERBLOCK    6098 ->  6626      +528
      proof edges fixed        74.5% -> 71.0%     -3.5pts

  One rule removes a third of every unexplained dangerous edge in the build.

  RULE 2 - BLOCKING ROOFS CANNOT TOUCH THE GROUND FLOOR. There are ZERO roof placements on
  plane 0 (by-plane counter 0 / 11749 / 8163 / 3636) and the plane-0 dangerous count is identical
  in both builds at 60837. This was raised as a risk and answered by measurement, not argument.

  RULE 3 - BLOCKING ROOFS CANNOT SEAL A WALKABLE UPPER STOREY. OSRS places a roof on the plane
  ABOVE the interior it covers, so a building with a walkable first floor has its interior at
  plane 1 and its roof at plane 2 - the walkable tile carries no roof marker. The evidence is the
  direction of the numbers: upper-plane dangerous fell about 36% (plane 1 13.842% -> 8.847%,
  plane 2 13.279% -> 8.403%) while route-aware overblock rose by 528 across the whole build.
  Sealing walkable storeys would have moved both the other way.

  RULE 4 - PHASE 2 IS THE WEAKER CHANGE ON EVERY AXIS MEASURED. Route-aware, same regions and
  capture: roofs are -29698 unexplained for +528 route-aware overblock, a ratio of 56.2 to 1;
  phase 2 is -13469 for +3532, a ratio of 3.8 to 1. Phase 2 also costs 4.7x more on the
  proof-edge set (-16.4pts against -3.5pts) for 45% of the fix. Phase 2 should be reworked or
  dropped rather than kept by default.

  RULE 5 - DO NOT SOFTEN A CRITERION TO PASS YOUR OWN CHANGE. Both configurations FAIL the
  proof-edge criterion (roofs 71.0%, phase 2 58.2%, against a 74.5% baseline). The criterion was
  not relaxed to let either through. A gate that gets loosened whenever it blocks the author's
  own work is not a gate. If a criterion is wrong it changes on its own merits, in its own
  commit, with its own reasoning - never as a side effect of wanting a result.

  RULE 6 - CONFIRM ATTRIBUTION BY EMPTYING THE BUCKET YOU BLAMED. ADJ_OTHER_IGNORED
  dangerousUnexplained collapses 29962 -> 428, a 98.6% reduction. The bucket the proof pass
  blamed is the bucket that empties. That is the difference between a correlation and a
  demonstrated cause, and it is the check to run whenever a fix comes out of an adjacency
  measurement.

  RULE 7 - SHIP THE RULE THAT WAS MEASURED. Roof blocking keys on locType alone, with no
  interactType condition and no footprint expansion, because locType alone is what the proof pass
  measured. Adding a plausible extra condition at implementation time ships a rule nobody proved.

  RULE 8 - A CONTROL THAT MEASURES ITS OWN FIX ALWAYS FAILS. Found three times in this one file:
  the ADJ_SCENERY overblock control, the union overblock control, and the per-locType overblock
  column. All three assume no edge is written for ignored objects, which stops being true the
  moment a blocking phase writes them. Same regions and capture, ADJ_SCENERY overblockRate is
  0.912% with phase 2 off and 14.026% with it on. Any control whose premise names something the
  build does must state whether the build is currently doing it.

  RULE 9 - A HARDCODED BASELINE INVALIDATES ITSELF WHEN THE INPUT SCOPE CHANGES. The phase 2 gate
  compared against constants from a 24-region run, so a 62-region run read AGREE_OPEN 854157
  against a 161245 baseline and reported ABORT on nothing but a bigger sample. Baselines are now
  measured live - same regions, same capture, blocking phases forced off. This is D-0138's "one
  sample is a direction, not a rate" one level up.

  RULE 10 - STATE THE PREDICTION BEFORE THE RUN. The prediction here, that no excluded locType
  would clear the bar locTypes 10 and 11 clear, was falsified: the excluded set returns 69.8% to
  91.7% dangerousUnexplained at 23.2x to 30.4x against locType 10's 49.6% at 16.5x. Writing it
  down first is what made the falsification usable instead of arguable.

  HELD BACK. locTypes 15 and 20 sit under the 500 compared-edge floor at 384 and 488 edges
  despite 90.6% and 98.2% rates, and will clear on wider capture. locType 4 clears the danger bar
  at 5.3x but is a wall decoration and is more likely to be standing beside the wall that really
  blocks; it needs its own pass. locTypes 5-8 are inconclusive or vacuous.

  Cross-reference: extends D-0138 and answers it. Supersedes nothing. Commits a469d96
  (per-locType proof pass, three control fixes, live gate baseline) and b48b4bd (phase 3 roofs).

D-0140 (2026-08-12) - Ship the all-region map: roofs on, phase 2 dropped, doors passable

  RULE 1 - A DOOR IS NOT A WALL. markDoor clears FLAG_*_PASSABLE and sets FLAG_*_DOOR, and
  archiveFlags copied only the PASSABLE bits into the shipped zip, so every door in the game
  was written as a solid wall and the runtime had no way to tell. archivePassable now
  collapses the two flags for the archive writer and for verifyRoundTrip, which has to agree
  or it fails on its own output. A door is passable until a door-aware reader can charge the
  tick that opening one costs.

  RULE 2 - EDGE COUNTS CANNOT SEE THAT A DOOR IS LOAD-BEARING. In one 26-tile box around the
  Ardougne mansion the rebuild opened 501 edges and closed 28 - eighteen to one - and six of
  those 28 were three door pairs, one being the only way out of the building. The aggregate
  over-block number looked excellent while a route teleported out of a room. Reachability
  needs a check of its own.

  RULE 3 - PHASE 2 IS DROPPED, AND NOT ON A RATIO. On a frozen capture, so both configs read
  identical input: phase 2 fixes 16,495 unexplained-dangerous edges for 9,320 new over-blocks
  (1.77x), roofs fix 32,931 for 4,468 (7.37x), and phase 2 loses on proof edges and on
  over-block at the same time. The naming defect is concrete: phase 2 seals x 3142-3155,
  y 2839-2841, the pier at the Ruins of Unkah where the ferry from 3271,3144 lands, leaving
  no route into the southern Kharidian Desert at all.

  RULE 4 - REPLAY A WORKING ROUTE TILE BY TILE TO FIND WHAT A MAP REFUSES. Recording the
  shipped map's 169-tile route and asking each rebuild "can you take this step" put the break
  on steps 96-104 in a single run. Nine consecutive refusals starting one tile off the boat
  is an address, not a ratio. The same method found the mansion door, and four hypotheses had
  already died on that route before it.

  RULE 5 - A MEASUREMENT MUST DESCRIBE THE BUILD THAT SHIPS. compareLiveEdge still read
  bits.isPassable after the archive began writing doors passable, so DANGEROUS and OVERBLOCK
  graded a map nobody would run. Fixed, and proven report-only by hashing both all-region
  archives before and after the edit: byte-identical. Fifth instance of this defect class in
  this one file in a single day.

  RULE 6 - FREEZE A GROWING INPUT BEFORE AN A/B. drews-live-flags.txt accumulates while the
  reporter plays, and a number that could not be affected by a report-only change still moved
  by 757 between runs. Both phase 2 builds read a frozen SHA256-verified copy of the capture.

  ACCEPTED COSTS, stated rather than hidden. The shape-ranking coordinate pin stays red: the
  route lands on 2990,3286 where the fixture pins 2991,3286, one tile on a tie, with equal
  walking distance and every other assertion passing, and it fails if and only if phase 2 is
  off across four full-suite runs. The mansion wall at 2573,3245 becomes walkable - it is a
  solid object rather than a wall type, so only phase 2 held it up. One wall, one continent.

  Cross-reference: extends D-0139. Commits 7de78e8 (doors passable in the archive), e01c157
  (doors passable in the report), 9c22f72 (the map: 1,524 -> 2,949 regions, 907,178 ->
  1,182,273 bytes).


D-0141 (2026-08-13) - Defer normalized edges across region seams

  RULE 1 - WALLS CAN BELONG TO THE NEIGHBOUR REGION. The Falador north-wall report at
  3019,3391 -> 3019,3392 proved the shipped map had the edge open while the live client
  blocked it. The cache classifier named a solid locType 0 wall object, id 24029, but the
  object lived in region 47_53 while the normalized stored edge lives in region 47_52.

  RULE 2 - NORMALIZING SOUTH AND WEST EDGES MUST NOT DROP THEM. The builder stores only
  NORTH and EAST passability, so SOUTH becomes y-1/NORTH and WEST becomes x-1/EAST.
  Before this decision, RegionBits.edgeIfInside rejected that normalized edge when it
  landed outside the current region. That skipped seam walls, doors, roof edges, and
  terrain edges whenever the object was seen from the other side of a 64-tile boundary.

  RULE 3 - DEFER, THEN APPLY TO THE REGION THAT OWNS THE STORED EDGE. markEdge now emits a
  DeferredEdge when the normalized edge belongs to another built region. After all selected
  regions are built, applyDeferredNeighbourEdges writes those edges into their owner region
  and still skips only when the owner region was not built. Solid-over-door precedence stays
  on the same markStoredEdge path as in-region writes.

  PROOF. A two-region rebuild of 47_52 and 47_53 applied 36 deferred edges and changed
  3019,3391,0 from north-open to north-blocked. The route from 3019,3390 to 3019,3401
  stopped being the 11-tile wall line and became a 145-step detour. The all-region rebuild
  deferred 166,441 neighbour edges, applied 116,788, and skipped 49,653 edges whose owner
  regions were not built or merged.

  Cross-reference: extends D-0140 and closes Mytharium's 3019,3390 -> 3019,3401 wall report.


D-0142 (2026-08-13) - Ship furniture blocking as a measured object-profile allowlist

  RULE 1 - DO NOT RESTORE BROAD PHASE 2. The old locType/size rule fixed some furniture but
  also sealed the Ruins of Unkah ferry beach. The replacement is exact object id plus locType:
  595/10 Table, 1104/10 Bench, 1088/10 Chair, and 1088/11 Chair. Names and locTypes are
  guards, not the rule; the measured profile key is the rule.

  RULE 2 - BLOCK THE ANCHOR TILE ONLY UNTIL FOOTPRINT EXPANSION IS PROVEN. Tables and benches
  in this first allowlist include 2x1 profiles, but the builder still blocks only the anchor
  tile. Expanding the full footprint would be a separate map-writing change with its own cost
  column and route checks.

  RULE 3 - OPEN-STYLE OBJECTS STAY OUT. The allowlist still requires a real name, non-zero
  interactType, and no Open/Close style action. Doors, chests, ferries, banks, and other
  action surfaces are not admitted by this pass.

  PROOF. Against the same frozen capture and roofs-on shipped baseline, furniture blocking
  changed DANGEROUS_UNEXPLAINED 77,880 -> 77,295, OVERBLOCK 20,072 -> 20,231, route-aware
  OVERBLOCK 7,478 -> 7,524, and proof edges fixed 14,898 -> 14,854. That is 585 fewer
  unexplained-dangerous edges for 159 added over-blocks, or 3.68x on the cost column, while
  only 46 of those were route-aware over-blocks. The build blocked 764 furniture placements
  and no open-style placements.

  SHIP CHECKS. The shipped map grew 1,188,463 -> 1,189,982 bytes. The new test pins the
  Ardougne chair at 2573,3245 as blocked and the Ruins of Unkah ferry landing/beach as still
  walkable. The full suite now has 197 tests with exactly the one accepted failure,
  shapeRankingShadowExposesDistinctSameLengthRandomChainRoute.

  NOTE. The no-object proof control still reports lower proof percentage whenever any real
  blocker is enabled. That control is useful as a warning light, not as the sole ship gate;
  use it with frozen A/B numbers, exact route checks, and the full test suite.

  Cross-reference: extends D-0140 and D-0141. Closes backlog item 33's first shippable slice;
  remaining slices are other named objects such as stools, shelves, hedges, and trees.


D-0143 (2026-08-13) - Transport gates are checked from account capability, not route reports

  RULE 1 - EXACT TILES ARE DEBUGGING PROOF, NOT THE FIX. Mytharium rejected a route-specific
  raft patch and restated the original design: every shortcut/transport edge must be filtered
  against the player's real skills, carried/equipped items, quests, varbits and varplayers
  before pathfinding ranks it. Route coordinates are useful only to identify the row and pin a
  regression test.

  RULE 2 - ITEM REQUIREMENTS MUST PARTICIPATE IN THE ROUTE-ENGINE CACHE KEY. The graph already
  filtered each edge through DrewsHelperPlayerCapability, but the capability signature only
  tracked broad item symbols and coin tiers. That was not enough for all upstream item rows:
  bare item ids, quantities and OR alternatives could flip without changing the cached graph
  key. The plugin now snapshots every distinct item requirement expression from the transport
  resource into the capability signature as a satisfied/unsatisfied bit.

  RULE 3 - THE REQUIREMENT SOURCE IS STILL THE TSV. Do not hardcode OSRS shortcut tables. The
  resource owns the skills, quests, items, varbits, varplayers and wilderness caps; the plugin
  derives the quest names, var ids and item requirement expressions from that same data so an
  upstream row change does not need a new hand-coded gate.

  PROOF. New regression tests pin both sides of the report class: the Agility 48 log-balance
  row at 2722,3592 -> 2722,3596 is absent at Agility 47 and present at Agility 48, and
  Grapple Broken Raft 17068 at 3246,3179 -> 3259,3179 is absent without crossbow plus mithril
  grapple and present with both items. Another test proves a bare item-id quantity changes the
  capability signature, which is the stale-cache case the old symbol-only signature missed.

  Cross-reference: follows D-0142 live test 2. Next live test should use Mytharium's account:
  the raft/grapple shortcut should disappear unless the skill and item requirements are met.


D-0144 (2026-08-13) - Shortcut corridors are not ordinary walking edges

  RULE 1 - REQUIREMENT FILTERING IS NOT ENOUGH WHEN COLLISION SAYS THE WATER IS OPEN. The
  live Broken Raft report still routed 3246,3184 -> 3260,3175 after D-0143 because the route
  did not use a transport edge at all. The recorded route walked 3246,3179 -> 3259,3179 one
  tile at a time, so the grapple row was correctly filtered out but the collision map still
  exposed the same shortcut corridor as normal ground.

  RULE 2 - AGILITY AND GRAPPLE SHORTCUT CORRIDORS BELONG TO THE TRANSPORT LAYER. Ordinary
  walking now refuses adjacent steps that lie on an AGILITY_SHORTCUT or GRAPPLE_SHORTCUT row's
  source-to-destination corridor. Qualified accounts still cross through the gated transport
  edge; unqualified accounts must detour instead of walking over the same water, wall, gap,
  stepping stones or raft tiles.

  RULE 3 - KEEP THIS DATA-DRIVEN. The blocker is derived from drewshelper-transports.tsv and
  not from the Broken Raft coordinates. This preserves Mytharium's global shortcut policy:
  player capability decides the transport edge, and shortcut geometry is not also available
  as plain walking.

  PROOF. New regression tests pin both sides of Broken Raft 17068. Without the grapple kit,
  the route from 3246,3184 to 3260,3175 no longer walks through the 3246..3259,3179 raft
  corridor. With Agility/Ranged/Strength plus crossbow and mithril grapple, the same route
  uses the expected transport step 3246,3179 -> 3259,3179.

  Cross-reference: extends D-0143 and closes the live screenshot where the route still drew
  across the Broken Raft after item/skill transport filtering shipped.


D-0145 (2026-08-13) - Audit all shortcut corridors as transport-only geometry

  RULE 1 - THE WORD SHORTCUT IS THE SCOPE BOUNDARY. The transport resource has exactly two
  corridor families: AGILITY_SHORTCUT and GRAPPLE_SHORTCUT. Those rows describe physical
  crossings such as logs, rocks, walls, rafts, gaps and stepping stones. Their corridor is
  not ordinary walking; it is transport-layer geometry gated by account capability.

  RULE 2 - OTHER TRANSPORT FAMILIES STAY OUT OF THE CORRIDOR BLOCKER. Canoes, balloons,
  gliders, quetzals, fairy rings, spirit trees, mushtrees, Wilderness systems and baseline
  access rows are still filtered by skills, items, quests, varbits and varplayers. They are
  not straight shortcut corridors unless the TSV category says they are. Blocking their
  source-to-destination line as walking would invent walls through normal travel networks.

  RULE 3 - THE AUDIT IS NOW A REGRESSION GATE. DrewsHelperShortcutCorridorAuditTest parses the
  active drewshelper-transports.tsv and checks every adjacent step in every same-plane
  shortcut row through DrewsHelperTransportGraph.blocksShortcutWalkingStep. It also proves
  that a zero-capability account loads no AGILITY_SHORTCUT or GRAPPLE_SHORTCUT transports.

  PROOF. Current TSV counts: 557 AGILITY_SHORTCUT rows and 15 GRAPPLE_SHORTCUT rows. Of those,
  481 agility rows and 11 grapple rows are same-plane corridors; 76 agility rows and 4 grapple
  rows change plane and cannot be crossed as ordinary same-plane walking. The same-plane rows
  expand to 2,981 adjacent walking steps, and the audit found 0 unblocked steps.

  Cross-reference: extends D-0144 from the Broken Raft one-case proof to the full shortcut
  corpus. Report snapshot: tools/shortcut-corridor-audit.txt.


D-0146 (2026-08-13) - Terrain completeness audit finds no shippable terrain-bit rule

  RULE 1 - THE LIVE-FLAG FORMAT IS APPEND-ONLY. DrewsHelperPlugin already writes an optional raw
  collision-flag word after the two stored edge bits. LiveFlagCrossTab must accept both formats
  so an older two-token capture and a newer three-token capture stay usable. The raw flag is
  additive evidence, not a reason to reject the row.

  RULE 2 - BIT-0 FLOOR BLOCKING STILL HOLDS. The new 379-scene capture gives the existing
  terrain rule 189,245 usable edge checks, 181,696 agreements, and 96.0% agreement. The bridge
  lowering branch is still a small sample at 96 tiles, but it also agrees 361/384 = 94.0%.
  That is not a reason to alter the bit-0 terrain rule.

  RULE 3 - THE REVERSE MISS SET DOES NOT JUSTIFY A NEW TERRAIN BIT. Of 109,156 no-wall tiles
  where live blocks all four edges but the terrain rule did not mark the tile, 95,898 carry
  tileSetting 0x00. Bit 4 is enriched compared to clean controls (8,349 target tiles, 7.7%,
  versus 759 controls, 0.1%), but it explains only a narrow slice of the miss set and is not
  enough by itself to ship a broad floor-blocking rule.

  RULE 4 - THE BIG REMAINING FALSE-OPEN CLASS IS IGNORED SCENERY, NOT FLOOR TERRAIN. The
  all-region dangerous pass reports 78,069 DANGEROUS_UNEXPLAINED edges after border exclusion.
  ADJ_SCENERY accounts for 37,020 of those at 45.061% dangerous-unexplained rate versus
  NOT_ADJACENT at 2.661%, a 16.931x ratio. ADJ_SOLID_FLAGGED accounts for 36,958 with the
  nonsolid-only control reading like NOT_ADJACENT. That points to named object profiles, not a
  global terrain-bit change.

  RULE 5 - DO NOT SHIP THIS AUDIT AS A MAP. The audit produced reports and one parser
  compatibility fix only. No collision-map.zip was copied into src/main/resources, and no
  runtime route behavior changed.

  Cross-reference: follows D-0145. Report snapshots: tools/live-flag-crosstab.txt and
  tools/collision-map-v2-report.txt. Next candidate work is an object-profile pass using the
  all-region named-solid ranking, with exact route checks for trees/hedges/boulders before any
  shipped map change.


D-0147 (2026-08-13) - Expand object-profile blockers with no-cost scenery profiles

  RULE 1 - KEEP THE OBJECT BLOCKER PROFILE-BASED. Do not restore broad locType 10/11, all
  named-solid, all 1x1, or old Phase 2 object blocking. Object blockers ship only as explicit
  object-id plus locType profiles whose measured benefit survives the all-region report.

  RULE 2 - THIS PASS ONLY SHIPS ZERO-COST RANKER WINNERS THAT KEEP PINNED ROUTES STABLE. The
  frozen all-region capture showed many named scenery profiles with projectedNewOverblock=0. The
  first trial included trees, but that moved the pinned Falador southeast live-route fork, so tree
  and tree-stump profiles stay out even when the cost column says no-cost. Paid profiles with
  projected overblock, including hedges, stools, shelves, crates and similar indoor/outdoor
  scenery, also stay out until they get their own proof batch. Wilderness_Ditch 23271/10 stays out
  despite a no-cost row because it is traversal/transport-like geometry, not ordinary object
  blocking.

  RULE 3 - FOOTPRINT EXPANSION IS STILL SEPARATE. The current blocker marks the object anchor
  tile through the same edge-suppression path used by the table/bench/chair pass. It does not
  invent footprint expansion for large objects in this decision. If a tree/boulder needs wider
  geometry than the cache anchor exposes, prove that in a later pass.

  PROOF. Frozen capture build/frozen-live-flags-object-profile-pass.txt was 26,141,222 bytes
  with SHA256 48033D0FCB248997626E6F9099686C8944BA853E1E53F90D8FEA97395E0390A7. Existing
  shipped furniture profiles produced DANGEROUS_UNEXPLAINED 78,069, OVERBLOCK 20,437,
  route-aware OVERBLOCK 7,468, and 764 blocked object placements. The shipped expanded list
  produced DANGEROUS_UNEXPLAINED 75,184, OVERBLOCK 20,437, route-aware OVERBLOCK 7,468, and
  7,850 blocked placements. That is 2,885 additional dangerous-unexplained edges fixed with 0 new
  measured OVERBLOCK and 0 new route-aware OVERBLOCK versus the current furniture map. Against
  the no-object diagnostic baseline, DANGEROUS_UNEXPLAINED drops 114,922 -> 75,184 while
  route-aware OVERBLOCK rises 6,857 -> 7,468, so the net gate remains OK (39,738 > 611).

  RULE 4 - KEEP THE OLD SWITCH NAME AS AN ALIAS. --disable-object-profile-blocking is now the
  accurate diagnostic flag name, but --disable-furniture-object-blocking remains accepted so old
  scripts and notes do not break while reports move to the broader object-profile wording.

  Cross-reference: extends D-0142 and follows D-0146. Report snapshot:
  tools/collision-map-v2-report.txt. Shipped map SHA256:
  FC2B4F971F40D1DAE30B54D103B071D722177A1B51DC7071C71D7242F020EECC.


D-0175 (2026-08-13) - Restore movement benchmark as the one-click route repro recorder

  RULE 1 - SAME KEY BECAUSE IT IS THE SAME QUESTION. `routeBenchmarkEnabled` means "record the
  displayed waypoint route against the player's actual walked route." That meaning has not changed,
  so reusing the old key does not violate D-0089. It only revives the same diagnostic Mytharium
  asked to use before reproducing a route-display bug.

  RULE 2 - FULL TILE TRACE, NOT SCREENSHOT FORENSICS. The start row records the complete proposed
  route as `expectedPath=[...]`. The completed/limit row records both `expectedPath=[...]` and
  `actualPath=[...]`. Prefix-only traces remain for in-progress rows, but the row that matters for
  a completed repro carries every tile.

  RULE 3 - ETA STAYS UNCONDITIONAL. This supersedes only the movement-benchmark half of D-0090.
  ETA accuracy logging remains always on and still has no config control.

  Cross-reference: D-0090 retired the movement UI after the old route-shape phase; D-0174
  reactivates it because the Falador southeast display-fidelity repro needs route-vs-actual ground
  truth again.


D-0177 (2026-08-14) - Falador southeast route-shape fix stays exact and target-aware

  RULE 1 - USE THE COMPLETED WALKED TRACE, NOT SCREENSHOT GUESSING. The accepted evidence is
  Myth's completed `DREW_ROUTE_BENCH reason=target` row for `2942,3243,0 -> 2951,3208,0`. It
  showed a 36-point displayed path versus a 39-point actual path, with the first repeatable fork
  from `(2942,3236,0)` to displayed `(2942,3235,0)` versus actual `(2943,3235,0)`.

  RULE 2 - DO NOT TURN THIS INTO BROAD TREE BLOCKING. The tree/tree-stump object-profile pass
  remains separate because D-0147 proved tree profiles can move a pinned live-route fork. This
  decision does not add tree profiles, global locType blocking, global named-solid blocking, or
  terrain-bit rules.

  RULE 3 - DO NOT PROMOTE `shapeShadow` GLOBALLY FROM ONE ROUTE. `shapeShadow` looked better on
  this sample, but D-0051/D-0057 keep it diagnostic-only until broader route-shape evidence says
  otherwise.

  RULE 4 - PATCH AS A TARGET-AWARE LOCAL ROUTE WINDOW. The route engine may force Myth's completed
  walked tile sequence only while solving toward target `(2951,3208,0)`, and only when each next
  tile is still a legal one-tile walk in the current collision map. That keeps the correction in
  the existing D-0044 local-override family instead of mutating map data globally.

  Cross-reference: D-0044 local walking overrides, D-0051/D-0057 shape diagnostics, D-0147
  tree-profile holdback, D-0175 benchmark evidence contract, D-0178 implementation note.


D-0180 (2026-08-14) - Falador southeast creative route-shape controls stay scoped to observed paths

  RULE 1 - STAGING WALKS ARE USABLE BUT NOISY. If `Log Benchmark Movement` is left enabled while
  walking to each test start tile, the captured row may contain staging or multi-waypoint context.
  Use segment diagnostics and the full `expectedPath`/`actualPath` to identify the intended route,
  but do not force obvious staging noise such as a one-tile east/back wobble at the starting tile.

  RULE 2 - CLEAN PASSES DO NOT NEED NEW PATCHES. Myth's fork isolate route
  `2942,3236,0 -> 2951,3208,0` matched the existing forced local window and should remain covered
  by the D-0177/D-0178 target-aware route. No separate override is needed just because it was part
  of the creative-control list.

  RULE 3 - REVERSE AND EAST PRESSURE ARE STILL LOCAL ROUTE WINDOWS, NOT MAP DATA. The reverse
  target `(2942,3243,0)` and east-pressure entry from `(2946,3239,0)` are patched as exact,
  target-aware local route windows from Myth's observed benchmark traces. They do not add
  tree/tree-stump object profiles, global named-solid blocking, broad tree blocking, or
  `shapeShadow` promotion.

  RULE 4 - FORCED WINDOWS MAY OVERRIDE THE STATIC COLLISION GRAPH ONLY INSIDE THE WINDOW. The
  reverse trace showed the live client walking one-tile edges that Drew's current static graph did
  not use as shortest/legal continuations. For these exact benchmark-proven route windows, the
  observed local edge is allowed to win over static collision data. This does not generalize to
  nearby trees, other targets, or object-profile blocking.

  Cross-reference: D-0175 benchmark evidence contract, D-0177 exact target-aware route-shape
  rule, D-0180 implementation note in CHANGELOG_AGENT_NOTES.


D-0183 (2026-08-14) - Batch A shifts route work from local windows to segment-aware classification

  RULE 1 - DO NOT PATCH BATCH A AS SIX LOCAL WINDOWS. Batch A was intentionally a broader system
  check. A1 and A6 show non-Falador legal route-shape misses, while A2 and A3 include object and
  door-state evidence. Treat these rows as classifier input, not as permission to keep adding
  target-aware windows.

  RULE 2 - LONG ROUTES NEED SEGMENT-AWARE EVIDENCE. Myth had to click the farthest visible tile
  repeatedly, open doors manually, and sometimes choose around trees that blocked mouse selection.
  The current one-route benchmark compares the original displayed full route to the full walked
  path, which is valid for proof that a mismatch exists but not precise enough to assign root cause
  across a long trip.

  RULE 3 - CLASSIFY BEFORE CHANGING BEHAVIOR. The next implementation step is a passive/segment
  recorder that can classify divergences into route-ranker misses, object-profile misses,
  door/traversal-state requirements, or static collision-map errors. Do not promote `shapeShadow`,
  add broad tree/dead-tree/table profiles, or mutate map data from Batch A alone.

  RULE 4 - OBJECT PROFILES STILL NEED LIVE PIN GATES. Tables, dead trees, and tree/stump profiles
  are valid suspects after Batch A, but D-0147 still applies: object-profile additions must survive
  pinned live-route checks and route-aware overblock checks before shipping.

  Cross-reference: D-0147 object-profile gates, D-0175 benchmark evidence contract, D-0177/D-0180
  local window limits, and D-0182 Batch A procedure.


D-0184 (2026-08-14) - Route-segment logs are evidence, not behaviour changes

  RULE 1 - SEGMENTS ARE THE UNIT FOR LONG-ROUTE DIAGNOSIS. When a route is walked through repeated
  visible-tile clicks, each clicked destination must be logged separately. Do not infer root cause
  from a whole-route `expectedPath`/`actualPath` row when the player had to click several times,
  open doors manually, or pick around scenery blocking mouse selection.

  RULE 2 - THE RECORDER STAYS DEFAULT OFF. `Log Route Segments` is a diagnostic switch under
  Settings, not a gameplay feature. It may write detailed `DREW_ROUTE_SEGMENT` rows while enabled,
  but it must not render anything new in-game or run by default.

  RULE 3 - SEGMENT ROWS DO NOT CHANGE ROUTING. Segment classifications are evidence labels only.
  They do not add route windows, object profiles, transport rows, collision overrides, or global
  route-ranker changes.

  RULE 4 - CLASSIFICATION IS COARSE UNTIL LIVE PINS EXIST. Treat `click-destination-off-route`,
  `legal-detour-or-object-pressure`, `legal-route-ranker-or-click-shape`, and
  `static-map-disagrees-with-live-step` as triage labels. A table, dead-tree, tree, or ranker
  change still needs a focused live pin and the D-0147 route-aware overblock gate before shipping.

  Cross-reference: D-0147 object-profile gates, D-0175 route benchmark evidence contract, and
  D-0183 Batch A segment-classification rule.


D-0185 (2026-08-14) - Interrupted route segments are click-cadence evidence first

  RULE 1 - COMPLETED SEGMENTS ARE THE DEFAULT OBJECT/RANKER PROOF. A `DREW_ROUTE_SEGMENT` row with
  `completed=true` reached the clicked destination and can be treated as normal route/object
  evidence. A `completed=false` row stopped because the player re-clicked, the destination cleared,
  the client settled, or the diagnostic limit fired before the clicked destination was reached.

  RULE 2 - RE-CLICK CADENCE IS NOT A ROUTE BUG BY ITSELF. Myth's first Batch B pass intentionally
  emulated frequent clicks, sparse clicks, and mistake clicks. Rows ending with
  `reason=destination-changed` must be labeled as interrupted rows unless the original clicked tile
  was actually reached. Use `interrupted-reclick-clean-prefix` and
  `interrupted-reclick-after-divergence` as triage labels, not as route/object changes.

  RULE 3 - FOCUSED OBJECT-PROFILE PINS SHOULD PREFER `completed=true`. Tables, dead trees, trees,
  and Sawmill/object-pressure changes still need focused completed segment rows plus the D-0147
  route-aware overblock gate. Interrupted rows can point to where to stand next, but should not
  ship object-profile or ranker changes by themselves.

  Cross-reference: D-0147 object-profile gates, D-0183 Batch A segment-classification rule, and
  D-0184 route-segment logging.


D-0186 (2026-08-14) - Object-profile proof stays gated after Batch C

  RULE 1 - COMPLETED SEGMENT ROWS CAN NAME OBJECT CANDIDATES, BUT DO NOT BYPASS D-0147. Batch C
  produced clean `completed=true` evidence for Lumbridge table, Draynor tree/dead-tree, and
  Sawmill tree-line pressure. That is enough to run object-profile proof, not enough to ship broad
  locType 10/11, all named-solid scenery, or all trees.

  RULE 2 - CANDIDATE-MAP TRIALS MUST BE COMMAND-LINE OVERRIDES FIRST. `CollisionMapBuilder`
  supports `--add-object-profile-keys=objectId/locType,...` for one build and
  `--object-profile-focus-keys=objectId/locType,...` for exact report rows. Use these switches to
  test a candidate profile set before hardcoding it into the default allowlist or promoting
  `build/collision-map-v2.zip` into `src/main/resources/collision-map.zip`.

  RULE 3 - SUPPORTED D-0186 CANDIDATES. The current supported test set is `596/10`, `10820/10`,
  `1282/10`, `1283/10`, `11510/10`, `1276/10`, `1276/11`, `1278/10`, and `1278/11`. In the
  all-region frozen-live trial these rows drove `DANGEROUS_UNEXPLAINED` from `139035` to `84729`
  while route-aware `OVERBLOCK` rose only `8264 -> 8886`, so the net gate passed (`54306 > 622`).

  RULE 4 - HOLD BACK PAID OR UNNAMED SUSPECTS. `1289/10`, `9661/10`, `7169/10`, `34803/10`, and
  `34804/10` stay out because their projected overblock/benefit rows did not clear the paid-profile
  gate in this pass. Unnamed `19143/10` stays out because it was not a named-solid profile in the
  all-region report.

  RULE 5 - TREE-FAMILY CANDIDATES STILL NEED LIVE ROUTE STABILITY. Do not treat no-cost tree rows
  as shippable by themselves. Before promoting a map containing `10820/10`, `1282/10`, `1283/10`,
  `11510/10`, `1276/10`, `1276/11`, `1278/10`, or `1278/11`, rerun the pinned Falador primary,
  reverse, and east-pressure routes plus C1/C2/C3. D-0147 already proved a no-cost tree trial can
  move a pinned live-route fork.

  Cross-reference: D-0147 object-profile gates, D-0183 Batch A classification, D-0184 route
  segments, and D-0185 completed/interrupted segment semantics.
D-0188 (2026-08-14) - D-0186 supported object profiles are promoted; held-back keys stay out

  RULE 1 - THE D-0186 SUPPORTED SET CLEARED THE PROMOTION GATE. The promoted runtime map contains
  the supported table/tree/dead-tree object-profile additions from D-0186: `596/10`, `10820/10`,
  `1282/10`, `1283/10`, `11510/10`, `1276/10`, `1276/11`, `1278/10`, and `1278/11`. The all-region
  gate stayed green (`DANGEROUS_UNEXPLAINED` drop `54306` versus route-aware `OVERBLOCK` rise
  `622`), and Myth's live pins did not expose a blocking regression.

  RULE 2 - LIVE STABILITY MEANS NO NEW STATIC-MAP REGRESSION, NOT PERFECT LONG-ROUTE SHAPE. Falador
  primary/reverse/east-pressure stayed exact. C1/C2/C3 still contain route-shape/ranker misses, but
  the post-swap completed rows did not show a new completed `static-map-disagrees-with-live-step`
  regression. Those remaining shape issues belong to route-ranker/confidence/recorder work, not to
  rolling back the supported object-profile map.

  RULE 3 - HELD-BACK KEYS REMAIN NON-SHIPPED. `1289/10`, `9661/10`, `7169/10`, `34803/10`,
  `34804/10`, and unnamed `19143/10` are still excluded. They need their own paid-profile or
  unnamed-object proof pass before they can enter a candidate map.

  Cross-reference: D-0147 object-profile gates, D-0186 supported candidate rules, and D-0187
  candidate-map build report.

D-0189 (2026-08-14) - Confidence is explicit route-data metadata.

  RULE 1 - USE THE FOUR D-0136 TIERS. `INHERITED` means copied from an upstream or legacy
  source we have not independently derived or live-checked. `INFERRED` means generated from
  current cache/tooling. `CONFIRMED` means live/manual proof supports the row. `CONTRADICTED`
  is reserved for known disagreements that must not be silently merged away.

  RULE 2 - COLLISION PROVENANCE IS A SIDECAR, NOT ZIP TIMESTAMP FORENSICS. The runtime
  collision archive remains `collision-map.zip`; provenance lives in
  `collision-map-confidence.tsv`. A `*` row supplies default provenance for every archive
  entry, and future region-specific rows may override it. Missing sidecar means legacy
  `INHERITED`, not a crash.

  RULE 3 - TRANSPORT PROVENANCE RIDES WITH EACH ROW. `drewshelper-transports.tsv` now carries
  `confidence` and `provenance` columns after `wildernessLevel`. Old 4/10/11-column resources
  still parse as `INHERITED` so a partial checkout does not break the route graph. The
  generator emits Skretzo rows as `INHERITED` and `tools/transport-overrides.tsv` rows as
  `CONFIRMED` by default.

  RULE 4 - MERGE CONFLICTS DO NOT GET AUTO-RESOLVED BY RECENCY. Until the object/door-state
  recorder lands, a proven disagreement should be represented as `CONTRADICTED` or kept out
  of the active resource. Do not overwrite a `CONFIRMED` row with an `INFERRED` or
  `INHERITED` row just because a generator saw it later.

  Cross-reference: implements D-0136 RULE 5 and preserves D-0105 generated-resource rules.

D-0191 (2026-08-14) - Object and door-state rows are evidence, not automatic map edits.

  RULE 1 - STATE MUST RIDE WITH IDENTITY. A captured object row must keep the base object id,
  active impostor id, action tokens, varbit/varp hooks, object kind, world tile, scene tile,
  orientation/config/hash, live edge flags, and collision-map confidence/provenance. Do not collapse
  this evidence into "object id was present" because open/closed/pulled/changed state is the useful
  signal.

  RULE 2 - LIVE SCENE ROWS ARE CONFIRMED OBSERVATIONS, NOT PROMOTED RESOURCES. `DREW_OBJECT_STATE`
  rows use `confidence=CONFIRMED` with `provenance=runelite-scene-live`, but they do not themselves
  update `collision-map.zip`, `collision-map-confidence.tsv`, `drewshelper-transports.tsv`, or any
  object-profile allowlist.

  RULE 3 - CURRENT-STATE DUPLICATES ARE SUPPRESSED PER SESSION. The recorder may scan the loaded
  scene repeatedly, but identical current-state bodies should not spam the evidence file. A state
  change still produces a new row because the active id/actions/state body changes.

  RULE 4 - SHARED OBJECT-DEFINITION RESOLUTION LIVES IN ROUTING. Guarded active-impostor lookup is
  now centralized in `DrewsHelperObjectDefinitions`; callers must check `getImpostorIds()` before
  using RuneLite's active impostor to avoid unsafe direct `getImpostor()` calls.

  Cross-reference: D-0189 confidence tiers, D-0188 promoted collision map, D-0186 held-back object
  keys, and the next route-validation harness item.

D-0192 (2026-08-14) - Route-validation harness is the gate before pilot-region cleanup.

  RULE 1 - THE HARNESS IS REPORT-ONLY. `gradlew validateRoutes` may read shipped route resources,
  route segment evidence, and object/door-state evidence, and it may write
  `tools/route-validation-harness.txt`. It must not rewrite `collision-map.zip`,
  `collision-map-confidence.tsv`, `drewshelper-transports.tsv`, or any object-profile allowlist.

  RULE 2 - OFFLINE STRUCTURAL ERRORS ARE HARD GATES. A READY route whose path does not start/end
  on the requested tiles, or whose path contains a step that is neither legal walking nor a known
  transport hop, is a structural failure. Do not promote a candidate map/ranker change while this
  count is non-zero.

  RULE 3 - HAND-WALKED EVIDENCE IS TRIAGE INPUT, NOT A PROMOTION BY ITSELF. `DREW_ROUTE_SEGMENT`
  rows classify route-vs-actual behavior. `DREW_OBJECT_STATE` rows describe nearby live object and
  door state. The harness may correlate them to choose the next live test target, but the rows do
  not automatically become collision, transport, or object-profile data.

  RULE 4 - TURN COUNT LIVES INSIDE THE HARNESS. The old standalone turn-count item is absorbed by
  the offline report's route length and turn-delta metrics between the current client-style solve
  and the shape-ranking solve.

  Cross-reference: D-0136 RULE 4, D-0185 segment evidence, and D-0191 object/door-state evidence.

D-0193 (2026-08-14) - Pilot cleanup hard gates require completed adjacent evidence.

  RULE 1 - PILOT CLEANUP IS REPORT-ONLY UNTIL A CLEAN ROW EXISTS. `gradlew pilotRegionCleanup`
  filters the current route/object evidence to the recorder-first pilot rectangle `rx45-48 /
  ry49-52`, confirms the shipped map has those regions, and writes `tools/pilot-region-cleanup.txt`.
  It must not rewrite `collision-map.zip`, `collision-map-confidence.tsv`,
  `drewshelper-transports.tsv`, route behavior, or object-profile allowlists.

  RULE 2 - INTERRUPTED OR NON-ADJACENT `legal=false` ROWS ARE NOT PROMOTION GATES. They are useful
  triage evidence, but a row that ended because the destination changed, or whose observed actual
  jump is non-adjacent, can be click cadence/client-tick compression rather than a single missing
  collision edge. Count it as `nonPromotableIllegalObservedEdges` and recapture it before promoting
  a map row.

  RULE 3 - THE CURRENT PILOT TARGET IS `48_50`, NOT AL KHARID OR VARLAMORE. Existing evidence puts
  the only current static-disagreement-looking row at `(3092,3245,0)` toward `(3131,3252,0)`, inside
  region `48_50`. The report named it `NEEDS_FOCUSED_RECAPTURE` because the row was interrupted and
  no object/door-state rows overlapped it. Do not jump to the parked `52_50` / `52_51` Al Kharid
  slice, Varlamore, broad tree blocking, or held-back object keys from this evidence.

  Cross-reference: D-0136 pilot-region rule, D-0185 interrupted segment handling, D-0191
  object/door-state rows, and D-0192 route-validation harness.

D-0194 (2026-08-14) - Focused clean recaptures supersede stale interrupted pilot rows.

  RULE 1 - DO NOT LET STALE NON-PROMOTABLE ROWS BLOCK FOREVER. If an interrupted or non-adjacent
  `legal=false` pilot row later has a focused clean recapture from the same or neighboring start
  tile to the same clicked destination, keep the stale row visible but count it as
  `supersededNonPromotableIllegalEdges`, not an unresolved recapture blocker.

  RULE 2 - CLEAN LEGAL RECAPTURE MEANS NO STATIC-MAP PROMOTION. Myth's focused `48_50` recapture
  near `(3092,3245,0) -> (3131,3252,0)` produced a completed row with `legal=true` and
  `classification=legal-detour-or-object-pressure`. That disproves the old row as a collision-map
  promotion gate. It does not justify a collision override, object-profile addition, or route-ranker
  patch by itself.

  RULE 3 - PILOT OUTPUT MUST PRESERVE BOTH FACTS. The report should show the old row as
  superseded, while still reporting `completedAdjacentIllegalEdges=0` and
  `verdict=NO_COMPLETED_STATIC_DISAGREEMENT`. This keeps historical evidence auditable without
  forcing Myth to clear the log file manually.

  Cross-reference: D-0193 pilot hard gates, D-0185 interrupted segment handling, and D-0192
  route-validation harness.

D-0195 (2026-08-14) - Requirement messaging uses same-policy unrestricted near-miss solves.

  RULE 1 - REQUIREMENTS ARE A SEPARATE BLOCK, NOT AN ACTION. `Requirements` rows describe why a
  destination cannot currently be reached. They render below the waypoint/action display and must
  never be numbered as route actions, because the player does not click a missing requirement.

  RULE 2 - DO NOT INVENT A SECOND REQUIREMENT SYSTEM. Requirement text is derived from the same
  `DrewsHelperPlayerCapability` checks that already allow or deny a transport edge: skills, item
  alternatives, quests, varbits, varplayers, and cooldown vars. Unknown ordinary quest/var data
  stays permissive; unknown cooldown vars stay locked.

  RULE 3 - RETAIN NEAR MISSES BY DIAGNOSTIC SOLVE. The normal route graph still filters unusable
  edges before pathfinding. When that filtered solve returns NO_PATH, the engine runs a second
  solve with the same transport policy but unrestricted account capability. If that diagnostic path
  reaches the waypoint through capability-locked edges, the route snapshot carries the missing
  requirements.

  RULE 4 - SAILING IS CODE-READY, NOT DATA-SHIPPED. `SAILING` is now a supported transport
  category, it is always policy-enabled like other capability-gated families, it labels as
  `Sailing (...)`, and `Skill.SAILING` invalidates cached routes. Active `SAILING` rows must not be
  added until their land-side gangplank/dock interaction tiles are verified. Port-task navigation
  points and wiki map pins are evidence, not enough to create Drew's land-route edges.

  Cross-reference: D-0143 account capability gates, D-0136 recorder-first route shape, D-0189
  confidence tiers, and D-0194 closed pilot cleanup.
