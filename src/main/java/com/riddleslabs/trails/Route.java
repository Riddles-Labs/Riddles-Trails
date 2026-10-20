package com.riddleslabs.trails;

import java.util.List;

public record Route(String id, String world, List<Point> points, List<Integer> teleports) {
    public Route(String id, String world, List<Point> points) { this(id, world, points, List.of()); }

    public Route {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,47}")) throw new IllegalArgumentException("Invalid destination ID");
        if (world == null || world.isBlank()) throw new IllegalArgumentException("A world is required");
        points = List.copyOf(points);
        if (points.isEmpty()) throw new IllegalArgumentException("A destination needs at least one point");
        teleports = List.copyOf(teleports);
        for (int end : teleports)
            if (end < 1 || end >= points.size()) throw new IllegalArgumentException("Invalid teleport endpoint index");
    }

    /** A teleport edge is never interpolated as a walkable segment. */
    public boolean teleportAt(int end) {
        if (teleports.contains(end)) return true;
        // Compatibility for old recordings: near-vertical elevator jumps were saved as ordinary edges.
        Point a = points.get(end - 1), b = points.get(end);
        return Math.abs(a.y() - b.y()) > 1.25
                && Math.hypot(a.x() - b.x(), a.z() - b.z()) < 1;
    }

    public List<List<Point>> sections() {
        java.util.ArrayList<List<Point>> result = new java.util.ArrayList<>();
        int start = 0;
        for (int end = 1; end < points.size(); end++) if (teleportAt(end)) {
            result.add(points.subList(start, end)); start = end;
        }
        result.add(points.subList(start, points.size()));
        return List.copyOf(result);
    }

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite point");
        }
        public double distanceSquared(Point p) {
            double dx = p.x - x, dy = p.y - y, dz = p.z - z;
            return dx * dx + dy * dy + dz * dz;
        }
        public Point interpolate(Point other, double t) {
            return new Point(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t);
        }
    }
}
