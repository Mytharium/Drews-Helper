# Drew's Helper

Drew's Helper is a RuneLite external plugin for route and teleport assistance.

This build establishes the plugin shell, Plugin Hub metadata, local RuneLite launcher, feature toggles, and the first runtime route bridge. Route ownership will be implemented inside this plugin so it can replace the stock Shortest Path overlay instead of competing with it.

## Current Features

- RuneLite external-plugin scaffold
- Local dev launcher: `gradlew.bat run`
- Routing options for Drew's Shortest Path, Chain Quests, and Quest Preparation sources
- Teleport options for highlighting, locked-teleport filtering, cooldown rerouting, hosted POH fallback, manual unlocks, and jewellery box tier selection
- Shortest Path transport-feed bridge for early in-game route telemetry
- Drew's Helper overlay showing the next transport from the active route feed

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
5. Watch the Drew's Helper overlay for `Next` and `Transports`.

If the overlay stays on `Route Feed: Waiting`, Shortest Path has not produced a transport-bearing route yet.

Publishing notes are in `docs/PUBLISHING.md`.
