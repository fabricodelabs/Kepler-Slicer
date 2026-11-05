package com.keplerslicer.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Elegoo Centauri Carbon 2 on the local network ("LAN only" mode): the printer runs its own MQTT broker (1883,
 * user "elegoo", password = access code or 123456). A client registers, then sends JSON-RPC requests
 * {"id","method","params"} (1002 status, 1020 start, 1021 pause, 1022 stop, 1023 resume, 1029 light, 1030 fans,
 * 1031 speed) and reads the response. Serial number from UDP discovery (52700, method 7000) or the extras.
 * Protocol as documented by the community (elegoo-homeassistant docs/CC2_PROTOCOL.md).
 */
final class ElegooCc2 {
    static int mqttPort = 1883, discoveryPort = 52700; // package-private for the loopback test
    private static final int TIMEOUT_MS = 8000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private ElegooCc2() {}

    static JSONObject status(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        JSONObject r = request(e, 1002, new JSONObject(), c);
        return telemetry(r.optJSONObject("result") == null ? r : r.optJSONObject("result"));
    }
    static void command(PrinterEndpoint e, int method, JSONObject params, NetworkHttp.Cancellation c) throws IOException {
        JSONObject r = request(e, method, params, c); JSONObject result = r.optJSONObject("result");
        int code = result == null ? 0 : result.optInt("error_code", 0);
        if (code == 1009) throw new IOException(L.t("Принтер занят."));
        if (code != 0) throw new IOException(L.t("Elegoo отклонил команду (") + code + ").");
    }

    static JSONObject telemetry(JSONObject s) throws IOException {
        try {
            JSONObject k = new JSONObject();
            JSONObject ps = s.optJSONObject("print_status"), ms = s.optJSONObject("machine_status"), ex = s.optJSONObject("extruder"), bed = s.optJSONObject("heater_bed"), box = s.optJSONObject("ztemperature_sensor");
            String state = ps == null ? "" : ps.optString("state", "");
            if (state.isEmpty()) { int st = ms == null ? -1 : ms.optInt("status", -1); state = st == 2 ? "printing" : st == 1 ? "standby" : "unknown"; }
            k.put("state", state);
            if (ex != null) k.put("nozzle", new org.json.JSONArray().put(ex.optDouble("temperature", Double.NaN)).put(ex.optDouble("target", 0)));
            if (bed != null) k.put("bed", new org.json.JSONArray().put(bed.optDouble("temperature", Double.NaN)).put(bed.optDouble("target", 0)));
            if (box != null) k.put("chamber", new org.json.JSONArray().put(box.optDouble("temperature", Double.NaN)).put(0));
            double progress = ps != null && ps.has("progress") ? ps.optDouble("progress") : ms != null && ms.has("progress") ? ms.optDouble("progress") : Double.NaN;
            if (Double.isFinite(progress) && ("printing".equals(state) || "paused".equals(state))) k.put("progress", Math.max(0, Math.min(1, progress / 100.0)));
            if (ps != null && ps.optLong("remaining_time_sec", 0) > 0) k.put("seconds_left", ps.optLong("remaining_time_sec"));
            if (ps != null && !ps.optString("filename", "").isEmpty()) { String f = ps.optString("filename"); k.put("file", f.substring(f.lastIndexOf('/') + 1)); }
            return k;
        } catch (JSONException x) { throw new IOException(x); }
    }

    private static String serial(PrinterEndpoint e, NetworkHttp.Cancellation c) throws IOException {
        String known = e.extras.get("serial"); if (known != null && known.matches("[A-Za-z0-9]{4,64}")) return known;
        try (DatagramSocket udp = new DatagramSocket()) {
            udp.setSoTimeout(3000); byte[] ask = "{\"id\":0,\"method\":7000}".getBytes(StandardCharsets.UTF_8);
            udp.send(new DatagramPacket(ask, ask.length, InetAddress.getByName(e.baseUri.getHost()), discoveryPort));
            byte[] buf = new byte[4096]; DatagramPacket reply = new DatagramPacket(buf, buf.length); udp.receive(reply); c.check();
            JSONObject r = new JSONObject(new String(buf, 0, reply.getLength(), StandardCharsets.UTF_8)).optJSONObject("result");
            String sn = r == null ? "" : r.optString("sn", ""); if (!sn.matches("[A-Za-z0-9]{4,64}")) throw new IOException(L.t("Elegoo не сообщил серийный номер."));
            return sn;
        } catch (SocketTimeoutException t) { throw new IOException(L.t("Elegoo не ответил на поиск (UDP 52700). Включите на принтере режим «только LAN».")); }
        catch (JSONException x) { throw new IOException(L.t("Elegoo вернул некорректный ответ."), x); }
    }

    private static String hex(int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(Integer.toHexString(RANDOM.nextInt(16))); return b.toString(); }

