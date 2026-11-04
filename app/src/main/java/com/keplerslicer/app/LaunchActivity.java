package com.keplerslicer.app;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.keplerslicer.app.glass.GlassBackground;
import com.keplerslicer.app.glass.GlassTypography;

/** Android task/intent routing. All model bytes still pass through the existing import path. */
public final class LaunchActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);if(GeneralPreferences.showSplash(this)){
        LinearLayout view=new LinearLayout(this);view.setGravity(Gravity.CENTER);view.setOrientation(LinearLayout.VERTICAL);view.setBackground(GlassBackground.create(this));TextView title=new TextView(this);title.setText("Kepler Slicer");title.setTextColor(android.graphics.Color.WHITE);title.setTextSize(28);GlassTypography.apply(title,600);view.addView(title);TextView detail=new TextView(this);detail.setText(R.string.general_starting);detail.setTextColor(android.graphics.Color.WHITE);view.addView(detail);setContentView(view);
        view.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){view.getViewTreeObserver().removeOnPreDrawListener(this);view.postOnAnimation(LaunchActivity.this::launch);return true;}});
    }else launch();}
    private void launch(){if(isFinishing())return;Intent source=getIntent();android.net.Uri uri=GeneralPreferences.modelUri(source);Intent workspace=new Intent(this,MainActivity.class);
        if(uri!=null)workspace.setAction(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        else if("home".equals(GeneralPreferences.defaultPage(this)))workspace.putExtra("general_start_home",true);
        // One shared private workspace: reactivate it instead of creating racing tasks.
        workspace.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(workspace);finish();}
}
