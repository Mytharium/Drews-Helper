package com.drewshelper.routing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;

/**
 * Report-only click/segment classifier for learning the OSRS client's walk-path choices.
 */
public final class DrewsHelperClickPathAnalyzer
{
    static final String CLICK_PREFIX = "DREW_CLICK_PATH v1";
    private static final int EXAMPLE_LIMIT = 12;

    private DrewsHelperClickPathAnalyzer()
    {
    }

    public static void main(String[] args) throws Exception
    {
        Request request = Request.parse(args, Paths.get(System.getProperty("user.dir")));
        Analysis analysis = analyse(
            readLinesIfPresent(request.clicksFile),
            readLinesIfPresent(request.segmentsFile)
        );
        String report = analysis.render(request);
        Path parent = request.outputFile.getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        Files.write(request.outputFile, report.getBytes(StandardCharsets.UTF_8));
        System.out.print(report);
    }

    static Analysis analyse(List<String> clickLines, List<String> segmentLines)
    {
        Analysis analysis = new Analysis();
        List<ClickEvidence> clicks = new ArrayList<>();
        for (String line : safeLines(clickLines))
        {
            ClickEvidence click = ClickEvidence.parse(line);
            if (click == null)
            {
                continue;
            }
            clicks.add(click);
            analysis.clickRows++;
            increment(analysis.clickResults, click.result);
            increment(analysis.clickSources, click.source);
        }

        for (String line : safeLines(segmentLines))
        {
            SegmentEvidence segment = SegmentEvidence.parse(line);
            if (segment == null)
            {
                continue;
            }
            analysis.segmentRows++;
            increment(analysis.segmentClassifications, segment.classification);
            increment(analysis.decisionBuckets, segment.decisionBucket());
            if (segment.completed)
            {
                analysis.completedSegments++;
            }
            else
            {
                analysis.interruptedSegments++;
            }
            if (segment.rankingActualRank > 0)
            {
                increment(analysis.actualCandidateRanks, Integer.toString(segment.rankingActualRank));
            }
            if (segment.rankingExpectedRank > 0)
            {
                increment(analysis.expectedCandidateRanks, Integer.toString(segment.rankingExpectedRank));
            }

            ClickEvidence click = matchingClick(segment, clicks);
            if (click == null)
            {
                analysis.unmatchedSegments++;
            }
            else
            {
                analysis.matchedSegments++;
                if (click.clickedTile != null && !samePoint(click.clickedTile, segment.clickDest))
                {
                    analysis.acceptedDestinationDiffersFromClickTile++;
                    analysis.addExample("accepted-differs clickTile="
                        + DrewsHelperRouteBenchmark.formatPoint(click.clickedTile)
                        + " acceptedDest=" + DrewsHelperRouteBenchmark.formatPoint(click.acceptedDest)
                        + " segment=" + segment.compact());
                }
            }

            if (!"match".equals(segment.classification))
            {
                analysis.addExample(segment.decisionBucket() + " " + segment.compact());
            }
        }
        return analysis;
    }

    private static ClickEvidence matchingClick(SegmentEvidence segment, List<ClickEvidence> clicks)
    {
        for (int i = clicks.size() - 1; i >= 0; i--)
        {
            ClickEvidence click = clicks.get(i);
            if (samePoint(click.acceptedDest, segment.clickDest)
                && samePlaneDistance(click.start, segment.start) <= 2)
            {
                return click;
            }
        }
        return null;
    }

    private static int parseIntField(String fields, String name)
    {
        if (fields == null)
        {
            return -1;
        }
        Matcher matcher = Pattern.compile("(^|[\\s{])" + name + "=(-?\\d+)([\\s}]|$)").matcher(fields);
        return matcher.find() ? Integer.parseInt(matcher.group(2)) : -1;
    }

