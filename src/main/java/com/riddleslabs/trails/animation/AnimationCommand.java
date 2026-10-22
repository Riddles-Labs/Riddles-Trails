package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.api.animation.TrailAnimationAPI;
import java.util.*;
import com.riddleslabs.trails.TrailMessages;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class AnimationCommand implements TabExecutor {
    private final TrailAnimationAPI api;
    private final TrailMessages messages;
    public AnimationCommand(TrailAnimationAPI api, TrailMessages messages) { this.api = api; this.messages = messages; }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.hasPermission("riddlestrails.animation.admin")) {messages.send(sender, "animation.denied");return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("list")) {messages.list(sender, "animation.list", "animations", api.animations());return true;}
        if(args.length<2) return usage(sender);
        Player target=Bukkit.getPlayerExact(args[1]);
        if(target==null) try {target=Bukkit.getPlayer(UUID.fromString(args[1]));} catch(IllegalArgumentException ignored) {}
        if(target==null) {messages.send(sender, "errors.online");return true;}
        try {
            UUID targetId=target.getUniqueId();
            String targetName=target.getName();
            java.util.concurrent.CompletionStage<Void> saved;
            if(args.length==3&&args[0].equalsIgnoreCase("set")) saved=api.selectAsync(targetId,args[2]);
            else if(args.length==2&&args[0].equalsIgnoreCase("reset")) saved=api.resetAsync(targetId);
            else return usage(sender);
            messages.after(sender, saved, () -> messages.send(sender, "animation.selected", "player", targetName, "animation", api.selected(targetId)));
        } catch(IllegalArgumentException|IllegalStateException ex) {messages.failure(sender, "animation.failed", ex);}
        return true;
    }
    private boolean usage(CommandSender sender) {messages.send(sender, "animation.usage");return true;}
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
