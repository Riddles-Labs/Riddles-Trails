package com.riddleslabs.trails;

import com.riddleslabs.paper.api.PaperModule;
import com.riddleslabs.paper.api.notification.NotificationProfile;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

final class TrailNotifications {
    private final JavaPlugin plugin;
    private final PaperModule module;
    private final Set<String> keys;
    private volatile Map<String,NotificationProfile> profiles=Map.of();
    TrailNotifications(JavaPlugin plugin, PaperModule module, Set<String> keys) {
        this.plugin=plugin;this.module=module;this.keys=Set.copyOf(keys);
    }
    void reload() throws Exception {
        try(var input=Objects.requireNonNull(plugin.getResource("notifications.yml"))) {
            profiles=module.notificationCatalog("notifications.yml",input.readAllBytes(),keys::contains).load();
        }
    }
    NotificationProfile profile(String key) {
        return profiles.getOrDefault(key,NotificationProfile.chat(key));
    }
}
