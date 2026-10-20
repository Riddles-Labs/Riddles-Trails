package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.api.animation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Procedural geometry and envelopes only. Appearance comes exclusively from AnimationStyle. */
public final class BuiltInAnimations {
    private BuiltInAnimations() {}
    public enum Pattern {
        BREATHING, CHASE, COMET, WAVE, RIPPLE, HEARTBEAT, TWINKLE, SPARKLE, SCAN,
        SEQUENTIAL_FILL, DRIPPING, THEATER_CHASE, STACKING, CROSSFADE_CHASE,
        TICK_TOCK, CLOCK_HANDS, TIME_SKIP, REVERSE_ECHO, CLOCKWORK_FOOTSTEPS,
        UNWINDING_SPRING, STEAM_BURSTS, FUSE_BURN, ORBITING_GUIDE, SHATTERED_SECONDS,
        PENDULUM, INK_REVEAL, MECHANICAL_RELAY, GHOST_OF_TOMORROW, ESCAPING_SECONDS,
        CLOCKWORK_MOTH, BROKEN_TIMELINE, POCKET_WATCH_SWING, BORROWED_FOOTSTEPS,
        MECHANICAL_FIREFLIES, STITCHING_TIME, FALLING_HOURGLASS, ECHO_DOORS,
        MAGNETIC_SHAVINGS, CLOCK_TEETH, REWINDING_RIBBON, FUTURE_GLIMPSE,
        PRESSURE_LEAK, ORBIT_COLLAPSE, UNFINISHED_BLUEPRINT, DELAYED_SHADOW, SECOND_HAND_SWEEP,
        WALKING_GHOST;
        public String id() { return "riddlestrails:" + name().toLowerCase(Locale.ROOT).replace('_','-'); }
    }
    public static Map<String,TrailAnimation> all() {
        Map<String,TrailAnimation> result=new LinkedHashMap<>();
        for (Pattern pattern:Pattern.values()) result.put(pattern.id(), (frame,sink)->new Draw(frame,sink).render(pattern));
        return Collections.unmodifiableMap(result);
    }

