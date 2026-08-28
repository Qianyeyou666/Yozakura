package gq.yozakura.module.world;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.RotationResolvedEvent;
import gq.yozakura.event.bridge.SafeWalkEvent;
import gq.yozakura.event.bridge.SneakInputEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.util.module.BlockUtil;
import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/** TellyBridge 模式唯一的激活、动作、旋转、放置和数据包状态所有者。 */
final class TellyBridgeRuntime {
    private static final int INPUT_PRIORITY = 100;
    private static final int ROTATION_PRIORITY = 5;
    private static final String ROTATION_SOURCE = "TellyBridge";
    private static final long ROTATION_DURATION_MS = 50L;
    private static final long STARTUP_ROTATION_DURATION_MS = 140L;
    private static final float VISUAL_YAW_RESPONSE = 11.0F;
    private static final float VISUAL_PITCH_RESPONSE = 13.0F;
    private static final float VISUAL_MAX_YAW_SPEED = 260.0F;
    private static final float VISUAL_MAX_PITCH_SPEED = 190.0F;
    private static final long PROMPT_READY_MS = 1000L;
    private static final long PROMPT_SUPPRESS_USE_MS = 850L;
    private static final long PROMPT_BREAK_GRACE_MS = 300L;
    private static final float HUD_PROGRESS_RESPONSE = 14.0F;
    private static final float HUD_PRESENCE_RESPONSE = 11.0F;
    private static final float HUD_STATE_RESPONSE = 16.0F;
    private static final float HUD_RING_RADIUS = 13.0F;
    private static final float HUD_RING_WIDTH = 1.45F;
    private static final float HUD_LABEL_GAP = 6.0F;
    private static final double ACTIVATION_ACROSS_MIN = 0.38D;
    private static final double ACTIVATION_ACROSS_MAX = 0.65D;
    private static final double ACTIVATION_HEIGHT_MIN = 0.25D;
    private static final double ACTIVATION_HEIGHT_MAX = 0.75D;
    private static final int NIGHTBLOOM_PINK = 0xFFFF7AC8;
    private static final int NIGHTBLOOM_VIOLET = 0xFFA78BFA;
    private static final int NIGHTBLOOM_TEXT = 0xFFF7EEFF;
    private static final int NIGHTBLOOM_TRACK = 0xFF5B4B63;
    private static final int[] YAW_NUDGE_PATTERN = {0, 1, -1, 2, -2};
    private static final int SUBMITTED_PLACEMENT_TTL_TICKS = 4;

    private static final class SubmittedPlacement {
        final TellyBridgePlacementSearch.Candidate candidate;
        final int tick;
        final long generation;

        SubmittedPlacement(TellyBridgePlacementSearch.Candidate candidate, int tick,
                           long generation) {
            this.candidate = candidate;
            this.tick = tick;
            this.generation = generation;
        }
    }

    private static final class AcceptedPlacement {
        final TellyBridgePlacementSearch.Candidate candidate;
        final int tick;
        final long generation;

        AcceptedPlacement(SubmittedPlacement submitted) {
            this.candidate = submitted.candidate;
            this.tick = submitted.tick;
            this.generation = submitted.generation;
        }
    }

    private static final class PlacementWriteResult {
        final AcceptedPlacement accepted;
        final boolean success;

        PlacementWriteResult(AcceptedPlacement accepted, boolean success) {
            this.accepted = accepted;
            this.success = success;
        }
    }

    private static final class TellyHudVisual {
        final String centerText;
        final String labelText;
        final int accentColor;

        TellyHudVisual(String centerText, String labelText, int accentColor) {
            this.centerText = centerText;
            this.labelText = labelText;
            this.accentColor = accentColor;
        }
    }

    private static final class TellyHudLayout {
        final float centerX;
        final float ringCenterY;
        final float centerTextX;
        final float centerTextY;
        final float labelX;
        final float labelY;

        TellyHudLayout(float centerX, float ringCenterY, float centerTextX, float centerTextY,
                       float labelX, float labelY) {
            this.centerX = centerX;
            this.ringCenterY = ringCenterY;
            this.centerTextX = centerTextX;
            this.centerTextY = centerTextY;
            this.labelX = labelX;
            this.labelY = labelY;
        }
    }

    private final Minecraft mc;
    private final Mode<BridgeAssistBridgeModeStateMachine.Mode> mode;
    private final Option<Boolean> autoSwap;
    private final Option<Boolean> disableSafeWalk;
    private final Option<Boolean> showActivationHitbox;
    private final TellyBridgePlacementSearch placementSearch;
    private final ConcurrentLinkedQueue<SubmittedPlacement> submittedPlacements =
            new ConcurrentLinkedQueue<SubmittedPlacement>();
    private final ConcurrentHashMap<Long, AcceptedPlacement> acceptedPlacements =
            new ConcurrentHashMap<Long, AcceptedPlacement>();
    private final ConcurrentLinkedQueue<PlacementWriteResult> placementWriteResults =
            new ConcurrentLinkedQueue<PlacementWriteResult>();

    private TellyBridgeRotation rotation;
    private boolean armed;
    private boolean running;
    private long activatePromptAt;
    private long promptBrokeAt;
    private float promptAlpha;
    private long promptFadeLastAt;
    private int promptFadeRgb = 0xFF5555;
    private long activationSucceededAt;
    private float persistentDisplayProgress;
    private float visualProgress;
    private float hudPresence;
    private float hudStateBlend;
    private long hudAnimationLastAt;
    private String hudVisualState = "";
    private int setupTick;
    private int cyclePhase;
    private float stagedForward;
    private float stagedStrafe;
    private boolean stagedJump;
    private boolean stagedSprint;
    private float baseYaw;
    private int travelX;
    private int travelZ;
    private double antiSwayLane;
    private float antiSwayYawOffset;
    private boolean antiSwayTapUsed;
    private float scriptedRotationYaw;
    private float scriptedRotationPitch;
    private int rotationStepCounter;
    private boolean firstTellyPlacementPending;
    private boolean adaptiveAimValid;
    private float adaptiveAimYaw;
    private float adaptiveAimPitch;
    private long adaptiveAimUpdatedAt;
    private long takeoverDetectionAt;
    private boolean takeoverCameraValid;
    private float takeoverCameraYaw;
    private float takeoverCameraPitch;
    private float takeoverAccumulated;
    private long takeoverLastFrameAt;
    private long freezeLastTickAt;
    private int lastAdvancedInputTick = Integer.MIN_VALUE;
    private int lastPlacementAttemptTick = Integer.MIN_VALUE;
    private int lastSuccessfulPlaceTick = Integer.MIN_VALUE;
    private int forceSuppressTick = Integer.MIN_VALUE;
    private final AtomicLong totalAcceptedC08 = new AtomicLong();
    private volatile long runtimeGeneration;
    private volatile long c08AtTickBoundary;
    private boolean scriptedUse;
    private boolean useSuppressed;
    private TellyBridgePlacementSearch.Candidate pendingPlacement;
    private BlockPos activationAnchor;
    private EnumFacing activationFace;

