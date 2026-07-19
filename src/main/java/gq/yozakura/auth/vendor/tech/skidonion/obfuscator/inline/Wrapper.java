package gq.yozakura.auth.vendor.tech.skidonion.obfuscator.inline;

import gq.yozakura.auth.vendor.skidonion.sWdSl.Base64Codec;
import gq.yozakura.auth.vendor.skidonion.sWdSl.ChaChaStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Supplies only the local proof material consumed by the native authority. */
public final class Wrapper {
    private static final String MACHINE_ID_DIR = "yozakura";
    private static final String CLIENT_BUILD_ID = "local-20260615-secure-auth-2";
    private static volatile String clientFingerprint;
    private static volatile String machineFingerprint;

    private Wrapper() {
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
                Path path = Paths.get(System.getProperty("user.home"), MACHINE_ID_DIR, ".machine-id");
                if (Files.exists(path)) {
                    String saved = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII).trim();
                    if (saved.matches("[0-9a-f]{64}")) {
                        machineFingerprint = saved;
                        return saved;
                    }
                }
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                machineFingerprint = hexLower(random);
                Files.createDirectories(path.getParent());
                Files.write(path, machineFingerprint.getBytes(StandardCharsets.US_ASCII));
            } catch (Exception ignored) {
                machineFingerprint = "unavailable";
            }
            return machineFingerprint;
        }
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
                updateClassDigest(digest, "gq.yozakura.auth.NativeAuthBridge");
                updateClassDigest(digest, "gq.yozakura.auth.vendor.skidonion.sWdSl.VerificationPanel");
                updateClassDigest(digest, "gq.yozakura.auth.YozakuraAuthGate");
                updateClassDigest(digest, "gq.yozakura.core.Client");
                updateClassDigest(digest, "gq.yozakura.core.StandaloneClient");
                updateClassDigest(digest, "gq.yozakura.module.Module");
                updateClassDigest(digest, "gq.yozakura.event.bus.EventManager");
                updateClassDigest(digest, "gq.yozakura.event.api.EventManager");
                updateClassDigest(digest, "gq.yozakura.bridge.YozakuraEventBridge");
                updateClassDigest(digest, "gq.yozakura.bridge.StandaloneEventBridge");
                updateClassDigest(digest, "gq.yozakura.bridge.MovementInputBridge");
                updateClassDigest(digest, "gq.yozakura.ui.click.yozakura.YozakuraClickGui");
                clientFingerprint = hexLower(digest.digest());
            } catch (Exception ignored) {
                clientFingerprint = "unavailable";
            }
            return clientFingerprint;
        }
    }

    private static void updateClassDigest(MessageDigest digest, Class<?> type) throws IOException {
        updateClassDigest(digest, type.getName());
    }

    private static void updateClassDigest(MessageDigest digest, String className) throws IOException {
        String resource = "/" + className.replace('.', '/') + ".class";
        try (InputStream input = Wrapper.class.getResourceAsStream(resource)) {
            if (input == null) {
                digest.update(className.getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
    }

    private static String hexLower(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            String hex = Integer.toHexString(value & 0xff);
            if (hex.length() == 1) {
                output.append('0');
            }
            output.append(hex);
        }
        return output.toString();
    }
}