    private static final class Draw {
        private static final double TAU=Math.PI*2;
        // The density limit bounds unit templates to 576 vertices. Templates are read-only.
        private static final AtomicReferenceArray<double[][]> CIRCLES=new AtomicReferenceArray<>(577);
        private static final AtomicReferenceArray<double[][]> HEARTS=new AtomicReferenceArray<>(577);
        final AnimationFrame f;
        final ParticleSink sink;
        final double p,t,width,tail,frequency,shapeSize,density;
        final int samples,markers;
        double anchorProgress=Double.NaN;
        AnimationTransform anchor;
        double pointProgress=Double.NaN,normalX,normalZ;
        AnimationPoint pointOrigin;
        Draw(AnimationFrame frame,ParticleSink sink) {
            f=frame; this.sink=sink; p=f.phase(); t=f.elapsedSeconds()/f.style().periodSeconds();
            width=param("pulse-width",.12,.01,1); tail=param("tail-length",.3,.01,1);
            frequency=param("frequency",3,.1,20);
            shapeSize=param("shape-size",.85,.15,3);
            density=Math.max(1,Math.min(param("detail-multiplier",6,1,12),f.budget()/120.0));
            markers=(int)param("markers",5,1,16);
            samples=Math.clamp((int)Math.ceil(f.path().length()/f.spacing()*density)+1,2,Math.min(2048,Math.max(2,f.budget())));
        }
        double param(String key,double value,double min,double max) { return Math.clamp(f.style().parameter(key,value),min,max); }
        double u(int i) { return (double)i/(samples-1); }
        void dot(double u,double side,double up,double light) {
            if (light<=.003 || sink.remaining()==0)return;
            double progress=Math.clamp(u,0,1);
            if(progress!=pointProgress) {
                double distance=progress*f.path().length();
                AnimationPoint a=f.path().at(Math.max(0,distance-.15)),b=f.path().at(Math.min(f.path().length(),distance+.15));
                double dx=b.x()-a.x(),dz=b.z()-a.z(),norm=Math.hypot(dx,dz);
                normalX=norm<1e-8?1:-dz/norm;normalZ=norm<1e-8?0:dx/norm;
                pointOrigin=f.path().at(distance);pointProgress=progress;
            }
            double lateral=side*f.style().width()*f.style().scale();
            sink.emit(pointOrigin.add(normalX*lateral,up*f.style().height()*f.style().scale(),normalZ*lateral),clamp(light),clamp(u));
        }
        void base() { for(int i=0;i<samples;i+=3) dot(u(i),0,0,f.style().baseBrightness()); }
        double noise(int i) { return random(f.seed()+i*0x9E3779B97F4A7C15L); }
        double pulse(double x,double center) { double d=(x-center)/width; return Math.exp(-d*d*3); }
        double fade(double age) { return clamp(1-age); }
        double meters(double value) { return value/Math.max(1,f.path().length()); }
        int dense(int points) { return Math.max(2,(int)Math.ceil(points*density)); }
        int count(int cost) { return Math.min(markers,Math.max(1,f.budget()/dense(cost))); }
        int detail() { return (int)param("shape-points",24,12,256); }
        double marker(int i,int count) { return (i+.5)/count; }
        AnimationTransform local(double center) {
            if(center!=anchorProgress) {anchor=f.localTransform(center);anchorProgress=center;}
            return anchor;
        }
        // Equal-distance samples cover the entire contour, even with a small particle budget.
        // Local rigid coordinates keep a recognizable silhouette at bends and route endpoints.
        void symbol(double center,double side,double up,double size,double rotation,boolean upright,
                    double light,int points,boolean closed,double[][] vertices) {
            if(light<=.003 || sink.remaining()==0) return;
            int edges=vertices.length-(closed?0:1);
            double[] lengths=new double[edges];double total=0;
            for(int i=0;i<edges;i++) {
                double[] a=vertices[i],b=vertices[(i+1)%vertices.length];
                total+=lengths[i]=Math.hypot(b[0]-a[0],b[1]-a[1]);
            }
            int n=Math.min(dense(points),sink.remaining());
            AnimationTransform transform=local(center);
            double cos=Math.cos(rotation),sin=Math.sin(rotation),factor=size*shapeSize;
            int edge=0;double consumed=0;
            for(int j=0;j<n;j++) {
                double distance=total*j/Math.max(1,closed?n:n-1);
                while(edge<edges-1 && distance-consumed>lengths[edge]) consumed+=lengths[edge++];
                distance-=consumed;
                double[] a=vertices[edge],b=vertices[(edge+1)%vertices.length];
                double q=lengths[edge]<1e-9?0:distance/lengths[edge];
                double x=(a[0]+(b[0]-a[0])*q)*factor,y=(a[1]+(b[1]-a[1])*q)*factor;
                double rx=x*cos-y*sin,ry=x*sin+y*cos;
                sink.emit(transform.point(upright?0:ry,side+rx,up+(upright?ry:0)),clamp(light),clamp(center));
            }
        }
        void glyph(double center,double up,double size,double light,boolean upright,double[][] vertices) {
            symbol(center,0,up,size,0,upright,light,detail(),true,vertices);
        }
        double[][] circle(int n) {
            n=dense(n);
            double[][] cached=CIRCLES.get(n);if(cached!=null)return cached;
            double[][] v=new double[n][2];
            for(int i=0;i<n;i++) {v[i][0]=Math.cos(TAU*i/n);v[i][1]=Math.sin(TAU*i/n);}
            CIRCLES.compareAndSet(n,null,v);return CIRCLES.get(n);
        }
        double[][] star(int tips,double inner) {
            double[][] v=new double[tips*2][2];
            for(int i=0;i<v.length;i++) {double a=TAU*i/v.length+Math.PI/2,r=i%2==0?1:inner;v[i][0]=Math.cos(a)*r;v[i][1]=Math.sin(a)*r;}return v;
        }
        double[][] heart() {
            int n=dense(48);double[][] cached=HEARTS.get(n);if(cached!=null)return cached;
            double[][] v=new double[n][2];
            for(int i=0;i<v.length;i++) {double a=TAU*i/v.length;v[i][0]=Math.pow(Math.sin(a),3);
                v[i][1]=(13*Math.cos(a)-5*Math.cos(2*a)-2*Math.cos(3*a)-Math.cos(4*a))/16;}
            HEARTS.compareAndSet(n,null,v);return HEARTS.get(n);
        }
        double[][] drop() {
            return new double[][]{{0,1},{.35,.3},{.55,-.25},{.4,-.7},{0,-.9},{-.4,-.7},{-.55,-.25},{-.35,.3}};
        }
        double[][] shoe(int side) {
            double[][] v={{-.16,-.48},{.15,-.48},{.18,-.26},{.12,-.08},{.23,.2},{.2,.43},{.08,.54},{-.12,.5},{-.24,.3},{-.2,.02}};
            for(double[] point:v)point[0]*=side;return v;
        }
        double[][] chevron() {return new double[][]{{-.6,-.4},{0,.4},{.6,-.4}};}
        double[][] arrowOutline() {return new double[][]{{0,.85},{.6,.1},{.2,.1},{.2,-.75},{-.2,-.75},{-.2,.1},{-.6,.1}};}
        void chevron(double center,double side,double up,double size,double rotation,double light) {
            symbol(center,side,up,size,rotation,false,light,12,false,chevron());
        }
        void gear(double center,double up,double size,double rotation,double light) {
            double[][] v=new double[32][2];
            for(int i=0;i<v.length;i++) {double a=TAU*i/v.length,r=i%4<2?1:.72;v[i][0]=Math.cos(a)*r;v[i][1]=Math.sin(a)*r;}
            symbol(center,0,up,size,rotation,false,light,32,true,v);
            if(density>1) {
                symbol(center,0,up,size*.3,0,false,light,12,true,circle(32));
                for(int i=0;i<4;i++)symbol(center,0,up,size,rotation+i*Math.PI/2,false,light*.7,3,false,new double[][]{{0,.3},{0,.7}});
            }
        }
        void arrow(double center,double light,double side,double up) {
            symbol(center,side,up,1,0,false,light,20,true,arrowOutline());
        }
        void ring(double center,double radius,double up,double light,boolean upright) {
            int n=(int)param("ring-points",12,6,128);
            symbol(center,0,up,radius,0,upright,light,n,true,circle(48));
        }
        void silhouette(double center,double light) {
            ring(center,.18,1.5,light,true);
            symbol(center,0,0,1,0,true,light,20,true,new double[][]{{-.25,1.25},{.25,1.25},{.18,.65},{-.18,.65}});
            for(int side:new int[]{-1,1}) {
                symbol(center,0,0,1,0,true,light,8,false,new double[][]{{side*.25,1.25},{side*.45,.8}});
                symbol(center,0,0,1,0,true,light,8,false,new double[][]{{side*.15,.65},{side*.3,.1}});
            }
        }
        void render(Pattern pattern) {
            switch(pattern) {
                case BREATHING -> {
                    double breath=.5-.5*Math.cos(TAU*p);int n=count(36);
                    for(int i=0;i<n;i++) {
                        double c=marker(i,n),r=.45+.35*breath;
                        symbol(c,0,.8,r,0,true,.2+.8*breath,18,true,circle(32));
                        symbol(c,0,.8,r,0,false,.2+.8*breath,18,true,circle(32));
                    }
                }
                case CHASE -> {
                    int n=count(24);
                    for(int i=0;i<n;i++) {double c=frac(p+i/(double)n);
                        chevron(c,0,.12,1,0,1);chevron(c-meters(.6),0,.12,.8,0,.4);}
                }
                case COMET -> {
                    double head=.15+p*.7;
                    glyph(head,.5,.55,1,true,star(5,.45));
                    int n=Math.max(2,Math.min(dense(36),sink.remaining()/2));
                    for(int i=0;i<n;i++) {double age=i/(double)(n-1),spread=.42*age;
                        for(int side:new int[]{-1,1}) dot(head-age*tail,side*spread,.5+.25*Math.sin(age*Math.PI),fade(age));}
                }
                case WAVE -> {
                    int n=Math.max(2,Math.min(dense(60),f.budget()/2));
                    for(int i=0;i<n;i++) {double q=i/(double)(n-1),a=TAU*(q*frequency-p);
                        dot(q,Math.sin(a)*.6,.6+.25*Math.cos(a),.85);
                        dot(q,Math.sin(a)*.6,.6-.25*Math.cos(a),.4);}
                }
                case RIPPLE -> {
                    double origin=param("origin",0,0,1);int n=count(detail());
                    for(int i=0;i<n;i++) {double age=frac(p+i/(double)n);
                        glyph(origin,.06,.15+age*2,fade(age),false,circle(48));}
                }
                case HEARTBEAT -> {
                    double beat=Math.max(pulse(p,.12),.8*pulse(p,.3));int n=count(detail());
                    for(int i=0;i<n;i++) glyph(marker(i,n),.9,.6+.35*beat,.25+.75*beat,true,heart());
                }
                case TWINKLE -> {
                    int n=count(detail());
                    for(int i=0;i<n;i++) {double light=.5+.5*Math.sin(TAU*(t*(.5+noise(i))+noise(i+91)));
                        glyph(marker(i,n),.65,.4+.3*light,.1+.9*light*light,true,star(5,.42));}
                }
                case SPARKLE -> {
                    int n=count(24);
                    for(int i=0;i<n;i++) {double light=Math.pow(.5+.5*Math.cos(TAU*(t*frequency+noise(i))),12);
                        symbol(marker(i,n),0,.75,.3+.6*light,p,true,.15+.85*light,24,true,star(8,.18));}
                }
                case SCAN -> {
                    double c=.1+.8*(1-Math.abs(2*p-1));
                    glyph(c,1,1,.45,true,new double[][]{{-.8,-1},{.8,-1},{.8,1},{-.8,1}});
                    symbol(c,0,1,.95,0,true,1,16,false,new double[][]{{-.8,Math.sin(TAU*p)},{.8,Math.sin(TAU*p)}});
                }
                case SEQUENTIAL_FILL -> {
                    int n=count(detail());
                    for(int i=0;i<n;i++) {double q=marker(i,n),light=p<.72?(q<p/.72?1:.08):fade((p-.72)/.28);
                        glyph(q,.08,1,light,false,arrowOutline());}
                }
                case DRIPPING -> {
                    int n=count(detail());
                    for(int i=0;i<n;i++) {double age=frac(p+i/(double)n);
                        glyph(age,1.3-age,.55,.35+.65*fade(age),true,drop());}
                }
                case THEATER_CHASE -> {
                    int n=Math.min(markers*2,Math.max(1,f.budget()/dense(12)));
                    for(int i=0;i<n;i++) {double light=Math.floorMod(i-(int)Math.floor(t*frequency),3)==0?1:.1;
                        symbol(marker(i,n),0,.4,.6,0,true,light,12,true,new double[][]{{0,1},{.65,0},{0,-1},{-.65,0}});}
                }
                case STACKING -> {
                    int n=count(20);double stage=Math.min(.999,p/.85)*n;int filled=(int)stage;
                    for(int i=0;i<=filled;i++) {double c=i==filled?frac(stage)*.85:.85,light=p>.85?fade((p-.85)/.15):1;
                        symbol(c,0,.18+i*.3,.65,0,true,light,20,true,new double[][]{{-1,-.18},{1,-.18},{1,.18},{-1,.18}});}
                }
                case CROSSFADE_CHASE -> {
                    double w=.5-.5*Math.cos(TAU*p);
                    for(int i=0;i<2;i++) {double c=frac(p+i*.5),light=i==0?1-w:w;
                        glyph(c,.12,.7,light,false,circle(40));chevron(c,0,.12,.7,0,light);}
                }
                default -> geometry(pattern);
            }
            // Outlines have priority; the optional dim route uses only the remaining budget.
            base();
        }

