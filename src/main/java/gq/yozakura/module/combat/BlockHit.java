package gq.yozakura.module.combat;

import com.google.common.base.CaseFormat;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.PlayerPacketBoundaryEvent;
import gq.yozakura.event.bridge.RenderTickEndEvent;
import gq.yozakura.event.bridge.RenderTickStartEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.value.properties.ModeProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automatic sword blocking driven through one bounded vanilla input cycle.
 * The module never constructs, cancels, queues, or reorders combat packets.
 */
public class BlockHit extends Module {
    private static final int MODE_MANUAL = 0;
    private static final int MODE_PREDICT = 1;
    private static final int MODE_AUTO = 2;
    private static final int MODE_LAG = 3;
    private static final int MODE_HYPIXEL = 4;
    private static final int MODE_NO_PRE_HYP = 5;
    private static final long NO_BRIDGE_GENERATION = -1L;

    private static BlockHit instance;

    private final BlockHitSettings settings = new BlockHitSettings();
    public final ModeProperty mode = settings.mode;
    private final BlockHitController controller = new BlockHitController();
    private final BlockHitVanillaUseAction useAction = new BlockHitVanillaUseAction(mc);
    private final BlockHitHelperController helperController = new BlockHitHelperController();
    private final BlockHitHelperInput helperInput = new BlockHitHelperInput(mc);
    private final BlockHitHelperThreatScanner helperThreatScanner = new BlockHitHelperThreatScanner(mc);
    private final BlockHitRenderPose renderPose = new BlockHitRenderPose();
    private final ConcurrentLinkedQueue<SuccessfulAttack> successfulAttacks =
            new ConcurrentLinkedQueue<SuccessfulAttack>();
    private final ConcurrentLinkedQueue<OwnedUseWrite> successfulUseWrites =
            new ConcurrentLinkedQueue<OwnedUseWrite>();
    private final ConcurrentLinkedQueue<AcceptedPacketKind> acceptedPacketKinds =
            new ConcurrentLinkedQueue<AcceptedPacketKind>();
    private final ConcurrentLinkedQueue<MovementBoundary> successfulMovementBoundaries =
            new ConcurrentLinkedQueue<MovementBoundary>();
    private final ConcurrentHashMap<Long, Long> acceptedWriteGenerations =
            new ConcurrentHashMap<Long, Long>();
    private final AtomicReference<AcceptedMovementWrite> latestAcceptedMovementWrite =
            new AtomicReference<AcceptedMovementWrite>();
    private final AtomicLong acceptedAttackSequence = new AtomicLong();
    private volatile boolean acceptingBridgeEvents;
    private volatile long bridgeGeneration;
    private long releaseAfterInputCycle = BlockHitController.NO_CYCLE;
    private long releaseWhenUseWriteConfirmsCycle = BlockHitController.NO_CYCLE;
    private long lastPostAcceptedAttackSequence;
    private int activeMode = -1;
    private boolean helperForceBlockPose;

    public BlockHit() {
        super("BlockHit", false);
        addValues(settings.values());
        setCategory(ModuleType.Combat);
        Chinese = "格挡攻击";
        Descript = "Vanilla-input sword block hit";
        About = Descript;
        instance = this;
    }

    @Override
    public void onEnabled() {
        bridgeGeneration++;
        activeMode = mode.getValue();
        resetState();
        armHelperFirstAttackWarmUp();
        lastPostAcceptedAttackSequence = acceptedAttackSequence.get();
        acceptingBridgeEvents = true;
    }

