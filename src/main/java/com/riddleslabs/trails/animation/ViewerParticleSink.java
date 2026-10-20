package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.api.animation.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

/** Owns all Bukkit emissions; third-party callbacks never need a world-wide spawn operation. */
final class ViewerParticleSink implements ParticleSink, AutoCloseable {
    private final Player player;
    private final AnimationStyle style;
    private final Thread thread=Thread.currentThread();
    private final long frameSeed;
    private int remaining;
    private boolean closed;
    private double lastBrightness=Double.NaN,lastColorPosition=Double.NaN;
    private Object lastData;
    ViewerParticleSink(Player player,AnimationStyle style,int budget,long seed) {
        this.player=player; this.style=style; remaining=budget; frameSeed=seed;
    }
    @Override public boolean emit(AnimationPoint point,double brightness,double colorPosition) {
        check();
        if(!Double.isFinite(brightness)||!Double.isFinite(colorPosition)) throw new IllegalArgumentException("Non-finite appearance");
        if(remaining==0 || brightness<=0 || style.brightness()==0) return false;
        if(point==null)throw new IllegalArgumentException("Particle position is required");
        Object data;
        if(style.particle()==Particle.DUST || style.particle()==Particle.DUST_COLOR_TRANSITION) {
            if(brightness!=lastBrightness || colorPosition!=lastColorPosition) {
                Color color=style.color(colorPosition,brightness);
                lastData=style.particle()==Particle.DUST?new Particle.DustOptions(color,style.size()):
                        new Particle.DustTransition(color,style.color(Math.min(1,colorPosition+.15),brightness*.5),style.size());
                lastBrightness=brightness;lastColorPosition=colorPosition;
            }
            data=lastData;
        }
        else if(style.particle()==Particle.TRAIL)
            data=new Particle.Trail(new Location(player.getWorld(),point.x(),point.y(),point.z()),style.color(colorPosition,brightness),style.durationTicks());
        else {
            // Non-colorable particles approximate brightness through deterministic emission density.
            long bits=Double.doubleToLongBits(point.x())^Long.rotateLeft(Double.doubleToLongBits(point.z()),23)^frameSeed;
            double chance=((bits*0x9E3779B97F4A7C15L)>>>11)*0x1.0p-53;
            if(chance>Math.clamp(brightness*style.brightness(),0,1)) return false;
            data=null;
        }
        return send(style.particle(),point,1,data);
    }
    @Override public boolean emit(Particle particle,AnimationPoint point,int count,Object data) {
        check();
        if(count<1) throw new IllegalArgumentException("Particle count must be positive");
        if(particle==null||point==null) throw new IllegalArgumentException("Particle and position are required");
        Class<?> type=particle.getDataType();
        if(type==Void.class ? data!=null : !type.isInstance(data)) throw new IllegalArgumentException("Wrong data type for "+particle);
        return send(particle,point,count,data);
    }
    private boolean send(Particle particle,AnimationPoint point,int count,Object data) {
        if(remaining==0) return false;
        int emitted=Math.min(count,remaining);
        remaining-=emitted;
        player.spawnParticle(particle,point.x(),point.y(),point.z(),emitted,0,0,0,0,data);
        return true;
    }
    @Override public int remaining() { check(); return remaining; }
    private void check() {
        if(closed||Thread.currentThread()!=thread) throw new IllegalStateException("Particle sink is only valid during its render callback");
    }
    @Override public void close() { closed=true; }
}
