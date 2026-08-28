package gq.yozakura.bridge;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes a complete PRE rotation state to the Netty thread with one volatile write.
 */
final class ForgeRotationPublication {
    private static final Snapshot INACTIVE = new Snapshot(false, false, 0.0F, 0.0F, 0L);

    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong sentGeneration = new AtomicLong();
    private volatile Snapshot current = INACTIVE;
    private long invalidatedGeneration;

    synchronized Snapshot beginPre() {
        Snapshot next = new Snapshot(true, false, 0.0F, 0.0F, sequence.incrementAndGet());
        current = next;
        return next;
    }

    synchronized Snapshot publish(boolean active, float yaw, float pitch) {
        Snapshot previous = current;
        if (!previous.preInProgress || previous.generation <= 0L) {
            throw new IllegalStateException("Forge rotation PRE was not started");
        }
        boolean publishActive = active && previous.generation > invalidatedGeneration;
        Snapshot published = new Snapshot(false, publishActive, yaw, pitch, previous.generation);
        current = published;
        return published;
    }

    synchronized Snapshot abortPre() {
        Snapshot previous = current;
        if (!previous.preInProgress) {
            return previous;
        }
        Snapshot aborted = new Snapshot(false, false, 0.0F, 0.0F, previous.generation);
        current = aborted;
        return aborted;
    }

    Snapshot snapshot() {
        return current;
    }

    synchronized void invalidateForTeleport() {
        Snapshot previous = current;
        if (previous.generation > invalidatedGeneration) {
            invalidatedGeneration = previous.generation;
        }
        current = previous.preInProgress
                ? new Snapshot(true, false, 0.0F, 0.0F, previous.generation)
                : INACTIVE;
    }

    synchronized void clear() {
        current = INACTIVE;
        invalidatedGeneration = sequence.get();
    }

    void markSent(Snapshot snapshot) {
        if (snapshot == null || snapshot.preInProgress || snapshot.generation <= 0L) {
            return;
        }
        long sent = sentGeneration.get();
        while (snapshot.generation > sent
                && !sentGeneration.compareAndSet(sent, snapshot.generation)) {
            sent = sentGeneration.get();
        }
    }

    boolean isGenerationSent(long generation) {
        return generation <= 0L || generation <= sentGeneration.get();
    }

    long getSentGeneration() {
        return sentGeneration.get();
    }

    static final class Snapshot {
        private final boolean preInProgress;
        private final boolean active;
        private final float yaw;
        private final float pitch;
        private final long generation;

        private Snapshot(boolean preInProgress, boolean active, float yaw, float pitch, long generation) {
            this.preInProgress = preInProgress;
            this.active = active;
            this.yaw = yaw;
            this.pitch = pitch;
            this.generation = generation;
        }

        boolean isPreInProgress() {
            return preInProgress;
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

        long getGeneration() {
            return generation;
        }
    }
}
