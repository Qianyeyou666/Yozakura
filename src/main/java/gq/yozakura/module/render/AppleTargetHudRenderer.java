package gq.yozakura.module.render;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * Compact modern card inspired by the common target HUD language across the
 * reference clients: solid dark rounded surface, bold name, muted metadata,
 * thin health bar with damage trail, and restrained hurt feedback.
 */
final class AppleTargetHudRenderer {
    static final float EDGE_OFFSET = 10.0F;
    static final float AVATAR_SIZE = 34.0F;
    static final float AVATAR_GAP = 10.0F;
    static final float HEIGHT = 54.0F;
    static final float RADIUS = 9.0F;
    private static final float MINIMUM_BODY_WIDTH = 128.0F;
    private static final float HEALTH_BAR_HEIGHT = 4.0F;
    private static final float AVATAR_RADIUS = 8.0F;
    private static final float NAME_FONT_SIZE = 14.0F;
    private static final float META_FONT_SIZE = 10.0F;
    private static final float HEALTH_FONT_SIZE = 12.0F;
    private static final float NAME_Y = 8.0F;
    private static final float META_Y = 23.0F;
    private static final float HEALTH_BAR_Y = 40.0F;
    private static final int TEXT_PRIMARY = 0xFFF5F5F7;
    private static final int TEXT_MUTED = 0xFF9BA1A6;
    private static final int SURFACE_COLOR = 0xFF111318;
    private static final int SURFACE_RAISED_COLOR = 0xFF1B1E24;
    private static final int HEALTH_HIGH = 0xFF34C759;
    private static final int HEALTH_MID = 0xFFFFCC00;
    private static final int HEALTH_LOW = 0xFFFF3B30;
    private static final int DAMAGE_TRAIL_COLOR = 0xFFFF453A;

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final NymphArrayListEffectsRenderer panelBlur = new NymphArrayListEffectsRenderer();

    Layout measure(EntityLivingBase target, float uiScale, boolean showAvatar) {
        CFontRenderer nameFont = FontLoaders.tenacityBold(fontSize(NAME_FONT_SIZE, uiScale));
        CFontRenderer metaFont = FontLoaders.regular(fontSize(META_FONT_SIZE, uiScale));
        CFontRenderer healthFont = FontLoaders.jetBrainsMono(fontSize(HEALTH_FONT_SIZE, uiScale));
        String name = target == null ? "Steve" : target.getName();
        String metadata = metadataText(target);
        float ratio = target == null ? 0.76F : healthRatio(target);
        String healthText = healthText(ratio, target == null ? 20.0F : maxHealth(target));
        float nameWidth = nameFont.getStringWidth(name) / uiScale;
        float metadataWidth = metaFont.getStringWidth(metadata) / uiScale;
        float healthTextWidth = healthFont.getStringWidth(healthText) / uiScale;
        float bodyWidth = Math.max(MINIMUM_BODY_WIDTH,
                Math.max(nameWidth + 12.0F, metadataWidth + 8.0F) + healthTextWidth + 8.0F);
        float avatarWidth = showAvatar ? AVATAR_SIZE + AVATAR_GAP : 0.0F;
        float width = EDGE_OFFSET + avatarWidth + bodyWidth + EDGE_OFFSET;
        return new Layout(width, HEIGHT, bodyWidth - healthTextWidth - 8.0F,
                nameFont, metaFont, healthFont);
    }

    void draw(EntityLivingBase target, float x, float y, float uiScale,
              AppleTargetHudMotion motion, Content current, Content previous,
              Layout layout, boolean showAvatar) {
        if (layout == null || current == null || motion == null) {
            return;
        }
        float panelAlpha = smoothStep(motion.getVisibility());
        if (panelAlpha <= 0.001F) {
            return;
        }

        float width = layout.width * uiScale;
        float height = layout.height * uiScale;
        float drawY = y + motion.getPanelYOffset() * uiScale;
        float panelScale = motion.getPanelScale();
        float scaledWidth = width * panelScale;
        float scaledHeight = height * panelScale;
        float panelX = x + (width - scaledWidth) * 0.5F;
        float panelY = drawY + (height - scaledHeight) * 0.5F;
        float centerX = x + width * 0.5F;
        float centerY = drawY + height * 0.5F;
        boolean blurReady = panelBlur.prepareBlur(10.0F);
        queuePanelShadow(x, drawY, width, height, RADIUS * uiScale, panelScale, panelAlpha);
        drawPanel(panelX, panelY, scaledWidth, scaledHeight,
                RADIUS * uiScale * panelScale, uiScale * panelScale, panelAlpha, blurReady);

        GlStateManager.pushMatrix();
        try {
            applyOpeningScale(centerX, centerY, panelScale);

            float previousAlpha = motion.getPreviousContentAlpha();
            if (previous != null && previousAlpha > 0.001F) {
                drawContent(previous, layout, x, drawY, width, uiScale, previousAlpha, showAvatar,
                        previous.getHealthRatio(), previous.getHealthRatio(),
                        previous.isHurt() ? 0.5F : 0.0F);
            }
            float currentAlpha = motion.getCurrentContentAlpha();
            if (currentAlpha > 0.001F) {
                drawContent(current, layout, x, drawY, width, uiScale, currentAlpha, showAvatar,
                        motion.getHealth(), motion.getDamageTrail(), motion.getHurt());
            }
        } finally {
            GlStateManager.popMatrix();
            resetRenderState();
        }
    }

