package gq.yozakura.module.player;

final class ChestAccessTracker {
    private final long interactionTimeoutMillis;
    private long pendingInteractionMillis = -1L;
    private int authorizedWindowId = -1;

    ChestAccessTracker(long interactionTimeoutMillis) {
        this.interactionTimeoutMillis = interactionTimeoutMillis;
    }

    void recordPhysicalInteraction(long nowMillis) {
        pendingInteractionMillis = nowMillis;
    }

    boolean authorizeWindow(int windowId, long nowMillis) {
        if (isAuthorizedWindow(windowId)) {
            return true;
        }
        if (pendingInteractionMillis < 0L
                || nowMillis < pendingInteractionMillis
                || nowMillis - pendingInteractionMillis > interactionTimeoutMillis) {
            pendingInteractionMillis = -1L;
            return false;
        }
        authorizedWindowId = windowId;
        pendingInteractionMillis = -1L;
        return true;
    }

    boolean isAuthorizedWindow(int windowId) {
        return authorizedWindowId == windowId;
    }

    void clearAuthorizedWindow() {
        authorizedWindowId = -1;
    }

    void reset() {
        pendingInteractionMillis = -1L;
        authorizedWindowId = -1;
    }
}
