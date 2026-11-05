package com.keplerslicer.app.glass;

import android.animation.TimeInterpolator;

/**
 * Damped spring as an interpolator, parameterised like Apple's springs: damping ratio (1 = no bounce, lower =
 * a little bounce) and response (seconds for one undamped period). Liquid Glass motion uses these instead of
 * fixed bezier curves; {@link #duration()} is the time the spring takes to settle.
 */
public final class Spring implements TimeInterpolator {
    /** Smooth: no bounce (iOS .smooth). */
    public static Spring smooth(float response) { return new Spring(1f, response); }
    /** Snappy: a hint of bounce (iOS .snappy). */
    public static Spring snappy(float response) { return new Spring(0.86f, response); }
    /** Bouncy: visible but brief bounce, for glass morphing and droplets (iOS .bouncy). */
    public static Spring bouncy(float response) { return new Spring(0.72f, response); }

    private final float zeta, omega; private final long durationMs;

    public Spring(float dampingRatio, float responseSeconds) {
        zeta = Math.max(0.05f, Math.min(1f, dampingRatio));
        omega = (float) (2 * Math.PI / Math.max(0.05f, responseSeconds));
        durationMs = Math.round(Math.log(1000) / (zeta * omega) * 1000);
    }

    public long duration() { return durationMs; }

    @Override public float getInterpolation(float input) {
        double t = input * durationMs / 1000.0;
        if (zeta >= 0.999f) return (float) (1 - Math.exp(-omega * t) * (1 + omega * t));
        double wd = omega * Math.sqrt(1 - zeta * zeta);
        return (float) (1 - Math.exp(-zeta * omega * t) * (Math.cos(wd * t) + zeta * omega / wd * Math.sin(wd * t)));
    }
}
