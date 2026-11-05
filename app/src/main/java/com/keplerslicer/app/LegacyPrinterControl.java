package com.keplerslicer.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Live status and job control for Duet (RepRapFirmware standalone and DSF), Repetier Server, Creality OS
 * (K1/K2 WebSocket, port 9999) and Elegoo (Neptune via the OctoPrint-compatible API, Centauri Carbon via SDCP).
 * Status is returned as a normalised "kepler" object: state, nozzle/bed/chamber [actual, target], progress 0..1,
 * seconds_left, file. Commands are explicit actions confirmed by the user; nothing is retried.
 */
final class LegacyPrinterControl {
    private static final int TIMEOUT_MS = 10000;
    private LegacyPrinterControl() {}

    static boolean supportsStatus(PrinterEndpoint e) {
        switch (e.type) { case DUET: case REPETIER: case CREALITYPRINT: return true; case ELEGOOLINK: return elegooModel(e) != 2; default: return false; }
    }

    // ---------------------------------------------------------------- status
    static PrinterHostClient.Status status(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        JSONObject k;
        switch (e.type) {
            case DUET: k = duetStatus(e, c); break;
            case REPETIER: k = repetierStatus(e, c); break;
            case CREALITYPRINT: k = crealityStatus(e, c); break;
            case ELEGOOLINK: k = elegooModel(e) == 1 ? sdcpStatus(e, c) : elegooOctoStatus(e, c); break;
            default: throw new IOException("Статус для этого сервера не поддерживается.");
        }
        try { return new PrinterHostClient.Status(k.optString("state", "unknown"), new JSONObject().put("kepler", k).toString()); }
        catch (JSONException x) { throw new IOException(x); }
    }

    static void job(PrinterEndpoint e, PrinterHostClient.Job job, NetworkHttp.Cancellation c) throws IOException {
        switch (e.type) {
            case DUET: duetGcode(e, job == PrinterHostClient.Job.PAUSE ? "M25" : job == PrinterHostClient.Job.RESUME ? "M24" : "M25\nM0", c); return;
            case REPETIER: {
                if (job == PrinterHostClient.Job.PAUSE) repetier(e, "send", new JSONObject(map("cmd", "@pause")), c);
                else repetier(e, job == PrinterHostClient.Job.RESUME ? "continueJob" : "stopJob", new JSONObject(), c);
                return;
            }
            case CREALITYPRINT: crealitySet(e, job == PrinterHostClient.Job.PAUSE ? "pause" : job == PrinterHostClient.Job.RESUME ? "pause" : "stop", job == PrinterHostClient.Job.RESUME ? 0 : 1, c); return;
            case ELEGOOLINK: {
                int m = elegooModel(e);
                if (m == 1) { sdcpCommand(e, job == PrinterHostClient.Job.PAUSE ? 129 : job == PrinterHostClient.Job.RESUME ? 131 : 130, new JSONObject(), c); return; }
                if (m == 0) { octo(e).job(job, c); return; }
                throw new IOException("Elegoo Centauri Carbon 2: управление пока не поддерживается.");
            }
            default: throw new IOException("Управление печатью для этого сервера не поддерживается.");
        }
    }

    /** Fixed G-code built by PrinterHostClient (speed, moves, homing). */
    static void gcode(PrinterEndpoint e, String lines, NetworkHttp.Cancellation c) throws IOException {
        switch (e.type) {
            case DUET: duetGcode(e, lines.trim(), c); return;
            case REPETIER: for (String l : lines.split("\n")) if (!l.isEmpty()) repetier(e, "send", new JSONObject(map("cmd", l)), c); return;
            default: throw new IOException("Команды G-code для этого сервера не поддерживаются.");
        }
    }

