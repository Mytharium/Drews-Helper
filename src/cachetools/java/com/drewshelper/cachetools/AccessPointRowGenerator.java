package com.drewshelper.cachetools;

import com.drewshelper.routing.DrewsHelperCollisionMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * To-do #2, first slice - turns wall-mounted access points from the cache dump into candidate
 * transport rows.
 *
 * <p>Writes to its OWN file, never straight into {@code transport-overrides.tsv}. Nothing here
 * changes a route until a human has read the output and moved rows across. That is deliberate:
 * a bad row is worse than a missing one, because the router will confidently send the player
 * through a door that does not open.
 *
 * <p><b>How a one-tile object becomes a two-tile edge.</b> The dump records where an object sits,
 * but a transport row needs a from and a to. For a WALL placement the orientation says which edge
 * of the tile the wall occupies, so the pair is (tile, neighbour across that edge). Verified
 * against ground truth: the Falador gate is locType 0 orientation 2, and the hand-written override
 * for it connects 2935,3450 -> 2936,3450, i.e. east. So 0=W, 1=N, 2=E, 3=S.
 *
 * <p><b>Every row is only a candidate.</b> The generator applies useful sanity filters: the
 * predicted edge must be blocked in our collision map, and both tiles must be standable. That is
 * enough to rank/review rows, but not enough to merge blindly. The control printed in the summary
 * proved orientation carries signal, not certainty; survivors still need live-client or manual
 * validation before they move into {@code transport-overrides.tsv}.
 */
public final class AccessPointRowGenerator
{
    private static final int DETOUR_CAP_STEPS = 512;

    /** Wall placement types - the only ones whose orientation defines a crossing. */
    private static final int[] WALL_LOC_TYPES = {0, 1, 2, 3, 9};

    /** Orientation to compass direction. Proved against the Falador gate, not assumed. */
    private static final char[] DIRECTION_BY_ORIENTATION = {'W', 'N', 'E', 'S'};

    private static final Pattern LIVE_MISMATCH_PATTERN = Pattern.compile(
        "(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s+([NSEW])\\s+OURS_BLOCKS_LIVE_OPEN");
    private static final Pattern TSV_PROOF_PATTERN = Pattern.compile(
        "^(\\d+)\\t(\\d+)\\t(\\d+)\\t([NSEW])(?:\\t.*)?$");

    private static final Map<String, String> EXCLUDED_NAMES = excludedNames();

    private AccessPointRowGenerator()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path project = Paths.get(args.length > 0 ? args[0] : System.getProperty("user.dir"));
        Path dump = project.resolve("tools/cache-access-points.tsv");
        Path out = project.resolve("tools/cache-derived-gates.tsv");
        Path reviewOut = project.resolve("tools/cache-derived-gates-review.tsv");
        Path provenOut = project.resolve("tools/cache-derived-gates-proven.tsv");

        if (!Files.exists(dump))
        {
            throw new IOException("No dump at " + dump + " - run dumpAccessPoints first.");
        }

        DrewsHelperCollisionMap map = DrewsHelperCollisionMap.loadDefault();
        List<String> lines = Files.readAllLines(dump, StandardCharsets.UTF_8);
        ProofData proofData = readLiveProof(project);

        StringBuilder log = new StringBuilder();
        Map<Character, int[]> byDirection = new TreeMap<>();
        List<Candidate> emitted = new ArrayList<>();
        Map<String, Integer> excludedByName = new TreeMap<>();

        int wallRows = 0;
        // CONTROL: if orientation is meaningful, the predicted edge should be blocked far
        // more often than a perpendicular edge at the SAME tile. If both rates match, the
        // orientation is telling us nothing and every emitted row is a guess.
        int controlPredictedBlocked = 0;
        int controlPerpendicularBlocked = 0;
        int controlSamples = 0;
        int alreadyCovered = 0;
        int noCollisionData = 0;
        int edgeNotBlocked = 0;
        int neighbourUnstandable = 0;
        int rawCandidateCrossings = 0;
        int excludedJunk = 0;

