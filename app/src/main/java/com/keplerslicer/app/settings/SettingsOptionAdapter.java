package com.keplerslicer.app.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.keplerslicer.app.R;
import com.keplerslicer.app.glass.GlassSurface;
import com.keplerslicer.app.glass.GlassSwitch;
import com.keplerslicer.app.glass.GlassTokens;
import com.keplerslicer.app.glass.GlassTypography;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Virtualized group headers and configurable engine options. */
public final class SettingsOptionAdapter extends BaseAdapter {
    public interface Listener {void edit(SettingsSchema.Option option);void help(SettingsSchema.Option option);void boolChanged(SettingsSchema.Option option,boolean value);}
    private static final int HEADER=0,OPTION=1;
    private final Context context;private final View anchor;private final boolean reduced;private final Listener listener;private final GlassTokens tokens;
    private final ArrayList<Item> items=new ArrayList<>();private JSONObject values=new JSONObject();private Set<String> explicit=Collections.emptySet(),definitionKeys=Collections.emptySet(),approximateKeys=Collections.emptySet();private String highlighted="";private boolean readOnly;
    private static final class Item {final SettingsSchema.Option option;final String heading;boolean first,last;Item(String h){option=null;heading=h;}Item(SettingsSchema.Option o){option=o;heading=null;}}
    public SettingsOptionAdapter(Context context,View backgroundAnchor,boolean reduced,Listener listener){if(context==null||backgroundAnchor==null||listener==null)throw new IllegalArgumentException("Missing adapter argument");this.context=context;this.anchor=backgroundAnchor;this.reduced=reduced;this.listener=listener;tokens=GlassTokens.get(context);}
    /** Display-only provenance for values the caller could not resolve exactly. */
    public void setValueOrigins(Set<String> definitionKeys,Set<String> approximateKeys){
        this.definitionKeys=definitionKeys==null?Collections.emptySet():new HashSet<>(definitionKeys);
        this.approximateKeys=approximateKeys==null?Collections.emptySet():new HashSet<>(approximateKeys);
        notifyDataSetChanged();
    }
    /** Project-native effective values are separate from the saved preset editor. */
    public void setReadOnly(boolean readOnly){this.readOnly=readOnly;notifyDataSetChanged();}
    public void submit(List<SettingsSchema.Option> options,JSONObject values,Set<String> explicitKeys,String highlightedKey){
        try{this.values=values==null?new JSONObject():new JSONObject(values.toString());}catch(JSONException impossible){throw new IllegalArgumentException("Invalid values",impossible);}this.explicit=explicitKeys==null?Collections.emptySet():new HashSet<>(explicitKeys);this.highlighted=highlightedKey==null?"":highlightedKey;items.clear();
        String lastGroup=null,lastPage=null;Item previous=null;
        if(options!=null)for(SettingsSchema.Option o:options){if(o==null)continue;String group=o.group==null?"":o.group;String page=o.page==null?"":o.page;boolean start=previous==null||!group.equals(lastGroup)||!page.equals(lastPage);if(start){if(previous!=null)previous.last=true;items.add(new Item(group.trim().isEmpty()?context.getString(R.string.phase2_option_other_group):group));}Item item=new Item(o);item.first=start;items.add(item);previous=item;lastGroup=group;lastPage=page;}
        if(previous!=null)previous.last=true;notifyDataSetChanged();
    }
    public int positionOf(String key){if(key==null)return -1;for(int i=0;i<items.size();i++)if(items.get(i).option!=null&&key.equals(items.get(i).option.key))return i;return -1;}
    @Override public int getCount(){return items.size();}@Override public Object getItem(int position){Item i=items.get(position);return i.option==null?i.heading:i.option;}@Override public long getItemId(int position){return position;}@Override public int getViewTypeCount(){return 2;}@Override public int getItemViewType(int position){return items.get(position).option==null?HEADER:OPTION;}@Override public boolean areAllItemsEnabled(){return false;}@Override public boolean isEnabled(int position){return getItemViewType(position)==OPTION;}
    /**
     * Kepler v3 Process list (Figma node 7:548): r20 glass card, rows >=52 with pl16 pr14, 14/500 label,
     * 7dp #FFB454 changed dot, value in a field r12 h36 px10 (14/600 value + 12/500 unit), 46x28 switch.
     */
    @Override public View getView(int position,View recycled,ViewGroup parent){Item item=items.get(position);if(item.option==null){TextView header=recycled instanceof TextView?(TextView)recycled:new TextView(context);header.setText(item.heading.toUpperCase(java.util.Locale.getDefault()));header.setTextColor(tokens.textSecondary);header.setTextSize(12);header.setIncludeFontPadding(false);header.setLetterSpacing(.06f);GlassTypography.apply(header,700);header.setPadding(tokens.dp(4),tokens.dp(20),tokens.dp(4),tokens.dp(8));if(android.os.Build.VERSION.SDK_INT>=28)header.setAccessibilityHeading(true);return header;}
        final Holder h;if(recycled instanceof GroupFrame){GroupFrame f=(GroupFrame)recycled;h=(Holder)f.getTag();}else{h=new Holder();h.frame=new GroupFrame(context);View backdrop=GlassSurface.backdrop(context,anchor,0,reduced);h.frame.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));h.surface=new View(context);h.frame.addView(h.surface,new FrameLayout.LayoutParams(-1,-1));
            h.row=new LinearLayout(context);h.row.setOrientation(LinearLayout.HORIZONTAL);h.row.setGravity(Gravity.CENTER_VERTICAL);h.row.setPadding(tokens.dp(16),tokens.dp(8),tokens.dp(14),tokens.dp(8));h.row.setMinimumHeight(tokens.dp(52));h.frame.addView(h.row,new FrameLayout.LayoutParams(-1,-2));
            LinearLayout labelBox=new LinearLayout(context);labelBox.setOrientation(LinearLayout.HORIZONTAL);labelBox.setGravity(Gravity.CENTER_VERTICAL);
            h.label=new TextView(context);h.label.setTextColor(tokens.textPrimary);h.label.setTextSize(14);h.label.setIncludeFontPadding(false);h.label.setLineSpacing(0,1.3f);h.label.setMaxLines(3);GlassTypography.apply(h.label,500);labelBox.addView(h.label,new LinearLayout.LayoutParams(-2,-2));h.dot=new android.graphics.drawable.GradientDrawable();h.dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);h.dot.setColor(CHANGED);h.dot.setSize(tokens.dp(7),tokens.dp(7));h.label.setCompoundDrawablePadding(tokens.dp(8));
            h.mark=new TextView(context);h.mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);android.graphics.drawable.GradientDrawable dot=new android.graphics.drawable.GradientDrawable();dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);dot.setColor(CHANGED);h.mark.setBackground(dot);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(tokens.dp(7),tokens.dp(7));mp.leftMargin=tokens.dp(8);labelBox.addView(h.mark,mp);
            h.row.addView(labelBox,new LinearLayout.LayoutParams(0,-2,1));
            h.field=new LinearLayout(context);h.field.setOrientation(LinearLayout.HORIZONTAL);h.field.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);h.field.setPadding(tokens.dp(10),tokens.dp(4),tokens.dp(10),tokens.dp(4));h.field.setMinimumHeight(tokens.dp(36));h.field.setMinimumWidth(tokens.dp(72));
            android.graphics.drawable.GradientDrawable fieldBg=new android.graphics.drawable.GradientDrawable();fieldBg.setColor(0x38000000);fieldBg.setStroke(Math.max(1,tokens.dp(1)),0x2EFFFFFF);fieldBg.setCornerRadius(tokens.dp(12));h.field.setBackground(fieldBg);
            h.value=new TextView(context);h.value.setTextColor(tokens.textPrimary);h.value.setTextSize(14);h.value.setIncludeFontPadding(false);h.value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);h.value.setMaxLines(3);h.value.setMaxWidth(tokens.dp(150));h.value.setMinWidth(tokens.dp(40));GlassTypography.apply(h.value,600);h.field.addView(h.value,new LinearLayout.LayoutParams(-2,-2));
            h.unit=new TextView(context);h.unit.setTextColor(tokens.textSecondary);h.unit.setTextSize(12);h.unit.setIncludeFontPadding(false);h.unit.setSingleLine(true);GlassTypography.apply(h.unit,500);LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(-2,-2);up.leftMargin=tokens.dp(6);h.field.addView(h.unit,up);
            LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,-2);fp.leftMargin=tokens.dp(12);h.row.addView(h.field,fp);
            h.toggle=new GlassSwitch(context);h.row.addView(h.toggle,new LinearLayout.LayoutParams(tokens.dp(tokens.minTouch)+tokens.dp(8),tokens.dp(tokens.minTouch)));h.frame.setTag(h);}
        SettingsSchema.Option option=item.option;Object raw=resolved(option);boolean known=raw!=null&&raw!=JSONObject.NULL;boolean edited=explicit.contains(option.key);
        boolean definition=known&&(definitionKeys.contains(option.key)||(!values.has(option.key)&&option.defaultResolved));
        boolean approximate=known&&!definition&&approximateKeys.contains(option.key);
        String origin=definition?context.getString(R.string.phase2_option_origin_default):approximate?context.getString(R.string.phase2_option_origin_approximate):"";
        String label=option.label==null||option.label.isEmpty()?option.key:option.label;
        h.frame.setCorners(item.first,item.last,tokens.dp(RADIUS));h.surface.setBackground(new GroupDrawable(tokens,reduced,item.first,item.last,option.key!=null&&option.key.equals(highlighted)));
        h.label.setText(label+(definition?" †":approximate?" ≈":""));h.mark.setVisibility(View.GONE);h.label.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,edited?h.dot:null,null);h.frame.setActivated(option.key!=null&&option.key.equals(highlighted));GlassTypography.apply(h.label,h.frame.isActivated()?700:500);
        h.toggle.setOnCheckedChangeListener(null);boolean bool=option.isBoolean();h.toggle.setVisibility(bool?View.VISIBLE:View.GONE);h.field.setVisibility(bool&&known?View.GONE:View.VISIBLE);
        String text=known?option.displayValue(raw):context.getString(R.string.phase2_option_not_set);
        String unit=known&&!bool&&text!=null?unitFor(option,text):"";
        h.value.setText(text==null?context.getString(R.string.phase2_option_not_set):text);h.unit.setText(unit);h.unit.setVisibility(unit.isEmpty()?View.GONE:View.VISIBLE);
        if(bool){h.toggle.setEnabled(known&&!readOnly);h.toggle.setContentDescription(label+(origin.isEmpty()?"":", "+origin));h.toggle.setChecked(known&&option.booleanValue(raw));h.toggle.setOnCheckedChangeListener((button,checked)->{if(!readOnly)listener.boolChanged(option,checked);});h.toggle.setOnLongClickListener(v->{listener.help(option);return true;});}
        String shown=text==null?context.getString(R.string.phase2_option_not_set):unit.isEmpty()?text:text+" "+unit;
        String desc=label+", "+shown+(edited?", "+context.getString(R.string.phase2_option_overridden):"")+(origin.isEmpty()?"":", "+origin);
        h.row.setContentDescription(desc);h.row.setFocusable(true);h.row.setOnClickListener(v->{if(readOnly)listener.help(option);else listener.edit(option);});h.row.setOnLongClickListener(v->{listener.help(option);return true;});return h.frame;
    }
    private static final int CHANGED=0xFFFFB454;private static final float RADIUS=20;
    /** Unit shown beside the value field; empty when the value already carries one. */
    private String unitFor(SettingsSchema.Option option,String value){
        String units=option.units==null?"":option.units.trim();
        if("coFloatOrPercent".equals(option.type)){
            if(value.trim().endsWith("%"))return "";
            int choice=units.toLowerCase(java.util.Locale.ROOT).indexOf(" or %");
            if(choice>=0)units=units.substring(0,choice).trim();
            if(units.isEmpty()||units.equals("%"))units=context.getString(R.string.phase2_option_unit_mm);
        }
        return units;
    }
    private String withUnits(SettingsSchema.Option option,String value){
        String units=option.units==null?"":option.units.trim();
        if("coFloatOrPercent".equals(option.type)){
            if(value.trim().endsWith("%"))return value;
            int choice=units.toLowerCase(java.util.Locale.ROOT).indexOf(" or %");
            if(choice>=0)units=units.substring(0,choice).trim();
            if(units.isEmpty()||units.equals("%"))units=context.getString(R.string.phase2_option_unit_mm);
        }
        return units.isEmpty()?value:value+" "+units;
    }

    private Object resolved(SettingsSchema.Option o){if(o.key!=null&&values.has(o.key))return values.opt(o.key);return !readOnly&&o.defaultResolved?o.defaultValue:null;}
    private static final class Holder {GroupFrame frame;View surface;LinearLayout row,field;TextView label,value,unit,mark;GlassSwitch toggle;android.graphics.drawable.GradientDrawable dot;}
    private static final class GroupFrame extends FrameLayout {private final Path clip=new Path();private boolean first,last;private float r;GroupFrame(Context c){super(c);}void setCorners(boolean first,boolean last,float radius){this.first=first;this.last=last;this.r=radius;invalidate();}@Override protected void dispatchDraw(Canvas canvas){clip.reset();float top=first?r:0,bottom=last?r:0;clip.addRoundRect(new RectF(0,0,getWidth(),getHeight()),new float[]{top,top,top,top,bottom,bottom,bottom,bottom},Path.Direction.CW);int save=canvas.save();canvas.clipPath(clip);super.dispatchDraw(canvas);canvas.restoreToCount(save);}}
    private static final class GroupDrawable extends Drawable {private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int fill,stroke,divider;private final float radius,width,inset;private final boolean first,last,target;private final int tint,ring;GroupDrawable(GlassTokens t,boolean reduced,boolean first,boolean last,boolean target){tint=(t.accent&0x00FFFFFF)|(0x33<<24);ring=(t.accent&0x00FFFFFF)|(0xCC<<24);inset=t.dp(3);this.target=target;fill=reduced?t.reduceTransparencyFill:t.glassFill;stroke=t.glassStroke;divider=t.rowDivider;radius=t.dp(RADIUS);width=Math.max(1,t.dp(1));this.first=first;this.last=last;}@Override public void draw(Canvas c){Rect b=getBounds();float top=first?b.top+width/2:b.top-radius,bottom=last?b.bottom-width/2:b.bottom+radius;RectF r=new RectF(b.left+width/2,top,b.right-width/2,bottom);paint.setColor(fill);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(r,radius,radius,paint);paint.setColor(stroke);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);c.drawRoundRect(r,radius,radius,paint);if(target){RectF t=new RectF(b.left+inset*2,b.top+inset,b.right-inset*2,b.bottom-inset);float tr=Math.min(radius,t.height()/2);paint.setColor(tint);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(t,tr,tr,paint);paint.setColor(ring);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,width*1.5f));c.drawRoundRect(t,tr,tr,paint);paint.setStrokeWidth(width);}if(!last){paint.setColor(divider);c.drawLine(b.left,b.bottom-width/2,b.right,b.bottom-width/2,paint);}}@Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
}
