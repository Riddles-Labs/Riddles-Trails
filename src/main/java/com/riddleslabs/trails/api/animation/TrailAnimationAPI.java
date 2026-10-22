package com.riddleslabs.trails.api.animation;

import java.util.Set;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

/**
 * Load through Bukkit's ServicesManager. IDs are namespaced, e.g. myplugin:aurora.
 * Prefer the async mutation methods. Legacy void mutations wait for durable saving and may block
 * their caller; calls from Trails' shared Core file worker are rejected to avoid a deadlock.
 * Do not block or join an async result on that worker or on server threads.
 */
public interface TrailAnimationAPI {
    void register(Plugin owner, String id, TrailAnimation animation);
    /** Only the registering owner can unregister an animation. */
    boolean unregister(Plugin owner, String id);
    Set<String> animations();
    /**
     * Selection applies to this player's ordinary and quest trails, including already running ones.
     * Saved by UUID across disconnects, reloads and restarts until explicitly reset.
     * @throws IllegalStateException if the preference cannot be saved (previous choice is kept)
     */
    void select(UUID playerId, String animationId);
    /** Durably removes the selection, returning to the configured default; preserves style overrides. */
    void reset(UUID playerId);
    /** Effective animation ID. An unavailable custom provider temporarily uses the configured fallback. */
    String selected(UUID playerId);
    /** Immutable configured profile (or common style if no profile is configured). */
    AnimationStyle style(String animationId);
    /** Saves a style override across disconnects, reloads and restarts until resetStyle is called. */
    void setStyle(UUID playerId, AnimationStyle style);
    /** Durably removes only the style override, preserving the selected animation. */
    void resetStyle(UUID playerId);
    /** Nonblocking durable mutations. Complete only after save; failures retain the previous value. */
    default java.util.concurrent.CompletionStage<Void> selectAsync(UUID player, String animation) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Async preferences unavailable"));
    }
    default java.util.concurrent.CompletionStage<Void> resetAsync(UUID player) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Async preferences unavailable"));
    }
    default java.util.concurrent.CompletionStage<Void> setStyleAsync(UUID player, AnimationStyle style) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Async preferences unavailable"));
    }
    default java.util.concurrent.CompletionStage<Void> resetStyleAsync(UUID player) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Async preferences unavailable"));
    }
}
