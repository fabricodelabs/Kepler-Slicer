package com.keplerslicer.engine;

/** Android JNI bridge to the pinned OrcaSlicer engine. Calls run on serialized workers. */
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
    /** Vendor, name and status triples; only the vendor trees supplied in data/system are enumerated. */
    public static native String[] listSystemCompatibility(String printerVendor, String printerName, String kind,
            String processVendor, String processName, String resourcesPath, String dataPath);

    /** Transform array: angles XYZ in radians, scales XYZ, offsets XY in millimetres. */
    public static native String sliceSystemProfilesTransformed(String printerVendor, String processVendor, String filamentVendor,
            String printerName, String processName, String filamentName, String modelPath,
            String resourcesPath, String dataPath, String jobDirectory, double[] transform);
    public static native byte[] processSettingsMetadata(String pv, String qv, String fv, String pn, String qn, String fn, String resources, String data);
    public static native double[] printerBuildVolume(String vendor, String name, String resources, String data);
    public static native void validateProcessSettings(String pv, String qv, String fv, String pn, String qn, String fn, String resources, String data, String settings);
    public static native String sliceSystemProfilesWithSettings(String pv, String qv, String fv, String pn, String qn, String fn,
            String model, String resources, String data, String job, double[] transform, String settings);
    public static native String sliceModelWithSettings(String pv, String qv, String fv, String pn, String qn, String fn,
            String model, String resources, String data, String job, double[] transform, String settings, int plateIndex);
    /** -1 legacy; 0 geometry; 1/2 full project with selected/embedded printer; 3 private verified STEP derivative. */
    public static native String sliceProjectWithSettings(String pv,String qv,String fv,String pn,String qn,String fn,
            String model,String resources,String data,String job,double[] transform,String settings,String materialEdits,int plateIndex,
            int projectMode,boolean allowMixedTemperatures);
    public static native byte[] projectSettingsSnapshot(String pv,String qv,String fv,String pn,String qn,String fn,
            String resources,String data,String settings,String materialEdits,String model,int plateIndex);
    public static native byte[] projectMaterialSettings(String pv,String qv,String fv,String pn,String qn,String fn,
            String resources,String data,String settings,String model,int plateIndex,int projectMode,String materialEdits,
            int changedSlot,String newColour,boolean recalculate);
    public static native byte[] resolvedSettingsSnapshot(String pv,String qv,String fv,String pn,String qn,String fn,
            String resources,String data,String settings);
    public static native String prepareStep(String source,String resources,String data,String job,double linearMm,double angularRad,boolean split);
    public static native String cloneModelWithPreferences(String pv,String qv,String fv,String pn,String qn,String fn,
            String model,String resources,String data,String job,double[] transform,String settings,int objectIndex,int instanceIndex,int copies,boolean arrange);
    public static native String setNativeLogLevel(String level);
    public static native byte[] runNativePreferencesSelfTest(String resources,String data);
}
