package com.keplerslicer.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import com.keplerslicer.app.glass.GlassTypography;

/**
 * Cold-start brand animation, drawn over the freshly built workspace so the app loads underneath it (no extra screen,
 * no hand-over flash). It continues the Android 12+ system splash (res/drawable/splash_blueprint.xml: the mark as a
 * thin blueprint at the same place and size) and then: guide lines open, the five print layers are laid bottom-up
 * with a nozzle pass, a light sweep crosses the faces, "Kepler Slicer" rises in, and everything dissolves into the
 * workspace. Geometry comes from BrandGeometry, timing from the constants below. Skipped when system animations are
 * off; a tap finishes it at once. Shown once per process (cold start from the launcher).
 */
final class BrandSplash extends View {
    static final String EXTRA = "kepler_brand_intro";
    /** System splash background; must equal android:windowSplashScreenBackground in styles.xml. */
    static final int BACKGROUND = 0xFF1C1F25;
    /** Design units to dp; must equal BrandKit.SPLASH_SCALE (splash_blueprint.xml). */
    static final float SCALE_DP = 1.5f, LINE_DP = 1.2f, LINE_ALPHA = 0.55f;
    /** Timeline (ms). */
    static final int TOTAL = 1150, GUIDES_END = 260, LAYERS_START = 110, LAYER_STEP = 70, LAYER_TIME = 250,
            SWEEP_START = 560, SWEEP_TIME = 300, TITLE_START = 600, TITLE_TIME = 300, EXIT_START = 880;
    private static boolean shown;

    static boolean wanted(Activity a) {
        return !shown && a.getIntent() != null && a.getIntent().getBooleanExtra(EXTRA, false) && ValueAnimator.areAnimatorsEnabled();
    }

    static void attach(Activity a) {
        shown = true;
        a.getIntent().removeExtra(EXTRA);
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        decor.addView(new BrandSplash(a), new ViewGroup.LayoutParams(-1, -1));
    }

