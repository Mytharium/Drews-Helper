# Runemoro Shortest Path Deep Dive

Last updated: 2026-08-07.

Reference repository: https://github.com/Runemoro/shortest-path

Analyzed upstream commit: `655f5a24cd1a08984d824fb0692fa29b3b7185f8`

Purpose: record exactly how Runemoro's `shortest-path` works so Drew's Helper can build a clean variant later from the proven architecture without reviving the failed vendored integration.

## Executive Summary

Runemoro's plugin is small and works because route ownership is simple. One plugin class owns target state, collision data, transport data, path calculation, marker state, and overlays. The overlays do not own route logic. They render `plugin.path`, `plugin.pathfinder.currentBest()`, `plugin.transports`, and config flags.

The pathfinder is a breadth-first search over OSRS world tiles. It treats cardinal moves, diagonal moves, ladders, doors, caves, portals, and other transport edges as equal-cost graph edges. That is important: a teleport-like jump has the same pathfinding cost as walking one tile. The result is "fewest graph edges", not true travel time, click count, unlock-aware routing, or best practical player route.

The collision map is precomputed offline and loaded from `collision-map.zip`. The transport graph is loaded from `transports.txt`. Runtime only parses the first six integers from each transport line: source x/y/plane and destination x/y/plane. All labels, object IDs, requirements, comments, and warning notes in `transports.txt` are ignored by the code.

The useful upstream ideas for our variant:

- Keep a single authoritative route owner.
- Load collision and transport resources once.
- Route from current player location to selected world target.
- Render map/minimap/scene overlays from the active route result.
- Expose current-best partial route while a long search is running.
- Treat transports as graph edges, not special-case UI patches.

The parts we should not copy unchanged:

- No structured transport metadata at runtime.
- No account-unlock, quest, item, cooldown, spellbook, or membership policy model.
- No weighted routing.
- No route-step/explanation model.
- No safe worker-thread contract.
- No current-master tests.
- No public route API for another plugin/HUD.
- A shutdown bug adds `PathMapOverlay` instead of removing it.

## Source Inventory

Current master has 10 Java files, 3 resources, and basic Gradle metadata.

```text
src/main/java/shortestpath/ShortestPathPlugin.java
src/main/java/shortestpath/ShortestPathConfig.java
src/main/java/shortestpath/PathMapOverlay.java
src/main/java/shortestpath/PathMinimapOverlay.java
src/main/java/shortestpath/PathTileOverlay.java
src/main/java/shortestpath/Util.java
src/main/java/shortestpath/pathfinder/Pathfinder.java
src/main/java/shortestpath/pathfinder/CollisionMap.java
src/main/java/shortestpath/pathfinder/FlagMap.java
src/main/java/shortestpath/pathfinder/SplitFlagMap.java
src/main/resources/collision-map.zip
src/main/resources/transports.txt
src/main/resources/marker.png
```

Line-count scale at analyzed commit:

```text
ShortestPathPlugin.java        281 lines
PathTileOverlay.java           149 lines
PathMapOverlay.java            116 lines
PathMinimapOverlay.java         85 lines
SplitFlagMap.java               80 lines
FlagMap.java                    70 lines
ShortestPathConfig.java         56 lines
CollisionMap.java               41 lines
Pathfinder.java                130 lines
Util.java                       29 lines
```

Build metadata:

- `runelite-plugin.properties` loads `shortestpath.ShortestPathPlugin`.
- Gradle project name is `shortest-path`.
- Build targets Java 8.
- The pinned RuneLite dependency is `1.7.5`.
- There are no current-master test source files.

License:

- BSD 2-Clause license file is present.
- If we copy code/resources instead of writing a clean-room variant, retain license/disclaimer text in source and binary documentation.

## Runtime Ownership Model

`ShortestPathPlugin` owns all live mutable route state:

```text
CollisionMap map
Map<WorldPoint, List<WorldPoint>> transports
List<WorldPoint> path
WorldPoint target
Pathfinder pathfinder
boolean pathUpdateScheduled
WorldMapPoint marker
Point lastMenuOpenedPoint
WorldPoint transportStart
MenuOptionClicked lastClick
```

This is the central contract:

```text
menu/world-map input
  -> ShortestPathPlugin.setTarget(...)
  -> target + marker + pathUpdateScheduled
  -> worker thread creates Pathfinder
  -> Pathfinder.find()
  -> plugin.path
  -> overlays render plugin.path or currentBest()
```

