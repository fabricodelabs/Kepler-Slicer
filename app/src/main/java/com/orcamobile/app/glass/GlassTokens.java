package com.orcamobile.app.glass;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable values from the handoff tokens file, in density-independent units. */
public final class GlassTokens {
    private static volatile GlassTokens instance;
    private final Resources resources;
    public final int bgBase,glassFill,glassStroke,accent,textPrimary,textSecondary,reduceTransparencyFill,rowDivider,toggleOn,toggleOff,chipFill,chipStroke,chipActive;
    public final float cardRadius,viewportRadius,pillRadiusMin,pillRadiusMax,blurRadius,rowMinHeight,rowPadX,toggleWidth,toggleHeight,toggleKnob,chipHeight,chipRadius,navHeight,navInset,navBottom,navActiveFlex,navActiveCircle,screenPadding,cardGap,iconSize,iconStroke,minTouch;
    public final int navFill,navStroke,navActiveText,navInactiveText;
    public final float navItemHeight,navPad,navGap,navRadius,navSelectedRadius,navStrokeWidth,navLabelSize,navLineHeight,navLabelGap,navIconSize;
    public final float titleSize,cardTitleSize,bodySize,captionSize;
    final Gradient[] gradients;
    static final class Gradient {final float x,y,stop;final int color;Gradient(float x,float y,float stop,int color){this.x=x;this.y=y;this.stop=stop;this.color=color;}}
    private static final Pattern GRADIENT=Pattern.compile("^\\s*([0-9.]+)%\\s+([0-9.]+)%\\s+(#[0-9a-fA-F]{6})\\s+0\\s+transparent\\s+([0-9.]+)%\\s*$");
    private static final Pattern RGBA=Pattern.compile("rgba\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*([0-9.]+)\\s*\\)");
    public static GlassTokens get(Context context) {
        GlassTokens value=instance;if(value==null)synchronized(GlassTokens.class){value=instance;if(value==null)instance=value=load(context.getApplicationContext());}return value;
    }
    private static GlassTokens load(Context context) {
        try(InputStream in=context.getAssets().open("design/glass_tokens.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()>65536-n)throw new IllegalStateException("Glass tokens file is too large");out.write(b,0,n);}return new GlassTokens(context.getResources(),new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("Cannot read design/glass_tokens.json",e);}
    }
    private GlassTokens(Resources r,JSONObject root)throws Exception {
        resources=r;JSONObject bg=root.getJSONObject("bg"),glass=root.getJSONObject("glass"),radius=glass.getJSONObject("radius"),text=root.getJSONObject("text"),font=root.getJSONObject("font"),row=root.getJSONObject("row"),toggle=root.getJSONObject("toggle"),chip=root.getJSONObject("chip"),nav=root.getJSONObject("nav"),spacing=root.getJSONObject("spacing"),icon=root.getJSONObject("icon");
        bgBase=color(bg.getString("base"));glassFill=color(glass.getString("fill"));glassStroke=color(glass.getString("stroke"));accent=color(root.getString("accent"));textPrimary=color(text.getString("primary"));textSecondary=color(text.getString("secondary"));reduceTransparencyFill=color(root.getString("reduceTransparencyFill"));rowDivider=color(row.getString("divider"));toggleOn=color(toggle.getString("on"));toggleOff=color(toggle.getString("off"));chipFill=color(chip.getString("fill"));chipStroke=color(chip.getString("stroke"));chipActive=color(chip.getString("active"));
        cardRadius=f(radius,"card");viewportRadius=f(radius,"viewport");JSONArray pill=radius.getJSONArray("pill");pillRadiusMin=(float)pill.getDouble(0);pillRadiusMax=(float)pill.getDouble(1);blurRadius=f(glass,"blur");
        rowMinHeight=f(row,"minHeight");rowPadX=f(row,"padX");toggleWidth=f(toggle,"w");toggleHeight=f(toggle,"h");toggleKnob=f(toggle,"knob");chipHeight=f(chip,"h");chipRadius=f(chip,"radius");navHeight=f(nav,"h");navInset=f(nav,"inset");navBottom=f(nav,"bottom");navActiveFlex=f(nav,"activeFlex");navActiveCircle=f(nav,"activeCircle");screenPadding=f(spacing,"screen");cardGap=f(spacing,"cardGap");iconSize=f(icon,"size");iconStroke=f(icon,"stroke");minTouch=f(root,"minTouch");titleSize=f(font,"title");cardTitleSize=f(font,"cardTitle");bodySize=f(font,"body");captionSize=f(font,"caption");
        navItemHeight=f(nav,"itemH");navPad=f(nav,"pad");navGap=f(nav,"gap");navRadius=f(nav,"radius");navSelectedRadius=f(nav,"selectedRadius");navStrokeWidth=f(nav,"strokeWidth");navLabelSize=f(nav,"labelSize");navLineHeight=f(nav,"lineHeight");navLabelGap=f(nav,"labelGap");navIconSize=f(nav,"iconSize");navFill=color(nav.getString("fill"));navStroke=color(nav.getString("stroke"));navActiveText=color(nav.getString("activeText"));navInactiveText=color(nav.getString("inactiveText"));
        JSONArray array=bg.getJSONArray("gradients");gradients=new Gradient[array.length()];for(int i=0;i<gradients.length;i++){Matcher m=GRADIENT.matcher(array.getString(i));if(!m.matches())throw new IllegalArgumentException("Unsupported background gradient token");float x=Float.parseFloat(m.group(1))/100f,y=Float.parseFloat(m.group(2))/100f,stop=Float.parseFloat(m.group(4))/100f;if(x<0||x>1||y<0||y>1||stop<=0||stop>1)throw new IllegalArgumentException("Invalid gradient position");gradients[i]=new Gradient(x,y,stop,color(m.group(3)));}
    }
    private static float f(JSONObject o,String key)throws Exception {double value=o.getDouble(key);if(!Double.isFinite(value)||value<0||value>10000)throw new IllegalArgumentException("Invalid token: "+key);return (float)value;}
    static int color(String s) {Matcher m=RGBA.matcher(s);if(!m.matches())return Color.parseColor(s);int r=Integer.parseInt(m.group(1)),g=Integer.parseInt(m.group(2)),b=Integer.parseInt(m.group(3));float a=Float.parseFloat(m.group(4));if(r>255||g>255||b>255||a<0||a>1)throw new IllegalArgumentException("Invalid rgba color");return Color.argb(Math.round(a*255),r,g,b);}
    public int dp(float value){return Math.round(value*resources.getDisplayMetrics().density);}
}