    private void drawPanel(float x, float y, float width, float height, float radius,
                           float uiScale, float alpha, boolean blurReady) {
        float borderWidth = Math.max(0.5F, 0.75F * uiScale);
        int fill = multiplyAlpha(SURFACE_COLOR, 0.56F * alpha);
        int border = multiplyAlpha(0xFFFFFFFF, 0.10F * alpha);
        float right = x + width - 1.0F;
        float bottom = y + height;
        if (!blurReady || !panelBlur.drawBlurredSurface(x, y, right, bottom, radius, fill)) {
            RenderServices.shapes().rounded(x, y, right, bottom, radius, fill);
        }
        RenderServices.shapes().roundedBorder(x, y, right, bottom,
                radius, borderWidth, 0x00000000, border);
    }

    private void drawContent(Content content, Layout layout, float x, float y, float width,
                             float uiScale, float alpha, boolean showAvatar,
                             float health, float damageTrail, float hurt) {
        float avatarSize = AVATAR_SIZE * uiScale;
        if (showAvatar) {
            float avatarX = x + EDGE_OFFSET * uiScale;
            float avatarY = y + (HEIGHT - AVATAR_SIZE) * 0.5F * uiScale;
            drawPortrait(content, avatarX, avatarY, avatarSize, uiScale, alpha, hurt);
        }

        float textX = x + (showAvatar ? EDGE_OFFSET + AVATAR_SIZE + AVATAR_GAP : EDGE_OFFSET) * uiScale;
        float right = x + width - EDGE_OFFSET * uiScale;
        layout.nameFont.drawStringWithShadow(trim(content.getName(), layout.nameFont, right - textX),
                textX, y + NAME_Y * uiScale, multiplyAlpha(TEXT_PRIMARY, alpha));
        layout.metaFont.drawString(trim(content.getMetadata(), layout.metaFont, right - textX),
                textX, y + META_Y * uiScale, multiplyAlpha(TEXT_MUTED, alpha));

        float maxHealth = content.getEntity() == null
                ? 20.0F : Math.max(1.0F, content.getEntity().getMaxHealth());
        String healthText = healthText(health, maxHealth);
        float healthTextWidth = layout.healthFont.getStringWidth(healthText);
        float healthTextX = right - healthTextWidth;
        float barWidth = Math.max(0.0F,
                Math.min(layout.healthBarWidth * uiScale, healthTextX - 8.0F * uiScale - textX));
        float barY = y + HEALTH_BAR_Y * uiScale;
        float barHeight = Math.max(3.0F, HEALTH_BAR_HEIGHT * uiScale);
        float barRadius = barHeight * 0.5F;
        RenderServices.shapes().rounded(textX, barY, textX + barWidth, barY + barHeight,
                barRadius, multiplyAlpha(0xFF0A0C10, 0.55F * alpha));
        float damageWidth = Math.max(0.0F, Math.min(barWidth, barWidth * clamp01(damageTrail)));
        if (damageWidth > 0.5F) {
            RenderServices.shapes().roundedGradient(textX, barY,
                    textX + damageWidth, barY + barHeight, barRadius,
                    multiplyAlpha(DAMAGE_TRAIL_COLOR, 0.16F * alpha),
                    multiplyAlpha(DAMAGE_TRAIL_COLOR, 0.16F * alpha),
                    multiplyAlpha(DAMAGE_TRAIL_COLOR, 0.42F * alpha),
                    multiplyAlpha(DAMAGE_TRAIL_COLOR, 0.42F * alpha));
        }
        float fillWidth = Math.max(0.0F, Math.min(barWidth, barWidth * clamp01(health)));
        if (fillWidth > 0.5F) {
            int base = healthColor(health);
            int top = lighten(base, 0.14F);
            RenderServices.shapes().roundedGradient(textX, barY,
                    textX + fillWidth, barY + barHeight, barRadius,
                    multiplyAlpha(top, alpha), multiplyAlpha(base, alpha),
                    multiplyAlpha(top, alpha), multiplyAlpha(base, alpha));
            float highlightInset = 1.5F * uiScale;
            float highlightWidth = fillWidth - highlightInset * 2.0F;
            if (highlightWidth > 1.5F) {
                RenderServices.shapes().rounded(textX + highlightInset,
                        barY + 0.75F * uiScale,
                        textX + highlightInset + highlightWidth,
                        barY + 1.75F * uiScale,
                        Math.max(0.5F, barHeight * 0.25F),
                        multiplyAlpha(0xFFFFFFFF, 0.22F * alpha));
            }
        }
        layout.healthFont.drawStringWithShadow(healthText, healthTextX,
                y + (HEALTH_BAR_Y - 2.0F) * uiScale, multiplyAlpha(TEXT_PRIMARY, alpha));
    }

