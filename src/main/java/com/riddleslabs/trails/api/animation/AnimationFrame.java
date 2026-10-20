package com.riddleslabs.trails.api.animation;

import java.util.Map;
import java.util.UUID;

/** One render update. State belongs only to this player, trail kind, source and animation selection. */
public record AnimationFrame(UUID playerId, TrailKind kind, String source, AnimationPath path,
                             AnimationPoint playerPosition, double elapsedSeconds, long seed,
                             double spacing, int budget, AnimationStyle style, Map<String,Object> state) {
    public double phase() { double t=elapsedSeconds/style.periodSeconds(); return t-Math.floor(t); }
    /** Normalized path position, lateral offset and vertical offset in style-scaled blocks. */
    public AnimationPoint point(double progress, double lateral, double vertical) {
        return path.at(Math.clamp(progress,0,1)*path.length(), lateral*style.width()*style.scale(),
                vertical*style.height()*style.scale());
    }
    /**
     * A rigid shape's local coordinates at a route position. Unlike point(), forward offsets
     * use the anchor's heading, so outlines do not fold around corners or flatten at endpoints.
     * Forward/lateral offsets use width and scale; vertical offsets use height and scale.
     */
    public AnimationPoint localPoint(double progress, double forward, double lateral, double vertical) {
        return localTransform(progress).point(forward,lateral,vertical);
    }
    /** Compute the route anchor/heading once, then reuse its transform for a complete shape. */
    public AnimationTransform localTransform(double progress) {
        double distance=Math.clamp(progress,0,1)*path.length();
        AnimationPoint a=path.at(distance-.15), b=path.at(distance+.15);
        double dx=b.x()-a.x(), dz=b.z()-a.z(), length=Math.hypot(dx,dz);
        if(length<1e-8) { dx=0;dz=1;length=1; }
        dx/=length;dz/=length;
        return new AnimationTransform(path.at(distance),dx,dz,style.width()*style.scale(),style.height()*style.scale());
    }
}
