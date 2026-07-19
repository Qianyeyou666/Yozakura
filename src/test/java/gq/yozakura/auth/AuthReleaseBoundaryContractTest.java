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
    public void javaArtifactDoesNotShipASecondAuthenticationProtocol() throws IOException {
        String wrapper = source("src/main/java/gq/yozakura/auth/vendor/tech/skidonion/obfuscator/inline/Wrapper.java");
        String gradleBuild = source("build.gradle");

        assertFalse("The JAR must not retain a comparable Java login implementation",
                wrapper.contains("static int login("));
        assertFalse("The JAR must not retain the old Java HTTP transport",
                wrapper.contains("HttpURLConnection"));
        assertFalse("The JAR must not retain Java heartbeat code",
                wrapper.contains("Heartbeat") || wrapper.contains("heartbeat"));
        assertFalse("The JAR must not retain old verification routes",
                wrapper.contains("/verify/login") || wrapper.contains("/verify/heartbeat"));
        assertFalse("The retired Java endpoint resource must not be packaged",
                Files.exists(Paths.get("src/main/resources/yozakura-auth.properties")));
        assertFalse("The Gradle build must not expand a retired Java endpoint resource",
                gradleBuild.contains("yozakura-auth.properties")
                        || gradleBuild.contains("yozakura_auth_base_url"));
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
        String nativeVerifier = source("tools/Verify-NativePayload.ps1");
        String obfuscationBuild = source("obfuscate-linux.sh");
        String gradleBuild = source("build.gradle");
        String readme = source("README.md");
        String remapLoader = source("src/main/java/gq/yozakura/bridge/VanillaRemapClassLoader.java");

        assertFalse("The runtime gate must not expose one centralized allowRuntime patch point",
                gate.contains("allowRuntime("));
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
        assertFalse("The verification window must not show the registration/legal link row",
                panel.contains("registerLabel") || panel.contains("termsLabel") || panel.contains("privacyLabel"));
        assertTrue("Native builds must compile the native authentication implementation",
                nativeBuild.contains("native\\yozakura_native_auth.cpp"));
        assertTrue("Native builds must link WinHTTP",
                nativeBuild.contains("winhttp.lib"));
        assertFalse("The Java obfuscation path must not expose a configurable authentication endpoint",
                obfuscationBuild.contains("YOZAKURA_AUTH_BASE_URL")
                        || obfuscationBuild.contains("49.235.166.227"));
        assertFalse("Native release builds must not retain the example endpoint",
                obfuscationBuild.contains("https://auth.example.com/"));
        assertFalse("Release documentation must not instruct builders to use an example endpoint",
                readme.contains("https://auth.example.com/"));
        assertFalse("Release documentation must not publish the native endpoint",
                readme.contains("49.235.166.227"));
        assertTrue("Native builds must run Gradle on the required Java 8 installation",
                nativeBuild.contains("set \"JAVA_HOME=%JAVA8_HOME%\""));
        assertTrue("Native builds should discover a user-installed Java 8 without a machine-specific path",
                nativeBuild.contains("%USERPROFILE%\\.jdks\\corretto-1.8*"));
        assertFalse("Native builds must not contain a previous developer's private JDK path",
                nativeBuild.contains("C:\\Users\\shiranaidk"));
        assertTrue("Windows native builds must verify the protected JAR before embedding it",
                nativeBuild.contains("Verify-ObfuscatedJar.ps1"));
        assertTrue("Windows native builds must scan the produced DLL",
                nativeBuild.contains("Verify-NativePayload.ps1"));
        assertFalse("Release native builds must not emit a program database",
                nativeBuild.contains("/PDB:"));
        assertTrue("The DLL verifier must reject retired log and export names",
                nativeVerifier.contains("JarToDllLoader.log")
                        && nativeVerifier.contains("JarToDllInject")
                        && nativeVerifier.contains("YozakuraInject"));
        assertTrue("Remapped clients must share the exact registered native bridge class",
                remapLoader.contains("!name.equals(\"gq.yozakura.auth.NativeAuthBridge\")"));
    }

    @Test
    public void javaDoesNotOwnAFixedNativeSuccessIdentity() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/auth/NativeAuthBridge.java");

        assertFalse("The Java bridge must not embed a fixed native runtime identifier",
                bridge.contains("RUNTIME_ID") || bridge.contains("0x594F5A414B555241"));
        assertFalse("The Java bridge must not expose the old runtime identity JNI method",
                bridge.contains("runtimeId0"));
        assertFalse("The Java bridge must not cache a patchable verified boolean",
                bridge.contains("boolean verified") || bridge.contains("boolean success"));
    }

    @Test
    public void tokenAuthScreenKeepsMinecraftGuiOverrideAbiAfterNeko() throws IOException {
        String rules = source("obfuscation/neko-release-rules.yml");
        String pipeline = source("tools/Obfuscate-Client.ps1");

        String match = "- match: 'gq.yozakura.auth.token.**'";
        int ruleStart = rules.indexOf(match);
        int nextRule = ruleStart < 0 ? -1 : rules.indexOf("\n  - match:", ruleStart + match.length());
        String tokenGuiRule = ruleStart < 0 ? "" : rules.substring(
                ruleStart, nextRule < 0 ? rules.length() : nextRule);
        assertTrue("Neko must preserve TokenAuthSessionGui's inherited GuiScreen method names",
                tokenGuiRule.contains("renamer: { enabled: false }")
                        && tokenGuiRule.contains("controlFlowFlattening: { enabled: false }"));
        assertTrue("The release verifier must inspect TokenAuthSessionGui",
                pipeline.contains("'gq.yozakura.auth.token.TokenAuthSessionGui'"));
        for (String callback : new String[] {
                "func_73866_w_", "func_146281_b", "func_73876_c", "func_73863_a",
                "func_146284_a", "func_73869_a", "func_73864_a", "func_73868_f"
        }) {
            assertTrue("The release verifier must require the TokenAuth GUI callback " + callback,
                    pipeline.contains(callback));
        }
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
