package gq.yozakura.module.render;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.ui.LiquidGlassSettings;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Source-faithful adapter for Rise 6's ModernTargetInfo renderer. */
final class RiseTargetHudRenderer {
    static final float EDGE_OFFSET = 7.0F;
    static final float PADDING = 6.0F;
    static final float INDENT = 4.0F;
    static final float FACE_SCALE = 30.0F;
    static final float HEIGHT = 46.0F;
    static final float RADIUS = 9.0F;
    private static final float MINIMUM_HEALTH_BAR_WIDTH = 62.0F;
    private static final int PARTICLE_BURST_COUNT = 3;

    private static final LiquidGlassSettings GLASS_SETTINGS = LiquidGlassSettings.defaults()
            .withBlurRadius(10.0F)
            .withBlurDownscale(0.84F)
            .withNoise(0.025F)
            .withRefractionScale(0.9F)
            .withHighlight(0.95F);
    private static final long PARTICLE_LIFETIME_MS = 650L;

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final List<HurtParticle> particles = new ArrayList<HurtParticle>();
    private int particleTargetId = Integer.MIN_VALUE;
    private int lastParticleHurtTime;

    Layout measure(EntityLivingBase target, float uiScale, boolean showAvatar) {
        CFontRenderer light = FontLoaders.inter(fontSize(11.0F, uiScale));
        CFontRenderer medium = FontLoaders.bricolage(fontSize(15.0F, uiScale));
        String name = target == null ? "Steve" : target.getName();
        String healthText = healthText(target);
        String metadata = metadataText(target);
        float nameWidth = medium.getStringWidth(name) / uiScale;
        float metadataWidth = light.getStringWidth(metadata) / uiScale;
        float healthTextWidth = medium.getStringWidth(healthText) / uiScale;
        float healthBarWidth = Math.max(nameWidth + 26.0F - healthTextWidth, MINIMUM_HEALTH_BAR_WIDTH);
        float avatarWidth = showAvatar ? EDGE_OFFSET + FACE_SCALE + PADDING : EDGE_OFFSET;
        float width = avatarWidth + Math.max(healthBarWidth, metadataWidth)
                + INDENT + healthTextWidth + EDGE_OFFSET;
        return new Layout(width, HEIGHT, healthBarWidth, healthTextWidth, name, healthText, light, medium);
    }

    void draw(EntityLivingBase target, float x, float y, float uiScale, float openingScale,
              float healthRemainingWidth, Layout layout, RiseTargetHudBackground background,
              boolean showAvatar, boolean particlesEnabled, float partialTicks) {
        if (layout == null || openingScale <= 0.0F) {
            return;
        }
        float width = layout.width * uiScale;
        float height = layout.height * uiScale;
        float opening = clamp01(openingScale);
        float openingPanelScale = 0.96F + 0.04F * opening;
        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        int accent1 = HUD.getThemeAccentColor();
        int accent2 = HUD.getThemeAccentAltColor();
        int backgroundTop = 0x64000000;
        int backgroundBottom = 0x64000000;
        if (background == RiseTargetHudBackground.TINT) {
            backgroundTop = darkTint(accent1, 128);
            backgroundBottom = darkTint(accent2, 128);
        }

        queuePanelShadow(x, y, width, height, RADIUS * uiScale, openingPanelScale, opening);
        GlStateManager.pushMatrix();
        try {
            applyOpeningScale(centerX, centerY, openingPanelScale);
            drawBackground(x, y, width, height, RADIUS * uiScale, backgroundTop, backgroundBottom, background);
            drawName(target, x, y, uiScale, layout, showAvatar, accent1);
            drawFace(target, x, y, uiScale, partialTicks, showAvatar);
            drawHealth(x, y, uiScale, layout, healthRemainingWidth, accent1, accent2, showAvatar);
        } finally {
            GlStateManager.popMatrix();
            resetRenderState();
        }

        if (particlesEnabled) {
            spawnHurtParticles(target, x, y, uiScale, partialTicks, openingScale, showAvatar);
        }
        drawParticles(openingScale);
    }

    private void drawBackground(float x, float y, float width, float height, float radius,
                                int top, int bottom, RiseTargetHudBackground background) {
        if (background == RiseTargetHudBackground.GLASS) {
            RenderServices.liquidGlass().rounded(x, y, x + width - 1.0F, y + height,
                    radius, top);
        }
        RenderServices.shapes().roundedGradient(x, y, x + width - 1.0F, y + height, radius,
                top, bottom, top, bottom);
    }

    private void drawName(EntityLivingBase target, float x, float y, float uiScale,
                           Layout layout, boolean showAvatar, int accent) {
        float contentX = x + (showAvatar ? EDGE_OFFSET + FACE_SCALE + PADDING : EDGE_OFFSET) * uiScale;
        float nameY = y + 7.0F * uiScale;
        layout.medium.drawStringWithShadow(trim(layout.name, layout.medium, 112.0F * uiScale),
                contentX, nameY, 0xFFF4F0F4);
        String metadata = metadataText(target);
        layout.light.drawString(metadata, contentX, y + 23.0F * uiScale,
                withAlpha(0xFFB6AEB8, 224));
    }

