package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.api.animation.TrailAnimationAPI;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class AnimationCommand implements TabExecutor {
    private final TrailAnimationAPI api;
    public AnimationCommand(TrailAnimationAPI api) { this.api=api; }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.hasPermission("riddlestrails.animation.admin")) {sender.sendMessage("§cYou cannot manage trail animations.");return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("list")) {sender.sendMessage("§bAnimations: §f"+String.join(", ",api.animations()));return true;}
        if(args.length<2) return usage(sender);
        Player target=Bukkit.getPlayerExact(args[1]);
        if(target==null) try {target=Bukkit.getPlayer(UUID.fromString(args[1]));} catch(IllegalArgumentException ignored) {}
        if(target==null) {sender.sendMessage("§cPlayer must be online.");return true;}
        try {
            if(args.length==3&&args[0].equalsIgnoreCase("set")) api.select(target.getUniqueId(),args[2]);
            else if(args.length==2&&args[0].equalsIgnoreCase("reset")) api.reset(target.getUniqueId());
            else return usage(sender);
            sender.sendMessage("§aAnimation for "+target.getName()+": "+api.selected(target.getUniqueId()));
        } catch(IllegalArgumentException|IllegalStateException ex) {sender.sendMessage("§c"+ex.getMessage());}
        return true;
    }
    private boolean usage(CommandSender sender) {sender.sendMessage("§e/riddlestrailanimation list | set <player> <id> | reset <player>");return true;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!sender.hasPermission("riddlestrails.animation.admin")) return List.of();
        Collection<String> choices=switch(args.length) {
            case 1 -> List.of("list","set","reset");
            case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            case 3 -> api.animations();
            default -> List.of();
        };
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
