package gq.yozakura.bridge;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.core.ConfigBridge;
import gq.yozakura.event.bridge.HitBlockEvent;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.SafeWalkEvent;
import gq.yozakura.event.bridge.SwapItemEvent;
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

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
    private volatile Channel terminatedPacketChannel;
    private volatile boolean packetBridgeTerminated;
    private boolean forcedSneak;
    private boolean lastLeftButton;
    private boolean lastRightButton;
    private boolean packetHandlerLogged;
    private boolean packetHandlerFailureLogged;
    private volatile UpdateEvent pendingPostUpdate;
    private volatile UpdateEvent activePreUpdate;
    private volatile boolean waitingForRotationPacket;
    private int lastPreUpdateTick = Integer.MIN_VALUE;
    private boolean dispatchingPlayerPacketPreUpdate;
    private boolean wasInGame;
    private boolean standaloneModulesCleaned;

    public void tick(boolean playerTick) {
        if (stopForTerminatedPacketBridge()) {
            return;
        }
        if (!YozakuraAuthGate.allowRuntime("standalone-tick")) {
            releaseForcedSneak();
            resetMouseState();
            MovementInputBridge.setBeforeMoveInputHook(null);
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
            releaseForcedSneak();
            resetMouseState();
            MovementInputBridge.setBeforeMoveInputHook(null);
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
        MovementInputBridge.setDirectYawPhysics(true);
        MovementInputBridge.restoreRotation();
        StandaloneGuiIngame.install(mc);
        StandaloneEntityRenderer.install(mc);
        StandaloneLivingRendererBridge.install(mc);
        injectPacketHandler();
        if (!playerTick) {
            dispatchMouseButtons();
            dispatchPendingPostUpdate();
            return;
        }
        dispatchForgeTick(gq.yozakura.bridge.forge.TickEvent.Phase.START);
        EventManager.call(new TickEvent(EventType.PRE));
        dispatchSafeWalk();
        dispatchMouseButtons();
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
        releaseForcedSneak();
        MovementInputBridge.setBeforeMoveInputHook(null);
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
        pendingPostUpdate = null;
        activePreUpdate = null;
        waitingForRotationPacket = false;
        lastPreUpdateTick = Integer.MIN_VALUE;
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

    private void dispatchMouseButtons() {
        try {
            if (!Mouse.isCreated()) {
                resetMouseState();
                return;
            }
            boolean leftDown = Mouse.isButtonDown(0);
            boolean rightDown = Mouse.isButtonDown(1);
            int dWheel = Mouse.getDWheel();
            if (leftDown && !lastLeftButton) {
                dispatchMouseButton(0);
            }
            if (rightDown && !lastRightButton) {
                dispatchMouseButton(1);
            }
            if (dWheel != 0) {
                dispatchMouseWheel(dWheel);
            }
            lastLeftButton = leftDown;
            lastRightButton = rightDown;
        } catch (Throwable ignored) {
            resetMouseState();
        }
    }

    private void dispatchMouseButton(int button) {
        gq.yozakura.bridge.forge.MouseEvent forgeMouse =
                EventManager.call(new gq.yozakura.bridge.forge.MouseEvent(button, true, 0));
        boolean cancelled = forgeMouse != null && forgeMouse.isCanceled();

        if (button == 0) {
            LeftClickMouseEvent left = EventManager.call(new LeftClickMouseEvent());
            cancelled |= left.isCancelled();
            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                HitBlockEvent hit = EventManager.call(new HitBlockEvent());
                cancelled |= hit.isCancelled();
            }
            if (!cancelled && mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
                EventManager.call(new gq.yozakura.bridge.forge.AttackEntityEvent(mc.thePlayer,
                        mc.objectMouseOver.entityHit));
            }
        } else if (button == 1) {
            RightClickMouseEvent right = EventManager.call(new RightClickMouseEvent());
            cancelled |= right.isCancelled();
        }

        if (cancelled) {
            suppressMouseKey(button);
        }
    }

    private void dispatchMouseWheel(int dWheel) {
        gq.yozakura.bridge.forge.MouseEvent forgeMouse =
                EventManager.call(new gq.yozakura.bridge.forge.MouseEvent(-1, false, dWheel));
        boolean cancelled = forgeMouse != null && forgeMouse.isCanceled();
        int offset = dWheel > 0 ? 1 : -1;
        SwapItemEvent swap = EventManager.call(new SwapItemEvent(mc.thePlayer.inventory.currentItem, offset));
        cancelled |= swap.isCancelled();
        if (cancelled) {
            return;
        }
        if (mc.thePlayer != null && mc.thePlayer.inventory != null) {
            mc.thePlayer.inventory.changeCurrentItem(offset);
            MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        }
    }

    private void suppressMouseKey(int button) {
        int key = button == 0 ? mc.gameSettings.keyBindAttack.getKeyCode()
                : button == 1 ? mc.gameSettings.keyBindUseItem.getKeyCode() : Integer.MIN_VALUE;
        if (key != Integer.MIN_VALUE) {
            KeyBinding.setKeyBindState(key, false);
        }
    }

    private void resetMouseState() {
        lastLeftButton = false;
        lastRightButton = false;
    }

    private void dispatchPreUpdate() {
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
            BridgeDebug.logUpdate("standalone", "PRE_AFTER_EVENT", update, pendingPostUpdate != null);
            RotationState.applyState(update.isRotated(), update.getNewYaw(), update.getNewPitch(),
                    update.getPreYaw(), update.isRotating(), update.isMoveFix());
            boolean rotationActive = RotationState.isActived();
            waitingForRotationPacket = rotationActive;
            rotationPublication.publish(rotationActive, RotationState.getRotationYawHead(),
                    RotationState.getRotationPitch());
            applyLocalAimAssistRotation(update);
            VisualRotationState.finishTick();
            syncVisibleRotation();
            RotationDebug.logUpdate("standalone", update);
            resetTransientPacketState();
            queuePostUpdate(update);
            BridgeDebug.logUpdate("standalone", "PRE_DONE", update, pendingPostUpdate != null);
        } finally {
            activePreUpdate = null;
        }
    }

    private void dispatchPreUpdateBeforePlayerPacket() {
        if (dispatchingPlayerPacketPreUpdate || mc.thePlayer == null) {
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
        if (safeWalk.isSafeWalk()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            forcedSneak = true;
        } else {
            releaseForcedSneak();
        }
    }

    private void releaseForcedSneak() {
        if (!forcedSneak) {
            return;
        }
        int key = mc.gameSettings.keyBindSneak.getKeyCode();
        boolean physicallyDown = key < 0 ? Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
        if (!physicallyDown) {
            KeyBinding.setKeyBindState(key, false);
        }
        forcedSneak = false;
    }

    private void syncAuraTarget() {
        gq.yozakura.module.Module module = gq.yozakura.manager.ModuleManager.getModule("KillAura");
        if (module instanceof gq.yozakura.module.combat.KillAura) {
            gq.yozakura.module.combat.KillAura.target =
                    ((gq.yozakura.module.combat.KillAura) module).getTarget();
        }
    }

    private void syncVisibleRotation() {
        if (!VisualRotationState.isActived()) {
            return;
        }
        mc.thePlayer.prevRotationYawHead = VisualRotationState.getPrevRotationYawHead();
        mc.thePlayer.rotationYawHead = VisualRotationState.getRotationYawHead();
        mc.thePlayer.prevRenderYawOffset = VisualRotationState.getPrevRenderYawOffset();
        mc.thePlayer.renderYawOffset = VisualRotationState.getRenderYawOffset();
    }

    private void injectPacketHandler() {
        try {
            NetworkManager manager = mc.getNetHandler().getNetworkManager();
            Channel next = getChannel(manager);
            if (next == null || !next.isOpen()) {
                logPacketHandlerFailure("Standalone packet bridge unavailable: network channel not found", null);
                return;
            }
            if (packetBridgeTerminated && terminatedPacketChannel == next) {
                return;
            }
            if (channel != null && channel != next) {
                removePacketHandler();
            }
            if (channel != next && next.pipeline().get(HANDLER_NAME) != null) {
                next.pipeline().remove(HANDLER_NAME);
            }
            PacketPipelineAnchors.installStandaloneBridge(next.pipeline(), new PacketBridgeHandler());
            logPacketHandlerInstalled();
            channel = next;
            if (terminatedPacketChannel != next) {
                terminatedPacketChannel = null;
                packetBridgeTerminated = false;
            }
        } catch (Throwable throwable) {
            logPacketHandlerFailure("Standalone packet bridge install failed", throwable);
            channel = null;
        }
    }

    private void removePacketHandler() {
        Channel old = channel;
        channel = null;
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
        if (hasReplacementPacketChannel()) {
            packetBridgeTerminated = false;
            terminatedPacketChannel = null;
            return false;
        }
        if (wasInGame) {
            wasInGame = false;
            dispatchDisconnected();
        }
        releaseForcedSneak();
        resetMouseState();
        MovementInputBridge.setBeforeMoveInputHook(null);
        MovementInputBridge.setDirectYawPhysics(true);
        MovementInputBridge.uninstall();
        clearBridgeState();
        removePacketHandler();
        return true;
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
        terminatedPacketChannel = terminated;
        packetBridgeTerminated = true;
        PacketBridgeSupport.clearNoEventPackets();
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
        private final Queue<DelayedPacket> delayedPackets = new ArrayDeque<DelayedPacket>();
        private boolean hasSentSilentRotation;
        private boolean duplicateYawFlip;
        private float lastSilentYaw;
        private float lastSilentPitch;

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
                boolean skipPacketEvent = PacketBridgeSupport.consumeNoEvent(packet);
                if (skipPacketEvent) {
                    BridgeDebug.logPacket("standalone", "SEND_NO_EVENT", packet, packetPendingPost);
                }
                if (packet instanceof C0BPacketEntityAction
                        && MovementInputBridge.shouldBlockSprintPacket((C0BPacketEntityAction) packet)) {
                    BridgeDebug.logPacket("standalone", "SEND_BLOCKED_SPRINT", packet, packetPendingPost);
                    completeDroppedWrite(promise);
                    return;
                }
                if (!skipPacketEvent) {
                    BridgeDebug.logPacket("standalone", "SEND_IN", packet, packetPendingPost);
                    PacketEvent event = EventManager.call(new PacketEvent(EventType.SEND, packet));
                    if (event.isCancelled()) {
                        BridgeDebug.logPacket("standalone", "SEND_CANCELLED", packet, packetPendingPost);
                        completeDroppedWrite(promise);
                        return;
                    }
                }
                if (shouldDelayUntilRotation(packet)) {
                    delayedPackets.add(new DelayedPacket(packet, promise, packetPendingPost));
                    BridgeDebug.logPacketDetail("standalone", "SEND_DELAYED_QUEUE", packet,
                            packetPendingPost, describeRotationState("queue"));
                    return;
                }

                boolean preUpdatePending = activePreUpdate != null;
                StandaloneRotationPublication.Snapshot rotation = rotationPublication.snapshot();
                boolean silentPlayerPacket = packet instanceof C03PacketPlayer && rotation.isActive();
                if (silentPlayerPacket) {
                    markSent(packet);
                    BridgeDebug.logPacket("standalone", "SEND_MARKED", packet, packetPendingPost);

                    C03PacketPlayer rewritten = rewritePlayerPacket((C03PacketPlayer) packet, rotation);
                    RotationDebug.logPacket("standalone", (C03PacketPlayer) packet, true);
                    BridgeDebug.logPacketRewrite("standalone", (C03PacketPlayer) packet, rewritten,
                            packetPendingPost);
                    if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                            && YozakuraRuntime.blinkManager.offerPacket(rewritten)) {
                        BridgeDebug.logPacket("standalone", "SEND_BLINK_BUFFERED", rewritten, packetPendingPost);
                        promise.trySuccess();
                    } else {
                        super.write(ctx, rewritten, promise);
                    }
                    rotationPublication.markSent(rotation);
                    if (!preUpdatePending) {
                        waitingForRotationPacket = false;
                        flushDelayedPackets(ctx);
                    }
                    return;
                }
                markSent(packet);
                BridgeDebug.logPacket("standalone", "SEND_MARKED", packet, packetPendingPost);
                if (packet instanceof C03PacketPlayer) {
                    RotationDebug.logPacket("standalone", (C03PacketPlayer) packet, false);
                    hasSentSilentRotation = false;
                    if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                            && YozakuraRuntime.blinkManager.offerPacket(packet)) {
                        BridgeDebug.logPacket("standalone", "SEND_BLINK_BUFFERED", packet, packetPendingPost);
                        promise.trySuccess();
                    } else {
                        super.write(ctx, msg, promise);
                    }
                    if (!preUpdatePending) {
                        waitingForRotationPacket = false;
                        flushDelayedPackets(ctx);
                    }
                    return;
                }
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(packet)) {
                    BridgeDebug.logPacket("standalone", "SEND_BLINK_BUFFERED", packet, packetPendingPost);
                    promise.trySuccess();
                    return;
                }
                BridgeDebug.logPacket("standalone", "SEND_OUT", packet, packetPendingPost);
            }
            super.write(ctx, msg, promise);
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

        private String describeRotationState(String source) {
            return "source=" + source
                    + " waiting=" + waitingForRotationPacket
                    + " activePre=" + (activePreUpdate != null)
                    + " rotationActive=" + rotationPublication.snapshot().isActive();
        }

        private boolean shouldDelayUntilRotation(Packet<?> packet) {
            if (!isRotationSensitiveAction(packet)) {
                return false;
            }
            if (activePreUpdate != null) {
                return true;
            }
            return waitingForRotationPacket
                    || rotationPublication.hasUnsentRotation()
                    || !delayedPackets.isEmpty();
        }

        private boolean isRotationSensitiveAction(Packet<?> packet) {
            return packet instanceof net.minecraft.network.play.client.C02PacketUseEntity
                    || packet instanceof net.minecraft.network.play.client.C07PacketPlayerDigging
                    || packet instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
                    || packet instanceof net.minecraft.network.play.client.C0APacketAnimation;
        }

        private void flushDelayedPackets(ChannelHandlerContext ctx) throws Exception {
            while (!delayedPackets.isEmpty()) {
                DelayedPacket delayed = delayedPackets.poll();
                markSent(delayed.packet);
                BridgeDebug.logPacketDetail("standalone", "SEND_DELAYED_OUT", delayed.packet,
                        delayed.pendingPost, describeRotationState("flush"));
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(delayed.packet)) {
                    delayed.promise.trySuccess();
                    BridgeDebug.logPacketDetail("standalone", "SEND_DELAYED_BLINK_BUFFERED", delayed.packet,
                            delayed.pendingPost, describeRotationState("flushBlink"));
                    continue;
                }
                super.write(ctx, delayed.packet, delayed.promise);
            }
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
            while ((delayed = delayedPackets.poll()) != null) {
                if (delayed.promise != null) {
                    delayed.promise.tryFailure(cause);
                }
            }
            delayedPackets.clear();
        }

        private void resetHandlerState() {
            hasSentSilentRotation = false;
            duplicateYawFlip = false;
            lastSilentYaw = 0.0F;
            lastSilentPitch = 0.0F;
            waitingForRotationPacket = false;
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

            private DelayedPacket(Packet<?> packet, ChannelPromise promise, boolean pendingPost) {
                this.packet = packet;
                this.promise = promise;
                this.pendingPost = pendingPost;
            }
        }
    }
}
