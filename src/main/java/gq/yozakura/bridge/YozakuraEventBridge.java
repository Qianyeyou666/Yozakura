package gq.yozakura.bridge;

import gq.yozakura.k.B;
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
import net.minecraftforge.event.world.WorldEvent;
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
import gq.yozakura.event.bridge.LoadWorldEvent;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.PlayerPacketBoundaryEvent;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bridge.RenderTickEndEvent;
import gq.yozakura.event.bridge.RenderTickStartEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.RightClickResolvedEvent;
import gq.yozakura.event.bridge.RotationPublishedEvent;
import gq.yozakura.event.bridge.RotationResolvedEvent;
import gq.yozakura.event.bridge.SafeWalkEvent;
import gq.yozakura.event.bridge.SwapItemEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.manager.BridgeDebug;
import gq.yozakura.manager.PacketRotationState;
import gq.yozakura.manager.RotationDebug;
import gq.yozakura.manager.RotationExitState;
import gq.yozakura.manager.RotationState;
import gq.yozakura.bridge.util.ReflectionUtils;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.module.render.FreeLook;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

public final class YozakuraEventBridge implements ClientBridge {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "yozakura_event_bridge";
    private static final YozakuraEventBridge INSTANCE = new YozakuraEventBridge();
    private static boolean registered;
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

