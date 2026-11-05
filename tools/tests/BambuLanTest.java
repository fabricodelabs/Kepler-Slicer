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

    /** SSDP announcement of a P1S (as sent on UDP 2021) is recognised; other devices and paper printers are not. */
    private static final String CRLF = String.valueOf((char) 13) + (char) 10;
    private static void ssdp() {
        String notify = String.join(CRLF, "NOTIFY * HTTP/1.1", "HOST: 239.255.255.250:1990", "Server: UPnP/1.0", "Location: 192.168.1.110", "NT: urn:bambulab-com:device:3dprinter:1", "USN: 01p00c123456789", "Cache-Control: max-age=1800", "DevModel.bambu.com: C12", "DevName.bambu.com: Workshop", "DevConnect.bambu.com: lan", "", "");
        byte[] b = notify.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        BambuSsdp.Device d = BambuSsdp.parse(b, b.length, "192.168.1.110");
        check(d != null && "192.168.1.110".equals(d.ip) && "01P00C123456789".equals(d.serial) && "Workshop".equals(d.name) && "Bambu Lab P1S".equals(d.model), "a Bambu SSDP announcement gives IP, serial, name and model");
        String tv = String.join(CRLF, "NOTIFY * HTTP/1.1", "Location: http://192.168.1.20:8008/desc.xml", "NT: urn:dial-multiscreen-org:service:dial:1", "USN: uuid:1234", "", "");
        byte[] t = tv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        check(BambuSsdp.parse(t, t.length, "192.168.1.20") == null, "non-Bambu SSDP devices are ignored");
        String noLocation = notify.replace("Location: 192.168.1.110" + CRLF, "");
        byte[] n = noLocation.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        BambuSsdp.Device fromSender = BambuSsdp.parse(n, n.length, "192.168.1.111");
        check(fromSender != null && "192.168.1.111".equals(fromSender.ip), "without Location the sender address is used");
        check(BambuSsdp.isPaperPrinter("HP LaserJet MFP M140w") && BambuSsdp.isPaperPrinter("Canon PIXMA G3410") && !BambuSsdp.isPaperPrinter("Voron 2.4 Mainsail"), "office printers are filtered, 3D hosts are kept");
        check(new String(BambuSsdp.search(), java.nio.charset.StandardCharsets.US_ASCII).contains("urn:bambulab-com:device:3dprinter:1"), "the search request asks for Bambu 3D printers");
    }

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
        check(BambuLan.pins.get("01P00A123456789") != null, "the first connection remembers the printer certificate");
        // A different certificate for the same printer is refused before the access code is sent.
        BambuLan.pins.put("01P00A123456789", "00");
        SSLServerSocket impostor = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress());
        BambuLan.mqttPort = impostor.getLocalPort();
        AtomicReference<String> leaked = new AtomicReference<>();
        Thread fake = new Thread(() -> { try (SSLSocket s = (SSLSocket) impostor.accept()) { s.setSoTimeout(3000); byte[] connect = packet(new DataInputStream(s.getInputStream())); leaked.set(new String(connect, StandardCharsets.UTF_8)); } catch (Exception ignored) { } });
        fake.start();
        boolean pinRefused = false;
        try { BambuLan.report(endpoint, new NetworkHttp.Cancellation()); } catch (java.io.IOException e) { pinRefused = true; }
        fake.join(5000); impostor.close();
        check(pinRefused && (leaked.get() == null || !leaked.get().contains("12345678")), "a changed printer certificate is refused and the access code is not sent");
        BambuLan.pins.forget("01P00A123456789");

        boolean refusedRemote = false;
        try { BambuLan.report(new PrinterEndpoint(PrinterEndpoint.Type.BAMBU, "http://8.8.8.8", PrinterEndpoint.Auth.PASSWORD, "01P00A123456789", "12345678", ""), new NetworkHttp.Cancellation()); }
        catch (java.io.IOException e) { refusedRemote = e.getMessage().contains("local network"); }
        check(refusedRemote, "public addresses are refused before connecting");
        boolean badSerial = false;
        try { BambuLan.report(new PrinterEndpoint(PrinterEndpoint.Type.BAMBU, "http://127.0.0.1", PrinterEndpoint.Auth.PASSWORD, "bad serial!", "12345678", ""), new NetworkHttp.Cancellation()); }
        catch (java.io.IOException e) { badSerial = e.getMessage().contains("serial"); }
        check(badSerial, "invalid serial is rejected");

        ftps(tls);
        commands(tls);
        ssdp();
        System.out.println("Bambu LAN: " + checks + " checks passed (loopback simulators: status, FTPS upload, commands)");
    }

    /** Implicit FTPS simulator: credentials, PROT P, passive data channel that must resume the control TLS session. */
    private static void ftps(SSLContext tls) throws Exception {
        SSLServerSocket control = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress());
        control.setEnabledProtocols(new String[]{"TLSv1.2"});
        BambuLan.ftpsPort = control.getLocalPort();
        AtomicReference<String> log = new AtomicReference<>(""), stored = new AtomicReference<>();
        AtomicReference<Boolean> resumed = new AtomicReference<>(false);
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        Thread server = new Thread(() -> {
            try (SSLSocket s = (SSLSocket) control.accept()) {
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                java.io.Writer out = new java.io.OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                out.write("220-Bambu\r\n220 ready\r\n"); out.flush();
                SSLServerSocket passive = null; String line;
                while ((line = in.readLine()) != null) {
                    String verb = line.split(" ")[0]; log.set(log.get() + verb + " ");
                    if (verb.equals("USER")) out.write(line.equals("USER bblp") ? "331 pass\r\n" : "530 no\r\n");
                    else if (verb.equals("PASS")) out.write(line.equals("PASS 12345678") ? "230 ok\r\n" : "530 no\r\n");
                    else if (verb.equals("PBSZ") || verb.equals("PROT") || verb.equals("TYPE")) out.write("200 ok\r\n");
                    else if (verb.equals("PASV")) {
                        passive = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress());
                        passive.setEnabledProtocols(new String[]{"TLSv1.2"}); int p = passive.getLocalPort();
                        out.write("227 Entering Passive Mode (10,9,8,7," + (p >> 8) + "," + (p & 255) + ")\r\n"); // wrong host on purpose
                    } else if (verb.equals("STOR")) {
                        stored.set(line.substring(5)); out.write("150 open\r\n"); out.flush();
                        try (SSLSocket d = (SSLSocket) passive.accept()) {
                            d.startHandshake();
                            resumed.set(d.getSession().getCreationTime() == s.getSession().getCreationTime());
                            java.io.InputStream data = d.getInputStream(); byte[] b = new byte[4096]; int n;
                            while ((n = data.read(b)) != -1) received.write(b, 0, n);
                        }
                        out.write("226 done\r\n");
                    } else if (verb.equals("QUIT")) { out.write("221 bye\r\n"); out.flush(); break; }
                    else out.write("502 no\r\n");
                    out.flush();
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        server.start();
        java.io.File gcode = java.io.File.createTempFile("kepler-bambu", ".gcode");
        byte[] content = new byte[200_000]; for (int i = 0; i < content.length; i++) content[i] = (byte) ("G1 X1\n".charAt(i % 6));
        java.nio.file.Files.write(gcode.toPath(), content);
        PrinterEndpoint e = new PrinterEndpoint(PrinterEndpoint.Type.BAMBU, "http://127.0.0.1", PrinterEndpoint.Auth.PASSWORD, "01P00A123456789", "12345678", "");
        PrinterHostClient.UploadResult r = new PrinterHostClient(e).upload(gcode, "cube.gcode", new NetworkHttp.Cancellation());
        server.join(10000); control.close(); gcode.delete();
        check("cube.gcode".equals(stored.get()) && "cube.gcode".equals(r.remotePath), "file stored under its name in the storage root");
        check(java.util.Arrays.equals(content, received.toByteArray()), "uploaded bytes arrive unchanged");
        check(log.get().contains("PROT ") && log.get().contains("PBSZ "), "data channel is encrypted (PBSZ 0, PROT P)");
        check(resumed.get(), "data channel resumes the control TLS session");
        check(!log.get().contains("SITE") && !log.get().contains("DELE"), "upload sends no other commands");
        boolean badName = false;
        try { new PrinterHostClient(e).upload(new java.io.File("x"), "../a.gcode", new NetworkHttp.Cancellation()); } catch (Exception x) { badName = true; }
        check(badName, "path-like file names are refused");
    }

    /** Each command publishes exactly one request message and does not subscribe. */
    private static void commands(SSLContext tls) throws Exception {
        PrinterEndpoint e = new PrinterEndpoint(PrinterEndpoint.Type.BAMBU, "http://127.0.0.1", PrinterEndpoint.Auth.PASSWORD, "01P00A123456789", "12345678", "");
        String[][] cases = {{"pause", "\"command\":\"pause\""}, {"stop", "\"command\":\"stop\""}, {"speed", "\"command\":\"print_speed\",\"param\":\"3\""},
            {"light", "\"command\":\"ledctrl\",\"led_node\":\"chamber_light\",\"led_mode\":\"on\""}, {"start", "\"command\":\"gcode_file\",\"param\":\"/sdcard/cube.gcode\""}, {"home", "\"param\":\"G28\\n\""}};
        for (String[] c : cases) {
            SSLServerSocket server = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress());
            BambuLan.mqttPort = server.getLocalPort();
            AtomicReference<String> got = new AtomicReference<>(); AtomicReference<Integer> extra = new AtomicReference<>(0);
            Thread t = new Thread(() -> {
                try (SSLSocket s = (SSLSocket) server.accept()) {
                    DataInputStream in = new DataInputStream(s.getInputStream()); OutputStream out = s.getOutputStream();
                    packet(in); out.write(new byte[]{0x20, 2, 0, 0}); out.flush();
                    while (true) {
                        int header = in.readUnsignedByte(); int len = 0, mult = 1, d; do { d = in.readUnsignedByte(); len += (d & 127) * mult; mult *= 128; } while ((d & 128) != 0);
                        byte[] body = new byte[len]; in.readFully(body);
                        if (header >> 4 == 3) { int tl = u16(body, 0); got.set(new String(body, 2 + tl, body.length - 2 - tl, StandardCharsets.UTF_8)); }
                        else if (header >> 4 == 14) break; else extra.set(extra.get() + 1);
                    }
                } catch (Exception ignored) { }
            });
            t.start();
            PrinterHostClient client = new PrinterHostClient(e); NetworkHttp.Cancellation cancel = new NetworkHttp.Cancellation();
            switch (c[0]) {
                case "pause": client.job(PrinterHostClient.Job.PAUSE, cancel); break;
                case "stop": client.job(PrinterHostClient.Job.CANCEL, cancel); break;
                case "speed": client.speed(3, cancel); break;
                case "light": client.chamberLight(true, cancel); break;
                case "start": client.startUploaded("cube.gcode", cancel); break;
                default: client.move('H', 0, cancel);
            }
            t.join(5000); server.close();
            check(got.get() != null && got.get().contains(c[1]), c[0] + " command payload");
            check(extra.get() == 0, c[0] + " sends no subscription or other packets");
        }
        boolean refused = false; try { BambuLan.gcode(e, "M104 S300; G28\n", new NetworkHttp.Cancellation()); } catch (IllegalArgumentException x) { refused = true; }
        check(refused, "free-form G-code is refused");
    }

    private static int u16(byte[] b, int at) { return ((b[at] & 255) << 8) | (b[at + 1] & 255); }
    private static byte[] packet(DataInputStream in) throws java.io.IOException {
        in.readUnsignedByte(); int len = 0, mult = 1, d;
        do { d = in.readUnsignedByte(); len += (d & 127) * mult; mult *= 128; } while ((d & 128) != 0);
        byte[] body = new byte[len]; in.readFully(body); return body;
    }
}