    private final float density, unit, line;
    private final Paint background = new Paint(), glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG), guide = new Paint(Paint.ANTI_ALIAS_FLAG),
            outline = new Paint(Paint.ANTI_ALIAS_FLAG), side = new Paint(Paint.ANTI_ALIAS_FLAG), face = new Paint(Paint.ANTI_ALIAS_FLAG),
            edge = new Paint(Paint.ANTI_ALIAS_FLAG), sweep = new Paint(Paint.ANTI_ALIAS_FLAG), nozzle = new Paint(Paint.ANTI_ALIAS_FLAG),
            title = new Paint(Paint.ANTI_ALIAS_FLAG), subtitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] front = new Path[BrandGeometry.LAYERS], back = new Path[BrandGeometry.LAYERS], edges = new Path[BrandGeometry.LAYERS];
    private final Path shapes = new Path();
    private final Matrix sweepMatrix = new Matrix();
    private final PathInterpolator ease = new PathInterpolator(0.2f, 0f, 0f, 1f), soft = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private final ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f);
    private float cx, cy, sweepWidth, titleWidthKepler, titleWidth;
    private long skipAt = -1;

    private BrandSplash(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        unit = SCALE_DP * density; line = LINE_DP * density;
        background.setColor(BACKGROUND);
        guide.setStyle(Paint.Style.STROKE); guide.setStrokeWidth(Math.max(1f, density * 0.8f)); guide.setColor(BrandGeometry.MINT);
        outline.setStyle(Paint.Style.STROKE); outline.setStrokeWidth(line); outline.setStrokeJoin(Paint.Join.ROUND); outline.setColor(BrandGeometry.MINT);
        side.setColor(BrandGeometry.SIDE); edge.setColor(BrandGeometry.EDGE_LIGHT);
        nozzle.setStyle(Paint.Style.STROKE); nozzle.setStrokeCap(Paint.Cap.ROUND); nozzle.setStrokeWidth(density * 2f); nozzle.setColor(0xFFE6FFF9);
        title.setColor(0xFFFFFFFF); title.setTypeface(GlassTypography.typeface(context, 800)); title.setTextSize(28 * density);
        subtitle.setColor(BrandGeometry.MINT); subtitle.setTypeface(GlassTypography.typeface(context, 500)); subtitle.setTextSize(28 * density);
        titleWidthKepler = title.measureText("Kepler "); titleWidth = titleWidthKepler + subtitle.measureText("Slicer");
        for (int i = 0; i < BrandGeometry.LAYERS; i++) { front[i] = new Path(); back[i] = new Path(); edges[i] = new Path(); }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(true); // the splash takes touches until it is gone
        clock.setDuration(TOTAL); clock.setInterpolator(new LinearInterpolator());
        clock.addUpdateListener(a -> invalidate());
        clock.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) { finish(); }
        });
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); clock.start(); }
    @Override protected void onDetachedFromWindow() { clock.removeAllListeners(); clock.cancel(); super.onDetachedFromWindow(); }

    private void finish() {
        clock.removeAllUpdateListeners();
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN && skipAt < 0) skipAt = clock.getCurrentPlayTime(); // jump to the exit
        return true;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f; cy = h / 2f; // the system splash centres its icon in the window, and this view fills the window
        shapes.rewind();
        for (float[] p : BrandGeometry.SHAPES) add(shapes, p, 0, 0);
        float d = BrandGeometry.DEPTH;
        for (int i = 0; i < BrandGeometry.LAYERS; i++) {
            front[i].rewind(); back[i].rewind(); edges[i].rewind();
            for (float[] p : BrandGeometry.layer(i)) { add(front[i], p, 0, 0); add(back[i], p, d, d); }
            for (float[] p : BrandGeometry.edge(i)) add(edges[i], p, 0, 0);
        }
        face.setShader(new LinearGradient(0, y(BrandGeometry.TOP), 0, y(BrandGeometry.BOTTOM), BrandGeometry.FACE_TOP, BrandGeometry.FACE_BOTTOM, Shader.TileMode.CLAMP));
        sweepWidth = 34 * unit;
        sweep.setShader(new LinearGradient(-sweepWidth, 0, sweepWidth, 0, new int[]{0x00FFFFFF, 0x8CFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        glowPaint.setShader(new RadialGradient(cx, cy, 70 * unit, 0x335EE0C8, 0x005EE0C8, Shader.TileMode.CLAMP));
    }

    private float x(float v) { return cx + (v - 50f) * unit; }
    private float y(float v) { return cy + (v - 50f) * unit; }
    private void add(Path path, float[] p, float dx, float dy) {
        for (int k = 0; k < p.length; k += 2) { float px = x(p[k] + dx), py = y(p[k + 1] + dy); if (k == 0) path.moveTo(px, py); else path.lineTo(px, py); }
        path.close();
    }

    private static float span(float t, float start, float length) { return Math.max(0f, Math.min(1f, (t - start) / length)); }

    @Override protected void onDraw(Canvas c) {
        float t = clock.getCurrentPlayTime();
        if (skipAt >= 0) t = Math.max(t, EXIT_START + (t - skipAt) * 2.5f); // tap: finish quickly from where it is
        if (t >= TOTAL && clock.isRunning()) { clock.end(); return; }
        float exit = soft.getInterpolation(span(t, EXIT_START, TOTAL - EXIT_START));
        background.setAlpha(Math.round(255 * (1f - span(t, EXIT_START + 60, TOTAL - EXIT_START - 60))));
        c.drawRect(0, 0, getWidth(), getHeight(), background);
        float content = 1f - exit;
        if (content <= 0f) return;

        // Lift the mark as the title arrives, then shrink everything slightly on exit.
        float titleIn = ease.getInterpolation(span(t, TITLE_START, TITLE_TIME));
        float lift = (18 * density + title.getTextSize()) / 2f * titleIn;
        int save = c.save();
        float s = 1f - 0.06f * exit;
        c.scale(s, s, cx, cy);
        c.translate(0, -lift);

        float glowIn = span(t, SWEEP_START - 200, 400);
        if (glowIn > 0) { glowPaint.setAlpha(Math.round(255 * glowIn * content)); c.drawCircle(cx, cy, 70 * unit, glowPaint); }

        // Stage 1: the blueprint (already on screen from the system splash) brightens, guide lines open outwards.
        float guides = ease.getInterpolation(span(t, 0, GUIDES_END));
        float guideFade = 1f - span(t, SWEEP_START, 260);
        guide.setAlpha(Math.round(70 * guideFade * content));
        float half = (10 + 36 * guides) * unit;
        c.drawLine(cx - half, y(BrandGeometry.TOP), cx + half, y(BrandGeometry.TOP), guide);
        c.drawLine(cx - half, y(BrandGeometry.BOTTOM), cx + half, y(BrandGeometry.BOTTOM), guide);
        float outlineAlpha = (LINE_ALPHA + (0.95f - LINE_ALPHA) * guides) * (1f - span(t, SWEEP_START - 40, 300));
        if (outlineAlpha > 0) { outline.setAlpha(Math.round(255 * outlineAlpha * content)); c.drawPath(shapes, outline); }

        // Stage 2: layers are laid from the bottom up, each dropping into place with a nozzle pass along its top.
        for (int k = 0; k < BrandGeometry.LAYERS; k++) {
            int i = BrandGeometry.LAYERS - 1 - k;
            float p = span(t, LAYERS_START + k * LAYER_STEP, LAYER_TIME);
            if (p <= 0) continue;
            float e = ease.getInterpolation(p);
            int alpha = Math.round(255 * Math.min(1f, p * 1.6f) * content);
            int layerSave = c.save();
            c.translate(0, -(1f - e) * 7 * density);
            side.setAlpha(alpha); c.drawPath(back[i], side);
            face.setAlpha(alpha); c.drawPath(front[i], face);
            edge.setAlpha(Math.round(((BrandGeometry.EDGE_LIGHT >>> 24) & 255) * e * content)); c.drawPath(edges[i], edge);
            if (p < 1f) {
                float top = BrandGeometry.layerTop(i), nx = x(BrandGeometry.LEFT + (BrandGeometry.RIGHT - BrandGeometry.LEFT) * e);
                nozzle.setAlpha(Math.round(230 * (1f - p) * content));
                c.drawLine(x(BrandGeometry.LEFT), y(top), nx, y(top), nozzle);
            }
            c.restoreToCount(layerSave);
        }

        // Stage 3: a light sweep crosses the faces.
        float sw = span(t, SWEEP_START, SWEEP_TIME);
        if (sw > 0 && sw < 1) {
            sweepMatrix.setTranslate(x(BrandGeometry.LEFT - 20) + (BrandGeometry.RIGHT - BrandGeometry.LEFT + 40) * unit * soft.getInterpolation(sw), 0);
            sweepMatrix.postSkew(-0.35f, 0, cx, cy);
            sweep.getShader().setLocalMatrix(sweepMatrix);
            sweep.setAlpha(Math.round(255 * content));
            for (Path p : front) c.drawPath(p, sweep);
        }
        c.restoreToCount(save);

        // Stage 4: the name rises in below the mark.
        if (titleIn > 0) {
            float base = cy - lift + 32 * unit + 18 * density - title.ascent() + (1f - titleIn) * 10 * density;
            int a = Math.round(255 * titleIn * content);
            title.setAlpha(a); subtitle.setAlpha(a);
            c.save(); c.scale(s, s, cx, cy);
            float left = cx - titleWidth / 2f;
            c.drawText("Kepler", left, base, title);
            c.drawText("Slicer", left + titleWidthKepler, base, subtitle);
            c.restore();
        }
    }
}
