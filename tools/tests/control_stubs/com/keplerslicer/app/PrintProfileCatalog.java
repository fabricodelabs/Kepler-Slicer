package com.keplerslicer.app;
final class PrintProfileCatalog {
    static final String PROCESS="process",FILAMENT="filament";
    static boolean present=true;
    static Object loadSelection(android.content.Context context,String kind){return present?new Object():null;}
}
