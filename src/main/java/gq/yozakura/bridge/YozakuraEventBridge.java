package gq.yozakura.bridge;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.core.StandaloneClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.engine.render.ShaderRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.HitBlockEvent;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.RightClickResolvedEvent;
import gq.yozakura.event.bridge.RotationResolvedEvent;
import gq.yozakura.event.bridge.SafeWalkEvent;
import gq.yozakura.event.bridge.SwapItemEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.manager.BridgeDebug;
import gq.yozakura.manager.PacketRotationState;
import gq.yozakura.manager.RotationDebug;
import gq.yozakura.manager.RotationExitState;
import gq.yozakura.manager.RotationState;
import gq.yozakura.manager.VisualRotationState;

import java.lang.reflect.Field;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

public final class YozakuraEventBridge {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "yozakura_event_bridge";
    private static final YozakuraEventBridge INSTANCE = new YozakuraEventBridge();
    private static boolean registered;
    private static Field channelField;
    private static int lastOverlayCounter = Integer.MIN_VALUE;
    private static long lastOverlayNanos;
    private final ForgeRotationPublication rotationPublication = new ForgeRotationPublication();
    private Channel channel;
    private volatile PacketBridgeHandler packetBridgeHandler;
    private volatile UpdateEvent activePreUpdate;
    private final ArrayDeque<PlayerRenderRotationSnapshot> playerRenderRotationSnapshots =
            new ArrayDeque<PlayerRenderRotationSnapshot>();

    private YozakuraEventBridge() {
    }

    public static void init() {
        YozakuraRuntime.init();
        if (registered) {
            return;
        }
        boolean forgeRegistrationAttempted = false;
        boolean fmlRegistrationAttempted = false;
        try {
            forgeRegistrationAttempted = true;
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            fmlRegistrationAttempted = true;
            FMLCommonHandler.instance().bus().register(INSTANCE);
            registered = true;
        } catch (RuntimeException failure) {
            rollbackFailedRegistration(failure, forgeRegistrationAttempted, fmlRegistrationAttempted);
            throw failure;
        } catch (Error failure) {
            rollbackFailedRegistration(failure, forgeRegistrationAttempted, fmlRegistrationAttempted);
            throw failure;
        }
    }

