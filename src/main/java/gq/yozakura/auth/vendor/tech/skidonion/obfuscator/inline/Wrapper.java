package gq.yozakura.auth.vendor.tech.skidonion.obfuscator.inline;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import gq.yozakura.auth.vendor.skidonion.sWdSl.JsonObject;
import gq.yozakura.auth.vendor.skidonion.sWdSl.JsonValue;
import gq.yozakura.auth.vendor.skidonion.sWdSl.Base64Codec;
import gq.yozakura.auth.vendor.skidonion.sWdSl.JsonArray;
import gq.yozakura.auth.vendor.skidonion.sWdSl.Json;
import gq.yozakura.auth.vendor.skidonion.sWdSl.FormUrlEncoder;
import gq.yozakura.auth.vendor.skidonion.sWdSl.ChaChaStream;

public class Wrapper {
    private static final String AUTH_BASE_URL_PROPERTY = "yozakura.auth.baseUrl";
    private static final String AUTH_BASE_URL_ENV = "YOZAKURA_AUTH_BASE_URL";
    private static final String CLIENT_BUILD_ID = "local-20260615-secure-auth-2";
    private static final long VERIFY_GRACE_MILLIS = TimeUnit.MINUTES.toMillis(6L);
    private static final byte[] ENVIRONMENT_XOR_KEY =
            new byte[] { 82, -7, -93, -53, -113, 107, -127, 8 };
    private static final byte[] ENVIRONMENT_MARKER = new byte[] { -1, 4 };
    private static final Map<Integer, List<String>> DEFAULT_CONSTANT_POOL = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<Integer, byte[]> CLOUD_CONSTANT_MAP = new HashMap<>();
    private static final Map<String, LocalDateTime> EXPIRED_DATE = new HashMap<>();
    private static final Map<String, String> HEADERS = new HashMap<>();
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final AtomicLong HEARTBEAT_SEQUENCE = new AtomicLong();
    private static final Pattern QQ_UIN_PATTERN = Pattern.compile("^[1-9][0-9]{4,10}$");
    private static volatile ScheduledExecutorService heartbeatExecutor;
    private static volatile long userId;
    private static volatile String username;
    private static volatile String nickname;
    private static volatile String verifyToken;
    private static volatile byte[] magicKey;
    private static volatile byte[] key;
    private static volatile byte[] nonce;
    private static volatile long lastVerifiedAt;
    private static volatile String clientFingerprint;
    private static volatile String machineFingerprint;

    public Wrapper() {
    }

    public static void _debug_addDefaultCloudConstant(String key, String value) {
        DEFAULT_CONSTANT_POOL.compute(key.hashCode(), (ignored, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(value);
            return list;
        });
    }

    public static Optional<Long> getUserId() {
        return userId > 0 ? Optional.of(userId) : Optional.empty();
    }

    public static Optional<String> getUsername() {
        return username == null ? Optional.empty() : Optional.of(username);
    }

    public static Optional<String> getNickname() {
        return nickname == null ? Optional.empty() : Optional.of(nickname);
    }

