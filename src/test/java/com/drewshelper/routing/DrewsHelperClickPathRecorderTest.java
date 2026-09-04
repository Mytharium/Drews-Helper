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
import static org.junit.Assert.assertTrue;

public class DrewsHelperClickPathRecorderTest
{
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void recordsAcceptedDestinationForRecentWalkClick() throws Exception
    {
        File output = temporaryFolder.newFile();
        DrewsHelperClickPathRecorder recorder = new DrewsHelperClickPathRecorder(output);
        DrewsHelperRouteSnapshot snapshot = route(point(0, 0), point(1, 0), point(2, 0));

        recorder.recordClick(
            "walk",
            "WALK",
            "Walk here",
            "",
            0,
            2,
            0,
            point(2, 0),
            point(0, 0),
            null,
            1
        );
        List<String> lines = recorder.onTick(point(0, 0), point(2, 0), snapshot, null, 2);

        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.contains("DREW_CLICK_PATH v1"));
        assertTrue(line.contains("result=accepted"));
        assertTrue(line.contains("source=walk"));
        assertTrue(line.contains("clickTick=1"));
        assertTrue(line.contains("clickAge=1"));
        assertTrue(line.contains("option=Walk_here"));
        assertTrue(line.contains("start=(0,0,0)"));
        assertTrue(line.contains("clickedTile=(2,0,0)"));
        assertTrue(line.contains("destBefore=(null)"));
        assertTrue(line.contains("acceptedDest=(2,0,0)"));
        assertTrue(line.contains("routeTarget=(2,0,0)"));
        assertTrue(line.contains("forkCandidates={none}"));
        assertEquals(line, Files.readAllLines(output.toPath(), StandardCharsets.UTF_8).get(0));
    }

    @Test
    public void destinationChangeWithoutMenuClickIsStillCaptured() throws Exception
    {
        File output = temporaryFolder.newFile();
        DrewsHelperClickPathRecorder recorder = new DrewsHelperClickPathRecorder(output);
        DrewsHelperRouteSnapshot snapshot = route(point(0, 0), point(1, 0));

        List<String> lines = recorder.onTick(point(0, 0), point(1, 0), snapshot, null, 5);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("source=destination-change"));
        assertTrue(lines.get(0).contains("clickTick=-1"));
        assertTrue(lines.get(0).contains("acceptedDest=(1,0,0)"));
    }

    @Test
    public void recordsTimedOutClickWhenNoDestinationAppears() throws Exception
    {
        File output = temporaryFolder.newFile();
        DrewsHelperClickPathRecorder recorder = new DrewsHelperClickPathRecorder(output);
        recorder.recordClick(
            "scene-object",
            "GAME_OBJECT_FIRST_OPTION",
            "Open",
            "Door",
            1,
            1,
            0,
            point(1, 0),
            point(0, 0),
            null,
            1
        );

        List<String> lines = recorder.onTick(point(0, 0), null, null, null, 5);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("result=no-destination"));
        assertTrue(lines.get(0).contains("source=scene-object"));
        assertTrue(lines.get(0).contains("clickAge=4"));
        assertTrue(lines.get(0).contains("acceptedDest=(null)"));
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
}
