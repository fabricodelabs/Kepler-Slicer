package com.keplerslicer.engine;

/**
 * Bambu Lab printer connection through open-bamboo-networking (https://github.com/ClusterM/open-bamboo-networking,
 * AGPL-3.0), the open replacement of the Bambu network plugin used by OrcaSlicer and Bambu Studio. One agent per
 * process; calls block and must run off the main thread.
 */
public final class BambuNetworking {
    private static boolean loaded;
    private BambuNetworking() {}

    /** Loads libbambu_networking.so and creates the agent; {@code certDir}/{@code certFile} is the Bambu CA bundle. */
    public static synchronized void init(String logDir, String configDir, String certDir, String certFile) {
        if (!loaded) { System.loadLibrary("bambu_networking"); loaded = true; }
        if (!nativeInit(logDir, configDir, certDir, certFile)) throw new IllegalStateException("open-bamboo-networking agent could not start");
    }

    public static native boolean nativeInit(String logDir, String configDir, String certDir, String certFile);
    /** 0 when connected, a negative plugin status otherwise. */
    public static native int nativeConnect(String serial, String ip, String user, String accessCode, int timeoutMs);
    public static native int nativeSend(String serial, String json);
    /** Next MQTT report of the printer, or null after the timeout. */
    public static native String nativeNextMessage(String serial, int timeoutMs);
    public static native void nativeDrop(String serial);
    public static native void nativeCancel();
    /** FTPS upload without starting the print; 0 or a negative plugin status. */
    public static native int nativeUpload(String serial, String ip, String accessCode, String localPath, String remoteName);
    /** Signs in with the account token envelope and connects the account broker; 0 when connected. */
    public static native int nativeCloudConnect(String userInfoJson, String countryCode, int timeoutMs);
    public static native int nativeCloudWatch(String serial);
    public static native int nativeCloudSend(String serial, String json);
    /** Plugin message of the last LAN connect attempt, empty when none. */
    public static native String nativeLastError(String serial);
}
