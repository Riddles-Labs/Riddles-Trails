package com.riddleslabs.trails.api.animation;

/** Called on the viewer's owning server/entity thread. Implementations must return promptly. */
@FunctionalInterface
public interface TrailAnimation {
    void render(AnimationFrame frame, ParticleSink particles);
}
