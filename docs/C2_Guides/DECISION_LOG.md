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
