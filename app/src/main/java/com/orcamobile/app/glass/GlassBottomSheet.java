package com.orcamobile.app.glass;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.orcamobile.app.R;

/** Native modal sheet used by gallery examples; the caller owns the selected value. */
public final class GlassBottomSheet {
    public interface Selection {void chosen(int index);}
    private GlassBottomSheet(){}
    public static void choose(Context context,View anchor,boolean reduced,String title,String[] values,int selected,Selection callback){
        Dialog dialog=new Dialog(context);GlassUi ui=new GlassUi(context,anchor,reduced);LinearLayout body=ui.vertical();ui.pad(body,16,16,16,16);body.addView(ui.heading(title));
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);LinearLayout rows=ui.vertical();scroll.addView(rows);for(int i=0;i<values.length;i++){final int index=i;TextView check=ui.value(i==selected?"✓":"");View row=ui.row(values[i],check,()->{dialog.dismiss();if(callback!=null)callback.chosen(index);});row.setSelected(i==selected);rows.addView(row);if(i+1<values.length)rows.addView(ui.divider());}
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));TextView close=ui.action(context.getString(R.string.glass_dismiss),false,dialog::dismiss);LinearLayout.LayoutParams button=new LinearLayout.LayoutParams(-1,ui.dp(48));button.topMargin=ui.dp(12);body.addView(close,button);
        open(dialog,ui.surface(body,ui.tokens.cardRadius),ui.dp(Math.min(480,130+values.length*52)));
    }
    public static void info(Context context,View anchor,boolean reduced,String title,String message){
        Dialog dialog=new Dialog(context);GlassUi ui=new GlassUi(context,anchor,reduced);LinearLayout body=ui.vertical();ui.pad(body,20,20,20,20);body.addView(ui.heading(title));ui.space(body,12);TextView description=ui.text(message,14,400,ui.tokens.textPrimary);body.addView(description);ui.space(body,16);body.addView(ui.action(context.getString(R.string.glass_dismiss),false,dialog::dismiss),new LinearLayout.LayoutParams(-1,ui.dp(48)));open(dialog,ui.surface(body,ui.tokens.cardRadius),-2);
    }
    private static void open(Dialog dialog,View content,int height){dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(content);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);android.view.WindowManager.LayoutParams p=window.getAttributes();p.dimAmount=.45f;window.setAttributes(p);}dialog.show();if(window!=null)window.setLayout(-1,height);}
}
