package com.keplerslicer.app;
final class ProcessOverridesStore {
    static String overrides="{}";
    static boolean stale;
    static String load(android.content.Context c,Identity id)throws java.io.IOException {
        if(stale)throw new java.io.IOException("Captured profile selection changed");return overrides;
    }
    static final class Profile { final String vendor; Profile(String vendor){this.vendor=vendor;} }
    static final class Identity {
        final Profile printer=new Profile("printer_vendor"), process=new Profile("process_vendor"), filament=new Profile("filament_vendor");
        final String printerName="printer_name",processName="process_name",filamentName="filament_name";
        final SettingsState.Snapshot profileSettings=new SettingsState.Snapshot();
    }
}
