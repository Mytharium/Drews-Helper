# Next Work

Last updated: 2026-08-16.

## CURRENT HANDOFF - START HERE (written 2026-08-16, session close after C1 proof)

This is the only active start-here block. Older handoffs below are retained for evidence and
design context; use them only when this section points back to a parked item.

**WHAT'S NEXT:** C1 is done. Start the paid/unnamed object-profile proof batch using the exact
coordinate anchors below. The goal is evidence only: capture `DREW_OBJECT_STATE` passive
object-profile rows and paired `DREW_ROUTE_SEGMENT` rows for the held-back keys `1289/10`,
`9661/10`, `7169/10`, `34803/10`, `34804/10`, and unnamed `19143/10`. Do not promote those keys to
runtime map data until their proof rows are inspected and the route gate stays clean. Active sailing
rows remain parked until land-side gangplank/dock interaction tiles are verified. Do not invent
sailing dock tiles from map pins or port-task navigation waypoints.

State through 2026-08-16 07:26 UTC:

1. Falador route-window work is live-verified for primary, reverse, and east-pressure pins.
2. Batch A proved the issue is broader than Falador and moved diagnosis to segment evidence.
3. D-0184/D-0185 shipped passive segment logging with `completed=true|false` so re-click cadence is
   not mistaken for object/ranker proof.
4. Batch C and D-0186 proved supported table/tree/dead-tree object-profile candidates.
5. D-0187/D-0188 promoted only the supported map after the gate and Myth's Falador/C1/C2/C3 live
   reruns; promoted `src/main/resources/collision-map.zip` is SHA256
   `8BE900A1FFD4A6F19E5C47FCEF8F3D13FE4BB24C47272A35E7EC8B965BCD27C3`.
6. D-0189 added confidence/provenance metadata: collision map default `INFERRED`, Skretzo
   transport rows `INHERITED`, and 24 override rows `CONFIRMED`.
7. D-0191 added `Settings` -> `Log Object/Door State`, default OFF. When enabled, Drew scans the
   loaded scene every 25 ticks and writes `DREW_OBJECT_STATE v1` evidence rows to
   `%USERPROFILE%\.runelite\drews-object-states.txt`. Rows keep base id, active impostor id,
   actions, varbit/varp hooks, live collision flags, and collision-map confidence/provenance.
8. D-0192 added `gradlew validateRoutes`. The harness runs deterministic offline route
   structural validations, reads `%USERPROFILE%\.runelite\drews-route-segments.txt`, reads
   `%USERPROFILE%\.runelite\drews-object-states.txt`, correlates segment divergence with nearby
   object/door rows, and writes `tools/route-validation-harness.txt`. It is evidence-only and
   does not promote collision, transport, or object-profile rows.
9. D-0193 added `gradlew pilotRegionCleanup`. The pilot report filters current evidence to
   `rx45-48 / ry49-52`, writes `tools/pilot-region-cleanup.txt`, and treats interrupted or
   non-adjacent `legal=false` rows as recapture targets rather than hard promotion gates.
10. D-0194 consumed Myth's focused recapture near `(3092,3245,0) -> (3131,3252,0)`. The clean row
   was `completed=true`, `legal=true`, and `classification=legal-detour-or-object-pressure`; the
   stale interrupted/non-adjacent illegal row is now reported as
   `supersededNonPromotableIllegalEdges=1`, with `completedAdjacentIllegalEdges=0` and
   `verdict=NO_COMPLETED_STATIC_DISAGREEMENT`.
11. D-0195 added locked-route requirement messaging. When the filtered route cannot reach a
   waypoint, the solver does a same-policy unrestricted diagnostic solve; if the only viable route
   uses capability-locked transport/shortcut edges, the overlay shows a separate `Requirements`
   block below the waypoint/action display. `SAILING` is now a supported transport category and
   `Skill.SAILING` invalidates cached routes, but no active sailing transport rows were added
   without verified dock interaction tiles.
12. D-0196 updated the object/door-state recorder so gangplanks, ships, boats, docks, moorings,
   piers, quays, and direct sailing verbs are captured as `category=sailing state=SAILING_ACCESS`.
   Generic `Travel` rows without boat/dock naming remain ordinary traversal evidence.
13. D-0197 expanded that same evidence stream to focused passive object-profile blockers. It decodes
   `TileObject.getConfig()` to `locType`, writes `locType=<n>` in every object-state row, and records
   supported/held-back table/tree/dead-tree/rubble/etc. proof keys as
   `category=object-profile state=PASSIVE_OBJECT_PROFILE`. This is evidence-only and does not
   promote any object-profile key.
14. Myth reran C1 after D-0197. The recorder captured the Lumbridge table exactly:
   `tile=3209,3221,0 objectId=596 locType=10 category=object-profile
   state=PASSIVE_OBJECT_PROFILE objectSize=1x4 rawFlags=256`. Fresh C1 segment rows also completed,
   including `match`, `legal-detour-or-object-pressure`, and
   `legal-route-ranker-or-click-shape` classifications with `illegalObservedEdges=0`.
15. Current evidence harness after the C1 rerun completed with
   `rows=83 completed=47 interrupted=36 matches=10 divergent=73 illegalObservedEdges=0` and object
   evidence `rows=1747`, including `object-profile=703` and
   `PASSIVE_OBJECT_PROFILE=703`.
16. `tools/route-validation-harness.txt` is dirty evidence output from the harness run. It is not a
   runtime behavior change and should not be bundled into an unrelated code commit.

Next paid/unnamed proof batch:

Settings for both blocks:

```text
In-game run: OFF
Log Benchmark Movement: OFF
Log Route Segments: ON
Log Object/Door State: ON
Validate Map Data: OFF
```

Pause about 5 seconds at each coordinate so the 25-tick object-state scan emits rows. Send both
`%USERPROFILE%\.runelite\drews-object-states.txt` and
`%USERPROFILE%\.runelite\drews-route-segments.txt` after the capture.

```text
P1 - Draynor/Manor held-back dead-tree proof
Expected held-back key: 1289/10
Waypoint target: 3109,3352,0
Walk/pause anchors:
3098,3318,0
3096,3323,0
3099,3331,0
3104,3333,0
3110,3341,0
3109,3352,0
```

```text
P2 - Varrock east / Sawmill held-back stump, rubble, table, unnamed proof
Expected held-back keys:
9661/10 at 3274,3445,0; 3300,3489,0; 3308,3483,0
19143/10 unnamed 2x2 at 3296,3481..3297,3482
34804/10 at 3303,3479,0 and 3305,3478,0
34803/10 at 3307,3478,0
7169/10 at 3301,3492,0

Waypoint target: 3307,3491,0
Walk/pause anchors:
3274,3445,0
3296,3481,0
3303,3479,0
3307,3478,0
3308,3483,0
3300,3489,0
3301,3492,0
3307,3491,0
```

Object/door-state recorder run procedure:

1. Leave `Log Object/Door State` OFF during ordinary play unless C2 asks for a capture.
2. For a capture, enable `Settings` -> `Log Object/Door State`.
3. Leave `Validate Map Data` OFF unless C2 also asks for `DREW_TRAVERSAL` or full live-flag rows;
   the object-state recorder captures its own nearby live edge mask.
4. Stand in or walk through the loaded scene that contains the door, gate, barrier, shortcut,
   pulled object, state-changing obstacle, or focused passive object-profile blocker.
5. Open/close/use the object normally, wait a few seconds after each visible state, then send the
   `DREW_OBJECT_STATE` rows from `%USERPROFILE%\.runelite\drews-object-states.txt`.
   For passive table/tree/dead-tree/rubble-style blockers, no interaction is needed; walk the
   coordinate loop and pause long enough for the 25-tick scene scan to emit rows.
   For Sailing access proof, stand at the land-side dock/gangplank tile and wait long enough for
   `category=sailing state=SAILING_ACCESS` rows to land before clicking the boarding object.
6. Treat these rows as evidence only. They do not auto-merge collision, transport, or object-profile
   data and they do not change route behavior by themselves.
7. Held-back keys `1289/10`, `9661/10`, `7169/10`, `34803/10`, `34804/10`, and unnamed `19143/10`
   may appear in evidence rows after D-0197, but they stay out of promoted route data until they get
   their own paid/unnamed proof pass.
8. Keep the known accepted full-test failure visible:
   `shapeRankingShadowExposesDistinctSameLengthRandomChainRoute`.

Route-validation harness procedure:

1. Run `gradlew validateRoutes` from the Drew's Helper repo root.
2. Use `--args="--samples=1000"` to make the sample count explicit; the default is already 1000.
3. Use `--args="--skip-offline"` only when you want a quick read of live evidence files without
   running the route solves.
4. Read `tools/route-validation-harness.txt`.
5. Treat `badStructure` and completed adjacent `illegalObservedEdges` as hard gates. Treat
   `nonPromotableIllegalObservedEdges` as focused-recapture work, not promotion evidence.
6. Treat divergent hand-walked route segments plus nearby object rows as the shortlist for the next
   live test target.
7. Do not use the harness to auto-promote map, transport, or object-profile data.

Pilot-region cleanup procedure:

1. Run `gradlew pilotRegionCleanup` from the Drew's Helper repo root.
2. Read `tools/pilot-region-cleanup.txt`.
3. If a stale interrupted/non-adjacent illegal row has a later clean focused recapture with the
   same click destination from the same/near start tile, treat the stale row as superseded instead
   of blocking the pilot report forever.
4. Enable `Log Route Segments` and `Log Object/Door State` for any recapture. Leave
   `Validate Map Data` OFF unless C2 asks for raw traversal rows too.
5. If the recapture produces a completed adjacent `legal=false` row, treat it as a hard collision
   candidate. If it only produces legal route-shape/object-pressure rows, use nearby object-state
   rows to decide the next table/tree/door proof target.
6. Do not promote collision-map rows, object profiles, or route-ranker changes from an interrupted
   or non-adjacent row.

For live route-shape checks, enable `Settings` -> `Log Benchmark Movement`. D-0174 reactivated
that one-click capture switch and made its `DREW_ROUTE_BENCH` rows include the full displayed
`expectedPath=[...]` trace at route start plus the full walked `actualPath=[...]` trace on
completion, so the Falador display bug can be compared tile-for-tile instead of from a screenshot.
D-0176 fixes the recorder lifecycle so off-path route recalculation and waypoint arrival no longer
erase the original movement capture before the final `reason=target` row. D-0177/D-0178 then use
that completed row to add a narrow target-aware route-shape correction for the Falador southeast
target, without enabling broad tree blocking or promoting `shapeShadow`. D-0179 live-validated
that correction on the primary route. D-0180 extends the same evidence-scoped local route-window
approach to the reverse and east-pressure creative controls after Myth's 2026-08-14 live pass.
D-0181 live-validated those controls and moves the next work from Falador-specific patching to a
broader route-shape validation sweep.

### ROUTE-SHAPE VALIDATION BATCH A

Run procedure:

1. Keep in-game run OFF.
2. Keep `Log Benchmark Movement` OFF while walking/teleporting to each start tile.
3. Turn `Log Benchmark Movement` ON only for the measured route.
4. Walk the highlighted route normally to the end tile.
5. Send the completed `DREW_ROUTE_BENCH reason=target` rows, especially any row where `full=false`,
   `lenDelta` is non-zero, `maxDev` is non-zero, or `divergence` is not `{none}`.

Batch A routes:

```text
A1 Varrock city -> Grand Exchange
Start: 3212,3424,0
End:   3165,3484,0
Why:   long city/road route with gates, walls, and dense pathing but not the Falador tree pocket.

A2 Lumbridge -> Draynor bank
Start: 3222,3218,0
End:   3092,3245,0
Why:   long open-road route through fences/river-road geometry in the captured low-level area.

A3 Draynor bank -> Draynor Manor entrance
Start: 3092,3245,0
End:   3109,3352,0
Why:   object-heavy manor approach with trees/hedges, useful before tree profiles are revisited.

A4 Lumbridge east side -> Al Kharid bank
Start: 3224,3219,0
End:   3270,3167,0
Why:   desert-gate/road route; direction avoids making Lumbridge Home Teleport the obvious target.

A5 Falador square -> Barbarian Village
Start: 2964,3378,0
End:   3081,3421,0
Why:   long Falador-area control that leaves the fixed southeast tree pocket.

A6 Varrock east bank -> Sawmill
Start: 3253,3420,0
End:   3307,3491,0
Why:   different Varrock-side tree/road clutter, checks whether similar outdoor object pressure repeats.
```

Interpretation rule: exact matches and benign same-time permutations do not justify route changes.
If several non-Falador rows show the same legal equal-length route-shape miss, stop adding local
windows and work the route ranker. If failures are illegal/static-map disagreements or object-edge
misses, classify them for the object-profile/collision-map pass instead.

Live Batch A result from Myth, checked 2026-08-14:

```text
A1 Varrock -> GE
row: exp=73 actual=74 full=false lenDelta=1 maxDev=7 turnDelta=5
first miss: idx=1 predicted=(3211,3425,0) actual=(3211,3424,0), legal=true, delta=1
classification: route-shape/ranker evidence; not a chunk-boundary-only explanation.

A2 Lumbridge -> Draynor bank
row: exp=137 actual=155 full=false lenDelta=18 maxDev=5 turnDelta=30
first miss: idx=7 predicted=(3215,3219,0) actual=(3215,3218,0), legal=true, delta=1
classification: mixed route-shape plus object/door-state evidence; Myth saw a Lumbridge dining
table route leak and had to click doors manually.

A3 Draynor bank -> Draynor Manor
row: exp=111 actual=125 full=false lenDelta=14 maxDev=5 turnDelta=25
first miss: idx=3 predicted=(3091,3248,0) actual=(3091,3247,0), legal=true, delta=0
classification: mixed route-shape plus object-profile evidence; Myth saw dead-tree leaks near
Draynor Manor.

A4 Lumbridge east -> Al Kharid bank
row: exp=109 actual=109 full=false lenDelta=0 maxDev=2 turnDelta=3
first miss: idx=11 predicted=(3235,3219,0) actual=(3235,3220,0), legal=true, delta=0
classification: mostly benign same-time permutation, not worth patching by itself.

A5 Falador square -> Barbarian Village
row: exp=135 actual=139 full=false lenDelta=4 maxDev=2 turnDelta=13
first miss: idx=18 predicted=(2968,3396,0) actual=(2968,3395,0), legal=true, delta=1
classification: mild long-route route-shape miss; keep as evidence, do not add a window.

A6 Varrock east bank -> Sawmill
row: exp=88 actual=94 full=false lenDelta=6 maxDev=6 turnDelta=13
first miss: idx=24 predicted=(3272,3428,0) actual=(3272,3429,0), legal=true, delta=0
classification: route-shape/ranker evidence; shapeShadow was better here, but still not enough
to promote globally because A2/A3/A5 need object and segment evidence too.
```

Batch A conclusion: the long-route rows are useful, but the current one-route benchmark is too
coarse for whole-system diagnosis when Myth has to click multiple visible tiles, open doors, or
route around objects that block the mouse. Build the segment/passive recorder next, then use it to
classify solver issues as route-ranker, object-profile, door/traversal-state, or collision-map
errors before shipping tree/dead-tree/table profile changes.

### ROUTE-SEGMENT VALIDATION BATCH B

D-0184 added `Settings` -> `Log Route Segments`, default OFF. When enabled, Drew records each
clicked walking destination as its own `DREW_ROUTE_SEGMENT v1` row. D-0185 adds
`completed=true|false` and interruption-aware classifications so normal re-click cadence is not
misread as a route/object fault. Rows are written to:

```text
%USERPROFILE%\.runelite\drews-route-segments.txt
```

and mirrored to the Gradle log. Each row includes the clicked destination, the current route target,
the displayed route slice for that click, the actual tiles walked, `route={...}` summary,
`divergence={...}`, `edgeValidation={...}`, and a first-pass classification such as `match`,
`click-destination-off-route`, `legal-detour-or-object-pressure`,
`legal-route-ranker-or-click-shape`, `static-map-disagrees-with-live-step`,
`interrupted-reclick-clean-prefix`, or `interrupted-reclick-after-divergence`.

Run procedure for Batch B:

1. Keep in-game run OFF.
2. Keep `Log Benchmark Movement` OFF.
3. Enable `Settings` -> `Log Route Segments`.
4. Put one waypoint on the final route target.
5. Walk by clicking the highlighted tile you would naturally click next. For object-profile proof,
   let the segment finish before clicking again. For human-cadence proof, re-click normally; those
   rows should be interpreted through `completed=false`.
6. If a door or object must be clicked first, click it normally; `Validate Map Data` can be enabled
   only when we specifically need `DREW_TRAVERSAL` object rows too.
7. Send the `DREW_ROUTE_SEGMENT` rows around any visible mismatch, especially rows with
   `classification` not equal to `match`.

First useful Batch B spots:

```text
B1 Lumbridge dining room table pressure
Start near: 3222,3218,0
Route to:   3092,3245,0
Goal:       capture the segment where the displayed route tries to use the dining-table tile.

B2 Draynor Manor dead-tree pressure
Start near: 3092,3245,0
Route to:   3109,3352,0
Goal:       capture the segment where the displayed route tries to cut through dead trees.

B3 Varrock east/Sawmill legal shape pressure
Start near: 3253,3420,0
Route to:   3307,3491,0
Goal:       capture one non-object legal route-shape miss from A6.
```

Live Batch B result from Myth, checked 2026-08-14:

```text
Rows read: 33 from %USERPROFILE%\.runelite\drews-route-segments.txt
Targets seen: (3222,3218,0), (3109,3352,0), (3307,3491,0)

Most noisy rows ended with reason=destination-changed, which is expected when Myth intentionally
emulated frequent re-clicking and mistake clicks. Those rows proved the recorder needed a
completed/interrupted distinction before object-profile proof.

Useful completed evidence:
- Target (3222,3218,0): several completed route-shape rows remain, including one 37 expected vs
  42 actual segment with maxDev=7.
- Target (3109,3352,0): final northbound segment matched exactly, but the dead-tree approach still
  needs a focused completed pin; most suspicious rows were interrupted.
- Target (3307,3491,0): final segment was a benign same-time local permutation; the stronger
  Sawmill/object-pressure rows were interrupted and need a focused completed pin.
```

### ROUTE-SEGMENT VALIDATION BATCH C

Use D-0185 for cleaner object/ranker pins:

```text
C1 Lumbridge table exact pin
Goal: set the waypoint so the highlighted route visibly tries to cross the dining table, then make
one click that allows that exact segment to finish. Send completed=true rows only.

C2 Draynor dead-tree exact pin
Goal: stand just before the dead-tree leak, click the highlighted next tile beyond it, and let that
segment finish. Send completed=true rows only.

C3 Sawmill shape/object pressure exact pin
Goal: repeat the A6 pressure area, but let the suspect segment finish before re-clicking. Send
completed=true rows with non-match classification.
```