        void geometry(Pattern pattern) {
            switch(pattern) {
                case TICK_TOCK -> {
                    int tick=(int)Math.floor(t*markers);
                    double head=frac((double)tick/markers);
                    double side=tick%2==0?-1:1;
                    symbol(head,side*.65,.25,.7,side*.35,false,1,24,true,
                            new double[][]{{-.45,-.65},{.45,-.65},{.45,.65},{-.45,.65}});
                    chevron(head,side*.65,.3,.45,0,1);
                }
                case CLOCK_HANDS -> {
                    int n=count((int)param("ring-points",12,6,128)+(density>1?36:12));
                    for(int i=0;i<n;i++) {
                        double c=marker(i,n), a=TAU*p+i*.4;
                        ring(c,.65,.25,.35,false);
                        symbol(c,0,.25,1,a,false,1,6,false,new double[][]{{0,0},{0,.6}});
                        symbol(c,0,.25,1,0,false,.7,6,false,new double[][]{{0,0},{0,.38}});
                        if(density>1) clockMarks(c,0,.25,.65,false,.65);
                    }
                }
                case TIME_SKIP -> {
                    double head=p<.3?.25:p<.55?.25:.65+(p-.55)*.7;
                    double light=p<.3?1:p<.45?1-(p-.3)/.15:p<.55?0:1;
                    double[][] bolt={{.1,1},{-.55,.05},{-.05,.05},{-.3,-1},{.6,.2},{.1,.2}};
                    glyph(head,.9,.8,light,true,bolt);
                    glyph(.25,.9,.8,.18*fade((p-.3)/.7),true,bolt);
                }
                case REVERSE_ECHO -> {
                    arrow(p,1,0,.12);
                    for(int i=0;i<3;i++) chevron(frac(-p+i*.3),0,.15,.8,Math.PI,.35/(i+1));
                }
                case CLOCKWORK_FOOTSTEPS, BORROWED_FOOTSTEPS -> footsteps(pattern==Pattern.BORROWED_FOOTSTEPS);
                case UNWINDING_SPRING -> {
                    double stretch=.08+.55*Math.sin(Math.PI*p), head=.12+p*.65;
                    int n=Math.max(2,Math.min(dense(100),f.budget()));
                    for(int i=0;i<n;i++) { double q=i/(double)(n-1), a=TAU*(q*6-p);
                        dot(Math.min(.9,head)+q*Math.min(stretch,1-head),Math.cos(a)*(.45-.3*p),.5+Math.sin(a)*(.45-.3*p),Math.sin(Math.PI*p)); }
                }
                case STEAM_BURSTS -> {
                    int n=count(24);
                    for(int i=0;i<n;i++) { double age=frac(p*2-i/(double)n);
                        int points=dense(24);
                        for(int j=0;j<points;j++) { double q=j/(double)(points-1),a=q*TAU*1.5;
                            dot(marker(i,n)+meters(age*.8+Math.cos(a)*q*.35),Math.sin(a)*q*.35,
                                    age*.9+q*1.1,fade(age)*fade(q*.6)); }
                    }
                }
                case FUSE_BURN -> {
                    glyph(p,.2,.45,1,true,star(8,.2));
                    for(int i=0;i<samples;i++) if(u(i)>p) {dot(u(i),-.08,0,.2);dot(u(i),.08,0,.2);}
                    for(int i=0;i<samples;i++) { double age=frac(p-u(i));
                        if(age<tail) dot(u(i),(noise(i)-.5)*age*3,noise(i+50)*age*2,Math.pow(fade(age/tail),3)); }
                }
                case ORBITING_GUIDE -> {
                    int n=Math.max(2,Math.min(dense(60),f.budget()/2));
                    for(int i=0;i<n;i++) { double q=i/(double)(n-1), a=TAU*(q*frequency-p);
                        for(int arm=0;arm<2;arm++) dot(q,Math.cos(a+arm*Math.PI)*.5,.65+Math.sin(a+arm*Math.PI)*.5,.65+.35*Math.sin(a)); }
                }
                case SHATTERED_SECONDS -> {
                    double gather=Math.sin(Math.PI*p), center=.25+.5*p;
                    double[][] outline=arrowOutline();
                    int points=dense(4);
                    for(int i=0;i<outline.length*points;i++) {int edge=i/points;double q=(i%points)/(double)points;
                        double[] a=outline[edge],b=outline[(edge+1)%outline.length];
                        sink.emit(local(center).point((a[1]+(b[1]-a[1])*q)*shapeSize+(noise(i)-.5)*2*(1-gather),
                                (a[0]+(b[0]-a[0])*q)*shapeSize+(noise(i+30)-.5)*2*(1-gather),
                                .4+(noise(i+60)-.5)*(1-gather)),.25+.75*gather,center); }
                }
                case PENDULUM -> {
                    double swing=Math.sin(TAU*p)*.8,side=Math.sin(swing),up=1.7-Math.cos(swing);
                    symbol(p,0,0,1,0,true,.5,16,false,new double[][]{{0,1.7},{side,up}});
                    symbol(p,side*shapeSize,up*shapeSize,.3,0,true,1,24,true,circle(32));
                }
                case INK_REVEAL -> {
                    int n=count(detail());
                    for(int i=0;i<n;i++) {double age=frac(p-i/(double)n);double[][] template=circle(40),blob=new double[template.length][2];
                        for(int j=0;j<blob.length;j++){blob[j][0]=template[j][0];blob[j][1]=template[j][1];}
                        for(int j=0;j<blob.length;j++) {double a=TAU*j/blob.length,r=.7+.2*Math.sin(a*3+i)+.1*Math.sin(a*7);blob[j][0]*=r;blob[j][1]*=r;}
                        glyph(marker(i,n),.02,.15+age,fade(age),false,blob);}
                }
                case MECHANICAL_RELAY -> {
                    int n=count(density>1?56:32);
                    for(int i=0;i<n;i++) {double age=frac(p-i/(double)n);
                        gear(marker(i,n),.15,.65,TAU*age,.15+.85*Math.pow(fade(age),5));}
                }
                case GHOST_OF_TOMORROW -> silhouette(.3+Math.floor(p*3)*.2,Math.pow(Math.sin(Math.PI*frac(p*3)),2)*.65);
                case ESCAPING_SECONDS -> {
                    for(int i=0;i<12;i++) { double age=frac(p+i/12.0), launch=Math.min(1,age/.2);
                        AnimationPoint rest=f.playerPosition().add((noise(i)-.5)*1.5*f.style().width()*f.style().scale(),
                                (.6+noise(i+40))*f.style().height()*f.style().scale(),(noise(i+80)-.5)*1.5*f.style().width()*f.style().scale());
                        AnimationPoint position=age<.4?f.playerPosition().interpolate(rest,launch):rest.interpolate(f.point(.8,0,0),Math.pow((age-.4)/.6,2));
                        int points=dense(6);
                        for(int j=0;j<points;j++) {double edge=j*6.0/points;int k=(int)edge;double q=edge-k;
                            double a=TAU*k/6,b=TAU*(k+1)/6,ra=k%2==0?.18:.045,rb=k%2==0?.045:.18;
                            sink.emit(position.add((Math.cos(a)*ra*(1-q)+Math.cos(b)*rb*q)*f.style().width()*f.style().scale(),
                                    (Math.sin(a)*ra*(1-q)+Math.sin(b)*rb*q)*f.style().height()*f.style().scale(),0),fade(age*.6),age);}
                    }
                }
                case CLOCKWORK_MOTH -> moth();
                case BROKEN_TIMELINE -> {
                    double align=Math.pow(Math.sin(Math.PI*p),6);int n=count(20);
                    for(int i=0;i<n;i++) symbol(marker(i,n),(noise(i)-.5)*2*(1-align),.2,1,(noise(i+40)-.5)*(1-align)*2,
                            false,.2+.8*align,20,true,arrowOutline());
                }
                case POCKET_WATCH_SWING -> {
                    double side=Math.sin(TAU*p)*.8, c=.2+p*.6;
                    for(int i=0;i<dense(8);i++) {double q=i/(double)dense(8);dot(c,side*q,1.8-q*1.12,.25);}
                    for(int i=0;i<dense(18);i++) { double a=TAU*i/dense(18); dot(c,side+Math.cos(a)*.3,.65+Math.sin(a)*.3,1); }
                    for(int i=0;i<dense(6);i++) {double r=i/(double)dense(6)*.24;dot(c,side+Math.sin(TAU*p)*r,.65+Math.cos(TAU*p)*r,1);}
                    if(density>1) {
                        clockMarks(c,side,.65,.3/shapeSize,true,.7);
                        symbol(c,side,1.01,.08,0,true,.8,8,true,new double[][]{{-1,-.5},{1,-.5},{1,.5},{-1,.5}});
                        symbol(c,side,.65,.18,TAU*p/12,true,1,6,false,new double[][]{{0,0},{0,1}});
                    }
                }
                case MECHANICAL_FIREFLIES -> {
                    double spread=.15+Math.pow(Math.cos(Math.PI*p),2), c=.2+p*.65;
                    for(int i=0;i<12;i++) symbol(c+meters(Math.sin(t*2+i)*spread),Math.cos(t*3+i*2)*spread,
                            .7+Math.sin(t+i*3)*spread*.5,.12,Math.sin(t*9+i),true,.4+.6*noise(i),8,true,star(4,.35));
                }
                case STITCHING_TIME -> {
                    glyph(p,.1,.4,1,false,new double[][]{{0,1},{.16,0},{0,-1},{-.16,0}});
                    int n=count(12);
                    for(int i=0;i<n;i++) {double c=marker(i,n),age=frac(p-c);
                        if(age<tail) {
                            symbol(c,0,.03,.55,0,false,fade(age/tail),6,false,new double[][]{{-.6,-.35},{.6,.35}});
                            symbol(c,0,.03,.55,0,false,fade(age/tail),6,false,new double[][]{{-.6,.35},{.6,-.35}});
                        }}
                }
                case FALLING_HOURGLASS -> {
                    int n=count(36);
                    for(int i=0;i<n;i++) {double c=marker(i,n);
                        symbol(c,0,.8,.85,0,true,.55,24,true,new double[][]{{-.55,1},{.55,1},{.1,0},{.55,-1},{-.55,-1},{-.1,0}});
                        for(int j=0;j<dense(10);j++) {double age=frac(p+i*.2+j/(double)dense(10));
                            dot(c,(noise(j)-.5)*Math.abs(1-age*2)*.5,1.5*(1-age),.9);}
                    }
                }
                case ECHO_DOORS -> {
                    int n=count(48);
                    for(int i=0;i<n;i++) {double c=marker(i,n),open=frac(p+i*.2),angle=open*Math.PI/2;
                        for(int side:new int[]{-1,1}) {
                            double hinge=side*.8,inner=hinge-side*.8*Math.cos(angle),forward=-Math.sin(angle)*.8;
                            List<AnimationPoint> points=new ArrayList<>();
                            double[][] corners={{0,hinge,0},{forward,inner,0},{forward,inner,1.8},{0,hinge,1.8}};
                            for(int j=0;j<4;j++) bodyLine(points,c,corners[j],corners[(j+1)%4],6);
                            for(AnimationPoint point:points)sink.emit(point,fade(open),c);
                        }
                    }
                }
                case MAGNETIC_SHAVINGS -> {
                    double align=Math.pow(Math.sin(Math.PI*p),4);int n=count(24);
                    for(int i=0;i<n;i++) {
                        double c=marker(i,n);
                        for(int side:new int[]{-1,1}) symbol(c,side*.45*(1-align),.03,.7,(noise(i+side+20)-.5)*(1-align)*4,
                                false,.3+.7*align,12,false,new double[][]{{side*.6,-.45},{0,.4}});
                    }
                }
                case CLOCK_TEETH -> {
                    int n=count(20);
                    for(int i=0;i<n;i++) {double c=marker(i,n),lift=Math.max(0,Math.sin(TAU*(p-i/(double)n)));
                        symbol(c,0,.05,1,0,true,.3+.7*lift,20,false,
                                new double[][]{{-.65,0},{-.3,0},{-.3,.2+lift*.7},{.3,.2+lift*.7},{.3,0},{.65,0}});}
                }
                case REWINDING_RIBBON -> {
                    int n=Math.max(2,Math.min(dense(60),f.budget()/2));
                    for(int i=0;i<n;i++) { double q=i/(double)(n-1), unwind=p<.5?0:(p-.5)*2;
                        double c=p<.5?.55-q*.25*p:.3+q*unwind*.65;
                        for(int edge:new int[]{-1,1}) dot(c,Math.sin(q*TAU*3-p*TAU)*.5*(1-unwind)+edge*.08,
                                .5+Math.cos(q*TAU*3)*.3*(1-unwind),edge==1?.9:.45); }
                }
                case FUTURE_GLIMPSE -> {
                    arrow(.95,fade(p/.5),0,.15);
                    for(int i=0;i<markers;i++) { double c=1-(i+1.0)/(markers+1), birth=(i+1.0)/(markers+2);
                        if(p>birth) arrow(c,fade((p-birth)*1.5),0,0); }
                }
                case PRESSURE_LEAK -> {
                    double bend=.5;
                    for(int i=1;i<samples-1;i++) if(f.path().bend(u(i)*f.path().length())>.1){bend=u(i);break;}
                    double puff=frac(p-bend),light=fade(puff*2);
                    glyph(bend,.3+puff*2,.15+puff*.8,light,true,circle(32));
                    for(int i=0;i<samples;i++) {double q=u(i),swell=pulse(q,p);
                        dot(q,-.1-swell*.25,.1+swell*.45,.3+.7*swell);
                        dot(q,.1+swell*.25,.1+swell*.45,.3+.7*swell);}
                }
                case ORBIT_COLLAPSE -> {
                    int marker=(int)Math.floor(p*markers); double age=frac(p*markers), c=(marker+.5)/markers;
                    if(age<.7) ring(c,.75*(1-age/.7),.5,.8,false);
                    else arrow(c+(age-.7)/.3/markers,1,0,.5);
                }
                case UNFINISHED_BLUEPRINT -> {
                    if(p>.55) arrow(.8,Math.sin(Math.PI*(p-.55)/.45),0,0);
                    for(int i=0;i<samples;i++) { double q=u(i);
                        if(q<p*1.7 && i%2==0) { dot(q,-.45,0,fade(Math.max(0,p-.7)/.3)); dot(q,.45,0,fade(Math.max(0,p-.7)/.3)); }
                        if(i%6==0 && q<p*1.7) for(int j=0;j<4;j++) dot(q,-.45+j*.3,0,.35);
                    }
                }
                case DELAYED_SHADOW -> {
                    arrow(p,1,0,.3);
                    for(int i=1;i<=3;i++) { double delayed=t-i*.14, q=frac(Math.floor(delayed*markers)/markers);
                        double catchup=Math.pow(frac(delayed*markers),5)/markers;
                        arrow(frac(q+catchup),.5/(i+1),0,.3); }
                }
                case SECOND_HAND_SWEEP -> {
                    double bend=.5, strength=0;
                    for(int i=1;i<samples-1;i++) { double b=f.path().bend(u(i)*f.path().length()); if(b>strength) {strength=b;bend=u(i);} }
                    double angle=TAU*p;
                    glyph(bend,.2,1.8,.25,false,circle(48));
                    symbol(bend,0,.2,1.7,angle,false,1,24,false,new double[][]{{0,0},{0,1}});
                    for(int i=0;i<samples;i++) dot(u(i),0,0,pulse(u(i),bend)*Math.pow(Math.max(0,Math.cos(angle)),4));
                }
                case WALKING_GHOST -> walkingGhost();
                default -> throw new IllegalArgumentException("Not a geometry pattern: " + pattern);
            }
        }

