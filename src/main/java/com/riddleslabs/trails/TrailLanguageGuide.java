package com.riddleslabs.trails;

import com.riddleslabs.api.config.ConfigurationDocumentation;
import java.util.*;
import org.bukkit.plugin.java.JavaPlugin;

final class TrailLanguageGuide {
    private TrailLanguageGuide() {}
    static String policy(JavaPlugin plugin, com.riddleslabs.paper.api.PaperModule module) throws Exception {
        var result=module.configurations().relocate("language.yml","languages/language.yml",null);
        if(result.status()==com.riddleslabs.api.config.FileRelocation.Status.CONFLICT)
            plugin.getLogger().warning("Both language policy files exist; preserving both and using language.yml until the conflict is resolved.");
        else if(result.status()==com.riddleslabs.api.config.FileRelocation.Status.MOVED)
            plugin.getLogger().info("Moved language settings to languages/language.yml.");
        return result.activePath();
    }
    static ConfigurationDocumentation documentation(String policy) {
        var entries=new LinkedHashMap<String, ConfigurationDocumentation.Entry>();
        Map.ofEntries(Map.entry("animation","Animations"),Map.entry("destination","Destinations"),
                Map.entry("errors","Errors"),Map.entry("help","Command help"),Map.entry("language","Language selection"),
                Map.entry("quest","Quest guidance"),Map.entry("record","Route recording"),
                Map.entry("reload","Reloading"),Map.entry("trail","Trail messages"))
                .forEach((key,title)->entries.put("messages."+key,new ConfigurationDocumentation.Entry(title,List.of(),Map.of(),"")));
        return new ConfigurationDocumentation("1", "Riddles Trails languages",
                List.of("Edit message text; keep keys and <var_name> placeholders.",
                        "Examples: <var_id> is a route ID; <var_player> is a player name; <var_count> is a count.",
                        "Choose a language in " + policy + ". Reload messages: /rtl reload.",
                        "Put personal notes outside managed blocks; originals are in .documentation-backups/."),entries);
    }
}
