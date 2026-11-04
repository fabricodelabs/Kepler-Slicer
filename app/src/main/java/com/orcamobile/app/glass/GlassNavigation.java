package com.orcamobile.app.glass;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.orcamobile.app.R;

/** Four-item Figma v3 navigation. It emits a selection; the host owns navigation. */
public final class GlassNavigation extends LinearLayout {
    public interface Listener {void selected(int item);}
    private static final int[] ICONS={R.drawable.kepler_v3_nav_workspace,R.drawable.kepler_v3_nav_settings,R.drawable.kepler_v3_nav_export,R.drawable.kepler_v3_nav_preferences};
    private static final int[] ICONS_SELECTED={R.drawable.kepler_v3_nav_workspace_selected,R.drawable.kepler_v3_nav_settings_selected,R.drawable.kepler_v3_nav_export_selected,R.drawable.kepler_v3_nav_preferences_selected};
    private final GlassTokens t;private final Listener listener;private final String[] labels;
    private final LinearLayout[] items=new LinearLayout[4];private final ImageView[] icons=new ImageView[4];private final TextView[] titles=new TextView[4];
    private int selected;private boolean reduced;
    public GlassNavigation(Context context,int selected,boolean reduced,Listener listener){super(context);t=GlassTokens.get(context);this.listener=listener;this.selected=Math.max(0,Math.min(3,selected));this.reduced=reduced;
        labels=new String[]{context.getString(R.string.glass_workspace),context.getString(R.string.glass_settings),context.getString(R.string.glass_export),context.getString(R.string.glass_preferences)};
        setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);int pad=t.dp(t.navPad)+t.dp(t.navStrokeWidth);setPadding(pad,pad,pad,pad);setMinimumHeight(t.dp(t.navHeight));build();update();}
    public void setReducedTransparency(boolean value){reduced=value;update();}
    public void setSelectedItem(int item){if(item<0||item>=4)return;selected=item;update();}
    private void build(){
        for(int i=0;i<4;i++){final int index=i;
            LinearLayout item=new LinearLayout(getContext());item.setOrientation(VERTICAL);item.setGravity(Gravity.CENTER);item.setMinimumHeight(Math.max(t.dp(t.navItemHeight),t.dp(t.minTouch)));item.setFocusable(true);item.setClickable(true);
            ImageView icon=new ImageView(getContext());icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(icon,new LinearLayout.LayoutParams(t.dp(t.navIconSize),t.dp(t.navIconSize)));
            TextView title=new TextView(getContext());title.setText(labels[i]);title.setTextSize(TypedValue.COMPLEX_UNIT_SP,t.navLabelSize);title.setLineSpacing(0,t.navLineHeight);title.setSingleLine(true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);title.setGravity(Gravity.CENTER);title.setIncludeFontPadding(false);title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);GlassTypography.apply(title,600);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);tp.topMargin=t.dp(t.navLabelGap);item.addView(title,tp);
            item.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setSelected(host.isSelected());}});
            item.setOnClickListener(v->{performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);setSelectedItem(index);if(listener!=null)listener.selected(index);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,LayoutParams.WRAP_CONTENT,1f);if(i>0)lp.leftMargin=t.dp(t.navGap);addView(item,lp);
            items[i]=item;icons[i]=icon;titles[i]=title;
        }
    }
    /** Applies selection and transparency in place so the item views, and accessibility focus, persist. */
    private void update(){
        if(reduced)setBackground(GlassSurface.of(getContext(),t.navRadius,true));
        else{GradientDrawable bar=new GradientDrawable();bar.setColor(t.navFill);bar.setCornerRadius(t.dp(t.navRadius));bar.setStroke(Math.max(1,t.dp(t.navStrokeWidth)),t.navStroke);setBackground(bar);}
        for(int i=0;i<4;i++){boolean active=i==selected;LinearLayout item=items[i];
            if(active){GradientDrawable pill=new GradientDrawable();pill.setColor(android.graphics.Color.WHITE);pill.setCornerRadius(t.dp(t.navSelectedRadius));item.setBackground(pill);}else item.setBackground(null);
            icons[i].setImageDrawable(getContext().getDrawable(active?ICONS_SELECTED[i]:ICONS[i]));titles[i].setTextColor(active?t.navActiveText:t.navInactiveText);
            item.setSelected(active);item.setContentDescription(labels[i]+(active?", "+getContext().getString(R.string.glass_selected):""));
        }
    }
}