        for (String line : lines)
        {
            if (line.startsWith("#") || line.startsWith("x\t"))
            {
                continue;
            }
            String[] p = line.split("\t", -1);
            if (p.length < 10)
            {
                continue;
            }

            int locType = Integer.parseInt(p[6].trim());
            if (!isWallPlacement(locType))
            {
                continue;
            }
            int orientation = Integer.parseInt(p[7].trim());
            if (orientation < 0 || orientation > 3)
            {
                continue;
            }
            wallRows++;

            char dir = DIRECTION_BY_ORIENTATION[orientation];
            int[] tally = byDirection.computeIfAbsent(dir, k -> new int[3]);
            tally[0]++;

            int x = Integer.parseInt(p[0].trim());
            int y = Integer.parseInt(p[1].trim());
            int z = Integer.parseInt(p[2].trim());
            String name = p[4];
            boolean covered = "yes".equals(p[8].trim());
            boolean haveRegion = "yes".equals(p[9].trim());

            if (!haveRegion)
            {
                noCollisionData++;
                continue;
            }

            int nx = x;
            int ny = y;
            boolean blocked;
            if (dir == 'W')
            {
                nx = x - 1;
                blocked = !map.canMoveWest(x, y, z);
            }
            else if (dir == 'N')
            {
                ny = y + 1;
                blocked = !map.canMoveNorth(x, y, z);
            }
            else if (dir == 'E')
            {
                nx = x + 1;
                blocked = !map.canMoveEast(x, y, z);
            }
            else
            {
                ny = y - 1;
                blocked = !map.canMoveSouth(x, y, z);
            }

            // control sample: predicted edge vs the two perpendicular edges at this tile
            controlSamples++;
            if (blocked)
            {
                controlPredictedBlocked++;
            }
            boolean perpA;
            boolean perpB;
            if (dir == 'W' || dir == 'E')
            {
                perpA = !map.canMoveNorth(x, y, z);
                perpB = !map.canMoveSouth(x, y, z);
            }
            else
            {
                perpA = !map.canMoveEast(x, y, z);
                perpB = !map.canMoveWest(x, y, z);
            }
            if (perpA)
            {
                controlPerpendicularBlocked++;
            }
            if (perpB)
            {
                controlPerpendicularBlocked++;
            }

            if (blocked)
            {
                // the orientation predicted this edge would be blocked, and it is
                tally[1]++;
            }
            else
            {
                edgeNotBlocked++;
                continue;
            }

            if (!isStandable(map, x, y, z) || !isStandable(map, nx, ny, z))
            {
                neighbourUnstandable++;
                continue;
            }
            if (covered)
            {
                alreadyCovered++;
                continue;
            }

            rawCandidateCrossings++;
            String exclusionReason = excludedReason(name);
            if (exclusionReason != null)
            {
                excludedJunk++;
                excludedByName.merge(name + " - " + exclusionReason, 1, Integer::sum);
                continue;
            }

            EdgeKey edgeKey = EdgeKey.between(x, y, z, nx, ny);
            Detour detour = measureDetour(map, x, y, z, nx, ny);
            boolean routeAProven = proofData.edges.contains(edgeKey);

            tally[2]++;
            emitted.add(new Candidate(x, y, z, nx, ny, name, p[5], dir, edgeKey, detour,
                routeAProven));
        }

        emitted.sort(Candidate.BY_REVIEW_PRIORITY);

        // ---- the mapping proof has to come first; if it fails, nothing below is trustworthy ----
        log.append("=== ORIENTATION MAPPING PROOF ===\n");
        log.append("If 0=W 1=N 2=E 3=S is useful, a wall object's predicted edge should be\n");
        log.append("blocked more often than the perpendicular control. A low absolute rate still\n");
        log.append("means the rows are not safe to merge blind.\n\n");
        log.append(String.format(Locale.ROOT, "  %-4s %8s %10s %9s %8s%n",
            "dir", "walls", "blocked", "rate", "review"));
        for (Map.Entry<Character, int[]> e : byDirection.entrySet())
        {
            int[] t = e.getValue();
            double rate = t[0] == 0 ? 0 : (100.0 * t[1] / t[0]);
            log.append(String.format(Locale.ROOT, "  %-4s %8d %10d %8.1f%% %8d%n",
                e.getKey(), t[0], t[1], rate, t[2]));
        }