    private void drawPortrait(Content content, float x, float y, float size, float uiScale,
                              float alpha, float hurt) {
        float radius = AVATAR_RADIUS * uiScale;
        RenderServices.shapes().shadow(x, y, x + size, y + size, radius,
                multiplyAlpha(0xFF000000, 0.24F * alpha), 3, 0.7F * uiScale);
        RenderServices.shapes().rounded(x, y, x + size, y + size, radius,
                multiplyAlpha(SURFACE_RAISED_COLOR, alpha));

        ResourceLocation skin = content.getEntity() instanceof AbstractClientPlayer
                ? ((AbstractClientPlayer) content.getEntity()).getLocationSkin() : null;
        if (skin != null) {
            float inset = 2.0F * uiScale;
            drawRoundedHead(skin, x + inset, y + inset, size - inset * 2.0F,
                    Math.max(1.0F, radius - inset), alpha);
        } else {
            drawEntityBadge(content.getEntity(), x, y, size, uiScale, alpha);
        }
        if (hurt > 0.001F) {
            RenderServices.shapes().rounded(x, y, x + size, y + size, radius,
                    multiplyAlpha(HEALTH_LOW, 0.16F * clamp01(hurt) * alpha));
        }
    }

    private void drawRoundedHead(ResourceLocation skin, float x, float y, float size,
                                 float radius, float alpha) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int is = Math.max(1, Math.round(size));
        GlStateManager.pushMatrix();
        try {
            RenderServices.stencil().initWrite();
            RenderServices.shapes().rounded(ix, iy, ix + is, iy + is, radius, 0xFFFFFFFF);
            RenderServices.stencil().read(1);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            minecraft.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(ix, iy, 8.0F, 8.0F, 8, 8, is, is, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(ix, iy, 40.0F, 8.0F, 8, 8, is, is, 64.0F, 64.0F);
        } finally {
            RenderServices.stencil().end();
            GlStateManager.popMatrix();
            resetRenderState();
        }
    }

    private void drawEntityBadge(EntityLivingBase entity, float x, float y, float size,
                                 float uiScale, float alpha) {
        CFontRenderer iconFont = FontLoaders.icon(Math.max(13, Math.round(17.0F * uiScale)));
        String icon = entity instanceof EntityPlayer ? FontLoaders.ICON_USER
                : entity instanceof EntityAnimal || entity instanceof EntityWaterMob
                || entity instanceof EntityAmbientCreature ? FontLoaders.ICON_HEARTBEAT
                : entity instanceof EntityMob || entity instanceof EntitySlime || entity instanceof IMob
                ? FontLoaders.ICON_WARNING : FontLoaders.ICON_CUBE;
        float iconX = x + size * 0.5F - iconFont.getStringWidth(icon) * 0.5F;
        float iconY = y + size * 0.5F - iconFont.getHeight() * 0.5F;
        iconFont.drawString(icon, iconX, iconY, multiplyAlpha(TEXT_PRIMARY, alpha));
    }

    private void queuePanelShadow(float x, float y, float width, float height, float radius,
                                  float scale, float alpha) {
        float resolvedScale = Math.max(0.0F, scale);
        float scaledWidth = width * resolvedScale;
        float scaledHeight = height * resolvedScale;
        float offsetX = 0.0F;
        float offsetY = 5.0F * Math.max(0.5F, scale);
        float expand = Math.max(4.0F, 5.0F * Math.max(0.5F, scale));
        float shadowX = x + (width - scaledWidth) * 0.5F + offsetX - expand;
        float shadowY = y + (height - scaledHeight) * 0.5F + offsetY - expand;
        float shadowWidth = scaledWidth + expand * 2.0F;
        float shadowHeight = scaledHeight + expand * 2.0F;
        if (RenderServices.shadows().isFrameOpen()) {
            RenderServices.shadows().queueRoundedRect(shadowX, shadowY,
                    shadowX + shadowWidth, shadowY + shadowHeight,
                    radius * resolvedScale + expand * 0.5F,
                    withAlpha(0xFF000000, Math.round(112.0F * clamp01(alpha))),
                    1.0F, GlowProfile.SHADOW);
        } else {
            RenderServices.shapes().shadow(shadowX, shadowY,
                    shadowX + shadowWidth, shadowY + shadowHeight,
                    radius * resolvedScale + expand * 0.5F,
                    withAlpha(0xFF000000, Math.round(72.0F * clamp01(alpha))), 4, 2.2F);
        }
    }

