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
- Drew converts scanned locked minigames into stable Shortest Path transport keys such as `teleportation_minigames:nightmare_zone`, sends them as `config.blockedTransportKeys`, and replays a real captured route target once when posted telemetry still contains a locked route.
- If stock Shortest Path posts the same locked minigame route after that exact-key replay, Drew escalates to the supported category fallback `useTeleportationMinigames=false` so the active jar can do a real recalculation today.
- Drew's `PluginMessage` subscriber now runs at high priority and merges the active locked-teleport policy into incoming `shortestpath/path` requests before Shortest Path consumes them, so Quest Helper/Shortest Path refreshes should not reassert the locked minigame route over Drew's fallback.
- While the minigame-category fallback is active for the current target, Drew ignores stale transport snapshots that still contain locked minigame teleports instead of saving/displaying them over the valid fallback route.
- If Drew has not captured a real `shortestpath/path` target, it sends config-only route requests and lets Shortest Path reuse its own current target set. Drew must not treat a transport destination from `shortestpath/transports` as the final route target.
- A source patch for Shortest Path `1.20.6` / `Skretzo/shortest-path@9953d52745f711a38c9cdd4a00bb1d0d57d1fdea` is staged at `docs/patches/shortest-path-blocked-transport-keys.patch`.

## Current Overlay Layout

Expected route overlay shape:

```text
Drew's Helper
Current Route Step       1/3
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

The stock installed jar still does not consume `blockedTransportKeys`; it safely ignores that unknown override. Exact per-destination rerouting becomes active after applying and running the staged Shortest Path patch/fork.

Until that patched Shortest Path build is installed, Drew falls back to Shortest Path's supported `useTeleportationMinigames=false` setting after it sees the same locked minigame route survive one exact-key replay. That produces a real recalculation with the stock jar, but it blocks the whole minigame teleport category, including any minigames Drew has already confirmed unlocked. If no real target was captured, the fallback is sent as a config-only request so Shortest Path keeps its current target instead of Drew guessing from route telemetry.
