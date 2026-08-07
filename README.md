# Drew's Helper

Drew's Helper is currently a UI-only RuneLite external plugin shell.

The route engine, Shortest Path bridge, transport resources, minigame scanner, teleport highlighter, route diagnostics, and saved route state were removed on 2026-08-07 per Myth's reset instruction. The live mod now preserves only the visible plugin entry, config/buttons surface, and in-client overlay panel.

## Current Features

- RuneLite external-plugin scaffold
- Local dev launcher: `gradlew.bat run`
- Drew's Helper config sections/buttons/dropdowns
- Drew's Helper overlay panel

## Build

```bat
gradlew.bat clean test build
```

## Run Locally

```bat
gradlew.bat run
```

## Test UI Shell

1. Launch the dev client with `run-drews-helper-dev.bat` or `gradlew.bat run`.
2. Enable Drew's Helper.
3. Confirm the plugin config buttons/dropdowns are visible.
4. Confirm the Drew's Helper overlay panel appears when the preserved UI toggles allow it.

## C2 Guide Notes

Development notes are in `docs/C2_Guides`.