    TellyBridgeRuntime(Minecraft mc,
                       Mode<BridgeAssistBridgeModeStateMachine.Mode> mode,
                       Option<Boolean> autoSwap,
                       Option<Boolean> disableSafeWalk,
                       Option<Boolean> showActivationHitbox) {
        this.mc = mc;
        this.mode = mode;
        this.autoSwap = autoSwap;
        this.disableSafeWalk = disableSafeWalk;
        this.showActivationHitbox = showActivationHitbox;
        this.placementSearch = new TellyBridgePlacementSearch(mc);
        this.rotation = new TellyBridgeRotation(rotationGcd());
    }

    boolean ownsSpecialPath() {
        return selected() && (armed || running || activatePromptAt != 0L);
    }

    boolean ownsAttackPath() {
        return selected() && running;
    }

    boolean shouldSuppressManualClicksThisTick() {
        if (!selected() || !running || mc.thePlayer == null) {
            return false;
        }
        int tick = mc.thePlayer.ticksExisted;
        return lastSuccessfulPlaceTick == tick || forceSuppressTick == tick;
    }

    void observeModeSelection() {
        if (!selected()) {
            if (running || armed || activatePromptAt != 0L) {
                resetRuntime();
            }
            return;
        }
        if (!running && !armed) {
            armed = true;
        }
    }

    boolean ownsMovementPath() {
        return selected() && (running || activationPromptReady());
    }

    void enable() {
        resetRuntime();
        armed = true;
    }

    void reset() {
        resetRuntime();
        armed = false;
    }

    void onWorldJoin() {
        resetRuntime();
        armed = selected();
    }

    void onUnavailable() {
        if (running || activatePromptAt != 0L) {
            stopAutomation();
        }
    }

    void onSneakInput(SneakInputEvent event) {
        if (!selected()) {
            return;
        }
        if (running) {
            advanceTellyCycle(event.getTick());
            float correctedStrafe = applyAntiSway(stagedForward, stagedStrafe);
            event.requestSneak(SneakInputEvent.SneakIntent.FORCE_OFF, INPUT_PRIORITY);
            event.requestMovement(stagedForward, correctedStrafe, stagedJump, INPUT_PRIORITY);
            if (mc.thePlayer != null) {
                mc.thePlayer.setSprinting(stagedSprint);
            }
            return;
        }
        if (activationPromptReady() && physicalUseDown()) {
            event.requestSneak(SneakInputEvent.SneakIntent.FORCE_OFF, INPUT_PRIORITY);
            event.requestMovement(-1.0F, -1.0F, false, INPUT_PRIORITY);
        }
    }

    boolean onUpdate(UpdateEvent event) {
        if (!selected()) {
            if (running || armed || activatePromptAt != 0L) {
                resetRuntime();
            }
            return false;
        }
        if (event.getType() == EventType.POST) {
            c08AtTickBoundary = totalAcceptedC08.get();
            return ownsSpecialPath();
        }
        if (event.getType() != EventType.PRE) {
            return ownsSpecialPath();
        }
        if (!validGame()) {
            onUnavailable();
            return true;
        }
        if (!running) {
            updateActivationPrompt();
            if (!running) {
                return true;
            }
        }

        long now = System.currentTimeMillis();
        if (freezeLastTickAt != 0L && now - freezeLastTickAt > 300L) {
            stopAutomation();
            return true;
        }
        freezeLastTickAt = now;
        if (mc.thePlayer.isDead || mc.thePlayer.fallDistance > 7.0F) {
            stopAutomation();
            return true;
        }
        handleAutoSwap();
        if (!ItemUtil.isBlock(mc.thePlayer.getHeldItem())) {
            stopAutomation();
            return true;
        }
        if (detectManualCameraTakeover()) {
            return true;
        }

        drainPlacementWriteResults();
        pruneSubmittedPlacements(mc.thePlayer.ticksExisted);
        refreshAdaptivePlacementAim(now);
        processAutoPlaceTick();
        refreshAdaptivePlacementAim(now);
        publishRotation(event, now);
        return true;
    }

    void onRotationResolved(RotationResolvedEvent event) {
        if (!selected() || !running || event == null || !event.isRotated()
                || mc.thePlayer == null
                || Float.compare(event.getYaw(), scriptedRotationYaw) != 0
                || Float.compare(event.getPitch(), scriptedRotationPitch) != 0) {
            return;
        }
        mc.thePlayer.rotationYaw = event.getYaw();
        mc.thePlayer.rotationPitch = event.getPitch();
        takeoverCameraValid = true;
        takeoverCameraYaw = event.getYaw();
        takeoverCameraPitch = event.getPitch();
        takeoverLastFrameAt = System.currentTimeMillis();
    }

    void onRightClick(RightClickMouseEvent event) {
        if (!selected()) {
            return;
        }
        if (running) {
            if (shouldSuppressManualClicksThisTick() || pendingPlacement != null
                    || totalAcceptedC08.get() > c08AtTickBoundary
                    || hasOutstandingPlacement()) {
                suppressUse();
            }
            event.setCancelled(true);
            return;
        }
        if (activationSuppressUse()) {
            event.setCancelled(true);
        }
    }

    void onRightClickCancelled() {
        if (!running) {
            return;
        }
        pendingPlacement = null;
    }

    void onSafeWalk(SafeWalkEvent event) {
        if (selected() && running && Boolean.TRUE.equals(disableSafeWalk.getValue())) {
            event.setSafeWalk(false);
        }
    }