Live Batch C result from Myth, checked 2026-08-14:

```text
C1 Lumbridge -> Draynor target (3092,3245,0)
6 completed non-match rows. The clearest object-pressure row was (3211,3221,0) -> (3207,3218,0),
where the displayed route used the Lumbridge dining-room table line.

C2 Draynor bank -> Manor target (3109,3352,0)
5 completed rows, 4 non-match and the final segment matched. The strongest object rows were
(3084,3282,0) -> (3083,3303,0) and (3098,3318,0) -> (3109,3343,0), both through/along the
Draynor tree/dead-tree clusters.

C3 Varrock east -> Sawmill target (3307,3491,0)
3 completed non-match rows. The strongest row was (3253,3420,0) -> (3275,3452,0), where the
displayed route rode the tree line north instead of following the live detour east.
```

### 2026-08-14 OBJECT-PROFILE PROOF PASS

D-0186 ran the object-placement probe over the completed Batch C divergence windows and rebuilt the
all-region collision-map report twice against frozen live flags
`build/frozen-live-flags-object-profile-pass-20260814.txt`
(`37,459,405` bytes, SHA256
`E9562CAB1466B2AF0C06EAB981DAC66BB9758A9CBBC005D00F8E0ACCC3397ACD`).

Supported candidate read:

```text
596/10 Table       - direct C1 table crossing, no projected overblock in focus row.
10820/10 Oak_tree  - C2 west Draynor object crossing, no projected overblock.
1282/10 Dead_tree  - C2 north Draynor cluster, no projected overblock.
1283/10 Dead_tree  - C2 north Draynor cluster, no projected overblock.
11510/10 Dead_tree - C2 north Draynor cluster, no projected overblock but low sample.
1276/10 Tree       - C3 Sawmill tree line, no projected overblock.
1276/11 Tree       - C3 Sawmill tree line, no projected overblock.
1278/10 Tree       - C3 Sawmill/tree pressure family, no projected overblock.
1278/11 Tree       - C3 Sawmill/tree pressure family, no projected overblock.
```

Hold-back read:

```text
1289/10 Dead_tree  - still useful evidence, but projectedNewOverblock=270 and benefit was below
                    the 3.0x paid-profile gate in the disabled-object run.
9661/10 Tree_stump - projectedNewOverblock=107, benefit below gate.
7169/10 Table      - projectedNewOverblock=20, benefit below gate.
34803/10 Rubble    - projectedNewOverblock=16, benefit below gate.
34804/10 Rubble    - projectedNewOverblock=20, benefit below gate.
19143/10           - missing named-solid profile in the all-region report; do not add by id.
```

Trial build result with the supported candidate set added by command-line override only:

```text
DANGEROUS_UNEXPLAINED: baseline 139035 -> trial 84729, drop 54306
route-aware OVERBLOCK: baseline 8264 -> trial 8886, rise 622
net gate: OK (54306 > 622)
object profile placements blocked: 17799
```

This did **not** promote `build/collision-map-v2.zip` into `src/main/resources/collision-map.zip`.
The current resource remains the D-0147 map. D-0147 still controls tree-family shipping: a no-cost
tree row is not enough by itself because an earlier tree trial moved a pinned Falador live-route
fork. The next implementation pass may create a gated test map from the supported set, but it must
be live-rerun on Falador primary/reverse/east-pressure plus C1/C2/C3 before shipping.

D-0187 gated candidate-map result:

```text
output: build/collision-map-v2.zip
zip sha256: 8BE900A1FFD4A6F19E5C47FCEF8F3D13FE4BB24C47272A35E7EC8B965BCD27C3
zip entries: 2936
runtime map still unchanged: src/main/resources/collision-map.zip
runtime sha256: FC2B4F971F40D1DAE30B54D103B071D722177A1B51DC7071C71D7242F020EECC
```

The D-0187 build used only these additional profile keys:

```text
596/10, 10820/10, 1282/10, 1283/10, 11510/10, 1276/10, 1276/11, 1278/10, 1278/11
```

Held-back keys were not present in the object-profile key line:

```text
1289/10, 9661/10, 7169/10, 34803/10, 34804/10, 19143/10
```

Gate result:

```text
ROUND TRIP OK 2936 regions
outside built regions: 0
DANGEROUS_UNEXPLAINED: baseline 139035 -> current 84729, drop 54306
route-aware OVERBLOCK: baseline 8264 -> current 8886, rise 622
net criterion: OK (54306 > 622)
object profile placements blocked: 17799
```

Proof control remains below the no-object baseline (`70.600% -> 64.429%`), which is expected for
real blockers and is not the ship gate by itself. The live route pins are still mandatory before
runtime promotion.

Live rerun list for the candidate map:

```text
Falador primary:        2942,3243,0 -> 2951,3208,0
Falador reverse:        2951,3208,0 -> 2942,3243,0
Falador east pressure:  2946,3239,0 -> 2951,3208,0
C1 Lumbridge/Draynor:   3222,3218,0 -> 3092,3245,0
C2 Draynor Manor:       3092,3245,0 -> 3109,3352,0
C3 Varrock/Sawmill:     3253,3420,0 -> 3307,3491,0
```

Commits from the 2026-08-13 Mytharium route/collision session, in order:

      d2225cf  fix collision map region seam edges              PUSHED
      f66d4b8  ship narrow furniture object blocking            PUSHED
      2118066  harden transport requirement cache filtering     PUSHED
      3c662d3  block shortcut corridors as walking              PUSHED
      1555f70  audit shortcut corridors                         PUSHED
      d407500  audit terrain completeness                       PUSHED
      7b42c6a  expand measured object profile blockers          PUSHED

Mytharium pushes manually. At handoff, `main` and `origin/main` were even before this docs-only
writeup. The docs changes from this handoff are not listed in the table above.

### WHAT CHANGED TODAY

The Falador wall report was valid and fixed. The bad edge was `3019,3391,0 N`, between
`3019,3391` and `3019,3392`. Root cause was a region seam: the wall object was read while
building region `47_53`, but the normalized stored edge belongs to region `47_52`. Deferred
neighbour edges now apply to the owning built region. Mytharium confirmed the route from
`3019,3390,0` to `3019,3401,0` now detours instead of drawing through the wall.

Furniture/object blocking shipped as a measured allowlist, not old Phase 2. The first pass
blocked only `595/10 Table`, `1104/10 Bench`, `1088/10 Chair`, and `1088/11 Chair`. Mytharium
confirmed the chair waypoint at `2573,3245,0` snaps west to `2572,3245,0` instead of staying on
the chair.

Transport eligibility was hardened globally. The route graph still uses
`drewshelper-transports.tsv` as the source of truth, but capability signatures now include every
item requirement expression from the TSV so inventory/equipment changes cannot reuse a stale
filtered graph. When Mytharium still saw Broken Raft routing, the second fix found the real bypass:
the raft corridor was being walked as normal ground after the gated transport was removed.
`AGILITY_SHORTCUT` and `GRAPPLE_SHORTCUT` corridors are now transport-only geometry.

The shortcut audit covered the full shortcut corpus: 557 `AGILITY_SHORTCUT` rows plus 15
`GRAPPLE_SHORTCUT` rows, 572 total. Same-plane rows expanded to 2,981 adjacent walking steps and
the audit found 0 unblocked shortcut-corridor walking steps.

The terrain completeness audit said not to ship a terrain-bit rule. The existing floor/terrain
rule still agrees at 181,696/189,245 edges (96.0%), and the bridge branch at 361/384 (94.0%).
`tileSetting` bit 4 is enriched but explains too small a slice of the miss set, so broad terrain
blocking would be guesswork. The bigger remaining class is ignored scenery/object blocking.

The object-profile blocker expanded from 4 furniture profiles to 22 measured profiles:
plants/bushes/cactus, boulders/rockslides/fountain, plus the original table/bench/chair profiles.
Map size changed `1,189,982 B -> 1,194,815 B`. Frozen A/B versus the current furniture map:
`DANGEROUS_UNEXPLAINED 78,069 -> 75,184`, fixing 2,885 dangerous-unexplained edges with 0 added
measured `OVERBLOCK` and 0 added route-aware `OVERBLOCK`.

Trees and tree-stumps were trialed and deliberately held back. Their cost column looked clean,
but they moved the pinned Falador southeast live-route fork. That violates the no-real-route-seal
rule, so tree profiles need their own pass.

### LIVE TEST RESULTS FROM MYTHARIUM

1. Chair waypoint: PASS. Waypoint `2573,3245,0` snaps to `2572,3245,0`, west of the chair, in an
   accessible square.
2. Ruins of Unkah ferry pier: PASS. Mytharium can still stand on `3148,2843,0`, walk the pier, and
   route/walk to `3156,2839,0`. The old Phase 2 pier/beach regression did not return.
3. Falador southeast tree-line route: PASS. The original completed benchmark showed the displayed
   route was 36 points while Myth's walked route was 39 points, merging back near `2952,3209,0`.
   D-0177/D-0178 patched the visible route to follow that completed walked path for target
   `2951,3208,0`. Myth's live rerun after the patch completed with displayed `expectedPath` and
   walked `actualPath` both 39 points: `full=true`, `lenDelta=0`, `maxDev=0`, `turnDelta=0`,
   `divergence={none}`.
4. Creative Falador route-shape controls: PASS after D-0180. Reverse
   `2951,3208,0 -> 2942,3243,0` completed as an exact 39-point match with `full=true`,
   `lenDelta=0`, `maxDev=0`, `turnDelta=0`, and `divergence={none}`. East pressure
   `2946,3239,0 -> 2951,3208,0` completed as an exact 35-point match with the same zero-deviation
   result. Fork isolate had already matched before D-0180.

### NEXT CODING ORDER

1. **Object and door-state recorder.** Record the object identity and state that the live client
   actually traversed or blocked against. A shut door and an open door are different collision
   worlds; do not record object id alone and call it proof.
2. **Route-validation harness.** Add the offline structural validations plus the small hand-walked
   check set so candidate maps and route-ranker changes have repeatable gates.
3. **Pilot region cleanup.** CLOSED, D-0194. The focused `48_50` recapture did not produce a
   completed adjacent static-map disagreement, so no pilot collision/object/ranker patch ships.
4. **Requirements messaging plus `SAILING` category support.** CLOSED, D-0195. `Requirements` now
   renders below Actions when no normal route exists but a capability-locked near-miss does.
5. **Active Sailing edge data.** PARKED until verified gangplank/dock interaction tiles exist.
   Port-task navigation points and wiki map pins are useful evidence, not safe land-route sources.
6. **Paid or unnamed object-profile batches.** Hedges, stools, shelves, crates, held-back paid
   profiles, and unnamed object rows need their own proof pass and live-route pins.
7. **Snap edge cases.** Revisit after the recorder and harness can tell
   reachable dock routing from bad snap or missing water-side reachability.

Do not reopen tonight's rejected paths without new evidence: broad Phase 2, global locType 10/11
blocking, `tileSetting` bit 4 as a terrain blocker, or route-specific shortcut hardcodes.

### WHERE TODAY IS WRITTEN DOWN

      D-0141  DECISION   region-seam edges defer to the owning built region
      D-0142  DECISION   furniture blocker ships as measured object-id/locType allowlist
      D-0143  DECISION   transport gates are account capability, not route reports
      D-0144  DECISION   shortcut corridors are not ordinary walking edges
      D-0145  DECISION   full shortcut corpus audited, 0 unblocked same-plane steps
      D-0146  DECISION   terrain completeness audit rejects broad terrain-bit change
      D-0147  DECISION   object-profile expansion ships no-cost non-tree scenery profiles
      D-0171  CHANGELOG  end-of-night summary, live tests, next work, and backlog updates
      D-0172  CHANGELOG  docs cleanup: one active handoff, older starts demoted
      D-0173  CHANGELOG  add top-level what-next line to active handoff
      D-0174  CHANGELOG  re-enable one-click route-vs-actual benchmark capture
      D-0175  DECISION   route benchmark restored as the same-key repro recorder
      D-0176  CHANGELOG  keep benchmark capture alive through off-path recalculation
      D-0177  DECISION   Falador southeast fix must stay exact, target-aware, and non-global
      D-0178  CHANGELOG  patch Falador southeast visible route from completed benchmark trace
      D-0179  CHANGELOG  live-validate Falador southeast visible route patch
      D-0180  DECISION   creative controls stay scoped to observed paths
      D-0181  CHANGELOG  live-validate reverse and east-pressure controls
      D-0182  CHANGELOG  stage broader route-shape validation Batch A
      D-0183  DECISION   Batch A shifts route work to segment classification
      D-0184  CHANGELOG  add passive route-segment recorder
      D-0185  DECISION   interrupted route segments are click-cadence evidence first
      D-0186  DECISION   object-profile proof stays gated after Batch C
      D-0187  CHANGELOG  build gated D-0186 candidate collision map
      D-0188  DECISION   promote supported object profiles; held-back keys stay out
      D-0189  DECISION   confidence is explicit route-data metadata
      D-0190  CHANGELOG  session-close handoff after confidence tiers
      D-0191  CHANGELOG  object and door-state evidence recorder
      D-0192  CHANGELOG  route-validation harness
      D-0193  DECISION   pilot cleanup hard gates require completed adjacent evidence
      D-0194  DECISION   focused clean recaptures supersede stale interrupted pilot rows
      D-0195  DECISION   requirement messaging uses same-policy unrestricted near-miss solves

## Historical Handoff - 2026-08-12 Recorder-First Plan

Historical note: this block was the active start point on 2026-08-12. The recorder-first plan is
still valid, but the 2026-08-13 route-display/object-blocker handoff above now comes first.

Commits from the 2026-08-12 sessions, in order:

      d71a2f1  post-promotion verification of 5bddcf4  (docs only)   PUSHED
      fe7ef81  terrain floor rule verified              (no logic)    PUSHED
      4c2d0d4  rescope + sailing research               (docs only)   PUSHED
      d20cf36  session 2 handoff document               (docs only)   PUSHED
      db50b28  handoff push-state and commit table      (docs only)   PUSHED

Mytharium pushes manually - ask, do not push. The table above will not list the commit that
carries this document; run `git log --oneline origin/main..main` for the true pending set.

### WHAT CHANGED THIS SESSION

Mytharium drafted an OSRS navigation-recorder plan and asked whether to adopt it. It is
ADOPTED, in the adapted form recorded as D-0136. That reorders the work: the project stops
expanding the collision map and starts verifying one pilot region. The first version named
UNKNOWN-in-capture as item A, but D-0137 later closed A as not a defect; the corrected recorder
sequence starts at B.

Two numbers drove the decision. Live-client ground truth covers 4.20% of shipped regions, which
the D-0137 correction did not change. The agreement figure is 84.03%; 51.86% is blocked-edge
recall, not a correction of the agreement score.

### THE LIST - revised 2026-08-12 after adopting the recorder-first plan

The original six items are kept for continuity. Two are closed, three are re-ranked against the
adopted plan, and one is absorbed. Within the recorder plan, the active work is B-F; A is retained
only as a closed correction.

CLOSED

      1. Fix the builder's floor rule            DONE - fe7ef81. Measured, found already
                                                 correct, no fix was needed.
      4. Snap-on-login fix                       CLOSED - no bug. setWaypoint persists the
                                                 SNAPPED value: :683 rebinds `point` before
                                                 the :692 config write, so loadWaypoints
                                                 reading it raw is harmless. The config
                                                 panel path self-heals via onConfigChanged.
                                                 Three unrelated edge cases fell out - see
                                                 Open items below.

ACTIVE SEQUENCE - in order

      A. UNKNOWN in the capture emitter          CLOSED - not a defect, no code written.
                                                 D-0137 supersedes D-0136 RULE 1: the format
                                                 already carries `covered=` per scene, the
                                                 builder enforces it, and unobserved tiles
                                                 never enter the comparison at all.
      B. Traversal verification listener         ~1 day. Compare predicted against actual on
                                                 every manual traversal and record the
                                                 contradictions. This is what breaks the
                                                 4.20% ground-truth ceiling, because it
                                                 collects during normal play instead of
                                                 needing a scheduled walk. D-0136 RULE 3.
      C. Confidence tiers, including INHERITED   CLOSED, D-0189. Collision-map provenance is
                                                 explicit via sidecar, and transport rows carry
                                                 confidence/provenance columns.
      D. Object and DOOR STATE recorder          CLOSED, D-0191. Object state is now
                                                 captured in `DREW_OBJECT_STATE v1`
                                                 evidence rows without changing route
                                                 behavior.
      E. Route-validation harness                CLOSED, D-0192. `gradlew validateRoutes`
                                                 runs the offline structural gate,
                                                 summarizes hand-walked route segments,
                                                 and correlates nearby object/door rows.
      F. Pilot region to zero known errors       CLOSED, D-0194. Focused `48_50` recapture
                                                 superseded the old interrupted/non-adjacent
                                                 row and found no completed adjacent static-map
                                                 disagreement to promote.
      G. Route-vs-actual tracking                DEFERRED until the map is fixed - Mytharium's
                                                 call, 2026-08-12. Not a build, a switch:
                                                 `ROUTE_BENCHMARK_ENABLED` at
                                                 `DrewsHelperPlugin.java:101` is a hardcoded
                                                 false. The comparator behind it is intact and
                                                 substantial - first divergence index, max
                                                 lateral deviation, merge-back classification,
                                                 shadow-route fit scoring. Held back on
                                                 purpose: at the error rate measured on
                                                 2026-08-12 (81.1% of 518,105 disagreements
                                                 are under-blocks) it would mostly report
                                                 divergences caused by the MAP, not by the
                                                 router, so it is noise until F closes.

                                                 Superseded for the immediate Falador display
                                                 repro by D-0174/D-0175: the same movement
                                                 benchmark is available again as
                                                 `Settings` -> `Log Benchmark Movement`, default
                                                 OFF, because this case needs tile-for-tile
                                                 route-vs-actual evidence before the fix.

CARRIED, RE-RANKED

      6. Sailing-aware routing + Requirements    PARTIAL, D-0195. Requirements messaging and
                                                 SAILING category/cache support are built.
                                                 Active sailing rows remain parked behind
                                                 verified dock interaction tiles - parked 30.
      3. 2 regions 52_50 / 52_51                 OFF the critical path - Al Kharid, outside
                                                 the pilot area. Otherwise unchanged: ~30
                                                 minutes, fixes 137 of 171 known leaks,
                                                 available whenever wanted.
      2. ~74 Varlamore regions                   DEPRIORITISED. Cache-derived expansion is
                                                 exactly what the adopted plan defers, and
                                                 it still needs a capture walk first.

### Recommended order to pick up, with the reasoning

