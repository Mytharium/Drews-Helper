# Current State

Last updated: 2026-08-06.

## Working

- Drew's Helper launches as a RuneLite external plugin through `gradlew.bat run`.
- The overlay receives Shortest Path transport telemetry through `shortestpath/transports`.
- The overlay no longer uses the narrow right-column `Next` display for route text.
- Quest Helper route targets sent through `shortestpath/path` are captured and replayed after login/startup.
- Drew passes whole-category unlock settings for spirit trees, fairy rings, and owned POH features into Shortest Path when `Hide Locked Teleports` is enabled, so those supported categories can trigger real Shortest Path recalculation.
- The last route snapshot is restored locally after plugin toggle, logout, world hop, or client restart.
- The minigame/grouping teleport UI scanner works against the real Grouping UI and walks all RuneLite widget child arrays.
- Minigame destination highlighting is clipped to visible widget bounds, so scrolled-off entries should not leave boxes stuck at the top/bottom of the panel.
- The highlighter draws one outer row box for a minigame destination instead of also boxing the text child.
- Per-destination minigame statuses persist across logout, world hop, and client restart.
- The in-game overlay now reports minigame state as `Minigame Teleports: X/18 Unlocked`.

## Current Overlay Layout

Expected route overlay shape:

```text
Drew's Helper
Current Route Step 1/3
1. Nightmare Zone Minigame Teleport
2. Bounty Hunter Minigame Teleport
3. Spirit tree -> Tree Gnome Village
Minigame Teleports      7/18 Unlocked
Locked Routes           1
1. Nightmare Zone Minigame Teleport
```

`Stored Scan` is intentionally hidden from the player-facing overlay now that persistence works. The backend still tracks known/cache counts through `MinigameTeleportUnlockState`.

## Minigame Cache Meaning

- `AVAILABLE`: the Grouping/minigame UI exposed the row without locked/requirement text.
- `LOCKED`: the UI row had requirement text such as quest, boss-completion, or NPC prereq text.
- `UNKNOWN`: no saved decision. Unknown destinations are not treated as locked.

The cache refreshes whenever the menu exposes the row again.

## Known Limitations

Shortest Path currently exposes plugin-message control for:
- `start`
- `target`
- `config` overrides

The installed Shortest Path config supports category-level transport toggles/costs such as boats, ships, spirit trees, fairy rings, minigame teleports, POH, spells, items, portals, and similar groups.

It does not expose a per-destination or per-transport block list through `shortestpath/path`. Because of that, Drew can identify and list locked routes, and can reroute whole disabled categories that Shortest Path already exposes. Exact rerouting around only one locked minigame destination requires a Shortest Path patch or a Drew-owned route solver.
