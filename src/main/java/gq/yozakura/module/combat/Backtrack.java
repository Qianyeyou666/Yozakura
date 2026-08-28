package gq.yozakura.module.combat;

import gq.yozakura.bridge.PacketPipelineAnchors;
import gq.yozakura.bridge.util.ReflectionUtils;
import gq.yozakura.core.StandaloneClient;
import gq.yozakura.event.bridge.MouseOverEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Backtrack extends Module {
    public enum BacktrackMode {
        HISTORY,
        PACKET,
        HYBRID
    }

    private static final String HANDLER_NAME = PacketPipelineAnchors.BACKTRACK_HANDLER_NAME;
    private static final int MAX_HISTORY = 40;
    private static final int MAX_QUEUED_PACKETS = 128;
    private static final int UNKNOWN_ENTITY_ID = Integer.MIN_VALUE;
    private static Backtrack INSTANCE;

    private final Mode<BacktrackMode> mode =
            new Mode<BacktrackMode>("Mode", "Mode", BacktrackMode.values(), BacktrackMode.HYBRID);
    private final Numbers<Double> range = new Numbers<Double>("Range", "Range", 3.6, 3.0, 6.0, 0.1);
    private final Numbers<Double> historyMs = new Numbers<Double>("History MS", "HistoryMS", 180.0, 50.0, 600.0, 10.0);
    private final Numbers<Double> packetDelay = new Numbers<Double>("Packet Delay", "PacketDelay", 120.0, 0.0, 400.0, 10.0);
    private final Numbers<Double> expand = new Numbers<Double>("Expand", "Expand", 0.08, 0.0, 1.0, 0.01);
    private final Option<Boolean> safeReach = new Option<Boolean>("Safe Reach", "SafeReach", true);
    private final Option<Boolean> attackOnly = new Option<Boolean>("Attack Only", "AttackOnly", true);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", true);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", true);
    private final Option<Boolean> throughWalls = new Option<Boolean>("Through Walls", "ThroughWalls", false);
    private final Option<Boolean> render = new Option<Boolean>("Render", "Render", true);
    private final Option<Boolean> renderTrail = new Option<Boolean>("Trail", "Trail", true);
    private final Numbers<Double> renderAlpha = new Numbers<Double>("Render Alpha", "RenderAlpha", 82.0, 25.0, 160.0, 5.0);
    private final Map<Integer, ArrayDeque<TrackedBox>> history = new ConcurrentHashMap<Integer, ArrayDeque<TrackedBox>>();
    private final Queue<QueuedPacket> queuedPackets = new ArrayDeque<QueuedPacket>();
    private final Object deliveryLock = new Object();
    private volatile Set<Integer> delayableEntityIds = Collections.emptySet();
    private int pendingDeliveryTasks;
    private volatile boolean acceptingPackets;
    private MovingObjectPosition lastAppliedHistoricalHit;
    private Channel channel;

    public Backtrack() {
        super("Backtrack", Keyboard.KEY_NONE, ModuleType.Combat, "Attack entities at recent historical positions");
        this.addValues(mode, range, historyMs, packetDelay, expand, safeReach, attackOnly, players, mobs, animals,
                throughWalls, render, renderTrail, renderAlpha);
        Chinese = "回溯";
        INSTANCE = this;
    }

    @Override
    public void enable() {
        history.clear();
        lastAppliedHistoricalHit = null;
        delayableEntityIds = Collections.emptySet();
        synchronized (deliveryLock) {
            queuedPackets.clear();
            acceptingPackets = true;
        }
        injectHandler();
    }

    @Override
    public void disable() {
        stopPacketAdmissionAndRelease();
        removeHandler();
        history.clear();
        lastAppliedHistoricalHit = null;
        delayableEntityIds = Collections.emptySet();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame()) {
            history.clear();
            lastAppliedHistoricalHit = null;
            delayableEntityIds = Collections.emptySet();
            stopPacketAdmissionAndRelease();
            removeHandler();
            return;
        }
        acceptingPackets = true;
        if (usesPacketDelay()) {
            injectHandler();
        } else {
            releaseQueuedPackets();
            removeHandler();
        }
        recordHistory();
        releaseDuePackets();
        if (!Boolean.TRUE.equals(attackOnly.getValue()) || mc.gameSettings.keyBindAttack.isKeyDown()) {
            applyHistoricalHit();
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button == 0 && event.buttonstate && isInGame()) {
            applyHistoricalHit();
        }
    }

    @EventTarget
    public void onMouseOver(MouseOverEvent event) {
        if (event == null || !isInGame() || mode.getValue() == BacktrackMode.PACKET) {
            return;
        }
        if (Boolean.TRUE.equals(attackOnly.getValue()) && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        applyHistoricalHit(event.getPartialTicks());
    }

    public static boolean applyBacktrackHit() {
        return INSTANCE != null && INSTANCE.getState() && INSTANCE.applyHistoricalHit();
    }

    public static EntityLivingBase getAimedTarget() {
        if (INSTANCE == null || !INSTANCE.getState() || INSTANCE.mode.getValue() == BacktrackMode.PACKET) {
            return null;
        }
        MovingObjectPosition hit = INSTANCE.findHistoricalHit(INSTANCE.effectiveAttackRange(),
                INSTANCE.expand.getValue(), 1.0f);
        return hit != null && hit.entityHit instanceof EntityLivingBase ? (EntityLivingBase) hit.entityHit : null;
    }

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent event) {
        if (!isInGame() || !Boolean.TRUE.equals(render.getValue()) || history.isEmpty()) {
            return;
        }
        renderHistoryBoxes();
    }

    private void recordHistory() {
        long now = System.currentTimeMillis();
        Set<Integer> currentDelayableIds = new HashSet<Integer>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!CombatUtil.isValidTarget(living, range.getValue() + 1.0D, 180.0D,
                    players.getValue(), mobs.getValue(), animals.getValue(), throughWalls.getValue())) {
                continue;
            }
            currentDelayableIds.add(living.getEntityId());
            ArrayDeque<TrackedBox> boxes = history.get(living.getEntityId());
            if (boxes == null) {
                boxes = new ArrayDeque<TrackedBox>();
                history.put(living.getEntityId(), boxes);
            }
            boxes.addLast(new TrackedBox(now, living, living.getEntityBoundingBox(), living.posX, living.posY, living.posZ));
            long maxHistoryAge = effectiveHistoryMillis();
            while (boxes.size() > MAX_HISTORY || !boxes.isEmpty() && now - boxes.peekFirst().time > maxHistoryAge) {
                boxes.pollFirst();
            }
        }

        Iterator<Map.Entry<Integer, ArrayDeque<TrackedBox>>> iterator = history.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ArrayDeque<TrackedBox>> entry = iterator.next();
            ArrayDeque<TrackedBox> boxes = entry.getValue();
            if (boxes == null || boxes.isEmpty() || now - boxes.peekLast().time > historyMs.getValue() + 120L) {
                iterator.remove();
            }
        }
        delayableEntityIds = Collections.unmodifiableSet(currentDelayableIds);
    }

    private double effectiveAttackRange() {
        double configured = range.getValue();
        if (!Boolean.TRUE.equals(safeReach.getValue()) || mc.playerController != null && mc.playerController.extendedReach()) {
            return configured;
        }
        return Math.min(configured, 3.0D);
    }

    private long effectiveHistoryMillis() {
        long configured = historyMs.getValue().longValue();
        if (mode.getValue() != BacktrackMode.HYBRID || !usesPacketDelay()) {
            return configured;
        }
        return Math.max(50L, configured - packetDelay.getValue().longValue());
    }

    private boolean applyHistoricalHit() {
        return applyHistoricalHit(1.0F);
    }

    private boolean applyHistoricalHit(float partialTicks) {
        if (!isInGame() || mode.getValue() == BacktrackMode.PACKET) {
            lastAppliedHistoricalHit = null;
            return false;
        }
        if (hasReliableCurrentEntityHit()) {
            lastAppliedHistoricalHit = null;
            return false;
        }
        MovingObjectPosition hit = findHistoricalHit(effectiveAttackRange(), expand.getValue(), partialTicks);
        if (hit == null || hit.entityHit == null) {
            lastAppliedHistoricalHit = null;
            return false;
        }
        mc.objectMouseOver = hit;
        mc.pointedEntity = hit.entityHit;
        lastAppliedHistoricalHit = hit;
        return true;
    }

    private boolean hasReliableCurrentEntityHit() {
        MovingObjectPosition current = mc.objectMouseOver;
        if (current == null || current == lastAppliedHistoricalHit || current.entityHit == null
                || !(current.entityHit instanceof EntityLivingBase)) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase) current.entityHit;
        return CombatUtil.isValidTarget(living, effectiveAttackRange() + 0.1D, 180.0D,
                players.getValue(), mobs.getValue(), animals.getValue(), throughWalls.getValue());
    }

    private MovingObjectPosition findHistoricalHit(double maxRange, double extraExpand, float partialTicks) {
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.theWorld == null) {
            return null;
        }
        Vec3 eyes = view.getPositionEyes(partialTicks);
        Vec3 look = view.getLook(partialTicks);
        Vec3 end = eyes.addVector(look.xCoord * maxRange, look.yCoord * maxRange, look.zCoord * maxRange);
        TrackedBox best = null;
        Vec3 bestHit = null;
        double bestDistance = blockObstructionDistance(eyes, end, maxRange);
        long now = System.currentTimeMillis();
        long maxHistoryAge = effectiveHistoryMillis();

        for (ArrayDeque<TrackedBox> boxes : history.values()) {
            if (boxes == null || boxes.isEmpty()) {
                continue;
            }
            Iterator<TrackedBox> newestFirst = boxes.descendingIterator();
            while (newestFirst.hasNext()) {
                TrackedBox tracked = newestFirst.next();
                if (tracked.entity == null || tracked.entity.isDead || now - tracked.time > maxHistoryAge) {
                    continue;
                }
                if (!CombatUtil.isValidTarget((EntityLivingBase) tracked.entity, maxRange + extraExpand + 0.1D,
                        180.0D, players.getValue(), mobs.getValue(), animals.getValue(), throughWalls.getValue())) {
                    continue;
                }
                AxisAlignedBB box = tracked.box.expand(extraExpand, extraExpand, extraExpand);
                MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
                Vec3 hit = box.isVecInside(eyes) ? eyes : intercept == null ? null : intercept.hitVec;
                if (hit == null) {
                    continue;
                }
                double distance = eyes.distanceTo(hit);
                if (distance <= bestDistance) {
                    best = tracked;
                    bestHit = hit;
                    bestDistance = distance;
                }
                break;
            }
        }
        return best == null || bestHit == null ? null : new MovingObjectPosition(best.entity, bestHit);
    }

    private double blockObstructionDistance(Vec3 eyes, Vec3 end, double maxRange) {
        if (Boolean.TRUE.equals(throughWalls.getValue())) {
            return maxRange;
        }
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, end, false, true, false);
        return blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                ? Math.min(maxRange, eyes.distanceTo(blockHit.hitVec)) : maxRange;
    }

    private void renderHistoryBoxes() {
        long now = System.currentTimeMillis();
        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;
        float baseAlpha = renderAlpha.getValue().floatValue() / 255.0f;
        boolean trail = Boolean.TRUE.equals(renderTrail.getValue());
        int rendered = 0;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            outer:
            for (ArrayDeque<TrackedBox> boxes : history.values()) {
                if (boxes == null || boxes.isEmpty()) {
                    continue;
                }
                int index = 0;
                int size = boxes.size();
                int step = trail ? Math.max(1, size / 5) : size;
                for (TrackedBox tracked : boxes) {
                    index++;
                    if (!trail && index != size) {
                        continue;
                    }
                    if (trail && index != size && index % step != 0) {
                        continue;
                    }
                    if (tracked.entity == null || tracked.entity.isDead || tracked.box == null) {
                        continue;
                    }
                    float age = (float) (now - tracked.time) / Math.max(1.0f, (float) effectiveHistoryMillis());
                    float fade = trail ? clamp01(1.0f - age) : 1.0f;
                    if (fade <= 0.02f) {
                        continue;
                    }
                    AxisAlignedBB box = toRenderBox(tracked.box.expand(expand.getValue(), expand.getValue(), expand.getValue()),
                            viewerX, viewerY, viewerZ);
                    boolean focused = mc.objectMouseOver != null && mc.objectMouseOver.entityHit == tracked.entity;
                    float lineAlpha = clamp01((focused ? 0.95f : 0.55f) * fade);
                    float fillAlpha = clamp01(baseAlpha * (focused ? 0.85f : 0.48f) * fade);
                    drawHistoryBox(box, focused ? 0.48f : 0.36f, focused ? 0.82f : 0.52f, 1.0f, lineAlpha, fillAlpha,
                            focused ? 2.0f : 1.25f);
                    rendered++;
                    if (rendered >= 96) {
                        break outer;
                    }
                }
            }
        } finally {
            GL11.glDepthMask(true);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private AxisAlignedBB toRenderBox(AxisAlignedBB box, double viewerX, double viewerY, double viewerZ) {
        return new AxisAlignedBB(box.minX - viewerX, box.minY - viewerY, box.minZ - viewerZ,
                box.maxX - viewerX, box.maxY - viewerY, box.maxZ - viewerZ);
    }

    private void drawHistoryBox(AxisAlignedBB box, float red, float green, float blue,
                                float lineAlpha, float fillAlpha, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GL11.glColor4f(red, green, blue, lineAlpha);
        RenderUtil.drawOutlinedBoundingBox(box);
        GL11.glColor4f(red, green, blue, fillAlpha);
        RenderUtil.drawBoundingBox(box);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private void injectHandler() {
        if (!usesPacketDelay() || !isInGame() || mc.getNetHandler() == null) {
            return;
        }
        try {
            NetworkManager manager = mc.getNetHandler().getNetworkManager();
            Channel current = ReflectionUtils.getChannel(manager);
            if (current == null || !current.isOpen()) {
                return;
            }
            if (channel != null && channel != current) {
                releaseQueuedPackets();
                removeHandler();
            }
            PacketPipelineAnchors.installDelayHandler(current.pipeline(), HANDLER_NAME,
                    new BacktrackPacketHandler(this));
            this.channel = current;
        } catch (Throwable ignored) {
            this.channel = null;
        }
    }

    private void removeHandler() {
        Channel current = this.channel;
        this.channel = null;
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

    private boolean isDelayablePacket(Packet packet) {
        if (!acceptingPackets || !getState() || !usesPacketDelay()
                || packetDelay.getValue() <= 0.0D) {
            return false;
        }
        int entityId = packetEntityId(packet);
        return entityId != UNKNOWN_ENTITY_ID && delayableEntityIds.contains(entityId);
    }

    private int packetEntityId(Packet packet) {
        if (packet instanceof S18PacketEntityTeleport) {
            return ((S18PacketEntityTeleport) packet).getEntityId();
        }
        if (!(packet instanceof S14PacketEntity)) {
            return UNKNOWN_ENTITY_ID;
        }
        Field field = ReflectionUtils.findField(S14PacketEntity.class, "entityId", "field_149074_a", "a");
        if (field == null) {
            return UNKNOWN_ENTITY_ID;
        }
        try {
            return field.getInt(packet);
        } catch (Throwable ignored) {
            return UNKNOWN_ENTITY_ID;
        }
    }

    private boolean usesPacketDelay() {
        return !StandaloneClient.isBridgeOwnerActive()
                && (mode.getValue() == BacktrackMode.PACKET || mode.getValue() == BacktrackMode.HYBRID);
    }

    private boolean queuePacketIfDelayable(ChannelHandlerContext ctx, Object packet) {
        synchronized (deliveryLock) {
            if (!(packet instanceof Packet) || !isDelayablePacket((Packet) packet)) {
                return false;
            }
            if (queuedPackets.size() >= MAX_QUEUED_PACKETS) {
                releaseQueuedPacketsLocked();
                firePacketLocked(new QueuedPacket(ctx, packet,
                        System.currentTimeMillis() + packetDelay.getValue().longValue()));
                return true;
            }
            queuedPackets.offer(new QueuedPacket(ctx, packet,
                    System.currentTimeMillis() + packetDelay.getValue().longValue()));
            return true;
        }
    }

    private void forwardPacket(ChannelHandlerContext ctx, Object packet) {
        synchronized (deliveryLock) {
            firePacketLocked(new QueuedPacket(ctx, packet, System.currentTimeMillis()));
        }
    }

    private void releaseDuePackets() {
        long now = System.currentTimeMillis();
        synchronized (deliveryLock) {
            while (true) {
                QueuedPacket queued = queuedPackets.peek();
                if (queued == null || queued.releaseAt > now) {
                    break;
                }
                queuedPackets.poll();
                firePacketLocked(queued);
            }
        }
    }

    private void releaseQueuedPackets() {
        synchronized (deliveryLock) {
            releaseQueuedPacketsLocked();
        }
    }

    private void stopPacketAdmissionAndRelease() {
        synchronized (deliveryLock) {
            acceptingPackets = false;
            releaseQueuedPacketsLocked();
        }
    }

    private void releaseQueuedPacketsLocked() {
        QueuedPacket queued;
        while ((queued = queuedPackets.poll()) != null) {
            firePacketLocked(queued);
        }
    }

    private void firePacketLocked(final QueuedPacket queued) {
        if (queued.ctx == null || queued.packet == null) {
            return;
        }
        if (queued.ctx.executor().inEventLoop() && pendingDeliveryTasks == 0) {
            queued.ctx.fireChannelRead(queued.packet);
            return;
        }
        pendingDeliveryTasks++;
        try {
            queued.ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        queued.ctx.fireChannelRead(queued.packet);
                    } finally {
                        synchronized (deliveryLock) {
                            pendingDeliveryTasks--;
                        }
                    }
                }
            });
        } catch (Throwable throwable) {
            pendingDeliveryTasks--;
            queued.ctx.fireExceptionCaught(throwable);
        }
    }

    private static final class TrackedBox {
        final long time;
        final Entity entity;
        final AxisAlignedBB box;
        final double x;
        final double y;
        final double z;

        TrackedBox(long time, Entity entity, AxisAlignedBB box, double x, double y, double z) {
            this.time = time;
            this.entity = entity;
            this.box = box;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class QueuedPacket {
        final ChannelHandlerContext ctx;
        final Object packet;
        final long releaseAt;

        QueuedPacket(ChannelHandlerContext ctx, Object packet, long releaseAt) {
            this.ctx = ctx;
            this.packet = packet;
            this.releaseAt = releaseAt;
        }
    }

    private static final class BacktrackPacketHandler extends ChannelDuplexHandler {
        private final Backtrack module;

        BacktrackPacketHandler(Backtrack module) {
            this.module = module;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (module.queuePacketIfDelayable(ctx, msg)) {
                return;
            }
            module.forwardPacket(ctx, msg);
        }
    }
}
