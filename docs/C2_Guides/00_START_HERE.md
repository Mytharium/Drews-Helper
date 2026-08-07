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

As of the 2026-08-07 reset, Drew's Helper is UI-only.

Runtime shape:
- `gradlew.bat run` loads only `com.drewshelper.DrewsHelperPlugin` as the visible RuneLite plugin.
- `DrewsHelperPlugin` only registers/removes the Drew's Helper overlay.
- `DrewsHelperConfig` owns the preserved player-facing config buttons/dropdowns.
- `DrewsHelperOverlay` owns the preserved in-client overlay panel.
- `JewelleryBoxTier` and `PortalNexusTier` remain only because config dropdowns need them.

Removed systems:
- Vendored `src/main/java/shortestpath/**` route engine.
- Shortest Path pathfinder, map/minimap/tile overlays, plugin-message bridge, and transport telemetry.
- `src/main/resources/**` route/path/transport data.
- Minigame teleport scanner/cache, teleport highlighter, route snapshots, session route persistence, and route diagnostics.
- Route behavior tests and diagnostic tools.

## Resume Rule

Start tomorrow's work in `02_NEXT_WORK.md`. Do not restore route drawing, Shortest Path telemetry, minigame teleport scanning, tab highlighting, or route diagnostics unless Myth explicitly asks to rebuild those systems from the UI shell.
