package com.keplerslicer.app;
final class SliceActivity {
    static final String EXTRA_AUTO_SLICE="kepler_auto_slice",EXTRA_AUTO_SLICE_REQUEST="kepler_auto_slice_request";
    static boolean working;
    static int inlineStarts;
    static boolean isSlicing(){return working;}
    /** Tablet: slicing on the plate; the stub only counts calls. */
    static boolean start(android.content.Context context,long request){inlineStarts++;return true;}
}
