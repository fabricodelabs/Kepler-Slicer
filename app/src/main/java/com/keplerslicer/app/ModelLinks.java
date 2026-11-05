package com.keplerslicer.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model page links from catalogue sites. A page is not a file: Printables pages are resolved to their model files
 * through the site's public API; sites that only give files to signed-in users get a clear explanation instead.
 */
final class ModelLinks {
    private ModelLinks() {}

    static final class FileRef {
        final String name, url; final long size;
        FileRef(String name, String url, long size) { this.name = name; this.url = url; this.size = size; }
    }

    private static final Pattern PRINTABLES = Pattern.compile("(?i)^(?:www\\.)?printables\\.com$");
    private static final Pattern PRINTABLES_ID = Pattern.compile("/model/(\\d+)");

    /** Printables model id of a page link, or null. */
    static String printablesId(URI uri) {
        if (uri.getHost() == null || !PRINTABLES.matcher(uri.getHost()).matches()) return null;
        Matcher m = PRINTABLES_ID.matcher(uri.getPath() == null ? "" : uri.getPath());
        return m.find() ? m.group(1) : null;
    }

    /** Explanation for a page link that cannot be downloaded directly, or null when the link may be a file. */
    static String unsupportedPage(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith("makerworld.com") || host.endsWith("makerworld.com.cn"))
            return L.t("MakerWorld отдаёт файлы только после входа в аккаунт на сайте. Скачайте модель на сайте (кнопка «Скачать»), затем откройте её здесь через «Загрузки».");
        if (host.endsWith("thingiverse.com") || host.endsWith("cults3d.com") || host.endsWith("thangs.com"))
            return L.t("Это страница модели, а не файл. Скачайте модель на сайте, затем откройте её здесь через «Загрузки».");
        return null;
    }

    /** Model files of a Printables model (STL and 3MF where present). */
    static List<FileRef> printablesFiles(String id, NetworkHttp.Cancellation c) throws IOException {
        JSONObject data = graphQl("query { print(id: \"" + id + "\") { id name stls { id name fileSize } } }", c).optJSONObject("print");
        if (data == null) throw new IOException(L.t("Printables: модель не найдена."));
        JSONArray stls = data.optJSONArray("stls");
        List<FileRef> out = new ArrayList<>();
        for (int i = 0; stls != null && i < stls.length(); i++) {
            JSONObject f = stls.optJSONObject(i); if (f == null) continue;
            String fileId = f.optString("id", ""), name = f.optString("name", "");
            if (!fileId.matches("\\d{1,20}") || name.isEmpty()) continue;
            out.add(new FileRef(name, "printables:" + id + ":" + fileId, f.optLong("fileSize", 0)));
        }
        if (out.isEmpty()) throw new IOException(L.t("Printables: у модели нет файлов STL/3MF для скачивания."));
        return out;
    }

    /** Signed download URL for one Printables file. */
    static String printablesLink(String ref, NetworkHttp.Cancellation c) throws IOException {
        String[] p = ref.split(":");
        if (p.length != 3 || !p[1].matches("\\d{1,20}") || !p[2].matches("\\d{1,20}")) throw new IOException(L.t("Printables: неверная ссылка на файл."));
        JSONObject r = graphQl("mutation { getDownloadLink(id: \"" + p[2] + "\", printId: \"" + p[1] + "\", fileType: stl, source: model_detail) { ok output { link } } }", c).optJSONObject("getDownloadLink");
        String link = r == null || !r.optBoolean("ok") || r.optJSONObject("output") == null ? "" : r.optJSONObject("output").optString("link", "");
        if (!link.startsWith("https://")) throw new IOException(L.t("Printables не выдал ссылку на файл. Попробуйте позже."));
        return link;
    }

    private static JSONObject graphQl(String query, NetworkHttp.Cancellation c) throws IOException {
        try {
            byte[] body = new JSONObject().put("query", query).toString().getBytes(StandardCharsets.UTF_8);
            java.util.Map<String, String> h = new java.util.LinkedHashMap<>(); h.put("Content-Type", "application/json"); h.put("User-Agent", "KeplerSlicer");
            NetworkHttp.Response r = NetworkHttp.request("POST", URI.create("https://api.printables.com/graphql/"), h, NetworkHttp.bytes(body), c);
            if (r.status / 100 != 2) throw new IOException(L.t("Printables: ошибка сервера (HTTP ") + r.status + ").");
            JSONObject json = new JSONObject(r.text()); JSONObject data = json.optJSONObject("data");
            if (data == null) throw new IOException(L.t("Printables вернул некорректный ответ."));
            return data;
        } catch (org.json.JSONException e) { throw new IOException(L.t("Printables вернул некорректный ответ."), e); }
    }
}