    private static int samePlaneDistance(WorldPoint first, WorldPoint second)
    {
        if (first == null || second == null || first.getPlane() != second.getPlane())
        {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getY() - second.getY()));
    }

    private static boolean samePoint(WorldPoint first, WorldPoint second)
    {
        return first != null && first.equals(second);
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

    private static void increment(Map<String, Integer> counts, String key)
    {
        counts.merge(key == null || key.isEmpty() ? "-" : key, 1, Integer::sum);
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

    static final class Analysis
    {
        final Map<String, Integer> clickResults = new TreeMap<>();
        final Map<String, Integer> clickSources = new TreeMap<>();
        final Map<String, Integer> segmentClassifications = new TreeMap<>();
        final Map<String, Integer> decisionBuckets = new TreeMap<>();
        final Map<String, Integer> actualCandidateRanks = new TreeMap<>();
        final Map<String, Integer> expectedCandidateRanks = new TreeMap<>();
        final List<String> examples = new ArrayList<>();
        int clickRows;
        int segmentRows;
        int completedSegments;
        int interruptedSegments;
        int matchedSegments;
        int unmatchedSegments;
        int acceptedDestinationDiffersFromClickTile;

        private void addExample(String example)
        {
            if (examples.size() < EXAMPLE_LIMIT)
            {
                examples.add(example);
            }
        }

        String render(Request request)
        {
            StringBuilder out = new StringBuilder();
            out.append("DREW_CLICK_PATH_ANALYSIS v1\n");
            out.append("output=").append(request.outputFile).append('\n');
            out.append("clicksFile=").append(request.clicksFile).append('\n');
            out.append("segmentsFile=").append(request.segmentsFile).append('\n');
            out.append('\n');
            out.append("CLICK PATH ROWS\n");
            out.append("rows=").append(clickRows)
                .append(" results=").append(formatCounts(clickResults))
                .append(" sources=").append(formatCounts(clickSources))
                .append('\n');
            out.append('\n');
            out.append("ROUTE SEGMENT DECISION BUCKETS\n");
            out.append("rows=").append(segmentRows)
                .append(" completed=").append(completedSegments)
                .append(" interrupted=").append(interruptedSegments)
                .append(" matchedClicks=").append(matchedSegments)
                .append(" unmatchedSegments=").append(unmatchedSegments)
                .append(" acceptedDestinationDiffersFromClickTile=")
                .append(acceptedDestinationDiffersFromClickTile)
                .append('\n');
            out.append("classifications=").append(formatCounts(segmentClassifications)).append('\n');
            out.append("decisionBuckets=").append(formatCounts(decisionBuckets)).append('\n');
            out.append("actualCandidateRanks=").append(formatCounts(actualCandidateRanks)).append('\n');
            out.append("expectedCandidateRanks=").append(formatCounts(expectedCandidateRanks)).append('\n');
            out.append('\n');
            out.append("EXAMPLES\n");
            if (examples.isEmpty())
            {
                out.append("examples=none\n");
            }
            else
            {
                for (int i = 0; i < examples.size(); i++)
                {
                    out.append("example").append(i + 1).append('=').append(examples.get(i)).append('\n');
                }
            }
            out.append('\n');
            out.append("ANALYZER DECISION\n");
            out.append("This report is evidence-only. Use click/segment matches to separate raw click ")
                .append("destination shifts from collision-map errors, object pressure, and same-length ")
                .append("route-ranking misses before changing the visible route model.\n");
            return out.toString();
        }
    }

    static final class Request
    {
        final Path clicksFile;
        final Path segmentsFile;
        final Path outputFile;

        private Request(Path clicksFile, Path segmentsFile, Path outputFile)
        {
            this.clicksFile = clicksFile;
            this.segmentsFile = segmentsFile;
            this.outputFile = outputFile;
        }

        static Request parse(String[] args, Path root)
        {
            Path runelite = Paths.get(System.getProperty("user.home"), ".runelite");
            Path clicksFile = runelite.resolve("drews-click-paths.txt");
            Path segmentsFile = runelite.resolve("drews-route-segments.txt");
            Path outputFile = root.resolve("tools").resolve("pathfinding-decision-report.txt");
            for (String arg : safeArgs(args))
            {
                if (arg.startsWith("--clicks="))
                {
                    clicksFile = Paths.get(arg.substring("--clicks=".length()));
                }
                else if (arg.startsWith("--segments="))
                {
                    segmentsFile = Paths.get(arg.substring("--segments=".length()));
                }
                else if (arg.startsWith("--output="))
                {
                    outputFile = Paths.get(arg.substring("--output=".length()));
                }
            }
            return new Request(clicksFile, segmentsFile, outputFile);
        }

        private static List<String> safeArgs(String[] args)
        {
            if (args == null || args.length == 0)
            {
                return Collections.emptyList();
            }
            List<String> values = new ArrayList<>();
            Collections.addAll(values, args);
            return values;
        }
    }

    private static final class ClickEvidence
    {
        private final String result;
        private final String source;
        private final WorldPoint start;
        private final WorldPoint clickedTile;
        private final WorldPoint acceptedDest;

        private ClickEvidence(
            String result,
            String source,
            WorldPoint start,
            WorldPoint clickedTile,
            WorldPoint acceptedDest
        )
        {
            this.result = result;
            this.source = source;
            this.start = start;
            this.clickedTile = clickedTile;
            this.acceptedDest = acceptedDest;
        }

        private static ClickEvidence parse(String line)
        {
            Map<String, String> fields = DrewsHelperRouteValidationHarness.parseFields(line, CLICK_PREFIX);
            if (fields.isEmpty())
            {
                return null;
            }
            return new ClickEvidence(
                fields.getOrDefault("result", "-"),
                fields.getOrDefault("source", "-"),
                DrewsHelperRouteValidationHarness.parsePoint(fields.get("start")),
                DrewsHelperRouteValidationHarness.parsePoint(fields.get("clickedTile")),
                DrewsHelperRouteValidationHarness.parsePoint(fields.get("acceptedDest"))
            );
        }
    }

    private static final class SegmentEvidence
    {
        private final boolean completed;
        private final String classification;
        private final WorldPoint start;
        private final WorldPoint clickDest;
        private final String edgeValidation;
        private final int rankingActualRank;
        private final int rankingExpectedRank;

        private SegmentEvidence(
            boolean completed,
            String classification,
            WorldPoint start,
            WorldPoint clickDest,
            String edgeValidation,
            int rankingActualRank,
            int rankingExpectedRank
        )
        {
            this.completed = completed;
            this.classification = classification;
            this.start = start;
            this.clickDest = clickDest;
            this.edgeValidation = edgeValidation;
            this.rankingActualRank = rankingActualRank;
            this.rankingExpectedRank = rankingExpectedRank;
        }

        private static SegmentEvidence parse(String line)
        {
            Map<String, String> fields =
                DrewsHelperRouteValidationHarness.parseFields(line, DrewsHelperRouteValidationHarness.SEGMENT_PREFIX);
            if (fields.isEmpty())
            {
                return null;
            }
            String ranking = fields.getOrDefault("ranking", "");
            return new SegmentEvidence(
                Boolean.parseBoolean(fields.getOrDefault("completed", "false")),
                fields.getOrDefault("classification", "-"),
                DrewsHelperRouteValidationHarness.parsePoint(fields.get("start")),
                DrewsHelperRouteValidationHarness.parsePoint(fields.get("clickDest")),
                fields.getOrDefault("edgeValidation", ""),
                parseIntField(ranking, "actualRank"),
                parseIntField(ranking, "expectedRank")
            );
        }

        private String decisionBucket()
        {
            if (!completed || classification.startsWith("interrupted-")
                || classification.startsWith("client-stopped-")
                || classification.startsWith("segment-limit-"))
            {
                return "reclick-or-noise";
            }
            if ("match".equals(classification))
            {
                return "match";
            }
            if ("click-destination-off-route".equals(classification))
            {
                return "click-destination-off-route";
            }
            if ("static-map-disagrees-with-live-step".equals(classification)
                || edgeValidation.contains("legal=false"))
            {
                return "collision-map-wrong";
            }
            if ("legal-detour-or-object-pressure".equals(classification))
            {
                return "object-pressure-or-longer-detour";
            }
            if ("legal-route-ranker-or-click-shape".equals(classification))
            {
                return "same-length-ranker-wrong";
            }
            return "other";
        }

        private String compact()
        {
            return "classification=" + classification
                + " completed=" + completed
                + " start=" + DrewsHelperRouteBenchmark.formatPoint(start)
                + " clickDest=" + DrewsHelperRouteBenchmark.formatPoint(clickDest)
                + " edgeValidation=" + edgeValidation
                + " actualRank=" + rankingActualRank
                + " expectedRank=" + rankingExpectedRank;
        }
    }
}
