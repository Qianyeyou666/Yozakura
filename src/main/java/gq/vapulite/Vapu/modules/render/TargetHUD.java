package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.modules.combat.Backtrack;
import gq.vapulite.Vapu.modules.combat.KillAura;
import gq.vapulite.Vapu.utils.ColorUtils;
import gq.vapulite.Vapu.utils.HudDrag;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import gq.vapulite.font.CFontRenderer;
import gq.vapulite.font.FontLoaders;
import gq.vapulite.render.ShaderRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

public class TargetHUD extends Module {
    private static final int TEXT = 0xFFEAF0F6;
    private static final int MUTED = 0xFFA7B0BE;
    private static final int GLASS_SOFT = 0xFF101722;
    private static final int ACCENT = 0xFF79C9FF;
    private static final int ACCENT_ALT = 0xFF9D8CFF;
    private static final int HEALTH_LOW = 0xFFFF6D7A;
    private static final int ABSORB = 0xFFFFD166;
    private static final int BAR_RED = 0xFFFF222C;
    private static final int BAR_YELLOW = 0xFFFFC83D;

    private final Numbers<Double> xPosition = new Numbers<Double>("X", "X", -1.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> yPosition = new Numbers<Double>("Y", "Y", -1.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0, 0.65, 1.8, 0.05);
    private final Numbers<Double> xOffset = new Numbers<Double>("X Offset", "XOffset", 22.0, -260.0, 260.0, 1.0);
    private final Numbers<Double> yOffset = new Numbers<Double>("Y Offset", "YOffset", 28.0, -180.0, 180.0, 1.0);
    private final Option<Boolean> showAvatar = new Option<Boolean>("Avatar", "Avatar", true);
    private final Option<Boolean> showDistance = new Option<Boolean>("Distance", "Distance", true);
    private final Option<Boolean> auraTarget = new Option<Boolean>("Aura Target", "AuraTarget", true);

    private EntityLivingBase displayTarget;
    private float visibility;
    private float healthAnimation = 0.66f;
    private float damageAnimation = 0.66f;
    private float absorptionAnimation;
    private float hurtPulse;
    private float switchPulse;
    private int lastTargetId = -1;
    private long lastFrameMS = System.currentTimeMillis();
    private EntityLivingBase attackedTarget;
    private long attackedTargetUntil;

    public TargetHUD() {
        super("TargetHUD", Keyboard.KEY_NONE, ModuleType.Render, "Show target info when aiming at an entity");
        Chinese = "目标HUD";
        this.addValues(xPosition, yPosition, scale, xOffset, yOffset, showAvatar, showDistance,
                auraTarget);
    }

    @Override
    public void enable() {
        visibility = 0.0f;
        healthAnimation = 0.66f;
        damageAnimation = 0.66f;
        absorptionAnimation = 0.0f;
        hurtPulse = 0.0f;
        switchPulse = 0.0f;
        displayTarget = null;
        attackedTarget = null;
        attackedTargetUntil = 0L;
        lastTargetId = -1;
        lastFrameMS = System.currentTimeMillis();
    }

    @Override
    public void disable() {
        visibility = 0.0f;
        displayTarget = null;
        attackedTarget = null;
        attackedTargetUntil = 0L;
        lastTargetId = -1;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isInGame() || event.entityPlayer != mc.thePlayer) {
            return;
        }
        EntityLivingBase attacked = asTarget(event.target);
        if (attacked == null) {
            return;
        }
        attackedTarget = attacked;
        attackedTargetUntil = System.currentTimeMillis() + 1600L;
        displayTarget = attacked;
        switchPulse = Math.max(switchPulse, 0.72f);
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (!isInGame()) {
            return;
        }

        long now = System.currentTimeMillis();
        float factor = animationFactor(now);
        boolean editMode = HudDrag.isEditMode();
        EntityLivingBase target = mc.currentScreen == null ? resolveTarget() : null;

        if (target != null) {
            if (target.getEntityId() != lastTargetId) {
                switchPulse = 1.0f;
                healthAnimation = healthRatio(target);
                damageAnimation = healthRatio(target);
                absorptionAnimation = absorptionRatio(target);
                lastTargetId = target.getEntityId();
            }
            displayTarget = target;
        } else if (!editMode) {
            lastTargetId = -1;
        }

        float wanted = target == null && !editMode ? 0.0f : 1.0f;
        visibility += (wanted - visibility) * factor;
        if (visibility <= 0.02f && target == null && !editMode) {
            displayTarget = null;
            return;
        }

        float targetHealth = displayTarget == null ? 0.66f : healthRatio(displayTarget);
        float targetAbsorb = displayTarget == null ? 0.0f : absorptionRatio(displayTarget);
        healthAnimation += (targetHealth - healthAnimation) * factor;
        float damageFactor = targetHealth < damageAnimation ? Math.min(1.0f, factor * 0.38f)
                : Math.min(1.0f, factor * 1.1f);
        damageAnimation += (targetHealth - damageAnimation) * damageFactor;
        absorptionAnimation += (targetAbsorb - absorptionAnimation) * factor;
        hurtPulse += ((displayTarget != null ? displayTarget.hurtTime / 10.0f : 0.0f) - hurtPulse) * Math.min(1.0f, factor * 1.45f);
        switchPulse += (0.0f - switchPulse) * factor;

        ShaderRenderer.invalidateFrostedGlass();
        drawHud(new ScaledResolution(mc), displayTarget, visibility);
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
            if (direct != null) {
                return direct;
            }
        }

