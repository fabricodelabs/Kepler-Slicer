package com.keplerslicer.engine;

/** Android JNI bridge to the pinned slicing engine. Calls run on serialized workers. */
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
    /** Tools sheet edits: op 1 cut at height a (b: 1 keep upper, 2 keep lower), op 2 text (a height, b depth), op 3 assembly. Returns a new 3MF in job. */
    public static native String editModel(String pv,String qv,String fv,String pn,String qn,String fn,
            String model,String resources,String data,String job,double[] transform,String settings,int op,double a,double b,String text,String fontPath);
    /** Project 3MF with the resolved settings and current placement; returns job/project.3mf. */
    public static native String exportProject3mf(String pv,String qv,String fv,String pn,String qn,String fn,
            String model,String resources,String data,String job,double[] transform,String settings);
    /** Objects and parts with their stored material slot: {"objects":[{"name","slot","parts":[{"name","slot"}]}]}. */
    public static native byte[] materialLayout(String pv,String qv,String fv,String pn,String qn,String fn,
            String model,String resources,String data,double[] transform,String settings);
    /** Multi-material project: extra slots, colours and object/part slots (JSON request); returns job/project.3mf. */
    public static native String assignMaterials(String pv,String qv,String fv,String pn,String qn,String fn,
            String model,String resources,String data,String job,double[] transform,String settings,String request);
    public static native String setNativeLogLevel(String level);
    public static native byte[] runNativePreferencesSelfTest(String resources,String data);
}
