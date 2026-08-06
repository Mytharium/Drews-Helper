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

## Open Technical Note

Exact locked-route rerouting is wired on Drew's side but still depends on running the patched Shortest Path build. The stock installed Shortest Path jar safely ignores `blockedTransportKeys`; Drew now escalates to `useTeleportationMinigames=false` after that exact replay fails, which is a real but broad fallback. Do not claim active exact per-destination rerouting until the patched/forked jar is installed and tested.
