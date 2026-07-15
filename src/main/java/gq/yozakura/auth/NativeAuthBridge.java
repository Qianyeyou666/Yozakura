package gq.yozakura.auth;

import gq.yozakura.auth.vendor.tech.skidonion.obfuscator.inline.Wrapper;

public final class NativeAuthBridge {
    private static final long RUNTIME_ID = 0x594F5A414B555241L;

    private NativeAuthBridge() {
    }

    public static int login(String username, char[] password) {
        requireNativeRuntime();
        return login0(Wrapper.getServiceBaseUrl(), username, password,
                Wrapper.getClientBuildId(), Wrapper.getClientFingerprintForNative(),
                Wrapper.getMachineFingerprintForNative());
    }

    public static boolean isVerifiedSession() {
        try {
            return runtimeId0() == RUNTIME_ID && isVerified0();
        } catch (UnsatisfiedLinkError error) {
            return false;
        }
    }

    public static String getVerifiedUsername() {
        if (!isVerifiedSession()) {
            return null;
        }
        return username0();
    }

    public static String getVerifiedRole() {
        return isVerifiedSession() ? role0() : null;
    }

    public static String getVerifiedExpiry() {
        return isVerifiedSession() ? expiry0() : null;
    }

    private static void requireNativeRuntime() {
        try {
            if (runtimeId0() == RUNTIME_ID) {
                return;
            }
        } catch (UnsatisfiedLinkError ignored) {
        }
        throw new IllegalStateException("Yozakura native authentication runtime is unavailable");
    }

    private static native long runtimeId0();

    private static native int login0(String baseUrl, String username, char[] password,
                                     String buildId, String clientFingerprint,
                                     String machineFingerprint);

    private static native boolean isVerified0();

    private static native String username0();

    private static native String role0();

    private static native String expiry0();
}
