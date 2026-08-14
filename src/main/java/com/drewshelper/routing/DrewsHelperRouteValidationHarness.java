package com.drewshelper.routing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;

/**
 * Offline route gate plus hand-walked evidence summariser.
 *
 * <p>This is deliberately report-only. It reads the same evidence files Myth captures in RuneLite,
 * correlates route-segment divergence with object/door state rows, and runs deterministic route
 * solves against the shipped map. It does not promote rows or rewrite resources.
 */
public final class DrewsHelperRouteValidationHarness
{
    static final String SEGMENT_PREFIX = "DREW_ROUTE_SEGMENT v1";
    static final String OBJECT_PREFIX = "DREW_OBJECT_STATE v1";

    private static final int DEFAULT_SAMPLE_LIMIT = 1_000;
    private static final int DEFAULT_POINT_LIMIT_PER_BOX = 80;
    private static final int DEFAULT_POINT_STRIDE = 8;
    private static final int EXAMPLE_LIMIT = 12;
    private static final int OBJECT_CORRELATION_RADIUS = 1;
    private static final Pattern POINT_PATTERN =
        Pattern.compile("\\((-?\\d+),(-?\\d+),(-?\\d+)\\)");
    private static final Pattern TILE_PATTERN =
        Pattern.compile("^(-?\\d+),(-?\\d+),(-?\\d+)$");

    private DrewsHelperRouteValidationHarness()
    {
    }

    public static void main(String[] args) throws Exception
    {
        Request request = Request.parse(args, Paths.get(System.getProperty("user.dir")));
        DrewsHelperCollisionMap map = DrewsHelperCollisionMap.loadDefault();
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.empty();
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(map, graph);

        OfflineReport offline = request.skipOffline
            ? OfflineReport.skipped(request.sampleLimit)
            : validateOffline(map, graph, engine, request.boxes, request.sampleLimit);
        EvidenceReport evidence = analyseEvidence(
            readLinesIfPresent(request.segmentsFile),
            readLinesIfPresent(request.objectsFile)
        );

        String report = renderReport(request, offline, evidence);
        Files.createDirectories(request.outputFile.getParent());
        Files.write(request.outputFile, report.getBytes(StandardCharsets.UTF_8));
        System.out.print(report);
    }

    static OfflineReport validateOffline(
        DrewsHelperMovementMap map,
        DrewsHelperTransportGraph graph,
        DrewsHelperWalkingRouteEngine engine,
        List<SearchBox> boxes,
        int sampleLimit
    ) throws InterruptedException
    {
        List<CandidatePair> pairs = candidatePairs(map, boxes, sampleLimit);
        OfflineReport report = new OfflineReport(sampleLimit, pairs.size());
        for (CandidatePair pair : pairs)
        {
            report.attempted++;
            DrewsHelperRouteSnapshot snapshot = engine.solve(
                pair.start,
                Collections.singletonList(pair.end)
            );
            if (snapshot.getStatus() != DrewsHelperRouteStatus.READY)
            {
                report.noPath++;
                report.addExample("NO_PATH " + pair.format() + " status=" + snapshot.getStatus());
                continue;
            }

            List<WorldPoint> path = snapshot.getPath();
            List<String> issues = pathIssues(map, graph, path, pair.start, pair.end);
            if (!issues.isEmpty())
            {
                report.badStructure++;
                report.addExample("BAD_STRUCTURE " + pair.format() + " " + String.join(",", issues));
            }
            else
            {
                report.solvedClean++;
            }

            report.clientDistance += DrewsHelperRouteBenchmark.pathDistance(path);
            report.clientTurns += DrewsHelperRouteBenchmark.turnCount(path);

            DrewsHelperRouteSnapshot shape = engine.solveWithShapeRankingWithoutLocalWalkingOverrides(
                pair.start,
                Collections.singletonList(pair.end)
            );
            if (shape.getStatus() != DrewsHelperRouteStatus.READY)
            {
                report.shapeNoPath++;
                continue;
            }

            List<WorldPoint> shapePath = shape.getPath();
            int clientLength = DrewsHelperRouteBenchmark.pathDistance(path);
            int shapeLength = DrewsHelperRouteBenchmark.pathDistance(shapePath);
            int clientTurns = DrewsHelperRouteBenchmark.turnCount(path);
            int shapeTurns = DrewsHelperRouteBenchmark.turnCount(shapePath);
            int lengthDelta = shapeLength - clientLength;
            int turnDelta = shapeTurns - clientTurns;
            report.maxAbsLengthDelta = Math.max(report.maxAbsLengthDelta, Math.abs(lengthDelta));
            report.maxAbsTurnDelta = Math.max(report.maxAbsTurnDelta, Math.abs(turnDelta));
            if (!path.equals(shapePath))
            {
                report.shapeDifferent++;
                if (shapeLength < clientLength)
                {
                    report.shapeShorter++;
                }
                if (shapeTurns < clientTurns)
                {
                    report.shapeFewerTurns++;
                }
                report.addExample("SHAPE_DIFF " + pair.format()
                    + " lenDelta=" + lengthDelta
                    + " turnDelta=" + turnDelta
                    + " client=" + DrewsHelperRouteBenchmark.formatPathPrefix(path)
                    + " shape=" + DrewsHelperRouteBenchmark.formatPathPrefix(shapePath));
            }
        }
        return report;
    }

