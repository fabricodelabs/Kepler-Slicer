package com.orcamobile.app.glass;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.orcamobile.app.R;
import java.util.function.Consumer;

/** Figma v3 Settings hub (node 6:368) presentation primitives; geometry and fills follow figma-reference.tsx. */
public final class SettingsHubUi {
    public static final int WHITE=0xFFFFFFFF,SECONDARY=0xE0FFFFFF,ON_ACTIVE=0xFF16181C,BASE=0xFF383D46;
    private static final int FILL=0x1CFFFFFF,STROKE=0x33FFFFFF,FIELD=0x38000000,FIELD_STROKE=0x2EFFFFFF,DIVIDER=0x1FFFFFFF;
    private final Context context;
    private final float density;

    public static final class Picker {
        public final TextView name,sub;
        final LinearLayout box;
        Picker(LinearLayout box,TextView name,TextView sub){this.box=box;this.name=name;this.sub=sub;}
        public View view(){return box;}
    }

    public SettingsHubUi(Context context){this.context=context;density=context.getResources().getDisplayMetrics().density;}
    public int dp(float v){return Math.round(v*density);}
    public Drawable drawable(int res){return context.getDrawable(res);}

    /** Scoped #383d46 backdrop with the three original Figma glows at their frame positions (dp from the window's top-left). */
    public static Drawable backdrop(Context context){
        final float d=context.getResources().getDisplayMetrics().density;
        final int[] res={R.drawable.kepler_v3_settings_glow_top,R.drawable.kepler_v3_settings_glow_right,R.drawable.kepler_v3_settings_glow_bottom};
        final float[][] box={{-430,-460,860,920},{40,0,700,930},{-290,460,700,760}};
        final Drawable[] glows=new Drawable[3];
        for(int i=0;i<3;i++){glows[i]=context.getDrawable(res[i]).mutate();glows[i].setBounds(Math.round(box[i][0]*d),Math.round(box[i][1]*d),Math.round((box[i][0]+box[i][2])*d),Math.round((box[i][1]+box[i][3])*d));}
        return new Drawable(){
            @Override public void draw(Canvas c){c.drawColor(BASE);for(Drawable g:glows)g.draw(c);}
            @Override public void setAlpha(int a){}
            @Override public void setColorFilter(ColorFilter f){}
            @Override public int getOpacity(){return PixelFormat.OPAQUE;}
        };
    }