    private void drawFace(EntityLivingBase target, float x, float y, float uiScale,
                          float partialTicks, boolean showAvatar) {
        if (!showAvatar) {
            return;
        }
        float hurtTime = hurtTime(target, partialTicks);
        float faceOffset = hurtTime / 2.0F * uiScale;
        float size = (FACE_SCALE - hurtTime) * uiScale;
        float faceX = x + EDGE_OFFSET * uiScale + faceOffset;
        float faceY = y + EDGE_OFFSET * uiScale + faceOffset;
        float radius = Math.max(3.0F, 6.0F * uiScale);
        RenderServices.shapes().shadow(faceX, faceY, faceX + size, faceY + size, radius,
                withAlpha(0xFF000000, 72), 2, 0.7F * uiScale);
        ResourceLocation skin = target instanceof AbstractClientPlayer
                ? ((AbstractClientPlayer) target).getLocationSkin() : null;
        if (skin == null) {
            RenderServices.shapes().rounded(faceX, faceY, faceX + size, faceY + size,
                    radius, 0xFF1A1A1F);
            return;
        }
        drawRoundedHead(skin, faceX, faceY, size, radius, hurtTime);
    }

    private void drawRoundedHead(ResourceLocation skin, float x, float y, float size,
                                 float radius, float hurtTime) {
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
            float hurt = Math.max(0.0F, Math.min(1.0F, hurtTime / 9.0F));
            GlStateManager.color(1.0F, 1.0F - hurt * 0.65F, 1.0F - hurt * 0.65F, 1.0F);
            minecraft.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(ix, iy, 8.0F, 8.0F, 8, 8, is, is, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(ix, iy, 40.0F, 8.0F, 8, 8, is, is, 64.0F, 64.0F);
        } finally {
            RenderServices.stencil().end();
            GlStateManager.popMatrix();
            resetRenderState();
        }
    }

    private void drawHealth(float x, float y, float uiScale, Layout layout,
                            float healthRemainingWidth, int accent1, int accent2, boolean showAvatar) {
        float contentX = x + (showAvatar ? EDGE_OFFSET + FACE_SCALE + PADDING : EDGE_OFFSET) * uiScale;
        float barY = y + 35.0F * uiScale;
        float barWidth = layout.healthBarWidth * uiScale;
        float barHeight = Math.max(2.0F, 3.0F * uiScale);
        RenderServices.shapes().rounded(contentX, barY, contentX + barWidth, barY + barHeight,
                barHeight * 0.5F, withAlpha(HUD.getThemeSoftBackgroundColor(), 168));
        float filled = Math.max(0.0F, Math.min(barWidth, healthRemainingWidth * uiScale));
        if (filled > 0.01F) {
            RenderServices.shapes().roundedGradient(contentX, barY, contentX + filled, barY + barHeight,
                    barHeight * 0.5F, accent2, accent1, accent2, accent1);
        }
        layout.medium.drawStringWithShadow(layout.healthText,
                contentX + barWidth + INDENT * uiScale,
                y + 32.0F * uiScale, accent1);
    }

    private void queuePanelShadow(float x, float y, float width, float height, float radius,
                                  float scale, float alpha) {
        float resolvedScale = Math.max(0.0F, scale);
        float scaledWidth = width * resolvedScale;
        float scaledHeight = height * resolvedScale;
        float shadowX = x + (width - scaledWidth) * 0.5F;
        float shadowY = y + (height - scaledHeight) * 0.5F;
        float shadowRadius = radius * resolvedScale;
        if (RenderServices.shadows().isFrameOpen()) {
            RenderServices.shadows().queueRoundedRect(shadowX, shadowY,
                    shadowX + scaledWidth, shadowY + scaledHeight, shadowRadius,
                    withAlpha(0xFF000000, Math.round(92.0F * clamp01(alpha))),
                    0.72F, GlowProfile.SHADOW);
        } else {
            RenderServices.shapes().shadow(shadowX, shadowY,
                    shadowX + scaledWidth, shadowY + scaledHeight, shadowRadius,
                    withAlpha(0xFF000000, Math.round(62.0F * clamp01(alpha))), 3, 1.6F);
        }
    }

    private void spawnHurtParticles(EntityLivingBase target, float x, float y, float uiScale,
                                    float partialTicks, float openingScale, boolean showAvatar) {
        if (!showAvatar || target == null) {
            return;
        }
        int hurt = target.hurtTime;
        if (hurt <= 0 || target.getEntityId() != particleTargetId || hurt >= lastParticleHurtTime) {
            particleTargetId = target.getEntityId();
            lastParticleHurtTime = hurt;
            return;
        }
        particleTargetId = target.getEntityId();
        lastParticleHurtTime = hurt;
        float centerX = x + (EDGE_OFFSET + FACE_SCALE * 0.5F) * uiScale;
        float centerY = y + (EDGE_OFFSET + FACE_SCALE * 0.5F) * uiScale;
        float speed = 1.2F * uiScale;
        int first = HUD.getThemeAccentColor();
        int second = HUD.getThemeAccentAltColor();
        for (int i = 0; i < PARTICLE_BURST_COUNT; i++) {
            particles.add(new HurtParticle(centerX, centerY,
                    (random.nextFloat() - 0.5F) * speed, (random.nextFloat() - 0.5F) * speed,
                    (1.4F + random.nextFloat() * 1.4F) * uiScale,
                    mix(first, second, random.nextFloat()), System.currentTimeMillis(), openingScale));
        }
    }

