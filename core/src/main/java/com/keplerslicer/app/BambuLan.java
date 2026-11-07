package com.keplerslicer.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/**
 * Bambu Lab printers, LAN mode and the Bambu account (cloud). The connection itself is open-bamboo-networking
 * (https://github.com/ClusterM/open-bamboo-networking), the open replacement of the Bambu network plugin, which the
 * app installs as {@link #transport}. This class keeps what Kepler decides on its own: which endpoints are allowed,
 * which messages are sent and how the printer's report is read.
 * Status: only the "pushall" status request is published. Files: upload only, never started by the upload.
 * Commands (pause, resume, stop, speed, light, start an uploaded file) are separate, explicit calls; callers confirm first.
 */
final class BambuLan {
    static final int TIMEOUT_MS = 12000;
    private BambuLan() {}

    /** A checked connection: LAN (printer address, serial, access code) or cloud (serial, account token). */
    static final class Session {
        final String host, serial, secret, uid, region; final boolean cloud;
        Session(String host, String serial, String secret, String uid, String region, boolean cloud) { this.host = host; this.serial = serial; this.secret = secret; this.uid = uid; this.region = region; this.cloud = cloud; }
    }

    /** The network plugin. Calls block; {@code next} returns null after the timeout. */
    interface Transport {
        void open(Session s, NetworkHttp.Cancellation cancel) throws IOException;
        void publish(Session s, String json) throws IOException;
        String next(Session s, int timeoutMs) throws IOException;
        void upload(Session s, File file, String remoteName, NetworkHttp.Cancellation cancel) throws IOException;
    }
    static volatile Transport transport;

    private static Transport transport() throws IOException {
        Transport t = transport;
        if (t == null) throw new IOException(L.t("Подключение к Bambu Lab недоступно на этом устройстве."));
        return t;
    }

    // ---- endpoint checks shared by status, commands and file transfer ----
    static Session session(PrinterEndpoint e) throws IOException {
        if (e.type == PrinterEndpoint.Type.BAMBU_CLOUD) {
            String serial = e.username.trim(), token = e.secret(), uid = e.extras.get("uid"); if (uid == null) uid = "";
            if (!serial.matches("[A-Za-z0-9]{6,32}")) throw new IOException(L.t("Bambu: выберите принтер аккаунта (кнопка входа в облако)."));
            if (token.isEmpty() || !uid.matches("[0-9]{1,20}")) throw new IOException(L.t("Bambu: войдите в облако заново."));
            String region = e.extras.get("region");
            return new Session(BambuCloud.mqttHost(region), serial.toUpperCase(java.util.Locale.ROOT), token, uid, region == null ? "" : region, true);
        }
        String host = e.baseUri.getHost(), serial = e.username.trim(), code = e.secret();
        if (host == null || host.isEmpty()) throw new IOException(L.t("Bambu: printer IP address is missing."));
        if (serial.isEmpty() || !serial.matches("[A-Za-z0-9]{6,32}")) throw new IOException(L.t("Bambu: enter the printer serial number in the User field."));
        if (code.isEmpty()) throw new IOException(L.t("Bambu: enter the LAN access code in the key field."));
        InetAddress address = InetAddress.getByName(host);
        if (!(address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()))
            throw new IOException(L.t("Bambu LAN mode is limited to printers on the local network."));
        return new Session(address.getHostAddress(), serial.toUpperCase(java.util.Locale.ROOT), code, "", "", false);
    }

    /** Returns the printer's "print" report (temperatures, progress, gcode_state, ams). */
    static JSONObject report(PrinterEndpoint e, NetworkHttp.Cancellation cancel) throws IOException {
        return send(e, cancel, "{\"pushing\":{\"sequence_id\":\"0\",\"command\":\"pushall\"}}", true);
    }

