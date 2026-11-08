package com.keplerslicer.app;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Android task/intent routing. It draws nothing: the system splash (styles.xml, splash_blueprint.xml) stays until the
 * workspace has drawn, and on a cold start from the launcher the workspace plays the brand animation (BrandSplash) over
 * itself. All model bytes still pass through the existing import path.
 */
public final class LaunchActivity extends Activity {
    /** First launch in this process: warm starts and returns from the background get no animation. */
    private static boolean coldStart = true;

    @Override public void onCreate(Bundle state){super.onCreate(state);launch();}
    private void launch(){if(isFinishing())return;Intent source=getIntent();android.net.Uri uri=GeneralPreferences.modelUri(source);Intent workspace=new Intent(this,MainActivity.class);
        if(uri!=null)workspace.setAction(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        else if("home".equals(GeneralPreferences.defaultPage(this)))workspace.putExtra("general_start_home",true);
        boolean fromLauncher=source!=null&&Intent.ACTION_MAIN.equals(source.getAction());
        if(coldStart&&fromLauncher&&GeneralPreferences.showSplash(this))workspace.putExtra(BrandSplash.EXTRA,true);
        coldStart=false;
        // One shared private workspace: reactivate it instead of creating racing tasks.
        workspace.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(workspace);finish();overridePendingTransition(0,0);}
}