    void onPacket(PacketEvent event) {
        if (!selected() || event.isCancelled()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (event.getType() == EventType.RECEIVE) {
            if (packet instanceof S08PacketPlayerPosLook && running) {
                stopAutomation();
            } else if (packet instanceof S23PacketBlockChange) {
                placementSearch.clearGhost(((S23PacketBlockChange) packet).getBlockPosition());
            }
            return;
        }
        if (isDropProtected() && packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging.Action action = ((C07PacketPlayerDigging) packet).getStatus();
            if (action == C07PacketPlayerDigging.Action.DROP_ITEM
                    || action == C07PacketPlayerDigging.Action.DROP_ALL_ITEMS) {
                event.setCancelled(true);
                return;
            }
        }
        if (packet instanceof C03PacketPlayer) {
            C03PacketPlayer movement = (C03PacketPlayer) packet;
            if (movement.isMoving()) {
                placementSearch.updateServerPosition(
                        movement.getPositionX(),
                        movement.getPositionY(),
                        movement.getPositionZ());
            }
        }
        if (!running) {
            return;
        }
        if (packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
            event.setCancelled(true);
            return;
        }
        if (packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging.Action action = ((C07PacketPlayerDigging) packet).getStatus();
            if (action == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
                    || action == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
                    || action == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
                event.setCancelled(true);
                return;
            }
        }
        if (packet instanceof C0BPacketEntityAction
                && ((C0BPacketEntityAction) packet).getAction()
                == C0BPacketEntityAction.Action.START_SNEAKING) {
            event.setCancelled(true);
            return;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement placement = (C08PacketPlayerBlockPlacement) packet;
            if (placement.getPlacedBlockDirection() == 255 && pendingPlacement != null) {
                event.setCancelled(true);
            } else if (placement.getPlacedBlockDirection() != 255) {
                BlockPos target = placement.getPosition().offset(
                        EnumFacing.getFront(placement.getPlacedBlockDirection()));
                if (!isStraightTarget(target)) {
                    placementSearch.markRejected(target, mc.thePlayer.ticksExisted);
                    placementSearch.markCancelledGhost(target);
                    event.setCancelled(true);
                } else {
                    placementSearch.clearCancelledGhost(target);
                }
            }
        }
        if (packet instanceof C03PacketPlayer) {
            // canonical C03 由 UpdateEvent 的 silent rotation bridge 统一重写。
        }
    }

    void onPacketAccepted(PacketAcceptedEvent event) {
        if (!selected() || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
        if (packet.getPlacedBlockDirection() == 255 || !ItemUtil.isBlock(packet.getStack())) {
            return;
        }
        event.requestOriginalPacketOrder();
        totalAcceptedC08.incrementAndGet();
        SubmittedPlacement submitted = claimSubmittedPlacement(packet);
        if (submitted != null) {
            acceptedPlacements.put(event.getWriteId(), new AcceptedPlacement(submitted));
        }
    }

    void onPacketWritten(PacketWriteEvent event) {
        if (event == null) {
            return;
        }
        AcceptedPlacement accepted = acceptedPlacements.remove(event.getWriteId());
        if (accepted != null) {
            placementWriteResults.offer(new PlacementWriteResult(accepted, event.isSuccess()));
        }
    }

    void onRenderTick() {
        if (running) {
            detectManualCameraTakeover();
        }
    }

    void onRender2D() {
        updateActivationPromptFade();
        if (!selected() || mc.fontRendererObj == null || mc.thePlayer == null
                || mc.theWorld == null || mc.playerController == null || mc.gameSettings == null) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean showingSuccess = activationSucceededAt != 0L
                && now - activationSucceededAt < 1400L;
        boolean shouldShow = mc.thePlayer.isSneaking() && (!running || showingSuccess);
        updateHudAnimation(now, shouldShow, showingSuccess);
        if (hudPresence < 0.01F) {
            return;
        }

        TellyHudVisual visual = resolveHudVisual(now, showingSuccess);
        TellyHudLayout layout = resolveHudLayout(new ScaledResolution(mc), visual);
        drawTellyHud(visual, layout);
    }

    private TellyHudVisual resolveHudVisual(long now, boolean showingSuccess) {
        if (showingSuccess) {
            return new TellyHudVisual("OK", "Telly activated", NIGHTBLOOM_VIOLET);
        }
        if (activatePromptAt == 0L) {
            return new TellyHudVisual("IDLE", activationStatusMessage(), NIGHTBLOOM_PINK);
        }

        long elapsed = Math.max(0L, now - activatePromptAt);
        float progress = clamp01(elapsed / (float) PROMPT_READY_MS);
        if (progress >= 1.0F) {
            return new TellyHudVisual("GO", "Release sneak + hold use", NIGHTBLOOM_VIOLET);
        }

        long remainingTenths = (Math.max(0L, PROMPT_READY_MS - elapsed) + 99L) / 100L;
        String label = "Hold " + remainingTenths / 10L + "." + remainingTenths % 10L + "s";
        String progressText = Integer.toString(Math.round(persistentDisplayProgress * 100.0F));
        return new TellyHudVisual(progressText, label, NIGHTBLOOM_PINK);
    }

    private TellyHudLayout resolveHudLayout(ScaledResolution scaled, TellyHudVisual visual) {
        CFontRenderer centerFont = FontLoaders.BRICOLAGE12;
        CFontRenderer labelFont = FontLoaders.BRICOLAGE14;
        float ringCenterY = scaled.getScaledHeight() * 0.5F + 20.0F
                - (1.0F - hudPresence) * 4.0F;
        float labelWidth = labelFont.getStringWidth(visual.labelText);
        float labelX = scaled.getScaledWidth() * 0.5F - labelWidth * 0.5F;
        float centerX = labelX + labelWidth * 0.5F;
        float labelY = ringCenterY + HUD_RING_RADIUS + HUD_LABEL_GAP;
        float centerTextX = centerX - centerFont.getStringWidth(visual.centerText) * 0.5F;
        float centerTextY = ringCenterY - centerFont.getHeight() * 0.5F + 0.5F;
        return new TellyHudLayout(centerX, ringCenterY, centerTextX, centerTextY, labelX, labelY);
    }

    private void drawTellyHud(TellyHudVisual visual, TellyHudLayout layout) {
        int trackColor = withAlpha(NIGHTBLOOM_TRACK, 76.0F * hudPresence);
        int progressColor = withAlpha(visual.accentColor, 238.0F * hudPresence);
        int centerTextColor = withAlpha(visual.accentColor,
                224.0F * hudPresence * stateTextAlpha());
        int labelColor = withAlpha(NIGHTBLOOM_TEXT,
                238.0F * hudPresence * stateTextAlpha());
        boolean shadowFrameOpen = RenderServices.shadows().isFrameOpen();
        boolean glowFrameOpen = RenderServices.glow().isFrameOpen();
        boolean ownsEffectsFrame = !shadowFrameOpen && !glowFrameOpen;
        if (shadowFrameOpen != glowFrameOpen) {
            return;
        }

        if (ownsEffectsFrame) {
            RenderServices.beginHudEffectsFrame();
        }
        try {
            // Another HUD widget may have queued effects while a local scale or
            // translation was active. Force this standalone screen-space HUD to
            // capture the current overlay matrix instead of reusing that snapshot.
            RenderServices.markHudEffectsStateChanged();
            if (visualProgress > 0.001F) {
                RenderServices.glow().queueRing(layout.centerX, layout.ringCenterY,
                        HUD_RING_RADIUS, HUD_RING_WIDTH, visualProgress,
                        withAlpha(visual.accentColor, 220.0F * hudPresence),
                        0.94F, GlowProfile.ACCENT);
                RenderServices.glow().queueRing(layout.centerX, layout.ringCenterY,
                        HUD_RING_RADIUS, HUD_RING_WIDTH, visualProgress,
                        withAlpha(visual.accentColor, 150.0F * hudPresence),
                        0.78F, GlowProfile.PANEL);
            }
            FontLoaders.BRICOLAGE12.drawGlowString(visual.centerText,
                    layout.centerTextX, layout.centerTextY,
                    withAlpha(visual.accentColor, 132.0F * hudPresence),
                    0.74F, GlowProfile.ACCENT);
            FontLoaders.BRICOLAGE12.drawStringWithGlow(visual.centerText,
                    layout.centerTextX, layout.centerTextY, centerTextColor,
                    withAlpha(visual.accentColor, 230.0F * hudPresence),
                    0.96F, GlowProfile.TEXT);
            FontLoaders.BRICOLAGE14.drawStringWithGlow(visual.labelText,
                    layout.labelX, layout.labelY, labelColor,
                    withAlpha(visual.accentColor, 156.0F * hudPresence),
                    0.72F, GlowProfile.TEXT);
            drawTellyHudRing(layout, trackColor, progressColor);
        } finally {
            if (ownsEffectsFrame) {
                RenderServices.flushHudEffectsFrame();
            }
        }
    }

    private void drawTellyHudRing(TellyHudLayout layout, int trackColor, int progressColor) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableDepth();
            RenderUtil.drawArcOutline(layout.centerX, layout.ringCenterY, HUD_RING_RADIUS,
                    0.0F, 360.0F, HUD_RING_WIDTH, trackColor);
            if (visualProgress > 0.001F) {
                RenderUtil.drawRoundedArcOutline(layout.centerX, layout.ringCenterY,
                        HUD_RING_RADIUS, -90.0F, -90.0F + visualProgress * 360.0F,
                        HUD_RING_WIDTH, progressColor);
            }
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private void updateHudAnimation(long now, boolean shouldShow, boolean showingSuccess) {
        long deltaMillis = hudAnimationLastAt == 0L ? 16L : Math.max(0L, Math.min(100L, now - hudAnimationLastAt));
        hudAnimationLastAt = now;
        float deltaSeconds = deltaMillis / 1000.0F;
        float targetProgress = showingSuccess ? 1.0F
                : activatePromptAt == 0L ? 0.0F
                : clamp01((now - activatePromptAt) / (float) PROMPT_READY_MS);
        persistentDisplayProgress += (targetProgress - persistentDisplayProgress)
                * exponentialBlend(HUD_PROGRESS_RESPONSE, deltaSeconds);
        visualProgress += (persistentDisplayProgress - visualProgress)
                * exponentialBlend(HUD_STATE_RESPONSE, deltaSeconds);
        float targetPresence = shouldShow ? 1.0F : 0.0F;
        hudPresence += (targetPresence - hudPresence)
                * exponentialBlend(HUD_PRESENCE_RESPONSE, deltaSeconds);
        String nextState = showingSuccess ? "success" : activatePromptAt == 0L ? "idle"
                : targetProgress >= 1.0F ? "ready" : "arming";
        if (!nextState.equals(hudVisualState)) {
            hudVisualState = nextState;
            hudStateBlend = 0.0F;
        }
        hudStateBlend += (1.0F - hudStateBlend)
                * exponentialBlend(HUD_STATE_RESPONSE, deltaSeconds);
    }

    private float stateTextAlpha() {
        return 0.72F + 0.28F * clamp01(hudStateBlend);
    }

    private static float exponentialBlend(float response, float deltaSeconds) {
        return 1.0F - (float) Math.exp(-response * Math.max(0.0F, deltaSeconds));
    }

    void onRender3D() {
        if (!Boolean.TRUE.equals(showActivationHitbox.getValue())
                || mc.thePlayer == null || !mc.thePlayer.isSneaking()
                || !armed || running || promptAlpha < 0.05F
                || activationAnchor == null || activationFace == null) {
            return;
        }
        drawActivationFaceRegion(activationAnchor, activationFace);
    }

    private void drawActivationFaceRegion(BlockPos pos, EnumFacing face) {
        if (face == EnumFacing.UP || face == EnumFacing.DOWN || mc.getRenderManager() == null) {
            return;
        }
        double yMin = pos.getY() + ACTIVATION_HEIGHT_MIN;
        double yMax = pos.getY() + ACTIVATION_HEIGHT_MAX;
        double x1;
        double z1;
        double x2;
        double z2;
        if (face == EnumFacing.EAST) {
            x1 = pos.getX() + 1.005D;
            x2 = x1;
            z1 = pos.getZ() + ACTIVATION_ACROSS_MIN;
            z2 = pos.getZ() + ACTIVATION_ACROSS_MAX;
        } else if (face == EnumFacing.WEST) {
            x1 = pos.getX() - 0.005D;
            x2 = x1;
            z1 = pos.getZ() + 1.0D - ACTIVATION_ACROSS_MAX;
            z2 = pos.getZ() + 1.0D - ACTIVATION_ACROSS_MIN;
        } else if (face == EnumFacing.SOUTH) {
            z1 = pos.getZ() + 1.005D;
            z2 = z1;
            x1 = pos.getX() + 1.0D - ACTIVATION_ACROSS_MAX;
            x2 = pos.getX() + 1.0D - ACTIVATION_ACROSS_MIN;
        } else {
            z1 = pos.getZ() - 0.005D;
            z2 = z1;
            x1 = pos.getX() + ACTIVATION_ACROSS_MIN;
            x2 = pos.getX() + ACTIVATION_ACROSS_MAX;
        }

        float red = (promptFadeRgb >> 16 & 0xFF) / 255.0F;
        float green = (promptFadeRgb >> 8 & 0xFF) / 255.0F;
        float blue = (promptFadeRgb & 0xFF) / 255.0F;
        float bloomPulse = 0.78F + 0.22F * (float) Math.sin(
                System.currentTimeMillis() * Math.PI * 2.0D / 1250.0D);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glTranslated(-mc.getRenderManager().viewerPosX,
                    -mc.getRenderManager().viewerPosY,
                    -mc.getRenderManager().viewerPosZ);

            GL11.glColor4f(red, green, blue, Math.max(3.0F / 255.0F,
                    32.0F / 255.0F * promptAlpha));
            GL11.glBegin(GL11.GL_QUADS);
            activationVertex(x1, yMin, z1);
            activationVertex(x2, yMin, z2);
            activationVertex(x2, yMax, z2);
            activationVertex(x1, yMax, z1);
            GL11.glEnd();

            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glLineWidth(3.8F);
            GL11.glColor4f(red, green, blue, Math.max(5.0F / 255.0F,
                    48.0F / 255.0F * promptAlpha * bloomPulse));
            GL11.glBegin(GL11.GL_LINE_LOOP);
            activationVertex(x1, yMin, z1);
            activationVertex(x2, yMin, z2);
            activationVertex(x2, yMax, z2);
            activationVertex(x1, yMax, z1);
            GL11.glEnd();

            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(1.35F);
            GL11.glColor4f(red, green, blue, Math.max(12.0F / 255.0F,
                    205.0F / 255.0F * promptAlpha));
            GL11.glBegin(GL11.GL_LINE_LOOP);
            activationVertex(x1, yMin, z1);
            activationVertex(x2, yMin, z2);
            activationVertex(x2, yMax, z2);
            activationVertex(x1, yMax, z1);
            GL11.glEnd();

            GL11.glColor4f(red, green, blue, Math.max(7.0F / 255.0F,
                    95.0F / 255.0F * promptAlpha));
            GL11.glBegin(GL11.GL_LINES);
            activationVertex(x1, yMin, z1);
            activationVertex(x2, yMax, z2);
            activationVertex(x2, yMin, z2);
            activationVertex(x1, yMax, z1);
            GL11.glEnd();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
        }
    }

    private static void activationVertex(double x, double y, double z) {
        GL11.glVertex3d(x, y, z);
    }

    private void updateActivationPromptFade() {
        boolean show = armed && !running && activatePromptAt != 0L;
        if (show) {
            rememberActivationPromptColor();
        }
        long now = System.currentTimeMillis();
        long elapsed = promptFadeLastAt == 0L ? 0L : Math.min(100L, now - promptFadeLastAt);
        promptFadeLastAt = now;
        float step = elapsed / 200.0F;
        promptAlpha += show ? step : -step;
        promptAlpha = MathHelper.clamp_float(promptAlpha, 0.0F, 1.0F);
    }

    private void rememberActivationPromptColor() {
        if (activatePromptAt != 0L) {
            promptFadeRgb = activationPromptReady()
                    ? NIGHTBLOOM_VIOLET & 0xFFFFFF
                    : NIGHTBLOOM_PINK & 0xFFFFFF;
        }
    }

    private String activationStatusMessage() {
        if (!ItemUtil.isBlock(mc.thePlayer.getHeldItem())) {
            return "Hold blocks";
        }
        if (!TellyBridgeProgram.isActivationYawAligned(mc.thePlayer.rotationYaw)) {
            return "Face diagonal";
        }
        if (mc.thePlayer.rotationPitch < 75.0F) {
            return "Look down";
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.sideHit == null || hit.sideHit.getAxis() == EnumFacing.Axis.Y) {
            return "Aim at block side";
        }
        int expectedX = TellyBridgeProgram.travelX(mc.thePlayer.rotationYaw);
        int expectedZ = TellyBridgeProgram.travelZ(mc.thePlayer.rotationYaw);
        if (hit.sideHit.getFrontOffsetX() != expectedX
                || hit.sideHit.getFrontOffsetZ() != expectedZ) {
            return "Aim at outward side";
        }
        BlockPos block = hit.getBlockPos();
        Vec3 local = hit.hitVec.subtract(new Vec3(block));
        double across = hit.sideHit.getAxis() == EnumFacing.Axis.X
                ? local.zCoord : local.xCoord;
        if (hit.sideHit == EnumFacing.SOUTH || hit.sideHit == EnumFacing.WEST) {
            across = 1.0D - across;
        }
        if (across < ACTIVATION_ACROSS_MIN || across > ACTIVATION_ACROSS_MAX
                || local.yCoord < ACTIVATION_HEIGHT_MIN
                || local.yCoord > ACTIVATION_HEIGHT_MAX) {
            return "Aim at marked zone";
        }
        if (!isPlayerOnActivationBlock(block)) {
            return "Stand on that block";
        }
        if (activationLipDistance(block, hit.sideHit) > 0.65D) {
            return "Move to the edge";
        }
        if (!BlockUtil.isReplaceable(block.offset(hit.sideHit).up())) {
            return "Clear space ahead";
        }
        return "Hold sneak";
    }

    private boolean isPlayerOnActivationBlock(BlockPos block) {
        if (block == null || block.getY() != MathHelper.floor_double(mc.thePlayer.posY - 0.01D)) {
            return false;
        }
        return Math.abs(mc.thePlayer.posX - (block.getX() + 0.5D)) <= 0.85D
                && Math.abs(mc.thePlayer.posZ - (block.getZ() + 0.5D)) <= 0.85D;
    }

    private double activationLipDistance(BlockPos block, EnumFacing face) {
        if (face == EnumFacing.EAST) {
            return block.getX() + 1.0D - mc.thePlayer.posX;
        }
        if (face == EnumFacing.WEST) {
            return mc.thePlayer.posX - block.getX();
        }
        if (face == EnumFacing.SOUTH) {
            return block.getZ() + 1.0D - mc.thePlayer.posZ;
        }
        return mc.thePlayer.posZ - block.getZ();
    }

    private void updateActivationPrompt() {
        if (!armed || !validGame()) {
            clearActivationPrompt();
            return;
        }
        boolean lookingDown = mc.thePlayer.rotationPitch >= 75.0F;
        boolean atEdge = lookingDown && isLookingAtEdge();
        long now = System.currentTimeMillis();
        if (mc.thePlayer.isSneaking() && atEdge) {
            if (activatePromptAt == 0L) {
                activatePromptAt = now;
            }
            promptBrokeAt = 0L;
            captureActivationAnchor();
            return;
        }
        if (activatePromptAt == 0L) {
            return;
        }
        if (!activationPromptReady()) {
            clearActivationPrompt();
            return;
        }
        if (promptBrokeAt == 0L) {
            rememberActivationPromptColor();
            promptBrokeAt = now;
        }
        if (!mc.thePlayer.isSneaking() && physicalUseDown()
                && TellyBridgeProgram.isActivationYawAligned(mc.thePlayer.rotationYaw)) {
            rememberActivationPromptColor();
            beginAutomation();
            return;
        }
        if (now - promptBrokeAt > PROMPT_BREAK_GRACE_MS) {
            clearActivationPrompt();
        }
    }

    private boolean isLookingAtEdge() {
        if (!TellyBridgeProgram.isActivationYawAligned(mc.thePlayer.rotationYaw)) {
            return false;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.sideHit == EnumFacing.UP || hit.sideHit == EnumFacing.DOWN) {
            return false;
        }
        int travelX = TellyBridgeProgram.travelX(mc.thePlayer.rotationYaw);
        int travelZ = TellyBridgeProgram.travelZ(mc.thePlayer.rotationYaw);
        EnumFacing expected = travelX > 0 ? EnumFacing.EAST
                : travelX < 0 ? EnumFacing.WEST
                : travelZ > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
        if (hit.sideHit != expected) {
            return false;
        }
        Vec3 local = hit.hitVec.subtract(new Vec3(hit.getBlockPos()));
        double across = expected.getAxis() == EnumFacing.Axis.X ? local.zCoord : local.xCoord;
        if (expected == EnumFacing.SOUTH || expected == EnumFacing.WEST) {
            across = 1.0D - across;
        }
        if (across < ACTIVATION_ACROSS_MIN || across > ACTIVATION_ACROSS_MAX
                || local.yCoord < ACTIVATION_HEIGHT_MIN
                || local.yCoord > ACTIVATION_HEIGHT_MAX) {
            return false;
        }
        double lip = expected == EnumFacing.EAST
                ? hit.getBlockPos().getX() + 1.0D - mc.thePlayer.posX
                : expected == EnumFacing.WEST
                ? mc.thePlayer.posX - hit.getBlockPos().getX()
                : expected == EnumFacing.SOUTH
                ? hit.getBlockPos().getZ() + 1.0D - mc.thePlayer.posZ
                : mc.thePlayer.posZ - hit.getBlockPos().getZ();
        return lip <= 0.65D;
    }

    private void captureActivationAnchor() {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            activationAnchor = hit.getBlockPos();
            activationFace = hit.sideHit;
        }
    }

