package com.orcamobile.app.glass;

import android.content.Context;
import com.orcamobile.app.R;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Reusable native glass primitives. Values and persistence belong to their caller. */
public final class GlassUi {
    private final Context context;private final View anchor;public final GlassTokens tokens;private final boolean reduced;
    public GlassUi(Context context,View anchor,boolean reduced){this.context=context;this.anchor=anchor;this.reduced=reduced;tokens=GlassTokens.get(context);}
    public int dp(float x){return tokens.dp(x);}
    public LinearLayout vertical(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.VERTICAL);return v;}
    public LinearLayout horizontal(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public void pad(View v,int l,int t,int r,int b){v.setPadding(dp(l),dp(t),dp(r),dp(b));}
    public void space(LinearLayout box,int height){box.addView(new View(context),new LinearLayout.LayoutParams(1,dp(height)));}
    public TextView text(String label,float sp,int weight,int color){TextView v=new TextView(context);v.setText(label);v.setTextColor(color);v.setTextSize(sp);v.setIncludeFontPadding(false);GlassTypography.apply(v,weight);return v;}
    public TextView heading(String label){TextView v=text(label,tokens.titleSize,600,tokens.textPrimary);if(android.os.Build.VERSION.SDK_INT>=28)v.setAccessibilityHeading(true);return v;}
    public TextView value(String text){TextView v=text(text,tokens.bodySize,600,tokens.textPrimary);v.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);v.setMaxWidth(dp(150));return v;}
    public FrameLayout surface(View content,float radius){FrameLayout frame=new FrameLayout(context);frame.addView(GlassSurface.backdrop(context,anchor,radius,reduced),new FrameLayout.LayoutParams(-1,-1));content.setBackground(GlassSurface.of(context,radius,reduced));frame.addView(content,new FrameLayout.LayoutParams(-1,-1));return frame;}
    public View divider(){View v=new View(context);v.setBackgroundColor(tokens.rowDivider);v.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.max(1,dp(1))));return v;}
    public View row(String label,View trailing,Runnable click){LinearLayout row=horizontal();row.setMinimumHeight(dp(tokens.rowMinHeight));pad(row,(int)tokens.rowPadX,0,(int)tokens.rowPadX,0);
        TextView title=text(label,14,500,tokens.textPrimary);title.setMaxLines(4);title.setLineSpacing(0,1.3f);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));if(trailing!=null){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=dp(10);row.addView(trailing,p);}
        if(click!=null){row.setOnClickListener(v->click.run());row.setFocusable(true);row.setClickable(true);row.setMinimumHeight(dp(Math.max(tokens.minTouch,tokens.rowMinHeight)));}return row;}
    public View stackedRow(String label,View control){LinearLayout row=vertical();row.setMinimumHeight(dp(tokens.rowMinHeight));pad(row,(int)tokens.rowPadX,8,(int)tokens.rowPadX,8);row.addView(text(label,tokens.captionSize,500,tokens.textSecondary));space(row,4);row.addView(control,new LinearLayout.LayoutParams(-1,-2));return row;}
    public EditText edit(String value,int inputType,boolean multiline){EditText field=new EditText(context);field.setTextSize(tokens.bodySize);field.setTextColor(tokens.textPrimary);field.setHintTextColor(tokens.textSecondary);field.setInputType(inputType|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));field.setSingleLine(!multiline);if(multiline)field.setMinLines(2);field.setMinHeight(dp(tokens.minTouch));field.setPadding(0,dp(4),0,dp(4));field.setBackgroundTintList(ColorStateList.valueOf(tokens.rowDivider));GlassTypography.apply(field,500);field.setText(value);return field;}
    public TextView action(String label,boolean primary,Runnable callback){TextView button=text(label,tokens.bodySize,600,tokens.textPrimary);button.setGravity(Gravity.CENTER);pad(button,16,8,16,8);button.setMinimumHeight(dp(tokens.minTouch));if(primary)button.setTextColor(KeplerUi.ON_ACTIVE);GlassTypography.apply(button,700);GradientDrawable d=new GradientDrawable();d.setColor(primary?KeplerUi.WHITE:KeplerUi.GLASS);if(!primary)d.setStroke(Math.max(1,dp(1)),KeplerUi.GLASS_STROKE);d.setCornerRadius(dp(22));button.setBackground(d);button.setFocusable(true);button.setOnClickListener(v->{button.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);if(callback!=null)callback.run();});return button;}
    public View chip(String label,boolean active,Runnable callback){FrameLayout target=new FrameLayout(context);target.setMinimumHeight(dp(tokens.minTouch));target.setMinimumWidth(dp(tokens.minTouch));TextView text=text(label,13,600,active?KeplerUi.ON_ACTIVE:KeplerUi.WHITE);text.setSingleLine(true);text.setGravity(Gravity.CENTER);pad(text,14,0,14,0);text.setMinHeight(dp(36));GradientDrawable d=new GradientDrawable();d.setColor(active?KeplerUi.WHITE:KeplerUi.GLASS);if(!active)d.setStroke(Math.max(1,dp(1)),KeplerUi.GLASS_STROKE);d.setCornerRadius(dp(18));text.setBackground(d);text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);target.addView(text,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));target.setSelected(active);target.setContentDescription(label);target.setFocusable(true);target.setOnClickListener(v->{target.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);if(callback!=null)callback.run();});return target;}
    /** Select field (Preferences 7:997): field r12 h36, 14/600 value, 16dp chevron-down. */
    public View select(String value){LinearLayout s=horizontal();s.setPadding(dp(12),dp(4),dp(10),dp(4));s.setMinimumHeight(dp(36));GradientDrawable d=new GradientDrawable();d.setColor(KeplerUi.FIELD);d.setStroke(Math.max(1,dp(1)),KeplerUi.FIELD_STROKE);d.setCornerRadius(dp(12));s.setBackground(d);TextView t=text(value,14,600,KeplerUi.WHITE);t.setMaxLines(2);t.setMaxWidth(dp(170));s.addView(t);android.widget.ImageView c=new android.widget.ImageView(context);c.setImageResource(R.drawable.kv3_chevron_down);c.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(16),dp(16));p.leftMargin=dp(8);s.addView(c,p);s.setContentDescription(value);return s;}
    public TextView section(String title){TextView h=text(title.toUpperCase(java.util.Locale.getDefault()),12,700,tokens.textSecondary);h.setLetterSpacing(.06f);if(android.os.Build.VERSION.SDK_INT>=28)h.setAccessibilityHeading(true);return h;}
}
