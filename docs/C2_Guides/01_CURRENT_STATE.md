# Current State

Last updated: 2026-08-07.

## Working

- Drew's Helper launches as a RuneLite external plugin through `gradlew.bat run`.
- `gradlew.bat run` now loads one visible RuneLite plugin: `Drew's Helper`; a dev-launch probe confirmed `DrewsHelperPlugin` loads and starts.
- Drew's Shortest Path is vendored under `src/main/java/shortestpath/**` with resources under `src/main/resources/**`; `DrewsHelperPlugin` starts it internally and it no longer depends on the Plugin Hub Shortest Path jar being installed.
- The internal route engine lazy-creates its map/minimap/tile/debug overlays after plugin construction to avoid a Guice dependency cycle when loaded as a Drew's Helper feature.
- The internal route engine is hidden and uses `DrewShortestPathInternalConfig` runtime defaults instead of `ConfigManager.getConfig(ShortestPathConfig.class)`, so the copied Shortest Path `Settings` / `Transport Thresholds` panel should not be player-facing.
- The overlay receives Drew's Shortest Path transport telemetry through the retained `shortestpath/transports` protocol.
- The overlay no longer uses the narrow right-column `Next` display for route text.
- Quest Helper route targets sent through `shortestpath/path` are captured and replayed after login/startup.
- Drew passes whole-category unlock settings for spirit trees, fairy rings, and owned POH features into the internal route engine, so those supported categories can trigger real path recalculation.
- The last route snapshot and the real route target are restored after plugin toggle, logout, world hop, or client restart. Manual right-click/shift-click targets are synced from the internal route engine; Quest Helper targets are still captured from `shortestpath/path` messages.
- The minigame/grouping teleport UI scanner works against the real Grouping UI and walks all RuneLite widget child arrays.
- Minigame destination highlighting is clipped to visible widget bounds, so scrolled-off entries should not leave boxes stuck at the top/bottom of the panel.
- The highlighter draws one outer row box for a minigame destination instead of also boxing the text child.
- Per-destination minigame statuses persist across logout, world hop, and client restart.
- The in-game overlay now reports minigame state as `Minigame Teleports: X/18 Unlocked`.
- Drew converts scanned locked minigames into stable transport keys such as `teleportation_minigames:nightmare_zone`, sends them as `config.blockedTransportKeys`, and replays a real captured route target once when posted telemetry still contains a locked route.
- Drew's Shortest Path consumes `config.blockedTransportKeys` directly and filters matching transports before path edges are built.
- Drew's Helper now owns the player-facing transport config split:
  - Base Drew's Shortest Path transports: gates/passages, boats, ordinary ships/ferries, charter ships, magic carpets, minecarts, home teleports, teleport levers, fixed teleport portals, spellbook teleports, and minigame teleports are enabled internally while Drew's Shortest Path is running, so they are not shown as player unlocks.
  - `Basic Transportation`: account-progress or preference-based travel networks in this order: agility shortcuts, canoes, quetzals, gnome gliders, grapple shortcuts, magic mushtrees, and hot-air balloons.
  - `Advanced Transportation`: spirit trees, fairy rings, mounted glory, portal chamber, portal nexus tier, and jewelry box tier.
  - `Other Transportation`: standard/ancient/lunar/Arceuus/other tablets, 1-use items, teleport scrolls, achievement diary items, combat achievement items, skill capes, quest related items, and other items.