    private void beginAutomation() {
        if (!ItemUtil.isBlock(mc.thePlayer.getHeldItem())) {
            return;
        }
        baseYaw = Math.round((mc.thePlayer.rotationYaw - 45.0F) / 90.0F) * 90.0F + 45.0F;
        travelX = TellyBridgeProgram.travelX(baseYaw);
        travelZ = TellyBridgeProgram.travelZ(baseYaw);
        antiSwayLane = travelX != 0 ? mc.thePlayer.posZ : mc.thePlayer.posX;
        antiSwayYawOffset = 0.0F;
        antiSwayTapUsed = false;
        setupTick = 0;
        cyclePhase = 19;
        stagedForward = -1.0F;
        stagedStrafe = -1.0F;
        stagedJump = false;
        stagedSprint = false;
        scriptedRotationYaw = baseYaw;
        scriptedRotationPitch = 74.52F;
        scriptedUse = true;
        rotation = new TellyBridgeRotation(rotationGcd());
        rotation.reset(scriptedRotationYaw, scriptedRotationPitch);
        rotation.setTarget(scriptedRotationYaw, scriptedRotationPitch,
                baseYaw, 74.52F, System.currentTimeMillis(), ROTATION_DURATION_MS);
        placementSearch.beginLane(mc.thePlayer, baseYaw, activationAnchor);
        activationSucceededAt = System.currentTimeMillis();
        running = true;
        armed = false;
        activatePromptAt = 0L;
        promptBrokeAt = 0L;
        freezeLastTickAt = System.currentTimeMillis();
        takeoverDetectionAt = 0L;
        takeoverCameraValid = false;
        firstTellyPlacementPending = false;
        lastAdvancedInputTick = Integer.MIN_VALUE;
        setUsePressed(true);
        setAttackPressed(false);
    }

