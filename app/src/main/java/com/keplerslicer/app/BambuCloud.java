package com.keplerslicer.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bambu Lab cloud account: sign-in (password or e-mail code), the account's printers and the cloud MQTT broker
 * used for status and commands. Only the user's own credentials, typed into the app, are sent, and only to Bambu.
 * Printing files through the cloud is not offered: upload goes over LAN.
 */
final class BambuCloud {
    private BambuCloud() {}

    static boolean china(String region) { return "China".equals(region); }
    static String testBase; // loopback test only
    static String apiBase(String region) { return testBase != null ? testBase : china(region) ? "https://api.bambulab.cn" : "https://api.bambulab.com"; }
    static String mqttHost(String region) { return china(region) ? "cn.mqtt.bambulab.com" : "us.mqtt.bambulab.com"; }

    static final class Login {
        final String token, refresh, uid; final boolean needsCode, needsTfa; final long expiresAt;
        Login(String token, String refresh, String uid, boolean needsCode, boolean needsTfa) { this(token, refresh, uid, needsCode, needsTfa, 0); }
        Login(String token, String refresh, String uid, boolean needsCode, boolean needsTfa, long expiresAt) { this.token = token; this.refresh = refresh; this.uid = uid; this.needsCode = needsCode; this.needsTfa = needsTfa; this.expiresAt = expiresAt; }
        /** Encrypted connection session: access and refresh token with the expiry time. */
        String session() throws JSONException { return new JSONObject().put("access_token", token).put("refresh_token", refresh == null ? "" : refresh).put("expires_at", expiresAt).toString(); }
    }
    private static long expiry(JSONObject r) { long s = r.optLong("expiresIn", 0); return s > 0 ? System.currentTimeMillis() + s * 1000 : System.currentTimeMillis() + 30L * 24 * 3600 * 1000; }

    /** New access token from the refresh token (sign-in again if this fails). */
    static Login refresh(String region, String refreshToken, NetworkHttp.Cancellation c) throws IOException {
        if (refreshToken == null || refreshToken.isEmpty()) throw new IOException(L.t("Bambu: войдите в облако заново."));
        try {
            JSONObject r = post(region, "v1/user-service/user/refreshtoken", new JSONObject().put("refreshToken", refreshToken), null, c);
            String token = r.optString("accessToken", ""); if (token.isEmpty()) throw new IOException(L.t("Bambu: войдите в облако заново."));
            String next = r.optString("refreshToken", ""); return new Login(token, next.isEmpty() ? refreshToken : next, null, false, false, expiry(r));
        } catch (JSONException e) { throw new IOException(L.t("Bambu вернул некорректный ответ."), e); }
    }
    static final class Device {
        final String serial, name, model; final boolean online;
        Device(String serial, String name, String model, boolean online) { this.serial = serial; this.name = name; this.model = model; this.online = online; }
    }

    /** Password sign-in, or code sign-in when {@code code} is not empty. A verification code is e-mailed when Bambu asks for one. */
    static Login signIn(String region, String email, String password, String code, NetworkHttp.Cancellation c) throws IOException {
        if (email == null || !email.matches("[^@\\s]{1,128}@[^@\\s]{1,128}")) throw new IOException(L.t("Bambu: введите адрес почты аккаунта."));
        try {
            JSONObject body = new JSONObject().put("account", email.trim());
            if (code != null && !code.trim().isEmpty()) { if (!code.trim().matches("[0-9]{4,8}")) throw new IOException(L.t("Bambu: код из письма состоит из цифр.")); body.put("code", code.trim()); }
            else if (password == null || password.isEmpty()) { post(region, "v1/user-service/user/sendemail/code", new JSONObject().put("email", email.trim()).put("type", "codeLogin"), null, c); return new Login(null, null, null, true, false); }
            else body.put("password", password);
            JSONObject r = post(region, "v1/user-service/user/login", body, null, c);
            String token = r.optString("accessToken", "");
            if (token.isEmpty()) {
                String type = r.optString("loginType", "");
                if ("verifyCode".equals(type)) { post(region, "v1/user-service/user/sendemail/code", new JSONObject().put("email", email.trim()).put("type", "codeLogin"), null, c); return new Login(null, null, null, true, false); }
                if ("tfa".equals(type)) return new Login(null, null, null, false, true);
                throw new IOException(L.t("Bambu не выдал токен входа."));
            }
            String uid = profileUid(region, token, c);
            return new Login(token, r.optString("refreshToken", ""), uid, false, false, expiry(r));
        } catch (JSONException e) { throw new IOException(L.t("Bambu вернул некорректный ответ."), e); }
    }

    static String profileUid(String region, String token, NetworkHttp.Cancellation c) throws IOException {
        JSONObject p = get(region, "v1/user-service/my/profile", token, c);
        String uid = p.has("uid") ? String.valueOf(p.opt("uid")) : "";
        if (!uid.matches("[0-9]{1,20}")) throw new IOException(L.t("Bambu не сообщил идентификатор аккаунта."));
        return uid;
    }

    static List<Device> devices(String region, String token, NetworkHttp.Cancellation c) throws IOException {
        JSONArray list = get(region, "v1/iot-service/api/user/bind", token, c).optJSONArray("devices");
        List<Device> out = new ArrayList<>();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject d = list.optJSONObject(i); if (d == null) continue;
            String serial = d.optString("dev_id", ""); if (!serial.matches("[A-Za-z0-9]{6,32}")) continue;
            out.add(new Device(serial, d.optString("name", serial), d.optString("dev_product_name", d.optString("dev_model_name", "")), d.optBoolean("online", false)));
        }
        return out;
    }

    private static Map<String, String> headers(String token) {
        Map<String, String> h = new LinkedHashMap<>(); h.put("Content-Type", "application/json"); h.put("User-Agent", "KeplerSlicer");
        if (token != null) h.put("Authorization", "Bearer " + token); return h;
    }
    private static JSONObject post(String region, String path, JSONObject body, String token, NetworkHttp.Cancellation c) throws IOException {
        return reply(NetworkHttp.request("POST", URI.create(apiBase(region) + "/" + path), headers(token), NetworkHttp.bytes(body.toString().getBytes(StandardCharsets.UTF_8)), c));
    }
    private static JSONObject get(String region, String path, String token, NetworkHttp.Cancellation c) throws IOException {
        return reply(NetworkHttp.request("GET", URI.create(apiBase(region) + "/" + path), headers(token), null, c));
    }
    private static JSONObject reply(NetworkHttp.Response r) throws IOException {
        if (r.status == 401 || r.status == 403) throw new IOException(L.t("Bambu отклонил вход: проверьте почту, пароль или код и войдите снова."));
        if (r.status / 100 != 2) throw new IOException(L.t("Bambu: ошибка сервера (HTTP ") + r.status + ").");
        try { return new JSONObject(r.text()); } catch (JSONException e) { throw new IOException(L.t("Bambu вернул некорректный ответ."), e); }
    }
}
