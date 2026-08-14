package com.drewshelper;

import com.google.inject.Provides;
import com.drewshelper.routing.DrewsHelperCollisionMap;
import com.drewshelper.routing.DrewsHelperMapValidator;
import com.drewshelper.routing.DrewsHelperPlayerCapability;
import com.drewshelper.routing.DrewsHelperRouteBenchmark;
import com.drewshelper.routing.DrewsHelperRouteSegmentRecorder;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import com.drewshelper.routing.DrewsHelperRouteSearchMetrics;
import com.drewshelper.routing.DrewsHelperRouteStatus;
import com.drewshelper.routing.DrewsHelperTransportGraph;
import com.drewshelper.routing.DrewsHelperTransportPolicy;
import com.drewshelper.routing.DrewsHelperTraversableTiles;
import com.drewshelper.routing.DrewsHelperTraversalRecorder;
import com.drewshelper.routing.DrewsHelperTravelEstimate;
import com.drewshelper.routing.DrewsHelperWalkingRouteEngine;
import com.drewshelper.routing.ui.DrewsHelperRouteMapOverlay;
import com.drewshelper.routing.ui.DrewsHelperRouteMinimapOverlay;
import com.drewshelper.routing.ui.DrewsHelperRouteTileOverlay;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

@Slf4j
@PluginDescriptor(
    name = "Drew's Helper",
    description = "Waypoint placement and route guidance.",
    tags = {"ui", "helper", "waypoint", "route", "transport"}
)
public class DrewsHelperPlugin extends Plugin
{
    public static final int MAX_WAYPOINTS = 5;

    /** How far off the drawn path you may stray before the route is re-solved from where you are. */
    private static final int ROUTE_RECALCULATE_OFF_PATH_DISTANCE = 2;
    /**
     * How far the player may be from a benchmark's expected start before that capture is
     * discarded as stale. Deliberately separate from the re-solve distance above - they were one
     * constant, but they answer different questions and tightening the re-solve should not make
     * the benchmark throw away usable samples.
     */
    private static final int ROUTE_BENCHMARK_STALE_START_DISTANCE = 10;
    private static final String CONFIG_GROUP = "drewshelper";

    /**
     * The only skills that appear anywhere in the transport requirements. A level-up in anything
     * else cannot change the route, so it must not mark it dirty.
     */
    private static final Set<Skill> ROUTE_RELEVANT_SKILLS = EnumSet.of(
        Skill.AGILITY, Skill.CONSTRUCTION, Skill.CRAFTING, Skill.FARMING, Skill.FIREMAKING,
        Skill.FISHING, Skill.MAGIC, Skill.MINING, Skill.PRAYER, Skill.RANGED, Skill.STRENGTH,
        Skill.THIEVING, Skill.WOODCUTTING);

    /** Per-slot graceful run-energy restoration. Totals 20; the complete set adds 10 more. */
    private static final Map<EquipmentInventorySlot, Integer> GRACEFUL_SLOT_PERCENT;

    static
    {
        Map<EquipmentInventorySlot, Integer> graceful = new EnumMap<>(EquipmentInventorySlot.class);
        graceful.put(EquipmentInventorySlot.HEAD, 3);
        graceful.put(EquipmentInventorySlot.BODY, 4);
        graceful.put(EquipmentInventorySlot.LEGS, 4);
        graceful.put(EquipmentInventorySlot.GLOVES, 3);
        graceful.put(EquipmentInventorySlot.BOOTS, 3);
        graceful.put(EquipmentInventorySlot.CAPE, 3);
        GRACEFUL_SLOT_PERCENT = Collections.unmodifiableMap(graceful);
    }
    private static final String SET = "Set";
    private static final String CANCEL = "Cancel";
    private static final String CLEAR = "Clear";
    private static final String WAYPOINT_TARGET_PREFIX = "Waypoint #";
    private static final String ALL_WAYPOINTS_TARGET = "All Waypoints";
    private static final String WAYPOINT_POSITION_KEY_PREFIX = "waypoint";
    private static final String WAYPOINT_POSITION_KEY_SUFFIX = "Position";
    private static final String WAYPOINT_COLOR_KEY_SUFFIX = "PathColor";
    private static final int OBSERVED_EDGE_OVERRIDE_REPEAT_THRESHOLD = 2;
    private static final int ROUTE_BENCHMARK_START_SYNC_TILE_LIMIT = 3;
    private static final int ROUTE_BENCHMARK_PENDING_START_TICK_LIMIT = 10;
    private static final int ROUTE_BENCHMARK_PENDING_START_MOVE_LIMIT = 3;

    /** Cap on validator rows logged per scene, so one bad region cannot flood the console. */
    private static final int MAX_VALIDATION_ROWS_LOGGED = 25;
    /**
     * Cap on unique proof rows written per session. Raised from 50,000 once both mismatch kinds
     * were recorded: a single dense scene now emits ~4,600 rows, which put the old cap about
     * eleven scenes into a normal session. Under-blocks are the half we most want a lot of, so
     * the ceiling is set well clear of any realistic session rather than tuned close to one.
     */
    private static final int MAX_VALIDATION_ROWS_WRITTEN = 500000;
    private static final int VALIDATION_REVALIDATE_TICKS = 100;

    /**
     * The scene header {@link #writeLiveFlagsIfNeeded} writes, for example
     * {@code DREW_LIVE_FLAGS scene 2912:3160:0 size=104 covered=103}. Group 1 is the scene key.
     */
    private static final Pattern LIVE_FLAG_SCENE_HEADER = Pattern.compile(
        "^DREW_LIVE_FLAGS\\s+scene\\s+(-?\\d+:-?\\d+:-?\\d+)\\s+size=\\d+\\s+covered=\\d+\\s*$");

    /** Scene and tick the validator last checked, so it can re-sample open doors without running every tick. */
    private String lastValidatedSceneKey;
    private int lastValidationTick;
    private final Set<String> emittedValidationLines = new HashSet<>();
    private final Set<String> dumpedLiveFlagSceneKeys = new HashSet<>();
    private boolean validationWriteLimitWarned;

    /** Ticks between real-level polls. A level-up is rare; per-tick string building is not free. */
    private static final int PLAYER_LEVEL_POLL_TICKS = 50;

    /** Last row written to drews-player-levels.txt, so only a genuine change appends. */
    private String lastPlayerLevelsRow;
    private boolean playerLevelsWriteWarned;

    /** Ticks between route-leg polls. Walking a long path every tick would be wasted work. */
    private static final int ROUTE_LEG_POLL_TICKS = 25;

    /** Last set of legs written, so a route only records once rather than on every poll. */
    private String lastRouteLegsBlock;
    private boolean routeLegsWriteWarned;
    private boolean validationFileWriteWarned;
    private boolean liveFlagFileWriteWarned;

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private WorldMapPointManager worldMapPointManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DrewsHelperOverlay overlay;

    @Inject
    private DrewsHelperRouteMapOverlay routeMapOverlay;

    @Inject
    private DrewsHelperRouteMinimapOverlay routeMinimapOverlay;

    @Inject
    private DrewsHelperRouteTileOverlay routeTileOverlay;

    private final WorldPoint[] waypoints = new WorldPoint[MAX_WAYPOINTS];
    private final WorldMapPoint[] waypointMarkers = new WorldMapPoint[MAX_WAYPOINTS];

    // A waypoint only auto-clears once the player has actually stood somewhere else first.
    // Without this, dropping a waypoint on your own tile would delete itself a tick later.
    private final boolean[] waypointArmed = new boolean[MAX_WAYPOINTS];

    private Point lastMenuOpenedPoint;
    private ExecutorService routeExecutor;
    private Future<?> routeFuture;
    private DrewsHelperCollisionMap collisionMap;
    private DrewsHelperWalkingRouteEngine routeEngine;
    private volatile DrewsHelperTravelEstimate travelEstimate = DrewsHelperTravelEstimate.EMPTY;
    private String routeEngineCacheKey = "";
    private final Map<Skill, Integer> lastKnownSkillLevels = new HashMap<>();
    private volatile DrewsHelperRouteSnapshot routeSnapshot = DrewsHelperRouteSnapshot.noWaypoints();
    private int routeRequestId;
    private boolean routeDirty = true;
    private String lastRouteSignature = "";
    private RouteBenchmarkCapture routeBenchmarkCapture;
    private final Map<String, Integer> routeBenchmarkObservedEdgeCounts = new HashMap<>();
    private volatile String routeBenchmarkSummary = "";
    private EtaDebugCapture etaDebugCapture;
    private boolean loggedUnresolvedQuests;

    // Stamina duration calibration. The varbit counts down in units of unknown size and RuneLite
    // never converts it anywhere, so rather than guess we measure: the gap between two
    // consecutive single-unit decrements IS the unit, in ticks. Persisted once learned.
    private static final String STAMINA_UNIT_KEY = "staminaTicksPerDurationUnit";
    private int tickCounter;

    /** Records object interactions and what they actually did. Null until the plugin starts. */
    private DrewsHelperTraversalRecorder traversalRecorder;
    /** Records clicked walk segments against the route visible at click time. */
    private DrewsHelperRouteSegmentRecorder routeSegmentRecorder;
    private int lastStaminaDuration;
    private int lastStaminaDurationTick;
    private int staminaTicksPerUnit;
    private long lastCooldownEpochMinute = -1;

    @Override
    protected void startUp()
    {
        routeExecutor = Executors.newSingleThreadExecutor(r ->
        {
            Thread thread = new Thread(r, "drews-helper-route");
            thread.setDaemon(true);
            return thread;
        });
        routeSnapshot = DrewsHelperRouteSnapshot.noWaypoints();
        routeDirty = true;
        lastRouteSignature = "";
        lastCooldownEpochMinute = -1;
        lastValidatedSceneKey = null;
        lastValidationTick = 0;
        emittedValidationLines.clear();
        dumpedLiveFlagSceneKeys.clear();
        validationWriteLimitWarned = false;
        lastPlayerLevelsRow = null;
        playerLevelsWriteWarned = false;
        lastRouteLegsBlock = null;
        routeLegsWriteWarned = false;
        validationFileWriteWarned = false;
        liveFlagFileWriteWarned = false;
        try
        {
            Files.deleteIfExists(new File(RuneLite.RUNELITE_DIR, "drews-map-validate.txt").toPath());
        }
        catch (IOException ex)
        {
            log.warn("Drew's Helper: could not reset map validation proof file", ex);
        }
        seedDumpedLiveFlagSceneKeys();
        traversalRecorder = new DrewsHelperTraversalRecorder(
            new File(RuneLite.RUNELITE_DIR, "drews-traversals.txt"));
        routeSegmentRecorder = new DrewsHelperRouteSegmentRecorder(
            new File(RuneLite.RUNELITE_DIR, "drews-route-segments.txt"));
        // Learned once, then reused - no need to re-measure the stamina unit every session.
        staminaTicksPerUnit = parseStaminaUnit(
            configManager.getConfiguration(CONFIG_GROUP, STAMINA_UNIT_KEY));
        clearRouteBenchmark();
        routeBenchmarkObservedEdgeCounts.clear();
        overlayManager.remove(overlay);
        overlayManager.remove(routeMapOverlay);
        overlayManager.remove(routeMinimapOverlay);
        overlayManager.remove(routeTileOverlay);
        overlayManager.add(overlay);
        overlayManager.add(routeMapOverlay);
        overlayManager.add(routeMinimapOverlay);
        overlayManager.add(routeTileOverlay);
        removeWaypointMarkers();
        loadWaypoints();
        log.debug("Drew's Helper waypoint route UI started");
    }

    @Override
    protected void shutDown()
    {
        routeRequestId++;
        cancelRouteFuture();
        if (routeExecutor != null)
        {
            routeExecutor.shutdownNow();
            routeExecutor = null;
        }
        routeSnapshot = DrewsHelperRouteSnapshot.disabled();
        clearRouteBenchmark();
        if (routeSegmentRecorder != null)
        {
            routeSegmentRecorder.reset();
            routeSegmentRecorder = null;
        }
        removeWaypointMarkers();
        overlayManager.remove(routeTileOverlay);
        overlayManager.remove(routeMinimapOverlay);
        overlayManager.remove(routeMapOverlay);
        overlayManager.remove(overlay);
        log.debug("Drew's Helper waypoint route UI stopped");
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        tickCounter++;
        updateStaminaCalibration();
        markRouteDirtyOnCooldownMinuteChange();
        validateMapDataIfEnabled();
        recordTraversalIfSettled();
        writePlayerLevelsIfChanged();
        writeRouteLegsIfChanged();

        // The diagnostic clocks run before waypoint clearing and route re-solves. Arrival clears
        // the final waypoint later in this tick, and off-path movement may immediately submit a
        // replacement route, so the original journey capture must see this position first.
        updateEtaDebugCapture();
        recordRouteBenchmarkPosition();
        recordRouteSegmentIfEnabled();

        // Runs before the dirty check so arriving at a waypoint takes effect immediately,
        // rather than waiting for whatever solve is already queued.
        clearReachedWaypoints();

        if (routeDirty)
        {
            refreshRouteIfNeeded();
            return;
        }

        if (routeSnapshot.getStatus() == DrewsHelperRouteStatus.READY)
        {
            advanceCommittedRouteIfNeeded();
        }

        refreshTravelEstimate();
    }