    private void advanceTellyCycle(int tick) {
        if (!running || lastAdvancedInputTick == tick) {
            return;
        }
        lastAdvancedInputTick = tick;
        long now = System.currentTimeMillis();
        if (setupTick >= 0) {
            if (setupTick < 12) {
                useSuppressed = false;
                stagedForward = -1.0F;
                stagedStrafe = -1.0F;
                stagedJump = setupTick >= 6;
                stagedSprint = false;
                scriptedUse = true;
                setUsePressed(scriptedUse);
                if (setupTick == 11) {
                    setRotationTarget(baseYaw + TellyBridgeProgram.yaw(19),
                            TellyBridgeProgram.pitch(19), now);
                } else {
                    setRotationTarget(baseYaw, 74.52F, now);
                }
                setupTick++;
                return;
            }
            setupTick = -1;
            takeoverDetectionAt = now + 125L;
            takeoverCameraValid = true;
            takeoverCameraYaw = mc.thePlayer.rotationYaw;
            takeoverCameraPitch = mc.thePlayer.rotationPitch;
            takeoverAccumulated = 0.0F;
            takeoverLastFrameAt = now;
            cyclePhase = 19;
            firstTellyPlacementPending = true;
            adaptiveAimValid = false;
        }
        int phase = cyclePhase;
        stagedForward = TellyBridgeProgram.forward(phase);
        stagedStrafe = TellyBridgeProgram.strafe(phase);
        stagedJump = TellyBridgeProgram.jumping(phase);
        stagedSprint = TellyBridgeProgram.sprinting(phase);
        scriptedUse = TellyBridgeProgram.using(phase);
        setUsePressed(scriptedUse);
        useSuppressed = false;
        int nextPhase = (phase + 1) % TellyBridgeProgram.length();
        setRotationTarget(baseYaw + TellyBridgeProgram.yaw(nextPhase),
                TellyBridgeProgram.pitch(nextPhase), now);
        cyclePhase = nextPhase;
    }

