package com.keplerslicer.app;
final class SliceActivity {
    static final String EXTRA_AUTO_SLICE="kepler_auto_slice",EXTRA_AUTO_SLICE_REQUEST="kepler_auto_slice_request";
    static boolean working;
    static boolean isSlicing(){return working;}
    /** Slicing on the plate: the stub records each start on the activity, like the former slice-screen intent. */
    static boolean start(android.content.Context context,long request){
        if(context instanceof android.app.Activity)((android.app.Activity)context).started.add(new android.content.Intent(context,SliceActivity.class).putExtra(EXTRA_AUTO_SLICE,true).putExtra(EXTRA_AUTO_SLICE_REQUEST,request));
        return true;
    }
}
