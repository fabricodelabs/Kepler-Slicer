package com.keplerslicer.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
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
 * Bambu Lab printer over the local network (LAN mode).
 * Status: MQTT over TLS, port 8883, username "bblp", password = the printer's access code, serial number from the
 * connection's username field; only the "pushall" status request is published.
 * Files: implicit FTPS, port 990 (same credentials), upload only.
 * Commands (pause, resume, stop, speed, light, start an uploaded file) are separate, explicit calls; callers confirm first.
 * The printer presents a certificate from its own CA, so the TLS check is relaxed, and only for private LAN addresses.
 */
final class BambuLan {
    static int mqttPort = 8883, ftpsPort = 990; // package-private for the loopback test
    private static final int TIMEOUT_MS = 12000;
    private BambuLan() {}

    // ---- endpoint checks shared by status, commands and file transfer ----
    private static final class Target { final InetAddress address; final String host, serial, code; Target(InetAddress a, String h, String s, String c) { address = a; host = h; serial = s; code = c; } }
    private static Target target(PrinterEndpoint e) throws IOException {
        String host = e.baseUri.getHost(), serial = e.username.trim(), code = e.secret();
        if (host == null || host.isEmpty()) throw new IOException("Bambu: printer IP address is missing.");
        if (serial.isEmpty() || !serial.matches("[A-Za-z0-9]{6,32}")) throw new IOException("Bambu: enter the printer serial number in the User field.");
        if (code.isEmpty()) throw new IOException("Bambu: enter the LAN access code in the key field.");
        InetAddress address = InetAddress.getByName(host);
        if (!(address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()))
            throw new IOException("Bambu LAN mode is limited to printers on the local network.");
        return new Target(address, host, serial, code);
    }

    /** Returns the printer's "print" report (temperatures, progress, gcode_state, ams). */
    static JSONObject report(PrinterEndpoint e, NetworkHttp.Cancellation cancel) throws IOException {
        return mqtt(e, cancel, "{\"pushing\":{\"sequence_id\":\"0\",\"command\":\"pushall\"}}", true);
    }

    // ---- explicit printer commands (the caller asks the user first) ----
    enum Job { PAUSE, RESUME, STOP }
    static void job(PrinterEndpoint e, Job job, NetworkHttp.Cancellation cancel) throws IOException {
        String cmd = job == Job.PAUSE ? "pause" : job == Job.RESUME ? "resume" : "stop";
        mqtt(e, cancel, "{\"print\":{\"sequence_id\":\"0\",\"command\":\"" + cmd + "\",\"param\":\"\"}}", false);
    }
    /** Speed level 1 silent, 2 standard, 3 sport, 4 ludicrous. */
    static void speed(PrinterEndpoint e, int level, NetworkHttp.Cancellation cancel) throws IOException {
        if (level < 1 || level > 4) throw new IllegalArgumentException("speed level");
        mqtt(e, cancel, "{\"print\":{\"sequence_id\":\"0\",\"command\":\"print_speed\",\"param\":\"" + level + "\"}}", false);
    }
    static void chamberLight(PrinterEndpoint e, boolean on, NetworkHttp.Cancellation cancel) throws IOException {
        mqtt(e, cancel, "{\"system\":{\"sequence_id\":\"0\",\"command\":\"ledctrl\",\"led_node\":\"chamber_light\",\"led_mode\":\"" + (on ? "on" : "off")
            + "\",\"led_on_time\":500,\"led_off_time\":500,\"loop_times\":0,\"interval_time\":0}}", false);
    }
    /** Fixed G-code lines built by the app (aux fan, axis moves); never user text. */
    static void gcode(PrinterEndpoint e, String lines, NetworkHttp.Cancellation cancel) throws IOException {
        if (!lines.matches("(?:[GM][0-9]{1,3}(?: [A-Z]-?[0-9]{1,5}(?:\\.[0-9]{1,3})?)*\\n){1,8}")) throw new IllegalArgumentException("G-code");
        mqtt(e, cancel, "{\"print\":{\"sequence_id\":\"0\",\"command\":\"gcode_line\",\"param\":" + JSONObject.quote(lines) + "}}", false);
    }
    /** Starts a G-code file previously uploaded to the printer's storage root. */
    static void startFile(PrinterEndpoint e, String remoteName, NetworkHttp.Cancellation cancel) throws IOException {
        checkName(remoteName);
        mqtt(e, cancel, "{\"print\":{\"sequence_id\":\"0\",\"command\":\"gcode_file\",\"param\":" + JSONObject.quote("/sdcard/" + remoteName) + "}}", false);
    }

