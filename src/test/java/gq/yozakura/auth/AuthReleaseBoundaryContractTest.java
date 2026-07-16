package gq.yozakura.auth;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthReleaseBoundaryContractTest {
    @Test
    public void releaseBuildKeepsAuthenticationEnabled() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/Client.java");
        String gate = source("src/main/java/gq/yozakura/auth/YozakuraAuthGate.java");
        String bridgeDebug = source("src/main/java/gq/yozakura/manager/BridgeDebug.java");

        assertFalse("The client must not expose a patchable DebugMode switch",
                client.contains("DebugMode"));
        assertFalse("The authentication gate must not contain a debug switch",
                gate.contains("Client.DebugMode"));
        assertFalse("The authentication gate must not contain a debug identity bypass",
                gate.contains("return \"DebugUser\";"));
        assertFalse("Bridge diagnostics must not depend on a client debug switch",
                bridgeDebug.contains("Client.DebugMode"));
    }

    @Test
    public void everyRuntimeEntryPointAuthenticatesBeforeTakingOwnership() throws IOException {
        String legacy = source("src/main/java/gq/yozakura/core/Client.java");
        String standalone = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        String modern = source("src/main/java/gq/yozakura/core/ModernForgeClient.java");

        assertBefore(legacy, "YozakuraAuthGate.verifyOrThrow(\"forge\");", "state = true;");
        assertBefore(standalone, "YozakuraAuthGate.verifyOrThrow(\"standalone\");", "state = true;");
        assertBefore(modern, "YozakuraAuthGate.verifyOrThrow(\"modern-forge\");", "state = true;");
    }

    @Test
    public void remoteAuthenticationRequiresHttps() throws IOException {
        String wrapper = source("src/main/java/gq/yozakura/auth/vendor/tech/skidonion/obfuscator/inline/Wrapper.java");

        assertFalse("The client must not embed the retired plaintext production endpoint",
                wrapper.contains("http://49.235.166.227:8080/"));
        assertTrue("Authentication URLs must be checked before opening a connection",
                wrapper.contains("requireSecureAuthUrl"));
    }

    @Test
    public void retiredApplicationLayerLoginCryptoIsNotShipped() throws IOException {
        String wrapper = source("src/main/java/gq/yozakura/auth/vendor/tech/skidonion/obfuscator/inline/Wrapper.java");

        assertFalse("TLS must not be wrapped in the retired login ECDH protocol",
                wrapper.contains("ed25519KeyExchange"));
        assertFalse("Heartbeat payloads must not use the retired stateful stream cipher",
                wrapper.contains("sessionCrypto"));
        assertFalse("The retired server signing key must not remain embedded in the client",
                wrapper.contains("LOGIN_SERVER_PUBLIC_KEY"));
    }

    @Test
    public void authenticationDecisionIsOwnedByTheNativeLoader() throws IOException {
        String gate = source("src/main/java/gq/yozakura/auth/YozakuraAuthGate.java");
        String panel = source("src/main/java/gq/yozakura/auth/vendor/skidonion/sWdSl/VerificationPanel.java");
        String loader = source("native/yozakura_loader.cpp");
        String nativeAuth = source("native/yozakura_native_auth.cpp");
        String nativeBuild = source("build-native.bat");
        String gradleBuild = source("build.gradle");
        String readme = source("README.md");
        String remapLoader = source("src/main/java/gq/yozakura/bridge/VanillaRemapClassLoader.java");

        assertTrue("The runtime gate must use the native session authority",
                gate.contains("NativeAuthBridge.isVerifiedSession()"));
        assertFalse("The runtime gate must not trust the retired Java session authority",
                gate.contains("Wrapper.isVerifiedSession()"));
        assertTrue("Credential submission must enter the native implementation",
                panel.contains("NativeAuthBridge.login("));
        assertFalse("Credential submission must not call the Java HTTP implementation",
                panel.contains("Wrapper.login("));
        assertBefore(loader, "if (entryLoader && !registerYozakuraNativeAuth",
                "if (entryLoader && instantiateClient");
        assertTrue("Native authentication must use the Windows HTTPS stack",
                nativeAuth.contains("WinHttpOpenRequest"));
        assertTrue("Native authentication must retain the server-issued expiry",
                nativeAuth.contains("expired_date"));
        assertTrue("The login UI must show the verified expiry to the customer",
                panel.contains("NativeAuthBridge.getVerifiedExpiry()"));
        assertTrue("Native builds must compile the native authentication implementation",
                nativeBuild.contains("native\\yozakura_native_auth.cpp"));
        assertTrue("Native builds must link WinHTTP",
                nativeBuild.contains("winhttp.lib"));
        assertTrue("Native release builds must receive an explicit authentication endpoint",
                nativeBuild.contains("YOZAKURA_AUTH_BASE_URL"));
        assertTrue("Native release builds must default to the official HTTPS endpoint",
                nativeBuild.contains("https://auth.yozakura.wtf/"));
        assertFalse("Native release builds must not retain the example endpoint",
                nativeBuild.contains("https://auth.example.com/"));
        assertFalse("Release documentation must not instruct builders to use an example endpoint",
                readme.contains("https://auth.example.com/"));
        assertTrue("Regular client builds must use the same official endpoint",
                gradleBuild.contains("orElse('https://auth.yozakura.wtf/')"));
        assertTrue("Native builds must run Gradle on the required Java 8 installation",
                nativeBuild.contains("set \"JAVA_HOME=%JAVA8_HOME%\""));
        assertTrue("Native builds should discover a user-installed Java 8 without a machine-specific path",
                nativeBuild.contains("%USERPROFILE%\\.jdks\\corretto-1.8*"));
        assertFalse("Native builds must not contain a previous developer's private JDK path",
                nativeBuild.contains("C:\\Users\\shiranaidk"));
        assertTrue("The endpoint must be embedded into the JAR before it enters the DLL",
                nativeBuild.contains("-Pyozakura_auth_base_url=%AUTH_BASE_URL%"));
        assertTrue("Remapped clients must share the exact registered native bridge class",
                remapLoader.contains("!name.equals(\"gq.yozakura.auth.NativeAuthBridge\")"));
    }

    private static void assertBefore(String source, String required, String boundary) {
        int requiredIndex = source.indexOf(required);
        int boundaryIndex = source.indexOf(boundary);
        assertTrue(required + " must appear before " + boundary,
                requiredIndex >= 0 && boundaryIndex >= 0 && requiredIndex < boundaryIndex);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
