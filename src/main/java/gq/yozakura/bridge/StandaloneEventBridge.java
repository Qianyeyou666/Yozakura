package gq.yozakura.bridge;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.event.bridge.HitBlockEvent;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.SafeWalkEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.manager.RotationDebug;
import gq.yozakura.manager.RotationState;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.util.module.PacketUtil;
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

public final class StandaloneEventBridge {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "yozakura_standalone_event_bridge";
    private static Field channelField;
    private Channel channel;
    private boolean forcedSneak;
    private boolean lastLeftButton;
    private boolean lastRightButton;
    private boolean packetHandlerLogged;
    private boolean packetHandlerFailureLogged;

    public void tick() {
        if (!YozakuraAuthGate.allowRuntime("standalone-tick")) {
            releaseForcedSneak();
            resetMouseState();
            MovementInputBridge.uninstall();
            return;
        }
        if (!isInGame()) {
            releaseForcedSneak();
            resetMouseState();
            MovementInputBridge.uninstall();
            VisualRotationState.clear();
            return;
        }
        MovementInputBridge.install();
        StandaloneGuiIngame.install(mc);
        StandaloneEntityRenderer.install(mc);
        injectPacketHandler();
        dispatchForgeTick(gq.yozakura.bridge.forge.TickEvent.Phase.START);
        dispatchMouseButtons();
        EventManager.call(new TickEvent(EventType.PRE));
        dispatchPreUpdate();
        dispatchSafeWalk();
        EventManager.call(new gq.yozakura.bridge.forge.LivingEvent.LivingUpdateEvent(mc.thePlayer));
        syncAuraTarget();
        EventManager.call(new TickEvent(EventType.POST));
        EventManager.call(new UpdateEvent(EventType.POST, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch));
        dispatchForgeTick(gq.yozakura.bridge.forge.TickEvent.Phase.END);
        MovementInputBridge.restoreRotation();
        dispatchSafeWalk();
        syncAuraTarget();
        MovementInputBridge.finishTick();
    }

    public void shutdown() {
        releaseForcedSneak();
        MovementInputBridge.uninstall();
        VisualRotationState.clear();
        removePacketHandler();
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
            if (leftDown && !lastLeftButton) {
                dispatchMouseButton(0);
            }
            if (rightDown && !lastRightButton) {
                dispatchMouseButton(1);
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
            if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
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
        VisualRotationState.beginTick();
        UpdateEvent update = new UpdateEvent(EventType.PRE, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        EventManager.call(update);
        RotationState.applyState(update.isRotated(), update.getNewYaw(), update.getNewPitch(),
                update.getPreYaw(), update.isRotating());
        VisualRotationState.finishTick();
        syncVisibleRotation();
        RotationDebug.logUpdate("standalone", update);
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
                logPacketHandlerFailure("Standalone packet bridge unavailable: network channel not found", null);
                return;
            }
            if (channel != null && channel != next) {
                removePacketHandler();
            }
            if (next.pipeline().get(HANDLER_NAME) == null) {
                next.pipeline().addBefore("packet_handler", HANDLER_NAME, new PacketBridgeHandler());
            }
            logPacketHandlerInstalled();
            channel = next;
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

    private static final class PacketBridgeHandler extends ChannelDuplexHandler {
        private static final float ROTATION_EPSILON = 1.0E-3F;
        private static final float ROTATION_DEDUPE_STEP = 0.0096F;
        private boolean hasSentSilentRotation;
        private boolean duplicateYawFlip;
        private float lastSilentYaw;
        private float lastSilentPitch;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!YozakuraAuthGate.allowRuntime("standalone-packet-send")) {
                super.write(ctx, msg, promise);
                return;
            }
            if (msg instanceof Packet<?>) {
                Packet<?> packet = (Packet<?>) msg;
                if (PacketBridgeSupport.consumeNoEvent(packet)) {
                    super.write(ctx, msg, promise);
                    return;
                }
                if (packet instanceof C0BPacketEntityAction
                        && MovementInputBridge.shouldBlockSprintPacket((C0BPacketEntityAction) packet)) {
                    return;
                }
                PacketEvent event = EventManager.call(new PacketEvent(EventType.SEND, packet));
                if (event.isCancelled()) {
                    return;
                }
                if (YozakuraRuntime.playerStateManager != null) {
                    YozakuraRuntime.playerStateManager.handlePacket(packet);
                }
                if (YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking()
                        && YozakuraRuntime.blinkManager.offerPacket(packet)) {
                    return;
                }
                if (packet instanceof C03PacketPlayer && RotationState.isActived()) {
                    RotationDebug.logPacket("standalone", (C03PacketPlayer) packet, true);
                    super.write(ctx, rewritePlayerPacket((C03PacketPlayer) packet), promise);
                    return;
                }
                if (packet instanceof C03PacketPlayer) {
                    hasSentSilentRotation = false;
                    RotationDebug.logPacket("standalone", (C03PacketPlayer) packet, false);
                }
            }
            super.write(ctx, msg, promise);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!YozakuraAuthGate.allowRuntime("standalone-packet-receive")) {
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
