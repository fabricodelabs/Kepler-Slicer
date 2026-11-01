package com.orcamobile.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Bounded, display-only 3D line vertices in G-code millimetres. Build off the UI thread. */
final class ToolpathGeometry {
    final FloatBuffer vertices;
    final int[] layerEnds; // cumulative vertex count; index zero displays no layers
    final int layerCount, skippedLayersWithoutZ;
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
