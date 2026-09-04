package com.drewshelper.routing;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DrewsHelperRouteSegmentRecorderTest
{
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void recordsOneClickedSegmentWhenTheClientDestinationIsReached() throws Exception
    {
        File output = temporaryFolder.newFile();
        DrewsHelperRouteSegmentRecorder recorder = new DrewsHelperRouteSegmentRecorder(output);
        DrewsHelperRouteSnapshot snapshot = route(point(0, 0), point(1, 0), point(2, 0), point(3, 0));

        assertTrue(recorder.onTick(point(0, 0), point(2, 0), snapshot, null, 1).isEmpty());
        assertTrue(recorder.onTick(point(1, 0), point(2, 0), snapshot, null, 2).isEmpty());
        List<String> lines = recorder.onTick(point(2, 0), point(2, 0), snapshot, null, 3);

        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.contains("DREW_ROUTE_SEGMENT v1"));
        assertTrue(line.contains("reason=destination"));
        assertTrue(line.contains("completed=true"));
        assertTrue(line.contains("start=(0,0,0)"));
        assertTrue(line.contains("clickDest=(2,0,0)"));
        assertTrue(line.contains("routeStart=exact:idx=0:dist=0"));
        assertTrue(line.contains("routeDest=exact:idx=2:dist=0"));
        assertTrue(line.contains("expectedPoints=3 actualPoints=3"));
        assertTrue(line.contains("classification=match"));
        assertTrue(line.contains("route={first=match 5=2/2 10=2/2 full=true lenDelta=0"));
        assertTrue(line.contains("expectedPath=[(0,0,0) -> (1,0,0) -> (2,0,0)]"));
        assertTrue(line.contains("actualPath=[(0,0,0) -> (1,0,0) -> (2,0,0)]"));
        assertEquals(line, Files.readAllLines(output.toPath(), StandardCharsets.UTF_8).get(0));
    }

    @Test
    public void destinationChangeEndsThePreviousSegmentAndStartsANewOne() throws Exception
    {
        File output = temporaryFolder.newFile();
        DrewsHelperRouteSegmentRecorder recorder = new DrewsHelperRouteSegmentRecorder(output);
        DrewsHelperRouteSnapshot snapshot = route(
            point(0, 0),
            point(1, 0),
            point(2, 0),
            point(3, 0),
            point(4, 0)
        );

        assertTrue(recorder.onTick(point(0, 0), point(2, 0), snapshot, null, 1).isEmpty());
        assertTrue(recorder.onTick(point(1, 0), point(2, 0), snapshot, null, 2).isEmpty());

        List<String> first = recorder.onTick(point(1, 0), point(4, 0), snapshot, null, 3);
        assertEquals(1, first.size());
        assertTrue(first.get(0).contains("reason=destination-changed"));
        assertTrue(first.get(0).contains("completed=false"));
        assertTrue(first.get(0).contains("clickDest=(2,0,0)"));
        assertTrue(first.get(0).contains("classification=interrupted-reclick-clean-prefix"));

        assertTrue(recorder.onTick(point(2, 0), point(4, 0), snapshot, null, 4).isEmpty());
        assertTrue(recorder.onTick(point(3, 0), point(4, 0), snapshot, null, 5).isEmpty());
        List<String> second = recorder.onTick(point(4, 0), point(4, 0), snapshot, null, 6);
        assertEquals(1, second.size());
        assertTrue(second.get(0).contains("reason=destination"));
        assertTrue(second.get(0).contains("clickDest=(4,0,0)"));

        assertEquals(2, Files.readAllLines(output.toPath(), StandardCharsets.UTF_8).size());
    }

    @Test
    public void destinationChangeAfterDivergenceIsLabeledAsInterruptedEvidence() throws Exception
    {
        File output = temporaryFolder.newFile();
        DrewsHelperRouteSegmentRecorder recorder = new DrewsHelperRouteSegmentRecorder(output);
        DrewsHelperRouteSnapshot snapshot = route(
            point(0, 0),
            point(1, 0),
            point(2, 0),
            point(3, 0),
            point(4, 0)
        );

        assertTrue(recorder.onTick(point(0, 0), point(3, 0), snapshot, null, 1).isEmpty());
        assertTrue(recorder.onTick(point(0, 1), point(3, 0), snapshot, null, 2).isEmpty());

        List<String> lines = recorder.onTick(point(0, 1), point(4, 0), snapshot, null, 3);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("reason=destination-changed"));
        assertTrue(lines.get(0).contains("completed=false"));
        assertTrue(lines.get(0).contains("classification=interrupted-reclick-after-divergence"));
        assertTrue(lines.get(0).contains("divergence={idx=1"));
    }

    @Test
    public void offRouteClickIsRecordedAsClickChoiceEvidence() throws Exception
    {
        File output = temporaryFolder.newFile();
        DrewsHelperRouteSegmentRecorder recorder = new DrewsHelperRouteSegmentRecorder(output);
        DrewsHelperRouteSnapshot snapshot = route(point(0, 0), point(1, 0), point(2, 0));

        assertTrue(recorder.onTick(point(0, 0), point(10, 0), snapshot, null, 1).isEmpty());
        assertTrue(recorder.onTick(point(1, 0), point(10, 0), snapshot, null, 2).isEmpty());
        List<String> lines = recorder.onTick(point(2, 0), null, snapshot, null, 3);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("reason=destination-cleared"));
        assertTrue(lines.get(0).contains("completed=false"));
        assertTrue(lines.get(0).contains("routeDest=off:idx=-1:dist=8"));
        assertTrue(lines.get(0).contains("expectedPoints=0 actualPoints=3"));
        assertTrue(lines.get(0).contains("classification=click-destination-off-route"));
        assertTrue(lines.get(0).contains("route={unavailable}"));
    }

    @Test
    public void noMovementClickIsDroppedRatherThanWrittenAsEvidence() throws Exception
    {
        File output = temporaryFolder.newFile();
        DrewsHelperRouteSegmentRecorder recorder = new DrewsHelperRouteSegmentRecorder(output);
        DrewsHelperRouteSnapshot snapshot = route(point(0, 0), point(1, 0), point(2, 0));

        assertTrue(recorder.onTick(point(0, 0), point(2, 0), snapshot, null, 1).isEmpty());
        assertTrue(recorder.onTick(point(0, 0), point(2, 0), snapshot, null, 2).isEmpty());
        assertTrue(recorder.onTick(point(0, 0), null, snapshot, null, 3).isEmpty());
        assertFalse(recorder.hasWriteFailed());
        assertTrue(Files.readAllLines(output.toPath(), StandardCharsets.UTF_8).isEmpty());
    }

    @Test
    public void divergentCompletedSegmentIncludesForkCandidatesAndRanking() throws Exception
    {
        File output = temporaryFolder.newFile();
        DrewsHelperRouteSegmentRecorder recorder = new DrewsHelperRouteSegmentRecorder(output);
        DrewsHelperRouteSnapshot snapshot = route(point(0, 0), point(1, 0), point(2, 0));
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        assertTrue(recorder.onTick(point(0, 0), point(2, 0), snapshot, engine, 1).isEmpty());
        assertTrue(recorder.onTick(point(0, 1), point(2, 0), snapshot, engine, 2).isEmpty());
        assertTrue(recorder.onTick(point(1, 1), point(2, 0), snapshot, engine, 3).isEmpty());
        List<String> lines = recorder.onTick(point(2, 0), point(2, 0), snapshot, engine, 4);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("classification=legal-"));
        assertTrue(lines.get(0).contains("forkCandidates={"));
        assertTrue(lines.get(0).contains(":expected=true"));
        assertTrue(lines.get(0).contains(":actual=true"));
        assertTrue(lines.get(0).contains("ranking={actualRank="));
        assertTrue(lines.get(0).contains("expectedRank="));
        assertTrue(lines.get(0).contains("clientWon="));
        assertTrue(lines.get(0).contains("shapeRawWon="));
    }

    private static DrewsHelperRouteSnapshot route(WorldPoint... path)
    {
        List<WorldPoint> points = Arrays.asList(path);
        return DrewsHelperRouteSnapshot.ready(
            points,
            Arrays.asList(points.get(points.size() - 1)),
            points.size() - 1
        );
    }

    private static WorldPoint point(int x, int y)
    {
        return new WorldPoint(x, y, 0);
    }

    private static final class OpenMovementMap implements DrewsHelperMovementMap
    {
        @Override
        public boolean canMoveNorth(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveSouth(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveEast(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveWest(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveNorthEast(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveNorthWest(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveSouthEast(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveSouthWest(int x, int y, int plane)
        {
            return true;
        }
    }
}