    @Override
    public void onDisabled() {
        long cycleId = useAction.getActiveUseCycleId();
        acceptingBridgeEvents = false;
        bridgeGeneration++;
        stopHelper(true);
        controller.reset();
        discardPendingPackets();
        releaseAfterInputCycle = BlockHitController.NO_CYCLE;
        releaseWhenUseWriteConfirmsCycle = BlockHitController.NO_CYCLE;
        if (cycleId == BlockHitController.NO_CYCLE) {
            return;
        }
        if (useAction.isUseWriteSucceeded(cycleId)) {
            useAction.releaseUse(cycleId);
        } else {
            new BlockHitDisabledUseRelease(useAction, cycleId, acceptedAttackSequence).register();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (event == null) {
            return;
        }
        if (event.getType() == EventType.POST) {
            finishPostInputCycle();
            return;
        }
        if (!isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (!isGameplayReady()) {
            stopHelper(true);
            resetState();
            return;
        }
        if (activeMode != mode.getValue()) {
            stopHelper(true);
            cancelCycle();
            discardPendingPackets();
            activeMode = mode.getValue();
            armHelperFirstAttackWarmUp();
        }
        if (!isPhysicalAttackDown()) {
            stopHelper(true);
            cancelCycle();
            discardPendingPackets();
            return;
        }
        if (isHelperMode(activeMode)) {
            cancelCycle();
            discardPendingPackets();
            if (activeMode == MODE_HYPIXEL) {
                handleHypixelTick();
            } else {
                handleHelperTick();
            }
            return;
        }
        stopHelper(true);
        if (hasExternalUseOwner() || useAction.isPhysicalUseDown()) {
            cancelCycle();
            discardPendingPackets();
            return;
        }

        drainSuccessfulMovementBoundaries();
        drainAcceptedPackets();
        drainSuccessfulUseWrites();
        drainSuccessfulAttacks();
    }

    @EventTarget(Priority.HIGHEST)
    public void onRenderTickStart(RenderTickStartEvent event) {
        if (event != null && helperForceBlockPose && isHelperReady()) {
            renderPose.begin(mc.thePlayer);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onRenderTickEnd(RenderTickEndEvent event) {
        renderPose.end();
    }

    @EventTarget(Priority.LOWEST)
    public void onPacketAccepted(PacketAcceptedEvent event) {
        long generation = bridgeGeneration;
        if (!acceptingBridgeEvents || event == null) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
            acceptedAttackSequence.incrementAndGet();
            rememberAcceptedWrite(event.getWriteId(), generation);
        }
        BlockHitController.PacketKind kind = classify(packet);
        if (kind == BlockHitController.PacketKind.USE_ITEM) {
            rememberAcceptedWrite(event.getWriteId(), generation);
            useAction.claimOwnedUseWrite(event.getWriteId());
        } else if (kind == BlockHitController.PacketKind.RELEASE_USE_ITEM) {
            useAction.observeRelease();
        }
        if (packet instanceof C03PacketPlayer) {
            rememberAcceptedMovementWrite(event.getWriteId(), generation);
        }
        if (kind != BlockHitController.PacketKind.OTHER) {
            acceptedPacketKinds.offer(new AcceptedPacketKind(kind, generation));
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacketWritten(PacketWriteEvent event) {
        if (event == null || !event.isPacketAccepted()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof C03PacketPlayer) {
            if (!event.isSuccess()) {
                clearAcceptedMovementWrite(event.getWriteId());
            }
            return;
        }
        long generation = consumeAcceptedWriteGeneration(event.getWriteId());
        if (!acceptingBridgeEvents) {
            return;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement
                && ((C08PacketPlayerBlockPlacement) packet).getPlacedBlockDirection() == 255) {
            long cycleId = useAction.completeOwnedUseWrite(event.getWriteId(), event.isSuccess());
            if (cycleId != BlockHitController.NO_CYCLE && generation != NO_BRIDGE_GENERATION) {
                successfulUseWrites.offer(new OwnedUseWrite(cycleId, event.isSuccess(), generation));
            }
            return;
        }
        if (!event.isSuccess()) {
            return;
        }
        if (packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
            if (generation != NO_BRIDGE_GENERATION) {
                successfulAttacks.offer(new SuccessfulAttack((C02PacketUseEntity) packet, generation));
            }
            return;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event) {
        if (event == null || !event.isPacketAccepted()) {
            return;
        }
        AcceptedMovementWrite acceptedMovementWrite = consumeAcceptedMovementWrite(event.getWriteId());
        if (!acceptingBridgeEvents || acceptedMovementWrite == null) {
            return;
        }
        successfulMovementBoundaries.offer(new MovementBoundary(acceptedMovementWrite.generation));
    }

    public static boolean isBlockingActive() {
        if (instance == null || !instance.isEnabled() || !instance.isGameplayReady()
                || !instance.isPhysicalAttackDown()) {
            return false;
        }
        return instance.mc.thePlayer.isBlocking()
                && (instance.useAction.isUsing() || instance.useAction.isPhysicalUseDown()
                || isHelperMode(instance.activeMode) && instance.helperInput.isHoldingUse());
    }

    @Override
    public String[] getSuffix() {
        String modeSuffix = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString());
        return hasExternalUseOwner() ? new String[]{modeSuffix, "Paused"} : new String[]{modeSuffix};
    }

    private void handleHypixelTick() {
        boolean threatPredicted = helperThreatScanner.hasThreat(
                settings.helperThreatRange.getValue(), settings.helperThreatAngle.getValue());
        handleHelperTick(threatPredicted);
    }

    private void handleHelperTick() {
        handleHelperTick(isPhysicalAttackDown());
    }

    private void handleHelperTick(boolean activationAllowed) {
        if (hasExternalUseOwner() || !activationAllowed || !isPhysicalAttackDown()) {
            stopHelper(true);
            return;
        }
        boolean attackDown = isPhysicalAttackDown();
        BlockHitHelperController.Action action = helperController.tick(
                attackDown, activationAllowed, mc.thePlayer.isBlocking(), settings.stopTicks.getValue());
        applyHelperAction(action);
    }

    private void applyHelperAction(BlockHitHelperController.Action action) {
        helperForceBlockPose = action.shouldForceBlockPose();
        if (action.shouldHoldUse()) {
            helperInput.holdUse();
        }
        if (action.shouldSuppressUse()) {
            helperInput.suppressUse();
        }
        if (action.shouldPressAttack()) {
            helperInput.pressAttackOnce();
        }
    }

    private void stopHelper(boolean releaseOwnedUse) {
        renderPose.end();
        helperForceBlockPose = false;
        helperController.reset();
        helperThreatScanner.reset();
        if (releaseOwnedUse) {
            helperInput.releaseOwnedUse();
        }
    }

    private void armHelperFirstAttackWarmUp() {
        if (isHelperMode(activeMode)) {
            helperController.armFirstAttackWarmUp();
        }
    }

    private boolean isHelperReady() {
        return isEnabled() && isHelperMode(activeMode) && isGameplayReady() && isPhysicalAttackDown()
                && !hasExternalUseOwner() && helperController.isActive();
    }

    private static boolean isHelperMode(int modeValue) {
        return modeValue == MODE_HYPIXEL || modeValue == MODE_NO_PRE_HYP;
    }

    private void armUseForAttack(C02PacketUseEntity packet) {
        if (!isGameplayReady() || !isPhysicalAttackDown() || hasExternalUseOwner()
                || useAction.isPhysicalUseDown() || mode.getValue() == MODE_LAG) {
            return;
        }
        if (mode.getValue() == MODE_MANUAL) {
            if (!passesManualChance()) {
                return;
            }
        } else if (!matchesAutomaticTarget(packet)) {
            return;
        }
        controller.armUseAfterMovementBoundary();
    }

    private void drainSuccessfulAttacks() {
        SuccessfulAttack attack;
        while ((attack = successfulAttacks.poll()) != null) {
            if (attack.generation != bridgeGeneration) {
                continue;
            }
            controller.observe(BlockHitController.PacketKind.ATTACK);
            armUseForAttack(attack.packet);
        }
    }

    private void drainSuccessfulUseWrites() {
        OwnedUseWrite write;
        while ((write = successfulUseWrites.poll()) != null) {
            if (write.generation != bridgeGeneration) {
                continue;
            }
            if (!write.success) {
                controller.cancelRequestedUseCycle(write.cycleId);
                if (releaseWhenUseWriteConfirmsCycle == write.cycleId) {
                    releaseWhenUseWriteConfirmsCycle = BlockHitController.NO_CYCLE;
                }
                continue;
            }
            controller.confirmUseWritten(write.cycleId);
            if (releaseWhenUseWriteConfirmsCycle == write.cycleId) {
                releaseWhenUseWriteConfirmsCycle = BlockHitController.NO_CYCLE;
                releaseAfterInputCycle = write.cycleId;
            }
        }
    }

    private void drainAcceptedPackets() {
        AcceptedPacketKind accepted;
        while ((accepted = acceptedPacketKinds.poll()) != null) {
            if (accepted.generation != bridgeGeneration) {
                continue;
            }
            BlockHitController.PacketKind kind = accepted.kind;
            controller.observe(kind);
            if (kind == BlockHitController.PacketKind.CONTEXT_CHANGED
                    || kind == BlockHitController.PacketKind.USE_ITEM && !useAction.isUsing()
                    || kind == BlockHitController.PacketKind.RELEASE_USE_ITEM) {
                cancelCycle();
            }
        }
    }

    private void drainSuccessfulMovementBoundaries() {
        MovementBoundary boundary;
        while ((boundary = successfulMovementBoundaries.poll()) != null) {
            if (boundary.generation == bridgeGeneration) {
                controller.confirmMovementBoundary();
            }
        }
    }

    private boolean matchesAutomaticTarget(C02PacketUseEntity packet) {
        Entity entity = packet.getEntityFromWorld(mc.theWorld);
        if (!(entity instanceof EntityLivingBase)) {
            return false;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        if (Boolean.TRUE.equals(settings.ignoreManualBlock.getValue()) && mc.thePlayer.isBlocking()) {
            return false;
        }
        return target != mc.thePlayer && !target.isDead && target.getHealth() > 0.0F
                && mc.thePlayer.getDistanceToEntity(target) <= settings.distance.getValue()
                && RotationUtil.angleToEntity(target) <= settings.angle.getValue();
    }

    private BlockHitController.PacketKind classify(Packet<?> packet) {
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement placement = (C08PacketPlayerBlockPlacement) packet;
            return placement.getPlacedBlockDirection() == 255
                    ? BlockHitController.PacketKind.USE_ITEM
                    : BlockHitController.PacketKind.CONTEXT_CHANGED;
        }
        if (packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus()
                == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            return BlockHitController.PacketKind.RELEASE_USE_ITEM;
        }
        if (packet instanceof C0APacketAnimation) {
            return BlockHitController.PacketKind.ANIMATION;
        }
        if (packet instanceof C09PacketHeldItemChange) {
            return BlockHitController.PacketKind.CONTEXT_CHANGED;
        }
        return BlockHitController.PacketKind.OTHER;
    }

    private void cancelCycle() {
        controller.cancelCycle();
        requestPostInputRelease();
    }

    private void resetState() {
        controller.reset();
        requestPostInputRelease();
        discardPendingPackets();
    }

    private void requestPostInputRelease() {
        long cycleId = useAction.getActiveUseCycleId();
        if (cycleId == BlockHitController.NO_CYCLE) {
            releaseAfterInputCycle = BlockHitController.NO_CYCLE;
            releaseWhenUseWriteConfirmsCycle = BlockHitController.NO_CYCLE;
            return;
        }
        if (useAction.isUseWriteSucceeded(cycleId)) {
            releaseAfterInputCycle = cycleId;
        } else {
            releaseWhenUseWriteConfirmsCycle = cycleId;
        }
    }

    private void discardPendingPackets() {
        successfulAttacks.clear();
        successfulUseWrites.clear();
        acceptedPacketKinds.clear();
        successfulMovementBoundaries.clear();
    }

    private void rememberAcceptedWrite(long writeId, long generation) {
        if (writeId != PacketAcceptedEvent.NO_WRITE_ID) {
            acceptedWriteGenerations.put(writeId, generation);
        }
    }

    private long consumeAcceptedWriteGeneration(long writeId) {
        Long generation = acceptedWriteGenerations.remove(writeId);
        return generation == null ? NO_BRIDGE_GENERATION : generation.longValue();
    }

    private void rememberAcceptedMovementWrite(long writeId, long generation) {
        if (writeId != PacketAcceptedEvent.NO_WRITE_ID) {
            latestAcceptedMovementWrite.set(new AcceptedMovementWrite(writeId, generation));
        }
    }

    private void clearAcceptedMovementWrite(long writeId) {
        while (true) {
            AcceptedMovementWrite accepted = latestAcceptedMovementWrite.get();
            if (accepted == null || accepted.writeId != writeId) {
                return;
            }
            if (latestAcceptedMovementWrite.compareAndSet(accepted, null)) {
                return;
            }
        }
    }

    private AcceptedMovementWrite consumeAcceptedMovementWrite(long writeId) {
        while (true) {
            AcceptedMovementWrite accepted = latestAcceptedMovementWrite.get();
            if (accepted == null || accepted.writeId != writeId) {
                return null;
            }
            if (latestAcceptedMovementWrite.compareAndSet(accepted, null)) {
                return accepted;
            }
        }
    }

    private void finishPostInputCycle() {
        long acceptedAttacks = acceptedAttackSequence.get();
        boolean attackAcceptedInCurrentInputWindow = acceptedAttacks != lastPostAcceptedAttackSequence;
        lastPostAcceptedAttackSequence = acceptedAttacks;
        if (!isEnabled() || !isGameplayReady() || !isPhysicalAttackDown()
                || hasExternalUseOwner() || useAction.isPhysicalUseDown()) {
            cancelCycle();
        }
        long requestedUseCycle = controller.getRequestedUseCycleId();
        long releaseCycle = releaseAfterInputCycle;
        if (releaseCycle == BlockHitController.NO_CYCLE && !attackAcceptedInCurrentInputWindow
                && controller.hasReleaseRequest() && useAction.isUseWriteSucceeded(requestedUseCycle)
                && controller.consumeReleaseRequest()) {
            releaseCycle = requestedUseCycle;
        }
        if (releaseCycle != BlockHitController.NO_CYCLE) {
            if (!useAction.isUseWriteSucceeded(releaseCycle)) {
                releaseAfterInputCycle = BlockHitController.NO_CYCLE;
            } else if (attackAcceptedInCurrentInputWindow) {
                return;
            } else {
                releaseAfterInputCycle = BlockHitController.NO_CYCLE;
                useAction.releaseUse(releaseCycle);
                return;
            }
        }
        if (isEnabled() && isGameplayReady() && isPhysicalAttackDown() && controller.consumeUseRequest()) {
            long useCycle = controller.getRequestedUseCycleId();
            if (!useAction.startUse(useCycle)) {
                controller.cancelCycle();
            }
        }
    }

    private static final class OwnedUseWrite {
        private final long cycleId;
        private final boolean success;
        private final long generation;

        private OwnedUseWrite(long cycleId, boolean success, long generation) {
            this.cycleId = cycleId;
            this.success = success;
            this.generation = generation;
        }
    }

    private static final class SuccessfulAttack {
        private final C02PacketUseEntity packet;
        private final long generation;

        private SuccessfulAttack(C02PacketUseEntity packet, long generation) {
            this.packet = packet;
            this.generation = generation;
        }
    }

    private static final class AcceptedPacketKind {
        private final BlockHitController.PacketKind kind;
        private final long generation;

        private AcceptedPacketKind(BlockHitController.PacketKind kind, long generation) {
            this.kind = kind;
            this.generation = generation;
        }
    }

    private static final class MovementBoundary {
        private final long generation;

        private MovementBoundary(long generation) {
            this.generation = generation;
        }
    }

    /** Latest C03 accepted by this module lifecycle; older boundaries are ignored conservatively. */
    private static final class AcceptedMovementWrite {
        private final long writeId;
        private final long generation;

        private AcceptedMovementWrite(long writeId, long generation) {
            this.writeId = writeId;
            this.generation = generation;
        }
    }

    private boolean hasExternalUseOwner() {
        return isModuleEnabled("KillAura") || isModuleEnabled("FakeLag") || isModuleEnabled("LagRange")
                || YozakuraRuntime.blinkManager != null && YozakuraRuntime.blinkManager.isBlinking();
    }

    private boolean isModuleEnabled(String name) {
        gq.yozakura.module.Module module = ModuleManager.getModule(name);
        return module != null && module.getState();
    }

    private boolean isGameplayReady() {
        return isInGame() && !mc.thePlayer.isDead && mc.currentScreen == null && mc.inGameHasFocus && isHoldingSword();
    }

    private boolean passesManualChance() {
        int chance = Math.max(0, Math.min(100, settings.chance.getValue()));
        return chance >= 100 || chance > 0 && Math.random() * 100.0D < chance;
    }

    private boolean isPhysicalAttackDown() {
        return mc.gameSettings != null && mc.gameSettings.keyBindAttack != null
                && gq.yozakura.util.module.KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
    }

    private boolean isHoldingSword() {
        if (!isInGame()) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemSword;
    }

}
