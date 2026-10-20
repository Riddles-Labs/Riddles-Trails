package com.riddleslabs.trails;

import com.riddleslabs.trails.animation.AnimationEngine;
import com.riddleslabs.trails.api.animation.*;
import java.util.*;
import org.bukkit.entity.Player;

/** Shared private particle renderer for ordinary and quest trails. */
public final class TrailParticles {
    private static volatile AnimationEngine engine;
    private TrailParticles() {}
    public static void initialize(AnimationEngine renderer) { engine=renderer; }
    public static void forget(UUID player,TrailKind kind) { AnimationEngine current=engine; if(current!=null) current.forget(player,kind); }
    public static void render(Player player,TrailSettings settings,RouteGeometry.Path path,double start,double end,
                              double elapsed,TrailKind kind,String source) {
        AnimationEngine current=engine;
        if(current==null) return;
        List<AnimationPoint> points=new ArrayList<>();
        double spacing=Math.clamp(settings.spacing()/2,.1,.5);
        for(double d=start;;d=Math.min(end,d+spacing)) {
            Route.Point point=path.at(d);
            points.add(new AnimationPoint(point.x(),point.y()+settings.height(),point.z()));
            if(d>=end) break;
        }
        current.render(player,kind,source,new AnimationPath(points),elapsed,settings.spacing(),settings.budget());
    }
}
