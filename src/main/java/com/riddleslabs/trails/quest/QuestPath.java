package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.Route;
import com.riddleslabs.trails.RouteGeometry;
import java.util.*;

/** Finds route entrances without interpolating through a recorded teleport. */
public final class QuestPath {
    private QuestPath() {}

    public record Entrance(List<Route.Point> tail, boolean teleport, double distanceSquared) {}

    public static List<Entrance> entrances(Route route, Route.Point player, Route.Point target, double targetRadius) {
        if (route.points().getLast().distanceSquared(target) > targetRadius * targetRadius) return List.of();
        List<List<Route.Point>> sections = route.sections();
        List<Entrance> entrances = new ArrayList<>();
        for (int s = 0; s < sections.size(); s++) {
            List<Route.Point> section = sections.get(s);
            Route.Point closest = section.getFirst();
            double best = closest.distanceSquared(player);
            int segment = 0;
            for (int i = 0; i < section.size() - 1; i++) {
                Route.Point a = section.get(i), b = section.get(i + 1);
                Route.Point projection = a.interpolate(b, RouteGeometry.projection(player, a, b));
                double distance = projection.distanceSquared(player);
                if (distance < best) { best = distance; closest = projection; segment = i; }
            }
            List<Route.Point> tail = new ArrayList<>();
            tail.add(closest);
            for (int i = segment + 1; i < section.size(); i++) append(tail, section.get(i));
            boolean teleport = s < sections.size() - 1;
            if (!teleport) append(tail, target);
            entrances.add(new Entrance(List.copyOf(tail), teleport, best));
        }
        entrances.sort(Comparator.comparingDouble(Entrance::distanceSquared));
        return List.copyOf(entrances);
    }

    static void append(List<Route.Point> points, Route.Point point) {
        if (points.isEmpty() || points.getLast().distanceSquared(point) > 1e-8) points.add(point);
    }
}
