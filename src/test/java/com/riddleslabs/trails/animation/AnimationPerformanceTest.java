package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.*;
import com.riddleslabs.trails.api.animation.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in CPU/allocation benchmark; does not measure Minecraft networking or client rendering. */
@EnabledIfSystemProperty(named="riddlestrails.benchmark",matches="true")
class AnimationPerformanceTest {
    static volatile long blackhole;
    static final class Sink implements ParticleSink {
        int left;long count,signature=1;
        void reset(){left=1200;}
        public boolean emit(AnimationPoint point,double light,double color) {
            if(left==0)return false;left--;count++;
            signature=31*signature+Math.round(point.x()*1e6);
            signature=31*signature+Math.round(point.y()*1e6);
            signature=31*signature+Math.round(point.z()*1e6);
            signature=31*signature+Math.round(light*1e6);
            signature=31*signature+Math.round(color*1e6);return true;
        }
        public boolean emit(Particle particle,AnimationPoint point,int count,Object data){throw new AssertionError();}
        public int remaining(){return left;}
    }
    @Test void benchmark() throws Exception {
        var config=BuiltInAnimationsTest.config();var animations=BuiltInAnimations.all();
        var sink=new Sink();Map<String,Object> state=new HashMap<>();
        StringBuilder result=new StringBuilder();
        // Golden position/intensity traces allow implementation changes without silently reducing detail.
        for(var entry:animations.entrySet()) {
            state.clear();sink.signature=1;sink.count=0;var style=config.style(entry.getKey());
            for(int phase=0;phase<40;phase++) {sink.reset();entry.getValue().render(BuiltInAnimationsTest.frame(style,style.periodSeconds()*phase/40,1200,state),sink);}
            result.append(entry.getKey()).append(' ').append(sink.count).append(' ').append(sink.signature).append('\n');
        }
        Files.createDirectories(Path.of("target"));Files.writeString(Path.of("target/performance-traces.txt"),result);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId();
        for(int pass=0;pass<8;pass++)renderAll(animations,config,sink,state);
        double[] times=new double[7],allocations=new double[7];
        for(int pass=0;pass<7;pass++) {
            long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(int repeat=0;repeat<5;repeat++)renderAll(animations,config,sink,state);
            times[pass]=(System.nanoTime()-start)/1e6;allocations[pass]=(bean.getThreadAllocatedBytes(thread)-allocated)/(1024.0*1024);
        }
        Arrays.sort(times);Arrays.sort(allocations);
        int frames=animations.size()*24*5;
        String summary=String.format(Locale.ROOT,"Animation: %d frames, median %.3f ms, %.3f MiB allocated%n",frames,times[3],allocations[3]);
        List<Route.Point> points=new ArrayList<>();
        for(int i=0;i<5000;i++)points.add(new Route.Point(i*.3,64+Math.sin(i*.03),Math.sin(i*.013)*30));
        var path=new RouteGeometry.Path(points);var positions=new ArrayList<Route.Point>();
        for(int i=0;i<1000;i++)positions.add(points.get((i*37)%points.size()));
        for(int pass=0;pass<3;pass++)for(var point:positions)blackhole+=(long)path.progress(point);
        for(int pass=0;pass<7;pass++) {
            long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(var point:positions)blackhole+=(long)path.progress(point);
            times[pass]=(System.nanoTime()-start)/1e6;allocations[pass]=(bean.getThreadAllocatedBytes(thread)-allocated)/(1024.0*1024);
        }
        Arrays.sort(times);Arrays.sort(allocations);
        summary+=String.format(Locale.ROOT,"Route: 1000 nearest queries, 5000 points, median %.3f ms, %.3f MiB allocated%n",times[3],allocations[3]);
        Files.writeString(Path.of("target/performance-summary.txt"),summary);System.out.print(summary);
    }
    private static void renderAll(Map<String,TrailAnimation> animations,AnimationConfig config,Sink sink,Map<String,Object> state) {
        for(var entry:animations.entrySet()) {state.clear();var style=config.style(entry.getKey());
            for(int phase=0;phase<24;phase++){sink.reset();entry.getValue().render(BuiltInAnimationsTest.frame(style,style.periodSeconds()*phase/24,1200,state),sink);}}
        blackhole=sink.signature;
    }
}
