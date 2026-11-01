package com.keplerslicer.engine;

/** JNI/toolchain probe only; no slicing functionality is provided. */
public final class NativeVersion {
    static { System.loadLibrary("kepler_upstream_probe"); }
    private NativeVersion() {}
    public static native int compare(String left, String right);
}
