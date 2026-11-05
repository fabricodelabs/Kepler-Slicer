package com.keplerslicer.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Approximate XY preview for extrusion after the engine's layer markers. */
final class GcodeAnalysis {
    private static final int MAX_SEGMENTS = 1_000_000, MAX_LAYERS = 20_000;
    private static final int MAX_ARC_SEGMENTS = 4096;
    private static final double ARC_CHORD_ERROR = .025, ARC_RADIUS_TOLERANCE = .020;
    // G-code's E axis is a command word, not an exponent (X1E2 means X1, E2).
    private static final Pattern WORD = Pattern.compile("([A-Za-z])\\s*([-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))");
    static final class Layer {
        float[] segments = new float[128]; int size; double z = Double.NaN;
        /** Per-segment display attributes: engine feature type, feed (mm/min), extrusion per mm, nozzle temperature. */
        byte[] types = new byte[32]; float[] feed = new float[32], flow = new float[32], temp = new float[32];
        void add(double x1, double y1, double x2, double y2) {
            if (size + 4 > segments.length) { float[] next = new float[segments.length * 2]; System.arraycopy(segments, 0, next, 0, size); segments = next; }
            segments[size++] = (float)x1; segments[size++] = (float)y1; segments[size++] = (float)x2; segments[size++] = (float)y2;
        }
        void attributes(byte type, float f, float w, float t) {
            int index = size / 4 - 1; if (index >= types.length) { int n = Math.max(types.length * 2, index + 1); types = java.util.Arrays.copyOf(types, n); feed = java.util.Arrays.copyOf(feed, n); flow = java.util.Arrays.copyOf(flow, n); temp = java.util.Arrays.copyOf(temp, n); }
            types[index] = type; feed[index] = f; flow[index] = w; temp[index] = t;
        }
    }
    final List<Layer> layers = new ArrayList<>();
    /** Feature classes shown in the Kepler v3 Preview legend (node 3:394). */
    static final byte OTHER = 0, OUTER_WALL = 1, INNER_WALL = 2, SPARSE_INFILL = 3, SOLID_INFILL = 4, SUPPORT = 5, BRIDGE = 6;
    private byte currentType = OTHER; private double currentFeed, currentFlow, currentTemp = Double.NaN;
    static byte featureOf(String featureType) {
        String t = featureType.trim().toLowerCase(Locale.ROOT);
        if (t.contains("bridge")) return BRIDGE;
        if (t.contains("support")) return SUPPORT;
        if (t.equals("outer wall") || t.contains("external perimeter") || t.contains("overhang")) return OUTER_WALL;
        if (t.contains("inner wall") || t.equals("perimeter") || t.contains("internal perimeter")) return INNER_WALL;
        if (t.contains("sparse") || t.equals("internal infill")) return SPARSE_INFILL;
        if (t.contains("solid") || t.contains("top surface") || t.contains("bottom surface") || t.contains("ironing") || t.contains("gap")) return SOLID_INFILL;
        return OTHER;
    }
    double minFeed = Double.POSITIVE_INFINITY, maxFeed = 0, minFlow = Double.POSITIVE_INFINITY, maxFlow = 0, minTemp = Double.POSITIVE_INFINITY, maxTemp = 0;
    int segments, renderedArcs, arcSegments, arcMoves, arcTravel, unsupportedMoves, skippedUnknown, skippedBeforeLayers, malformedLines, unknownResets, ignoredOther;
    final Map<String, Integer> ignoredCommands = new TreeMap<>();
    double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
    double linearMinX = Double.POSITIVE_INFINITY, linearMinY = Double.POSITIVE_INFINITY, linearMaxX = Double.NEGATIVE_INFINITY, linearMaxY = Double.NEGATIVE_INFINITY;

