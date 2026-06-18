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
import gq.yozakura.manager.RotationDebug;
import gq.yozakura.manager.RotationState;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.util.module.PacketUtil;

import java.lang.reflect.Field;

public final class YozakuraEventBridge {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "yozakura_event_bridge";
    private static final YozakuraEventBridge INSTANCE = new YozakuraEventBridge();
    private static boolean registered;
    private static Field channelField;
    private static int lastOverlayCounter = Integer.MIN_VALUE;
    private static long lastOverlayNanos;
    private Channel channel;
    private boolean forcedSneak;
    private boolean renderingPlayer;
    private float savedPrevPitch;
    private float savedPitch;
    private float savedPrevYawHead;
    private float savedYawHead;
    private float savedPrevRenderYawOffset;
    private float savedRenderYawOffset;

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

    private static boolean consumeNoEvent(Packet<?> packet) {
        return PacketBridgeSupport.consumeNoEvent(packet);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!YozakuraAuthGate.allowRuntime("forge-client-tick")) {
            releaseForcedSneak();
            RotationState.clear();
            VisualRotationState.clear();
            return;
        }
        if (!isInGame()) {
            releaseForcedSneak();
            MovementInputBridge.uninstall();
            RotationState.clear();
            VisualRotationState.clear();
            return;
        }
        MovementInputBridge.install();
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
            EventManager.call(new Render2DEvent(event.partialTicks));
            markOverlayRendered();
        }
    }

    @SubscribeEvent
    public void onRender3D(RenderWorldLastEvent event) {
        if (!YozakuraAuthGate.allowRuntime("forge-render-3d")) {
            return;
        }
        if (isInGame()) {
            EventManager.call(new Render3DEvent(event.partialTicks));
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

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!isInGame() || event.entityPlayer != mc.thePlayer || !VisualRotationState.isActived()) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        renderingPlayer = true;
        savedPrevPitch = player.prevRotationPitch;
        savedPitch = player.rotationPitch;
        savedPrevYawHead = player.prevRotationYawHead;
        savedYawHead = player.rotationYawHead;
        savedPrevRenderYawOffset = player.prevRenderYawOffset;
        savedRenderYawOffset = player.renderYawOffset;
        player.prevRotationPitch = VisualRotationState.getPrevRotationPitch();
        player.rotationPitch = VisualRotationState.getRotationPitch();
        player.prevRotationYawHead = VisualRotationState.getPrevRotationYawHead();
        player.rotationYawHead = VisualRotationState.getRotationYawHead();
        player.prevRenderYawOffset = VisualRotationState.getPrevRenderYawOffset();
        player.renderYawOffset = VisualRotationState.getRenderYawOffset();
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!renderingPlayer || event.entityPlayer != mc.thePlayer) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        renderingPlayer = false;
        player.prevRotationPitch = savedPrevPitch;
        player.rotationPitch = savedPitch;
        player.prevRotationYawHead = savedPrevYawHead;
        player.rotationYawHead = savedYawHead;
        player.prevRenderYawOffset = savedPrevRenderYawOffset;
        player.renderYawOffset = savedRenderYawOffset;
    }

    private void dispatchPreUpdate() {
        BridgeDebug.logState("forge", "PRE_START", false);
        VisualRotationState.beginTick();
        UpdateEvent update = new UpdateEvent(EventType.PRE, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        EventManager.call(update);
        BridgeDebug.logUpdate("forge", "PRE_AFTER_EVENT", update, false);
        RotationState.applyState(update.isRotated(), update.getNewYaw(), update.getNewPitch(),
                update.getPreYaw(), update.isRotating());
        VisualRotationState.finishTick();
        syncVisibleRotation();
        RotationDebug.logUpdate("forge", update);
        BridgeDebug.logUpdate("forge", "PRE_DONE", update, false);
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
                next.pipeline().addBefore("packet_handler", HANDLER_NAME, new PacketBridgeHandler());
            }
            channel = next;
        } catch (Throwable ignored) {
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
            if (old.isOpen() && old.pipeline().get(HANDLER_NAME) != null) {
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

    private static final class PacketBridgeHandler extends ChannelDuplexHandler {
        private static final float ROTATION_EPSILON = 1.0E-3F;
        private static final float ROTATION_DEDUPE_STEP = 0.0096F;
        private boolean hasSentSilentRotation;
        private boolean duplicateYawFlip;
        private float lastSilentYaw;
        private float lastSilentPitch;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!YozakuraAuthGate.allowRuntime("forge-packet-send")) {
                super.write(ctx, msg, promise);
                return;
            }
            if (msg instanceof Packet<?>) {
                Packet<?> packet = (Packet<?>) msg;
                if (consumeNoEvent(packet)) {
                    BridgeDebug.logPacket("forge", "SEND_NO_EVENT", packet, false);
                    super.write(ctx, msg, promise);
                    return;
                }
                if (packet instanceof C0BPacketEntityAction
                        && MovementInputBridge.shouldBlockSprintPacket((C0BPacketEntityAction) packet)) {
                    BridgeDebug.logPacket("forge", "SEND_BLOCKED_SPRINT", packet, false);
                    return;
                }
                BridgeDebug.logPacket("forge", "SEND_IN", packet, false);
                PacketEvent event = EventManager.call(new PacketEvent(EventType.SEND, packet));
                if (event.isCancelled()) {
                    BridgeDebug.logPacket("forge", "SEND_CANCELLED", packet, false);
                    return;
                }
                if (YozakuraRuntime.playerStateManager != null) {
                    YozakuraRuntime.playerStateManager.handlePacket(packet);
                }
                BridgeDebug.logPacket("forge", "SEND_MARKED", packet, false);
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(packet)) {
                    BridgeDebug.logPacket("forge", "SEND_BLINK_BUFFERED", packet, false);
                    promise.setSuccess();
                    return;
                }
                if (packet instanceof C03PacketPlayer && RotationState.isActived()) {
                    RotationDebug.logPacket("forge", (C03PacketPlayer) packet, true);
                    C03PacketPlayer rewritten = rewritePlayerPacket((C03PacketPlayer) packet);
                    BridgeDebug.logPacketRewrite("forge", (C03PacketPlayer) packet, rewritten, false);
                    super.write(ctx, rewritten, promise);
                    return;
                }
                if (packet instanceof C03PacketPlayer) {
                    hasSentSilentRotation = false;
                    RotationDebug.logPacket("forge", (C03PacketPlayer) packet, false);
                }
                BridgeDebug.logPacket("forge", "SEND_OUT", packet, false);
            }
            super.write(ctx, msg, promise);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!YozakuraAuthGate.allowRuntime("forge-packet-receive")) {
                super.channelRead(ctx, msg);
                return;
            }
            if (msg instanceof Packet<?>) {
                Packet<?> packet = (Packet<?>) msg;
                if (!PacketUtil.skipReceiveEvent.remove(packet)) {
                    PacketEvent event = EventManager.call(new PacketEvent(EventType.RECEIVE, packet));
                    if (event.isCancelled()) {
                        return;
                    }
                }
            }
            super.channelRead(ctx, msg);
        }

        private C03PacketPlayer rewritePlayerPacket(C03PacketPlayer packet) {
            float yaw = RotationState.getRotationYawHead();
            float pitch = RotationState.getRotationPitch();
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
                    || Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - lastSilentYaw)) > ROTATION_EPSILON
                    || Math.abs(pitch - lastSilentPitch) > ROTATION_EPSILON;
        }

        private float nudgeDuplicateYaw(float yaw) {
            duplicateYawFlip = !duplicateYawFlip;
            return yaw + (duplicateYawFlip ? ROTATION_DEDUPE_STEP : -ROTATION_DEDUPE_STEP);
        }
    }
}
