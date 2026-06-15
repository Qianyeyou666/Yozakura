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
import java.math.BigInteger;
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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import gq.yozakura.auth.vendor.skidonion.sWdSl.JsonObject;
import gq.yozakura.auth.vendor.skidonion.sWdSl.JsonValue;
import gq.yozakura.auth.vendor.skidonion.sWdSl.Base64Codec;
import gq.yozakura.auth.vendor.skidonion.sWdSl.JsonArray;
import gq.yozakura.auth.vendor.skidonion.sWdSl.Json;
import gq.yozakura.auth.vendor.skidonion.sWdSl.FormUrlEncoder;
import gq.yozakura.auth.vendor.skidonion.sWdSl.ChaChaStream;

public class Wrapper {
    private static final String BASE_URL = "http://49.235.166.227:8080/";
    private static final String HEARTBEAT_API = BASE_URL + "api/v2/verify/heartbeat";
    private static final String LOGIN_API = BASE_URL + "api/v2/verify/login";
    private static final String CLIENT_BUILD_ID = "local-20260615-anti-patch-1";
    private static final long VERIFY_GRACE_MILLIS = TimeUnit.MINUTES.toMillis(6L);
    private static final byte[] ENVIRONMENT_XOR_KEY =
            new byte[] { 82, -7, -93, -53, -113, 107, -127, 8 };
    private static final byte[] ENVIRONMENT_MARKER = new byte[] { -1, 4 };
    private static final BigInteger CURVE_P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger EDWARDS_D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(CURVE_P)).mod(CURVE_P);
    private static final BigInteger EDWARDS_SQRT_M1 =
            BigInteger.valueOf(2).modPow(CURVE_P.subtract(BigInteger.ONE).shiftRight(2), CURVE_P);
    private static final BigInteger ED25519_L = BigInteger.ONE.shiftLeft(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));
    private static final BigInteger EDWARDS_BASE_X = new BigInteger(
            "15112221349535400772501151409588531511454012693041857206046113283949847762202");
    private static final BigInteger EDWARDS_BASE_Y = new BigInteger(
            "46316835694926478169428394003475163141307993866256225615783033603165251855960");
    private static final byte[] LOGIN_SERVER_PUBLIC_KEY = new byte[] {
            -102, -110, -78, -103, -1, -80, 115, 48,
            71, 94, 5, -58, -126, -117, 61, -99,
            123, -1, 6, 6, 105, 41, -99, -73,
            -4, 16, -100, 89, 120, 51, 62, -70
    };
    private static final Map<Integer, List<String>> DEFAULT_CONSTANT_POOL = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<Integer, byte[]> CLOUD_CONSTANT_MAP = new HashMap<>();
    private static final Map<String, LocalDateTime> EXPIRED_DATE = new HashMap<>();
    private static final Map<String, String> HEADERS = new HashMap<>();
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final Pattern QQ_UIN_PATTERN = Pattern.compile("^[1-9][0-9]{4,10}$");
    private static volatile ScheduledExecutorService heartbeatExecutor;
    private static volatile long userId;
    private static volatile String username;
    private static volatile String nickname;
    private static volatile String verifyToken;
    private static volatile byte[] magicKey;
    private static volatile byte[] key;
    private static volatile byte[] nonce;
    private static volatile ChaChaStream sessionCrypto;
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
        EXPIRED_DATE.clear();
        Map<String, String> verifyHeaders = new HashMap<>();
        if (verifyToken != null) {
            verifyHeaders.put("verify-token", verifyToken);
        }

        try {
            LoginHandshake handshake = createLoginHandshake();
            Map<String, String> body = new HashMap<>();
            body.put("username", FormUrlEncoder.encode(username));
            body.put("password", FormUrlEncoder.encode(password));
            body.put("software_id", Long.toString(183L));
            body.put("s", FormUrlEncoder.encode(Base64Codec.encodeToString(handshake.publicKey)));
            body.put("e", FormUrlEncoder.encode(Boolean.toString(remember)));
            body.put("build", FormUrlEncoder.encode(CLIENT_BUILD_ID));
            body.put("fp", clientFingerprint());
            body.put("hw", machineFingerprint());

            String response = post(LOGIN_API, body, verifyHeaders);
            if (response != null) {
                JsonObject root = Json.parse(response).asObject();
                code = (byte) root.getInt("code", -1);
                if (code == 0) {
                    JsonObject entity = root.get("entity").asObject();
                    JsonObject data = entity.get("data").asObject();
                    userId = data.getLong("uid", -1L);
                    Wrapper.username = username;
                    verifyToken = data.getString("jwt", "");
                    nickname = data.getString("nickname", "");
                    configureLoginCrypto(data, entity.getString("signature", ""), handshake);
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
        ensureRuntime();
        Map<String, String> headers = new HashMap<>();
        if (verifyToken != null) {
            headers.put("verify-token", verifyToken);
        }
        try {
            JsonObject payload = Json.object();
            payload.addLong("t", System.currentTimeMillis());
            payload.add("-", JsonValue.NULL);
            payload.addString("r", reason == null ? "unknown" : reason);

            Map<String, String> body = new HashMap<>();
            body.put("data", FormUrlEncoder.encode(encryptPayload(payload)));
            post(HEARTBEAT_API, body, headers);
        } catch (Exception ignored) {
        } finally {
            terminateProcess();
        }
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
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
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
            if (responseCode >= 300) {
                throw new RuntimeException("HTTP Request is not success, Response code is " + responseCode);
            }
            StringBuilder response = new StringBuilder();
            try (InputStream in = connection.getInputStream();
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
        if (sessionCrypto == null && key != null && nonce != null) {
            sessionCrypto = new ChaChaStream(key, nonce, 0L);
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

    private static void addClientProof(JsonObject payload) {
        payload.addString("cb", CLIENT_BUILD_ID);
        payload.addString("cf", clientFingerprint());
        payload.addString("hw", machineFingerprint());
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
        Object[] environment = new Object[] { null, RANDOM.nextInt(), null, null };
        buildEnvironmentPacket(environment);

        JsonObject payload = Json.object();
        payload.addLong("t", now);
        payload.add("+", JsonValue.NULL);
        payload.add("q", Json.arrayOfStrings(getAllQQ().toArray(new String[0])));
        payload.addString("v", "1");
        payload.addString("h", String.valueOf(environment[environment.length - 1]));
        addClientProof(payload);

        Map<String, String> body = new HashMap<>();
        body.put("data", FormUrlEncoder.encode(encryptPayload(payload)));

        try {
            String response = post(HEARTBEAT_API, body, headers);
            if (response == null) {
                return Optional.empty();
            }
            JsonObject root = Json.parse(response).asObject();
            int code = root.getInt("code", -1);
            if (code != 0) {
                return Optional.of((byte) code);
            }
            JsonObject entity = root.get("entity").asObject();
            String encrypted = entity.getString("data", "==");
            String signature = entity.getString("signature", "");
            byte[] plain = decryptPayload(encrypted);

            JsonObject data = Json.parse(new String(plain, StandardCharsets.UTF_8)).asObject();
            long serverTime = data.getLong("t", now);
            if (Math.abs(now - serverTime) > 60000L) {
                return Optional.of((byte) -1);
            }

            int verificationSeed = RANDOM.nextInt();
            Object[] verifyArgs = new Object[] {
                    RANDOM.nextInt(),
                    RANDOM.nextInt(),
                    RANDOM.nextInt(),
                    verificationSeed,
                    data.getString("h", "==")
            };
            verifyEnvironmentPacket(verifyArgs);
            if (!(verifyArgs[0] instanceof Number)) {
                return Optional.of((byte) -2);
            }
            long proof = ((Number) verifyArgs[0]).longValue();
            if ((((proof >> 32) ^ verificationSeed) & 1L) != 1L) {
                return Optional.of((byte) -2);
            }
            if (signature.isEmpty()
                    || !verifyEd25519Signature(signature, data.toString().getBytes(StandardCharsets.UTF_8))) {
                return Optional.of((byte) -3);
            }
            absorbHeartbeatData(data);
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
            JsonObject payload = Json.object();
            payload.addLong("t", System.currentTimeMillis());
            payload.add("-", JsonValue.NULL);
            addClientProof(payload);

            Map<String, String> body = new HashMap<>();
            body.put("data", FormUrlEncoder.encode(encryptPayload(payload)));

            String response = post(HEARTBEAT_API, body, headers);
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
            String encrypted = entity.getString("data", "==");
            String signature = entity.getString("signature", "");
            JsonObject data = Json.parse(new String(decryptPayload(encrypted), StandardCharsets.UTF_8)).asObject();
            if (signature.isEmpty()
                    || !verifyEd25519Signature(signature, data.toString().getBytes(StandardCharsets.UTF_8))) {
                terminateProcess();
                return;
            }
            if (data.get("b") != null) {
                terminateProcess();
            }
            absorbHeartbeatData(data);
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

    private static String encryptPayload(JsonObject payload) {
        ensureRuntime();
        synchronized (Wrapper.class) {
            return Base64Codec.encodeToString(sessionCrypto.apply(payload.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static byte[] decryptPayload(String encrypted) {
        ensureRuntime();
        synchronized (Wrapper.class) {
            return sessionCrypto.apply(Base64Codec.decode(encrypted));
        }
    }

    private static LoginHandshake createLoginHandshake() {
        byte[] seed = new byte[32];
        RANDOM.nextBytes(seed);
        byte[] privateScalar = deriveEd25519PrivateScalar(seed);
        byte[] publicKey = ed25519PublicKey(privateScalar);
        return new LoginHandshake(privateScalar, publicKey);
    }

    private static byte[] deriveEd25519PrivateScalar(byte[] seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(seed);
            byte[] scalar = Arrays.copyOf(hash, 32);
            scalar[0] &= (byte) 248;
            scalar[31] &= (byte) 63;
            scalar[31] |= (byte) 64;
            return scalar;
        } catch (Exception ex) {
            return Arrays.copyOf(seed, seed.length);
        }
    }

    private static byte[] ed25519PublicKey(byte[] privateScalar) {
        EdwardsPoint point = edwardsScalarMultiply(privateScalar, new EdwardsPoint(EDWARDS_BASE_X, EDWARDS_BASE_Y));
        return encodeEdwardsPoint(point);
    }

    private static void configureLoginCrypto(JsonObject data, String signatureText, LoginHandshake handshake) {
        try {
            byte[] encodedNonce = Base64Codec.decode(data.getString("n", "=="));
            if (encodedNonce != null && encodedNonce.length == 48) {
                nonce = decodeLoginNonce(encodedNonce);
            }
        } catch (RuntimeException ignored) {
        }

        try {
            byte[] signatureBytes = signatureText == null || signatureText.isEmpty()
                    ? new byte[0]
                    : Base64Codec.decode(signatureText);
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] sharedSecret = ed25519KeyExchange(LOGIN_SERVER_PUBLIC_KEY, handshake.privateScalar);
            byte[] fullDigest = digest.digest(sharedSecret);
            key = Arrays.copyOfRange(fullDigest, 12, 44);
            sessionCrypto = new ChaChaStream(key, nonce, 0L);
        } catch (Exception ignored) {
        }
    }

    private static byte[] ed25519KeyExchange(byte[] publicKey, byte[] privateScalar) {
        byte[] yBytes = Arrays.copyOf(publicKey, 32);
        yBytes[31] &= (byte) 0x7f;
        BigInteger y = fromLittleEndian(yBytes);
        BigInteger u = y.add(BigInteger.ONE)
                .multiply(BigInteger.ONE.subtract(y).mod(CURVE_P).modInverse(CURVE_P))
                .mod(CURVE_P);
        BigInteger shared = x25519(privateScalar, u);
        return toLittleEndian(shared, 32);
    }

    private static BigInteger x25519(byte[] scalarBytes, BigInteger u) {
        BigInteger scalar = fromLittleEndian(scalarBytes);
        BigInteger x1 = u;
        BigInteger x2 = BigInteger.ONE;
        BigInteger z2 = BigInteger.ZERO;
        BigInteger x3 = u;
        BigInteger z3 = BigInteger.ONE;
        int swap = 0;
        for (int pos = 254; pos >= 0; pos--) {
            int bit = scalar.testBit(pos) ? 1 : 0;
            swap ^= bit;
            if (swap != 0) {
                BigInteger tmp = x2; x2 = x3; x3 = tmp;
                tmp = z2; z2 = z3; z3 = tmp;
            }
            swap = bit;

            BigInteger a = x2.add(z2).mod(CURVE_P);
            BigInteger aa = a.multiply(a).mod(CURVE_P);
            BigInteger b = x2.subtract(z2).mod(CURVE_P);
            BigInteger bb = b.multiply(b).mod(CURVE_P);
            BigInteger e = aa.subtract(bb).mod(CURVE_P);
            BigInteger c = x3.add(z3).mod(CURVE_P);
            BigInteger d = x3.subtract(z3).mod(CURVE_P);
            BigInteger da = d.multiply(a).mod(CURVE_P);
            BigInteger cb = c.multiply(b).mod(CURVE_P);
            x3 = da.add(cb).mod(CURVE_P).pow(2).mod(CURVE_P);
            z3 = x1.multiply(da.subtract(cb).mod(CURVE_P).pow(2).mod(CURVE_P)).mod(CURVE_P);
            x2 = aa.multiply(bb).mod(CURVE_P);
            z2 = e.multiply(bb.add(BigInteger.valueOf(121666).multiply(e)).mod(CURVE_P)).mod(CURVE_P);
        }
        if (swap != 0) {
            BigInteger tmp = x2; x2 = x3; x3 = tmp;
            tmp = z2; z2 = z3; z3 = tmp;
        }
        return x2.multiply(z2.modInverse(CURVE_P)).mod(CURVE_P);
    }

    private static EdwardsPoint edwardsScalarMultiply(byte[] scalarBytes, EdwardsPoint base) {
        return edwardsScalarMultiply(fromLittleEndian(scalarBytes), base);
    }

    private static EdwardsPoint edwardsScalarMultiply(BigInteger scalar, EdwardsPoint base) {
        EdwardsPoint result = new EdwardsPoint(BigInteger.ZERO, BigInteger.ONE);
        EdwardsPoint addend = base;
        for (int i = 0; i < 256; i++) {
            if (scalar.testBit(i)) {
                result = edwardsAdd(result, addend);
            }
            addend = edwardsAdd(addend, addend);
        }
        return result;
    }

    private static EdwardsPoint edwardsAdd(EdwardsPoint p, EdwardsPoint q) {
        BigInteger x1x2 = p.x.multiply(q.x).mod(CURVE_P);
        BigInteger y1y2 = p.y.multiply(q.y).mod(CURVE_P);
        BigInteger dxxyy = EDWARDS_D.multiply(x1x2).multiply(y1y2).mod(CURVE_P);
        BigInteger x = p.x.multiply(q.y).add(q.x.multiply(p.y)).mod(CURVE_P)
                .multiply(BigInteger.ONE.add(dxxyy).mod(CURVE_P).modInverse(CURVE_P))
                .mod(CURVE_P);
        BigInteger y = y1y2.add(x1x2).mod(CURVE_P)
                .multiply(BigInteger.ONE.subtract(dxxyy).mod(CURVE_P).modInverse(CURVE_P))
                .mod(CURVE_P);
        return new EdwardsPoint(x, y);
    }

    private static EdwardsPoint edwardsNegate(EdwardsPoint point) {
        return new EdwardsPoint(CURVE_P.subtract(point.x), point.y);
    }

    private static EdwardsPoint decodeEdwardsPoint(byte[] encoded) {
        if (encoded == null || encoded.length != 32) {
            return null;
        }
        byte[] yBytes = Arrays.copyOf(encoded, 32);
        boolean xOdd = (yBytes[31] & 0x80) != 0;
        yBytes[31] &= (byte) 0x7f;
        BigInteger y = fromLittleEndian(yBytes);
        if (y.compareTo(CURVE_P) >= 0) {
            return null;
        }

        BigInteger y2 = y.multiply(y).mod(CURVE_P);
        BigInteger numerator = y2.subtract(BigInteger.ONE).mod(CURVE_P);
        BigInteger denominator = EDWARDS_D.multiply(y2).add(BigInteger.ONE).mod(CURVE_P);
        BigInteger x2 = numerator.multiply(denominator.modInverse(CURVE_P)).mod(CURVE_P);
        BigInteger x = x2.modPow(CURVE_P.add(BigInteger.valueOf(3)).shiftRight(3), CURVE_P);
        if (!x.multiply(x).subtract(x2).mod(CURVE_P).equals(BigInteger.ZERO)) {
            x = x.multiply(EDWARDS_SQRT_M1).mod(CURVE_P);
        }
        if (!x.multiply(x).subtract(x2).mod(CURVE_P).equals(BigInteger.ZERO)) {
            return null;
        }
        if (x.testBit(0) != xOdd) {
            x = CURVE_P.subtract(x).mod(CURVE_P);
        }
        return new EdwardsPoint(x, y);
    }

    private static byte[] encodeEdwardsPoint(EdwardsPoint point) {
        byte[] encoded = toLittleEndian(point.y, 32);
        if (point.x.testBit(0)) {
            encoded[31] |= (byte) 0x80;
        }
        return encoded;
    }

    private static BigInteger fromLittleEndian(byte[] bytes) {
        byte[] bigEndian = new byte[bytes.length + 1];
        for (int i = 0; i < bytes.length; i++) {
            bigEndian[bigEndian.length - 1 - i] = bytes[i];
        }
        return new BigInteger(bigEndian);
    }

    private static byte[] toLittleEndian(BigInteger value, int length) {
        byte[] bigEndian = value.mod(CURVE_P).toByteArray();
        byte[] littleEndian = new byte[length];
        for (int i = 0; i < length; i++) {
            int source = bigEndian.length - 1 - i;
            littleEndian[i] = source >= 0 ? bigEndian[source] : 0;
        }
        return littleEndian;
    }

    private static byte[] decodeLoginNonce(byte[] encoded) {
        byte[] out = new byte[12];
        for (int i = 0; i < 12; i++) {
            int pos = i * 4;
            int value = (encoded[pos] & 0xff)
                    | ((encoded[pos + 1] & 0xff) << 8)
                    | ((encoded[pos + 2] & 0xff) << 16)
                    | ((encoded[pos + 3] & 0xff) << 24);
            value -= 0x3a812d31;
            value = Integer.rotateRight(value, 11);
            value = Integer.rotateRight(value, 4);
            value ^= 0xc6285b6a;
            value ^= 0xe60db52e;
            value ^= 0xfe2a55f6;
            value += 0xc01f77ed;
            value = ~value;
            value += 0xaf03b7be;
            value = ~value;
            value = Integer.rotateLeft(value, 23);
            value ^= 0xa470ff42;
            value = ~value;
            value += 0x9d1f8fa8;
            value += 0xcb008166;
            value = Integer.rotateRight(value, 25);
            out[i] = (byte) value;
        }
        return out;
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
                    sessionCrypto = new ChaChaStream(key, nonce, 0L);
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

    private static boolean verifyEd25519Signature(String signatureText, byte[] message) {
        try {
            byte[] signatureBytes = Base64Codec.decode(signatureText);
            if (signatureBytes == null || signatureBytes.length != 64 || (signatureBytes[63] & 0xe0) != 0) {
                return false;
            }
            EdwardsPoint publicKey = decodeEdwardsPoint(LOGIN_SERVER_PUBLIC_KEY);
            if (publicKey == null) {
                return false;
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            digest.update(signatureBytes, 0, 32);
            digest.update(LOGIN_SERVER_PUBLIC_KEY);
            digest.update(message);
            BigInteger h = fromLittleEndian(digest.digest()).mod(ED25519_L);
            BigInteger s = fromLittleEndian(Arrays.copyOfRange(signatureBytes, 32, 64));

            EdwardsPoint sBase = edwardsScalarMultiply(s, new EdwardsPoint(EDWARDS_BASE_X, EDWARDS_BASE_Y));
            EdwardsPoint hPublic = edwardsScalarMultiply(h, publicKey);
            byte[] checker = encodeEdwardsPoint(edwardsAdd(sBase, edwardsNegate(hPublic)));
            return Arrays.equals(Arrays.copyOf(signatureBytes, 32), checker);
        } catch (Exception ex) {
            return false;
        }
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

    private static final class LoginHandshake {
        final byte[] privateScalar;
        final byte[] publicKey;

        LoginHandshake(byte[] privateScalar, byte[] publicKey) {
            this.privateScalar = privateScalar;
            this.publicKey = publicKey;
        }
    }

    private static final class EdwardsPoint {
        final BigInteger x;
        final BigInteger y;

        EdwardsPoint(BigInteger x, BigInteger y) {
            this.x = x.mod(CURVE_P);
            this.y = y.mod(CURVE_P);
        }
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

