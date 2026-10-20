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
import com.riddleslabs.api.scheduler.TaskHandle;
import com.riddleslabs.paper.api.scheduler.ModuleScheduler;

/** Paper server-thread implementation. Quest assignments are rebuilt from progress on login. */
public final class QuestTrailService implements QuestTrailAPI, Listener {
    private final JavaPlugin plugin;
    private final ModuleScheduler scheduler;
    private final Supplier<Config> configuration;
    private final RouteStore store;
    private final Supplier<TrailSettings> settings;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Object> restores = new HashMap<>();
    private final Set<TaskHandle> restoreTasks = new HashSet<>();
    private final TaskHandle ticker;
    private volatile boolean closed;
    private Config config;

    public QuestTrailService(JavaPlugin plugin, ModuleScheduler scheduler, RouteStore store, Supplier<TrailSettings> settings, Supplier<Config> configuration) {
        this.plugin = plugin;
        this.scheduler = Objects.requireNonNull(scheduler);
        this.configuration = Objects.requireNonNull(configuration);
        this.store = store;
        this.settings = settings;
        reload();
        ticker = scheduler.globalRepeating(1, 1, this::tick);
    }

    public void reload() { config = configuration.get(); }

    @Override public void showToNpc(UUID playerId, int npcId) {
        Objects.requireNonNull(playerId, "playerId");
        if (npcId < 0) throw new IllegalArgumentException("NPC ID must be nonnegative");
        submit(() -> assign(playerId, new Session(npcId, null)));
    }

