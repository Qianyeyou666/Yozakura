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
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Source-faithful adapter for Nymphilila's Strife TargetHUD. */
final class NymphTargetHudRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();

    Layout measure(EntityLivingBase target) {
        CFontRenderer measureFont = FontLoaders.productSans(15);
        CFontRenderer nameFont = FontLoaders.productSans(18);
        String name = target == null ? "Steve" : target.getName();
        float width = NymphTargetHudLayout.width(measureFont.getStringWidth(name));
        return new Layout(width, NymphTargetHudLayout.HEIGHT, nameFont);
    }

    void draw(EntityLivingBase target, float x, float y, float uiScale,
              NymphTargetHudMotion.Snapshot motion, float animatedHealth,
              int backgroundAlpha, boolean showAvatar) {
        if (target == null || motion == null || !motion.isRetained()
                || motion.getOpacity() <= 0.0F || motion.getScale() <= 0.0F) {
            return;
        }
        Layout layout = measure(target);
        float scale = Math.max(0.1F, uiScale);
        queueBloomMask(layout, x, y, scale, motion);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            GlStateManager.translate(-x, -y, 0.0F);
            applyOpeningScale(x + layout.width * 0.5F, y + layout.height * 0.5F,
                    motion.getScale());
            drawContent(target, layout, x, y, motion.getOpacity(), animatedHealth,
                    backgroundAlpha, showAvatar);
        } finally {
            GlStateManager.popMatrix();
            resetRenderState();
            RenderServices.markHudEffectsStateChanged();
        }
    }

    /** Replays the source DoAFuckingBloomEvent mask pass at the animated panel bounds. */
    private void queueBloomMask(Layout layout, float x, float y, float scale,
                                NymphTargetHudMotion.Snapshot motion) {
        float openingScale = motion.getScale();
        float animatedScale = motion.getScale() * scale;
        float left = x + layout.width * scale * (1.0F - openingScale) * 0.5F;
        float top = y + layout.height * scale * (1.0F - openingScale) * 0.5F;
        float right = left + layout.width * animatedScale;
        float bottom = top + layout.height * animatedScale;
        int maskAlpha = Math.max(0, Math.min(255, Math.round(255.0F * motion.getOpacity())));
        if (right <= left || bottom <= top || maskAlpha <= 0) {
            return;
        }

        GlowRenderer shadows = RenderServices.shadows();
        boolean isolatedFrame = !shadows.isFrameOpen();
        if (isolatedFrame) {
            shadows.beginFrame();
            shadows.beginCommandSnapshotCache();
        }
        try {
            shadows.queueRoundedRect(left, top, right, bottom,
                    NymphTargetHudLayout.RADIUS * animatedScale,
                    withAlpha(0xFF000000, maskAlpha), 1.0F, GlowProfile.SHADOW);
        } finally {
            if (isolatedFrame) {
                shadows.flush();
            }
        }
    }

    private void drawContent(EntityLivingBase target, Layout layout, float x, float y,
                             float opacity, float animatedHealth, int backgroundAlpha,
                             boolean showAvatar) {
        int alpha = Math.max(0, Math.min(255, Math.round(255.0F * opacity)));
        int panelAlpha = Math.round(Math.max(0, Math.min(255, backgroundAlpha)) * opacity);
        RenderServices.shapes().rounded(x, y, x + layout.width, y + layout.height,
                NymphTargetHudLayout.RADIUS, withAlpha(0xFF141414, panelAlpha));

        float contentX = x;
        ResourceLocation skin = showAvatar && target instanceof AbstractClientPlayer
                ? ((AbstractClientPlayer) target).getLocationSkin() : null;
        if (skin != null) {
            drawHead(skin, x + 2.0F, y + 2.0F, NymphTargetHudLayout.AVATAR_SIZE, opacity);
            contentX += NymphTargetHudLayout.AVATAR_ADVANCE;
        }

        String name = target.getName();
        layout.nameFont.drawString(name, contentX + 6.0F, y + 4.0F,
                withAlpha(0xFFFFFFFF, alpha));
        float ratio = clamp01(animatedHealth);
        float actualRatio = clamp01(target.getHealth() / Math.max(1.0F, target.getMaxHealth()));
        float healthWidth = NymphTargetHudLayout.healthBarWidth(layout.width);
        float barRight = contentX + 3.0F + healthWidth * ratio;
        if (barRight > contentX + 5.0F) {
            RenderServices.shapes().rect(contentX + 5.0F, y + layout.height - 4.0F,
                    barRight, y + layout.height - 2.0F,
                    withAlpha(NymphTargetHudLayout.healthColor(actualRatio), alpha));
        }
        drawEquipment(target, contentX,
                y + 4.0F + layout.nameFont.getHeight() + 2.0F, opacity);
    }

    private void drawHead(ResourceLocation skin, float x, float y, float size, float opacity) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, clamp01(opacity));
            minecraft.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(Math.round(x), Math.round(y),
                    8.0F, 8.0F, 8, 8, Math.round(size), Math.round(size), 64.0F, 64.0F);
        } finally {
            GlStateManager.popMatrix();
            resetRenderState();
        }
    }

    private void drawEquipment(EntityLivingBase target, float contentX, float y, float opacity) {
        int visibleSlot = 0;
        for (int sourceSlot = 0; sourceSlot < 5; sourceSlot++) {
            ItemStack stack = equipment(target, sourceSlot);
            if (stack == null) {
                continue;
            }
            boolean armor = stack.getItem() instanceof ItemArmor;
            float itemX = NymphTargetHudLayout.itemX(contentX, visibleSlot, armor);
            drawItem(stack, itemX, y, opacity);
            visibleSlot++;
        }
    }

    private static ItemStack equipment(EntityLivingBase target, int sourceSlot) {
        if (sourceSlot < 4) {
            return target.getCurrentArmor(3 - sourceSlot);
        }
        return target.getHeldItem();
    }

    private void drawItem(ItemStack stack, float x, float y, float opacity) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableRescaleNormal();
            GlStateManager.color(1.0F, 1.0F, 1.0F, clamp01(opacity));
            RenderHelper.enableGUIStandardItemLighting();
            minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, Math.round(x), Math.round(y));
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.popMatrix();
            resetRenderState();
        }
    }

    private static void applyOpeningScale(float centerX, float centerY, float scale) {
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

        Layout(float width, float height, CFontRenderer nameFont) {
            this.width = width;
            this.height = height;
            this.nameFont = nameFont;
        }
    }
}
