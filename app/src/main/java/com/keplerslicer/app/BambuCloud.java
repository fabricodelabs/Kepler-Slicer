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
        final String token, refresh, uid; final boolean needsCode, needsTfa; final long expiresAt; String tfaKey = "", notice = "";
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
            boolean byCode = code != null && !code.trim().isEmpty();
            if (byCode) { if (!code.trim().matches("[0-9]{4,8}")) throw new IOException(L.t("Bambu: код из письма состоит из цифр.")); body.put("code", code.trim()); }
            else if (password == null || password.isEmpty()) { sendCode(region, email, c); return new Login(null, null, null, true, false); }
            else body.put("password", password);
            body.put("apiError", "");
            NetworkHttp.Response raw = NetworkHttp.request("POST", URI.create(apiBase(region) + "/v1/user-service/user/login"), headers(null), NetworkHttp.bytes(body.toString().getBytes(StandardCharsets.UTF_8)), c);
            if (byCode && raw.status == 400) {
                JSONObject e = jsonOrNull(raw);
                int reason = e == null ? 0 : e.optInt("code", 0);
                if (reason == 1) { sendCode(region, email, c); Login again = new Login(null, null, null, true, false); again.notice = L.t("Код устарел — мы отправили новый на почту."); return again; }
                if (reason == 2) throw new IOException(L.t("Неверный код подтверждения. Проверьте письмо и введите код ещё раз."));
            }
            JSONObject r = reply(raw);
            String token = r.optString("accessToken", "");
            if (token.isEmpty()) {
                String type = r.optString("loginType", "");
                if ("verifyCode".equals(type)) { sendCode(region, email, c); return new Login(null, null, null, true, false); }
                if ("tfa".equals(type)) { Login tfa = new Login(null, null, null, false, true); tfa.tfaKey = r.optString("tfaKey", ""); return tfa; }
                throw new IOException(L.t("Bambu не выдал токен входа."));
            }
            String uid = accountUid(region, token, c);
            return new Login(token, r.optString("refreshToken", ""), uid, false, false, expiry(r));
        } catch (JSONException e) { throw new IOException(L.t("Bambu вернул некорректный ответ."), e); }
    }

    static void sendCode(String region, String email, NetworkHttp.Cancellation c) throws IOException {
        try { post(region, "v1/user-service/user/sendemail/code", new JSONObject().put("email", email.trim()).put("type", "codeLogin"), null, c); }
        catch (JSONException e) { throw new IOException(L.t("Bambu вернул некорректный ответ."), e); }
    }

    /** Second step of two-factor sign-in: the code from the authenticator app for the key the login returned. */
    static Login signInTfa(String region, String tfaKey, String code, NetworkHttp.Cancellation c) throws IOException {
        if (tfaKey == null || tfaKey.isEmpty()) throw new IOException(L.t("Bambu не выдал ключ двухфакторного входа. Войдите по коду из письма."));
        if (code == null || !code.trim().matches("[0-9]{6}")) throw new IOException(L.t("Код из приложения-аутентификатора состоит из 6 цифр."));
        String site = testBase != null ? testBase : china(region) ? "https://bambulab.cn" : "https://bambulab.com";
        try {
            JSONObject body = new JSONObject().put("tfaKey", tfaKey).put("tfaCode", code.trim());
            NetworkHttp.Response r = NetworkHttp.request("POST", URI.create(site + "/api/sign-in/tfa"), headers(null), NetworkHttp.bytes(body.toString().getBytes(StandardCharsets.UTF_8)), c);
            String token = cookie(r, "token");
            if (token.isEmpty()) { JSONObject j = reply(r); token = j.optString("accessToken", j.optString("token", "")); }
            if (token.isEmpty()) throw new IOException(L.t("Bambu не принял код. Проверьте код и повторите."));
            return new Login(token, cookie(r, "refreshToken"), accountUid(region, token, c), false, false, 0);
        } catch (JSONException e) { throw new IOException(L.t("Bambu вернул некорректный ответ."), e); }
    }
    private static String cookie(NetworkHttp.Response r, String name) {
        for (Map.Entry<String, List<String>> h : r.headers.entrySet()) {
            if (h.getKey() == null || !h.getKey().equalsIgnoreCase("Set-Cookie") || h.getValue() == null) continue;
            for (String v : h.getValue()) { if (v == null) continue; String first = v.split(";", 2)[0].trim(); if (first.startsWith(name + "=")) return first.substring(name.length() + 1); }
        }
        return "";
    }

    /** Account id for the MQTT user name: from the token itself when it is a JWT, otherwise from the account API. */
    static String accountUid(String region, String token, NetworkHttp.Cancellation c) throws IOException {
        String fromToken = jwtUid(token); if (!fromToken.isEmpty()) return fromToken;
        for (String path : new String[] {"v1/design-user-service/my/preference", "v1/user-service/my/profile"}) {
            try { JSONObject p = get(region, path, token, c); String uid = p.has("uid") ? String.valueOf(p.opt("uid")) : ""; if (uid.matches("[0-9]{1,20}")) return uid; }
            catch (IOException ignored) { }
        }
        throw new IOException(L.t("Bambu не сообщил идентификатор аккаунта."));
    }
    static String profileUid(String region, String token, NetworkHttp.Cancellation c) throws IOException { return accountUid(region, token, c); }
    static String jwtUid(String token) {
        if (token == null) return "";
        String[] parts = token.split("\\."); if (parts.length != 3) return "";
        try {
            JSONObject payload = new JSONObject(new String(java.util.Base64.getUrlDecoder().decode(parts[1].replace("=", "")), StandardCharsets.UTF_8));
            String name = payload.optString("username", ""); if (name.startsWith("u_")) name = name.substring(2);
            if (name.matches("[0-9]{1,20}")) return name;
            String uid = payload.has("uid") ? String.valueOf(payload.opt("uid")) : "";
            return uid.matches("[0-9]{1,20}") ? uid : "";
        } catch (IllegalArgumentException | JSONException e) { return ""; }
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

    /**
     * Puts a file into the account's cloud storage: the API returns signed upload URLs, the file is PUT to them
     * unchanged (no extra headers, they would break the signature). No print is started.
     */
    static String upload(String region, String token, java.io.File file, String name, NetworkHttp.Cancellation c) throws IOException {
        if (name == null || !name.matches("[^/\\\\\\p{Cntrl}]{1,200}")) throw new IOException(L.t("Недопустимое имя файла."));
        long size = file.length();
        NetworkHttp.Response r = NetworkHttp.request("GET", URI.create(apiBase(region) + "/v1/iot-service/api/user/upload?filename=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20") + "&size=" + size), headers(token), null, c);
        JSONObject info = reply(r); String fileUrl = info.optString("upload_url", ""), sizeUrl = "";
        JSONArray urls = info.optJSONArray("urls");
        for (int i = 0; urls != null && i < urls.length(); i++) { JSONObject u = urls.optJSONObject(i); if (u == null) continue; String type = u.optString("type"), url = u.optString("url");
            if ("filename".equals(type) && fileUrl.isEmpty()) fileUrl = url; else if ("size".equals(type)) sizeUrl = url; }
        if (fileUrl.isEmpty() && urls != null && urls.optJSONObject(0) != null) fileUrl = urls.optJSONObject(0).optString("url", "");
        if (!fileUrl.startsWith("https://") && !(testBase != null && fileUrl.startsWith(testBase))) throw new IOException(L.t("Bambu не выдал адрес загрузки."));
        NetworkHttp.Response put = NetworkHttp.request("PUT", URI.create(fileUrl), new LinkedHashMap<>(), NetworkHttp.file(file), c, 64 * 1024);
        if (put.status / 100 != 2) throw new IOException(L.t("Облако Bambu не приняло файл (HTTP ") + put.status + ").");
        if (sizeUrl.startsWith("https://") || (testBase != null && sizeUrl.startsWith(testBase))) { try { NetworkHttp.request("PUT", URI.create(sizeUrl), new LinkedHashMap<>(), NetworkHttp.bytes(String.valueOf(size).getBytes(StandardCharsets.UTF_8)), c, 64 * 1024); } catch (IOException ignored) { } }
        return name;
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
    private static JSONObject jsonOrNull(NetworkHttp.Response r) { try { return new JSONObject(r.text()); } catch (JSONException e) { return null; } }
    private static JSONObject reply(NetworkHttp.Response r) throws IOException {
        String text = r.text().trim();
        if (text.startsWith("<")) throw new IOException(L.t("Сервер Bambu ответил страницей проверки вместо данных. Попробуйте вход по коду из письма или повторите позже."));
        if (r.status / 100 != 2) {
            JSONObject e = jsonOrNull(r); String why = e == null ? "" : e.optString("error", e.optString("message", ""));
            if (r.status == 400 || r.status == 401 || r.status == 403) throw new IOException(L.t("Bambu отклонил вход: проверьте почту, пароль или код и войдите снова.") + (why.isEmpty() ? "" : "\n" + why));
            throw new IOException(L.t("Bambu: ошибка сервера (HTTP ") + r.status + ")." + (why.isEmpty() ? "" : "\n" + why));
        }
        try { return new JSONObject(r.text()); } catch (JSONException e) { throw new IOException(L.t("Bambu вернул некорректный ответ."), e); }
    }
}
