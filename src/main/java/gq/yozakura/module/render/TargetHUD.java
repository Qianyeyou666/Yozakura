package gq.yozakura.module.render;

import gq.yozakura.bridge.YozakuraEventBridge;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.event.bridge.AttackEvent;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.combat.Backtrack;
import gq.yozakura.module.combat.KillAura;
import gq.yozakura.module.combat.AntiBot;
import gq.yozakura.util.render.HudDrag;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

public class TargetHUD extends Module {
    private static final int NIGHT_BLOOM_PREVIEW_TARGET_ID = Integer.MIN_VALUE;
    private static final long RISE_TARGET_HOLD_MS = 1000L;
    private static final double AIM_TARGET_RANGE = 6.0D;
    private static final long RISE_ENTER_MS = 180L;
    private static final long RISE_EXIT_MS = 140L;
    private static final TargetHudStyle[] SELECTABLE_STYLES = new TargetHudStyle[]{
            TargetHudStyle.NYMPHILILA,
            TargetHudStyle.COOL,
            TargetHudStyle.RISE,
            TargetHudStyle.NIGHT_BLOOM
    };

    private final Mode<TargetHudStyle> style = new Mode<TargetHudStyle>("Style", "Style",
            SELECTABLE_STYLES, TargetHudStyle.NYMPHILILA);
    private final Numbers<Double> xPosition = new Numbers<Double>("X", "X", -1.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> yPosition = new Numbers<Double>("Y", "Y", -1.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0, 0.65, 1.8, 0.05);
    private final Numbers<Double> xOffset = new Numbers<Double>("X Offset", "XOffset", 22.0, -260.0, 260.0, 1.0);
    private final Numbers<Double> yOffset = new Numbers<Double>("Y Offset", "YOffset", 28.0, -180.0, 180.0, 1.0);
    private final Option<Boolean> showAvatar = new Option<Boolean>("Avatar", "Avatar", true);
    private final Option<Boolean> auraTarget = new Option<Boolean>("Aura Target", "AuraTarget", true);
    private final Option<Boolean> follow = new Option<Boolean>("Follow", "Follow", false);
    private final Option<Boolean> frostedGlass = new Option<Boolean>("Frosted Glass", "FrostedGlass", true);
    private final Numbers<Double> nymphBackgroundAlpha = new Numbers<Double>(
            "Background Alpha", "NymphBackgroundAlpha", 120.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> coolBackgroundAlpha = new Numbers<Double>(
            "Cool Background Alpha", "CoolBackgroundAlpha", 120.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> coolCornerRadius = new Numbers<Double>(
            "Cool Corner Radius", "CoolCornerRadius", 8.0, 0.0, 16.0, 0.5);
    private final Mode<RiseTargetHudBackground> riseBackground = new Mode<RiseTargetHudBackground>(
            "Background", "RiseBackground", RiseTargetHudBackground.values(), RiseTargetHudBackground.GLASS);
    private final Option<Boolean> riseParticles = new Option<Boolean>("Particles", "RiseParticles", true);

    private final TargetHudMotion nightBloomMotion = new TargetHudMotion();
    private final NightBloomTargetHudRenderer nightBloomRenderer = new NightBloomTargetHudRenderer();
    private final AppleTargetHudMotion appleMotion = new AppleTargetHudMotion();
    private final AppleTargetHudRenderer appleRenderer = new AppleTargetHudRenderer();
    private final RiseTargetHudRenderer riseRenderer = new RiseTargetHudRenderer();
    private final NymphTargetHudMotion nymphMotion = new NymphTargetHudMotion();
    private final NymphTargetHudRenderer nymphRenderer = new NymphTargetHudRenderer();
    private final NymphTargetHudMotion coolMotion = new NymphTargetHudMotion();
    private final CoolTargetHudHurtMotion coolHurtMotion = new CoolTargetHudHurtMotion();
    private final CoolTargetHudNumberMotion coolNumberMotion = new CoolTargetHudNumberMotion();
    private final CoolTargetHudRenderer coolRenderer = new CoolTargetHudRenderer();
    private final TargetHudFollowProjection followProjection = new TargetHudFollowProjection();
    private final RiseTargetHudAnimation riseOpeningAnimation = new RiseTargetHudAnimation(
            RiseTargetHudAnimation.Easing.EASE_OUT_CUBIC, 180L);
    private final RiseTargetHudAnimation riseHealthAnimation = new RiseTargetHudAnimation(
            RiseTargetHudAnimation.Easing.EASE_OUT_SINE, 500L);

    private EntityLivingBase displayTarget;
    private EntityLivingBase attackedTarget;
    private long attackedTargetUntil;
    private long riseTargetSeenAt;
    private TargetHudStyle renderedStyle;

    private NightBloomTargetHudRenderer.Content nightBloomCurrent;
    private NightBloomTargetHudRenderer.Content nightBloomPrevious;
    private long lastNightBloomFrameMS = System.currentTimeMillis();
    private AppleTargetHudRenderer.Content appleCurrent;
    private AppleTargetHudRenderer.Content applePrevious;
    private long lastAppleFrameMS = System.currentTimeMillis();
    private long lastNymphFrameMS = System.currentTimeMillis();
    private long lastCoolFrameMS = System.currentTimeMillis();

    public TargetHUD() {
        super("TargetHUD", Keyboard.KEY_NONE, ModuleType.Render, "Show target info when aiming at an entity");
        Chinese = "目标信息";
        xPosition.visibleWhen(() -> false);
        yPosition.visibleWhen(() -> false);
        scale.visibleWhen(() -> false);
        xOffset.visibleWhen(() -> false);
        yOffset.visibleWhen(() -> false);
        frostedGlass.visibleWhen(() -> false);
        nymphBackgroundAlpha.visibleWhen(() -> getSelectedStyle() == TargetHudStyle.NYMPHILILA);
        coolBackgroundAlpha.visibleWhen(() -> getSelectedStyle() == TargetHudStyle.COOL);
        coolCornerRadius.visibleWhen(() -> getSelectedStyle() == TargetHudStyle.COOL);
        riseBackground.visibleWhen(() -> isRiseStyle(getSelectedStyle()));
        riseParticles.visibleWhen(() -> isRiseStyle(getSelectedStyle()));
        this.addValues(xPosition, yPosition, scale, xOffset, yOffset, showAvatar, auraTarget, follow,
                frostedGlass, nymphBackgroundAlpha, coolBackgroundAlpha, coolCornerRadius,
                riseBackground, riseParticles, style);
    }

    @Override
    public void enable() {
        displayTarget = null;
        attackedTarget = null;
        attackedTargetUntil = 0L;
        renderedStyle = null;
        resetNightBloomState();
        resetAppleState();
        resetNymphState();
        resetCoolState();
        resetRiseState();
        followProjection.clear();
    }

    @Override
    public void disable() {
        HudDrag.unregisterDocked("target_hud");
        displayTarget = null;
        attackedTarget = null;
        attackedTargetUntil = 0L;
        renderedStyle = null;
        resetNightBloomState();
        resetAppleState();
        resetNymphState();
        resetCoolState();
        resetRiseState();
        followProjection.clear();
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isInGame() || event.entityPlayer != mc.thePlayer) {
            return;
        }
        rememberRiseAttackTarget(event.target);
    }

    @EventTarget
    public void onClientAttack(AttackEvent event) {
        if (!isInGame() || event == null) {
            return;
        }
        rememberRiseAttackTarget(event.getTarget());
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        renderOverlay(event.getPartialTicks());
    }

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent event) {
        if (!isInGame() || !Boolean.TRUE.equals(follow.getValue()) || HudDrag.isEditMode()) {
            followProjection.clear();
            return;
        }
        EntityLivingBase target = resolveTarget();
        if (target == null) {
            target = displayTarget;
        }
        followProjection.capture(target, event.partialTicks, mc);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (YozakuraEventBridge.hasRenderedOverlayThisFrame()) {
            return;
        }
        boolean ownsEffectsFrame = !RenderServices.shadows().isFrameOpen()
                && !RenderServices.glow().isFrameOpen();
        if (!ownsEffectsFrame) {
            renderOverlay(1.0F);
            return;
        }
        RenderServices.beginHudEffectsFrame();
        try {
            renderOverlay(1.0F);
        } finally {
            RenderServices.flushHudEffectsFrame();
        }
    }

    private void renderOverlay(float partialTicks) {
        if (!isInGame()) {
            resetNightBloomState();
            resetAppleState();
            resetNymphState();
            resetCoolState();
            resetRiseState();
            return;
        }

        TargetHudStyle selectedStyle = getSelectedStyle();
        if (renderedStyle != selectedStyle) {
            resetNightBloomState();
            resetAppleState();
            resetNymphState();
            resetCoolState();
            resetRiseState();
            renderedStyle = selectedStyle;
        }
        if (selectedStyle == TargetHudStyle.NIGHT_BLOOM) {
            renderNightBloomOverlay();
            return;
        }
        if (selectedStyle == TargetHudStyle.NYMPHILILA) {
            renderNymphOverlay(new ScaledResolution(mc));
            return;
        }
        if (selectedStyle == TargetHudStyle.COOL) {
            renderCoolOverlay(new ScaledResolution(mc));
            return;
        }
        renderRiseOverlay(new ScaledResolution(mc), partialTicks);
    }

    private void renderNymphOverlay(ScaledResolution resolution) {
        long now = System.currentTimeMillis();
        float deltaSeconds = Math.max(0.0F, Math.min(0.1F,
                (now - lastNymphFrameMS) / 1000.0F));
        lastNymphFrameMS = now;
        boolean editMode = HudDrag.isEditMode();
        EntityLivingBase resolved = editMode ? mc.thePlayer : resolveTarget();
        if (resolved != null) {
            boolean switched = displayTarget == null
                    || displayTarget.getEntityId() != resolved.getEntityId();
            displayTarget = resolved;
            nymphMotion.setVisible(true, now);
            if (switched) {
                nymphMotion.snapHealth(healthRatio(resolved));
            }
        } else {
            nymphMotion.setVisible(false, now);
        }

        nymphMotion.updateHealth(resolved == null ? 0.0F : healthRatio(resolved), deltaSeconds);
        NymphTargetHudMotion.Snapshot snapshot = nymphMotion.snapshot(now);
        if (!snapshot.isRetained() || displayTarget == null) {
            if (!snapshot.isRetained()) {
                displayTarget = null;
            }
            return;
        }

        float uiScale = Math.max(0.1F, scale.getValue().floatValue());
        NymphTargetHudRenderer.Layout layout = nymphRenderer.measure(displayTarget);
        float defaultX = resolution.getScaledWidth() / 2.0F + xOffset.getValue().floatValue();
        float defaultY = resolution.getScaledHeight() / 2.0F + yOffset.getValue().floatValue();
        float[] position = resolveHudPosition(displayTarget, layout.width * uiScale,
                layout.height * uiScale, defaultX, defaultY, resolution, false,
                NymphTargetHudLayout.RADIUS * uiScale);
        if (position == null) {
            return;
        }
        nymphRenderer.draw(displayTarget, position[0], position[1], uiScale, snapshot,
                nymphMotion.getHealth(), nymphBackgroundAlpha.getValue().intValue(),
                Boolean.TRUE.equals(showAvatar.getValue()));
        HudDrag.drawHint("target_hud", position[0], position[1],
                layout.width * uiScale, layout.height * uiScale,
                NymphTargetHudLayout.RADIUS * uiScale);
        HudDrag.handleScroll("target_hud", scale, position[0], position[1],
                layout.width * uiScale, layout.height * uiScale, 0.65F, 1.8F);
    }

    private void renderCoolOverlay(ScaledResolution resolution) {
        long now = System.currentTimeMillis();
        float deltaSeconds = Math.max(0.0F, Math.min(0.1F,
                (now - lastCoolFrameMS) / 1000.0F));
        lastCoolFrameMS = now;
        boolean editMode = HudDrag.isEditMode();
        EntityLivingBase resolved = editMode ? mc.thePlayer : resolveTarget();
        if (resolved != null) {
            boolean switched = displayTarget == null
                    || displayTarget.getEntityId() != resolved.getEntityId();
            displayTarget = resolved;
            coolMotion.setVisible(true, now);
            if (switched) {
                coolMotion.snapHealth(healthRatio(resolved));
                coolNumberMotion.snap(resolved.getEntityId(), resolved.getHealth());
            }
        } else {
            coolMotion.setVisible(false, now);
        }

        coolMotion.updateHealth(resolved == null ? 0.0F : healthRatio(resolved), deltaSeconds);
        NymphTargetHudMotion.Snapshot snapshot = coolMotion.snapshot(now);
        if (!snapshot.isRetained() || displayTarget == null) {
            if (!snapshot.isRetained()) {
                displayTarget = null;
            }
            return;
        }

        float uiScale = Math.max(0.1F, scale.getValue().floatValue());
        CoolTargetHudRenderer.Layout layout = coolRenderer.measure(displayTarget);
        float defaultX = resolution.getScaledWidth() / 2.0F + xOffset.getValue().floatValue();
        float defaultY = resolution.getScaledHeight() / 2.0F + yOffset.getValue().floatValue();
        float[] position = resolveHudPosition(displayTarget, layout.width * uiScale,
                layout.height * uiScale, defaultX, defaultY, resolution, false,
                coolCornerRadius.getValue().floatValue() * uiScale);
        if (position == null) {
            return;
        }
        CoolTargetHudHurtMotion.Snapshot hurt = coolHurtMotion.update(displayTarget, now);
        CoolTargetHudNumberMotion.Snapshot numberMotion = coolNumberMotion.update(
                displayTarget.getEntityId(), displayTarget.getHealth(), deltaSeconds);
        coolRenderer.draw(displayTarget, position[0], position[1], uiScale, snapshot,
                coolMotion.getHealth(), coolBackgroundAlpha.getValue().intValue(),
                coolCornerRadius.getValue().floatValue(),
                hurt, numberMotion,
                Boolean.TRUE.equals(showAvatar.getValue()));
        HudDrag.drawHint("target_hud", position[0], position[1],
                layout.width * uiScale, layout.height * uiScale,
                coolCornerRadius.getValue().floatValue() * uiScale);
        HudDrag.handleScroll("target_hud", scale, position[0], position[1],
                layout.width * uiScale, layout.height * uiScale, 0.65F, 1.8F);
    }

    private void renderAppleOverlay(ScaledResolution resolution, float partialTicks) {
        long now = System.currentTimeMillis();
        float deltaSeconds = Math.max(0.0F, Math.min(0.05F, (now - lastAppleFrameMS) / 1000.0F));
        lastAppleFrameMS = now;
        boolean editMode = HudDrag.isEditMode();
        EntityLivingBase resolved = editMode ? mc.thePlayer : resolveTarget();
        if (resolved != null) {
            AppleTargetHudRenderer.Content next = appleContent(resolved);
            boolean switched = appleCurrent == null
                    || appleCurrent.getEntityId() != next.getEntityId()
                    || !appleMotion.isPresent();
            if (switched && appleCurrent != null
                    && appleCurrent.getEntityId() != next.getEntityId()) {
                applePrevious = appleCurrent;
            }
            appleCurrent = next;
            if (switched) {
                appleMotion.acquire(next.getEntityId(), next.getHealthRatio());
            }
            displayTarget = resolved;
            riseTargetSeenAt = now;
        }

        boolean inWorld = isRiseTargetInWorld(displayTarget);
        boolean timedOut = !editMode && (displayTarget == null || now - riseTargetSeenAt >= RISE_TARGET_HOLD_MS);
        boolean out = !editMode && (!inWorld || timedOut);
        if (out) {
            appleMotion.release();
        }

        float health = appleCurrent == null ? 0.0F : appleCurrent.getHealthRatio();
        boolean hurt = appleCurrent != null && appleCurrent.isHurt();
        appleMotion.update(deltaSeconds, health, hurt);
        if (!appleMotion.hasRetainedTarget()) {
            appleCurrent = null;
            applePrevious = null;
            return;
        }

        float uiScale = Math.max(0.1F, scale.getValue().floatValue());
        boolean avatar = Boolean.TRUE.equals(showAvatar.getValue());
        AppleTargetHudRenderer.Layout layout = appleRenderer.measure(displayTarget, uiScale, avatar);
        float defaultX = resolution.getScaledWidth() / 2.0F + xOffset.getValue().floatValue();
        float defaultY = resolution.getScaledHeight() / 2.0F + yOffset.getValue().floatValue();
        float[] position = resolveHudPosition(displayTarget, layout.width * uiScale,
                layout.height * uiScale, defaultX, defaultY, resolution, false,
                AppleTargetHudRenderer.RADIUS * uiScale);
        if (position == null) {
            return;
        }

        appleRenderer.draw(displayTarget, position[0], position[1], uiScale, appleMotion,
                appleCurrent, applePrevious, layout, avatar);
        HudDrag.drawHint("target_hud", position[0], position[1], layout.width * uiScale,
                layout.height * uiScale, AppleTargetHudRenderer.RADIUS * uiScale);
        HudDrag.handleScroll("target_hud", scale, position[0], position[1], layout.width * uiScale,
                layout.height * uiScale, 0.65F, 1.8F);
    }

    private void renderRiseOverlay(ScaledResolution resolution, float partialTicks) {
        long now = System.currentTimeMillis();
        boolean editMode = HudDrag.isEditMode();
        EntityLivingBase resolved = editMode ? mc.thePlayer : resolveTarget();
        boolean switchedTarget = false;
        if (resolved != null) {
            switchedTarget = displayTarget == null
                    || displayTarget.getEntityId() != resolved.getEntityId();
            displayTarget = resolved;
            riseTargetSeenAt = now;
            if (switchedTarget) {
                riseRenderer.onTargetChanged(resolved);
            }
        }

        boolean inWorld = isRiseTargetInWorld(displayTarget);
        boolean timedOut = !editMode && (displayTarget == null || now - riseTargetSeenAt >= RISE_TARGET_HOLD_MS);
        boolean out = !editMode && (!inWorld || timedOut);
        riseOpeningAnimation.setDuration(out ? RISE_EXIT_MS : RISE_ENTER_MS);
        riseOpeningAnimation.setEasing(RiseTargetHudAnimation.Easing.EASE_OUT_CUBIC);
        riseOpeningAnimation.run(out ? 0.0D : 1.0D);
        float openingScale = (float) riseOpeningAnimation.getValue();
        if (openingScale <= 0.001F) {
            if (out) {
                displayTarget = null;
            }
            return;
        }

        float uiScale = Math.max(0.1F, scale.getValue().floatValue());
        boolean avatar = Boolean.TRUE.equals(showAvatar.getValue());
        RiseTargetHudRenderer.Layout layout = riseRenderer.measure(displayTarget, uiScale, avatar);
        float defaultX = resolution.getScaledWidth() / 2.0F + xOffset.getValue().floatValue();
        float defaultY = resolution.getScaledHeight() / 2.0F + yOffset.getValue().floatValue();
        float[] position = resolveHudPosition(displayTarget, layout.width * uiScale,
                layout.height * uiScale, defaultX, defaultY, resolution, false,
                RiseTargetHudRenderer.RADIUS * uiScale);
        if (position == null) {
            return;
        }

        float health = displayTarget == null || !inWorld ? 0.0F : healthRatio(displayTarget);
        riseHealthAnimation.setEasing(RiseTargetHudAnimation.Easing.EASE_OUT_QUINT);
        riseHealthAnimation.setDuration(250L);
        if (switchedTarget) {
            riseHealthAnimation.snap(health * layout.healthBarWidth);
        }
        riseHealthAnimation.run(health * layout.healthBarWidth);
        riseRenderer.draw(displayTarget, position[0], position[1], uiScale, openingScale,
                (float) riseHealthAnimation.getValue(), layout, riseBackground.getValue(), avatar,
                Boolean.TRUE.equals(riseParticles.getValue()), partialTicks);
        HudDrag.drawHint("target_hud", position[0], position[1], layout.width * uiScale,
                layout.height * uiScale, RiseTargetHudRenderer.RADIUS * uiScale);
        HudDrag.handleScroll("target_hud", scale, position[0], position[1], layout.width * uiScale,
                layout.height * uiScale, 0.65F, 1.8F);
    }

    private void rememberRiseAttackTarget(Entity entity) {
        EntityLivingBase attacked = asTarget(entity);
        if (attacked == null) {
            return;
        }
        long now = System.currentTimeMillis();
        attackedTarget = attacked;
        attackedTargetUntil = now + RISE_TARGET_HOLD_MS;
        displayTarget = attacked;
        riseTargetSeenAt = now;
    }

    private boolean isRiseTargetInWorld(EntityLivingBase target) {
        return target != null && mc.theWorld != null && mc.theWorld.loadedEntityList.contains(target)
                && !target.isDead && target.deathTime <= 0;
    }

    private void resetRiseState() {
        riseOpeningAnimation.snap(0.0D);
        riseHealthAnimation.snap(0.0D);
        riseTargetSeenAt = 0L;
        riseRenderer.reset();
    }

    private TargetHudStyle getSelectedStyle() {
        TargetHudStyle selected = style.getValue();
        return selected == null || selected == TargetHudStyle.APPLE
                ? TargetHudStyle.NYMPHILILA : selected;
    }

    private static boolean isRiseStyle(TargetHudStyle style) {
        return style == TargetHudStyle.RISE;
    }

    private void renderNightBloomOverlay() {
        long now = System.currentTimeMillis();
        float deltaSeconds = Math.max(0.0F, Math.min(0.05F, (now - lastNightBloomFrameMS) / 1000.0F));
        lastNightBloomFrameMS = now;
        boolean editMode = HudDrag.isEditMode();
        EntityLivingBase target = mc.currentScreen == null ? resolveTarget() : null;

        if (target != null) {
            displayTarget = target;
            NightBloomTargetHudRenderer.Content next = nightBloomContent(target);
            if (nightBloomCurrent == null || next.getEntityId() != nightBloomCurrent.getEntityId()) {
                nightBloomPrevious = nightBloomCurrent;
                nightBloomCurrent = next;
            } else {
                nightBloomCurrent = next;
            }
            nightBloomMotion.acquire(next.getEntityId(), next.getHealthRatio());
        } else if (editMode) {
            NightBloomTargetHudRenderer.Content preview = nightBloomPreviewContent();
            if (nightBloomCurrent == null || preview.getEntityId() != nightBloomCurrent.getEntityId()) {
                nightBloomPrevious = nightBloomCurrent;
                nightBloomCurrent = preview;
            }
            nightBloomMotion.acquire(preview.getEntityId(), preview.getHealthRatio());
        } else {
            nightBloomMotion.release();
        }

        float targetHealth = nightBloomCurrent == null ? 0.0F : nightBloomCurrent.getHealthRatio();
        nightBloomMotion.update(deltaSeconds, targetHealth);
        if (nightBloomMotion.getPreviousTargetId() == TargetHudMotion.NO_TARGET) {
            nightBloomPrevious = null;
        }
        if (!nightBloomMotion.hasRetainedTarget()) {
            nightBloomCurrent = null;
            nightBloomPrevious = null;
            return;
        }
        drawNightBloomHud(new ScaledResolution(mc), editMode);
    }

    private void drawNightBloomHud(ScaledResolution resolution, boolean editMode) {
        float uiScale = Math.max(0.1F, scale.getValue().floatValue());
        float defaultX = resolution.getScaledWidth() / 2.0F + xOffset.getValue().floatValue();
        float defaultY = resolution.getScaledHeight() / 2.0F + yOffset.getValue().floatValue();
        float width = NightBloomTargetHudRenderer.WIDTH * uiScale;
        float height = NightBloomTargetHudRenderer.HEIGHT * uiScale;
        float[] position = resolveHudPosition(displayTarget, width, height, defaultX, defaultY,
                resolution, true, NightBloomHudLayout.PANEL_RADIUS * uiScale);
        if (position == null) {
            return;
        }

        nightBloomRenderer.draw(position[0], position[1], uiScale, nightBloomCurrent, nightBloomPrevious,
                nightBloomMotion, editMode, Boolean.TRUE.equals(showAvatar.getValue()));
        HudDrag.drawDockHint("target_hud", position[0], position[1], width, height,
                NightBloomHudLayout.PANEL_RADIUS * uiScale);
        HudDrag.handleScroll("target_hud", scale, position[0], position[1], width, height, 0.65F, 1.8F);
    }

    private NightBloomTargetHudRenderer.Content nightBloomContent(EntityLivingBase target) {
        return new NightBloomTargetHudRenderer.Content(target, target.getEntityId(), target.getName(),
                distanceText(target) + "  |  " + pingText(target), healthRatio(target), target.hurtTime > 0);
    }

    private NightBloomTargetHudRenderer.Content nightBloomPreviewContent() {
        return new NightBloomTargetHudRenderer.Content(null, NIGHT_BLOOM_PREVIEW_TARGET_ID,
                "Steve", "3.2m  |  --", 0.76F, false);
    }

    private String distanceText(EntityLivingBase target) {
        if (target == null || mc.thePlayer == null) {
            return "--";
        }
        return String.format(Locale.ROOT, "%.1fm", mc.thePlayer.getDistanceToEntity(target));
    }

    private String pingText(EntityLivingBase target) {
        if (target instanceof EntityPlayer && mc.getNetHandler() != null) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(((EntityPlayer) target).getUniqueID());
            if (info != null) {
                return Math.max(0, info.getResponseTime()) + "ms";
            }
        }
        return "--";
    }