**Item A is CLOSED - do not build it.** D-0137 supersedes D-0136 RULE 1 in full. The capture
format already carries the observed region per scene, the builder parses and enforces it, and
unobserved tiles never enter the comparison at all. AGREE_OPEN is observed evidence rather than
silence, so 84.03% is the honest agreement figure and 51.86% is blocked-edge recall, not a
correction of it. No emitter change is needed and none was made.

**Start with B, because it compounds.** Every other item is a fixed amount of work for a fixed
payoff. B changes the collection RATE for the rest of the project's life - ground truth stops
being an errand that has to be scheduled and becomes a by-product of playing. Coverage is still
4.20% and that was never affected by the RULE 1 error, so B is now unambiguously the top item.

**C, D and E are the plan's machinery**, and that order matters: tiers before the object recorder
so object data lands already labelled, and the harness last so it has something to validate.

**Sailing (item 6) is split now.** D-0195 built the visible requirements path and category support.
The data slice is still parked because the graph needs walkable dock interaction tiles, not just map
pins or boat navigation points.

**Needs Mytharium in game - item 2 / Slice 1.** Unchanged: the ~74 Varlamore regions have zero
live-client ground truth and need one capture walk before shipping. Deprioritised, not dropped.

**Blocked on a prerequisite - any expansion.** Unchanged: the zip merge has no script and no
gradle task. A `promoteCollisionMap` task must exist before adding NEW entries. Spec in D-0170.

### Where everything from these sessions is written down

      D-0136  DECISION   adopt the recorder-first plan - eight rules, four unbuildable
                         targets restated, and what already exists and must not be rebuilt
      D-0189  CHANGELOG  confidence tiers landed for collision-map provenance and transport rows
      D-0169  CHANGELOG  floor rule measured and verified, 98.086% / 100.000%
      D-0134  DECISION   the rule is verified - do not "improve" it, plus two method traps
      D-0170  CHANGELOG  region census, ocean hazard, Varlamore correction, slicing plan
      D-0135  DECISION   five rules: absence is safe, never bulk, positional selector,
                         the Phase 2 gate is 24-region-only, Zeah shipped / Varlamore missing
      parked  27  cross-region seam drops S/W edges, 18 in rebuilt regions
      parked  28  526 false positives, likely cause of parked item 24's regression
      parked  29  legacy regions hold 147 of 171 leaks, concentrated 52_50 / 52_51
      parked  30  active sailing edge rows need verified gangplank/dock interaction tiles
      closed  31  "Requirements:" near-miss diagnosis and display shipped in D-0195

### Corrections that supersede earlier notes

- **There is no `--regions` flag.** The builder's region selector is positional; tokens are
  `rx_ry`, a bare id, or the literal `all`.
- **Zeah/Kourend is shipped. Varlamore is the gap.** Any guide text saying Zeah is missing is
  wrong.
- **The "63% of routes over-turn, max 33 excess" figure is retired.** Obstacle-aware measurement
  gives trueMean 2.04, trueMedian 1.50, trueMax 12.
- **`useShips` / `useBoats` / `useCharterShips` / `useAgilityShortcuts` do not exist in the
  code.** They are stale orphans in the RuneLite profile, like the deleted `routeSolverMode`.
- **The ocean is not blocked by the client either** - only a 1-2 tile coastline band. Our map and
  the client agree there at 98.1%. Any future "the map thinks water is walkable" report must
  check the client's own flags before being treated as a disagreement.
- **Parked item 26 is FALSE as written.** It claims 21 of the 24 rebuilt regions have no live
  data. All 24 have live-client rows, from 187 to 15,724 each; region 47_50 has 2,414. The claim
  held only for `POST_20260812` read in isolation. Defensible version: no rebuilt region has FULL
  ground truth - aggregate plane-0 coverage across the 24 is 39,405/98,304 = 40.1%, best 70.7%,
  worst 4.6%, and none reaches 99%.
- **D-0169's stated sample size does not reconcile.** It reports 233,067 covered tiles minus
  40,113 sentinel rows leaving 192,061, but that subtraction gives 192,954 - a gap of 893 - and
  neither input reproduces from the three named capture files. The 98.086% / 100.000% confusion
  matrix is internally consistent and stands on its own arithmetic; the SAMPLE SIZE must be
  re-derived before it is quoted again.
- **D-0170's "13 stale entries, all of them 32,784-byte legacy entries" is wrong on size.** The
  13 region ids are correct. Measured entry lengths run 143 to 1,506 bytes and no entry in the
  zip is 32,784 bytes - that figure is the decoded in-memory size, not the zip entry size.
- **The 82.97% v2 figure is not a clean "our data" score.** That scene spans 6 regions of which
  only 4 are rebuilt; 45_49 and 46_49 are untouched legacy shipping identical bytes on both
  sides, which dilutes the delta. The figure UNDERSTATES the rebuild.

### Open items for Mytharium

- **Sailing versus the open-sea marker.** Sailing edges fix "route me to that island". They do
  NOT fix "I dropped a marker in open sea where there is no dock" - that is still the snap
  problem. Decide whether the snap also needs a reachability-gated water seal, or whether dock
  routing is enough. D-0136 RULE 4 rules out a water map, so any seal is a snap-side fix.
- **Three snap edge cases found while closing item 4**, none actioned and none yet parked:
  `snapToTraversable` swallows an IOException and persists the UNSNAPPED coordinate when
  collision data is missing at set time; editing the position field while the plugin is toggled
  OFF skips `onConfigChanged`, so the raw value is never re-snapped; and a tile snapped under one
  collision build can become non-standable under a newer one.
- **Confirm the pilot region overlap.** rx45-48 / ry49-52 is coordinate arithmetic, not a lookup.
  Five minutes, and it should happen before any work starts inside the pilot area.

## Historical Handoff - Magic-Tab Spell Teleports From Carried Supplies

Historical note: home teleports shipped on 2026-08-09, and this section records the magic-tab and
bank-aware teleport plan. It is parked context, not the current next code pass while the
route-display/object-blocker and recorder-first work above it are active.

### Completed slice: home teleports

- `teleportation_spells_home.tsv` is ingested into `drewshelper-transports.tsv`.
- Destination-only rows emit as originless `BASELINE` edges with source `-1,-1,0` (`ANYWHERE`).
- Originless edges are offered only at each waypoint leg start.
- `@` cooldown requirements are supported; active or unknown cooldowns lock the teleport.
- Lumbridge's four home-teleport variants remain distinct through requirement-aware originless dedup.
- Legal step generation, edge legality, travel-estimate lookup, and `Actions` labels all recognize originless home-teleport jumps.
- Full test/build passed after implementation.

### Live test checklist for the home-teleport slice

Launch with `gradlew.bat run`. The unit tests build the capability object directly, so the one link
they cannot cover is the live client read: `DrewsHelperPlugin` snapshots varplayer 892 through
`DrewsHelperTransportGraph.requiredVarPlayerIds()`, and `892@30` is then evaluated as
`currentEpochMinute - actual > 30`. Whether varp 892's units and epoch actually match that
arithmetic can only be settled in game.

1. Fresh and off cooldown, set a distant waypoint. A home teleport should appear as `Actions` row 1
   with the time `0:00`, and the walking route should begin from the teleport destination.
2. Cast the home teleport, then re-route immediately. It must NOT be offered.
3. Around 31 minutes later it should reappear on its own, without touching any setting. The route is
   marked dirty once a minute while waypoints exist, so up to a minute of lag is expected behaviour.
4. On the Lunar or Arceuus spellbook, the offered teleport should be that book's destination rather
   than Lumbridge.
5. A route with two or more waypoints should offer a fresh teleport at each leg start, not only the
   first leg.
6. Standing inside the Wilderness, no home teleport should be offered at all.

Two failure signatures worth reporting separately, because they point at opposite causes:

- Teleport never offered anywhere - varp 892 is not minutes-since-epoch in the way `@` assumes.
- Teleport offered even immediately after casting - the varp is not being read, or reads 0.

**Live results, 2026-08-09 (Myth).** Steps 1, 2 and 3 all pass in game.

- Step 1 PASS - teleport offered on a long route, `Actions` row 1, path starts at the destination.
- Step 2 PASS - after casting, a fresh route from Giants' Foundry to Lumbridge did NOT offer it.
- Step 3 PASS - roughly 30 minutes later the route offered the teleport again on its own.
- Step 1 passing is also the proof that varplayer 892 is read off the live client: an unread
  cooldown var evaluates as unknown, which locks the edge, so no teleport would ever appear.
- Label PASS - the destination name renders in white over the marked tile.
- Multi-waypoint PARTIAL - a two-waypoint route took a home teleport on leg 1 and a spirit tree
  on leg 2, which proves each leg solves and picks its own transport independently. It does not
  isolate an originless offer at leg 2 or later; that stays covered by the unit test alone.
- Still untested: spellbook variants, which need Lunar or Arceuus unlocked.
- Wilderness escape: PASS. Myth confirmed that standing in the Wilderness with the teleport off
  cooldown, the route offers it as expected.

### Next coding slice: spell teleports from carried supplies

Do not ship `teleportation_spells.tsv` until rune requirements are modeled well enough to avoid false offers.

Required:
- Real Magic level gate.
- Spellbook/unlock var gates.
- Inventory item counts.
- Equipped staff counts, because staffs are normally worn.
- Rune-pouch contents from vars.
- Generator-side expansion of symbolic rune names into the existing item requirement grammar, using upstream's rune/staff/combination-rune table instead of hand-written wiki memory.

Known rule:
- Bank contents do not count as castable from anywhere. They become usable only after a bank step exists in the route.

### Later slice: bank-aware teleports

Myth wants bank-aware teleport routing when it is actually faster, not a blanket "count the bank" shortcut.

Design:
- Use bank contents only if RuneLite has a known bank cache from the player opening the bank.
- If the bank cache is unknown, show no bank route rather than inventing one.
- Use upstream bank tile data first. Ask Myth for missing bank tile coordinates only if upstream is incomplete.
- Add bank access as an honest graph/state transition. Search node becomes `(tile, bankedYet)`.
- Teleports requiring supplies that are only in the bank become legal only after the bank transition.
- Give the bank transition a fixed withdraw cost, then tune it from live use.
- Highlight the exact needed runes/staff/items in the bank UI so the fixed withdraw cost can be lower and more realistic.
- Let A* decide. A bank route wins only if its full cost is shorter than walking, spirit trees, boats, or another available teleport.

Rule after bank support:

```text
If carried supplies can cast it -> use teleport normally.
If carried supplies cannot cast it but known bank has supplies -> consider route-to-bank + withdraw + teleport.
If bank is unknown or lacks supplies -> treat teleport as locked.
If cooldown is active -> treat teleport as locked.
```

### Later families

- Minigame teleports: not a submenu tree in the data. Each destination is already its own row. Add after the originless/cooldown machinery works.
- Teleport items, jewellery boxes, portals, POH portals, tablets, scrolls, capes, and other bulk transport files come after spells/minigames prove the account-gating model.
- Retire or repurpose the dead Teleport Options / placeholder Other Transportation toggles only after their transport families are innate in the route graph.

## Historical Handoff - 2026-08-10 Session Close

Historical note: this handoff is superseded by the later 2026-08-12 recorder-first plan and the
2026-08-13 route-display/object-blocker handoff. Keep it as evidence for map-data decisions, not
as the current work order.

The map-data work is BUILT but not yet WIRED IN. Route B produces a list; nothing in the
plugin reads it yet. Everything below is in priority order - do them top down.

### 1. Tighten the movement-verb filter  -- DONE 2026-08-10 (see D-0119)

**Result: 14,048 -> 12,474 rows. 1,574 containers dropped, 100 real passages rescued, the
Falador gate fixture still passes cold.** The fix was NOT the one predicted below - see D-0119.
Original note kept for the record:


Blocks judging everything else, so it goes first. `CacheAccessPointDumper.MOVEMENT_OPS`
matches on "open", which also collects 640 Chest, 226 Drawers, 197 Closed chest and 145
Wardrobe - containers, not movement. So the 11,610 uncovered figure is an UPPER BOUND and
must not be quoted as a gap count until this is done.
Fix: intersect the verb match with the object actually obstructing movement.
`ObjectDefinition` already exposes `getInteractType()`, `getBlockingMask()` and
`getWallOrDoor()` - none are used yet. A chest blocks nothing you would path through.
Done when: the Falador gate fixture still passes AND Chest/Drawers/Wardrobe are gone.

### 2. Turn confirmed access points into real transport rows  -- IN PROGRESS, WAITING ON ROUTE A PROOF (see D-0120, D-0121)

**Generator built, rows not active.** `gradlew.bat generateTransportRows` writes
`tools/cache-derived-gates.tsv` and deliberately never touches `tools/transport-overrides.tsv`.
The current output is a review/ranking file, not routing data.

The missing piece is confidence, not plumbing. A transport row needs two tiles, and the dump gives
one. For wall placements, orientation gives a real signal about the edge: the Falador gate proves
`orientation 2 = east`, and the current mapping is `0=W, 1=N, 2=E, 3=S`.

But the control is not strong enough to ship:
- predicted edge blocked: 65.0% (2172 of 3344)
- perpendicular edge blocked: 40.1% (2680 of 6688)

That 25-point gap proves orientation is not noise. It also proves this is not safe enough to bulk
merge 1,282 crossings. A wrong row routes the player through a wall, which is worse than the detour
we started from. Do not move `cache-derived-gates.tsv` rows into `transport-overrides.tsv` until
they have live-client/manual proof or a stronger filter.

Second slice result:
- exact obvious instance/minigame/scenery junk is dropped before review: Cloud bank, Portal of
  Death, Oozing barrier, Wall of flame, Gate of War, Energy Barrier, Neutral Barrier, Blue
  Barrier, Red Barrier and Alchemical door
- survivors are ranked by walking detour pain with a 512-step cap
- `tools/cache-derived-gates.tsv` stays copy-paste-shaped but is sorted by review rank and marked
  with comments
- `tools/cache-derived-gates-review.tsv` is the machine-readable review queue with rank, edge key,
  detour, source tile, destination tile, name and action
- `tools/cache-derived-gates-proven.tsv` contains only candidates whose normalized edge matched
  Route A live mismatch proof

Current second-slice funnel:
- raw candidate crossings: 1,282
- exact junk removed: 337
- review crossings left: 945 / 1,890 bidirectional rows
- detour severity: 862 are `>512`, 5 are `65-512`, 18 are `17-64`, 60 are `2-16`
- Route A proof files are absent, so proven crossings are 0 and no active route row changed

Route A proof workflow:
1. Enable `Validate Map Data` in the dev client and visit a ranked candidate area.
2. Copy raw `DREW_MAP_VALIDATE   x,y,plane DIR OURS_BLOCKS_LIVE_OPEN` lines into
   `tools/route-a-live-mismatches.txt`, or write `x<TAB>y<TAB>plane<TAB>DIR` rows into
   `tools/route-a-live-mismatches.tsv`.
3. Re-run `gradlew.bat --no-daemon --console=plain generateTransportRows`.
4. Review `tools/cache-derived-gates-proven.tsv`.
5. Only after that, hand-copy reviewed proven rows into `tools/transport-overrides.tsv` with an
   evidence comment. The generator still must not write the active override file.

Original note kept for the record:

This is the step that fixes the original complaint. Until it happens, none of the work so
far has changed a single route.

**Hard constraint agreed with Myth - do NOT bulk-import all 1,793 doors.** Many are locked,
quest-gated, members-only, or open onto nothing useful. Importing blind makes routing WORSE
than today, because the router would confidently send him through a door he cannot open.
That is a worse failure than the long detour we started with.
Sequence: start with a narrow high-confidence slice (Gate, Gap, Wilderness Ditch), Myth
eyeballs two or three real routes, only then widen to Door/Ladder/Staircase.
Existing mechanism to reuse: `tools/transport-overrides.tsv` already exists and already
holds the hand-added Falador fix, so there is a proven path for injecting rows.

### 3. Rebuild the collision map from the cache  (biggest win, biggest job)

The cache holds 2,936 regions; the shipped `collision-map.zip` holds 1,524. That is 1,425
regions with NO walking data at all - Zeah/Kourend and more - which is why routing over
there has been unreliable. Not a missing gate: no data whatsoever.
Now possible because the cache decodes without keys (see D-0117). Check
`github.com/osrs-pathfinding/shortest-path-tooling` before writing a decoder - upstream's
README points there for exactly this.

### 4. Carried-spell teleports  (the pre-existing next feature)

Runes, staffs, rune pouch. This was the next slice BEFORE the map work started and is still
outstanding. Unlike 1-3 it is a visible gameplay feature rather than plumbing, so it may be
the better pick if Myth wants something he can see working.

### 5. Wilderness fix live check  -- DONE 2026-08-10

Myth confirmed this works in game. Original repro kept for the record: waypoint #1 inside the
Wilderness, waypoint #2 at Lumbridge, home teleport ON COOLDOWN, Wilderness transports OFF.
PASS = routed back across the ditch. FAIL = still offered Teleport Mage of Zamorak.
Also sanity-check that walking out is still free and that an off-cooldown home teleport is
still offered - those are what a bad version of that fix would have broken.

### 6. Decide whether the generated dump belongs in git

See Parked Item 17. `tools/cache-access-points.tsv` is 638 KB and regenerates on demand.
Either gitignore it or commit it deliberately - do not let it drift in uncommitted, which is
how the untracked `transport-overrides.tsv` problem started.

---
## Parked Items - revisit after the teleport build

Standing list, opened 2026-08-09 at Myth's request: side-findings raised while building are logged
here instead of being actioned mid-slice, so the build backlog stays uninterrupted. Nothing in this
section has been changed. Append new findings here as they come up; strike them out when cleared.

### Confirmed - evidence recorded

1. ~~**`tools/transport-overrides.tsv` is untracked in git.**~~ RESOLVED 2026-08-09, committed in `8aed260`.
   The generated transport resource merges it at generation time, so any checkout without that file
   regenerates and silently drops those 4 verified override edges (the Taverley/Falador wall gate
   fix among them). Fix is a one-line `git add`, which is Myth's call to make, not C2's.
   Evidence: `git status --porcelain` reports it as `??` while `drewshelper-transports.tsv` depends on it.

2. **The destination-only fallback is category-wide, not home-teleport-scoped.**
   `tools/generate-drewshelper-transports.ps1` line 355, `elseif ($destOnly.Count -gt 0)`, fires for
   any section that has landing rows and no boarding rows. Today only home teleports reach it -
   proven, a regeneration with the home file withheld produced 0 originless rows - but new upstream
   data with landing-only rows would silently become teleport-from-anywhere edges with no warning.
   Suggested fix: assert the originless row count against an expected set, or restrict the branch to
   files explicitly declared originless, so the failure is loud instead of silent.