There is no separate route snapshot layer. There is no event message bridge. There is no HUD route list. There is no "Drew policy sees a route and replays it" loop. That simplicity is why the upstream visual path is hard to split accidentally.

## Startup Flow

`ShortestPathPlugin.startUp()` does four jobs.

1. Load collision map resource.

It opens `/collision-map.zip` as a `ZipInputStream`. Each zip entry name is split on `_`; the two parts become a `SplitFlagMap.Position(regionX, regionY)`. The entry bytes are stored in `compressedRegions`.

```text
zip entry name: "50_53"
Position.x: 50
Position.y: 53
entry payload: gzip-compressed FlagMap bytes
```

Then it constructs:

```text
map = new CollisionMap(64, compressedRegions)
```

`64` means each lazy-loaded region covers a 64x64 tile square.

2. Load transport graph.

It reads `/transports.txt` as UTF-8, scans line by line, skips blank lines and lines beginning with `#`, then splits each remaining line on a single space.

Only the first six tokens are used:

```text
l[0] source x
l[1] source y
l[2] source plane
l[3] destination x
l[4] destination y
l[5] destination plane
```

The parsed edge is stored as:

```text
transports.computeIfAbsent(source, k -> new ArrayList<>()).add(destination)
```

Everything after the first six tokens is ignored at runtime. A line like:

```text
3070 3260 0 3064 3260 0 Climb-into Underwall tunnel 19036 "42 Agility, Diary"
```

becomes only:

```text
WorldPoint(3070, 3260, 0) -> WorldPoint(3064, 3260, 0)
```

The action label, object ID, agility level, diary note, warning text, and quoted notes are not parsed into any model.

3. Start worker loop.

Startup launches a raw `new Thread(...)`. That thread loops while `running` is true, sleeps 10 ms between checks, and reacts to `pathUpdateScheduled`.

Worker behavior:

```text
if pathUpdateScheduled:
  if target == null:
    path = null
  else:
    pathfinder = new Pathfinder(
      map,
      transports,
      client.getLocalPlayer().getWorldLocation(),
      target,
      config.avoidWilderness() && !isInWilderness(target)
    )
    path = pathfinder.find()
    pathUpdateScheduled = false
```

Notable detail: if `target == null`, the worker clears `path` but does not clear `pathUpdateScheduled` in that branch. That means after clearing the target, the thread can keep re-entering the null-target path until another set happens or shutdown ends the loop. It is not catastrophic, but it is wasteful.

4. Register overlays.

Startup adds:

```text
PathTileOverlay
PathMinimapOverlay
PathMapOverlay
```

to `OverlayManager`.

## Shutdown Flow

`shutDown()` sets:

```text
map = null
running = false
```

Then it removes `PathTileOverlay` and `PathMinimapOverlay`.

There is a bug: it calls `overlayManager.add(pathMapOverlay)` instead of `overlayManager.remove(pathMapOverlay)`.

For our variant, do not copy this. Shutdown must remove all overlays and stop/cancel worker state cleanly.

## Menu And Target Flow

`onMenuOpened(MenuOpened event)` records the current mouse canvas point. This is used because RuneLite menu clicks can move the live mouse position after the menu opens.

`onMenuEntryAdded(MenuEntryAdded event)` injects custom RuneLite menu entries:

- If `drawTransports()` is enabled, add debug entries `Start` and `End`.
- If the world map widget exists and the mouse is inside it, add `Set Target` and `Clear Target`.

`addMenuEntry(...)` prepends a `RUNELITE` menu entry unless another entry already has the same option text.

`onMenuOptionClicked(MenuOptionClicked event)` handles five options:

- `Start`: record current player location as `transportStart`.
- `End`: record current player location as destination, print a transport line to stdout, and add the edge to in-memory `transports`.
- `Copy Position`: copy current player world position to clipboard. The menu entry is commented out, so this path is inactive unless re-enabled.
- `Set Target`: calculate a world map point from the menu/mouse location, then call `setTarget(...)`.
- `Clear Target`: call `setTarget(null)`.

At the end, any non-walk click is stored as `lastClick`. The debug `End` output includes `lastClick` option/target/id so a developer can manually add object metadata to `transports.txt`.