        EntityLivingBase recentAttack = recentAttackTarget();
        if (recentAttack != null) {
            return recentAttack;
        }

        EntityLivingBase backtrack = Backtrack.getAimedTarget();
        if (backtrack != null) {
            return backtrack;
        }
        return null;
    }

    private EntityLivingBase asTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer) {
            return null;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        if (living.isDead || living.deathTime > 0 || living.getHealth() <= 0.0f) {
            return null;
        }
        return living;
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

    private void drawHud(ScaledResolution sr, EntityLivingBase target, float alpha) {
        alpha = clamp01(alpha);
        float width = 172.0f;
        float height = 64.0f;
        float uiScale = Math.max(0.1f, scale.getValue().floatValue());
        float defaultX = sr.getScaledWidth() / 2.0f + xOffset.getValue().floatValue();
        float defaultY = sr.getScaledHeight() / 2.0f + yOffset.getValue().floatValue();
        float[] pos = HudDrag.update("target_hud", xPosition, yPosition, scale, defaultX, defaultY,
                width * uiScale, height * uiScale, sr);
        float x = pos[0];
        float y = pos[1] + (HudDrag.isEditMode() ? 0.0f : (1.0f - alpha) * 7.0f);
        float avatar = Boolean.TRUE.equals(showAvatar.getValue()) ? 40.0f : 0.0f;
        float avatarX = x + 7.0f;
        float avatarY = y + 8.0f;
        float textX = x + (avatar > 0.0f ? 53.0f : 10.0f);
        float right = x + width - 9.0f;
        float textW = right - textX;
        float hurt = clamp01(hurtPulse);
        float pulse = clamp01(switchPulse);
        int fillAlpha = Math.round((162.0f + hurt * 22.0f) * alpha);
        int borderAlpha = Math.round((42.0f + pulse * 34.0f + hurt * 42.0f) * alpha);
        int accent = hurt > 0.04f ? ColorUtils.interpolate(ACCENT, HEALTH_LOW, hurt) : ACCENT;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0f);
            GlStateManager.scale(uiScale, uiScale, 1.0f);
            GlStateManager.translate(-x, -y, 0.0f);

            RenderUtil.drawSoftShadow(x, y, x + width, y + height, 5.0f,
                    withAlpha(0xFF000000, Math.round((86.0f + hurt * 38.0f) * alpha)), 8, 3.2f);
            if (hurt > 0.03f) {
                RenderUtil.drawSoftShadow(x - 1.0f, y - 1.0f, x + width + 1.0f, y + height + 1.0f,
                        8.0f, withAlpha(HEALTH_LOW, Math.round(70.0f * hurt * alpha)), 5, 3.0f);
            }
            RenderUtil.drawFrostedGlassRect(x, y, x + width, y + height, 5.0f, 0.75f,
                    withAlpha(0xFF050608, fillAlpha), withAlpha(0xFF1D2027, borderAlpha));
            RenderUtil.drawHorizontalGradientRect(x + 1.0f, y + 1.0f, x + width - 1.0f, y + 16.0f,
                    withAlpha(0xFFFFFFFF, Math.round(18.0f * alpha)),
                    withAlpha(0xFF11151C, Math.round(8.0f * alpha)));

            if (avatar > 0.0f) {
                drawAvatar(target, avatarX, avatarY, avatar, alpha, accent);
                resetTextRenderState();
            }

            drawHeader(target, textX, y + 7.0f, textW, alpha);
            drawStatBadges(target, textX, y + 24.0f, alpha);
            drawOutcomeText(target, textX, y + 43.0f, textW, alpha);
            drawHealthBar(target, x + 8.0f, y + height - 6.0f, width - 16.0f, 3.2f, alpha);
        } finally {
            GlStateManager.popMatrix();
        }
        HudDrag.drawHint("target_hud", x, y, width * uiScale, height * uiScale, 8.0f * uiScale);
    }

    private void drawHeader(EntityLivingBase target, float x, float y, float width, float alpha) {
        String name = target == null ? "Target HUD" : target.getName();
        String distance = Boolean.TRUE.equals(showDistance.getValue()) ? buildDistance(target) : "";
        float distanceW = FontLoaders.C12.getStringWidth(distance);
        FontLoaders.C18.drawString(trim(name, FontLoaders.C18, width - distanceW - 8.0f), x, y,
                withAlpha(TEXT, Math.round(252.0f * alpha)));
        if (distance.length() > 0) {
            FontLoaders.C12.drawString(distance, x + width - distanceW, y + 2.0f,
                    withAlpha(MUTED, Math.round(218.0f * alpha)));
        }
    }

    private void drawStatBadges(EntityLivingBase target, float x, float y, float alpha) {
        int armorValue = target == null ? 0 : target.getTotalArmorValue();
        float targetHealth = target == null ? 0.0f : Math.max(0.0f, target.getHealth());
        float targetMaxHealth = target == null ? 20.0f : Math.max(1.0f, target.getMaxHealth());
        String armor = String.valueOf(armorValue);
        String health = String.valueOf(Math.round(targetHealth));
        String percent = Math.round(clamp01(healthAnimation) * 100.0f) + "%";
        drawBadge(armor, x + 8.5f, y + 8.5f, 8.2f, 0xFFFFD34D,
                clamp01(armorValue / 20.0f), alpha);
        drawBadge(health, x + 29.5f, y + 8.5f, 8.2f, 0xFFFF4A50,
                clamp01(targetHealth / targetMaxHealth), alpha);
        drawBadge(percent, x + 55.0f, y + 8.5f, 10.0f, 0xFFE9EDF4,
                clamp01(healthAnimation), alpha);
    }

    private void drawBadge(String text, float centerX, float centerY, float radius, int accent,
                           float progress, float alpha) {
        RenderUtil.drawCircleBadge(centerX, centerY, radius, 1.35f, progress,
                withAlpha(0xFF07080B, Math.round(188.0f * alpha)),
                withAlpha(0xFF29313A, Math.round(112.0f * alpha)),
                withAlpha(accent, Math.round(232.0f * alpha)));
        drawBadgeText(text, centerX, centerY, radius - 2.4f, accent, alpha);
    }

    private void drawBadgeText(String text, float centerX, float centerY, float maxWidth, int accent, float alpha) {
        if (text == null || text.length() == 0) {
            return;
        }
        resetTextRenderState();
        float textWidth = Math.max(1.0f, FontLoaders.C12.getStringWidth(text));
        float textScale = Math.min(1.0f, maxWidth * 2.0f / textWidth);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(centerX, centerY, 0.0f);
            GlStateManager.scale(textScale, textScale, 1.0f);
            FontLoaders.C12.drawString(text, -FontLoaders.C12.getStringWidth(text) / 2.0f,
                    -FontLoaders.C12.getHeight() / 2.0f + 2.0f,
                    withAlpha(accent, Math.round(242.0f * alpha)));
        } finally {
            GlStateManager.popMatrix();
            resetTextRenderState();
        }
    }

    private void drawOutcomeText(EntityLivingBase target, float x, float y, float width, float alpha) {
        String outcome = target == null ? "Waiting" : fightOutcome(target);
        FontLoaders.C18.drawString(trim(outcome, FontLoaders.C18, width), x, y,
                withAlpha(TEXT, Math.round(238.0f * alpha)));
    }

    private void drawHealthBar(EntityLivingBase target, float x, float y, float width, float height,
                               float alpha) {
        float health = clamp01(healthAnimation);
        float delayed = clamp01(Math.max(health, damageAnimation));
        float absorb = clamp01(absorptionAnimation);
        RenderUtil.drawRoundedRect(x, y, x + width, y + height, 1.6f,
                withAlpha(0xFF1B0E12, Math.round(198.0f * alpha)));
        if (delayed > health + 0.002f) {
            float delayedW = width * delayed;
            RenderUtil.drawRoundedRect(x, y, x + delayedW, y + height, Math.min(1.6f, delayedW / 2.0f),
                    withAlpha(0xFFFF5A36, Math.round(112.0f * alpha)));
        }
        float fillW = width * health;
        if (fillW > 0.5f) {
            RenderUtil.drawHorizontalGradientRect(x, y, x + fillW, y + height,
                    withAlpha(BAR_RED, Math.round(245.0f * alpha)),
                    withAlpha(BAR_YELLOW, Math.round(245.0f * alpha)));
        }
        if (target != null && absorb > 0.001f && fillW < width - 0.5f) {
            float absorbW = Math.min(width - fillW, width * absorb);
            RenderUtil.drawRoundedRect(x + fillW, y, x + fillW + absorbW, y + height,
                    Math.min(1.6f, absorbW / 2.0f), withAlpha(ABSORB, Math.round(210.0f * alpha)));
        }
    }

    private void drawAvatar(EntityLivingBase target, float x, float y, float size, float alpha, int accent) {
        RenderUtil.drawSoftShadow(x, y, x + size, y + size, 5.0f,
                withAlpha(0xFF000000, Math.round(62.0f * alpha)), 5, 2.2f);
        RenderUtil.drawFrostedGlassRect(x, y, x + size, y + size, 4.0f, 0.9f,
                withAlpha(GLASS_SOFT, Math.round(146.0f * alpha)), withAlpha(accent, Math.round(72.0f * alpha)));
        RenderUtil.drawHorizontalGradientRect(x + 2.0f, y + size - 3.0f, x + size - 2.0f, y + size - 2.0f,
                withAlpha(accent, Math.round(160.0f * alpha)),
                withAlpha(ACCENT_ALT, Math.round(100.0f * alpha)));

        if (target != null) {
            drawEntityPreview(target, x + size / 2.0f, y + size - 4.0f, size, alpha);
            return;
        }

        float centerX = x + size / 2.0f;
        float centerY = y + size / 2.0f;
        RenderUtil.drawCircle(centerX, centerY, 0, 360, size / 2.0f - 3.0f,
                withAlpha(accent, Math.round(42.0f * alpha)));
        RenderUtil.drawCircle(centerX, centerY, 0, 360, size / 2.0f - 8.0f,
                withAlpha(0xFF06090D, Math.round(104.0f * alpha)));
        String icon = targetIcon(target);
        FontLoaders.I26.drawString(icon, centerX - FontLoaders.I26.getStringWidth(icon) / 2.0f,
                centerY - FontLoaders.I26.getHeight() / 2.0f + 2.0f,
                withAlpha(accent, Math.round(230.0f * alpha)));
    }

    private void drawEntityPreview(EntityLivingBase target, float centerX, float bottomY, float size, float alpha) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
            int entityScale = Math.max(14, Math.round(size * 0.46f));
            GuiInventory.drawEntityOnScreen(Math.round(centerX), Math.round(bottomY), entityScale, 0.0f, 0.0f, target);
        } finally {
            GlStateManager.popMatrix();
            resetTextRenderState();
        }
    }

    private void resetTextRenderState() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private String buildDistance(EntityLivingBase target) {
        if (target == null || mc.thePlayer == null) {
            return "0.0m";
        }
        return formatOneDecimal(mc.thePlayer.getDistanceToEntity(target)) + "m";
    }

    private String fightOutcome(EntityLivingBase target) {
        if (target == null || mc.thePlayer == null) {
            return "Waiting";
        }
        float playerScore = mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()
                + mc.thePlayer.getTotalArmorValue() * 0.65f;
        float targetScore = target.getHealth() + target.getAbsorptionAmount()
                + target.getTotalArmorValue() * 0.65f;
        float diff = playerScore - targetScore;
        if (diff > 1.0f) {
            return "Winning";
        }
        if (diff < -1.0f) {
            return "Losing";
        }
        return "Trading";
    }

    private String targetIcon(EntityLivingBase target) {
        if (target == null) {
            return FontLoaders.ICON_FOCUS;
        }
        String simple = target.getClass().getSimpleName().toLowerCase(Locale.ENGLISH);
        if (simple.contains("creeper")) {
            return FontLoaders.ICON_BOMB;
        }
        if (simple.contains("zombie") || simple.contains("skeleton") || simple.contains("spider")
                || simple.contains("slime") || simple.contains("witch") || simple.contains("enderman")) {
            return FontLoaders.ICON_WARNING;
        }
        if (simple.contains("villager") || simple.contains("animal") || simple.contains("cow")
                || simple.contains("pig") || simple.contains("sheep") || simple.contains("horse")) {
            return FontLoaders.ICON_USER;
        }
        return FontLoaders.ICON_FOCUS;
    }

    private float healthRatio(EntityLivingBase target) {
        if (target == null) {
            return 0.66f;
        }
        return clamp01(target.getHealth() / Math.max(1.0f, target.getMaxHealth()));
    }

    private float absorptionRatio(EntityLivingBase target) {
        if (target == null) {
            return 0.0f;
        }
        return clamp01(target.getAbsorptionAmount() / Math.max(1.0f, target.getMaxHealth()));
    }

    private float animationFactor(long now) {
        float delta = Math.max(1.0f, Math.min(50.0f, now - lastFrameMS));
        lastFrameMS = now;
        return 1.0f - (float) Math.pow(0.001D, delta / 220.0D);
    }

    private String formatOneDecimal(float value) {
        float rounded = Math.round(value * 10.0f) / 10.0f;
        if (Math.abs(rounded - Math.round(rounded)) < 0.01f) {
            return String.valueOf(Math.round(rounded));
        }
        return String.valueOf(rounded);
    }

    private String trim(String text, CFontRenderer font, float maxWidth) {
        if (text == null) {
            return "";
        }
        if (maxWidth <= 0.0f) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        if (font.getStringWidth("...") > maxWidth) {
            return "";
        }
        String result = text;
        while (result.length() > 1 && font.getStringWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        String trimmed = result + "...";
        return font.getStringWidth(trimmed) <= maxWidth ? trimmed : "...";
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