    @Override public void showToLocation(UUID playerId, Location destination) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destination, "destination");
        if (destination.getWorld() == null) throw new IllegalArgumentException("Destination needs a loaded world");
        Location copy = destination.clone();
        TrailService.point(copy); // validates finite coordinates before queuing
        submit(() -> assign(playerId, new Session(null, copy)));
    }

    private void assign(UUID id, Session session) {
        TrailParticles.forget(id,TrailKind.QUEST);
        restores.remove(id); // a new command/API decision always wins over delayed login restoration
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline()) {
            session.ticks = settings.get().interval() - 1; // first render on the next server tick
            sessions.put(id, session);
        }
        else sessions.remove(id);
    }

    @Override public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        submit(() -> { restores.remove(playerId); sessions.remove(playerId); TrailParticles.forget(playerId,TrailKind.QUEST); });
    }

    @Override public boolean hasTrail(UUID playerId) { return sessions.containsKey(Objects.requireNonNull(playerId)); }

    private void submit(Runnable work) {
        if (closed) throw new IllegalStateException("Riddles Trails is disabled");
        if (Bukkit.isPrimaryThread()) work.run();
        else scheduler.global(() -> { if (!closed) work.run(); });
    }

    private void tick() {
        TrailSettings render = settings.get();
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.isOnline()) {
                if(sessions.remove(entry.getKey(), session)) TrailParticles.forget(entry.getKey(),TrailKind.QUEST);
                continue;
            }
            if (++session.ticks % render.interval() != 0) continue;
            try { render(player, session, render); }
            catch (ReflectiveOperationException | IllegalStateException ex) {
                notice(player, session, "The quest NPC is unavailable. Guidance will resume when it returns.");
                if (!session.warned) { plugin.getLogger().warning("Quest trail target unavailable: " + ex.getMessage()); session.warned = true; }
            }
        }
    }

    private void render(Player player, Session session, TrailSettings render) throws ReflectiveOperationException {
        if (player.isDead()) return;
        Location target = session.npcId == null ? session.destination : CitizensTarget.location(session.npcId);
        if (target == null) { notice(player, session, "The quest NPC is not spawned. Guidance is paused."); return; }
        if (!player.getWorld().equals(target.getWorld())) {
            session.planTarget = null;
            notice(player, session, "Your quest destination is in " + target.getWorld().getName() + ". Travel there to resume guidance.");
            return;
        }
        Route.Point position = TrailService.point(player.getLocation()), endpoint = TrailService.point(target);
        if (position.distanceSquared(endpoint) <= render.arrivalRadius() * render.arrivalRadius()) {
            notice(player, session, "Quest destination reached."); return;
        }
        double progress=session.plan==null?0:session.plan.path.progress(position);
        boolean offPath = session.plan != null && session.plan.path.at(progress).distanceSquared(position) > 9;
        if (session.planTarget == null || endpoint.distanceSquared(session.planTarget) > .25
                || session.ticks - session.planAt >= 40 || offPath || session.routeSnapshot != store.all() || session.config != config) {
            session.plan = plan(player.getWorld(), session, position, endpoint);
            progress=session.plan==null?0:session.plan.path.progress(position);
            session.planTarget = endpoint;
            session.planAt = session.ticks;
            session.routeSnapshot = store.all();
            session.config = config;
        }
        if (session.plan == null) {
            notice(player, session, "No walkable connection to a configured quest route is currently available.");
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
            notice(player, session, "The next part of the quest route is obstructed or not loaded."); return;
        }
        session.warned = false;
        if (session.plan.teleport && position.distanceSquared(path.end()) <= 2.25)
            notice(player, session, "Use the elevator or teleport here; your trail continues on the other side.");
        else session.notice = null;
        RouteGeometry.Path display = new RouteGeometry.Path(visible);
        TrailParticles.render(player,render,display,Math.min(display.length(),render.minAhead()),display.length(),
                (System.nanoTime()-session.started)/1_000_000_000.0,TrailKind.QUEST,
                session.npcId==null?"location:"+target.getWorld().getName()+":"+target.getX()+":"+target.getY()+":"+target.getZ():"npc:"+session.npcId);
    }

    private record Plan(RouteGeometry.Path path, boolean teleport) {}

    private Plan plan(World world, Session session, Route.Point position, Route.Point endpoint) {
        List<String> ids = session.npcId == null ? config.locationRoutes : config.npcRoutes.getOrDefault(session.npcId, List.of());
        List<QuestPath.Entrance> entrances = new ArrayList<>();
        for (String id : ids) {
            Route route = store.get(id);
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

    private static void notice(Player player, Session session, String message) {
        if (!message.equals(session.notice)) { player.sendMessage("§e" + message); session.notice = message; }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { restore(event.getPlayer()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            session.planTarget = null;
            session.ticks = settings.get().interval() - 1;
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        restores.remove(id);
    }

    public void restore(Player player) {
        if (config.restoreRules.isEmpty()) return;
        Object token = new Object();
        restores.put(player.getUniqueId(), token);
        restoreLater(player.getUniqueId(), token, 0);
    }

    private void restoreLater(UUID id, Object token, int attempt) {
        // This service is only constructed on Paper: scheduling and this set share its server thread.
        TaskHandle[] reference = new TaskHandle[1];
        TaskHandle task = scheduler.globalDelayed(20, () -> {
            restoreTasks.remove(reference[0]);
            if (closed || restores.get(id) != token) return;
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) { restores.remove(id); return; }
            try {
                Integer npc = BeautyQuestsProgress.destination(player, config.restoreRules);
                restores.remove(id);
                // Current quest progress is authoritative; do not persist and replay stale assignments.
                sessions.remove(id);
                if (npc != null) assign(id, new Session(npc, null));
            } catch (ReflectiveOperationException | IllegalStateException ex) {
                if (attempt < 9) restoreLater(id, token, attempt + 1);
                else {
                    restores.remove(id);
                    plugin.getLogger().warning("Could not restore quest guidance for " + player.getName() + ": " + ex.getMessage());
                }
            }
        });
        reference[0] = task;
        restoreTasks.add(task);
    }

    public void shutdown() {
        closed = true;
        ticker.cancel();
        restoreTasks.forEach(TaskHandle::cancel);
        restoreTasks.clear();
        restores.clear();
        sessions.keySet().forEach(id->TrailParticles.forget(id,TrailKind.QUEST));
        sessions.clear();
    }

    private static final class Session {
        final Integer npcId;
        final Location destination;
        int ticks;
        String notice;
        boolean warned;
        final long started=System.nanoTime();
        Plan plan;
        Route.Point planTarget;
        int planAt;
        Map<String, Route> routeSnapshot;
        Config config;
        Session(Integer npcId, Location destination) { this.npcId = npcId; this.destination = destination; }
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
