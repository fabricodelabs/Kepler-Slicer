package com.keplerslicer.app;
final class CurrentModelStore {
    static boolean present=true;
    static String selectionKey(android.content.Context context)throws java.io.IOException {if(!present)throw new java.io.IOException("missing");return "file";}
}