- Manual `Unlocked: ...` and `Use: ...` transport settings are sent to the internal route engine. Baseline transport networks are sent as enabled without frontend toggles. Scanned locked minigames become blocked transport keys only while `Hide Locked Teleports` is enabled; the scanner still remembers lock state when the toggle is off.
- Route config changes mark the active route policy dirty, clear stale HUD telemetry, and replay the saved/current route target directly into the internal route engine with Drew's current config override. The internal route engine still refreshes its active path when it receives a config-only external `shortestpath/path` message, but Drew-origin refreshes no longer depend on event-bus delivery order.
- Manual right-click/shift-click targets are not just saved now: when Drew observes a changed internal route target during gameplay, it immediately replays that target through Drew's current config and locked-minigame policy. The hidden internal route config defaults `postTransports=true` so manual internal routes still publish HUD telemetry.
- Drew's HUD and minigame highlighter use the same availability rule: while `Hide Locked Teleports` is enabled, locked minigame transports are hidden from the main route step list and the highlighter follows the first available minigame step. When the toggle is off, cached locked minigames are treated as usable/highlightable.
- Compared against the OSRS wiki Transportation page on 2026-08-06: outstanding categories needing a Myth decision are wilderness obelisks, POH fairy ring, POH spirit tree, POH wilderness obelisk, and exact subtype filtering for teleport items/tablets/scrolls/capes.
- Drew's `PluginMessage` subscriber runs at high priority and merges active locked-teleport policy into incoming external `shortestpath/path` requests before the internal route engine consumes them, including config-only path refreshes with no target. Drew-owned refreshes bypass that bus and call the internal engine directly.
- The old broad stock-jar fallback (`useTeleportationMinigames=false` after exact reroute fails) is no longer part of the normal route loop. Exact keys are the expected behavior.
- If Drew has not captured a real external `shortestpath/path` target, it should prefer the internal engine's active route target before falling back to saved state. Drew must not treat a transport destination from `shortestpath/transports` as the final route target.
- A source patch for Shortest Path `1.20.6` / `Skretzo/shortest-path@9953d52745f711a38c9cdd4a00bb1d0d57d1fdea` is staged at `docs/patches/shortest-path-blocked-transport-keys.patch`.
- A custom Shortest Path fork was previously built from `Skretzo/shortest-path@8551e6016d053aa5930bb16485069a6997718da3`; that source has now been vendored into `Drews Helper` as Drew's Shortest Path.
- The current-head patch for the installed fork is staged at `docs/patches/shortest-path-blocked-transport-keys-current.patch`.
- The old active `shortest-path_*.jar` was moved out of `C:\Users\drews\.runelite\plugins` to `C:\Users\drews\.runelite\plugins-c2-backups\shortest-path_j65TV2lGDTkVcJlwg4jIvqU_Z2mHP1lUWx9t9lfkfRY.removed-for-drewpath-20260806-165054.jar`.

## Current Overlay Layout

Expected route overlay shape:

```text
Drew's Helper
Current Route Step       1/3
1. Pest Control Minigame Teleport
2. Spirit tree -> Tree Gnome Village
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

## Drew's Shortest Path Runtime

Active source:
- Project: `C:\Users\drews\OneDrive\Documents\My Games\RuneScape\Drews Helper`
- Internal route engine: `src/main/java/shortestpath/**`
- Vendored resources: `src/main/resources/**`
- Dev launcher: `src/test/java/com/drewshelper/DrewsHelperPluginTest.java`

Plugin identity:
- Visible RuneLite plugin name: `Drew's Helper`
- Internal route-engine config: hidden runtime defaults through `DrewShortestPathInternalConfig`; player-facing settings live in `DrewsHelperConfig`
- Compatibility message namespace: `shortestpath`

There should be no active Plugin Hub Shortest Path jar in:

```text
C:\Users\drews\.runelite\plugins
```

Drew's Shortest Path consumes:
- `start`
- `target`
- `config` overrides
- `config.blockedTransportKeys`

Expected exact key shape: `teleportation_minigames:nightmare_zone`.

## Known Limitations

Drew's Shortest Path is integrated and build-verified, but the in-game route behavior still needs live testing after the 2026-08-07 policy-refresh patch. Expected behavior: when `Nightmare Zone Minigame Teleport` is scanned as locked and `Hide Locked Teleports` is enabled, the route engine should exclude only `teleportation_minigames:nightmare_zone` and still allow other valid minigame teleports. Turning `Hide Locked Teleports` off should keep the scan cache, stop sending blocked minigame keys, and highlight/use Nightmare Zone again if the solver prefers it.

If every minigame teleport disappears from the route, treat that as evidence that an old fallback path or stale Plugin Hub plugin is active. The normal Drew-owned route loop is exact-key only now.
