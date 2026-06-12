package gq.vapulite.module.render;

import gq.vapulite.engine.font.CFontRenderer;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ShaderRenderer;
import gq.vapulite.engine.render.ui.LiquidGlassSettings;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.module.Module;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.combat.Backtrack;
import gq.vapulite.module.combat.KillAura;
import gq.vapulite.util.render.HudDrag;
import gq.vapulite.util.render.RenderUtil;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;

public class TargetHUD extends Module {
    private static final int TEXT = 0xFFF5F0F5;
    private static final int MUTED = 0xFFB8AEB8;
    private static final int SAKURA = 0xFFFFB7D1;
    private static final int SAKURA_STRONG = 0xFFFF80B3;
    private static final int GLASS_FILL = 0xFF08080D;
    private static final int GLASS_BORDER = 0xFFFFB7D1;

    private final Numbers<Double> xPosition = new Numbers<Double>("X", "X", -1.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> yPosition = new Numbers<Double>("Y", "Y", -1.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0, 0.65, 1.8, 0.05);
    private final Numbers<Double> xOffset = new Numbers<Double>("X Offset", "XOffset", 22.0, -260.0, 260.0, 1.0);
    private final Numbers<Double> yOffset = new Numbers<Double>("Y Offset", "YOffset", 28.0, -180.0, 180.0, 1.0);
    private final Option<Boolean> showAvatar = new Option<Boolean>("Avatar", "Avatar", true);
    private final Option<Boolean> auraTarget = new Option<Boolean>("Aura Target", "AuraTarget", true);

    private EntityLivingBase displayTarget;
    private EntityLivingBase attackedTarget;
    private long attackedTargetUntil;
    private long lastFrameMS = System.currentTimeMillis();
    private int lastTargetId = -1;
    private float visibility;
    private float healthAnimation = 0.88f;
    private float damageAnimation = 0.88f;
    private float flowerAnimation = 0.88f;
    private float switchPulse;

    public TargetHUD() {
        super("TargetHUD", Keyboard.KEY_NONE, ModuleType.Render, "Show target info when aiming at an entity");
        Chinese = "目标HUD";
        this.addValues(xPosition, yPosition, scale, xOffset, yOffset, showAvatar, auraTarget);
    }

    @Override
    public void enable() {
        visibility = 0.0f;
        healthAnimation = 0.88f;
        damageAnimation = 0.88f;
        flowerAnimation = 0.88f;
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
        switchPulse = 1.0f;
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
                lastTargetId = target.getEntityId();
                healthAnimation = healthRatio(target);
                damageAnimation = healthRatio(target);
                flowerAnimation = healthRatio(target);
                switchPulse = 1.0f;
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

        float targetHealth = displayTarget == null ? 0.0f : healthRatio(displayTarget);
        float healthFactor = targetHealth < healthAnimation ? Math.min(1.0f, factor * 0.48f)
                : Math.min(1.0f, factor * 1.35f);
        healthAnimation += (targetHealth - healthAnimation) * healthFactor;
        float damageFactor = targetHealth < damageAnimation ? Math.min(1.0f, factor * 0.18f)
                : Math.min(1.0f, factor * 1.15f);
        damageAnimation += (targetHealth - damageAnimation) * damageFactor;
        flowerAnimation += (healthAnimation - flowerAnimation) * Math.min(1.0f, factor * 0.72f);
        switchPulse += (0.0f - switchPulse) * factor;

        ShaderRenderer.invalidateFrostedGlass();
        drawHud(new ScaledResolution(mc), displayTarget, visibility);
    }

    private void drawHud(ScaledResolution sr, EntityLivingBase target, float alpha) {
        float uiScale = Math.max(0.1f, scale.getValue().floatValue());
        float width = 200.0f;
        float height = 42.0f;
        float defaultX = sr.getScaledWidth() / 2.0f + xOffset.getValue().floatValue();
        float defaultY = sr.getScaledHeight() / 2.0f + yOffset.getValue().floatValue();
        float[] pos = HudDrag.update("target_hud", xPosition, yPosition, scale, defaultX, defaultY,
                width * uiScale, height * uiScale, sr);

        float rawAlpha = clamp01(alpha);
        float easedAlpha = smoothStep(rawAlpha);
        float x = pos[0];
        boolean editMode = HudDrag.isEditMode();
        float contentAlpha = editMode ? easedAlpha : foregroundAlpha(rawAlpha);
        float y = pos[1] + (editMode ? 0.0f : (1.0f - rawAlpha) * 10.0f);
        float w = width * uiScale;
        float h = height * uiScale;
        float radius = 8.0f * uiScale;
        float pulse = clamp01(switchPulse);
        float panelScale = editMode ? 1.0f : 0.88f + 0.12f * easeOutBack(rawAlpha) + pulse * 0.018f;

        int fill = withAlpha(GLASS_FILL, Math.round((158.0f + pulse * 18.0f) * easedAlpha));
        int border = withAlpha(GLASS_BORDER, Math.round((24.0f + pulse * 18.0f) * easedAlpha));
        LiquidGlassSettings settings = LiquidGlassSettings.defaults()
                .withBlurRadius(18.0f)
                .withBlurDownscale(0.92f)
                .withNoise(0.018f)
                .withRefractionScale(1.16f)
                .withHighlight(1.05f);

        GlStateManager.pushMatrix();
        if (Math.abs(panelScale - 1.0f) > 0.001f) {
            float centerX = x + w * 0.5f;
            float centerY = y + h * 0.5f;
            GlStateManager.translate(centerX, centerY, 0.0f);
            GlStateManager.scale(panelScale, panelScale, 1.0f);
            GlStateManager.translate(-centerX, -centerY, 0.0f);
        }
        RenderServices.shapes().shadow(x, y, x + w, y + h, radius,
                withAlpha(0xFF000000, Math.round(96.0f * easedAlpha)), 8, 3.4f * uiScale);
        RenderServices.shapes().shadow(x, y, x + w, y + h, radius,
                withAlpha(SAKURA, Math.round((28.0f + 26.0f * pulse) * easedAlpha)), 5, 2.2f * uiScale);
        RenderServices.liquidGlass().roundedBorder(x, y, x + w, y + h, radius, 0.55f * uiScale,
                fill, border, settings);
        drawBackgroundAccent(x, y, w, h, radius, uiScale, easedAlpha);

        drawAvatar(target, x + 14.0f * uiScale, y + 8.0f * uiScale, 26.0f * uiScale, uiScale, contentAlpha);
        drawText(target, x, y, w, uiScale, contentAlpha);
        drawHealth(target, x, y, w, uiScale, contentAlpha);
        HudDrag.drawHint("target_hud", x, y, w, h, radius);
        GlStateManager.popMatrix();
    }

    private void drawBackgroundAccent(float x, float y, float width, float height, float radius,
                                      float uiScale, float alpha) {
        RenderServices.shapes().shadow(x + 16.0f * uiScale, y + 5.0f * uiScale,
                x + width - 18.0f * uiScale, y + height - 5.0f * uiScale,
                radius, withAlpha(SAKURA, Math.round(24.0f * alpha)), 3, 1.8f * uiScale);
        RenderServices.shapes().rounded(x + 8.0f * uiScale, y + height - 9.0f * uiScale,
                x + 72.0f * uiScale, y + height - 4.0f * uiScale,
                3.0f * uiScale, withAlpha(SAKURA, Math.round(14.0f * alpha)));
    }

    private void drawText(EntityLivingBase target, float x, float y, float width, float uiScale, float alpha) {
        if (alpha <= 0.018f) {
            return;
        }
        CFontRenderer nameFont = FontLoaders.regular(Math.max(12, Math.round(18.0f * uiScale)));
        CFontRenderer smallFont = FontLoaders.regular(Math.max(9, Math.round(11.0f * uiScale)));
        CFontRenderer percentFont = FontLoaders.regular(Math.max(12, Math.round(16.0f * uiScale)));
        String name = target == null ? "Steve" : target.getName();
        String ping = target == null ? "--" : pingText(target);
        int percent = Math.round(clamp01(healthAnimation) * 100.0f);
        float nameX = x + 54.0f * uiScale;
        float nameY = y + 9.0f * uiScale;
        float right = x + width - 11.0f * uiScale;

        nameFont.drawString(trim(name, nameFont, 104.0f * uiScale), nameX, nameY,
                withAlpha(TEXT, Math.round(248.0f * alpha)));
        smallFont.drawString(ping, right - smallFont.getStringWidth(ping), y + 8.0f * uiScale,
                withAlpha(SAKURA, Math.round(218.0f * alpha)));

        String percentText = percent + "%";
        float percentX = right - percentFont.getStringWidth(percentText);
        float percentY = y + 21.0f * uiScale;
        drawTextGlow(percentFont, percentText, percentX, percentY, uiScale, alpha);
        percentFont.drawString(percentText, percentX, percentY,
                withAlpha(0xFFFFB9D4, Math.round(248.0f * alpha)));
    }

    private void drawTextGlow(CFontRenderer font, String text, float x, float y, float uiScale, float alpha) {
        if (alpha <= 0.018f) {
            return;
        }
        int wideGlow = withAlpha(SAKURA, Math.round(28.0f * alpha));
        int nearGlow = withAlpha(0xFFFFBED8, Math.round(48.0f * alpha));
        float wide = Math.max(0.72f, 0.88f * uiScale);
        float near = Math.max(0.38f, 0.50f * uiScale);
        font.drawString(text, x - wide, y, wideGlow);
        font.drawString(text, x + wide, y, wideGlow);
        font.drawString(text, x, y - wide, wideGlow);
        font.drawString(text, x, y + wide, wideGlow);
        font.drawString(text, x - near, y - near, nearGlow);
        font.drawString(text, x + near, y - near, nearGlow);
        font.drawString(text, x - near, y + near, nearGlow);
        font.drawString(text, x + near, y + near, nearGlow);
    }

    private void drawHealth(EntityLivingBase target, float x, float y, float width, float uiScale, float alpha) {
        float barX = x + 54.0f * uiScale;
        float barY = y + 27.0f * uiScale;
        float barW = 110.0f * uiScale;
        float barH = 5.6f * uiScale;
        float barRadius = barH * 0.5f;
        float health = target == null ? 0.0f : clamp01(healthAnimation);
        float delayed = clamp01(Math.max(health, damageAnimation));

        RenderServices.shapes().shadow(barX, barY, barX + barW, barY + barH, barRadius,
                withAlpha(SAKURA, Math.round(28.0f * alpha)), 3, 1.0f * uiScale);
        drawCapsule(barX, barY, barW, barH, withAlpha(0xFF17171D, Math.round(186.0f * alpha)));
        if (delayed > health + 0.003f) {
            drawCapsule(barX, barY, barW * delayed, barH,
                    withAlpha(0xFFFF6F9A, Math.round(92.0f * alpha)));
        }
        float fillW = Math.max(0.0f, barW * health);
        float flowerX = barX + Math.max(0.0f, Math.min(barW, barW * clamp01(flowerAnimation)));
        drawSakuraFlower(flowerX, barY + barH * 0.5f, 3.8f * uiScale, alpha);
        if (fillW > 0.75f) {
            drawCapsule(barX, barY, fillW, barH, withAlpha(SAKURA, Math.round(252.0f * alpha)));
            float shineInset = Math.min(barH * 0.28f, fillW * 0.18f);
            if (fillW > shineInset * 2.0f + 1.0f) {
                RenderServices.shapes().rounded(barX + shineInset, barY + shineInset,
                        barX + fillW - shineInset, barY + barH * 0.52f,
                        Math.min(barRadius * 0.45f, (barH * 0.52f - shineInset) * 0.5f),
                        withAlpha(0xFFFFD5E5, Math.round(76.0f * alpha)));
            }
            drawMovingBarSheen(barX, barY, fillW, barH, alpha);
        }
    }

    private void drawCapsule(float x, float y, float width, float height, int color) {
        if (width <= 0.0f || height <= 0.0f || ((color >>> 24) & 255) <= 0) {
            return;
        }
        drawCapsuleRaw(x, y, width, height, color);
        resetTextRenderState();
    }

    private void drawCapsuleRaw(float x, float y, float width, float height, int color) {
        if (width <= 0.0f || height <= 0.0f || ((color >>> 24) & 255) <= 0) {
            return;
        }
        float radius = height * 0.5f;
        if (width <= height) {
            RenderServices.shapes().circle(x + width * 0.5f, y + radius, 0, 360, width * 0.5f, color);
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        glColor(color, 1.0f);
        float centerY = y + radius;
        int segments = 12;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x + width * 0.5f, centerY);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.toRadians(90.0f + 180.0f * i / segments);
            GL11.glVertex2f(x + radius + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.toRadians(270.0f + 180.0f * i / segments);
            GL11.glVertex2f(x + width - radius + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GlStateManager.enableTexture2D();
    }

    private void drawMovingBarSheen(float x, float y, float width, float height, float alpha) {
        if (width <= height || alpha <= 0.002f) {
            return;
        }
        float t = (System.currentTimeMillis() % 2600L) / 2600.0f;
        float bandW = Math.max(height * 3.2f, width * 0.22f);
        float start = x - bandW + (width + bandW * 2.0f) * t;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glBegin(GL11.GL_QUADS);
        glColor(0xFFFFD8E8, alpha * 0.00f);
        GL11.glVertex2f(start - bandW * 0.48f, y + height);
        GL11.glVertex2f(start - bandW * 0.24f, y);
        glColor(0xFFFFD8E8, alpha * 0.28f);
        GL11.glVertex2f(start, y);
        GL11.glVertex2f(start - bandW * 0.24f, y + height);

        glColor(0xFFFFD8E8, alpha * 0.28f);
        GL11.glVertex2f(start, y);
        GL11.glVertex2f(start - bandW * 0.24f, y + height);
        glColor(0xFFFFD8E8, alpha * 0.00f);
        GL11.glVertex2f(start + bandW * 0.42f, y + height);
        GL11.glVertex2f(start + bandW * 0.66f, y);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        resetTextRenderState();
    }

    private void drawAvatar(EntityLivingBase target, float x, float y, float size, float uiScale, float alpha) {
        if (!Boolean.TRUE.equals(showAvatar.getValue()) || alpha <= 0.018f) {
            return;
        }
        float frameRadius = 7.0f * uiScale;
        RenderServices.shapes().shadow(x, y, x + size, y + size, frameRadius,
                withAlpha(0xFF000000, Math.round(86.0f * alpha)), 5, 1.8f * uiScale);
        RenderServices.shapes().roundedBorder(x, y, x + size, y + size, frameRadius, 0.8f * uiScale,
                withAlpha(0xFF20171C, Math.round(190.0f * alpha)),
                withAlpha(SAKURA, Math.round(62.0f * alpha)));

        ResourceLocation skin = skin(target);
        if (skin != null) {
            float pad = 2.5f * uiScale;
            drawRoundedHead(skin, x + pad, y + pad, size - pad * 2.0f, 5.0f * uiScale, alpha);
        } else {
            ResourceLocation entityTexture = entityTexture(target);
            if (entityTexture != null) {
                float pad = 2.5f * uiScale;
                drawRoundedHead(entityTexture, x + pad, y + pad, size - pad * 2.0f,
                        5.0f * uiScale, alpha, 64.0f, 64.0f, false);
            } else {
                drawEntityBadge(target, x, y, size, uiScale, alpha);
            }
        }
        resetTextRenderState();
    }

    private void drawRoundedHead(ResourceLocation skin, float x, float y, float size, float radius, float alpha) {
        drawRoundedHead(skin, x, y, size, radius, alpha, 64.0f, 64.0f, true);
    }

    private void drawRoundedHead(ResourceLocation skin, float x, float y, float size, float radius, float alpha,
                                 float textureWidth, float textureHeight, boolean overlay) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int is = Math.max(1, Math.round(size));
        float fx = ix;
        float fy = iy;
        float fs = is;
        GlStateManager.pushMatrix();
        try {
            RenderServices.stencil().initWrite();
            RenderServices.shapes().rounded(fx, fy, fx + fs, fy + fs, radius, 0xFFFFFFFF);
            RenderServices.stencil().read(1);
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
            mc.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(ix, iy, 8.0f, 8.0f, 8, 8,
                    is, is, textureWidth, textureHeight);
            if (overlay) {
                Gui.drawScaledCustomSizeModalRect(ix, iy, 40.0f, 8.0f, 8, 8,
                        is, is, textureWidth, textureHeight);
            }
        } finally {
            RenderServices.stencil().end();
            GlStateManager.popMatrix();
            resetTextRenderState();
        }
    }

    private void drawEntityBadge(EntityLivingBase target, float x, float y, float size, float uiScale, float alpha) {
        float pad = 2.5f * uiScale;
        float ix = x + pad;
        float iy = y + pad;
        float inner = size - pad * 2.0f;
        RenderServices.shapes().rounded(ix, iy, ix + inner, iy + inner, 5.0f * uiScale,
                withAlpha(0xFF160F15, Math.round(150.0f * alpha)));
        RenderServices.shapes().shadow(ix + 2.0f * uiScale, iy + 2.0f * uiScale,
                ix + inner - 2.0f * uiScale, iy + inner - 2.0f * uiScale, 4.0f * uiScale,
                withAlpha(SAKURA, Math.round(34.0f * alpha)), 3, 1.2f * uiScale);
        CFontRenderer iconFont = FontLoaders.icon(Math.max(13, Math.round(16.0f * uiScale)));
        String icon = target instanceof EntityPlayer ? FontLoaders.ICON_USER
                : target instanceof EntityAnimal || target instanceof EntityWaterMob || target instanceof EntityAmbientCreature
                ? FontLoaders.ICON_HEARTBEAT
                : target instanceof EntityMob || target instanceof EntitySlime || target instanceof IMob
                ? FontLoaders.ICON_WARNING : FontLoaders.ICON_CUBE;
        iconFont.drawString(icon, x + size / 2.0f - iconFont.getStringWidth(icon) / 2.0f,
                y + size / 2.0f - iconFont.getHeight() / 2.0f + 1.3f * uiScale,
                withAlpha(SAKURA, Math.round(232.0f * alpha)));
    }

    private void drawSakuraFlower(float centerX, float centerY, float size, float alpha) {
        if (alpha <= 0.002f || size <= 0.002f) {
            return;
        }
        RenderServices.shapes().shadow(centerX - size, centerY - size, centerX + size, centerY + size,
                size, withAlpha(SAKURA, Math.round(74.0f * alpha)), 4, size * 0.70f);
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0f);
        GlStateManager.rotate((System.currentTimeMillis() % 2400L) / 2400.0f * 24.0f, 0.0f, 0.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        for (int i = 0; i < 5; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(i * 72.0f, 0.0f, 0.0f, 1.0f);
            GL11.glTranslatef(0.0f, size * 0.20f, 0.0f);
            drawSakuraPetal2D(size, alpha);
            GL11.glPopMatrix();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        RenderServices.shapes().circle(centerX, centerY, 0, 360, size * 0.30f,
                withAlpha(0xFFFFF3FA, Math.round(235.0f * alpha)));
        resetTextRenderState();
    }

    private void drawSakuraPetal2D(float size, float alpha) {
        float width = size * 0.58f;
        float length = size * 1.12f;
        float[][] points = new float[][]{
                {0.00f, -0.18f}, {-0.30f, -0.07f}, {-0.64f, 0.25f}, {-0.66f, 0.62f},
                {-0.36f, 0.94f}, {-0.10f, 0.82f}, {0.00f, 0.74f}, {0.10f, 0.82f},
                {0.36f, 0.94f}, {0.66f, 0.62f}, {0.64f, 0.25f}, {0.30f, -0.07f}, {0.00f, -0.18f}
        };

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        glColor(0xFFFFEAF3, alpha * 0.96f);
        GL11.glVertex2f(0.0f, length * 0.36f);
        for (float[] point : points) {
            glColor(SAKURA, alpha * 0.70f);
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();

        GL11.glLineWidth(0.75f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        glColor(0xFFFFF6FA, alpha * 0.45f);
        for (float[] point : points) {
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();
    }

    private void glColor(int color, float alpha) {
        float a = ((color >>> 24) & 255) / 255.0f * clamp01(alpha);
        float r = ((color >>> 16) & 255) / 255.0f;
        float g = ((color >>> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        GlStateManager.color(r, g, b, a);
    }

    private ResourceLocation skin(EntityLivingBase target) {
        if (target instanceof AbstractClientPlayer) {
            return ((AbstractClientPlayer) target).getLocationSkin();
        }
        return null;
    }

    private ResourceLocation entityTexture(EntityLivingBase target) {
        if (target == null || mc.getRenderManager() == null) {
            return null;
        }
        try {
            Render render = mc.getRenderManager().getEntityRenderObject(target);
            Method method = findEntityTextureMethod(render.getClass());
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            Object texture = method.invoke(render, target);
            return texture instanceof ResourceLocation ? (ResourceLocation) texture : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Method findEntityTextureMethod(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod("getEntityTexture", Entity.class);
            } catch (NoSuchMethodException ignored) {
                Method[] methods = current.getDeclaredMethods();
                for (Method method : methods) {
                    if ("getEntityTexture".equals(method.getName())
                            && method.getParameterTypes().length == 1
                            && Entity.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
        }
        return null;
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
            if (direct != null) {
                return direct;
            }
        }
        EntityLivingBase recentAttack = recentAttackTarget();
        if (recentAttack != null) {
            return recentAttack;
        }
        return Backtrack.getAimedTarget();
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

    private float healthRatio(EntityLivingBase target) {
        if (target == null) {
            return 0.0f;
        }
        return clamp01(target.getHealth() / Math.max(1.0f, target.getMaxHealth()));
    }

    private float animationFactor(long now) {
        float delta = Math.max(1.0f, Math.min(50.0f, now - lastFrameMS));
        lastFrameMS = now;
        return 1.0f - (float) Math.pow(0.001D, delta / 220.0D);
    }

    private String trim(String text, CFontRenderer font, float maxWidth) {
        if (text == null || maxWidth <= 0.0f) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && font.getStringWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.length() <= 1 ? "..." : result + "...";
    }

    private void resetTextRenderState() {
        GlStateManager.disableDepth();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0f - 2.0f * t);
    }

    private float foregroundAlpha(float value) {
        return smoothStep((clamp01(value) - 0.055f) / 0.945f);
    }

    private float easeOutBack(float value) {
        float t = clamp01(value) - 1.0f;
        float c = 1.55f;
        return 1.0f + t * t * ((c + 1.0f) * t + c);
    }
}
