package com.keplerslicer.app.glass;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.keplerslicer.app.R;

/** Native modal sheet used by gallery examples; the caller owns the selected value. */
public final class GlassBottomSheet {
    public interface Selection {void chosen(int index);}
    private GlassBottomSheet(){}
    public static void choose(Context context,View anchor,boolean reduced,String title,String[] values,int selected,Selection callback){
        SheetDialog dialog=new SheetDialog(context);GlassUi ui=new GlassUi(context,anchor,reduced);LinearLayout body=ui.vertical();ui.pad(body,16,16,16,16);
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);LinearLayout rows=ui.vertical();scroll.addView(rows);rows.addView(ui.heading(title));for(int i=0;i<values.length;i++){final int index=i;TextView check=ui.value(i==selected?"✓":"");View row=ui.row(values[i],check,()->{dialog.dismiss();if(callback!=null)callback.chosen(index);});row.setSelected(i==selected);rows.addView(row);if(i+1<values.length)rows.addView(ui.divider());}
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));TextView close=ui.action(context.getString(R.string.glass_dismiss),false,dialog::dismiss);LinearLayout.LayoutParams button=desktopButton(context,ui);button.topMargin=ui.dp(12);body.addView(close,button);
        open(dialog,ui.surface(body,ui.tokens.cardRadius),ui.dp(Math.min(480,130+values.length*52)),anchor);
    }
    public static void info(Context context,View anchor,boolean reduced,String title,String message){title=com.keplerslicer.app.EngineText.clean(title);message=com.keplerslicer.app.EngineText.clean(message);
        SheetDialog dialog=new SheetDialog(context);GlassUi ui=new GlassUi(context,anchor,reduced);LinearLayout body=ui.vertical();ui.pad(body,20,20,20,20);TextView heading=ui.heading(title);
        TextView description=ui.text(message,14,400,ui.tokens.textPrimary);ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);LinearLayout text=ui.vertical();text.addView(heading);ui.space(text,12);text.addView(description);scroll.addView(text);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        ui.space(body,16);body.addView(ui.action(context.getString(R.string.glass_dismiss),false,dialog::dismiss),desktopButton(context,ui));
        Rect visible=visibleBounds(anchor);int width=Math.max(1,Math.min(Desktop.is(context)?ui.dp(480):Integer.MAX_VALUE,visible.width()-ui.dp(40)));int w=View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),h=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);
        heading.measure(w,h);description.measure(w,h);int desired=ui.dp(20+12+16+48+20)+heading.getMeasuredHeight()+description.getMeasuredHeight();
        open(dialog,ui.surface(body,ui.tokens.cardRadius),desired,anchor);
    }
    /** Desktop (tablet) dialogs keep a compact button on the right; the mobile sheet uses a full-width one. */
    private static LinearLayout.LayoutParams desktopButton(Context context,GlassUi ui){if(!Desktop.is(context))return new LinearLayout.LayoutParams(-1,ui.dp(48));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(140),ui.dp(44));p.gravity=Gravity.END;return p;}
    private static Rect visibleBounds(View anchor){Rect r=new Rect();anchor.getWindowVisibleDisplayFrame(r);if(r.width()<=0||r.height()<=0){android.util.DisplayMetrics d=anchor.getResources().getDisplayMetrics();r.set(0,0,d.widthPixels,d.heightPixels);}return r;}
    private static int availableHeight(View anchor){int height=visibleBounds(anchor).height();
        if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsets in=anchor.getRootWindowInsets();if(in!=null){android.graphics.Insets safe=in.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());int rootHeight=anchor.getRootView().getHeight();if(rootHeight>0)height=Math.min(height,Math.max(1,rootHeight-safe.top-safe.bottom));}}
        return Math.max(1,height);
    }
    private static void open(SheetDialog dialog,View content,int desiredHeight,View anchor){dialog.setContentView(content);dialog.preferredHeight(Math.max(1,desiredHeight)).maxFraction(.9f);dialog.show();}
}
