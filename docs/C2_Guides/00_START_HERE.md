# Drew's Helper C2 Guides

Last updated: 2026-08-06.

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

Drew's Helper now vendors a Drew-owned pathfinder as an internal Drew's Shortest Path feature. The source is adapted from the BSD-2-Clause `Skretzo/shortest-path` fork, but it is compiled and launched from the `Drews Helper` project instead of relying on the Plugin Hub Shortest Path mod.

Runtime shape:
- `gradlew.bat run` loads only `com.drewshelper.DrewsHelperPlugin` as the visible RuneLite plugin.
- `DrewsHelperPlugin` starts the vendored `shortestpath.ShortestPathPlugin` internally as Drew's Shortest Path route engine.
- The `shortestpath` plugin-message namespace is intentionally retained for Quest Helper / Drew Helper compatibility.
- The internal route engine uses a hidden runtime-default config object. Do not expose the copied Shortest Path `drewpath` settings panel; player-facing settings live in `DrewsHelperConfig`.
- There should be no active `shortest-path_*.jar` in `C:\Users\drews\.runelite\plugins`.
- Old Shortest Path jars are backed up under `C:\Users\drews\.runelite\plugins-c2-backups`.

Owned Drew systems:
- `src/main/java/shortestpath/**` owns the internal route engine, map/minimap/tile overlays, transport data, and the `blockedTransportKeys` solver hook.
- `ShortestPathBridge` sends `shortestpath/path` requests, captures Quest Helper target messages, and parses posted transport lists.
- `RouteTransportState` stores the latest route transport snapshot in memory.
- `DrewsHelperSessionState` persists route snapshots, Shortest Path targets, and minigame locked/unlocked statuses in RuneLite config. `DrewsHelperPlugin` must sync manual targets from the internal route engine because right-click/shift-click routes do not arrive as external `shortestpath/path` messages.
- `MinigameTeleportUnlockState` scans the Grouping/minigame UI and caches per-destination `AVAILABLE` / `LOCKED` results.
- `TeleportAvailabilityService` is the single owner for deciding whether a posted transport is currently usable by Drew's rules.
- `DrewsHelperOverlay` renders the route list, minigame unlock count, and locked-route list.
- `TeleportHighlightOverlay` highlights the magic tab/spell before the minigame UI opens, then the destination row / Teleport button while the UI is open.

## Resume Rule

Start tomorrow's work in `02_NEXT_WORK.md`. Exact locked-route rerouting is now integrated into Drew's Helper, but still needs live RuneLite route testing. Do not call it proven until the in-game route markers and Drew overlay both avoid a locked destination such as Nightmare Zone while still allowing other available minigame teleports.
