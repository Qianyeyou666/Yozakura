package gq.yozakura.auth;

import gq.yozakura.auth.vendor.tech.skidonion.obfuscator.inline.Wrapper;

public final class NativeAuthBridge {
    private NativeAuthBridge() {
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    logout0();
                } catch (Throwable ignored) {
                }
            }
        }, "Yozakura Auth Logout"));
    }

    public static int login(String username, char[] password) {
        return login0(username, password,
                Wrapper.getClientBuildId(), Wrapper.getClientFingerprintForNative(),
                Wrapper.getMachineFingerprintForNative());
    }

    public static int redeemLicense(String licenseKey, String username, char[] password) {
        return redeemLicense0(licenseKey, username, password);
    }

    static boolean permitStartup() {
        try {
            return q0(System.nanoTime()) != 0L;
        } catch (UnsatisfiedLinkError error) {
            return false;
        }
    }

    static boolean permitModuleActivation() {
        try {
            return q0(System.nanoTime() ^ 0x41D2B7A9L) != 0L;
        } catch (UnsatisfiedLinkError error) {
            return false;
        }
    }

    static boolean permitTickDispatch() {
        return channelPermit(3);
    }

    static boolean permitRenderDispatch() {
        return channelPermit(7);
    }

    static boolean permitInputDispatch() {
        return channelPermit(11);
    }

    static boolean permitPacketDispatch() {
        return channelPermit(17);
    }

    static boolean permitEventDispatch() {
        return channelPermit(23);
    }

    static boolean permitMovementDispatch() {
        return channelPermit(29);
    }

    private static boolean channelPermit(int channel) {
        try {
            long probe = System.nanoTime();
            return q1(channel, probe) != 0L;
        } catch (UnsatisfiedLinkError error) {
            return false;
        }
    }

    public static String getVerifiedUsername() {
        if (!permitStartup()) {
            return null;
        }
        return username0();
    }

    public static String getVerifiedRole() {
        return permitStartup() ? role0() : null;
    }

    public static String getVerifiedExpiry() {
        return permitStartup() ? expiry0() : null;
    }

    private static native long q0(long probe);

    private static native long q1(int channel, long probe);

    private static native int login0(String username, char[] password,
                                     String buildId, String clientFingerprint,
                                     String machineFingerprint);

    private static native int redeemLicense0(String licenseKey,
                                             String username, char[] password);

    private static native void logout0();

    private static native String username0();

    private static native String role0();

    private static native String expiry0();
}
