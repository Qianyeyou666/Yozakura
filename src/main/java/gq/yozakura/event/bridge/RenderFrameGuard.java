package gq.vapulite.event.bridge;

public final class RenderFrameGuard {
    private static long standalone3DFrame;

    private RenderFrameGuard() {
    }

    public static long nextStandalone3DFrame() {
        return ++standalone3DFrame;
    }

    public static long currentStandalone3DFrame() {
        return standalone3DFrame;
    }
}
