package gq.yozakura.build;

import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ObfuscateClientBatchTest {
    @Test
    public void helpWorksOutsideTheRepositoryAndDocumentsOneClickModes() throws Exception {
        assumeWindows();

        BatchResult result = runBatch("help");

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains(
                "Full build: Neko + Eskid + JNIC + x64 DLL + Themida"));
        assertTrue(result.output, result.output.contains("jar-only"));
        assertTrue(result.output, result.output.contains("preflight"));
    }

    @Test
    public void unknownModeAndExtraArgumentsAreRejected() throws Exception {
        assumeWindows();

        BatchResult unknown = runBatch("not-a-mode");
        BatchResult extra = runBatch("help", "unexpected");

        assertEquals(unknown.output, 2, unknown.exitCode);
        assertEquals(extra.output, 2, extra.exitCode);
        assertTrue(extra.output, extra.output.contains("Unexpected argument"));
    }

    @Test
    public void releasePipelineProvidesEnoughHeapForTheConfiguredNekoPasses() throws Exception {
        String pipeline = new String(Files.readAllBytes(Paths.get("tools/Obfuscate-Client.ps1")),
                StandardCharsets.UTF_8);
        assertTrue("The release CFF pipeline exhausted the previous 4 GB default heap",
                pipeline.contains("-Xmx10240m"));
        assertTrue("Builders must be able to override the Neko JVM allocation",
                pipeline.contains("YOZAKURA_NEKO_JVM_OPTS"));
    }

    @Test
    public void releaseRulesKeepThirdPartyLibrariesOutsideSelectiveCff() throws Exception {
        String rules = new String(Files.readAllBytes(Paths.get("obfuscation/neko-release-rules.yml")),
                StandardCharsets.UTF_8);

        for (String namespace : new String[] {"org.**", "io.**", "javax.**"}) {
            String rule = "- match: '" + namespace + "'";
            int start = rules.indexOf(rule);
            int end = start < 0 ? -1 : rules.indexOf("\n  - match:", start + rule.length());
            String block = start < 0 ? "" : rules.substring(start, end < 0 ? rules.length() : end);
            assertTrue("Third-party namespace must be excluded from CFF: " + namespace,
                    block.contains("controlFlowFlattening: { enabled: false }")
                            && block.contains("renamer: { enabled: false }"));
        }
    }

    @Test
    public void formalNekoProfileKeepsCffGloballyDisabled() throws Exception {
        String rules = new String(Files.readAllBytes(Paths.get("obfuscation/neko-release-rules.yml")),
                StandardCharsets.UTF_8);
        String pipeline = new String(Files.readAllBytes(Paths.get("tools/Obfuscate-Client.ps1")),
                StandardCharsets.UTF_8);

        assertTrue(rules.contains("controlFlowFlattening: { enabled: false, intensity: 0.35 }"));
        assertTrue("No class rule may re-enable the unfinished CFF implementation",
                !rules.contains("controlFlowFlattening: { enabled: true"));
        assertTrue("The formal pipeline must describe the disabled CFF boundary honestly",
                pipeline.contains("Running Neko class/member renaming with CFF disabled"));
    }

    @Test
    public void formalWindowsPathUsesNekoAndEskidWithoutLegacyComparisonProfiles() throws Exception {
        String batch = new String(Files.readAllBytes(Paths.get("obfuscate-client.bat")),
                StandardCharsets.UTF_8);
        String pipeline = new String(Files.readAllBytes(Paths.get("tools/Obfuscate-Client.ps1")),
                StandardCharsets.UTF_8);
        String eskid = new String(Files.readAllBytes(Paths.get("obfuscation/eskid-release.json")),
                StandardCharsets.UTF_8);

        assertTrue(batch.contains("Neko rename") && batch.contains("Eskid hardening"));
        assertTrue(pipeline.contains("YOZAKURA_NEKO_HOME"));
        assertTrue(pipeline.contains("YOZAKURA_ESKID_JAR"));
        assertTrue("The self-owned JNIC-compatible tool must have a usable string default and remain overridable",
                pipeline.contains("YOZAKURA_JNIC_JAR")
                        && pipeline.contains("else { \"D:\\obf\\jnic\\jnic-3.7.0.jar\" }")
                        && !pipeline.contains("$jnicTool = {"));
        assertTrue(pipeline.contains("New-EskidApplicationBoundaryJar"));
        assertTrue(pipeline.contains("New-EskidStableAbiSupportJar"));
        assertTrue(pipeline.contains("Merge-EskidApplicationBoundary"));
        assertTrue(pipeline.contains("Eskid reported unresolved application hierarchy contracts"));
        assertTrue(eskid.contains("\"StringEncryptionTransformer\"")
                && eskid.contains("\"NumberObfuscationTransformer\"")
                && eskid.contains("\"ControlFlowTransformer\""));
        assertTrue(!batch.contains("github-baseline") && !batch.contains("no-cff"));
        assertTrue(!pipeline.contains("GitHubBaseline") && !pipeline.contains("[switch]$NoCff"));
    }

    @Test
    public void eskidReleaseProfileUsesCompatibilitySafeTransformsOnly() throws Exception {
        String eskid = new String(Files.readAllBytes(Paths.get("obfuscation/eskid-release.json")),
                StandardCharsets.UTF_8);

        assertTrue(eskid.contains("\"StringEncryptionTransformer\"")
                && eskid.contains("\"Type\": \"FAST\""));
        assertTrue(eskid.contains("\"NumberObfuscationTransformer\"")
                && eskid.contains("\"Execute_twice\": false")
                && eskid.contains("\"ShiftNumber\": false"));
        assertTrue("Eskid 0.42 flow rewrites boolean calls, StringBuilder.toString(), class initializers, and fields even when its sub-options are disabled",
                eskid.contains("\"ControlFlowTransformer\": {")
                        && eskid.contains("\"ControlFlowTransformer\": {\n      \"Enable\": false"));
        assertTrue("Member shuffling mutates parameter, exception, and local-variable table order and is not safe for the Forge compatibility profile",
                eskid.contains("\"ShuffleMembersTransformer\": {\n      \"Enable\": false"));
        assertTrue("Eskid watermarks use invalid or collision-prone field names and must remain disabled",
                eskid.contains("\"WaterMarkTransformer\": {\n      \"Enable\": false"));
        for (String unsafe : new String[] {
                "\"AntiDebugTransformer\": {\n      \"Enable\": true",
                "\"PackagerTransformer\": {\n      \"Enable\": true",
                "\"InvokeDynamicTransformer\": {\n      \"Enable\": true",
                "\"ScrambleTransformer\": {\n      \"Enable\": true",
                "\"CrasherTransformer\": {\n      \"Enable\": true",
                "\"UnusedMemberTransformer\": {\n      \"Enable\": true"
        }) {
            assertTrue("Unsafe Eskid transform enabled in the Forge release profile: " + unsafe,
                    !eskid.contains(unsafe));
        }
    }

    @Test
    public void currentProfileCanBeBuiltWithoutThemidaForIsolation() throws Exception {
        String batch = new String(Files.readAllBytes(Paths.get("obfuscate-client.bat")),
                StandardCharsets.UTF_8);
        String pipeline = new String(Files.readAllBytes(Paths.get("tools/Obfuscate-Client.ps1")),
                StandardCharsets.UTF_8);

        assertTrue("The one-click entry point must expose the Themida isolation profile",
                batch.contains("no-themida"));
        assertTrue("The pipeline must accept the isolation switch", pipeline.contains("[switch]$NoThemida"));
        assertTrue("The isolation build must keep its artifacts separate from the normal release",
                pipeline.contains("neko-eskid-jnic-no-themida"));
        assertTrue("Only the explicit no-themida mode may skip Themida",
                pipeline.contains("$skipThemida = $NoThemida"));
    }


    @Test
    public void thirdPartyClassesBypassNekoAndAreMergedBackUnchanged() throws Exception {
        String pipeline = new String(Files.readAllBytes(Paths.get("tools/Obfuscate-Client.ps1")),
                StandardCharsets.UTF_8);

        assertTrue(pipeline.contains("function Test-StableThirdPartyEntry"));
        for (String prefix : new String[] {"com/", "io/", "javax/", "javazoom/", "org/"}) {
            assertTrue("Missing stable third-party prefix: " + prefix,
                    pipeline.contains("$Name.StartsWith('" + prefix + "')"));
        }
        assertTrue(pipeline.contains("(Test-StableThirdPartyEntry $entry.FullName)"));
        assertTrue(pipeline.contains("-not (Test-StableThirdPartyEntry $entry.FullName)"));
    }

    @Test
    public void fullReleaseRequiresAnExternalThemidaProtectionStep() throws Exception {
        String batch = new String(Files.readAllBytes(Paths.get("obfuscate-client.bat")),
                StandardCharsets.UTF_8);
        String pipeline = new String(Files.readAllBytes(Paths.get("tools/Obfuscate-Client.ps1")),
                StandardCharsets.UTF_8);
        String themida = new String(Files.readAllBytes(Paths.get("tools/Invoke-Themida.ps1")),
                StandardCharsets.UTF_8);
        String inline = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/k/vendor/tech/skidonion/obfuscator/inline/Inline.java")),
                StandardCharsets.UTF_8);

        assertTrue(batch.contains("YOZAKURA_THEMIDA_MARKERS=1"));
        assertTrue("The one-click path must validate actual Themida files and output instead of requiring a manual environment acknowledgement",
                !pipeline.contains("YOZAKURA_THEMIDA_LICENSED")
                        && !themida.contains("YOZAKURA_THEMIDA_LICENSED"));
        assertTrue(pipeline.contains("YOZAKURA_THEMIDA_PROTECTOR"));
        assertTrue(pipeline.contains("YOZAKURA_THEMIDA_PROJECT"));
        assertTrue(pipeline.contains("YozakuraLoader-x64-themida-input.dll"));
        assertTrue(pipeline.contains("Invoke-ThemidaProtection"));
        assertTrue(pipeline.contains("New-ReleaseThemidaProject"));
        assertTrue(pipeline.contains("[8/9] Protecting the x64 loader with Themida"));
        assertTrue(pipeline.contains("[9/9] Verifying the embedded x64 native payload"));
        assertTrue("The compatibility profile must retain a Themida VM for the auth markers",
                pipeline.contains("OPTION_VIRTUAL_MACHINE_NUMBER"));
        assertTrue(pipeline.contains("OPTION_COMPRESSION_COMPRESS_RESOURCES=false"));
        assertTrue("The release profile must not globally pack an injected loader",
                pipeline.contains("OPTION_COMPRESSION_COMPRESS_APPLICATION=false"));
        for (String option : new String[] {
                "OPTION_PROTECTION_IS_ANTIDEBUG=false",
                "OPTION_PROTECTION_IS_API_WRAPPER_ENABLED=false",
                "OPTION_PROTECTION_IS_FILE_REGISTRY_MONITORS=false",
                "OPTION_PROTECTION_IS_VMWARE_SUPPORT=false",
                "OPTION_MACROS_INTEGRITY_CHECKS=false"
        }) {
            assertTrue("The compatibility profile must disable the global Themida hook: " + option,
                    pipeline.contains(option));
        }
        assertTrue(themida.contains("Themida64.exe"));
        assertTrue(themida.contains("Start-Process -FilePath $themidaExecutable"));
        assertTrue(themida.contains("'/protect', $resolvedProject"));
        assertTrue(themida.contains("'/inputfile', $resolvedInput"));
        assertTrue(themida.contains("'/outputfile', $resolvedOutput"));
        assertTrue("The release JAR must not retain a property that disables protection",
                !inline.contains("phantom-shield-inline.disable-protection"));
    }

    @Test
    public void releaseJarRelocatesTheAuthenticationAbiOutOfTheAuthNamespace() throws Exception {
        String pipeline = new String(Files.readAllBytes(Paths.get("tools/Obfuscate-Client.ps1")),
                StandardCharsets.UTF_8);
        String nativeSource = new String(Files.readAllBytes(Paths.get("native/yozakura_native_auth.cpp")),
                StandardCharsets.UTF_8);
        String jnic = new String(Files.readAllBytes(Paths.get("obfuscation/jnic-auth-release.xml")),
                StandardCharsets.UTF_8);

        for (String source : new String[] {pipeline, nativeSource, jnic}) {
            assertTrue("Release ABI must use the relocated authentication package",
                    source.contains("gq/yozakura/k/A") || source.contains("gq.yozakura.k.A"));
        }
        for (String source : new String[] {nativeSource, jnic}) {
            assertTrue("Release ABI must not use the old authentication package",
                    !source.contains("gq/yozakura/auth/") && !source.contains("gq.yozakura.auth."));
        }
        assertTrue("The verifier must explicitly reject a leaked auth directory",
                pipeline.contains("StartsWith('gq/yozakura/auth/'"));
    }

    private static BatchResult runBatch(String... arguments) throws Exception {
        Path batch = Paths.get("obfuscate-client.bat").toAbsolutePath().normalize();
        assertTrue("Missing one-click batch entry point: " + batch, Files.isRegularFile(batch));

        Path externalWorkingDirectory = Files.createTempDirectory("yozakura-obfuscate-batch-test");
        try {
            String[] command = new String[5 + arguments.length];
            command[0] = "cmd.exe";
            command[1] = "/d";
            command[2] = "/c";
            command[3] = "call";
            command[4] = batch.toString();
            System.arraycopy(arguments, 0, command, 5, arguments.length);

            Process process = new ProcessBuilder(command)
                    .directory(externalWorkingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = readFully(process.getInputStream());
            int exitCode = process.waitFor();
            return new BatchResult(exitCode, output);
        } finally {
            Files.deleteIfExists(externalWorkingDirectory);
        }
    }

    private static String readFully(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void assumeWindows() {
        Assume.assumeTrue(File.separatorChar == '\\');
    }

    private static final class BatchResult {
        private final int exitCode;
        private final String output;

        private BatchResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
