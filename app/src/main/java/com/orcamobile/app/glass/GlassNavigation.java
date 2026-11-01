package com.orcamobile.app.glass;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.orcamobile.app.R;

/** Four-item gallery navigation. It emits a selection; the host owns navigation. */
public final class GlassNavigation extends LinearLayout {
    public interface Listener {void selected(int item);}
    private final GlassTokens t;private final Listener listener;private final String[] labels,icons={"workspace","settings","upload","preferences"};
    private int selected;private boolean reduced;
    public GlassNavigation(Context context,int selected,boolean reduced,Listener listener){super(context);t=GlassTokens.get(context);this.listener=listener;this.selected=Math.max(0,Math.min(3,selected));this.reduced=reduced;
        labels=new String[]{context.getString(R.string.glass_workspace),context.getString(R.string.glass_settings),context.getString(R.string.glass_export),context.getString(R.string.glass_preferences)};setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(t.dp(6),t.dp(6),t.dp(6),t.dp(6));rebuild();}
    public void setReducedTransparency(boolean value){reduced=value;rebuild();}
    public void setSelectedItem(int item){if(item<0||item>=4)return;selected=item;rebuild();}
    private void rebuild(){removeAllViews();setBackground(GlassSurface.of(getContext(),t.pillRadiusMax,reduced));setMinimumHeight(t.dp(t.navHeight));
        for(int i=0;i<4;i++){final int index=i;boolean active=i==selected;LinearLayout item=new LinearLayout(getContext());item.setOrientation(HORIZONTAL);item.setGravity(Gravity.CENTER);item.setMinimumHeight(t.dp(50));item.setFocusable(true);item.setClickable(true);
            if(!active)item.setBackground(GlassSurface.of(getContext(),t.pillRadiusMax,reduced));
            if(active){android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(t.accent);bg.setCornerRadius(t.dp(t.pillRadiusMax));item.setBackground(bg);
                TextView title=new TextView(getContext());title.setText(labels[i]);title.setTextColor(t.textPrimary);title.setTextSize(14);title.setSingleLine(false);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);GlassTypography.apply(title,600);title.setGravity(Gravity.CENTER);item.addView(title,new LinearLayout.LayoutParams(0,LayoutParams.WRAP_CONTENT,1));}
            FrameLayout circle=new FrameLayout(getContext());int iconColor=t.textPrimary;if(active){android.graphics.drawable.GradientDrawable white=new android.graphics.drawable.GradientDrawable();white.setColor(Color.WHITE);white.setShape(android.graphics.drawable.GradientDrawable.OVAL);circle.setBackground(white);iconColor=t.accent;}
            android.widget.ImageView icon=new android.widget.ImageView(getContext());icon.setImageDrawable(GlassIcon.create(getContext(),icons[i],iconColor,t.iconSize));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);circle.addView(icon,new FrameLayout.LayoutParams(t.dp(t.iconSize),t.dp(t.iconSize),Gravity.CENTER));int diameter=t.dp(active?t.navActiveCircle:t.minTouch);item.addView(circle,new LinearLayout.LayoutParams(diameter,diameter));
            item.setSelected(active);item.setContentDescription(labels[i]+(active?", "+getContext().getString(R.string.glass_selected):""));item.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setSelected(host.isSelected());}});
            item.setOnClickListener(v->{performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);setSelectedItem(index);if(listener!=null)listener.selected(index);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,LayoutParams.WRAP_CONTENT,active?t.navActiveFlex:1f);if(i>0)lp.leftMargin=t.dp(6);addView(item,lp);
        }
    }
}
