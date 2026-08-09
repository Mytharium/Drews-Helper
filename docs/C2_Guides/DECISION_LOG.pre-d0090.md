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