    public static void initBridge() {
        INSTANCE.init();
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

    public static void shutdownBridge() {
        INSTANCE.shutdown();
    }

    @Override
    public void init() {
        YozakuraRuntime.init();
        if (registered) {
            return;
        }
        boolean forgeRegistrationAttempted = false;
        boolean fmlRegistrationAttempted = false;
        try {
            forgeRegistrationAttempted = true;
            MinecraftForge.EVENT_BUS.register(this);
            fmlRegistrationAttempted = true;
            FMLCommonHandler.instance().bus().register(this);
            registered = true;
        } catch (RuntimeException failure) {
            rollbackFailedRegistration(failure, forgeRegistrationAttempted, fmlRegistrationAttempted);
            throw failure;
        } catch (Error failure) {
            rollbackFailedRegistration(failure, forgeRegistrationAttempted, fmlRegistrationAttempted);
            throw failure;
        }
    }

    @Override
    public void shutdown() {
        shutdownInternal();
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
        return registered;
    }

    @Override
    public void tick(boolean playerTick) {
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

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        EventManager.call(new LoadWorldEvent());
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (yieldToStandaloneBridge()) {
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            EventManager.call(new RenderTickEndEvent(event.renderTickTime));
        } else if (B.permitRenderDispatch() && isInGame()) {
            EventManager.call(new RenderTickStartEvent(event.renderTickTime));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (yieldToStandaloneBridge()) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            restoreDanglingPlayerRenderRotations();
        }
        if (!B.permitTickDispatch()) {
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
        if (!B.permitRenderDispatch()) {
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
        if (!B.permitRenderDispatch()) {
            return;
        }
        if (isInGame()) {
            try {
                FreeLook.restorePlayerFacingForOverlays();
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
        if (!B.permitInputDispatch()) {
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
        boolean visualRotation = VisualRotationState.isActived();
        boolean leaderRotation = RotationState.isRotated(1);
        if (!visualRotation && !leaderRotation) {
            playerRenderRotationSnapshots.push(PlayerRenderRotationSnapshot.noChange());
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        playerRenderRotationSnapshots.push(new PlayerRenderRotationSnapshot(player));
        player.prevRotationPitch = visualRotation
                ? VisualRotationState.getPrevRotationPitch() : RotationState.getPrevRotationPitch();
        player.rotationPitch = visualRotation
                ? VisualRotationState.getRotationPitch() : RotationState.getRotationPitch();
        player.prevRotationYawHead = visualRotation
                ? VisualRotationState.getPrevRotationYawHead() : RotationState.getPrevRotationYawHead();
        player.rotationYawHead = visualRotation
                ? VisualRotationState.getRotationYawHead() : RotationState.getRotationYawHead();
        player.prevRenderYawOffset = visualRotation
                ? VisualRotationState.getPrevRenderYawOffset() : RotationState.getPrevRenderYawOffset();
        player.renderYawOffset = visualRotation
                ? VisualRotationState.getRenderYawOffset() : RotationState.getRenderYawOffset();
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
        float prevPublishedYaw = RotationState.isActived()
                ? RotationState.getRotationYawHead() : mc.thePlayer.rotationYaw;
        float prevPublishedPitch = RotationState.isActived()
                ? RotationState.getRotationPitch() : mc.thePlayer.rotationPitch;
        UpdateEvent update = new UpdateEvent(EventType.PRE, prevPublishedYaw, prevPublishedPitch,
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
            EventManager.call(new RotationPublishedEvent(update));
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
            Channel next = ReflectionUtils.getChannel(manager);
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
        Channel next = ReflectionUtils.getChannel(mc.getNetHandler().getNetworkManager());
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

    private static final class DelayedPacket {
        private final Packet<?> packet;
        private final ChannelPromise promise;
        private final long writeId;

        private DelayedPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
            this.packet = packet;
            this.promise = promise;
            this.writeId = writeId;
        }
    }

    private static final class DelayedPlayerPacket {
        private final C03PacketPlayer packet;
        private final ChannelPromise promise;
        private final long requiredGeneration;
        private final long writeId;
        private final boolean nonCanonicalPlayerPacket;

        private DelayedPlayerPacket(C03PacketPlayer packet, ChannelPromise promise, long requiredGeneration,
                                    long writeId, boolean nonCanonicalPlayerPacket) {
            this.packet = packet;
            this.promise = promise;
            this.requiredGeneration = requiredGeneration;
            this.writeId = writeId;
            this.nonCanonicalPlayerPacket = nonCanonicalPlayerPacket;
        }
    }

    private final class PacketBridgeHandler extends BasePacketBridgeHandler<ForgeRotationPublication.Snapshot, DelayedPacket> {
        private final Queue<DelayedPlayerPacket> delayedPlayerPackets = new ArrayDeque<DelayedPlayerPacket>();
        private boolean flushingDelayedPlayerPackets;

        @Override
        protected String getBridgeType() {
            return "forge";
        }

        @Override
        protected boolean isBridgeTerminated() {
            return false;
        }

        @Override
        protected ForgeRotationPublication.Snapshot getRotationSnapshot() {
            return rotationPublication.snapshot();
        }

        @Override
        protected boolean isPreUpdatePending() {
            return activePreUpdate != null;
        }

        @Override
        protected boolean isPacketPendingPost() {
            return false;
        }

        @Override
        protected DelayedPacket createDelayedPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
            return new DelayedPacket(packet, promise, writeId);
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
            return false;
        }

        @Override
        protected boolean isRotationActive(ForgeRotationPublication.Snapshot snapshot) {
            return snapshot.isActive();
        }

        @Override
        protected float getRotationYaw(ForgeRotationPublication.Snapshot snapshot) {
            return snapshot.getYaw();
        }

        @Override
        protected float getRotationPitch(ForgeRotationPublication.Snapshot snapshot) {
            return snapshot.getPitch();
        }

        @Override
        protected long getRotationGeneration(ForgeRotationPublication.Snapshot snapshot) {
            return snapshot.getGeneration();
        }

        @Override
        protected void markRotationSent(ForgeRotationPublication.Snapshot snapshot) {
            rotationPublication.markSent(snapshot);
        }

        @Override
        protected void onHandlerRemoved(ChannelHandlerContext ctx) {
        }

        @Override
        protected void onChannelInactive(ChannelHandlerContext ctx) {
        }

        @Override
        protected void onResetHandlerState() {
            if (packetBridgeHandler == this) {
                packetBridgeHandler = null;
            }
        }

        @Override
        protected void onAcceptedTeleportBoundary() {
            rotationPublication.invalidateForTeleport();
        }

        @Override
        protected boolean shouldDelayPlayerPacket(ForgeRotationPublication.Snapshot snapshot) {
            return snapshot.isPreInProgress();
        }

        @Override
        protected void handleDelayedPlayerPacket(C03PacketPlayer packet, ChannelPromise promise,
                                                  ForgeRotationPublication.Snapshot snapshot, long writeId,
                                                  boolean nonCanonicalPlayerPacket) {
            delayedPlayerPackets.add(new DelayedPlayerPacket(packet, promise,
                    snapshot.getGeneration(), writeId, nonCanonicalPlayerPacket));
            BridgeDebug.logPacketDetail("forge", "SEND_PLAYER_DELAYED_QUEUE", packet, false,
                    describeRotationState(snapshot));
        }

        @Override
        protected void writePlayerPacketInternal(ChannelHandlerContext ctx, C03PacketPlayer packet,
                                                  ChannelPromise promise, ForgeRotationPublication.Snapshot rotation,
                                                  long writeId, boolean canonicalPlayerPacket,
                                                  boolean preservePlayerLook) throws Exception {
            writePlayerPacketCommon(ctx, packet, promise, rotation, writeId, canonicalPlayerPacket,
                    activePreUpdate != null, preservePlayerLook);
        }

        @Override
        protected void logActionQueue(Packet<?> packet, boolean pendingPost) {
            BridgeDebug.logPacket("forge", "SEND_ACTION_QUEUE", packet, pendingPost);
        }

        @Override
        protected void logActionReadyQueue(Packet<?> packet, boolean pendingPost) {
            BridgeDebug.logPacket("forge", "SEND_ACTION_READY_QUEUE", packet, pendingPost);
        }

        @Override
        protected void logActionOut(Packet<?> packet, boolean pendingPost, String source) {
            BridgeDebug.logPacketDetail("forge", "SEND_ACTION_OUT", packet, pendingPost, source);
        }

        @Override
        protected void logActionBlinkBuffered(Packet<?> packet, boolean pendingPost, String source) {
            BridgeDebug.logPacketDetail("forge", "SEND_ACTION_BLINK_BUFFERED", packet, pendingPost, source);
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
            flushingDelayedPlayerPackets = true;
            try {
                while ((delayed = delayedPlayerPackets.peek()) != null
                        && delayed.requiredGeneration <= published.getGeneration()) {
                    delayedPlayerPackets.poll();
                    try {
                        writePlayerPacketInternal(ctx, delayed.packet, delayed.promise, published, delayed.writeId,
                                !delayed.nonCanonicalPlayerPacket, false);
                    } catch (Throwable throwable) {
                        if (delayed.promise != null) {
                            delayed.promise.tryFailure(throwable);
                        }
                        throw throwable;
                    }
                }
            } finally {
                flushingDelayedPlayerPackets = false;
            }
        }

        private String describeRotationState(ForgeRotationPublication.Snapshot rotation) {
            return "generation=" + rotation.getGeneration()
                    + " sentGeneration=" + rotationPublication.getSentGeneration()
                    + " preInProgress=" + rotation.isPreInProgress()
                    + " rotationActive=" + rotation.isActive()
                    + " activePre=" + (activePreUpdate != null);
        }

        @Override
        protected void discardDelayedPacketsForTeleport() {
            super.discardDelayedPacketsForTeleport();
            DelayedPlayerPacket delayedPlayer;
            while ((delayedPlayer = delayedPlayerPackets.poll()) != null) {
                completeDroppedWrite(delayedPlayer.promise);
            }
        }

        @Override
        protected void failDelayedPackets(Throwable cause) {
            super.failDelayedPackets(cause);
            DelayedPlayerPacket delayedPlayer;
            while ((delayedPlayer = delayedPlayerPackets.poll()) != null) {
                if (delayedPlayer.promise != null) {
                    delayedPlayer.promise.tryFailure(cause);
                }
            }
        }
    }
}
