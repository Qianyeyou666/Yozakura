package gq.yozakura.k.vendor.skidonion.sWdSl;

public class K {
    public static boolean probeEnabled;
    public K() {}
    private static Thread newDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
    private static void resetProbeFlag() {
        probeEnabled = false;
    }
}

