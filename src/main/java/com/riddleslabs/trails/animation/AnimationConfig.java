package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.api.animation.AnimationStyle;
import java.util.*;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

record AnimationConfig(String selected, String fallback, AnimationStyle common, Map<String,AnimationStyle> profiles) {
    static AnimationConfig read(ConfigurationSection root) {
        String selected=AnimationEngine.id(Objects.requireNonNull(root.getString("default-animation"),"default-animation"));
        String fallback=AnimationEngine.id(Objects.requireNonNull(root.getString("fallback-animation"),"fallback-animation"));
        ConfigurationSection style=Objects.requireNonNull(root.getConfigurationSection("style"),"style");
        AnimationStyle common=parse(style,null);
        Map<String,AnimationStyle> profiles=new HashMap<>();
        ConfigurationSection section=root.getConfigurationSection("patterns");
        if(section!=null) section.getValues(false).forEach((key,value)-> {
            if(value instanceof ConfigurationSection profile) profiles.put(AnimationEngine.id(key),parse(profile,style));
        });
        return new AnimationConfig(selected,fallback,common,Map.copyOf(profiles));
    }
    AnimationStyle style(String id) { return profiles.getOrDefault(id,common); }
    static AnimationStyle parse(ConfigurationSection section,ConfigurationSection defaults) {
        java.util.function.Function<String,Object> get=key->section.contains(key)?section.get(key):defaults==null?null:defaults.get(key);
        Object rawPalette=get.apply("palette");
        if(!(rawPalette instanceof List<?> list)) throw new IllegalArgumentException("An animation palette is required");
        List<Color> palette=list.stream().map(value->{
            String hex=value.toString();
            if(!hex.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use #RRGGBB colors: "+hex);
            return Color.fromRGB(Integer.parseInt(hex.substring(1),16));
        }).toList();
        Map<String,Double> parameters=new HashMap<>();
        if(defaults!=null) parameters(defaults,parameters);
        parameters(section,parameters);
        return new AnimationStyle(com.riddleslabs.paper.api.command.argument.TypedArguments.enumeration(Particle.class).parse(Objects.toString(get.apply("particle"))),palette,
                number(get.apply("period-seconds")),number(get.apply("scale")),number(get.apply("width")),number(get.apply("height")),
                number(get.apply("brightness")),number(get.apply("base-brightness")),(float)number(get.apply("size")),
                (int)number(get.apply("duration-ticks")),parameters);
    }
    private static double number(Object value) {
        if(!(value instanceof Number n)) throw new IllegalArgumentException("Animation setting requires a number: "+value);
        return n.doubleValue();
    }
    private static void parameters(ConfigurationSection section,Map<String,Double> result) {
        ConfigurationSection params=section.getConfigurationSection("parameters");
        if(params!=null) params.getValues(false).forEach((k,v)->result.put(k,number(v)));
    }
}
