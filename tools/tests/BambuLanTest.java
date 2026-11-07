package com.keplerslicer.app;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Bambu LAN and cloud logic over a recording transport (the app uses open-bamboo-networking). No real printer and no
 * print command: checks which endpoints are allowed, which messages are published and how reports are read.
 */
public final class BambuLanTest {
    private static int checks;
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checks++; }

    /** Records every call; replies with the queued reports. */
    static final class Fake implements BambuLan.Transport {
        final List<String> calls = new ArrayList<>(); final ArrayDeque<String> reports = new ArrayDeque<>();
        BambuLan.Session last; IOException openError;
        public void open(BambuLan.Session s, NetworkHttp.Cancellation c) throws IOException { last = s; calls.add("open " + (s.cloud ? "cloud " : "lan ") + s.host + " " + s.serial); if (openError != null) throw openError; }
        public void publish(BambuLan.Session s, String json) { calls.add("publish " + json); }
        public String next(BambuLan.Session s, int timeoutMs) { return reports.poll(); }
        public void upload(BambuLan.Session s, File f, String name, NetworkHttp.Cancellation c) { calls.add("upload " + s.host + " " + name); }
    }

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
        boolean missing = false;
        try { BambuLan.report(lan("192.168.1.50"), new NetworkHttp.Cancellation()); } catch (IOException e) { missing = true; }
        check(missing, "without the network plugin the call fails with a message, not a crash");

        Fake fake = new Fake(); BambuLan.transport = fake;
        fake.reports.add("not json");
        fake.reports.add("{\"info\":{\"command\":\"get_version\"}}");
        fake.reports.add("{\"print\":{\"gcode_state\":\"RUNNING\",\"nozzle_temper\":219.5,\"nozzle_target_temper\":220,\"bed_temper\":55,\"mc_percent\":42,\"mc_remaining_time\":17,"
            + "\"ams\":{\"tray_now\":\"1\",\"ams\":[{\"id\":\"0\",\"tray\":[{\"id\":\"0\",\"tray_type\":\"PLA\",\"tray_color\":\"FF0000FF\"},{\"id\":\"1\",\"tray_type\":\"PETG\",\"tray_color\":\"00FF00FF\"},{\"id\":\"2\"},{\"id\":\"3\",\"tray_type\":\"\"}]}]}}}");
        JSONObject r = BambuLan.report(lan("192.168.1.50"), new NetworkHttp.Cancellation());
        check(fake.calls.get(0).equals("open lan 192.168.1.50 01P00A123456789"), "LAN session goes to the printer address with the serial");
        check("12345678".equals(fake.last.secret) && !fake.last.cloud, "the access code is the LAN secret");
        check(fake.calls.get(1).equals("publish {\"pushing\":{\"sequence_id\":\"0\",\"command\":\"pushall\"}}") && fake.calls.size() == 2, "only the pushall status request is published");
        check("RUNNING".equals(r.optString("gcode_state")) && r.optDouble("nozzle_temper") == 219.5, "other messages are skipped and report fields are returned");
        AmsTrays.Tray[] trays = AmsTrays.parse(r);
        check(trays != null && trays.length == 4, "four AMS trays");
        check(trays[0].loaded && "PLA".equals(trays[0].type) && trays[0].color == 0xFFFF0000, "tray 1 type and colour");
        check(trays[1].active && "PETG".equals(trays[1].type), "tray_now marks the active tray");
        check(!trays[2].loaded && !trays[3].loaded, "empty trays stay empty");

        fake.calls.clear(); fake.openError = new IOException("refused");
        boolean failed = false; try { BambuLan.job(lan("192.168.1.50"), BambuLan.Job.PAUSE, new NetworkHttp.Cancellation()); } catch (IOException e) { failed = true; }
        check(failed && fake.calls.size() == 1, "nothing is published when the connection fails");
        fake.openError = null;

        fake.calls.clear();
        boolean refusedRemote = false;
        try { BambuLan.report(lan("8.8.8.8"), new NetworkHttp.Cancellation()); } catch (IOException e) { refusedRemote = e.getMessage().contains("local network"); }
        check(refusedRemote && fake.calls.isEmpty(), "public addresses are refused before connecting");
        boolean badSerial = false;
        try { BambuLan.report(new PrinterEndpoint(PrinterEndpoint.Type.BAMBU, "http://192.168.1.50", PrinterEndpoint.Auth.PASSWORD, "bad serial!", "12345678", ""), new NetworkHttp.Cancellation()); }
        catch (IOException e) { badSerial = e.getMessage().contains("serial"); }
        check(badSerial && fake.calls.isEmpty(), "invalid serial is rejected");
        NetworkHttp.Cancellation cancelled = new NetworkHttp.Cancellation(); cancelled.cancel();
        boolean stopped = false; try { BambuLan.report(lan("192.168.1.50"), cancelled); } catch (java.io.InterruptedIOException e) { stopped = true; }
        check(stopped && fake.calls.isEmpty(), "a cancelled request does not connect");

        commands(fake);
        upload(fake);
        cloud(fake);
        ssdp();
        System.out.println("Bambu LAN: " + checks + " checks passed (recording transport: status, commands, upload, cloud)");
    }

    static PrinterEndpoint lan(String ip) { return new PrinterEndpoint(PrinterEndpoint.Type.BAMBU, "http://" + ip, PrinterEndpoint.Auth.PASSWORD, "01p00a123456789", "12345678", ""); }

    /** Each command publishes exactly one request message. */
    private static void commands(Fake fake) throws Exception {
        PrinterEndpoint e = lan("192.168.1.50");
        String[][] cases = {{"pause", "\"command\":\"pause\""}, {"stop", "\"command\":\"stop\""}, {"speed", "\"command\":\"print_speed\",\"param\":\"3\""},
            {"light", "\"command\":\"ledctrl\",\"led_node\":\"chamber_light\",\"led_mode\":\"on\""}, {"start", "\"command\":\"gcode_file\",\"param\":\"/sdcard/cube.gcode\""}, {"home", "\"param\":\"G28\\n\""}};
        for (String[] c : cases) {
            fake.calls.clear();
            PrinterHostClient client = new PrinterHostClient(e); NetworkHttp.Cancellation cancel = new NetworkHttp.Cancellation();
            switch (c[0]) {
                case "pause": client.job(PrinterHostClient.Job.PAUSE, cancel); break;
                case "stop": client.job(PrinterHostClient.Job.CANCEL, cancel); break;
                case "speed": client.speed(3, cancel); break;
                case "light": client.chamberLight(true, cancel); break;
                case "start": client.startUploaded("cube.gcode", cancel); break;
                default: client.move('H', 0, cancel);
            }
            check(fake.calls.size() == 2 && fake.calls.get(1).startsWith("publish ") && fake.calls.get(1).contains(c[1]), c[0] + " command payload");
        }
        boolean refused = false; try { BambuLan.gcode(e, "M104 S300; G28\n", new NetworkHttp.Cancellation()); } catch (IllegalArgumentException x) { refused = true; }
        check(refused, "free-form G-code is refused");
    }

    /** Upload goes to the printer address under its own name; path-like names and cloud upload are refused. */
    private static void upload(Fake fake) throws Exception {
        File gcode = File.createTempFile("cube", ".gcode"); gcode.deleteOnExit();
        java.nio.file.Files.write(gcode.toPath(), "G28\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        fake.calls.clear();
        PrinterHostClient.UploadResult r = new PrinterHostClient(lan("192.168.1.50")).upload(gcode, "cube.gcode", new NetworkHttp.Cancellation());
        check("cube.gcode".equals(r.remotePath) && fake.calls.equals(java.util.Collections.singletonList("upload 192.168.1.50 cube.gcode")), "file stored under its name, nothing else sent");
        boolean badName = false;
        try { new PrinterHostClient(lan("192.168.1.50")).upload(gcode, "../a.gcode", new NetworkHttp.Cancellation()); } catch (Exception x) { badName = true; }
        check(badName, "path-like file names are refused");
    }

    /** Cloud: account broker of the region, the token as secret, same report handling. */
    private static void cloud(Fake fake) throws Exception {
        java.util.Map<String, String> extras = new java.util.HashMap<>(); extras.put("uid", "123456"); extras.put("region", "China");
        PrinterEndpoint e = new PrinterEndpoint(PrinterEndpoint.Type.BAMBU_CLOUD, "https://api.bambulab.cn", PrinterEndpoint.Auth.PASSWORD, "01P00A123456789", "token-abc", "", extras);
        fake.calls.clear(); fake.reports.add("{\"print\":{\"gcode_state\":\"IDLE\"}}");
        JSONObject r = BambuLan.report(e, new NetworkHttp.Cancellation());
        check("IDLE".equals(r.optString("gcode_state")) && fake.last.cloud && "cn.mqtt.bambulab.com".equals(fake.last.host) && "123456".equals(fake.last.uid) && "token-abc".equals(fake.last.secret), "cloud session uses the account and region");
        boolean refused = false;
        try { new PrinterHostClient(e).upload(new File("x"), "a.gcode", new NetworkHttp.Cancellation()); } catch (IOException x) { refused = true; }
        check(refused, "cloud upload is refused");
        extras.put("uid", "");
        PrinterEndpoint signedOut = new PrinterEndpoint(PrinterEndpoint.Type.BAMBU_CLOUD, "https://api.bambulab.cn", PrinterEndpoint.Auth.PASSWORD, "01P00A123456789", "token-abc", "", extras);
        boolean relogin = false; try { BambuLan.report(signedOut, new NetworkHttp.Cancellation()); } catch (IOException x) { relogin = true; }
        check(relogin, "a cloud endpoint without the account asks to sign in again");
    }
}