    private void setRotationTarget(float yaw, float pitch, long now) {
        float correctedYaw = yaw;
        if (firstTellyPlacementPending && adaptiveAimValid
                && now - adaptiveAimUpdatedAt <= 125L) {
            correctedYaw = adaptiveAimYaw;
            pitch = adaptiveAimPitch;
        } else {
            correctedYaw += antiSwayYawOffset;
        }
        correctedYaw += rotationGcd() * YAW_NUDGE_PATTERN[++rotationStepCounter % 5];
        rotation.setTarget(scriptedRotationYaw, scriptedRotationPitch,
                correctedYaw, pitch, now, ROTATION_DURATION_MS);
    }

    private void publishRotation(UpdateEvent event, long now) {
        TellyBridgeRotation.Sample sample = rotation.sample(now);
        scriptedRotationYaw = sample.yaw;
        scriptedRotationPitch = sample.pitch;
        if (event.trySetRotation(sample.yaw, sample.pitch, ROTATION_PRIORITY)) {
            event.setPervRotation(sample.yaw, ROTATION_PRIORITY, false);
            VisualRotationState.publish(ROTATION_SOURCE, sample.yaw, sample.pitch,
                    ROTATION_PRIORITY);
        }
    }

    private float applyAntiSway(float forward, float recordedStrafe) {
        if (mc.thePlayer == null) {
            return recordedStrafe;
        }
        double lane = travelX != 0 ? mc.thePlayer.posZ : mc.thePlayer.posX;
        double velocity = travelX != 0 ? mc.thePlayer.motionZ : mc.thePlayer.motionX;
        double error = antiSwayLane - lane;
        if (Math.abs(error) < 0.015D && Math.abs(velocity) < 0.008D) {
            antiSwayTapUsed = false;
            antiSwayYawOffset *= 0.65F;
            return recordedStrafe;
        }
        double desiredVelocity = MathHelper.clamp_double(error * 0.42D - velocity * 0.78D,
                -0.16D, 0.16D);
        double correction = desiredVelocity - velocity;
        double radians = Math.toRadians(scriptedRotationYaw);
        double derivative = travelX != 0
                ? -forward * Math.sin(radians) + recordedStrafe * Math.cos(radians)
                : -forward * Math.cos(radians) - recordedStrafe * Math.sin(radians);
        double yawOffset = Math.abs(derivative) < 0.12D ? 0.0D
                : Math.toDegrees(correction * 0.55D / derivative);
        yawOffset = MathHelper.clamp_double(yawOffset, -2.25D, 2.25D);
        antiSwayYawOffset = antiSwayYawOffset * 0.60F + (float) yawOffset * 0.40F;
        double strafeAxis = travelX != 0 ? Math.sin(radians) : Math.cos(radians);
        if (!antiSwayTapUsed && Math.abs(correction) >= 0.03D
                && correction * strafeAxis > 0.0D && recordedStrafe < 0.5F) {
            antiSwayTapUsed = true;
            return recordedStrafe + 1.0F;
        }
        return recordedStrafe;
    }

