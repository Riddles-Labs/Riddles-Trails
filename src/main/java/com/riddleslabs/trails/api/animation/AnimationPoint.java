package com.riddleslabs.trails.api.animation;

/** Immutable world-space position/vector, safe to retain without a Bukkit world reference. */
public record AnimationPoint(double x, double y, double z) {
    public AnimationPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite position");
    }
    public AnimationPoint add(double dx, double dy, double dz) { return new AnimationPoint(x + dx, y + dy, z + dz); }
    public AnimationPoint interpolate(AnimationPoint b, double t) { return add((b.x-x)*t, (b.y-y)*t, (b.z-z)*t); }
    public double distanceSquared(AnimationPoint b) { return Math.pow(x-b.x, 2) + Math.pow(y-b.y, 2) + Math.pow(z-b.z, 2); }
}