3. **The A/B regeneration harness is not in the repo.**
   The proof that a generator change loses no pre-existing rows currently lives outside the project at
   `C:\Users\drews\verify-transport-regen.ps1`. It regenerates with and without a candidate input file
   and set-diffs every pre-existing row. Worth promoting into `tools/` so the check is repeatable by
   anyone rather than being a one-off C2 ran once.

4. **The generated TSV trips `git diff --check` on trailing tabs.**
   Blank trailing requirement fields are written as tabs by the fixed-column writer. A generator-side
   trim was written and then reverted during the home-teleport slice, because it expanded the
   generated-resource diff far past the scope of that change. Not a defect, and the Java loader
   tolerates either shape (`split("\t", -1)`, first 4 columns required). Revisit as its own isolated
   commit so the whole-file reformat is reviewable on its own.

5. **Duration-weighted transports bypass the A\* tie-refinement pass.**
   `DrewsHelperWalkingRouteEngine.java` lines 486 and 503 gate and compare on `path.size() - 1`, a
   step count, against `MAX_A_STAR_TIE_REFINEMENT_DISTANCE = 256` (line 16), while transport edges are
   priced by duration. A route whose cost is dominated by transport duration therefore does not get
   ranked. Preserved deliberately during the home-teleport slice rather than refactored mid-feature.

6. **D-0101 still reads "Not yet fixed" even though D-0102 supersedes it.**
   `DECISION_LOG.md` line 1079 says the Wilderness scope change is "Not yet fixed - the scope change is
   a decision for Mytharium", and D-0102 at line 1082 records that avoidance shipped and D-0101 is
   superseded. A reader landing on D-0101 first is not told that. Fix is a one-line forward pointer in
   D-0101; the decision itself is not being reopened.

7. **Home-teleport cooldown clears one minute late at the exact boundary.**
   `DrewsHelperPlayerCapability.java` line 295 evaluates `@` terms as
   `currentEpochMinute - actual > operand`, so a 30-minute cooldown only unlocks once 31 minutes
   have elapsed. `DrewsHelperPlayerCapabilityTest.java` line 195 pins this deliberately - "the
   handoff rule is strictly greater than the cooldown minutes". It errs in the conservative
   direction and matches the standing "a false teleport offer is worse than under-offering" rule,
   and because the stored value is minute-granular, `>=` could offer a teleport up to a minute
   early. Logged so the choice is explicit rather than accidental; no change made.

8. ~~**Agent backup files were committed into the repo.**~~ RESOLVED 2026-08-10, committed in `3b0f922`.
   Commit `8aed260` swept in roughly fifteen `.pre-*` snapshots alongside the real changes -
   `DECISION_LOG.pre-d0086.md`, `02_NEXT_WORK.md.pre-teleport-plan-20260809` and similar. These
   are working scratch taken before each edit and are meant to be transient, not repo content;
   several are near-complete duplicates of large guides, so they inflate the tree and will show
   up in future diffs and searches. Suggested cleanup: `git rm --cached` them and add a
   `*.pre-*` ignore rule. Myth approved deleting them, C2 removed them, and Myth committed the
   cleanup. Verified after commit: `git ls-files '*.pre-*'` and a worktree scan both return 0.

9. **The `varIds()` comment omits the `@` cooldown operator.**
   `DrewsHelperTransportGraph.java` line 163 states that terms look like `id=value`, `id>value`,
   `id<value` or `id&mask`; cooldown terms `id@minutes` are missing from that list. The code itself
   is correct and operator-agnostic - it consumes leading digits and stops at the first non-digit,
   so `892@30` yields `892` without `@` needing to appear anywhere - but the comment could convince
   a future reader that cooldown vars are never snapshotted for the live client. Comment only, no
   behaviour change.

10. ~~**Home teleports are refused everywhere in the Wilderness, but the game allows them to level 20.**~~ RESOLVED 2026-08-09 - the cap is carried through and enforced; see changelog D-0110.
    Upstream carries a `Wilderness level` column - `teleportation_spells_home.tsv` gives every
    Lumbridge row the value `20`, and upstream models the thresholds in
    `AbstractNodeKind.fromWildernessLevel` with buckets at >0, >20 and >30. Our generator drops
    that column entirely: the generated resource has 10 columns and no wilderness level, which is
    why `originlessTransportAllowed` falls back to the blanket `!isInWilderness(from)`. The guard
    is deliberately conservative and was the right call while the data was missing, but it now
    under-offers a teleport the player could legally cast anywhere in levels 1-20.
    Fix: carry the column through the generator as an 11th field, then compare the player's
    wilderness level against the edge's maximum instead of testing mere presence in the box.

11. **A multi-waypoint route can offer the same one-shot teleport on more than one leg.**
    Cooldowns are filtered once, when the graph is built from the capability snapshot, so a
    teleport that is available at solve time is available to every leg of that solve. The engine
    tracks no consumed-transport state across legs - a grep for consumed/used/spent in
    `DrewsHelperWalkingRouteEngine.java` matches nothing but an unrelated comment. Two long legs
    could therefore both be told to cast the same 30-minute home teleport. Not observed live yet;
    Myth's two-waypoint test happened to pick a spirit tree for leg 2. Fix would be forward
    simulation of one-shot transports across legs, which is a real design change, not a patch.

