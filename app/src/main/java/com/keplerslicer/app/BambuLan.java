package com.keplerslicer.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Read-only Bambu Lab printer status over the local network (LAN mode MQTT, port 8883).
 * Username "bblp", password = the printer's access code, serial number from the connection's username field.
 * Only the "pushall" status request is published; no print, pause, stop or setting command exists here.
 * The printer presents a certificate from its own CA, so the TLS check is relaxed, and only for private LAN addresses.
 */
final class BambuLan {
    private static final int PORT = 8883, TIMEOUT_MS = 12000;
    private BambuLan() {}

    /** Returns the printer's "print" report (temperatures, progress, gcode_state, ams). */
    static JSONObject report(PrinterEndpoint e, NetworkHttp.Cancellation cancel) throws IOException {
        String host = e.baseUri.getHost(), serial = e.username.trim(), code = e.secret();
        if (host == null || host.isEmpty()) throw new IOException("Bambu: printer IP address is missing.");
        if (serial.isEmpty() || !serial.matches("[A-Za-z0-9]{6,32}")) throw new IOException("Bambu: enter the printer serial number in the User field.");
        if (code.isEmpty()) throw new IOException("Bambu: enter the LAN access code in the key field.");
        InetAddress address = InetAddress.getByName(host);
        if (!(address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()))
            throw new IOException("Bambu LAN mode is limited to printers on the local network.");
        Socket raw = new Socket();
        cancel.setCloser(raw);
        try {
            raw.connect(new InetSocketAddress(address, PORT), TIMEOUT_MS);
            raw.setSoTimeout(TIMEOUT_MS);
            SSLSocket tls = (SSLSocket) lanTls().getSocketFactory().createSocket(raw, host, PORT, true);
            tls.startHandshake();
            OutputStream out = tls.getOutputStream(); DataInputStream in = new DataInputStream(tls.getInputStream());
            write(out, 0x10, connect("kepler-" + Long.toHexString(System.nanoTime()), "bblp", code));
            Packet ack = read(in);
            if (ack.header >> 4 != 2 || ack.body.length < 2) throw new IOException("Bambu: unexpected MQTT reply.");
            if (ack.body[1] != 0) throw new IOException(ack.body[1] == 5 || ack.body[1] == 4 ? "Bambu: the access code was rejected." : "Bambu: connection refused (" + ack.body[1] + ").");
            String report = "device/" + serial + "/report", request = "device/" + serial + "/request";
            write(out, 0x82, subscribe(1, report));
            write(out, 0x30, publish(request, "{\"pushing\":{\"sequence_id\":\"0\",\"command\":\"pushall\"}}"));
            long until = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < until) {
                cancel.check();
                Packet h;
                try { h = read(in); } catch (SocketTimeoutException t) { break; }
                if (h.header >> 4 != 3) continue; // only PUBLISH carries reports
                byte[] p = h.body; if (p.length < 2) continue; int topicLen = ((p[0] & 255) << 8) | (p[1] & 255); int start = 2 + topicLen + (((h.header >> 1) & 3) > 0 ? 2 : 0); if (start > p.length) continue;
                String body = new String(p, start, p.length - start, StandardCharsets.UTF_8);
                try {
                    JSONObject print = new JSONObject(body).optJSONObject("print");
                    if (print != null && (print.has("gcode_state") || print.has("ams") || print.has("nozzle_temper"))) {
                        try { write(out, 0xE0, new byte[0]); } catch (IOException ignored) { }
                        return print;
                    }
                } catch (JSONException ignored) { }
            }
            throw new IOException("Bambu: the printer did not send a status report. Check LAN mode and the serial number.");
        } finally {
            cancel.clearCloser(raw);
            try { raw.close(); } catch (IOException ignored) { }
        }
    }

    private static SSLContext lanTls() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            return context;
        } catch (Exception e) { throw new IOException("TLS is unavailable", e); }
    }

    // ---- minimal MQTT 3.1.1 framing ----
    private static final class Packet { final int header; final byte[] body; Packet(int header, byte[] body) { this.header = header; this.body = body; } }

    private static byte[] connect(String clientId, String user, String password) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        str(b, "MQTT"); b.write(4); b.write(0x02 | 0x80 | 0x40); b.write(0); b.write(60);
        str(b, clientId); str(b, user); str(b, password);
        return b.toByteArray();
    }
    private static byte[] subscribe(int id, String topic) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); b.write(id >> 8); b.write(id & 255); str(b, topic); b.write(0); return b.toByteArray();
    }
    private static byte[] publish(String topic, String payload) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); str(b, topic); byte[] p = payload.getBytes(StandardCharsets.UTF_8); b.write(p, 0, p.length); return b.toByteArray();
    }
    private static void str(ByteArrayOutputStream b, String s) { byte[] v = s.getBytes(StandardCharsets.UTF_8); b.write(v.length >> 8); b.write(v.length & 255); b.write(v, 0, v.length); }
    private static void write(OutputStream out, int header, byte[] body) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); b.write(header); int n = body.length;
        do { int d = n % 128; n /= 128; if (n > 0) d |= 128; b.write(d); } while (n > 0);
        b.write(body, 0, body.length); out.write(b.toByteArray()); out.flush();
    }
    /** Reads one MQTT packet. */
    private static Packet read(DataInputStream in) throws IOException {
        int header = in.readUnsignedByte(), len = 0, mult = 1, d;
        do { d = in.readUnsignedByte(); len += (d & 127) * mult; mult *= 128; if (mult > 128 * 128 * 128 * 128) throw new IOException("MQTT length"); } while ((d & 128) != 0);
        if (len > 4 * 1024 * 1024) throw new IOException("Bambu: report too large.");
        byte[] body = new byte[len]; in.readFully(body);
        return new Packet(header, body);
    }
}
