package com.keplerslicer.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.keplerslicer.app.glass.GlassNavigation;
import com.keplerslicer.app.glass.GlassTypography;
import com.keplerslicer.app.glass.KeplerUi;

/**
 * Shared surface, typography and touch targets for the remaining list/form screens, rendered in the
 * Kepler Glass UI v3 language (glow backdrop, glass cards, Manrope, white primary actions).
 */
final class MdUi {
    final Context context;
    final boolean dark;
    final int bg, surface, container, ink, muted, line;
    private LinearLayout pageRoot; private FrameLayout pageHost;
    MdUi(Context context) {
        this.context=context; dark=true;
        bg=KeplerUi.BASE; surface=KeplerUi.GLASS; container=KeplerUi.GLASS;
        ink=KeplerUi.WHITE; muted=KeplerUi.SECONDARY; line=KeplerUi.GLASS_STROKE;
    }
    static boolean themeDark(Context c) { return true; }
    static void prepare(Activity a) { a.setTheme(R.style.AppThemeDark); }
    static android.app.AlertDialog.Builder alert(Activity a) { return new android.app.AlertDialog.Builder(new android.view.ContextThemeWrapper(a,R.style.KeplerAlertDark)) {
        @Override public android.app.AlertDialog.Builder setMessage(CharSequence m) { return super.setMessage(EngineText.clean(m)); }
        @Override public android.app.AlertDialog.Builder setTitle(CharSequence t) { return super.setTitle(EngineText.clean(t)); }
        @Override public android.app.AlertDialog create() { return com.keplerslicer.app.glass.GlassDialog.style(super.create()); }
    }; }
    int dp(float n) { return (int)(n*context.getResources().getDisplayMetrics().density+.5f); }
    LinearLayout vertical() { LinearLayout l=new LinearLayout(context); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout horizontal() { LinearLayout l=new LinearLayout(context); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); }
    void pad(View v,int l,int t,int r,int b) { v.setPadding(dp(l),dp(t),dp(r),dp(b)); }
    void gap(LinearLayout l,int n) { l.addView(new View(context),lp(1,n)); }
    TextView text(String label,int size,boolean bold) { TextView t=new TextView(context); t.setText(label); t.setTextSize(size); t.setTextColor(ink); t.setIncludeFontPadding(false); GlassTypography.apply(t,bold?700:500); t.setLineSpacing(0,1.3f); return t; }
    GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if(color==KeplerUi.GLASS)d.setStroke(Math.max(1,dp(1)),KeplerUi.GLASS_STROKE); return d; }
    void clickable(View v,int color,int radius,Runnable action) { v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),color==bg?null:shape(color,radius),shape(0xffffffff,radius))); v.setFocusable(true); v.setClickable(true); v.setOnClickListener(w->action.run()); }
    /** Primary: white r26 h52 with on-active 16/700 label (Figma "Slice plate"); secondary: glass with white label. */
    TextView button(String label,boolean filled,Runnable action) { TextView b=text(label,filled?16:15,true); b.setGravity(Gravity.CENTER); b.setMinHeight(dp(52)); pad(b,20,12,20,12); b.setTextColor(filled?KeplerUi.ON_ACTIVE:ink); clickable(b,filled?KeplerUi.WHITE:KeplerUi.GLASS,26,action); return b; }
    void bars(Activity a,View root) {
        a.getWindow().setStatusBarColor(android.graphics.Color.rgb(74,79,89)); a.getWindow().setNavigationBarColor(android.graphics.Color.rgb(56,61,70));
        a.getWindow().getDecorView().setSystemUiVisibility(0);
        root.setBackground(new KeplerUi(a).backdrop());
        if(android.os.Build.VERSION.SDK_INT>=35) root.setOnApplyWindowInsetsListener((v,in)-> { android.graphics.Insets s=in.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()|android.view.WindowInsets.Type.ime()); v.setPadding(s.left,s.top,s.right,s.bottom); return in; });
    }
    /** Sub-screen header (Figma "Top bar"): 44dp glass back, centred 18/700 title, x20 y8. */
    LinearLayout page(Activity a,String title,Runnable back) {
        LinearLayout root=vertical(); pageRoot=root; FrameLayout host=new FrameLayout(context); pageHost=host; host.addView(root,new FrameLayout.LayoutParams(-1,-1)); bars(a,host); a.setContentView(host);
        KeplerUi k=new KeplerUi(a);
        LinearLayout top=k.subHeader(title,a.getString(R.string.kv3_back),back,null);
        LinearLayout.LayoutParams tp=lp(-1,-2); tp.setMargins(0,0,0,dp(8));
        ScrollView scroll=new ScrollView(context); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout frame=new FrameLayout(context); scroll.addView(frame); LinearLayout content=vertical(); pad(content,20,8,20,28);
        int width=(int)(context.getResources().getDisplayMetrics().widthPixels/context.getResources().getDisplayMetrics().density);
        frame.addView(content,new FrameLayout.LayoutParams(width>640?dp(600):-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL)); content.addView(top,tp); return content; // top bar scrolls with the content
    }
    /** Glass list card r24: 32dp tile r10 with the glyph, 16/700 title, 12/500 subtitle, original chevron. */
    LinearLayout row(String title,String subtitle,String glyph,Runnable action) {
        LinearLayout r=horizontal(); pad(r,16,14,14,14); r.setMinimumHeight(dp(72));
        if(glyph!=null) { int res=glyphIcon(glyph); View icon;
            if(res!=0){FrameLayout tile=new FrameLayout(context);ImageView art=new ImageView(context);art.setImageResource(res);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(art,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));icon=tile;}
            else {TextView t=text(glyph,18,false); t.setGravity(Gravity.CENTER); icon=t;}
            icon.setBackground(shape(KeplerUi.FIELD,12)); r.addView(icon,lp(40,40)); }
        LinearLayout names=vertical(); pad(names,glyph==null?0:14,0,12,0); names.addView(text(title,16,true));
        if(subtitle!=null&&!subtitle.isEmpty()) { gap(names,3); TextView sub=text(subtitle,12,false); sub.setTextColor(muted); names.addView(sub); }
        r.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null) { ImageView chevron=new ImageView(context); chevron.setImageResource(R.drawable.kv3_chevron_right); chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); r.addView(chevron,lp(20,20)); clickable(r,surface,24,action); } else r.setBackground(shape(surface,24));
        return r;
    }
    /** Original Figma Library icons for the legacy text glyphs. */
    private static int glyphIcon(String glyph) {
        switch(glyph) {
            case "⌁": return R.drawable.kv3_wifi; case "⌕": return R.drawable.kv3_search; case "＋": return R.drawable.kv3_plus;
            case "☁": return R.drawable.kv3_link; case "▣": case "▧": return R.drawable.kv3_printer; case "◇": return R.drawable.kv3_cube;
            case "✓": return R.drawable.kv3_check; case "↗": return R.drawable.kv3_share; default: return 0;
        }
    }
    void navigation(Activity a,int selected) { if(pageRoot==null)throw new IllegalStateException(L.t("Сначала создайте страницу.")); navigation(a,pageRoot,selected); }
    /** Figma v3 navigation (node 2:318) with the original routes through MainActivity. */
    void navigation(Activity a,LinearLayout parent,int selected) {
        FrameLayout shell=new FrameLayout(context);
        final GlassNavigation[] holder=new GlassNavigation[1];
        GlassNavigation nav=new GlassNavigation(a,Math.max(0,selected),false,index->{
            if(index==selected&&!(a instanceof ImportActivity)&&!(a instanceof GcodePreviewActivity))return;
            a.startActivity(new android.content.Intent(a,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("kepler_nav",index));
            if(holder[0]!=null)holder[0].post(()->holder[0].setSelectedItem(Math.max(0,selected)));
        }); holder[0]=nav;
        int width=(int)(context.getResources().getDisplayMetrics().widthPixels/context.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams n=new FrameLayout.LayoutParams(width>640?dp(600):-1,-2,Gravity.CENTER);n.setMargins(dp(16),dp(8),dp(16),dp(24));shell.addView(nav,n);parent.addView(shell,lp(-1,-2));
        if(parent==pageRoot&&pageHost!=null){parent.removeView(shell);shell.removeView(nav);KeplerUi.floatNavigation(pageHost,parent,nav,dp(16),dp(24));}
    }
}