Important limitation: `Start`/`End` are a developer transport-authoring helper, not user route behavior. They mutate only the current runtime `transports` map and print to stdout. They do not save `transports.txt`.

## World Map Coordinate Conversion

`calculateMapPoint(Point point)` converts a canvas point to a world point.

Process:

1. Read world map zoom from `client.getRenderOverview().getWorldMapZoom()`.
2. Read current world map center from `renderOverview.getWorldMapPosition()`.
3. Convert that center world point to graphics point through `WorldMapOverlay.mapWorldPointToGraphicsPoint(...)`.
4. Compute graphics delta from center, divide by zoom, invert Y.
5. Return `mapPoint.dx(dx).dy(dy)`.

The returned target always uses plane 0 because `mapPoint` is created with plane 0. That is acceptable for the basic surface world-map use case but not a complete model for dungeon/sublevel target selection.

## Game Tick Route Maintenance

`onGameTick(GameTick tick)` handles active-route maintenance if `path != null`.

1. Recalculate/cancel when the player is no longer near the path.

`isNearPath()` scans all path tiles and returns true if local player distance to any path point is less than `recalculateDistance()`.

If false:

- If `cancelInstead()` is true, set `target = null`.
- Set `pathUpdateScheduled = true`.

2. Clear/recalculate when the player reaches target.

If local player distance to `target` is less than `reachedDistance()`, set `target = null` and schedule an update.

`reachedDistance()` default is 5. Config range allows `-1`; with the current condition, a distance can never be less than `-1`, so `-1` effectively means never finish.

## Pathfinder Algorithm

`Pathfinder` is breadth-first search.

Constructor inputs:

```text
CollisionMap map
Map<WorldPoint, List<WorldPoint>> transports
WorldPoint start
WorldPoint target
boolean avoidWilderness
```

Internal state:

```text
Node start
List<Node> boundary = new LinkedList<>()
Set<WorldPoint> visited = new HashSet<>()
Node nearest
```

`find()`:

1. Add start node to `boundary`.
2. Track `bestDistance` using Chebyshev distance to target:

```text
max(abs(x - target.x), abs(y - target.y))
```

3. While boundary is not empty:

- Remove first node.
- If node position equals target, return `node.path()`.
- If this node is closer than previous nearest, update `nearest`.
- Add neighbors.

4. If target was not reached but `nearest` exists, return `nearest.path()`.
5. If no nearest exists, return null.

`currentBest()` returns `nearest.path()` while the worker is still searching. `PathMapOverlay` draws this in blue when `pathUpdateScheduled` is true.

### Neighbor Order

`addNeighbors(Node node)` considers neighbors in this order:

```text
west
east
south
north
southwest
southeast
northwest
northeast
transports from this tile, in transports.txt order
```

This order matters. BFS picks the first discovered shortest edge-count path. If multiple paths have the same number of graph edges, this order becomes a route-preference bias.

### Movement Cost

Every edge has cost 1:

- One west/east/north/south tile = 1.
- One diagonal tile = 1.
- One ladder/door/cave/portal/transport jump = 1.

This makes the solver effectively optimize graph-edge count, not travel time. Because diagonal movement costs 1, open-ground distance is closer to Chebyshev distance than to Manhattan distance. Because all transports cost 1, a long portal can dominate the result even if it has practical requirements or UI friction.

### Wilderness Avoidance

`ShortestPathPlugin.isInWilderness(...)` checks two rectangular areas:

```text
above ground: x 2944..3391, y 3523..3970, plane 0
underground:  x 2944..3263, y 9918..10359, plane 0
```

The worker passes `avoidWilderness = config.avoidWilderness() && !isInWilderness(target)`.

So if the target is inside wilderness, the solver allows wilderness. If the target is outside wilderness and config says avoid, `addNeighbor(...)` rejects any neighbor inside those rectangles.

This is a blunt area filter. It does not model player risk preference, wilderness level, shortcut necessity, or "already inside wilderness and needs to walk out" nuance.

### Visited Handling

`visited` starts empty. The start tile is not added to `visited` before the loop. That means a neighbor can add the start tile later, causing one unnecessary revisit. It is not usually a correctness bug, but our variant should mark the start visited at initialization.

## Collision Data Model

`CollisionMap` extends `SplitFlagMap` with `flagCount = 2`.

The two flags are directional passability bits:

