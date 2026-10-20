package com.riddleslabs.trails.api.animation;

import org.bukkit.Particle;

/** Private-to-viewer, budget-limited emitter. Valid only during the render callback; do not retain it. */
public interface ParticleSink {
    /** Emit the configured particle. colorPosition selects a point in the configured palette (0-1). */
    boolean emit(AnimationPoint position, double brightness, double colorPosition);
    /** Custom particles, including particles with Bukkit data objects; count must be positive. */
    boolean emit(Particle particle, AnimationPoint position, int count, Object data);
    int remaining();
}
