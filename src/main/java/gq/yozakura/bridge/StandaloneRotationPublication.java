package gq.yozakura.bridge;

import java.util.concurrent.atomic.AtomicLong;

final class StandaloneRotationPublication {
    private static final Snapshot INACTIVE = new Snapshot(false, 0.0F, 0.0F, 0L);

    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong sentGeneration = new AtomicLong();
    private volatile Snapshot current = INACTIVE;

    void publish(boolean active, float yaw, float pitch) {
        current = active ? new Snapshot(true, yaw, pitch, sequence.incrementAndGet()) : INACTIVE;
    }

    Snapshot snapshot() {
        return current;
    }

    void clear() {
        current = INACTIVE;
    }

    void invalidateForTeleport() {
        current = INACTIVE;
    }

    boolean hasUnsentRotation() {
        Snapshot snapshot = current;
        return snapshot.active && snapshot.generation > sentGeneration.get();
    }

    void markSent(Snapshot snapshot) {
        if (snapshot == null || !snapshot.active) {
            return;
        }
        long sent = sentGeneration.get();
        while (snapshot.generation > sent
                && !sentGeneration.compareAndSet(sent, snapshot.generation)) {
            sent = sentGeneration.get();
        }
    }

    static final class Snapshot {
        private final boolean active;
        private final float yaw;
        private final float pitch;
        private final long generation;

        private Snapshot(boolean active, float yaw, float pitch, long generation) {
            this.active = active;
            this.yaw = yaw;
            this.pitch = pitch;
            this.generation = generation;
        }

        boolean isActive() {
            return active;
        }

        float getYaw() {
            return yaw;
        }

        float getPitch() {
            return pitch;
        }
    }
}
