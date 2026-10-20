package com.riddleslabs.trails.api.animation;

import java.util.*;

/** The currently visible continuous walking section. Never spans an elevator/teleport gap. */
public final class AnimationPath {
    private final List<AnimationPoint> points;
    private final double[] cumulative;
    public AnimationPath(List<AnimationPoint> points) {
        this.points = List.copyOf(points);
        if (points.isEmpty()) throw new IllegalArgumentException("Empty path");
        cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) cumulative[i] = cumulative[i-1] + Math.sqrt(points.get(i-1).distanceSquared(points.get(i)));
        if (!Double.isFinite(length())) throw new IllegalArgumentException("Path is too long");
    }
    public List<AnimationPoint> points() { return points; }
    public double length() { return cumulative[cumulative.length-1]; }
    public AnimationPoint at(double distance) {
        distance = Math.clamp(distance, 0, length());
        int i = Arrays.binarySearch(cumulative, distance);
        if (i >= 0) return points.get(i);
        i = -i-1;
        if (i == 0) return points.getFirst();
        if (i >= points.size()) return points.getLast();
        double span = cumulative[i]-cumulative[i-1];
        return points.get(i-1).interpolate(points.get(i), span < 1e-9 ? 0 : (distance-cumulative[i-1])/span);
    }
    /** Offset in the path's local horizontal normal and vertical direction; distance is in blocks. */
    public AnimationPoint at(double distance, double lateral, double vertical) {
        AnimationPoint a = at(Math.max(0, distance-.15)), b = at(Math.min(length(), distance+.15));
        double dx = b.x()-a.x(), dz = b.z()-a.z(), norm = Math.hypot(dx, dz);
        double nx = norm < 1e-8 ? 1 : -dz/norm, nz = norm < 1e-8 ? 0 : dx/norm;
        return at(distance).add(nx*lateral, vertical, nz*lateral);
    }
    /** Turn strength at distance, from 0 (straight) to 1 (reversal). */
    public double bend(double distance) {
        AnimationPoint a=at(distance-1), b=at(distance), c=at(distance+1);
        double ax=b.x()-a.x(), az=b.z()-a.z(), bx=c.x()-b.x(), bz=c.z()-b.z();
        double norm=Math.hypot(ax,az)*Math.hypot(bx,bz);
        return norm < 1e-8 ? 0 : Math.clamp((1-(ax*bx+az*bz)/norm)/2,0,1);
    }
}
