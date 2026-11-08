package com.keplerslicer.app;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Generates every Kepler Slicer brand asset from BrandGeometry: logo SVGs (primary, monochrome, light, dark, compact,
 * lockups, the three concepts), the adaptive / monochrome / legacy launcher icon, the Play Store icon, the system
 * splash drawable and PNG previews.
 *
 *   java -cp <classes> com.keplerslicer.app.BrandKit <repo root> <dir with Manrope-ExtraBold.ttf and Manrope-Medium.ttf>
 *
 * (tools/branding/build.ps1 compiles and runs it.) Edit BrandGeometry or the constants here, run it again, rebuild the app.
 */
public final class BrandKit {
    private static File root; private static Font bold, medium;

    public static void main(String[] a) throws Exception {
        root = new File(a[0]);
        bold = Font.createFont(Font.TRUETYPE_FONT, new File(a[1], "Manrope-ExtraBold.ttf"));
        medium = Font.createFont(Font.TRUETYPE_FONT, new File(a[1], "Manrope-Medium.ttf"));
        logos(); concepts(); androidResources(); pngs();
        System.out.println("Brand kit written to " + new File(root, "branding").getPath() + " and app/src/main/res");
    }

    // ------------------------------------------------------------------ geometry helpers
    static String pts(float[] p, float s, float tx, float ty) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < p.length; i += 2) b.append(i == 0 ? "M" : "L").append(f(p[i] * s + tx)).append(',').append(f(p[i + 1] * s + ty));
        return b.append('Z').toString();
    }
    static String f(double v) { String s = String.format(Locale.ROOT, "%.2f", v); return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s; }
    static String hex(int argb) { return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF); }
    static String alpha(int argb) { return f(((argb >>> 24) & 255) / 255.0); }
    static String layers(float s, float tx, float ty, boolean edge) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < BrandGeometry.LAYERS; i++) for (float[] p : edge ? BrandGeometry.edge(i) : BrandGeometry.layer(i)) b.append(pts(p, s, tx, ty));
        return b.toString();
    }
    static String solid(float s, float tx, float ty) { StringBuilder b = new StringBuilder(); for (float[] p : BrandGeometry.SHAPES) b.append(pts(p, s, tx, ty)); return b.toString(); }

    /** Text as outlines (no font needed by the viewer). */
    static String text(String t, Font font, float size, float x, float baseline) {
        GlyphVector gv = font.deriveFont(size).createGlyphVector(new FontRenderContext(null, true, true), t);
        return svgPath(gv.getOutline(x, baseline));
    }
    static float textWidth(String t, Font font, float size) {
        return (float) font.deriveFont(size).createGlyphVector(new FontRenderContext(null, true, true), t).getLogicalBounds().getWidth();
    }
    static String svgPath(Shape s) {
        StringBuilder b = new StringBuilder(); float[] c = new float[6];
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO: b.append('M').append(f(c[0])).append(',').append(f(c[1])); break;
                case PathIterator.SEG_LINETO: b.append('L').append(f(c[0])).append(',').append(f(c[1])); break;
                case PathIterator.SEG_QUADTO: b.append('Q').append(f(c[0])).append(',').append(f(c[1])).append(' ').append(f(c[2])).append(',').append(f(c[3])); break;
                case PathIterator.SEG_CUBICTO: b.append('C').append(f(c[0])).append(',').append(f(c[1])).append(' ').append(f(c[2])).append(',').append(f(c[3])).append(' ').append(f(c[4])).append(',').append(f(c[5])); break;
                default: b.append('Z');
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ SVG logos
    /** The full mark (extrusion, faces, light edges) for a scale/offset; colours by theme. */
    static String markSvg(float s, float tx, float ty, boolean light, String id) {
        int face0 = light ? BrandGeometry.LIGHT_FACE : BrandGeometry.FACE_TOP, face1 = light ? BrandGeometry.LIGHT_FACE : BrandGeometry.FACE_BOTTOM;
        int side = light ? BrandGeometry.LIGHT_SIDE : BrandGeometry.SIDE;
        float d = BrandGeometry.DEPTH * s;
        return "<defs><linearGradient id='" + id + "' x1='0' y1='" + f(BrandGeometry.TOP * s + ty) + "' x2='0' y2='" + f(BrandGeometry.BOTTOM * s + ty) + "' gradientUnits='userSpaceOnUse'>"
            + "<stop offset='0' stop-color='" + hex(face0) + "'/><stop offset='1' stop-color='" + hex(face1) + "'/></linearGradient></defs>"
            + "<path fill='" + hex(side) + "' d='" + layers(s, tx + d, ty + d, false) + "'/>"
            + "<path fill='url(#" + id + ")' d='" + layers(s, tx, ty, false) + "'/>"
            + (light ? "" : "<path fill='" + hex(BrandGeometry.EDGE_LIGHT) + "' fill-opacity='" + alpha(BrandGeometry.EDGE_LIGHT) + "' d='" + layers(s, tx, ty, true) + "'/>");
    }
    static void svg(String path, int w, int h, String body) throws IOException {
        write(path, "<svg xmlns='http://www.w3.org/2000/svg' width='" + w + "' height='" + h + "' viewBox='0 0 " + w + " " + h + "'>" + body + "</svg>\n");
    }
    static String graphite(int w, int h, float r) {
        return "<defs><linearGradient id='bg' x1='0' y1='0' x2='0' y2='" + h + "' gradientUnits='userSpaceOnUse'><stop offset='0' stop-color='" + hex(BrandGeometry.GRAPHITE_TOP)
            + "'/><stop offset='1' stop-color='" + hex(BrandGeometry.GRAPHITE_BOTTOM) + "'/></linearGradient></defs><rect width='" + w + "' height='" + h + "' rx='" + f(r) + "' fill='url(#bg)'/>";
    }

    static void logos() throws IOException {
        svg("branding/logo/primary.svg", 100, 100, markSvg(1, 0, 0, false, "face"));
        svg("branding/logo/monochrome.svg", 100, 100, "<path fill='#000000' d='" + layers(1, 0, 0, false) + "'/>");
        svg("branding/logo/mark-compact.svg", 100, 100, "<path fill='" + hex(BrandGeometry.MINT) + "' d='" + solid(1, 0, 0) + "'/>");
        svg("branding/logo/mark-compact-monochrome.svg", 100, 100, "<path fill='#000000' d='" + solid(1, 0, 0) + "'/>");
        // Horizontal lockups: mark + "Kepler Slicer" (text as outlines).
        for (boolean light : new boolean[]{false, true}) {
            float size = 44, gap = 22, s = 1.1f, markW = (BrandGeometry.RIGHT - BrandGeometry.LEFT + BrandGeometry.DEPTH) * s;
            float tx = 40 - BrandGeometry.LEFT * s, ty = 30 - BrandGeometry.TOP * s, x = 40 + markW + gap, base = 30 + 64 * s / 2 + size * 0.36f;
            float kw = textWidth("Kepler ", bold, size), sw = textWidth("Slicer", medium, size);
            int w = Math.round(x + kw + sw + 40), h = Math.round(64 * s + 60);
            String body = (light ? "<rect width='" + w + "' height='" + h + "' fill='#FFFFFF'/>" : graphite(w, h, 0)) + markSvg(s, tx, ty, light, "face")
                + "<path fill='" + (light ? hex(BrandGeometry.INK) : "#FFFFFF") + "' d='" + text("Kepler", bold, size, x, base) + "'/>"
                + "<path fill='" + (light ? hex(BrandGeometry.LIGHT_FACE) : hex(BrandGeometry.MINT)) + "' d='" + text("Slicer", medium, size, x + kw, base) + "'/>";
            svg("branding/logo/" + (light ? "light" : "dark") + ".svg", w, h, body);
            // Transparent lockup for use on any background of the same tone.
            svg("branding/logo/lockup-" + (light ? "on-light" : "on-dark") + ".svg", w, h, body.substring(body.indexOf(light ? "/>" : "</rect>") + (light ? 2 : 7)));
        }
        // Stacked lockup (splash composition).
        float s = 1.4f, size = 30; float kw = textWidth("Kepler ", bold, size), sw = textWidth("Slicer", medium, size);
        int w = Math.round(Math.max(100 * s, kw + sw) + 60), h = Math.round(100 * s + 70);
        float tx = (w - 100 * s) / 2f, x = (w - kw - sw) / 2f;
        svg("branding/logo/lockup-stacked.svg", w, h, graphite(w, h, 0) + markSvg(s, tx, 0, false, "face")
            + "<path fill='#FFFFFF' d='" + text("Kepler", bold, size, x, 100 * s + 34) + "'/><path fill='" + hex(BrandGeometry.MINT) + "' d='" + text("Slicer", medium, size, x + kw, 100 * s + 34) + "'/>");
    }

    /** Three directions considered; A (sliced K) was chosen — see branding/README.md. */
    static void concepts() throws IOException {
        svg("branding/logo/concepts/concept-a-sliced-k.svg", 100, 100, graphite(100, 100, 22) + markSvg(0.8f, 10, 10, false, "face"));
        // B: orbit K (Kepler's orbits): a monoline K inside an inclined ellipse with a planet.
        svg("branding/logo/concepts/concept-b-orbit-k.svg", 100, 100, graphite(100, 100, 22)
            + "<ellipse cx='50' cy='50' rx='38' ry='17' fill='none' stroke='" + hex(BrandGeometry.MINT) + "' stroke-width='3' transform='rotate(-24 50 50)'/>"
            + "<path d='M38,26V74M38,52L62,26M45,46L64,74' fill='none' stroke='#FFFFFF' stroke-width='8' stroke-linecap='round' stroke-linejoin='round'/>"
            + "<circle cx='83' cy='34' r='5' fill='" + hex(BrandGeometry.MINT) + "'/>");
        // C: isometric cube with the K cut into its front face.
        svg("branding/logo/concepts/concept-c-iso-cube.svg", 100, 100, graphite(100, 100, 22)
            + "<path d='M50,14L82,32L50,50L18,32Z' fill='" + hex(BrandGeometry.FACE_TOP) + "'/><path d='M18,32L50,50L50,88L18,70Z' fill='" + hex(BrandGeometry.FACE_BOTTOM) + "'/>"
            + "<path d='M82,32L50,50L50,88L82,70Z' fill='" + hex(BrandGeometry.SIDE) + "'/>"
            + "<path d='M27,44V71M27,58L40,48M32,55L41,76' fill='none' stroke='" + hex(BrandGeometry.GRAPHITE_BOTTOM) + "' stroke-width='4.5' stroke-linecap='round'/>");
    }

    // ------------------------------------------------------------------ Android resources
    static final float ICON_S = 0.70f;
    static float iconTx() { return 54 - (BrandGeometry.LEFT + BrandGeometry.RIGHT + BrandGeometry.DEPTH) / 2f * ICON_S; }
    static float iconTy() { return 54 - (BrandGeometry.TOP + BrandGeometry.BOTTOM + BrandGeometry.DEPTH) / 2f * ICON_S; }

    static void androidResources() throws IOException {
        float s = ICON_S, tx = iconTx(), ty = iconTy(), d = BrandGeometry.DEPTH * s;
        String head = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- Generated by tools/branding/BrandKit.java from BrandGeometry; do not edit by hand. -->\n";
        write("app/src/main/res/drawable/ic_launcher_foreground.xml", head
            + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" xmlns:aapt=\"http://schemas.android.com/aapt\"\n    android:width=\"108dp\" android:height=\"108dp\" android:viewportWidth=\"108\" android:viewportHeight=\"108\">\n"
            + "    <path android:fillColor=\"" + hex(BrandGeometry.SIDE) + "\" android:pathData=\"" + layers(s, tx + d, ty + d, false) + "\" />\n"
            + "    <path android:pathData=\"" + layers(s, tx, ty, false) + "\">\n        <aapt:attr name=\"android:fillColor\">\n"
            + "            <gradient android:type=\"linear\" android:startX=\"54\" android:startY=\"" + f(BrandGeometry.TOP * s + ty) + "\" android:endX=\"54\" android:endY=\"" + f(BrandGeometry.BOTTOM * s + ty) + "\"\n"
            + "                android:startColor=\"" + hex(BrandGeometry.FACE_TOP) + "\" android:endColor=\"" + hex(BrandGeometry.FACE_BOTTOM) + "\" />\n        </aapt:attr>\n    </path>\n"
            + "    <path android:fillColor=\"" + hex(BrandGeometry.EDGE_LIGHT) + "\" android:fillAlpha=\"" + alpha(BrandGeometry.EDGE_LIGHT) + "\" android:pathData=\"" + layers(s, tx, ty, true) + "\" />\n</vector>\n");
        write("app/src/main/res/drawable/ic_launcher_background.xml", head
            + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" xmlns:aapt=\"http://schemas.android.com/aapt\"\n    android:width=\"108dp\" android:height=\"108dp\" android:viewportWidth=\"108\" android:viewportHeight=\"108\">\n"
            + "    <path android:pathData=\"M0,0h108v108h-108z\">\n        <aapt:attr name=\"android:fillColor\">\n"
            + "            <gradient android:type=\"linear\" android:startX=\"54\" android:startY=\"0\" android:endX=\"54\" android:endY=\"108\" android:startColor=\"" + hex(BrandGeometry.GRAPHITE_TOP) + "\" android:endColor=\"" + hex(BrandGeometry.GRAPHITE_BOTTOM) + "\" />\n        </aapt:attr>\n    </path>\n"
            + "    <path android:pathData=\"M0,0h108v108h-108z\">\n        <aapt:attr name=\"android:fillColor\">\n"
            + "            <gradient android:type=\"radial\" android:centerX=\"54\" android:centerY=\"50\" android:gradientRadius=\"46\" android:startColor=\"#385EE0C8\" android:endColor=\"#005EE0C8\" />\n        </aapt:attr>\n    </path>\n</vector>\n");
        write("app/src/main/res/drawable/ic_launcher_monochrome.xml", head
            + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:width=\"108dp\" android:height=\"108dp\" android:viewportWidth=\"108\" android:viewportHeight=\"108\">\n"
            + "    <path android:fillColor=\"#FFFFFFFF\" android:pathData=\"" + layers(s, tx, ty, false) + "\" />\n</vector>\n");
        String adaptive = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <background android:drawable=\"@drawable/ic_launcher_background\" />\n    <foreground android:drawable=\"@drawable/ic_launcher_foreground\" />\n"
            + "    <monochrome android:drawable=\"@drawable/ic_launcher_monochrome\" />\n</adaptive-icon>\n";
        write("app/src/main/res/mipmap-anydpi/ic_launcher.xml", adaptive);
        write("app/src/main/res/mipmap-anydpi/ic_launcher_round.xml", adaptive);
        // System splash (Android 12+): the mark as a thin blueprint, 240dp icon area, content inside the 160dp circle.
        float ss = SPLASH_SCALE, st = 120 - 50 * ss;
        StringBuilder outline = new StringBuilder(); for (float[] p : BrandGeometry.SHAPES) outline.append(pts(p, ss, st, st));
        write("app/src/main/res/drawable/splash_blueprint.xml", head.replace("-->", "Thin \"blueprint\" of the mark for the system splash; the in-app splash (BrandSplash) continues from it at the same place. -->")
            + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:width=\"240dp\" android:height=\"240dp\" android:viewportWidth=\"240\" android:viewportHeight=\"240\">\n"
            + "    <path android:strokeColor=\"" + hex(BrandGeometry.MINT) + "\" android:strokeAlpha=\"" + f(SPLASH_LINE_ALPHA) + "\" android:strokeWidth=\"" + f(SPLASH_LINE) + "\" android:strokeLineJoin=\"round\" android:pathData=\"" + outline + "\" />\n</vector>\n");
        write("branding/splash/animation-source/splash-blueprint.svg", "<svg xmlns='http://www.w3.org/2000/svg' width='240' height='240' viewBox='0 0 240 240'><rect width='240' height='240' fill='#1C1F25'/><path fill='none' stroke='"
            + hex(BrandGeometry.MINT) + "' stroke-opacity='" + f(SPLASH_LINE_ALPHA) + "' stroke-width='" + f(SPLASH_LINE) + "' stroke-linejoin='round' d='" + outline + "'/></svg>\n");
        write("branding/splash/animation-source/final-frame.svg", "<svg xmlns='http://www.w3.org/2000/svg' width='240' height='240' viewBox='0 0 240 240'><rect width='240' height='240' fill='#1C1F25'/>" + markSvg(ss, st, st, false, "face") + "</svg>\n");
        // Brand files mirror the Android layers.
        write("branding/app-icon/adaptive/foreground.svg", "<svg xmlns='http://www.w3.org/2000/svg' width='108' height='108' viewBox='0 0 108 108'>" + markSvg(s, tx, ty, false, "face") + "</svg>\n");
        write("branding/app-icon/adaptive/background.svg", "<svg xmlns='http://www.w3.org/2000/svg' width='108' height='108' viewBox='0 0 108 108'>" + graphite(108, 108, 0)
            + "<defs><radialGradient id='glow' cx='54' cy='50' r='46' gradientUnits='userSpaceOnUse'><stop offset='0' stop-color='#5EE0C8' stop-opacity='0.22'/><stop offset='1' stop-color='#5EE0C8' stop-opacity='0'/></radialGradient></defs><rect width='108' height='108' fill='url(#glow)'/></svg>\n");
        write("branding/app-icon/monochrome/monochrome.svg", "<svg xmlns='http://www.w3.org/2000/svg' width='108' height='108' viewBox='0 0 108 108'><path fill='#000000' d='" + layers(s, tx, ty, false) + "'/></svg>\n");
    }
    /** Splash mark scale (dp per design unit) and line style; BrandSplash uses the same values. */
    static final float SPLASH_SCALE = 1.5f, SPLASH_LINE = 1.2f, SPLASH_LINE_ALPHA = 0.55f;

    // ------------------------------------------------------------------ PNG renders (Java2D)
    static Path2D path(List<float[]> polys, double s, double tx, double ty) {
        Path2D p = new Path2D.Float(Path2D.WIND_NON_ZERO);
        for (float[] q : polys) { for (int i = 0; i < q.length; i += 2) { double x = q[i] * s + tx, y = q[i + 1] * s + ty; if (i == 0) p.moveTo(x, y); else p.lineTo(x, y); } p.closePath(); }
        return p;
    }
    static List<float[]> allLayers() { List<float[]> l = new java.util.ArrayList<>(); for (int i = 0; i < BrandGeometry.LAYERS; i++) l.addAll(BrandGeometry.layer(i)); return l; }
    static List<float[]> allEdges() { List<float[]> l = new java.util.ArrayList<>(); for (int i = 0; i < BrandGeometry.LAYERS; i++) l.addAll(BrandGeometry.edge(i)); return l; }

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }
    static Color c(int argb) { return new Color(argb, true); }
    static void background(Graphics2D g, Shape clip, double size) {
        g.setPaint(new GradientPaint(0, 0, c(BrandGeometry.GRAPHITE_TOP), 0, (float) size, c(BrandGeometry.GRAPHITE_BOTTOM))); g.fill(clip);
        g.setPaint(new RadialGradientPaint((float) (size * 0.5), (float) (size * 50 / 108.0), (float) (size * 46 / 108.0), new float[]{0, 1}, new Color[]{new Color(0x5E, 0xE0, 0xC8, 56), new Color(0x5E, 0xE0, 0xC8, 0)}));
        g.fill(clip);
    }
    /** Mark in 108-unit icon space scaled to size px. */
    static void mark(Graphics2D g, double size, boolean light) {
        double u = size / 108.0, s = ICON_S * u, tx = iconTx() * u, ty = iconTy() * u, d = BrandGeometry.DEPTH * s;
        g.setPaint(c(light ? BrandGeometry.LIGHT_SIDE : BrandGeometry.SIDE)); g.fill(path(allLayers(), s, tx + d, ty + d));
        g.setPaint(light ? c(BrandGeometry.LIGHT_FACE) : new GradientPaint(0, (float) (BrandGeometry.TOP * s + ty), c(BrandGeometry.FACE_TOP), 0, (float) (BrandGeometry.BOTTOM * s + ty), c(BrandGeometry.FACE_BOTTOM)));
        g.fill(path(allLayers(), s, tx, ty));
        if (!light) { g.setPaint(c(BrandGeometry.EDGE_LIGHT)); g.fill(path(allEdges(), s, tx, ty)); }
    }
    static BufferedImage icon(int px, Shape mask) {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB); Graphics2D g = g(img);
        background(g, mask, px); g.setClip(mask); mark(g, px, false); g.dispose(); return img;
    }
    /** Adaptive masks: the icon is drawn at 108 units and masked in the central 72 units, like a launcher. */
    static BufferedImage launcherShape(int px, String shape) {
        double full = px * 108.0 / 72.0, off = -(full - px) / 2;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB); Graphics2D g = g(img);
        Shape mask = shape.equals("circle") ? new Ellipse2D.Double(0, 0, px, px) : shape.equals("rounded") ? new RoundRectangle2D.Double(0, 0, px, px, px * 0.36, px * 0.36)
            : shape.equals("squircle") ? squircle(px) : teardrop(px);
        g.setClip(mask); g.translate(off, off); background(g, new Rectangle2D.Double(0, 0, full, full), full); mark(g, full, false); g.dispose(); return img;
    }
    static Shape squircle(double px) {
        Path2D p = new Path2D.Double(); int n = 120;
        for (int i = 0; i <= n; i++) { double t = 2 * Math.PI * i / n, ct = Math.cos(t), st = Math.sin(t);
            double x = Math.signum(ct) * Math.pow(Math.abs(ct), 2 / 4.0), y = Math.signum(st) * Math.pow(Math.abs(st), 2 / 4.0);
            if (i == 0) p.moveTo(px / 2 + x * px / 2, px / 2 + y * px / 2); else p.lineTo(px / 2 + x * px / 2, px / 2 + y * px / 2); }
        p.closePath(); return p;
    }
    static Shape teardrop(double px) {
        double r = px / 2; Path2D p = new Path2D.Double(); p.moveTo(r, 0); p.curveTo(r * 1.552, 0, px, r * 0.448, px, r); p.lineTo(px, px); p.lineTo(r, px);
        p.curveTo(r * 0.448, px, 0, r * 1.552, 0, r); p.curveTo(0, r * 0.448, r * 0.448, 0, r, 0); p.closePath(); return p;
    }

    static void pngs() throws IOException {
        int[][] dens = {{48, 0}, {72, 0}, {96, 0}, {144, 0}, {192, 0}}; String[] names = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (int i = 0; i < names.length; i++) {
            int px = dens[i][0];
            png(launcherShape(px, "rounded"), "app/src/main/res/mipmap-" + names[i] + "/ic_launcher.png");
            png(launcherShape(px, "circle"), "app/src/main/res/mipmap-" + names[i] + "/ic_launcher_round.png");
            png(launcherShape(px, "rounded"), "branding/app-icon/legacy/ic_launcher-" + names[i] + ".png");
            png(launcherShape(px, "circle"), "branding/app-icon/legacy/ic_launcher_round-" + names[i] + ".png");
        }
        // Play Store: full-bleed square, the store applies its own mask.
        BufferedImage play = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB); Graphics2D g = g(play);
        double full = 512 * 108.0 / 72.0, off = -(full - 512) / 2; g.translate(off, off); background(g, new Rectangle2D.Double(0, 0, full, full), full); mark(g, full, false); g.dispose();
        png(play, "branding/app-icon/play-store-512.png");
        // Previews: launcher masks, small sizes, themed icon, logo on light/dark.
        String[] shapes = {"circle", "squircle", "rounded", "teardrop"}; BufferedImage sheet = new BufferedImage(4 * 220 + 60, 520, BufferedImage.TYPE_INT_ARGB); g = g(sheet);
        g.setPaint(new Color(0xF2F3F5)); g.fillRect(0, 0, sheet.getWidth(), 260); g.setPaint(new Color(0x101114)); g.fillRect(0, 260, sheet.getWidth(), 260);
        for (int i = 0; i < 4; i++) { BufferedImage ic = launcherShape(180, shapes[i]); g.drawImage(ic, 40 + i * 220, 40, null); g.drawImage(ic, 40 + i * 220, 300, null); }
        g.dispose(); png(sheet, "branding/app-icon/previews/launcher-masks.png");
        BufferedImage small = new BufferedImage(420, 120, BufferedImage.TYPE_INT_ARGB); g = g(small); g.setPaint(new Color(0x1B1E23)); g.fillRect(0, 0, 420, 120);
        int x = 20; for (int px : new int[]{24, 32, 48, 64, 96}) { g.drawImage(launcherShape(px, "circle"), x, (120 - px) / 2, null); x += px + 24; }
        g.dispose(); png(small, "branding/app-icon/previews/small-sizes.png");
        BufferedImage themed = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB); g = g(themed);
        g.setPaint(new Color(0xD9E7E3)); g.fill(new Ellipse2D.Double(10, 10, 180, 180)); double u = 180 * 108.0 / 72.0 / 108.0, s = ICON_S * u, off2 = 10 - (180 * 108.0 / 72.0 - 180) / 2;
        g.setPaint(new Color(0x1F4D45)); g.fill(path(allLayers(), s, iconTx() * u + off2, iconTy() * u + off2)); g.dispose(); png(themed, "branding/app-icon/previews/themed-icon.png");
        // Logo previews for promo use.
        BufferedImage hero = new BufferedImage(1200, 630, BufferedImage.TYPE_INT_ARGB); g = g(hero);
        background(g, new Rectangle2D.Double(0, 0, 1200, 630), 630); g.setPaint(new GradientPaint(0, 0, c(BrandGeometry.GRAPHITE_TOP), 0, 630, c(BrandGeometry.GRAPHITE_BOTTOM))); g.fillRect(0, 0, 1200, 630);
        g.translate(130, 112); mark(g, 400, false); g.translate(-130, -112);
        g.setFont(bold.deriveFont(92f)); g.setPaint(Color.WHITE); g.drawString("Kepler", 520, 350);
        float kw = (float) g.getFontMetrics().stringWidth("Kepler "); g.setFont(medium.deriveFont(92f)); g.setPaint(c(BrandGeometry.MINT)); g.drawString("Slicer", 520 + kw, 350);
        g.setFont(medium.deriveFont(30f)); g.setPaint(new Color(255, 255, 255, 170)); g.drawString("3D slicer for Android", 524, 410); g.dispose();
        png(hero, "branding/logo/previews/hero-1200x630.png");
        for (String n : new String[]{"concept-a-sliced-k"}) { BufferedImage m = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB); g = g(m); g.translate(-64 * 0.0, 0); mark(g, 256, false); g.dispose(); png(m, "branding/logo/previews/" + n + "-mark.png"); }
        BufferedImage lightMark = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB); g = g(lightMark); g.setPaint(Color.WHITE); g.fillRect(0, 0, 256, 256); mark(g, 256, true); g.dispose(); png(lightMark, "branding/logo/previews/mark-on-light.png");
    }

    static void png(BufferedImage img, String rel) throws IOException { File f = new File(root, rel); f.getParentFile().mkdirs(); ImageIO.write(img, "png", f); }
    static void write(String rel, String text) throws IOException { File f = new File(root, rel); f.getParentFile().mkdirs(); Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8)); }
    @SuppressWarnings("unused") private static final AlphaComposite KEEP = AlphaComposite.SrcOver; @SuppressWarnings("unused") private static final BasicStroke UNUSED = new BasicStroke(1);
}
