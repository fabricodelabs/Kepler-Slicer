package com.keplerslicer.app;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Worker-only immutable inputs shared by native preference actions. */
final class EngineProfileSession {
    final ProcessOverridesStore.Snapshot selection;
    final ProcessOverridesStore.Identity id;
    final File resources,data;
    private EngineProfileSession(ProcessOverridesStore.Snapshot s,CompatibilityResources r,File fullResources){selection=s;id=s.identity;resources=fullResources;data=r.data;}
    static EngineProfileSession capture(Context c)throws IOException {
        ProcessOverridesStore.Snapshot s=ProcessOverridesStore.snapshotForSlice(c);
        ProcessOverridesStore.Identity id=s.identity;
        File fullResources=EngineResources.prepare(c);
        return new EngineProfileSession(s,CompatibilityResources.prepareTriple(c,id.printer.vendor,id.process.vendor,id.filament.vendor,id.profileSettings),fullResources);
    }
    File newJob(String purpose){return new File(data,purpose+"-"+UUID.randomUUID());}
    boolean stillCurrent(Context c)throws IOException {
        ProcessOverridesStore.Snapshot now=ProcessOverridesStore.snapshotForSlice(c);
        return id.fingerprint.equals(now.identity.fingerprint)&&selection.overrides.equals(now.overrides);
    }
}
