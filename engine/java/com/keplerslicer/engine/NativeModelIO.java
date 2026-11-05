package com.keplerslicer.engine;
/** Native model inspection uses the same slicing engine lock as slicing. */
public final class NativeModelIO {
    static { System.loadLibrary("kepler_slicer_experimental"); }
    private NativeModelIO() {}
    public static native byte[] inspect(String path, String resources, String data, int plateIndex, int maxTriangles);
}
