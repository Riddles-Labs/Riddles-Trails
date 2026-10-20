package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.api.animation.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class BuiltInAnimationsTest {
    static final int HIGH_BUDGET=1200;
    @TestFactory Stream<DynamicTest> everyEffectUsesExtraBudgetForMoreDistinctGeometry() throws Exception {
        var config=config();
        return BuiltInAnimations.all().entrySet().stream().map(entry->DynamicTest.dynamicTest(entry.getKey()+" detail",()->{
            var style=config.style(entry.getKey());Map<String,Object> lowState=new HashMap<>(),highState=new HashMap<>();
            long low=0,high=0;
            for(int i=0;i<24;i++) {
                Capture sparse=new Capture(120),dense=new Capture(HIGH_BUDGET);double seconds=style.periodSeconds()*i/24;
                entry.getValue().render(frame(style,seconds,120,lowState),sparse);
                entry.getValue().render(frame(style,seconds,HIGH_BUDGET,highState),dense);
                low+=distinctPositions(sparse);high+=distinctPositions(dense);
                assertTrue(sparse.points.size()<=120);assertTrue(dense.points.size()<=HIGH_BUDGET);
            }
            assertTrue(high>low*2,"Higher detail must add new positions, not duplicate particles: "+low+" -> "+high);
        }));
    }
    private static long distinctPositions(Capture capture) {
        return capture.points.stream().map(v->String.format(Locale.ROOT,"%.4f,%.4f,%.4f",v[0],v[1],v[2])).distinct().count();
    }
    static AnimationConfig config() throws Exception {
        var cfg=new YamlConfiguration();cfg.load("src/main/resources/animations.yml");return AnimationConfig.read(cfg);
    }
    static AnimationPath path() {
        return new AnimationPath(List.of(new AnimationPoint(0,64,0),new AnimationPoint(10,64,0),new AnimationPoint(10,64,10)));
    }
    static AnimationFrame frame(AnimationStyle style,double seconds,int budget,Map<String,Object> state) {
        return new AnimationFrame(new UUID(1,2),TrailKind.QUEST,"npc:11",path(),new AnimationPoint(-2,64,0),seconds,73,.8,budget,style,state);
    }
    static final class Capture implements ParticleSink {
        final List<double[]> points=new ArrayList<>();
        final int budget;
        Capture(int budget) {this.budget=budget;}
        @Override public boolean emit(AnimationPoint point,double light,double color) {
            assertTrue(Double.isFinite(light)&&light>=0&&light<=1);
            assertTrue(Double.isFinite(color)&&color>=0&&color<=1);
            if(remaining()==0)return false;
            points.add(new double[]{point.x(),point.y(),point.z(),light,color});return true;
        }
        @Override public boolean emit(Particle type,AnimationPoint point,int count,Object data) {throw new AssertionError("Built-ins must use configurable particles");}
        @Override public int remaining() {return budget-points.size();}
    }

    @TestFactory Stream<DynamicTest> everyRequestedPatternAnimatesAcrossItsCycleWithinBudget() throws Exception {
        AnimationConfig config=config();
        assertEquals(47,BuiltInAnimations.all().size());
        assertEquals(BuiltInAnimations.all().keySet(),config.profiles().keySet());
        return BuiltInAnimations.all().entrySet().stream().map(entry->DynamicTest.dynamicTest(entry.getKey(),()->{
            AnimationStyle style=config.style(entry.getKey());Map<String,Object> state=new HashMap<>();
            Set<String> frames=new HashSet<>();int emitted=0;
            for(int i=0;i<40;i++) {
                Capture sink=new Capture(HIGH_BUDGET);
                entry.getValue().render(frame(style,style.periodSeconds()*i/40,HIGH_BUDGET,state),sink);
                assertTrue(sink.points.size()<=HIGH_BUDGET);
                frames.add(sink.points.stream().map(Arrays::toString).toList().toString());emitted+=sink.points.size();
            }
            assertTrue(emitted>0,"Must produce a visible animation");
            assertTrue(frames.size()>=3,"Must change throughout its cycle (theater chase has three steps)");
            Capture tiny=new Capture(6);entry.getValue().render(frame(style,.4,6,new HashMap<>()),tiny);assertTrue(tiny.points.size()<=6);
        }));
    }

    @TestFactory Stream<DynamicTest> everyEffectDrawsAShapeBeyondTheRouteCenterline() throws Exception {
        AnimationConfig config=config();
        AnimationPath straight=new AnimationPath(List.of(new AnimationPoint(0,64,0),new AnimationPoint(20,64,0)));
        return BuiltInAnimations.all().entrySet().stream().map(entry->DynamicTest.dynamicTest(entry.getKey()+" shape",()->{
            var style=config.style(entry.getKey());boolean shaped=false;Map<String,Object> state=new HashMap<>();
            for(int i=1;i<24;i++) {
                Capture sink=new Capture(HIGH_BUDGET);
                entry.getValue().render(new AnimationFrame(new UUID(1,2),TrailKind.QUEST,"npc:11",straight,
                        new AnimationPoint(0,64,0),style.periodSeconds()*i/24,73,.8,HIGH_BUDGET,style,state),sink);
                List<double[]> visible=sink.points.stream().filter(v->v[3]>.12).toList();
                double lowZ=visible.stream().mapToDouble(v->v[2]).min().orElse(0),highZ=visible.stream().mapToDouble(v->v[2]).max().orElse(0);
                double lowY=visible.stream().mapToDouble(v->v[1]).min().orElse(0),highY=visible.stream().mapToDouble(v->v[1]).max().orElse(0);
                if(visible.size()>=8 && (highZ-lowZ>.15 || highY-lowY>.15)) shaped=true;
            }
            assertTrue(shaped,"A brightness-only centerline does not count as a shape");
        }));
    }

    @Test void rigidShapesKeepTheirDimensionsAtCornersAndEndpoints() throws Exception {
        AnimationFrame frame=frame(config().common(),0,HIGH_BUDGET,new HashMap<>());
        for(double progress:new double[]{0,.5,1}) {
            AnimationPoint a=frame.localPoint(progress,-1,0,0),b=frame.localPoint(progress,1,0,0);
            assertEquals(2*frame.style().width()*frame.style().scale(),Math.sqrt(a.distanceSquared(b)),1e-9);
            assertEquals(frame.path().at(progress*frame.path().length()),a.interpolate(b,.5));
        }
    }

    @Test void walkingGhostHasDarkConfigMovesWalksAndDissolves() throws Exception {
        var ghost=BuiltInAnimations.all().get("riddlestrails:walking-ghost");var style=config().style("riddlestrails:walking-ghost");
        assertTrue(style.palette().stream().allMatch(c->c.getRed()<=24&&c.getGreen()<=24&&c.getBlue()<=24));
        Capture early=new Capture(HIGH_BUDGET),later=new Capture(HIGH_BUDGET),end=new Capture(HIGH_BUDGET);
        ghost.render(frame(style,style.periodSeconds()*.125,HIGH_BUDGET,new HashMap<>()),early);
        ghost.render(frame(style,style.periodSeconds()*.1875,HIGH_BUDGET,new HashMap<>()),later);
        ghost.render(frame(style,style.periodSeconds()*.95,HIGH_BUDGET,new HashMap<>()),end);
        assertTrue(early.points.stream().mapToDouble(v->v[1]).max().orElseThrow()>65.8,"Head reaches NPC height");
        assertTrue(later.points.stream().mapToDouble(v->v[0]).average().orElseThrow()>
                early.points.stream().mapToDouble(v->v[0]).average().orElseThrow(),"Figure moves forward");
        double earlyFeet=spanX(early.points.stream().filter(v->v[1]<64.3).toList());
        double laterFeet=spanX(later.points.stream().filter(v->v[1]<64.3).toList());
        assertTrue(laterFeet>earlyFeet+.1,"Legs change pose rather than sliding as a rigid figure");
        assertTrue(end.points.size()<early.points.size()/2,"Black particles must dissolve through density as well as brightness");
        // Changing the palette must recolor this effect; black is a configuration default only.
        var red=style.withPalette(List.of(Color.RED));
        assertEquals(Color.RED,red.color(.5,1));
    }
    private static double spanX(List<double[]> points) {
        return points.stream().mapToDouble(v->v[0]).max().orElse(0)-points.stream().mapToDouble(v->v[0]).min().orElse(0);
    }

    @Test void allPatternsHaveDistinctTracesAndExportPreviewFrames() throws Exception {
        AnimationConfig config=config();Set<String> traces=new HashSet<>();StringBuilder json=new StringBuilder("[");
        for(var entry:BuiltInAnimations.all().entrySet()) {
            if(json.length()>1)json.append(',');json.append("{\"id\":\"").append(entry.getKey()).append("\",\"seconds\":")
                    .append(config.style(entry.getKey()).periodSeconds()).append(",\"frames\":[");
            StringBuilder signature=new StringBuilder();Map<String,Object> state=new HashMap<>();AnimationStyle style=config.style(entry.getKey());
            for(int i=0;i<48;i++) {
                Capture capture=new Capture(HIGH_BUDGET);entry.getValue().render(frame(style,style.periodSeconds()*i/48,HIGH_BUDGET,state),capture);
                if(i>0)json.append(',');json.append('[');
                for(int j=0;j<capture.points.size();j++) {
                    double[] p=capture.points.get(j);if(j>0)json.append(',');
                    Color color=style.color(p[4],p[3]);
                    json.append(String.format(Locale.ROOT,"[%.3f,%.3f,%.3f,%d]",p[0],p[1]-64,p[2],color.asRGB()));
                    signature.append(Arrays.toString(p));
                }
                json.append(']');
            }
            assertTrue(traces.add(signature.toString()),"Duplicate pattern: "+entry.getKey());json.append("]}");
        }
        json.append(']');Files.createDirectories(Path.of("target"));Files.writeString(Path.of("target/animation-preview.json"),json);
        Files.writeString(Path.of("target/animation-preview.html"),Files.readString(Path.of("src/test/resources/animation-preview.html")).replace("__FRAMES__",json));
        atlas(config);
        shapeAtlas(config);
    }

    private static void shapeAtlas(AnimationConfig config) throws Exception {
        int columns=4,cellWidth=400,cellHeight=250;
        var image=new java.awt.image.BufferedImage(columns*cellWidth,12*cellHeight,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int index=0;
        for(var entry:BuiltInAnimations.all().entrySet()) {
            int x=index%columns*cellWidth,y=index/columns*cellHeight;index++;
            boolean ghost=entry.getKey().endsWith(":walking-ghost");
            g.setColor(new java.awt.Color(ghost?0xB8C9BD:0x101821));g.fillRect(x,y,cellWidth,cellHeight);
            g.setColor(new java.awt.Color(ghost?0x182825:0xDCE6F0));
            g.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,15));g.drawString(entry.getKey().substring(12),x+14,y+22);
            var style=config.style(entry.getKey());Map<String,Object> state=new HashMap<>();Capture best=null;double energy=-1;
            for(int i=0;i<48;i++) {
                Capture candidate=new Capture(HIGH_BUDGET);entry.getValue().render(frame(style,style.periodSeconds()*i/48,HIGH_BUDGET,state),candidate);
                double score=candidate.points.stream().mapToDouble(v->v[3]).sum();
                if(score>energy){energy=score;best=candidate;}
            }
            List<double[]> points=best.points.stream().filter(v->v[3]>.1).toList();
            double[] focus=focus(points);
            var child=(java.awt.Graphics2D)g.create(x,y+30,cellWidth,cellHeight-30);
            for(double[] point:points) {
                double dx=point[0]-focus[0],dz=point[2]-focus[2];
                if(Math.hypot(dx,dz)>2.3)continue;
                double px=cellWidth/2.0+(dx-dz)*55,py=cellHeight-90+(dx+dz)*24-(point[1]-64)*65;
                child.setColor(new java.awt.Color(style.color(point[4],point[3]).asRGB()));
                child.fill(new java.awt.geom.Ellipse2D.Double(px-2,py-2,4,4));
            }
            child.dispose();
        }
        g.dispose();javax.imageio.ImageIO.write(image,"png",Path.of("target/animation-shapes.png").toFile());
    }
    private static double[] focus(List<double[]> points) {
        double[] best={0,64,0};double score=-1;
        for(double[] candidate:points) {
            double energy=points.stream().filter(v->Math.hypot(v[0]-candidate[0],v[2]-candidate[2])<1.2).mapToDouble(v->v[3]).sum();
            if(energy>score){score=energy;best=candidate;}
        }
        return best;
    }

    private static void atlas(AnimationConfig config) throws Exception {
        int columns=4,cellWidth=300,cellHeight=180;
        var image=new java.awt.image.BufferedImage(columns*cellWidth,12*cellHeight,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(16,24,33));g.fillRect(0,0,image.getWidth(),image.getHeight());
        int index=0;
        for(var entry:BuiltInAnimations.all().entrySet()) {
            int x=(index%columns)*cellWidth,y=(index/columns)*cellHeight;index++;
            g.setColor(new java.awt.Color(220,230,240));g.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,13));g.drawString(entry.getKey().substring(12),x+12,y+20);
            Capture sink=new Capture(HIGH_BUDGET);var style=config.style(entry.getKey());Map<String,Object> state=new HashMap<>();
            for(int i=0;i<=15;i++){sink=new Capture(HIGH_BUDGET);entry.getValue().render(frame(style,style.periodSeconds()*i/40,HIGH_BUDGET,state),sink);}
            for(double[] p:sink.points) {
                int px=x+150+(int)((p[0]-p[2])*8),py=y+60+(int)((p[0]+p[2])*3.5-(p[1]-64)*20);
                Color color=style.color(p[4],p[3]);g.setColor(new java.awt.Color(color.asRGB()));g.fillOval(px-2,py-2,4,4);
            }
        }
        g.dispose();javax.imageio.ImageIO.write(image,"png",Path.of("target/animation-atlas.png").toFile());
    }

    @Test void palettesAndParametersAreImmutableAndInvalidAppearanceIsRejected() throws Exception {
        AnimationStyle original=config().common();
        assertThrows(UnsupportedOperationException.class,()->original.palette().clear());
        assertThrows(UnsupportedOperationException.class,()->original.parameters().clear());
        AnimationStyle red=original.withPalette(List.of(Color.RED));
        assertEquals(Color.RED,red.color(.7,1));
        assertEquals(Color.fromRGB(128,0,0),red.color(.7,.5));
        assertNotEquals(red.palette(),original.palette());
        assertThrows(IllegalArgumentException.class,()->original.withPeriod(0));
        assertThrows(IllegalArgumentException.class,()->original.withPalette(List.of()));
    }
    @Test void mothWaitsInWorldSpaceUntilTheViewerApproaches() throws Exception {
        var moth=BuiltInAnimations.all().get("riddlestrails:clockwork-moth");var style=config().common();Map<String,Object> state=new HashMap<>();
        moth.render(frame(style,0,HIGH_BUDGET,state),new Capture(HIGH_BUDGET));Object first=state.get("moth-guide");
        moth.render(frame(style,1,HIGH_BUDGET,state),new Capture(HIGH_BUDGET));assertEquals(first,state.get("moth-guide"));
        AnimationPoint near=(AnimationPoint)first;
        AnimationPath advanced=new AnimationPath(List.of(near,new AnimationPoint(10,64,10)));
        moth.render(new AnimationFrame(new UUID(1,2),TrailKind.QUEST,"npc:11",advanced,near,2,73,.8,HIGH_BUDGET,style,state),new Capture(HIGH_BUDGET));
        assertNotEquals(first,state.get("moth-guide"));
    }
}
