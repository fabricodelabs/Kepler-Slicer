package com.orcamobile.app;
final class OrcaProfileCatalog {
    static boolean present=true;
    static final class Profile {boolean imported;}
    static Profile loadSelection(android.content.Context context){return present?new Profile():null;}
}
