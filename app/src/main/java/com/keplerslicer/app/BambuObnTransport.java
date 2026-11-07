package com.keplerslicer.app;

import android.content.Context;

import com.keplerslicer.engine.BambuNetworking;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Bambu printer connection through open-bamboo-networking (AGPL-3.0). LAN: MQTT and FTPS to the printer, its
 * certificate checked against the Bambu CA bundle (resources/cert/printer.cer) and the printer serial. Cloud: the
 * account broker with the account token, publicly trusted certificate.
 */
final class BambuObnTransport implements BambuLan.Transport {
    private final Context app;
    private boolean started;

    BambuObnTransport(Context context) { app = context.getApplicationContext(); }

    private synchronized void start() throws IOException {
        if (started) return;
        File root = new File(app.getFilesDir(), "bambu_networking"), cert = new File(root, "cert"), log = new File(root, "log"), config = new File(root, "config");
        for (File d : new File[]{cert, log, config}) if (!d.isDirectory() && !d.mkdirs()) throw new IOException(L.t("Подключение к Bambu Lab недоступно на этом устройстве."));
        // The Bambu CA bundles ship with the upstream resources (resources/cert in OrcaSlicer).
        if (!new File(cert, "printer.cer").isFile() || !new File(cert, "slicer_base64.cer").isFile()) {
            try (ZipInputStream zip = new ZipInputStream(app.getAssets().open("upstream_resources.zip"))) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                    String name = entry.getName();
                    if (!name.equals("cert/printer.cer") && !name.equals("cert/slicer_base64.cer")) continue;
                    File out = new File(cert, name.substring(5)), tmp = new File(cert, name.substring(5) + ".tmp");
                    try (OutputStream o = new FileOutputStream(tmp)) { copy(zip, o); }
                    if (!tmp.renameTo(out)) throw new IOException("cert");
                }
            }
        }
        android.util.Log.i("KeplerBambu", "starting open-bamboo-networking");
        try { BambuNetworking.init(log.getAbsolutePath(), config.getAbsolutePath(), cert.getAbsolutePath(), "slicer_base64.cer"); }
        catch (UnsatisfiedLinkError | IllegalStateException e) { throw new IOException(L.t("Подключение к Bambu Lab недоступно на этом устройстве."), e); }
        started = true;
        android.util.Log.i("KeplerBambu", "open-bamboo-networking started");
    }
    private static void copy(InputStream in, OutputStream out) throws IOException { byte[] b = new byte[16384]; for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n); }

    @Override public void open(BambuLan.Session s, NetworkHttp.Cancellation cancel) throws IOException {
        start();
        if (s.cloud) {
            JSONObject user = new JSONObject(), data = new JSONObject(), info = new JSONObject();
            try {
                user.put("uid", s.uid);
                data.put("token", s.secret).put("refresh_token", "").put("expires_in", "7776000").put("user", user);
                info.put("data", data);
            } catch (JSONException e) { throw new IOException(e); }
            int rc = BambuNetworking.nativeCloudConnect(info.toString(), BambuCloud.china(s.region) ? "CN" : "US", BambuLan.TIMEOUT_MS);
            if (rc != 0) throw new IOException(rc == -17 ? L.t("Bambu: облако не ответило. Проверьте интернет.") : L.t("Bambu: облако отклонило токен, войдите заново.") + " (" + rc + ")");
            rc = BambuNetworking.nativeCloudWatch(s.serial);
            if (rc != 0) throw new IOException(L.t("Bambu: облако отклонило токен, войдите заново.") + " (" + rc + ")");
            return;
        }
        android.util.Log.i("KeplerBambu", "LAN connect " + s.host);
        int rc = BambuNetworking.nativeConnect(s.serial, s.host, "bblp", s.secret, BambuLan.TIMEOUT_MS);
        android.util.Log.i("KeplerBambu", "LAN connect result " + rc);
        if (rc == 0) return;
        String detail = BambuNetworking.nativeLastError(s.serial);
        String text = rc == -17 ? L.t("Bambu: принтер не ответил. Проверьте IP-адрес и что принтер включён.")
            : L.t("Bambu: не удалось подключиться. Проверьте серийный номер, код доступа и режим LAN.");
        throw new IOException(detail == null || detail.isEmpty() ? text + " (" + rc + ")" : text + " (" + detail + ")");
    }

    @Override public void publish(BambuLan.Session s, String json) throws IOException {
        int rc = s.cloud ? BambuNetworking.nativeCloudSend(s.serial, json) : BambuNetworking.nativeSend(s.serial, json);
        if (rc != 0) throw new IOException(L.t("Bambu: команда не отправлена (") + rc + ").");
    }

    @Override public String next(BambuLan.Session s, int timeoutMs) { return BambuNetworking.nativeNextMessage(s.serial, timeoutMs); }

    @Override public void upload(BambuLan.Session s, File file, String remoteName, NetworkHttp.Cancellation cancel) throws IOException {
        start();
        java.io.Closeable stop = BambuNetworking::nativeCancel;
        cancel.setCloser(stop);
        try {
            int rc = BambuNetworking.nativeUpload(s.serial, s.host, s.secret, file.getAbsolutePath(), remoteName);
            cancel.check();
            if (rc != 0) throw new IOException(L.t("Bambu: файл не отправлен (") + rc + L.t("). Проверьте код доступа и карту памяти принтера."));
        } finally { cancel.clearCloser(stop); }
    }
}
