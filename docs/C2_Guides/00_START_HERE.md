# Drew's Helper C2 Guides

Last updated: 2026-08-07.

## Project

Drew's Helper is a RuneLite external plugin in:

```text
C:\Users\drews\OneDrive\Documents\My Games\RuneScape\Drews Helper
```

Use `lcl-ssh` against `mythpc` for live file checks and edits. Do not commit, push, pull, or alter the active RuneLite profile unless Myth explicitly asks for that specific operation.

## Build And Run

```bat
gradlew.bat --no-daemon --console=plain clean test build
gradlew.bat run
```

## Current Runtime Shape

As of the 2026-08-07 reset, waypoint follow-up, and walking-route follow-up, Drew's Helper is a Drew-owned waypoint and walking-route plugin. It does not restore the old vendored `shortestpath` package.

Runtime shape:
- `gradlew.bat run` loads only `com.drewshelper.DrewsHelperPlugin` as the visible RuneLite plugin.
- `DrewsHelperPlugin` registers/removes the Drew's Helper overlay, route overlays, and five persistent world-map waypoint markers.
- `com.drewshelper.routing/**` owns the Drew walking-only route snapshot, collision-map loader, and A* solver.
- `com.drewshelper.routing.ui/**` renders that one route snapshot on the world map, minimap, and in-scene base tiles.
- `DrewsHelperConfig` owns the preserved player-facing config buttons/dropdowns.
- `DrewsHelperOverlay` owns the preserved in-client overlay panel, waypoint status display, route status, and walking distance.
- `JewelleryBoxTier` and `PortalNexusTier` remain only because config dropdowns need them.
- `src/main/resources/collision-map.zip` is present again as a third-party walking collision data resource from Runemoro's BSD-licensed `shortest-path`; license notes live in `THIRD_PARTY_NOTICES.md`.

Removed systems:
- Vendored `src/main/java/shortestpath/**` route engine.
- Shortest Path pathfinder, plugin-message bridge, and transport telemetry.
- Fast travel/teleport/transport resources and route logic.
- Minigame teleport scanner/cache, teleport highlighter, route snapshots, session route persistence, and route diagnostics.
- Route behavior tests and diagnostic tools.

## Upstream Reference Notes

Myth asked for a deep read of Runemoro's original Shortest Path so Drew's Helper can rebuild a clean variant later. The persistent reference note is:

```text
docs/C2_Guides/RUNEMORO_SHORTEST_PATH_DEEP_DIVE.md
```

Use that note before designing any new route engine. The current rule is: learn from Runemoro's single-owner route model, but do not restore the deleted vendored route stack.

## Resume Rule

Start tomorrow's work in `02_NEXT_WORK.md`. The active route feature is walking-only guidance from the player through placed waypoints. Do not restore Shortest Path telemetry, fast travel, minigame teleport scanning, tab highlighting, or route diagnostics unless Myth explicitly asks to rebuild those systems from the Drew-owned route model.
