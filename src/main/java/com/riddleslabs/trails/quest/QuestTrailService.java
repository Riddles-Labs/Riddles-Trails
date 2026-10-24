package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.*;
import com.riddleslabs.trails.api.QuestTrailAPI;
import com.riddleslabs.trails.api.animation.TrailKind;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.riddleslabs.paper.api.scheduler.ModuleScheduler;

/** Player-owned guidance on Paper and Folia. Assignments are rebuilt from progress on login. */
public final class QuestTrailService implements QuestTrailAPI, Listener {
    private final JavaPlugin plugin;
    private final TrailMessages messages;
    private final ModuleScheduler scheduler;
    private final Supplier<Config> configuration;
    private final RouteStore store;
    private final Supplier<TrailSettings> settings;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Restore> restores = new HashMap<>();
    private final CitizensTarget citizens;
    private volatile boolean closed;
    private volatile Config config;

    public QuestTrailService(JavaPlugin plugin, ModuleScheduler scheduler, RouteStore store, Supplier<TrailSettings> settings, Supplier<Config> configuration, TrailMessages messages) {
        this.messages = messages;
        this.plugin = plugin;
        this.scheduler = Objects.requireNonNull(scheduler);
        this.configuration = Objects.requireNonNull(configuration);
        this.store = store;
        this.settings = settings;
        reload();
        citizens = new CitizensTarget(plugin, scheduler);
    }

    public void reload() { config = configuration.get(); }

    @Override public void showToNpc(UUID playerId, int npcId) {
        Objects.requireNonNull(playerId, "playerId");
        if (npcId < 0) throw new IllegalArgumentException("NPC ID must be nonnegative");
        submit(playerId, player -> assign(player, npcId, null));
    }

