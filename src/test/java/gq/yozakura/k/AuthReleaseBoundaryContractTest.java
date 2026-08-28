package gq.yozakura.k;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthReleaseBoundaryContractTest {
    @Test
    public void clientVerificationIsEnabledAtTheAuthBoundary() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/Client.java");
        String gate = source("src/main/java/gq/yozakura/k/B.java");

        assertFalse("Authentication must not depend on the general client debug flag",
                client.contains("DebugMode"));
        assertTrue("Release authentication must be explicitly enabled at the centralized boundary",
                gate.contains("private static final boolean CLIENT_VERIFICATION_ENABLED = true;"));
        assertFalse("Release sources must not retain the temporary verification bypass",
                gate.contains("CLIENT_VERIFICATION_ENABLED = false"));
        assertTrue("The local identity must remain isolated from cloud authentication proof",
                gate.contains("private static final String LOCAL_USERNAME = \"YozakuraUser\";"));
        assertTrue("Runtime permits must share the same authentication decision",
                gate.contains("return !CLIENT_VERIFICATION_ENABLED || A.permitModuleActivation();"));
    }

    @Test
    public void everyRuntimeEntryPointAuthenticatesBeforeTakingOwnership() throws IOException {
        String legacy = source("src/main/java/gq/yozakura/core/Client.java");
        String standalone = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        String modern = source("src/main/java/gq/yozakura/core/ModernForgeClient.java");

        assertBefore(legacy, "B.verifyOrThrow(\"forge\");", "state = true;");
        assertBefore(standalone, "B.verifyOrThrow(\"standalone\");", "state = true;");
        assertBefore(modern, "B.verifyOrThrow(\"modern-forge\");", "state = true;");
    }

    @Test
    public void javaArtifactDoesNotShipASecondAuthenticationProtocol() throws IOException {
        String wrapper = source("src/main/java/gq/yozakura/k/vendor/tech/skidonion/obfuscator/inline/C.java");
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
        String wrapper = source("src/main/java/gq/yozakura/k/vendor/tech/skidonion/obfuscator/inline/C.java");

        assertFalse("TLS must not be wrapped in the retired login ECDH protocol",
                wrapper.contains("ed25519KeyExchange"));
        assertFalse("Heartbeat payloads must not use the retired stateful stream cipher",
                wrapper.contains("sessionCrypto"));
        assertFalse("The retired server signing key must not remain embedded in the client",
                wrapper.contains("LOGIN_SERVER_PUBLIC_KEY"));
    }

    @Test
    public void authenticationDecisionIsOwnedByTheNativeLoader() throws IOException {
        String gate = source("src/main/java/gq/yozakura/k/B.java");
        String panel = source("src/main/java/gq/yozakura/k/vendor/skidonion/sWdSl/D.java");
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
                gate.contains("C.isVerifiedSession()"));
        assertTrue("Credential submission must enter the native implementation",
                panel.contains("A.login("));
        assertFalse("Credential submission must not call the Java HTTP implementation",
                panel.contains("C.login("));
        assertBefore(loader, "if (entryLoader && !registerYozakuraNativeAuth",
                "if (entryLoader && instantiateClient");
        assertTrue("Native authentication must use the Windows HTTPS stack",
                nativeAuth.contains("WinHttpOpenRequest"));
        assertTrue("Authentication must honor Windows automatic proxy discovery",
                nativeAuth.contains("WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY"));
        assertFalse("Authentication must not force a direct connection that bypasses the system proxy",
                nativeAuth.contains("WINHTTP_ACCESS_TYPE_NO_PROXY"));
        assertTrue("The client must connect only to the production verification domain",
                nativeAuth.contains("https://auth.yozakura.wtf/")
                        && nativeAuth.contains("isConfiguredAuthEndpoint")
                        && !nativeAuth.contains("https://yozakura.wtf/"));
        assertTrue("The config hall must use the public website API instead of a player loopback port",
                source("src/main/java/gq/yozakura/club/ClubApiClient.java")
                        .contains("DEFAULT_BASE_URL = \"https://yozakura.wtf\""));
        assertFalse("Release config hall sources must not default to the local development server",
                source("src/main/java/gq/yozakura/club/ClubApiClient.java")
                        .contains("DEFAULT_BASE_URL = \"http://127.0.0.1:4173\""));
        assertFalse("The retired IP endpoint and self-signed certificate bypass must be removed",
                nativeAuth.contains("49.235.166.227")
                        || nativeAuth.contains("SECURITY_FLAG_IGNORE_UNKNOWN_CA"));
        assertTrue("Transport failures must be classified instead of collapsing into one generic error",
                nativeAuth.contains("requestFailureLoginCode()")
                        && nativeAuth.contains("ERROR_WINHTTP_CANNOT_CONNECT")
                        && nativeAuth.contains("ERROR_WINHTTP_SECURE_FAILURE"));
        assertFalse("The integrity digest must not require a retired class resource",
                nativeAuth.contains("gq/yozakura/event/api/EventManager.class"));
        assertTrue("Local integrity and device-key failures must not masquerade as network errors",
                nativeAuth.contains("return -16;")
                        && nativeAuth.contains("return -17;")
                        && nativeAuth.contains("return -18;"));
        assertTrue("Challenge policy rejections must preserve the server response code",
                nativeAuth.contains("return static_cast<jint>(challengeCode);"));
        assertTrue("Proxy and TLS failures must have actionable customer-facing messages",
                source("src/main/resources/gq/yozakura/k/vendor/tech/skidonion/verification/lang.properties")
                        .contains("D.login.code.-13=Windows automatic proxy discovery failed")
                        && source("src/main/resources/gq/yozakura/k/vendor/tech/skidonion/verification/lang.properties")
                        .contains("D.login.code.-14="));
        assertTrue("Native authentication must retain the server-issued expiry",
                nativeAuth.contains("expired_date"));
        assertFalse("The verification success path must not create a Swing success notification",
                panel.contains("showVerifiedEntitlement")
                        || panel.contains("JOptionPane.INFORMATION_MESSAGE")
                        || panel.contains("JDialog")
                        || panel.contains("successTimer"));
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
        assertTrue("The DLL verifier must reject protected endpoints and retired export names",
                nativeVerifier.contains("auth.yozakura.wtf")
                        && nativeVerifier.contains("JarToDllLoader.log")
                        && nativeVerifier.contains("JarToDllInject")
                        && nativeVerifier.contains("YozakuraInject"));
        assertTrue("Remapped clients must share the exact registered native bridge class",
                remapLoader.contains("!name.equals(\"gq.yozakura.k.A\")"));
    }

    @Test
    public void verificationWindowOnlyAcceptsWebsiteAccountLogin() throws IOException {
        String panel = source("src/main/java/gq/yozakura/k/vendor/skidonion/sWdSl/D.java");

        assertTrue("The verification window must retain website account login",
                panel.contains("A.login("));
        assertFalse("License redemption belongs to the website, not the client verification window",
                panel.contains("A.redeemLicense(")
                        || panel.contains("performRedeem(")
                        || panel.contains("redeemButton")
                        || panel.contains("licenseField")
                        || panel.contains("registerUsernameField")
                        || panel.contains("registerPasswordField"));
        assertTrue("Native bridge linkage failures must remain visible to the customer",
                panel.contains("catch (LinkageError error)"));
        assertTrue("Swing state must be captured and updated on the event dispatch thread",
                panel.contains("LOGIN_EXECUTOR.execute(")
                        && panel.contains("private static void runOnEdt(Runnable action)"));
        assertFalse("A discarded Future must not swallow login task failures",
                panel.contains("LOGIN_EXECUTOR.submit(this::performLogin)"));
    }

    @Test
    public void successfulLoginReleasesTheRuntimeWithoutCreatingASwingSuccessDialog() throws IOException {
        String panel = source("src/main/java/gq/yozakura/k/vendor/skidonion/sWdSl/D.java");

        assertBefore(panel, "writeResult(1);", "runOnEdt(() -> frame.dispose());");
        assertTrue("The verification window must close asynchronously after releasing the runtime",
                panel.contains("runOnEdt(() -> frame.dispose());"));
        assertFalse("The success path must not create a Swing success notification",
                panel.contains("showVerifiedEntitlement")
                        || panel.contains("JOptionPane.INFORMATION_MESSAGE")
                        || panel.contains("JDialog")
                        || panel.contains("successTimer"));
        assertFalse("The success path must not re-enter native permit or entitlement getters",
                panel.contains("A.getVerifiedRole()") || panel.contains("A.getVerifiedExpiry()"));
    }

    @Test
    public void javaDoesNotOwnAFixedNativeSuccessIdentity() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/k/A.java");

        assertFalse("The Java bridge must not embed a fixed native runtime identifier",
                bridge.contains("RUNTIME_ID") || bridge.contains("0x594F5A414B555241"));
        assertFalse("The Java bridge must not expose the old runtime identity JNI method",
                bridge.contains("runtimeId0"));
        assertFalse("The Java bridge must not cache a patchable verified boolean",
                bridge.contains("boolean verified") || bridge.contains("boolean success"));
    }

    @Test
    public void jnicLeavesHotRuntimePermitWrappersOnTheRegisteredNativeBridge() throws IOException {
        String jnic = source("obfuscation/jnic-auth-release.xml");
        String bridge = source("src/main/java/gq/yozakura/k/A.java");
        String gate = source("src/main/java/gq/yozakura/k/B.java");

        for (String method : new String[] {
                "permitStartup", "permitModuleActivation", "permitTickDispatch",
                "permitRenderDispatch", "permitInputDispatch", "permitPacketDispatch",
                "permitEventDispatch", "permitMovementDispatch", "channelPermit"
        }) {
            assertFalse("JNIC must not transform hot permit wrapper " + method,
                    jnic.contains(method));
        }
        assertTrue("The bridge must dispatch startup permits through native q0",
                bridge.contains("return q0(System.nanoTime()) != 0L;"));
        assertTrue("The bridge must dispatch channel permits through native q1",
                bridge.contains("return q1(channel, probe) != 0L;"));
        assertTrue("The gate must still fail closed through the native bridge",
                gate.contains("A.permitModuleActivation()"));
    }

    @Test
    public void cloudIdentityUsesTheNativeVerificationProofAndFailsClosedInLocalMode() throws IOException {
        String gate = source("src/main/java/gq/yozakura/k/B.java");
        String bridge = source("src/main/java/gq/yozakura/k/A.java");
        String nativeAuth = source("native/yozakura_native_auth.cpp");

        assertTrue(gate.contains("public static String getVerifiedSessionProof()"));
        assertTrue(gate.contains("return CLIENT_VERIFICATION_ENABLED"));
        assertTrue(bridge.contains("return sessionProof0();"));
        assertTrue(nativeAuth.contains("nativeSessionProof"));
        assertFalse("The local development username must never become a cloud authentication proof",
                gate.contains("getVerifiedSessionProof() {\n        return LOCAL_USERNAME"));
    }

    @Test
    public void verificationSessionRequiresDeviceHeldProofOfPossession() throws IOException {
        String gate = source("src/main/java/gq/yozakura/k/B.java");
        String nativeAuth = source("native/yozakura_native_auth.cpp");
        String nativeBuild = source("build-native.bat");
        String server = source("openclaw/server/CompatVerifyServer.java");

        assertTrue("The PoP implementation must be enforced by the enabled release boundary",
                gate.contains("private static final boolean CLIENT_VERIFICATION_ENABLED = true;"));
        assertTrue("The server must issue a short-lived single-use login challenge",
                server.contains("/api/v2/verify/challenge")
                        && server.contains("CHALLENGE_TTL_SECONDS"));
        assertTrue("The server must bind the signed JWT to a device JWK thumbprint",
                server.contains("\\\"cnf\\\":{\\\"jkt\\\":\\\"")
                        && server.contains("OPENCLAW_JWT_SECRET"));
        assertTrue("Heartbeat authorization must require an ECDSA device proof",
                server.contains("pop_signature")
                        && server.contains("SHA256withECDSA"));
        assertTrue("The native client must use a persisted Windows CNG signing key",
                nativeAuth.contains("NCryptCreatePersistedKey")
                        && nativeAuth.contains("NCryptSignHash")
                        && nativeAuth.contains("BCRYPT_ECCPUBLIC_BLOB"));
        assertTrue("The native build must link Windows CNG and BCrypt",
                nativeBuild.contains("ncrypt.lib") && nativeBuild.contains("bcrypt.lib"));
        assertFalse("A copyable machine-id must not be sent as the device proof",
                nativeAuth.contains("addFormField(&body, \"hw\""));
    }

    @Test
    public void nativeAuthenticationPinsTheProductionTlsPublicKey() throws IOException {
        String nativeAuth = source("native/yozakura_native_auth.cpp");

        assertTrue("Authentication must obtain the presented TLS server certificate",
                nativeAuth.contains("WINHTTP_OPTION_SERVER_CERT_CONTEXT"));
        assertTrue("Authentication must hash the certificate SubjectPublicKeyInfo",
                nativeAuth.contains("CryptEncodeObjectEx")
                        && nativeAuth.contains("X509_PUBLIC_KEY_INFO"));
        assertTrue("Authentication must compare the SPKI hash against the release pin",
                nativeAuth.contains("kAuthServerSpkiSha256")
                        && nativeAuth.contains("constantTimeEquals"));
        assertTrue("The TLS pin must be checked before a response is accepted",
                nativeAuth.indexOf("verifyAuthServerCertificate")
                        < nativeAuth.indexOf("WinHttpQueryHeaders(requestHandle"));
    }

    @Test
    public void nativeRuntimeContinuouslyRevalidatesIntegrityAndDebuggerState() throws IOException {
        String nativeAuth = source("native/yozakura_native_auth.cpp");
        String themidaGuard = source("native/yozakura_themida_guard.cpp");
        String nativeBuild = source("build-native.bat");
        String nativeTest = source("native/tests/yozakura_themida_guard_test.cpp");

        assertTrue("Login must fail closed while a debugger is attached",
                nativeAuth.contains("debuggerEnvironmentTrusted()")
                        && nativeAuth.contains("return -20;"));
        assertTrue("Runtime permits must revoke a session when debugger checks fail",
                nativeAuth.contains("if (!debuggerEnvironmentTrusted())")
                        && nativeAuth.contains("InterlockedExchange(&verified, 0);"));
        assertTrue("Debugger state decisions belong inside the protected native guard",
                themidaGuard.contains("yozakuraThemidaAcceptDebuggerState")
                        && themidaGuard.contains("localDebuggerPresent == 0")
                        && themidaGuard.contains("remoteQuerySucceeded == 0")
                        && themidaGuard.contains("remoteDebuggerPresent == 0"));
        assertTrue("Native tests must reject confirmed debuggers but tolerate failed remote probes",
                nativeTest.contains("yozakuraThemidaAcceptDebuggerState(1, 1, 0) == 0")
                        && nativeTest.contains("yozakuraThemidaAcceptDebuggerState(0, 1, 1) == 0")
                        && nativeTest.contains("yozakuraThemidaAcceptDebuggerState(0, 0, 0) == 1"));
        assertTrue("Every heartbeat must re-read the protected class digest",
                nativeAuth.contains("runtimeClassDigest(&runtimeDigest)")
                        && nativeAuth.contains("constantTimeEquals(runtimeDigest, fingerprint)"));
        assertTrue("A post-login integrity mismatch must reject the heartbeat and clear the native session",
                nativeAuth.contains("!constantTimeEquals(runtimeDigest, fingerprint)")
                        && nativeAuth.contains("HeartbeatResult::Rejected")
                        && nativeAuth.contains("if (result == HeartbeatResult::Rejected)")
                        && nativeAuth.contains("clearSession();"));
        assertTrue("Native builds must expose the anti-debug rejection to the login UI",
                source("src/main/resources/gq/yozakura/k/vendor/tech/skidonion/verification/lang.properties")
                        .contains("D.login.code.-20="));
        assertTrue("Protected native builds must compile the guard before auth",
                nativeBuild.indexOf("yozakura_themida_guard.cpp")
                        < nativeBuild.indexOf("yozakura_native_auth.cpp"));
    }

    @Test
    public void nekoUsesABroadRenameSurfaceWithANarrowStableAbiWhitelist() throws IOException {
        String rules = source("obfuscation/neko-release-rules.yml");
        String fingerprint = source("src/main/java/gq/yozakura/k/vendor/tech/skidonion/obfuscator/inline/C.java");
        String gradleBuild = source("build.gradle");
        String pipeline = source("tools/Obfuscate-Client.ps1");
        String verifier = source("tools/Verify-ObfuscatedJar.ps1");

        assertTrue("Neko class and member renaming must remain enabled for eligible application code",
                rules.contains("renamer: { enabled: true, packagePrefix: 'n/' }"));
        assertFalse("The authentication package must not be excluded wholesale from Neko renaming",
                rules.contains("match: 'gq.yozakura.k.**'\n    exclude: true"));
        assertFalse("The module package must not be excluded wholesale from Neko renaming",
                rules.contains("match: 'gq.yozakura.module.**'\n    exclude: true"));
        for (String stableClass : new String[] {
                "gq.yozakura.k.A", "gq.yozakura.k.B",
                "gq.yozakura.k.A$**", "gq.yozakura.k.B$**",
                "gq.yozakura.k.vendor.tech.skidonion.obfuscator.inline.C**",
                "gq.yozakura.module.Module", "gq.yozakura.module.Module$**"
        }) {
            assertTrue("Neko must preserve stable ABI class family " + stableClass,
                    rules.contains("match: '" + stableClass + "'"));
        }
        assertTrue("The fingerprint must follow the renamed login panel through a class literal",
                fingerprint.contains("updateClassDigest(digest, D.class);")
                        && !fingerprint.contains("\"gq.yozakura.k.vendor.skidonion.sWdSl.D\""));
        assertTrue("Neko must use an SRG Minecraft classpath matching the remapped input ABI",
                gradleBuild.contains("syncObfuscationSrgClasspath")
                        && pipeline.contains("obfuscation\\srg-libs"));
        assertTrue("The Windows release path must invoke Neko CLI",
                pipeline.contains("YOZAKURA_NEKO_HOME")
                        && pipeline.contains("neko-cli\\build\\install\\neko-cli\\bin\\neko-cli.bat")
                        && pipeline.contains("obfuscate --config $nekoConfig --input $nekoInputJar --output $nekoJar"));
        assertFalse("The retired ZKM and constrained post-renamer must not remain on the formal release path",
                pipeline.contains("YOZAKURA_ZKM_JAR")
                        || pipeline.contains("Zelix KlassMaster")
                        || pipeline.contains("ConstrainedClassRenamer")
                        || pipeline.contains("Yozakura-zkm-renamed.jar"));
        assertTrue("The verifier must consume and require the Neko mapping",
                verifier.contains("[string]$NekoMapping")
                        && verifier.contains("Neko mapping")
                        && verifier.contains("gq/yozakura/k/t/F"));
    }

    @Test
    public void nekoKeepsCffDisabledUntilItsImplementationIsReworked() throws IOException {
        String rules = source("obfuscation/neko-release-rules.yml");
        String jnic = source("obfuscation/jnic-auth-release.xml");

        assertTrue("Neko control-flow flattening must remain globally disabled",
                rules.contains("controlFlowFlattening: { enabled: false, intensity: 0.35 }"));
        assertFalse("No release rule may re-enable the unfinished Neko CFF pass",
                rules.contains("controlFlowFlattening: { enabled: true"));
        assertTrue("The minimal native authentication boundary retains JNIC flow protection",
                jnic.contains("<flowObf>true</flowObf>"));
    }

    @Test
    public void oneClickObfuscationUsesCurrentAuthApiAndFailsClosed() throws IOException {
        String batch = source("obfuscate-client.bat");
        String pipeline = source("tools/Obfuscate-Client.ps1");
        String verifier = source("tools/Verify-ObfuscatedJar.ps1");
        String nativeVerifier = source("tools/Verify-NativePayload.ps1");
        String nativeBuild = source("build-native.bat");
        String jnic = source("obfuscation/jnic-auth-release.xml");
        String neko = source("obfuscation/neko-release-rules.yml");

        assertTrue("The one-click entry point must support a toolchain preflight",
                batch.contains("preflight") && pipeline.contains("PreflightOnly"));
        assertTrue("The one-click entry point must build a clean, fresh obfuscation input by default",
                batch.contains(":run_pipeline")
                        && pipeline.contains("--no-daemon --offline clean prepareObfuscation"));
        assertTrue("A failed obfuscation must never package an unprotected fallback",
                batch.contains("No unprotected fallback was packaged"));
        assertTrue("The batch wrapper must capture the PowerShell exit code before returning from the subroutine",
                batch.contains("set \"PIPELINE_EXIT=%errorlevel%\"")
                        && batch.contains("exit /b %PIPELINE_EXIT%"));
        assertTrue("A successful PowerShell pipeline must not leak a stale native child-process exit code",
                pipeline.trim().endsWith("exit 0"));
        assertTrue("Default preflight must validate the Themida VM project before starting Gradle",
                pipeline.contains("$preflightProject = [System.IO.Path]::GetTempFileName()")
                        && pipeline.contains("New-ReleaseThemidaProject $themidaProject $preflightProject"));
        assertTrue("The Windows release pipeline must invoke the configured Neko CLI",
                pipeline.contains("YOZAKURA_NEKO_HOME")
                        && pipeline.contains("Neko CLI")
                        && pipeline.contains("obfuscate --config $nekoConfig --input $nekoInputJar --output $nekoJar"));
        assertFalse("The retired ZKM toolchain must not remain on the release path",
                pipeline.contains("YOZAKURA_ZKM_JAR")
                        || pipeline.contains("Zelix KlassMaster")
                        || pipeline.contains("ConstrainedClassRenamer"));
        assertTrue("Neko must rename eligible Java application classes into n/** while CFF remains disabled",
                neko.contains("renamer: { enabled: true, packagePrefix: 'n/' }")
                        && neko.contains("controlFlowFlattening: { enabled: false, intensity: 0.35 }")
                        && !neko.contains("controlFlowFlattening: { enabled: true"));
        assertTrue("The authentication core must retain JNIC flow obfuscation after Neko",
                jnic.contains("<flowObf>true</flowObf>"));
        assertTrue("Eskid must harden only the renamed application boundary before JNIC",
                pipeline.contains("function New-EskidApplicationBoundaryJar")
                        && pipeline.contains("function New-EskidStableAbiSupportJar")
                        && pipeline.contains("function Merge-EskidApplicationBoundary")
                        && pipeline.contains("Eskid reported unresolved application hierarchy contracts")
                        && pipeline.contains("YOZAKURA_ESKID_JAR")
                        && pipeline.contains("eskid-release.json"));
        assertTrue("JNIC must parse only the expanded A/B authentication boundary and merge it back precisely",
                pipeline.contains("function New-JnicAuthenticationBoundaryJar")
                        && pipeline.contains("function Merge-JnicAuthenticationBoundary")
                        && pipeline.contains("Test-JnicAuthenticationBoundaryEntry")
                        && pipeline.contains("dev/jnic/"));
        assertTrue("The self-owned Java-to-native tool must be directly usable while retaining path override and tool hashing",
                pipeline.contains("YOZAKURA_JNIC_JAR")
                        && pipeline.contains("else { \"D:\\obf\\jnic\\jnic-3.7.0.jar\" }")
                        && pipeline.contains("JNIC SHA-256")
                        && !pipeline.contains("$jnicTool = {")
                        && !pipeline.contains("YOZAKURA_JNIC_LICENSED"));
        assertTrue("Release Neko logging and mapping must be retained for audit",
                pipeline.contains("neko.log")
                        && pipeline.contains(".map")
                        && pipeline.contains("NekoMapping"));
        assertTrue("Full native release must pass the fresh input, Neko/Eskid intermediates, and Eskid audit log to JAR verification",
                pipeline.contains("YOZAKURA_OBFUSCATION_INPUT_JAR")
                        && pipeline.contains("YOZAKURA_INTERMEDIATE_JAR")
                        && pipeline.contains("YOZAKURA_ESKID_INTERMEDIATE_JAR")
                        && pipeline.contains("YOZAKURA_ESKID_LOG")
                        && pipeline.contains("YOZAKURA_NEKO_MAPPING")
                        && nativeBuild.contains("-EskidJar \"%YOZAKURA_ESKID_INTERMEDIATE_JAR%\"")
                        && nativeBuild.contains("-EskidLog \"%YOZAKURA_ESKID_LOG%\""));
        assertTrue("JNIC must not parse unrelated application classes or large resources",
                pipeline.contains("New-JnicAuthenticationBoundaryJar")
                        && pipeline.contains("Merge-JnicAuthenticationBoundary")
                        && !pipeline.contains("Copy-JarWithoutJnicDeferredEntries")
                        && !pipeline.contains("Restore-JnicDeferredEntries"));
        assertTrue("A new release attempt must remove stale managed outputs before doing any work",
                pipeline.contains("function Remove-StaleReleaseArtifacts")
                        && pipeline.contains("Remove-Item -LiteralPath $artifact -Force")
                        && pipeline.contains("Remove-StaleReleaseArtifacts @("));
        String retiredEventManager = "'gq/yozakura/event/api/EventManager.class'";
        assertTrue("Release validation must reject, not require, the retired duplicate event manager",
                verifier.contains("the retired duplicate event manager is present")
                        && verifier.indexOf(retiredEventManager) >= 0);
        assertFalse("JNIC must not select retired authentication methods",
                jnic.contains("requireNativeRuntime") || jnic.contains("requireRuntime"));
        for (String method : new String[] {
                "login", "redeemLicense", "getVerifiedUsername", "getVerifiedSessionProof",
                "verifyOrThrow", "getVerifiedUsername", "getVerifiedSessionProof"
        }) {
            assertTrue("JNIC must select the current authentication method " + method,
                    jnic.contains(method));
            assertTrue("The final verifier must require the current JNIC method " + method,
                    verifier.contains("\"" + method + "\""));
        }
        for (String method : new String[] {
                "permitStartup", "permitModuleActivation", "permitTickDispatch",
                "permitRenderDispatch", "permitInputDispatch", "permitPacketDispatch",
                "permitEventDispatch", "permitMovementDispatch", "channelPermit"
        }) {
            assertFalse("JNIC must preserve the hot native permit wrapper " + method,
                    jnic.contains(method));
        }
        assertFalse("The final verifier must not require retired centralized permit methods",
                verifier.contains("\"allowRuntime\"") || verifier.contains("\"requireRuntime\""));
        assertTrue("The final verifier must reject a JNIC-transformed runtime permit wrapper",
                verifier.contains("JNIC transformed the hot runtime permit wrapper"));
        assertTrue("The release JAR name must identify the Neko, Eskid, and JNIC protection chain",
                pipeline.contains("Yozakura-1.5.0-neko-eskid-jnic.jar")
                        && batch.contains("Yozakura-1.5.0-neko-eskid-jnic.jar")
                        && !pipeline.contains("Yozakura-1.5.0-zkm"));
        assertTrue("The full release pipeline must target x64 only because JNIC 3.7.0 has no x86 target",
                pipeline.contains("YozakuraLoader-x64.dll")
                        && !pipeline.contains("YozakuraLoader-x86.dll")
                        && !batch.contains("YozakuraLoader-x86.dll"));
        assertTrue("Native payload verification must prove the named release JAR, runtime copy, and embedded RCDATA are identical",
                pipeline.contains("embeddedJar")
                        && pipeline.contains("Verify-NativePayload.ps1")
                        && pipeline.contains("-Jar $releaseJar")
                        && pipeline.contains("-EmbeddedJarSource $embeddedJar"));
        assertTrue("The native build must verify the copied JAR matches the source runtime JAR before embedding",
                nativeBuild.contains("The copied Yozakura.jar does not match the source runtime JAR."));
        assertTrue("Native asset packaging must synchronize the final runtime JAR back to the named release artifact",
                nativeBuild.contains("call :synchronize_final_runtime_jar")
                        && nativeBuild.contains("Final named release JAR does not match build\\libs\\Yozakura.jar."));
        assertTrue("The final named JAR must be re-verified after native assets are packaged",
                pipeline.contains("Re-verifying the final named release JAR after native asset packaging")
                        && pipeline.contains("-Jar $releaseJar"));
        assertTrue("The native verifier must reject divergence between the named and runtime-copy JARs",
                nativeVerifier.contains("EmbeddedJarSource")
                        && nativeVerifier.contains("named release JAR does not match the runtime JAR used by the resource compiler"));
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
