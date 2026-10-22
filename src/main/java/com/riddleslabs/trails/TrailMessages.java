package com.riddleslabs.trails;

import com.riddleslabs.paper.api.PaperModule;
import com.riddleslabs.paper.api.messaging.ModuleLanguages;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Product keys and command policy only; Core owns catalogs, rendering, preferences and reloads. */
public final class TrailMessages implements TabExecutor {
    private final JavaPlugin plugin;
    private final PaperModule module;
    private final ModuleLanguages languages;
    private final TrailNotifications notifications;
    private final java.util.concurrent.atomic.AtomicLong nextWarning = new java.util.concurrent.atomic.AtomicLong();
    public TrailMessages(JavaPlugin plugin, PaperModule module) throws Exception {
        this.plugin = plugin; this.module = module;
        var bundled = new LinkedHashMap<String, String>();
        for (String locale : List.of("en", "es")) try (var input = Objects.requireNonNull(plugin.getResource("languages/" + locale + ".yml"))) {
            bundled.put(locale, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        String policy = TrailLanguageGuide.policy(plugin,module);
        languages = module.languages("en", bundled, policy, TrailLanguageGuide.documentation(policy));
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.loadFromString(bundled.get("en"));
        var keys = new HashSet<String>();
        for (String path : yaml.getConfigurationSection("messages").getKeys(true))
            if (!yaml.isConfigurationSection("messages." + path)) keys.add(path);
        notifications = new TrailNotifications(plugin, module, keys);
        try { reloadNotifications(); }
        catch (Exception failure) { plugin.getLogger().warning("Invalid notifications.yml; using chat: " + failure.getMessage()); }
    }
    public void send(CommandSender sender, String key, Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Expected variable/value pairs");
        var values = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String)pairs[i], pairs[i + 1]);
        if (sender instanceof Player player) {
            try {
                module.notifications().send(player, languages, notifications.profile(key), values)
                        .whenComplete((ignored,failure)->{if(failure!=null) notificationFailure(failure);});
            } catch (RuntimeException failure) { notificationFailure(failure); }
        } else languages.send(sender, key, values);
    }
    public void reloadNotifications() throws Exception {
        try { notifications.reload(); module.facilities().health().update("notifications",
                com.riddleslabs.api.health.HealthStatus.HEALTHY,"READY"); }
        catch (Exception failure) {
            module.facilities().health().update("notifications",com.riddleslabs.api.health.HealthStatus.DEGRADED,"CONFIG_REJECTED");
            throw failure;
        }
    }
    private void notificationFailure(Throwable failure) {
        long now=System.nanoTime(),next=nextWarning.get();
        if(now>=next && nextWarning.compareAndSet(next,now+java.time.Duration.ofMinutes(1).toNanos()))
            plugin.getLogger().warning("Trail notification unavailable: "+failure.getClass().getSimpleName());
    }
    public void after(CommandSender sender, java.util.concurrent.CompletionStage<?> saved, Runnable success) {
        after(sender,saved,success,()->{});
    }
    public void after(CommandSender sender, java.util.concurrent.CompletionStage<?> saved, Runnable success, Runnable failed) {
        saved.whenComplete((ignored,failure)->reply(sender,()->{
            if (failure == null) success.run();
            else { failed.run(); plugin.getLogger().warning("Trail save failed: "+failure.getMessage()); send(sender,"errors.failed"); }
        }));
    }
    public String noticeIdentity(Player player, String key, Object... pairs) {
        return key + ":" + languages.report().revision() + ":" + languages.locale(player).toLanguageTag() + ":" + Arrays.toString(pairs);
    }
    public int localeCount() { return languages.report().fallbackKeys().size(); }
    public void list(CommandSender sender, String key, String variable, Collection<String> values) {
        var chunk = new ArrayList<String>();
        int length = 0;
        for (String value : values) {
            if (!chunk.isEmpty() && (chunk.size() >= 40 || length + value.length() + 2 > 6000)) {
                send(sender, key, variable, String.join(", ", chunk)); chunk.clear(); length = 0;
            }
            chunk.add(value); length += value.length() + 2;
        }
        if (!chunk.isEmpty()) send(sender, key, variable, String.join(", ", chunk));
    }
    public void failure(CommandSender sender, String key, RuntimeException failure) {
        plugin.getLogger().warning("Trail action failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        send(sender, key);
    }
    private void reply(CommandSender sender, Runnable action) {
        if (!module.isOpen()) return;
        try {
            if (sender instanceof Player player) module.scheduler().entity(player, action, () -> {});
            else module.scheduler().global(action);
        } catch (IllegalStateException closed) { /* Module shutdown cancels the response. */ }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("riddlestrails.use")) { send(sender, "errors.denied"); return true; }
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("list"))) {
            send(sender, "language.list", "locales", String.join(", ", languages.report().fallbackKeys().keySet()), "locale", languages.locale(sender).toLanguageTag());
            send(sender, "language.usage"); return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("riddlestrails.admin")) { send(sender, "errors.admin"); return true; }
            send(sender, "language.reloading");
            try {
                languages.reload().whenComplete((report, failure) -> {
                    if (failure != null) plugin.getLogger().warning("Language reload rejected; previous catalogs retained: " + failure.getMessage());
                    reply(sender, () -> {
                        if (failure == null) send(sender, "language.reloaded", "count", report.fallbackKeys().size(), "keys", report.keys());
                        else send(sender, "language.failed");
                    });
                });
            } catch (RuntimeException rejected) { send(sender, "language.failed"); }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            if (!sender.hasPermission("riddlestrails.admin")) { send(sender, "errors.admin"); return true; }
            var report = languages.report();
            send(sender, "language.status", "locale", report.serverLocale(), "revision", report.revision(), "keys", report.keys());
            report.fallbackKeys().forEach((locale, missing) -> send(sender, "language.coverage", "locale", locale, "count", missing.size()));
            return true;
        }
        if (args.length != 1) { send(sender, "language.usage"); return true; }
        if (!(sender instanceof Player player)) { send(sender, "errors.players-only"); return true; }
        if (args[0].equalsIgnoreCase("auto")) {
            languages.preference(player, null); send(sender, "language.automatic"); return true;
        }
        try {
            Locale locale = new Locale.Builder().setLanguageTag(args[0].replace('_', '-')).build();
            languages.preference(player, locale);
            send(sender, "language.selected", "locale", locale.toLanguageTag());
        } catch (IllegalArgumentException | IllegalStateException unavailable) { send(sender, "language.unavailable"); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("riddlestrails.use")) return List.of();
        var options = new TreeSet<>(languages.report().fallbackKeys().keySet()); options.addAll(List.of("auto", "list"));
        if (sender.hasPermission("riddlestrails.admin")) options.addAll(List.of("reload", "status"));
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
