package com.riddleslabs.trails.quest;

import com.riddleslabs.paper.api.scheduler.ModuleScheduler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Event-maintained NPC handles; live positions are captured only by the entity's owner. */
final class CitizensTarget implements Listener, AutoCloseable {
    private record Npc(Object identity, Entity entity) {}
    private record Observation(Entity entity, Location location, long captured) {}
    private final JavaPlugin owner;
    private final ModuleScheduler scheduler;
    private final Map<Integer, Npc> entities = new ConcurrentHashMap<>();
    private final Plugin citizens;
    private Object registry;
    private volatile boolean closed;
    private volatile boolean available;

    /** Construct during plugin enable, before region ticks start; never scan the registry from a timer. */
    CitizensTarget(JavaPlugin owner, ModuleScheduler scheduler) {
        this.owner = owner;
        this.scheduler = scheduler;
        citizens = Bukkit.getPluginManager().getPlugin("Citizens");
        if (citizens == null || !citizens.isEnabled()) return;
        try {
            registry = OptionalPluginAccess.call(
                    OptionalPluginAccess.type("Citizens", "net.citizensnpcs.api.CitizensAPI"), "getNPCRegistry");
            listen("NPCSpawnEvent", true);
            listen("NPCDespawnEvent", false);
            listen("NPCRemoveEvent", false);
            Bukkit.getPluginManager().registerEvents(this, owner);
            // Startup seeds already-spawned handles only. No isSpawned/isValid/location reads here.
            for (Object npc : (Iterable<?>) registry) remember(npc);
            available = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            HandlerList.unregisterAll(this);
            entities.clear();
            owner.getLogger().warning("Citizens quest adapter unavailable: " + failure.getClass().getSimpleName());
        }
    }

    private void listen(String name, boolean spawned) throws ReflectiveOperationException {
        Class<? extends Event> type = OptionalPluginAccess.type("Citizens", "net.citizensnpcs.api.event." + name).asSubclass(Event.class);
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.MONITOR, (listener, event) -> {
            if (closed) return;
            try {
                Object npc = OptionalPluginAccess.call(event, "getNPC");
                if (OptionalPluginAccess.call(npc, "getOwningRegistry") != registry) return;
                if (spawned) remember(npc);
                else {
                    int id = (Integer) OptionalPluginAccess.call(npc, "getId");
                    entities.computeIfPresent(id, (key, current) -> current.identity == npc ? null : current);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                if (available) owner.getLogger().warning("Citizens quest events unavailable: " + failure.getClass().getSimpleName());
                available = false;
                entities.clear();
            }
        }, owner, true);
    }

    private void remember(Object npc) throws ReflectiveOperationException {
        int id = (Integer) OptionalPluginAccess.call(npc, "getId");
        Entity entity = (Entity) OptionalPluginAccess.call(npc, "getEntity");
        if (entity != null) entities.put(id, new Npc(npc, entity));
    }

    @EventHandler public void disabled(PluginDisableEvent event) {
        if (event.getPlugin() == citizens) { available = false; entities.clear(); }
    }

    Tracker track(int id) { return new Tracker(id); }

    final class Tracker implements AutoCloseable {
        private final int id;
        private final AtomicReference<QuestTask> pending = new AtomicReference<>();
        private volatile Observation observation;
        private volatile boolean retired;
        private volatile boolean readFailed;
        Tracker(int id) { this.id = id; }

        void refresh() {
            if (retired || closed) return;
            if (!available || citizens == null || !citizens.isEnabled())
                throw new IllegalStateException("Citizens quest adapter is unavailable");
            Npc npc = entities.get(id);
            if (npc == null) { observation = null; readFailed = false; return; }
            Entity entity = npc.entity;
            if (com.riddleslabs.paper.api.scheduler.PlatformThreads.owns(entity)) capture(npc);
            else {
                QuestTask request = new QuestTask();
                if (pending.compareAndSet(null, request)) {
                    try {
                        request.bind(scheduler.entity(entity, () -> {
                            try { capture(npc); }
                            finally { pending.compareAndSet(request, null); request.close(); }
                        }, () -> {
                            Observation current = observation;
                            if (current != null && current.entity == entity) observation = null;
                            pending.compareAndSet(request, null);
                            request.close();
                        }));
                        if (retired || closed) { pending.compareAndSet(request, null); request.close(); }
                    } catch (RuntimeException | LinkageError failure) {
                        pending.compareAndSet(request, null); request.close();
                        throw failure;
                    }
                }
            }
        }

        Location location() {
            refresh();
            if (retired || closed) return null;
            if (readFailed) throw new IllegalStateException("NPC location observation failed");
            Npc npc = entities.get(id);
            if (npc == null) return null;
            Entity entity = npc.entity;
            Observation current = observation;
            // A stalled, despawned or replaced NPC must not leave a stale destination on screen.
            return current != null && current.entity == entity && entities.get(id) == npc
                    && System.nanoTime() - current.captured < 2_000_000_000L ? current.location.clone() : null;
        }

        private void capture(Npc npc) {
            if (retired || closed || entities.get(id) != npc) return;
            try {
                if (!npc.entity.isValid()) { observation = null; return; }
                Location location = npc.entity.getLocation();
                if (!retired && !closed && entities.get(id) == npc)
                    observation = new Observation(npc.entity, location, System.nanoTime());
                readFailed = false;
            } catch (RuntimeException | LinkageError failure) { observation = null; readFailed = true; }
        }

        @Override public void close() {
            retired = true;
            observation = null;
            QuestTask task = pending.getAndSet(null);
            if (task != null) task.close();
        }
    }

    @Override public void close() {
        closed = true;
        available = false;
        HandlerList.unregisterAll(this);
        entities.clear();
    }
}
