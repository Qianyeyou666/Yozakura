package gq.yozakura.module.combat;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Transfers successful server-visible movement rotations from the Netty write
 * callback to KillAura's next client PRE update without reordering combat
 * packets behind the current movement packet.
 */
final class KillAuraConfirmedRotationTracker {
    private static final float ROTATION_EPSILON = 0.001F;

    private final AtomicReference<Claim> pending = new AtomicReference<Claim>();
    private final ConcurrentLinkedQueue<Boundary> accepted =
            new ConcurrentLinkedQueue<Boundary>();
    private Rotation confirmed;

    void claim(float yaw, float pitch) {
        confirmed = null;
        pending.set(new Claim(yaw, pitch));
    }

    void acceptBoundary(boolean packetAccepted, boolean rotated, float yaw, float pitch) {
        if (!packetAccepted) {
            return;
        }
        Claim claimed = pending.getAndSet(null);
        boolean owned = rotated && claimed != null
                && Math.abs(wrapAngleTo180(yaw - claimed.yaw)) <= ROTATION_EPSILON
                && Math.abs(pitch - claimed.pitch) <= ROTATION_EPSILON;
        accepted.offer(new Boundary(owned, yaw, pitch));
    }

    void drain(boolean enabled, boolean confirmationRequired) {
        Boundary next;
        Boundary latest = null;
        while ((next = accepted.poll()) != null) {
            latest = next;
        }
        if (latest != null) {
            confirmed = latest.owned ? new Rotation(latest.yaw, latest.pitch) : null;
        }
        if (!enabled || !confirmationRequired) {
            confirmed = null;
            pending.set(null);
            accepted.clear();
        }
    }

    Rotation getConfirmed() {
        return confirmed;
    }

    private static float wrapAngleTo180(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) {
            angle -= 360.0F;
        }
        if (angle < -180.0F) {
            angle += 360.0F;
        }
        return angle;
    }

    void clear() {
        pending.set(null);
        accepted.clear();
        confirmed = null;
    }

    static final class Rotation {
        final float yaw;
        final float pitch;

        Rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class Claim {
        private final float yaw;
        private final float pitch;

        private Claim(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class Boundary {
        private final boolean owned;
        private final float yaw;
        private final float pitch;

        private Boundary(boolean owned, float yaw, float pitch) {
            this.owned = owned;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
