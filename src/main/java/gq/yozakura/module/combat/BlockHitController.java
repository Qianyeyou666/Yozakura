package gq.yozakura.module.combat;

/**
 * Records accepted action packets and advances the owned use cycle only when
 * the bridge confirms a player-movement write.
 */
final class BlockHitController {
    static final long NO_CYCLE = 0L;

    enum PacketKind {
        MOVEMENT,
        ATTACK,
        USE_ITEM,
        RELEASE_USE_ITEM,
        ANIMATION,
        CONTEXT_CHANGED,
        OTHER
    }

    static final class Snapshot {
        private final long movementEpoch;
        private final long lastAttackEpoch;
        private final boolean observedUseActive;
        private final boolean requestedUseActive;

        private Snapshot(long movementEpoch, long lastAttackEpoch,
                         boolean observedUseActive, boolean requestedUseActive) {
            this.movementEpoch = movementEpoch;
            this.lastAttackEpoch = lastAttackEpoch;
            this.observedUseActive = observedUseActive;
            this.requestedUseActive = requestedUseActive;
        }

        long getMovementEpoch() {
            return movementEpoch;
        }

        long getLastAttackEpoch() {
            return lastAttackEpoch;
        }

        boolean isObservedUseActive() {
            return observedUseActive;
        }

        boolean isRequestedUseActive() {
            return requestedUseActive;
        }
    }

    private long movementEpoch;
    private long lastAttackEpoch = -1L;
    private long nextCycleId;
    private long useArmEpoch = -1L;
    private long useArmCycleId = NO_CYCLE;
    private long requestedUseCycleId = NO_CYCLE;
    private long useWriteEpoch = -1L;
    private boolean observedUseActive;
    private boolean useRequestPending;
    private boolean requestedUseActive;
    private boolean useWriteConfirmed;
    private boolean releaseRequestPending;

    synchronized Snapshot observe(PacketKind kind) {
        if (kind == null) {
            return snapshot();
        }

        switch (kind) {
            case ATTACK:
                lastAttackEpoch = movementEpoch;
                break;
            case USE_ITEM:
                observedUseActive = true;
                break;
            case RELEASE_USE_ITEM:
                observedUseActive = false;
                break;
            case MOVEMENT:
            case ANIMATION:
            case CONTEXT_CHANGED:
            case OTHER:
            default:
                break;
        }
        return snapshot();
    }

    synchronized boolean armUseAfterMovementBoundary() {
        if (useArmEpoch >= 0L || useRequestPending || requestedUseActive || releaseRequestPending) {
            return false;
        }
        useArmEpoch = movementEpoch;
        useArmCycleId = ++nextCycleId;
        return true;
    }

    synchronized Snapshot confirmMovementBoundary() {
        movementEpoch++;
        if (useArmEpoch >= 0L && movementEpoch > useArmEpoch) {
            useArmEpoch = -1L;
            useRequestPending = true;
        }
        if (requestedUseActive && useWriteConfirmed && movementEpoch > useWriteEpoch) {
            releaseRequestPending = true;
        }
        return snapshot();
    }

    synchronized boolean consumeUseRequest() {
        if (!useRequestPending) {
            return false;
        }
        useRequestPending = false;
        requestedUseActive = true;
        requestedUseCycleId = useArmCycleId;
        useArmCycleId = NO_CYCLE;
        useWriteEpoch = -1L;
        useWriteConfirmed = false;
        return true;
    }

    synchronized void confirmUseWritten() {
        confirmUseWritten(requestedUseCycleId);
    }

    synchronized boolean confirmUseWritten(long cycleId) {
        if (!requestedUseActive || cycleId == NO_CYCLE || cycleId != requestedUseCycleId) {
            return false;
        }
        useWriteConfirmed = true;
        useWriteEpoch = movementEpoch;
        return true;
    }

    synchronized long getRequestedUseCycleId() {
        return requestedUseCycleId;
    }

    synchronized boolean hasReleaseRequest() {
        return releaseRequestPending;
    }

    synchronized boolean cancelRequestedUseCycle(long cycleId) {
        if (cycleId == NO_CYCLE || cycleId != requestedUseCycleId) {
            return false;
        }
        cancelCycle();
        return true;
    }

    synchronized boolean consumeReleaseRequest() {
        if (!releaseRequestPending) {
            return false;
        }
        releaseRequestPending = false;
        requestedUseActive = false;
        requestedUseCycleId = NO_CYCLE;
        useWriteEpoch = -1L;
        useWriteConfirmed = false;
        return true;
    }

    synchronized void cancelCycle() {
        useArmEpoch = -1L;
        useArmCycleId = NO_CYCLE;
        useRequestPending = false;
        requestedUseActive = false;
        requestedUseCycleId = NO_CYCLE;
        useWriteEpoch = -1L;
        useWriteConfirmed = false;
        releaseRequestPending = false;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(movementEpoch, lastAttackEpoch, observedUseActive, requestedUseActive);
    }

    synchronized void reset() {
        movementEpoch = 0L;
        lastAttackEpoch = -1L;
        observedUseActive = false;
        cancelCycle();
    }
}