        void moth() {
            AnimationPoint guide=(AnimationPoint)f.state().get("moth-guide");
            double wait=param("wait-distance",3,1,12), ahead=param("look-ahead",5,1,20);
            if(guide==null || guide.distanceSquared(f.playerPosition())<wait*wait || guide.distanceSquared(f.playerPosition())>24*24) {
                guide=f.path().at(Math.min(ahead,f.path().length())); f.state().put("moth-guide",guide);
            }
            double flap=Math.sin(t*TAU*8), orbit=t*TAU;
            for(int side:new int[]{-1,1}) for(int i=0;i<dense(24);i++) { double a=TAU*i/dense(24);
                double wing=side*(.12+.65*Math.sin(a/2)),along=Math.sin(a)*.4;
                sink.emit(guide.add((Math.cos(orbit)*.2+wing)*f.style().width()*f.style().scale()*shapeSize,
                        (.9+Math.abs(wing)*flap*.5)*f.style().height()*f.style().scale(),
                        (Math.sin(orbit)*.2+along)*f.style().width()*f.style().scale()*shapeSize),.9,i/(double)dense(24));
            }
            for(int i=0;i<dense(8);i++) sink.emit(guide.add(Math.cos(orbit)*.2*f.style().width()*f.style().scale()*shapeSize,
                    .9*f.style().height()*f.style().scale(),(Math.sin(orbit)*.2+(i/(double)(dense(8)-1)-.5)*.7)*f.style().width()*f.style().scale()*shapeSize),1,.5);
            if(density>1) for(int side:new int[]{-1,1}) for(int vein=1;vein<=5;vein++) {
                double a=TAU*vein/6;
                for(int i=0;i<dense(3);i++) {double q=i/(double)(dense(3)-1),wing=side*(.12+.65*Math.sin(a/2))*q;
                    sink.emit(guide.add((Math.cos(orbit)*.2+wing)*f.style().width()*f.style().scale()*shapeSize,
                            (.9+Math.abs(wing)*flap*.5)*f.style().height()*f.style().scale(),
                            (Math.sin(orbit)*.2+Math.sin(a)*.4*q)*f.style().width()*f.style().scale()*shapeSize),.45,.5);}
            }
        }

