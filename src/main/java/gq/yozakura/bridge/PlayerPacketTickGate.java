package gq.yozakura.bridge;

/**
 * Event-loop-only gate for the one canonical player packet expected after a
 * completed client tick. A newer marker replaces an unused one because
 * vanilla may legitimately skip a movement packet for a tick.
 */
final class PlayerPacketTickGate {
    private long pendingGeneration;
    private long consumedGeneration;

    void markNextPlayerPacket(long generation) {
        if (generation > consumedGeneration && generation > pendingGeneration) {
            pendingGeneration = generation;
        }
    }

    boolean consumeNextPlayerPacket() {
        long generation = pendingGeneration;
        pendingGeneration = 0L;
        if (generation <= consumedGeneration) {
            return false;
        }
        consumedGeneration = generation;
        return true;
    }

    boolean consumeNextCanonicalPlayerPacket(boolean canonicalPlayerPacket) {
        return canonicalPlayerPacket && consumeNextPlayerPacket();
    }

    void invalidatePending() {
        pendingGeneration = 0L;
    }

    void clear() {
        pendingGeneration = 0L;
        consumedGeneration = 0L;
    }
}