    private static void applyOpeningScale(float centerX, float centerY, float scale) {
        GlStateManager.translate(centerX * (1.0F - scale), centerY * (1.0F - scale), 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
    }

    private static int fontSize(float size, float uiScale) {
        return Math.max(1, Math.round(size * Math.max(0.1F, uiScale)));
    }

    private static float healthRatio(EntityLivingBase target) {
        return Math.max(0.0F, Math.min(1.0F,
                target.getHealth() / Math.max(1.0F, target.getMaxHealth())));
    }

    private static float maxHealth(EntityLivingBase target) {
        return target == null ? 20.0F : Math.max(1.0F, target.getMaxHealth());
    }

    private static String healthText(float ratio, float maxHealth) {
        float health = clamp01(ratio) * Math.max(1.0F, maxHealth);
        return String.format(Locale.ROOT, "%.1f", health);
    }

    private String metadataText(EntityLivingBase target) {
        if (target == null || minecraft.thePlayer == null) {
            return "--  ·  --ms";
        }
        float distance = minecraft.thePlayer.getDistanceToEntity(target);
        String ping = "--ms";
        if (target instanceof EntityPlayer && minecraft.getNetHandler() != null) {
            NetworkPlayerInfo info = minecraft.getNetHandler()
                    .getPlayerInfo(((EntityPlayer) target).getUniqueID());
            if (info != null) {
                ping = Math.max(0, info.getResponseTime()) + "ms";
            }
        }
        return String.format(Locale.ROOT, "%.1fm  ·  %s", distance, ping);
    }

    private static int healthColor(float health) {
        float ratio = clamp01(health);
        if (ratio <= 0.30F) {
            return HEALTH_LOW;
        }
        if (ratio <= 0.60F) {
            return HEALTH_MID;
        }
        return HEALTH_HIGH;
    }

    private static int lighten(int color, float amount) {
        int red = Math.min(255, Math.round(((color >>> 16) & 255) * (1.0F + amount)));
        int green = Math.min(255, Math.round(((color >>> 8) & 255) * (1.0F + amount)));
        int blue = Math.min(255, Math.round((color & 255) * (1.0F + amount)));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static String trim(String text, CFontRenderer font, float maxWidth) {
        if (text == null || maxWidth <= 0.0F) {
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

    private static int multiplyAlpha(int color, float alpha) {
        int sourceAlpha = color >>> 24 & 255;
        int resolvedAlpha = Math.round(sourceAlpha * clamp01(alpha));
        return color & 0x00FFFFFF | resolvedAlpha << 24;
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    private static float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void resetRenderState() {
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static final class Layout {
        final float width;
        final float height;
        final float healthBarWidth;
        final CFontRenderer nameFont;
        final CFontRenderer metaFont;
        final CFontRenderer healthFont;

        Layout(float width, float height, float healthBarWidth,
               CFontRenderer nameFont, CFontRenderer metaFont, CFontRenderer healthFont) {
            this.width = width;
            this.height = height;
            this.healthBarWidth = healthBarWidth;
            this.nameFont = nameFont;
            this.metaFont = metaFont;
            this.healthFont = healthFont;
        }
    }

    static final class Content {
        private final EntityLivingBase entity;
        private final int entityId;
        private final String name;
        private final String metadata;
        private final float healthRatio;
        private final boolean hurt;

        Content(EntityLivingBase entity, int entityId, String name, String metadata,
                float healthRatio, boolean hurt) {
            this.entity = entity;
            this.entityId = entityId;
            this.name = name == null || name.length() == 0 ? "Unknown" : name;
            this.metadata = metadata == null ? "" : metadata;
            this.healthRatio = clamp01(healthRatio);
            this.hurt = hurt;
        }

        EntityLivingBase getEntity() {
            return entity;
        }

        int getEntityId() {
            return entityId;
        }

        String getName() {
            return name;
        }

        String getMetadata() {
            return metadata;
        }

        float getHealthRatio() {
            return healthRatio;
        }

        boolean isHurt() {
            return hurt;
        }
    }
}