    static List<String> pathIssues(
        DrewsHelperMovementMap map,
        DrewsHelperTransportGraph graph,
        List<WorldPoint> path,
        WorldPoint expectedStart,
        WorldPoint expectedEnd
    )
    {
        List<String> issues = new ArrayList<>();
        if (path == null || path.isEmpty())
        {
            issues.add("empty-path");
            return issues;
        }

        if (!path.get(0).equals(expectedStart))
        {
            issues.add("start=" + formatPoint(path.get(0)));
        }
        if (!path.get(path.size() - 1).equals(expectedEnd))
        {
            issues.add("end=" + formatPoint(path.get(path.size() - 1)));
        }

        for (int i = 1; i < path.size(); i++)
        {
            WorldPoint from = path.get(i - 1);
            WorldPoint to = path.get(i);
            if (!isLegalStep(map, graph, from, to))
            {
                issues.add("illegal-step@" + i + ":" + formatPoint(from) + "->" + formatPoint(to));
                if (issues.size() >= 4)
                {
                    break;
                }
            }
        }
        return issues;
    }

    static boolean isLegalStep(
        DrewsHelperMovementMap map,
        DrewsHelperTransportGraph graph,
        WorldPoint from,
        WorldPoint to
    )
    {
        if (from == null || to == null || map == null)
        {
            return false;
        }
        if (graph != null && DrewsHelperTravelEstimate.transportLabel(graph, from, to) != null)
        {
            return true;
        }
        if (from.getPlane() != to.getPlane())
        {
            return false;
        }

        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx == 0 && dy == 0)
        {
            return false;
        }
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1)
        {
            return false;
        }
        int x = from.getX();
        int y = from.getY();
        int plane = from.getPlane();
        if (dx == 0 && dy == 1)
        {
            return map.canMoveNorth(x, y, plane);
        }
        if (dx == 0 && dy == -1)
        {
            return map.canMoveSouth(x, y, plane);
        }
        if (dx == 1 && dy == 0)
        {
            return map.canMoveEast(x, y, plane);
        }
        if (dx == -1 && dy == 0)
        {
            return map.canMoveWest(x, y, plane);
        }
        if (dx == 1 && dy == 1)
        {
            return map.canMoveNorthEast(x, y, plane);
        }
        if (dx == -1 && dy == 1)
        {
            return map.canMoveNorthWest(x, y, plane);
        }
        if (dx == 1 && dy == -1)
        {
            return map.canMoveSouthEast(x, y, plane);
        }
        return map.canMoveSouthWest(x, y, plane);
    }

    static EvidenceReport analyseEvidence(List<String> segmentLines, List<String> objectLines)
    {
        EvidenceReport report = new EvidenceReport();
        Map<WorldPoint, List<ObjectStateEvidence>> objectsByTile = new LinkedHashMap<>();

        for (String line : safeLines(objectLines))
        {
            ObjectStateEvidence object = ObjectStateEvidence.parse(line);
            if (object == null)
            {
                continue;
            }
            report.objectRows++;
            increment(report.objectCategories, object.category);
            increment(report.objectStates, object.state);
            increment(report.objectMapConfidence, object.mapConfidence);
            objectsByTile.computeIfAbsent(object.tile, key -> new ArrayList<>()).add(object);
        }

        for (String line : safeLines(segmentLines))
        {
            RouteSegmentEvidence segment = RouteSegmentEvidence.parse(line);
            if (segment == null)
            {
                continue;
            }
            report.segmentRows++;
            increment(report.segmentClassifications, segment.classification);
            if (segment.completed)
            {
                report.completedSegments++;
            }
            else
            {
                report.interruptedSegments++;
            }
            if ("match".equals(segment.classification))
            {
                report.matchingSegments++;
            }
            else
            {
                report.divergentSegments++;
            }
            if (segment.edgeValidation.contains("legal=false"))
            {
                report.illegalObservedEdges++;
            }
            if (segment.edgeValidation.contains("longer=true"))
            {
                report.longerLegalDetours++;
            }

            CorrelatedSegment correlated = correlate(segment, objectsByTile);
            if (correlated != null)
            {
                report.addCorrelation(correlated.format());
            }
        }

        return report;
    }

    static Map<String, String> parseFields(String line, String rowPrefix)
    {
        if (line == null || rowPrefix == null)
        {
            return Collections.emptyMap();
        }
        int start = line.indexOf(rowPrefix);
        if (start < 0)
        {
            return Collections.emptyMap();
        }

        Map<String, String> fields = new LinkedHashMap<>();
        int index = start + rowPrefix.length();
        while (index < line.length())
        {
            while (index < line.length() && Character.isWhitespace(line.charAt(index)))
            {
                index++;
            }
            if (index >= line.length())
            {
                break;
            }

            int keyStart = index;
            while (index < line.length() && line.charAt(index) != '='
                && !Character.isWhitespace(line.charAt(index)))
            {
                index++;
            }
            if (index >= line.length() || line.charAt(index) != '=')
            {
                while (index < line.length() && !Character.isWhitespace(line.charAt(index)))
                {
                    index++;
                }
                continue;
            }

            String key = line.substring(keyStart, index);
            index++;
            int valueStart = index;
            if (index < line.length() && (line.charAt(index) == '{' || line.charAt(index) == '['))
            {
                index = consumeBalanced(line, index, line.charAt(index),
                    line.charAt(index) == '{' ? '}' : ']');
            }
            else
            {
                while (index < line.length() && !Character.isWhitespace(line.charAt(index)))
                {
                    index++;
                }
            }
            fields.put(key, line.substring(valueStart, Math.min(index, line.length())));
        }
        return fields;
    }

    static List<WorldPoint> parsePath(String encoded)
    {
        if (encoded == null || encoded.isEmpty())
        {
            return Collections.emptyList();
        }
        List<WorldPoint> points = new ArrayList<>();
        Matcher matcher = POINT_PATTERN.matcher(encoded);
        while (matcher.find())
        {
            points.add(new WorldPoint(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
            ));
        }
        return points;
    }

    private static CorrelatedSegment correlate(
        RouteSegmentEvidence segment,
        Map<WorldPoint, List<ObjectStateEvidence>> objectsByTile
    )
    {
        if ("match".equals(segment.classification) || segment.expectedPath.isEmpty()
            || segment.actualPath.isEmpty())
        {
            return null;
        }

        int divergenceIndex = DrewsHelperRouteBenchmark.firstDivergenceIndex(
            segment.expectedPath,
            segment.actualPath,
            segment.completed
        );
        if (divergenceIndex < 0)
        {
            return null;
        }

        List<WorldPoint> anchors = new ArrayList<>();
        if (divergenceIndex > 0 && divergenceIndex - 1 < segment.actualPath.size())
        {
            anchors.add(segment.actualPath.get(divergenceIndex - 1));
        }
        WorldPoint predicted = DrewsHelperRouteBenchmark.pointAt(segment.expectedPath, divergenceIndex);
        WorldPoint actual = DrewsHelperRouteBenchmark.pointAt(segment.actualPath, divergenceIndex);
        if (predicted != null)
        {
            anchors.add(predicted);
        }
        if (actual != null)
        {
            anchors.add(actual);
        }

        List<ObjectStateEvidence> nearbyObjects = nearbyObjects(objectsByTile, anchors);
        return new CorrelatedSegment(segment, divergenceIndex, predicted, actual, nearbyObjects);
    }

    private static List<ObjectStateEvidence> nearbyObjects(
        Map<WorldPoint, List<ObjectStateEvidence>> objectsByTile,
        List<WorldPoint> anchors
    )
    {
        if (objectsByTile.isEmpty() || anchors.isEmpty())
        {
            return Collections.emptyList();
        }
        List<ObjectStateEvidence> found = new ArrayList<>();
        for (Map.Entry<WorldPoint, List<ObjectStateEvidence>> entry : objectsByTile.entrySet())
        {
            for (WorldPoint anchor : anchors)
            {
                if (samePlaneDistance(entry.getKey(), anchor) <= OBJECT_CORRELATION_RADIUS)
                {
                    found.addAll(entry.getValue());
                    break;
                }
            }
            if (found.size() >= 4)
            {
                break;
            }
        }
        return found;
    }

    private static int samePlaneDistance(WorldPoint first, WorldPoint second)
    {
        if (first == null || second == null || first.getPlane() != second.getPlane())
        {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getY() - second.getY()));
    }

    private static List<CandidatePair> candidatePairs(
        DrewsHelperMovementMap map,
        List<SearchBox> boxes,
        int limit
    )
    {
        List<CandidatePair> pairs = new ArrayList<>();
        for (SearchBox box : boxes)
        {
            List<WorldPoint> points = sampledPoints(map, box);
            for (WorldPoint start : points)
            {
                for (WorldPoint end : points)
                {
                    if (!start.equals(end))
                    {
                        pairs.add(new CandidatePair(start, end));
                    }
                }
            }
        }

        pairs.sort(Comparator
            .comparingInt(CandidatePair::distance).reversed()
            .thenComparingInt(CandidatePair::startX)
            .thenComparingInt(CandidatePair::startY)
            .thenComparingInt(CandidatePair::endX)
            .thenComparingInt(CandidatePair::endY));
        if (pairs.size() <= limit)
        {
            return pairs;
        }
        return new ArrayList<>(pairs.subList(0, limit));
    }

    private static List<WorldPoint> sampledPoints(DrewsHelperMovementMap map, SearchBox box)
    {
        List<WorldPoint> points = new ArrayList<>();
        for (int x = box.minX; x <= box.maxX; x += DEFAULT_POINT_STRIDE)
        {
            for (int y = box.minY; y <= box.maxY; y += DEFAULT_POINT_STRIDE)
            {
                if (map instanceof DrewsHelperCollisionMap
                    && !((DrewsHelperCollisionMap) map).hasRegion(x, y))
                {
                    continue;
                }
                WorldPoint point = new WorldPoint(x, y, box.plane);
                if (hasAnyStep(map, point))
                {
                    points.add(point);
                }
            }
        }

        if (points.size() <= DEFAULT_POINT_LIMIT_PER_BOX)
        {
            return points;
        }
        List<WorldPoint> capped = new ArrayList<>();
        int stride = (points.size() + DEFAULT_POINT_LIMIT_PER_BOX - 1) / DEFAULT_POINT_LIMIT_PER_BOX;
        for (int i = 0; i < points.size() && capped.size() < DEFAULT_POINT_LIMIT_PER_BOX; i += stride)
        {
            capped.add(points.get(i));
        }
        return capped;
    }

    private static boolean hasAnyStep(DrewsHelperMovementMap map, WorldPoint point)
    {
        int x = point.getX();
        int y = point.getY();
        int plane = point.getPlane();
        return map.canMoveNorth(x, y, plane)
            || map.canMoveSouth(x, y, plane)
            || map.canMoveEast(x, y, plane)
            || map.canMoveWest(x, y, plane)
            || map.canMoveNorthEast(x, y, plane)
            || map.canMoveNorthWest(x, y, plane)
            || map.canMoveSouthEast(x, y, plane)
            || map.canMoveSouthWest(x, y, plane);
    }

    private static String renderReport(Request request, OfflineReport offline, EvidenceReport evidence)
    {
        StringBuilder out = new StringBuilder();
        out.append("DREW_ROUTE_VALIDATION_HARNESS v1\n");
        out.append("output=").append(request.outputFile).append('\n');
        out.append("segmentsFile=").append(request.segmentsFile).append('\n');
        out.append("objectsFile=").append(request.objectsFile).append('\n');
        out.append('\n');

        out.append("OFFLINE STRUCTURAL VALIDATIONS\n");
        out.append("sampleLimit=").append(offline.sampleLimit)
            .append(" candidatePairs=").append(offline.candidatePairs)
            .append(" attempted=").append(offline.attempted)
            .append(" solvedClean=").append(offline.solvedClean)
            .append(" noPath=").append(offline.noPath)
            .append(" badStructure=").append(offline.badStructure)
            .append('\n');
        out.append("shapeDifferent=").append(offline.shapeDifferent)
            .append(" shapeNoPath=").append(offline.shapeNoPath)
            .append(" shapeShorter=").append(offline.shapeShorter)
            .append(" shapeFewerTurns=").append(offline.shapeFewerTurns)
            .append(" maxAbsLengthDelta=").append(offline.maxAbsLengthDelta)
            .append(" maxAbsTurnDelta=").append(offline.maxAbsTurnDelta)
            .append('\n');
        out.append("averageClientDistance=").append(offline.averageDistance())
            .append(" averageClientTurns=").append(offline.averageTurns())
            .append(" verdict=").append(offline.verdict())
            .append('\n');
        appendExamples(out, offline.examples);
        out.append('\n');

        out.append("HAND-WALKED ROUTE SEGMENTS\n");
        out.append("rows=").append(evidence.segmentRows)
            .append(" completed=").append(evidence.completedSegments)
            .append(" interrupted=").append(evidence.interruptedSegments)
            .append(" matches=").append(evidence.matchingSegments)
            .append(" divergent=").append(evidence.divergentSegments)
            .append(" illegalObservedEdges=").append(evidence.illegalObservedEdges)
            .append(" longerLegalDetours=").append(evidence.longerLegalDetours)
            .append('\n');
        out.append("classificationCounts=").append(formatCounts(evidence.segmentClassifications)).append('\n');
        out.append('\n');

        out.append("OBJECT AND DOOR STATE EVIDENCE\n");
        out.append("rows=").append(evidence.objectRows)
            .append(" categories=").append(formatCounts(evidence.objectCategories))
            .append('\n');
        out.append("states=").append(formatCounts(evidence.objectStates)).append('\n');
        out.append("mapConfidence=").append(formatCounts(evidence.objectMapConfidence)).append('\n');
        out.append('\n');

        out.append("CORRELATED ROUTE/OBJECT EXAMPLES\n");
        appendExamples(out, evidence.correlations);
        out.append('\n');

        out.append("HARNESS DECISION\n");
        out.append("This report is evidence-only. It does not promote collision-map rows, object profiles, ")
            .append("or transport rows.\n");
        out.append("Use badStructure and illegalObservedEdges as hard gates; use divergent hand-walked ")
            .append("segments plus nearby object rows to pick the next live test target.\n");
        return out.toString();
    }

    private static void appendExamples(StringBuilder out, List<String> examples)
    {
        if (examples.isEmpty())
        {
            out.append("examples=none\n");
            return;
        }
        for (int i = 0; i < examples.size(); i++)
        {
            out.append("example").append(i + 1).append('=').append(examples.get(i)).append('\n');
        }
    }

    private static String formatCounts(Map<String, Integer> counts)
    {
        if (counts.isEmpty())
        {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet())
        {
            if (!first)
            {
                builder.append(' ');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append('}').toString();
    }

    private static List<String> readLinesIfPresent(Path path) throws IOException
    {
        if (path == null || !Files.isRegularFile(path))
        {
            return Collections.emptyList();
        }
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    private static List<String> safeLines(List<String> lines)
    {
        return lines == null ? Collections.emptyList() : lines;
    }

    private static int consumeBalanced(String value, int start, char open, char close)
    {
        int depth = 0;
        int index = start;
        while (index < value.length())
        {
            char c = value.charAt(index);
            if (c == open)
            {
                depth++;
            }
            else if (c == close)
            {
                depth--;
                if (depth == 0)
                {
                    return index + 1;
                }
            }
            index++;
        }
        return value.length();
    }

    private static void increment(Map<String, Integer> counts, String key)
    {
        counts.merge(key == null || key.isEmpty() ? "-" : key, 1, Integer::sum);
    }

    private static WorldPoint parsePoint(String value)
    {
        if (value == null)
        {
            return null;
        }
        Matcher matcher = POINT_PATTERN.matcher(value);
        if (!matcher.find())
        {
            return null;
        }
        return new WorldPoint(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3))
        );
    }

    private static WorldPoint parseTile(String value)
    {
        if (value == null)
        {
            return null;
        }
        Matcher matcher = TILE_PATTERN.matcher(value);
        if (!matcher.find())
        {
            return null;
        }
        return new WorldPoint(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3))
        );
    }

    private static String formatPoint(WorldPoint point)
    {
        return point == null
            ? "(null)"
            : "(" + point.getX() + "," + point.getY() + "," + point.getPlane() + ")";
    }

    static final class Request
    {
        private final Path outputFile;
        private final Path segmentsFile;
        private final Path objectsFile;
        private final List<SearchBox> boxes;
        private final int sampleLimit;
        private final boolean skipOffline;

        private Request(
            Path outputFile,
            Path segmentsFile,
            Path objectsFile,
            List<SearchBox> boxes,
            int sampleLimit,
            boolean skipOffline
        )
        {
            this.outputFile = outputFile;
            this.segmentsFile = segmentsFile;
            this.objectsFile = objectsFile;
            this.boxes = boxes;
            this.sampleLimit = sampleLimit;
            this.skipOffline = skipOffline;
        }

        private static Request parse(String[] args, Path project)
        {
            Path runelite = Paths.get(System.getProperty("user.home"), ".runelite");
            Path output = project.resolve("tools/route-validation-harness.txt");
            Path segments = runelite.resolve("drews-route-segments.txt");
            Path objects = runelite.resolve("drews-object-states.txt");
            List<SearchBox> boxes = new ArrayList<>(defaultBoxes());
            int samples = DEFAULT_SAMPLE_LIMIT;
            boolean skipOffline = false;
            boolean customBoxes = false;

            for (String arg : args == null ? new String[0] : args)
            {
                if (arg.startsWith("--out="))
                {
                    output = project.resolve(arg.substring("--out=".length())).normalize();
                }
                else if (arg.startsWith("--segments="))
                {
                    segments = Paths.get(arg.substring("--segments=".length())).toAbsolutePath().normalize();
                }
                else if (arg.startsWith("--objects="))
                {
                    objects = Paths.get(arg.substring("--objects=".length())).toAbsolutePath().normalize();
                }
                else if (arg.startsWith("--samples="))
                {
                    samples = Math.max(0, Integer.parseInt(arg.substring("--samples=".length())));
                }
                else if (arg.startsWith("--box="))
                {
                    if (!customBoxes)
                    {
                        boxes.clear();
                        customBoxes = true;
                    }
                    boxes.add(SearchBox.parse(arg.substring("--box=".length())));
                }
                else if ("--skip-offline".equals(arg))
                {
                    skipOffline = true;
                }
                else if (!arg.trim().isEmpty())
                {
                    throw new IllegalArgumentException("Unknown route validation harness arg: " + arg);
                }
            }

            return new Request(output, segments, objects,
                Collections.unmodifiableList(new ArrayList<>(boxes)), samples, skipOffline);
        }

        private static List<SearchBox> defaultBoxes()
        {
            List<SearchBox> boxes = new ArrayList<>();
            boxes.add(new SearchBox(2944, 3200, 3136, 3360, 0));
            boxes.add(new SearchBox(3200, 3200, 3320, 3520, 0));
            boxes.add(new SearchBox(3056, 3304, 3160, 3416, 0));
            return boxes;
        }
    }

    static final class SearchBox
    {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;
        private final int plane;

        SearchBox(int minX, int minY, int maxX, int maxY, int plane)
        {
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.plane = plane;
        }

        private static SearchBox parse(String encoded)
        {
            String[] parts = encoded.split(",");
            if (parts.length != 5)
            {
                throw new IllegalArgumentException("Box must be minX,minY,maxX,maxY,plane: " + encoded);
            }
            return new SearchBox(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()),
                Integer.parseInt(parts[3].trim()),
                Integer.parseInt(parts[4].trim())
            );
        }
    }

    static final class CandidatePair
    {
        private final WorldPoint start;
        private final WorldPoint end;

        private CandidatePair(WorldPoint start, WorldPoint end)
        {
            this.start = start;
            this.end = end;
        }

        private int distance()
        {
            return Math.max(
                Math.abs(start.getX() - end.getX()),
                Math.abs(start.getY() - end.getY())
            );
        }

        private int startX()
        {
            return start.getX();
        }

        private int startY()
        {
            return start.getY();
        }

        private int endX()
        {
            return end.getX();
        }

        private int endY()
        {
            return end.getY();
        }

        private String format()
        {
            return formatPoint(start) + "->" + formatPoint(end);
        }
    }

    static final class OfflineReport
    {
        private final int sampleLimit;
        private final int candidatePairs;
        private final List<String> examples = new ArrayList<>();
        private int attempted;
        private int solvedClean;
        private int noPath;
        private int badStructure;
        private int shapeDifferent;
        private int shapeNoPath;
        private int shapeShorter;
        private int shapeFewerTurns;
        private int maxAbsLengthDelta;
        private int maxAbsTurnDelta;
        private long clientDistance;
        private long clientTurns;

        private OfflineReport(int sampleLimit, int candidatePairs)
        {
            this.sampleLimit = sampleLimit;
            this.candidatePairs = candidatePairs;
        }

        private static OfflineReport skipped(int sampleLimit)
        {
            return new OfflineReport(sampleLimit, 0);
        }

        private void addExample(String example)
        {
            if (examples.size() < EXAMPLE_LIMIT)
            {
                examples.add(example);
            }
        }

        private String averageDistance()
        {
            return solvedClean == 0 ? "-" : String.format(Locale.ROOT, "%.2f", clientDistance / (double) solvedClean);
        }

        private String averageTurns()
        {
            return solvedClean == 0 ? "-" : String.format(Locale.ROOT, "%.2f", clientTurns / (double) solvedClean);
        }

        private String verdict()
        {
            if (attempted == 0)
            {
                return "SKIPPED";
            }
            if (badStructure > 0)
            {
                return "FAIL_STRUCTURE";
            }
            if (noPath > attempted / 2)
            {
                return "ATTENTION_LOW_SOLVE_RATE";
            }
            return "PASS_STRUCTURE";
        }
    }

    static final class EvidenceReport
    {
        final Map<String, Integer> segmentClassifications = new TreeMap<>();
        final Map<String, Integer> objectCategories = new TreeMap<>();
        final Map<String, Integer> objectStates = new TreeMap<>();
        final Map<String, Integer> objectMapConfidence = new TreeMap<>();
        final List<String> correlations = new ArrayList<>();
        int segmentRows;
        int completedSegments;
        int interruptedSegments;
        int matchingSegments;
        int divergentSegments;
        int illegalObservedEdges;
        int longerLegalDetours;
        int objectRows;

        private void addCorrelation(String correlation)
        {
            if (correlations.size() < EXAMPLE_LIMIT)
            {
                correlations.add(correlation);
            }
        }
    }

    private static final class RouteSegmentEvidence
    {
        private final boolean completed;
        private final String classification;
        private final WorldPoint start;
        private final WorldPoint clickDest;
        private final WorldPoint routeTarget;
        private final String edgeValidation;
        private final List<WorldPoint> expectedPath;
        private final List<WorldPoint> actualPath;

        private RouteSegmentEvidence(
            boolean completed,
            String classification,
            WorldPoint start,
            WorldPoint clickDest,
            WorldPoint routeTarget,
            String edgeValidation,
            List<WorldPoint> expectedPath,
            List<WorldPoint> actualPath
        )
        {
            this.completed = completed;
            this.classification = classification;
            this.start = start;
            this.clickDest = clickDest;
            this.routeTarget = routeTarget;
            this.edgeValidation = edgeValidation == null ? "" : edgeValidation;
            this.expectedPath = expectedPath;
            this.actualPath = actualPath;
        }

        private static RouteSegmentEvidence parse(String line)
        {
            Map<String, String> fields = parseFields(line, SEGMENT_PREFIX);
            if (fields.isEmpty())
            {
                return null;
            }
            return new RouteSegmentEvidence(
                Boolean.parseBoolean(fields.getOrDefault("completed", "false")),
                fields.getOrDefault("classification", "-"),
                parsePoint(fields.get("start")),
                parsePoint(fields.get("clickDest")),
                parsePoint(fields.get("routeTarget")),
                fields.getOrDefault("edgeValidation", ""),
                parsePath(fields.get("expectedPath")),
                parsePath(fields.get("actualPath"))
            );
        }
    }

    private static final class ObjectStateEvidence
    {
        private final WorldPoint tile;
        private final String category;
        private final String state;
        private final String name;
        private final String objectId;
        private final String activeId;
        private final String liveEdges;
        private final String mapConfidence;

        private ObjectStateEvidence(
            WorldPoint tile,
            String category,
            String state,
            String name,
            String objectId,
            String activeId,
            String liveEdges,
            String mapConfidence
        )
        {
            this.tile = tile;
            this.category = category;
            this.state = state;
            this.name = name;
            this.objectId = objectId;
            this.activeId = activeId;
            this.liveEdges = liveEdges;
            this.mapConfidence = mapConfidence;
        }

        private static ObjectStateEvidence parse(String line)
        {
            Map<String, String> fields = parseFields(line, OBJECT_PREFIX);
            if (fields.isEmpty())
            {
                return null;
            }
            WorldPoint tile = parseTile(fields.get("tile"));
            if (tile == null)
            {
                return null;
            }
            return new ObjectStateEvidence(
                tile,
                fields.getOrDefault("category", "-"),
                fields.getOrDefault("state", "-"),
                fields.getOrDefault("name", "-"),
                fields.getOrDefault("objectId", "-"),
                fields.getOrDefault("activeId", "-"),
                fields.getOrDefault("liveEdges", "-"),
                fields.getOrDefault("mapConfidence", "-")
            );
        }

        private String compact()
        {
            return "tile=" + formatPoint(tile)
                + " category=" + category
                + " state=" + state
                + " name=" + name
                + " objectId=" + objectId
                + " activeId=" + activeId
                + " liveEdges=" + liveEdges
                + " mapConfidence=" + mapConfidence;
        }
    }

    private static final class CorrelatedSegment
    {
        private final RouteSegmentEvidence segment;
        private final int divergenceIndex;
        private final WorldPoint predicted;
        private final WorldPoint actual;
        private final List<ObjectStateEvidence> nearbyObjects;

        private CorrelatedSegment(
            RouteSegmentEvidence segment,
            int divergenceIndex,
            WorldPoint predicted,
            WorldPoint actual,
            List<ObjectStateEvidence> nearbyObjects
        )
        {
            this.segment = segment;
            this.divergenceIndex = divergenceIndex;
            this.predicted = predicted;
            this.actual = actual;
            this.nearbyObjects = nearbyObjects;
        }

        private String format()
        {
            StringBuilder builder = new StringBuilder();
            builder.append("classification=").append(segment.classification)
                .append(" start=").append(formatPoint(segment.start))
                .append(" clickDest=").append(formatPoint(segment.clickDest))
                .append(" routeTarget=").append(formatPoint(segment.routeTarget))
                .append(" divergenceIdx=").append(divergenceIndex)
                .append(" predicted=").append(formatPoint(predicted))
                .append(" actual=").append(formatPoint(actual));
            if (nearbyObjects.isEmpty())
            {
                return builder.append(" nearbyObjects=none").toString();
            }

            builder.append(" nearbyObjects=[");
            for (int i = 0; i < nearbyObjects.size(); i++)
            {
                if (i > 0)
                {
                    builder.append("; ");
                }
                builder.append(nearbyObjects.get(i).compact());
            }
            return builder.append(']').toString();
        }
    }
}
