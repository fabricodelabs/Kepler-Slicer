package com.keplerslicer.app;
final class PrinterProfileCatalog {
    static boolean present=true;
    static final class Profile {boolean imported;}
    static Profile loadSelection(android.content.Context context){return present?new Profile():null;}
}
