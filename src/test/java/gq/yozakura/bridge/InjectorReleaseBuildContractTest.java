package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class InjectorReleaseBuildContractTest {
    @Test
    public void dllBuildCIsLocationIndependentAndValidatesItsOutput() throws IOException {
        String script = source("build-dll-release.bat");

        assertTrue(script.contains("pushd \"%~dp0\""));
        assertTrue(script.contains("set \"ACC_PRODUCT_CONFIG_V3=\""));
        assertTrue(script.contains("call \"build-native.bat\""));
        assertTrue(script.contains("Verify-NativePayload.ps1"));
        assertTrue(source("build-native.bat").contains("if errorlevel 1 exit /b 1"));
        assertTrue(script.contains("Get-FileHash -Algorithm SHA256"));
    }

    @Test
    public void releasePackageKeepsInjectorAndX64LoaderTogether() throws IOException {
        String script = source("package-injector-release.bat");

        assertTrue(script.contains("call \"build-dll-release.bat\""));
        assertTrue(script.contains("call \"build-injector-ui.bat\""));
        assertTrue(script.contains("%PACKAGE_DIR%\\YozakuraInjector.exe"));
        assertTrue(script.contains("%PACKAGE_DIR%\\YozakuraLoader-x64.dll"));
        assertTrue(script.contains("$m -ne 0x8664"));
        assertTrue(script.contains("SHA256SUMS.txt"));
        assertTrue(script.contains("Compress-Archive"));
        assertTrue(source("build-injector-ui.bat").contains("if errorlevel 1 exit /b 1"));
    }

    @Test
    public void graphicalInjectorSearchesItsOwnDirectoryFirst() throws IOException {
        String source = source("native/yozakura_injector_ui.cpp");
        int directory = source.indexOf("const std::wstring directory = executableDirectory()");
        int sameDirectoryLoader = source.indexOf("L\"\\\\YozakuraLoader-x64.dll\"", directory);
        int buildTreeLoader = source.indexOf("L\"\\\\build\\\\libs\\\\YozakuraLoader-x64.dll\"", directory);

        assertTrue(directory >= 0);
        assertTrue(sameDirectoryLoader > directory);
        assertTrue(buildTreeLoader > sameDirectoryLoader);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
