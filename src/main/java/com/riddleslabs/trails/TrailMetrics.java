package com.riddleslabs.trails;

import com.riddleslabs.api.health.HealthStatus;
import com.riddleslabs.api.telemetry.TelemetrySession;
import com.riddleslabs.api.telemetry.TelemetryStatus;
import com.riddleslabs.paper.api.PaperModule;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/** Aggregate observations only. Core owns the SDK, transport, consent and error scrubbing. */
public final class TrailMetrics {
    public enum Counter {
        TRAILS_STARTED, TRAILS_ARRIVED, TRAILS_TIMED_OUT, TRAIL_FAILURES,
        ANIMATION_FAILURES, FILE_FAILURES, RELOAD_FAILURES, COMMAND_FAILURES,
        QUEST_FAILURES, NOTIFICATION_FAILURES, STARTUP_FAILURES, SAMPLING_FAILURES;
        public String id() { return name().toLowerCase(Locale.ROOT) + "_total"; }
    }
    private static final Set<String> GAUGES = Set.of("destinations", "active_trails", "animations", "recordings",
            "route_points", "teleport_segments", "custom_animations", "languages", "quest_available",
            "active_quest_trails", "runtime_ready", "health_degraded", "health_unknown", "uptime_seconds");
    private final PaperModule module;
    private final EnumMap<Counter, LongAdder> counters = new EnumMap<>(Counter.class);
    private final EnumMap<Counter, Long> lastReport = new EnumMap<>(Counter.class);
    private final long started = System.nanoTime();
    private volatile TelemetrySession telemetry;
    private volatile Supplier<Map<String, Double>> snapshot;

    public TrailMetrics(PaperModule module) {
        this.module = module;
        for (var counter : Counter.values()) counters.put(counter, new LongAdder());
    }
    public void start() {
        try {
            Set<String> ids = new HashSet<>(GAUGES);
            for (var counter : Counter.values()) ids.add(counter.id());
            telemetry = module.telemetry(Set.copyOf(ids));
            if (telemetry.status() != TelemetryStatus.ACTIVE) return;
            sample(); // Seed a startup snapshot before the SDK's first upload.
            module.scheduler().asyncRepeating(Duration.ofSeconds(10), Duration.ofSeconds(30), this::sample);
        } catch (RuntimeException | LinkageError unavailable) {
            health(HealthStatus.DEGRADED, "TELEMETRY_UNAVAILABLE");
        }
    }
    public String status() { return telemetry == null ? "UNAVAILABLE" : telemetry.status().name(); }
    public void ready(Supplier<Map<String, Double>> source) { snapshot = source; sample(); }
    public void count(Counter counter) { counters.get(counter).increment(); }
    public void failure(Counter counter, Throwable failure) {
        count(counter);
        report(counter, failure);
        if (counter == Counter.STARTUP_FAILURES) { snapshot = null; sample(); }
    }
    public void report(Counter counter, Throwable failure) {
        try {
            if (!module.isOpen() || telemetry == null || telemetry.status() != TelemetryStatus.ACTIVE) return;
            long now = System.nanoTime();
            synchronized (lastReport) {
                Long previous = lastReport.get(counter);
                if (previous != null && now - previous < Duration.ofMinutes(5).toNanos()) return;
                lastReport.put(counter, now);
            }
            while ((failure instanceof java.util.concurrent.CompletionException
                    || failure instanceof java.util.concurrent.ExecutionException) && failure.getCause() != null)
                failure = failure.getCause();
            telemetry.reportFailure(counter.name().toLowerCase(Locale.ROOT), failure);
        } catch (RuntimeException | LinkageError ignored) { /* Optional reporting cannot change gameplay results. */ }
    }
    private synchronized void sample() {
        if (!module.isOpen() || telemetry == null || telemetry.status() != TelemetryStatus.ACTIVE) return;
        var values = new LinkedHashMap<String, Double>();
        var source = snapshot;
        boolean complete = false;
        try {
            if (source != null) { values.putAll(source.get()); complete = true; }
            var signals = module.facilities().health().snapshot().stream()
                    .filter(signal -> !signal.component().equals("metrics")).toList();
            values.put("health_degraded", signals.stream().anyMatch(s -> s.status() == HealthStatus.DEGRADED
                    || s.status() == HealthStatus.UNAVAILABLE) ? 1d : 0d);
            values.put("health_unknown", signals.isEmpty() || signals.stream().anyMatch(s -> s.status() == HealthStatus.UNKNOWN) ? 1d : 0d);
        } catch (RuntimeException | LinkageError failure) { complete = false; failure(Counter.SAMPLING_FAILURES, failure); }
        values.put("runtime_ready", source == null ? 0d : 1d);
        values.put("uptime_seconds", (System.nanoTime() - started) / 1_000_000_000d);
        for (var entry : counters.entrySet()) values.put(entry.getKey().id(), (double) entry.getValue().sum());
        try {
            telemetry.publish(Map.copyOf(values));
            health(complete ? HealthStatus.HEALTHY : HealthStatus.DEGRADED,
                    complete ? "AGGREGATES_READY" : source == null ? "STARTING" : "AGGREGATES_PARTIAL");
        } catch (RuntimeException | LinkageError failure) {
            failure(Counter.SAMPLING_FAILURES, failure);
            health(HealthStatus.DEGRADED, "PUBLICATION_FAILED");
        }
    }
    private void health(HealthStatus status, String code) {
        try { if (module.isOpen()) module.facilities().health().update("metrics", status, code); }
        catch (RuntimeException | LinkageError ignored) { }
    }
}
