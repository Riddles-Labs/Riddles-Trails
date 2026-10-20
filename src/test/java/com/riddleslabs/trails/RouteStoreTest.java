package com.riddleslabs.trails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RouteStoreTest {
    @TempDir Path directory;
    private Route route(String id) { return new Route(id, "world", List.of(new Route.Point(1, 64, 2), new Route.Point(8, 65, 3))); }
    @Test void destinationsSurviveRestartAndDeletionPreservesOtherRoutes() throws Exception {
        Path file = directory.resolve("destinations.yml");
        var store = new RouteStore(file);
        store.load(); store.save(route("market")); store.save(route("spawn"));
        var restarted = new RouteStore(file); restarted.load();
        assertEquals(route("market"), restarted.get("market"));
        assertTrue(restarted.delete("market"));
        var again = new RouteStore(file); again.load();
        assertNull(again.get("market")); assertEquals(route("spawn"), again.get("spawn"));
        assertFalse(again.delete("missing"));
        assertFalse(Files.exists(directory.resolve("destinations.yml.tmp")));
    }
    @Test void malformedReloadKeepsPreviouslyLoadedRoutes() throws Exception {
        Path file = directory.resolve("destinations.yml");
        var store = new RouteStore(file); store.save(route("spawn"));
        Files.writeString(file, "destinations:\n  broken:\n    world: world\n    points: []\n");
        assertThrows(IllegalArgumentException.class, store::load);
        assertEquals(route("spawn"), store.get("spawn"));
    }
    @Test void snapshotsCannotBeMutated() throws Exception {
        var store = new RouteStore(directory.resolve("destinations.yml")); store.save(route("spawn"));
        assertThrows(UnsupportedOperationException.class, () -> store.all().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.get("spawn").points().clear());
    }
    @Test void teleportTransitionsSurviveRestartWithoutBecomingWalkableSegments() throws Exception {
        Path file = directory.resolve("elevators.yml");
        var route = new Route("lift", "world", List.of(new Route.Point(0, 64, 0),
                new Route.Point(8, 64, 0), new Route.Point(8, 84, 0), new Route.Point(20, 84, 0)), List.of(2));
        new RouteStore(file).save(route);
        var loaded = new RouteStore(file); loaded.load();
        assertEquals(route, loaded.get("lift"));
        assertEquals(2, loaded.get("lift").sections().size());
        assertThrows(IllegalArgumentException.class, () -> new Route("bad", "world", route.points(), List.of(99)));
    }
    @Test void unsafeConfigValuesAreBounded() {
        var config = new YamlConfiguration();
        config.set("render.particle-spacing", Double.NaN);
        config.set("render.max-particles-per-update", Integer.MAX_VALUE);
        config.set("render.min-ahead", 20);
        config.set("render.max-ahead", 1);
        config.set("recording.point-spacing", 10);
        config.set("recording.max-step-distance", 1);
        var settings = TrailSettings.read(config);
        assertTrue(Double.isFinite(settings.spacing()));
        assertEquals(6000, settings.budget());
        assertTrue(settings.maxAhead() > settings.minAhead());
        assertTrue(settings.recordSpacing() < settings.maxStep());
    }
    @Test void particleBudgetUpgradeIsOnceOnlyAndPreservesCustomBudgets() {
        var config=new YamlConfiguration();config.set("render.max-particles-per-update",120);
        assertTrue(TrailSettings.upgradeParticleBudget(config));assertEquals(1200,TrailSettings.read(config).budget());
        config.set("render.max-particles-per-update",120);
        assertFalse(TrailSettings.upgradeParticleBudget(config));assertEquals(120,TrailSettings.read(config).budget());
        var custom=new YamlConfiguration();custom.set("render.max-particles-per-update",480);
        assertTrue(TrailSettings.upgradeParticleBudget(custom));assertEquals(480,TrailSettings.read(custom).budget());
    }
}
