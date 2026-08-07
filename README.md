# Drew's Helper

Drew's Helper is currently a RuneLite external plugin with Drew-owned waypoint placement and walking-only route guidance.

The old vendored Shortest Path engine, transport bridge, minigame scanner, teleport highlighter, and route diagnostics were removed on 2026-08-07 per Myth's reset instruction. The live mod now rebuilds route guidance from the Drew waypoint surface with no fast travel, teleports, plugin messages, or teleport UI automation.

## Current Features

- RuneLite external-plugin scaffold
- Local dev launcher: `gradlew.bat run`
- Drew's Helper config sections/buttons/dropdowns
- Drew's Helper overlay panel
- Five persistent world-map waypoints set from the world-map right-click menu
- Walking-only route calculation from the player to each placed waypoint in order
- Route drawing on the world map, minimap, and in-scene base tiles

## Build

```bat
gradlew.bat clean test build
```

## Run Locally

```bat
gradlew.bat run
```

## Test Waypoint Routing

1. Launch the dev client with `run-drews-helper-dev.bat` or `gradlew.bat run`.
2. Enable Drew's Helper.
3. Confirm the plugin config buttons/dropdowns are visible.
4. Open the world map and right-click inside the map to set `Waypoint #1` through `Waypoint #5`.
5. Confirm the Drew's Helper overlay reports the placed waypoint count, coordinates, route status, and walking distance.
6. Confirm the configured path colour draws on the world map, minimap, and in-scene tiles.

## C2 Guide Notes

Development notes are in `docs/C2_Guides`.