12. **Upstream transport data misses real shortcuts, and nothing detects that but stumbling on one.**
    The Taverley gate was the case in point: the gate exists in game and blocks movement, and the
    collision map was right about it - what was missing was a transport ROW letting a route pass
    through. `tools/transport-overrides.tsv` already exists for exactly this and already carries
    the fix, so the mechanism is fine. The gap is discovery: today a missing shortcut is only
    found when a route looks wrong and someone investigates.
    Proposal, in preference order:
    - **Detector, recommended.** Both this project and upstream already read the live scene
      (`client.getScene().getTiles()` here, `getTopLevelWorldView().getScene().getTiles()`
      upstream). A debug mode could walk the loaded scene, pick out objects whose menu actions
      read Open / Pass / Climb / Enter / Squeeze, and report any that have no matching row in the
      shipped transport data. That turns "something felt missing" into a tile-and-id list, and the
      output feeds straight into the overrides file. Bounded, and built on an API already in use.
    - **Do not rebuild the collision map.** Decoding the game cache ourselves would be complete and
      authoritative but is a large standalone project, needs redoing after game updates, and would
      not have caught the Taverley case at all - the collision data was already correct there.
    - Note: a live collision-flag API was NOT confirmed to exist in the RuneLite version in use.
      Nothing in either project calls one; upstream's `CollisionMap` wraps its own packed
      `SplitFlagMap` loaded from `collision-map.zip`. Verify before designing around it.
    - **The wiki is not a usable source for this.** Checked the OSRS wiki Gate page directly: it is
      a disambiguation page listing roughly a dozen notable gates by name, with no coordinates, no
      object ids, and no entry for the Falador west wall gate at (2935, 3450) that started all of
      this. Scraping it would not have found our missing row.
    - **Better: an offline chokepoint scan, no walking required.** We already ship upstream's
      whole-world `collision-map.zip`. The Falador gate was found as a one-tile-thick blocked
      crossing separating two otherwise-connected regions with no transport row through it - which
      is a shape that can be searched for offline across the entire map. That yields a candidate
      list; Myth then only visits those specific tiles to confirm and record the object id, rather
      than walking the world. The live-scene detector becomes a second pass, not the primary one.
    - **Design confirmed against the real API (2026-08-10).** `DrewsHelperCollisionMap` exposes
      `canMoveNorth/South/East/West(x, y, plane)` plus diagonals, over 64x64 regions held in a
      map keyed by region position. That is everything a whole-map scan needs, offline, with no
      client running and no walking.
    - Algorithm, three phases:
      1. **Enumerate.** Walk every tile of every region present in `collision-map.zip`. Only
         regions that exist are scanned, so this is bounded by real map coverage, not 4096^2.
      2. **Find blocked seams.** Keep adjacent cardinal pairs (A,B) where movement A->B is blocked
         but BOTH tiles are otherwise open - each has at least one other legal move. Solid rock and
         map edges fall out here; what survives is walls, doors, gates and fences.
      3. **Score the detour.** For each seam, BFS from A with a step cap (start at 40). If B is not
         reached inside the cap, the seam is a genuine chokepoint. Rank by detour length; the
         Falador gate was exactly this shape - a one-tile seam with a very long way round.
    - Then subtract every seam already covered by a transport row. What is left is the candidate
      list, WITH coordinates. Only then does Myth visit anything, and only the ranked candidates.
    - Order matters: the scan comes first and produces the list. Myth has nothing to check until
      it has run.
    - **mapgenie is not a usable source.** It is an interactive commercial map whose markers come
      from its own API rather than the page, its access points are hand-placed by editors (so it
      is the same class of possibly-incomplete third-party data we are trying to stop depending
      on), and scraping it is a licensing question we do not need to have. Our own collision map
      is authoritative for "is this blocked" and we already ship it.
    - **Measured accuracy of `collision-map.zip` (2026-08-10), because "is it accurate" is the
      right question to ask before building on it.** 1,524 regions, spanning region x 18..61 and
      y 19..196. Landmark spot checks all present: Falador west gate 45_53, Taverley door 45_54,
      Lumbridge 50_50, Varrock 50_53, Wilderness ditch 48_55, Kourend 25_57. Combined with the
      earlier decode check (Taverley door at 2900,3473 blocks eastward movement exactly as the
      transport data requires), the map is trustworthy WHERE IT HAS DATA.
    - **The real gap is coverage, not correctness.** Of 24,792 transport endpoints in the shipped
      resource, 1,567 land in regions the collision map does not contain: 1,403 overworld
      endpoints across 38 distinct regions (mostly region x 18-25, the Zeah/Kourend side), and 164
      instanced endpoints across 9. A missing region is treated as fully impassable, so this fails
      SAFE - routes into those areas fail rather than route wrongly.
    - **Consequence the scanner MUST handle:** since a missing region reads as solid, every border
      between a present and a missing region would look like one enormous wall. Seams where either
      side lies in a region we do not have have to be skipped, or the output is drowned in
      thousands of false chokepoints.
    - **Step plan, in the order it gets done.**
      - Phase 0, trust: build a fixture list of ~10 gates/doors known to exist WITH coordinates
        (fixture #1 is the Falador west wall gate at 2935,3450) and require the scanner to find
        every one. That is the scanner's acceptance test - no fixture pass, no output trusted.
      - Phase 1.1: enumerate every tile of every region present in the zip.
      - Phase 1.2: keep adjacent cardinal pairs (A,B) where A->B is blocked, BOTH tiles have at
        least one other legal move (so both are walkable, not solid rock), and BOTH lie in regions
        we have.
      - Phase 1.3: BFS from A with a step cap (start at 40). B not reached inside the cap means a
        genuine chokepoint. Record the detour length as the rank.
      - Phase 1.4: drop seams already covered by a transport row (endpoint within 1 tile).
      - Phase 1.5: cluster adjacent seams - a two-tile gate is two seams and must report once.
      - Phase 1.6: emit ranked CSV - x, y, plane, blocked direction, detour cost.
      - Phase 2, and ONLY now does Myth do anything: he visits the top N ranked candidates, and
        for each reports the object name, its left-click action (Open/Climb/Pass/Enter/Squeeze),
        and whether it can actually be passed. Object name plus coordinates is enough - the id can
        be resolved from those, so no dev-tools inspector is required.
      - Phase 3: freeze every confirmed find as a test fixture so a future data change cannot
        silently drop it again.
    - Trip count for Myth is NOT knowable until Phase 1 has run. It is bounded by ranking - only
      the worst N candidates get sent - but promising a number before the scan would be invented.
    - **MEASURED 2026-08-10 with a throwaway prototype, and it changes the plan.** A real seam
      scan was run over all 1,524 regions on plane 0 using the shipped map:
      - walkable tiles: 2,611,645
      - BLOCKED SEAMS: 63,602 - of which only 2,340 are already covered by a transport row,
        leaving 61,262 uncovered.
      - BFS detour filter at a 40-step cap, per region: Lumbridge 360 seams -> 72 chokepoints
        (62 uncovered), Varrock 443 -> 139 (107), Falador 192 -> 55 (44), Draynor 153 -> 61 (54).
      - Extrapolated globally that is roughly 14,500 uncovered chokepoints. Nobody is visiting
        14,500 places. The "ranked list Myth walks to" version of this plan does not survive
        contact with the data.
    - **Phase 0 fixture #1 PASSES, so the geometric approach itself is sound.** The Falador west
      wall gate is found: `SEAM 2935,3450 -> 2936,3450` and `2935,3451 -> 2936,3451`, both with a
      detour of more than 400 steps. The wall column at x 2935 is blocked east for y 3448..3452,
      and only y 3450 and 3451 have an open tile on the far side - that two-tile gap IS the gate.
    - **A run-length filter would be WRONG and must not be used.** The obvious idea - discard
      seams that sit in a collinear run because runs are walls - would discard the Falador gate,
      which is itself a run of two. Measured isolated-vs-run split on the four regions:
      Lumbridge 13/59, Varrock 24/115, Falador 13/42, Draynor 5/56. Tempting, and wrong.
    - **Detour MAGNITUDE is the real discriminator.** A house wall has its door a few tiles away
      so the detour is small; the missing Falador gate had a detour beyond 400 steps. Re-ranking
      by detour with a much larger cap is what should separate genuine missing entrances from
      ordinary walls. NOT yet measured - do that before promising any list size.
    - **The collision map alone can never tell a wall from a shut door.** It stores blocked/open,
      not object identity. That is the ceiling on any purely geometric detector, and it is why
      the next step is object identity rather than a better geometry filter.

13. **"Use: Wilderness Transports" does not mean what it says.**
    The `WILDERNESS` category holds 331 rows and nothing else: 324 obelisk destinations and 7
    `Pull Lever`. Everything else that lives in or crosses the Wilderness is `BASELINE` - the 668
    `Cross Wilderness Ditch` rows, the whole Abyss chain (`Teleport Mage of Zamorak 2581` at
    3106,3559, `Enter Passage`, `Operate Appendage 27027` landing at Lumbridge), and roughly 2,060
    edges with both ends inside. So the toggle never gated any of it; what actually keeps routes
    out of the Wilderness is the avoidance rule, not that checkbox.
    Decide which: recategorise those rows so the toggle owns them, or rename the toggle to say
    what it really controls. Recategorising changes routing behaviour for anyone who has it on, so
    it is not a rename-level change.
    **The Abyss specifically does NOT need adding (checked 2026-08-10).** The OSRS wiki is explicit:
    "While the run to the Mage of Zamorak is in a PVP area, the Abyss itself is not." The Abyss sits
    in Abyssal Space around y 4800, its outer ring is multicombat against NPCs rather than players,
    and it is not Wilderness. The dangerous half of that route is the walk to the Mage of Zamorak at
    3106,3559 - which is already inside the Wilderness box and is already what D-0112 refuses. So
    the Abyss route is correctly blocked today without touching the box, and widening the box to
    include Abyssal Space would mislabel a non-PvP area as Wilderness.
    **Correction to how this item was first written (2026-08-10).** It said the Wilderness toggle
    "was never involved". That is only half true and the wrong half was stated too strongly. The
    checkbox drives TWO separate mechanisms: the `WILDERNESS` category filter at graph-build time
    (obelisks and levers only - that part was right), and ALSO the router's avoidance rule, via
    `DrewsHelperPlugin:729` passing `!transportPolicy.allowsWilderness()` in as `avoidWilderness`.
    So the toggle does govern the ditch and the Abyss chain, through avoidance rather than through
    the category. That is why D-0114 could be fixed entirely inside the avoidance rule and needed
    no recategorisation. Recategorising is now a tidiness question, not a behaviour gap.

15. **Building our own collision/object map - both routes verified available (2026-08-10).**
    Myth asked how we would build our own rather than trusting upstream. Two routes exist and
    the blocking uncertainty from the earlier note is now resolved.
    - **Route A, live harvest (recommended).** `Client.getCollisionMaps()` DOES exist in the
      RuneLite version in use (1.12.35), alongside `CollisionData` and `CollisionDataFlag`. This
      supersedes the earlier caveat that no live collision API could be confirmed - that was
      unverified at the time and is now checked directly against the api jar. Those flags are the
      game engine's own, for the loaded scene, so they are ground truth rather than a rebuild.
      `Tile.getWallObject()`, `getGameObjects()`, `getDecorativeObject()` and `getGroundObject()`
      all exist too, AND the project already calls them at `DrewsHelperRouteTileOverlay.java:234,
      246, 312` - so object identity needs no new API and carries no version risk.
      This is the route that answers "what am I looking for": nothing. Harvest passively while
      Myth plays, record blocked seams together with the object sitting on them, and report only
      seams that have an openable object and no transport row. Coverage follows wherever he
      actually plays, which is exactly where a missing route costs him something.
    - **Route B, offline from the game cache.** The cache IS on the machine, at
      `C:\Users\drews\.runelite\jagexcache`. Decoding it yields the whole map at once with no
      playing. Much larger job: needs the map index plus object definitions, and OSRS map
      location archives are believed to be XTEA-encrypted - VERIFY that before planning around
      it, do not assume either way. Also needs redoing after game updates.
    - Route A is strictly better as a first step: smaller, uses APIs already in the codebase, and
      produces the one thing the shipped collision map structurally cannot - object identity.
    - **REVISED 2026-08-10 after Myth asked "can't we do B then A for game updates".** He is right
      about the order, and Route B is a great deal more viable than the line above implies. What
      changed is evidence, not opinion:
      - `net.runelite:cache` is ALREADY a declared dependency of the sibling upstream project
        (`Drew Shortest Path/build.gradle:32`, testImplementation) and the jar is already on disk
        at `.gradle\caches\...\net.runelite\cache\1.12.35\...\cache-1.12.35.jar` (434 KB). Nothing
        needs downloading or writing from scratch.
      - Nothing upstream actually IMPORTS it - a source-wide grep for `net.runelite.cache`,
        `RegionLoader`, `ObjectManager` and friends returns zero hits. So upstream does NOT ship a
        collision-map generator; `collision-map.zip` came from Runemoro pre-built. That is exactly
        why it can be stale or incomplete and we cannot fix it in place.
      - Upstream's `README.md:32` points at `github.com/osrs-pathfinding/shortest-path-tooling`
        for "developer dashboards and OSRS cache dumpers". A purpose-built public toolchain for
        this job already exists - check it before writing a decoder.
      - Myth's cache is complete and current: `.runelite\jagexcache\oldschool\LIVE\`,
        `main_file_cache.dat2` at 215.78 MB plus idx0..idx20, all written the same day.
    - **What Route B actually yields, and it is more than collision.** The jar contains
      `RegionLoader` / `Region` / `Location`, `LocationsLoader`, `MapLoader` / `MapDefinition`,
      AND `ObjectManager` / `ObjectDefinition`. Object definitions carry the NAME and the ACTION
      list. Combined with locations (which object sits on which tile) that generates the whole-map
      transport candidate list automatically - every object whose action reads Open / Climb / Pass
      / Enter / Squeeze, with coordinates and ids. That is the thing that turns the 63,602 blocked
      seams from D-0115 into an answer, and it removes the "Myth visits places" step entirely.
    - **Known blocker, and it is the first thing to settle in the slice.** The jar ships
      `util/XteaKeyManager`, `util/XteaKey` and `util/Xtea`, and no key file exists anywhere on the
      machine (searched `.runelite`, both project trees). Read that as: map LOCATION archives need
      XTEA keys and we do not have them yet. Confirm empirically before committing to a design -
      do not assume in either direction. Note the split that matters: object DEFINITIONS (names,
      actions) live in the config index and are not encrypted; object PLACEMENTS live in the map
      index and are what needs keys. Keys are published openly and are what `XteaKeyManager`
      consumes - that is a key file, NOT a curated third-party dataset, so it does not reintroduce
      the mapgenie problem. The map data still comes from Myth's own cache.
    - **Correction to the sequencing Myth proposed: A is NOT the update mechanism, B is.** On a
      game update the cache refreshes and B is re-run to regenerate everything - one command, not
      a re-harvest. Route A's real job is VALIDATOR: compare what B generated against what the
      live client reports via `Client.getCollisionMaps()` and `Tile.getWallObject()`, and flag
      disagreements. That is how the Falador gate would have surfaced as a data bug instead of as
      a routing symptom weeks later. A also covers what B cannot: instanced content, and anything
      the decode gets wrong.
    - Agreed order therefore: **B builds it, A checks it, B refreshes it.**
    - **SHIPPED 2026-08-10. Both A and B are built, and the XTEA question is settled: keys are
      NOT required.** Probe result against the live cache with an all-zero key provider (a zero
      key means "do not decrypt", so anything that parses is genuinely unencrypted):
      terrain ok=2747 threw=0, object placements ok=2747 threw=0, 4,829,650 placements decoded.
      That removes the only blocker and means no key file, no third-party dataset, nothing but
      Myth's own cache.
    - **Route B: `gradlew.bat dumpAccessPoints`.** Lives in its own `cachetools` source set so
      `net.runelite:cache` is a build-time tool only and never reaches the shipped jar or the
      test suite. Reads the cache, cross-references our transport TSV, writes
      `tools/cache-access-points.tsv` plus a summary. First run:
      62,401 object definitions (5,122 with a movement action), 4,980,697 placements scanned,
      2,936 regions in the cache against 1,524 in our shipped collision map - **1,425 regions the
      cache has that we do not**, which is the Zeah coverage hole from D-0114 explained.
      14,048 openable placements found, 2,438 already covered, 11,610 not.
    - **Acceptance fixture PASSES cold.** The Falador west wall gate is found by name and id
      without being told where to look: `2935,3450 id=1728 Gate action=Open` and
      `2935,3451 id=1727 Gate action=Open`. World-coordinate maths and object filtering are both
      confirmed correct by that. Region-local positions plus region base is the right conversion;
      `LocationsDefinition` holds LOCAL coordinates, not world.
    - **Route A: `DrewsHelperMapValidator` plus the `Validate Map Data` config toggle**, off by
      default, hooked into `onGameTick` but throttled to once per scene (the scene only changes
      on a region boundary; re-diffing ten thousand edges every 600ms would be waste). It diffs
      `client.getCollisionMaps()` against our shipped map and logs `DREW_MAP_VALIDATE` lines,
      listing only the we-block-but-the-game-allows half because that is the Falador-gate shape
      and the half that becomes an override row. A wholly absent region is reported once as a
      coverage hole rather than as ten thousand bogus mismatches - that guard matters because
      there are 1,425 such regions.
    - Five unit tests cover the validator: both mismatch directions, the blocked-destination
      rule, the coverage-hole guard, and silence on agreement. Suite 160 -> 165, 0 failures.

16. ~~**Route B's movement-verb filter is too loose - refine before trusting the 11,610.**~~
    **CLOSED 2026-08-10 (D-0119).** Fixed, but not the way this item predicted - the predicted
    fix does not work. See the new item 18 for the small remaining tail.

    Matching on "Open" also catches containers: the uncovered breakdown is led by 1,793 Door and
    1,478 Ladder (both real) but also 640 Chest, 226 Drawers, 197 Closed chest and 145 Wardrobe,
    which are not movement at all. So 11,610 is an UPPER BOUND, not a gap count. The genuinely
    interesting rows are Door, Gate (353), Gap (78), Staircase (583), Stairs (573), Ladder and
    Wilderness Ditch (192). Fix by intersecting the verb match with the object actually blocking
    movement - `ObjectDefinition` carries `getInteractType()`, `getBlockingMask()` and
    `getWallOrDoor()`, none of which are used yet. A chest blocks nothing you would path through.

17. **Decide whether the generated dump belongs in git.**
    `tools/cache-access-points.tsv` is 638 KB and regenerates from the cache on demand, so it is
    a build artifact rather than a source. Either gitignore it or commit it deliberately as a
    snapshot - but do not let it drift in uncommitted, which is how the earlier untracked
    `transport-overrides.tsv` problem started.

18. **Small tail the passage filter still drops (2026-08-10, ~11 rows).**
    The wall-placement + name-hint rule is right for the bulk, but three passage-ish names
    carry an ambiguous verb on a non-wall placement and do not match any current name hint:
    `Manhole` (6), `Cave` (3), `Tomb exit` (2).
    `manhole` is a safe one-word addition to `PASSAGE_NAME_HINTS`. `cave` and `exit` are NOT
    obviously safe - they are generic enough to risk re-admitting scenery, and the whole point
    of that list is that it stays short and auditable. Decide deliberately rather than widening
    it by reflex; the dumper prints everything the hints rescue, so any addition is checkable.

19. ~~**Cache-derived transport rows need a stronger acceptance filter before merge.**~~ RESOLVED 2026-08-10 (filter/proof gate built; #2 still needs live proof before active rows move).
    `AccessPointRowGenerator` produced 1,282 candidate wall crossings / 2,564 bidirectional rows,
    but the validation control is only 65.0% predicted-edge blocked versus 40.1% perpendicular-edge
    blocked. Orientation is real signal, not enough certainty. Next pass should rank by detour
    severity, remove obvious instance/minigame scenery names, and use Route A live-client mismatch
    proof before moving any row into `transport-overrides.tsv`. Implemented by D-0121: exact junk
    filter, 512-step detour ranking, review/proven output split, and optional Route A proof parser.

24. **v2 false-open edges - the safety-axis regression that rode along with the win (2026-08-12).**
    Measured against the live client capture `drews-live-flags.POST_20260812.txt` (scene 2888:3192:0,
    4,048 rows, plane 0, x2888-2990 y3192-3294): edges where the map says OPEN but the live client
    says BLOCKED rose from 132 (v1) to 1,735 (v2). Overall map-vs-client agreement still improved
    67.98% -> 82.97% on that same capture, so this is a net-positive change with a real regression
    riding along - not a rollback signal. It is still worth a targeted pass later, because the win
    sits on the over-blocking axis while the cost sits on the safety axis, and only the second one
    can walk a player into scenery. Parked deliberately: do not fix this mid-slice.

25. **Sealed-off walkable pockets cause false NO_PATH (2026-08-12).**
    v2 opens terrain the live client agrees is walkable, but leaves a residual barrier ring around
    it, so the route engine returns `NO_PATH` for any destination inside those pockets. Two known
    pockets: roughly 4,375 tiles around (2886,3252) and roughly 1,100 tiles around (3064,3201).
    The opened tiles themselves are correct; the ring enclosing them is not. Parked, not fixed.

26. **Coverage gap: region `47_50` and most rebuilt planes have no live-client ground truth (2026-08-12).**
    Region `47_50` (Port Sarim waterfront, x3040-3071 y3200-3239) has ZERO live-client ground truth -
    the capture stops at x2990. Its "unreachable pocket" status is inferred from the route engine
    only and has never been confirmed against the client. Planes 1-3, and 21 of the 24 rebuilt
    regions, likewise have no live data. Anything asserted about those regions is route-engine
    inference rather than measurement, and should be quoted that way until a capture covers them.

27. **Cross-region seam drops S/W edges (2026-08-12).**
    `markSolidAllEdges` reaches its neighbours through `edgeIfInside`, which cannot clear a flag
    whose storage cell lives in the NEIGHBOURING region, so a terrain-blocked tile sitting on a
    region boundary leaks exactly one edge. Measured: 18 wrongly-passable edges inside the 24
    rebuilt regions, 24 across all 64 captured regions - and every single one is at `y%64==0`
    (south) or `x%64==0` (west), with zero unexplained. Needs cross-region edge reconciliation at
    write time, after all regions are built. This is NOT a terrain-rule problem - D-0169 measured
    the rule itself as correct. Parked, not fixed.

28. **C1's 526 false positives are the likely source of parked item 24 (2026-08-12).**
    The verified terrain rule (D-0169) still over-blocks 526 tiles: cache says blocked, live
    client says walkable. Breakdown - 484 on plane 0, 516 have `tileSetting[plane] == 1`, only 3
    are bridge-flagged and only 9 sit on a region seam. That is a 0.32% over-block rate. Note the
    direction: this is the OPPOSITE of the reported ocean symptom, and it is the more plausible
    cause of the "OPEN-but-live-BLOCKED edges 132 -> 1,735" regression recorded in item 24 above.
    Work item 24 from this angle first. Parked, not fixed.

29. **Legacy (non-rebuilt) regions are the real weak spot (2026-08-12).**
    147 of the 171 unexplained floor leaks sit in 2021-vintage Runemoro entries that the v2
    rebuild has never touched. They concentrate in `52_50` (101 leaks) and `52_51` (36) - Al
    Kharid east - followed by `51_154`, `50_49` and `50_48`. EXTENDING THE REBUILD TO THOSE
    REGIONS WILL BUY FAR MORE THAN ANY TERRAIN-RULE CHANGE: the v2 rebuild is measurably better
    than the v1 data sitting next to it. Highest-value follow-up of the three. Parked, not fixed.

30. **Sailing-aware routing - category ready, active edges NOT built (updated D-0195).**
    Requested by Mytharium: route ocean destinations by walking to a boat and sailing, and show a
    "Requirements:" message when Sailing is not unlocked.
    - **OSRS Sailing is FREE-ROAM STEERING** (click-to-steer, no autopilot, no port-to-port menu)
      and Jagex explicitly rejected boat collision. Released 19 Nov 2025, members only, quest
      gate Pandemonium. Boats run roughly 0.5-1x running speed, so this is transport, not a
      teleport.
    - **But the travel endpoints are a fixed set: 57 docking/mooring points, Sailing levels 1 to
      87, NOT boostable.** Sailing can therefore be modelled as dock-to-dock transport edges with
      no water collision map and no second movement mode. Because collision was removed, a
      straight-line approximation is close to the real path. This is what collapses the job from
      large to small.
    - **Upstream `Skretzo/shortest-path` has NO sailing transport data** - no `SAILING` in its
      TransportType enum, no dock/mooring rows, and an explicit source comment at
      `PathfinderConfig.java:665-668`: "We don't model sailing navigation." Tracking issue #351
      is unassigned with no activity since 2026-05-11 and the author states he does not intend to
      start soon. DO NOT WAIT FOR UPSTREAM. (Runemoro/shortest-path is dead - last push
      2024-07-26. Skretzo is canonical.)
    - **Upstream DOES already have, merged 2026-05-24 - do not double-add:** 5 island rowboat
      pairs in `boats.tsv` gated on construction varbits 18351/18355/18356/18370/18371; Port
      Sarim and Musa Point to The Pandemonium in `ships.tsv` gated on the Pandemonium quest;
      Sailors' amulet 3 teleports; sailing-island bank chests; and PR #485, which suppresses
      teleports while aboard a boat.
    - **The data is published and machine-readable - nobody needs to visit a dock in game.** OSRS
      Wiki `Mooring point` (`?action=raw`) yields 57 rows, all with `{{Map|...|x,y}}`
      coordinates, Sailing level and quest gates. `Map:Sailing` is contentmodel json, 147 KB, 525
      markers across 34 categories with positions and level requirements. `nucleon/port-tasks`
      (GitHub, BSD-2-Clause, the same licence as upstream) has 31 ports with level + gangplank
      ObjectID + WorldPoint, plus 164 pre-authored port-pair routes and 1,673 waypoints. RuneLite
      `ObjectID1` publishes 32 `SAILING_GANGPLANK_*` constants (59835-59866) and 27
      `SAILING_MOORING_*` (59867-59893). Cross-validated: on all 26 ports present in both
      port-tasks and the wiki the Sailing levels match EXACTLY; coordinates differ by 1-7 tiles
      (map pin versus nav waypoint).
    - **Our schema already supports it with no new columns.** The `skills` column format is
      `Name=level` joined by `;`, compared against UNBOOSTED real levels - which matches the
      game's not-boostable rule exactly. `SAILING("Sailing", true)` exists in RuneLite's `Skill`
      enum. Unknown transport categories are skipped rather than fatal, so `SAILING` rows can
      ship and older builds ignore them.
    - **D-0195 closed the one-line trap:** `SAILING` is now a transport category,
      `DrewsHelperTransportPolicy` always enables it, `DrewsHelperTravelEstimate` labels it, and
      `Skill.SAILING` is route-cache relevant. Future `SAILING` rows will therefore be gated and
      refreshed correctly.
    - **Not published anywhere:** the exact walkable interaction tile per dock (wiki pins and
      port-tasks nav points disagree by 1-7 tiles); edge topology, because free-roam means
      effectively all-pairs at 57x56 = 3,192 and port-tasks only authored the 164 pairs its own
      tasks needed; and per-leg duration, which is genuinely variable by hull/sail/wind and is
      unsolved upstream too (issue #370).
    - Next safe implementation step: verify walkable gangplank/dock interaction tiles, then add
      data-driven `SAILING` rows. A true on-water path overlay would be weeks and is explicitly
      NOT recommended. Parked, no active rows shipped.

31. ~~**"Requirements:" message needs the near-miss retained (2026-08-12).**~~ CLOSED, D-0195.
    The engine now keeps the near-miss by running a second same-policy unrestricted diagnostic solve
    when the normal filtered solve returns NO_PATH. If that diagnostic path uses edges blocked by
    account capability, `DrewsHelperRouteSnapshot` carries player-facing requirement lines and
    `DrewsHelperOverlay` renders them as a separate `Requirements` block below the waypoint/action
    display. Example lines: `Agility = 90`, `Sailing = 67`, `Mith grapple = 1`, `Quest: The Grand
    Tree`, or a var/cooldown line when the route data exposes only var metadata.

32. **The level and route-leg records are temporary instrumentation (2026-08-12).**
    `writePlayerLevelsIfChanged` appends real (unboosted) levels to `drews-player-levels.txt`
    on change, and `writeRouteLegsIfChanged` appends the transport hops of the live route to
    `drews-route-legs.txt`. Both run unconditionally rather than behind the Validate Map Data
    toggle, which is deliberate: it is what let the Falador wall work resolve the requirements
    gate from stored state instead of asking. Neither is a feature. **Remove both before this
    plugin is called finished.** Agreed with Mytharium 2026-08-12. Parked, not built.

33. ~~**Furniture needs its own blocking rule (2026-08-12).**~~ RESOLVED FIRST TWO SLICES
    2026-08-13 by D-0142 and D-0147. The original report was the Ardougne mansion chair at
    `2573,3245,0`: dropping old Phase 2 reopened the chair, but restoring Phase 2 would reseal the
    Ruins of Unkah ferry beach. The replacement shipped as measured object-id/locType profiles, not
    a broad locType rule. First slice: `595/10 Table`, `1104/10 Bench`, `1088/10 Chair`,
    `1088/11 Chair` in commit `f66d4b8`. Second slice: 18 additional non-tree scenery profiles in
    commit `7b42c6a`.
    Live verification from Mytharium: waypoint `2573,3245,0` now snaps to `2572,3245,0`, and the
    Ruins of Unkah pier/beach remains walkable. The general furniture item is closed. Remaining
    object-profile work is now split into narrower items: route-display fidelity around object
    blockers, trees/tree-stumps as a separate proof batch, and later paid profiles such as hedges,
    stools, shelves and crates.

34. **Displayed route line can cut through trees while the player path avoids them (2026-08-13).**
    Found during the final live check of `7b42c6a`. Test route `2942,3243,0 -> 2951,3208,0` did
    not make the player walk through trees, which means the actual route/collision path is probably
    correct. But the displayed route still appeared to go through the trees. Treat this as a
    route-rendering fidelity issue until proven otherwise: inspect whether the map/world overlay is
    drawing a coarse segment, smoothing/skipping intermediate path tiles, or showing a different
    route representation than the tile path the player follows. Do not respond by adding tree
    blockers blindly; trees were already held back because they moved a pinned route fork.

35. **Trees and tree-stumps need a separate object-profile proof pass (2026-08-13).** The no-cost
    ranker made trees look attractive, but the trial moved the pinned Falador southeast live-route
    fork. They are therefore not part of the shipped 22-profile blocker. A future tree pass needs
    its own frozen A/B, route-aware overblock check, the Falador southeast route pin, and the
    display-fidelity check from item 34 before any tree/tree-stump profile ships.

### Unconfirmed - status needs checking before acting

14. **Route-speed baseline before the heuristic change.** The prior recommendation was to bank a clean
   641-tile `DREW_ROUTE_BENCH` number on the current jar before shipping the teleport-aware heuristic,
   so a speed comparison is not measuring two changes at once. Whether that baseline was ever captured
   is unverified - confirm with Myth before treating it as outstanding.

Everything below this active handoff is older project/history context. Use it only when it still matches the current Drew-owned route model.

## Drew's Shortest Path Build Plan

Goal: Drew's Helper should own Shortest Path-style routing as one integrated feature. There should be one visible RuneLite plugin, `Drew's Helper`, with Drew's Shortest Path inside it.

Phases:
1. Collapse the architecture: remove the separate visible path plugin seam, load only `Drew's Helper`, and start the vendored route engine internally.
2. Own the core route feature: route target state, world-map right-click destination, shift-right-click tile destination, clear route control, and route drawing on map/minimap/ground/HUD.
3. Integrate locked teleport state: feed Drew's Teleport Options and scanned minigame statuses into the solver, block exact keys such as `teleportation_minigames:nightmare_zone`, and surface unreachable/blocked-route warnings.
4. Merge config parity: keep guidance controls in Teleport Options, expose Drew-owned transport unlocks under Basic Transportation / Advanced Transportation / Other Transportation, add remaining route-specific controls under Routing Options, and keep the inherited `ShortestPathConfig` panel hidden/runtime-only.
5. Improve beyond stock Shortest Path: prefer known unlocked routes, explain rejected transports, support route quality modes, add quest-prep routes, use cooldown-aware rerouting, and show clearer route reasoning in the HUD.
6. Live validation: test without Plugin Hub Shortest Path installed, verify manual routes, Quest Helper routes, locked Nightmare Zone exclusion, other minigame teleport availability, and no route bouncing.

Current phase:
- Phase 1 is complete, build-verified, and dev-launch probe verified. `Drew's Helper` is the only visible plugin target, and `DrewsHelperPlugin` owns the internal route-engine lifecycle.
- The missing-plugin-list issue was a Guice construction cycle in the internal route overlays; `shortestpath.ShortestPathPlugin` now lazy-creates those overlays through providers after the route engine itself is constructed.
- Part of Phase 4 was pulled forward by Myth's UI direction: player-facing transport unlocks now belong to Drew's own `Basic Transportation`, `Advanced Transportation`, and `Other Transportation` sections, not the copied Shortest Path `Settings` bucket. Baseline travel networks with no meaningful account unlock are default-on internally instead of shown as `Unlocked: ...` toggles.
- The copied Shortest Path config surface is no longer ConfigManager-backed. The internal engine uses `DrewShortestPathInternalConfig`, and `ShortestPathPlugin` is marked hidden so the visible config should be Drew's Helper only.
- Manual right-click/shift-click route targets are now synced from the internal engine into `DrewsHelperSessionState`; route clear also clears the saved target/snapshot so stale routes are not replayed.
- Next coding phase is Phase 2: expose the core route controls through Drew's Helper and validate map/minimap/ground/HUD drawing from the single-plugin runtime.

## Priority 1: Live-Test Drew's Shortest Path Exact Rerouting

Goal: when `Hide Locked Teleports` is enabled and Drew's Shortest Path would choose a locked route, Drew should recalculate through the next best valid option: walking, boats, ships, spirit trees, fairy rings, another unlocked teleport, or another supported transport.

Current implementation status:
- Drew-side outbound support is implemented. Locked minigame statuses are converted into `blockedTransportKeys`, included in `ShortestPathBridge.buildConfigOverride`, and sent on normal route refresh/replay.
- Drew's Shortest Path is vendored directly into `Drews Helper` under `src/main/java/shortestpath/**` with resources under `src/main/resources/**`.
- `gradlew.bat run` loads only visible plugin `Drew's Helper`; `DrewsHelperPlugin` starts the vendored route engine internally.
- Drew's Shortest Path keeps the `shortestpath/path` and `shortestpath/transports` plugin-message namespace for Quest Helper compatibility and route telemetry.
- Drew's Shortest Path uses hidden runtime defaults for remaining inherited display/debug/threshold behavior. Add Drew-owned config items later only when Myth wants those controls visible.
- Drew's Helper now owns the transportation unlock menu shape:
  - Base Drew's Shortest Path transports: gates/passages, boats, ordinary ships/ferries, charter ships, magic carpets, minecarts, home teleports, teleport levers, fixed teleport portals, spellbook teleports, and minigame teleports are always enabled internally.
  - `Basic Transportation`: agility shortcuts, canoes, quetzals, gnome gliders, grapple shortcuts, magic mushtrees, and hot-air balloons.
  - `Advanced Transportation`: spirit trees, fairy rings, mounted glory, portal chamber, portal nexus tier, and jewelry box tier.
  - `Other Transportation`: standard/ancient/lunar/Arceuus/other tablets, 1-use items, teleport scrolls, achievement diary items, combat achievement items, skill capes, quest related items, and other items.
- Locked minigames are scanner-filtered by exact `blockedTransportKeys` while `Hide Locked Teleports` is enabled, even though Minigame Teleports are a base-on category. Turning that toggle off keeps the scan cache but stops sending blocked keys so the base solver can use those routes again.
- Config changes now mark the route policy dirty, clear stale HUD telemetry, and replay the saved/current target directly into the internal engine with Drew's current override. Targetless external `shortestpath/path` messages still refresh the internal engine's current path, but Drew-origin toggle refreshes do not rely on plugin-message subscriber ordering.
- Manual right-click/shift-click route targets are now immediately re-requested through Drew's override when observed, and the hidden internal config defaults `postTransports=true` so Drew's HUD receives transport telemetry even for manual routes created inside the internal engine.
- Drew's HUD/highlighter now receive transport snapshots through a direct internal listener from the route engine; legacy `shortestpath/transports` telemetry is still posted for compatibility. Stale/cancelled pathfinder completions are ignored, and duplicate pending route signatures are not restarted during refresh bursts.
- After comparing against Runemoro `shortest-path`, Drew's current policy is now installed inside the internal route engine before every pathfinder rebuild. Manual route creation, config refresh, and Quest Helper requests all rebuild under the same Drew override map instead of relying on a replay-after-the-fact correction.
- Drew's policy override must preserve the upstream visual layer. Every Drew override now forces `drawMap`, `drawMinimap`, `drawTiles`, `showTransportInfo`, and `postTransports` on so a stale hidden Shortest Path display setting cannot blank the map/tiles/HUD while the solver still owns the route.
- Cancelled or otherwise non-done pathfinder instances are not valid telemetry sources. If route rendering disappears after a policy refresh, check for a cancelled completion or stale hidden display config before adding another replay loop.
- `blockedTransportKeys` is emitted explicitly on every Drew override. With `Hide Locked Teleports` on it carries exact locked keys such as `teleportation_minigames:nightmare_zone`; with the toggle off it carries an empty list so stale blocked keys cannot survive in the static engine override map.
- Drew's HUD hides unavailable route transports from the main route step list while `Hide Locked Teleports` is enabled, but still shows them under `Locked Routes`.
- Minigame hint overlays now prefer the first available minigame route transport, so a locked Nightmare Zone hint should not remain active when an available minigame step such as Pest Control exists. When `Hide Locked Teleports` is off, cached locked minigames are still highlightable because the route policy is allowing them.
- Wiki comparison open decisions: whether to expose wilderness obelisks, POH fairy ring, POH spirit tree, and POH wilderness obelisk in Advanced/Other; and whether to add exact transport-item subtype filtering beyond the internal broad `useTeleportationItems` mode.
- Drew's Shortest Path consumes `config.blockedTransportKeys` directly and filters matching transports before path edges are built.
- The old active Plugin Hub `shortest-path_*.jar` was moved out of `.runelite\plugins` and backed up under `.runelite\plugins-c2-backups`.
- The broad stock-jar fallback (`useTeleportationMinigames=false` after exact keys fail) is retired for normal routing. Exact filtering should work or be debugged directly.

## Compatibility Protocol

Drew's Shortest Path intentionally accepts the same route message shape:

```text
namespace: shortestpath
name: path
data:
  start: <packed world point or WorldPoint>
  target: <packed world point or WorldPoint>
  config:
    postTransports: true
    blockedTransportKeys:
      - teleportation_minigames:nightmare_zone
      - teleportation_minigames:blast_furnace
```

Drew's Shortest Path solver behavior:
- Adds `ShortestPathPlugin.overrideStringSet("blockedTransportKeys")`.
- Stores the override set on `PathfinderConfig.refresh()`.
- Normalizes each `Transport` as `<transport_tsv_name>:<destination_slug>`, e.g. `teleportation_minigames:nightmare_zone`.
- Filters matching transports inside `useTransport(...)` before usable transport edges are built.
- Keeps category toggles working.
- Continues posting transport telemetry so Drew's overlay reflects the actual recalculated route.

Drew-side work completed:
- Convert locked minigame statuses into blocked transport keys.
- Include those keys in `ShortestPathBridge.buildConfigOverride`.
- When a posted route contains a locked route, replay the saved/current target with the blocked list.
- When a manual internal target is observed, immediately replay it through Drew's current route policy instead of waiting for the periodic transport-feed request.
- Merge active Drew policy into incoming external `shortestpath/path` messages before the internal route engine consumes them, including config-only messages without a target. Use direct internal route-engine calls for Drew-origin refreshes and reroutes.
- Do not replay from `shortestpath/transports` destinations. Those are intermediate transport steps, not the final route target.
- Tests cover blocked-key sending, override parsing, and minigame transport-key generation.

## Test Path

- Fully close normal RuneLite.
- From `C:\Users\drews\OneDrive\Documents\My Games\RuneScape\Drews Helper`, run `run-drews-helper-dev.bat` or `gradlew.bat run`.
- Do not use the normal RuneLite shortcut for this test. The normal launcher cannot see Drew's local source plugin.
- Confirm only `Drew's Helper` is enabled from this project; there should be no separate `Drew Path` plugin entry.
- Confirm Plugin Hub Shortest Path is not enabled and no active `shortest-path_*.jar` is in `C:\Users\drews\.runelite\plugins`.
- Turn on Drew's Helper `Route Diagnostics` before setting the route. In the dev launcher path, `run-drews-helper-dev.bat` captures Gradle/RuneLite console output into `logs\drews-helper-dev-*.log`; the collector reads the newest captured dev log automatically.
- In Drew's Helper, keep `Hide Locked Teleports` enabled.
- Open the Grouping/minigame teleport UI and confirm Drew has scanned `Nightmare Zone` as locked while at least one other useful minigame teleport is available.
- Request the same route that previously selected Nightmare Zone.
- If using right-click/shift-click/manual map routing, wait one game tick after setting the destination; Drew should observe the internal target and replay it through the current blocked-key policy.
- Watch 10-15 seconds.
- If the map route still does not draw, run:

```powershell
.\tools\collect-route-diagnostics.ps1 -TailLines 8000
```

Attach or paste the generated `route-diagnostics-*.log`. The key lines to inspect first are `engine.gameState`, `drew.gameState`, `engine.tick`, `engine.menu.add`, `engine.menu.click`, `engine.target.set`, `engine.restart.apply`, `engine.pathfinder.submit`, `engine.telemetry.publish`, `map.render`, `tile.render`, `drew.snapshot.accept`, and `drew.currentPathSnapshot.empty`.

If the output only contains `engine.start`, `drew.engine.start`, `drew.requestFeed.skip reason=gameState LOGIN_SCREEN`, and `drew.start`, the repro did not reach the route input path in the captured dev session. Re-run from the updated batch file, log fully into game, set the route from the map/tile menu, then collect again.

Expected with `Hide Locked Teleports` on: Drew's Shortest Path no longer selects `Nightmare Zone Minigame Teleport`, the overlay reflects the recalculated route, and it does not bounce every ~2 seconds between old and corrected routes. Other available minigame teleports should still be allowed.

Expected after turning `Hide Locked Teleports` off: Drew keeps the saved scan result, stops sending `teleportation_minigames:nightmare_zone` as a blocked key, refreshes the active route so Nightmare Zone can be used again if the solver prefers it, and highlights the magic tab/minigame teleport flow for the allowed route.

## Priority 2: Quest Helper Resume

Current route-target replay works for Quest Helper paths because Quest Helper sends `shortestpath/path` with a target. Full quest resume still needs a Quest Helper bridge that can restore or reopen the active quest helper task itself.

Do not fake Quest Helper clicks until a clean API/message path is identified.
## 2026-08-07 UI-Only Reset

Myth ordered the mod reduced to the UI element and UI buttons only. Current next work should treat the old route engine, minigame scanner, highlighter, diagnostics, and path resources as removed, not broken.

Next work is UI-only:
- Launch `run-drews-helper-dev.bat`.
- Confirm the RuneLite plugin list shows only `Drew's Helper`.
- Confirm the overlay panel appears when the preserved UI toggles allow it.
- Confirm the config buttons/dropdowns are still visible.
- Open the world map, right-click inside the map bounds, and confirm `Set -> Waypoint #1` through `Set -> Waypoint #5` appear.
- Place all five waypoints, confirm colored markers appear, restart the plugin/client, and confirm the markers reload from hidden config.
- Do not debug or restore route drawing, shortest path telemetry, minigame teleport scanning, tab highlighting, or route diagnostics unless Myth explicitly asks to rebuild those systems from scratch.

Everything below this reset note is historical context from the removed route-engine attempt.

## 2026-08-07 Upstream Reference Analysis

Before rebuilding any route feature, read:

```text
docs/C2_Guides/RUNEMORO_SHORTEST_PATH_DEEP_DIVE.md
```

Next route work should not start by restoring `src/main/java/shortestpath/**`. Start from the UI shell and design a Drew-owned variant with:
- one authoritative `RouteEngine`;
- structured `TransportEdge` metadata;
- typed `RoutePolicy` from Drew's config UI;
- immutable `RouteResult` / `RouteSnapshot`;
- worker cancellation or version-token stale-result rejection;
- map/minimap/tile/HUD/highlighter views derived from the same snapshot;
- tests before live RuneLite wiring.
## Waypoint Colour Settings Follow-Up

Waypoint markers now consume waypoint marker colours from `DrewsHelperConfig.waypoint1PathColor()` through `waypoint5PathColor()`, with `Waypoint #1` defaulting to `#A9A9A9`. `DrewsHelperConfig.pathColor()` owns the route overlay colour and defaults to `#800020`. The current implementation uses RuneLite native `Color` config controls; if Myth wants an always-visible custom hex text field over a swatch instead of RuneLite's built-in colour picker, build that as a custom Swing/plugin-panel control rather than route-engine code.

Current waypoint-routing state:
- A Drew-owned walking route layer reads the five saved `waypointNPosition` values as ordered destinations.
- `src/main/java/shortestpath/**` remains deleted; do not restore it.
- `src/main/resources/collision-map.zip` is present as a third-party walking-collision data source from Runemoro's BSD-licensed project; keep `THIRD_PARTY_NOTICES.md` with it.
- World map, minimap, scene tile, and Drew overlay views all read one authoritative `DrewsHelperRouteSnapshot`.

Next route work:
- Live-test world-map and in-scene path drawing after setting two or more waypoints.
- Keep `Routing Options` -> `Benchmark Movement` ON only while testing overlay-vs-client movement. There is no route-solver selector anymore; Drew uses the single A* route solver with client-style final path ranking.
- For the next benchmark run, use the coordinate trace fields: compare `start`, `target`, `expectedPath10`, `actualPath10`, `divergence`, `candidates`, and `edgeValidation`. The candidate trace shows the exact fork tile, the legal moves in solver order, which tile Drew predicted, and which tile the client actually chose. `edgeValidation` shows whether the actual client edge is legal in Drew's collision graph, the graph continuation distance from that actual tile, whether the continuation is longer than the displayed route from the fork, the session repeat count, and `overrideCandidate`.
- Target-aware local route overrides are now built for the repeated live-client branches. Path 3 toward `(2970,3229,0)` already confirmed `full=true maxDev=0`; rerun Path 1 toward `(2932,3214,0)` once after the tail-preference build with `Benchmark Movement` ON, Run OFF, ground-click only, and no config changes mid-walk. Expected: no divergence at the old fork `(2939,3223,0)` and no late tail divergence from `(2935,3218,0)`. If it still diverges, use `DREW_ROUTE_BENCH` `divergence`, `candidates`, and `edgeValidation` to identify the next edge instead of adding broad collision-map changes.
- Do not add teleports/fast travel until the walking-only route is stable.
- Plane changes need a deliberate ladder/stair/transport model before they can work.

### Next live route check after D-0046
- Restart the Drew's Helper dev client.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- Run Path 1 only to exact target (2932,3214,0).
- No return leg is needed.
- Expected benchmark result: full=true, maxDev=0, divergence={none}.
- If another late divergence appears, inspect the new DREW_ROUTE_BENCH edgeValidation line and add only the next repeated target-aware edge.

### Next live route check after D-0047
- Restart the Drew's Helper dev client.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- Run these outbound-only control paths once each:
  - Path 1: start near (2942,3243,0), target (2932,3214,0).
  - Path 2: start near (2942,3243,0), target (2955,3206,0).
  - Path 3: start near (2942,3243,0), target (2970,3229,0).
- Let the player fully stop before each next click. Return legs are not needed for the control set.
- Expected for Path 1 and Path 3: `full=true`, `maxDev=0`, `divergence={none}`. Path 2 remains the clean control route.
- If returning anyway, stale return movement should now log `reason=stale-start ignored={...}` instead of producing a false `idx=0` route failure.
- After the three controls, gather 5-10 nearby random outbound routes with the same settings. The useful fields are `divergence={...}`, `edgeValidation={...}`, and `shape={... winner=...}`.
- Do not promote the shape ranker or delete the target-aware overrides until the diagnostic winner agrees with the live client across the controls plus the random-route sample.

### Next live route check after D-0048
- Restart the Drew's Helper dev client.
- With Benchmark Movement ON, Run OFF, ground-click only, rerun the three controls from the same start: Point 1 (2932,3214,0), Point 2 (2955,3206,0), Point 3 (2970,3229,0).
- Then collect 5 nearby random routes, but avoid object/tree clicks and random-event interruptions for the shape-ranker sample.
- If a random event happens again, confirm the waypoint markers and connector route tiles both recover after the client returns in-game.

### Next live route check after D-0049
- Restart the Drew's Helper dev client.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- For separate control samples, place and walk one waypoint at a time from the same start:
  - Point 1: (2932,3214,0).
  - Point 2: (2955,3206,0).
  - Point 3: (2970,3229,0).
- Turn Benchmark Movement OFF while returning/repositioning to the start. Turn it back ON only after the character is fully stopped and the single control waypoint is active.
- For the random-chain sample, placing five waypoints at once is now acceptable. The D-0049 benchmark log is segment-aware and should show `target=<current segment waypoint>` plus `finalTarget=<last waypoint>` when a divergence happens before the final waypoint.
- Useful fields are `divergence={...}`, `candidates={... target=... finalTarget=...}`, `edgeValidation={... target=...}`, and `shape={scope=segment ...}`.
- Do not promote the shape ranker or remove target-aware overrides until segment-aware logs agree with live movement across the controls plus random chains.

### Next live route check after D-0050
- Restart the Drew's Helper dev client.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- Re-run the same separate controls from the shared start:
  - Point 1: (2932,3214,0).
  - Point 2: (2955,3206,0).
  - Point 3: (2970,3229,0).
- Turn Benchmark Movement OFF while returning/repositioning to the start. Turn it back ON only after stopped and the single control waypoint is active.
- Then clear all, place five nearby random waypoints, turn Benchmark Movement ON, and walk the full chain.
- The key new field is `shadow={...}`:
  - `overridesMatter=false` means the no-override route matched the visible route.
  - `overridesMatter=true winner=visible` means the current local override still matches live movement better than the no-override baseline.
  - `overridesMatter=true winner=shadow` means the no-override/general route matched actual movement better and the override should be reconsidered.
  - `winner=tie` means both visible and no-override routes scored the same against actual movement.
- Do not remove the Path 1 / Path 3 local overrides until D-0050 shadow data shows the no-override/general route is equal or better on those exact controls and does not regress random chains.

### Next live route check after D-0051
- Restart the Drew's Helper dev client.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- Re-run the same separate controls from the shared start:
  - Point 1: (2932,3214,0).
  - Point 2: (2955,3206,0).
  - Point 3: (2970,3229,0).
- Turn Benchmark Movement OFF while returning/repositioning to the start. Turn it back ON only after stopped and the single control waypoint is active.
- Then clear all, place five nearby random waypoints, turn Benchmark Movement ON, and walk the full chain.
- Compare these fields on completed target reports:
  - `shadow={...}`: no-overrides baseline using the current client-style ranker.
  - `shapeShadow={...}`: no-overrides diagnostic route using segment line-shape tie ranking.
  - `shape={...}`: displayed route versus actual client movement.
- Early D-0051 unit evidence says the full-route line-shape ranker can overcorrect before a live fork, so treat `shapeShadow` as telemetry only. Promote nothing until repeated live samples show `shapeShadow` wins without creating new early divergence.

### Next live route check after D-0052
- Restart the Drew's Helper dev client after the D-0052 build.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- Clear all, place five nearby random waypoints, and walk the full chain in waypoint order.
- If a divergence appears, read `mergeBack={...}` first:
  - `stepDelta=0` means the client chose a local step permutation and rejoined the displayed route on schedule.
  - positive `stepDelta` means the actual route lagged behind the displayed route before rejoining.
  - `none` means the client did not rejoin the displayed route inside the captured path window.
- Do not promote `shapeShadow` or add local overrides from a single merge-back sample. Use repeated clean samples to decide whether the issue is a general step-order preference, a collision-resource disagreement, or input/click noise.

### Next live route check after D-0053
- Restart the Drew's Helper dev client after the D-0053 build.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- Clear all, place five nearby random waypoints, and walk the full chain in waypoint order.
- The key fields are now:
  - `divergence={... classification=sameTimePermutation benign=true ...}` for harmless same-time local step permutations.
  - `fit={visible=... shadow=...}` inside `shadow={...}`.
  - `fit={visible=... shapeShadow=...}` inside `shapeShadow={...}`.
- If the chain reports only `sameTimePermutation benign=true` divergences and the displayed route still reaches the final waypoint on schedule, collect two or three more nearby five-waypoint chains before promoting any route-ranker behavior.
- Do not remove Path 1 / Path 3 overrides or promote `shapeShadow` until controls still pass and repeated random chains show the merge-aware winner does not regress visible movement.

### Next live route check after D-0054
- Restart the Drew's Helper dev client after the D-0054 build.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- Clear all, place five nearby random waypoints, and walk the full chain in waypoint order.
- The key field is now `additionalDivergences={...}` inside every non-`none` `divergence={...}` block.
- If the first divergence is `classification=sameTimePermutation benign=true` and `additionalDivergences={none}`, treat that route as a harmless local step-order permutation.
- If `additionalDivergences` reports another `idx=...` or length-only `actual=(null)` / `predicted=(null)` case, inspect that later fork before changing route ranking. This is especially important when the completed route still has `full=false` or non-zero `lenDelta`.
- Do not promote `shapeShadow`, add a local override, or remove the Path 1 / Path 3 overrides until the post-merge mismatch is understood.

### Next live route check after D-0055
- Restart the Drew's Helper dev client after the D-0055 build.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- Clear all, place five nearby random waypoints, and walk the full chain in waypoint order.
- If the first divergence is benign but `additionalDivergences` reports a later `idx=...`, read `additionalDivergenceDetail={...}` for that later fork:
  - `candidates={...}` should show the second fork's predicted and actual tiles against the active segment target.
  - `edgeValidation={...}` should show whether the later actual edge is legal, whether continuation is longer, and whether it repeats enough to become an override candidate.
- Do not promote `shapeShadow`, add a local override, or remove Path 1 / Path 3 overrides until repeated clean chains classify the later fork consistently.

### Next live route check after D-0056
- Restart the Drew's Helper dev client after the D-0056 build.
- Keep Benchmark Movement ON, Run OFF, ground-click only.
- First, rerun the same five-waypoint chain that repeated the `idx=52` fork if it is still available or easy to recreate.
- Then collect two or three new nearby five-waypoint chains in waypoint order.
- The key field is now `forkRank={...}` inside `additionalDivergenceDetail={...}` on completed target reports:
  - `best=actual` or `actualRank=1` means the local candidate ranking would have preferred the client branch.
  - `best=predicted` or `predictedRank=1` means the displayed branch still wins the local ranker.
  - `best=candidate` means a third legal neighboring tile looks better than both displayed and actual, so do not promote the rule without more evidence.
- Treat this as telemetry only. Do not change visible route selection, add local overrides, or remove Path 1 / Path 3 overrides until repeated clean chains and the fixed controls agree.

### Route diagnostic closeout after D-0057
- Current phase is complete. Myth reran the fixed Point 1 / Point 2 / Point 3 controls after the random-chain samples, and all three visible routes completed cleanly with `full=true`, `lenDelta=0`, `maxDev=0`, and `divergence={none}`.
- Leave visible routing unchanged. Keep the Path 1 / Path 3 target-aware overrides, keep `shapeShadow` and `forkRank` as telemetry only, and do not promote a broad local ranker from the current evidence.
- Keep `Benchmark Movement` OFF during normal use. Turn it on only for deliberate route diagnostics.
- If a future route visibly disagrees with the client, collect a fresh completed `DREW_ROUTE_BENCH` report and judge `classification`, `additionalDivergences`, `additionalDivergenceDetail`, and `forkRank` before making another routing change.

## 2026-08-10 addendum - door highlights, and why the castle proof run was void

### Item 2 (access-point transport rows) - still blocked on live proof
The 2026-08-10 Falador Castle proof run produced no evidence and must be re-run.
Cause was not the route data: Drew's Helper was not running in that client at all.
See parked item 21. Nothing about the ranked queue was disproven or confirmed.

### Done this session
- Door world-highlights shipped. Route steps that cross a door now outline the door
  object in the same cyan used for gates and agility shortcuts. Doors are deliberately
  NOT added to the UI action list.

### Parked items added

20. Adjacent-tile transport edges are invisible to every transport-jump consumer.
    DrewsHelperRouteSnapshot.isTransportJump is `different plane OR max(|dx|,|dy|) > 1`,
    so an edge joining two neighbouring tiles never registers as a transport. That is
    exactly the shape of every row item 2 generates. The overlay now handles it for
    doors, but the same blind spot may still exist in the travel estimate, the ETA and
    the transport label path. Audit those before promoting any adjacent-tile row, or the
    rows will land and silently do nothing visible.

21. The deployed plugin jar is overwritten by RuneLite's plugin-hub sync.
    Drew's Helper is installed by overwriting
    `%USERPROFILE%\.runelite\plugins\shortest-path_<hub-hash>.jar`. RuneLite verifies its
    installed hub jars against the manifest and re-downloads any that do not match, so
    that file reverts to the stock Skretzo plugin without warning and without a log line.
    Observed 2026-08-10: all 25 hub jars rewritten at 13:44:51, and the deployed jar now
    contains 81 `shortestpath/` classes and 0 `drewshelper/` classes. The masquerade
    install is not a safe test path. Use `run-drews-helper-dev.bat` instead - it runs
    `gradlew run`, which loads the plugin through ExternalPluginManager.loadBuiltin and
    cannot be clobbered by the hub.

## 2026-08-10 addendum 2 - first real Route A capture, and the three things that ate it

The Falador Castle sweep was re-run through run-drews-helper-dev.bat and it WORKED. Our
plugin was loaded, the validator fired, and it found genuine mismatches for the first time:

    scene 2912:3328:0   tiles=10609  mismatches=2924  (1321 we block but the game allows)
    scene 2936:3288:0   tiles=10609  mismatches=2882  (1345 we block but the game allows)

2,666 real we-block-game-allows edges. Only 50 survived to the proof file, and 0 of those
matched a ranked candidate. Three separate causes, all now addressed:

1. The 25-row-per-scene log cap. MAX_VALIDATION_ROWS_LOGGED prints in scene iteration
   order, which is sorted by scene-x - so the 50 rows we recovered were all at x=2913-2914
   and x=2937-2939, the westernmost sliver of each scene. Falador Castle sits near x=2960,
   so every castle door was inside the 2,616 suppressed rows. The cap did not just lose
   data, it lost it in a spatially biased way, which is worse than losing it at random.

2. A closed door is not a mismatch. The live client says blocked and our map says blocked -
   they agree. The mismatch only exists while the door is OPEN. The validator ran once, on
   the tick you arrived, so opening every door afterwards changed nothing it could see.

3. Dev-run output never reaches client.log. See D-0124.

Fix shipped: the validator now writes every OURS_BLOCKS_LIVE_OPEN row, uncapped and
de-duplicated, to %USERPROFILE%\.runelite\drews-map-validate.txt, and re-validates the
current scene every 100 ticks so doors opened after arrival are picked up. The file is
truncated on plugin start, so it is one file per session.

Still unknown: only plane 0 scenes ever logged a summary line, despite the sweep covering
the second and top floors. Either the upper floors genuinely had zero mismatches, or they
were not validated. The uncapped file will settle it on the next run.

Note on parked item 21: the hub-clobber problem is real but does NOT affect dev runs.
run-drews-helper-dev.bat loads the plugin via ExternalPluginManager.loadBuiltin, which is
immune to the hub sync. Confirmed: the deployed hub jar was still the stock Skretzo build
(0 drewshelper classes) during a run in which our validator produced 2,666 mismatches.
Item 21 therefore only bites when playing through the official launcher.

## 2026-08-10 addendum 3 - item 2 CLOSED: first rows promoted to active routing

The third castle sweep worked exactly as designed. All three planes validated (the ~60s
re-check did its job), and plane 2's mismatch count visibly moved between 2385 and 2387 as
doors were opened and shut - the validator watching a door change state in real time.

    scene 2928:3288:0  mismatches=2986  (1395 we block, game allows)
    scene 2928:3288:1  mismatches=2181  ( 550 we block, game allows)
    scene 2928:3288:2  mismatches=2387  ( 301 we block, game allows)

2248 unique proof edges captured. generateTransportRows matched 10 of the 945 ranked
candidates, including the exact three top-floor doors named in the very first test brief.
All 10 promoted (20 rows, both directions) - see the evidence block in transport-overrides.tsv.

Item 2 is DONE. The loop it proves out - cache candidates, ranked by detour, gated on live
evidence, merged only after a set-diff regeneration - is now repeatable for anywhere in game.

### Parked items added

22. The route does not re-solve when you walk near a shortcut that would shorten it.
    Reported from the castle sweep: standing next to a staircase that would obviously
    improve the remaining trip does not trigger a recalculation. Worth checking against the
    existing recalculateDistance config and markRouteDirty* paths before assuming it is a
    bug rather than a deliberate stability choice - constant re-solving while walking is its
    own problem.

23. The Falador Castle crypt entrance is not in the transport data at all.
    Cache dump has it: object 39617 "Crypt", verb Enter, at 2965,3330,0. The upstream file
    contains exactly one row mentioning Crypt and it is 1483,3549,0 -> 1483,9951,3 in
    Kourend - nothing for Falador. So the router can never use it and it can never
    highlight, because highlighting only ever follows a route.
    This one cannot be auto-generated the way the doors were: AccessPointRowGenerator builds
    adjacent wall crossings from placement orientation, and a crypt entrance is a plane
    change whose destination tile the cache does not record. Capturing it needs the landing
    tile observed in game. Same shape as every staircase, ladder and cave entrance in the
    1478-ladder pile, so solving it once solves a large class.

## 2026-08-10 addendum 4 - double doors, and the cache is not account-scoped

Double-door highlighting now outlines every leaf of a doorway, not just the leaf whose tile
the route steps through. See D-0128.

### The cache question, settled with numbers

Asked whether a new account has a smaller cache because it has been to fewer places. It does
not, and the direction is the opposite of the worry:

    object definitions                                 : 62,401
    regions in the cache                               :  2,936
    regions in our shipped collision map               :  1,524
    regions the cache has that the shipped map has NOT :  1,425

The OSRS cache is the game's asset store - every map region, object definition and model,
downloaded from Jagex. It is identical for every account regardless of age or where the
player has walked. Confirmed by counting access points in places a brand-new account cannot
have visited:

    Kourend / Zeah   623      Mount Karuulm   211
    Fossil Island     23      Zanaris          12      Prifddinas   5

So the cache is roughly TWICE the coverage of the shipped map, not less. Those 1,425 missing
regions are exactly why routing refuses to plan into Zeah and Kourend, and they are the prize
in item 3.

## 2026-08-10 addendum 5 - item 3 scoped with real numbers

Double-door highlighting CONFIRMED IN GAME on two different doorways: the Falador Castle
double door and the Taverley Wall Gate. Both leaves outline. That closes the highlight work.

### What item 3 is actually worth - measured, not estimated

Diffed the shipped map's region set (the 1524 zip entries, named regionX_regionY) against
every region that actually contains an access point:

    access points in the cache dump          12,474  across 1,190 regions
    regions already in the shipped map          867  (9,419 access points)
    regions MISSING from the shipped map        323  (3,055 access points)
      of those, surface (regionY <= 65)          80  (  640 access points)
      of those, deep / instanced                243  (2,415 access points)

This corrects the headline "1,425 regions missing". Most of those 1,425 contain no access
point at all - empty terrain or instance space. The regions that carry real doors, gates and
stairs and are missing number 323, and only 80 of those are surface world.

The biggest missing surface cluster is x 1300-1800, y 2880-3260, and it is populated:

    141 Staircase   106 Ladder   92 Door   10 Gangplank   8 Ship's ladder   3 Tightrope

Gangplanks and ship's ladders mean a port; tightropes mean agility content. This is a real
landmass the router currently has no data for at all.

### The decision that has to be made BEFORE any rebuild

A closed door is recorded in the cache as a blocking wall placement. Rebuilding to the same
blocked/not-blocked format therefore does NOT fix a single door detour - a shut door stays a
wall and item 2's prove-and-promote loop would be needed region by region forever.

So item 3 forks at the start:
  A. Rebuild to parity. Fixes coverage and wrong-wall errors. Doors unchanged.
  B. Rebuild to a richer format that marks "blocked, but by an openable object". Bigger job,
     format change, and the map itself would then know a door is a door - which retires most
     of the manual override workflow.
Do not start cutting code until this is chosen.

### Proving a rebuild before shipping it

We now have an oracle we did not have before: the 2,248-edge Route A proof file from the
Falador sweep. Build the cache-derived map for those two scenes ONLY, re-run the validator
against live collision, and compare mismatch counts to the shipped map's. If the rebuild does
not lower the mismatch count on ground truth, it does not ship. Full rebuild only after that,
and still under the tools/README.md subset acceptance test.

## Item 3 - APPROVED DESIGN (2026-08-10). Read this before touching the collision map.

Mytharium chose option B: rebuild the collision map to a door-aware format, and price opening
a door at 1 tick. Both decisions are settled - do not re-litigate them.

### What the current format actually is

src/main/resources/collision-map.zip, one gzipped entry per region named regionX_regionY.
Each entry is a DrewsHelperFlagMap: header of four ints (minX, minY, maxX, maxY) then a
BitSet. DrewsHelperCollisionMap reads it with FLAG_COUNT = 2:

    bit 0  NORTH passable      bit 1  EAST passable

South and West are NOT stored - they are derived from the neighbour
(canMoveSouth(x,y) == canMoveNorth(x,y-1)). Diagonals are derived from the four cardinals.
The bit count per tile is already a parameter of the format, which is why this extension is
cheap rather than a rewrite.

### The door-aware format

FLAG_COUNT 2 -> 4:

    bit 0  NORTH passable          bit 2  NORTH blocked BY AN OPENABLE OBJECT
    bit 1  EAST  passable          bit 3  EAST  blocked BY AN OPENABLE OBJECT

South/West stay derived exactly as now. A door edge is NOT passable in the bit-0/1 sense -
the two states are exclusive, which keeps every existing caller correct by default.

Diagonal moves through a door are NOT allowed. You cannot corner-cut a doorway in game, and
allowing it would produce routes that cannot be walked.

VERSIONING - this is the trap. Bumping FLAG_COUNT changes the BitSet stride, so every
existing region entry silently decodes as garbage. Ship the new data as a NEW resource
name (collision-map-v2.zip) and have the loader prefer v2, falling back to the v1 resource at
FLAG_COUNT 2. That keeps the old map working, lets us A/B the two, and makes the cutover an
explicit decision instead of a silent corruption.

### The cost model - already supported

DrewsHelperWalkingRouteEngine is ALREADY cost-aware. Its own comment: plain steps cost 1 and
a transport taking D ticks costs 2 * D - so the unit is HALF A TICK. addNeighbor takes
stepCostUnits and applies Math.max(1, stepCostUnits).

Therefore: 1 tick to open a door = +2 cost units. A door step costs 1 (the step) + 2 (the
open) = 3. No new cost machinery is needed, only a new step source.

Two call sites matter: the neighbour expansion (canMove check, around line 894) has to admit
a door edge as a passable-but-dearer step, and DrewsHelperTraversableTiles.isTraversable has
to count door edges - otherwise a tile whose only exit is a door stops being standable and
waypoint snapping starts moving people.

### First step is a measurement, not a rebuild

Do NOT start by reimplementing OSRS collision. The question that decides everything is
narrower, and we can answer it offline with the 2,248-edge proof file as the oracle:

  For each proof edge - one the live game let Mytharium walk through and the shipped map
  refuses - what does the CACHE say is on that edge?

    nothing        -> the shipped map is simply wrong there; a rebuild fixes it
    an openable    -> the door bit fixes it; this is the size of the door prize
    a solid wall   -> our decode disagrees with the live client; investigate before trusting
                      anything else in the rebuild

That classification needs only the loc parsing CacheAccessPointDumper already does. It needs
no game session, no format change, and no collision algorithm - and its answer sizes every
remaining part of item 3.

Upstream is no help for the builder itself: the Drew Shortest Path checkout CONSUMES
collision-map.zip but contains no generator for it. All the inputs we need are already on the
cachetools classpath though - RegionLoader, Region, ObjectManager, LocationsDefinition.

## Item 3 - FIRST MEASUREMENT IS IN (2026-08-10). It changes the ordering.

ProofEdgeClassifier ran against all 2,248 proof edges. Every one fell inside a region the
cache has, so nothing was dropped:

    NOTHING   1444  (64.2%)   no wall placement at all on either tile
    OPENABLE    17  ( 0.8%)   an Open/Close wall object
    SOLID      787  (35.0%)   a wall placement with no open action

### What each bucket means

NOTHING at 64% is the headline. On 1,444 edges the live client let the player walk, the cache
agrees there is nothing there, and only our shipped map disagrees. Those are rebuild wins
outright - no format change, no door bit, no proof loop. This is the strongest evidence yet
that the shipped map is genuinely stale rather than merely incomplete.

OPENABLE at 17 edges is a real and honest surprise. The whole door-aware case rested on doors
being a big slice and in this sample they are not. The 17 break down as Castle door 12,
Door 4, Guild Door 1 - i.e. exactly the Falador castle doors already promoted in item 2.
IMPORTANT: this sample is biased and 17 is a FLOOR, not an estimate. A proof edge only exists
if the door was standing OPEN when the scene was validated, and the only doors opened were the
castle ones. World-wide the cache dump holds roughly 2,300 wall-mounted openables (353 Gate,
1,793 Door, 188 Large door), each covering one or more edges, so the true door prize is on the
order of a few thousand edges. Do not quote 17 as the size of the door problem, and do not
quote "a few thousand" as measured either - neither number is proven yet.

SOLID at 35% is NOT yet evidence of a decode problem, despite what the generated report says.
Every one of the 878 placements in that bucket has name = null, and null-named objects in the
OSRS cache are overwhelmingly non-interactive scenery - wall trims, arches, floor edging. The
classifier was deliberately built to report placements and NOT to model blocking, so "a
wall-type placement exists here" is not the same claim as "something blocks here". The
hypothesis is that SOLID is mostly non-blocking decoration and the classifier is simply blind
to that by design.

### Next step, and it is small

Add ONE field to the classifier: does the ObjectDefinition actually block? interactType and
blockingMask are already read by CacheAccessPointDumper (they were measured during item 1),
so this is a field lookup, not new decoding. Split SOLID into SOLID-BLOCKING and
SOLID-DECORATION.

If SOLID collapses into decoration as expected, the rebuild is trustworthy and NOTHING+SOLID
together are ~99% rebuild wins. If a real SOLID-BLOCKING population survives, that is a
genuine decode disagreement and it must be understood before any rebuilt map ships.

Ordering consequence: the coverage/correctness rebuild is now the proven-large half and should
lead. The door bit from D-0117 is still the right design and still rides along in the same
format change - but it is no longer the part carrying most of the value, and the plan should
not be sold that way.

## Item 3 - the SOLID decoration hypothesis is DEAD (2026-08-10)

Ran the field split on the 787 SOLID edges. The hypothesis was that they are non-blocking
scenery. They are not:

    SOLID interactType     2 -> 1159    0 -> 7    1 -> 4
    SOLID blockingMask     0 -> 1170
    SOLID wallOrDoor       0 -> 1170
    SOLID blocksProjectile true -> 982  false -> 188

    SOLID edges where EVERY placement has interactType == 0:  7 of 787

Seven. The decoration reading is wrong.

And note what the contrast group does: the OPENABLE bucket - real, confirmed doors - is
interactType 2 on all 22 placements, blockingMask 0 on all 22. IDENTICAL to SOLID on both.
These fields do not separate a door from whatever SOLID is, and they do not encode
traversability. That is the SECOND time this project has been burned by exactly these three
fields - see D-0119, where interactType/blockingMask/wallOrDoor were measured against gates
vs chests and overlapped completely. Promoted to DECISION_LOG as D-0118 so nobody reaches for
them a third time.

One field did separate, in this sample only: wallOrDoor is 1 on 17 of the 22 OPENABLE
placements and 0 on all 1,170 SOLID placements. Interesting as a door signal, but it is a
17-placement sample and D-0119 already caught wallOrDoor overlapping elsewhere. Do not build
on it without a much wider measurement.

### The better hypothesis - and it is a flaw in the classifier, not the cache

The classifier counts ANY wall-type placement on either tile of the edge, and deliberately
ignores the placement's orientation. But a wall on a tile's NORTH edge does not block its
EAST edge. With four edges per tile and both tiles inspected, most SOLID hits are probably
walls facing a completely different direction from the one the player walked through.

That would explain everything: real walls, correctly decoded, simply not on the edge in
question - which is exactly why the live client let the player walk.

It is cheap to test. The Location carries its orientation and AccessPointRowGenerator already
has the orientation-to-edge mapping (proven by ground truth on the Falador gate, and measured
at 65% predicted-edge-blocked vs 40% perpendicular control). Filter the wall placements by
whether their orientation actually faces the crossed edge and re-run.

Expected outcome if the hypothesis holds: SOLID collapses hard, most of it moving into
NOTHING, and the rebuild case gets stronger rather than weaker. If SOLID survives an
orientation filter, then there IS a real decode disagreement and no rebuilt map should ship
until it is understood.

## Item 3 - SOLID explained. The rebuild case survives. (2026-08-10)

Added an orientation-facing filter to the classifier, using the mapping already proved against
the Falador gate: orientation 0=W, 1=N, 2=E, 3=S. A placement on the near tile faces the
crossed edge when its direction equals the crossing; on the far tile when it equals the
opposite. Zero invalid orientation values in the whole dataset, so the inputs are clean.

    OPENABLE_FACING       10  (58.8% of OPENABLE)
    OPENABLE_NOT_FACING    7  (41.2%)
    SOLID_FACING         294  (37.4% of SOLID)
    SOLID_NOT_FACING     493  (62.6%)

The hypothesis holds: nearly two thirds of SOLID is wall placements facing a different side of
the tile from the one the player walked through. The classifier was blind to orientation, and
that blindness manufactured most of the bucket.

### But read the residual before trusting it

The 294 SOLID_FACING edges are NOT 294 mysteries. Their locType mix:

    SOLID_FACING placements    9 -> 228    1 -> 119    3 -> 62    0 -> 35    2 -> 3

The facing test is rigorous ONLY for locType 0, a straight wall on one side of a tile.
locTypes 1, 3 and 9 are corners and diagonals, where a single orientation value does not
describe every side the shape blocks. 412 of the 447 SOLID_FACING placements - 92% - are those
shapes. Measured by the only instrument that is actually valid here, the genuinely unexplained
population is on the order of 35 straight-wall placements out of the original 787 edges.

### The instrument measured its own error rate, and it is high

OPENABLE_NOT_FACING is 7 of 17. Those are CONFIRMED doors - proven in game, already promoted
into transport-overrides.tsv and visibly routed through - and the facing test says 41% of them
do not face the edge they demonstrably open onto.

So the facing test is a blunt instrument with a measured ~41% false-negative rate on
known-good data. The 62.6% figure carries that error bar: some of the 493 are facing walls the
test got wrong, and some of the 294 are not really facing. Do not quote 62.6% as precise. The
direction is what is trustworthy, not the decimal.

This is also a warning for the v2 builder: DO NOT build the door bit on a naive
orientation-equals-direction test. It would miss roughly two in five real doors. The builder
needs per-locType blocking shapes, or live-client confirmation, or both.

### Where item 3 stands now

NOTHING 1444 (64.2%) plus SOLID_NOT_FACING 493 (22.0%) = 1,937 of 2,248 proof edges, 86%,
are consistent with "the shipped map is simply wrong and a rebuild fixes it". Nothing found so
far argues against rebuilding. Proceed to the v2 builder per D-0117, with the caveat above
about how the door bit must be derived.

## Item 3 - shape-derived door bit chosen. Measure the shapes BEFORE building. (2026-08-10)

Mytharium chose the shape-derived door bit over seeding from the known-openable list, on the
explicit grounds of doing it right rather than quickly (D-0119).

Shape-derived means the builder needs a rule per placement: given a locType and an
orientation, WHICH tile edges does this placement block? That table is the foundation of the
whole v2 map - every flag written depends on it.

### Why the table gets measured, not written from memory

This project has now been wrong twice by assuming what cache fields mean:
  - D-0119/item 1: interactType, blockingMask and wallOrDoor were assumed to separate gates
    from chests. They overlapped completely.
  - D-0118: the same three fields were assumed to encode blocking. Confirmed doors and
    unexplained SOLID edges turned out identical on all of them.
Both were caught only because they were measured. A hand-written locType table would be the
third assumption, and this one would be baked into 2,936 regions of shipped data.

Also relevant: the facing test used in D-0132 has a measured 41% false-negative rate on
confirmed doors. A naive "orientation equals direction" rule is already known to be wrong.
The real rule must differ per locType, and locTypes 1, 3 and 9 are 92% of the residual.

### The derivation

LocTypeShapeProbe cross-tabulates every wall placement in the cache against what the SHIPPED
collision map blocks on each of that tile's four edges, grouped by (locType, orientation).
Output per group: how often each of N/E/S/W is blocked, plus a sample count.

The shipped map is used only as a TEACHER FOR SHAPE, never as a source of truth for coverage.
That distinction matters: we already measured it wrong on ~14% of edges, and it is missing
323 regions entirely. But it was produced from this same cache by a working toolchain, so
wherever it does have data its shape rules should be structurally right. The ~14% error
becomes noise, and it shows up directly in the per-rule percentages.

ACCEPTANCE: a rule is only usable if its blocked-rate is decisively high AND its sample count
is large. Anything landing near 50% is the same non-signal the item 2 control caught (65% vs
a 40% perpendicular baseline was judged not good enough to ship, and that judgement stands).
Rules that do not clear the bar get carried into the builder as UNKNOWN and resolved by live
validation, not guessed.

## Item 3 - THE SHAPE TABLE, MEASURED (2026-08-10). This is what the builder uses.

LocTypeShapeProbe cross-tabulated 558,894 single-placement wall tiles in covered regions
against the shipped map's four edge flags. 275,173 placements were correctly skipped for
sitting in regions the shipped map does not have - without that filter every rule would have
read ~100% blocked, because an absent region returns all-false and false means blocked.

NULL BASELINE over 250,000 no-wall tiles: N 22.2%  E 22.2%  S 22.4%  W 22.5%.
Every number below is read against that 22%.

### The derived table

locType 0 - STRAIGHT WALL, one edge. Confirms DIRECTION_BY_ORIENTATION independently:
    orient 0 -> W 93.5%      orient 1 -> N 93.3%
    orient 2 -> E 93.2%      orient 3 -> S 94.0%
    ~80,000 samples each. Secondary directions sit at 54-65%, which is neighbouring-wall
    correlation, not the rule. The peak-to-secondary gap is ~30pp and unambiguous.

locType 2 - CORNER, two adjacent edges. This is a genuinely NEW rule we did not have:
    orient 0 -> N 93.7% + W 93.8%   (NW corner)
    orient 1 -> N 94.0% + E 94.0%   (NE corner)
    orient 2 -> E 93.0% + S 93.2%   (SE corner)
    orient 3 -> S 93.4% + W 93.4%   (SW corner)
    ~3,800 samples each, and the other two directions drop to 51-55%. Very clean.

locType 3 - one edge, SAME mapping as locType 0, slightly weaker but cleaner separation:
    orient 0 -> W 88.2%      orient 1 -> N 87.6%
    orient 2 -> E 87.8%      orient 3 -> S 88.1%
    ~4,300 samples each, and the non-blocked directions fall to 33-48% - closer to baseline
    than locType 0 manages.

locType 9 - DIAGONAL, blocks the whole tile. Orientation is irrelevant:
    all four directions 94.6-95.0% at every orientation, ~24,500 samples each.

locType 1 - DOES NOT CLEAR. Carry as UNKNOWN.
    All four directions sit flat at 76-79% at every orientation, ~23,000 samples each.
    It clearly blocks something - 78% against a 22% baseline is not nothing - but orientation
    carries NO directional signal for it. The builder must not derive a direction from
    orientation for locType 1. Resolve it with live validation, do not guess.

### The door signature falls straight out of this

Openable locType 0 placements peak on the SAME direction as solid ones, but at ~60% instead
of ~93%:
    orient 0 -> W 63.3%   orient 1 -> N 64.3%   orient 2 -> E 59.2%   orient 3 -> S 64.0%

That ~33pp deficit is the door bit in raw form: roughly a third of doors were standing OPEN
when the shipped map was built, so the map recorded those edges as passable. Same shape rule,
different observed state. This is exactly the ambiguity v2 removes by storing "blocked by an
openable" as its own bit rather than collapsing it into blocked/not-blocked.

### One correction to the probe's own output

The report's "rules that clear the bar" section uses a +30pp-over-baseline threshold, and by
that measure it lists all four directions as clearing for locType 0. That is too permissive -
the secondary directions are wall-clustering correlation, not blocking. The correct reading is
the PEAK against its own secondaries, not lift over the null baseline. The raw table is
unambiguous to a human; the auto-selection heuristic is not. Trust the table, not that
section, and do not wire the heuristic into the builder.

## Item 3 - build phase. Two pieces, and the in-game run got bigger on purpose.

Decisions now locked: shape-derived door bit (D-0119 reasoning), 1-tick door cost = +2 units
(D-0117), UNKNOWN defaults to blocked (D-0120).

### Piece 1 - full-scene live flag dump, and why it replaces the old ask

Every round of item 3 so far has needed another in-game trip, because the validator only ever
recorded OURS_BLOCKS_LIVE_OPEN - one-sided evidence. It tells us where our map over-blocks and
nothing else. Resolving locType 1 needs the opposite view: what the live client blocks on all
four edges of tiles we already know carry a single locType 1 placement.

Rather than build a probe for that one question and then need another trip for the next one,
the validator now dumps the COMPLETE live blocked-state of every tile edge in the scene, once
per validated scene, to .runelite\drews-live-flags.txt. About 10,609 tiles times four edges per
scene - small on disk, and it is a permanent ground-truth dataset.

That turns every future question into an offline cross-tab: locType 1, the ~35 unexplained
straight-wall placements, the real door open/closed rate, diagonal shapes. One trip, reusable
forever, instead of one trip per hypothesis. This is the D-0119 principle applied to the test
loop itself.

### Piece 2 - the v2 region builder

Cache -> per-region DrewsHelperFlagMap at FLAG_COUNT = 4 -> collision-map-v2.zip. Uses the
measured shape table from D-0133, NOT a hand-written one:

    locType 0  one edge   {0:W, 1:N, 2:E, 3:S}
    locType 3  one edge   {0:W, 1:N, 2:E, 3:S}
    locType 2  two edges  {0:NW, 1:NE, 2:SE, 3:SW}
    locType 9  all four edges, orientation ignored
    locType 1  UNKNOWN -> blocked (D-0120), counted and reported every run

An openable placement sets the DOOR bit for the same edges its shape blocks, instead of the
passable bit. Door and passable are mutually exclusive, which keeps every existing caller
correct by default.

Not shipped until it beats the old map on the 2,248-edge proof file, two Falador regions only,
before anything touches the other 2,934.

## Item 3 - v2 builder is REAL and MEASURED. Two open questions, both need live data.

Status: builder written, compiles, runs on the real cache, round-trip verified, and fixes
65.7% of the proof edges from a 0% baseline. See D-0135 for the numbers.

NOT yet done, deliberately: the loader still reads v1 only. Do NOT wire
collision-map-v2.zip into DrewsHelperCollisionMap until the two questions below are answered.
Shipping a loader change for data that has not passed ground truth is the exact mistake the
proof-first discipline exists to prevent.

### Open question 1 - the 772 still-blocked edges

These are edges the live client demonstrably let the player walk through, which v2 still
blocks. Expected contributors, in order of likely size:
  - locType 1 UNKNOWN, 909 placements in these 6 regions, each blocking all four edges by
    D-0120. This is the conservative default working as designed, and it is the single biggest
    lever available.
  - locType 9 diagonals blocking whole tiles - correct per the shape table, but a diagonal
    that only blocks a corner would be over-blocked by a whole-tile rule.
  - doors that were standing open when the proof was captured and are not caught by the
    Open/Close action test.
Resolve with the drews-live-flags.txt ground truth, NOT by loosening the shape table on a hunch.

### Open question 2 - the terrain rule is unverified

Tile-setting floor blocking and the plane-1 bit-2 bridge convention are implemented as
CONVENTION, explicitly labelled as such in the code and the report. 4,909 terrain-blocked tiles
and 48 bridge-branch tiles in 6 regions. Nothing in this project has verified either. The live
flag dump settles it: cross-tab tiles the builder blocked on terrain against what the client
actually blocks.

### The in-game run that answers both

Needs Validate Map Data ON and a few stops of about ten seconds each. The dump fires once per
scene key, on that scene's first validation, so arriving somewhere new is enough - no standing
around for a minute like the mismatch capture needed. Bridges and upper floors are worth
including specifically because they are the least verified part of the builder.

## PARKED: regather the 200 KiB-truncated files

Mytharium asked for this to be queued behind "once Weylin updates the cutoff from 5 minutes".
CORRECTION, and it changes the trigger: those are two unrelated ceilings.

  - The 200 KiB TRUNCATION was a download-path bug (scp falling back to SFTP). It is ALREADY
    FIXED by the conditional -O patch and verified byte-perfect on a 928,448-byte file with a
    matching SHA. Downloads work correctly RIGHT NOW.
  - The 5-MINUTE CUTOFF is the ssh-watchdog kill age. It only limits how long a single blocking
    SSH call may run. It has nothing to do with file size.

So regathering is NOT waiting on Weylin. It is waiting on something else entirely: the SOURCES
cannot be located. Searched on mythpc and found nothing - no addons directory under My Games at
all, and no pak matching breathing/circulation/tccc under either Workbench root.

THE INVENTORY (11 distinct files at exactly 204,800 bytes, 2026-06-12 to 2026-08-05):
    ace_paks/circulation.pak      ace_paks/breathing.pak
    tccc_data.pak                 tccc_data2.pak
    gtt_heightmap.asc             analysis/wb-index.txt
    analysis/rhs-cp01.rdb         analysis/rhs-statusquo.rdb
    medlog_0135.log               med0703_diag.txt
    m320_live/M320.xob            (this one is MacKelnuts lane, not Fort Stewart)
Three more under workspace/xfer-test are deliberate control artefacts and are correct at that size.

WHAT IS ACTUALLY AT RISK: the recorded conclusion that the ACE Breathing and Circulation paks
are "real and rich" was formed from files that are exactly 204,800 bytes, so it may rest on a
fraction of each pak. Treat it as unverified until the paks are re-read from a located source.

WHAT IS NOT AT RISK: nothing in the collision-map work. That was never analysed from a
downloaded copy - every read happened on mythpc, and the cross-tab reported 57,979 rows across
14 scenes, which is exactly 57,993 file lines minus 14 scene headers. A truncated read would
have yielded 12,792 rows and 3 scenes. The arithmetic closes.

TO ACTUALLY CLOSE THIS, in order: (1) identify where each file originally came from - host and
path - since that was never recorded; (2) confirm the source still exists; (3) re-download and
verify size+hash against the remote. Step 1 is the blocker, not any infrastructure limit.
