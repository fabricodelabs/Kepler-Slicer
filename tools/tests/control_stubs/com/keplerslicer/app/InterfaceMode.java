package com.keplerslicer.app;
final class InterfaceMode {
    static boolean tablet(android.content.Context c){return "tablet".equals(c.getSharedPreferences("app_preferences",0).getString("interface_mode","mobile"));}
}
