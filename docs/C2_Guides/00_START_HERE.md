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

Drew's Helper does not replace Shortest Path's pathfinder yet. It bridges into Shortest Path by sending plugin messages and requesting transport telemetry with `postTransports=true`.

Owned Drew systems:
- `ShortestPathBridge` sends `shortestpath/path` requests, captures Quest Helper target messages, and parses posted transport lists.
- `RouteTransportState` stores the latest route transport snapshot in memory.
- `DrewsHelperSessionState` persists route snapshots, Shortest Path targets, and minigame locked/unlocked statuses in RuneLite config.
- `MinigameTeleportUnlockState` scans the Grouping/minigame UI and caches per-destination `AVAILABLE` / `LOCKED` results.
- `TeleportAvailabilityService` is the single owner for deciding whether a posted transport is currently usable by Drew's rules.
- `DrewsHelperOverlay` renders the route list, minigame unlock count, and locked-route list.
- `TeleportHighlightOverlay` highlights the magic tab/spell before the minigame UI opens, then the destination row / Teleport button while the UI is open.

## Resume Rule

Start tomorrow's work in `02_NEXT_WORK.md`. The main unresolved feature is exact locked-route rerouting. Do not present Drew's filtered overlay as a true reroute unless Shortest Path's path calculation is actually changed.
