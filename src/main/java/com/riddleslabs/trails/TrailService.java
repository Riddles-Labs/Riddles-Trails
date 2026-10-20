package com.riddleslabs.trails;

import com.riddleslabs.api.scheduler.TaskHandle;
import com.riddleslabs.paper.api.scheduler.ModuleScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import com.riddleslabs.trails.api.animation.TrailKind;

/** Personal forward-moving TRAIL particles, color-transition accents, and dust glow owned by Riddles Trails. */
public final class TrailService {
    private final ModuleScheduler scheduler;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    public TrailService(ModuleScheduler scheduler) { this.scheduler = java.util.Objects.requireNonNull(scheduler); }
    public int activeCount() { return sessions.size(); }

    public void start(Player player, Route destination, TrailSettings settings) {
        if (!player.getWorld().getName().equals(destination.world())) {
            player.sendMessage("§cThis destination is in world §f" + destination.world() + "§c. Travel there first.");
            return;
        }
        Route.Point from = point(player.getLocation());
        if (from.distanceSquared(destination.points().getLast()) <= settings.arrivalRadius() * settings.arrivalRadius()) {
            stop(player.getUniqueId());
            player.sendMessage("§aYou are already at " + destination.id() + ".");
            return;
        }
        stop(player.getUniqueId());
        Session session = new Session(player, destination, settings,
                new RouteGeometry.Path(RouteGeometry.join(from, destination)));
        sessions.put(player.getUniqueId(), session);
        try {
            session.task = scheduler.entityRepeating(player, 1L, settings.interval(), () -> {
                try { session.tick(); }
                catch (RuntimeException | LinkageError failure) { session.retired(); throw failure; }
            }, session::retired);
            if (session.task.isDone() || sessions.get(player.getUniqueId()) != session) { session.task.cancel(); session.retired(); return; }
        } catch (RuntimeException failure) { session.retired(); throw failure; }
        player.sendMessage("§aFollow the trail to §f" + destination.id() + "§a. §7/riddlestrails stop to cancel.");
    }

    public boolean stop(UUID id) {
        Session session = sessions.remove(id);
        if (session == null) return false;
        session.cancel();
        TrailParticles.forget(id,TrailKind.ORDINARY);
        return true;
    }
    /** Rejoin the original route from the player's actual position on the next entity tick. */
    public void rejoin(UUID id) {
        Session session = sessions.get(id);
        if (session != null) session.needsRejoin.set(true);
    }
    public void stopDestination(String id) {
        sessions.values().stream().filter(s -> s.destination.id().equals(id)).forEach(s -> stop(s.player.getUniqueId()));
    }
    public void shutdown() { for (UUID id : java.util.List.copyOf(sessions.keySet())) stop(id); }
    public static Route.Point point(Location l) { return new Route.Point(l.getX(), l.getY(), l.getZ()); }

    private final class Session {
        final Player player;
        final Route destination;
        final TrailSettings settings;
        RouteGeometry.Path path;
        final AtomicBoolean needsRejoin = new AtomicBoolean();
        final long started = System.nanoTime();
        volatile TaskHandle task;
        Session(Player player, Route destination, TrailSettings settings, RouteGeometry.Path path) {
            this.player = player; this.destination = destination; this.settings = settings; this.path = path;
        }
        void cancel() { TaskHandle current = task; if (current != null) current.cancel(); }
        void retired() { if(sessions.remove(player.getUniqueId(), this)) TrailParticles.forget(player.getUniqueId(),TrailKind.ORDINARY); }
        void finish(String message) {
            if (sessions.remove(player.getUniqueId(), this)) {
                cancel();
                TrailParticles.forget(player.getUniqueId(),TrailKind.ORDINARY);
                if (message != null && player.isOnline()) player.sendMessage(message);
            }
        }
        void tick() {
            if (sessions.get(player.getUniqueId()) != this) { cancel(); return; }
            if (!player.isOnline() || player.isDead()) {
                finish(null); return;
            }
            if ((System.nanoTime() - started) / 1_000_000_000L >= settings.timeoutSeconds()) {
                finish("§eTrail timed out. Use /riddlestrails " + destination.id() + " to restart."); return;
            }
            if (!player.getWorld().getName().equals(destination.world())) {
                needsRejoin.set(true);
                return;
            }
            Route.Point position = point(player.getLocation());
            if (needsRejoin.getAndSet(false))
                path = new RouteGeometry.Path(RouteGeometry.join(position, destination));
            if (position.distanceSquared(destination.points().getLast()) <= settings.arrivalRadius() * settings.arrivalRadius()) {
                finish("§aDestination reached: §f" + destination.id()); return;
            }
            double progress = path.progress(position);
            double end = Math.min(path.length(), progress + settings.maxAhead());
            double start = Math.min(end, progress + settings.minAhead());
            TrailParticles.render(player,settings,path,start,end,(System.nanoTime()-started)/1_000_000_000.0,TrailKind.ORDINARY,destination.id());
        }
    }
}
