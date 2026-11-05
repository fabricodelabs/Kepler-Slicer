package com.keplerslicer.app;

import android.content.Context;

import java.io.IOException;

/** Plate choice of a 3MF project: the preview of that plate and, for projects with settings, its checked snapshot. */
final class ProjectPlates {
    private ProjectPlates() {}

    static CurrentModelStore select(Context context, String expectedFile, int plate) throws IOException {
        CurrentModelStore selected = CurrentModelStore.selectPlate(context, expectedFile, plate);
        if (CurrentModelStore.storedProjectMode(context) <= 0 || selected.metadata == null) return selected;
        try {
            EngineProfileSession session = EngineProfileSession.capture(context); ProcessOverridesStore.Identity id = session.id;
            ProjectSettingsSnapshot snapshot = ProjectSettingsSnapshot.parse(com.keplerslicer.engine.ExperimentalSlicer.projectSettingsSnapshot(
                id.printer.vendor, id.process.vendor, id.filament.vendor, id.printerName, id.processName, id.filamentName,
                session.resources.getAbsolutePath(), session.data.getAbsolutePath(), session.selection.overrides, "{}", selected.file.getAbsolutePath(), plate));
            selected.metadata.put("project_settings_snapshot", snapshot.json());
            CurrentModelStore.saveProjectSnapshot(context, expectedFile, snapshot.json());
            return selected;
        } catch (IOException e) { throw e; }
        catch (Exception | LinkageError e) { throw new IOException(e.getMessage() == null ? L.t("Не удалось прочитать выбранный стол проекта.") : e.getMessage(), e); }
    }
}
