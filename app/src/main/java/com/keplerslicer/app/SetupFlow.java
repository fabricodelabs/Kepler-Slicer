package com.keplerslicer.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Mandatory printer setup: printer and nozzle, then filament, then process (layer height). The workspace opens only
 * when all three are chosen. Changing the printer or nozzle starts the filament and process steps again, because
 * the old ones may not fit the new printer.
 */
final class SetupFlow {
    static final String EXTRA = "kepler_setup";
    private static final String PREFS = "kepler_setup", STAGE = "stage";
    static final String FILAMENT_STAGE = "filament", PROCESS_STAGE = "process";

    private SetupFlow() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** Pending step after a printer change, or null. */
    static String stage(Context c) { return prefs(c).getString(STAGE, null); }

    /** Step that still has to be done before the workspace, or null when setup is complete. */
    static String missing(Context c) {
        PrinterProfileCatalog.Profile printer = PrinterProfileCatalog.loadSelection(c);
        if (printer == null) return "printer";
        // User printer profiles carry their own filament and process; the catalog pickers do not apply to them.
        if (printer.imported || printer.path == null || printer.path.isEmpty()) return null;
        String stage = stage(c);
        if (FILAMENT_STAGE.equals(stage) || PrintProfileCatalog.loadSelection(c, PrintProfileCatalog.FILAMENT) == null) return FILAMENT_STAGE;
        if (PROCESS_STAGE.equals(stage) || PrintProfileCatalog.loadSelection(c, PrintProfileCatalog.PROCESS) == null) return PROCESS_STAGE;
        return null;
    }

    static boolean complete(Context c) { return missing(c) == null; }

    /** The printer or nozzle changed: filament and process must be chosen again. */
    static void printerChanged(Context c) { prefs(c).edit().putString(STAGE, FILAMENT_STAGE).apply(); }

    static void filamentChosen(Context c) { prefs(c).edit().putString(STAGE, PROCESS_STAGE).apply(); }

    static void processChosen(Context c) { prefs(c).edit().remove(STAGE).apply(); }

    static Intent picker(Context c, String kind) {
        return new Intent(c, ProfilePickerActivity.class).putExtra(ProfilePickerActivity.EXTRA_KIND, kind).putExtra(EXTRA, true);
    }

    /** Opens the remaining steps on top of {@code from}, keeping earlier steps in the back stack. */
    static void continueFrom(Activity from) {
        String step = missing(from);
        if (step == null) { backToWorkspace(from); return; }
        if (FILAMENT_STAGE.equals(step)) from.startActivity(picker(from, PrintProfileCatalog.FILAMENT));
        else if (PROCESS_STAGE.equals(step)) {
            // Filament stays below the process step, so Back from the process returns to the filament.
            from.startActivities(new Intent[]{picker(from, PrintProfileCatalog.FILAMENT), picker(from, PrintProfileCatalog.PROCESS)});
        }
    }

    /** MainActivity is singleTask: this closes every setup screen above it. */
    static void backToWorkspace(Activity from) {
        from.startActivity(new Intent(from, MainActivity.class));
        from.finish();
    }
}
