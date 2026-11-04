package com.keplerslicer.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Bounded, display-only 3D line vertices in G-code millimetres. Build off the UI thread. */
final class ToolpathGeometry {
    final FloatBuffer vertices;
    final int[] layerEnds; // cumulative vertex count; index zero displays no layers
    final int layerCount, skippedLayersWithoutZ;
    /** RGB per vertex for the active Preview colour mode, aligned with {@link #vertices}; null draws one colour. */
    volatile FloatBuffer colors;
    static final int MODE_FEATURE = 0, MODE_SPEED = 1, MODE_FLOW = 2, MODE_HEIGHT = 3, MODE_TEMPERATURE = 4;
    /** Figma Preview legend colours (node 3:394): other, outer wall, inner wall, sparse, solid, support, bridge. */
    static final int[] FEATURE_COLORS = {0xFF9DA3AD, 0xFFFF9F43, 0xFFF5D06B, 0xFFFF7A6E, 0xFFB06CF0, 0xFF6FD0C4, 0xFF6F9CF0};
    private static final int[] RAMP = {0xFF6F9CF0, 0xFF6FD0C4, 0xFFF5D06B, 0xFFFF9F43, 0xFFFF7A6E};
    static int ramp(double t) { if (!Double.isFinite(t)) t = 0; t = Math.max(0, Math.min(1, t)) * (RAMP.length - 1); int i = Math.min(RAMP.length - 2, (int) t); double f = t - i; int a = RAMP[i], b = RAMP[i + 1];
        int r = (int) Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f), g = (int) Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f), bl = (int) Math.round((a & 255) * (1 - f) + (b & 255) * f); return 0xFF000000 | (r << 16) | (g << 8) | bl; }
    /** Range [min,max] used by a numeric mode, for the legend. */
    static double[] range(GcodeAnalysis a, int mode) {
        if (mode == MODE_SPEED) return new double[]{a.minFeed / 60.0, a.maxFeed / 60.0};
        if (mode == MODE_FLOW) return new double[]{a.minFlow, a.maxFlow};
        if (mode == MODE_TEMPERATURE) return new double[]{a.minTemp, a.maxTemp};
        double lo = Double.POSITIVE_INFINITY, hi = 0, prev = 0; for (GcodeAnalysis.Layer l : a.layers) { if (!Double.isFinite(l.z)) continue; double h = l.z - prev; prev = l.z; if (h > 1e-4) { lo = Math.min(lo, h); hi = Math.max(hi, h); } }
        return new double[]{lo, hi};
    }
    /** Builds vertex colours for a mode with the same layer filtering as {@link #build}. */
    static FloatBuffer colors(GcodeAnalysis a, int mode, int vertexCount) throws IOException {
        FloatBuffer out = ByteBuffer.allocateDirect(Math.max(1, vertexCount) * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        double[] r = mode == MODE_FEATURE ? null : range(a, mode); double prev = 0;
        for (GcodeAnalysis.Layer l : a.layers) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Подготовка предпросмотра отменена.");
            if (!Double.isFinite(l.z) || Math.abs(l.z) > Float.MAX_VALUE) continue;
            double height = l.z - prev; prev = l.z;
            for (int s = 0; s < l.size / 4; s++) {
                int c;
                if (mode == MODE_FEATURE) c = FEATURE_COLORS[s < l.types.length ? Math.max(0, Math.min(FEATURE_COLORS.length - 1, l.types[s])) : 0];
                else { double v = mode == MODE_SPEED ? (s < l.feed.length ? l.feed[s] / 60.0 : 0) : mode == MODE_FLOW ? (s < l.flow.length ? l.flow[s] : 0) : mode == MODE_TEMPERATURE ? (s < l.temp.length ? l.temp[s] : 0) : height;
                    c = ramp(r[1] > r[0] ? (v - r[0]) / (r[1] - r[0]) : 0.5); }
                float cr = ((c >> 16) & 255) / 255f, cg = ((c >> 8) & 255) / 255f, cb = (c & 255) / 255f;
                out.put(cr).put(cg).put(cb).put(cr).put(cg).put(cb);
            }
        }
        out.position(0); return out;
    }
    private ToolpathGeometry(FloatBuffer vertices,int[] layerEnds,int skipped) {
        this.vertices=vertices.asReadOnlyBuffer();this.layerEnds=layerEnds;layerCount=layerEnds.length-1;skippedLayersWithoutZ=skipped;
    }
    /** Translation and uniform scale for mapping absolute machine millimetres into the bed scene. */
    static float[] sceneTransform(double centerX,double centerY,double scale) {
        if(!Double.isFinite(centerX)||!Double.isFinite(centerY)||!Double.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Некорректные координаты стола.");
        float x=(float)(-centerX*scale),y=(float)(-centerY*scale),s=(float)scale;
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(s)||s<=0)throw new IllegalArgumentException("Некорректный масштаб стола.");
        return new float[]{x,y,s};
    }
    static ToolpathGeometry build(GcodeAnalysis analysis) throws IOException {
        if(analysis==null||analysis.layers.isEmpty())throw new IOException("G-code не содержит слоёв для просмотра.");
        long points=0;int skipped=0;
        for(GcodeAnalysis.Layer layer:analysis.layers) {
            if(Thread.currentThread().isInterrupted())throw new IOException("Подготовка предпросмотра отменена.");
            if(layer.size<0 || layer.size>layer.segments.length || layer.size%4!=0)throw new IOException("Некорректные данные слоя.");
            if(!Double.isFinite(layer.z)||Math.abs(layer.z)>Float.MAX_VALUE) {if(layer.size>0)skipped++;continue;}
            points+=layer.size/2L;
            if(points>2_000_000L)throw new IOException("Слишком много отрезков для 3D-предпросмотра.");
        }
        if(points==0)throw new IOException("Нет поддерживаемых траекторий с известной высотой слоя.");
        FloatBuffer out=ByteBuffer.allocateDirect((int)points*3*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        int[] ends=new int[analysis.layers.size()+1];int layerIndex=0;
        for(GcodeAnalysis.Layer layer:analysis.layers) {
            if(Thread.currentThread().isInterrupted())throw new IOException("Подготовка предпросмотра отменена.");
            if(Double.isFinite(layer.z)&&Math.abs(layer.z)<=Float.MAX_VALUE) {
                float z=(float)layer.z;
                for(int i=0;i<layer.size;i+=4) {
                    float x1=layer.segments[i],y1=layer.segments[i+1],x2=layer.segments[i+2],y2=layer.segments[i+3];
                    if(!Float.isFinite(x1)||!Float.isFinite(y1)||!Float.isFinite(x2)||!Float.isFinite(y2))throw new IOException("Некорректные координаты траектории.");
                    out.put(x1).put(y1).put(z).put(x2).put(y2).put(z);
                }
            }
            ends[++layerIndex]=out.position()/3;
        }
        out.position(0);return new ToolpathGeometry(out,ends,skipped);
    }
}