    static void light(PrinterEndpoint e, boolean on, NetworkHttp.Cancellation c) throws IOException {
        if (e.type == PrinterEndpoint.Type.CREALITYPRINT) { crealitySet(e, "lightSw", on ? 1 : 0, c); return; }
        if (e.type == PrinterEndpoint.Type.ELEGOOLINK && elegooModel(e) == 1) {
            try { sdcpCommand(e, 403, new JSONObject().put("LightStatus", new JSONObject().put("SecondLight", on)), c); } catch (JSONException x) { throw new IOException(x); }
            return;
        }
        throw new IOException("Подсветка для этого принтера не поддерживается.");
    }

    /** Duet only: starts a file previously uploaded to 0:/gcodes. */
    static void startDuet(PrinterEndpoint e, String name, NetworkHttp.Cancellation c) throws IOException {
        if (name == null || !name.matches("[^\"/\\\\\\p{Cntrl}]{1,200}")) throw new IOException("Недопустимое имя файла.");
        duetGcode(e, "M32 \"0:/gcodes/" + name + "\"", c);
    }

    // ---------------------------------------------------------------- Duet
    private static final class DuetSession { final boolean rrf; final String key; DuetSession(boolean r, String k) { rrf = r; key = k; } }
    private static DuetSession duetConnect(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        NetworkHttp.Response r;
        try { r = NetworkHttp.request("GET", e.url("rr_connect?password=" + q(e.secret().isEmpty() ? "reprap" : e.secret()) + "&time=" + q(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(new java.util.Date()))), null, null, c); }
        catch (java.io.InterruptedIOException x) { throw x; } catch (IOException unavailable) { return new DuetSession(false, null); }
        if (r.status == 404 || r.status == 405) return new DuetSession(false, null);
        r.requireSuccess(); JSONObject j = json(r.text());
        if (j.optInt("err", -1) != 0) throw new IOException("Duet отклонил подключение: проверьте пароль.");
        String key = j.optString("sessionKey", ""); return new DuetSession(true, key.isEmpty() ? null : key);
    }
    private static Map<String, String> duetHeaders(DuetSession s) { Map<String, String> h = new LinkedHashMap<>(); if (s.key != null) h.put("X-Session-Key", s.key); return h; }
    private static void duetClose(PrinterEndpoint e, DuetSession s, NetworkHttp.Cancellation c) { if (s.rrf) try { NetworkHttp.request("GET", e.url("rr_disconnect"), duetHeaders(s), null, c); } catch (IOException ignored) { } }
    private static JSONObject duetStatus(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        DuetSession s = duetConnect(e, c);
        try {
            JSONObject model;
            if (s.rrf) {
                NetworkHttp.Response r = NetworkHttp.request("GET", e.url("rr_model?key=&flags=d99fn"), duetHeaders(s), null, c); r.requireSuccess();
                model = json(r.text()).optJSONObject("result");
            } else { NetworkHttp.Response r = NetworkHttp.request("GET", e.url("machine/status"), null, null, c); r.requireSuccess(); model = json(r.text()); }
            if (model == null) throw new IOException("Duet не вернул модель состояния.");
            return duetTelemetry(model);
        } finally { duetClose(e, s, c); }
    }
    static JSONObject duetTelemetry(JSONObject m) throws IOException {
        try {
            JSONObject k = new JSONObject();
            JSONObject state = m.optJSONObject("state"); String st = state == null ? "unknown" : state.optString("status", "unknown"); k.put("state", st);
            JSONObject heat = m.optJSONObject("heat"); JSONArray heaters = heat == null ? null : heat.optJSONArray("heaters");
            JSONArray tools = m.optJSONArray("tools"); int nozzle = -1;
            if (tools != null && tools.length() > 0 && tools.optJSONObject(0) != null) { JSONArray th = tools.optJSONObject(0).optJSONArray("heaters"); if (th != null && th.length() > 0) nozzle = th.optInt(0, -1); }
            putHeater(k, "nozzle", heaters, nozzle);
            putHeater(k, "bed", heaters, first(heat, "bedHeaters"));
            putHeater(k, "chamber", heaters, first(heat, "chamberHeaters"));
            JSONObject job = m.optJSONObject("job");
            if (job != null) {
                JSONObject file = job.optJSONObject("file");
                long size = file == null ? 0 : file.optLong("size", 0), pos = job.optLong("filePosition", 0);
                if (size > 0 && ("printing".equals(st) || "paused".equals(st) || "pausing".equals(st) || "resuming".equals(st))) k.put("progress", Math.min(1.0, pos / (double) size));
                if (file != null && !file.optString("fileName", "").isEmpty()) { String n = file.optString("fileName"); k.put("file", n.substring(n.lastIndexOf('/') + 1)); }
                JSONObject left = job.optJSONObject("timesLeft"); if (left != null) { long t = left.optLong("slicer", left.optLong("file", -1)); if (t > 0) k.put("seconds_left", t); }
            }
            return k;
        } catch (JSONException x) { throw new IOException(x); }
    }
    private static int first(JSONObject heat, String key) { JSONArray a = heat == null ? null : heat.optJSONArray(key); return a == null || a.length() == 0 ? -1 : a.optInt(0, -1); }
    private static void putHeater(JSONObject k, String name, JSONArray heaters, int index) throws JSONException {
        if (heaters == null || index < 0 || index >= heaters.length() || heaters.optJSONObject(index) == null) return;
        JSONObject h = heaters.optJSONObject(index); k.put(name, new JSONArray().put(h.optDouble("current", Double.NaN)).put(h.optDouble("active", 0)));
    }
    private static void duetGcode(PrinterEndpoint e, String code, NetworkHttp.Cancellation c) throws IOException {
        DuetSession s = duetConnect(e, c);
        try {
            NetworkHttp.Response r = s.rrf
                ? NetworkHttp.request("GET", e.url("rr_gcode?gcode=" + q(code)), duetHeaders(s), null, c)
                : NetworkHttp.request("POST", e.url("machine/code"), null, NetworkHttp.bytes(code.getBytes(StandardCharsets.UTF_8)), c);
            r.requireSuccess();
        } finally { duetClose(e, s, c); }
    }