    /**
     * Recomputed every tick rather than stored on the snapshot, so the ETA actually counts down
     * as you move and stretches back out if you burn energy faster than the model expected.
     * Cheap - one pass over the path.
     */
    private void refreshTravelEstimate()
    {
        DrewsHelperRouteSnapshot snapshot = routeSnapshot;
        DrewsHelperWalkingRouteEngine engine = routeEngine;
        if (engine == null || snapshot.getStatus() != DrewsHelperRouteStatus.READY)
        {
            travelEstimate = DrewsHelperTravelEstimate.EMPTY;
            return;
        }

        travelEstimate = DrewsHelperTravelEstimate.estimate(
            snapshot.getPath(),
            snapshot.getDestinations(),
            engine.getTransportGraph(),
            buildCapability()
        );
    }

    DrewsHelperTravelEstimate getTravelEstimate()
    {
        return travelEstimate;
    }

    /**
     * Route A - checks our shipped walking data against the game's own collision flags.
     *
     * <p>Runs when the scene changes, then re-checks the same scene at a slow interval. A
     * closed door is not evidence: the live client says blocked and our map says blocked, so
     * they agree. The mismatch only exists while the door is open, which means validating a
     * scene exactly once on arrival cannot see any door the player opens afterwards, which is
     * why a full castle sweep could produce no usable door evidence.
     *
     * <p>Off unless switched on: this is the check that keeps Route B honest, not a gameplay
     * feature. What it reports becomes rows in transport-overrides.tsv.
     */
    private void validateMapDataIfEnabled()
    {
        if (!config().validateMapData() || collisionMap == null)
        {
            lastValidatedSceneKey = null;
            lastValidationTick = 0;
            return;
        }

        int baseX = client.getBaseX();
        int baseY = client.getBaseY();
        int plane = client.getPlane();
        String sceneKey = baseX + ":" + baseY + ":" + plane;
        if (sceneKey.equals(lastValidatedSceneKey)
            && tickCounter - lastValidationTick < VALIDATION_REVALIDATE_TICKS)
        {
            return;
        }
        lastValidatedSceneKey = sceneKey;
        lastValidationTick = tickCounter;

        CollisionData[] collision = client.getCollisionMaps();
        if (collision == null)
        {
            return;
        }

        // Every plane of the loaded scene, not just the one being stood on. The upper planes are
        // where the shipped map is worst - the first scene measured both ways was 5.31x more
        // permissive on plane 1 than 1.55x on plane 0 - and reaching them by standing on them
        // would mean climbing every staircase in the game. The client already holds all four.
        for (int scenePlane = 0; scenePlane < collision.length; scenePlane++)
        {
            validateScenePlane(collision, baseX, baseY, scenePlane);
        }
    }

    /**
     * Validates one plane of the loaded scene against the client's own collision flags.
     *
     * <p>Split out of {@link #validateMapDataIfEnabled()} so every plane is checked from wherever
     * the player is standing. The per-scene summary row carries the plane, so a plane the client
     * has not populated announces itself as an implausible count rather than as silence - which
     * matters, because silence is exactly what hid the under-block half for as long as it did.
     */
    private void validateScenePlane(CollisionData[] collision, int baseX, int baseY, int plane)
    {
        if (plane < 0 || plane >= collision.length || collision[plane] == null)
        {
            return;
        }

        String sceneKey = baseX + ":" + baseY + ":" + plane;
        int[][] flags = collision[plane].getFlags();
        writeLiveFlagsIfNeeded(sceneKey, flags, baseX, baseY, plane);
        DrewsHelperMapValidator.Report report = DrewsHelperMapValidator.validate(
            flags, baseX, baseY, plane, collisionMap);

        if (report.isCoverageHole())
        {
            log.info("DREW_MAP_VALIDATE scene {} NO COVERAGE - our map has no data for this region",
                sceneKey);
            return;
        }
        if (report.getMismatches().isEmpty())
        {
            return;
        }

        int weBlockGameAllows = 0;
        int gameBlocksWeAllow = 0;
        for (DrewsHelperMapValidator.Mismatch mismatch : report.getMismatches())
        {
            if (mismatch.getKind() == DrewsHelperMapValidator.Kind.OURS_BLOCKS_LIVE_OPEN)
            {
                weBlockGameAllows++;
            }
            else
            {
                gameBlocksWeAllow++;
            }
        }
        writeValidationMismatches(report, sceneKey, weBlockGameAllows, gameBlocksWeAllow);
        log.info("DREW_MAP_VALIDATE scene {} tiles={} mismatches={} (overblock={} underblock={})",
            sceneKey, report.getTilesChecked(), report.getMismatches().size(),
            weBlockGameAllows, gameBlocksWeAllow);

        // Under-blocks first. They are the rare half and the only half that can explain a route
        // crossing something solid, so a dense over-blocking scene must not consume the whole cap
        // before one of them is shown.
        int printed = logMismatches(report, DrewsHelperMapValidator.Kind.OURS_OPEN_LIVE_BLOCKS, 0);
        printed = logMismatches(report, DrewsHelperMapValidator.Kind.OURS_BLOCKS_LIVE_OPEN, printed);
        int suppressed = report.getMismatches().size() - printed;
        if (suppressed > 0)
        {
            log.info("DREW_MAP_VALIDATE   ... {} more suppressed", suppressed);
        }
    }

    /**
     * Logs mismatches of one kind, continuing from {@code printed} and stopping at the shared cap.
     * Returns the new running total so the caller can carry one cap across both kinds.
     */
    private int logMismatches(
        DrewsHelperMapValidator.Report report, DrewsHelperMapValidator.Kind kind, int printed)
    {
        for (DrewsHelperMapValidator.Mismatch mismatch : report.getMismatches())
        {
            if (printed >= MAX_VALIDATION_ROWS_LOGGED)
            {
                return printed;
            }
            if (mismatch.getKind() != kind)
            {
                continue;
            }
            log.info("DREW_MAP_VALIDATE   {}", mismatch);
            printed++;
        }
        return printed;
    }

    /**
     * Appends the account's real (unboosted) levels whenever they change.
     *
     * <p>Real levels, not boosted ones, because a potion moves what you can hit right now but not
     * what a transport requirement is checked against - the same value the capability snapshot
     * uses. Append-only and change-gated, so the file is a history rather than a current-state
     * blob: a route recorded last week can be read against the levels held at the time.
     *
     * <p>TEMPORARY INSTRUMENTATION. This is a development aid, not a feature, and it runs
     * unconditionally rather than behind the data-quality toggle so a requirement question can
     * be answered from stored state instead of asking. Remove it - and the route leg record -
     * before this plugin is called finished. Agreed 2026-08-12; parked item 32.
     */
    private void writePlayerLevelsIfChanged()
    {
        if (tickCounter % PLAYER_LEVEL_POLL_TICKS != 0
            || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        StringBuilder row = new StringBuilder("DREW_LEVELS v1");
        int total = 0;
        for (Skill skill : Skill.values())
        {
            int level = client.getRealSkillLevel(skill);
            total += level;
            row.append(" ").append(skill.name()).append("=").append(level);
        }
        row.append(" TOTAL=").append(total);

        String line = row.toString();
        if (line.equals(lastPlayerLevelsRow))
        {
            return;
        }
        lastPlayerLevelsRow = line;

        List<String> rows = new ArrayList<>();
        rows.add(line);
        try
        {
            Files.write(new File(RuneLite.RUNELITE_DIR, "drews-player-levels.txt").toPath(),
                rows, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException ex)
        {
            if (!playerLevelsWriteWarned)
            {
                playerLevelsWriteWarned = true;
                log.warn("Drew's Helper: could not write the player level record", ex);
            }
        }
    }

    private void writeLiveFlagsIfNeeded(
        String sceneKey, int[][] flags, int baseX, int baseY, int plane)
    {
        if (flags == null || dumpedLiveFlagSceneKeys.contains(sceneKey))
        {
            return;
        }

        // The north and east edges of a tile are decided partly by the neighbouring tile, so the
        // final row and column of the scene have no neighbour to consult and cannot be measured.
        // They are excluded rather than reported as passable: a consumer reading absence as
        // "passable" would otherwise silently ingest a ring of false ground truth. "covered" is
        // the exclusive offset bound, so absence is only meaningful strictly inside it.
        final int covered = DrewsHelperMapValidator.SCENE_SIZE - 1;

        List<String> lines = new ArrayList<>();
        lines.add("DREW_LIVE_FLAGS scene " + sceneKey
            + " size=" + DrewsHelperMapValidator.SCENE_SIZE
            + " covered=" + covered);
        // Absence inside the covered area means both stored edges are passable, not missing.
        for (int sx = 0; sx < covered; sx++)
        {
            for (int sy = 0; sy < covered; sy++)
            {
                int mask = DrewsHelperMapValidator.liveBlockedMask(flags, sx, sy);
                if (mask == 0)
                {
                    continue;
                }

                String blockedEdges = ((mask & 1) != 0 ? "1" : "0")
                    + ((mask & 2) != 0 ? "1" : "0");
                // The final token is the client's raw collision flag word; it is additive,
                // and older captures simply lack it.
                lines.add((baseX + sx) + "," + (baseY + sy) + "," + plane
                    + " " + blockedEdges + " " + Integer.toString(flags[sx][sy]));
            }
        }

        try
        {
            Files.write(new File(RuneLite.RUNELITE_DIR, "drews-live-flags.txt").toPath(),
                lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            dumpedLiveFlagSceneKeys.add(sceneKey);
        }
        catch (IOException ex)
        {
            if (!liveFlagFileWriteWarned)
            {
                liveFlagFileWriteWarned = true;
                log.warn("Drew's Helper: could not write live collision dump rows", ex);
            }
        }
    }

    /**
     * The live collision dump is an accumulating record, not per-session scratch. Coverage is only
     * worth collecting if it survives a client restart, and start-up used to delete the file
     * outright. The dumped-scene set is rebuilt from the file's own headers rather than persisted
     * separately, so archiving or deleting the file is all that is needed to begin a clean capture.
     */
    private void seedDumpedLiveFlagSceneKeys()
    {
        File dump = new File(RuneLite.RUNELITE_DIR, "drews-live-flags.txt");
        if (!dump.isFile())
        {
            return;
        }

        int seeded = 0;
        try (BufferedReader reader = Files.newBufferedReader(dump.toPath(), StandardCharsets.UTF_8))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String sceneKey = parseLiveFlagSceneKey(line);
                if (sceneKey != null && dumpedLiveFlagSceneKeys.add(sceneKey))
                {
                    seeded++;
                }
            }
        }
        catch (IOException ex)
        {
            // A dump we cannot read is no reason to stop recording. Re-dumping a scene costs disk
            // but not correctness: the builder keys tiles by coordinate and collapses duplicates.
            log.warn("Drew's Helper: could not read the existing live collision dump;"
                + " already-captured scenes may be recorded again", ex);
            return;
        }

        log.info("Drew's Helper: live collision dump holds {} scenes in {} KB - appending to it,"
            + " not resetting it", seeded, dump.length() / 1024);
    }

    /**
     * Returns the {@code baseX:baseY:plane} key of a live-flag scene header, or null for any other
     * line. Static and package-private so the parse is testable without touching a filesystem.
     */
    static String parseLiveFlagSceneKey(String line)
    {
        if (line == null)
        {
            return null;
        }

        Matcher header = LIVE_FLAG_SCENE_HEADER.matcher(line);
        if (!header.matches())
        {
            return null;
        }
        return header.group(1);
    }

    /**
     * Records what an object interaction actually did, so transport rows can be checked against
     * observed behaviour instead of against themselves. Gated on the same data-quality toggle as
     * the collision capture rather than adding a second switch to find.
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (traversalRecorder == null || !config().validateMapData())
        {
            return;
        }
        if (!isSceneObjectAction(event.getMenuAction()))
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return;
        }

        WorldPoint objectTile = new WorldPoint(
            client.getBaseX() + event.getParam0(),
            client.getBaseY() + event.getParam1(),
            client.getPlane());
        traversalRecorder.recordClick(event.getMenuOption(), event.getMenuTarget(), event.getId(),
            objectTile, localPlayer.getWorldLocation(), tickCounter);
    }

    /**
     * Only scene-object interactions can correspond to a transport edge. Walking, item use and
     * dialogue would add rows no edge could ever explain, which is noise rather than evidence.
     */
    private static boolean isSceneObjectAction(MenuAction action)
    {
        return action == MenuAction.GAME_OBJECT_FIRST_OPTION
            || action == MenuAction.GAME_OBJECT_SECOND_OPTION
            || action == MenuAction.GAME_OBJECT_THIRD_OPTION
            || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
            || action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
    }

    private void recordTraversalIfSettled()
    {
        if (traversalRecorder == null || !config().validateMapData())
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            traversalRecorder.reset();
            return;
        }

        String line = traversalRecorder.onTick(
            localPlayer.getWorldLocation(), tickCounter, getTransportGraph());
        if (line != null)
        {
            log.info("{}", line);
        }
    }