        double predictedRate = controlSamples == 0 ? 0
            : (100.0 * controlPredictedBlocked / controlSamples);
        double perpendicularRate = controlSamples == 0 ? 0
            : (100.0 * controlPerpendicularBlocked / (controlSamples * 2.0));
        log.append('\n').append("=== CONTROL: is orientation actually telling us anything? ===\n");
        log.append(String.format(Locale.ROOT,
            "  predicted edge blocked     : %.1f%%  (%d of %d)%n",
            predictedRate, controlPredictedBlocked, controlSamples));
        log.append(String.format(Locale.ROOT,
            "  perpendicular edge blocked : %.1f%%  (%d of %d)%n",
            perpendicularRate, controlPerpendicularBlocked, controlSamples * 2));
        log.append("  If these two are close, orientation is NOT a signal and the rows below\n");
        log.append("  are guesses. A large gap means the mapping is real, not automatically\n");
        log.append("  safe enough to merge without live/manual validation.\n");

        log.append('\n').append("=== FUNNEL ===\n");
        log.append("  wall placements considered : ").append(wallRows).append('\n');
        log.append("  no collision data          : ").append(noCollisionData).append('\n');
        log.append("  edge not actually blocked  : ").append(edgeNotBlocked)
            .append("  (no transport row needed - already walkable)\n");
        log.append("  a side is not standable    : ").append(neighbourUnstandable)
            .append("  (would be an invented shortcut)\n");
        log.append("  already covered upstream   : ").append(alreadyCovered).append('\n');
        log.append("  raw candidate crossings    : ").append(rawCandidateCrossings).append('\n');
        log.append("  obvious instance/minigame  : ").append(excludedJunk).append('\n');
        log.append("  REVIEW CROSSINGS           : ").append(emitted.size()).append('\n');
        log.append("  review rows (both ways)    : ").append(emitted.size() * 2).append('\n');

        if (!excludedByName.isEmpty())
        {
            log.append('\n').append("=== EXCLUDED OBVIOUS JUNK ===\n");
            List<Map.Entry<String, Integer>> excluded = new ArrayList<>(excludedByName.entrySet());
            excluded.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
            for (Map.Entry<String, Integer> e : excluded)
            {
                log.append("  ").append(e.getValue()).append("  ").append(e.getKey()).append('\n');
            }
        }

        int proven = 0;
        int overCap = 0;
        int detour65Plus = 0;
        int detour17To64 = 0;
        int detour16OrLess = 0;
        for (Candidate c : emitted)
        {
            if (c.routeAProven)
            {
                proven++;
            }
            if (c.detour.capped)
            {
                overCap++;
            }
            else if (c.detour.steps >= 65)
            {
                detour65Plus++;
            }
            else if (c.detour.steps >= 17)
            {
                detour17To64++;
            }
            else
            {
                detour16OrLess++;
            }
        }

        log.append('\n').append("=== DETOUR SEVERITY ===\n");
        log.append("  >").append(DETOUR_CAP_STEPS).append(" steps or no path inside cap : ")
            .append(overCap).append('\n');
        log.append("  65-").append(DETOUR_CAP_STEPS).append(" steps                 : ")
            .append(detour65Plus).append('\n');
        log.append("  17-64 steps                  : ").append(detour17To64).append('\n');
        log.append("  2-16 steps                   : ").append(detour16OrLess).append('\n');

        log.append('\n').append("=== ROUTE A LIVE PROOF ===\n");
        if (proofData.sources.isEmpty())
        {
            log.append("  proof source files          : none\n");
            log.append("  expected file               : tools/route-a-live-mismatches.txt or .tsv\n");
            log.append("  accepted input              : raw DREW_MAP_VALIDATE mismatch lines, or x<TAB>y<TAB>plane<TAB>dir\n");
        }
        else
        {
            log.append("  proof source files          : ");
            for (int i = 0; i < proofData.sources.size(); i++)
            {
                if (i > 0)
                {
                    log.append(", ");
                }
                log.append(project.relativize(proofData.sources.get(i)));
            }
            log.append('\n');
            log.append("  proof edges parsed          : ").append(proofData.edges.size()).append('\n');
        }
        log.append("  review crossings proven     : ").append(proven).append('\n');
        log.append("  proven rows (both ways)     : ").append(proven * 2).append('\n');
        log.append("  transport-overrides.tsv     : untouched by this task\n");

