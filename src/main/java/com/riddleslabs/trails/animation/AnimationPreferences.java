package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.api.animation.AnimationStyle;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Durable UUID preferences. Publish changes only after their atomic file replacement succeeds. */
final class AnimationPreferences {
    private record Preference(String animation, AnimationStyle style) {}
    private final com.riddleslabs.api.files.FileCoordinator persistence;
    private static final String FILE = "animation-preferences.yml";
    private volatile Map<UUID, Preference> entries = Map.of();

    AnimationPreferences(com.riddleslabs.api.files.FileCoordinator persistence) {
        this.persistence = persistence;
        String content = persistence.read(FILE);
        if (content == null) { persistence.acceptRead(FILE); return; }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(content);
            if (yaml.getInt("version") != 1) throw new IllegalArgumentException("Unsupported preferences version");
            ConfigurationSection players = Objects.requireNonNull(yaml.getConfigurationSection("players"), "players");
            Map<UUID, Preference> loaded = new HashMap<>();
            for (String key : players.getKeys(false)) {
                ConfigurationSection player = Objects.requireNonNull(players.getConfigurationSection(key), key);
                String animation = player.getString("animation");
                ConfigurationSection style = player.getConfigurationSection("style");
                loaded.put(UUID.fromString(key), new Preference(animation == null ? null : AnimationEngine.id(animation),
                        style == null ? null : AnimationConfig.parse(style, null)));
            }
            entries = Map.copyOf(loaded);
            persistence.acceptRead(FILE);
        } catch (Exception ex) {
            persistence.health(FILE, false);
            throw new IllegalStateException("Could not load animation preferences from " + FILE, ex);
        }
    }

    String selection(UUID player, String fallback) {
        Preference entry = entries.get(Objects.requireNonNull(player));
        return entry == null || entry.animation == null ? fallback : entry.animation;
    }

    AnimationStyle style(UUID player, AnimationStyle fallback) {
        Preference entry = entries.get(Objects.requireNonNull(player));
        return entry == null || entry.style == null ? fallback : entry.style;
    }

    synchronized void select(UUID player, String animation) {
        Preference previous = entries.get(Objects.requireNonNull(player));
        update(player, new Preference(animation, previous == null ? null : previous.style));
    }

    synchronized void setStyle(UUID player, AnimationStyle style) {
        Preference previous = entries.get(Objects.requireNonNull(player));
        update(player, new Preference(previous == null ? null : previous.animation, style));
    }

    private void update(UUID player, Preference preference) {
        Map<UUID, Preference> updated = new HashMap<>(entries);
        if (preference.animation == null && preference.style == null) updated.remove(player);
        else updated.put(player, preference);
        if (updated.equals(entries)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        ConfigurationSection players = yaml.createSection("players");
        updated.forEach((uuid, value) -> {
            ConfigurationSection section = players.createSection(uuid.toString());
            section.set("animation", value.animation);
            if (value.style != null) writeStyle(section.createSection("style"), value.style);
        });
        persistence.write(FILE, yaml.saveToString());
        entries = Map.copyOf(updated);
    }

    private static void writeStyle(ConfigurationSection section, AnimationStyle style) {
        section.set("particle", style.particle().name());
        section.set("palette", style.palette().stream().map(color -> String.format(Locale.ROOT, "#%06X", color.asRGB())).toList());
        section.set("period-seconds", style.periodSeconds());
        section.set("scale", style.scale());
        section.set("width", style.width());
        section.set("height", style.height());
        section.set("brightness", style.brightness());
        section.set("base-brightness", style.baseBrightness());
        section.set("size", style.size());
        section.set("duration-ticks", style.durationTicks());
        section.set("parameters", style.parameters());
    }
}
