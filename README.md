# Drew's Helper

Drew's Helper is a RuneLite external plugin for route and teleport assistance.

This first build establishes the plugin shell, Plugin Hub metadata, local RuneLite launcher, and feature toggles. Route ownership will be implemented inside this plugin so it can replace the stock Shortest Path overlay instead of competing with it.

## Current Features

- RuneLite external-plugin scaffold
- Local dev launcher: `gradlew.bat run`
- Feature toggles for path ownership, teleport highlighting, unavailable teleport filtering, cooldown rerouting, local-exit preference, Quest Helper prep routing, GE prep routing, and manual unlocks

## Build

```bat
gradlew.bat clean test build
```

## Run Locally

```bat
gradlew.bat run
```

Publishing notes are in `docs/PUBLISHING.md`.