    void onTargetChanged(EntityLivingBase target) {
        particleTargetId = target == null ? Integer.MIN_VALUE : target.getEntityId();
        lastParticleHurtTime = target == null ? 0 : target.hurtTime;
    }

    void reset() {
        particles.clear();
        particleTargetId = Integer.MIN_VALUE;
        lastParticleHurtTime = 0;
    }

    float minimumHealthBarWidth() {
        return MINIMUM_HEALTH_BAR_WIDTH;
    }

    private void drawParticles(float openingScale) {
        long now = System.currentTimeMillis();
        Iterator<HurtParticle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            HurtParticle particle = iterator.next();
            long age = now - particle.createdAt;
            if (age >= PARTICLE_LIFETIME_MS) {
                iterator.remove();
                continue;
            }
            float seconds = age / 1000.0F;
            float alpha = (1.0F - age / (float) PARTICLE_LIFETIME_MS)
                    * clamp01(openingScale) * clamp01(particle.spawnScale);
            float px = particle.x + particle.velocityX * seconds * 12.0F;
            float py = particle.y + particle.velocityY * seconds * 12.0F;
            RenderServices.shapes().circle(px, py, 0, 360, particle.size * 0.5F,
                    withAlpha(particle.color, Math.round(180.0F * alpha)));
            if (RenderServices.glow().isFrameOpen()) {
                RenderServices.glow().queueRoundedRect(px - particle.size * 0.5F, py - particle.size * 0.5F,
                        px + particle.size * 0.5F, py + particle.size * 0.5F, particle.size * 0.5F,
                        withAlpha(particle.color, Math.round(110.0F * alpha)), 0.42F, GlowProfile.ACCENT);
            }
        }
    }

    private static void applyOpeningScale(float centerX, float centerY, float scale) {
        GlStateManager.translate(centerX * (1.0F - scale), centerY * (1.0F - scale), 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
    }

    private static int fontSize(float riseSize, float uiScale) {
        return Math.max(1, Math.round(riseSize * Math.max(0.1F, uiScale)));
    }

    private static String healthText(EntityLivingBase target) {
        float max = target == null ? 20.0F : Math.max(1.0F, target.getMaxHealth());
        float health = target == null ? 15.2F : Math.min(max, Math.max(0.0F, target.getHealth()));
        double rounded = Math.round(health * 10.0D) / 10.0D;
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private String metadataText(EntityLivingBase target) {
        if (target == null || minecraft.thePlayer == null) {
            return "3.2m  ·  --ms";
        }
        return String.format(Locale.ROOT, "%.1fm  ·  target",
                minecraft.thePlayer.getDistanceToEntity(target));
    }

    private static float hurtTime(EntityLivingBase target, float partialTicks) {
        if (target == null || target.hurtTime == 0) {
            return 0.0F;
        }
        return Math.max(0.0F, target.hurtTime - partialTicks) * 0.5F;
    }

    private static int darkTint(int color, int alpha) {
        int red = ((color >> 16) & 255) / 5;
        int green = ((color >> 8) & 255) / 5;
        int blue = (color & 255) / 5;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int mix(int first, int second, float amount) {
        float t = clamp01(amount);
        int r = Math.round(((first >> 16) & 255) + (((second >> 16) & 255) - ((first >> 16) & 255)) * t);
        int g = Math.round(((first >> 8) & 255) + (((second >> 8) & 255) - ((first >> 8) & 255)) * t);
        int b = Math.round((first & 255) + ((second & 255) - (first & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
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

    private static float clamp01(float value) {
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
        final float healthTextWidth;
        final String name;
        final String healthText;
        final CFontRenderer light;
        final CFontRenderer medium;

        Layout(float width, float height, float healthBarWidth, float healthTextWidth,
               String name, String healthText, CFontRenderer light, CFontRenderer medium) {
            this.width = width;
            this.height = height;
            this.healthBarWidth = healthBarWidth;
            this.healthTextWidth = healthTextWidth;
            this.name = name;
            this.healthText = healthText;
            this.light = light;
            this.medium = medium;
        }
    }

    private static final class HurtParticle {
        final float x;
        final float y;
        final float velocityX;
        final float velocityY;
        final float size;
        final int color;
        final long createdAt;
        final float spawnScale;

        HurtParticle(float x, float y, float velocityX, float velocityY, float size,
                     int color, long createdAt, float spawnScale) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.size = size;
            this.color = color;
            this.createdAt = createdAt;
            this.spawnScale = spawnScale;
        }
    }
}

enum RiseTargetHudBackground {
    GLASS,
    TINT
}
