package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.Route.Point;
import java.util.*;
import org.bukkit.World;

/** Bounded A* over loaded walking surfaces. A partial result still guides toward a distant route. */
final class WalkingConnector {
    private WalkingConnector() {}
    record Result(List<Point> points, boolean complete) {}
    private record Node(Point point, double cost, double score) {}

    static Result find(World world, Point start, Point goal, int maxNodes) {
        return find(new SafeCorridor.Snapshot(world),start,goal,maxNodes);
    }
    static Result find(SafeCorridor.Snapshot world, Point start, Point goal, int maxNodes) {
        if (start.distanceSquared(goal) < .04) return new Result(List.of(start, goal), true);
        List<Point> direct = line(world, start, goal, 64);
        if (direct != null) return new Result(direct, start.distanceSquared(goal) <= 64 * 64);
        Point first = world.stand(
                new Point(Math.floor(start.x()) + .5, start.y(), Math.floor(start.z()) + .5), 1.05, 2);
        if (first == null || line(world, start, first, 2) == null) return null;
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<Point, Double> costs = new HashMap<>();
        Map<Point, Point> previous = new HashMap<>();
        Set<Point> closed = new HashSet<>();
        open.add(new Node(first, 0, distance(first, goal)));
        costs.put(first, 0.0);
        Point best = first;
        boolean complete = false;
        for (int visited = 0; !open.isEmpty() && visited < maxNodes; ) {
            Node node = open.remove();
            Point current = node.point;
            if (!closed.add(current)) continue;
            visited++;
            if (current.distanceSquared(goal) < best.distanceSquared(goal)) best = current;
            if (current.distanceSquared(goal) <= 2 && line(world, current, goal, 2) != null) {
                best = current; complete = true; break;
            }
            for (int[] direction : DIRECTIONS) {
                Point next = world.stand(
                        new Point(current.x() + direction[0], current.y(), current.z() + direction[1]), 1.05, 2);
                if (next == null || next.distanceSquared(start) > 64 * 64 || closed.contains(next)
                        || line(world, current, next, 3) == null) continue;
                double cost = node.cost + distance(current, next);
                if (cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                costs.put(next, cost); previous.put(next, current);
                open.add(new Node(next, cost, cost + distance(next, goal)));
            }
        }
        if (!complete && best.distanceSquared(goal) >= start.distanceSquared(goal) - .25) return null;
        List<Point> path = new ArrayList<>();
        for (Point point = best; point != null; point = previous.get(point)) path.add(point);
        Collections.reverse(path);
        path.addFirst(start);
        if (complete) path.add(goal);
        return new Result(List.copyOf(path), complete);
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static double distance(Point a, Point b) { return Math.sqrt(a.distanceSquared(b)); }

    private static List<Point> line(SafeCorridor.Snapshot world, Point start, Point goal, double maxDistance) {
        double length = distance(start, goal), used = Math.min(length, maxDistance);
        if (!Double.isFinite(length) || length < 1e-8) return List.of(start);
        List<Point> points = new ArrayList<>();
        points.add(start);
        for (double d = Math.min(.25, used); ; d = Math.min(used, d + .25)) {
            Point sample = start.interpolate(goal, d / length);
            Point surface = world.stand(sample, 1.05, 2);
            if (surface == null || world.adjust(sample) == null) return null;
            double change = surface.y() - points.getLast().y();
            if (change > 1.05 || change < -2.05) return null;
            points.add(surface);
            if (d >= used) break;
        }
        return List.copyOf(points);
    }
}
