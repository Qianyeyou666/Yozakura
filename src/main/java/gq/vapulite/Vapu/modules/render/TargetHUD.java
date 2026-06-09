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
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public class TargetHUD extends Module {
    private static final int TEXT = 0xFFEAF0F6;
    private static final int MUTED = 0xFFA7B0BE;
    private static final int GLASS = 0xFF05070A;
    private static final int BORDER = 0xFF253243;
    private static final int ACCENT = 0xFF79C9FF;
    private static final int HEALTH_HIGH = 0xFF67D992;
    private static final int HEALTH_MID = 0xFFFFC75C;
    private static final int HEALTH_LOW = 0xFFFF6D7A;

    private final Numbers<Double> xPosition = new Numbers<Double>("X", "X", -1.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> yPosition = new Numbers<Double>("Y", "Y", -1.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0, 0.65, 1.8, 0.05);
    private final Numbers<Double> xOffset = new Numbers<Double>("X Offset", "XOffset", 20.0, -260.0, 260.0, 1.0);
    private final Numbers<Double> yOffset = new Numbers<Double>("Y Offset", "YOffset", 24.0, -180.0, 180.0, 1.0);
    private final Option<Boolean> showAvatar = new Option<Boolean>("Avatar", "Avatar", true);
    private final Option<Boolean> showDistance = new Option<Boolean>("Distance", "Distance", true);
    private final Option<Boolean> auraTarget = new Option<Boolean>("Aura Target", "AuraTarget", true);

    private EntityLivingBase displayTarget;
    private float visibility;
    private float healthAnimation;
    private long lastFrameMS = System.currentTimeMillis();

    public TargetHUD() {
        super("TargetHUD", Keyboard.KEY_NONE, ModuleType.Render, "Show target info when aiming at an entity");
        Chinese = "目标HUD";
        this.addValues(xPosition, yPosition, scale, xOffset, yOffset, showAvatar, showDistance, auraTarget);
    }

    @Override
    public void enable() {
        visibility = 0.0f;
        displayTarget = null;
        lastFrameMS = System.currentTimeMillis();
    }

    @Override
    public void disable() {
        visibility = 0.0f;
        displayTarget = null;
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
        if (target != null && target != displayTarget) {
            healthAnimation = healthRatio(target);
        }
        if (target != null) {
            displayTarget = target;
        }

        float wanted = target == null && !editMode ? 0.0f : 1.0f;
        visibility += (wanted - visibility) * factor;
        if (visibility <= 0.02f || displayTarget == null && !editMode) {
            return;
        }

        healthAnimation += ((displayTarget == null ? 0.66f : healthRatio(displayTarget)) - healthAnimation) * factor;
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

    private void drawHud(ScaledResolution sr, EntityLivingBase target, float alpha) {
        float width = 150.0f;
        float height = 48.0f;
        float uiScale = scale.getValue().floatValue();
        float defaultX = sr.getScaledWidth() / 2.0f + xOffset.getValue().floatValue();
        float defaultY = sr.getScaledHeight() / 2.0f + yOffset.getValue().floatValue();
        float[] pos = HudDrag.update("target_hud", xPosition, yPosition, scale, defaultX, defaultY,
                width * uiScale, height * uiScale, sr);
        float x = pos[0];
        float y = pos[1] + (HudDrag.isEditMode() ? 0.0f : (1.0f - alpha) * 8.0f);
        float avatar = Boolean.TRUE.equals(showAvatar.getValue()) ? 30.0f : 0.0f;
        float textX = x + 12.0f + avatar + (avatar > 0.0f ? 8.0f : 0.0f);
        float textW = width - (textX - x) - 10.0f;
        int fillAlpha = Math.round(150.0f * alpha);
        int borderAlpha = Math.round(54.0f * alpha);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0f);
            GlStateManager.scale(uiScale, uiScale, 1.0f);
            GlStateManager.translate(-x, -y, 0.0f);
            RenderUtil.drawSoftShadow(x, y, x + width, y + height, 7.0f,
                    withAlpha(0xFF000000, Math.round(70.0f * alpha)), 7, 3.2f);
            RenderUtil.drawFrostedGlassRect(x, y, x + width, y + height, 7.0f, 0.9f,
                    withAlpha(GLASS, fillAlpha), withAlpha(BORDER, borderAlpha));
            RenderUtil.drawHorizontalGradientRect(x + 9.0f, y + 4.0f, x + width - 9.0f, y + 5.2f,
                    withAlpha(ACCENT, Math.round(130.0f * alpha)), withAlpha(0xFF9D8CFF, Math.round(92.0f * alpha)));

            if (avatar > 0.0f) {
                drawAvatar(target, x + 10.0f, y + 10.0f, avatar, alpha);
            }

            String name = trim(target == null ? "Target HUD" : target.getName(), FontLoaders.C18, textW);
            String meta = buildMeta(target);
            FontLoaders.C18.drawString(name, textX, y + 11.0f, withAlpha(TEXT, Math.round(242.0f * alpha)));
            FontLoaders.C14.drawString(trim(meta, FontLoaders.C14, textW), textX, y + 25.0f,
                    withAlpha(MUTED, Math.round(218.0f * alpha)));

            float barX = textX;
            float barY = y + height - 10.0f;
            float barW = textW;
            int healthColor = healthColor(healthAnimation);
            float progress = clamp01(healthAnimation);
            float fillW = Math.max(0.0f, barW * progress);
            RenderUtil.drawRoundedRect(barX, barY, barX + barW, barY + 3.2f, 1.6f,
                    withAlpha(0xFF1E2630, Math.round(160.0f * alpha)));
            if (fillW > 0.4f) {
                RenderUtil.drawRoundedRect(barX, barY, barX + fillW, barY + 3.2f,
                        Math.min(1.6f, fillW / 2.0f), withAlpha(healthColor, Math.round(225.0f * alpha)));
                RenderUtil.drawHorizontalGradientRect(barX, barY, barX + fillW, barY + 3.2f,
                        withAlpha(healthColor, Math.round(245.0f * alpha)),
                        withAlpha(ColorUtils.lighten(healthColor, 0.16f), Math.round(210.0f * alpha)));
            }
        } finally {
            GlStateManager.popMatrix();
        }
        HudDrag.drawHint("target_hud", x, y, width * uiScale, height * uiScale, 7.0f * uiScale);
    }

    private void drawAvatar(EntityLivingBase target, float x, float y, float size, float alpha) {
        RenderUtil.drawFrostedGlassRect(x - 1.0f, y - 1.0f, x + size + 1.0f, y + size + 1.0f, 5.0f, 0.8f,
                withAlpha(0xFF111821, Math.round(118.0f * alpha)), withAlpha(ACCENT, Math.round(52.0f * alpha)));

        if (target instanceof AbstractClientPlayer) {
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
            mc.getTextureManager().bindTexture(((AbstractClientPlayer) target).getLocationSkin());
            Gui.drawScaledCustomSizeModalRect(Math.round(x), Math.round(y), 8.0f, 8.0f, 8, 8,
                    Math.round(size), Math.round(size), 64.0f, 64.0f);
            Gui.drawScaledCustomSizeModalRect(Math.round(x), Math.round(y), 40.0f, 8.0f, 8, 8,
                    Math.round(size), Math.round(size), 64.0f, 64.0f);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            return;
        }

        String icon = FontLoaders.ICON_CROSSHAIR;
        FontLoaders.I20.drawString(icon, x + size / 2.0f - FontLoaders.I20.getStringWidth(icon) / 2.0f,
                y + size / 2.0f - FontLoaders.I20.getHeight() / 2.0f + 2.0f,
                withAlpha(ACCENT, Math.round(226.0f * alpha)));
    }

    private String buildMeta(EntityLivingBase target) {
        if (target == null) {
            return "Drag to move";
        }
        float health = Math.max(0.0f, target.getHealth());
        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        String meta = formatOneDecimal(health) + "/" + formatOneDecimal(maxHealth) + " HP";
        if (Boolean.TRUE.equals(showDistance.getValue())) {
            meta += "  " + formatOneDecimal(mc.thePlayer.getDistanceToEntity(target)) + "m";
        }
        return meta;
    }

    private float healthRatio(EntityLivingBase target) {
        if (target == null) {
            return 0.0f;
        }
        return clamp01(target.getHealth() / Math.max(1.0f, target.getMaxHealth()));
    }

    private int healthColor(float ratio) {
        if (ratio > 0.55f) {
            return ColorUtils.interpolate(HEALTH_MID, HEALTH_HIGH, (ratio - 0.55f) / 0.45f);
        }
        return ColorUtils.interpolate(HEALTH_LOW, HEALTH_MID, ratio / 0.55f);
    }

    private float animationFactor(long now) {
        float delta = Math.max(1.0f, Math.min(50.0f, now - lastFrameMS));
        lastFrameMS = now;
        return 1.0f - (float) Math.pow(0.001D, delta / 210.0D);
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
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && font.getStringWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
