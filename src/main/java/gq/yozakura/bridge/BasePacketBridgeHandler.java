package gq.yozakura.bridge;

import gq.yozakura.k.B;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.PlayerPacketBoundaryEvent;
import gq.yozakura.event.bridge.TeleportBoundaryEvent;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.manager.BridgeDebug;
import gq.yozakura.manager.RotationDebug;
import gq.yozakura.runtime.YozakuraRuntime;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

abstract class BasePacketBridgeHandler<S, D> extends ChannelDuplexHandler {
    protected static final float ROTATION_EPSILON = 1.0E-3F;

    protected final OutboundActionBatchQueue<D> delayedPackets =
            new OutboundActionBatchQueue<D>();
    protected final Queue<D> afterCurrentRotationPackets = new ArrayDeque<D>();
    protected final PlayerPacketTickGate playerPacketTickGate = new PlayerPacketTickGate();
    protected volatile ChannelHandlerContext handlerContext;
    protected long pendingPlayerPacketGeneration;
    protected int currentClickWindowPackets;
    protected int readyClickWindowPackets;
    protected boolean hasSentSilentRotation;
    protected float lastSilentYaw;
    protected float lastSilentPitch;
    protected boolean teleportConfirmationPending;

    protected abstract String getBridgeType();

    protected abstract boolean isBridgeTerminated();

    protected abstract S getRotationSnapshot();

    protected abstract boolean isPreUpdatePending();

    protected abstract boolean isPacketPendingPost();

    protected abstract D createDelayedPacket(Packet<?> packet, ChannelPromise promise, long writeId);

    protected abstract Packet<?> getDelayedPacketPacket(D delayed);

    protected abstract ChannelPromise getDelayedPacketPromise(D delayed);

    protected abstract long getDelayedPacketWriteId(D delayed);

    protected abstract boolean isDelayedPacketPendingPost(D delayed);

    protected abstract boolean isRotationActive(S snapshot);

    protected abstract float getRotationYaw(S snapshot);

    protected abstract float getRotationPitch(S snapshot);

    protected abstract long getRotationGeneration(S snapshot);

    protected abstract void markRotationSent(S snapshot);

    protected abstract void onHandlerRemoved(ChannelHandlerContext ctx);

    protected abstract void onChannelInactive(ChannelHandlerContext ctx);

    protected abstract void onResetHandlerState();

    protected abstract void onAcceptedTeleportBoundary();

    protected abstract boolean shouldDelayPlayerPacket(S snapshot);

    protected abstract void handleDelayedPlayerPacket(C03PacketPlayer packet, ChannelPromise promise,
                                                      S snapshot, long writeId, boolean nonCanonicalPlayerPacket);

    protected abstract void writePlayerPacketInternal(ChannelHandlerContext ctx, C03PacketPlayer packet,
                                                       ChannelPromise promise, S snapshot, long writeId,
                                                       boolean canonicalPlayerPacket,
                                                       boolean preservePlayerLook) throws Exception;

    protected abstract void logActionQueue(Packet<?> packet, boolean pendingPost);

    protected abstract void logActionReadyQueue(Packet<?> packet, boolean pendingPost);

    protected abstract void logActionOut(Packet<?> packet, boolean pendingPost, String source);

