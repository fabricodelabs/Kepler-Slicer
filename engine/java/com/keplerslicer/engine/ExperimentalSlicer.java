package com.keplerslicer.engine;

/** Unintegrated experiment. Requires a separately linked upstream libslic3r. */
public final class ExperimentalSlicer {
    static { System.loadLibrary("kepler_slicer_experimental"); }
    private ExperimentalSlicer() {}

    /** The job directory must be a new direct child of the app-private data directory. */
    public static native String sliceStl(String modelPath, String configPath, String jobDirectory,
                                         String resourcesPath, String dataPath);

    /** Resolve three exact system presets into an INI in a new private job directory. */
    public static native String resolveSystemProfiles(String printerVendor, String processVendor, String filamentVendor,
                                                       String printerName, String processName, String filamentName,
                                                       String resourcesPath, String dataPath, String jobDirectory);
    public static native String sliceSystemProfiles(String printerVendor, String processVendor, String filamentVendor, String printerName, String processName, String filamentName, String modelPath, String resourcesPath, String dataPath, String jobDirectory);

    /** Transform array: angles XYZ in radians, scales XYZ, offsets XY in millimetres. */
    public static native String sliceSystemProfilesTransformed(String printerVendor, String processVendor, String filamentVendor,
            String printerName, String processName, String filamentName, String modelPath,
            String resourcesPath, String dataPath, String jobDirectory, double[] transform);
}
