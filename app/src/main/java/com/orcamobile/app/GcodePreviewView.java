package com.orcamobile.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Top-down sketch of supported extrusion paths, in model millimetres. */
final class GcodePreviewView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GcodeAnalysis analysis;
    private final MdUi ui;
    private int index;
    GcodePreviewView(Context context, GcodeAnalysis analysis) { super(context); this.analysis = analysis;ui=new MdUi(context);setBackground(ui.shape(ui.surface,24)); }
    void setLayer(int index) { this.index = index; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (analysis.layers.isEmpty() || analysis.segments + analysis.arcSegments == 0 || index < 0 || index >= analysis.layers.size()) return;
        GcodeAnalysis.Layer layer = analysis.layers.get(index);
        float margin = 24 * getResources().getDisplayMetrics().density;
        double w = Math.max(1e-6, analysis.maxX - analysis.minX), h = Math.max(1e-6, analysis.maxY - analysis.minY);
        float availableW = Math.max(1, getWidth() - 2 * margin), availableH = Math.max(1, getHeight() - 2 * margin);
        float scale = (float)Math.min(availableW / w, availableH / h);
        if (!Float.isFinite(scale) || scale <= 0) return;
        float left = (float)((getWidth() - w * scale) / 2), bottom = (float)((getHeight() + h * scale) / 2);
        canvas.save(); canvas.translate(left, bottom); canvas.scale(scale, -scale); canvas.translate((float)-analysis.minX, (float)-analysis.minY);
        line.setColor(ui.ink); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(1.3f * getResources().getDisplayMetrics().density / scale); line.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLines(layer.segments, 0, layer.size, line); canvas.restore();
    }
}