        void clockMarks(double center,double side,double up,double radius,boolean upright,double light) {
            for(int i=0;i<12;i++) symbol(center,side,up,radius,i*TAU/12,upright,light,2,false,
                    new double[][]{{0,i%3==0?.7:.82},{0,1}});
        }

        void bodyLine(List<AnimationPoint> points,double center,double[] a,double[] b,int count) {
            count=dense(count);
            for(int i=0;i<count;i++) {double q=i/(double)(count-1);
                points.add(local(center).point((a[0]+(b[0]-a[0])*q)*shapeSize,
                        (a[1]+(b[1]-a[1])*q)*shapeSize,(a[2]+(b[2]-a[2])*q)*shapeSize));}
        }

        void bodyPanel(List<AnimationPoint> points,double center,double[] origin,double[] right,double[] up) {
            int grid=(int)Math.ceil(2*Math.sqrt(density));
            for(int i=1;i<grid;i++)for(int j=1;j<grid;j++) {
                double x=i/(double)grid,y=j/(double)grid;
                points.add(local(center).point((origin[0]+right[0]*x+up[0]*y)*shapeSize,
                        (origin[1]+right[1]*x+up[1]*y)*shapeSize,(origin[2]+right[2]*x+up[2]*y)*shapeSize));
            }
        }
        void limb(List<AnimationPoint> points,double center,double[] a,double[] b,int detail,double radius) {
            if(density<=1) {bodyLine(points,center,a,b,detail);return;}
            int steps=dense(2);double dx=b[0]-a[0],dy=b[2]-a[2],length=Math.max(.001,Math.hypot(dx,dy));
            for(int i=0;i<steps;i++)for(int j=0;j<4;j++) {
                double q=i/(double)(steps-1),angle=TAU*j/4,n=Math.sin(angle)*radius;
                points.add(local(center).point((a[0]+dx*q-dy/length*n)*shapeSize,
                        (a[1]+(b[1]-a[1])*q+Math.cos(angle)*radius)*shapeSize,(a[2]+dy*q+dx/length*n)*shapeSize));
            }
        }

