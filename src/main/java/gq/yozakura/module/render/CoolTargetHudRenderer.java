package gq.yozakura.module.render;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.glow.GlowRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Nymphilila-derived TargetHUD without equipment, with an enlarged glowing health bar. */
final class CoolTargetHudRenderer {
    private static final int NAME_COLOR = 0xFFA668FF;
    private static final int HEALTH_YELLOW = 0xFFFFDB20;
    private static final int HEALTH_RED = 0xFFFF3D4D;
    private static final float LOW_HEALTH_RATIO = 0.25F;

    private final Minecraft minecraft = Minecraft.getMinecraft();

    Layout measure(EntityLivingBase target) {
        CFontRenderer nameFont = FontLoaders.circularMedium(18);
        String name = target == null ? "Steve" : target.getName();
        return new Layout(CoolTargetHudLayout.width(nameFont.getStringWidth(name)),
                CoolTargetHudLayout.HEIGHT, nameFont, FontLoaders.circularMedium(16));
    }

    void draw(EntityLivingBase target, float x, float y, float uiScale,
              NymphTargetHudMotion.Snapshot motion, float animatedHealth,
              int backgroundAlpha, float cornerRadius, CoolTargetHudHurtMotion.Snapshot hurt,
              CoolTargetHudNumberMotion.Snapshot numberMotion,
              boolean showAvatar) {
        if (target == null || motion == null || !motion.isRetained()
                || motion.getOpacity() <= 0.0F || motion.getScale() <= 0.0F) {
            return;
        }
        Layout layout = measure(target);
        float scale = Math.max(0.1F, uiScale);
        float radius = Math.max(0.0F, Math.min(CoolTargetHudLayout.HEIGHT * 0.5F, cornerRadius));
        float hurtIntensity = hurt == null ? 0.0F : hurt.getIntensity();
        float hurtShakeX = hurt == null ? 0.0F : hurt.getShakeX();
        queueBloomMask(layout, x + hurtShakeX, y, scale, radius, motion);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            GlStateManager.translate(-x, -y, 0.0F);
            GlStateManager.translate(hurtShakeX, 0.0F, 0.0F);
            applyOpeningScale(x + layout.width * 0.5F, y + layout.height * 0.5F, motion.getScale());
            applyImpactScale(x + layout.width * 0.5F, y + layout.height * 0.5F,
                    1.0F + hurtIntensity * 0.015F);
            RenderServices.markHudEffectsStateChanged();
            drawContent(target, layout, x, y, motion.getOpacity(), animatedHealth,
                    backgroundAlpha, radius, hurtIntensity, numberMotion, showAvatar);
        } finally {
            GlStateManager.popMatrix();
            resetRenderState();
            RenderServices.markHudEffectsStateChanged();
        }
    }

    private void drawContent(EntityLivingBase target, Layout layout, float x, float y,
                             float opacity, float animatedHealth, int backgroundAlpha,
                             float cornerRadius, float hurtIntensity,
                             CoolTargetHudNumberMotion.Snapshot numberMotion, boolean showAvatar) {
        int alpha = Math.max(0, Math.min(255, Math.round(255.0F * opacity)));
        int panelAlpha = Math.round(Math.max(0, Math.min(255, backgroundAlpha)) * opacity);
        RenderServices.shapes().rounded(x, y, x + layout.width, y + layout.height,
                cornerRadius, withAlpha(0xFF141414, panelAlpha));

        ResourceLocation skin = showAvatar && target instanceof AbstractClientPlayer
                ? ((AbstractClientPlayer) target).getLocationSkin() : null;
        if (skin != null) {
            drawHead(skin, x + 3.0F, y + 4.0F, CoolTargetHudLayout.AVATAR_SIZE, opacity, hurtIntensity);
        }

        float contentX = x + CoolTargetHudLayout.AVATAR_ADVANCE;
        float nameX = contentX + 4.0F;
        float nameY = y + 10.0F;
        queueNameGlow(layout.nameFont, target.getName(), nameX, nameY, opacity);
        layout.nameFont.drawString(target.getName(), nameX, nameY, withAlpha(NAME_COLOR, alpha));

        float actualRatio = clamp01(target.getHealth() / Math.max(1.0F, target.getMaxHealth()));
        float ratio = clamp01(animatedHealth);
        int healthColor = actualRatio <= LOW_HEALTH_RATIO ? HEALTH_RED : HEALTH_YELLOW;
        float displayedHealth = numberMotion == null ? target.getHealth() : numberMotion.getHealth();
        float healthTextScale = numberMotion == null ? 1.0F : numberMotion.getScale();
        String healthText = Integer.toString(Math.max(0, Math.round(displayedHealth)));
        float healthTextWidth = layout.healthFont.getStringWidth(healthText);
        float barX = contentX + 4.0F;
        float barY = y + 27.0F;
        float barWidth = CoolTargetHudLayout.healthBarWidth(layout.width);
        RenderServices.shapes().rounded(barX, barY, barX + barWidth,
                barY + CoolTargetHudLayout.HEALTH_BAR_HEIGHT, 2.5F,
                withAlpha(0xFF000000, Math.round(105.0F * opacity)));
        float barRight = barX + barWidth * ratio;
        if (barRight > barX) {
            RenderServices.shapes().rounded(barX, barY, barRight,
                    barY + CoolTargetHudLayout.HEALTH_BAR_HEIGHT, 2.5F,
                    withAlpha(healthColor, alpha));
            queueHealthGlow(barX, barY, barRight, opacity, healthColor);
        }
        float healthTextY = barY + CoolTargetHudLayout.HEALTH_BAR_HEIGHT * 0.5F
                - layout.healthFont.getHeight() * 0.5F + 1.5F;
        float healthTextX = x + layout.width - healthTextWidth - 9.0F;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(healthTextX + healthTextWidth * 0.5F,
                    healthTextY + layout.healthFont.getHeight() * 0.5F, 0.0F);
            GlStateManager.scale(healthTextScale, healthTextScale, 1.0F);
            GlStateManager.translate(-(healthTextX + healthTextWidth * 0.5F),
                    -(healthTextY + layout.healthFont.getHeight() * 0.5F), 0.0F);
            layout.healthFont.drawString(healthText, healthTextX, healthTextY, withAlpha(0xFFFFFFFF, alpha));
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void queueNameGlow(CFontRenderer font, String text, float x, float y, float opacity) {
        GlowRenderer glow = RenderServices.glow();
        boolean isolatedFrame = !glow.isFrameOpen();
        if (isolatedFrame) {
            glow.beginFrame();
            glow.beginCommandSnapshotCache();
        }
        try {
            glow.queueText(font, text, x, y, withAlpha(NAME_COLOR, Math.round(235.0F * opacity)),
                    0.88F, GlowProfile.TEXT);
        } finally {
            if (isolatedFrame) {
                glow.flush();
            }
        }
    }

    private void queueHealthGlow(float left, float top, float right, float opacity, int color) {
        GlowRenderer glow = RenderServices.glow();
        boolean isolatedFrame = !glow.isFrameOpen();
        if (isolatedFrame) {
            glow.beginFrame();
            glow.beginCommandSnapshotCache();
        }
        try {
            glow.queueRoundedRect(left, top, right, top + CoolTargetHudLayout.HEALTH_BAR_HEIGHT,
                    2.5F, withAlpha(color, Math.round(220.0F * opacity)),
                    0.80F, GlowProfile.ACCENT);
        } finally {
            if (isolatedFrame) {
                glow.flush();
            }
        }
    }

    /** Replays the Nymph DoAFuckingBloomEvent-equivalent mask for the Cool card. */
    private void queueBloomMask(Layout layout, float x, float y, float scale, float cornerRadius,
                                NymphTargetHudMotion.Snapshot motion) {
        float openingScale = motion.getScale();
        float animatedScale = openingScale * scale;
        float left = x + layout.width * scale * (1.0F - openingScale) * 0.5F;
        float top = y + layout.height * scale * (1.0F - openingScale) * 0.5F;
        float right = left + layout.width * animatedScale;
        float bottom = top + layout.height * animatedScale;
        int alpha = Math.max(0, Math.min(255, Math.round(255.0F * motion.getOpacity())));
        if (right <= left || bottom <= top || alpha <= 0) {
            return;
        }
        GlowRenderer shadows = RenderServices.shadows();
        boolean isolatedFrame = !shadows.isFrameOpen();
        if (isolatedFrame) {
            shadows.beginFrame();
            shadows.beginCommandSnapshotCache();
        }
        try {
            shadows.queueRoundedRect(left, top, right, bottom, cornerRadius * animatedScale,
                    withAlpha(0xFF000000, alpha), 1.0F, GlowProfile.SHADOW);
        } finally {
            if (isolatedFrame) {
                shadows.flush();
            }
        }
    }

    private void drawHead(ResourceLocation skin, float x, float y, float size, float opacity,
                          float hurtIntensity) {
        int left = Math.round(x);
        int top = Math.round(y);
        int edge = Math.max(1, Math.round(size));
        float radius = Math.min(6.0F, edge * 0.5F);
        GlStateManager.pushMatrix();
        try {
            RenderServices.stencil().initWrite();
            RenderServices.shapes().joinedRounded(left, top, left + edge, top + edge,
                    radius, radius, radius, radius, 0xFFFFFFFF);
            RenderServices.stencil().read(1);
            GlStateManager.enableBlend();
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F - hurtIntensity * 0.65F,
                    1.0F - hurtIntensity * 0.65F, clamp01(opacity));
            minecraft.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(left, top, 8.0F, 8.0F, 8, 8, edge, edge, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(left, top, 40.0F, 8.0F, 8, 8, edge, edge, 64.0F, 64.0F);
        } finally {
            RenderServices.stencil().end();
            RenderServices.shapes().roundedBorder(left, top, left + edge, top + edge, radius, 0.75F,
                    0x00000000, withAlpha(0xFF141414, Math.round(185.0F * clamp01(opacity))));
            GlStateManager.popMatrix();
            resetRenderState();
        }
    }

    private static void applyOpeningScale(float centerX, float centerY, float scale) {
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-centerX, -centerY, 0.0F);
    }

    private static void applyImpactScale(float centerX, float centerY, float scale) {
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-centerX, -centerY, 0.0F);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void resetRenderState() {
        GlStateManager.disableDepth();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static final class Layout {
        final float width;
        final float height;
        final CFontRenderer nameFont;
        final CFontRenderer healthFont;

        Layout(float width, float height, CFontRenderer nameFont, CFontRenderer healthFont) {
            this.width = width;
            this.height = height;
            this.nameFont = nameFont;
            this.healthFont = healthFont;
        }
    }
}
