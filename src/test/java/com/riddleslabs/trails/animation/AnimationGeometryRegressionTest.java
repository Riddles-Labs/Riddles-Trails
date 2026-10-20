package com.riddleslabs.trails.animation;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationGeometryRegressionTest {
    @Test void performanceChangesPreserveAllRecordedParticleTraces() throws Exception {
        var config=BuiltInAnimationsTest.config();var actual=new ArrayList<String>();
        for(var entry:BuiltInAnimations.all().entrySet()) {
            var sink=new AnimationPerformanceTest.Sink();var style=config.style(entry.getKey());var state=new HashMap<String,Object>();
            for(int phase=0;phase<40;phase++) {
                sink.reset();entry.getValue().render(BuiltInAnimationsTest.frame(style,style.periodSeconds()*phase/40,1200,state),sink);
            }
            actual.add(entry.getKey()+" "+sink.count+" "+sink.signature);
        }
        assertEquals(Files.readAllLines(Path.of("src/test/resources/animation-geometry-traces.txt")),actual,
                "Particle counts and coordinates/intensities rounded to 1e-6 must match the 1.4.1 reference");
    }
}