    @Override public void showToLocation(UUID playerId, Location destination) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destination, "destination");
        Location copy = destination.clone();
        if (copy.getWorld() == null) throw new IllegalArgumentException("Destination needs a loaded world");
        TrailService.point(copy);
        submit(playerId, player -> assign(player, null, copy));
    }

    /** Called on the player's entity scheduler. This lock serializes installation with shutdown. */
    private synchronized void assign(Player player, Integer npcId, Location destination) {
        if (closed || !player.isOnline()) return;
        UUID id = player.getUniqueId();
        stopSession(id);
        Session session = new Session(player, npcId, destination);
        session.ticks = settings.get().interval() - 1;
        sessions.put(id, session);
        try {
            session.task.bind(scheduler.entityRepeating(player, 1, 1,
                    () -> tick(session), () -> retire(session)));
        } catch (RuntimeException | LinkageError failure) {
            retire(session);
            throw failure;
        }
    }

    @Override public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        submit(playerId, player -> stopSession(playerId));
    }

    @Override public boolean hasTrail(UUID playerId) { return sessions.containsKey(Objects.requireNonNull(playerId)); }

    /** Resolve only an entity handle here; every live player read happens in its callback. */
    private synchronized void submit(UUID id, java.util.function.Consumer<Player> work) {
        if (closed) throw new IllegalStateException("Riddles Trails is disabled");
        Restore restoring = restores.remove(id);
        if (restoring != null) restoring.task.close();
        Pending previous = pending.remove(id);
        if (previous != null) previous.task.close();
        Player player = Bukkit.getPlayer(id);
        if (player == null) { stopSession(id); return; }
        Pending request = new Pending(player);
        pending.put(id, request);
        try {
            request.task.bind(scheduler.entity(player, () -> {
                synchronized (QuestTrailService.this) {
                    if (closed || !pending.remove(id, request)) return;
                    request.task.close();
                    if (!player.isOnline() || Bukkit.getPlayer(id) != player) return;
                    try { work.accept(player); }
                    catch (RuntimeException | LinkageError failure) {
                        messages.observeFailure(TrailMetrics.Counter.QUEST_FAILURES, failure);
                        plugin.getLogger().warning("Quest assignment failed: " + failure.getClass().getSimpleName());
                    }
                }
            }, () -> retirePending(id, request)));
        } catch (RuntimeException | LinkageError failure) {
            retirePending(id, request);
            throw failure;
        }
    }

    private synchronized void retirePending(UUID id, Pending request) {
        pending.remove(id, request);
        request.task.close();
    }

    private synchronized void stopSession(UUID id) {
        Session old = sessions.remove(id);
        if (old != null) old.close();
        TrailParticles.forget(id, TrailKind.QUEST);
    }

    private synchronized void retire(Session session) {
        if (sessions.remove(session.player.getUniqueId(), session))
            TrailParticles.forget(session.player.getUniqueId(), TrailKind.QUEST);
        session.close();
    }

    public int activeCount() { return sessions.size(); }

    private void tick(Session session) {
        Player player = session.player;
        if (closed || sessions.get(player.getUniqueId()) != session) { session.close(); return; }
        if (!player.isOnline()) { retire(session); return; }
        TrailSettings render = settings.get();
        ++session.ticks;
        try {
            // Keep remote NPC snapshots fresh even with a long configured particle interval.
            if (session.target != null && session.ticks % 10 == 0) session.target.refresh();
            if (session.ticks % render.interval() == 0) render(player, session, render);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!session.warned) {
                session.warned = true;
                messages.observeFailure(TrailMetrics.Counter.QUEST_FAILURES, failure);
                plugin.getLogger().warning("Quest guidance paused: " + failure.getClass().getSimpleName());
                try { notice(player, session, "quest.notice-npc-unavailable"); }
                catch (RuntimeException | LinkageError notificationFailure) {
                    messages.observeFailure(TrailMetrics.Counter.NOTIFICATION_FAILURES, notificationFailure);
                }
            }
        }
    }

    private void render(Player player, Session session, TrailSettings render) throws ReflectiveOperationException {
        if (player.isDead()) return;
        Location target = session.npcId == null ? session.destination : session.target.location();
        if (target == null) { notice(player, session, "quest.notice-npc-paused"); return; }
        if (!player.getWorld().equals(target.getWorld())) {
            session.planTarget = null;
            notice(player, session, "quest.notice-world", "world", target.getWorld().getName());
            return;
        }
        Config rules = config;
        Map<String, Route> routes = store.all();
        Route.Point position = TrailService.point(player.getLocation()), endpoint = TrailService.point(target);
        if (position.distanceSquared(endpoint) <= render.arrivalRadius() * render.arrivalRadius()) {
            notice(player, session, "quest.notice-arrived"); return;
        }
        double progress=session.plan==null?0:session.plan.path.progress(position);
        boolean offPath = session.plan != null && session.plan.path.at(progress).distanceSquared(position) > 9;
        if (session.planTarget == null || endpoint.distanceSquared(session.planTarget) > .25
                || session.ticks - session.planAt >= 40 || offPath || session.routeSnapshot != routes || session.config != rules) {
            session.plan = plan(player.getWorld(), session, position, endpoint, rules, routes);
            progress=session.plan==null?0:session.plan.path.progress(position);
            session.planTarget = endpoint;
            session.planAt = session.ticks;
            session.routeSnapshot = routes;
            session.config = rules;
        }
        if (session.plan == null) {
            notice(player, session, "quest.notice-no-connection");
            return;
        }
        RouteGeometry.Path path = session.plan.path;
        double end = Math.min(path.length(), progress + render.maxAhead());
        // Render the usable prefix. A later obstruction must not hide the road leading up to it.
        List<Route.Point> visible = new ArrayList<>();
        SafeCorridor.Snapshot corridor=new SafeCorridor.Snapshot(player.getWorld());
        for (double d = progress; ; d = Math.min(end, d + .2)) {
            Route.Point adjusted = corridor.adjust(path.at(d));
            if (adjusted == null) break;
            QuestPath.append(visible, adjusted);
            if (d >= end) break;
        }
        if (visible.isEmpty()) {
            notice(player, session, "quest.notice-obstructed"); return;
        }
        session.warned = false;
        if (session.plan.teleport && position.distanceSquared(path.end()) <= 2.25)
            notice(player, session, "quest.notice-teleport");
        else session.notice = null;
        RouteGeometry.Path display = new RouteGeometry.Path(visible);
        TrailParticles.render(player,render,display,Math.min(display.length(),render.minAhead()),display.length(),
                (System.nanoTime()-session.started)/1_000_000_000.0,TrailKind.QUEST,
                session.npcId==null?"location:"+target.getWorld().getName()+":"+target.getX()+":"+target.getY()+":"+target.getZ():"npc:"+session.npcId);
    }

    private record Plan(RouteGeometry.Path path, boolean teleport) {}

    private Plan plan(World world, Session session, Route.Point position, Route.Point endpoint, Config config, Map<String, Route> routes) {
        List<String> ids = session.npcId == null ? config.locationRoutes : config.npcRoutes.getOrDefault(session.npcId, List.of());
        List<QuestPath.Entrance> entrances = new ArrayList<>();
        for (String id : ids) {
            Route route = routes.get(id);
            if (route != null && route.world().equals(world.getName()))
                entrances.addAll(QuestPath.entrances(route, position, endpoint, config.targetRadius));
        }
        entrances.sort(Comparator.comparingDouble(QuestPath.Entrance::distanceSquared));
        Plan partial = null;
        SafeCorridor.Snapshot corridor=new SafeCorridor.Snapshot(world);
        for (QuestPath.Entrance entrance : entrances.stream().limit(3).toList()) {
            WalkingConnector.Result connection = WalkingConnector.find(corridor, position, entrance.tail().getFirst(), config.connectorNodes / 3);
            if (connection == null) continue;
            List<Route.Point> points = new ArrayList<>(connection.points());
            if (connection.complete()) {
                entrance.tail().forEach(point -> QuestPath.append(points, point));
                return new Plan(new RouteGeometry.Path(points), entrance.teleport());
            }
            if (partial == null) partial = new Plan(new RouteGeometry.Path(points), false);
        }
        return partial;
    }

    private void notice(Player player, Session session, String key, Object... values) {
        String identity = messages.noticeIdentity(player, key, values);
        if (!identity.equals(session.notice)) { messages.send(player, key, values); session.notice = identity; }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { restore(event.getPlayer()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.player == event.getPlayer()) {
            session.planTarget = null;
            session.ticks = settings.get().interval() - 1;
        }
    }

    @EventHandler public synchronized void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Session session = sessions.get(id);
        if (session != null && session.player == player) retire(session);
        Pending request = pending.get(id);
        if (request != null && request.player == player) retirePending(id, request);
        Restore restore = restores.get(id);
        if (restore != null && restore.player == player) finishRestore(id, restore);
    }

    public synchronized void restore(Player player) {
        if (closed || config.restoreRules.isEmpty()) return;
        UUID id = player.getUniqueId();
        // A command or API assignment submitted during login wins over restoration.
        if (sessions.containsKey(id) || pending.containsKey(id) || restores.containsKey(id)) return;
        Restore restore = new Restore(player);
        restores.put(id, restore);
        restoreLater(id, restore, 0);
    }

    private synchronized void restoreLater(UUID id, Restore restore, int attempt) {
        if (closed || restores.get(id) != restore) return;
        restore.task.close();
        restore.task = new QuestTask();
        try {
            restore.task.bind(scheduler.entityDelayed(restore.player, 20, () -> {
                if (closed) return;
                synchronized (QuestTrailService.this) {
                    if (restores.get(id) != restore) return;
                }
                Player player = restore.player;
                if (!player.isOnline() || Bukkit.getPlayer(id) != player) { finishRestore(id, restore); return; }
                try {
                    Integer npc = BeautyQuestsProgress.destination(player, config.restoreRules);
                    synchronized (QuestTrailService.this) {
                        if (closed || restores.get(id) != restore) return;
                        finishRestore(id, restore);
                        stopSession(id);
                        if (npc != null) assign(player, npc, null);
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    if (attempt < 9) restoreLater(id, restore, attempt + 1);
                    else {
                        finishRestore(id, restore);
                        messages.observeFailure(TrailMetrics.Counter.QUEST_FAILURES, failure);
                        plugin.getLogger().warning("Quest progress restoration unavailable: " + failure.getClass().getSimpleName());
                    }
                }
            }, () -> finishRestore(id, restore)));
        } catch (RuntimeException | LinkageError failure) {
            finishRestore(id, restore);
            if (!closed) messages.observeFailure(TrailMetrics.Counter.QUEST_FAILURES, failure);
        }
    }

    private synchronized void finishRestore(UUID id, Restore restore) {
        if (restores.remove(id, restore)) restore.task.close();
    }

    public synchronized void shutdown() {
        closed = true;
        var requests = List.copyOf(pending.values());
        pending.clear();
        requests.forEach(request -> request.task.close());
        var restorations = List.copyOf(restores.values());
        restores.clear();
        restorations.forEach(restore -> restore.task.close());
        var closing = List.copyOf(sessions.values());
        sessions.clear();
        closing.forEach(session -> {
            session.close();
            TrailParticles.forget(session.player.getUniqueId(), TrailKind.QUEST);
        });
        citizens.close();
    }

    private static final class Pending {
        final Player player;
        final QuestTask task = new QuestTask();
        Pending(Player player) { this.player = player; }
    }

    private static final class Restore {
        final Player player;
        QuestTask task = new QuestTask();
        Restore(Player player) { this.player = player; }
    }

    private final class Session {
        final Player player;
        final Integer npcId;
        final Location destination;
        final CitizensTarget.Tracker target;
        final QuestTask task = new QuestTask();
        long ticks;
        String notice;
        boolean warned;
        final long started = System.nanoTime();
        Plan plan;
        Route.Point planTarget;
        long planAt;
        Map<String, Route> routeSnapshot;
        Config config;
        Session(Player player, Integer npcId, Location destination) {
            this.player = player; this.npcId = npcId; this.destination = destination;
            target = npcId == null ? null : citizens.track(npcId);
        }
        void close() { task.close(); if (target != null) target.close(); }
    }

    public record Config(Map<Integer, List<String>> npcRoutes, List<String> locationRoutes,
                          List<Map<?, ?>> restoreRules, int connectorNodes, double targetRadius) {
        public static Config read(ConfigurationSection root) {
            Map<Integer, List<String>> mappings = new HashMap<>();
            ConfigurationSection section = root.getConfigurationSection("quest-trails.npc-routes");
            if (section != null) for (String key : section.getKeys(false)) {
                int id = Integer.parseInt(key);
                if (id < 0) throw new IllegalArgumentException("NPC IDs must be nonnegative");
                mappings.put(id, List.copyOf(section.getStringList(key)));
            }
            List<Map<?, ?>> rules = new ArrayList<>();
            for (Map<?, ?> rule : root.getMapList("quest-trails.restore-rules")) {
                for (String key : List.of("after-quest", "until-quest", "npc")) BeautyQuestsProgress.integer(rule, key);
                rules.add(Map.copyOf(rule));
            }
            return new Config(Map.copyOf(mappings), List.copyOf(root.getStringList("quest-trails.location-routes")),
                    List.copyOf(rules), Math.clamp(root.getInt("quest-trails.connector-max-nodes", 768), 96, 3072), radius(root, "target-radius", 3));
        }
        private static double radius(ConfigurationSection root, String key, double fallback) {
            double value = root.getDouble("quest-trails." + key, fallback);
            return Double.isFinite(value) ? Math.clamp(value, .5, 8) : fallback;
        }
    }
}