    private EntityLivingBase resolveTarget() {
        if (Boolean.TRUE.equals(auraTarget.getValue())) {
            EntityLivingBase aura = asTarget(KillAura.target);
            if (aura != null) {
                return aura;
            }
        }
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            EntityLivingBase direct = asTarget(mc.objectMouseOver.entityHit);
            if (direct != null && mc.thePlayer.getDistanceSqToEntity(direct) <= AIM_TARGET_RANGE * AIM_TARGET_RANGE) {
                return direct;
            }
        }
        EntityLivingBase recentAttack = recentAttackTarget();
        return recentAttack != null ? recentAttack : asTarget(Backtrack.getAimedTarget());
    }

    private float[] resolveHudPosition(EntityLivingBase target, float width, float height,
                                       float defaultX, float defaultY, ScaledResolution resolution,
                                       boolean docked, float radius) {
        if (Boolean.TRUE.equals(follow.getValue()) && !HudDrag.isEditMode()) {
            TargetHudFollowProjection.Position position = followProjection.position(
                    target, width, height, resolution);
            return position == null ? null : new float[]{position.getX(), position.getY()};
        }
        if (docked) {
            return HudDrag.updateDocked("target_hud", xPosition, yPosition, scale,
                    defaultX, defaultY, width, height, radius, resolution);
        }
        return HudDrag.update("target_hud", xPosition, yPosition, scale,
                defaultX, defaultY, width, height, resolution);
    }

    private EntityLivingBase asTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer) {
            return null;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        return living.isDead || living.deathTime > 0 || living.getHealth() <= 0.0F
                || AntiBot.isServerBot(living) ? null : living;
    }

    private EntityLivingBase recentAttackTarget() {
        if (attackedTarget == null) {
            return null;
        }
        if (System.currentTimeMillis() > attackedTargetUntil) {
            attackedTarget = null;
            attackedTargetUntil = 0L;
            return null;
        }
        EntityLivingBase living = asTarget(attackedTarget);
        if (living == null) {
            attackedTarget = null;
            attackedTargetUntil = 0L;
        }
        return living;
    }

    private float healthRatio(EntityLivingBase target) {
        if (target == null) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F,
                target.getHealth() / Math.max(1.0F, target.getMaxHealth())));
    }

    private void resetNightBloomState() {
        nightBloomMotion.reset();
        nightBloomCurrent = null;
        nightBloomPrevious = null;
        lastNightBloomFrameMS = System.currentTimeMillis();
    }

    private AppleTargetHudRenderer.Content appleContent(EntityLivingBase target) {
        return new AppleTargetHudRenderer.Content(target, target.getEntityId(), target.getName(),
                distanceText(target) + "  ·  " + pingText(target), healthRatio(target),
                target.hurtTime > 0);
    }

    private void resetAppleState() {
        appleMotion.reset();
        appleCurrent = null;
        applePrevious = null;
        lastAppleFrameMS = System.currentTimeMillis();
    }

    private void resetNymphState() {
        nymphMotion.reset();
        lastNymphFrameMS = System.currentTimeMillis();
    }

    private void resetCoolState() {
        coolMotion.reset();
        coolHurtMotion.reset();
        coolNumberMotion.reset();
        lastCoolFrameMS = System.currentTimeMillis();
    }
}
