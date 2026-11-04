package com.keplerslicer.app;

/** Names of stored preferences, secrets and folders. {@link StorageMigration} moves data saved under older names. */
public final class StorageNames {
    public static final String PRINT_PROFILES = "kepler_print_profiles";
    public static final String PROFILE_SELECTION = "kepler_profile_selection";
    public static final String CLOUD_TOKEN = "kepler_cloud_token";
    public static final String CLOUD_PROFILES_DIR = "kepler-cloud-profiles";
    private StorageNames() {}
}
