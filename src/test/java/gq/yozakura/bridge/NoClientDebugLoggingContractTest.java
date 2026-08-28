package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class NoClientDebugLoggingContractTest {
    @Test
    public void javaClientDoesNotGenerateDebugLogs() throws IOException {
        final List<String> violations = new ArrayList<String>();
        Path root = Paths.get("src/main/java/gq/yozakura");
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                reject(file, source, violations, "YozakuraBridgeDebug.log");
                reject(file, source, violations, "YozakuraRotationDebug.log");
                reject(file, source, violations, "YozakuraEventBus.log");
                reject(file, source, violations, "YozakuraStandalone.log");
                reject(file, source, violations, "YozakuraBootstrap.log");
                reject(file, source, violations, "YozakuraModernForge.log");
                reject(file, source, violations, "YozakuraModernVisual.log");
                reject(file, source, violations, "YozakuraModuleInit.log");
                reject(file, source, violations, "YozakuraConfig.log");
                reject(file, source, violations, "YozakuraAuth.log");
                reject(file, source, violations, "YozakuraFont.log");
                reject(file, source, violations, "YozakuraChat.log");
                reject(file, source, violations, "YozakuraMusicPlayer.log");
                reject(file, source, violations, "YozakuraWebClickGui.log");
                reject(file, source, violations, "YozakuraModernWebClickGui.log");
                reject(file, source, violations, "System.out.");
                reject(file, source, violations, "System.err.");
                reject(file, source, violations, ".printStackTrace(");
                reject(file, source, violations, "BridgeDebug.");
                reject(file, source, violations, "RotationDebug.");
                return FileVisitResult.CONTINUE;
            }
        });
        assertTrue(violations.toString(), violations.isEmpty());
    }

    @Test
    public void nativeClientDoesNotGenerateDebugLogs() throws IOException {
        List<String> violations = new ArrayList<String>();
        for (String name : new String[]{"yozakura_loader.cpp", "yozakura_native_auth.cpp"}) {
            Path file = Paths.get("native", name);
            String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            reject(file, source, violations, "JarToDllLoader.log");
            reject(file, source, violations, "OutputDebugString");
            reject(file, source, violations, "authDebug(");
            reject(file, source, violations, "debugLastError(");
            reject(file, source, violations, "debug(");
        }
        assertTrue(violations.toString(), violations.isEmpty());
    }

    private static void reject(Path file, String source, List<String> violations, String forbidden) {
        if (source.contains(forbidden)) {
            violations.add(file + " contains " + forbidden);
        }
    }
}