        Map<String, Integer> nameTally = new TreeMap<>();
        for (Candidate c : emitted)
        {
            nameTally.merge(c.name, 1, Integer::sum);
        }
        log.append('\n').append("=== CANDIDATES BY NAME ===\n");
        List<Map.Entry<String, Integer>> named = new ArrayList<>(nameTally.entrySet());
        named.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        for (int i = 0; i < Math.min(20, named.size()); i++)
        {
            log.append("  ").append(named.get(i).getValue()).append("  ")
                .append(named.get(i).getKey()).append('\n');
        }

        log.append('\n').append("=== TOP REVIEW TARGETS ===\n");
        for (int i = 0; i < Math.min(25, emitted.size()); i++)
        {
            Candidate c = emitted.get(i);
            log.append(String.format(Locale.ROOT, "  #%04d %-8s %-6s %s -> %s  %s %s%n",
                i + 1, c.detour.label(), c.routeAProven ? "PROVEN" : "needsA",
                c.fromPoint(), c.toPoint(), c.action, c.name));
        }

        writeRows(out, emitted, false);
        writeReviewRows(reviewOut, emitted);
        writeRows(provenOut, provenCandidates(emitted), true);
        log.append('\n').append("wrote ranked review rows to ").append(out).append('\n');
        log.append("wrote review metadata to ").append(reviewOut).append('\n');
        log.append("wrote live-proven rows to ").append(provenOut).append('\n');
        log.append("NOT merged into transport-overrides.tsv - Route A proof is required first.\n");

        Files.write(project.resolve("tools/cache-derived-gates-summary.txt"),
            log.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(log);
    }