    private void processAutoPlaceTick() {
        placementSearch.discardInvalidContinuation();
        if (!scriptedUse) {
            pendingPlacement = null;
            restoreUseToAutomationState();
            return;
        }
        int tick = mc.thePlayer.ticksExisted;
        if (lastPlacementAttemptTick == tick || totalAcceptedC08.get() > c08AtTickBoundary
                || hasOutstandingPlacement()) {
            suppressUse();
            return;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (!ItemUtil.isBlock(stack) || !BlockUtil.isReplaceable(new BlockPos(
                mc.thePlayer.posX, MathHelper.floor_double(mc.thePlayer.posY) - 1,
                mc.thePlayer.posZ))) {
            pendingPlacement = null;
            restoreUseToAutomationState();
            return;
        }
        pendingPlacement = placementSearch.resolveCandidateWithOffCursorSilentPitch(mc.thePlayer,
                scriptedRotationYaw, scriptedRotationPitch, stack,
                System.currentTimeMillis() + 8L);
        if (pendingPlacement == null) {
            restoreUseToAutomationState();
            return;
        }
        suppressUse();
        lastPlacementAttemptTick = tick;
        if (attemptPlacement(stack, pendingPlacement, tick)) {
            return;
        }
        if (totalAcceptedC08.get() > c08AtTickBoundary || hasOutstandingPlacement()) {
            return;
        }
        retryPlacement(stack, tick);
    }

    private void retryPlacement(ItemStack stack, int tick) {
        TellyBridgePlacementSearch.Candidate failed = pendingPlacement;
        if (failed != null) {
            placementSearch.markRejected(failed.target, tick);
        }
        if (totalAcceptedC08.get() > c08AtTickBoundary
                || hasOutstandingPlacement()) {
            return;
        }
        pendingPlacement = placementSearch.findBelowPlacement(mc.thePlayer,
                scriptedRotationYaw, scriptedRotationPitch, stack,
                System.currentTimeMillis() + 4L);
        if (pendingPlacement != null) {
            attemptPlacement(stack, pendingPlacement, tick);
        }
    }

    private boolean attemptPlacement(ItemStack stack,
                                     TellyBridgePlacementSearch.Candidate candidate,
                                     int tick) {
        if (candidate == null || totalAcceptedC08.get() > c08AtTickBoundary
                || hasOutstandingPlacement() || !BlockUtil.isReplaceable(candidate.target)) {
            return false;
        }
        submittedPlacements.offer(new SubmittedPlacement(candidate, tick, runtimeGeneration));
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        boolean placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                candidate.support, candidate.face, candidate.hitVec);
        if (placed) {
            mc.thePlayer.swingItem();
        }
        return true;
    }

