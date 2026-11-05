package com.keplerslicer.app;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Duet, Repetier, Creality and Elegoo SDCP status/control against loopback simulators. No real printer. */
public final class LegacyControlTest {
    private static int checks;
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checks++; }
    private static final List<String> requests = new CopyOnWriteArrayList<>();

    public static void main(String[] a) throws Exception {
        duet(); repetier(); creality(); elegoo(); bambuCloud();
        System.out.println("Printer control: " + checks + " checks passed (loopback simulators: Duet, Repetier, Creality, Elegoo)");
    }

    private static HttpServer http(String body) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        s.createContext("/", x -> {
            String uri = x.getRequestURI().toString(); requests.add(x.getRequestMethod() + " " + uri);
            String reply = uri.startsWith("/rr_connect") ? "{\"err\":0,\"sessionKey\":\"7\"}" : uri.startsWith("/rr_model") ? body
                : uri.startsWith("/printer/list") ? "{\"data\":[{\"slug\":\"mk3\",\"online\":1,\"job\":\"cube.gcode\",\"done\":25.0,\"paused\":false,\"printTime\":1000,\"printedTimeComp\":250}]}"
                : uri.contains("a=stateList") ? "{\"mk3\":{\"extruder\":[{\"tempRead\":210.5,\"tempSet\":215}],\"heatedBeds\":[{\"tempRead\":60,\"tempSet\":60}]}}" : "{\"err\":0}";
            byte[] b = reply.getBytes(StandardCharsets.UTF_8); x.sendResponseHeaders(200, b.length); x.getResponseBody().write(b); x.close();
        });
        s.start(); return s;
    }

    /** Bambu cloud account API against a loopback server: code sign-in, profile uid, bound devices, no cloud upload. */
    private static void bambuCloud() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        s.createContext("/", x -> {
            String path = x.getRequestURI().getPath(); String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); bodies.add(path + " " + body);
            String auth = x.getRequestHeaders().getFirst("Authorization"); String reply;
            if (path.endsWith("/user/login")) reply = body.contains("\"code\":\"123456\"") ? "{\"accessToken\":\"tok\",\"refreshToken\":\"ref\"}" : "{\"accessToken\":\"\",\"loginType\":\"verifyCode\"}";
            else if (path.endsWith("/sendemail/code")) reply = "{}";
            else if (path.endsWith("/user/refreshtoken")) reply = body.contains("\"refreshToken\":\"ref\"") ? "{\"accessToken\":\"tok2\",\"refreshToken\":\"ref2\",\"expiresIn\":7776000}" : null;
            else if (path.endsWith("/my/profile")) reply = "Bearer tok".equals(auth) ? "{\"uid\":4242}" : null;
            else if (path.endsWith("/user/bind")) reply = "Bearer tok".equals(auth) ? "{\"devices\":[{\"dev_id\":\"01P00A123456789\",\"name\":\"P1S\",\"online\":true,\"dev_product_name\":\"P1S\"},{\"dev_id\":\"bad id\"}]}" : null;
            else reply = null;
            byte[] b = (reply == null ? "{}" : reply).getBytes(StandardCharsets.UTF_8); x.sendResponseHeaders(reply == null ? 401 : 200, b.length); x.getResponseBody().write(b); x.close();
        });
        s.start(); BambuCloud.testBase = "http://127.0.0.1:" + s.getAddress().getPort();
        NetworkHttp.Cancellation t = new NetworkHttp.Cancellation();
        BambuCloud.Login first = BambuCloud.signIn("Europe", "me@example.com", "pw", "", t);
        check(first.needsCode && first.token == null, "password sign-in that needs a code asks for it");
        check(bodies.stream().anyMatch(b -> b.startsWith("/v1/user-service/user/sendemail/code") && b.contains("codeLogin")), "the sign-in code is requested by e-mail");
        BambuCloud.Login done = BambuCloud.signIn("Europe", "me@example.com", "", "123456", t);
        check("tok".equals(done.token) && "4242".equals(done.uid), "code sign-in returns the token and account id");
        List<BambuCloud.Device> devices = BambuCloud.devices("Europe", done.token, t);
        check(devices.size() == 1 && "01P00A123456789".equals(devices.get(0).serial) && devices.get(0).online, "bound printers are listed; invalid serials are dropped");
        boolean rejected = false; try { BambuCloud.devices("Europe", "wrong", t); } catch (java.io.IOException e) { rejected = e.getMessage().contains("войдите снова"); }
        check(rejected, "an expired token asks to sign in again");
        check(bodies.stream().noneMatch(b -> b.contains("\"password\":\"\"")), "an empty password is never sent");
        BambuCloud.Login renewed = BambuCloud.refresh("Europe", done.refresh, t);
        check("tok2".equals(renewed.token) && "ref2".equals(renewed.refresh) && renewed.expiresAt > System.currentTimeMillis() + 80L * 24 * 3600 * 1000, "the refresh token renews the access token and its expiry");
        check(new JSONObject(renewed.session()).getString("refresh_token").equals("ref2"), "the renewed session keeps the refresh token");
        s.stop(0); BambuCloud.testBase = null;
        java.util.Map<String, String> ex = new java.util.LinkedHashMap<>(); ex.put("uid", "4242"); ex.put("region", "Europe");
        PrinterEndpoint cloud = new PrinterEndpoint(PrinterEndpoint.Type.BAMBU_CLOUD, "https://api.bambulab.com", PrinterEndpoint.Auth.BEARER, "01P00A123456789", "tok", "", ex);
        boolean noUpload = false; try { new PrinterHostClient(cloud).upload(new java.io.File("build.gradle"), "a.gcode", t); } catch (java.io.IOException e) { noUpload = e.getMessage().contains("LAN"); }
        check(noUpload || !new java.io.File("build.gradle").isFile(), "cloud upload is refused with a pointer to LAN");
        check("us.mqtt.bambulab.com".equals(BambuCloud.mqttHost("Europe")) && "cn.mqtt.bambulab.com".equals(BambuCloud.mqttHost("China")), "broker follows the account region");
    }

    private static void duet() throws Exception {
        String model = "{\"result\":{\"state\":{\"status\":\"printing\"},\"heat\":{\"bedHeaters\":[0],\"chamberHeaters\":[-1],\"heaters\":[{\"current\":59.5,\"active\":60},{\"current\":209.9,\"active\":210}]},"
            + "\"tools\":[{\"heaters\":[1]}],\"job\":{\"file\":{\"fileName\":\"0:/gcodes/cube.gcode\",\"size\":1000},\"filePosition\":400,\"timesLeft\":{\"slicer\":600}}}}";
        HttpServer s = http(model); requests.clear();
        PrinterEndpoint e = new PrinterEndpoint(PrinterEndpoint.Type.DUET, "http://127.0.0.1:" + s.getAddress().getPort(), PrinterEndpoint.Auth.PASSWORD, "", "secret", "");
        PrinterHostClient c = new PrinterHostClient(e); NetworkHttp.Cancellation t = new NetworkHttp.Cancellation();
        PrinterHostClient.Status st = c.status(t); JSONObject k = new JSONObject(st.rawJson).getJSONObject("kepler");
        check("printing".equals(st.state), "Duet state");
        check(k.getJSONArray("nozzle").getDouble(0) == 209.9 && k.getJSONArray("bed").getDouble(1) == 60, "Duet nozzle from tool heater, bed from bedHeaters");
        check(Math.abs(k.getDouble("progress") - 0.4) < 1e-9 && k.getLong("seconds_left") == 600 && "cube.gcode".equals(k.getString("file")), "Duet progress, time and file");
        check(requests.stream().anyMatch(r -> r.startsWith("GET /rr_disconnect")), "Duet session is closed");
        requests.clear(); c.job(PrinterHostClient.Job.PAUSE, t); check(requests.contains("GET /rr_gcode?gcode=M25"), "Duet pause is M25");
        requests.clear(); c.job(PrinterHostClient.Job.CANCEL, t); check(requests.contains("GET /rr_gcode?gcode=M25%0AM0"), "Duet cancel is M25 then M0");
        requests.clear(); c.move('X', 10, t); check(requests.contains("GET /rr_gcode?gcode=G91%0AG1%20X10.0%20F3000%0AG90"), "Duet relative move");
        requests.clear(); c.startUploaded("cube.gcode", t); check(requests.contains("GET /rr_gcode?gcode=M32%20%220%3A%2Fgcodes%2Fcube.gcode%22"), "Duet start uses M32 in 0:/gcodes");
        s.stop(0);
    }

    private static void repetier() throws Exception {
        HttpServer s = http(""); requests.clear();
        PrinterEndpoint e = new PrinterEndpoint(PrinterEndpoint.Type.REPETIER, "http://127.0.0.1:" + s.getAddress().getPort(), PrinterEndpoint.Auth.API_KEY, "", "key", "mk3");
        PrinterHostClient c = new PrinterHostClient(e); NetworkHttp.Cancellation t = new NetworkHttp.Cancellation();
        PrinterHostClient.Status st = c.status(t); JSONObject k = new JSONObject(st.rawJson).getJSONObject("kepler");
        check("printing".equals(st.state) && "cube.gcode".equals(k.getString("file")), "Repetier job state");
        check(k.getJSONArray("nozzle").getDouble(0) == 210.5 && Math.abs(k.getDouble("progress") - 0.25) < 1e-9 && k.getLong("seconds_left") == 750, "Repetier temperatures and progress");
        requests.clear(); c.job(PrinterHostClient.Job.PAUSE, t); check(requests.get(0).contains("/printer/api/mk3?a=send&data=") && requests.get(0).contains("%40pause"), "Repetier pause is @pause");
        requests.clear(); c.job(PrinterHostClient.Job.CANCEL, t); check(requests.get(0).contains("a=stopJob"), "Repetier stop");
        requests.clear(); c.speed(3, t); check(requests.get(0).contains("M220%20S124"), "Repetier speed is M220");
        s.stop(0);
    }

    /** One-connection WebSocket server: records text messages, replies with the given messages after the first one. */
    private static Thread wsServer(ServerSocket server, List<String> got, String... replies) {
        Thread th = new Thread(() -> {
            try (Socket s = server.accept()) {
                InputStream in = s.getInputStream(); OutputStream out = s.getOutputStream();
                StringBuilder head = new StringBuilder(); int ch; while ((ch = in.read()) != -1) { head.append((char) ch); if (head.toString().endsWith("\r\n\r\n")) break; }
                String key = head.toString().replaceAll("(?s).*Sec-WebSocket-Key: ([^\r]+)\r\n.*", "$1");
                String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                DataInputStream d = new DataInputStream(in); boolean replied = false;
                while (true) {
                    int b0 = d.readUnsignedByte(), b1 = d.readUnsignedByte(); int len = b1 & 127; if (len == 126) len = d.readUnsignedShort();
                    byte[] mask = new byte[4]; d.readFully(mask); byte[] p = new byte[len]; d.readFully(p); for (int i = 0; i < len; i++) p[i] ^= mask[i & 3];
                    if ((b0 & 15) == 8) break;
                    got.add(new String(p, StandardCharsets.UTF_8));
                    if (!replied) { replied = true; for (String r : replies) { byte[] rb = r.getBytes(StandardCharsets.UTF_8); ByteArrayOutputStream f = new ByteArrayOutputStream(); f.write(0x81);
                        if (rb.length < 126) f.write(rb.length); else { f.write(126); f.write(rb.length >> 8); f.write(rb.length & 255); } f.write(rb); out.write(f.toByteArray()); } out.flush(); }
                }
            } catch (Exception ignored) { }
        });
        th.start(); return th;
    }

    private static void creality() throws Exception {
        // The client uses the fixed Creality port 9999 on the printer host; the test binds it on loopback.
        List<String> got = new CopyOnWriteArrayList<>();
        try (ServerSocket server = new ServerSocket(9999, 1, InetAddress.getLoopbackAddress())) {
            Thread th = wsServer(server, got, "{\"nozzleTemp\":\"219.40\",\"targetNozzleTemp\":220,\"bedTemp0\":\"55.0\",\"targetBedTemp0\":55}", "{\"state\":1,\"printProgress\":42,\"printLeftTime\":900,\"printFileName\":\"/usr/data/printer_data/gcodes/cube.gcode\"}");
            PrinterEndpoint e = new PrinterEndpoint(PrinterEndpoint.Type.CREALITYPRINT, "http://127.0.0.1", PrinterEndpoint.Auth.BEARER, "", "token", "");
            PrinterHostClient.Status st = new PrinterHostClient(e).status(new NetworkHttp.Cancellation()); th.join(3000);
            JSONObject k = new JSONObject(st.rawJson).getJSONObject("kepler");
            check("printing".equals(st.state) && Math.abs(k.getJSONArray("nozzle").getDouble(0) - 219.4) < 1e-9, "Creality state and nozzle (string values)");
            check(Math.abs(k.getDouble("progress") - 0.42) < 1e-9 && k.getLong("seconds_left") == 900 && "cube.gcode".equals(k.getString("file")), "Creality progress, time and file");
        }
        got.clear();
        try (ServerSocket server = new ServerSocket(9999, 1, InetAddress.getLoopbackAddress())) {
            Thread th = wsServer(server, got);
            new PrinterHostClient(new PrinterEndpoint(PrinterEndpoint.Type.CREALITYPRINT, "http://127.0.0.1", PrinterEndpoint.Auth.BEARER, "", "token", "")).job(PrinterHostClient.Job.PAUSE, new NetworkHttp.Cancellation()); th.join(3000);
            check(got.size() == 1 && got.get(0).equals("{\"method\":\"set\",\"params\":{\"pause\":1}}"), "Creality pause sends one set message");
        }
    }

    private static void elegoo() throws Exception {
        List<String> got = new CopyOnWriteArrayList<>();
        try (DatagramSocket udp = new DatagramSocket(3000, InetAddress.getLoopbackAddress()); ServerSocket server = new ServerSocket(3030, 1, InetAddress.getLoopbackAddress())) {
            Thread discovery = new Thread(() -> { try { byte[] b = new byte[64]; DatagramPacket p = new DatagramPacket(b, b.length); udp.receive(p);
                if (new String(b, 0, p.getLength(), StandardCharsets.US_ASCII).equals("M99999")) { byte[] r = "{\"Id\":\"x\",\"Data\":{\"MainboardID\":\"ABC123def\",\"Name\":\"CC\"}}".getBytes(StandardCharsets.UTF_8); udp.send(new DatagramPacket(r, r.length, p.getSocketAddress())); } } catch (Exception ignored) { } });
            discovery.start();
            Thread th = wsServer(server, got, "{\"Status\":{\"CurrentStatus\":[1],\"TempOfNozzle\":220.1,\"TempTargetNozzle\":220,\"TempOfHotbed\":60,\"TempTargetHotbed\":60,\"TempOfBox\":31,\"PrintInfo\":{\"Status\":13,\"CurrentTicks\":100,\"TotalTicks\":400,\"Filename\":\"cube.gcode\",\"Progress\":25}},\"Topic\":\"sdcp/status/ABC123def\"}");
            java.util.Map<String, String> extras = new java.util.LinkedHashMap<>(); extras.put("model", "Elegoo Centauri Carbon");
            PrinterEndpoint e = new PrinterEndpoint(PrinterEndpoint.Type.ELEGOOLINK, "http://127.0.0.1", PrinterEndpoint.Auth.NONE, "", "", "", extras);
            PrinterHostClient.Status st = new PrinterHostClient(e).status(new NetworkHttp.Cancellation()); th.join(3000); discovery.join(3000);
            JSONObject k = new JSONObject(st.rawJson).getJSONObject("kepler");
            check("printing".equals(st.state) && k.getJSONArray("nozzle").getDouble(0) == 220.1 && k.getJSONArray("chamber").getDouble(0) == 31, "Elegoo SDCP state and temperatures");
            check(Math.abs(k.getDouble("progress") - 0.25) < 1e-9 && k.getLong("seconds_left") == 300, "Elegoo SDCP progress and time left");
            JSONObject req = new JSONObject(got.get(0));
            check("sdcp/request/ABC123def".equals(req.getString("Topic")) && req.getJSONObject("Data").getInt("Cmd") == 0 && "ABC123def".equals(req.getJSONObject("Data").getString("MainboardID")), "SDCP status request uses the discovered mainboard");
        }
        boolean cc2 = false;
        try { java.util.Map<String, String> x = new java.util.LinkedHashMap<>(); x.put("model", "Elegoo Centauri Carbon 2");
            new PrinterHostClient(new PrinterEndpoint(PrinterEndpoint.Type.ELEGOOLINK, "http://127.0.0.1", PrinterEndpoint.Auth.NONE, "", "", "", x)).job(PrinterHostClient.Job.PAUSE, new NetworkHttp.Cancellation()); }
        catch (java.io.IOException expected) { cc2 = expected.getMessage().contains("Carbon 2"); }
        check(cc2, "Centauri Carbon 2 control is refused, not guessed");
    }
}
