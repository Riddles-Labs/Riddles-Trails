package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.Route;
import com.riddleslabs.trails.RouteGeometry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestPathTest {
    private Route.Point p(double x, double z) { return new Route.Point(x, 64, z); }
    private Route road() { return new Route("road", "world", List.of(p(0, 0), p(10, 0), p(10, 10))); }
    private RouteGeometry.Path path(Route route, Route.Point start, Route.Point end) {
        var points = new ArrayList<Route.Point>(); points.add(start);
        points.addAll(QuestPath.entrances(route, start, end, 3).getFirst().tail());
        return new RouteGeometry.Path(points);
    }

    @Test void followsRecordedCornerAndUpdatesMovingTarget() {
        var first = path(road(), p(0, 0), p(11, 10));
        assertEquals(p(10, 0), first.at(10));
        assertEquals(p(10, 5), first.at(15));
        assertEquals(p(11, 10), first.end());
        var moved = path(road(), p(0, 0), p(12, 10));
        assertEquals(p(12, 10), moved.end());
    }

    @Test void acceptsFarAwayPlayersButStillMatchesTheTargetEndpoint() {
        assertFalse(QuestPath.entrances(road(), p(-100, 0), p(10, 10), 3).isEmpty());
        assertTrue(QuestPath.entrances(road(), p(0, 0), p(30, 10), 3).isEmpty());
        var waypoint = new Route("point", "world", List.of(p(10, 10)));
        assertFalse(QuestPath.entrances(waypoint, p(9, 10), p(10, 10), 3).isEmpty());
    }

    @Test void joinsNearbySegmentWithoutBacktrackingToStart() {
        var path = path(road(), p(5, 1), p(10, 10));
        assertEquals(p(5, 0), path.at(1));
        assertEquals(16, path.length(), 1e-8);
    }

    @Test void elevatorGapIsNeverAnEntranceOrWalkingSegment() {
        var bottom = new Route.Point(10, 64, 0);
        var top = new Route.Point(10, 84, 0);
        var end = new Route.Point(20, 84, 0);
        var route = new Route("lift", "world", List.of(p(0, 0), bottom, top, end), List.of(2));
        var below = QuestPath.entrances(route, p(5, 0), end, 3).getFirst();
        assertTrue(below.teleport());
        assertEquals(bottom, below.tail().getLast());
        var above = QuestPath.entrances(route, top, end, 3).getFirst();
        assertFalse(above.teleport());
        assertEquals(end, above.tail().getLast());
        assertEquals(top, above.tail().getFirst());
        assertEquals(route.sections(), new Route("legacy", "world", route.points()).sections());
    }

    @Test void restoreRulesDoNotReviveFinishedOrAlreadyStartedQuests() {
        assertTrue(BeautyQuestsProgress.matches(true, false, false));
        assertFalse(BeautyQuestsProgress.matches(false, false, false));
        assertFalse(BeautyQuestsProgress.matches(true, true, false));
        assertFalse(BeautyQuestsProgress.matches(true, false, true));
    }
}
