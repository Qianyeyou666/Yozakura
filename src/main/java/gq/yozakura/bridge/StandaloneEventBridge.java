package gq.yozakura.bridge;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.core.ConfigBridge;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.RotationResolvedEvent;
import gq.yozakura.event.bridge.SafeWalkEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.manager.BridgeDebug;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.manager.PacketRotationState;
import gq.yozakura.manager.RotationDebug;
import gq.yozakura.manager.RotationExitState;
import gq.yozakura.manager.RotationState;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.runtime.YozakuraRuntime;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

public final class StandaloneEventBridge {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = PacketPipelineAnchors.STANDALONE_BRIDGE_HANDLER_NAME;
    private static Field channelField;
    private final StandaloneRotationPublication rotationPublication = new StandaloneRotationPublication();
    private volatile Channel channel;
    private volatile PacketBridgeHandler packetBridgeHandler;
    private volatile Channel terminatedPacketChannel;
    private volatile boolean packetBridgeTerminated;
    private volatile boolean terminatedPacketBridgeCleaned;
    private boolean packetHandlerLogged;
    private boolean packetHandlerFailureLogged;
    private volatile UpdateEvent pendingPostUpdate;
    private volatile UpdateEvent activePreUpdate;
    private volatile boolean waitingForRotationPacket;
    private int lastPreUpdateTick = Integer.MIN_VALUE;
    private int lastPlayerPacketMarkerTick = Integer.MIN_VALUE;
    private boolean dispatchingPlayerPacketPreUpdate;
    private boolean wasInGame;
    private boolean standaloneModulesCleaned;
    private long nextPlayerPacketGeneration;

    public void tick(boolean playerTick) {
        if (stopForTerminatedPacketBridge()) {
            return;
        }
        if (!YozakuraAuthGate.allowRuntime("standalone-tick")) {
            MovementInputBridge.setSafeWalkRequested(false);
            MovementInputBridge.setBeforeMoveInputHook(null);
            MovementInputBridge.setAfterMoveInputHook(null);
            MovementInputBridge.setDirectYawPhysics(true);
            MovementInputBridge.uninstall();
            uninstallRendererHooks();
            cleanupStandaloneModules();
            clearBridgeState();
            removePacketHandler();
            wasInGame = false;
            return;
        }
        if (!isInGame()) {
            if (wasInGame) {
                wasInGame = false;
                dispatchDisconnected();
            }
            MovementInputBridge.setSafeWalkRequested(false);
            MovementInputBridge.setBeforeMoveInputHook(null);
            MovementInputBridge.setAfterMoveInputHook(null);
            MovementInputBridge.setDirectYawPhysics(true);
            MovementInputBridge.uninstall();
            uninstallRendererHooks();
            clearBridgeState();
            removePacketHandler();
            return;
        }
        wasInGame = true;
        standaloneModulesCleaned = false;
        MovementInputBridge.install();
        MovementInputBridge.setBeforeMoveInputHook(new Runnable() {
            @Override
            public void run() {
                dispatchPreUpdateBeforePlayerPacket();
            }
        });
        MovementInputBridge.setAfterMoveInputHook(new Runnable() {
            @Override
            public void run() {
                markNextPlayerPacketTick();
            }
        });
        MovementInputBridge.setDirectYawPhysics(true);
        MovementInputBridge.restoreRotation();
        StandaloneGuiIngame.install(mc);
        StandaloneEntityRenderer.install(mc);
        StandaloneLivingRendererBridge.install(mc);
        injectPacketHandler();
        if (stopForTerminatedPacketBridge()) {
            return;
        }
        if (!playerTick) {
            dispatchPendingPostUpdate();
            return;
        }
        dispatchForgeTick(gq.yozakura.bridge.forge.TickEvent.Phase.START);
        EventManager.call(new TickEvent(EventType.PRE));
        dispatchSafeWalk();
        EventManager.call(new gq.yozakura.bridge.forge.LivingEvent.LivingUpdateEvent(mc.thePlayer));
        syncAuraTarget();
        MovementInputBridge.restoreRotation();
        EventManager.call(new TickEvent(EventType.POST));
        dispatchPendingPostUpdate();
        dispatchForgeTick(gq.yozakura.bridge.forge.TickEvent.Phase.END);
        dispatchSafeWalk();
        syncAuraTarget();
        MovementInputBridge.finishTick();
    }