    private void refreshAdaptivePlacementAim(long now) {
        if (!firstTellyPlacementPending || pendingPlacement == null) {
            return;
        }
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double dx = pendingPlacement.hitVec.xCoord - eyes.xCoord;
        double dy = pendingPlacement.hitVec.yCoord - eyes.yCoord;
        double dz = pendingPlacement.hitVec.zCoord - eyes.zCoord;
        adaptiveAimYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        adaptiveAimPitch = MathHelper.clamp_float((float) -Math.toDegrees(
                Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -89.0F, 89.0F);
        adaptiveAimUpdatedAt = now;
        adaptiveAimValid = true;
    }

    private boolean detectManualCameraTakeover() {
        if (!running || setupTick >= 0 || System.currentTimeMillis() < takeoverDetectionAt) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (!takeoverCameraValid) {
            takeoverCameraValid = true;
            takeoverCameraYaw = mc.thePlayer.rotationYaw;
            takeoverCameraPitch = mc.thePlayer.rotationPitch;
            takeoverLastFrameAt = now;
            takeoverAccumulated = 0.0F;
            return false;
        }
        double yawInput = Math.abs(MathHelper.wrapAngleTo180_float(
                mc.thePlayer.rotationYaw - takeoverCameraYaw));
        double pitchInput = Math.abs(mc.thePlayer.rotationPitch - takeoverCameraPitch);
        double noiseFloor = rotationGcd() * 0.45D;
        long elapsed = Math.max(0L, now - takeoverLastFrameAt);
        takeoverLastFrameAt = now;
        takeoverAccumulated = Math.max(0.0F, takeoverAccumulated - elapsed * 0.045F);
        if (yawInput > noiseFloor || pitchInput > noiseFloor) {
            takeoverAccumulated += yawInput + pitchInput;
        }
        takeoverCameraYaw = mc.thePlayer.rotationYaw;
        takeoverCameraPitch = mc.thePlayer.rotationPitch;
        if (takeoverAccumulated >= 25.0F) {
            stopAutomation();
            return true;
        }
        return false;
    }

    private void handleAutoSwap() {
        if (!Boolean.TRUE.equals(autoSwap.getValue())) {
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        int heldCount = ItemUtil.isBlock(held) ? held.stackSize : 0;
        if (heldCount > 5) {
            return;
        }
        int bestSlot = -1;
        int bestSize = heldCount;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (ItemUtil.isBlock(stack) && stack.stackSize > bestSize) {
                bestSlot = slot;
                bestSize = stack.stackSize;
            }
        }
        if (bestSlot >= 0 && bestSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = bestSlot;
            MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        }
    }

    private boolean isStraightTarget(BlockPos target) {
        if (target == null || activationAnchor == null) {
            return true;
        }
        int lane = travelX != 0 ? target.getZ() : target.getX();
        int anchorLane = travelX != 0 ? activationAnchor.getZ() : activationAnchor.getX();
        int progress = target.getX() * travelX + target.getZ() * travelZ;
        int start = activationAnchor.getX() * travelX + activationAnchor.getZ() * travelZ;
        return lane == anchorLane && progress >= start;
    }

    private boolean activationPromptReady() {
        return activatePromptAt != 0L
                && System.currentTimeMillis() - activatePromptAt >= PROMPT_READY_MS;
    }

    private boolean activationSuppressUse() {
        return activatePromptAt != 0L
                && System.currentTimeMillis() - activatePromptAt >= PROMPT_SUPPRESS_USE_MS;
    }

    private boolean isDropProtected() {
        return running || activatePromptAt != 0L;
    }

    private void clearActivationPrompt() {
        rememberActivationPromptColor();
        activatePromptAt = 0L;
        promptBrokeAt = 0L;
        activationAnchor = null;
        activationFace = null;
    }

    private void stopAutomation() {
        resetRuntime();
        armed = selected();
    }

    private void resetRuntime() {
        running = false;
        armed = false;
        clearActivationPrompt();
        setupTick = 0;
        cyclePhase = 19;
        stagedForward = 0.0F;
        stagedStrafe = 0.0F;
        stagedJump = false;
        stagedSprint = false;
        baseYaw = 0.0F;
        scriptedRotationYaw = 0.0F;
        scriptedRotationPitch = 0.0F;
        antiSwayYawOffset = 0.0F;
        antiSwayTapUsed = false;
        firstTellyPlacementPending = false;
        adaptiveAimValid = false;
        takeoverDetectionAt = 0L;
        takeoverCameraValid = false;
        takeoverCameraYaw = 0.0F;
        takeoverCameraPitch = 0.0F;
        takeoverAccumulated = 0.0F;
        freezeLastTickAt = 0L;
        lastAdvancedInputTick = Integer.MIN_VALUE;
        lastPlacementAttemptTick = Integer.MIN_VALUE;
        lastSuccessfulPlaceTick = Integer.MIN_VALUE;
        forceSuppressTick = Integer.MIN_VALUE;
        runtimeGeneration++;
        totalAcceptedC08.set(0L);
        c08AtTickBoundary = 0L;
        promptAlpha = 0.0F;
        promptFadeLastAt = 0L;
        promptFadeRgb = 0xFF5555;
        activationSucceededAt = 0L;
        persistentDisplayProgress = 0.0F;
        visualProgress = 0.0F;
        hudPresence = 0.0F;
        hudStateBlend = 0.0F;
        hudAnimationLastAt = 0L;
        hudVisualState = "";
        submittedPlacements.clear();
        placementWriteResults.clear();
        scriptedUse = false;
        useSuppressed = false;
        pendingPlacement = null;
        acceptedPlacements.clear();
        placementSearch.reset();
        VisualRotationState.clearSource(ROTATION_SOURCE);
        restoreUseToAutomationState();
        setAttackPressed(physicalAttackDown());
        if (mc.thePlayer != null) {
            mc.thePlayer.setSprinting(false);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int withAlpha(int color, float alpha) {
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round(alpha)));
        return color & 0xFFFFFF | resolvedAlpha << 24;
    }

    private SubmittedPlacement claimSubmittedPlacement(C08PacketPlayerBlockPlacement packet) {
        if (packet == null || packet.getPlacedBlockDirection() < 0
                || packet.getPlacedBlockDirection() >= EnumFacing.values().length) {
            return null;
        }
        BlockPos support = packet.getPosition();
        EnumFacing face = EnumFacing.getFront(packet.getPlacedBlockDirection());
        for (SubmittedPlacement submitted : submittedPlacements) {
            TellyBridgePlacementSearch.Candidate candidate = submitted.candidate;
            if (submitted.generation == runtimeGeneration && candidate != null
                    && candidate.support.equals(support) && candidate.face == face
                    && submittedPlacements.remove(submitted)) {
                return submitted;
            }
        }
        return null;
    }

    private void drainPlacementWriteResults() {
        PlacementWriteResult result;
        while ((result = placementWriteResults.poll()) != null) {
            AcceptedPlacement accepted = result.accepted;
            if (accepted == null || accepted.generation != runtimeGeneration) {
                continue;
            }
            if (result.success) {
                placementSearch.recordAccepted(accepted.candidate);
                lastSuccessfulPlaceTick = accepted.tick;
                forceSuppressTick = accepted.tick;
                firstTellyPlacementPending = false;
                adaptiveAimValid = false;
            } else {
                placementSearch.markRejected(accepted.candidate.target, currentPlayerTick());
            }
        }
    }

    private void pruneSubmittedPlacements(int tick) {
        for (SubmittedPlacement submitted : submittedPlacements) {
            if (submitted.generation != runtimeGeneration
                    || tick - submitted.tick > SUBMITTED_PLACEMENT_TTL_TICKS) {
                submittedPlacements.remove(submitted);
            }
        }
    }

    private boolean hasOutstandingPlacement() {
        return !submittedPlacements.isEmpty() || !acceptedPlacements.isEmpty();
    }

    private int currentPlayerTick() {
        return mc.thePlayer == null ? Integer.MIN_VALUE : mc.thePlayer.ticksExisted;
    }

    private boolean selected() {
        return mode.getValue() == BridgeAssistBridgeModeStateMachine.Mode.TellyBridge;
    }

    private boolean validGame() {
        return mc.thePlayer != null && mc.theWorld != null && mc.playerController != null
                && mc.currentScreen == null && !mc.thePlayer.capabilities.isFlying;
    }

    private float rotationGcd() {
        if (mc.gameSettings == null) {
            return 0.03404715F;
        }
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return sensitivity * sensitivity * sensitivity * 8.0F * 0.15F;
    }

    private void suppressUse() {
        setUsePressed(false);
        useSuppressed = true;
    }

    private void restoreUseToAutomationState() {
        setUsePressed(running ? scriptedUse : physicalUseDown());
        useSuppressed = false;
    }

    private boolean physicalUseDown() {
        return mc.gameSettings != null
                && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode());
    }

    private boolean physicalAttackDown() {
        return mc.gameSettings != null
                && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
    }

    private void setUsePressed(boolean pressed) {
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), pressed);
        }
    }

    private void setAttackPressed(boolean pressed) {
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), pressed);
        }
    }
}
