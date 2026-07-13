package gq.yozakura.module.combat;

import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.AttackEvent;
import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.FloatProperty;
import gq.yozakura.value.properties.IntProperty;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LagRange extends Module {
    private static final String HANDLER_NAME = "yozakura_lag_range";
    private static final double MINIMUM_DISTANCE_SQ = 9.0D;
    private static final long INDICATOR_INTERP_MS = 80L;
    private static final double POS_EPS = 1.0E-6D;

    private final FloatProperty range = new FloatProperty("Range", 6.0F, 3.0F, 10.0F);
    private final IntProperty maximumDelay = new IntProperty("Maximum Delay", 200, 50, 1000);
    private final BooleanProperty sprintReset = new BooleanProperty("Sprint Reset", true);
    private final BooleanProperty blockSword = new BooleanProperty("Block Sword", true);
    private final BooleanProperty usedSplashPotion = new BooleanProperty("Used Splash Potion", true);
    private final BooleanProperty holdingWeapon = new BooleanProperty("Holding Weapon", true);
    private final BooleanProperty realPositionIndicator = new BooleanProperty("Real Position Indicator", true);
    private final BooleanProperty showInFirstPerson = new BooleanProperty("Show In First Person", false);
    private final BooleanProperty indicatorFilled = new BooleanProperty("Indicator Filled", false);
    private final IntProperty indicatorAlpha = new IntProperty("Indicator Alpha", 100, 25, 180);
    private final FloatProperty indicatorLineWidth = new FloatProperty("Indicator Line Width", 2.0F, 1.0F, 5.0F);

    private final Queue<QueuedPacket> queuedPackets = new ConcurrentLinkedQueue<QueuedPacket>();

    private EntityPlayer currentTarget;
    private double lastDistSq = -1.0D;
    private boolean lagging;
    private int lastSelfHurtTime;
    private int lastTargetHurtTime;
    private int hitMarkedEntityId = -1;
    private boolean lastSprintState;
    private boolean lastBlockingState;
    private Channel channel;
    private Vec3 lastReleasedServerPosition;
    private Vec3 indicatorInterpFrom;
    private Vec3 indicatorInterpTo;
    private long indicatorInterpStartMs;

    public LagRange() {
        super("LagRange", Keyboard.KEY_NONE, ModuleType.Combat, "Delay outbound packets while entering combat range");
        this.addValues(range, maximumDelay, sprintReset, blockSword, usedSplashPotion, holdingWeapon,
                realPositionIndicator, showInFirstPerson, indicatorFilled, indicatorAlpha, indicatorLineWidth);
        this.Chinese = "延迟范围";
    }

    @Override
    public void enable() {
        resetState();
        injectHandler();
    }

    @Override
    public void disable() {
        flushLag();
        resetState();
        removeHandler();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (!isInGame() || mc.thePlayer.isDead) {
            flushLag();
            resetState();
            removeHandler();
            return;
        }
        injectHandler();
        updateLagState();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (lagging) {
            flushLag();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isInGame() || !lagging || !realPositionIndicator.getValue()) {
            clearIndicatorInterp();
            return;
        }
        if (mc.gameSettings.thirdPersonView == 0 && !showInFirstPerson.getValue()) {
            return;
        }
        Vec3 serverPos = lastReleasedServerPosition;
        if (serverPos == null) {
            clearIndicatorInterp();
            return;
        }
        long now = System.currentTimeMillis();
        if (indicatorInterpTo == null) {
            indicatorInterpFrom = serverPos;
            indicatorInterpTo = serverPos;
            indicatorInterpStartMs = now;
        } else if (serverPosChanged(serverPos, indicatorInterpTo)) {
            double progress = Math.min(1.0D, (now - indicatorInterpStartMs) / (double) INDICATOR_INTERP_MS);
            indicatorInterpFrom = lerp(indicatorInterpFrom, indicatorInterpTo, progress);
            indicatorInterpTo = serverPos;
            indicatorInterpStartMs = now;
        }
        double progress = Math.min(1.0D, (now - indicatorInterpStartMs) / (double) INDICATOR_INTERP_MS);
        Vec3 drawPos = lerp(indicatorInterpFrom, indicatorInterpTo, progress);
        float halfWidth = mc.thePlayer.width / 2.0F;
        AxisAlignedBB box = new AxisAlignedBB(
                drawPos.xCoord - halfWidth,
                drawPos.yCoord,
                drawPos.zCoord - halfWidth,
                drawPos.xCoord + halfWidth,
                drawPos.yCoord + mc.thePlayer.height,
                drawPos.zCoord + halfWidth
        ).offset(-mc.getRenderManager().viewerPosX, -mc.getRenderManager().viewerPosY, -mc.getRenderManager().viewerPosZ);
        int color = (MathHelper.clamp_int(indicatorAlpha.getValue(), 0, 255) << 24) | 0xFF4040;
        GL11.glPushAttrib(GL11.GL_LINE_BIT);
        GlStateManager.disableDepth();
        GL11.glLineWidth(indicatorLineWidth.getValue());
        if (indicatorFilled.getValue()) {
            RenderUtil.drawBox(box, color, false);
        }
        RenderUtil.drawOutlinedBox(box, color, false);
        GlStateManager.enableDepth();
        GL11.glPopAttrib();
    }

    private void updateLagState() {
        if (mc.currentScreen != null) {
            flushLag();
            resetState();
            return;
        }
        EntityPlayer nextTarget = findTarget(range.getValue() * range.getValue());
        if (!sameTarget(nextTarget)) {
            flushLag();
            lastDistSq = -1.0D;
            hitMarkedEntityId = -1;
            lastTargetHurtTime = nextTarget == null ? 0 : nextTarget.hurtTime;
        }
        currentTarget = nextTarget;
        if (currentTarget == null) {
            flushLag();
            lastDistSq = -1.0D;
            hitMarkedEntityId = -1;
            lastTargetHurtTime = 0;
            return;
        }
        double rangeSq = range.getValue() * range.getValue();
        double distSq = distanceSqFromEyeToClosest(currentTarget.getEntityBoundingBox());
        boolean moving = isMoving();
        if (lagging) {
            updateWhileLagging(distSq, rangeSq);
            return;
        }
        int hurtTime = mc.thePlayer.hurtTime;
        if (hurtTime > lastSelfHurtTime) {
            hitMarkedEntityId = -1;
        }
        lastSelfHurtTime = hurtTime;
        lastSprintState = mc.thePlayer.isSprinting();
        lastBlockingState = isBlocking();
        if (hurtTime == 0 && lastTargetHurtTime == 0 && currentTarget.hurtTime > 0) {
            hitMarkedEntityId = currentTarget.getEntityId();
        }
        lastTargetHurtTime = currentTarget.hurtTime;
        boolean closing = lastDistSq >= 0.0D && distSq < lastDistSq;
        boolean outsideMinimum = distSq > MINIMUM_DISTANCE_SQ;
        boolean weaponOk = !holdingWeapon.getValue() || isHoldingWeapon();
        boolean hitMarkedHere = hitMarkedEntityId == currentTarget.getEntityId();
        boolean hitStart = hitMarkedHere && distSq <= MINIMUM_DISTANCE_SQ && hurtTime == 0 && moving && weaponOk;
        lastDistSq = distSq;
        if (hurtTime == 0 && weaponOk && moving && (closing && outsideMinimum || hitStart)) {
            startLag();
        }
    }

    private void updateWhileLagging(double distSq, double rangeSq) {
        if (distSq > rangeSq) {
            flushLagAndTrack(distSq);
            return;
        }
        if (lastDistSq >= 0.0D && distSq >= lastDistSq) {
            boolean hitHold = hitMarkedEntityId == currentTarget.getEntityId()
                    && distSq <= MINIMUM_DISTANCE_SQ
                    && mc.thePlayer.hurtTime == 0;
            if (!hitHold) {
                flushLagAndTrack(distSq);
                return;
            }
        }
        int hurtTime = mc.thePlayer.hurtTime;
        if (hurtTime > lastSelfHurtTime) {
            hitMarkedEntityId = -1;
            lastSelfHurtTime = hurtTime;
            flushLagAndTrack(distSq);
            return;
        }
        lastSelfHurtTime = hurtTime;
        releaseExpiredPackets();
        if (holdingWeapon.getValue() && !isHoldingWeapon()) {
            flushLagAndTrack(distSq);
            return;
        }
        if (sprintReset.getValue()) {
            boolean sprintingNow = mc.thePlayer.isSprinting();
            if (sprintingNow && !lastSprintState) {
                lastSprintState = sprintingNow;
                flushLagAndTrack(distSq);
                return;
            }
            lastSprintState = sprintingNow;
        }
        if (blockSword.getValue()) {
            boolean blockingNow = isBlocking();
            if (blockingNow && !lastBlockingState) {
                lastBlockingState = blockingNow;
                flushLagAndTrack(distSq);
                return;
            }
            lastBlockingState = blockingNow;
        }
        if (usedSplashPotion.getValue() && mc.thePlayer.isUsingItem()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held != null && held.getItem() instanceof ItemPotion && ItemPotion.isSplash(held.getMetadata())) {
                flushLagAndTrack(distSq);
                return;
            }
        }
        lastDistSq = distSq;
        lastTargetHurtTime = currentTarget.hurtTime;
    }

    private void startLag() {
        if (lagging) {
            return;
        }
        if (lastReleasedServerPosition == null) {
            lastReleasedServerPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        }
        lagging = true;
    }

    private void flushLagAndTrack(double distSq) {
        flushLag();
        lastDistSq = distSq;
        lastTargetHurtTime = currentTarget == null ? 0 : currentTarget.hurtTime;
    }

    private void flushLag() {
        if (!lagging && queuedPackets.isEmpty()) {
            return;
        }
        lagging = false;
        QueuedPacket queued;
        while ((queued = queuedPackets.poll()) != null) {
            writePacket(queued);
        }
        clearIndicatorInterp();
    }

    private void releaseExpiredPackets() {
        long now = System.currentTimeMillis();
        long maxDelay = maximumDelay.getValue();
        while (true) {
            QueuedPacket queued = queuedPackets.peek();
            if (queued == null || now - queued.queuedAt < maxDelay) {
                break;
            }
            queuedPackets.poll();
            writePacket(queued);
        }
    }

    private boolean shouldQueuePacket(Object packet) {
        if (!getState() || !lagging || packet == null) {
            return false;
        }
        if (packet instanceof C00Handshake
                || packet instanceof C00PacketLoginStart
                || packet instanceof C01PacketEncryptionResponse
                || packet instanceof C00PacketServerQuery
                || packet instanceof C01PacketPing
                || packet instanceof C00PacketKeepAlive
                || packet instanceof C01PacketChatMessage
                || packet instanceof C0FPacketConfirmTransaction) {
            return false;
        }
        return packet instanceof C03PacketPlayer
                || packet instanceof C02PacketUseEntity
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange
                || packet instanceof C0APacketAnimation
                || packet instanceof C0BPacketEntityAction
                || packet instanceof C0CPacketInput;
    }

    private boolean queuePacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {
        if (queuedPackets.size() >= maxQueuedPackets()) {
            flushLag();
            return false;
        }
        queuedPackets.offer(new QueuedPacket(ctx, packet, promise, System.currentTimeMillis()));
        return true;
    }

    private void writePacket(final QueuedPacket queued) {
        if (queued.ctx == null || queued.packet == null || queued.promise == null) {
            return;
        }
        updateReleasedServerPosition(queued.packet);
        queued.ctx.executor().execute(new Runnable() {
            @Override
            public void run() {
                queued.ctx.writeAndFlush(queued.packet, queued.promise);
            }
        });
    }

    private void updateReleasedServerPosition(Object packet) {
        if (packet instanceof C03PacketPlayer) {
            C03PacketPlayer playerPacket = (C03PacketPlayer) packet;
            if (playerPacket.isMoving()) {
                lastReleasedServerPosition = new Vec3(
                        playerPacket.getPositionX(),
                        playerPacket.getPositionY(),
                        playerPacket.getPositionZ()
                );
            }
        }
    }

    private int maxQueuedPackets() {
        return Math.max(24, Math.min(220, maximumDelay.getValue() / 50 * 14));
    }

    private EntityPlayer findTarget(double rangeSq) {
        EntityPlayer best = null;
        double bestDistance = rangeSq;
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) object;
            if (player == mc.thePlayer || player.isDead || player.getHealth() <= 0.0F || AntiBot.isServerBot(player)) {
                continue;
            }
            double distance = distanceSqFromEyeToClosest(player.getEntityBoundingBox());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private double distanceSqFromEyeToClosest(AxisAlignedBB box) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double x = MathHelper.clamp_double(eyes.xCoord, box.minX, box.maxX);
        double y = MathHelper.clamp_double(eyes.yCoord, box.minY, box.maxY);
        double z = MathHelper.clamp_double(eyes.zCoord, box.minZ, box.maxZ);
        double dx = eyes.xCoord - x;
        double dy = eyes.yCoord - y;
        double dz = eyes.zCoord - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean sameTarget(EntityPlayer nextTarget) {
        if (currentTarget == null || nextTarget == null) {
            return currentTarget == nextTarget;
        }
        return currentTarget.getEntityId() == nextTarget.getEntityId();
    }

    private boolean isMoving() {
        return mc.thePlayer.moveForward != 0.0F
                || mc.thePlayer.moveStrafing != 0.0F
                || Math.abs(mc.thePlayer.motionX) + Math.abs(mc.thePlayer.motionZ) > 0.02D;
    }

    private boolean isHoldingWeapon() {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && (held.getItem() instanceof ItemSword || held.getItem() instanceof ItemAxe);
    }

    private boolean isBlocking() {
        return mc.thePlayer.isBlocking() || mc.thePlayer.isUsingItem()
                && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    protected boolean isInGame() {
        return mc.thePlayer != null && mc.theWorld != null && mc.getNetHandler() != null;
    }

    private void resetState() {
        currentTarget = null;
        lastDistSq = -1.0D;
        lagging = false;
        lastSelfHurtTime = 0;
        lastTargetHurtTime = 0;
        hitMarkedEntityId = -1;
        lastSprintState = false;
        lastBlockingState = false;
        queuedPackets.clear();
        lastReleasedServerPosition = mc.thePlayer == null ? null : new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        clearIndicatorInterp();
    }

    private void clearIndicatorInterp() {
        indicatorInterpFrom = null;
        indicatorInterpTo = null;
        indicatorInterpStartMs = 0L;
    }

    private static boolean serverPosChanged(Vec3 first, Vec3 second) {
        return Math.abs(first.xCoord - second.xCoord) > POS_EPS
                || Math.abs(first.yCoord - second.yCoord) > POS_EPS
                || Math.abs(first.zCoord - second.zCoord) > POS_EPS;
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double progress) {
        if (from == null) {
            return to;
        }
        if (to == null || progress <= 0.0D) {
            return from;
        }
        if (progress >= 1.0D) {
            return to;
        }
        return new Vec3(
                from.xCoord + (to.xCoord - from.xCoord) * progress,
                from.yCoord + (to.yCoord - from.yCoord) * progress,
                from.zCoord + (to.zCoord - from.zCoord) * progress
        );
    }

    private void injectHandler() {
        if (!isInGame()) {
            return;
        }
        try {
            NetworkManager manager = mc.getNetHandler().getNetworkManager();
            Channel current = getChannel(manager);
            if (current == null || !current.isOpen()) {
                return;
            }
            if (channel != null && channel != current) {
                flushLag();
                removeHandler();
            }
            if (current.pipeline().get(HANDLER_NAME) == null) {
                current.pipeline().addBefore("packet_handler", HANDLER_NAME, new LagRangePacketHandler(this));
            }
            channel = current;
        } catch (Throwable ignored) {
            channel = null;
        }
    }

    private void removeHandler() {
        Channel current = channel;
        channel = null;
        if (current == null) {
            return;
        }
        try {
            if (current.isOpen() && current.pipeline().get(HANDLER_NAME) != null) {
                current.pipeline().remove(HANDLER_NAME);
            }
        } catch (Throwable ignored) {
        }
    }

    private Channel getChannel(NetworkManager manager) {
        for (String name : new String[]{"channel", "field_150746_k", "k"}) {
            try {
                Field field = NetworkManager.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(manager);
                if (value instanceof Channel) {
                    return (Channel) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static final class QueuedPacket {
        private final ChannelHandlerContext ctx;
        private final Object packet;
        private final ChannelPromise promise;
        private final long queuedAt;

        private QueuedPacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise, long queuedAt) {
            this.ctx = ctx;
            this.packet = packet;
            this.promise = promise;
            this.queuedAt = queuedAt;
        }
    }

    private static final class LagRangePacketHandler extends ChannelDuplexHandler {
        private final LagRange module;

        private LagRangePacketHandler(LagRange module) {
            this.module = module;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (module.shouldQueuePacket(msg) && module.queuePacket(ctx, msg, promise)) {
                return;
            }
            module.updateReleasedServerPosition(msg);
            super.write(ctx, msg, promise);
        }
    }
}
