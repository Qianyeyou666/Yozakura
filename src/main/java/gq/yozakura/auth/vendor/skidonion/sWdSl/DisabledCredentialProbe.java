package gq.yozakura.auth.vendor.skidonion.sWdSl;

public class DisabledCredentialProbe {
    public static boolean probeEnabled;
    public DisabledCredentialProbe() {}
    private static Thread newDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
    private static void resetProbeFlag() {
        probeEnabled = false;
    }
}

