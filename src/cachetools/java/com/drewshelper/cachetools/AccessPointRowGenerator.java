package com.drewshelper.cachetools;

import com.drewshelper.routing.DrewsHelperCollisionMap;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

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
    /** Wall placement types - the only ones whose orientation defines a crossing. */
    private static final int[] WALL_LOC_TYPES = {0, 1, 2, 3, 9};

    /** Orientation to compass direction. Proved against the Falador gate, not assumed. */
    private static final char[] DIRECTION_BY_ORIENTATION = {'W', 'N', 'E', 'S'};

    private AccessPointRowGenerator()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path project = Paths.get(args.length > 0 ? args[0] : System.getProperty("user.dir"));
        Path dump = project.resolve("tools/cache-access-points.tsv");
        Path out = project.resolve("tools/cache-derived-gates.tsv");

        if (!Files.exists(dump))
        {
            throw new IOException("No dump at " + dump + " - run dumpAccessPoints first.");
        }

        DrewsHelperCollisionMap map = DrewsHelperCollisionMap.loadDefault();
        List<String> lines = Files.readAllLines(dump, StandardCharsets.UTF_8);

        StringBuilder log = new StringBuilder();
        Map<Character, int[]> byDirection = new TreeMap<>();
        List<Candidate> emitted = new ArrayList<>();

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

            tally[2]++;
            emitted.add(new Candidate(x, y, z, nx, ny, name, p[5]));
        }

        // ---- the mapping proof has to come first; if it fails, nothing below is trustworthy ----
        log.append("=== ORIENTATION MAPPING PROOF ===\n");
        log.append("If 0=W 1=N 2=E 3=S is right, a wall object's predicted edge should be blocked\n");
        log.append("in our collision map nearly always. A low rate means the mapping is wrong.\n\n");
        log.append(String.format(Locale.ROOT, "  %-4s %8s %10s %9s %8s%n",
            "dir", "walls", "blocked", "rate", "emitted"));
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
        log.append("  CANDIDATE CROSSINGS        : ").append(emitted.size()).append('\n');
        log.append("  rows to write (both ways)  : ").append(emitted.size() * 2).append('\n');

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

        writeRows(out, emitted);
        log.append('\n').append("wrote ").append(emitted.size() * 2).append(" rows to ").append(out)
            .append('\n');
        log.append("NOT merged into transport-overrides.tsv - review first, then move rows across.\n");

        Files.write(project.resolve("tools/cache-derived-gates-summary.txt"),
            log.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(log);
    }

    private static void writeRows(Path out, List<Candidate> candidates) throws IOException
    {
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8))
        {
            w.write("# CANDIDATE transport rows derived from the OSRS cache by generateTransportRows.");
            w.newLine();
            w.write("# NOT active. Review, then move rows into transport-overrides.tsv to use them.");
            w.newLine();
            w.write("#");
            w.newLine();
            w.write("# Every row below is only a candidate: the object is wall-mounted, its orientation");
            w.newLine();
            w.write("# predicts the crossing, our collision map confirms that edge is blocked, and both");
            w.newLine();
            w.write("# tiles are standable. Live-client or manual validation is still required before merge.");
            w.newLine();
            for (Candidate c : candidates)
            {
                w.write(c.row(c.x, c.y, c.nx, c.ny));
                w.newLine();
                w.write(c.row(c.nx, c.ny, c.x, c.y));
                w.newLine();
            }
        }
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

    private static final class Candidate
    {
        private final int x;
        private final int y;
        private final int z;
        private final int nx;
        private final int ny;
        private final String name;
        private final String action;

        private Candidate(int x, int y, int z, int nx, int ny, String name, String action)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.name = name == null || name.isEmpty() ? "(unnamed)" : name;
            this.action = action == null || action.isEmpty() ? "Open" : action;
        }

        private String row(int fx, int fy, int tx, int ty)
        {
            return "BASELINE\t" + fx + "," + fy + "," + z + "\t" + tx + "," + ty + "," + z
                + "\t" + action + " " + name + "\t1";
        }
    }
}
