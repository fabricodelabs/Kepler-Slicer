package com.keplerslicer.app;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/** Bambu LAN status against a loopback MQTT/TLS printer simulator. No real printer and no print command. */
public final class BambuLanTest {
    private static int checks;
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checks++; }

    public static void main(String[] a) throws Exception {
        String keystore = a[0], pass = a[1];
        KeyStore ks = KeyStore.getInstance("PKCS12"); try (FileInputStream in = new FileInputStream(keystore)) { ks.load(in, pass.toCharArray()); }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); kmf.init(ks, pass.toCharArray());
        SSLContext tls = SSLContext.getInstance("TLS"); tls.init(kmf.getKeyManagers(), null, null);
        SSLServerSocket server = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket(8883, 1, InetAddress.getLoopbackAddress());
        AtomicReference<String> published = new AtomicReference<>(), user = new AtomicReference<>(), password = new AtomicReference<>();
        Thread printer = new Thread(() -> {
            try (SSLSocket s = (SSLSocket) server.accept()) {
                DataInputStream in = new DataInputStream(s.getInputStream()); OutputStream out = s.getOutputStream();
                byte[] connect = packet(in);
                int p = 10; int idLen = u16(connect, p); p += 2 + idLen; int uLen = u16(connect, p); user.set(new String(connect, p + 2, uLen, StandardCharsets.UTF_8)); p += 2 + uLen;
                int pwLen = u16(connect, p); password.set(new String(connect, p + 2, pwLen, StandardCharsets.UTF_8));
                boolean ok = "12345678".equals(password.get());
                out.write(new byte[]{0x20, 2, 0, (byte) (ok ? 0 : 5)}); out.flush();
                if (!ok) return;
                packet(in); // SUBSCRIBE
                byte[] pub = packet(in); int tLen = u16(pub, 0); published.set(new String(pub, 2 + tLen, pub.length - 2 - tLen, StandardCharsets.UTF_8) + "@" + new String(pub, 2, tLen, StandardCharsets.UTF_8));
                String report = "{\"print\":{\"gcode_state\":\"RUNNING\",\"nozzle_temper\":219.5,\"nozzle_target_temper\":220,\"bed_temper\":55,\"mc_percent\":42,\"mc_remaining_time\":17,"
                    + "\"ams\":{\"tray_now\":\"1\",\"ams\":[{\"id\":\"0\",\"tray\":[{\"id\":\"0\",\"tray_type\":\"PLA\",\"tray_color\":\"FF0000FF\"},{\"id\":\"1\",\"tray_type\":\"PETG\",\"tray_color\":\"00FF00FF\"},{\"id\":\"2\"},{\"id\":\"3\",\"tray_type\":\"\"}]}]}}}";
                ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] topic = "device/01P00A123456789/report".getBytes(StandardCharsets.UTF_8);
                b.write(topic.length >> 8); b.write(topic.length & 255); b.write(topic); b.write(report.getBytes(StandardCharsets.UTF_8));
                byte[] body = b.toByteArray(); ByteArrayOutputStream frame = new ByteArrayOutputStream(); frame.write(0x30); int n = body.length;
                do { int d = n % 128; n /= 128; if (n > 0) d |= 128; frame.write(d); } while (n > 0);
                frame.write(body); out.write(frame.toByteArray()); out.flush();
                try { packet(in); } catch (Exception ignored) { } // DISCONNECT
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        printer.start();
        PrinterEndpoint endpoint = new PrinterEndpoint(PrinterEndpoint.Type.BAMBU, "http://127.0.0.1", PrinterEndpoint.Auth.PASSWORD, "01P00A123456789", "12345678", "");
        JSONObject r = BambuLan.report(endpoint, new NetworkHttp.Cancellation());
        printer.join(5000);
        check("bblp".equals(user.get()), "LAN user is bblp");
        check(published.get() != null && published.get().endsWith("@device/01P00A123456789/request"), "status request goes to the request topic");
        check(published.get().startsWith("{\"pushing\":{\"sequence_id\":\"0\",\"command\":\"pushall\"}}"), "only the pushall status request is published");
        check("RUNNING".equals(r.optString("gcode_state")) && r.optDouble("nozzle_temper") == 219.5, "report fields are returned");
        AmsTrays.Tray[] trays = AmsTrays.parse(r);
        check(trays != null && trays.length == 4, "four AMS trays");
        check(trays[0].loaded && "PLA".equals(trays[0].type) && trays[0].color == 0xFFFF0000, "tray 1 type and colour");
        check(trays[1].active && "PETG".equals(trays[1].type), "tray_now marks the active tray");
        check(!trays[2].loaded && !trays[3].loaded, "empty trays stay empty");
        server.close();

        boolean refusedRemote = false;
        try { BambuLan.report(new PrinterEndpoint(PrinterEndpoint.Type.BAMBU, "http://8.8.8.8", PrinterEndpoint.Auth.PASSWORD, "01P00A123456789", "12345678", ""), new NetworkHttp.Cancellation()); }
        catch (java.io.IOException e) { refusedRemote = e.getMessage().contains("local network"); }
        check(refusedRemote, "public addresses are refused before connecting");
        boolean badSerial = false;
        try { BambuLan.report(new PrinterEndpoint(PrinterEndpoint.Type.BAMBU, "http://127.0.0.1", PrinterEndpoint.Auth.PASSWORD, "bad serial!", "12345678", ""), new NetworkHttp.Cancellation()); }
        catch (java.io.IOException e) { badSerial = e.getMessage().contains("serial"); }
        check(badSerial, "invalid serial is rejected");
        System.out.println("Bambu LAN status: " + checks + " checks passed (loopback simulator, no print command)");
    }

    private static int u16(byte[] b, int at) { return ((b[at] & 255) << 8) | (b[at + 1] & 255); }
    private static byte[] packet(DataInputStream in) throws java.io.IOException {
        in.readUnsignedByte(); int len = 0, mult = 1, d;
        do { d = in.readUnsignedByte(); len += (d & 127) * mult; mult *= 128; } while ((d & 128) != 0);
        byte[] body = new byte[len]; in.readFully(body); return body;
    }
}
