package gq.vapulite.bridge;

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
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import gq.vapulite.runtime.VapuRuntime;
import gq.vapulite.engine.render.ShaderRenderer;
import gq.vapulite.event.bus.EventManager;
import gq.vapulite.event.bus.types.EventType;
import gq.vapulite.event.bridge.HitBlockEvent;
import gq.vapulite.event.bridge.LeftClickMouseEvent;
import gq.vapulite.event.bridge.LivingUpdateEvent;
import gq.vapulite.event.bridge.MoveInputEvent;
import gq.vapulite.event.bridge.PacketEvent;
import gq.vapulite.event.bridge.Render2DEvent;
import gq.vapulite.event.bridge.Render3DEvent;
import gq.vapulite.event.bridge.RightClickMouseEvent;
import gq.vapulite.event.bridge.SafeWalkEvent;
import gq.vapulite.event.bridge.StrafeEvent;
import gq.vapulite.event.bridge.SwapItemEvent;
import gq.vapulite.event.bridge.UpdateEvent;
import gq.vapulite.manager.RotationState;
import gq.vapulite.util.module.PacketUtil;

import java.lang.reflect.Field;

public final class VapuEventBridge {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "vapulite_event_bridge";
    private static final VapuEventBridge INSTANCE = new VapuEventBridge();
    private static boolean registered;
    private static Field channelField;
    private Channel channel;
    private boolean forcedSneak;
    private boolean renderingPlayer;
    private float savedPrevPitch;
    private float savedPitch;
    private float savedPrevYawHead;
    private float savedYawHead;
    private float savedPrevRenderYawOffset;
    private float savedRenderYawOffset;

    private VapuEventBridge() {
    }

    public static void init() {
        VapuRuntime.init();
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
        if (!isInGame()) {
            releaseForcedSneak();
            return;
        }
        injectPacketHandler();
        if (event.phase == TickEvent.Phase.START) {
            EventManager.call(new gq.vapulite.event.bridge.TickEvent(EventType.PRE));
            dispatchPreUpdate();
            dispatchMoveInput();
            dispatchStrafe();
            dispatchSafeWalk();
            syncAuraTarget();
        } else {
            EventManager.call(new gq.vapulite.event.bridge.TickEvent(EventType.POST));
            EventManager.call(new UpdateEvent(EventType.POST, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch));
            dispatchSafeWalk();
            syncAuraTarget();
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving == mc.thePlayer && isInGame()) {
            EventManager.call(new LivingUpdateEvent());
        }
    }

    @SubscribeEvent
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (isInGame()) {
            ShaderRenderer.beginOverlayFrame();
            EventManager.call(new Render2DEvent(event.partialTicks));
        }
    }

    @SubscribeEvent
    public void onRender3D(RenderWorldLastEvent event) {
        if (isInGame()) {
            EventManager.call(new Render3DEvent(event.partialTicks));
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
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
        if (!isInGame() || event.entityPlayer != mc.thePlayer || !RotationState.isActived()) {
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
        player.prevRotationPitch = RotationState.getPrevRotationPitch();
        player.rotationPitch = RotationState.getRotationPitch();
        player.prevRotationYawHead = RotationState.getPrevRotationYawHead();
        player.rotationYawHead = RotationState.getRotationYawHead();
        player.prevRenderYawOffset = RotationState.getPrevRenderYawOffset();
        player.renderYawOffset = RotationState.getRenderYawOffset();
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
        UpdateEvent update = new UpdateEvent(EventType.PRE, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        EventManager.call(update);
        RotationState.applyState(update.isRotated(), update.getNewYaw(), update.getNewPitch(),
                update.getPreYaw(), update.isRotating());
        syncVisibleRotation();
    }

    private void dispatchMoveInput() {
        EventManager.call(new MoveInputEvent());
    }

    private void dispatchStrafe() {
        StrafeEvent strafe = new StrafeEvent(mc.thePlayer.movementInput.moveStrafe,
                mc.thePlayer.movementInput.moveForward, 0.91F);
        EventManager.call(strafe);
        mc.thePlayer.movementInput.moveStrafe = strafe.getStrafe();
        mc.thePlayer.movementInput.moveForward = strafe.getForward();
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
        if (!RotationState.isActived()) {
            return;
        }
        mc.thePlayer.prevRotationYawHead = RotationState.getPrevRotationYawHead();
        mc.thePlayer.rotationYawHead = RotationState.getRotationYawHead();
        mc.thePlayer.prevRenderYawOffset = RotationState.getPrevRenderYawOffset();
        mc.thePlayer.renderYawOffset = RotationState.getRenderYawOffset();
    }

    private void syncAuraTarget() {
        gq.vapulite.module.Module module = gq.vapulite.manager.ModuleManager.getModule("KillAura");
        if (module instanceof gq.vapulite.module.combat.KillAura) {
            gq.vapulite.module.combat.KillAura.target =
                    ((gq.vapulite.module.combat.KillAura) module).getTarget();
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
        private boolean hasSentSilentRotation;
        private float lastSilentYaw;
        private float lastSilentPitch;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Packet<?>) {
                Packet<?> packet = (Packet<?>) msg;
                if (consumeNoEvent(packet)) {
                    super.write(ctx, msg, promise);
                    return;
                }
                PacketEvent event = EventManager.call(new PacketEvent(EventType.SEND, packet));
                if (event.isCancelled()) {
                    return;
                }
                if (VapuRuntime.playerStateManager != null) {
                    VapuRuntime.playerStateManager.handlePacket(packet);
                }
                if (VapuRuntime.blinkManager != null && VapuRuntime.blinkManager.isBlinking()
                        && VapuRuntime.blinkManager.offerPacket(packet)) {
                    return;
                }
                if (packet instanceof C03PacketPlayer) {
                    if (RotationState.isActived()) {
                        super.write(ctx, rewritePlayerPacket((C03PacketPlayer) packet), promise);
                        return;
                    }
                    hasSentSilentRotation = false;
                }
            }
            super.write(ctx, msg, promise);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
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
            boolean onGround = packet.isOnGround();
            float yaw = RotationState.getRotationYawHead();
            float pitch = RotationState.getRotationPitch();

            if (!shouldSendLook(yaw, pitch)) {
                return stripLook(packet);
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

        private C03PacketPlayer stripLook(C03PacketPlayer packet) {
            if (packet.isMoving()) {
                if (packet.getRotating()) {
                    return new C03PacketPlayer.C04PacketPlayerPosition(packet.getPositionX(), packet.getPositionY(),
                            packet.getPositionZ(), packet.isOnGround());
                }
                return packet;
            }
            if (packet.getRotating()) {
                return new C03PacketPlayer(packet.isOnGround());
            }
            return packet;
        }
    }
}
