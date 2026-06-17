package gq.yozakura.module.render.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NeteaseMusicApi {
    private static final String API = "https://netease-cloud-music-api-five-roan-88.vercel.app";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;
    private volatile String cookie = "";

    public void setCookie(String cookie) {
        this.cookie = cookie == null ? "" : cookie;
    }

    public String getCookie() {
        return cookie;
    }

    public List<Song> searchSongs(String keyword) throws IOException {
        JsonObject root = getJson("/search?keywords=" + encode(keyword) + "&type=1&limit=30");
        JsonObject result = object(root, "result");
        JsonArray songs = array(result, "songs");
        ArrayList<Song> output = new ArrayList<Song>();
        for (int i = 0; i < songs.size() && output.size() < 30; i++) {
            Song song = songFromSearch(object(songs.get(i)));
            if (song.id > 0L) {
                output.add(song);
            }
        }
        return output;
    }

    public String songUrl(long id) throws IOException {
        JsonObject root = getJson("/song/url?id=" + id + "&br=320000");
        JsonArray data = array(root, "data");
        if (data.size() == 0) {
            return null;
        }
        JsonObject item = object(data.get(0));
        String url = string(item, "url");
        return url.length() == 0 || "null".equals(url) ? null : url;
    }

    public Lyrics lyrics(long id) throws IOException {
        JsonObject root = getJson("/lyric?id=" + id);
        String raw = string(object(root, "lrc"), "lyric");
        String translated = string(object(root, "tlyric"), "lyric");
        return Lyrics.parse(raw, translated);
    }

    public String qrKey() throws IOException {
        IOException last = null;
        String timestamp = String.valueOf(System.currentTimeMillis());
        String[] paths = new String[] {
                "https://music.163.com/api/login/qrcode/unikey?type=1&timestamp=" + timestamp,
                "/login/qr/key?timestamp=" + timestamp
        };
        for (String path : paths) {
            try {
                JsonObject root = getJson(path);
                String key = string(object(root, "data"), "unikey");
                if (key.length() == 0) {
                    key = string(root, "unikey");
                }
                if (key.length() > 0) {
                    return key;
                }
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        return "";
    }

    public QrCode qrCode(String key) throws IOException {
        JsonObject root = getJson("/login/qr/create?key=" + encode(key)
                + "&qrimg=true&timestamp=" + System.currentTimeMillis());
        JsonObject data = object(root, "data");
        return new QrCode(string(data, "qrurl"), string(data, "qrimg"));
    }

    public QrStatus qrStatus(String key) throws IOException {
        IOException last = null;
        String timestamp = String.valueOf(System.currentTimeMillis());
        String[] paths = new String[] {
                "https://music.163.com/api/login/qrcode/client/login?key=" + encode(key)
                        + "&type=1&timestamp=" + timestamp,
                "/login/qr/check?key=" + encode(key) + "&timestamp=" + timestamp
        };
        for (String path : paths) {
            try {
                JsonObject root = getJson(path);
                int code = (int) number(root, "code");
                String message = string(root, "message");
                String nextCookie = string(root, "cookie");
                if (nextCookie.length() > 0) {
                    cookie = nextCookie;
                }
                return new QrStatus(code, message, cookie);
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        return new QrStatus(0, "", cookie);
    }

    public UserProfile account() throws IOException {
        IOException last = null;
        String timestamp = String.valueOf(System.currentTimeMillis());
        String[] paths = new String[] {
                "https://music.163.com/api/nuser/account/get?timestamp=" + timestamp,
                "https://music.163.com/api/user/account/get?timestamp=" + timestamp,
                "/user/account?timestamp=" + timestamp
        };
        for (String path : paths) {
            try {
                UserProfile profile = profileFromAccount(getJson(path));
                if (profile.id > 0L) {
                    return profile;
                }
                last = new IOException("Account response did not include a logged-in profile");
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("Account unavailable") : last;
    }

    public List<Playlist> userPlaylists(long uid) throws IOException {
        JsonObject root = getJson("/user/playlist?uid=" + uid + "&limit=50&timestamp=" + System.currentTimeMillis());
        JsonArray array = array(root, "playlist");
        ArrayList<Playlist> output = new ArrayList<Playlist>();
        for (int i = 0; i < array.size() && output.size() < 50; i++) {
            JsonObject item = object(array.get(i));
            long id = number(item, "id");
            if (id > 0L) {
                output.add(new Playlist(id, string(item, "name"), string(item, "coverImgUrl"),
                        (int) number(item, "trackCount")));
            }
        }
        return output;
    }

    public List<Song> playlistSongs(long playlistId) throws IOException {
        JsonObject root = getJson("/playlist/detail?id=" + playlistId + "&timestamp=" + System.currentTimeMillis());
        JsonArray tracks = array(object(root, "playlist"), "tracks");
        ArrayList<Song> output = new ArrayList<Song>();
        for (int i = 0; i < tracks.size() && output.size() < 300; i++) {
            Song song = songFromTrack(object(tracks.get(i)));
            if (song.id > 0L) {
                output.add(song);
            }
        }
        return output;
    }

    public byte[] download(String url) throws IOException {
        HttpURLConnection connection = open(url);
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("User-Agent", "Yozakura MusicPlayer");
        InputStream input = connection.getInputStream();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
            connection.disconnect();
        }
    }

    private Song songFromSearch(JsonObject object) {
        JsonObject album = object(object, "album");
        JsonArray artists = array(object, "artists");
        return new Song(number(object, "id"), string(object, "name"),
                joinArtists(artists), number(album, "id"), string(album, "name"),
                string(album, "picUrl"), number(object, "duration"));
    }

    private Song songFromTrack(JsonObject object) {
        JsonObject album = object(object, "al");
        JsonArray artists = array(object, "ar");
        return new Song(number(object, "id"), string(object, "name"),
                joinArtists(artists), number(album, "id"), string(album, "name"),
                string(album, "picUrl"), number(object, "dt"));
    }

    private JsonObject getJson(String path) throws IOException {
        return object(new JsonParser().parse(get(path)));
    }

    private String get(String path) throws IOException {
        URL target = new URL(path.startsWith("http") ? path : API + path);
        IOException last = null;
        for (Proxy proxy : resolveProxies(target)) {
            HttpURLConnection connection = null;
            InputStream input = null;
            try {
                connection = open(target, proxy);
                prepare(connection);
                input = connection.getInputStream();
                absorbCookies(connection);
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return builder.toString();
            } catch (IOException e) {
                last = e;
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (Throwable ignored) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        throw last == null ? new IOException("Request failed") : last;
    }

    private static HttpURLConnection open(String url) throws IOException {
        URL target = new URL(url);
        return open(target, firstProxy(target));
    }

    private static HttpURLConnection open(URL target, Proxy proxy) throws IOException {
        return (HttpURLConnection) (proxy == null ? target.openConnection() : target.openConnection(proxy));
    }

    private void prepare(HttpURLConnection connection) throws IOException {
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Yozakura MusicPlayer");
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (connection.getURL().getHost().contains("music.163.com")) {
            connection.setRequestProperty("Referer", "https://music.163.com/");
            connection.setRequestProperty("Origin", "https://music.163.com");
        }
        if (cookie.length() > 0) {
            connection.setRequestProperty("Cookie", cookie);
        }
    }

    private static Proxy firstProxy(URL target) {
        List<Proxy> proxies = resolveProxies(target);
        return proxies.isEmpty() ? null : proxies.get(0);
    }

    private static List<Proxy> resolveProxies(URL target) {
        ArrayList<Proxy> proxies = new ArrayList<Proxy>();
        ProxyCandidate candidate = null;
        if ("https".equalsIgnoreCase(target.getProtocol())) {
            candidate = proxyCandidate("https.proxyHost", "https.proxyPort", "HTTPS_PROXY", "https_proxy");
        }
        if (candidate == null) {
            candidate = proxyCandidate("http.proxyHost", "http.proxyPort", "HTTP_PROXY", "http_proxy");
        }
        if (candidate != null) {
            proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(candidate.host, candidate.port)));
        }
        addProxy(proxies, "127.0.0.1", 7890);
        addProxy(proxies, "127.0.0.1", 7897);
        addProxy(proxies, "127.0.0.1", 1080);
        proxies.add(null);
        return proxies;
    }

    private static void addProxy(List<Proxy> proxies, String host, int port) {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        for (Proxy existing : proxies) {
            if (existing != null && existing.address() != null && existing.address().equals(proxy.address())) {
                return;
            }
        }
        proxies.add(proxy);
    }

    private void absorbCookies(HttpURLConnection connection) {
        try {
            Map<String, List<String>> headers = connection.getHeaderFields();
            if (headers == null) {
                return;
            }
            List<String> setCookies = null;
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && "Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                    setCookies = entry.getValue();
                    break;
                }
            }
            if (setCookies == null || setCookies.isEmpty()) {
                return;
            }
            cookie = mergeCookies(cookie, setCookies);
        } catch (Throwable ignored) {
        }
    }

    private static String mergeCookies(String current, List<String> setCookies) {
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
        if (current != null && current.length() > 0) {
            String[] pieces = current.split(";");
            for (int i = 0; i < pieces.length; i++) {
                String piece = pieces[i].trim();
                int equals = piece.indexOf('=');
                if (equals > 0 && piece.length() > equals + 1) {
                    values.put(piece.substring(0, equals), piece.substring(equals + 1));
                }
            }
        }
        for (int i = 0; i < setCookies.size(); i++) {
            String cookieLine = setCookies.get(i);
            if (cookieLine == null || cookieLine.length() == 0) {
                continue;
            }
            int end = cookieLine.indexOf(';');
            String piece = end >= 0 ? cookieLine.substring(0, end) : cookieLine;
            int equals = piece.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = piece.substring(0, equals).trim();
            String value = piece.substring(equals + 1).trim();
            if (name.length() == 0 || value.length() == 0) {
                continue;
            }
            values.put(name, value);
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static UserProfile profileFromAccount(JsonObject root) {
        JsonObject profile = object(root, "profile");
        JsonObject account = object(root, "account");
        long id = number(profile, "userId");
        if (id <= 0L) {
            id = number(profile, "id");
        }
        if (id <= 0L) {
            id = number(account, "id");
        }
        String nickname = string(profile, "nickname");
        if (nickname.length() == 0 && id > 0L) {
            nickname = "NetEase User";
        }
        return new UserProfile(id, nickname, string(profile, "avatarUrl"));
    }

    private static ProxyCandidate proxyCandidate(String hostProperty, String portProperty, String upperEnv, String lowerEnv) {
        String host = System.getProperty(hostProperty);
        int port = intValue(System.getProperty(portProperty), -1);
        if (host != null && host.length() > 0 && port > 0) {
            return new ProxyCandidate(host, port);
        }
        String raw = System.getenv(upperEnv);
        if (raw == null || raw.length() == 0) {
            raw = System.getenv(lowerEnv);
        }
        if (raw == null || raw.length() == 0) {
            return null;
        }
        try {
            URI uri = raw.contains("://") ? URI.create(raw) : URI.create("http://" + raw);
            if (uri.getHost() != null && uri.getHost().length() > 0 && uri.getPort() > 0) {
                return new ProxyCandidate(uri.getHost(), uri.getPort());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int intValue(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static final class ProxyCandidate {
        final String host;
        final int port;

        ProxyCandidate(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text == null ? "" : text, "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String joinArtists(JsonArray artists) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < artists.size(); i++) {
            String name = string(object(artists.get(i)), "name");
            if (name.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(name);
        }
        return builder.length() == 0 ? "Unknown Artist" : builder.toString();
    }

    private static JsonObject object(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return new JsonObject();
        }
        return object(object.get(name));
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return new JsonArray();
        }
        JsonElement element = object.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return "";
        }
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        try {
            return element.getAsString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static long number(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return 0L;
        }
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return 0L;
        }
        try {
            return element.getAsLong();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static final class Song {
        public final long id;
        public final String name;
        public final String artist;
        public final long albumId;
        public final String album;
        public final String coverUrl;
        public final long durationMs;

        public Song(long id, String name, String artist, long albumId, String album, String coverUrl, long durationMs) {
            this.id = id;
            this.name = name;
            this.artist = artist;
            this.albumId = albumId;
            this.album = album;
            this.coverUrl = coverUrl;
            this.durationMs = durationMs <= 0L ? 240000L : durationMs;
        }
    }

    public static final class Playlist {
        public final long id;
        public final String name;
        public final String coverUrl;
        public final int trackCount;

        public Playlist(long id, String name, String coverUrl, int trackCount) {
            this.id = id;
            this.name = name;
            this.coverUrl = coverUrl;
            this.trackCount = trackCount;
        }
    }

    public static final class UserProfile {
        public final long id;
        public final String nickname;
        public final String avatarUrl;

        public UserProfile(long id, String nickname, String avatarUrl) {
            this.id = id;
            this.nickname = nickname;
            this.avatarUrl = avatarUrl;
        }
    }

    public static final class QrCode {
        public final String url;
        public final String image;

        public QrCode(String url, String image) {
            this.url = url;
            this.image = image;
        }
    }

    public static final class QrStatus {
        public final int code;
        public final String message;
        public final String cookie;

        public QrStatus(int code, String message, String cookie) {
            this.code = code;
            this.message = message;
            this.cookie = cookie;
        }
    }
}