        void walkingGhost() {
            double distance=param("look-ahead",2,0,20)+p*param("ghost-distance",8,1,30);
            double center=Math.min(1,distance/Math.max(.01,f.path().length()));
            double gait=TAU*t*param("gait-cycles",4,1,12),stride=param("stride",.38,.05,.8);
            List<AnimationPoint> body=new ArrayList<>();
            // Cuboid head and torso make a Minecraft-like humanoid, viewed from any direction.
            for(double y:new double[]{1.48,1.9}) {
                double[][] corners={{-.21,-.23,y},{.21,-.23,y},{.21,.23,y},{-.21,.23,y}};
                for(int i=0;i<4;i++)bodyLine(body,center,corners[i],corners[(i+1)%4],3);
            }
            for(int side:new int[]{-1,1}) {
                bodyLine(body,center,new double[]{-.21,side*.23,1.48},new double[]{-.21,side*.23,1.9},3);
                bodyLine(body,center,new double[]{.21,side*.23,1.48},new double[]{.21,side*.23,1.9},3);
            }
            for(double forward:new double[]{-.13,.13}) {
                double[][] torso={{forward,-.27,1.42},{forward,.27,1.42},{forward,.2,.84},{forward,-.2,.84}};
                for(int i=0;i<4;i++)bodyLine(body,center,torso[i],torso[(i+1)%4],3);
            }
            if(density>1) {
                for(double forward:new double[]{-.21,.21})bodyPanel(body,center,new double[]{forward,-.23,1.48},new double[]{0,.46,0},new double[]{0,0,.42});
                for(double side:new double[]{-.23,.23})bodyPanel(body,center,new double[]{-.21,side,1.48},new double[]{.42,0,0},new double[]{0,0,.42});
                for(double height:new double[]{1.48,1.9})bodyPanel(body,center,new double[]{-.21,-.23,height},new double[]{.42,0,0},new double[]{0,.46,0});
                for(double forward:new double[]{-.13,.13})bodyPanel(body,center,new double[]{forward,-.2,.84},new double[]{0,.4,0},new double[]{0,0,.58});
            }
            for(int side:new int[]{-1,1}) {
                double swing=Math.sin(gait+ (side==1?0:Math.PI))*stride,lift=Math.max(0,swing)*.45;
                double[] hip={0,side*.16,.84},knee={swing*.55,side*.17,.43+lift},foot={swing,side*.18,.06+lift};
                limb(body,center,hip,knee,5,.105);limb(body,center,knee,foot,5,.105);
                double[] shoulder={0,side*.32,1.35},elbow={-swing*.55,side*.36,1.08},hand={-swing,side*.36,.85};
                limb(body,center,shoulder,elbow,4,.085);limb(body,center,elbow,hand,4,.085);
            }
            // Black dust cannot fade by getting darker: dissolve the silhouette's density too.
            double opacity=Math.min(1,p/.08)*Math.pow(1-p,.65);
            int limit=Math.min(body.size(),sink.remaining());
            for(int j=0;j<limit;j++) {int i=j*body.size()/limit;
                if(noise(i+600)<opacity) sink.emit(body.get(i),opacity,center);}
        }

