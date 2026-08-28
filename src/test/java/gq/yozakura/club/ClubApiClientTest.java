package gq.yozakura.club;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClubApiClientTest {
    @Test
    public void releaseDefaultUsesThePublicWebsiteApiOverHttps() {
        assertEquals("https://yozakura.wtf", ClubApiClient.DEFAULT_BASE_URL);
        Proxy proxy = ClubApiClient.proxyFor(
                "https://yozakura.wtf/api/config-hall",
                "http://127.0.0.1:7890/", "");
        assertEquals(Proxy.Type.HTTP, proxy.type());
        assertEquals("/127.0.0.1:7890", proxy.address().toString());
    }

    @Test
    public void explicitLoopbackDevelopmentServerBypassesEnvironmentProxy() {
        Proxy proxy = ClubApiClient.proxyFor(
                "http://127.0.0.1:4173/api/config-hall",
                "http://127.0.0.1:7890/", "localhost,127.0.0.1,::1");
        assertEquals(Proxy.NO_PROXY, proxy);
    }

    private HttpServer server;
    private ClubApiClient client;
    private RecordingHandler handler;

    @Before
    public void startServer() throws Exception {
        handler = new RecordingHandler();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        client = new ClubApiClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void loginPostsCredentialsAndParsesSession() throws Exception {
        handler.response = "{\"token\":\"club-token\",\"user\":{\"id\":\"u1\",\"username\":\"Sakura\",\"createdAt\":\"now\"}}";

        ClubAuthResult result = client.login("Sakura", "correct horse battery");

        assertEquals("POST", handler.method);
        assertEquals("/api/auth/login", handler.path);
        assertTrue(handler.body.contains("\"username\":\"Sakura\""));
        assertTrue(handler.body.contains("\"password\":\"correct horse battery\""));
        assertEquals("club-token", result.getToken());
        assertEquals("Sakura", result.getUser().getUsername());
    }

    @Test
    public void verifiedClientExchangeUsesNativeProofInsteadOfUsername() throws Exception {
        handler.response = "{\"token\":\"club-token\",\"user\":{\"id\":\"u1\",\"username\":\"Sakura\",\"createdAt\":\"now\"}}";

        ClubAuthResult result = client.exchangeVerifiedClient("native-proof");

        assertEquals("POST", handler.method);
        assertEquals("/api/auth/client-exchange", handler.path);
        assertEquals("PoP native-proof", handler.authorization);
        assertTrue(handler.body.isEmpty());
        assertEquals("club-token", result.getToken());
        assertEquals("Sakura", result.getUser().getUsername());
    }

    @Test
    public void hallUploadUsesBearerTokenAndPublicVisibility() throws Exception {
        handler.response = "{\"config\":{\"id\":\"c1\",\"name\":\"legit\",\"visibility\":\"public\",\"createdAt\":\"now\",\"updatedAt\":\"now\"}}";
        JsonObject snapshot = new JsonObject();
        JsonObject module = new JsonObject();
        module.addProperty("state", true);
        snapshot.add("Reach", module);

        ClubConfigSummary saved = client.saveHallConfig("club-token", "legit", snapshot);

        assertEquals("Bearer club-token", handler.authorization);
        assertEquals("/api/configs", handler.path);
        assertTrue(handler.body.contains("\"name\":\"legit\""));
        assertTrue(handler.body.contains("\"visibility\":\"public\""));
        assertTrue(handler.body.contains("\"payload\":{\"Reach\":{\"state\":true}}"));
        assertEquals("legit", saved.getName());
    }

    @Test
    public void hallListAndDownloadAreAnonymousReadOnlyRequests() throws Exception {
        handler.response = "{\"configs\":[{\"id\":\"c1\",\"name\":\"first\",\"visibility\":\"public\",\"createdAt\":\"a\",\"updatedAt\":\"b\"}]}";

        List<ClubConfigSummary> configs = client.listHallConfigs();

        assertEquals("GET", handler.method);
        assertEquals("/api/config-hall", handler.path);
        assertEquals(null, handler.authorization);
        assertEquals(1, configs.size());

        handler.response = "{\"config\":{\"id\":\"c1\",\"name\":\"first\",\"visibility\":\"public\",\"owner\":\"Sakura\",\"createdAt\":\"a\",\"updatedAt\":\"b\",\"payload\":{}}}";
        ClubConfig downloaded = client.getHallConfig("c1");
        assertEquals("/api/config-hall/c1", handler.path);
        assertEquals(null, handler.authorization);
        assertEquals("Sakura", downloaded.getSummary().getOwner());
    }

    @Test
    public void deleteConfigUsesAuthenticatedOwnerScopedEndpoint() throws Exception {
        handler.response = "{\"ok\":true}";

        client.deleteConfig("club-token", "config id/with spaces");

        assertEquals("DELETE", handler.method);
        assertEquals("/api/configs/config%20id%2Fwith%20spaces", handler.rawPath);
        assertEquals("Bearer club-token", handler.authorization);
    }

    @Test
    public void listConfigsParsesSummaries() throws Exception {
        handler.response = "{\"configs\":[{\"id\":\"c1\",\"name\":\"first\",\"visibility\":\"private\",\"createdAt\":\"a\",\"updatedAt\":\"b\"}]}";

        List<ClubConfigSummary> configs = client.listConfigs("club-token");

        assertEquals("GET", handler.method);
        assertEquals("Bearer club-token", handler.authorization);
        assertEquals(1, configs.size());
        assertEquals("first", configs.get(0).getName());
    }

    @Test
    public void downloadedConfigRejectsNonObjectPayload() throws Exception {
        handler.response = "{\"config\":{\"id\":\"c1\",\"name\":\"bad\",\"visibility\":\"private\",\"createdAt\":\"a\",\"updatedAt\":\"b\",\"payload\":[]}}";

        try {
            client.getConfig("club-token", "c1");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("JSON object"));
            return;
        }
        throw new AssertionError("Expected non-object cloud config to fail");
    }

    private static final class RecordingHandler implements HttpHandler {
        private String response = "{}";
        private String method;
        private String path;
        private String rawPath;
        private String body;
        private String authorization;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            method = exchange.getRequestMethod();
            path = exchange.getRequestURI().getPath();
            rawPath = exchange.getRequestURI().getRawPath();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            body = read(exchange.getRequestBody());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream output = exchange.getResponseBody();
            try {
                output.write(bytes);
            } finally {
                output.close();
            }
        }

        private static String read(InputStream input) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