    // ---- explicit printer commands (the caller asks the user first) ----
    enum Job { PAUSE, RESUME, STOP }
    static void job(PrinterEndpoint e, Job job, NetworkHttp.Cancellation cancel) throws IOException {
        String cmd = job == Job.PAUSE ? "pause" : job == Job.RESUME ? "resume" : "stop";
        send(e, cancel, "{\"print\":{\"sequence_id\":\"0\",\"command\":\"" + cmd + "\",\"param\":\"\"}}", false);
    }
    /** Speed level 1 silent, 2 standard, 3 sport, 4 ludicrous. */
    static void speed(PrinterEndpoint e, int level, NetworkHttp.Cancellation cancel) throws IOException {
        if (level < 1 || level > 4) throw new IllegalArgumentException("speed level");
        send(e, cancel, "{\"print\":{\"sequence_id\":\"0\",\"command\":\"print_speed\",\"param\":\"" + level + "\"}}", false);
    }
    static void chamberLight(PrinterEndpoint e, boolean on, NetworkHttp.Cancellation cancel) throws IOException {
        send(e, cancel, "{\"system\":{\"sequence_id\":\"0\",\"command\":\"ledctrl\",\"led_node\":\"chamber_light\",\"led_mode\":\"" + (on ? "on" : "off")
            + "\",\"led_on_time\":500,\"led_off_time\":500,\"loop_times\":0,\"interval_time\":0}}", false);
    }
    /** Fixed G-code lines built by the app (aux fan, axis moves); never user text. */
    static void gcode(PrinterEndpoint e, String lines, NetworkHttp.Cancellation cancel) throws IOException {
        if (!lines.matches("(?:[GM][0-9]{1,3}(?: [A-Z]-?[0-9]{1,5}(?:\\.[0-9]{1,3})?)*\\n){1,8}")) throw new IllegalArgumentException("G-code");
        send(e, cancel, "{\"print\":{\"sequence_id\":\"0\",\"command\":\"gcode_line\",\"param\":" + JSONObject.quote(lines) + "}}", false);
    }
    /** Starts a G-code file previously uploaded to the printer's storage root. */
    static void startFile(PrinterEndpoint e, String remoteName, NetworkHttp.Cancellation cancel) throws IOException {
        checkName(remoteName);
        send(e, cancel, "{\"print\":{\"sequence_id\":\"0\",\"command\":\"gcode_file\",\"param\":" + JSONObject.quote("/sdcard/" + remoteName) + "}}", false);
    }

    private static JSONObject send(PrinterEndpoint e, NetworkHttp.Cancellation cancel, String payload, boolean waitReport) throws IOException {
        Session s = session(e);
        Transport t = transport();
        cancel.check();
        t.open(s, cancel);
        cancel.check();
        t.publish(s, payload);
        if (!waitReport) return null;
        long until = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < until) {
            cancel.check();
            String body = t.next(s, 500);
            if (body == null) continue;
            try {
                JSONObject print = new JSONObject(body).optJSONObject("print");
                if (print != null && (print.has("gcode_state") || print.has("ams") || print.has("nozzle_temper"))) return print;
            } catch (JSONException ignored) { }
        }
        throw new IOException(L.t("Bambu: the printer did not send a status report. Check LAN mode and the serial number."));
    }

    // ---- upload (no start) ----
    private static void checkName(String name) throws IOException {
        if (name == null || !name.matches("[^/\\\\\\p{Cntrl}]{1,200}") || name.equals(".") || name.equals("..")) throw new IOException(L.t("Bambu: invalid file name."));
    }
    /** Uploads one file to the printer's storage root. Returns the stored name. */
    static String upload(PrinterEndpoint e, File file, String remoteName, NetworkHttp.Cancellation cancel) throws IOException {
        checkName(remoteName);
        if (e.type == PrinterEndpoint.Type.BAMBU_CLOUD) throw new IOException(L.t("Отправка файла через облако Bambu не поддерживается. Добавьте этот принтер как Bambu Lab (LAN)."));
        Session s = session(e);
        Transport t = transport();
        cancel.check();
        cancel.progress(0, file.length());
        t.upload(s, file, remoteName, cancel);
        cancel.progress(file.length(), file.length());
        return remoteName;
    }
}
