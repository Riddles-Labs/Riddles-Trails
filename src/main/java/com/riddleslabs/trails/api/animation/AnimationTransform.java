package com.riddleslabs.trails.api.animation;

/** Immutable local coordinate frame. Reuse it for every vertex of a rigid shape. */
public record AnimationTransform(AnimationPoint origin,double forwardX,double forwardZ,
                                 double horizontalScale,double verticalScale) {
    public AnimationPoint point(double forward,double lateral,double vertical) {
        return origin.add((forwardX*forward-forwardZ*lateral)*horizontalScale,
                vertical*verticalScale,(forwardZ*forward+forwardX*lateral)*horizontalScale);
    }
}
