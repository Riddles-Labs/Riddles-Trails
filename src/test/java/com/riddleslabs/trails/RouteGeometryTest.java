package com.riddleslabs.trails;

import org.junit.jupiter.api.Test;
import java.util.*;
import com.riddleslabs.trails.Route.Point;
import static org.junit.jupiter.api.Assertions.*;

class RouteGeometryTest {
    @Test void indexedProgressMatchesExhaustiveSearchOnLongFoldedAndDuplicateRoutes() {
        Random random=new Random(371);
        for(int route=0;route<15;route++) {
            List<Point> points=new ArrayList<>();
            for(int i=0;i<300;i++)points.add(i%11==0&&!points.isEmpty()?points.getLast():
                    new Point(random.nextDouble()*100,random.nextDouble()*20,random.nextDouble()*100));
            var path=new RouteGeometry.Path(points);
            for(int query=0;query<200;query++) {
                Point position=new Point(random.nextDouble()*140-20,random.nextDouble()*40-10,random.nextDouble()*140-20);
                assertEquals(exhaustive(points,position),path.progress(position),1e-8);
            }
        }
        var overlapping=new ArrayList<Point>();
        for(int i=0;i<200;i++)overlapping.add(new Point(i%2*10,64,0));
        assertEquals(5,new RouteGeometry.Path(overlapping).progress(new Point(5,64,1)),1e-8);
        assertEquals(0,new RouteGeometry.Path(Collections.nCopies(200,new Point(1,2,3))).progress(new Point(4,5,6)));
    }
    private static double exhaustive(List<Point> points,Point position) {
        double best=Double.POSITIVE_INFINITY,progress=0,cumulative=0;
        for(int i=1;i<points.size();i++) {
            Point a=points.get(i-1),b=points.get(i);double length=Math.sqrt(a.distanceSquared(b));
            double t=RouteGeometry.projection(position,a,b),distance=position.distanceSquared(a.interpolate(b,t));
            if(distance<best){best=distance;progress=cumulative+length*t;}cumulative+=length;
        }
        return progress;
    }
    @Test void waypointDrawsFromPlayerToDestination() {
        var start = new Point(0, 64, 0);
        var target = new Point(10, 64, 0);
        var path = new RouteGeometry.Path(RouteGeometry.join(start, List.of(target)));
        assertEquals(10, path.length(), .001);
        assertEquals(new Point(5, 64, 0), path.at(5));
        assertEquals(target, path.end());
    }
    @Test void joinsNearestSegmentInsteadOfReturningToRouteStart() {
        var route = List.of(new Point(0, 64, 0), new Point(10, 64, 0), new Point(10, 64, 10));
        var joined = RouteGeometry.join(new Point(12, 64, 5), route);
        assertEquals(List.of(new Point(12, 64, 5), new Point(10, 64, 5), new Point(10, 64, 10)), joined);
        assertEquals(7, new RouteGeometry.Path(joined).length(), .001);
    }
    @Test void followsCornerInsteadOfCuttingAcrossBuildings() {
        var path = new RouteGeometry.Path(List.of(new Point(0, 64, 0), new Point(10, 64, 0), new Point(10, 64, 10)));
        assertEquals(new Point(10, 64, 5), path.at(15));
        assertEquals(15, path.progress(new Point(11, 64, 5)), .001);
    }
    @Test void duplicatePointsAndArrivalRemainFinite() {
        var point = new Point(-8, -20, 7);
        var path = new RouteGeometry.Path(RouteGeometry.join(point, List.of(point, point)));
        assertEquals(0, path.length());
        assertEquals(point, path.at(100));
        assertEquals(0, path.progress(point));
    }
    @Test void rejectsInvalidRouteData() {
        assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Route("../invalid", "world", List.of(new Point(0, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> new Route("empty", "world", List.of()));
    }
    @Test void samplingClampsAtEnds() {
        var a = new Point(0, 0, 0); var b = new Point(0, 10, 0);
        var path = new RouteGeometry.Path(List.of(a, b));
        assertEquals(a, path.at(-10)); assertEquals(b, path.at(100));
        assertEquals(0, path.progress(new Point(0, -5, 0)));
        assertEquals(10, path.progress(new Point(0, 15, 0)));
    }
}
