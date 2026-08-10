# Next Work

Last updated: 2026-08-09.

## Active Handoff - Magic-Tab Spell Teleports From Carried Supplies

Home teleports shipped on 2026-08-09. The next code pass is **magic-tab spell teleports from carried supplies**, then bank-aware teleport routing.

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

## NEXT SESSION - start here (written 2026-08-10, session close)

The map-data work is BUILT but not yet WIRED IN. Route B produces a list; nothing in the
plugin reads it yet. Everything below is in priority order - do them top down.

### 1. Tighten the movement-verb filter  (small, ~20 min)

Blocks judging everything else, so it goes first. `CacheAccessPointDumper.MOVEMENT_OPS`
matches on "open", which also collects 640 Chest, 226 Drawers, 197 Closed chest and 145
Wardrobe - containers, not movement. So the 11,610 uncovered figure is an UPPER BOUND and
must not be quoted as a gap count until this is done.
Fix: intersect the verb match with the object actually obstructing movement.
`ObjectDefinition` already exposes `getInteractType()`, `getBlockingMask()` and
`getWallOrDoor()` - none are used yet. A chest blocks nothing you would path through.
Done when: the Falador gate fixture still passes AND Chest/Drawers/Wardrobe are gone.

### 2. Turn confirmed access points into real transport rows  (the actual payoff)

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

### 5. Untested from 2026-08-09 - verify before trusting

The source-side Wilderness fix (D-0114) was never confirmed in game. Myth was given the
repro and did not report back. Test: waypoint #1 inside the Wilderness, waypoint #2 at
Lumbridge, home teleport ON COOLDOWN, Wilderness transports OFF.
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

8. **Agent backup files were committed into the repo.**
   Commit `8aed260` swept in roughly fifteen `.pre-*` snapshots alongside the real changes -
   `DECISION_LOG.pre-d0086.md`, `02_NEXT_WORK.md.pre-teleport-plan-20260809` and similar. These
   are working scratch taken before each edit and are meant to be transient, not repo content;
   several are near-complete duplicates of large guides, so they inflate the tree and will show
   up in future diffs and searches. Suggested cleanup: `git rm --cached` them and add a
   `*.pre-*` ignore rule. Not actioned - deleting committed files is Myth's call.

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

16. **Route B's movement-verb filter is too loose - refine before trusting the 11,610.**
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
