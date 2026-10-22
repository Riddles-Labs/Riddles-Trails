package com.riddleslabs.trails;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import com.riddleslabs.api.config.ReloadableConfiguration;
import com.riddleslabs.api.health.HealthStatus;
import com.riddleslabs.api.module.ModuleDescriptor;
import com.riddleslabs.api.service.ServiceKey;
import com.riddleslabs.api.startup.StartupRow;
import com.riddleslabs.api.startup.StartupTone;

import com.riddleslabs.paper.api.PaperCore;
import com.riddleslabs.paper.api.PaperModule;
import com.riddleslabs.paper.api.command.CommandRoute;
import org.bukkit.command.TabExecutor;
import java.util.concurrent.ConcurrentHashMap;
import com.riddleslabs.trails.api.QuestTrailAPI;
import com.riddleslabs.trails.quest.QuestTrailService;
import com.riddleslabs.trails.quest.QuestTrailCommand;
import org.bukkit.plugin.ServicePriority;
import com.riddleslabs.trails.animation.AnimationEngine;
import com.riddleslabs.trails.animation.AnimationCommand;
import com.riddleslabs.trails.api.animation.TrailAnimationAPI;

public final class RiddlesTrailsPlugin extends JavaPlugin implements Listener {
    private static final Set<String> RESERVED = Set.of("help", "list", "stop", "go", "set", "record", "pause", "resume", "save", "cancel", "delete", "reload");
    private RouteStore store;
    private com.riddleslabs.api.files.FileCoordinator persistence;
    private boolean folia;
    private TrailService trails;
    private QuestTrailService questTrails;
    private AnimationEngine animations;
    private volatile TrailSettings settings;
    private PaperModule module;
    private TrailMessages messages;
    private TrailMetrics metrics;
    public TrailMetrics metrics() { return metrics; }
    private ReloadableConfiguration<TrailConfiguration> configuration;
    private final java.util.concurrent.atomic.AtomicBoolean reloading = new java.util.concurrent.atomic.AtomicBoolean();
    private record TrailConfiguration(TrailSettings trails, QuestTrailService.Config quests) {}
    private final Map<UUID, Recording> recordings = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        PaperCore core = getServer().getServicesManager().load(PaperCore.class);
        if (core == null) {
            getLogger().severe("RiddlesLabsCore is required and must be enabled before Riddles Trails.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            PaperModule owned = core.register(this, ModuleDescriptor.builder("riddlestrails")
                    .displayName("Riddles Trails").version(getPluginMeta().getVersion())
                    .requiredApi("0.6.13", "0.7.0").build());
            module = owned;
            metrics = new TrailMetrics(owned);
            metrics.start();
            folia = core.platform().folia();
            persistence = owned.fileCoordinator("trail-persistence",256,(path,ready) -> {
                if (!ready) metrics.count(TrailMetrics.Counter.FILE_FAILURES);
                if(owned.isOpen()) owned.facilities().health().update(path.startsWith("destinations") ? "routes" : "preferences",
                        ready ? HealthStatus.HEALTHY : HealthStatus.DEGRADED,ready ? "FILE_READY" : "FILE_OPERATION_FAILED");
            });
            messages = new TrailMessages(this, owned);
            owned.own(recordings::clear);
            installDefault("config.yml");
            installDefault("animations.yml");
            configuration = owned.configurations().open("config.yml", candidate -> {
                TrailSettings.upgradeParticleBudget(candidate);
                return new TrailConfiguration(TrailSettings.read(candidate), QuestTrailService.Config.read(candidate));
            });
            settings = configuration.snapshot().value().trails();
            store = new RouteStore(persistence);
            persistence.blocking(() -> { store.load(); return null; });
            animations = new AnimationEngine(this, owned.configurations(), persistence, metrics);
            owned.own(animations::shutdown);
            TrailParticles.initialize(animations);
            owned.own(() -> TrailParticles.initialize(null));
            trails = new TrailService(owned.scheduler(), messages);
            owned.own(trails::shutdown);
            owned.listeners().register(animations);
            owned.listeners().register(this);
            publish(TrailAnimationAPI.class, animations, "animations");
            bind("riddlestrailanimation", "riddlestrails.animation.admin", new AnimationCommand(animations, messages));
            bind("riddlestrails", "riddlestrails.use", this);
            bind("riddlestrailslanguage", "riddlestrails.use", messages);
            if (!folia) {
                questTrails = new QuestTrailService(this, owned.scheduler(), store, () -> settings,
                        () -> configuration.snapshot().value().quests(), messages);
                owned.own(questTrails::shutdown);
                owned.listeners().register(questTrails);
                publish(QuestTrailAPI.class, questTrails, "quests");
                getServer().getOnlinePlayers().forEach(questTrails::restore);
            }
            bind("riddlesquesttrail", "riddlestrails.quest.admin", new QuestTrailCommand(questTrails, messages));
            owned.facilities().placeholders().register("destinations", context -> Integer.toString(store.all().size()));
            owned.facilities().placeholders().register("active", context -> Integer.toString(trails.activeCount()));
            owned.facilities().health().update("quests", questTrails == null ? HealthStatus.DEGRADED : HealthStatus.HEALTHY,
                    questTrails == null ? "PAPER_ONLY" : "AVAILABLE");
            owned.facilities().health().update("runtime", HealthStatus.HEALTHY, "READY");
            metrics.ready(() -> {
                var routes = store.all();
                var values = new LinkedHashMap<String, Double>();
                values.put("destinations", (double) routes.size());
                values.put("route_points", (double) routes.values().stream().mapToLong(r -> r.points().size()).sum());
                values.put("teleport_segments", (double) routes.values().stream().mapToLong(r -> r.teleports().size()).sum());
                values.put("active_trails", (double) trails.activeCount());
                values.put("animations", (double) animations.animations().size());
                values.put("custom_animations", (double) animations.customCount());
                values.put("recordings", (double) recordings.size());
                values.put("languages", (double) messages.localeCount());
                values.put("quest_available", questTrails == null ? 0d : 1d);
                values.put("active_quest_trails", questTrails == null ? 0d : (double) questTrails.activeCount());
                return values;
            });
            getLogger().info("FastStats Trails: " + metrics.status() + " | 26 aggregate metrics");
            owned.markReady();
            owned.startupBanner(List.of("R I D D L E S   L A B S", "T R A I L S"), List.of(
                    new StartupRow("Core API", core.apiVersion().toString(), StartupTone.INFO),
                    new StartupRow("Runtime", "READY", StartupTone.READY),
                    new StartupRow("Languages", Integer.toString(messages.localeCount()) + " (Core)", StartupTone.INFO),
                    new StartupRow("Destinations", Integer.toString(store.all().size()), StartupTone.INFO),
                    new StartupRow("Animations", Integer.toString(animations.animations().size()), StartupTone.INFO),
                    new StartupRow("Quest guidance", questTrails == null ? "Paper only" : "READY", StartupTone.INFO),
                    new StartupRow("FastStats", metrics.status(), StartupTone.INFO)));
        } catch (Exception | LinkageError failure) {
            if (metrics != null) metrics.failure(TrailMetrics.Counter.STARTUP_FAILURES, failure);
            getLogger().severe("Could not enable Riddles Trails: " + failure.getMessage());
            if (module != null) module.close();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void installDefault(String name) throws Exception {
        try (var input = Objects.requireNonNull(getResource(name), name)) {
            module.configurations().installDefaults(name, input.readAllBytes());
        }
    }

    private <T> void publish(Class<T> type, T service, String name) {
        getServer().getServicesManager().register(type, service, this, ServicePriority.Normal);
        module.own(() -> getServer().getServicesManager().unregister(type, service));
        module.publish(ServiceKey.of("riddlestrails", name, type), service);
    }

    private void bind(String name, String permission, TabExecutor executor) {
        var metadata = new org.bukkit.configuration.file.YamlConfiguration();
        try (var reader = new java.io.InputStreamReader(
                Objects.requireNonNull(getResource("commands.yml"), "Missing commands.yml"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            metadata.load(reader);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load bundled command metadata", failure);
        }
        var definition = Objects.requireNonNull(metadata.getConfigurationSection("commands." + name), name);
        // Metadata for the existing executors; Core owns the native Paper registration.
        var command = new Command(name, Objects.requireNonNull(definition.getString("description")),
                Objects.requireNonNull(definition.getString("usage")), definition.getStringList("aliases")) {
            @Override public boolean execute(CommandSender sender, String label, String[] arguments) {
                return executor.onCommand(sender, this, label, arguments);
            }
        };
        module.commands().register(new com.riddleslabs.paper.api.command.LifecycleCommand(
                name, command.getDescription(), command.getAliases(),
                List.of(new CommandRoute("", permission, false, command.getUsage(),
                context -> executor.onCommand(context.sender(), command, context.label(), context.arguments().toArray(String[]::new)),
                context -> {
                    var suggestions = executor.onTabComplete(context.sender(), command, context.label(), context.arguments().toArray(String[]::new));
                    return suggestions == null ? List.of() : suggestions;
                }))), (sender, reason) -> messages.send(sender, switch (reason) {
                    case DENIED -> "errors.denied";
                    case TOO_MANY_ARGUMENTS -> "errors.too-many-arguments";
                    case FAILED -> "errors.failed";
                }));
    }

    @Override public void onDisable() {
        PaperModule closing = module;
        if (closing != null) closing.close();
        module = null;
    }

    private void reloadFromDisk(CommandSender sender) {
        if (!reloading.compareAndSet(false, true)) { messages.send(sender, "reload.busy"); return; }
        PaperModule owned = module;
        try {
            persistence.submit(() -> {
                messages.reloadNotifications();
                store.load();
                animations.reload();
                return configuration.reload().value().trails();
            }).whenComplete((loaded, failure) -> {
                if (failure != null) metrics.failure(TrailMetrics.Counter.RELOAD_FAILURES, failure);
                if (!owned.isOpen()) { reloading.set(false); return; }
                try {
                    // Apply shared state even if the requesting player disconnected during file I/O.
                    // Quest services only exist on Paper; this never accesses Folia entity/world state.
                    owned.scheduler().global(() -> {
                        try {
                            if (!owned.isOpen()) return;
                            if (failure == null) {
                                settings = loaded;
                                trails.shutdown();
                                if (questTrails != null) questTrails.reload();
                            } else getLogger().warning("Riddles Trails reload failed: " + failure.getClass().getSimpleName());
                            reply(owned, sender, failure == null
                                    ? "reload.success" : "reload.failed");
                        } finally { reloading.set(false); }
                    });
                } catch (IllegalStateException closed) { reloading.set(false); }
            });
        } catch (RuntimeException rejected) { reloading.set(false); metrics.failure(TrailMetrics.Counter.RELOAD_FAILURES, rejected); throw rejected; }
    }

    private void reply(PaperModule owned, CommandSender sender, String message) {
        if (!owned.isOpen()) return;
        if (sender instanceof Player player) owned.scheduler().entity(player, () -> messages.send(player, message), () -> {});
        else messages.send(sender, message); // Called on the global scheduler for console senders.
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (module == null || !module.isOpen()) return true;
        if (!sender.hasPermission("riddlestrails.use") && !sender.hasPermission("riddlestrails.admin")) {
            messages.send(sender, "errors.denied"); return true;
        }
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            if (action.equals("help")) { help(sender); return true; }
            if (action.equals("list")) {
                if (store.all().isEmpty()) messages.send(sender, "destination.empty");
                else messages.list(sender, "destination.list", "destinations", new TreeSet<>(store.all().keySet()));
                return true;
            }
            if (Set.of("set", "record", "pause", "resume", "save", "cancel", "delete", "reload").contains(action)
                    && !sender.hasPermission("riddlestrails.admin")) {
                messages.send(sender, "errors.admin"); return true;
            }
            if (action.equals("reload")) {
                reloadFromDisk(sender); return true;
            }
            if (action.equals("delete")) {
                if (args.length != 3 || !args[2].equals("DELETE")) {
                    messages.send(sender, "destination.confirm-delete"); return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                var deletion = persistence.submit(() -> {
                    boolean deleted=store.delete(id);
                    if(deleted) trails.stopDestination(id);
                    return deleted;
                });
                messages.after(sender, deletion, () -> {
                    if(deletion.toCompletableFuture().getNow(false)) messages.send(sender,"destination.deleted","id",id);
                    else messages.send(sender,"destination.not-found");
                });
                return true;
            }
            if (!(sender instanceof Player player)) { messages.send(sender, "errors.players-only"); return true; }
            Recording active=recordings.get(player.getUniqueId());
            if(active != null && active.saving && Set.of("record","pause","resume","save","cancel").contains(action)) {
                messages.send(player,"record.saving"); return true;
            }
            switch (action) {
                case "stop" -> { trails.stop(player.getUniqueId()); messages.send(player, "trail.stopped"); }
                case "set" -> {
                    String id = newId(args);
                    if (id == null) { messages.send(player, "destination.set-usage"); break; }
                    Route route=new Route(id, player.getWorld().getName(), List.of(TrailService.point(player.getLocation())));
                    messages.after(player,persistence.submit(() -> { saveNew(route); return null; }),
                            () -> messages.send(player,"destination.saved","id",id));
                }
                case "record" -> {
                    String id = newId(args);
                    if (id == null) { messages.send(player, "record.usage"); break; }
                    if (recordings.containsKey(player.getUniqueId())) { messages.send(player, "record.active"); break; }
                    if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) { messages.send(player, "record.on-foot"); break; }
                    trails.stop(player.getUniqueId());
                    recordings.put(player.getUniqueId(), new Recording(id, player.getWorld().getName(), TrailService.point(player.getLocation())));
                    messages.send(player, "record.started", "id", id);
                }
                case "pause" -> {
                    Recording rec = recording(player);
                    if (rec != null) { rec.paused = true; messages.send(player, "record.paused"); }
                }
                case "resume" -> {
                    Recording rec = recording(player);
                    if (rec != null && canContinue(player, rec)) { rec.paused = false; messages.send(player, "record.resumed"); }
                }
                case "save" -> {
                    Recording rec = recording(player);
                    if (rec == null || !canContinue(player, rec)) break;
                    Route.Point end = TrailService.point(player.getLocation());
                    List<Route.Point> points = new ArrayList<>(rec.points);
                    if (points.getLast().distanceSquared(end) > .01) points.add(end);
                    if (points.size() < 2) { messages.send(player, "record.too-short"); break; }
                    if (points.size() > settings.maxPoints()) { messages.send(player, "record.save-limit"); break; }
                    Route route=new Route(rec.id, rec.world, points, List.copyOf(rec.teleports));
                    boolean wasPaused=rec.paused;
                    rec.saving=true;rec.paused=true;
                    messages.after(player,persistence.submit(() -> { saveNew(route); return null; }), () -> {
                        recordings.remove(player.getUniqueId(),rec);
                        messages.send(player,"record.saved","id",rec.id,"count",points.size());
                    }, () -> {
                        if(recordings.get(player.getUniqueId())==rec) { rec.saving=false;rec.paused=wasPaused; }
                    });
                }
                case "cancel" -> { recordings.remove(player.getUniqueId()); messages.send(player, "record.cancelled"); }
                default -> {
                    String id = action.equals("go") ? (args.length == 2 ? args[1].toLowerCase(Locale.ROOT) : "") : action;
                    Route destination = store.get(id);
                    if (destination == null) messages.send(player, "destination.unknown");
                    else trails.start(player, destination, settings);
                }
            }
        } catch (Exception ex) {
            metrics.failure(TrailMetrics.Counter.COMMAND_FAILURES, ex);
            getLogger().warning("Trail command failed: " + ex.getMessage());
            messages.send(sender, "errors.failed");
        }
        return true;
    }

    private synchronized void saveNew(Route route) throws java.io.IOException {
        if (store.get(route.id()) != null) throw new IllegalArgumentException("That destination already exists; use a different ID.");
        store.save(route);
    }
    private String newId(String[] args) {
        if (args.length != 2) return null;
        String id = args[1].toLowerCase(Locale.ROOT);
        try { id = TrailArguments.routeId(id); } catch (IllegalArgumentException invalid) { return null; }
        return !RESERVED.contains(id) && store.get(id) == null ? id : null;
    }
    private Recording recording(Player p) {
        Recording rec = recordings.get(p.getUniqueId());
        if (rec == null) messages.send(p, "record.none");
        return rec;
    }
    private boolean canContinue(Player p, Recording rec) {
        if (!p.getWorld().getName().equals(rec.world) || p.isFlying() || p.isGliding() || p.isInsideVehicle()
                || rec.points.getLast().distanceSquared(TrailService.point(p.getLocation())) > settings.maxStep() * settings.maxStep()) {
            messages.send(p, "record.return", "world", rec.world); return false;
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true) public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) return;
        Recording rec = recordings.get(event.getPlayer().getUniqueId());
        if (rec == null || rec.paused || event.getTo() == null || !event.hasChangedPosition()) return;
        Player p = event.getPlayer();
        if (!canContinue(p, rec) || !event.getTo().getWorld().getName().equals(rec.world)
                || rec.points.getLast().distanceSquared(TrailService.point(event.getTo())) > settings.maxStep() * settings.maxStep()) {
            rec.paused = true;
            messages.send(p, "record.move-paused");
            return;
        }
        if (rec.points.size() >= settings.maxPoints()) {
            rec.paused = true; messages.send(p, "record.limit"); return;
        }
        Route.Point point = TrailService.point(event.getTo());
        if (rec.points.getLast().distanceSquared(point) >= settings.recordSpacing() * settings.recordSpacing()) rec.points.add(point);
    }
    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        trails.rejoin(event.getPlayer().getUniqueId());
        Recording rec = recordings.get(event.getPlayer().getUniqueId());
        if (rec == null || event.getTo() == null) return;
        switch (rec.teleport(event.getTo().getWorld().getName(), TrailService.point(event.getFrom()),
                TrailService.point(event.getTo()), settings.maxPoints())) {
            case WORLD_CHANGED -> messages.send(event.getPlayer(), "record.world-changed", "world", rec.world);
            case POINT_LIMIT -> messages.send(event.getPlayer(), "record.teleport-limit");
            default -> { }
        }
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { trails.rejoin(event.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { trails.stop(event.getPlayer().getUniqueId()); recordings.remove(event.getPlayer().getUniqueId()); }

    private void help(CommandSender sender) {
        messages.send(sender, "help.user");
        if (sender.hasPermission("riddlestrails.admin")) messages.send(sender, "help.admin");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (module == null || !module.isOpen()) return List.of();
        if (!sender.hasPermission("riddlestrails.use") && !sender.hasPermission("riddlestrails.admin")) return List.of();
        Set<String> candidates = new TreeSet<>();
        if (args.length == 1) {
            candidates.addAll(store.all().keySet()); candidates.addAll(List.of("help", "list", "stop", "go"));
            if (sender.hasPermission("riddlestrails.admin")) candidates.addAll(RESERVED);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("go") || (args[0].equalsIgnoreCase("delete") && sender.hasPermission("riddlestrails.admin")))) {
            candidates.addAll(store.all().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(s -> s.startsWith(prefix)).toList();
    }
    static final class Recording {
        final String id, world;
        final List<Route.Point> points = new ArrayList<>();
        final List<Integer> teleports = new ArrayList<>();
        boolean paused, saving;
        Recording(String id, String world, Route.Point start) { this.id = id; this.world = world; points.add(start); }

        enum TeleportResult { CONTINUED, ALREADY_PAUSED, WORLD_CHANGED, POINT_LIMIT }

        TeleportResult teleport(String targetWorld, Route.Point from, Route.Point to, int maxPoints) {
            if (paused) return TeleportResult.ALREADY_PAUSED;
            if (!world.equals(targetWorld)) { paused = true; return TeleportResult.WORLD_CHANGED; }
            List<Route.Point> additions = new ArrayList<>(2);
            Route.Point last = points.getLast();
            if (last.distanceSquared(from) > .01) { additions.add(from); last = from; }
            boolean moved = last.distanceSquared(to) > .01;
            if (moved) additions.add(to);
            if (points.size() + additions.size() > maxPoints) { paused = true; return TeleportResult.POINT_LIMIT; }
            points.addAll(additions);
            if (moved) teleports.add(points.size() - 1);
            return TeleportResult.CONTINUED;
        }
    }
}
