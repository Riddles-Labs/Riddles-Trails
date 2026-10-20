package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.api.QuestTrailAPI;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class QuestTrailCommand implements TabExecutor {
    private final QuestTrailAPI api;
    public QuestTrailCommand(QuestTrailAPI api) { this.api = api; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("riddlestrails.quest.admin")) {
            sender.sendMessage("§cYou do not have permission to manage quest trails."); return true;
        }
        if (api == null) { sender.sendMessage("§cQuest trails are unavailable. This extension requires Paper and valid configuration; check the startup log."); return true; }
        if (args.length < 2) return usage(sender);
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            try { player = Bukkit.getPlayer(UUID.fromString(args[1])); }
            catch (IllegalArgumentException ignored) { }
        }
        if (player == null) { sender.sendMessage("§cPlayer must be online (use their exact name or UUID)."); return true; }
        try {
            if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
                api.clear(player.getUniqueId());
                sender.sendMessage("§aQuest trail cleared for " + player.getName()); return true;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
                sender.sendMessage(api.hasTrail(player.getUniqueId()) ? "§aQuest guidance assigned (it may be paused)." : "§eNo quest guidance assigned."); return true;
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
            sender.sendMessage("§aQuest guidance assigned for " + player.getName() + ". Connecting to a configured route toward the destination.");
        } catch (IllegalArgumentException | IllegalStateException ex) { sender.sendMessage("§c" + ex.getMessage()); }
        return true;
    }

    private static boolean usage(CommandSender sender) {
        sender.sendMessage("§e/riddlesquesttrail show <player> npc <id>");
        sender.sendMessage("§e/riddlesquesttrail show <player> location <world> <x> <y> <z>");
        sender.sendMessage("§e/riddlesquesttrail <clear|status> <player>");
        return true;
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
