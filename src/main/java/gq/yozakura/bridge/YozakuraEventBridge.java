package gq.yozakura.bridge;

import gq.yozakura.auth.YozakuraAuthGate;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
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
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.engine.render.ShaderRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.HitBlockEvent;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
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
    private boolean forcedSneak;
    private final ArrayDeque<PlayerRenderRotationSnapshot> playerRenderRotationSnapshots =
            new ArrayDeque<PlayerRenderRotationSnapshot>();

    private YozakuraEventBridge() {
    }

    public static void init() {
        YozakuraRuntime.init();
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    public static void markNoEvent(Packet<?> packet) {
        PacketBridgeSupport.markNoEvent(packet);
    }

    public static void shutdown() {
        INSTANCE.shutdownInternal();
    }

    private static boolean consumeNoEvent(Packet<?> packet) {
        return PacketBridgeSupport.consumeNoEvent(packet);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            restoreDanglingPlayerRenderRotations();
        }
        if (!YozakuraAuthGate.allowRuntime("forge-client-tick")) {
            releaseForcedSneak();
            MovementInputBridge.setBeforeMoveInputHook(null);
            MovementInputBridge.setDirectYawPhysics(true);
            MovementInputBridge.uninstall();
            clearBridgeState();
            removePacketHandler();
            return;
        }
        if (!isInGame()) {
            releaseForcedSneak();
            MovementInputBridge.setBeforeMoveInputHook(null);
            MovementInputBridge.setDirectYawPhysics(true);
            MovementInputBridge.uninstall();
            clearBridgeState();
            removePacketHandler();
            return;
        }
        MovementInputBridge.install();
        MovementInputBridge.setBeforeMoveInputHook(null);
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
        releaseForcedSneak();
        MovementInputBridge.setBeforeMoveInputHook(null);
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
            if (next.pipeline().get(HANDLER_NAME) == null) {
                PacketBridgeHandler handler = new PacketBridgeHandler();
                next.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                packetBridgeHandler = handler;
            } else if (next.pipeline().get(HANDLER_NAME) instanceof PacketBridgeHandler) {
                packetBridgeHandler = (PacketBridgeHandler) next.pipeline().get(HANDLER_NAME);
            }
            channel = next;
        } catch (Throwable ignored) {
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
        private final Queue<DelayedPacket> delayedPackets = new ArrayDeque<DelayedPacket>();
        private final Queue<DelayedPlayerPacket> delayedPlayerPackets = new ArrayDeque<DelayedPlayerPacket>();
        private volatile ChannelHandlerContext handlerContext;
        private boolean hasSentSilentRotation;
        private boolean duplicateYawFlip;
        private float lastSilentYaw;
        private float lastSilentPitch;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            handlerContext = ctx;
            super.handlerAdded(ctx);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!YozakuraAuthGate.allowRuntime("forge-packet-send")) {
                super.write(ctx, msg, promise);
                return;
            }
            if (msg instanceof Packet<?>) {
                Packet<?> packet = (Packet<?>) msg;
                boolean skipPacketEvent = consumeNoEvent(packet);
                if (skipPacketEvent) {
                    BridgeDebug.logPacket("forge", "SEND_NO_EVENT", packet, false);
                }
                if (packet instanceof C0BPacketEntityAction
                        && MovementInputBridge.shouldBlockSprintPacket((C0BPacketEntityAction) packet)) {
                    BridgeDebug.logPacket("forge", "SEND_BLOCKED_SPRINT", packet, false);
                    completeDroppedWrite(promise);
                    return;
                }
                if (!skipPacketEvent) {
                    BridgeDebug.logPacket("forge", "SEND_IN", packet, false);
                    PacketEvent event = EventManager.call(new PacketEvent(EventType.SEND, packet));
                    if (event.isCancelled()) {
                        BridgeDebug.logPacket("forge", "SEND_CANCELLED", packet, false);
                        completeDroppedWrite(promise);
                        return;
                    }
                }

                ForgeRotationPublication.Snapshot rotation = rotationPublication.snapshot();
                if (shouldDelayUntilRotation(packet, rotation)) {
                    delayedPackets.add(new DelayedPacket(packet, promise, rotation.getGeneration()));
                    BridgeDebug.logPacketDetail("forge", "SEND_DELAYED_QUEUE", packet, false,
                            describeRotationState(rotation));
                    return;
                }
                if (packet instanceof C03PacketPlayer) {
                    if (rotation.isPreInProgress()) {
                        delayedPlayerPackets.add(new DelayedPlayerPacket((C03PacketPlayer) packet, promise,
                                rotation.getGeneration()));
                        BridgeDebug.logPacketDetail("forge", "SEND_PLAYER_DELAYED_QUEUE", packet, false,
                                describeRotationState(rotation));
                        return;
                    }
                    writePlayerPacket(ctx, (C03PacketPlayer) packet, promise, rotation);
                    return;
                }

                markSent(packet);
                BridgeDebug.logPacket("forge", "SEND_MARKED", packet, false);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(packet)) {
                    BridgeDebug.logPacket("forge", "SEND_BLINK_BUFFERED", packet, false);
                    completeDroppedWrite(promise);
                    return;
                }
                BridgeDebug.logPacket("forge", "SEND_OUT", packet, false);
            }
            super.write(ctx, msg, promise);
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
                                       ForgeRotationPublication.Snapshot rotation) throws Exception {
            if (rotation.isActive()) {
                C03PacketPlayer rewritten = rewritePlayerPacket(packet, rotation);
                RotationDebug.logPacket("forge", packet, true);
                BridgeDebug.logPacketRewrite("forge", packet, rewritten, false);
                markSent(rewritten);
                BridgeDebug.logPacket("forge", "SEND_MARKED", rewritten, false);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(rewritten)) {
                    BridgeDebug.logPacket("forge", "SEND_BLINK_BUFFERED", rewritten, false);
                    completeDroppedWrite(promise);
                } else {
                    super.write(ctx, rewritten, promise);
                }
            } else {
                hasSentSilentRotation = false;
                RotationDebug.logPacket("forge", packet, false);
                markSent(packet);
                BridgeDebug.logPacket("forge", "SEND_MARKED", packet, false);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(packet)) {
                    BridgeDebug.logPacket("forge", "SEND_BLINK_BUFFERED", packet, false);
                    completeDroppedWrite(promise);
                } else {
                    BridgeDebug.logPacket("forge", "SEND_OUT", packet, false);
                    super.write(ctx, packet, promise);
                }
            }
            rotationPublication.markSent(rotation);
            flushDelayedPackets(ctx);
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

        private boolean shouldDelayUntilRotation(Packet<?> packet,
                                                 ForgeRotationPublication.Snapshot rotation) {
            return isRotationSensitiveAction(packet)
                    && rotation.getGeneration() > 0L
                    && !rotationPublication.isGenerationSent(rotation.getGeneration());
        }

        private boolean isRotationSensitiveAction(Packet<?> packet) {
            return packet instanceof net.minecraft.network.play.client.C02PacketUseEntity
                    || packet instanceof net.minecraft.network.play.client.C07PacketPlayerDigging
                    || packet instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
                    || packet instanceof net.minecraft.network.play.client.C0APacketAnimation;
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
                    writePlayerPacket(ctx, delayed.packet, delayed.promise, published);
                } catch (Throwable throwable) {
                    if (delayed.promise != null) {
                        delayed.promise.tryFailure(throwable);
                    }
                    throw throwable;
                }
            }
        }

        private void flushDelayedPackets(ChannelHandlerContext ctx) throws Exception {
            long sentGeneration = rotationPublication.getSentGeneration();
            DelayedPacket delayed;
            while ((delayed = delayedPackets.peek()) != null
                    && delayed.requiredGeneration <= sentGeneration) {
                delayedPackets.poll();
                markSent(delayed.packet);
                BridgeDebug.logPacketDetail("forge", "SEND_DELAYED_OUT", delayed.packet, false,
                        "requiredGeneration=" + delayed.requiredGeneration
                                + " sentGeneration=" + sentGeneration);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(delayed.packet)) {
                    completeDroppedWrite(delayed.promise);
                    BridgeDebug.logPacket("forge", "SEND_DELAYED_BLINK_BUFFERED", delayed.packet, false);
                    continue;
                }
                super.write(ctx, delayed.packet, delayed.promise);
            }
        }

        private String describeRotationState(ForgeRotationPublication.Snapshot rotation) {
            return "generation=" + rotation.getGeneration()
                    + " sentGeneration=" + rotationPublication.getSentGeneration()
                    + " preInProgress=" + rotation.isPreInProgress()
                    + " rotationActive=" + rotation.isActive()
                    + " activePre=" + (activePreUpdate != null);
        }

        private void completeDroppedWrite(ChannelPromise promise) {
            if (promise != null) {
                promise.trySuccess();
            }
        }

        private void failDelayedPackets(Throwable cause) {
            DelayedPacket delayed;
            while ((delayed = delayedPackets.poll()) != null) {
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
        }

        private void resetHandlerState() {
            handlerContext = null;
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
            private final long requiredGeneration;

            private DelayedPacket(Packet<?> packet, ChannelPromise promise, long requiredGeneration) {
                this.packet = packet;
                this.promise = promise;
                this.requiredGeneration = requiredGeneration;
            }
        }

        private final class DelayedPlayerPacket {
            private final C03PacketPlayer packet;
            private final ChannelPromise promise;
            private final long requiredGeneration;

            private DelayedPlayerPacket(C03PacketPlayer packet, ChannelPromise promise, long requiredGeneration) {
                this.packet = packet;
                this.promise = promise;
                this.requiredGeneration = requiredGeneration;
            }
        }
    }
}