    static GcodeAnalysis read(File file) throws IOException {
        if (file.length() > 200L * 1024 * 1024) throw new IOException(L.t("Файл G-code больше 200 МБ."));
        GcodeAnalysis out = new GcodeAnalysis();
        boolean absoluteXYZ = true, absoluteE = true, xKnown = false, yKnown = false, zKnown = false, eKnown = true;
        boolean relativeArcCenters = true;
        int plane = 17;
        double x = 0, y = 0, z = 0, e = 0, units = 1, xOffset = 0, yOffset = 0;
        Layer layer = null;
        String modalMotion = null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new IOException(L.t("Чтение отменено."));
                if (line.length() > 8192) throw new IOException(L.t("Слишком длинная строка G-code."));
                String trimmed = line.trim();
                if (trimmed.startsWith(";LAYER_CHANGE")) {
                    if (out.layers.size() >= MAX_LAYERS) throw new IOException(L.t("Слишком много слоёв для просмотра."));
                    layer = new Layer(); out.layers.add(layer); continue;
                }
                if (trimmed.startsWith(";TYPE:")) { out.currentType = featureOf(trimmed.substring(6)); continue; }
                if (layer != null && (trimmed.startsWith(";Z:") || trimmed.startsWith("; Z_HEIGHT:"))) {
                    int colon = trimmed.indexOf(':');
                    try { double value = Double.parseDouble(trimmed.substring(colon + 1).trim()); if (Double.isFinite(value)) layer.z = value; } catch (NumberFormatException ignored) { out.malformedLines++; }
                    continue;
                }
                int semicolon = trimmed.indexOf(';'); if (semicolon >= 0) trimmed = trimmed.substring(0, semicolon);
                trimmed = trimmed.replaceAll("\\([^)]*\\)", " ");
                Matcher m = WORD.matcher(trimmed); if (!m.find()) continue;
                char command = Character.toUpperCase(m.group(1).charAt(0)); String number = m.group(2);
                if (command == 'N') {
                    if (!m.find()) continue;
                    command = Character.toUpperCase(m.group(1).charAt(0)); number = m.group(2);
                }
                String code = command + number.toUpperCase(Locale.ROOT);
                // Do not invent an order for blocks containing multiple commands.
                if (command == 'G') {
                    Matcher rest = WORD.matcher(trimmed); rest.region(m.end(), trimmed.length()); boolean multi = false;
                    while (rest.find()) { char c = Character.toUpperCase(rest.group(1).charAt(0)); if (c == 'G' || c == 'M' || c == 'T') { multi = true; break; } }
                    if (multi) { out.unsupportedMoves++; out.ignored(L.t("несколько команд в строке")); xKnown = yKnown = zKnown = eKnown = false; continue; }
                }
                // A motion line may omit its G0/G1/G2/G3 when the last motion is modal.
                if (command == 'X' || command == 'Y' || command == 'Z' || command == 'E' || command == 'F') {
                    if (modalMotion == null) { out.unsupportedMoves++; out.ignored("motion without G0–G3"); xKnown = yKnown = zKnown = eKnown = false; continue; }
                    code = modalMotion; m.reset();
                }
                if ("G90".equals(code)) { absoluteXYZ = true; continue; }
                if ("G91".equals(code)) { absoluteXYZ = false; continue; }
                if ("G90.1".equals(code)) { relativeArcCenters = false; out.ignored(code); continue; }
                if ("G91.1".equals(code)) { relativeArcCenters = true; continue; }
                if ("G17".equals(code)) { plane = 17; continue; }
                if ("G18".equals(code)) { plane = 18; out.ignored(code); continue; }
                if ("G19".equals(code)) { plane = 19; out.ignored(code); continue; }
                if ("M82".equals(code)) { absoluteE = true; continue; }
                if ("M83".equals(code)) { absoluteE = false; continue; }
                if ("G20".equals(code)) { units = 25.4; continue; }
                if ("G21".equals(code)) { units = 1; continue; }
                if (command == 'T') { out.unsupportedMoves++; out.ignored(code); xKnown = yKnown = zKnown = eKnown = false; continue; }
                boolean linear = "G0".equals(code) || "G00".equals(code) || "G1".equals(code) || "G01".equals(code);
                boolean arc = "G2".equals(code) || "G02".equals(code) || "G3".equals(code) || "G03".equals(code);
                if (linear || arc) modalMotion = code;
                boolean reset = "G92".equals(code), home = "G28".equals(code);
                if ("M104".equals(code) || "M109".equals(code)) { Matcher s = WORD.matcher(trimmed); s.region(m.end(), trimmed.length()); while (s.find()) if (Character.toUpperCase(s.group(1).charAt(0)) == 'S') { try { double t = Double.parseDouble(s.group(2)); if (t > 0 && t < 600) out.currentTemp = t; } catch (NumberFormatException ignoredTemp) { } } }
                if (!linear && !arc && !reset && !home) {
                    out.ignored(code);
                    if (command == 'G' && !("G4".equals(code) || "G04".equals(code))) { out.unsupportedMoves++; xKnown = yKnown = zKnown = eKnown = false; }
                    if (command == 'M' && ("M206".equals(code) || "M218".equals(code) || "M290".equals(code) || "M401".equals(code) || "M402".equals(code) || "M428".equals(code) || "M600".equals(code) || "M701".equals(code) || "M702".equals(code))) { out.unsupportedMoves++; xKnown = yKnown = zKnown = eKnown = false; }
                    continue;
                }
                Double dx = null, dy = null, dz = null, de = null, di = null, dj = null, dr = null, dk = null, dp = null;
                boolean invalid = false;
                while (m.find()) {
                    char axis = Character.toUpperCase(m.group(1).charAt(0));
                    if (axis == 'F') { try { double f = Double.parseDouble(m.group(2)) * units; if (f > 0 && Double.isFinite(f)) out.currentFeed = f; } catch (NumberFormatException ignoredFeed) { } continue; }
                    if (axis != 'X' && axis != 'Y' && axis != 'Z' && axis != 'E' && axis != 'I' && axis != 'J' && axis != 'K' && axis != 'R' && axis != 'P') continue;
                    double v;
                    try { v = Double.parseDouble(m.group(2)) * units; } catch (NumberFormatException bad) { invalid = true; break; }
                    if (!Double.isFinite(v) || Math.abs(v) > 10_000_000) { invalid = true; break; }
                    if (axis == 'X') dx = v; else if (axis == 'Y') dy = v; else if (axis == 'Z') dz = v; else if (axis == 'E') de = v;
                    else if (axis == 'I') di = v; else if (axis == 'J') dj = v; else if (axis == 'K') dk = v; else if (axis == 'R') dr = v; else dp = v;
                }
                if (invalid) { out.malformedLines++; xKnown = yKnown = zKnown = eKnown = false; continue; }
                if (home) { out.unsupportedMoves++; out.ignored(code); xKnown = yKnown = zKnown = false; xOffset = yOffset = 0; continue; }
                if (reset) {
                    if (dx == null && dy == null && dz == null && de == null) {
                        out.unsupportedMoves++; out.ignored(L.t("G92 (без осей)"));
                        xKnown = yKnown = zKnown = eKnown = false; xOffset = yOffset = 0; continue;
                    }
                    // G92 changes the coordinate frame without moving the head.
                    // Keep each XY offset so segments before and after it share a frame.
                    if (dx != null) { if (xKnown) xOffset += x - dx; else { xOffset = 0; out.unknownResets++; } x = dx; xKnown = true; }
                    if (dy != null) { if (yKnown) yOffset += y - dy; else { yOffset = 0; out.unknownResets++; } y = dy; yKnown = true; }
                    if (dz != null) { z = dz; zKnown = true; } if (de != null) { e = de; eKnown = true; } continue;
                }
                double nx = dx == null ? x : absoluteXYZ ? dx : x + dx, ny = dy == null ? y : absoluteXYZ ? dy : y + dy;
                double nz = dz == null ? z : absoluteXYZ ? dz : z + dz, ne = de == null ? e : absoluteE ? de : e + de;
                boolean nextXKnown = dx == null ? xKnown : absoluteXYZ || xKnown;
                boolean nextYKnown = dy == null ? yKnown : absoluteXYZ || yKnown;
                boolean nextZKnown = dz == null ? zKnown : absoluteXYZ || zKnown;
                boolean nextEKnown = de == null ? eKnown : absoluteE || eKnown;
                boolean extruding = de != null && (absoluteE ? eKnown && ne - e > 1e-9 : de > 1e-9);
                boolean extrusionUncertain = de != null && absoluteE && !eKnown;
                if (arc && (dx != null || dy != null || di != null || dj != null || dr != null || dk != null)) {
                    if (extrusionUncertain) { if (layer == null) out.skippedBeforeLayers++; else out.skippedUnknown++; }
                    else if (!extruding) out.arcTravel++;
                    else if (layer == null) out.skippedBeforeLayers++;
                    else if (!xKnown || !yKnown || !nextXKnown || !nextYKnown) out.skippedUnknown++;
                    else if ((out.currentFlow = Math.hypot(nx - x, ny - y) > 1e-9 ? (absoluteE ? ne - e : de) / Math.hypot(nx - x, ny - y) : 0) < 0 || plane != 17 || !relativeArcCenters || dk != null || dp != null ||
                            !out.addArc(layer, x, y, nx, ny, xOffset, yOffset, di, dj, dr, "G3".equals(code) || "G03".equals(code))) {
                        out.arcMoves++; out.ignored(code);
                    } else if (Double.isNaN(layer.z) && nextZKnown) layer.z = nz;
                }
                else if (extrusionUncertain && (dx != null || dy != null)) { if (layer == null) out.skippedBeforeLayers++; else out.skippedUnknown++; }
                else if (extruding && (dx != null || dy != null)) {
                    if (layer == null) out.skippedBeforeLayers++;
                    else if (!xKnown || !yKnown || !nextXKnown || !nextYKnown || !Double.isFinite(nx) || !Double.isFinite(ny)) out.skippedUnknown++;
                    else {
                        if (out.segments + out.arcSegments >= MAX_SEGMENTS) throw new IOException(L.t("Слишком много отрезков для просмотра."));
                        if (Double.isNaN(layer.z) && nextZKnown) layer.z = nz;
                        double px = x + xOffset, py = y + yOffset, pnx = nx + xOffset, pny = ny + yOffset;
                        double length = Math.hypot(nx - x, ny - y); out.currentFlow = length > 1e-9 ? (absoluteE ? ne - e : de) / length : 0;
                        out.addSegment(layer, px, py, pnx, pny, true); out.segments++;
                    }
                }
                x = nx; y = ny; z = nz; e = ne; xKnown = nextXKnown; yKnown = nextYKnown; zKnown = nextZKnown; eKnown = nextEKnown;
            }
        }
        return out;
    }
    private void addSegment(Layer layer, double x1, double y1, double x2, double y2, boolean linear) {
        layer.add(x1, y1, x2, y2); layer.attributes(currentType, (float) currentFeed, (float) currentFlow, (float) currentTemp);
        if (currentFeed > 0) { minFeed = Math.min(minFeed, currentFeed); maxFeed = Math.max(maxFeed, currentFeed); }
        if (currentFlow > 0) { minFlow = Math.min(minFlow, currentFlow); maxFlow = Math.max(maxFlow, currentFlow); }
        if (currentTemp > 0) { minTemp = Math.min(minTemp, currentTemp); maxTemp = Math.max(maxTemp, currentTemp); }
        minX = Math.min(minX, Math.min(x1, x2)); minY = Math.min(minY, Math.min(y1, y2));
        maxX = Math.max(maxX, Math.max(x1, x2)); maxY = Math.max(maxY, Math.max(y1, y2));
        if (linear) {
            linearMinX = Math.min(linearMinX, Math.min(x1, x2)); linearMinY = Math.min(linearMinY, Math.min(y1, y2));
            linearMaxX = Math.max(linearMaxX, Math.max(x1, x2)); linearMaxY = Math.max(linearMaxY, Math.max(y1, y2));
        }
    }
    /** Return false instead of guessing for an ambiguous, invalid or too-complex arc. */
    private boolean addArc(Layer layer, double x, double y, double nx, double ny, double ox, double oy,
                           Double i, Double j, Double r, boolean ccw) {
        if (r != null && (i != null || j != null) || r == null && i == null && j == null) return false;
        double vx = nx - x, vy = ny - y, chord = Math.hypot(vx, vy), cx, cy, radius;
        if (r != null) {
            radius = Math.abs(r);
            if (radius < 1e-9 || chord < 1e-9 || chord > 2 * radius + 1e-9) return false;
            double h = Math.sqrt(Math.max(0, radius * radius - chord * chord / 4));
            double side = (ccw ? 1 : -1) * (r >= 0 ? 1 : -1);
            cx = (x + nx) / 2 - side * vy / chord * h;
            cy = (y + ny) / 2 + side * vx / chord * h;
        } else {
            cx = x + (i == null ? 0 : i); cy = y + (j == null ? 0 : j);
            radius = Math.hypot(x - cx, y - cy);
            if (radius < 1e-9 || Math.abs(Math.hypot(nx - cx, ny - cy) - radius) > ARC_RADIUS_TOLERANCE) return false;
        }
        if (!Double.isFinite(radius) || !Double.isFinite(cx) || !Double.isFinite(cy)) return false;
        double start = Math.atan2(y - cy, x - cx), end = Math.atan2(ny - cy, nx - cx);
        double sweep = end - start;
        if (ccw) { if (sweep <= 0) sweep += 2 * Math.PI; }
        else { if (sweep >= 0) sweep -= 2 * Math.PI; }
        if (chord < 1e-9) sweep = ccw ? 2 * Math.PI : -2 * Math.PI;
        double arg = Math.max(-1, Math.min(1, 1 - ARC_CHORD_ERROR / radius));
        double step = Math.min(Math.PI / 4, 2 * Math.acos(arg));
        if (!Double.isFinite(step) || step <= 0) return false;
        int count = (int)Math.ceil(Math.abs(sweep) / step);
        if (count < 1 || count > MAX_ARC_SEGMENTS || segments + arcSegments + count > MAX_SEGMENTS) return false;
        double px = x + ox, py = y + oy;
        for (int n = 1; n <= count; n++) {
            double angle = start + sweep * n / count;
            double qx = (n == count ? nx : cx + radius * Math.cos(angle)) + ox;
            double qy = (n == count ? ny : cy + radius * Math.sin(angle)) + oy;
            addSegment(layer, px, py, qx, qy, false); px = qx; py = qy;
        }
        renderedArcs++; arcSegments += count; return true;
    }
    private void ignored(String code) {
        Integer count = ignoredCommands.get(code);
        if (count != null) ignoredCommands.put(code, count + 1);
        else if (ignoredCommands.size() < 512) ignoredCommands.put(code, 1);
        else ignoredOther++;
    }
}
