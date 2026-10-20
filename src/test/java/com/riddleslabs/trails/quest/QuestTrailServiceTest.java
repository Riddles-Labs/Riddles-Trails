package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.*;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestTrailServiceTest {
    @TempDir Path directory;
    private final UUID id = UUID.randomUUID();
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<TrailParticles> particles;
    private JavaPlugin plugin;
    private Player player;
    private World world;
    private BukkitScheduler scheduler;
    private BukkitTask tickerTask;
    private YamlConfiguration config;
    private Runnable ticker;
    private final List<Runnable> delayed = new ArrayList<>();
    private QuestTrailService service;
    private RouteStore store;

    @BeforeEach void setup() throws Exception {
        bukkit = mockStatic(Bukkit.class);
        particles = mockStatic(TrailParticles.class);
        plugin = mock(JavaPlugin.class);
        player = mock(Player.class);
        world = mock(World.class);
        scheduler = mock(BukkitScheduler.class);
        tickerTask = mock(BukkitTask.class);
        config = new YamlConfiguration();
        config.set("render.update-interval-ticks", 2);
        config.set("quest-trails.npc-routes.11", List.of("road"));
        config.set("quest-trails.location-routes", List.of("road"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("quest-test"));
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, .5, 64, .5));
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block air = mock(Block.class);
        VoxelShape empty = mock(VoxelShape.class);
        when(empty.getBoundingBoxes()).thenReturn(List.of());
        when(air.getCollisionShape()).thenReturn(empty);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenAnswer(call -> {
            ticker = call.getArgument(1); return tickerTask;
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(20L))).thenAnswer(call -> {
            delayed.add(call.getArgument(1));
            BukkitTask task = mock(BukkitTask.class);
            when(task.getTaskId()).thenReturn(delayed.size());
            return task;
        });
        store = new RouteStore(directory.resolve("routes.yml"));
        store.save(new Route("road", "world", List.of(new Route.Point(.5, 64, .5), new Route.Point(10.5, 64, .5))));
        service = new QuestTrailService(plugin, store, () -> TrailSettings.read(config));
    }

    @AfterEach void cleanup() {
        if (service != null) service.shutdown();
        particles.close();
        bukkit.close();
    }

    private void render() { ticker.run(); ticker.run(); }
    private Location destination() { return new Location(world, 10.5, 64, .5); }
    private void expectParticles() {
        particles.verify(() -> TrailParticles.render(eq(player), any(), any(),anyDouble(),anyDouble(),anyDouble(),any(),anyString()), atLeastOnce());
    }

    @Test void assignmentRendersPrivatelyAndCopiesMutableLocations() {
        Location location = destination();
        service.showToLocation(id, location);
        location.setX(200);
        render();
        assertTrue(service.hasTrail(id));
        expectParticles();
    }

    @Test void replacingAndClearingNeverLeaveOldGuidanceRendering() {
        service.showToLocation(id, destination());
        try (var citizens = mockStatic(CitizensTarget.class)) {
            citizens.when(() -> CitizensTarget.location(13)).thenReturn(null);
            service.showToNpc(id, 13);
            render();
            particles.verify(() -> TrailParticles.render(any(),any(),any(),anyDouble(),anyDouble(),anyDouble(),any(),anyString()),never());
            service.clear(id);
            assertFalse(service.hasTrail(id));
            render();
            citizens.verify(() -> CitizensTarget.location(13), times(1));
        }
    }

    @Test void npcRespawnAndWorldReturnResumeWithoutReassigning() throws Exception {
        try (var citizens = mockStatic(CitizensTarget.class)) {
            citizens.when(() -> CitizensTarget.location(11)).thenReturn(null);
            service.showToNpc(id, 11);
            render();
            particles.verify(() -> TrailParticles.render(any(),any(),any(),anyDouble(),anyDouble(),anyDouble(),any(),anyString()),never());
            World other = mock(World.class);
            when(other.getName()).thenReturn("other");
            citizens.when(() -> CitizensTarget.location(11)).thenReturn(new Location(other, 10, 64, 0));
            render();
            particles.verify(() -> TrailParticles.render(any(),any(),any(),anyDouble(),anyDouble(),anyDouble(),any(),anyString()),never());
            citizens.when(() -> CitizensTarget.location(11)).thenReturn(destination());
            render();
            expectParticles();
            citizens.when(() -> CitizensTarget.location(11)).thenReturn(new Location(world, 100, 64, 0));
            particles.clearInvocations();
            render();
            particles.verify(() -> TrailParticles.render(any(),any(),any(),anyDouble(),anyDouble(),anyDouble(),any(),anyString()),never());
        }
    }

    @Test void unloadedChunksSuppressParticlesWithoutLoadingChunks() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        service.showToLocation(id, destination());
        render();
        particles.verify(() -> TrailParticles.render(any(),any(),any(),anyDouble(),anyDouble(),anyDouble(),any(),anyString()),never());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test void quittingAndShutdownRemoveAssignments() {
        service.showToNpc(id, 11);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        service.onQuit(quit);
        assertFalse(service.hasTrail(id));
        service.showToNpc(id, 13);
        service.shutdown();
        assertFalse(service.hasTrail(id));
        verify(tickerTask).cancel();
        assertThrows(IllegalStateException.class, () -> service.showToNpc(id, 11));
    }

    @Test void restoreReadsProgressButCannotOverrideANewerClear() {
        config.set("quest-trails.restore-rules", List.of(Map.of("after-quest", 1, "until-quest", 2, "npc", 11)));
        service.reload();
        try (var progress = mockStatic(BeautyQuestsProgress.class)) {
            progress.when(() -> BeautyQuestsProgress.destination(eq(player), anyList())).thenReturn(11);
            service.restore(player);
            delayed.getFirst().run();
            assertTrue(service.hasTrail(id));
            service.clear(id);
            service.restore(player);
            service.clear(id);
            delayed.get(1).run();
            assertFalse(service.hasTrail(id));
            progress.verify(() -> BeautyQuestsProgress.destination(eq(player), anyList()), times(1));
        }
    }

    @Test void missingProgressRetriesAndDisconnectInvalidatesPendingRestoration() {
        config.set("quest-trails.restore-rules", List.of(Map.of("after-quest", 1, "until-quest", 2, "npc", 11)));
        service.reload();
        try (var progress = mockStatic(BeautyQuestsProgress.class)) {
            progress.when(() -> BeautyQuestsProgress.destination(eq(player), anyList()))
                    .thenThrow(new IllegalStateException("not loaded"));
            service.restore(player);
            delayed.getFirst().run();
            assertEquals(2, delayed.size());
            PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(player);
            service.onQuit(quit);
            delayed.get(1).run();
            assertFalse(service.hasTrail(id));
            progress.verify(() -> BeautyQuestsProgress.destination(eq(player), anyList()), times(1));
        }
    }

    @Test void questCompletionAssignmentRendersOnNextTickFromFarOffRoute() {
        World terrain = WalkingConnectorTest.flat();
        when(terrain.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(terrain);
        when(player.getLocation()).thenReturn(new Location(terrain, -20.5, 64, .5));
        service.showToLocation(id, new Location(terrain, 10.5, 64, .5));
        ticker.run();
        expectParticles();
        particles.verify(() -> TrailParticles.render(eq(player), any(),
                argThat(p -> p.at(2).x() < -10), anyDouble(),anyDouble(),anyDouble(),any(),anyString()), atLeastOnce());
    }

    @Test void obstructionAheadKeepsTheVisiblePrefix() {
        Block wall = WalkingConnectorTest.shape(new org.bukkit.util.BoundingBox(0, 0, 0, 1, 1, 1));
        for (int y = 64; y <= 67; y++) when(world.getBlockAt(7, y, 0)).thenReturn(wall);
        service.showToLocation(id, destination());
        ticker.run();
        expectParticles();
        particles.verify(() -> TrailParticles.render(eq(player), any(),
                argThat(p -> p.end().x() >= 7), anyDouble(),anyDouble(),anyDouble(),any(),anyString()), never());
    }

    @Test void elevatorShowsEntranceThenResumesOnUpperFloorWithoutDrawingThroughFloor() throws Exception {
        store.save(new Route("road", "world", List.of(new Route.Point(.5, 64, .5),
                new Route.Point(5.5, 64, .5), new Route.Point(5.5, 84, .5),
                new Route.Point(15.5, 84, .5)), List.of(2)));
        service.showToLocation(id, new Location(world, 15.5, 84, .5));
        ticker.run();
        expectParticles();
        particles.verify(() -> TrailParticles.render(eq(player), any(),
                argThat(p -> p.end().y() > 64.01), anyDouble(),anyDouble(),anyDouble(),any(),anyString()), never());
        particles.clearInvocations();
        when(player.getLocation()).thenReturn(new Location(world, 5.5, 84, .5));
        PlayerTeleportEvent teleport = mock(PlayerTeleportEvent.class);
        when(teleport.getPlayer()).thenReturn(player);
        service.onTeleport(teleport);
        ticker.run();
        expectParticles();
        particles.verify(() -> TrailParticles.render(eq(player), any(),
                argThat(p -> p.at(0).y() < 83.99), anyDouble(),anyDouble(),anyDouble(),any(),anyString()), never());
        assertTrue(service.hasTrail(id));
    }
}