    public static int login(String username, String password, boolean remember) {
        ensureRuntime();
        int marker = RANDOM.nextInt();
        int code = -1;
        verifyToken = null;
        lastVerifiedAt = 0L;
        HEARTBEAT_SEQUENCE.set(0L);
        EXPIRED_DATE.clear();
        Map<String, String> verifyHeaders = new HashMap<>();
        if (verifyToken != null) {
            verifyHeaders.put("verify-token", verifyToken);
        }

        try {
            Map<String, String> body = new HashMap<>();
            body.put("username", FormUrlEncoder.encode(username));
            body.put("password", FormUrlEncoder.encode(password));
            body.put("software_id", Long.toString(183L));
            body.put("e", FormUrlEncoder.encode(Boolean.toString(remember)));
            body.put("build", FormUrlEncoder.encode(CLIENT_BUILD_ID));
            body.put("fp", clientFingerprint());
            body.put("hw", machineFingerprint());

            String response = post(authApi("api/v2/verify/login"), body, verifyHeaders);
            if (response != null) {
                JsonObject root = Json.parse(response).asObject();
                code = (byte) root.getInt("code", -1);
                if (code == 0) {
                    JsonObject entity = root.get("entity").asObject();
                    userId = entity.getLong("uid", -1L);
                    Wrapper.username = username;
                    verifyToken = entity.getString("jwt", "");
                    nickname = entity.getString("nickname", "");
                    JsonValue loginRoles = entity.get("roles");
                    if (loginRoles != null) {
                        parseRoleExpiry(loginRoles.asArray());
                    }
                    Optional<Byte> firstHeartbeat = getVerifyTokenOptional().isPresent()
                            ? initialHeartbeat()
                            : Optional.empty();
                    if (!firstHeartbeat.isPresent()) {
                        code = -3;
                    } else if (firstHeartbeat.get() != 0) {
                        code = (byte) (firstHeartbeat.get() + 100);
                    } else {
                        scheduleHeartbeat();
                    }
                }
            }
        } catch (Exception exception) {
            logAuth("Login failed before result code", exception);
            code = -1;
        }
        return encodeResult(marker, code);
    }

    public static String getVerifyToken() {
        return verifyToken == null ? "" : verifyToken;
    }

    public static void setAsSuspected(String reason) {
        logAuth("Runtime marked as suspected: " + (reason == null ? "unknown" : reason), null);
        terminateProcess();
    }

    public static Optional<String> getCloudConstant(int id, int index) {
        String property = System.getProperty("phantom-shield-inline.cloud-constant." + id + "." + index);
        if (property != null) {
            return Optional.of(property);
        }
        List<String> defaults = DEFAULT_CONSTANT_POOL.get(id);
        if (defaults != null && index >= 0 && index < defaults.size()) {
            return Optional.of(defaults.get(index));
        }
        byte[] data = CLOUD_CONSTANT_MAP.get(id);
        if (data == null) {
            return Optional.empty();
        }
        return decodeCloudConstant(id, index, data);
    }

    public static Optional<LocalDateTime> getExpiredDate(String role) {
        return isVerifiedSession() ? Optional.ofNullable(EXPIRED_DATE.get(role)) : Optional.empty();
    }

    public static Map<String, LocalDateTime> getExpiredDates() {
        return isVerifiedSession()
                ? Collections.unmodifiableMap(new HashMap<>(EXPIRED_DATE))
                : Collections.emptyMap();
    }

    public static boolean hasRole(String role) {
        LocalDateTime expired = isVerifiedSession() ? EXPIRED_DATE.get(role) : null;
        return expired != null && expired.isAfter(LocalDateTime.now());
    }

    public static boolean isVerifiedSession() {
        return verifyToken != null
                && !verifyToken.isEmpty()
                && lastVerifiedAt > 0L
                && System.currentTimeMillis() - lastVerifiedAt <= VERIFY_GRACE_MILLIS;
    }

    static void processEnvironment() {
        ensureRuntime();
        initializeQQHeadersOnly();
    }

    static Set<String> getAllQQ() {
        initializeQQHeadersOnly();
        Set<String> qqs = new LinkedHashSet<>();
        addConfiguredQQ(qqs);
        return qqs;
    }