    // ---------------------------------------------------------------- Repetier Server
    private static String slug(PrinterEndpoint e) { return PrinterEndpoint.encodeComponent(e.storage); }
    private static Map<String, String> apiKey(PrinterEndpoint e) { Map<String, String> h = new LinkedHashMap<>(); if (!e.secret().isEmpty()) h.put("X-Api-Key", e.secret()); return h; }
    private static String repetier(PrinterEndpoint e, String action, JSONObject data, NetworkHttp.Cancellation c) throws IOException {
        NetworkHttp.Response r = NetworkHttp.request("GET", e.url("printer/api/" + slug(e) + "?a=" + action + "&data=" + q(data.toString())), apiKey(e), null, c); r.requireSuccess(); return r.text();
    }
    private static JSONObject repetierStatus(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        try {
            JSONObject k = new JSONObject();
            JSONObject states = json(repetier(e, "stateList", new JSONObject(), c)); JSONObject s = states.optJSONObject(e.storage);
            if (s == null && states.length() == 1) s = states.optJSONObject(states.keys().next());
            if (s != null) { tempPair(k, "nozzle", s.optJSONArray("extruder")); tempPair(k, "bed", s.optJSONArray("heatedBeds")); tempPair(k, "chamber", s.optJSONArray("heatedChambers")); }
            NetworkHttp.Response r = NetworkHttp.request("GET", e.url("printer/list"), apiKey(e), null, c); r.requireSuccess();
            Object raw = new org.json.JSONTokener(r.text()).nextValue(); JSONArray list = raw instanceof JSONArray ? (JSONArray) raw : raw instanceof JSONObject ? ((JSONObject) raw).optJSONArray("data") : null;
            String state = "idle";
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i); if (p == null || !e.storage.equals(p.optString("slug"))) continue;
                if (!p.optBoolean("online", true) && p.optInt("online", 1) == 0) state = "offline";
                String job = p.optString("job", "none");
                if (!job.isEmpty() && !"none".equalsIgnoreCase(job)) {
                    state = p.optBoolean("paused", false) ? "paused" : "printing"; k.put("file", job);
                    if (p.has("done")) k.put("progress", Math.max(0, Math.min(1, p.optDouble("done", 0) / 100.0)));
                    double total = p.optDouble("printTime", 0), done = p.optDouble("printedTimeComp", 0); if (total > done && done > 0) k.put("seconds_left", (long) (total - done));
                }
            }
            return k.put("state", state);
        } catch (JSONException x) { throw new IOException("Repetier вернул некорректный ответ.", x); }
    }
    private static void tempPair(JSONObject k, String name, JSONArray list) throws JSONException {
        JSONObject t = list == null || list.length() == 0 ? null : list.optJSONObject(0); if (t == null) return;
        k.put(name, new JSONArray().put(t.optDouble("tempRead", Double.NaN)).put(t.optDouble("tempSet", 0)));
    }

    // ---------------------------------------------------------------- Creality OS (K1 / K2 family)
    private static JSONObject crealityStatus(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        try (MiniWebSocket ws = new MiniWebSocket(e.baseUri.getHost(), 9999, "/", TIMEOUT_MS, c)) {
            JSONObject merged = new JSONObject(); long until = System.currentTimeMillis() + TIMEOUT_MS;
            ws.send("{\"method\":\"get\",\"params\":{\"reqPrinterPara\":1}}");
            while (System.currentTimeMillis() < until) {
                c.check(); String m = ws.receive(); if (m == null) break;
                try { JSONObject j = new JSONObject(m); for (java.util.Iterator<String> it = j.keys(); it.hasNext(); ) { String key = it.next(); merged.put(key, j.get(key)); } } catch (JSONException ignored) { continue; }
                if (merged.has("nozzleTemp") && merged.has("state")) break;
            }
            if (!merged.has("nozzleTemp") && !merged.has("state")) throw new IOException("Creality не прислал состояние (порт 9999).");
            return crealityTelemetry(merged);
        }
    }
    static JSONObject crealityTelemetry(JSONObject j) throws IOException {
        try {
            JSONObject k = new JSONObject();
            int st = j.optInt("state", -1);
            k.put("state", st == 1 ? "printing" : st == 5 ? "paused" : st == 0 ? "idle" : st == 2 ? "complete" : st == 4 ? "stopped" : "unknown");
            k.put("nozzle", new JSONArray().put(num(j, "nozzleTemp")).put(num(j, "targetNozzleTemp")));
            k.put("bed", new JSONArray().put(num(j, "bedTemp0")).put(num(j, "targetBedTemp0")));
            if (j.has("boxTemp")) k.put("chamber", new JSONArray().put(num(j, "boxTemp")).put(0));
            if (j.has("printProgress") && (st == 1 || st == 5)) k.put("progress", Math.max(0, Math.min(1, num(j, "printProgress") / 100.0)));
            if (j.has("printLeftTime")) k.put("seconds_left", (long) num(j, "printLeftTime"));
            String f = j.optString("printFileName", ""); if (!f.isEmpty()) k.put("file", f.substring(f.lastIndexOf('/') + 1));
            return k;
        } catch (JSONException x) { throw new IOException(x); }
    }
    private static double num(JSONObject j, String key) { Object v = j.opt(key); if (v instanceof Number) return ((Number) v).doubleValue(); try { return Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException x) { return Double.NaN; } }
    private static void crealitySet(PrinterEndpoint e, String key, int value, NetworkHttp.Cancellation c) throws IOException {
        try (MiniWebSocket ws = new MiniWebSocket(e.baseUri.getHost(), 9999, "/", TIMEOUT_MS, c)) {
            ws.send(new JSONObject().put("method", "set").put("params", new JSONObject().put(key, value)).toString());
        } catch (JSONException x) { throw new IOException(x); }
    }

    // ---------------------------------------------------------------- Elegoo
    private static int elegooModel(PrinterEndpoint e) { String m = e.extras.get("model"); if (m == null || !m.startsWith("Elegoo Centauri")) return 0; return m.trim().endsWith("2") ? 2 : 1; }
    private static PrinterHostClient octo(PrinterEndpoint e) {
        return new PrinterHostClient(new PrinterEndpoint(PrinterEndpoint.Type.OCTOPRINT, e.baseUri.toASCIIString(), e.secret().isEmpty() ? PrinterEndpoint.Auth.NONE : PrinterEndpoint.Auth.API_KEY, "", e.secret(), "local"));
    }
    private static JSONObject elegooOctoStatus(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        PrinterHostClient.Status s = octo(e).status(c);
        try {
            JSONObject k = new JSONObject().put("state", s.state.toLowerCase(Locale.ROOT)); JSONObject j = new JSONObject(s.rawJson); JSONObject t = j.optJSONObject("temperature");
            if (t != null) { for (String[] p : new String[][]{{"tool0", "nozzle"}, {"bed", "bed"}, {"chamber", "chamber"}}) { JSONObject o = t.optJSONObject(p[0]); if (o != null) k.put(p[1], new JSONArray().put(o.optDouble("actual", Double.NaN)).put(o.optDouble("target", 0))); } }
            return k;
        } catch (JSONException x) { throw new IOException(x); }
    }
    /** MainboardID by unicast SDCP discovery ("M99999" on UDP 3000). */
    private static String sdcpMainboard(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        String known = e.extras.get("mainboard_id"); if (known != null && known.matches("[A-Za-z0-9]{4,64}")) return known;
        try (DatagramSocket udp = new DatagramSocket()) {
            udp.setSoTimeout(3000); byte[] ask = "M99999".getBytes(StandardCharsets.US_ASCII);
            udp.send(new DatagramPacket(ask, ask.length, InetAddress.getByName(e.baseUri.getHost()), 3000));
            byte[] buf = new byte[4096]; DatagramPacket reply = new DatagramPacket(buf, buf.length); udp.receive(reply); c.check();
            JSONObject data = new JSONObject(new String(buf, 0, reply.getLength(), StandardCharsets.UTF_8)).optJSONObject("Data");
            String id = data == null ? "" : data.optString("MainboardID", "");
            if (!id.matches("[A-Za-z0-9]{4,64}")) throw new IOException("Elegoo не сообщил идентификатор платы.");
            return id;
        } catch (SocketTimeoutException t) { throw new IOException("Elegoo не ответил на поиск SDCP (UDP 3000)."); }
        catch (JSONException x) { throw new IOException("Elegoo вернул некорректный ответ SDCP.", x); }
    }
    private static String sdcpRequest(String mainboard, int cmd, JSONObject data) throws JSONException {
        String id = UUID.randomUUID().toString().replace("-", "");
        return new JSONObject().put("Id", id).put("Data", new JSONObject().put("Cmd", cmd).put("Data", data).put("RequestID", id).put("MainboardID", mainboard)
            .put("TimeStamp", System.currentTimeMillis() / 1000).put("From", 1)).put("Topic", "sdcp/request/" + mainboard).toString();
    }
    private static JSONObject sdcpStatus(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        String board = sdcpMainboard(e, c);
        try (MiniWebSocket ws = new MiniWebSocket(e.baseUri.getHost(), 3030, "/websocket", TIMEOUT_MS, c)) {
            ws.send(sdcpRequest(board, 0, new JSONObject()));
            long until = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < until) {
                c.check(); String m = ws.receive(); if (m == null) break;
                JSONObject j; try { j = new JSONObject(m); } catch (JSONException ignored) { continue; }
                JSONObject s = j.optJSONObject("Status"); if (s == null && j.optJSONObject("Data") != null) s = j.optJSONObject("Data").optJSONObject("Status");
                if (s != null) return sdcpTelemetry(s);
            }
            throw new IOException("Elegoo не прислал состояние (SDCP).");
        } catch (JSONException x) { throw new IOException(x); }
    }
    static JSONObject sdcpTelemetry(JSONObject s) throws IOException {
        try {
            JSONObject k = new JSONObject(); JSONObject info = s.optJSONObject("PrintInfo"); int ps = info == null ? 0 : info.optInt("Status", 0);
            String state = ps == 6 || ps == 5 ? "paused" : ps == 13 || ps == 1 || ps == 2 || ps == 3 || ps == 4 || ps == 20 ? "printing" : ps == 9 ? "complete" : ps == 8 || ps == 7 ? "stopped" : "idle";
            k.put("state", state);
            k.put("nozzle", new JSONArray().put(s.optDouble("TempOfNozzle", Double.NaN)).put(s.optDouble("TempTargetNozzle", 0)));
            k.put("bed", new JSONArray().put(s.optDouble("TempOfHotbed", Double.NaN)).put(s.optDouble("TempTargetHotbed", 0)));
            if (s.has("TempOfBox")) k.put("chamber", new JSONArray().put(s.optDouble("TempOfBox", Double.NaN)).put(s.optDouble("TempTargetBox", 0)));
            if (info != null) {
                if (info.has("Progress") && ("printing".equals(state) || "paused".equals(state))) k.put("progress", Math.max(0, Math.min(1, info.optDouble("Progress", 0) / 100.0)));
                long total = info.optLong("TotalTicks", 0), cur = info.optLong("CurrentTicks", 0); if (total > cur && cur > 0) k.put("seconds_left", total - cur);
                String f = info.optString("Filename", ""); if (!f.isEmpty()) k.put("file", f.substring(f.lastIndexOf('/') + 1));
            }
            return k;
        } catch (JSONException x) { throw new IOException(x); }
    }
    private static void sdcpCommand(PrinterEndpoint e, int cmd, JSONObject data, NetworkHttp.Cancellation c) throws IOException {
        String board = sdcpMainboard(e, c);
        try (MiniWebSocket ws = new MiniWebSocket(e.baseUri.getHost(), 3030, "/websocket", TIMEOUT_MS, c)) {
            ws.send(sdcpRequest(board, cmd, data));
            long until = System.currentTimeMillis() + 5000; // wait briefly for the acknowledgement
            while (System.currentTimeMillis() < until) {
                String m = ws.receive(); if (m == null) break;
                try { JSONObject d = new JSONObject(m).optJSONObject("Data"); if (d != null && d.optInt("Cmd", -1) == cmd) { JSONObject r = d.optJSONObject("Data"); if (r != null && r.optInt("Ack", 0) != 0) throw new IOException("Elegoo отклонил команду (" + r.optInt("Ack") + ")."); return; } } catch (JSONException ignored) { }
            }
        } catch (JSONException x) { throw new IOException(x); }
    }

    // ---------------------------------------------------------------- helpers
    private static Map<String, String> map(String k, String v) { Map<String, String> m = new LinkedHashMap<>(); m.put(k, v); return m; }
    private static JSONObject json(String body) throws IOException { try { return new JSONObject(body); } catch (JSONException x) { throw new IOException("Принтер вернул некорректный JSON.", x); } }
    private static String q(String s) {
        StringBuilder b = new StringBuilder(); char[] h = "0123456789ABCDEF".toCharArray();
        for (byte x : s.getBytes(StandardCharsets.UTF_8)) { int v = x & 255; if ((v >= 'a' && v <= 'z') || (v >= 'A' && v <= 'Z') || (v >= '0' && v <= '9') || v == '-' || v == '_' || v == '.' || v == '~') b.append((char) v); else b.append('%').append(h[v >> 4]).append(h[v & 15]); }
        return b.toString();
    }
}
