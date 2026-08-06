# Drew's Helper

Drew's Helper is a RuneLite external plugin for route and teleport assistance.

This build establishes the plugin shell, Plugin Hub metadata, local RuneLite launcher, feature toggles, and the first runtime route bridge. Route ownership will be implemented inside this plugin so it can replace the stock Shortest Path overlay instead of competing with it.

## Current Features

- RuneLite external-plugin scaffold
- Local dev launcher: `gradlew.bat run`
- Routing options for Drew's Shortest Path, Chain Quests, and Quest Preparation sources
- Teleport options for highlighting, locked-teleport filtering, cooldown rerouting, hosted POH fallback, manual unlocks, minigame/grouping teleport scanning, and jewellery box tier selection
- Shortest Path transport-feed bridge with config overrides for early in-game route telemetry
- Drew's Helper overlay showing the active route feed, next unlocked transport, hidden locked transports, minigame unlocked count, and first route transports
- Minigame/grouping teleport UI scanner/highlighter for the magic tab/spell, matching destination row, current Grouping selection, and teleport button
- Session restore for the last route transport feed, Quest Helper/Shortest Path target messages, and scanned locked/unlocked minigame teleport statuses after plugin toggle, logout, or client restart

## Build

```bat
gradlew.bat clean test build
```

## Run Locally

```bat
gradlew.bat run
```

## Test Route Feed Overlay

1. Launch the dev client with `gradlew.bat run`.
2. Enable Drew's Helper.
3. Enable Shortest Path in the same client.
4. Set a Shortest Path destination that uses a transport or teleport.
5. Watch the Drew's Helper overlay for `Route Feed: Active`, the full-width `Next:` line, `Transports`, and the first transport rows.

If the overlay stays on `Route Feed: Waiting`, Shortest Path has not produced a transport-bearing route yet.

## Test Minigame Teleport Detection

1. Open Drew's Helper settings.
2. Leave `Hide Locked Teleports` checked.
3. Set a Shortest Path destination that recommends a minigame teleport.
4. Open the magic tab. Drew's Helper should highlight the minigame teleport spell.
5. Open the minigame/grouping teleport interface. Drew's Helper scans destination rows that are currently visible in the scroll window and refreshes its cached locked/unlocked statuses.
6. Watch the overlay for `Minigames: <unlocked>/18 Unlocked`; after any row is classified, `Stored Scan: <known>/18` shows how many locked/unlocked decisions are cached.
7. If the recommended destination is not the current Grouping selection, open the dropdown so Drew's Helper can scan/highlight the matching destination.
8. If the recommended destination is the current Grouping selection, Drew's Helper highlights the destination and the Teleport button.
9. If the recommended destination is detected as locked, Drew's Helper marks it as locked/hidden locally and highlights the matching row in warning color while the interface is open.

Unknown minigame destinations are not treated as locked. Drew's Helper stores scanned locked/unlocked minigames after plugin toggle, logout, or client restart, then refreshes those statuses whenever the minigame interface exposes the row again. Highlight boxes are clipped to the minigame window and deduped to the destination row, so text children and offscreen scrolled rows are not highlighted separately.

## Test Session Restore

1. Set a Shortest Path destination that produces route transports.
2. Confirm Drew's Helper shows `Route Feed: Active`.
3. Toggle Drew's Helper off, then back on.
4. The overlay should restore the previous route immediately, then refresh it from Shortest Path within the next few ticks.
5. Log out and back in with the same client open. Drew's Helper should request a fresh feed immediately after login instead of waiting for the normal refresh interval.
6. For Quest Helper routes, Drew's Helper captures the `shortestpath/path` target message and replays that target after login so the Shortest Path marker/path is set again.

Manual Shortest Path map-click targets are internal to Shortest Path and are not published in its transport feed. Until Shortest Path publishes those targets, Drew's Helper can only replay a best-effort target from the last saved transport destination for manual routes.

Publishing notes are in `docs/PUBLISHING.md`.