    private static void rollbackFailedRegistration(Throwable failure, boolean forgeRegistrationAttempted,
                                                   boolean fmlRegistrationAttempted) {
        registered = false;
        if (fmlRegistrationAttempted) {
            try {
                FMLCommonHandler.instance().bus().unregister(INSTANCE);
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        if (forgeRegistrationAttempted) {
            try {
                MinecraftForge.EVENT_BUS.unregister(INSTANCE);
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    public static void markNoEvent(Packet<?> packet) {
        PacketBridgeSupport.markNoEvent(packet);
    }

    public static void shutdown() {
        INSTANCE.shutdownInternal();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (yieldToStandaloneBridge()) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            restoreDanglingPlayerRenderRotations();
        }
        if (!YozakuraAuthGate.allowRuntime("forge-client-tick")) {
            MovementInputBridge.setSafeWalkRequested(false);
            MovementInputBridge.setBeforeMoveInputHook(null);
            MovementInputBridge.setAfterMoveInputHook(null);
            MovementInputBridge.setDirectYawPhysics(true);
            MovementInputBridge.uninstall();
            clearBridgeState();
            removePacketHandler();
            return;
        }
        if (!isInGame()) {
            MovementInputBridge.setSafeWalkRequested(false);
            MovementInputBridge.setBeforeMoveInputHook(null);
            MovementInputBridge.setAfterMoveInputHook(null);
            MovementInputBridge.setDirectYawPhysics(true);
            MovementInputBridge.uninstall();
            clearBridgeState();
            removePacketHandler();
            return;
        }
        MovementInputBridge.install();
        MovementInputBridge.setBeforeMoveInputHook(null);
        MovementInputBridge.setAfterMoveInputHook(null);
        injectPacketHandler();
        if (event.phase == TickEvent.Phase.START) {
            EventManager.call(new gq.yozakura.event.bridge.TickEvent(EventType.PRE));
            dispatchPreUpdate();
            dispatchSafeWalk();
            syncAuraTarget();
        } else {
            MovementInputBridge.restoreRotation();
            EventManager.call(new gq.yozakura.event.bridge.TickEvent(EventType.POST));
            UpdateEvent post = new UpdateEvent(EventType.POST, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            BridgeDebug.logUpdate("forge", "POST_START", post, false);
            EventManager.call(post);
            BridgeDebug.logUpdate("forge", "POST_DONE", post, false);
            dispatchSafeWalk();
            syncAuraTarget();
            MovementInputBridge.finishTick();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (yieldToStandaloneBridge()) {
            return;
        }
        if (event.phase == TickEvent.Phase.END && event.player == mc.thePlayer) {
            MovementInputBridge.restoreRotation();
        }
    }

    public static boolean hasRenderedOverlayThisFrame() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.ingameGUI == null) {
                return false;
            }
            return minecraft.ingameGUI.getUpdateCounter() == lastOverlayCounter
                    && System.nanoTime() - lastOverlayNanos < 100000000L;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void markOverlayRendered() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.ingameGUI != null) {
                lastOverlayCounter = minecraft.ingameGUI.getUpdateCounter();
                lastOverlayNanos = System.nanoTime();
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (yieldToStandaloneBridge()) {
            return;
        }
        if (!YozakuraAuthGate.allowRuntime("forge-render-2d")) {
            return;
        }
        if (isInGame()) {
            ShaderRenderer.beginOverlayFrame();
            RenderServices.beginHudEffectsFrame();
            try {
                EventManager.call(new Render2DEvent(event.partialTicks));
            } finally {
                try {
                    RenderServices.flushHudEffectsFrame();
                } finally {
                    restoreDanglingPlayerRenderRotations();
                }
            }
            markOverlayRendered();
        }
    }

    @SubscribeEvent
    public void onRender3D(RenderWorldLastEvent event) {
        if (yieldToStandaloneBridge()) {
            return;
        }
        if (!YozakuraAuthGate.allowRuntime("forge-render-3d")) {
            return;
        }
        if (isInGame()) {
            try {
                EventManager.call(new Render3DEvent(event.partialTicks));
            } finally {
                restoreDanglingPlayerRenderRotations();
            }
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (yieldToStandaloneBridge()) {
            return;
        }
        if (!YozakuraAuthGate.allowRuntime("forge-mouse")) {
            return;
        }
        if (!isInGame()) {
            return;
        }
        if (event.button == 0 && event.buttonstate) {
            LeftClickMouseEvent left = EventManager.call(new LeftClickMouseEvent());
            if (left.isCancelled()) {
                event.setCanceled(true);
            }
            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                HitBlockEvent hit = EventManager.call(new HitBlockEvent());
                if (hit.isCancelled()) {
                    event.setCanceled(true);
                }
            }
        }
        if (event.button == 1 && event.buttonstate) {
            RightClickMouseEvent right = EventManager.call(new RightClickMouseEvent());
            EventManager.call(new RightClickResolvedEvent(right.isCancelled()));
            if (right.isCancelled()) {
                event.setCanceled(true);
            }
        }
        if (event.dwheel != 0) {
            int offset = event.dwheel > 0 ? 1 : -1;
            SwapItemEvent swap = EventManager.call(new SwapItemEvent(mc.thePlayer.inventory.currentItem, offset));
            if (swap.isCancelled()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (yieldToStandaloneBridge()) {
            return;
        }
        if (event.isCanceled() || !isInGame() || event.entityPlayer != mc.thePlayer) {
            return;
        }
        if (!VisualRotationState.isActived()) {
            playerRenderRotationSnapshots.push(PlayerRenderRotationSnapshot.noChange());
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        playerRenderRotationSnapshots.push(new PlayerRenderRotationSnapshot(player));
        player.prevRotationPitch = VisualRotationState.getPrevRotationPitch();
        player.rotationPitch = VisualRotationState.getRotationPitch();
        player.prevRotationYawHead = VisualRotationState.getPrevRotationYawHead();
        player.rotationYawHead = VisualRotationState.getRotationYawHead();
        player.prevRenderYawOffset = VisualRotationState.getPrevRenderYawOffset();
        player.renderYawOffset = VisualRotationState.getRenderYawOffset();
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (yieldToStandaloneBridge()) {
            return;
        }
        if (event.entityPlayer != mc.thePlayer) {
            return;
        }
        restoreLatestPlayerRenderRotation();
    }

    private void restoreLatestPlayerRenderRotation() {
        PlayerRenderRotationSnapshot snapshot = playerRenderRotationSnapshots.poll();
        if (snapshot != null) {
            snapshot.restore();
        }
    }

    private void restoreDanglingPlayerRenderRotations() {
        while (!playerRenderRotationSnapshots.isEmpty()) {
            restoreLatestPlayerRenderRotation();
        }
    }

    private void shutdownInternal() {
        restoreDanglingPlayerRenderRotations();
        MovementInputBridge.setSafeWalkRequested(false);
        MovementInputBridge.setBeforeMoveInputHook(null);
        MovementInputBridge.setAfterMoveInputHook(null);
        MovementInputBridge.setDirectYawPhysics(true);
        MovementInputBridge.uninstall();
        removePacketHandler();
        clearBridgeState();
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(INSTANCE);
            FMLCommonHandler.instance().bus().unregister(INSTANCE);
            registered = false;
        }
    }

    private void clearBridgeState() {
        PacketBridgeSupport.clearNoEventPackets();
        PacketRotationState.clear();
        RotationExitState.clear();
        RotationState.clear();
        VisualRotationState.clear();
        rotationPublication.clear();
        activePreUpdate = null;
        YozakuraRuntime.rotationManager.clear();
        if (YozakuraRuntime.playerStateManager != null) {
            YozakuraRuntime.playerStateManager.resetTransientState();
        }
        restoreDanglingPlayerRenderRotations();
    }

    private void dispatchPreUpdate() {
        BridgeDebug.logState("forge", "PRE_START", false);
        VisualRotationState.beginTick();
        UpdateEvent update = new UpdateEvent(EventType.PRE, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        rotationPublication.beginPre();
        activePreUpdate = update;
        ForgeRotationPublication.Snapshot published = null;
        try {
            EventManager.call(update);
            RotationExitState.apply(update);
            EventManager.call(new RotationResolvedEvent(update));
            BridgeDebug.logUpdate("forge", "PRE_AFTER_EVENT", update, false);
            RotationState.applyState(update.isRotated(), update.getNewYaw(), update.getNewPitch(),
                    update.getPreYaw(), update.isRotating(), update.isMoveFix());
            published = rotationPublication.publish(RotationState.isActived(),
                    RotationState.getRotationYawHead(), RotationState.getRotationPitch());
            applyLocalViewRotation(update);
            VisualRotationState.finishTick();
            syncVisibleRotation();
            RotationDebug.logUpdate("forge", update);
            BridgeDebug.logUpdate("forge", "PRE_DONE", update, false);
        } finally {
            if (published == null) {
                published = rotationPublication.abortPre();
            }
            activePreUpdate = null;
            PacketBridgeHandler handler = packetBridgeHandler;
            if (handler != null) {
                handler.onRotationPublished(published);
                handler.markNextPlayerPacketTick(published.getGeneration());
            }
        }
    }

    private void applyLocalViewRotation(UpdateEvent update) {
        if (update == null || !update.isRotated() || mc.thePlayer == null
                || !YozakuraRuntime.rotationManager.isRotated()) {
            return;
        }
        mc.thePlayer.rotationYaw = update.getNewYaw();
        mc.thePlayer.rotationPitch = update.getNewPitch();
        mc.thePlayer.prevRotationYawHead = mc.thePlayer.rotationYawHead;
        mc.thePlayer.rotationYawHead = update.getNewYaw();
        mc.thePlayer.prevRenderYawOffset = mc.thePlayer.renderYawOffset;
        mc.thePlayer.renderYawOffset = update.getNewYaw();
    }

    private void dispatchSafeWalk() {
        SafeWalkEvent safeWalk = EventManager.call(new SafeWalkEvent(false));
        MovementInputBridge.setSafeWalkRequested(safeWalk.isSafeWalk());
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
                return;
            }
            if (channel != null && channel != next) {
                removePacketHandler();
            }
            if (next.pipeline().get(PacketPipelineAnchors.STANDALONE_BRIDGE_HANDLER_NAME) != null) {
                if (next.pipeline().get(HANDLER_NAME) != null) {
                    next.pipeline().remove(HANDLER_NAME);
                }
                abandonStaleForgeBridge(next);
                return;
            }
            Object existing = next.pipeline().get(HANDLER_NAME);
            if (existing == null) {
                PacketBridgeHandler handler = new PacketBridgeHandler();
                next.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                packetBridgeHandler = handler;
            } else if (existing instanceof PacketBridgeHandler) {
                packetBridgeHandler = (PacketBridgeHandler) existing;
            } else {
                next.pipeline().remove(HANDLER_NAME);
                PacketBridgeHandler handler = new PacketBridgeHandler();
                next.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                packetBridgeHandler = handler;
            }
            channel = next;
        } catch (Throwable ignored) {
            channel = null;
            packetBridgeHandler = null;
        }
    }

    private boolean yieldToStandaloneBridge() {
        if (StandaloneClient.isBridgeOwnerActive()) {
            removePacketHandler();
            abandonStaleForgeBridge(null);
            return true;
        }
        if (mc.getNetHandler() == null) {
            return false;
        }
        Channel next = getChannel(mc.getNetHandler().getNetworkManager());
        if (next == null || !next.isOpen()) {
            return false;
        }
        boolean standalone;
        try {
            standalone = next.pipeline().get(PacketPipelineAnchors.STANDALONE_BRIDGE_HANDLER_NAME) != null;
        } catch (Throwable ignored) {
            MovementInputBridge.setSafeWalkRequested(false);
            return true;
        }
        if (!standalone) {
            return false;
        }
        try {
            if (channel != null && channel != next) {
                removePacketHandler();
            }
            if (next.pipeline().get(HANDLER_NAME) != null) {
                next.pipeline().remove(HANDLER_NAME);
            }
            abandonStaleForgeBridge(next);
            return true;
        } catch (Throwable ignored) {
            MovementInputBridge.setSafeWalkRequested(false);
            return true;
        }
    }

    private void abandonStaleForgeBridge(Channel standaloneChannel) {
        MovementInputBridge.setSafeWalkRequested(false);
        if (channel == standaloneChannel) {
            channel = null;
        }
        packetBridgeHandler = null;
        restoreDanglingPlayerRenderRotations();
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

    private static final class PlayerRenderRotationSnapshot {
        private static final PlayerRenderRotationSnapshot NO_CHANGE = new PlayerRenderRotationSnapshot();

        private final EntityPlayerSP player;
        private final float prevPitch;
        private final float pitch;
        private final float prevYawHead;
        private final float yawHead;
        private final float prevRenderYawOffset;
        private final float renderYawOffset;

        private PlayerRenderRotationSnapshot() {
            this.player = null;
            this.prevPitch = 0.0F;
            this.pitch = 0.0F;
            this.prevYawHead = 0.0F;
            this.yawHead = 0.0F;
            this.prevRenderYawOffset = 0.0F;
            this.renderYawOffset = 0.0F;
        }

        private PlayerRenderRotationSnapshot(EntityPlayerSP player) {
            this.player = player;
            this.prevPitch = player.prevRotationPitch;
            this.pitch = player.rotationPitch;
            this.prevYawHead = player.prevRotationYawHead;
            this.yawHead = player.rotationYawHead;
            this.prevRenderYawOffset = player.prevRenderYawOffset;
            this.renderYawOffset = player.renderYawOffset;
        }

        private void restore() {
            if (player == null) {
                return;
            }
            player.prevRotationPitch = prevPitch;
            player.rotationPitch = pitch;
            player.prevRotationYawHead = prevYawHead;
            player.rotationYawHead = yawHead;
            player.prevRenderYawOffset = prevRenderYawOffset;
            player.renderYawOffset = renderYawOffset;
        }

        private static PlayerRenderRotationSnapshot noChange() {
            return NO_CHANGE;
        }
    }

    private final class PacketBridgeHandler extends ChannelDuplexHandler {
        private static final float ROTATION_EPSILON = 1.0E-3F;
        private static final float ROTATION_DEDUPE_STEP = 0.0096F;
        private final OutboundActionBatchQueue<DelayedPacket> delayedPackets =
                new OutboundActionBatchQueue<DelayedPacket>();
        private final Queue<DelayedPlayerPacket> delayedPlayerPackets = new ArrayDeque<DelayedPlayerPacket>();
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
            if (!YozakuraAuthGate.allowRuntime("forge-packet-send")) {
                super.write(ctx, msg, promise);
                return;
            }
            if (msg instanceof Packet<?>) {
                Packet<?> packet = (Packet<?>) msg;
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
                    BridgeDebug.logPacket("forge", "SEND_NO_EVENT", packet, false);
                }
                if (!skipPacketEvent) {
                    BridgeDebug.logPacket("forge", "SEND_IN", packet, false);
                    PacketEvent event = EventManager.call(new PacketEvent(EventType.SEND, packet));
                    if (event.isCancelled()) {
                        BridgeDebug.logPacket("forge", "SEND_CANCELLED", packet, false);
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
                ForgeRotationPublication.Snapshot rotation = rotationPublication.snapshot();
                if (packet instanceof C03PacketPlayer) {
                    if (rotation.isPreInProgress()) {
                        delayedPlayerPackets.add(new DelayedPlayerPacket((C03PacketPlayer) packet, promise,
                                rotation.getGeneration(), writeId));
                        BridgeDebug.logPacketDetail("forge", "SEND_PLAYER_DELAYED_QUEUE", packet, false,
                                describeRotationState(rotation));
                        return;
                    }
                    writePlayerPacket(ctx, (C03PacketPlayer) packet, promise, rotation, writeId);
                    return;
                }

                markSent(packet);
                BridgeDebug.logPacket("forge", "SEND_MARKED", packet, false);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(packet, promise, writeId)) {
                    BridgeDebug.logPacket("forge", "SEND_BLINK_BUFFERED", packet, false);
                    return;
                }
                BridgeDebug.logPacket("forge", "SEND_OUT", packet, false);
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
            } finally {
                super.handlerRemoved(ctx);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            failDelayedPackets(new ClosedChannelException());
            resetHandlerState();
            super.channelInactive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!YozakuraAuthGate.allowRuntime("forge-packet-receive")) {
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
                                       ForgeRotationPublication.Snapshot rotation, long writeId) throws Exception {
            boolean playerTickAdvanced = playerPacketTickGate.consumeNextPlayerPacket();
            if (playerTickAdvanced) {
                flushReadyActionPackets(ctx);
            }
            if (playerTickAdvanced && !rotation.isActive()) {
                flushCurrentActionPackets(ctx);
            }
            if (rotation.isActive()) {
                C03PacketPlayer rewritten = rewritePlayerPacket(packet, rotation);
                RotationDebug.logPacket("forge", packet, true);
                BridgeDebug.logPacketRewrite("forge", packet, rewritten, false);
                markSent(rewritten);
                BridgeDebug.logPacket("forge", "SEND_MARKED", rewritten, false);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(rewritten, promise, writeId)) {
                    BridgeDebug.logPacket("forge", "SEND_BLINK_BUFFERED", rewritten, false);
                } else {
                    super.write(ctx, rewritten, promise);
                }
            } else {
                hasSentSilentRotation = false;
                RotationDebug.logPacket("forge", packet, false);
                markSent(packet);
                BridgeDebug.logPacket("forge", "SEND_MARKED", packet, false);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(packet, promise, writeId)) {
                    BridgeDebug.logPacket("forge", "SEND_BLINK_BUFFERED", packet, false);
                } else {
                    BridgeDebug.logPacket("forge", "SEND_OUT", packet, false);
                    super.write(ctx, packet, promise);
                }
            }
            rotationPublication.markSent(rotation);
            if (rotation.isActive() && playerTickAdvanced) {
                promoteCurrentActionPackets();
            }
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
                                                     ForgeRotationPublication.Snapshot rotation) {
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

        private void onRotationPublished(final ForgeRotationPublication.Snapshot published) {
            final ChannelHandlerContext ctx = handlerContext;
            if (ctx == null || published == null) {
                return;
            }
            Runnable flushTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        flushDelayedPlayerPackets(ctx, published);
                    } catch (Throwable throwable) {
                        failDelayedPackets(throwable);
                        ctx.fireExceptionCaught(throwable);
                    }
                }
            };
            if (ctx.executor().inEventLoop()) {
                flushTask.run();
            } else {
                ctx.executor().execute(flushTask);
            }
        }

        private void flushDelayedPlayerPackets(ChannelHandlerContext ctx,
                                               ForgeRotationPublication.Snapshot published) throws Exception {
            DelayedPlayerPacket delayed;
            while ((delayed = delayedPlayerPackets.peek()) != null
                    && delayed.requiredGeneration <= published.getGeneration()) {
                delayedPlayerPackets.poll();
                try {
                    writePlayerPacket(ctx, delayed.packet, delayed.promise, published, delayed.writeId);
                } catch (Throwable throwable) {
                    if (delayed.promise != null) {
                        delayed.promise.tryFailure(throwable);
                    }
                    throw throwable;
                }
            }
        }

        private String describeRotationState(ForgeRotationPublication.Snapshot rotation) {
            return "generation=" + rotation.getGeneration()
                    + " sentGeneration=" + rotationPublication.getSentGeneration()
                    + " preInProgress=" + rotation.isPreInProgress()
                    + " rotationActive=" + rotation.isActive()
                    + " activePre=" + (activePreUpdate != null);
        }

        private void queueCurrentActionPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
            delayedPackets.addCurrent(new DelayedPacket(packet, promise, writeId));
            if (isClickWindowPacket(packet)) {
                currentClickWindowPackets++;
            }
            BridgeDebug.logPacket("forge", "SEND_ACTION_QUEUE", packet, false);
        }

        private void queueReadyActionPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
            delayedPackets.addReady(new DelayedPacket(packet, promise, writeId));
            if (isClickWindowPacket(packet)) {
                readyClickWindowPackets++;
            }
            BridgeDebug.logPacket("forge", "SEND_ACTION_READY_QUEUE", packet, false);
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
            BridgeDebug.logPacketDetail("forge", "SEND_ACTION_OUT", delayed.packet, false, source);
            if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                    && YozakuraRuntime.blinkManager.offerPacket(delayed.packet, delayed.promise,
                    delayed.writeId)) {
                BridgeDebug.logPacketDetail("forge", "SEND_ACTION_BLINK_BUFFERED", delayed.packet, false,
                        source);
                return;
            }
            super.write(ctx, delayed.packet, delayed.promise);
        }

        private void completeDroppedWrite(ChannelPromise promise) {
            if (promise != null) {
                promise.trySuccess();
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
            DelayedPlayerPacket delayedPlayer;
            while ((delayedPlayer = delayedPlayerPackets.poll()) != null) {
                if (delayedPlayer.promise != null) {
                    delayedPlayer.promise.tryFailure(cause);
                }
            }
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
            private final long writeId;

            private DelayedPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
                this.packet = packet;
                this.promise = promise;
                this.writeId = writeId;
            }
        }

        private final class DelayedPlayerPacket {
            private final C03PacketPlayer packet;
            private final ChannelPromise promise;
            private final long requiredGeneration;
            private final long writeId;

            private DelayedPlayerPacket(C03PacketPlayer packet, ChannelPromise promise, long requiredGeneration,
                                        long writeId) {
                this.packet = packet;
                this.promise = promise;
                this.requiredGeneration = requiredGeneration;
                this.writeId = writeId;
            }
        }
    }
}