    /** Connect, register, send one request, wait for its response; nothing else is published. */
    private static JSONObject request(PrinterEndpoint e, int method, JSONObject params, NetworkHttp.Cancellation c) throws IOException {
        String sn = serial(e, c), clientId = ("0cli" + Long.toHexString(System.currentTimeMillis()) + hex(6)).substring(0, 10), requestId = hex(16) + Long.toHexString(System.currentTimeMillis());
        Socket s = new Socket(); c.setCloser(s);
        try {
            s.connect(new InetSocketAddress(InetAddress.getByName(e.baseUri.getHost()), mqttPort), TIMEOUT_MS); s.setSoTimeout(TIMEOUT_MS);
            OutputStream out = s.getOutputStream(); DataInputStream in = new DataInputStream(s.getInputStream());
            write(out, 0x10, connect(clientId, "elegoo", e.secret().isEmpty() ? "123456" : e.secret()));
            byte[][] ack = read(in); if ((ack[0][0] & 255) >> 4 != 2 || ack[1].length < 2) throw new IOException(L.t("Elegoo: неожиданный ответ MQTT."));
            if (ack[1][1] != 0) throw new IOException(L.t("Elegoo отклонил код доступа. Введите его в поле ключа."));
            String base = "elegoo/" + sn + "/";
            write(out, 0x82, subscribe(1, base + requestId + "/register_response"));
            write(out, 0x30, publish(base + "api_register", new JSONObject().put("client_id", clientId).put("request_id", requestId).toString()));
            JSONObject reg = await(in, base + requestId + "/register_response", -1, c);
            if (reg == null || !"ok".equals(reg.optString("error"))) throw new IOException(L.t("Elegoo не принял подключение (") + (reg == null ? "timeout" : reg.optString("error")) + ").");
            write(out, 0x82, subscribe(2, base + clientId + "/api_response"));
            int id = 1 + RANDOM.nextInt(100000);
            write(out, 0x30, publish(base + clientId + "/api_request", new JSONObject().put("id", id).put("method", method).put("params", params).toString()));
            JSONObject response = await(in, base + clientId + "/api_response", id, c);
            if (response == null) throw new IOException(L.t("Принтер не подтвердил команду."));
            try { write(out, 0xE0, new byte[0]); } catch (IOException ignored) { }
            return response;
        } catch (JSONException x) { throw new IOException(x); }
        finally { c.clearCloser(s); try { s.close(); } catch (IOException ignored) { } }
    }
    private static JSONObject await(DataInputStream in, String topic, int id, NetworkHttp.Cancellation c) throws IOException {
        long until = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < until) {
            c.check(); byte[][] p; try { p = read(in); } catch (SocketTimeoutException t) { return null; }
            if ((p[0][0] & 255) >> 4 != 3) continue; byte[] b = p[1]; if (b.length < 2) continue;
            int tl = ((b[0] & 255) << 8) | (b[1] & 255); if (2 + tl > b.length) continue;
            String t = new String(b, 2, tl, StandardCharsets.UTF_8); int start = 2 + tl + ((((p[0][0] & 255) >> 1) & 3) > 0 ? 2 : 0); if (!t.equals(topic) || start > b.length) continue;
            try { JSONObject j = new JSONObject(new String(b, start, b.length - start, StandardCharsets.UTF_8)); if (id < 0 || j.optInt("id", -1) == id) return j; } catch (JSONException ignored) { }
        }
        return null;
    }

    // ---- minimal MQTT 3.1.1 framing (plain TCP on the LAN) ----
    private static byte[] connect(String clientId, String user, String password) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); str(b, "MQTT"); b.write(4); b.write(0x02 | 0x80 | 0x40); b.write(0); b.write(60);
        str(b, clientId); str(b, user); str(b, password); return b.toByteArray();
    }
    private static byte[] subscribe(int id, String topic) { ByteArrayOutputStream b = new ByteArrayOutputStream(); b.write(id >> 8); b.write(id & 255); str(b, topic); b.write(0); return b.toByteArray(); }
    private static byte[] publish(String topic, String payload) { ByteArrayOutputStream b = new ByteArrayOutputStream(); str(b, topic); byte[] p = payload.getBytes(StandardCharsets.UTF_8); b.write(p, 0, p.length); return b.toByteArray(); }
    private static void str(ByteArrayOutputStream b, String s) { byte[] v = s.getBytes(StandardCharsets.UTF_8); b.write(v.length >> 8); b.write(v.length & 255); b.write(v, 0, v.length); }
    private static void write(OutputStream out, int header, byte[] body) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); b.write(header); int n = body.length;
        do { int d = n % 128; n /= 128; if (n > 0) d |= 128; b.write(d); } while (n > 0);
        b.write(body, 0, body.length); out.write(b.toByteArray()); out.flush();
    }
    private static byte[][] read(DataInputStream in) throws IOException {
        int header = in.readUnsignedByte(), len = 0, mult = 1, d;
        do { d = in.readUnsignedByte(); len += (d & 127) * mult; mult *= 128; if (mult > 128 * 128 * 128 * 128) throw new IOException("MQTT length"); } while ((d & 128) != 0);
        if (len > 4 * 1024 * 1024) throw new IOException("MQTT packet too large");
        byte[] body = new byte[len]; in.readFully(body); return new byte[][]{{(byte) header}, body};
    }
}