    private static void addConfiguredQQ(Set<String> qqs) {
        String configured = System.getProperty("yozakura.auth.qq");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("YOZAKURA_AUTH_QQ");
        }
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getProperty("phantom-shield-inline.qq");
        }
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("PHANTOM_SHIELD_INLINE_QQ");
        }
        if (configured == null) {
            return;
        }
        for (String value : configured.split("[,;\\s]+")) {
            addQQIfValid(qqs, value);
        }
    }

    private static boolean addQQIfValid(Set<String> qqs, String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (!QQ_UIN_PATTERN.matcher(trimmed).matches()) {
            return false;
        }
        qqs.add(trimmed);
        return true;
    }

    static Optional<String> getVerifyTokenOptional() {
        return verifyToken == null ? Optional.empty() : Optional.of(verifyToken);
    }

    static String mapToString(String base, Map<?, ?> map, String prefix) {
        StringBuilder builder = base == null ? new StringBuilder() : new StringBuilder(base);
        if (map != null) {
            boolean first = true;
            for (Object key : map.keySet()) {
                if (first) {
                    if (prefix != null) {
                        builder.append(prefix);
                    }
                    first = false;
                } else {
                    builder.append('&');
                }
                builder.append(key).append('=').append(map.get(key));
            }
        }
        return builder.toString();
    }

    static String get(String url, Map<?, ?> params, Map<String, String> headers) throws IOException {
        return request(mapToString(url, params, "?"), null, headers, "GET", "application/x-www-form-urlencoded");
    }

    static String get(String url, Map<?, ?> params) throws IOException {
        return get(url, params, null);
    }

    static String delete(String url, Map<?, ?> params, Map<String, String> headers) throws IOException {
        return request(mapToString(url, params, "?"), null, headers, "DELETE", "application/x-www-form-urlencoded");
    }

    static String delete(String url, Map<?, ?> params) throws IOException {
        return delete(url, params, null);
    }

    static String post(String url, Map<?, ?> body, Map<String, String> headers) throws IOException {
        return request(url, mapToString(null, body, null), headers, "POST", "application/x-www-form-urlencoded");
    }

    static String post(String url, Map<?, ?> body) throws IOException {
        return post(url, body, null);
    }

    static String put(String url, Map<?, ?> body, Map<String, String> headers) throws IOException {
        return request(url, mapToString(null, body, null), headers, "PUT", "application/x-www-form-urlencoded");
    }

    static String put(String url, Map<?, ?> body) throws IOException {
        return put(url, body, null);
    }

    static String request(String url, String body, Map<String, String> headers, String method, String contentType)
            throws IOException {
        try {
            return requestOnce(url, body, headers, method, contentType);
        } catch (SocketException exception) {
            if (!isRetryableSocketException(exception)) {
                throw exception;
            }
            logAuth("Retrying auth request after socket close: " + url, exception);
            return requestOnce(url, body, headers, method, contentType);
        }
    }

    private static boolean isRetryableSocketException(SocketException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("unexpected end of file");
    }

    private static String requestOnce(String url, String body, Map<String, String> headers, String method, String contentType)
            throws IOException {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        method = method.toUpperCase();
        URL target = requireSecureAuthUrl(url);
        HttpURLConnection connection = (HttpURLConnection) target.openConnection(Proxy.NO_PROXY);
        try {
            connection.setRequestMethod(method);
            connection.setUseCaches(false);
            if ("POST".equals(method) || "PUT".equals(method)) {
                connection.setDoOutput(true);
            }
            connection.setReadTimeout(15000);
            connection.setConnectTimeout(15000);
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("User-Agent", "Mozilla\\5.0 (Windows NT 10.0; Win64; x64)");
            connection.setRequestProperty("Accept-Charset", "utf-8");
            connection.setRequestProperty("Content-Type", contentType);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            if (body != null) {
                connection.setRequestProperty("Content-Length", Integer.toString(body.length()));
                try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(body);
                }
            }
            int responseCode = connection.getResponseCode();
            StringBuilder response = new StringBuilder();
            InputStream responseStream = responseCode >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            if (responseStream == null) {
                throw new IOException("Authentication server returned HTTP " + responseCode + " without a body");
            }
            try (InputStream in = responseStream;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append(System.lineSeparator());
                }
            }
            return response.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static void logAuth(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraAuth.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    public static String getServiceBaseUrl() {
        String configured = System.getProperty(AUTH_BASE_URL_PROPERTY);
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv(AUTH_BASE_URL_ENV);
        }
        if (configured == null || configured.trim().isEmpty()) {
            configured = loadBundledAuthBaseUrl();
        }
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException("Authentication endpoint is not configured. Set "
                    + AUTH_BASE_URL_PROPERTY + " or " + AUTH_BASE_URL_ENV + '.');
        }
        String normalized = configured.trim();
        return normalized.endsWith("/") ? normalized : normalized + '/';
    }

    public static String getClientBuildId() {
        return CLIENT_BUILD_ID;
    }

    public static String getClientFingerprintForNative() {
        return clientFingerprint();
    }

    public static String getMachineFingerprintForNative() {
        return machineFingerprint();
    }

    private static String loadBundledAuthBaseUrl() {
        try (InputStream input = Wrapper.class.getResourceAsStream("/yozakura-auth.properties")) {
            if (input == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("baseUrl");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load bundled authentication endpoint", exception);
        }
    }

    private static String authApi(String path) throws IOException {
        URL base = requireSecureAuthUrl(getServiceBaseUrl());
        return new URL(base, path).toString();
    }

    private static URL requireSecureAuthUrl(String value) throws IOException {
        URL target = new URL(value);
        if (target.getUserInfo() != null) {
            throw new IOException("Authentication URL must not contain user information");
        }
        if ("https".equalsIgnoreCase(target.getProtocol())) {
            return target;
        }
        String host = target.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host);
        if ("http".equalsIgnoreCase(target.getProtocol()) && loopback) {
            return target;
        }
        throw new IOException("Remote authentication requires HTTPS");
    }

    private static void ensureRuntime() {
        disableSystemProxyForLocalAuth();
        initializeQQHeadersOnly();
        if (magicKey == null) {
            magicKey = new byte[16];
            RANDOM.nextBytes(magicKey);
        }
        if (nonce == null) {
            nonce = new byte[12];
            RANDOM.nextBytes(nonce);
        }
        if (key == null) {
            key = new byte[32];
            RANDOM.nextBytes(key);
        }
        if (CLOUD_CONSTANT_MAP.isEmpty()) {
            for (Map.Entry<Integer, List<String>> entry : DEFAULT_CONSTANT_POOL.entrySet()) {
                CLOUD_CONSTANT_MAP.put(entry.getKey(), encodeDefaultCloudConstants(entry.getKey(), entry.getValue()));
            }
        }
    }

    private static void disableSystemProxyForLocalAuth() {
        try {
            System.setProperty("java.net.useSystemProxies", "false");
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
            System.clearProperty("https.proxyHost");
            System.clearProperty("https.proxyPort");
            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");
        } catch (Throwable ignored) {
        }
    }

    private static void parseRoleExpiry(JsonArray roles) {
        EXPIRED_DATE.clear();
        for (int i = 0; i < roles.size(); i++) {
            JsonObject role = roles.get(i).asObject();
            String name = role.getString("rank_name", Integer.toString(i));
            String date = role.getString("expired_date", "1970-1-1T00:00:00");
            EXPIRED_DATE.put(name, parseDate(date));
        }
    }

    private static LocalDateTime parseDate(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ex) {
            return LocalDateTime.of(1970, 1, 1, 0, 0, 0);
        }
    }

    private static Optional<String> decodeCloudConstant(int id, int index, byte[] data) {
        ensureRuntime();
        int seed = 0;
        int current = 0;
        for (int i = 0; i < 16 && i < magicKey.length; i++) {
            current |= magicKey[i] & 0xff;
            if (i % 4 == 3) {
                seed ^= current;
                current = 0;
            } else {
                current <<= 8;
            }
        }
        long counter = (long) seed * (long) id * 13L;
        ChaChaStream crypto = new ChaChaStream(key, nonce, counter);
        int item = 0;
        int pos = 0;
        while (pos + 2 <= data.length) {
            byte[] lenBytes = crypto.apply(new byte[] { data[pos++] });
            int length = lenBytes[0] & 0xff;
            lenBytes = crypto.apply(new byte[] { data[pos++] });
            length = (short) (length + ((lenBytes[0] & 0xff) << 8));
            if (pos + length > data.length || length < 0) {
                return Optional.empty();
            }
            if (item++ == index) {
                byte[] value = new byte[length];
                System.arraycopy(data, pos, value, 0, length);
                return Optional.of(new String(crypto.apply(value), StandardCharsets.UTF_8));
            }
            crypto.skip(length);
            pos += length;
        }
        return Optional.empty();
    }

    private static void scheduleHeartbeat() {
        if (heartbeatExecutor != null) {
            return;
        }
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("PhantomShield-Heartbeat-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(factory);
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                heartbeatLoop();
            } catch (Throwable ignored) {
            }
        }, 4L, 4L, TimeUnit.MINUTES);
    }

    private static void addClientProof(Map<String, String> body) {
        body.put("build", CLIENT_BUILD_ID);
        body.put("fp", clientFingerprint());
        body.put("hw", machineFingerprint());
    }

    private static String machineFingerprint() {
        String cached = machineFingerprint;
        if (cached != null) {
            return cached;
        }
        synchronized (Wrapper.class) {
            if (machineFingerprint != null) {
                return machineFingerprint;
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                List<String> material = new ArrayList<>();
                addMachineValue(material, "os.name", System.getProperty("os.name"));
                addMachineValue(material, "os.arch", System.getProperty("os.arch"));
                addMachineValue(material, "user.name", System.getProperty("user.name"));
                addMachineValue(material, "host", InetAddress.getLocalHost().getHostName());
                addMachineValue(material, "COMPUTERNAME", System.getenv("COMPUTERNAME"));
                addMachineValue(material, "PROCESSOR_IDENTIFIER", System.getenv("PROCESSOR_IDENTIFIER"));
                addMachineValue(material, "PROCESSOR_ARCHITECTURE", System.getenv("PROCESSOR_ARCHITECTURE"));
                addMachineValue(material, "PROCESSOR_LEVEL", System.getenv("PROCESSOR_LEVEL"));
                addMachineValue(material, "PROCESSOR_REVISION", System.getenv("PROCESSOR_REVISION"));
                addMachineValue(material, "NUMBER_OF_PROCESSORS", System.getenv("NUMBER_OF_PROCESSORS"));
                addNetworkInterfaces(material);
                Collections.sort(material);
                digest.update("yozakura-machine-v1".getBytes(StandardCharsets.UTF_8));
                for (String value : material) {
                    digest.update((byte) 0);
                    digest.update(value.getBytes(StandardCharsets.UTF_8));
                }
                machineFingerprint = hexLower(digest.digest());
            } catch (Exception ex) {
                machineFingerprint = "unavailable";
            }
            return machineFingerprint;
        }
    }

    private static void addMachineValue(List<String> material, String key, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            material.add(key + '=' + trimmed.toLowerCase());
        }
    }

    private static void addNetworkInterfaces(List<String> material) {
        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                try {
                    if (networkInterface.isLoopback() || networkInterface.isVirtual()) {
                        continue;
                    }
                    byte[] address = networkInterface.getHardwareAddress();
                    if (address == null || address.length < 6 || isZeroAddress(address)) {
                        continue;
                    }
                    material.add("mac=" + hexLower(address));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isZeroAddress(byte[] address) {
        for (byte value : address) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String clientFingerprint() {
        String cached = clientFingerprint;
        if (cached != null) {
            return cached;
        }
        synchronized (Wrapper.class) {
            if (clientFingerprint != null) {
                return clientFingerprint;
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                updateClassDigest(digest, Wrapper.class);
                updateClassDigest(digest, Inline.class);
                updateClassDigest(digest, Base64Codec.class);
                updateClassDigest(digest, ChaChaStream.class);
                updateClassDigest(digest, "gq.yozakura.auth.YozakuraAuthGate");
                updateClassDigest(digest, "gq.yozakura.core.Client");
                updateClassDigest(digest, "gq.yozakura.core.StandaloneClient");
                updateClassDigest(digest, "gq.yozakura.module.Module");
                updateClassDigest(digest, "gq.yozakura.event.bus.EventManager");
                updateClassDigest(digest, "gq.yozakura.event.api.EventManager");
                updateClassDigest(digest, "gq.yozakura.bridge.YozakuraEventBridge");
                updateClassDigest(digest, "gq.yozakura.bridge.StandaloneEventBridge");
                clientFingerprint = hexLower(digest.digest());
            } catch (Exception ex) {
                clientFingerprint = "unavailable";
            }
            return clientFingerprint;
        }
    }

    private static void updateClassDigest(MessageDigest digest, Class<?> clazz) throws IOException {
        updateClassDigest(digest, clazz.getName());
    }

    private static void updateClassDigest(MessageDigest digest, String className) throws IOException {
        String resource = "/" + className.replace('.', '/') + ".class";
        try (InputStream in = Wrapper.class.getResourceAsStream(resource)) {
            if (in == null) {
                digest.update(className.getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
    }

    private static String hexLower(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                out.append('0');
            }
            out.append(hex);
        }
        return out.toString();
    }

    private static Optional<Byte> initialHeartbeat() {
        ensureRuntime();
        Map<String, String> headers = new HashMap<>();
        if (verifyToken != null) {
            headers.put("verify-token", verifyToken);
        }
        long now = System.currentTimeMillis();
        long sequence = HEARTBEAT_SEQUENCE.incrementAndGet();
        Map<String, String> body = new HashMap<>();
        body.put("client_time", Long.toString(now));
        body.put("sequence", Long.toString(sequence));
        addClientProof(body);

        try {
            String response = post(authApi("api/v2/verify/heartbeat"), body, headers);
            if (response == null) {
                return Optional.empty();
            }
            JsonObject root = Json.parse(response).asObject();
            int code = root.getInt("code", -1);
            if (code != 0) {
                return Optional.of((byte) code);
            }
            JsonObject entity = root.get("entity").asObject();
            long serverTime = entity.getLong("server_time", now);
            long acknowledgedSequence = entity.getLong("sequence", -1L);
            if (Math.abs(now - serverTime) > 90000L || acknowledgedSequence != sequence) {
                return Optional.of((byte) -1);
            }
            absorbHeartbeatData(entity);
            lastVerifiedAt = System.currentTimeMillis();
            return Optional.of((byte) 0);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static void heartbeatLoop() {
        ensureRuntime();
        Map<String, String> headers = new HashMap<>();
        if (verifyToken != null) {
            headers.put("verify-token", verifyToken);
        }

        try {
            long now = System.currentTimeMillis();
            long sequence = HEARTBEAT_SEQUENCE.incrementAndGet();
            Map<String, String> body = new HashMap<>();
            body.put("client_time", Long.toString(now));
            body.put("sequence", Long.toString(sequence));
            addClientProof(body);

            String response = post(authApi("api/v2/verify/heartbeat"), body, headers);
            if (response == null) {
                return;
            }
            JsonObject root = Json.parse(response).asObject();
            byte code = (byte) root.getInt("code", -1);
            if (code != 0) {
                terminateProcess();
                return;
            }

            JsonObject entity = root.get("entity").asObject();
            long serverTime = entity.getLong("server_time", now);
            long acknowledgedSequence = entity.getLong("sequence", -1L);
            if (Math.abs(now - serverTime) > 90000L || acknowledgedSequence != sequence) {
                terminateProcess();
                return;
            }
            absorbHeartbeatData(entity);
            lastVerifiedAt = System.currentTimeMillis();
        } catch (Exception ex) {
            terminateProcess();
        }
    }

    static void buildEnvironmentPacket(Object[] out) {
        if (out == null || out.length == 0) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(bytes);
            int maxLength = 127;
            int used = 6;
            int randomPrefix = ThreadLocalRandom.current().nextInt(4, 9);
            used += randomPrefix;

            data.writeByte(255);
            data.writeByte(4);
            for (int i = 0; i < randomPrefix; i++) {
                data.write(ThreadLocalRandom.current().nextInt(1, 256));
            }
            data.write(0);

            byte[] host = InetAddress.getLocalHost().getHostName().getBytes(StandardCharsets.UTF_8);
            used += 3 + host.length;
            if (used <= maxLength) {
                data.writeByte(1);
                data.writeShort(host.length & 0xffff);
                data.write(host, 0, host.length);
            }

            byte[] user = userHomeAndName().getBytes(StandardCharsets.UTF_8);
            used += 3 + user.length;
            if (used <= maxLength) {
                data.writeByte(2);
                data.writeShort(user.length & 0xffff);
                data.write(user, 0, user.length);
            }

            data.writeByte(3);
            byte[] fileBytes = environmentFileBytes();
            used += 2 + fileBytes.length;
            if (used <= maxLength) {
                data.write(fileBytes.length & 0xff);
                data.write(fileBytes, 0, fileBytes.length);
            }

            data.writeByte(0);
            int padding = maxLength - used;
            for (int i = 0; i < padding; i++) {
                data.write(ThreadLocalRandom.current().nextInt(1, 256));
            }
            data.writeByte(4);
            data.writeByte(255);

            out[out.length - 1] = encodeEnvironmentBytes(bytes.toByteArray());
        } catch (Exception ignored) {
        }
    }

    static void verifyEnvironmentPacket(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        try {
            Object encodedObject = args[args.length - 1];
            byte[] encoded = decodeEnvironmentHex(String.valueOf(encodedObject));
            byte[] decoded = xorEnvironment(encoded);
            if (decoded.length < 4
                    || decoded[0] != ENVIRONMENT_MARKER[0]
                    || decoded[1] != ENVIRONMENT_MARKER[1]
                    || decoded[decoded.length - 2] != ENVIRONMENT_MARKER[1]
                    || decoded[decoded.length - 1] != ENVIRONMENT_MARKER[0]) {
                args[0] = 1157442765409226768L;
                return;
            }

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(decoded, 2, decoded.length - 4));
            byte tag;
            do {
                tag = in.readByte();
            } while (tag != 0);

            int matches = 0;
            while ((tag = in.readByte()) != 0) {
                if (tag == 1) {
                    byte[] value = readShortBytes(in);
                    if (Arrays.equals(InetAddress.getLocalHost().getHostName().getBytes(StandardCharsets.UTF_8), value)) {
                        matches++;
                    }
                } else if (tag == 2) {
                    byte[] value = readShortBytes(in);
                    if (Arrays.equals(userHomeAndName().getBytes(StandardCharsets.UTF_8), value)) {
                        matches++;
                    }
                } else if (tag == 3) {
                    int length = in.readUnsignedByte();
                    byte[] value = new byte[length];
                    in.readFully(value);
                    if (Arrays.equals(environmentFileBytes(), value)) {
                        matches++;
                    }
                }
            }

            if (matches >= 3) {
                long result = Math.abs(ThreadLocalRandom.current().nextInt());
                if (args.length >= 2 && args[args.length - 2] instanceof Integer) {
                    result += (-4294967296L & ((Integer) args[args.length - 2] ^ 1L) << 32);
                }
                args[0] = result;
            }
        } catch (Exception ex) {
            args[0] = ThreadLocalRandom.current().nextInt();
        }
    }

    private static int encodeResult(int marker, int code) {
        return (marker & 0xffff00ff) | ((code & 0xff) << 8);
    }

    private static void terminateProcess() {
        System.exit(0);
    }

    private static void initializeQQHeadersOnly() {
        if (!HEADERS.isEmpty()) {
            return;
        }
        synchronized (HEADERS) {
            if (!HEADERS.isEmpty()) {
                return;
            }
            HEADERS.put("Accept", "application/json, text/plain, */*");
            HEADERS.put("Accept-Charset", "utf-8");
            HEADERS.put("Cache-Control", "no-cache");
        }
    }

    private static void absorbHeartbeatData(JsonObject data) {
        JsonValue magic = data.get("m");
        if (magic != null) {
            try {
                magicKey = Base64Codec.decode(magic.asString());
            } catch (RuntimeException ignored) {
            }
        }

        JsonValue roles = data.get("roles");
        if (roles != null) {
            try {
                parseRoleExpiry(roles.asArray());
            } catch (RuntimeException ignored) {
            }
        }
        JsonValue constants = data.get("c");
        if (constants != null) {
            try {
                if (constants.isArray()) {
                    JsonArray array = constants.asArray();
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject item = array.get(i).asObject();
                        int id = Integer.parseInt(item.getString("h", "-1"));
                        CLOUD_CONSTANT_MAP.put(Integer.valueOf(id), Base64Codec.decode(item.getString("e", "==")));
                    }
                } else if (constants.isObject()) {
                    JsonObject object = constants.asObject();
                    for (gq.yozakura.auth.vendor.skidonion.sWdSl.JsonMember member : object) {
                        CLOUD_CONSTANT_MAP.put(Integer.valueOf(Integer.parseInt(member.getName())), Base64Codec.decode(member.getValue().asString()));
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        JsonValue newKey = data.get("k");
        if (newKey != null && newKey.isString()) {
            try {
                byte[] decoded = Base64Codec.decode(newKey.asString());
                if (decoded.length >= 32) {
                    key = Arrays.copyOf(decoded, 32);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static byte[] encodeDefaultCloudConstants(int id, List<String> values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int seed = magicSeed();
        ChaChaStream crypto = new ChaChaStream(key, nonce, (long) seed * (long) id * 13L);
        try {
            for (String value : values) {
                byte[] raw = value.getBytes(StandardCharsets.UTF_8);
                byte[] length = crypto.apply(new byte[] { (byte) raw.length, (byte) (raw.length >>> 8) });
                out.write(length);
                out.write(crypto.apply(raw));
            }
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }

    private static int magicSeed() {
        int seed = 0;
        int current = 0;
        for (int i = 0; i < 16 && i < magicKey.length; i++) {
            current |= magicKey[i] & 0xff;
            if (i % 4 == 3) {
                seed ^= current;
                current = 0;
            } else {
                current <<= 8;
            }
        }
        return seed;
    }

    private static String userHomeAndName() {
        return String.join("-", System.getProperty("user.home"), System.getProperty("user.name"));
    }

    private static byte[] environmentFileBytes() throws IOException {
        Path path = environmentPath();
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        Files.write(path, bytes);
        return bytes;
    }

    private static Path environmentPath() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder builder = new StringBuilder(".");
        java.util.Random seeded = new java.util.Random(System.getProperty("user.home").hashCode());
        for (int i = 0; i < 16; i++) {
            builder.append(alphabet.charAt(seeded.nextInt(alphabet.length())));
        }
        return Paths.get(System.getProperty("user.home"), builder.toString());
    }

    private static byte[] readShortBytes(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] value = new byte[length];
        in.readFully(value);
        return value;
    }

    private static String encodeEnvironmentBytes(byte[] input) {
        byte[] xored = xorEnvironment(input);
        StringBuilder builder = new StringBuilder();
        for (byte value : xored) {
            String hex = Integer.toHexString((value & 0xff) | 0x100);
            builder.append(hex, 1, 3);
        }
        return builder.toString();
    }

    private static byte[] decodeEnvironmentHex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < text.length(); i += 2) {
            int hi = Character.digit(text.charAt(i), 16);
            int lo = Character.digit(text.charAt(i + 1), 16);
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private static byte[] xorEnvironment(byte[] input) {
        byte[] out = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = (byte) (input[i] ^ ENVIRONMENT_XOR_KEY[i % ENVIRONMENT_XOR_KEY.length]);
        }
        return out;
    }

    @SuppressWarnings("unused")
    private static byte[] toByteArray(CharSequence chars, int start, int end) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            for (int i = start; i < end; i++) {
                data.writeByte(chars.charAt(i));
            }
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }
}