- flag 0: can move north from this tile.
- flag 1: can move east from this tile.

South and west are derived from adjacent tiles:

```text
s(x, y, z) = n(x, y - 1, z)
w(x, y, z) = e(x - 1, y, z)
```

Diagonal movement requires all relevant cardinal movement checks:

```text
ne = n(x,y) && e(x,y+1) && e(x,y) && n(x+1,y)
nw = n(x,y) && w(x,y+1) && w(x,y) && n(x-1,y)
se = s(x,y) && e(x,y-1) && e(x,y) && s(x+1,y)
sw = s(x,y) && w(x,y-1) && w(x,y) && s(x-1,y)
```

That prevents cutting through blocked corners.

`FlagMap` supports four planes:

```text
PLANE_COUNT = 4
```

Out-of-bounds lookup returns false, which means blocked.

### SplitFlagMap Lazy Region Loading

`SplitFlagMap` lazy-loads 64x64 regions through a Guava `LoadingCache`.

Cache weight limit:

```text
20 * 1024 * 1024
```

On lookup:

```text
region position = (x / regionSize, y / regionSize)
regionMaps.get(position).get(x, y, z, flag)
```

If a compressed region exists, it is GZIP-decoded and passed to `new FlagMap(bytes, flagCount)`.

If no compressed region exists, it creates an empty `FlagMap` for that region. Empty means all flags false, so movement is blocked.

This matters for our variant: missing collision data does not mean open world; it means no movement.

### FlagMap Byte Format

`FlagMap(byte[] bytes, int flagCount)` reads:

```text
int minX
int minY
int maxX
int maxY
BitSet flags from the remaining bytes
```

Java `ByteBuffer` defaults to big-endian, so the four bounds ints are big-endian.

For a normal 64x64 region:

```text
64 * 64 tiles * 4 planes * 2 flags = 32768 bits = 4096 bytes of real flag data
```

But the stored decompressed region blobs in current `collision-map.zip` are 32,784 bytes:

```text
16-byte bounds header + 32,768 bytes bitset payload
```

That is 8x larger than the minimum 4,096 byte flag payload. The reason is visible in `FlagMap.toBytes()`: it allocates `16 + flags.size()` bytes, and `BitSet.size()` returns a bit capacity, not byte count. It then writes `flags.toByteArray()` into that oversized array. The extra zeros do not break reads, but a future variant/generator should store compact bitset bytes.

## Resource Facts

At analyzed commit:

- `collision-map.zip`: 938,345 bytes.
- Zip entries: 1,524 region files.
- Example entry names: `18_54`, `50_53`, `60_156`.
- Entry payloads are GZIP-compressed `FlagMap` blobs.
- Typical decompressed 64x64 region size: 32,784 bytes.

`transports.txt`:

- 3,886 total lines.
- 102 section header comments.
- 3,681 transport edge lines with valid first-six coordinate tokens.
- 3,654 unique coordinate edges.
- 2,428 unique edges have an exact reverse edge.
- 1,984 edges change plane.
- 707 edges are same x/y but change plane.
- 878 edges jump more than 100 total x/y tiles.

Largest sections by line count include:

- Tree Gnome Stronghold: 283.
- Slayer Cave: 195.
- Meyerditch: 150.
- Varrock: 134.
- East Ardougne: 116.
- Haunted Mine: 106.
- Tarn's Lair: 106.
- Falador: 96.
- Enakhra's Lament: 96.
- Keldagrim: 84.

Do not treat exact reverse count as a quality guarantee. Many real transports are intentionally one-way, and some bidirectional facilities are represented by multiple adjacent tiles rather than exact one-to-one reverse edges.

## Overlay Contracts

### PathMapOverlay

Layer setup:

```text
position: DYNAMIC
priority: LOW
layer: ABOVE_WIDGETS
```

Render guards:

- If `drawMap()` is false, return.
- If `WORLD_MAP_VIEW` widget is missing, return.

If `drawTransports()` is true, draw every transport edge as a line on the world map when both endpoints can be converted to graphics points.

Then it builds a clip area from the world map widget bounds and subtracts the overview map and surface selector widgets if visible.

Path drawing:

- If `plugin.path != null` and `!plugin.pathUpdateScheduled`, draw complete path in red.
- Else if `plugin.pathUpdateScheduled` and `plugin.pathfinder != null`, draw `plugin.pathfinder.currentBest()` in blue if available.