    /**
     * Records one row per clicked walk segment, using RuneLite's current local destination as the
     * segment boundary. This is deliberately independent of `Log Benchmark Movement`: the
     * benchmark answers "did this whole route match?", while segment rows answer "which player
     * click disagreed with which slice of the displayed route?".
     */
    private void recordRouteSegmentIfEnabled()
    {
        if (routeSegmentRecorder == null)
        {
            return;
        }

        if (!config().routeSegmentValidationEnabled())
        {
            routeSegmentRecorder.reset();
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            routeSegmentRecorder.reset();
            return;
        }

        List<String> lines = routeSegmentRecorder.onTick(
            localPlayer.getWorldLocation(),
            currentWalkDestination(),
            routeSnapshot,
            routeEngine,
            tickCounter
        );
        for (String line : lines)
        {
            log.info("{}", line);
        }
    }

    private WorldPoint currentWalkDestination()
    {
        LocalPoint destination = client.getLocalDestinationLocation();
        return destination == null ? null : WorldPoint.fromLocalInstance(client, destination);
    }

    /**
     * Appends the transport hops the current route uses, with the tiles they run between.
     *
     * <p>The HUD's Actions rows carry the label alone, so "Jump Wall" on a 326-tile route cannot
     * be told from any other wall - which is exactly what a route-crossed-a-wall report asks. The
     * interesting row is the one with no matching edge: the router moved between two tiles that no
     * transport connects, which means it reached one of them some other way.
     *
     * <p>Pure-walk routes are recorded too, with legs=0. Absence of a row must mean "no route",
     * never "a route with nothing interesting in it" - the second reading is what made a
     * wall-clipping report untraceable.
     *
     * <p>TEMPORARY INSTRUMENTATION, same as the level record - see parked item 32.
     */
    private void writeRouteLegsIfChanged()
    {
        if (tickCounter % ROUTE_LEG_POLL_TICKS != 0)
        {
            return;
        }

        DrewsHelperRouteSnapshot snapshot = routeSnapshot;
        DrewsHelperTransportGraph graph = getTransportGraph();
        if (snapshot == null || graph == null || !snapshot.hasPath())
        {
            return;
        }

        List<WorldPoint> path = snapshot.getPath();
        List<String> legs = new ArrayList<>();
        for (int i = 1; i < path.size(); i++)
        {
            WorldPoint from = path.get(i - 1);
            WorldPoint to = path.get(i);
            if (!DrewsHelperRouteSnapshot.isTransportJump(from, to))
            {
                continue;
            }

            String label = DrewsHelperTravelEstimate.transportLabel(graph, from, to);
            // The path index is deliberately NOT recorded: it shifts as walked tiles are consumed,
            // and a signature that moves every tick would append the same route over and over.
            legs.add("DREW_ROUTELEG v1 #" + (legs.size() + 1)
                + " from=" + pointText(from)
                + " to=" + pointText(to)
                + " objId=" + DrewsHelperTravelEstimate.targetId(graph, from, to)
                + " label=" + (label == null ? "NO_MATCHING_EDGE" : label));
        }

        // Every route is recorded, including one that is pure walking. Returning early on an
        // empty leg list made the recorder blind to exactly the reports it exists to answer: a
        // route that clips a house wall uses no ladder, door or teleport, so it produced no row
        // at all and there was nothing to match a screenshot against.
        //
        // Keyed on the destinations and the hops - NOT on the route solve and NOT on the path.
        // routeSignature carries the player's exact tile (see appendPoint), so it changes on
        // every step, and path.size() shrinks as walked tiles are consumed; either would append
        // the same route once per poll for the whole journey. Destinations change when a waypoint
        // is placed or cleared, which is when a route is genuinely a different one.
        List<WorldPoint> destinations = snapshot.getDestinations();
        String legKey = destinations.toString() + legs.toString();
        if (legKey.equals(lastRouteLegsBlock))
        {
            return;
        }
        lastRouteLegsBlock = legKey;

        List<String> rows = new ArrayList<>();
        rows.add("DREW_ROUTELEG v1 route tick=" + tickCounter
            + " legs=" + legs.size()
            + " path=" + path.size()
            + " from=" + pointText(path.get(0))
            + " dest=" + (destinations.isEmpty()
                ? "-" : pointText(destinations.get(destinations.size() - 1))));
        rows.addAll(legs);

        try
        {
            Files.write(new File(RuneLite.RUNELITE_DIR, "drews-route-legs.txt").toPath(),
                rows, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException ex)
        {
            if (!routeLegsWriteWarned)
            {
                routeLegsWriteWarned = true;
                log.warn("Drew's Helper: could not write the route leg record", ex);
            }
        }
    }

    private static String pointText(WorldPoint point)
    {
        if (point == null)
        {
            return "-";
        }
        return point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    private void writeValidationMismatches(
        DrewsHelperMapValidator.Report report,
        String sceneKey,
        int weBlockGameAllows,
        int gameBlocksWeAllow
    )
    {
        List<String> lines = new ArrayList<>();
        Set<String> pendingLines = new HashSet<>();

        // The per-scene totals used to exist only as a log line, and a dev run's console is not
        // captured anywhere, so the counts died with the window. They belong in the file that
        // outlives the session, alongside the rows they summarise.
        String summary = "DREW_MAP_VALIDATE scene " + sceneKey
            + " tiles=" + report.getTilesChecked()
            + " mismatches=" + report.getMismatches().size()
            + " overblock=" + weBlockGameAllows
            + " underblock=" + gameBlocksWeAllow;
        if (!emittedValidationLines.contains(summary) && pendingLines.add(summary))
        {
            lines.add(summary);
        }

        // Both kinds are recorded. Filtering to the over-blocks here is what left the file unable
        // to answer "did our map let a route through something solid": the validator computes that
        // half and it was discarded before anything reached disk. The kind is in the row text, so
        // a reader can still take either half on its own.
        for (DrewsHelperMapValidator.Mismatch mismatch : report.getMismatches())
        {
            String line = "DREW_MAP_VALIDATE   " + mismatch.toString();
            if (emittedValidationLines.contains(line) || !pendingLines.add(line))
            {
                continue;
            }

            if (emittedValidationLines.size() + lines.size() >= MAX_VALIDATION_ROWS_WRITTEN)
            {
                if (!validationWriteLimitWarned)
                {
                    validationWriteLimitWarned = true;
                    log.warn("DREW_MAP_VALIDATE reached {} unique proof rows; suppressing additional rows",
                        MAX_VALIDATION_ROWS_WRITTEN);
                }
                break;
            }

            lines.add(line);
        }

        if (lines.isEmpty())
        {
            return;
        }

        try
        {
            Files.write(new File(RuneLite.RUNELITE_DIR, "drews-map-validate.txt").toPath(),
                lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            emittedValidationLines.addAll(lines);
        }
        catch (IOException ex)
        {
            if (!validationFileWriteWarned)
            {
                validationFileWriteWarned = true;
                log.warn("Drew's Helper: could not write map validation proof rows", ex);
            }
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        lastMenuOpenedPoint = client.getMouseCanvasPosition();
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!isMouseOverWorldMap())
        {
            return;
        }

        if (getPlacedWaypointCount() > 0)
        {
            addMenuEntry(event, CLEAR, ALL_WAYPOINTS_TARGET, 0);
        }

        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            addMenuEntry(event, waypointMenuOption(waypoints[index]), waypointLabel(index), 0);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()) || event.getKey() == null)
        {
            return;
        }

        if (isWaypointPositionConfigKey(event.getKey()))
        {
            // isWaypointPositionConfigKey only checks the prefix and suffix, so a key such as
            // waypoint9Position passes it while resolving to -1. Indexing waypoints[-1] would
            // throw inside an event handler, so the index is verified before it is used.
            int index = waypointPositionIndex(event.getKey());
            if (index >= 0)
            {
                WorldPoint decoded = WaypointPositionCodec.decode(
                    configManager.getConfiguration(CONFIG_GROUP, event.getKey()));
                if (decoded == null)
                {
                    waypoints[index] = null;
                    waypointArmed[index] = false;
                    syncWaypointMarker(index);
                }
                else if (!decoded.equals(waypoints[index]))
                {
                    setWaypoint(index, decoded);
                }
            }
            refreshWaypointMarkers();
            markRouteDirty();
        }

        if (isWaypointColorConfigKey(event.getKey()))
        {
            refreshWaypointMarkers();
        }

        if ("pathingReplacementEnabled".equals(event.getKey())
            || "routeBenchmarkEnabled".equals(event.getKey())
            || isTransportConfigKey(event.getKey()))
        {
            markRouteDirty();
        }

        if ("routeSegmentValidationEnabled".equals(event.getKey())
            && routeSegmentRecorder != null)
        {
            routeSegmentRecorder.reset();
        }
    }

    @Provides
    DrewsHelperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DrewsHelperConfig.class);
    }

    int getPlacedWaypointCount()
    {
        int count = 0;
        for (WorldPoint waypoint : waypoints)
        {
            if (waypoint != null)
            {
                count++;
            }
        }
        return count;
    }

    public WorldPoint getWaypoint(int index)
    {
        if (index < 0 || index >= MAX_WAYPOINTS)
        {
            return null;
        }
        return waypoints[index];
    }

    /** Exposed so the overlay can resolve which NPC or object a transport hop belongs to. */
    public DrewsHelperTransportGraph getTransportGraph()
    {
        return routeEngine == null ? null : routeEngine.getTransportGraph();
    }

    public DrewsHelperRouteSnapshot getRouteSnapshot()
    {
        return routeSnapshot;
    }

    public String getRouteBenchmarkSummary()
    {
        return routeBenchmarkSummary;
    }

    private void loadWaypoints()
    {
        Arrays.fill(waypoints, null);
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            waypoints[index] = WaypointPositionCodec.decode(
                configManager.getConfiguration(CONFIG_GROUP, waypointPositionKey(index)));
            syncWaypointMarker(index);
        }
        markRouteDirty();
    }

    private void setWaypoint(int index, WorldPoint point)
    {
        if (point == null || index < 0 || index >= MAX_WAYPOINTS)
        {
            return;
        }

        WorldPoint requested = point;
        point = snapToTraversable(requested);
        if (!point.equals(requested))
        {
            log.debug("{} requested at {} is not standable - snapped to {}",
                waypointLabel(index), requested, point);
        }

        waypoints[index] = point;
        waypointArmed[index] = false;
        configManager.setConfiguration(CONFIG_GROUP, waypointPositionKey(index), WaypointPositionCodec.encode(point));
        syncWaypointMarker(index);
        markRouteDirty();
        log.debug("Set {} at {}", waypointLabel(index), point);
    }

    /**
     * Keeps waypoints on tiles the character can stand on. Clicking a river or the inside of a
     * wall otherwise produces a destination the router can never reach, and the only symptom is
     * a route that quietly refuses to solve.
     *
     * <p>If the collision data is not loaded yet the request is honoured unchanged - snapping is
     * a convenience, and it must never block placing a waypoint.
     */
    private WorldPoint snapToTraversable(WorldPoint requested)
    {
        try
        {
            if (collisionMap == null)
            {
                collisionMap = DrewsHelperCollisionMap.loadDefault();
            }
            return DrewsHelperTraversableTiles.nearest(collisionMap, requested);
        }
        catch (IOException ex)
        {
            log.warn("Drew's Helper: collision data unavailable, placing waypoint as clicked", ex);
            return requested;
        }
    }

    private void clearWaypoints()
    {
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            clearWaypoint(index);
        }
        log.debug("Cleared Drew's Helper waypoints");
    }

    private void clearWaypoint(int index)
    {
        if (index < 0 || index >= MAX_WAYPOINTS)
        {
            return;
        }

        waypoints[index] = null;
        waypointArmed[index] = false;
        configManager.unsetConfiguration(CONFIG_GROUP, waypointPositionKey(index));
        syncWaypointMarker(index);
        markRouteDirty();
        log.debug("Cleared {}", waypointLabel(index));
    }

    /**
     * Drops a waypoint the moment the player stands on it - once it is reached there is no route
     * left to show for it.
     *
     * <p>Slots are cleared in place rather than renumbered. The slot is chosen by the user from
     * the menu, and each slot owns a colour, so compacting the list would silently recolour and
     * renumber the waypoints still ahead of them mid-journey.
     */
    private void clearReachedWaypoints()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            return;
        }

        WorldPoint here = localPlayer.getWorldLocation();
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            if (reachedWaypoint(waypoints[index], here, waypointArmed, index))
            {
                log.debug("Reached {} at {} - clearing it", waypointLabel(index), here);
                clearWaypoint(index);
            }
        }
    }

    /**
     * Whether one slot counts as reached this tick, updating its armed flag as a side effect.
     *
     * <p>Standing anywhere other than the waypoint arms it. Only an armed waypoint clears on
     * arrival, which is what stops a waypoint placed on your own tile from deleting itself a
     * tick later. Static and array-driven so the rule is testable without a client.
     */
    static boolean reachedWaypoint(WorldPoint waypoint, WorldPoint here, boolean[] armed, int index)
    {
        if (waypoint == null || here == null)
        {
            return false;
        }

        if (!waypoint.equals(here))
        {
            armed[index] = true;
            return false;
        }
        return armed[index];
    }

    /**
     * The slot number behind a route leg. Destinations are the placed waypoints in slot order, so
     * leg 0 is the first non-empty slot - which is not slot 0 if you only placed Waypoint #3, or
     * once an earlier waypoint has been reached and cleared.
     */
    public int waypointSlotForLeg(int legIndex)
    {
        int seen = 0;
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            if (waypoints[index] == null)
            {
                continue;
            }
            if (seen == legIndex)
            {
                return index;
            }
            seen++;
        }
        return legIndex;
    }

    private void markRouteDirty()
    {
        routeDirty = true;
    }

    private void refreshRouteIfNeeded()
    {
        if (!config().pathingReplacementEnabled())
        {
            routeDirty = false;
            lastRouteSignature = "";
            cancelRouteFuture();
            clearRouteBenchmark();
            routeSnapshot = DrewsHelperRouteSnapshot.disabled();
            return;
        }

        List<WorldPoint> destinations = orderedWaypointDestinations();
        if (destinations.isEmpty())
        {
            routeDirty = false;
            lastRouteSignature = "";
            cancelRouteFuture();
            clearRouteBenchmark();
            routeSnapshot = DrewsHelperRouteSnapshot.noWaypoints();
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            routeDirty = true;
            lastRouteSignature = "";
            cancelRouteFuture();
            clearRouteBenchmark();
            routeSnapshot = DrewsHelperRouteSnapshot.noPlayer();
            return;
        }

        WorldPoint start = localPlayer.getWorldLocation();
        DrewsHelperTransportPolicy transportPolicy = transportPolicy();
        // onGameTick runs on the client thread, which is the only place account state may be read.
        DrewsHelperPlayerCapability capability = buildCapability();
        boolean benchmarkMovement = config().routeBenchmarkEnabled();
        String signature = routeSignature(start, destinations, transportPolicy, capability, benchmarkMovement);
        if (!routeDirty && signature.equals(lastRouteSignature))
        {
            return;
        }

        submitRoute(start, destinations, signature, transportPolicy, capability);
    }

    private void advanceCommittedRouteIfNeeded()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            routeDirty = true;
            lastRouteSignature = "";
            cancelRouteFuture();
            clearRouteBenchmark();
            routeSnapshot = DrewsHelperRouteSnapshot.noPlayer();
            return;
        }

        DrewsHelperRouteSnapshot snapshot = routeSnapshot;
        CommittedRouteProgress progress = committedRouteProgress(
            snapshot.getPath(),
            localPlayer.getWorldLocation(),
            ROUTE_RECALCULATE_OFF_PATH_DISTANCE
        );
        if (progress.shouldRecalculate())
        {
            markRouteDirty();
            refreshRouteIfNeeded();
            return;
        }

        if (progress.getConsumeCount() > 0)
        {
            routeSnapshot = snapshot.consumeLeadingPathTiles(progress.getConsumeCount());
        }
    }

    private List<WorldPoint> orderedWaypointDestinations()
    {
        List<WorldPoint> destinations = new ArrayList<>();
        for (WorldPoint waypoint : waypoints)
        {
            if (waypoint != null)
            {
                destinations.add(waypoint);
            }
        }
        return destinations;
    }

    private void submitRoute(
        WorldPoint start,
        List<WorldPoint> destinations,
        String signature,
        DrewsHelperTransportPolicy transportPolicy,
        DrewsHelperPlayerCapability capability
    )
    {
        if (routeExecutor == null)
        {
            return;
        }

        cancelRouteFuture();
        int requestId = ++routeRequestId;
        List<WorldPoint> routeDestinations = new ArrayList<>(destinations);
        boolean keepRouteBenchmarkCapture = shouldKeepRouteBenchmarkCapture(routeDestinations);
        DrewsHelperRouteSnapshot previousSnapshot = routeSnapshot;
        routeSnapshot = DrewsHelperRouteSnapshot.calculating(routeDestinations, previousSnapshot.getPath());
        if (!keepRouteBenchmarkCapture)
        {
            clearRouteBenchmark();
        }
        lastRouteSignature = signature;
        routeDirty = false;

        routeFuture = routeExecutor.submit(() ->
        {
            DrewsHelperRouteSnapshot calculatedSnapshot;
            try
            {
                calculatedSnapshot = routeEngine(transportPolicy, capability).solve(
                    start,
                    routeDestinations
                );
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (RuntimeException | IOException ex)
            {
                log.warn("Failed to calculate Drew's route", ex);
                calculatedSnapshot = DrewsHelperRouteSnapshot.error(routeDestinations, ex.getMessage());
            }

            DrewsHelperRouteSnapshot publishSnapshot = calculatedSnapshot;
            clientThread.invokeLater(() ->
            {
                if (requestId == routeRequestId)
                {
                    routeSnapshot = publishSnapshot;
                    startRouteBenchmarkIfNeeded(publishSnapshot);
                }
                return true;
            });
        });
    }

    private synchronized DrewsHelperWalkingRouteEngine routeEngine(
        DrewsHelperTransportPolicy transportPolicy,
        DrewsHelperPlayerCapability capability
    ) throws IOException
    {
        if (collisionMap == null)
        {
            collisionMap = DrewsHelperCollisionMap.loadDefault();
        }

        String cacheKey = transportPolicy.signature() + '#' + capability.signature();
        if (routeEngine == null || !routeEngineCacheKey.equals(cacheKey))
        {
            routeEngine = new DrewsHelperWalkingRouteEngine(
                collisionMap,
                DrewsHelperTransportGraph.loadDefault(transportPolicy, capability),
                !transportPolicy.allowsWilderness()
            );
            routeEngineCacheKey = cacheKey;
        }
        return routeEngine;
    }

    /**
     * Snapshots the account state the router cares about. MUST be called on the client thread.
     * Skills, carried/equipped items, quest completion and unlock vars are all checked against
     * the transport resource before pathfinding sees an edge.
     */
    private DrewsHelperPlayerCapability buildCapability()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return DrewsHelperPlayerCapability.UNRESTRICTED;
        }

        DrewsHelperPlayerCapability.Builder builder = DrewsHelperPlayerCapability.builder();
        for (Skill skill : Skill.values())
        {
            builder.skill(skill.name(), client.getRealSkillLevel(skill));
        }

        addItems(builder, client.getItemContainer(InventoryID.INV));
        ItemContainer worn = client.getItemContainer(InventoryID.WORN);
        addItems(builder, worn);
        addTrackedItemRequirements(builder);
        addUnlockState(builder);

        return builder
            .weightKg(client.getWeight())
            .energyUnits(client.getEnergy())
            .running(client.getVarpValue(VarPlayerID.OPTION_RUN) == 1)
            .staminaActive(client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0)
            .ringOfEndurance(hasChargedRingOfEndurance(worn))
            .gracefulRestorePercent(gracefulRestorePercent(worn))
            .autoRunThresholdPercent(client.getVarbitValue(VarbitID.RUNENERGY_AUTOENABLE))
            .staminaTicksRemaining(staminaTicksRemaining())
            .currentEpochMinute(currentEpochMinute())
            .build();
    }

    private void markRouteDirtyOnCooldownMinuteChange()
    {
        if (client.getGameState() != GameState.LOGGED_IN || getPlacedWaypointCount() == 0)
        {
            return;
        }

        long minute = currentEpochMinute();
        if (minute != lastCooldownEpochMinute)
        {
            lastCooldownEpochMinute = minute;
            markRouteDirty();
        }
    }

    static long currentEpochMinute()
    {
        return System.currentTimeMillis() / 60_000L;
    }

    /**
     * Ticks of stamina left, or 0 while the unit is still unmeasured - which keeps the previous
     * behaviour of assuming the dose covers the whole route rather than inventing a number.
     */
    private int staminaTicksRemaining()
    {
        if (staminaTicksPerUnit <= 0)
        {
            return 0;
        }
        return Math.max(0, client.getVarbitValue(VarbitID.STAMINA_DURATION)) * staminaTicksPerUnit;
    }

    /**
     * Learns how many game ticks one unit of the stamina duration varbit represents, by watching
     * it count down. MUST be called on the client thread, once per tick.
     *
     * <p>The unit is not documented and RuneLite never converts this varbit anywhere, so guessing
     * it would put an unverified number straight into the ETA. Measuring costs nothing: hold a
     * dose for a few seconds and the answer falls out exactly.
     */
    private void updateStaminaCalibration()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        int duration = client.getVarbitValue(VarbitID.STAMINA_DURATION);
        if (duration > 0 && lastStaminaDuration > 0)
        {
            int measured = staminaTicksPerUnit(
                lastStaminaDuration, lastStaminaDurationTick, duration, tickCounter, staminaTicksPerUnit);
            if (measured != staminaTicksPerUnit)
            {
                staminaTicksPerUnit = measured;
                configManager.setConfiguration(CONFIG_GROUP, STAMINA_UNIT_KEY, measured);
                log.info("DREW_ROUTE_BENCH stamina calibrated: 1 duration unit = {} tick(s), "
                    + "current duration={} (~{} ticks left)", measured, duration, duration * measured);
            }
        }

        if (duration != lastStaminaDuration)
        {
            lastStaminaDuration = duration;
            lastStaminaDurationTick = tickCounter;
        }
    }

    /**
     * The measured tick-length of one stamina duration unit, or {@code known} if this sample
     * cannot establish it.
     *
     * <p>Only a drop of exactly one unit counts. A larger drop means ticks went unobserved -
     * lag, a hop, logging out - and would understate the interval. The 1..100 bound rejects
     * nonsense from a long gap between observations.
     */
    static int parseStaminaUnit(String stored)
    {
        try
        {
            int value = Integer.parseInt(String.valueOf(stored).trim());
            return value > 0 && value <= 100 ? value : 0;
        }
        catch (NumberFormatException ex)
        {
            return 0;
        }
    }

    static int staminaTicksPerUnit(int previousValue, int previousTick, int currentValue, int currentTick, int known)
    {
        if (previousValue - currentValue != 1)
        {
            return known;
        }

        int elapsed = currentTick - previousTick;
        if (elapsed < 1 || elapsed > 100)
        {
            return known;
        }
        return elapsed;
    }

    /**
     * Tracks every item requirement expression in the transport resource in the capability
     * signature. The edge filter itself still evaluates the exact requirement per edge; this
     * only prevents the cached filtered graph from surviving an inventory/equipment change that
     * flips a bare item-id, quantity or alternative requirement.
     */
    private void addTrackedItemRequirements(DrewsHelperPlayerCapability.Builder builder)
    {
        try
        {
            for (String requirement : DrewsHelperTransportGraph.requiredItemRequirements())
            {
                builder.trackedItemRequirement(requirement);
            }
        }
        catch (IOException ex)
        {
            // The graph resource is unreadable; routing will fail louder elsewhere. Leaving the
            // tracked item list empty preserves the previous cache behaviour for this one solve.
            log.warn("Drew's Helper: could not read transport item requirements", ex);
        }
    }

    /**
     * Snapshots quest completion and the unlock varbits/varplayers the transport data actually
     * references. MUST be called on the client thread.
     *
     * <p>The id and quest lists come from the data, not from a hardcoded table, so upstream can
     * add a requirement without a code change. Names that do not resolve to a RuneLite quest are
     * logged once and then left out - the capability treats an absent entry as satisfied, so a
     * data problem can never silently delete a route the player can actually use.
     */
    private void addUnlockState(DrewsHelperPlayerCapability.Builder builder)
    {
        try
        {
            Map<String, Quest> byName = questsByName();
            List<String> unresolved = new ArrayList<>();
            for (String name : DrewsHelperTransportGraph.requiredQuestNames())
            {
                Quest quest = byName.get(name.toLowerCase(Locale.ROOT));
                if (quest == null)
                {
                    unresolved.add(name);
                    continue;
                }
                builder.quest(name, quest.getState(client) == QuestState.FINISHED);
            }

            if (!unresolved.isEmpty() && !loggedUnresolvedQuests)
            {
                loggedUnresolvedQuests = true;
                log.warn("Drew's Helper: {} transport quest name(s) do not match RuneLite's quest "
                    + "list and are treated as satisfied: {}", unresolved.size(), unresolved);
            }

            for (int id : DrewsHelperTransportGraph.requiredVarbitIds())
            {
                builder.varbit(id, client.getVarbitValue(id));
            }
            for (int id : DrewsHelperTransportGraph.requiredVarPlayerIds())
            {
                builder.varPlayer(id, client.getVarpValue(id));
            }
        }
        catch (IOException ex)
        {
            // The graph resource is unreadable; routing will fail louder elsewhere. Leaving the
            // unlock maps empty keeps every edge satisfied rather than blocking the whole graph.
            log.warn("Drew's Helper: could not read transport unlock requirements", ex);
        }
    }

    private static Map<String, Quest> questsByName()
    {
        Map<String, Quest> byName = new HashMap<>();
        for (Quest quest : Quest.values())
        {
            byName.put(quest.getName().toLowerCase(Locale.ROOT), quest);
        }
        return byName;
    }

    /**
     * The charged and uncharged rings are separate items, so charge state is readable. We cannot
     * see the exact count, so a ring below the 500-charge threshold still reads as active.
     */
    private static boolean hasChargedRingOfEndurance(ItemContainer worn)
    {
        if (worn == null)
        {
            return false;
        }
        Item ring = worn.getItem(EquipmentInventorySlot.RING.getSlotIdx());
        return ring != null && ring.getId() == ItemID.RING_OF_ENDURANCE;
    }

    /**
     * Graceful gives a per-piece run-energy restoration bonus, not an all-or-nothing one:
     * hood 3, top 4, legs 4, gloves 3, boots 3, cape 3 = 20, plus 10 more for the complete set.
     *
     * <p>Matched on item name rather than id — there are 147 graceful item ids across the colour
     * variants, and new recolours keep appearing.
     */
    private int gracefulRestorePercent(ItemContainer worn)
    {
        if (worn == null)
        {
            return 0;
        }

        int percent = 0;
        int pieces = 0;
        for (Map.Entry<EquipmentInventorySlot, Integer> entry : GRACEFUL_SLOT_PERCENT.entrySet())
        {
            Item item = worn.getItem(entry.getKey().getSlotIdx());
            if (item == null || item.getId() <= 0)
            {
                continue;
            }

            ItemComposition composition = client.getItemDefinition(item.getId());
            if (composition == null || !composition.getName().toLowerCase(Locale.ROOT).contains("graceful"))
            {
                continue;
            }

            percent += entry.getValue();
            pieces++;
        }

        if (pieces == GRACEFUL_SLOT_PERCENT.size())
        {
            percent += 10;
        }
        return percent;
    }

    private static void addItems(DrewsHelperPlayerCapability.Builder builder, ItemContainer container)
    {
        if (container == null)
        {
            return;
        }
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() > 0)
            {
                builder.item(item.getId(), Math.max(1, item.getQuantity()));
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        // Fires on every XP drop, so key on the LEVEL. Keying on experience would rebuild
        // the route on every hit while training.
        Skill skill = event.getSkill();
        if (skill == null || !ROUTE_RELEVANT_SKILLS.contains(skill))
        {
            // A Cooking level cannot open or close a transport, so it must not cost a rebuild.
            return;
        }

        int level = client.getRealSkillLevel(skill);
        Integer previous = lastKnownSkillLevels.put(skill, level);
        if (previous != null && previous != level)
        {
            markRouteDirty();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        // Carried items gate canoes (axe), grapple shortcuts (crossbow + grapple) and paid ferries.
        int containerId = event.getContainerId();
        if (containerId == InventoryID.INV || containerId == InventoryID.WORN)
        {
            markRouteDirty();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            lastKnownSkillLevels.clear();
            markRouteDirty();
        }
    }

    /**
     * Which transport families the router may use. The checkbox is an attestation that
     * the family is unlocked on this account; per-edge requirement checking arrives with
     * the capability snapshot.
     */
    private DrewsHelperTransportPolicy transportPolicy()
    {
        // Only two families are still a choice. The rest are always loaded and gated per edge
        // by the capability snapshot, which knows the account's real skills, items, quests
        // and unlock varbits.
        DrewsHelperConfig config = config();
        return DrewsHelperTransportPolicy.builder()
            .wilderness(config.wildernessTransportsEnabled())
            .magicMushtrees(config.magicMushtreesUnlocked())
            .plantedSpiritTrees(config.plantedSpiritTreesUnlocked())
            .build();
    }

    private static boolean isTransportConfigKey(String key)
    {
        return "useWildernessTransports".equals(key)
            || "useMagicMushtrees".equals(key);
    }

    private void cancelRouteFuture()
    {
        if (routeFuture != null)
        {
            routeRequestId++;
            routeFuture.cancel(true);
            routeFuture = null;
        }
    }

    private static String routeSignature(
        WorldPoint start,
        List<WorldPoint> destinations,
        DrewsHelperTransportPolicy transportPolicy,
        DrewsHelperPlayerCapability capability,
        boolean benchmarkMovement
    )
    {
        StringBuilder signature = new StringBuilder();
        signature.append("transports=").append(transportPolicy.signature()).append('|');
        signature.append("account=").append(capability.signature()).append('|');
        signature.append(benchmarkMovement ? "benchmark=1|" : "benchmark=0|");
        appendPoint(signature, start);
        for (WorldPoint destination : destinations)
        {
            signature.append('|');
            appendPoint(signature, destination);
        }
        return signature.toString();
    }

    private static void appendPoint(StringBuilder signature, WorldPoint point)
    {
        signature.append(point.getX())
            .append(',')
            .append(point.getY())
            .append(',')
            .append(point.getPlane());
    }

    private void clearRouteBenchmark()
    {
        routeBenchmarkCapture = null;
        routeBenchmarkSummary = "";
        etaDebugCapture = null;
    }

    private boolean shouldKeepRouteBenchmarkCapture(List<WorldPoint> destinations)
    {
        RouteBenchmarkCapture capture = routeBenchmarkCapture;
        return config().routeBenchmarkEnabled()
            && capture != null
            && capture.hasStarted()
            && capture.matchesActiveDestinations(destinations);
    }

    /**
     * Predicted-versus-actual ETA check, driven off the benchmark's movement lifecycle.
     *
     * <p>The clock starts on the first tick the player actually moves, not when the route goes
     * ready, so time spent standing at the start does not count against the forecast.
     */
    private static final class EtaDebugCapture
    {
        private final int predictedTicks;
        private final String inputs;
        private final WorldPoint destination;
        private WorldPoint lastPosition;
        private boolean moving;
        private int elapsedTicks;

        EtaDebugCapture(int predictedTicks, String inputs, WorldPoint destination)
        {
            this.predictedTicks = predictedTicks;
            this.inputs = inputs;
            this.destination = destination;
        }

        String startLine()
        {
            return "eta predicted=" + predictedTicks + "t ("
                + DrewsHelperTravelEstimate.formatTicks(predictedTicks) + ") " + inputs;
        }

        /** Clocks a tick, starting the count on the first one where the player actually moved. */
        void onTick(WorldPoint here)
        {
            if (lastPosition != null && !lastPosition.equals(here))
            {
                moving = true;
            }
            lastPosition = here;
            if (moving)
            {
                elapsedTicks++;
            }
        }

        /** Arrival is judged against the route's own final tile, not the benchmark's progress. */
        boolean hasArrived(WorldPoint here)
        {
            return moving && destination != null && destination.equals(here);
        }

        String resultLine()
        {
            int delta = elapsedTicks - predictedTicks;
            String percent = predictedTicks > 0
                ? String.format("%+.1f%%", (100.0 * delta) / predictedTicks)
                : "n/a";

            return "eta result predicted=" + predictedTicks + "t ("
                + DrewsHelperTravelEstimate.formatTicks(predictedTicks) + ") actual="
                + elapsedTicks + "t (" + DrewsHelperTravelEstimate.formatTicks(elapsedTicks)
                + ") delta=" + (delta >= 0 ? "+" : "") + delta + "t " + percent
                + " | " + inputs;
        }
    }

    private void startRouteBenchmarkIfNeeded(DrewsHelperRouteSnapshot snapshot)
    {
        boolean keepRouteBenchmarkCapture = shouldKeepRouteBenchmarkCapture(snapshot.getDestinations());
        if (!keepRouteBenchmarkCapture)
        {
            clearRouteBenchmark();
        }
        if (snapshot.getStatus() != DrewsHelperRouteStatus.READY || !snapshot.hasPath())
        {
            return;
        }

        // ETA accuracy logging is independent of the movement benchmark. It is two lines per
        // journey, so it can stay on permanently and catch a forecast that starts drifting,
        // rather than only firing when someone remembered to enable the benchmark first.
        if (!keepRouteBenchmarkCapture)
        {
            startEtaDebugCapture(snapshot);
        }

        // Solve time and expanded-node count were already measured on every solve - they
        // were just only ever printed from inside the movement benchmark, so retiring that
        // switched off the one number the performance work needs. One line per solve.
        log.info("DREW_ROUTE_BENCH solve {} {}",
            searchMetricsSummary(snapshot.getPrimaryMetrics()),
            routeEngine == null ? "rank=?" : routeEngine.lastPhaseSummary());

        if (!config().routeBenchmarkEnabled())
        {
            return;
        }

        if (keepRouteBenchmarkCapture)
        {
            return;
        }

        routeBenchmarkCapture = new RouteBenchmarkCapture(
            snapshot.getPath(),
            snapshot.getDestinations(),
            routeEngine,
            routeBenchmarkObservedEdgeCounts
        );
        routeBenchmarkSummary = "Waiting for movement";
        log.info(
            "DREW_ROUTE_BENCH start route={}",
            searchMetricsSummary(snapshot.getPrimaryMetrics())
        );
        log.info("DREW_ROUTE_BENCH {}", routeBenchmarkCapture.startTraceLine());
    }

    /**
     * Snapshots the forecast while it still describes the whole journey. refreshTravelEstimate
     * recomputes from the player's current position every tick, so by arrival it reads zero and
     * would be useless as a comparison baseline.
     */
    private void startEtaDebugCapture(DrewsHelperRouteSnapshot snapshot)
    {
        List<WorldPoint> path = snapshot.getPath();
        if (path == null || path.isEmpty())
        {
            return;
        }

        DrewsHelperPlayerCapability capability = buildCapability();
        DrewsHelperTravelEstimate predicted = DrewsHelperTravelEstimate.estimate(
            path,
            snapshot.getDestinations(),
            routeEngine == null ? null : routeEngine.getTransportGraph(),
            capability
        );
        if (predicted.isEmpty())
        {
            return;
        }

        etaDebugCapture = new EtaDebugCapture(
            predicted.getTotalTicks(),
            DrewsHelperTravelEstimate.describeEnergyModel(capability),
            path.get(path.size() - 1)
        );
        log.info("DREW_ROUTE_BENCH {}", etaDebugCapture.startLine());
    }

    /**
     * Advances the predicted-versus-actual ETA clock and reports on arrival.
     *
     * <p>Runs every tick from the very top of {@code onGameTick}, deliberately independent of
     * route state and of the movement benchmark. Reaching the last waypoint clears it, which
     * dirties the route and short-circuits the rest of the tick, so anything checking arrival
     * later in the tick would never see it.
     */
    private void updateEtaDebugCapture()
    {
        EtaDebugCapture eta = etaDebugCapture;
        if (eta == null)
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            return;
        }

        WorldPoint here = localPlayer.getWorldLocation();
        eta.onTick(here);
        if (eta.hasArrived(here))
        {
            log.info("DREW_ROUTE_BENCH {}", eta.resultLine());
            etaDebugCapture = null;
        }
    }

    private void recordRouteBenchmarkPosition()
    {
        RouteBenchmarkCapture capture = routeBenchmarkCapture;
        if (capture == null)
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            return;
        }

        RouteBenchmarkUpdate update = capture.record(localPlayer.getWorldLocation());
        if (update == null)
        {
            return;
        }

        routeBenchmarkSummary = update.overlaySummary();
        log.info("DREW_ROUTE_BENCH {}", update.logLine());
        if (update.isComplete())
        {
            routeBenchmarkCapture = null;
        }
    }

    private static String searchMetricsSummary(DrewsHelperRouteSearchMetrics metrics)
    {
        return "found=" + metrics.isRouteFound()
            + " solve=" + formatMillis(metrics.getSolveTimeMillis())
            + " expanded=" + metrics.getExpandedNodes()
            + " steps=" + metrics.getRouteStepCount()
            + " turns=" + metrics.getTurnCount();
    }

    private static String formatMillis(double millis)
    {
        return String.format(Locale.ROOT, "%.2fms", millis);
    }

    static int routePathIndex(List<WorldPoint> path, WorldPoint point)
    {
        if (path == null || point == null)
        {
            return -1;
        }

        for (int index = 0; index < path.size(); index++)
        {
            if (point.equals(path.get(index)))
            {
                return index;
            }
        }
        return -1;
    }

    static CommittedRouteProgress committedRouteProgress(List<WorldPoint> path, WorldPoint point, int recalculateDistance)
    {
        if (path == null || path.isEmpty() || point == null)
        {
            return CommittedRouteProgress.recalculate();
        }

        int nearestIndex = -1;
        int nearestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < path.size(); index++)
        {
            WorldPoint routePoint = path.get(index);
            if (routePoint.getPlane() != point.getPlane())
            {
                continue;
            }

            int distance = tileDistance(routePoint, point);
            if (distance < nearestDistance)
            {
                nearestDistance = distance;
                nearestIndex = index;
            }
        }

        if (nearestIndex < 0 || nearestDistance > recalculateDistance)
        {
            return CommittedRouteProgress.recalculate();
        }

        int consumeCount = nearestDistance == 0 ? Math.max(0, nearestIndex) : 0;
        return CommittedRouteProgress.keep(consumeCount, nearestDistance);
    }

    private static int tileDistance(WorldPoint first, WorldPoint second)
    {
        return Math.max(
            Math.abs(first.getX() - second.getX()),
            Math.abs(first.getY() - second.getY())
        );
    }

    static final class CommittedRouteProgress
    {
        private final boolean recalculate;
        private final int consumeCount;
        private final int distanceFromRoute;

        private CommittedRouteProgress(boolean recalculate, int consumeCount, int distanceFromRoute)
        {
            this.recalculate = recalculate;
            this.consumeCount = consumeCount;
            this.distanceFromRoute = distanceFromRoute;
        }

        static CommittedRouteProgress recalculate()
        {
            return new CommittedRouteProgress(true, 0, Integer.MAX_VALUE);
        }

        static CommittedRouteProgress keep(int consumeCount, int distanceFromRoute)
        {
            return new CommittedRouteProgress(false, consumeCount, distanceFromRoute);
        }

        boolean shouldRecalculate()
        {
            return recalculate;
        }

        int getConsumeCount()
        {
            return consumeCount;
        }

        int getDistanceFromRoute()
        {
            return distanceFromRoute;
        }
    }

    static final class RouteBenchmarkCapture
    {
        private final List<WorldPoint> primaryPath;
        private final DrewsHelperWalkingRouteEngine routeEngine;
        private final Map<String, Integer> observedEdgeCounts;
        private final WorldPoint start;
        private final WorldPoint target;
        private final List<RouteBenchmarkSegment> segments;
        private final int maxMovementTicks;
        private final List<WorldPoint> actualPath = new ArrayList<>();
        private WorldPoint pendingLastPoint;
        private int pendingTicks;
        private int pendingMoves;
        private String observedEdgeKey;
        private int observedEdgeRepeatCount;
        private String additionalObservedEdgeKey;
        private int additionalObservedEdgeRepeatCount;

        RouteBenchmarkCapture(
            List<WorldPoint> primaryPath,
            DrewsHelperWalkingRouteEngine routeEngine,
            Map<String, Integer> observedEdgeCounts
        )
        {
            this(
                primaryPath,
                primaryPath == null || primaryPath.isEmpty()
                    ? Collections.emptyList()
                    : Collections.singletonList(primaryPath.get(primaryPath.size() - 1)),
                routeEngine,
                observedEdgeCounts
            );
        }

        RouteBenchmarkCapture(
            List<WorldPoint> primaryPath,
            List<WorldPoint> destinations,
            DrewsHelperWalkingRouteEngine routeEngine,
            Map<String, Integer> observedEdgeCounts
        )
        {
            this.primaryPath = new ArrayList<>(primaryPath);
            this.routeEngine = routeEngine;
            this.observedEdgeCounts = observedEdgeCounts;
            this.start = primaryPath.get(0);
            this.target = primaryPath.get(primaryPath.size() - 1);
            this.segments = buildSegments(this.primaryPath, destinations);
            this.maxMovementTicks = Math.max(
                50,
                DrewsHelperRouteBenchmark.pathDistance(primaryPath) + 25
            );
        }

        private String startTraceLine()
        {
            return "trace capture=pendingStart start=" + DrewsHelperRouteBenchmark.formatPoint(start)
                + " target=" + DrewsHelperRouteBenchmark.formatPoint(target)
                + " destinations=" + DrewsHelperRouteBenchmark.formatPathPrefix(
                    segmentTargets(),
                    MAX_WAYPOINTS
                )
                + " expectedPath=" + DrewsHelperRouteBenchmark.formatPath(primaryPath);
        }

        boolean hasStarted()
        {
            return !actualPath.isEmpty();
        }

        boolean matchesActiveDestinations(List<WorldPoint> destinations)
        {
            List<WorldPoint> targets = segmentTargets();
            List<WorldPoint> activeDestinations = destinations == null
                ? Collections.emptyList()
                : destinations;
            if (targets.equals(activeDestinations))
            {
                return true;
            }

            if (activeDestinations.isEmpty())
            {
                return false;
            }

            int offset = targets.size() - activeDestinations.size();
            return offset > 0 && targets.subList(offset, targets.size()).equals(activeDestinations);
        }

        RouteBenchmarkUpdate record(WorldPoint point)
        {
            if (actualPath.isEmpty())
            {
                return recordPendingStart(point);
            }

            if (actualPath.get(actualPath.size() - 1).equals(point))
            {
                return null;
            }

            actualPath.add(point);
            int movementTicks = actualPath.size() - 1;
            boolean reachedTarget = point.equals(target);
            boolean movementLimitReached = movementTicks >= maxMovementTicks;
            boolean shouldReport = reachedTarget
                || movementLimitReached
                || movementTicks == 1
                || movementTicks == 5
                || movementTicks == 10
                || movementTicks % 25 == 0;

            if (!shouldReport)
            {
                return null;
            }

            return new RouteBenchmarkUpdate(
                movementTicks,
                reachedTarget || movementLimitReached,
                reachedTarget ? "target" : movementLimitReached ? "limit" : "progress",
                DrewsHelperRouteBenchmark.compare(primaryPath, actualPath),
                routePathTrace(primaryPath, reachedTarget || movementLimitReached),
                routePathTrace(actualPath, reachedTarget || movementLimitReached),
                DrewsHelperRouteBenchmark.formatDivergence(primaryPath, actualPath, reachedTarget || movementLimitReached),
                candidateTrace(primaryPath, reachedTarget || movementLimitReached),
                edgeValidationTrace(primaryPath, reachedTarget || movementLimitReached),
                additionalDivergenceTrace(primaryPath, reachedTarget || movementLimitReached),
                shapeTrace(primaryPath, reachedTarget),
                shadowTrace(reachedTarget || movementLimitReached),
                shapeShadowTrace(reachedTarget || movementLimitReached)
            );
        }

        private RouteBenchmarkUpdate recordPendingStart(WorldPoint point)
        {
            if (point == null)
            {
                return null;
            }

            int routeIndex = routePathIndex(primaryPath, point);
            if (routeIndex >= 0 && routeIndex <= ROUTE_BENCHMARK_START_SYNC_TILE_LIMIT)
            {
                actualPath.addAll(primaryPath.subList(0, routeIndex + 1));
                return null;
            }

            pendingTicks++;
            if (pendingLastPoint == null)
            {
                pendingLastPoint = point;
            }
            else if (!pendingLastPoint.equals(point))
            {
                pendingMoves++;
                pendingLastPoint = point;
            }

            int startDistance = tileDistance(point, start);
            if (pendingTicks >= ROUTE_BENCHMARK_PENDING_START_TICK_LIMIT
                || pendingMoves >= ROUTE_BENCHMARK_PENDING_START_MOVE_LIMIT
                || startDistance > ROUTE_BENCHMARK_STALE_START_DISTANCE)
            {
                return RouteBenchmarkUpdate.ignored(
                    pendingTicks,
                    "stale-start",
                    "expectedStart=" + DrewsHelperRouteBenchmark.formatPoint(start)
                        + " current=" + DrewsHelperRouteBenchmark.formatPoint(point)
                        + " distance=" + startDistance
                        + " pendingMoves=" + pendingMoves
                );
            }

            return null;
        }

        private static String routePathTrace(List<WorldPoint> path, boolean complete)
        {
            return complete
                ? DrewsHelperRouteBenchmark.formatPath(path)
                : DrewsHelperRouteBenchmark.formatPathPrefix(path);
        }

        private String candidateTrace(List<WorldPoint> predictedPath, boolean actualComplete)
        {
            int divergenceIndex = DrewsHelperRouteBenchmark.firstDivergenceIndex(
                predictedPath,
                actualPath,
                actualComplete
            );
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            return candidateTraceAt(predictedPath, divergenceIndex);
        }

        private String candidateTraceAt(List<WorldPoint> predictedPath, int divergenceIndex)
        {
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            WorldPoint from = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex - 1);
            WorldPoint predicted = DrewsHelperRouteBenchmark.pointAt(predictedPath, divergenceIndex);
            WorldPoint actual = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex);
            RouteBenchmarkSegment segment = segmentForPathIndex(divergenceIndex);
            WorldPoint segmentTarget = segment.getTarget();
            if (from == null)
            {
                return "none";
            }

            return "from=" + DrewsHelperRouteBenchmark.formatPoint(from)
                + " target=" + DrewsHelperRouteBenchmark.formatPoint(segmentTarget)
                + finalTargetTrace(segmentTarget)
                + " candidates=" + DrewsHelperRouteBenchmark.formatMoveCandidates(
                    routeEngine.moveCandidates(from, segmentTarget),
                    predicted,
                    actual
                );
        }

        private String edgeValidationTrace(List<WorldPoint> predictedPath, boolean actualComplete)
        {
            int divergenceIndex = DrewsHelperRouteBenchmark.firstDivergenceIndex(
                predictedPath,
                actualPath,
                actualComplete
            );
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            return edgeValidationTraceAt(predictedPath, divergenceIndex, true);
        }

        private String edgeValidationTraceAt(
            List<WorldPoint> predictedPath,
            int divergenceIndex,
            boolean primaryDivergence
        )
        {
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            WorldPoint from = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex - 1);
            WorldPoint actual = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex);
            RouteBenchmarkSegment segment = segmentForPathIndex(divergenceIndex);
            WorldPoint segmentTarget = segment.getTarget();
            if (from == null || actual == null)
            {
                return "none";
            }

            String key = edgeKey(from, actual, segmentTarget);
            int repeatCount;
            if (primaryDivergence)
            {
                if (observedEdgeKey == null || !observedEdgeKey.equals(key))
                {
                    observedEdgeKey = key;
                    observedEdgeRepeatCount = observedEdgeCounts.merge(key, 1, Integer::sum);
                }
                repeatCount = observedEdgeRepeatCount;
            }
            else
            {
                if (additionalObservedEdgeKey == null || !additionalObservedEdgeKey.equals(key))
                {
                    additionalObservedEdgeKey = key;
                    additionalObservedEdgeRepeatCount = observedEdgeCounts.merge(key, 1, Integer::sum);
                }
                repeatCount = additionalObservedEdgeRepeatCount;
            }

            DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic =
                routeEngine.validateObservedEdge(
                    from,
                    actual,
                    segmentTarget,
                    segment.expectedRemainingFromFork(divergenceIndex)
                );
            return DrewsHelperRouteBenchmark.formatObservedEdgeDiagnostic(
                diagnostic,
                repeatCount,
                OBSERVED_EDGE_OVERRIDE_REPEAT_THRESHOLD
            );
        }

        private String additionalDivergenceTrace(List<WorldPoint> predictedPath, boolean actualComplete)
        {
            int divergenceIndex = DrewsHelperRouteBenchmark.additionalDivergenceIndexAfterFirstMerge(
                predictedPath,
                actualPath,
                actualComplete
            );
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            return "idx=" + divergenceIndex
                + " candidates={" + candidateTraceAt(predictedPath, divergenceIndex) + "}"
                + " edgeValidation={" + edgeValidationTraceAt(predictedPath, divergenceIndex, false) + "}"
                + " forkRank={" + (actualComplete ? forkRankTraceAt(predictedPath, divergenceIndex) : "pending") + "}";
        }

        private String forkRankTraceAt(List<WorldPoint> predictedPath, int divergenceIndex)
        {
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            WorldPoint from = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex - 1);
            WorldPoint predicted = DrewsHelperRouteBenchmark.pointAt(predictedPath, divergenceIndex);
            WorldPoint actual = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex);
            RouteBenchmarkSegment segment = segmentForPathIndex(divergenceIndex);
            WorldPoint segmentTarget = segment.getTarget();
            if (from == null)
            {
                return "none";
            }

            int expectedRemaining = segment.expectedRemainingFromFork(divergenceIndex);
            List<DrewsHelperWalkingRouteEngine.MoveCandidate> candidates =
                routeEngine.moveCandidates(from, segmentTarget);
            if (candidates.isEmpty())
            {
                return "none";
            }

            List<ForkCandidateRank> ranks = new ArrayList<>(candidates.size());
            for (DrewsHelperWalkingRouteEngine.MoveCandidate candidate : candidates)
            {
                DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic =
                    routeEngine.validateObservedEdge(
                        from,
                        candidate.getDestination(),
                        segmentTarget,
                        expectedRemaining
                    );
                ranks.add(new ForkCandidateRank(
                    candidate,
                    diagnostic,
                    candidate.getDestination().equals(predicted),
                    candidate.getDestination().equals(actual)
                ));
            }

            ranks.sort((left, right) -> compareForkCandidateRank(left, right));

            StringBuilder entries = new StringBuilder();
            for (int index = 0; index < ranks.size(); index++)
            {
                if (index > 0)
                {
                    entries.append("; ");
                }
                entries.append(ranks.get(index).format(index + 1));
            }

            return "from=" + DrewsHelperRouteBenchmark.formatPoint(from)
                + " target=" + DrewsHelperRouteBenchmark.formatPoint(segmentTarget)
                + finalTargetTrace(segmentTarget)
                + " expectedRemaining=" + expectedRemaining
                + " best=" + ranks.get(0).formatBest()
                + " predictedRank=" + rankOf(ranks, true)
                + " actualRank=" + rankOf(ranks, false)
                + " entries=[" + entries + "]";
        }

        private static int compareForkCandidateRank(ForkCandidateRank left, ForkCandidateRank right)
        {
            int compared = Integer.compare(left.availabilityPenalty(), right.availabilityPenalty());
            if (compared != 0)
            {
                return compared;
            }

            compared = Integer.compare(left.totalRemainingFromFork(), right.totalRemainingFromFork());
            if (compared != 0)
            {
                return compared;
            }

            compared = Integer.compare(left.getCandidate().getPreferencePenalty(), right.getCandidate().getPreferencePenalty());
            if (compared != 0)
            {
                return compared;
            }

            compared = Integer.compare(left.getCandidate().getDistanceToTarget(), right.getCandidate().getDistanceToTarget());
            if (compared != 0)
            {
                return compared;
            }

            return Integer.compare(left.getCandidate().getOrder(), right.getCandidate().getOrder());
        }

        private static int rankOf(List<ForkCandidateRank> ranks, boolean predicted)
        {
            for (int index = 0; index < ranks.size(); index++)
            {
                ForkCandidateRank rank = ranks.get(index);
                if ((predicted && rank.isPredicted()) || (!predicted && rank.isActual()))
                {
                    return index + 1;
                }
            }
            return -1;
        }

        private String shapeTrace(List<WorldPoint> predictedPath, boolean reachedTarget)
        {
            if (!reachedTarget)
            {
                return "pending";
            }

            if (segments.size() <= 1)
            {
                return DrewsHelperRouteBenchmark.formatShapeDiagnostic(predictedPath, actualPath, true);
            }

            int divergenceIndex = DrewsHelperRouteBenchmark.firstDivergenceIndex(predictedPath, actualPath, true);
            if (divergenceIndex < 0)
            {
                return "scope=segments count=" + segments.size() + " status=match winner=tie";
            }

            RouteBenchmarkSegment segment = segmentForPathIndex(divergenceIndex);
            List<WorldPoint> expectedSegment = segment.expectedPath(predictedPath);
            List<WorldPoint> actualSegment = actualSegmentPath(segment);
            if (actualSegment.isEmpty())
            {
                return "scope=segment"
                    + " target=" + DrewsHelperRouteBenchmark.formatPoint(segment.getTarget())
                    + " status=unavailable reason=actual-segment-not-found";
            }

            return "scope=segment"
                + " target=" + DrewsHelperRouteBenchmark.formatPoint(segment.getTarget())
                + finalTargetTrace(segment.getTarget())
                + " "
                + DrewsHelperRouteBenchmark.formatShapeDiagnostic(expectedSegment, actualSegment, true);
        }

        private String shadowTrace(boolean actualComplete)
        {
            if (!actualComplete)
            {
                return "pending";
            }

            if (routeEngine == null)
            {
                return "status=unavailable reason=no-engine";
            }

            try
            {
                DrewsHelperRouteSnapshot shadowRoute =
                    routeEngine.solveWithoutLocalWalkingOverrides(start, segmentTargets());
                if (shadowRoute.getStatus() != DrewsHelperRouteStatus.READY)
                {
                    return "status=" + shadowRoute.getStatus()
                        + " message=" + shadowRoute.getMessage();
                }

                return DrewsHelperRouteBenchmark.formatShadowRouteDiagnostic(
                    primaryPath,
                    shadowRoute.getPath(),
                    actualPath,
                    true
                );
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return "status=interrupted";
            }
        }

        private String shapeShadowTrace(boolean actualComplete)
        {
            if (!actualComplete)
            {
                return "pending";
            }

            if (routeEngine == null)
            {
                return "status=unavailable reason=no-engine";
            }

            try
            {
                DrewsHelperRouteSnapshot shapeShadowRoute =
                    routeEngine.solveWithShapeRankingWithoutLocalWalkingOverrides(start, segmentTargets());
                if (shapeShadowRoute.getStatus() != DrewsHelperRouteStatus.READY)
                {
                    return "status=" + shapeShadowRoute.getStatus()
                        + " message=" + shapeShadowRoute.getMessage();
                }

                return DrewsHelperRouteBenchmark.formatShapeShadowRouteDiagnostic(
                    primaryPath,
                    shapeShadowRoute.getPath(),
                    actualPath,
                    true
                );
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return "status=interrupted";
            }
        }

        private List<WorldPoint> actualSegmentPath(RouteBenchmarkSegment segment)
        {
            WorldPoint segmentStart = DrewsHelperRouteBenchmark.pointAt(primaryPath, segment.getStartIndex());
            int actualStartIndex = indexOfPathPoint(actualPath, segmentStart, 0);
            if (actualStartIndex < 0)
            {
                return Collections.emptyList();
            }

            int actualEndIndex = indexOfPathPoint(actualPath, segment.getTarget(), actualStartIndex);
            if (actualEndIndex < actualStartIndex)
            {
                return Collections.emptyList();
            }

            return actualPath.subList(actualStartIndex, actualEndIndex + 1);
        }

        private RouteBenchmarkSegment segmentForPathIndex(int pathIndex)
        {
            for (RouteBenchmarkSegment segment : segments)
            {
                if (pathIndex <= segment.getEndIndex())
                {
                    return segment;
                }
            }
            return segments.get(segments.size() - 1);
        }

        private List<WorldPoint> segmentTargets()
        {
            List<WorldPoint> targets = new ArrayList<>(segments.size());
            for (RouteBenchmarkSegment segment : segments)
            {
                targets.add(segment.getTarget());
            }
            return targets;
        }

        private String finalTargetTrace(WorldPoint segmentTarget)
        {
            if (target.equals(segmentTarget))
            {
                return "";
            }

            return " finalTarget=" + DrewsHelperRouteBenchmark.formatPoint(target);
        }

        private static String edgeKey(WorldPoint from, WorldPoint actual, WorldPoint target)
        {
            return DrewsHelperRouteBenchmark.formatPoint(from)
                + "->"
                + DrewsHelperRouteBenchmark.formatPoint(actual)
                + "|target="
                + DrewsHelperRouteBenchmark.formatPoint(target);
        }

        private static List<RouteBenchmarkSegment> buildSegments(
            List<WorldPoint> primaryPath,
            List<WorldPoint> destinations
        )
        {
            if (primaryPath == null || primaryPath.isEmpty())
            {
                return Collections.emptyList();
            }

            List<RouteBenchmarkSegment> routeSegments = new ArrayList<>();
            List<WorldPoint> orderedDestinations = destinations == null
                ? Collections.emptyList()
                : destinations;
            int segmentStartIndex = 0;
            for (WorldPoint destination : orderedDestinations)
            {
                int endIndex = indexOfPathPoint(primaryPath, destination, segmentStartIndex);
                if (endIndex < segmentStartIndex)
                {
                    continue;
                }

                routeSegments.add(new RouteBenchmarkSegment(
                    segmentStartIndex,
                    endIndex,
                    destination
                ));
                segmentStartIndex = endIndex;
            }

            int finalIndex = primaryPath.size() - 1;
            WorldPoint finalTarget = primaryPath.get(finalIndex);
            if (routeSegments.isEmpty() || routeSegments.get(routeSegments.size() - 1).getEndIndex() < finalIndex)
            {
                routeSegments.add(new RouteBenchmarkSegment(
                    segmentStartIndex,
                    finalIndex,
                    finalTarget
                ));
            }

            return Collections.unmodifiableList(routeSegments);
        }

        private static int indexOfPathPoint(List<WorldPoint> path, WorldPoint point, int startIndex)
        {
            if (path == null || point == null)
            {
                return -1;
            }

            for (int index = Math.max(0, startIndex); index < path.size(); index++)
            {
                if (point.equals(path.get(index)))
                {
                    return index;
                }
            }
            return -1;
        }
    }

    private static final class ForkCandidateRank
    {
        private final DrewsHelperWalkingRouteEngine.MoveCandidate candidate;
        private final DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic;
        private final boolean predicted;
        private final boolean actual;

        private ForkCandidateRank(
            DrewsHelperWalkingRouteEngine.MoveCandidate candidate,
            DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic,
            boolean predicted,
            boolean actual
        )
        {
            this.candidate = candidate;
            this.diagnostic = diagnostic;
            this.predicted = predicted;
            this.actual = actual;
        }

        private DrewsHelperWalkingRouteEngine.MoveCandidate getCandidate()
        {
            return candidate;
        }

        private boolean isPredicted()
        {
            return predicted;
        }

        private boolean isActual()
        {
            return actual;
        }

        private int availabilityPenalty()
        {
            if (!diagnostic.isAvailable())
            {
                return 4;
            }
            if (!diagnostic.isEdgeLegal())
            {
                return 3;
            }
            if (!diagnostic.isContinuationFound())
            {
                return 2;
            }
            return 0;
        }

        private int totalRemainingFromFork()
        {
            return diagnostic.isContinuationFound()
                ? diagnostic.getTotalRemainingFromFork()
                : Integer.MAX_VALUE;
        }

        private String formatBest()
        {
            return role()
                + "@"
                + DrewsHelperRouteBenchmark.formatPoint(candidate.getDestination())
                + " total="
                + totalTrace()
                + " delta="
                + deltaTrace();
        }

        private String format(int rank)
        {
            return rank
                + ":"
                + DrewsHelperRouteBenchmark.formatPoint(candidate.getDestination())
                + " role=" + role()
                + " type=" + candidate.getMoveType()
                + " dist=" + candidate.getDistanceToTarget()
                + " pref=" + candidate.getPreferencePenalty()
                + " legal=" + diagnostic.isEdgeLegal()
                + " total=" + totalTrace()
                + " delta=" + deltaTrace()
                + " reason=" + diagnostic.getReason();
        }

        private String role()
        {
            if (predicted && actual)
            {
                return "predicted+actual";
            }
            if (predicted)
            {
                return "predicted";
            }
            if (actual)
            {
                return "actual";
            }
            return "candidate";
        }

        private String totalTrace()
        {
            return diagnostic.isContinuationFound()
                ? Integer.toString(diagnostic.getTotalRemainingFromFork())
                : "none";
        }

        private String deltaTrace()
        {
            return diagnostic.isContinuationFound()
                ? Integer.toString(diagnostic.getContinuationDelta())
                : "none";
        }
    }

    private static final class RouteBenchmarkSegment
    {
        private final int startIndex;
        private final int endIndex;
        private final WorldPoint target;

        private RouteBenchmarkSegment(int startIndex, int endIndex, WorldPoint target)
        {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.target = target;
        }

        private int getStartIndex()
        {
            return startIndex;
        }

        private int getEndIndex()
        {
            return endIndex;
        }

        private WorldPoint getTarget()
        {
            return target;
        }

        private int expectedRemainingFromFork(int divergenceIndex)
        {
            return Math.max(0, endIndex - divergenceIndex + 1);
        }

        private List<WorldPoint> expectedPath(List<WorldPoint> routePath)
        {
            if (routePath == null || routePath.isEmpty())
            {
                return Collections.emptyList();
            }

            int from = Math.max(0, Math.min(startIndex, routePath.size() - 1));
            int to = Math.max(from, Math.min(endIndex, routePath.size() - 1));
            return routePath.subList(from, to + 1);
        }
    }

    static final class RouteBenchmarkUpdate
    {
        private final int movementTicks;
        private final boolean complete;
        private final String reason;
        private final DrewsHelperRouteBenchmark.Report primaryReport;
        private final String primaryPathTrace;
        private final String actualPathTrace;
        private final String divergenceTrace;
        private final String primaryCandidateTrace;
        private final String edgeValidationTrace;
        private final String additionalDivergenceTrace;
        private final String shapeTrace;
        private final String shadowTrace;
        private final String shapeShadowTrace;
        private final String ignoredTrace;

        private RouteBenchmarkUpdate(
            int movementTicks,
            boolean complete,
            String reason,
            DrewsHelperRouteBenchmark.Report primaryReport,
            String primaryPathTrace,
            String actualPathTrace,
            String divergenceTrace,
            String primaryCandidateTrace,
            String edgeValidationTrace,
            String additionalDivergenceTrace,
            String shapeTrace,
            String shadowTrace,
            String shapeShadowTrace
        )
        {
            this.movementTicks = movementTicks;
            this.complete = complete;
            this.reason = reason;
            this.primaryReport = primaryReport;
            this.primaryPathTrace = primaryPathTrace;
            this.actualPathTrace = actualPathTrace;
            this.divergenceTrace = divergenceTrace;
            this.primaryCandidateTrace = primaryCandidateTrace;
            this.edgeValidationTrace = edgeValidationTrace;
            this.additionalDivergenceTrace = additionalDivergenceTrace;
            this.shapeTrace = shapeTrace;
            this.shadowTrace = shadowTrace;
            this.shapeShadowTrace = shapeShadowTrace;
            this.ignoredTrace = null;
        }

        private RouteBenchmarkUpdate(int movementTicks, String reason, String ignoredTrace)
        {
            this.movementTicks = movementTicks;
            this.complete = true;
            this.reason = reason;
            this.primaryReport = null;
            this.primaryPathTrace = "";
            this.actualPathTrace = "";
            this.divergenceTrace = "";
            this.primaryCandidateTrace = "";
            this.edgeValidationTrace = "";
            this.additionalDivergenceTrace = "";
            this.shapeTrace = "";
            this.shadowTrace = "";
            this.shapeShadowTrace = "";
            this.ignoredTrace = ignoredTrace;
        }

        private static RouteBenchmarkUpdate ignored(int movementTicks, String reason, String ignoredTrace)
        {
            return new RouteBenchmarkUpdate(movementTicks, reason, ignoredTrace);
        }

        boolean isComplete()
        {
            return complete;
        }

        String overlaySummary()
        {
            if (ignoredTrace != null)
            {
                return "Benchmark ignored: " + reason;
            }

            return "Route " + primaryReport.getFirstTenMatches() + "/" + primaryReport.getFirstTenCompared();
        }

        String logLine()
        {
            if (ignoredTrace != null)
            {
                return "ticks=" + movementTicks
                    + " reason=" + reason
                    + " ignored={" + ignoredTrace + "}";
            }

            return "ticks=" + movementTicks
                + " reason=" + reason
                + " route={" + primaryReport.summary() + "}"
                + " expectedPath=" + primaryPathTrace
                + " actualPath=" + actualPathTrace
                + " divergence={" + divergenceTrace + "}"
                + " candidates={" + primaryCandidateTrace + "}"
                + " edgeValidation={" + edgeValidationTrace + "}"
                + " additionalDivergenceDetail={" + additionalDivergenceTrace + "}"
                + " shape={" + shapeTrace + "}"
                + " shadow={" + shadowTrace + "}"
                + " shapeShadow={" + shapeShadowTrace + "}";
        }
    }

    private void refreshWaypointMarkers()
    {
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            syncWaypointMarker(index);
        }
    }

    private void syncWaypointMarker(int index)
    {
        if (waypointMarkers[index] != null)
        {
            worldMapPointManager.remove(waypointMarkers[index]);
            waypointMarkers[index] = null;
        }

        WorldPoint waypoint = waypoints[index];
        if (waypoint == null)
        {
            return;
        }

        WorldMapPoint marker = new DrewsHelperWaypointMapPoint(index + 1, waypoint, getWaypointColor(index));
        waypointMarkers[index] = marker;
        worldMapPointManager.add(marker);
    }

    private void removeWaypointMarkers()
    {
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            if (waypointMarkers[index] != null)
            {
                worldMapPointManager.remove(waypointMarkers[index]);
                waypointMarkers[index] = null;
            }
        }

        worldMapPointManager.removeIf(DrewsHelperWaypointMapPoint::isDrewsHelperWaypoint);
    }

    private void onWaypointMenuClicked(MenuEntry entry)
    {
        if (SET.equals(entry.getOption()))
        {
            int waypointIndex = waypointIndexFromTarget(entry.getTarget());
            if (waypointIndex < 0)
            {
                return;
            }

            setWaypoint(waypointIndex, getSelectedMapPoint());
            return;
        }

        if (CANCEL.equals(entry.getOption()))
        {
            clearWaypoint(waypointIndexFromTarget(entry.getTarget()));
            return;
        }

        if (CLEAR.equals(entry.getOption()) && ALL_WAYPOINTS_TARGET.equals(entry.getTarget()))
        {
            clearWaypoints();
        }
    }

    private WorldPoint getSelectedMapPoint()
    {
        Point selectedPoint = client.isMenuOpen() && lastMenuOpenedPoint != null
            ? lastMenuOpenedPoint
            : client.getMouseCanvasPosition();

        if (selectedPoint == null)
        {
            return null;
        }

        return calculateMapPoint(selectedPoint.getX(), selectedPoint.getY());
    }

    private WorldPoint calculateMapPoint(int pointX, int pointY)
    {
        if (pointX == Integer.MIN_VALUE || pointY == Integer.MIN_VALUE || client.getWorldMap() == null)
        {
            return null;
        }

        Point worldMapPosition = client.getWorldMap().getWorldMapPosition();
        if (worldMapPosition == null)
        {
            return null;
        }

        float zoom = client.getWorldMap().getWorldMapZoom();
        if (zoom <= 0)
        {
            return null;
        }

        WorldPoint center = new WorldPoint(worldMapPosition.getX(), worldMapPosition.getY(), 0);
        int middleX = mapWorldPointToGraphicsPointX(center);
        int middleY = mapWorldPointToGraphicsPointY(center);

        if (middleX == Integer.MIN_VALUE || middleY == Integer.MIN_VALUE)
        {
            return null;
        }

        int dx = (int) ((pointX - middleX) / zoom);
        int dy = (int) ((-(pointY - middleY)) / zoom);

        return new WorldPoint(center.getX() + dx, center.getY() + dy, center.getPlane());
    }

    private int mapWorldPointToGraphicsPointX(WorldPoint worldPoint)
    {
        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (map == null || client.getWorldMap() == null)
        {
            return Integer.MIN_VALUE;
        }

        float pixelsPerTile = client.getWorldMap().getWorldMapZoom();
        if (pixelsPerTile <= 0)
        {
            return Integer.MIN_VALUE;
        }
        Rectangle worldMapRect = map.getBounds();
        int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
        Point worldMapPosition = client.getWorldMap().getWorldMapPosition();
        if (worldMapPosition == null)
        {
            return Integer.MIN_VALUE;
        }
        int xTileOffset = worldPoint.getX() + widthInTiles / 2 - worldMapPosition.getX();
        int xGraphDiff = (int) (xTileOffset * pixelsPerTile);
        xGraphDiff += (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
        xGraphDiff += (int) worldMapRect.getX();
        return xGraphDiff;
    }

    private int mapWorldPointToGraphicsPointY(WorldPoint worldPoint)
    {
        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (map == null || client.getWorldMap() == null)
        {
            return Integer.MIN_VALUE;
        }

        float pixelsPerTile = client.getWorldMap().getWorldMapZoom();
        if (pixelsPerTile <= 0)
        {
            return Integer.MIN_VALUE;
        }
        Rectangle worldMapRect = map.getBounds();
        int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);
        Point worldMapPosition = client.getWorldMap().getWorldMapPosition();
        if (worldMapPosition == null)
        {
            return Integer.MIN_VALUE;
        }
        int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
        int yTileOffset = (yTileMax - worldPoint.getY() - 1) * -1;
        int yGraphDiff = (int) (yTileOffset * pixelsPerTile);
        yGraphDiff -= (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
        yGraphDiff = worldMapRect.height - yGraphDiff;
        yGraphDiff += (int) worldMapRect.getY();
        return yGraphDiff;
    }

    private boolean isMouseOverWorldMap()
    {
        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        Point mouse = client.getMouseCanvasPosition();
        return map != null
            && mouse != null
            && map.getBounds().contains(mouse.getX(), mouse.getY());
    }

    private void addMenuEntry(MenuEntryAdded event, String option, String target, int position)
    {
        if (menuContains(option, target))
        {
            return;
        }

        client.getMenu().createMenuEntry(position)
            .setOption(option)
            .setTarget(target)
            .setParam0(event.getActionParam0())
            .setParam1(event.getActionParam1())
            .setIdentifier(event.getIdentifier())
            .setType(MenuAction.RUNELITE)
            .onClick(this::onWaypointMenuClicked);
    }

    private boolean menuContains(String option, String target)
    {
        for (MenuEntry entry : client.getMenu().getMenuEntries())
        {
            if (option.equals(entry.getOption()) && target.equals(entry.getTarget()))
            {
                return true;
            }
        }
        return false;
    }

    private String waypointPositionKey(int index)
    {
        return WAYPOINT_POSITION_KEY_PREFIX + (index + 1) + WAYPOINT_POSITION_KEY_SUFFIX;
    }

    private static int waypointPositionIndex(String key)
    {
        if (key == null
            || !key.startsWith(WAYPOINT_POSITION_KEY_PREFIX)
            || !key.endsWith(WAYPOINT_POSITION_KEY_SUFFIX))
        {
            return -1;
        }

        try
        {
            int waypointNumber = Integer.parseInt(key.substring(
                WAYPOINT_POSITION_KEY_PREFIX.length(),
                key.length() - WAYPOINT_POSITION_KEY_SUFFIX.length()));
            int waypointIndex = waypointNumber - 1;
            return waypointIndex >= 0 && waypointIndex < MAX_WAYPOINTS ? waypointIndex : -1;
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    private static String waypointLabel(int index)
    {
        return WAYPOINT_TARGET_PREFIX + (index + 1);
    }

    static String waypointMenuOption(WorldPoint waypoint)
    {
        return waypoint == null ? SET : CANCEL;
    }

    static String[] waypointMenuDisplayLabels(WorldPoint[] waypointState)
    {
        if (waypointState == null || waypointState.length != MAX_WAYPOINTS)
        {
            throw new IllegalArgumentException("Waypoint menu state must contain exactly " + MAX_WAYPOINTS + " slots");
        }

        String[] labels = new String[MAX_WAYPOINTS];
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            labels[index] = waypointMenuOption(waypointState[index]) + " " + waypointLabel(index);
        }
        return labels;
    }

    static boolean isWaypointPositionConfigKey(String key)
    {
        return waypointPositionIndex(key) >= 0;
    }

    static boolean isWaypointColorConfigKey(String key)
    {
        return key != null && key.endsWith(WAYPOINT_COLOR_KEY_SUFFIX);
    }

    private int waypointIndexFromTarget(String target)
    {
        if (target == null || !target.startsWith(WAYPOINT_TARGET_PREFIX))
        {
            return -1;
        }

        try
        {
            int waypointNumber = Integer.parseInt(target.substring(WAYPOINT_TARGET_PREFIX.length()));
            int waypointIndex = waypointNumber - 1;
            return waypointIndex >= 0 && waypointIndex < MAX_WAYPOINTS ? waypointIndex : -1;
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    public java.awt.Color getWaypointColor(int index)
    {
        switch (index)
        {
            case 0:
                return config().waypoint1PathColor();
            case 1:
                return config().waypoint2PathColor();
            case 2:
                return config().waypoint3PathColor();
            case 3:
                return config().waypoint4PathColor();
            case 4:
                return config().waypoint5PathColor();
            default:
                throw new IllegalArgumentException("Unsupported waypoint index " + index);
        }
    }

    private DrewsHelperConfig config()
    {
        return configManager.getConfig(DrewsHelperConfig.class);
    }
}
