package com.orcamobile.app;
import android.content.Context;
final class ObjectSettingsStore {
 static String file="model-one.stl",fingerprint="objects-a";static int plate=-1;
 static final class Snapshot {final String modelFileName,fingerprint;final int plateIndex;Snapshot(){modelFileName=file;plateIndex=plate;fingerprint=ObjectSettingsStore.fingerprint;}}
 static Snapshot snapshot(Context c){return new Snapshot();}
}
