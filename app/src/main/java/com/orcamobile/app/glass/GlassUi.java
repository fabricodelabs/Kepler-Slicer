package com.orcamobile.app.glass;

import android.content.Context;
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
        TextView title=text(label,tokens.bodySize,400,tokens.textPrimary);title.setMaxLines(4);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));if(trailing!=null){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=dp(10);row.addView(trailing,p);}
        if(click!=null){row.setOnClickListener(v->click.run());row.setFocusable(true);row.setClickable(true);row.setMinimumHeight(dp(Math.max(tokens.minTouch,tokens.rowMinHeight)));}return row;}
    public View stackedRow(String label,View control){LinearLayout row=vertical();row.setMinimumHeight(dp(tokens.rowMinHeight));pad(row,(int)tokens.rowPadX,8,(int)tokens.rowPadX,8);row.addView(text(label,tokens.captionSize,500,tokens.textSecondary));space(row,4);row.addView(control,new LinearLayout.LayoutParams(-1,-2));return row;}
    public EditText edit(String value,int inputType,boolean multiline){EditText field=new EditText(context);field.setTextSize(tokens.bodySize);field.setTextColor(tokens.textPrimary);field.setHintTextColor(tokens.textSecondary);field.setInputType(inputType|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));field.setSingleLine(!multiline);if(multiline)field.setMinLines(2);field.setMinHeight(dp(tokens.minTouch));field.setPadding(0,dp(4),0,dp(4));field.setBackgroundTintList(ColorStateList.valueOf(tokens.rowDivider));GlassTypography.apply(field,500);field.setText(value);return field;}
    public TextView action(String label,boolean primary,Runnable callback){TextView button=text(label,tokens.bodySize,600,tokens.textPrimary);button.setGravity(Gravity.CENTER);pad(button,16,8,16,8);button.setMinimumHeight(dp(tokens.minTouch));GradientDrawable d=new GradientDrawable();d.setColor(primary?tokens.accent:(reduced?tokens.reduceTransparencyFill:tokens.chipFill));d.setStroke(Math.max(1,dp(1)),tokens.chipStroke);d.setCornerRadius(dp(tokens.pillRadiusMax));button.setBackground(d);button.setFocusable(true);button.setOnClickListener(v->{button.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);if(callback!=null)callback.run();});return button;}
    public View chip(String label,boolean active,Runnable callback){FrameLayout target=new FrameLayout(context);target.setMinimumHeight(dp(tokens.minTouch));TextView text=text(label,tokens.captionSize,active?600:500,tokens.textPrimary);text.setGravity(Gravity.CENTER);pad(text,12,2,12,2);text.setMinHeight(dp(tokens.chipHeight));GradientDrawable d=new GradientDrawable();d.setColor(active?tokens.chipActive:(reduced?tokens.reduceTransparencyFill:tokens.chipFill));d.setStroke(Math.max(1,dp(1)),tokens.chipStroke);d.setCornerRadius(dp(tokens.chipRadius));text.setBackground(d);text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);target.addView(text,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));target.setSelected(active);target.setContentDescription(label);target.setFocusable(true);target.setOnClickListener(v->{target.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);if(callback!=null)callback.run();});return target;}
    public TextView section(String title){TextView h=text(title.toUpperCase(java.util.Locale.ROOT),tokens.captionSize,500,tokens.textSecondary);h.setLetterSpacing(.04f);if(android.os.Build.VERSION.SDK_INT>=28)h.setAccessibilityHeading(true);return h;}
}
