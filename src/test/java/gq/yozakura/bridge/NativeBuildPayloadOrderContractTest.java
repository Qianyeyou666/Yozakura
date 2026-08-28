package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeBuildPayloadOrderContractTest {
    @Test
    public void packagesBothArchitecturesBeforeEmbeddingTheFinalRuntimeJar() throws IOException {
        String script = new String(Files.readAllBytes(Paths.get("build-native.bat")), StandardCharsets.UTF_8);

        int themidaPrepareX64 = script.indexOf("call :prepare_webview2 x64");
        int prepareX64 = script.indexOf("call :prepare_webview2 x64", themidaPrepareX64 + 1);
        int prepareX86 = script.indexOf("call :prepare_webview2 x86", prepareX64 + 1);
        int buildX64 = script.indexOf("call :build_one x64", prepareX86 + 1);
        int buildX86 = script.indexOf("call :build_one x86", buildX64 + 1);
        int verifyX64 = script.indexOf("-Dll \"build\\libs\\YozakuraLoader-x64.dll\"");
        int verifyX86 = script.indexOf("-Dll \"build\\libs\\YozakuraLoader-x86.dll\"");

        assertTrue(themidaPrepareX64 >= 0);
        assertTrue(prepareX64 >= 0);
        assertTrue(prepareX86 > prepareX64);
        assertTrue(buildX64 > prepareX86);
        assertTrue(buildX86 > buildX64);
        assertTrue(verifyX64 > buildX86);
        assertTrue(verifyX86 > buildX86);

        int buildOne = script.indexOf(":build_one");
        int prepareWebView = script.indexOf(":prepare_webview2");
        assertTrue(buildOne >= 0);
        assertTrue(prepareWebView > buildOne);
        assertFalse(script.substring(buildOne, prepareWebView).contains("call :package_webview2"));
    }
}