Each world tile is drawn as a filled rectangle between the graphics position of the tile and `point.dx(1).dy(-1)`.

### PathMinimapOverlay

Layer setup:

```text
position: DYNAMIC
priority: LOW
layer: ABOVE_WIDGETS
```

Render guards:

- If `drawMinimap()` is false, return.
- If `plugin.path == null`, return.

For each path point:

- Skip if point plane differs from client plane.
- Skip if distance to local player is 50 or more.
- Convert `WorldPoint` to `LocalPoint`.
- Convert `LocalPoint` to minimap point.
- Draw a 4x4 red rectangle rotated with current minimap angle.

### PathTileOverlay

Layer setup:

```text
position: DYNAMIC
priority: LOW
layer: ABOVE_SCENE
```

Debug transport drawing when `drawTransports()` is true:

- Draw each transport source tile green.
- Draw a line from source tile center to each destination tile center when both are on current plane and visible.
- Draw a small text string at source: `+`, `-`, or `=` per destination depending on destination plane relative to source.

Collision debug when `drawCollisionMap()` is true:

- Iterate visible scene tiles on current plane.
- Build a string of blocked cardinal directions from `plugin.map`.
- If partially blocked, draw direction letters centered in tile.
- If fully blocked, fill tile blue-ish.

Path drawing:

- If `drawTiles()` is true and `plugin.path != null`, fill each same-plane visible path tile red.

## Config Surface

Config group:

```text
@ConfigGroup("shortestPath")
```

Items:

- `drawTiles`, default true.
- `drawMinimap`, default true.
- `drawMap`, default true.
- `drawCollisionMap`, default false.
- `drawTransports`, default false.
- `recalculateDistance`, range 1..1000, default 10.
- `cancelInstead`, default false.
- `finishDistance`, range -1..50, default 5.
- `avoidWilderness`, default true.

For Drew's variant, these are not a sufficient player config model. They are renderer/debug/maintenance settings. Drew policy needs its own typed route policy: unlocks, blocked exact transports, route quality, avoid categories, UI preferences, and diagnostic level.

## Transport Authoring Helper

The `drawTransports` setting also activates developer menu entries:

```text
Start
End
```

Workflow:

1. Stand at transport source tile.
2. Choose `Start`.
3. Use the transport.
4. Choose `End`.
5. Plugin prints a transport line to stdout:

```text
sourceX sourceY sourcePlane destX destY destPlane lastClickOption lastClickTarget lastClickId
```

It also adds the edge to the live `transports` map, so the current session can route through it.

This is useful, but primitive. For our variant, make this a real opt-in developer tool that writes structured candidate data to a review file, not stdout only. Include source/dest, object/action/menu option, object ID, varbit/quest requirements if known, region/section, one-way/bidirectional intent, and a stable route key.

## Important Runtime Weaknesses

These are facts from the source, not complaints about the concept.

1. `shutDown()` has an overlay typo.

It removes tile and minimap overlays, then adds the map overlay instead of removing it.

2. Threading is unsafe by modern RuneLite standards.

The worker thread calls `client.getLocalPlayer().getWorldLocation()` outside the client thread. It also reads config and writes shared fields (`path`, `pathfinder`, `pathUpdateScheduled`) with no `volatile`, lock, immutable snapshot, version token, future cancellation, or client-thread publish step.

3. No cancellation model.

Once `pathfinder.find()` starts, it runs until done. A new target/config change only waits behind the existing search. The map overlay may draw `currentBest()` while the worker is still searching, but there is no clean cancel/restart semantics.

4. Route result has no status.

`find()` returns a full target path, nearest partial path, or null. The caller does not get an explicit `EXACT`, `PARTIAL`, `NO_PATH`, `CANCELLED`, or `STALE` status. That makes UI explanations weak.

5. Transports have no runtime metadata.

The solver cannot explain "use ladder", "click portal", "requires 42 agility", "has warning", or "blocked by quest state" because it discards all non-coordinate tokens.

6. All graph edges cost 1.

This makes teleports and long cave jumps extremely cheap compared with walking. Good for "shortest edge count", bad for practical travel scoring.

7. No route-step model.

The final route is only a `List<WorldPoint>`. To infer transports later, another layer would need to detect adjacent path points that are not tile-neighbors and match them back to the transport map. Since metadata is discarded, even that match only gives coordinates.

