package com.riddleslabs.trails.api.animation;

import java.util.*;
import org.bukkit.Color;
import org.bukkit.Particle;

/** Immutable configurable appearance. No built-in animation chooses its own colors or particle type. */
public record AnimationStyle(Particle particle, List<Color> palette, double periodSeconds, double scale,
                             double width, double height, double brightness, double baseBrightness,
                             float size, int durationTicks, Map<String, Double> parameters) {
    public AnimationStyle {
        Objects.requireNonNull(particle);
        palette = List.copyOf(palette);
        if (palette.isEmpty()) throw new IllegalArgumentException("Palette cannot be empty");
        bounded(periodSeconds,.1,120,"period"); bounded(scale,.05,10,"scale");
        bounded(width,.05,5,"width"); bounded(height,0,5,"height");
        bounded(brightness,0,1,"brightness"); bounded(baseBrightness,0,1,"base brightness");
        bounded(size,.05,4,"size");
        if (durationTicks < 1 || durationTicks > 200) throw new IllegalArgumentException("Duration must be 1-200 ticks");
        parameters = Map.copyOf(parameters);
        parameters.forEach((key,value) -> { if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite parameter: " + key); });
        if (particle.getDataType() != Void.class && particle != Particle.DUST
                && particle != Particle.DUST_COLOR_TRANSITION && particle != Particle.TRAIL)
            throw new IllegalArgumentException("Default particle needs unsupported data: " + particle + "; custom emitters may provide its data explicitly");
    }
    private static void bounded(double v,double min,double max,String name) {
        if (!Double.isFinite(v)||v<min||v>max) throw new IllegalArgumentException("Invalid " + name + ": " + v);
    }
    public double parameter(String key, double fallback) { return parameters.getOrDefault(key, fallback); }
    public AnimationStyle withPalette(List<Color> colors) {
        return new AnimationStyle(particle,colors,periodSeconds,scale,width,height,brightness,baseBrightness,size,durationTicks,parameters);
    }
    public AnimationStyle withParticle(Particle type) {
        return new AnimationStyle(type,palette,periodSeconds,scale,width,height,brightness,baseBrightness,size,durationTicks,parameters);
    }
    public AnimationStyle withPeriod(double seconds) {
        return new AnimationStyle(particle,palette,seconds,scale,width,height,brightness,baseBrightness,size,durationTicks,parameters);
    }
    public Color color(double position, double intensity) {
        if(!Double.isFinite(position)||!Double.isFinite(intensity)) throw new IllegalArgumentException("Non-finite color position or intensity");
        double index = Math.clamp(position,0,1)*(palette.size()-1);
        int i=(int)index; Color a=palette.get(i), b=palette.get(Math.min(i+1,palette.size()-1));
        double t=index-i, light=Math.clamp(intensity*brightness,0,1);
        return Color.fromRGB((int)Math.round((a.getRed()+(b.getRed()-a.getRed())*t)*light),
                (int)Math.round((a.getGreen()+(b.getGreen()-a.getGreen())*t)*light),
                (int)Math.round((a.getBlue()+(b.getBlue()-a.getBlue())*t)*light));
    }
}
