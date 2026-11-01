package com.orcamobile.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared monochrome Material surface, typography and touch targets. */
final class MdUi {
    final Context context;
    final boolean dark;
    final int bg, surface, container, ink, muted, line;
    private LinearLayout pageRoot;
    MdUi(Context context) {
        this.context=context; dark=themeDark(context);
        bg=dark?0xff111311:0xfffafaf7; surface=dark?0xff1c1e1c:0xffeff0ec;
        container=dark?0xff2b2e29:0xffe2e5dd; ink=dark?0xffe4e6df:0xff1b1c1b;
        muted=dark?0xffacafa8:0xff696b68; line=dark?0xff3b3d39:0xffdadcd7;
    }
    static boolean themeDark(Context c) { return !"light".equals(c.getSharedPreferences("app_preferences",0).getString("theme","dark")); }
    static void prepare(Activity a) { a.setTheme(themeDark(a)?R.style.AppThemeDark:R.style.AppTheme); }
    static android.app.AlertDialog.Builder alert(Activity a) { return new android.app.AlertDialog.Builder(new android.view.ContextThemeWrapper(a,themeDark(a)?R.style.KeplerAlertDark:R.style.KeplerAlertLight)); }
    int dp(float n) { return (int)(n*context.getResources().getDisplayMetrics().density+.5f); }
    LinearLayout vertical() { LinearLayout l=new LinearLayout(context); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout horizontal() { LinearLayout l=new LinearLayout(context); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); }
    void pad(View v,int l,int t,int r,int b) { v.setPadding(dp(l),dp(t),dp(r),dp(b)); }
    void gap(LinearLayout l,int n) { l.addView(new View(context),lp(1,n)); }
    TextView text(String label,int size,boolean bold) { TextView t=new TextView(context); t.setText(label); t.setTextSize(size); t.setTextColor(ink); t.setIncludeFontPadding(false); t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL)); t.setLineSpacing(dp(3),1); return t; }
    GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    void clickable(View v,int color,int radius,Runnable action) { v.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x22ffffff:0x18000000),shape(color,radius),shape(0xffffffff,radius))); v.setFocusable(true); v.setOnClickListener(w->action.run()); }
    TextView button(String label,boolean filled,Runnable action) { TextView b=text(label,14,true); b.setGravity(Gravity.CENTER); b.setMinHeight(dp(52)); pad(b,20,12,20,12); b.setTextColor(filled?bg:ink); clickable(b,filled?ink:container,100,action); return b; }
    void bars(Activity a,View root) {
        a.getWindow().setStatusBarColor(bg); a.getWindow().setNavigationBarColor(bg);
        a.getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setBackgroundColor(bg);
        if(android.os.Build.VERSION.SDK_INT>=35) root.setOnApplyWindowInsetsListener((v,in)-> { android.graphics.Insets s=in.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()); v.setPadding(s.left,s.top,s.right,s.bottom); return in; });
    }
    LinearLayout page(Activity a,String title,Runnable back) {
        LinearLayout root=vertical(); pageRoot=root; bars(a,root); a.setContentView(root);
        LinearLayout top=horizontal(); pad(top,12,8,20,8);
        TextView arrow=text("‹",32,false); arrow.setGravity(Gravity.CENTER); arrow.setContentDescription("Назад"); clickable(arrow,bg,24,back); top.addView(arrow,lp(48,48));
        TextView name=text(title,23,true); pad(name,8,0,0,0); top.addView(name,new LinearLayout.LayoutParams(0,-2,1)); root.addView(top,lp(-1,-2));
        ScrollView scroll=new ScrollView(context); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout frame=new FrameLayout(context); scroll.addView(frame); LinearLayout content=vertical(); pad(content,20,16,20,28);
        int width=(int)(context.getResources().getDisplayMetrics().widthPixels/context.getResources().getDisplayMetrics().density);
        frame.addView(content,new FrameLayout.LayoutParams(width>640?dp(600):-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL)); return content;
    }
    LinearLayout row(String title,String subtitle,String glyph,Runnable action) {
        LinearLayout r=horizontal(); pad(r,16,16,16,16); r.setMinimumHeight(dp(76));
        if(glyph!=null) { TextView icon=text(glyph,24,false); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(container,14)); r.addView(icon,lp(44,44)); }
        LinearLayout names=vertical(); pad(names,glyph==null?0:14,0,12,0); names.addView(text(title,16,true));
        if(subtitle!=null&&!subtitle.isEmpty()) { gap(names,5); TextView sub=text(subtitle,12,false); sub.setTextColor(muted); names.addView(sub); }
        r.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null) { TextView chevron=text("›",24,false); chevron.setTextColor(muted); r.addView(chevron); clickable(r,surface,22,action); } else r.setBackground(shape(surface,22));
        return r;
    }
    void navigation(Activity a,int selected) { if(pageRoot==null)throw new IllegalStateException("Сначала создайте страницу."); navigation(a,pageRoot,selected); }
    void navigation(Activity a,LinearLayout parent,int selected) {
        FrameLayout shell=new FrameLayout(context);
        LinearLayout nav=horizontal(); nav.setGravity(Gravity.TOP); nav.setBackground(shape(surface,24));pad(nav,2,4,2,4);nav.setMinimumHeight(dp(72));
        String[] names={"Стол","Параметры","Экспорт","Общие"};
        for(int i=0;i<names.length;i++) {final int index=i;LinearLayout item=vertical();item.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);item.setMinimumHeight(dp(64));
            FrameLayout pill=new FrameLayout(context);if(i==selected)pill.setBackground(shape(container,100));
            View icon=new NavIcon(context,i,i==selected?ink:muted);pill.addView(icon,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));item.addView(pill,lp(56,32));gap(item,4);
            TextView label=text(names[i],12,i==selected);label.setTextColor(i==selected?ink:muted);label.setGravity(Gravity.CENTER);label.setMaxLines(1);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(label,lp(-1,-2));item.setSelected(i==selected);item.setContentDescription(names[i]+(i==selected?", выбран":""));
            item.setOnClickListener(v->{if(index==selected&&!(a instanceof ImportActivity)&&!(a instanceof GcodePreviewActivity))return;a.startActivity(new android.content.Intent(a,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("kepler_nav",index));});
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        int width=(int)(context.getResources().getDisplayMetrics().widthPixels/context.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams n=new FrameLayout.LayoutParams(width>640?dp(600):-1,-2,Gravity.CENTER);n.setMargins(dp(12),dp(4),dp(12),dp(8));shell.addView(nav,n);parent.addView(shell,lp(-1,-2));
    }
    private static final class NavIcon extends View {
        private final int kind,color; private final android.graphics.Paint p=new android.graphics.Paint(3);
        NavIcon(Context c,int kind,int color){super(c);this.kind=kind;this.color=color;}
        @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setStrokeJoin(android.graphics.Paint.Join.ROUND);android.graphics.Path path=new android.graphics.Path();
            if(kind==0){path.moveTo(12,3);path.lineTo(20,7.5f);path.lineTo(20,16.5f);path.lineTo(12,21);path.lineTo(4,16.5f);path.lineTo(4,7.5f);path.close();path.moveTo(4,7.5f);path.lineTo(12,12);path.lineTo(20,7.5f);path.moveTo(12,12);path.lineTo(12,21);c.drawPath(path,p);}
            else if(kind==1){for(int y:new int[]{7,12,17})c.drawLine(4,y,20,y,p);p.setStyle(android.graphics.Paint.Style.FILL);c.drawCircle(9,7,2,p);c.drawCircle(16,12,2,p);c.drawCircle(10,17,2,p);}
            else if(kind==2){path.moveTo(12,15);path.lineTo(12,3);path.moveTo(7,8);path.lineTo(12,3);path.lineTo(17,8);path.moveTo(4,15);path.lineTo(4,20);path.lineTo(20,20);path.lineTo(20,15);c.drawPath(path,p);}
            else {c.drawCircle(12,12,3,p);c.drawCircle(12,12,8,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,1,12,4,p);c.restore();}}c.restore();
        }
    }
}
