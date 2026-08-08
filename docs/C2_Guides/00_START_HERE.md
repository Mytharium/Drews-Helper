# Drew's Helper C2 Guides

Last updated: 2026-08-08.

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

As of the 2026-08-07 reset and route follow-ups, Drew's Helper is a Drew-owned waypoint and route-guidance plugin. It does not restore the old vendored `shortestpath` package.

Runtime shape:
- `gradlew.bat run` loads only `com.drewshelper.DrewsHelperPlugin` as the visible RuneLite plugin.
- `DrewsHelperPlugin` registers/removes the Drew's Helper overlay, route overlays, and five persistent world-map waypoint markers.
- `com.drewshelper.routing/**` owns the Drew route snapshot, collision-map loader, baseline transport graph, Wilderness transport filter, and A* solver.
- `com.drewshelper.routing.ui/**` renders that one route snapshot on the world map, minimap, and in-scene base tiles.
- `DrewsHelperConfig` owns the preserved player-facing config buttons/dropdowns.
- `DrewsHelperOverlay` owns the preserved in-client overlay panel, waypoint status display, route status, and route-step count.
- `JewelleryBoxTier` and `PortalNexusTier` remain only because config dropdowns need them.
- `src/main/resources/collision-map.zip` is present again as a third-party walking collision data resource from Runemoro's BSD-licensed `shortest-path`.
- `src/main/resources/drewshelper-transports.tsv` is a Drew-generated baseline/Wilderness transport edge file from selected Skretzo `shortest-path` transport TSVs; license notes live in `THIRD_PARTY_NOTICES.md`.

Removed systems:
- Vendored `src/main/java/shortestpath/**` route engine.
- Shortest Path pathfinder, plugin-message bridge, and transport telemetry.
- Old vendored fast travel/teleport/transport resources and route logic.
- Minigame teleport scanner/cache, teleport highlighter, route snapshots, session route persistence, and route diagnostics.
- Route behavior tests and diagnostic tools.

## Upstream Reference Notes

Myth asked for a deep read of Runemoro's original Shortest Path so Drew's Helper can rebuild a clean variant later. The persistent reference note is:

```text
docs/C2_Guides/RUNEMORO_SHORTEST_PATH_DEEP_DIVE.md
```

Use that note before designing any new route engine. The current rule is: learn from Runemoro's single-owner route model, but do not restore the deleted vendored route stack.

## Resume Rule

Start tomorrow's work in `02_NEXT_WORK.md`. The active route feature is Drew-owned waypoint guidance with baseline click/pay physical transports built into the graph and one `Use: Wilderness Transports` toggle for Wilderness levers/obelisks. Do not restore Shortest Path telemetry, fast travel, minigame teleport scanning, tab highlighting, or route diagnostics unless Myth explicitly asks to rebuild those systems from the Drew-owned route model.

## Current Route Diagnostics

As of D-0057, the current route-diagnostics phase is closed. Visible routing still uses the target-aware Path 1 / Path 3 local walking overrides from D-0044 through D-0046, and the latest Point 1 / Point 2 / Point 3 controls all passed with `full=true`, `lenDelta=0`, `maxDev=0`, and `divergence={none}`. Benchmark reports keep `shadow={...}`, `shapeShadow={...}`, `classification=<...>` / `benign=<...>`, `additionalDivergences={...}`, and `additionalDivergenceDetail={idx=... candidates={...} edgeValidation={...} forkRank={...}}` as telemetry. The repeated same-chain `actualRank=1` signal did not generalize across new random chains, so do not promote `shapeShadow`, add broad local ranking, or remove the overrides from this evidence.
