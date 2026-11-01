package com.orcamobile.app.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Responsive rendering of the three token-defined radial gradients. */
public final class GlassBackground extends Drawable {
    private final GlassTokens tokens;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader[] shaders;
    private int alpha=255;
    private ColorFilter filter;
    private GlassBackground(Context context){tokens=GlassTokens.get(context);}
    public static GlassBackground create(Context context){return new GlassBackground(context);}
    @Override protected void onBoundsChange(Rect b){super.onBoundsChange(b);shaders=new Shader[tokens.gradients.length];
        for(int i=0;i<tokens.gradients.length;i++){GlassTokens.Gradient g=tokens.gradients[i];float cx=b.left+b.width()*g.x,cy=b.top+b.height()*g.y;
            float dx=Math.max(cx-b.left,b.right-cx),dy=Math.max(cy-b.top,b.bottom-cy);float radius=(float)Math.hypot(dx,dy)*g.stop;
            if(radius>0)shaders[i]=new RadialGradient(cx,cy,radius,new int[]{g.color,g.color&0x00ffffff},null,Shader.TileMode.CLAMP);}}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();if(b.isEmpty())return;
        paint.setShader(null);paint.setColor(tokens.bgBase);paint.setAlpha(alpha);paint.setColorFilter(filter);canvas.drawRect(b,paint);
        // CSS layers are painted front-to-back: render the last gradient first.
        if(shaders==null)onBoundsChange(b);
        for(int i=shaders.length-1;i>=0;i--){if(shaders[i]==null)continue;paint.setColor(0xffffffff);paint.setAlpha(alpha);paint.setShader(shaders[i]);canvas.drawRect(b,paint);}
        paint.setShader(null);
    }
    @Override public void setAlpha(int value){alpha=Math.max(0,Math.min(255,value));invalidateSelf();}
    @Override public void setColorFilter(ColorFilter value){filter=value;invalidateSelf();}
    @Override public int getOpacity(){return alpha==255&&filter==null?PixelFormat.OPAQUE:PixelFormat.TRANSLUCENT;}
}