    public TextView text(String value,float sp,int weight,int color){TextView t=new TextView(context);t.setText(value);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,sp);t.setTextColor(color);GlassTypography.apply(t,weight);t.setIncludeFontPadding(false);t.setLineSpacing(0,1.3f);return t;}

    private GradientDrawable shape(int fill,int stroke,float radiusDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radiusDp));if(stroke!=0)g.setStroke(Math.max(1,dp(1)),stroke);return g;}
    private void pressable(View v,int fill,int stroke,float radiusDp){v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),fill==0?null:shape(fill,stroke,radiusDp),shape(WHITE,0,radiusDp)));v.setClickable(true);v.setFocusable(true);}
    private ImageView image(int res,float sizeDp){ImageView i=new ImageView(context);i.setImageResource(res);i.setScaleType(ImageView.ScaleType.FIT_CENTER);i.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp),dp(sizeDp)));i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return i;}

    public LinearLayout topBar(View title,View... actions){
        LinearLayout bar=new LinearLayout(context);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(20),dp(8),dp(20),0);
        bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        for(View action:actions){LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(44),dp(44));ap.leftMargin=dp(12);bar.addView(action,ap);}
        bar.setMinimumHeight(dp(56));return bar;
    }

    /** 44dp circular glass button (22dp art). */
    public View circleButton(Drawable art,String description,Runnable action){
        FrameLayout f=new FrameLayout(context);ImageView i=new ImageView(context);i.setImageDrawable(art);i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);f.addView(i,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
        pressable(f,FILL,STROKE,22);f.setContentDescription(description);f.setOnClickListener(v->action.run());return f;
    }

    /** 44dp target with 22dp art and no fill (card header action). */
    public View iconButton(int res,String description,Runnable action){
        FrameLayout f=new FrameLayout(context);f.addView(image(res,22),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
        pressable(f,0,0,22);f.setContentDescription(description);f.setOnClickListener(v->action.run());f.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));return f;
    }

    /** Same 44dp unfilled target for a non-Figma-asset glyph (the existing GlassIcon gear). */
    public View iconButton(Drawable art,String description,Runnable action){
        FrameLayout f=new FrameLayout(context);ImageView i=new ImageView(context);i.setImageDrawable(art);i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);f.addView(i,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
        pressable(f,0,0,22);f.setContentDescription(description);f.setOnClickListener(v->action.run());f.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));return f;
    }

    public LinearLayout card(float radiusDp){LinearLayout c=new LinearLayout(context);c.setOrientation(LinearLayout.VERTICAL);c.setBackground(shape(FILL,STROKE,radiusDp));return c;}
    public View gap(){View v=new View(context);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(10)));return v;}
    public View divider(){View v=new View(context);v.setBackgroundColor(DIVIDER);v.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.max(1,dp(1))));return v;}

    /** Header: icon20, title16/700, then 44dp actions; left16/right8/top8/bottom4. */
    public LinearLayout cardHeader(int iconRes,String title,View... trailing){
        LinearLayout h=new LinearLayout(context);h.setOrientation(LinearLayout.HORIZONTAL);h.setGravity(Gravity.CENTER_VERTICAL);h.setPadding(dp(16),dp(8),dp(8),dp(4));
        ImageView ic=image(iconRes,20);h.addView(ic);TextView t=text(title,16,700,WHITE);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(8);tp.rightMargin=dp(8);h.addView(t,tp);
        for(View v:trailing){if(v.getLayoutParams()==null)v.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));h.addView(v);}
        h.setMinimumHeight(dp(52));return h;
    }

    public View thumb(int res){FrameLayout f=new FrameLayout(context);f.setBackground(shape(FILL,0,12));f.addView(image(res,24),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));f.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));return f;}
    public View slotChip(String number){TextView t=text(number,12,800,WHITE);t.setGravity(Gravity.CENTER);GradientDrawable g=shape(0xFF0F1114,WHITE,7);t.setBackground(g);t.setLayoutParams(new LinearLayout.LayoutParams(dp(24),dp(24)));return t;}

    /** Dark picker r18: inset 12/10, gap 12, 15/600 name, 12/500 subtitle, trailing chevron20. */
    public Picker picker(View leading,String name,Runnable action){
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setMinimumHeight(dp(52));
        pressable(row,FIELD,FIELD_STROKE,18);row.setOnClickListener(v->action.run());
        if(leading!=null){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)leading.getLayoutParams();lp.rightMargin=dp(12);row.addView(leading,lp);}
        LinearLayout col=new LinearLayout(context);col.setOrientation(LinearLayout.VERTICAL);
        TextView n=text(name,15,600,WHITE);n.setMaxLines(2);n.setEllipsize(android.text.TextUtils.TruncateAt.END);col.addView(n);
        TextView s=text("",12,500,SECONDARY);s.setMaxLines(2);s.setEllipsize(android.text.TextUtils.TruncateAt.END);s.setVisibility(View.GONE);col.addView(s);
        row.addView(col,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(20),dp(20));cp.leftMargin=dp(12);row.addView(image(R.drawable.kepler_v3_settings_chevron_down,20),cp);
        return new Picker(row,n,s);
    }
    public void setSubtitle(TextView sub,String value){sub.setText(value);sub.setVisibility(value==null||value.isEmpty()?View.GONE:View.VISIBLE);}
    public LinearLayout pickerGroup(int topDp,Picker... pickers){
        LinearLayout g=new LinearLayout(context);g.setOrientation(LinearLayout.VERTICAL);g.setPadding(dp(12),dp(topDp),dp(12),dp(12));
        for(Picker p:pickers)g.addView(p.view(),new LinearLayout.LayoutParams(-1,-2));return g;
    }

    /** Figma Advanced control: atom18 + 12/600 label + 46x28 switch inside a >=44dp checkable target. */
    public View advancedControl(String label,String description,boolean initial,Consumer<Boolean> onChange){
        final boolean[] on={initial};
        LinearLayout g=new LinearLayout(context);g.setOrientation(LinearLayout.HORIZONTAL);g.setGravity(Gravity.CENTER_VERTICAL);g.setPadding(dp(4),0,dp(6),0);g.setMinimumHeight(dp(44));
        g.addView(image(R.drawable.kepler_v3_settings_atom,18));
        TextView t=text(label,12,600,SECONDARY);t.setMaxWidth(dp(96));t.setMaxLines(2);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=dp(8);tp.rightMargin=dp(8);g.addView(t,tp);
        ImageView sw=new ImageView(context);sw.setImageResource(initial?R.drawable.kepler_v3_settings_switch_on:R.drawable.kepler_v3_settings_switch_off);sw.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);g.addView(sw,new LinearLayout.LayoutParams(dp(46),dp(28)));
        pressable(g,0,0,22);g.setContentDescription(description);
        g.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName("android.widget.Switch");info.setCheckable(true);info.setChecked(on[0]);}});
        g.setOnClickListener(v->{on[0]=!on[0];sw.setImageResource(on[0]?R.drawable.kepler_v3_settings_switch_on:R.drawable.kepler_v3_settings_switch_off);g.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);onChange.accept(on[0]);});
        return g;
    }

    /** Scope segmented group r16/p3; selected 32dp white r13 pill. Wraps the pair so long captions never overlap. */
    public View scope(String selected,String other,Runnable onSelected,Runnable onOther){
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(3),dp(3),dp(3),dp(3));row.setBackground(shape(FIELD,0,16));
        row.addView(segment(selected,true,onSelected));row.addView(segment(other,false,onOther));
        LinearLayout wrap=new LinearLayout(context);wrap.setPadding(dp(16),dp(6),dp(16),dp(10));wrap.addView(row,new LinearLayout.LayoutParams(-2,-2));return wrap;
    }
    private View segment(String caption,boolean selected,Runnable action){
        TextView t=text(caption,13,700,selected?ON_ACTIVE:WHITE);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(32));t.setPadding(dp(12),0,dp(12),0);t.setSingleLine(true);
        if(selected)t.setBackground(shape(WHITE,0,13));else pressable(t,0,0,13);
        t.setClickable(true);t.setFocusable(true);t.setOnClickListener(v->action.run());return t;
    }

    /** Row >=52dp: tile32/r10 + icon18, caption, trailing original chevron18. Divider is added by the caller via divider(). */
    public View row(int iconRes,String caption,float sp,Runnable action){
        LinearLayout r=new LinearLayout(context);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(16),dp(8),dp(14),dp(8));r.setMinimumHeight(dp(52));
        FrameLayout tile=new FrameLayout(context);tile.setBackground(shape(FILL,0,10));tile.addView(image(iconRes,18),new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER));r.addView(tile,new LinearLayout.LayoutParams(dp(32),dp(32)));
        TextView t=text(caption,sp,600,WHITE);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(12);tp.rightMargin=dp(12);r.addView(t,tp);
        r.addView(image(R.drawable.kepler_v3_settings_chevron_right,18));
        pressable(r,0,0,0);r.setOnClickListener(v->action.run());return r;
    }
}
