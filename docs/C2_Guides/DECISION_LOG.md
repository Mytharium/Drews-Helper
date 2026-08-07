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
