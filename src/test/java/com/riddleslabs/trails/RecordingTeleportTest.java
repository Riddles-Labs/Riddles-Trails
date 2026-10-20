package com.riddleslabs.trails;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.riddleslabs.trails.RiddlesTrailsPlugin.Recording.TeleportResult.*;

class RecordingTeleportTest {
    private Route.Point p(double x) { return new Route.Point(x, 64, 0); }
    private RiddlesTrailsPlugin.Recording recording() {
        return new RiddlesTrailsPlugin.Recording("road", "world", p(0));
    }

    @Test void longSameWorldTeleportContinuesFromNewPosition() {
        var rec = recording();
        assertEquals(CONTINUED, rec.teleport("world", p(.5), p(100), 5000));
        assertFalse(rec.paused);
        assertEquals(List.of(p(0), p(.5), p(100)), rec.points);
        assertEquals(List.of(2), rec.teleports);
        assertEquals(0, rec.points.getLast().distanceSquared(p(100)));
    }

    @Test void manualPauseAndDifferentWorldDoNotCreateInvalidSegments() {
        var rec = recording();
        rec.paused = true;
        assertEquals(ALREADY_PAUSED, rec.teleport("world", p(0), p(100), 5000));
        assertEquals(List.of(p(0)), rec.points);
        assertTrue(rec.teleports.isEmpty());
        rec.paused = false;
        assertEquals(WORLD_CHANGED, rec.teleport("nether", p(0), p(100), 5000));
        assertTrue(rec.paused);
        assertEquals(List.of(p(0)), rec.points);
    }

    @Test void pointLimitIsAtomicAndZeroDistanceTeleportAddsNoDuplicates() {
        var rec = recording();
        assertEquals(CONTINUED, rec.teleport("world", p(0), p(0), 1));
        assertFalse(rec.paused);
        assertEquals(1, rec.points.size());
        assertEquals(POINT_LIMIT, rec.teleport("world", p(.5), p(100), 2));
        assertTrue(rec.paused);
        assertEquals(List.of(p(0)), rec.points);
    }
}
