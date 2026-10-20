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
import java.time.Duration;
import com.riddleslabs.api.config.ReloadableConfiguration;
import com.riddleslabs.api.health.HealthStatus;
import com.riddleslabs.api.module.ModuleDescriptor;
import com.riddleslabs.api.service.ServiceKey;
import com.riddleslabs.api.startup.StartupRow;
import com.riddleslabs.api.startup.StartupTone;
import com.riddleslabs.api.telemetry.TelemetrySession;
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
    private TrailService trails;
    private QuestTrailService questTrails;
    private AnimationEngine animations;
    private volatile TrailSettings settings;
    private PaperModule module;
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
                    .requiredApi("0.6.0", "0.7.0").build());
            module = owned;
            owned.own(recordings::clear);
            installDefault("config.yml");
            installDefault("animations.yml");
            configuration = owned.configurations().open("config.yml", candidate -> {
                TrailSettings.upgradeParticleBudget(candidate);
                return new TrailConfiguration(TrailSettings.read(candidate), QuestTrailService.Config.read(candidate));
            });
            settings = configuration.snapshot().value().trails();
            store = new RouteStore(getDataFolder().toPath().resolve("destinations.yml"));
            store.load();
            animations = new AnimationEngine(this, owned.configurations());
            owned.own(animations::shutdown);
            TrailParticles.initialize(animations);
            owned.own(() -> TrailParticles.initialize(null));
            trails = new TrailService(owned.scheduler());
            owned.own(trails::shutdown);
            owned.listeners().register(animations);
            owned.listeners().register(this);
            publish(TrailAnimationAPI.class, animations, "animations");
            bind("riddlestrailanimation", "riddlestrails.animation.admin", new AnimationCommand(animations));
            bind("riddlestrails", "riddlestrails.use", this);
            if (!isFolia()) {
                questTrails = new QuestTrailService(this, owned.scheduler(), store, () -> settings,
                        () -> configuration.snapshot().value().quests());
                owned.own(questTrails::shutdown);
                owned.listeners().register(questTrails);
                publish(QuestTrailAPI.class, questTrails, "quests");
                getServer().getOnlinePlayers().forEach(questTrails::restore);
            }
            bind("riddlesquesttrail", "riddlestrails.quest.admin", new QuestTrailCommand(questTrails));
            owned.facilities().placeholders().register("destinations", context -> Integer.toString(store.all().size()));
            owned.facilities().placeholders().register("active", context -> Integer.toString(trails.activeCount()));
            owned.facilities().health().update("runtime", HealthStatus.HEALTHY, "READY");
            TelemetrySession telemetry = owned.telemetry(Set.of("destinations", "active_trails", "animations", "recordings"));
            owned.scheduler().asyncRepeating(Duration.ofSeconds(30), Duration.ofSeconds(30), () ->
                    telemetry.publish(Map.of("destinations", (double) store.all().size(),
                            "active_trails", (double) trails.activeCount(), "animations", (double) animations.animations().size(),
                            "recordings", (double) recordings.size())));
            owned.markReady();
            owned.startupBanner(List.of("R I D D L E S   L A B S", "T R A I L S"), List.of(
                    new StartupRow("Core API", core.apiVersion().toString(), StartupTone.INFO),
                    new StartupRow("Runtime", "READY", StartupTone.READY),
                    new StartupRow("Destinations", Integer.toString(store.all().size()), StartupTone.INFO),
                    new StartupRow("Animations", Integer.toString(animations.animations().size()), StartupTone.INFO),
                    new StartupRow("Quest guidance", questTrails == null ? "Paper only" : "READY", StartupTone.INFO),
                    new StartupRow("FastStats", telemetry.status().name(), StartupTone.INFO)));
        } catch (Exception | LinkageError failure) {
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
        var command = Objects.requireNonNull(getCommand(name), name);
        module.commands().bind(name, List.of(new CommandRoute("", permission, false, "/" + name,
                context -> executor.onCommand(context.sender(), command, context.label(), context.arguments().toArray(String[]::new)),
                context -> {
                    var suggestions = executor.onTabComplete(context.sender(), command, context.label(), context.arguments().toArray(String[]::new));
                    return suggestions == null ? List.of() : suggestions;
                })));
    }

    @Override public void onDisable() {
        PaperModule closing = module;
        if (closing != null) closing.close();
        module = null;
    }

    private void reloadFromDisk(CommandSender sender) {
        if (!reloading.compareAndSet(false, true)) { sender.sendMessage("§eA reload is already running."); return; }
        PaperModule owned = module;
        try {
            owned.facilities().work().submit(() -> {
                store.load();
                animations.reload();
                return configuration.reload().value().trails();
            }).whenComplete((loaded, failure) -> {
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
                                    ? "§aDestinations and configuration reloaded; ordinary trails stopped."
                                    : "§cReload failed. Check Riddles Trails configuration and the console.");
                        } finally { reloading.set(false); }
                    });
                } catch (IllegalStateException closed) { reloading.set(false); }
            });
        } catch (RuntimeException rejected) { reloading.set(false); throw rejected; }
    }

    private void reply(PaperModule owned, CommandSender sender, String message) {
        if (!owned.isOpen()) return;
        if (sender instanceof Player player) owned.scheduler().entity(player, () -> player.sendMessage(message), () -> {});
        else sender.sendMessage(message); // Called on the global scheduler for console senders.
    }

    private static boolean isFolia() {
        try { Class.forName("io.papermc.paper.threadedregions.RegionizedServer"); return true; }
        catch (ClassNotFoundException ignored) { return false; }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (module == null || !module.isOpen()) return true;
        if (!sender.hasPermission("riddlestrails.use") && !sender.hasPermission("riddlestrails.admin")) {
            sender.sendMessage("§cYou do not have permission to use trails."); return true;
        }
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            if (action.equals("help")) { help(sender); return true; }
            if (action.equals("list")) {
                sender.sendMessage("§bDestinations: §f" + (store.all().isEmpty() ? "None yet." : String.join(", ", new TreeSet<>(store.all().keySet()))));
                return true;
            }
            if (Set.of("set", "record", "pause", "resume", "save", "cancel", "delete", "reload").contains(action)
                    && !sender.hasPermission("riddlestrails.admin")) {
                sender.sendMessage("§cOnly trail administrators can do that."); return true;
            }
            if (action.equals("reload")) {
                reloadFromDisk(sender); return true;
            }
            if (action.equals("delete")) {
                if (args.length != 3 || !args[2].equals("DELETE")) {
                    sender.sendMessage("§eConfirm with /riddlestrails delete <id> DELETE"); return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (store.delete(id)) { trails.stopDestination(id); sender.sendMessage("§aDeleted " + id); }
                else sender.sendMessage("§cDestination not found.");
                return true;
            }
            if (!(sender instanceof Player player)) { sender.sendMessage("This command must be run in game."); return true; }
            switch (action) {
                case "stop" -> { trails.stop(player.getUniqueId()); player.sendMessage("§eTrail stopped."); }
                case "set" -> {
                    String id = newId(args);
                    if (id == null) { player.sendMessage("§cUse /riddlestrails set <new-id>; IDs use letters, numbers, - and _. Existing IDs cannot be overwritten."); break; }
                    saveNew(new Route(id, player.getWorld().getName(), List.of(TrailService.point(player.getLocation()))));
                    player.sendMessage("§aWaypoint saved: §f" + id + "§7. Players can use /riddlestrails " + id);
                }
                case "record" -> {
                    String id = newId(args);
                    if (id == null) { player.sendMessage("§cUse /riddlestrails record <new-id>. Choose an unused destination ID."); break; }
                    if (recordings.containsKey(player.getUniqueId())) { player.sendMessage("§cSave or cancel your current recording first."); break; }
                    if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) { player.sendMessage("§cStart recording on foot."); break; }
                    trails.stop(player.getUniqueId());
                    recordings.put(player.getUniqueId(), new Recording(id, player.getWorld().getName(), TrailService.point(player.getLocation())));
                    player.sendMessage("§aRecording §f" + id + "§a. Walk the route, then /riddlestrails save at the destination.");
                }
                case "pause" -> {
                    Recording rec = recording(player);
                    if (rec != null) { rec.paused = true; player.sendMessage("§eRecording paused."); }
                }
                case "resume" -> {
                    Recording rec = recording(player);
                    if (rec != null && canContinue(player, rec)) { rec.paused = false; player.sendMessage("§aRecording resumed."); }
                }
                case "save" -> {
                    Recording rec = recording(player);
                    if (rec == null || !canContinue(player, rec)) break;
                    Route.Point end = TrailService.point(player.getLocation());
                    List<Route.Point> points = new ArrayList<>(rec.points);
                    if (points.getLast().distanceSquared(end) > .01) points.add(end);
                    if (points.size() < 2) { player.sendMessage("§cWalk farther before saving, or use /riddlestrails set for a simple waypoint."); break; }
                    if (points.size() > settings.maxPoints()) { player.sendMessage("§cPoint limit reached. Return to the last recorded point to save."); break; }
                    saveNew(new Route(rec.id, rec.world, points, rec.teleports));
                    recordings.remove(player.getUniqueId());
                    player.sendMessage("§aRoute saved: §f" + rec.id + " §7(" + points.size() + " points)");
                }
                case "cancel" -> { recordings.remove(player.getUniqueId()); player.sendMessage("§eRecording cancelled. Saved destinations are unchanged."); }
                default -> {
                    String id = action.equals("go") ? (args.length == 2 ? args[1].toLowerCase(Locale.ROOT) : "") : action;
                    Route destination = store.get(id);
                    if (destination == null) player.sendMessage("§cUnknown destination. Use /riddlestrails list.");
                    else trails.start(player, destination, settings);
                }
            }
        } catch (Exception ex) {
            getLogger().warning("Trail command failed: " + ex.getMessage());
            sender.sendMessage("§cCould not complete that action. " + ex.getMessage());
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
        return id.matches("[a-z0-9][a-z0-9_-]{0,47}") && !RESERVED.contains(id) && store.get(id) == null ? id : null;
    }
    private Recording recording(Player p) {
        Recording rec = recordings.get(p.getUniqueId());
        if (rec == null) p.sendMessage("§cNo active recording. Use /riddlestrails record <id>.");
        return rec;
    }
    private boolean canContinue(Player p, Recording rec) {
        if (!p.getWorld().getName().equals(rec.world) || p.isFlying() || p.isGliding() || p.isInsideVehicle()
                || rec.points.getLast().distanceSquared(TrailService.point(p.getLocation())) > settings.maxStep() * settings.maxStep()) {
            p.sendMessage("§cReturn on foot near the last recorded point in " + rec.world + " before resuming or saving."); return false;
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
            p.sendMessage("§eRecording paused. Return near the last recorded point and use /riddlestrails resume.");
            return;
        }
        if (rec.points.size() >= settings.maxPoints()) {
            rec.paused = true; p.sendMessage("§eRecording point limit reached. Use /riddlestrails save or /riddlestrails cancel."); return;
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
            case WORLD_CHANGED -> event.getPlayer().sendMessage("§eRecording paused: routes cannot span worlds. Return to " + rec.world + " before /riddlestrails resume.");
            case POINT_LIMIT -> event.getPlayer().sendMessage("§eRecording point limit reached. Return to the last recorded point to save, or /riddlestrails cancel.");
            default -> { }
        }
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { trails.rejoin(event.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { trails.stop(event.getPlayer().getUniqueId()); recordings.remove(event.getPlayer().getUniqueId()); }

    private void help(CommandSender sender) {
        sender.sendMessage("§bRiddles Trails §7— /riddlestrails <destination>, /riddlestrails list, /riddlestrails stop");
        if (sender.hasPermission("riddlestrails.admin")) {
            sender.sendMessage("§7/riddlestrails set <id> — save your position as a waypoint");
            sender.sendMessage("§7/riddlestrails record <id> — walk a route toward its destination");
            sender.sendMessage("§7/riddlestrails pause | resume | save | cancel");
            sender.sendMessage("§7/riddlestrails delete <id> DELETE | /riddlestrails reload");
        }
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
        boolean paused;
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
