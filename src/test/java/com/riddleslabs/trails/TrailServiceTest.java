package com.riddleslabs.trails;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrailServiceTest {
    @Test void teleportRejoinsOriginalRouteIncludingPreviouslySkippedSegments() {
        Fixture f = new Fixture();
        try (var particles = mockStatic(TrailParticles.class)) {
            f.start(); // Starts partway down the route, skipping its initial segment.
            when(f.player.getLocation()).thenReturn(new Location(f.world, 0, 64, 0));
            f.service.rejoin(f.id);
            f.tick();
            particles.verify(() -> TrailParticles.render(eq(f.player), any(),
                    argThat(path -> path.at(2).equals(new Route.Point(2,64,0))), eq(2.0), anyDouble(),anyDouble(),any(),anyString()));
            verify(f.task, never()).cancel();
            assertTrue(f.service.stop(f.id));
        }
    }

    @Test void crossWorldTeleportPausesAndReturnResumesTheSameAssignment() {
        Fixture f = new Fixture();
        try (var particles = mockStatic(TrailParticles.class)) {
            f.start();
            World other = mock(World.class);
            when(other.getName()).thenReturn("other");
            when(f.player.getWorld()).thenReturn(other);
            f.service.rejoin(f.id);
            f.tick();
            particles.verifyNoInteractions();
            verify(f.task, never()).cancel();
            when(f.player.getWorld()).thenReturn(f.world);
            when(f.player.getLocation()).thenReturn(new Location(f.world, 0, 64, 0));
            f.tick();
            particles.verify(() -> TrailParticles.render(eq(f.player), any(),
                    argThat(path -> path.at(2).equals(new Route.Point(2,64,0))), eq(2.0), anyDouble(),anyDouble(),any(),anyString()));
            assertTrue(f.service.stop(f.id));
        }
    }

    @Test void teleportToDestinationStillFinishesOnArrival() {
        Fixture f = new Fixture();
        try (var particles = mockStatic(TrailParticles.class)) {
            f.start();
            when(f.player.getLocation()).thenReturn(new Location(f.world, 20, 64, 0));
            f.service.rejoin(f.id);
            f.tick();
            particles.verify(() -> TrailParticles.render(any(),any(),any(),anyDouble(),anyDouble(),anyDouble(),any(),anyString()),never());
            verify(f.task).cancel();
            assertFalse(f.service.stop(f.id));
        }
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final Player player = mock(Player.class);
        final World world = mock(World.class);
        final Plugin plugin = mock(Plugin.class);
        final EntityScheduler scheduler = mock(EntityScheduler.class);
        final ScheduledTask task = mock(ScheduledTask.class);
        final TrailService service = new TrailService(plugin);
        Consumer<ScheduledTask> action;

        Fixture() {
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(world.getName()).thenReturn("world");
            when(player.getLocation()).thenReturn(new Location(world, 8, 64, 0));
            when(player.getScheduler()).thenReturn(scheduler);
            when(scheduler.runAtFixedRate(eq(plugin), any(), any(), eq(1L), eq(6L))).thenAnswer(call -> {
                action = call.getArgument(1); return task;
            });
        }

        void start() {
            service.start(player, new Route("road", "world", List.of(
                    new Route.Point(0, 64, 0), new Route.Point(20, 64, 0))),
                    TrailSettings.read(new YamlConfiguration()));
        }

        void tick() { action.accept(task); }
    }
}
