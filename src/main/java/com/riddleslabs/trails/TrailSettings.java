package com.riddleslabs.trails;

import org.bukkit.configuration.ConfigurationSection;

public record TrailSettings(int interval, double spacing, double minAhead, double maxAhead, int budget,
                            int timeoutSeconds, int duration, double height, double arrivalRadius,
                            double recordSpacing, int maxPoints, double maxStep) {
    /** One-time upgrade of the old shipped budget; explicit non-default budgets are retained. */
    public static boolean upgradeParticleBudget(ConfigurationSection cfg) {
        if(cfg.contains("render.detail-version",true)) return false;
        if(!cfg.contains("render.max-particles-per-update",true)||cfg.getInt("render.max-particles-per-update")==120)
            cfg.set("render.max-particles-per-update",1200);
        cfg.set("render.detail-version",1);
        return true;
    }
    public static TrailSettings read(ConfigurationSection cfg) {
        double min = bounded(cfg.getDouble("render.min-ahead", 2), 0, 20, 2);
        double maxStep = bounded(cfg.getDouble("recording.max-step-distance", 8), 1, 100, 8);
        return new TrailSettings(
                Math.clamp(cfg.getInt("render.update-interval-ticks", 6), 2, 100),
                bounded(cfg.getDouble("render.particle-spacing", .8), .2, 5, .8), min,
                bounded(cfg.getDouble("render.max-ahead", 20), min + 1, 100, 20),
                Math.clamp(cfg.getInt("render.max-particles-per-update", 1200), 6, 6000),
                Math.clamp(cfg.getInt("render.timeout-seconds", 180), 5, 3600),
                Math.clamp(cfg.getInt("render.trail-duration-ticks", 40), 5, 200),
                bounded(cfg.getDouble("render.height-offset", .25), 0, 3, .25),
                bounded(cfg.getDouble("render.arrival-radius", 2), .5, 10, 2),
                bounded(cfg.getDouble("recording.point-spacing", .75), .25, Math.min(10, maxStep / 2), .75),
                Math.clamp(cfg.getInt("recording.max-points", 5000), 2, 20000),
                maxStep);
    }
    private static double bounded(double value, double min, double max, double fallback) {
        return Math.clamp(Double.isFinite(value) ? value : fallback, min, max);
    }
}
