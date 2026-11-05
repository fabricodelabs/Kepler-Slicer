package com.keplerslicer.app.glass;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Build;

/**
 * Liquid Glass lens (Android 13+): instead of only scattering light (blur), the glass bends it. Content near the
 * rounded edge is refracted inward with a slight chromatic split, the middle is magnified a little, and a specular
 * rim catches light from the top-left (a weaker bounce bottom-right). Applied to the backdrop after the blur.
 */
final class LiquidLens {
    private static final String AGSL =
        "uniform shader content;" +
        "uniform float2 size; uniform float radius; uniform float band; uniform float strength;" +
        "float sd(float2 p, float2 b, float r){ float2 q=abs(p)-b+r; return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r; }" +
        "half4 main(float2 xy){" +
        "  float2 c=size*0.5; float2 p=xy-c; float r=min(radius,min(c.x,c.y));" +
        "  float d=sd(p,c,r);" +
        "  float2 n=float2(sd(p+float2(1,0),c,r)-sd(p-float2(1,0),c,r), sd(p+float2(0,1),c,r)-sd(p-float2(0,1),c,r));" +
        "  float nl=length(n); n = nl>0.0001 ? n/nl : float2(0,0);" +
        "  float edge=clamp(1.0+d/band,0.0,1.0); float bend=edge*edge*edge*strength;" +
        "  float2 uv=c+p*0.985-n*bend;" +
        "  half4 col=content.eval(uv);" +
        "  col.r=content.eval(uv+n*bend*0.10).r; col.b=content.eval(uv-n*bend*0.10).b;" +
        "  float rim=clamp(1.0+d/(band*0.22),0.0,1.0);" +
        "  float key=clamp(dot(n,normalize(float2(-0.55,-0.85))),0.0,1.0); float fill=clamp(dot(n,normalize(float2(0.55,0.85))),0.0,1.0);" +
        "  col.rgb+=half3(rim*rim*(key*0.55+fill*0.22));" +
        "  return col;" +
        "}";

    private final RuntimeShader shader; private int w, h;

    private LiquidLens(float density, float cornerPx) {
        shader = new RuntimeShader(AGSL);
        shader.setFloatUniform("radius", cornerPx);
        shader.setFloatUniform("band", 14 * density);
        shader.setFloatUniform("strength", 9 * density);
        shader.setFloatUniform("size", 1, 1);
    }

    static boolean supported() { return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU; }

    static LiquidLens create(float density, float cornerPx) {
        if (!supported()) return null;
        try { return new LiquidLens(density, cornerPx); } catch (RuntimeException e) { return null; }
    }

    /** True when the size changed and the effect must be rebuilt. */
    boolean resize(int width, int height) {
        if (width == w && height == h) return false;
        w = width; h = height; shader.setFloatUniform("size", width, height); return true;
    }

    RenderEffect effect() { return RenderEffect.createRuntimeShaderEffect(shader, "content"); }
}
