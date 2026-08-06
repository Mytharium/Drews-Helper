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

When Drew owns an active locked-route fallback, it must apply that policy to incoming `shortestpath/path` plugin messages before Shortest Path consumes them instead of only posting a competing request afterward. Use high-priority `PluginMessage` handling to merge Drew's config overrides into the existing request, preserve the external request's target/start data, and suppress stale locked transport snapshots while the fallback signature is active.