8. No account state.

The plugin cannot know spellbooks, diaries, quests, agility level, items, POH state, minigame unlocks, cooldowns, wilderness risk, or membership.

9. World-map target plane is always 0.

The world-map right-click flow does not encode underground plane/submap target selection.

10. Debug menu entries are global-ish.

When `drawTransports` is on, `Start` and `End` are added during menu-entry events. That is acceptable for a developer but not polished user behavior.

11. No current-master tests.

There was an older dev harness in history, but current master has no tests for parser, collision data, pathfinder, overlays, or route examples.

12. Resource generation is not part of current master.

The repo ships precomputed `collision-map.zip` and `transports.txt`, but not a full current pipeline for regenerating collision data from RuneLite cache/game data.

## What To Copy Conceptually

Use these as design principles:

- Single authoritative route owner.
- Immutable route inputs: start, target, config/policy, resource revision.
- Immutable route outputs: route status, tile path, step list, used transports, rejected transports.
- Collision provider separated from solver.
- Transport registry separated from solver.
- Overlays render route outputs only.
- UI/config creates route policy only.
- Worker completion publishes only if version token still matches current request.
- Current-best route is optional telemetry from the active solver, not a second result owner.

## What Not To Recreate

Do not revive these old Drew failure patterns:

- Drew notices a completed route and replays it afterward.
- HUD owns one route state while map/tile overlays own another.
- Locked-route filtering happens only in display code.
- Transport telemetry is treated as final route target.
- Config toggles post competing route messages back into the same bus.
- Stale pathfinder completion can publish after a newer request starts.
- UI/highlighter relies on an event message that overlays do not use.

## Clean Drew Variant Architecture

Recommended package shape:

```text
com.drewshelper.routing
  RouteEngine
  RouteRequest
  RoutePolicy
  RouteResult
  RouteStatus
  RouteStep
  RouteSnapshot

com.drewshelper.routing.graph
  CollisionProvider
  CollisionMapStore
  TransportRegistry
  TransportEdge
  TransportKey
  TransportRequirement
  TransportCategory

com.drewshelper.routing.solver
  Pathfinder
  SearchNode
  RouteCost
  RouteHeuristic

com.drewshelper.routing.ui
  RouteOverlayModel
  RouteMapOverlay
  RouteMinimapOverlay
  RouteTileOverlay

com.drewshelper.routing.debug
  RouteDiagnostics
```

### RouteRequest

Owns one calculation request:

```text
requestId/version
WorldPoint start
WorldPoint target
RoutePolicy policy
long createdAtTickOrMillis
```

Capture start/target/config on the RuneLite client thread. Pass only immutable values to the worker.

### RoutePolicy

Drew-owned policy object, not raw config map:

```text
boolean drawMap
boolean drawMinimap
boolean drawTiles
boolean avoidWilderness
Set<TransportKey> blockedTransportKeys
Set<TransportCategory> disabledCategories
PlayerUnlockState unlocks
RouteQualityMode mode
```

The config UI can build this object. The solver consumes this object. No plugin-message map should be the internal API.

### TransportEdge

Runtime must preserve metadata:

```text
TransportKey key
WorldPoint source
WorldPoint destination
String section
String action
String targetName
int objectId
TransportCategory category
List<TransportRequirement> requirements
boolean oneWay
RouteCost cost
String displayName
```

Stable key example:

```text
teleportation_minigames:nightmare_zone
object:climb_up_ladder:16683:3228_3213_0
ship:port_sarim_to_karamja
```

Do not rely on display names as keys.

### RouteResult

The solver should return:

```text
RouteStatus status
List<WorldPoint> tiles
List<RouteStep> steps
List<TransportEdge> usedTransports
List<RejectedTransport> rejectedTransports
WorldPoint start
WorldPoint target
WorldPoint reachedPoint
int visitedCount
Duration solveTime
String debugSummary
```

Statuses:

```text
EXACT
PARTIAL_NEAREST
NO_PATH
CANCELLED
STALE
ERROR
```

### Solver

Start with Dijkstra or A* instead of plain BFS.

Reasons:

- Walking tile cost should differ from teleport/boat/ladder/click-chain cost.
- Diagonal cost can be modeled deliberately.
- Route preference can penalize wilderness, warning screens, long UI interactions, quest-locked options, or unknown availability.
- A* with admissible lower-bound heuristic can speed target-directed search.