    public void shutdown() {
        MovementInputBridge.setSafeWalkRequested(false);
        MovementInputBridge.setBeforeMoveInputHook(null);
        MovementInputBridge.setAfterMoveInputHook(null);
        MovementInputBridge.setDirectYawPhysics(true);
        MovementInputBridge.uninstall();
        uninstallRendererHooks();
        cleanupStandaloneModules();
        clearBridgeState();
        wasInGame = false;
        removePacketHandler();
    }

    private void uninstallRendererHooks() {
        StandaloneLivingRendererBridge.uninstall(mc);
        StandaloneGuiIngame.uninstall(mc);
        StandaloneEntityRenderer.uninstall(mc);
    }

    private void clearBridgeState() {
        PacketBridgeSupport.clearNoEventPackets();
        PacketRotationState.clear();
        RotationExitState.clear();
        RotationState.clear();
        VisualRotationState.clear();
        rotationPublication.clear();
        nextPlayerPacketGeneration = 0L;
        pendingPostUpdate = null;
        activePreUpdate = null;
        waitingForRotationPacket = false;
        lastPreUpdateTick = Integer.MIN_VALUE;
        lastPlayerPacketMarkerTick = Integer.MIN_VALUE;
        dispatchingPlayerPacketPreUpdate = false;
    }

    private void dispatchDisconnected() {
        EventManager.call(new gq.yozakura.bridge.forge.FMLNetworkEvent.ClientDisconnectionFromServerEvent());
        cleanupStandaloneModules();
    }

    private void cleanupStandaloneModules() {
        if (standaloneModulesCleaned) {
            return;
        }
        standaloneModulesCleaned = true;
        ConfigBridge.saveIfDirtyQuietly();
        ConfigBridge.setAutoSaveSuspended(true);
        try {
            ModuleManager.disableAll(false);
        } finally {
            ConfigBridge.setAutoSaveSuspended(false);
        }
    }

    private void dispatchForgeTick(gq.yozakura.bridge.forge.TickEvent.Phase phase) {
        EventManager.call(new gq.yozakura.bridge.forge.TickEvent(phase));
        EventManager.call(new gq.yozakura.bridge.forge.TickEvent.ClientTickEvent(phase));
        EventManager.call(new gq.yozakura.bridge.forge.TickEvent.PlayerTickEvent(phase, mc.thePlayer));
    }

