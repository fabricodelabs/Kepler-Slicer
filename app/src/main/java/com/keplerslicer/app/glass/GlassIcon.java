package com.keplerslicer.app.glass;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import com.keplerslicer.app.R;

/** Official Material Symbols Outlined glyphs. Set an accessible label on the host view. */
public final class GlassIcon extends Drawable {
    private final String glyph;
    private final int size,color;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    private int alpha=255;
    private ColorFilter filter;
    private GlassIcon(Context context,String name,int color,float sizeDp){
        if(name==null||!Float.isFinite(sizeDp)||sizeDp<=0)throw new IllegalArgumentException("Invalid icon");
        glyph=new String(Character.toChars(codepoint(name)));size=GlassTokens.get(context).dp(sizeDp);this.color=color;
        Resources resources=context.getResources();
        Typeface face=resources.getFont(R.font.material_symbols_outlined);paint.setTypeface(face);paint.setTextAlign(Paint.Align.CENTER);
        paint.setFontVariationSettings("'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24");
    }
    public static Drawable create(Context context,String name,int color,float sizeDp){return new GlassIcon(context,name,color,sizeDp);}
    public static Drawable create(Context context,String name,int color){return create(context,name,color,GlassTokens.get(context).iconSize);}
    /** Mapping from Google's MaterialSymbolsOutlined variablefont .codepoints file. */
    private static int codepoint(String name){switch(name){
        case "workspace": return 0xefc9; // view_in_ar
        case "settings": return 0xe429; // tune
        case "preferences": return 0xe518; // light_mode
        case "upload": return 0xf09b; case "download": return 0xf090;
        case "chevron_right":return 0xe5cc;case "expand_more":return 0xe5cf;
        case "info":return 0xe88e;case "add":return 0xe145;case "search":return 0xef7a;
        case "warning":return 0xf083;case "cloud":return 0xf15c;case "refresh":return 0xe5d5;
        case "check":return 0xe668;case "close":return 0xe5cd;case "back":return 0xe5c4;
        case "file":return 0xe873;case "folder":return 0xe2c7;case "printer":return 0xe8ad;
        case "wifi":return 0xe63e;case "edit":return 0xf097;case "delete":return 0xe92e;
        case "play":return 0xe037;case "pause":return 0xe034;case "stop":return 0xe047;
        default:throw new IllegalArgumentException("Unknown icon: "+name);
    }}
    @Override public void draw(Canvas canvas){Rect b=getBounds();float extent=Math.min(b.width(),b.height());if(extent<=0)return;
        paint.setColor(color);paint.setAlpha(Math.round(Color.alpha(color)*(alpha/255f)));paint.setColorFilter(filter);paint.setTextSize(extent);
        Paint.FontMetrics metrics=paint.getFontMetrics();float baseline=b.exactCenterY()-(metrics.ascent+metrics.descent)*.5f;
        canvas.drawText(glyph,b.exactCenterX(),baseline,paint);
    }
    @Override public void setAlpha(int value){alpha=Math.max(0,Math.min(255,value));invalidateSelf();}
    @Override public void setColorFilter(ColorFilter value){filter=value;invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
}