Safe starting point:

```text
tile cardinal cost: 10
tile diagonal cost: 14
simple door/ladder/stair cost: 10-20
teleport/long transport cost: category-specific
blocked transport: excluded, not high cost
unknown requirement: configurable penalty or exclusion
```

Keep exact policy exclusions separate from soft penalties.

### Worker Contract

Use a single executor and version tokens.

```text
on route request:
  requestId++
  capture immutable RouteRequest on client thread
  cancel previous Future if running
  publish state CALCULATING
  submit solver worker

worker done:
  produce immutable RouteResult
  clientThread.invokeLater:
    if requestId still current:
      activeResult = result
      overlays/HUD render it
    else:
      discard stale result
```

Never let old worker results publish to UI state after a newer request exists.

### Overlay Contract

Overlays should read one immutable `RouteSnapshot`:

```text
RouteSnapshot activeSnapshot
```

Map/minimap/tile/HUD/highlighter should all derive from that same snapshot. If a future feature needs a specialized view, derive it from the snapshot, not from a separate route parser.

### Diagnostics Contract

Diagnostics should be built from day one:

```text
route.request
route.policy
route.worker.start
route.worker.progress
route.worker.done
route.worker.discardStale
route.result.publish
overlay.map.skip/draw
overlay.tile.skip/draw
hud.snapshot
transport.reject
```

Keep logs opt-in and de-duped. Include requestId, start, target, policy hash, status, path length, used transport count, first used transport, and rejection counts.

## Development Phases For Drew Variant

### Phase 0: Reference Lock

- Keep Drew Helper UI-only runtime intact.
- Keep this upstream deep-dive as the reference note.
- Do not restore the deleted vendored route code.

### Phase 1: Data Model And Parser

- Add `TransportEdge`, `TransportKey`, `TransportCategory`, and parser tests.
- Parse a structured transport file that keeps labels, object IDs, requirements, and notes.
- Convert existing upstream `transports.txt` into structured records only if license handling is accepted.
- Add parser tests for quoted metadata, one-way edges, reverse edges, and stable keys.

### Phase 2: Collision Provider And Solver Tests

- Add a tiny synthetic collision map for tests.
- Build pathfinder against synthetic maps first.
- Test cardinal, diagonal, blocked corner, plane change, transport edge, unreachable target, and partial-nearest behavior.
- Decide BFS vs Dijkstra/A* with route-cost model before integrating RuneLite UI.

### Phase 3: RuneLite Route Owner

- Add one internal `RouteEngine` owned by Drew Helper.
- Add world-map target and clear target.
- Add map/minimap/tile overlays that render a single `RouteSnapshot`.
- Do not add HUD/highlighter yet.

### Phase 4: Drew Policy Integration

- Wire config buttons/dropdowns into `RoutePolicy`.
- Add blocked exact transport keys.
- Add visible route explanation in Drew overlay.
- Add rejected transport reporting.

### Phase 5: Teleport/Minigame Intelligence

- Rebuild minigame scanner only after the solver and snapshot contract are stable.
- Scanner writes availability state.
- Route policy consumes availability state.
- HUD/highlighter derive from route snapshot and availability state, not from separate route telemetry.

## Quick Reference Trace

Entrypoints in upstream:

```text
ShortestPathPlugin.startUp()
ShortestPathPlugin.onMenuOpened()
ShortestPathPlugin.onMenuEntryAdded()
ShortestPathPlugin.onMenuOptionClicked()
ShortestPathPlugin.setTarget()
ShortestPathPlugin.onGameTick()
Pathfinder.find()
Pathfinder.addNeighbors()
CollisionMap.n/s/e/w/ne/nw/se/sw()
PathMapOverlay.render()
PathMinimapOverlay.render()
PathTileOverlay.render()
```

Data files:

```text
collision-map.zip -> lazy collision flags by 64x64 region
transports.txt -> source/destination worldpoint edges, metadata ignored by upstream
marker.png -> world map target marker icon
```

Core flow to remember:

```text
target changes
  -> pathUpdateScheduled = true
  -> worker builds Pathfinder from current player location
  -> BFS searches collision + transport graph
  -> plugin.path is assigned
  -> overlays draw plugin.path
```

This is the upstream shape we should keep: one route result, many views.