    private static void writeRows(Path out, List<Candidate> candidates, boolean provenOnly)
        throws IOException
    {
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8))
        {
            w.write(provenOnly
                ? "# LIVE-PROVEN candidate transport rows derived from the OSRS cache by generateTransportRows."
                : "# RANKED CANDIDATE transport rows derived from the OSRS cache by generateTransportRows.");
            w.newLine();
            w.write("# NOT active. Review, then move rows into transport-overrides.tsv to use them.");
            w.newLine();
            w.write("#");
            w.newLine();
            if (provenOnly)
            {
                w.write("# Rows here matched Route A live validator edges (DREW_MAP_VALIDATE");
                w.newLine();
                w.write("# OURS_BLOCKS_LIVE_OPEN). They still need human review before copying.");
            }
            else
            {
                w.write("# Sorted by detour pain, highest first. Every row below is still only a");
                w.newLine();
                w.write("# candidate: Route A live mismatch proof is required before merge.");
            }
            w.newLine();
            int rank = 1;
            for (Candidate c : candidates)
            {
                w.write("# rank=" + rank + " detour=" + c.detour.label() + " routeA="
                    + (c.routeAProven ? "proven" : "missing") + " edge=" + c.edgeKey
                    + " name=" + c.name);
                w.newLine();
                w.write(c.row(c.x, c.y, c.nx, c.ny));
                w.newLine();
                w.write(c.row(c.nx, c.ny, c.x, c.y));
                w.newLine();
                rank++;
            }
        }
    }

    private static void writeReviewRows(Path out, List<Candidate> candidates) throws IOException
    {
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8))
        {
            w.write("# Machine-readable review queue. One row per crossing; not an active transport file.");
            w.newLine();
            w.write("rank\trouteAProven\tdetourSteps\tdetourCapped\tedgeKey\tfrom\tto\tdirection\tname\taction\tlabel");
            w.newLine();
            for (int i = 0; i < candidates.size(); i++)
            {
                Candidate c = candidates.get(i);
                w.write(Integer.toString(i + 1));
                w.write('\t');
                w.write(Boolean.toString(c.routeAProven));
                w.write('\t');
                w.write(c.detour.capped ? ">" + DETOUR_CAP_STEPS : Integer.toString(c.detour.steps));
                w.write('\t');
                w.write(Boolean.toString(c.detour.capped));
                w.write('\t');
                w.write(c.edgeKey.toString());
                w.write('\t');
                w.write(c.fromPoint());
                w.write('\t');
                w.write(c.toPoint());
                w.write('\t');
                w.write(Character.toString(c.direction));
                w.write('\t');
                w.write(c.name);
                w.write('\t');
                w.write(c.action);
                w.write('\t');
                w.write(c.label());
                w.newLine();
            }
        }
    }

    private static List<Candidate> provenCandidates(List<Candidate> candidates)
    {
        List<Candidate> proven = new ArrayList<>();
        for (Candidate candidate : candidates)
        {
            if (candidate.routeAProven)
            {
                proven.add(candidate);
            }
        }
        return proven;
    }

    private static boolean isStandable(DrewsHelperCollisionMap map, int x, int y, int plane)
    {
        return map.canMoveNorth(x, y, plane) || map.canMoveSouth(x, y, plane)
            || map.canMoveEast(x, y, plane) || map.canMoveWest(x, y, plane);
    }

    private static boolean isWallPlacement(int locType)
    {
        for (int wall : WALL_LOC_TYPES)
        {
            if (wall == locType)
            {
                return true;
            }
        }
        return false;
    }

    private static String excludedReason(String name)
    {
        if (name == null)
        {
            return null;
        }
        return EXCLUDED_NAMES.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> excludedNames()
    {
        Map<String, String> names = new TreeMap<>();
        names.put("cloud bank", "instance scenery");
        names.put("portal of death", "death-office/instance portal");
        names.put("oozing barrier", "minigame/raid barrier");
        names.put("wall of flame", "instance scenery");
        names.put("gate of war", "pvm hub/minigame portal");
        names.put("energy barrier", "minigame barrier");
        names.put("neutral barrier", "minigame barrier");
        names.put("blue barrier", "minigame barrier");
        names.put("red barrier", "minigame barrier");
        names.put("alchemical door", "raid/puzzle instance");
        return Collections.unmodifiableMap(names);
    }

    private static Detour measureDetour(
        DrewsHelperCollisionMap map, int startX, int startY, int plane, int targetX, int targetY)
    {
        int minX = Math.min(startX, targetX) - DETOUR_CAP_STEPS;
        int maxX = Math.max(startX, targetX) + DETOUR_CAP_STEPS;
        int minY = Math.min(startY, targetY) - DETOUR_CAP_STEPS;
        int maxY = Math.max(startY, targetY) + DETOUR_CAP_STEPS;

        Deque<Node> queue = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();
        Node start = new Node(startX, startY, 0);
        queue.add(start);
        seen.add(pack(startX, startY, plane));

        while (!queue.isEmpty())
        {
            Node node = queue.removeFirst();
            if (node.x == targetX && node.y == targetY)
            {
                return new Detour(node.distance, false);
            }
            if (node.distance >= DETOUR_CAP_STEPS)
            {
                continue;
            }

            enqueueIfLegal(map, plane, node, 0, 1, minX, maxX, minY, maxY, targetX, targetY,
                queue, seen);
            enqueueIfLegal(map, plane, node, 1, 1, minX, maxX, minY, maxY, targetX, targetY,
                queue, seen);
            enqueueIfLegal(map, plane, node, 1, 0, minX, maxX, minY, maxY, targetX, targetY,
                queue, seen);
            enqueueIfLegal(map, plane, node, 1, -1, minX, maxX, minY, maxY, targetX, targetY,
                queue, seen);
            enqueueIfLegal(map, plane, node, 0, -1, minX, maxX, minY, maxY, targetX, targetY,
                queue, seen);
            enqueueIfLegal(map, plane, node, -1, -1, minX, maxX, minY, maxY, targetX, targetY,
                queue, seen);
            enqueueIfLegal(map, plane, node, -1, 0, minX, maxX, minY, maxY, targetX, targetY,
                queue, seen);
            enqueueIfLegal(map, plane, node, -1, 1, minX, maxX, minY, maxY, targetX, targetY,
                queue, seen);
        }

        return new Detour(DETOUR_CAP_STEPS + 1, true);
    }

    private static void enqueueIfLegal(
        DrewsHelperCollisionMap map,
        int plane,
        Node node,
        int dx,
        int dy,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int targetX,
        int targetY,
        Deque<Node> queue,
        Set<Integer> seen)
    {
        int nextX = node.x + dx;
        int nextY = node.y + dy;
        if (nextX < minX || nextX > maxX || nextY < minY || nextY > maxY)
        {
            return;
        }
        if (!canStep(map, node.x, node.y, plane, dx, dy))
        {
            return;
        }
        int packed = pack(nextX, nextY, plane);
        if (seen.add(packed))
        {
            queue.addLast(new Node(nextX, nextY, node.distance + 1));
        }
    }

    private static boolean canStep(DrewsHelperCollisionMap map, int x, int y, int plane, int dx, int dy)
    {
        if (dx == 0 && dy == 1)
        {
            return map.canMoveNorth(x, y, plane);
        }
        if (dx == 1 && dy == 1)
        {
            return map.canMoveNorthEast(x, y, plane);
        }
        if (dx == 1 && dy == 0)
        {
            return map.canMoveEast(x, y, plane);
        }
        if (dx == 1 && dy == -1)
        {
            return map.canMoveSouthEast(x, y, plane);
        }
        if (dx == 0 && dy == -1)
        {
            return map.canMoveSouth(x, y, plane);
        }
        if (dx == -1 && dy == -1)
        {
            return map.canMoveSouthWest(x, y, plane);
        }
        if (dx == -1 && dy == 0)
        {
            return map.canMoveWest(x, y, plane);
        }
        if (dx == -1 && dy == 1)
        {
            return map.canMoveNorthWest(x, y, plane);
        }
        return false;
    }

    private static int pack(int x, int y, int plane)
    {
        return ((plane & 3) << 28) | ((x & 0x3fff) << 14) | (y & 0x3fff);
    }

    private static ProofData readLiveProof(Path project) throws IOException
    {
        Set<EdgeKey> edges = new HashSet<>();
        List<Path> sources = new ArrayList<>();
        readProofFile(project.resolve("tools/route-a-live-mismatches.txt"), edges, sources);
        readProofFile(project.resolve("tools/route-a-live-mismatches.tsv"), edges, sources);
        return new ProofData(edges, sources);
    }

    private static void readProofFile(Path path, Set<EdgeKey> edges, List<Path> sources)
        throws IOException
    {
        if (!Files.exists(path))
        {
            return;
        }

        boolean sourceUsed = false;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                EdgeKey edge = parseProofEdge(line);
                if (edge != null)
                {
                    edges.add(edge);
                    sourceUsed = true;
                }
            }
        }
        catch (NoSuchFileException ex)
        {
            return;
        }

        if (sourceUsed)
        {
            sources.add(path);
        }
    }

    private static EdgeKey parseProofEdge(String line)
    {
        if (line == null || line.trim().isEmpty() || line.startsWith("#"))
        {
            return null;
        }

        Matcher live = LIVE_MISMATCH_PATTERN.matcher(line);
        if (live.find())
        {
            return EdgeKey.fromDirection(
                Integer.parseInt(live.group(1)),
                Integer.parseInt(live.group(2)),
                Integer.parseInt(live.group(3)),
                live.group(4).charAt(0));
        }

        Matcher tsv = TSV_PROOF_PATTERN.matcher(line);
        if (tsv.matches())
        {
            return EdgeKey.fromDirection(
                Integer.parseInt(tsv.group(1)),
                Integer.parseInt(tsv.group(2)),
                Integer.parseInt(tsv.group(3)),
                tsv.group(4).charAt(0));
        }
        return null;
    }

    private static String clean(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static final class Candidate
    {
        private static final Comparator<Candidate> BY_REVIEW_PRIORITY =
            Comparator.<Candidate>comparingInt(c -> c.routeAProven ? 0 : 1)
                .thenComparing(Comparator.comparingInt((Candidate c) -> c.detour.rankValue())
                    .reversed())
                .thenComparing(c -> c.name.toLowerCase(Locale.ROOT))
                .thenComparing(c -> c.edgeKey.toString());

        private final int x;
        private final int y;
        private final int z;
        private final int nx;
        private final int ny;
        private final String name;
        private final String action;
        private final char direction;
        private final EdgeKey edgeKey;
        private final Detour detour;
        private final boolean routeAProven;

        private Candidate(
            int x,
            int y,
            int z,
            int nx,
            int ny,
            String name,
            String action,
            char direction,
            EdgeKey edgeKey,
            Detour detour,
            boolean routeAProven)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.name = clean(name).isEmpty() ? "(unnamed)" : clean(name);
            this.action = clean(action).isEmpty() ? "Open" : clean(action);
            this.direction = direction;
            this.edgeKey = edgeKey;
            this.detour = detour;
            this.routeAProven = routeAProven;
        }

        private String row(int fx, int fy, int tx, int ty)
        {
            return "BASELINE\t" + fx + "," + fy + "," + z + "\t" + tx + "," + ty + "," + z
                + "\t" + label() + "\t1";
        }

        private String label()
        {
            return action + " " + name;
        }

        private String fromPoint()
        {
            return x + "," + y + "," + z;
        }

        private String toPoint()
        {
            return nx + "," + ny + "," + z;
        }
    }

    private static final class Detour
    {
        private final int steps;
        private final boolean capped;

        private Detour(int steps, boolean capped)
        {
            this.steps = steps;
            this.capped = capped;
        }

        private int rankValue()
        {
            return capped ? DETOUR_CAP_STEPS + 1 : steps;
        }

        private String label()
        {
            return capped ? ">" + DETOUR_CAP_STEPS : Integer.toString(steps);
        }
    }

    private static final class EdgeKey
    {
        private final int x;
        private final int y;
        private final int plane;
        private final char direction;

        private EdgeKey(int x, int y, int plane, char direction)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.direction = direction;
        }

        private static EdgeKey between(int x, int y, int plane, int nx, int ny)
        {
            if (nx == x + 1 && ny == y)
            {
                return new EdgeKey(x, y, plane, 'E');
            }
            if (nx == x - 1 && ny == y)
            {
                return new EdgeKey(nx, ny, plane, 'E');
            }
            if (nx == x && ny == y + 1)
            {
                return new EdgeKey(x, y, plane, 'N');
            }
            if (nx == x && ny == y - 1)
            {
                return new EdgeKey(nx, ny, plane, 'N');
            }
            throw new IllegalArgumentException("Not a cardinal edge: " + x + "," + y + " -> "
                + nx + "," + ny);
        }

        private static EdgeKey fromDirection(int x, int y, int plane, char direction)
        {
            if (direction == 'E')
            {
                return new EdgeKey(x, y, plane, 'E');
            }
            if (direction == 'W')
            {
                return new EdgeKey(x - 1, y, plane, 'E');
            }
            if (direction == 'N')
            {
                return new EdgeKey(x, y, plane, 'N');
            }
            if (direction == 'S')
            {
                return new EdgeKey(x, y - 1, plane, 'N');
            }
            throw new IllegalArgumentException("Bad direction: " + direction);
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof EdgeKey))
            {
                return false;
            }
            EdgeKey edge = (EdgeKey) other;
            return x == edge.x && y == edge.y && plane == edge.plane
                && direction == edge.direction;
        }

        @Override
        public int hashCode()
        {
            int hash = x;
            hash = hash * 31 + y;
            hash = hash * 31 + plane;
            hash = hash * 31 + direction;
            return hash;
        }

        @Override
        public String toString()
        {
            return x + "," + y + "," + plane + " " + direction;
        }
    }

    private static final class Node
    {
        private final int x;
        private final int y;
        private final int distance;

        private Node(int x, int y, int distance)
        {
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
    }

    private static final class ProofData
    {
        private final Set<EdgeKey> edges;
        private final List<Path> sources;

        private ProofData(Set<EdgeKey> edges, List<Path> sources)
        {
            this.edges = edges;
            this.sources = sources;
        }
    }
}
