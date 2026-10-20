package com.riddleslabs.trails;

import java.util.ArrayList;
import java.util.List;
import com.riddleslabs.trails.Route.Point;

/** Pure route geometry; never loads chunks or performs automatic obstacle pathfinding. */
public final class RouteGeometry {
    private RouteGeometry() {}

    public static double projection(Point p, Point a, Point b) {
        double lengthSquared = a.distanceSquared(b);
        if (lengthSquared < 1e-10) return 0;
        return Math.clamp(((p.x() - a.x()) * (b.x() - a.x()) + (p.y() - a.y()) * (b.y() - a.y())
                + (p.z() - a.z()) * (b.z() - a.z())) / lengthSquared, 0, 1);
    }

    /** Join the closest segment and follow the recorded direction to its destination. */
    public static List<Point> join(Point from, Route route) {
        List<Point> best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (List<Point> section : route.sections()) {
            double nearest = from.distanceSquared(section.getFirst());
            for (int i = 1; i < section.size(); i++) {
                Point a = section.get(i - 1), b = section.get(i);
                nearest = Math.min(nearest, from.distanceSquared(a.interpolate(b, projection(from, a, b))));
            }
            if (nearest < distance) { distance = nearest; best = section; }
        }
        return join(from, best);
    }

    /** Join a continuous walking section. */
    public static List<Point> join(Point from, List<Point> points) {
        if (points.isEmpty()) throw new IllegalArgumentException("Empty route");
        List<Point> result = new ArrayList<>();
        result.add(from);
        if (points.size() == 1) {
            addDistinct(result, points.getFirst());
            return List.copyOf(result);
        }
        int nearest = 0;
        double best = Double.POSITIVE_INFINITY;
        Point joined = points.getFirst();
        for (int i = 0; i < points.size() - 1; i++) {
            Point a = points.get(i), b = points.get(i + 1);
            Point projected = a.interpolate(b, projection(from, a, b));
            double distance = from.distanceSquared(projected);
            if (distance < best) { best = distance; nearest = i; joined = projected; }
        }
        addDistinct(result, joined);
        for (int i = nearest + 1; i < points.size(); i++) addDistinct(result, points.get(i));
        return List.copyOf(result);
    }

    private static void addDistinct(List<Point> points, Point point) {
        if (points.getLast().distanceSquared(point) > 1e-8) points.add(point);
    }

    public static final class Path {
        private final List<Point> points;
        private final double[] cumulative;
        private final Node index;
        public Path(List<Point> points) {
            if (points.isEmpty()) throw new IllegalArgumentException("Empty route");
            this.points = List.copyOf(points);
            cumulative = new double[points.size()];
            for (int i = 1; i < points.size(); i++) cumulative[i] = cumulative[i - 1] + Math.sqrt(points.get(i - 1).distanceSquared(points.get(i)));
            index=points.size()>=64?new Node(1,points.size()):null;
        }
        public double length() { return cumulative[cumulative.length - 1]; }
        public Point end() { return points.getLast(); }
        public double progress(Point player) {
            Nearest nearest=new Nearest(player);
            if(index==null) nearest.scan(1,points.size());
            else nearest.visit(index);
            return nearest.progress;
        }
        /** Exact nearest-segment lookup; immutable bounds are shared safely by concurrent readers. */
        private final class Node {
            final int from,to;
            final Node left,right;
            final double minX,minY,minZ,maxX,maxY,maxZ;
            Node(int from,int to) {
                this.from=from;this.to=to;
                if(to-from>12) {
                    int middle=(from+to)>>>1;left=new Node(from,middle);right=new Node(middle,to);
                    minX=Math.min(left.minX,right.minX);minY=Math.min(left.minY,right.minY);minZ=Math.min(left.minZ,right.minZ);
                    maxX=Math.max(left.maxX,right.maxX);maxY=Math.max(left.maxY,right.maxY);maxZ=Math.max(left.maxZ,right.maxZ);
                } else {
                    left=null;right=null;Point first=points.get(from-1);
                    double lx=first.x(),ly=first.y(),lz=first.z(),hx=lx,hy=ly,hz=lz;
                    for(int i=from;i<to;i++){Point p=points.get(i);lx=Math.min(lx,p.x());ly=Math.min(ly,p.y());lz=Math.min(lz,p.z());
                        hx=Math.max(hx,p.x());hy=Math.max(hy,p.y());hz=Math.max(hz,p.z());}
                    minX=lx;minY=ly;minZ=lz;maxX=hx;maxY=hy;maxZ=hz;
                }
            }
            double distance(Point p) {
                double dx=Math.max(0,Math.max(minX-p.x(),p.x()-maxX)),dy=Math.max(0,Math.max(minY-p.y(),p.y()-maxY)),dz=Math.max(0,Math.max(minZ-p.z(),p.z()-maxZ));
                return dx*dx+dy*dy+dz*dz;
            }
        }
        private final class Nearest {
            final Point player;
            double best=Double.POSITIVE_INFINITY,progress;
            int segment=Integer.MAX_VALUE;
            Nearest(Point player){this.player=player;}
            void visit(Node node) {
                if(node.left==null){scan(node.from,node.to);return;}
                double a=node.left.distance(player),b=node.right.distance(player);
                Node first=a<=b?node.left:node.right,second=a<=b?node.right:node.left;
                if(Math.min(a,b)<=best+1e-10)visit(first);
                if(Math.max(a,b)<=best+1e-10)visit(second);
            }
            void scan(int from,int to) { for (int i = from; i < to; i++) {
                Point a = points.get(i - 1), b = points.get(i);
                double t = projection(player, a, b);
                double dx=player.x()-(a.x()+(b.x()-a.x())*t),dy=player.y()-(a.y()+(b.y()-a.y())*t),dz=player.z()-(a.z()+(b.z()-a.z())*t);
                double distance=dx*dx+dy*dy+dz*dz;
                if (distance < best || distance==best && i<segment) { best = distance;segment=i; progress = cumulative[i - 1] + (cumulative[i] - cumulative[i - 1]) * t; }
            } }
        }
        public Point at(double distance) {
            distance = Math.clamp(distance, 0, length());
            int index = java.util.Arrays.binarySearch(cumulative, distance);
            if (index >= 0) return points.get(index);
            index = -index - 1;
            if (index == 0) return points.getFirst();
            if (index >= points.size()) return end();
            double segment = cumulative[index] - cumulative[index - 1];
            return points.get(index - 1).interpolate(points.get(index), segment <= 1e-10 ? 0 : (distance - cumulative[index - 1]) / segment);
        }
    }
}