    protected abstract void logActionBlinkBuffered(Packet<?> packet, boolean pendingPost, String source);

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        handlerContext = ctx;
        super.handlerAdded(ctx);
        drainPendingPlayerPacketTick(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (isBridgeTerminated() || !ctx.channel().isActive()) {
            completeFailedWrite(promise, new ClosedChannelException());
            return;
        }
        if (!B.permitPacketDispatch()) {
            super.write(ctx, msg, promise);
            return;
        }
        if (msg instanceof Packet<?>) {
            Packet<?> packet = (Packet<?>) msg;
            boolean nonCanonicalPlayerPacket = packet instanceof C03PacketPlayer
                    && PacketBridgeSupport.consumeNonCanonicalPlayerPacket(packet);
            boolean preservePlayerLook = packet instanceof C03PacketPlayer
                    && PacketBridgeSupport.consumePreservePlayerLook(packet);
            boolean packetPendingPost = isPacketPendingPost();
            PacketBridgeSupport.NoEventMarker noEventMarker =
                    PacketBridgeSupport.consumeNoEventMarker(packet);
            if (noEventMarker.isAlreadyBridgeProcessed()) {
                super.write(ctx, msg, promise);
                return;
            }
            boolean skipPacketEvent = noEventMarker.isMarked();
            long writeId = noEventMarker.getWriteId();
            boolean preserveOriginalPacketOrder = true;
            boolean afterCurrentRotation = false;
            if (skipPacketEvent) {
                BridgeDebug.logPacket(getBridgeType(), "SEND_NO_EVENT", packet, packetPendingPost);
            }
            if (!skipPacketEvent) {
                BridgeDebug.logPacket(getBridgeType(), "SEND_IN", packet, packetPendingPost);
                PacketEvent event = EventManager.call(new PacketEvent(EventType.SEND, packet));
                if (event.isCancelled()) {
                    BridgeDebug.logPacket(getBridgeType(), "SEND_CANCELLED", packet, packetPendingPost);
                    completeDroppedWrite(promise);
                    return;
                }
                PacketAcceptedEvent accepted = new PacketAcceptedEvent(packet);
                EventManager.call(accepted);
                writeId = accepted.getWriteId();
                afterCurrentRotation = accepted.isAfterCurrentRotationRequired();
                preserveOriginalPacketOrder = !afterCurrentRotation
                        && (preserveOriginalPacketOrder || accepted.isOriginalPacketOrderRequired());
            }

            observePacketWrite(ctx, packet, promise, writeId);
            if (!skipPacketEvent && afterCurrentRotation && isPostSensitiveAction(packet)) {
                queueAfterCurrentRotationPacket(packet, promise, writeId);
                return;
            }
            if (!skipPacketEvent && !preserveOriginalPacketOrder && isPostSensitiveAction(packet)) {
                queueCurrentActionPacket(packet, promise, writeId);
                return;
            }
            if (!skipPacketEvent && packet instanceof net.minecraft.network.play.client.C0DPacketCloseWindow) {
                if (currentClickWindowPackets > 0) {
                    queueCurrentActionPacket(packet, promise, writeId);
                    return;
                }
                if (readyClickWindowPackets > 0) {
                    queueReadyActionPacket(packet, promise, writeId);
                    return;
                }
            }
            S rotation = getRotationSnapshot();
            if (packet instanceof C03PacketPlayer) {
                C03PacketPlayer playerPacket = (C03PacketPlayer) packet;
                if (!preservePlayerLook && !isTeleportConfirmation(playerPacket)
                        && shouldDelayPlayerPacket(rotation)) {
                    handleDelayedPlayerPacket(playerPacket, promise, rotation, writeId,
                            nonCanonicalPlayerPacket);
                    return;
                }
                writePlayerPacketInternal(ctx, (C03PacketPlayer) packet, promise, rotation, writeId,
                        !nonCanonicalPlayerPacket, preservePlayerLook);
                return;
            }

            markSent(packet);
            BridgeDebug.logPacket(getBridgeType(), "SEND_MARKED", packet, packetPendingPost);
            if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                    && YozakuraRuntime.blinkManager.offerPacket(packet, promise, writeId)) {
                BridgeDebug.logPacket(getBridgeType(), "SEND_BLINK_BUFFERED", packet, packetPendingPost);
                return;
            }
            BridgeDebug.logPacket(getBridgeType(), "SEND_OUT", packet, packetPendingPost);
        }
        super.write(ctx, msg, promise);
    }

    protected void observePacketWrite(ChannelHandlerContext ctx, final Packet<?> packet,
                                      ChannelPromise promise, final long writeId) {
        if (ctx == null || packet == null || promise == null) {
            return;
        }
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                reportPacketWrite(packet, writeId,
                        PacketWriteDisposition.isServerVisibleSuccess(future));
            }
        });
    }

    protected void reportPacketWrite(Packet<?> packet, long writeId, boolean success) {
        EventManager.call(new PacketWriteEvent(packet, writeId, success));
    }

    protected void reportPlayerPacketBoundary(ChannelPromise promise, final long writeId,
                                              final boolean playerTickAdvanced,
                                              final float serverYaw, final float serverPitch,
                                              final boolean rotated) {
        if (!playerTickAdvanced || promise == null) {
            return;
        }
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (!PacketWriteDisposition.isServerVisibleSuccess(future)
                        || writeId == PacketAcceptedEvent.NO_WRITE_ID) {
                    return;
                }
                EventManager.call(new PlayerPacketBoundaryEvent(writeId, serverYaw, serverPitch, rotated));
            }
        });
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            failDelayedPackets(new ClosedChannelException());
            resetHandlerState();
            onHandlerRemoved(ctx);
        } finally {
            super.handlerRemoved(ctx);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        failDelayedPackets(new ClosedChannelException());
        resetHandlerState();
        onChannelInactive(ctx);
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isBridgeTerminated() || !ctx.channel().isActive()) {
            return;
        }
        if (!B.permitPacketDispatch()) {
            super.channelRead(ctx, msg);
            return;
        }
        if (msg instanceof Packet<?>) {
            Packet<?> packet = (Packet<?>) msg;
            PacketEvent event = EventManager.call(new PacketEvent(EventType.RECEIVE, packet));
            if (event.isCancelled()) {
                return;
            }
            if (packet instanceof S08PacketPlayerPosLook) {
                handleAcceptedTeleportBoundary((S08PacketPlayerPosLook) packet);
            }
        }
        super.channelRead(ctx, msg);
    }

    private void handleAcceptedTeleportBoundary(S08PacketPlayerPosLook packet) {
        synchronized (this) {
            pendingPlayerPacketGeneration = 0L;
        }
        playerPacketTickGate.invalidatePending();
        teleportConfirmationPending = true;
        hasSentSilentRotation = false;
        discardDelayedPacketsForTeleport();
        onAcceptedTeleportBoundary();
        MovementInputBridge.restoreRotation();
        EventManager.call(new TeleportBoundaryEvent(packet));
    }

    protected void writePlayerPacketCommon(ChannelHandlerContext ctx, C03PacketPlayer packet, ChannelPromise promise,
                                           S snapshot, long writeId, boolean canonicalPlayerPacket,
                                           boolean preUpdatePending, boolean preservePlayerLook) throws Exception {
        boolean teleportConfirmation = consumeTeleportConfirmation(packet);
        if (teleportConfirmation) {
            snapshot = getRotationSnapshot();
        }
        boolean playerTickAdvanced = playerPacketTickGate.consumeNextCanonicalPlayerPacket(canonicalPlayerPacket);
        boolean boundaryRotated = isRotationActive(snapshot);
        float boundaryYaw = getRotationYaw(snapshot);
        float boundaryPitch = getRotationPitch(snapshot);
        reportPlayerPacketBoundary(promise, writeId, playerTickAdvanced,
                boundaryYaw, boundaryPitch, boundaryRotated);
        if (playerTickAdvanced) {
            flushReadyActionPackets(ctx);
        }
        if (playerTickAdvanced && !preUpdatePending && !isRotationActive(snapshot)) {
            flushCurrentActionPackets(ctx);
        }

        if (teleportConfirmation) {
            markSent(packet);
            BridgeDebug.logPacket(getBridgeType(), "SEND_TELEPORT_CONFIRMATION", packet, isPacketPendingPost());
            RotationDebug.logPacket(getBridgeType(), packet, false);
            hasSentSilentRotation = false;
            if (YozakuraRuntime.blinkManager != null
                    && YozakuraRuntime.blinkManager.offerPacket(packet, promise, writeId)) {
                BridgeDebug.logPacket(getBridgeType(), "SEND_TELEPORT_CONFIRMATION_BLINK_BUFFERED",
                        packet, isPacketPendingPost());
            } else {
                super.write(ctx, packet, promise);
            }
            markRotationSent(snapshot);
            return;
        }

        if (preservePlayerLook) {
            markSent(packet);
            BridgeDebug.logPacket(getBridgeType(), "SEND_PRESERVED_PLAYER_LOOK", packet,
                    isPacketPendingPost());
            RotationDebug.logPacket(getBridgeType(), packet, false);
            if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                    && YozakuraRuntime.blinkManager.offerPacket(packet, promise, writeId)) {
                BridgeDebug.logPacket(getBridgeType(), "SEND_PRESERVED_PLAYER_LOOK_BLINK_BUFFERED",
                        packet, isPacketPendingPost());
            } else {
                super.write(ctx, packet, promise);
            }
            return;
        }

        if (isRotationActive(snapshot)) {
            markSent(packet);
            BridgeDebug.logPacket(getBridgeType(), "SEND_MARKED", packet, isPacketPendingPost());

            C03PacketPlayer rewritten = rewritePlayerPacket(packet, snapshot);
            RotationDebug.logPacket(getBridgeType(), packet, true);
            BridgeDebug.logPacketRewrite(getBridgeType(), packet, rewritten, isPacketPendingPost());
            if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                    && YozakuraRuntime.blinkManager.offerPacket(rewritten, promise, writeId)) {
                BridgeDebug.logPacket(getBridgeType(), "SEND_BLINK_BUFFERED", rewritten, isPacketPendingPost());
            } else {
                super.write(ctx, rewritten, promise);
            }
            markRotationSent(snapshot);
            if (playerTickAdvanced) {
                flushAfterCurrentRotationPackets(ctx);
            }
            if (!preUpdatePending && playerTickAdvanced) {
                promoteCurrentActionPackets();
            }
            return;
        }

        markSent(packet);
        BridgeDebug.logPacket(getBridgeType(), "SEND_MARKED", packet, isPacketPendingPost());
        RotationDebug.logPacket(getBridgeType(), packet, false);
        hasSentSilentRotation = false;
        if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                && YozakuraRuntime.blinkManager.offerPacket(packet, promise, writeId)) {
            BridgeDebug.logPacket(getBridgeType(), "SEND_BLINK_BUFFERED", packet, isPacketPendingPost());
        } else {
            super.write(ctx, packet, promise);
        }
        markRotationSent(snapshot);
    }

    private boolean isTeleportConfirmation(C03PacketPlayer packet) {
        return teleportConfirmationPending && packet != null && packet.isMoving();
    }

    private boolean consumeTeleportConfirmation(C03PacketPlayer packet) {
        if (!isTeleportConfirmation(packet)) {
            return false;
        }
        teleportConfirmationPending = false;
        return true;
    }

    void markNextPlayerPacketTick(final long generation) {
        if (generation <= 0L) {
            return;
        }
        storePendingPlayerPacketGeneration(generation);
        ChannelHandlerContext current = handlerContext;
        if (current == null) {
            current = handlerContext;
            if (current == null) {
                return;
            }
        }
        final ChannelHandlerContext ctx = current;
        Runnable markerTask = new Runnable() {
            @Override
            public void run() {
                drainPendingPlayerPacketTick(ctx);
            }
        };
        if (ctx.executor().inEventLoop()) {
            markerTask.run();
        } else {
            ctx.executor().execute(markerTask);
        }
    }

    protected synchronized void storePendingPlayerPacketGeneration(long generation) {
        if (generation > pendingPlayerPacketGeneration) {
            pendingPlayerPacketGeneration = generation;
        }
    }

    protected void drainPendingPlayerPacketTick(ChannelHandlerContext ctx) {
        if (ctx == null || handlerContext != ctx || !ctx.channel().isActive()) {
            return;
        }
        long generation;
        synchronized (this) {
            generation = pendingPlayerPacketGeneration;
            pendingPlayerPacketGeneration = 0L;
        }
        if (generation > 0L) {
            playerPacketTickGate.markNextPlayerPacket(generation);
        }
    }

    protected C03PacketPlayer rewritePlayerPacket(C03PacketPlayer packet, S rotation) {
        float yaw = getRotationYaw(rotation);
        float pitch = getRotationPitch(rotation);
        boolean sendLook = shouldSendLook(yaw, pitch);

        if (sendLook) {
            hasSentSilentRotation = true;
            lastSilentYaw = yaw;
            lastSilentPitch = pitch;
            if (packet.isMoving()) {
                return new C03PacketPlayer.C06PacketPlayerPosLook(packet.getPositionX(), packet.getPositionY(),
                        packet.getPositionZ(), yaw, pitch, packet.isOnGround());
            }
            return new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, packet.isOnGround());
        }
        if (packet.isMoving()) {
            return new C03PacketPlayer.C04PacketPlayerPosition(packet.getPositionX(), packet.getPositionY(),
                    packet.getPositionZ(), packet.isOnGround());
        }
        return new C03PacketPlayer(packet.isOnGround());
    }

    protected boolean shouldSendLook(float yaw, float pitch) {
        return !hasSentSilentRotation
                || Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - lastSilentYaw))
                > ROTATION_EPSILON
                || Math.abs(pitch - lastSilentPitch) > ROTATION_EPSILON;
    }

    protected boolean isPostSensitiveAction(Packet<?> packet) {
        return packet instanceof net.minecraft.network.play.client.C02PacketUseEntity
                || packet instanceof net.minecraft.network.play.client.C07PacketPlayerDigging
                || packet instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
                || packet instanceof net.minecraft.network.play.client.C09PacketHeldItemChange
                || packet instanceof net.minecraft.network.play.client.C0APacketAnimation
                || packet instanceof net.minecraft.network.play.client.C0EPacketClickWindow;
    }

    protected void queueAfterCurrentRotationPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
        afterCurrentRotationPackets.add(createDelayedPacket(packet, promise, writeId));
        logActionQueue(packet, isPacketPendingPost());
    }

    protected void flushAfterCurrentRotationPackets(ChannelHandlerContext ctx) throws Exception {
        D delayed;
        while ((delayed = afterCurrentRotationPackets.poll()) != null) {
            writeQueuedActionPacket(ctx, delayed, "after-current-rotation");
        }
    }

    protected void queueCurrentActionPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
        delayedPackets.addCurrent(createDelayedPacket(packet, promise, writeId));
        if (isClickWindowPacket(packet)) {
            currentClickWindowPackets++;
        }
        logActionQueue(packet, isPacketPendingPost());
    }

    protected void queueReadyActionPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
        delayedPackets.addReady(createDelayedPacket(packet, promise, writeId));
        if (isClickWindowPacket(packet)) {
            readyClickWindowPackets++;
        }
        logActionReadyQueue(packet, isPacketPendingPost());
    }

    protected void flushReadyActionPackets(ChannelHandlerContext ctx) throws Exception {
        D delayed;
        while ((delayed = delayedPackets.pollReady()) != null) {
            consumeReadyClickWindowPacket(getDelayedPacketPacket(delayed));
            writeQueuedActionPacket(ctx, delayed, "ready");
        }
    }

    protected void flushCurrentActionPackets(ChannelHandlerContext ctx) throws Exception {
        D delayed;
        while ((delayed = delayedPackets.pollCurrent()) != null) {
            consumeCurrentClickWindowPacket(getDelayedPacketPacket(delayed));
            writeQueuedActionPacket(ctx, delayed, "current");
        }
    }

    protected void promoteCurrentActionPackets() {
        delayedPackets.promoteCurrent();
        readyClickWindowPackets += currentClickWindowPackets;
        currentClickWindowPackets = 0;
    }

    protected boolean isClickWindowPacket(Packet<?> packet) {
        return packet instanceof net.minecraft.network.play.client.C0EPacketClickWindow;
    }

    protected void consumeReadyClickWindowPacket(Packet<?> packet) {
        if (isClickWindowPacket(packet) && readyClickWindowPackets > 0) {
            readyClickWindowPackets--;
        }
    }

    protected void consumeCurrentClickWindowPacket(Packet<?> packet) {
        if (isClickWindowPacket(packet) && currentClickWindowPackets > 0) {
            currentClickWindowPackets--;
        }
    }

    protected void writeQueuedActionPacket(ChannelHandlerContext ctx, D delayed, String source)
            throws Exception {
        Packet<?> packet = getDelayedPacketPacket(delayed);
        ChannelPromise promise = getDelayedPacketPromise(delayed);
        long writeId = getDelayedPacketWriteId(delayed);
        boolean pendingPost = isDelayedPacketPendingPost(delayed);
        markSent(packet);
        logActionOut(packet, pendingPost, source);
        if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                && YozakuraRuntime.blinkManager.offerPacket(packet, promise, writeId)) {
            logActionBlinkBuffered(packet, pendingPost, source);
            return;
        }
        super.write(ctx, packet, promise);
    }

    protected void completeDroppedWrite(ChannelPromise promise) {
        PacketWriteDisposition.completeDropped(promise);
    }

    protected void completeFailedWrite(ChannelPromise promise, Throwable cause) {
        if (promise != null) {
            promise.tryFailure(cause);
        }
    }

    protected void discardDelayedPacketsForTeleport() {
        D delayed;
        while ((delayed = afterCurrentRotationPackets.poll()) != null) {
            completeDroppedWrite(getDelayedPacketPromise(delayed));
        }
        while ((delayed = delayedPackets.pollReady()) != null) {
            completeDroppedWrite(getDelayedPacketPromise(delayed));
        }
        while ((delayed = delayedPackets.pollCurrent()) != null) {
            completeDroppedWrite(getDelayedPacketPromise(delayed));
        }
        delayedPackets.clear();
        currentClickWindowPackets = 0;
        readyClickWindowPackets = 0;
    }

    protected void failDelayedPackets(Throwable cause) {
        D delayed;
        while ((delayed = afterCurrentRotationPackets.poll()) != null) {
            ChannelPromise promise = getDelayedPacketPromise(delayed);
            if (promise != null) {
                promise.tryFailure(cause);
            }
        }
        while ((delayed = delayedPackets.pollReady()) != null) {
            ChannelPromise promise = getDelayedPacketPromise(delayed);
            if (promise != null) {
                promise.tryFailure(cause);
            }
        }
        while ((delayed = delayedPackets.pollCurrent()) != null) {
            ChannelPromise promise = getDelayedPacketPromise(delayed);
            if (promise != null) {
                promise.tryFailure(cause);
            }
        }
        delayedPackets.clear();
        currentClickWindowPackets = 0;
        readyClickWindowPackets = 0;
    }

    protected void resetHandlerState() {
        handlerContext = null;
        synchronized (this) {
            pendingPlayerPacketGeneration = 0L;
        }
        playerPacketTickGate.clear();
        afterCurrentRotationPackets.clear();
        currentClickWindowPackets = 0;
        readyClickWindowPackets = 0;
        hasSentSilentRotation = false;
        lastSilentYaw = 0.0F;
        lastSilentPitch = 0.0F;
        teleportConfirmationPending = false;
        onResetHandlerState();
    }

    protected void markSent(Packet<?> packet) {
        if (YozakuraRuntime.playerStateManager != null) {
            YozakuraRuntime.playerStateManager.handlePacket(packet);
        }
    }
}