        private record Foot(List<AnimationPoint> points,double born,int number) {}
        @SuppressWarnings("unchecked")
        void footsteps(boolean borrowed) {
            List<Foot> feet=(List<Foot>)f.state().computeIfAbsent("footprints",key->new ArrayList<Foot>());
            int number=(int)Math.floor(t*markers);
            Integer previous=(Integer)f.state().get("footstep-number");
            if(previous==null||previous!=number) {
                f.state().put("footstep-number",number);
                double center=.12+frac(number/(double)markers)*.55;
                List<AnimationPoint> points=new ArrayList<>();
                int side=number%2==0?-1:1;
                double[][] outline=shoe(side);
                for(int i=0;i<outline.length;i++) {
                    double[] a=outline[i],b=outline[(i+1)%outline.length];
                    for(int j=0;j<dense(2);j++) {double q=j/(double)dense(2);
                        points.add(local(center).point((a[1]+(b[1]-a[1])*q)*shapeSize,
                                side*.35+(a[0]+(b[0]-a[0])*q)*shapeSize,0));}
                }
                if(density>1) for(int row=0;row<5;row++)for(int j=0;j<dense(2);j++) {
                    double forward=-.3+row*.15,lateral=-.12+j/(double)(dense(2)-1)*.24;
                    points.add(local(center).point(forward*shapeSize,side*.35+lateral*shapeSize,0));
                }
                feet.add(new Foot(List.copyOf(points),f.elapsedSeconds(),number));
            }
            double lifetime=f.style().periodSeconds()*.8;
            feet.removeIf(foot->f.elapsedSeconds()-foot.born>lifetime);
            while(feet.size()>markers*2)feet.removeFirst();
            for(Foot foot:feet) {
                double age=(f.elapsedSeconds()-foot.born)/lifetime;
                if(borrowed&&noise(foot.number)>.45&&age>.2&&age<.45)continue;
                for(AnimationPoint point:foot.points) sink.emit(point,fade(age),frac(foot.number/(double)markers));
            }
        }
    }
    private static double clamp(double x) { return Math.clamp(x,0,1); }
    private static double frac(double x) { return x-Math.floor(x); }
    private static double random(long x) { x=(x^(x>>>30))*0xbf58476d1ce4e5b9L; x=(x^(x>>>27))*0x94d049bb133111ebL; return ((x^(x>>>31))>>>11)*0x1.0p-53; }
}
