package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.api.QuestTrailAPI;
import java.util.*;
import com.riddleslabs.trails.TrailMessages;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class QuestTrailCommand implements TabExecutor {
    private final QuestTrailAPI api;
    private final TrailMessages messages;
    public QuestTrailCommand(QuestTrailAPI api, TrailMessages messages) { this.api = api; this.messages = messages; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("riddlestrails.quest.admin")) {
            messages.send(sender, "quest.denied"); return true;
        }
        if (api == null) { messages.send(sender, "quest.unavailable"); return true; }
        if (args.length < 2) return usage(sender);
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            try { player = Bukkit.getPlayer(UUID.fromString(args[1])); }
            catch (IllegalArgumentException ignored) { }
        }
        if (player == null) { messages.send(sender, "quest.online"); return true; }
        try {
            if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
                api.clear(player.getUniqueId());
                messages.send(sender, "quest.cleared", "player", player.getName()); return true;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
                messages.send(sender, api.hasTrail(player.getUniqueId()) ? "quest.active" : "quest.none"); return true;
            }
            if (!args[0].equalsIgnoreCase("show")) return usage(sender);
            if (args.length == 4 && args[2].equalsIgnoreCase("npc")) {
                int npc = Integer.parseInt(args[3]);
                api.showToNpc(player.getUniqueId(), npc);
            } else if (args.length == 7 && args[2].equalsIgnoreCase("location")) {
                World world = Bukkit.getWorld(args[3]);
                if (world == null) throw new IllegalArgumentException("World is not loaded");
                api.showToLocation(player.getUniqueId(), new Location(world,
                        Double.parseDouble(args[4]), Double.parseDouble(args[5]), Double.parseDouble(args[6])));
            } else return usage(sender);
            messages.send(sender, "quest.assigned", "player", player.getName());
        } catch (IllegalArgumentException | IllegalStateException ex) { messages.failure(sender, "quest.failed", ex); }
        return true;
    }

    private boolean usage(CommandSender sender) {
        messages.send(sender, "quest.usage"); return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("riddlestrails.quest.admin")) return List.of();
        List<String> choices = switch (args.length) {
            case 1 -> List.of("show", "clear", "status");
            case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            case 3 -> args[0].equalsIgnoreCase("show") ? List.of("npc", "location") : List.of();
            case 4 -> args[2].equalsIgnoreCase("location") ? Bukkit.getWorlds().stream().map(World::getName).toList() : List.of();
            default -> List.of();
        };
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
