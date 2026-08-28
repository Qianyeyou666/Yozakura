package gq.yozakura.bridge;

import gq.yozakura.k.B;
import gq.yozakura.core.ConfigBridge;
import gq.yozakura.event.bridge.LoadWorldEvent;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.PlayerPacketBoundaryEvent;
import gq.yozakura.event.bridge.RotationPublishedEvent;
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
import gq.yozakura.bridge.util.ReflectionUtils;
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
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

public final class StandaloneEventBridge implements ClientBridge {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = PacketPipelineAnchors.STANDALONE_BRIDGE_HANDLER_NAME;
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
        if (!B.permitTickDispatch()) {
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
        if (!wasInGame) {
            EventManager.call(new LoadWorldEvent());
        }
        wasInGame = true;
        standaloneModulesCleaned = false;
        if (!playerTick) {
            dispatchPendingPostUpdate();
            return;
        }
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

    @Override
    public void init() {
    }

    @Override
    public boolean isInGame() {
        return mc.thePlayer != null && mc.theWorld != null && mc.getNetHandler() != null;
    }

    @Override
    public Minecraft getMinecraft() {
        return mc;
    }

    @Override
    public boolean isBridgeActive() {
        return !packetBridgeTerminated && channel != null;
    }

    @Override
    public void sendPacket(Packet<?> packet) {
        if (mc.getNetHandler() != null && packet != null) {
            mc.getNetHandler().addToSendQueue(packet);
        }
    }

    @Override
    public void markPacketBypass(Packet<?> packet) {
        PacketBridgeSupport.markNoEvent(packet);
    }

    @Override
    public void setSilentRotation(float yaw, float pitch, boolean moveFix) {
        RotationState.applyState(true, yaw, pitch, yaw, 0, moveFix);
    }

    @Override
    public void clearSilentRotation() {
        RotationState.clear();
    }

    @Override
    public boolean hasSilentRotation() {
        return RotationState.isActived();
    }

    @Override
    public float getSilentYaw() {
        return RotationState.getRotationYawHead();
    }

    @Override
    public float getSilentPitch() {
        return RotationState.getRotationPitch();
    }

    @Override
    public void applyVisibleRotation(float yaw, float pitch) {
        if (mc.thePlayer != null) {
            mc.thePlayer.rotationYaw = yaw;
            mc.thePlayer.rotationPitch = pitch;
            mc.thePlayer.rotationYawHead = yaw;
            mc.thePlayer.renderYawOffset = yaw;
        }
    }

    @Override
    public boolean isKeyDown(String keyName) {
        if (mc.gameSettings == null || keyName == null) {
            return false;
        }
        if ("forward".equalsIgnoreCase(keyName)) {
            return mc.gameSettings.keyBindForward.isKeyDown();
        }
        if ("back".equalsIgnoreCase(keyName)) {
            return mc.gameSettings.keyBindBack.isKeyDown();
        }
        if ("left".equalsIgnoreCase(keyName)) {
            return mc.gameSettings.keyBindLeft.isKeyDown();
        }
        if ("right".equalsIgnoreCase(keyName)) {
            return mc.gameSettings.keyBindRight.isKeyDown();
        }
        if ("jump".equalsIgnoreCase(keyName)) {
            return mc.gameSettings.keyBindJump.isKeyDown();
        }
        if ("sneak".equalsIgnoreCase(keyName) || "shift".equalsIgnoreCase(keyName)) {
            return mc.gameSettings.keyBindSneak.isKeyDown();
        }
        if ("sprint".equalsIgnoreCase(keyName)) {
            return mc.gameSettings.keyBindSprint.isKeyDown();
        }
        if ("attack".equalsIgnoreCase(keyName)) {
            return mc.gameSettings.keyBindAttack.isKeyDown();
        }
        if ("use".equalsIgnoreCase(keyName)) {
            return mc.gameSettings.keyBindUseItem.isKeyDown();
        }
        return false;
    }

    @Override
    public void setKeyDown(String keyName, boolean down) {
        if (mc.gameSettings == null || keyName == null) {
            return;
        }
        net.minecraft.client.settings.KeyBinding keyBinding = null;
        if ("forward".equalsIgnoreCase(keyName)) {
            keyBinding = mc.gameSettings.keyBindForward;
        } else if ("back".equalsIgnoreCase(keyName)) {
            keyBinding = mc.gameSettings.keyBindBack;
        } else if ("left".equalsIgnoreCase(keyName)) {
            keyBinding = mc.gameSettings.keyBindLeft;
        } else if ("right".equalsIgnoreCase(keyName)) {
            keyBinding = mc.gameSettings.keyBindRight;
        } else if ("jump".equalsIgnoreCase(keyName)) {
            keyBinding = mc.gameSettings.keyBindJump;
        } else if ("sneak".equalsIgnoreCase(keyName) || "shift".equalsIgnoreCase(keyName)) {
            keyBinding = mc.gameSettings.keyBindSneak;
        } else if ("sprint".equalsIgnoreCase(keyName)) {
            keyBinding = mc.gameSettings.keyBindSprint;
        } else if ("attack".equalsIgnoreCase(keyName)) {
            keyBinding = mc.gameSettings.keyBindAttack;
        } else if ("use".equalsIgnoreCase(keyName)) {
            keyBinding = mc.gameSettings.keyBindUseItem;
        }
        if (keyBinding != null) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(keyBinding.getKeyCode(), down);
        }
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
        ConfigBridge.saveIfDirtyQuietly();
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
        float prevPublishedYaw = RotationState.isActived()
                ? RotationState.getRotationYawHead() : mc.thePlayer.rotationYaw;
        float prevPublishedPitch = RotationState.isActived()
                ? RotationState.getRotationPitch() : mc.thePlayer.rotationPitch;
        UpdateEvent update = new UpdateEvent(EventType.PRE, prevPublishedYaw, prevPublishedPitch,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        activePreUpdate = update;
        try {
            resetTransientPacketState();
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
            EventManager.call(new RotationPublishedEvent(update));
            applyLocalAimAssistRotation(update);
            VisualRotationState.finishTick();
            RotationDebug.logUpdate("standalone", update);
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
            Channel next = ReflectionUtils.getChannel(manager);
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
            Channel replacement = ReflectionUtils.getChannel(mc.getNetHandler().getNetworkManager());
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

    private static final class DelayedPacket {
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

    private final class PacketBridgeHandler extends BasePacketBridgeHandler<StandaloneRotationPublication.Snapshot, DelayedPacket> {
        @Override
        protected String getBridgeType() {
            return "standalone";
        }

        @Override
        protected boolean isBridgeTerminated() {
            return packetBridgeTerminated;
        }

        @Override
        protected StandaloneRotationPublication.Snapshot getRotationSnapshot() {
            return rotationPublication.snapshot();
        }

        @Override
        protected boolean isPreUpdatePending() {
            return activePreUpdate != null;
        }

        @Override
        protected boolean isPacketPendingPost() {
            return pendingPostUpdate != null;
        }

        @Override
        protected DelayedPacket createDelayedPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
            return new DelayedPacket(packet, promise, pendingPostUpdate != null, writeId);
        }

        @Override
        protected Packet<?> getDelayedPacketPacket(DelayedPacket delayed) {
            return delayed.packet;
        }

        @Override
        protected ChannelPromise getDelayedPacketPromise(DelayedPacket delayed) {
            return delayed.promise;
        }

        @Override
        protected long getDelayedPacketWriteId(DelayedPacket delayed) {
            return delayed.writeId;
        }

        @Override
        protected boolean isDelayedPacketPendingPost(DelayedPacket delayed) {
            return delayed.pendingPost;
        }

        @Override
        protected boolean isRotationActive(StandaloneRotationPublication.Snapshot snapshot) {
            return snapshot.isActive();
        }

        @Override
        protected float getRotationYaw(StandaloneRotationPublication.Snapshot snapshot) {
            return snapshot.getYaw();
        }

        @Override
        protected float getRotationPitch(StandaloneRotationPublication.Snapshot snapshot) {
            return snapshot.getPitch();
        }

        @Override
        protected long getRotationGeneration(StandaloneRotationPublication.Snapshot snapshot) {
            return 0L;
        }

        @Override
        protected void markRotationSent(StandaloneRotationPublication.Snapshot snapshot) {
            rotationPublication.markSent(snapshot);
        }

        @Override
        protected void onHandlerRemoved(ChannelHandlerContext ctx) {
            onPacketBridgeTerminated(ctx.channel());
        }

        @Override
        protected void onChannelInactive(ChannelHandlerContext ctx) {
            onPacketBridgeTerminated(ctx.channel());
        }

        @Override
        protected void onResetHandlerState() {
            waitingForRotationPacket = false;
            if (packetBridgeHandler == this) {
                packetBridgeHandler = null;
            }
        }

        @Override
        protected void onAcceptedTeleportBoundary() {
            rotationPublication.invalidateForTeleport();
            waitingForRotationPacket = false;
        }

        @Override
        protected boolean shouldDelayPlayerPacket(StandaloneRotationPublication.Snapshot snapshot) {
            return false;
        }

        @Override
        protected void handleDelayedPlayerPacket(C03PacketPlayer packet, ChannelPromise promise,
                                                 StandaloneRotationPublication.Snapshot snapshot, long writeId,
                                                 boolean nonCanonicalPlayerPacket) {
        }

        @Override
        protected void writePlayerPacketInternal(ChannelHandlerContext ctx, C03PacketPlayer packet,
                                                  ChannelPromise promise, StandaloneRotationPublication.Snapshot snapshot,
                                                  long writeId, boolean canonicalPlayerPacket,
                                                  boolean preservePlayerLook) throws Exception {
            writePlayerPacketCommon(ctx, packet, promise, snapshot, writeId, canonicalPlayerPacket,
                    activePreUpdate != null, preservePlayerLook);
        }

        @Override
        protected void logActionQueue(Packet<?> packet, boolean pendingPost) {
            BridgeDebug.logPacket("standalone", "SEND_ACTION_QUEUE", packet, pendingPost);
        }

        @Override
        protected void logActionReadyQueue(Packet<?> packet, boolean pendingPost) {
            BridgeDebug.logPacket("standalone", "SEND_ACTION_READY_QUEUE", packet, pendingPost);
        }

        @Override
        protected void logActionOut(Packet<?> packet, boolean pendingPost, String source) {
            BridgeDebug.logPacketDetail("standalone", "SEND_ACTION_OUT", packet, pendingPost, source);
        }

        @Override
        protected void logActionBlinkBuffered(Packet<?> packet, boolean pendingPost, String source) {
            BridgeDebug.logPacketDetail("standalone", "SEND_ACTION_BLINK_BUFFERED", packet, pendingPost, source);
        }
    }
}
