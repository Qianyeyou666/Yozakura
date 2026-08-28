package gq.yozakura.club;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ClubApiClient {
    public static final String DEFAULT_BASE_URL = "https://yozakura.wtf";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 12000;
    private static final int MAX_RESPONSE_CHARS = 1024 * 1024;

    private final String baseUrl;

    public ClubApiClient() {
        this(resolveBaseUrl());
    }

    public ClubApiClient(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public ClubAuthResult register(String username, String password) throws IOException {
        return authenticate("/api/auth/register", username, password);
    }

    public ClubAuthResult login(String username, String password) throws IOException {
        return authenticate("/api/auth/login", username, password);
    }

    public ClubAuthResult exchangeVerifiedClient(String verificationProof) throws IOException {
        JsonObject response = requestWithAuthorization("POST", "/api/auth/client-exchange",
                "PoP " + requireText(verificationProof, "Client verification proof"), null);
        String token = requiredString(response, "token");
        return new ClubAuthResult(token, parseUser(requiredObject(response, "user")));
    }

    public void logout(String token) throws IOException {
        request("POST", "/api/auth/logout", token, null);
    }

    public ClubUser getCurrentUser(String token) throws IOException {
        JsonObject response = request("GET", "/api/me", token, null);
        return parseUser(requiredObject(response, "user"));
    }

    public List<ClubConfigSummary> listConfigs(String token) throws IOException {
        return parseConfigList(request("GET", "/api/configs", token, null));
    }

    public ClubConfigSummary saveConfig(String token, String name, JsonObject payload) throws IOException {
        return saveConfig(token, name, "private", payload);
    }

    public ClubConfigSummary saveHallConfig(String token, String name, JsonObject payload)
            throws IOException {
        return saveConfig(token, name, "public", payload);
    }

    public List<ClubConfigSummary> listHallConfigs() throws IOException {
        return parseConfigList(request("GET", "/api/config-hall", null, null));
    }

    public ClubConfig getHallConfig(String id) throws IOException {
        String encoded = URLEncoder.encode(requireText(id, "Hall config id"), "UTF-8")
                .replace("+", "%20");
        return parseConfig(request("GET", "/api/config-hall/" + encoded, null, null));
    }

    private ClubConfigSummary saveConfig(String token, String name, String visibility,
                                         JsonObject payload) throws IOException {
        if (payload == null) {
            throw new IOException("Cloud config payload must be a JSON object");
        }
        JsonObject body = new JsonObject();
        body.addProperty("name", requireText(name, "Cloud config name"));
        body.addProperty("visibility", visibility);
        body.add("payload", payload);
        JsonObject response = request("POST", "/api/configs", token, body);
        return parseConfigSummary(requiredObject(response, "config"));
    }

    public ClubConfig getConfig(String token, String idOrName) throws IOException {
        String encoded = URLEncoder.encode(requireText(idOrName, "Cloud config id"), "UTF-8")
                .replace("+", "%20");
        return parseConfig(request("GET", "/api/configs/" + encoded, token, null));
    }

    public void deleteConfig(String token, String idOrName) throws IOException {
        String encoded = URLEncoder.encode(requireText(idOrName, "Cloud config id"), "UTF-8")
                .replace("+", "%20");
        request("DELETE", "/api/configs/" + encoded, token, null);
    }

    private ClubAuthResult authenticate(String path, String username, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username", requireText(username, "Club username"));
        body.addProperty("password", password == null ? "" : password);
        JsonObject response = request("POST", path, null, body);
        String token = requiredString(response, "token");
        return new ClubAuthResult(token, parseUser(requiredObject(response, "user")));
    }

    private JsonObject request(String method, String path, String token, JsonObject body) throws IOException {
        return requestWithAuthorization(method, path,
                isBlank(token) ? null : "Bearer " + token.trim(), body);
    }

    private JsonObject requestWithAuthorization(String method, String path, String authorization,
                                                JsonObject body) throws IOException {
        HttpURLConnection connection = null;
        try {
            String requestUrl = baseUrl + path;
            URL url = new URL(requestUrl);
            Proxy proxy = proxyFor(requestUrl, environmentProxy(url), environmentNoProxy());
            connection = (HttpURLConnection) (proxy == Proxy.NO_PROXY
                    ? url.openConnection() : url.openConnection(proxy));
            connection.setRequestMethod(method);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Yozakura-Club-Client/1.0");
            if (!isBlank(authorization)) {
                connection.setRequestProperty("Authorization", authorization.trim());
            }
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(bytes);
                } finally {
                    output.close();
                }
            }

            int code = connection.getResponseCode();
            String responseBody = readBody(code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            JsonObject response = parseObject(responseBody, "Club response");
            if (code < 200 || code >= 300) {
                String message = optionalString(response, "error");
                throw new ClubApiException(code, isBlank(message)
                        ? "Club request failed: HTTP " + code : message);
            }
            return response;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static List<ClubConfigSummary> parseConfigList(JsonObject response) throws IOException {
        JsonElement configsElement = response.get("configs");
        if (configsElement == null || !configsElement.isJsonArray()) {
            throw new IOException("Club response missing configs array");
        }
        JsonArray configs = configsElement.getAsJsonArray();
        List<ClubConfigSummary> result = new ArrayList<ClubConfigSummary>();
        for (JsonElement element : configs) {
            if (element != null && element.isJsonObject()) {
                result.add(parseConfigSummary(element.getAsJsonObject()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static ClubConfig parseConfig(JsonObject response) throws IOException {
        JsonObject config = requiredObject(response, "config");
        JsonElement payload = config.get("payload");
        if (payload == null || !payload.isJsonObject()) {
            throw new IOException("Cloud config payload must be a JSON object");
        }
        return new ClubConfig(parseConfigSummary(config), payload.getAsJsonObject());
    }

    private static ClubUser parseUser(JsonObject user) throws IOException {
        return new ClubUser(requiredString(user, "id"), requiredString(user, "username"),
                optionalString(user, "createdAt"));
    }

    private static ClubConfigSummary parseConfigSummary(JsonObject config) throws IOException {
        return new ClubConfigSummary(requiredString(config, "id"), requiredString(config, "name"),
                optionalString(config, "visibility"), optionalString(config, "owner"),
                optionalString(config, "createdAt"), optionalString(config, "updatedAt"));
    }

    private static JsonObject parseObject(String json, String label) throws IOException {
        try {
            JsonElement element = new JsonParser().parse(json == null ? "" : json);
            if (element == null || !element.isJsonObject()) {
                throw new IOException(label + " must be a JSON object");
            }
            return element.getAsJsonObject();
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException(label + " was not valid JSON", exception);
        }
    }

    private static JsonObject requiredObject(JsonObject object, String name) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonObject()) {
            throw new IOException("Club response missing " + name + " object");
        }
        return element.getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        String value = optionalString(object, name);
        if (isBlank(value)) {
            throw new IOException("Club response missing " + name);
        }
        return value;
    }

    private static String optionalString(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "{}";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        try {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                if (builder.length() + count > MAX_RESPONSE_CHARS) {
                    throw new IOException("Club response exceeded size limit");
                }
                builder.append(buffer, 0, count);
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

    static Proxy proxyFor(String requestUrl, String proxyUrl, String noProxy) {
        try {
            URI request = URI.create(requestUrl);
            String host = request.getHost();
            if (isBlank(host) || bypassesProxy(host, noProxy)) {
                return Proxy.NO_PROXY;
            }
            Proxy environment = parseHttpProxy(proxyUrl);
            if (environment != Proxy.NO_PROXY) {
                return environment;
            }
            return selectSystemProxy(request);
        } catch (IllegalArgumentException exception) {
            return Proxy.NO_PROXY;
        }
    }

    private static Proxy parseHttpProxy(String proxyUrl) {
        if (isBlank(proxyUrl)) {
            return Proxy.NO_PROXY;
        }
        URI proxy = URI.create(proxyUrl.trim());
        String proxyHost = proxy.getHost();
        if (isBlank(proxyHost)) {
            return Proxy.NO_PROXY;
        }
        int proxyPort = proxy.getPort();
        if (proxyPort < 0) {
            proxyPort = "https".equalsIgnoreCase(proxy.getScheme()) ? 443 : 80;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    private static Proxy selectSystemProxy(URI request) {
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(request);
            if (proxies != null) {
                for (Proxy proxy : proxies) {
                    if (proxy != null && proxy.type() == Proxy.Type.HTTP) {
                        return proxy;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to a direct request when the host proxy selector is unavailable.
        }
        return Proxy.NO_PROXY;
    }

    private static String environmentProxy(URL url) {
        String primary = environmentIgnoreCase("https".equalsIgnoreCase(url.getProtocol())
                ? "HTTPS_PROXY" : "HTTP_PROXY");
        if (!isBlank(primary)) {
            return primary;
        }
        return environmentIgnoreCase("ALL_PROXY");
    }

    private static String environmentIgnoreCase(String name) {
        String direct = System.getenv(name);
        if (!isBlank(direct)) {
            return direct;
        }
        for (String key : System.getenv().keySet()) {
            if (name.equals(key.toUpperCase(Locale.ROOT))) {
                return System.getenv(key);
            }
        }
        return null;
    }

    private static String environmentNoProxy() {
        return environmentIgnoreCase("NO_PROXY");
    }

    private static boolean bypassesProxy(String host, String noProxy) {
        if (isBlank(noProxy)) {
            return false;
        }
        String normalizedHost = host.trim().toLowerCase();
        String[] entries = noProxy.split(",");
        for (String entry : entries) {
            String pattern = entry == null ? "" : entry.trim().toLowerCase();
            if (pattern.isEmpty()) {
                continue;
            }
            int colon = pattern.lastIndexOf(':');
            if (colon > 0 && pattern.indexOf(']') < 0) {
                pattern = pattern.substring(0, colon);
            }
            if ("*".equals(pattern)
                    || normalizedHost.equals(pattern)
                    || (pattern.startsWith(".") && normalizedHost.endsWith(pattern))
                    || (!pattern.startsWith(".") && normalizedHost.endsWith("." + pattern))) {
                return true;
            }
        }
        return false;
    }

    private static String resolveBaseUrl() {
        String property = System.getProperty("yozakura.club.api");
        if (!isBlank(property)) {
            return property;
        }
        String environment = System.getenv("YOZAKURA_CLUB_API");
        return isBlank(environment) ? DEFAULT_BASE_URL : environment;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = requireText(value, "Club API base URL");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalArgumentException("Club API base URL must use HTTP or HTTPS");
        }
        return normalized;
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class ClubApiException extends IOException {
        private final int statusCode;

        private ClubApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