    private void dispatchPreUpdate() {
        MovementInputBridge.restoreRotation();
        dispatchPendingPostUpdate();
        lastPreUpdateTick = mc.thePlayer == null ? Integer.MIN_VALUE : mc.thePlayer.ticksExisted;
        BridgeDebug.logState("standalone", "PRE_START", pendingPostUpdate != null);
        VisualRotationState.beginTick();
        UpdateEvent update = new UpdateEvent(EventType.PRE, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        activePreUpdate = update;
        try {
            EventManager.call(update);
            RotationExitState.apply(update);
            EventManager.call(new RotationResolvedEvent(update));
            BridgeDebug.logUpdate("standalone", "PRE_AFTER_EVENT", update, pendingPostUpdate != null);
            RotationState.applyState(update.isRotated(), update.getNewYaw(), update.getNewPitch(),
                    update.getPreYaw(), update.isRotating(), update.isMoveFix());
            boolean rotationActive = RotationState.isActived();
            waitingForRotationPacket = rotationActive;
            rotationPublication.publish(rotationActive, RotationState.getRotationYawHead(),
                    RotationState.getRotationPitch());
            applyLocalAimAssistRotation(update);
            VisualRotationState.finishTick();
            RotationDebug.logUpdate("standalone", update);
            resetTransientPacketState();
            queuePostUpdate(update);
            BridgeDebug.logUpdate("standalone", "PRE_DONE", update, pendingPostUpdate != null);
        } finally {
            activePreUpdate = null;
        }
    }

    private void dispatchPreUpdateBeforePlayerPacket() {
        if (packetBridgeTerminated || dispatchingPlayerPacketPreUpdate || mc.thePlayer == null) {
            return;
        }
        int tick = mc.thePlayer.ticksExisted;
        if (lastPreUpdateTick == tick) {
            return;
        }
        dispatchingPlayerPacketPreUpdate = true;
        try {
            dispatchPreUpdate();
        } finally {
            dispatchingPlayerPacketPreUpdate = false;
        }
    }

    private void markNextPlayerPacketTick() {
        if (packetBridgeTerminated || mc.thePlayer == null) {
            return;
        }
        int tick = mc.thePlayer.ticksExisted;
        if (lastPreUpdateTick != tick || lastPlayerPacketMarkerTick == tick) {
            return;
        }
        PacketBridgeHandler handler = packetBridgeHandler;
        if (handler != null) {
            handler.markNextPlayerPacketTick(++nextPlayerPacketGeneration);
            lastPlayerPacketMarkerTick = tick;
        }
    }

    private void applyLocalAimAssistRotation(UpdateEvent update) {
        if (update == null || !update.isRotated() || !YozakuraRuntime.rotationManager.isRotated()
                || mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.rotationYaw = update.getNewYaw();
        mc.thePlayer.rotationPitch = update.getNewPitch();
        mc.thePlayer.prevRotationYawHead = mc.thePlayer.rotationYawHead;
        mc.thePlayer.rotationYawHead = update.getNewYaw();
        mc.thePlayer.prevRenderYawOffset = mc.thePlayer.renderYawOffset;
        mc.thePlayer.renderYawOffset = update.getNewYaw();
    }

    private void queuePostUpdate(UpdateEvent preUpdate) {
        this.pendingPostUpdate = new UpdateEvent(EventType.POST, preUpdate.getYaw(), preUpdate.getPitch(),
                preUpdate.getNewYaw(), preUpdate.getNewPitch());
        BridgeDebug.logUpdate("standalone", "POST_QUEUED", this.pendingPostUpdate, true);
    }

    private void dispatchPendingPostUpdate() {
        UpdateEvent post = this.pendingPostUpdate;
        this.pendingPostUpdate = null;
        if (post == null) {
            BridgeDebug.logState("standalone", "POST_SKIP_NULL", false);
            return;
        }
        resetTransientPacketState();
        BridgeDebug.logUpdate("standalone", "POST_START", post, false);
        EventManager.call(post);
        BridgeDebug.logUpdate("standalone", "POST_DONE", post, false);
    }

    private void resetTransientPacketState() {
        if (YozakuraRuntime.playerStateManager != null) {
            YozakuraRuntime.playerStateManager.resetTransientState();
        }
    }

    private void dispatchSafeWalk() {
        SafeWalkEvent safeWalk = EventManager.call(new SafeWalkEvent(false));
        MovementInputBridge.setSafeWalkRequested(safeWalk.isSafeWalk());
    }

    private void syncAuraTarget() {
        gq.yozakura.module.Module module = gq.yozakura.manager.ModuleManager.getModule("KillAura");
        if (module instanceof gq.yozakura.module.combat.KillAura) {
            gq.yozakura.module.combat.KillAura.target =
                    ((gq.yozakura.module.combat.KillAura) module).getTarget();
        }
    }

    private void injectPacketHandler() {
        try {
            NetworkManager manager = mc.getNetHandler().getNetworkManager();
            Channel next = getChannel(manager);
            if (next == null || !next.isOpen()) {
                logPacketHandlerFailure("Standalone packet bridge unavailable: network channel not found", null);
                return;
            }
            if (packetBridgeTerminated) {
                return;
            }
            if (channel != null && channel != next) {
                removePacketHandler();
            }
            if (channel != next && next.pipeline().get(HANDLER_NAME) != null) {
                next.pipeline().remove(HANDLER_NAME);
            }
            Object existing = next.pipeline().get(HANDLER_NAME);
            if (existing != null && !(existing instanceof PacketBridgeHandler)) {
                next.pipeline().remove(HANDLER_NAME);
                existing = null;
            }
            PacketBridgeHandler handler;
            if (existing instanceof PacketBridgeHandler) {
                handler = (PacketBridgeHandler) existing;
            } else {
                handler = new PacketBridgeHandler();
                PacketPipelineAnchors.installStandaloneBridge(next.pipeline(), handler);
            }
            packetBridgeHandler = handler;
            channel = next;
            if (!next.isActive()) {
                onPacketBridgeTerminated(next);
                return;
            }
            logPacketHandlerInstalled();
        } catch (Throwable throwable) {
            logPacketHandlerFailure("Standalone packet bridge install failed", throwable);
            channel = null;
            packetBridgeHandler = null;
        }
    }

    private void removePacketHandler() {
        Channel old = channel;
        channel = null;
        packetBridgeHandler = null;
        if (old == null) {
            return;
        }
        try {
            if (old.pipeline().get(HANDLER_NAME) != null) {
                old.pipeline().remove(HANDLER_NAME);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean stopForTerminatedPacketBridge() {
        if (!packetBridgeTerminated) {
            return false;
        }
        if (!terminatedPacketBridgeCleaned) {
            if (wasInGame) {
                wasInGame = false;
                dispatchDisconnected();
            }
            MovementInputBridge.setSafeWalkRequested(false);
            MovementInputBridge.setBeforeMoveInputHook(null);
            MovementInputBridge.setAfterMoveInputHook(null);
            MovementInputBridge.setDirectYawPhysics(true);
            MovementInputBridge.uninstall();
            uninstallRendererHooks();
            clearBridgeState();
            removePacketHandler();
            terminatedPacketBridgeCleaned = true;
            return true;
        }
        if (!hasReplacementPacketChannel()) {
            return true;
        }
        terminatedPacketChannel = null;
        terminatedPacketBridgeCleaned = false;
        packetBridgeTerminated = false;
        return false;
    }

    private boolean hasReplacementPacketChannel() {
        try {
            if (mc.getNetHandler() == null) {
                return false;
            }
            Channel replacement = getChannel(mc.getNetHandler().getNetworkManager());
            return replacement != null && replacement.isActive() && replacement != terminatedPacketChannel;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void onPacketBridgeTerminated(Channel terminated) {
        if (terminated == null || channel != terminated) {
            return;
        }
        channel = null;
        packetBridgeHandler = null;
        terminatedPacketChannel = terminated;
        terminatedPacketBridgeCleaned = false;
        PacketBridgeSupport.clearNoEventPackets();
        packetBridgeTerminated = true;
    }

    private static Channel getChannel(NetworkManager manager) {
        if (manager == null) {
            return null;
        }
        try {
            if (channelField == null) {
                for (String name : new String[]{"channel", "field_150746_k", "k"}) {
                    try {
                        channelField = NetworkManager.class.getDeclaredField(name);
                        channelField.setAccessible(true);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }
            Object value = channelField == null ? null : channelField.get(manager);
            return value instanceof Channel ? (Channel) value : null;
        } catch (Throwable ignored) {
            channelField = null;
            return null;
        }
    }

    private static boolean isInGame() {
        return mc.thePlayer != null && mc.theWorld != null && mc.getNetHandler() != null;
    }

    private void logPacketHandlerInstalled() {
        if (!packetHandlerLogged) {
            packetHandlerLogged = true;
            log("Standalone packet bridge installed");
        }
    }

    private void logPacketHandlerFailure(String message, Throwable throwable) {
        if (!packetHandlerFailureLogged) {
            packetHandlerFailureLogged = true;
            log(message, throwable);
        }
    }

    private static void log(String message) {
        log(message, null);
    }

    private static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraStandalone.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private final class PacketBridgeHandler extends ChannelDuplexHandler {
        private static final float ROTATION_EPSILON = 1.0E-3F;
        private static final float ROTATION_DEDUPE_STEP = 0.0096F;
        private final OutboundActionBatchQueue<DelayedPacket> delayedPackets =
                new OutboundActionBatchQueue<DelayedPacket>();
        private final PlayerPacketTickGate playerPacketTickGate = new PlayerPacketTickGate();
        private volatile ChannelHandlerContext handlerContext;
        private long pendingPlayerPacketGeneration;
        private int currentClickWindowPackets;
        private int readyClickWindowPackets;
        private boolean hasSentSilentRotation;
        private boolean duplicateYawFlip;
        private float lastSilentYaw;
        private float lastSilentPitch;
        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            handlerContext = ctx;
            super.handlerAdded(ctx);
            drainPendingPlayerPacketTick(ctx);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (packetBridgeTerminated || !ctx.channel().isActive()) {
                completeFailedWrite(promise, new ClosedChannelException());
                return;
            }
            if (!YozakuraAuthGate.allowRuntime("standalone-packet-send")) {
                super.write(ctx, msg, promise);
                return;
            }
            if (msg instanceof Packet<?>) {
                Packet<?> packet = (Packet<?>) msg;
                boolean packetPendingPost = pendingPostUpdate != null;
                PacketBridgeSupport.NoEventMarker noEventMarker =
                        PacketBridgeSupport.consumeNoEventMarker(packet);
                if (noEventMarker.isAlreadyBridgeProcessed()) {
                    super.write(ctx, msg, promise);
                    return;
                }
                boolean skipPacketEvent = noEventMarker.isMarked();
                long writeId = noEventMarker.getWriteId();
                boolean preserveOriginalPacketOrder = false;
                if (skipPacketEvent) {
                    BridgeDebug.logPacket("standalone", "SEND_NO_EVENT", packet, packetPendingPost);
                }
                if (!skipPacketEvent) {
                    BridgeDebug.logPacket("standalone", "SEND_IN", packet, packetPendingPost);
                    PacketEvent event = EventManager.call(new PacketEvent(EventType.SEND, packet));
                    if (event.isCancelled()) {
                        BridgeDebug.logPacket("standalone", "SEND_CANCELLED", packet, packetPendingPost);
                        completeDroppedWrite(promise);
                        return;
                    }
                    PacketAcceptedEvent accepted = new PacketAcceptedEvent(packet);
                    EventManager.call(accepted);
                    writeId = accepted.getWriteId();
                    preserveOriginalPacketOrder = accepted.isOriginalPacketOrderRequired();
                }
                observePacketWrite(ctx, packet, promise, writeId);
                if (!skipPacketEvent && !preserveOriginalPacketOrder && isPostSensitiveAction(packet)) {
                    queueCurrentActionPacket(packet, promise, packetPendingPost, writeId);
                    return;
                }
                if (!skipPacketEvent && packet instanceof net.minecraft.network.play.client.C0DPacketCloseWindow) {
                    if (currentClickWindowPackets > 0) {
                        queueCurrentActionPacket(packet, promise, packetPendingPost, writeId);
                        return;
                    }
                    if (readyClickWindowPackets > 0) {
                        queueReadyActionPacket(packet, promise, packetPendingPost, writeId);
                        return;
                    }
                }

                boolean preUpdatePending = activePreUpdate != null;
                StandaloneRotationPublication.Snapshot rotation = rotationPublication.snapshot();
                if (packet instanceof C03PacketPlayer) {
                    writePlayerPacket(ctx, (C03PacketPlayer) packet, promise, rotation,
                            preUpdatePending, packetPendingPost, writeId);
                    return;
                }
                markSent(packet);
                BridgeDebug.logPacket("standalone", "SEND_MARKED", packet, packetPendingPost);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(packet, promise, writeId)) {
                    BridgeDebug.logPacket("standalone", "SEND_BLINK_BUFFERED", packet, packetPendingPost);
                    return;
                }
                BridgeDebug.logPacket("standalone", "SEND_OUT", packet, packetPendingPost);
            }
            super.write(ctx, msg, promise);
        }

        private void observePacketWrite(ChannelHandlerContext ctx, final Packet<?> packet,
                                        ChannelPromise promise, final long writeId) {
            if (ctx == null || packet == null || promise == null) {
                return;
            }
            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    reportPacketWrite(packet, writeId, future.isSuccess());
                }
            });
        }

        private void reportPacketWrite(Packet<?> packet, long writeId, boolean success) {
            EventManager.call(new PacketWriteEvent(packet, writeId, success));
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            try {
                failDelayedPackets(new ClosedChannelException());
                resetHandlerState();
                onPacketBridgeTerminated(ctx.channel());
            } finally {
                super.handlerRemoved(ctx);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            failDelayedPackets(new ClosedChannelException());
            resetHandlerState();
            onPacketBridgeTerminated(ctx.channel());
            super.channelInactive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (packetBridgeTerminated || !ctx.channel().isActive()) {
                return;
            }
            if (!YozakuraAuthGate.allowRuntime("standalone-packet-receive")) {
                super.channelRead(ctx, msg);
                return;
            }
            if (msg instanceof Packet<?>) {
                Packet<?> packet = (Packet<?>) msg;
                PacketEvent event = EventManager.call(new PacketEvent(EventType.RECEIVE, packet));
                if (event.isCancelled()) {
                    return;
                }
            }
            super.channelRead(ctx, msg);
        }

        private void writePlayerPacket(ChannelHandlerContext ctx, C03PacketPlayer packet, ChannelPromise promise,
                                       StandaloneRotationPublication.Snapshot rotation, boolean preUpdatePending,
                                       boolean packetPendingPost, long writeId) throws Exception {
            boolean playerTickAdvanced = playerPacketTickGate.consumeNextPlayerPacket();
            if (playerTickAdvanced) {
                flushReadyActionPackets(ctx);
            }
            if (playerTickAdvanced && !preUpdatePending && !rotation.isActive()) {
                flushCurrentActionPackets(ctx);
            }

            if (rotation.isActive()) {
                markSent(packet);
                BridgeDebug.logPacket("standalone", "SEND_MARKED", packet, packetPendingPost);

                C03PacketPlayer rewritten = rewritePlayerPacket(packet, rotation);
                RotationDebug.logPacket("standalone", packet, true);
                BridgeDebug.logPacketRewrite("standalone", packet, rewritten, packetPendingPost);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(rewritten, promise, writeId)) {
                    BridgeDebug.logPacket("standalone", "SEND_BLINK_BUFFERED", rewritten, packetPendingPost);
                } else {
                    super.write(ctx, rewritten, promise);
                }
                rotationPublication.markSent(rotation);
                if (!preUpdatePending && playerTickAdvanced) {
                    promoteCurrentActionPackets();
                }
                return;
            }

            markSent(packet);
            BridgeDebug.logPacket("standalone", "SEND_MARKED", packet, packetPendingPost);
            RotationDebug.logPacket("standalone", packet, false);
            hasSentSilentRotation = false;
            if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                    && YozakuraRuntime.blinkManager.offerPacket(packet, promise, writeId)) {
                BridgeDebug.logPacket("standalone", "SEND_BLINK_BUFFERED", packet, packetPendingPost);
            } else {
                super.write(ctx, packet, promise);
            }
            rotationPublication.markSent(rotation);
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

        private synchronized void storePendingPlayerPacketGeneration(long generation) {
            if (generation > pendingPlayerPacketGeneration) {
                pendingPlayerPacketGeneration = generation;
            }
        }

        private void drainPendingPlayerPacketTick(ChannelHandlerContext ctx) {
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

        private C03PacketPlayer rewritePlayerPacket(C03PacketPlayer packet,
                                                     StandaloneRotationPublication.Snapshot rotation) {
            float yaw = rotation.getYaw();
            float pitch = rotation.getPitch();
            boolean onGround = packet.isOnGround();

            if (!shouldSendLook(yaw, pitch)) {
                yaw = nudgeDuplicateYaw(yaw);
            }

            hasSentSilentRotation = true;
            lastSilentYaw = yaw;
            lastSilentPitch = pitch;

            if (packet.isMoving()) {
                return new C03PacketPlayer.C06PacketPlayerPosLook(packet.getPositionX(), packet.getPositionY(),
                        packet.getPositionZ(), yaw, pitch, onGround);
            }
            return new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, onGround);
        }

        private boolean shouldSendLook(float yaw, float pitch) {
            return !hasSentSilentRotation
                    || Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - lastSilentYaw))
                    > ROTATION_EPSILON
                    || Math.abs(pitch - lastSilentPitch) > ROTATION_EPSILON;
        }

        private float nudgeDuplicateYaw(float yaw) {
            duplicateYawFlip = !duplicateYawFlip;
            return yaw + (duplicateYawFlip ? ROTATION_DEDUPE_STEP : -ROTATION_DEDUPE_STEP);
        }

        private boolean isPostSensitiveAction(Packet<?> packet) {
            return packet instanceof net.minecraft.network.play.client.C02PacketUseEntity
                    || packet instanceof net.minecraft.network.play.client.C07PacketPlayerDigging
                    || packet instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
                    || packet instanceof net.minecraft.network.play.client.C09PacketHeldItemChange
                    || packet instanceof net.minecraft.network.play.client.C0APacketAnimation
                    || packet instanceof net.minecraft.network.play.client.C0EPacketClickWindow;
        }

        private void queueCurrentActionPacket(Packet<?> packet, ChannelPromise promise, boolean pendingPost,
                                              long writeId) {
            delayedPackets.addCurrent(new DelayedPacket(packet, promise, pendingPost, writeId));
            if (isClickWindowPacket(packet)) {
                currentClickWindowPackets++;
            }
            BridgeDebug.logPacket("standalone", "SEND_ACTION_QUEUE", packet, pendingPost);
        }

        private void queueReadyActionPacket(Packet<?> packet, ChannelPromise promise, boolean pendingPost,
                                            long writeId) {
            delayedPackets.addReady(new DelayedPacket(packet, promise, pendingPost, writeId));
            if (isClickWindowPacket(packet)) {
                readyClickWindowPackets++;
            }
            BridgeDebug.logPacket("standalone", "SEND_ACTION_READY_QUEUE", packet, pendingPost);
        }

        private void flushReadyActionPackets(ChannelHandlerContext ctx) throws Exception {
            DelayedPacket delayed;
            while ((delayed = delayedPackets.pollReady()) != null) {
                consumeReadyClickWindowPacket(delayed.packet);
                writeQueuedActionPacket(ctx, delayed, "ready");
            }
        }

        private void flushCurrentActionPackets(ChannelHandlerContext ctx) throws Exception {
            DelayedPacket delayed;
            while ((delayed = delayedPackets.pollCurrent()) != null) {
                consumeCurrentClickWindowPacket(delayed.packet);
                writeQueuedActionPacket(ctx, delayed, "current");
            }
        }

        private void promoteCurrentActionPackets() {
            delayedPackets.promoteCurrent();
            readyClickWindowPackets += currentClickWindowPackets;
            currentClickWindowPackets = 0;
        }

        private boolean isClickWindowPacket(Packet<?> packet) {
            return packet instanceof net.minecraft.network.play.client.C0EPacketClickWindow;
        }

        private void consumeReadyClickWindowPacket(Packet<?> packet) {
            if (isClickWindowPacket(packet) && readyClickWindowPackets > 0) {
                readyClickWindowPackets--;
            }
        }

        private void consumeCurrentClickWindowPacket(Packet<?> packet) {
            if (isClickWindowPacket(packet) && currentClickWindowPackets > 0) {
                currentClickWindowPackets--;
            }
        }

        private void writeQueuedActionPacket(ChannelHandlerContext ctx, DelayedPacket delayed, String source)
                throws Exception {
            markSent(delayed.packet);
            BridgeDebug.logPacketDetail("standalone", "SEND_ACTION_OUT", delayed.packet,
                    delayed.pendingPost, source);
            if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                    && YozakuraRuntime.blinkManager.offerPacket(delayed.packet, delayed.promise,
                    delayed.writeId)) {
                BridgeDebug.logPacketDetail("standalone", "SEND_ACTION_BLINK_BUFFERED", delayed.packet,
                        delayed.pendingPost, source);
                return;
            }
            super.write(ctx, delayed.packet, delayed.promise);
        }

        private void completeDroppedWrite(ChannelPromise promise) {
            if (promise != null) {
                promise.trySuccess();
            }
        }

        private void completeFailedWrite(ChannelPromise promise, Throwable cause) {
            if (promise != null) {
                promise.tryFailure(cause);
            }
        }

        private void failDelayedPackets(Throwable cause) {
            DelayedPacket delayed;
            while ((delayed = delayedPackets.pollReady()) != null) {
                if (delayed.promise != null) {
                    delayed.promise.tryFailure(cause);
                }
            }
            while ((delayed = delayedPackets.pollCurrent()) != null) {
                if (delayed.promise != null) {
                    delayed.promise.tryFailure(cause);
                }
            }
            delayedPackets.clear();
            currentClickWindowPackets = 0;
            readyClickWindowPackets = 0;
        }

        private void resetHandlerState() {
            handlerContext = null;
            synchronized (this) {
                pendingPlayerPacketGeneration = 0L;
            }
            playerPacketTickGate.clear();
            currentClickWindowPackets = 0;
            readyClickWindowPackets = 0;
            hasSentSilentRotation = false;
            duplicateYawFlip = false;
            lastSilentYaw = 0.0F;
            lastSilentPitch = 0.0F;
            waitingForRotationPacket = false;
            if (packetBridgeHandler == this) {
                packetBridgeHandler = null;
            }
        }

        private void markSent(Packet<?> packet) {
            if (YozakuraRuntime.playerStateManager != null) {
                YozakuraRuntime.playerStateManager.handlePacket(packet);
            }
        }

        private final class DelayedPacket {
            private final Packet<?> packet;
            private final ChannelPromise promise;
            private final boolean pendingPost;
            private final long writeId;

            private DelayedPacket(Packet<?> packet, ChannelPromise promise, boolean pendingPost,
                                  long writeId) {
                this.packet = packet;
                this.promise = promise;
                this.pendingPost = pendingPost;
                this.writeId = writeId;
            }
        }
    }
}