    private static JSONObject mqtt(PrinterEndpoint e, NetworkHttp.Cancellation cancel, String payload, boolean waitReport) throws IOException {
        Target t = target(e);
        Socket raw = new Socket();
        cancel.setCloser(raw);
        try {
            raw.connect(new InetSocketAddress(t.address, mqttPort), TIMEOUT_MS);
            raw.setSoTimeout(TIMEOUT_MS);
            SSLSocket tls = (SSLSocket) lanTls().getSocketFactory().createSocket(raw, t.host, mqttPort, true);
            tls.startHandshake();
            OutputStream out = tls.getOutputStream(); DataInputStream in = new DataInputStream(tls.getInputStream());
            write(out, 0x10, connect("kepler-" + Long.toHexString(System.nanoTime()), "bblp", t.code));
            Packet ack = read(in);
            if (ack.header >> 4 != 2 || ack.body.length < 2) throw new IOException("Bambu: unexpected MQTT reply.");
            if (ack.body[1] != 0) throw new IOException(ack.body[1] == 5 || ack.body[1] == 4 ? "Bambu: the access code was rejected." : "Bambu: connection refused (" + ack.body[1] + ").");
            String report = "device/" + t.serial + "/report", request = "device/" + t.serial + "/request";
            if (waitReport) write(out, 0x82, subscribe(1, report));
            cancel.check();
            write(out, 0x30, publish(request, payload));
            if (!waitReport) { try { write(out, 0xE0, new byte[0]); } catch (IOException ignored) { } return null; }
            long until = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < until) {
                cancel.check();
                Packet h;
                try { h = read(in); } catch (SocketTimeoutException timeout) { break; }
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

    // ---- implicit FTPS upload (no start) ----
    private static void checkName(String name) throws IOException {
        if (name == null || !name.matches("[^/\\\\\\p{Cntrl}]{1,200}") || name.equals(".") || name.equals("..")) throw new IOException("Bambu: invalid file name.");
    }
    /** Uploads one file to the printer's storage root over FTPS. Returns the stored name. */
    static String upload(PrinterEndpoint e, File file, String remoteName, NetworkHttp.Cancellation cancel) throws IOException {
        checkName(remoteName);
        Target t = target(e);
        SSLContext context = lanTls();
        Socket raw = new Socket();
        cancel.setCloser(raw);
        try {
            raw.connect(new InetSocketAddress(t.address, ftpsPort), TIMEOUT_MS);
            raw.setSoTimeout(TIMEOUT_MS);
            SSLSocket control = (SSLSocket) context.getSocketFactory().createSocket(raw, t.host, ftpsPort, true);
            control.startHandshake();
            BufferedReader in = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
            Writer out = new OutputStreamWriter(control.getOutputStream(), StandardCharsets.UTF_8);
            expect(in, 220);
            command(out, in, "USER bblp", 331);
            command(out, in, "PASS " + t.code, 230, "Bambu: the access code was rejected.");
            command(out, in, "PBSZ 0", 200);
            command(out, in, "PROT P", 200);
            command(out, in, "TYPE I", 200);
            String pasv = command(out, in, "PASV", 227);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)").matcher(pasv);
            if (!m.find()) throw new IOException("Bambu: unexpected FTP passive reply.");
            int port = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
            if (port <= 0 || port > 65535) throw new IOException("Bambu: invalid FTP data port.");
            // The data connection goes to the printer's own address (never the address in the reply).
            Socket dataRaw = new Socket();
            cancel.setCloser(dataRaw);
            try {
                dataRaw.connect(new InetSocketAddress(t.address, port), TIMEOUT_MS);
                dataRaw.setSoTimeout(TIMEOUT_MS);
                send(out, "STOR " + remoteName);
                // The printer requires TLS session reuse on the data channel. An engine created for the control
                // connection's host and port finds that session in the context's cache and resumes it.
                TlsEngineChannel data = new TlsEngineChannel(context, dataRaw, t.host, ftpsPort);
                data.handshake();
                int mark = code(reply(in));
                if (mark != 150 && mark != 125) throw new IOException("Bambu: the printer refused the file (" + mark + ").");
                try (InputStream src = new FileInputStream(file)) {
                    byte[] buf = new byte[64 * 1024]; int n; long sent = 0, total = file.length();
                    while ((n = src.read(buf)) != -1) { cancel.check(); data.write(buf, 0, n); sent += n; cancel.progress(sent, total); }
                }
                data.close();
            } finally {
                cancel.clearCloser(dataRaw);
                try { dataRaw.close(); } catch (IOException ignored) { }
            }
            raw.setSoTimeout(120000); // the printer writes to its card before confirming
            int done = code(reply(in));
            if (done != 226 && done != 250) throw new IOException("Bambu: the transfer was not confirmed (" + done + ").");
            try { send(out, "QUIT"); } catch (IOException ignored) { }
            return remoteName;
        } finally {
            cancel.clearCloser(raw);
            try { raw.close(); } catch (IOException ignored) { }
        }
    }
    private static void send(Writer out, String line) throws IOException { out.write(line + "\r\n"); out.flush(); }
    private static String command(Writer out, BufferedReader in, String line, int expected) throws IOException { return command(out, in, line, expected, null); }
    private static String command(Writer out, BufferedReader in, String line, int expected, String failure) throws IOException {
        send(out, line); String last = reply(in); int code = code(last);
        if (code != expected) throw new IOException(failure != null ? failure : "Bambu: FTP " + line.split(" ")[0] + " failed (" + code + ").");
        return last;
    }
    private static void expect(BufferedReader in, int expected) throws IOException { int code = code(reply(in)); if (code != expected) throw new IOException("Bambu: unexpected FTP greeting (" + code + ")."); }
    private static int code(String line) throws IOException { try { return Integer.parseInt(line.substring(0, 3)); } catch (NumberFormatException e) { throw new IOException("Bambu: invalid FTP reply."); } }
    /** Reads one (possibly multi-line) FTP reply and returns its last line. */
    private static String reply(BufferedReader in) throws IOException {
        String line = in.readLine(); if (line == null || line.length() < 3) throw new IOException("Bambu: the FTP connection closed.");
        if (line.length() > 3 && line.charAt(3) == '-') { String end = line.substring(0, 3) + " "; do { line = in.readLine(); if (line == null) throw new IOException("Bambu: the FTP connection closed."); } while (!line.startsWith(end)); }
        return line;
    }

    static SSLContext lanTls() throws IOException {
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
