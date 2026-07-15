package gq.yozakura.module.render;

import gq.yozakura.bridge.YozakuraEventBridge;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.color.ColorUtils;
import gq.yozakura.util.render.HudDrag;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.util.animation.UiClock;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public class Health extends Module {
    private static final VisualPalette NIGHT_BLOOM = VisualPalette.nightBloom();
    private static final float NIGHT_BLOOM_RADIUS = 4.0F;
    private static final int NIGHT_BLOOM_SURFACE = 0xDC16161A;
    private static final int NIGHT_BLOOM_PRIMARY = 0xFFFF4FC7;

    int fuck = 0;
    private int width;
    private final Numbers<Double> xPosition = new Numbers<Double>("X", "X", -1.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> yPosition = new Numbers<Double>("Y", "Y", -1.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0, 0.65, 2.0, 0.05);
    private final NightBloomHealthMotion nightBloomMotion = new NightBloomHealthMotion();
    private final UiClock nightBloomClock = new UiClock();

    public Health() {
        super("Health", Keyboard.KEY_NONE, ModuleType.Render,"show your health on your screen");
        Chinese="血量显示";
        this.addValues(xPosition, yPosition, scale);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        renderOverlay();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (!YozakuraEventBridge.hasRenderedOverlayThisFrame()) {
            renderOverlay();
        }
    }

    private void renderOverlay() {
        if (!isInGame()) {
            return;
        }
        if (mc.thePlayer.getHealth() >= 0.0f && mc.thePlayer.getHealth() < 10.0f) {
            this.width = 3;
        }
        if (mc.thePlayer.getHealth() >= 10.0f && mc.thePlayer.getHealth() < 100.0f) {
            this.width = 5;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        if (HUD.getActiveStyle() == HUD.HudStyle.NIGHT_BLOOM) {
            drawNightBloomHealth(sr);
            return;
        }
        nightBloomClock.reset();
        nightBloomMotion.reset();
        String valueText = String.valueOf(MathHelper.ceiling_float_int(mc.thePlayer.getHealth()));
        if (HUD.useVapeSimpleStyle()) {
            drawVapeHealth(sr);
            return;
        }

        float uiScale = scale.getValue().floatValue();
        CFontRenderer iconFont = FontLoaders.I14;
        CFontRenderer valueFont = FontLoaders.C14;
        float iconW = iconFont.getStringWidth(FontLoaders.ICON_HEARTBEAT);
        float boxW = iconW + valueFont.getStringWidth(valueText) + 12.0f;
        float boxH = 13.0f;
        float[] pos = HudDrag.update("health_display", xPosition, yPosition, scale,
                sr.getScaledWidth() / 2.0f - this.width, sr.getScaledHeight() / 2.0f - 15.0f,
                boxW * uiScale, boxH * uiScale, sr);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(pos[0], pos[1], 0.0f);
            GlStateManager.scale(uiScale, uiScale, 1.0f);
            GlStateManager.translate(-pos[0], -pos[1], 0.0f);
            iconFont.drawString(FontLoaders.ICON_HEARTBEAT, pos[0] + 3.0f, pos[1] + 3.5f,
                    RenderUtil.applyAlpha(0xFFFF5A6C, 245));
            valueFont.drawString(valueText, pos[0] + iconW + 8.0f, pos[1] + 3.0f, 0xFFFFFFFF);
        } finally {
            GlStateManager.popMatrix();
        }
        HudDrag.drawHint("health_display", pos[0], pos[1], boxW * uiScale, boxH * uiScale, 3.0f * uiScale);
        HudDrag.handleScroll("health_display", scale, pos[0], pos[1], boxW * uiScale, boxH * uiScale, 0.65f, 2.0f);
    }

    private void drawNightBloomHealth(ScaledResolution sr) {
        float uiScale = scale.getValue().floatValue();
        float current = Math.max(0.0F, mc.thePlayer.getHealth());
        float maximum = Math.max(1.0F, mc.thePlayer.getMaxHealth());
        float healthRatio = MathHelper.clamp_float(current / maximum, 0.0F, 1.0F);
        NightBloomHealthMotion.Snapshot motion = nightBloomMotion.update(healthRatio,
                nightBloomClock.tick(System.nanoTime()));
        String valueText = Math.round(current) + "/" + Math.round(maximum);
        float boxW = Math.max(88.0F, FontLoaders.C14.getStringWidth(valueText) + 48.0F);
        float boxH = 26.0F;
        float[] pos = HudDrag.updateDocked("health_display", xPosition, yPosition, scale,
                sr.getScaledWidth() / 2.0F - this.width, sr.getScaledHeight() / 2.0F - 15.0F,
                boxW * uiScale, boxH * uiScale, NIGHT_BLOOM_RADIUS * uiScale, sr);

        NightBloomHudDockRenderer.drawPanel("health_display", pos[0], pos[1], boxW * uiScale, boxH * uiScale,
                NIGHT_BLOOM_RADIUS * uiScale, 1.0F, NIGHT_BLOOM_SURFACE);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(pos[0], pos[1], 0.0F);
            GlStateManager.scale(uiScale, uiScale, 1.0F);
            GlStateManager.translate(-pos[0], -pos[1], 0.0F);

            float x = pos[0];
            float y = pos[1];
            int healthColor = NightBloomHealthMotion.colorFor(motion.getHealth(), NIGHT_BLOOM);

            float iconSize = 18.0F;
            float iconX = x + 6.0F;
            float iconY = y + 4.0F;
            RenderServices.shapes().rounded(iconX, iconY, iconX + iconSize, iconY + iconSize,
                    NIGHT_BLOOM_RADIUS, multiplyAlpha(NIGHT_BLOOM_PRIMARY, 0.16F));
            HUD.drawNightBloomCenteredIcon(FontLoaders.ICON_HEARTBEAT, FontLoaders.I14,
                    iconX + iconSize * 0.5F, iconY + iconSize * 0.5F,
                    NIGHT_BLOOM_PRIMARY, multiplyAlpha(NIGHT_BLOOM_PRIMARY, 0.72F), 0.60F);
            HUD.drawNightBloomText(FontLoaders.C14, valueText, x + 31.0F, y + 4.0F,
                    multiplyAlpha(NIGHT_BLOOM.getTextPrimary(), 1.0F),
                    multiplyAlpha(NIGHT_BLOOM_PRIMARY, 0.42F), 0.34F);

            float barX = x + 31.0F;
            float barY = y + 17.0F;
            float barW = boxW - 39.0F;
            float barH = 3.2F;
            float barRadius = barH * 0.5F;
            RenderServices.shapes().rounded(barX, barY, barX + barW, barY + barH, barRadius,
                    multiplyAlpha(NIGHT_BLOOM.getSurfaceOverlay(), 0.88F));
            float damageWidth = barW * motion.getDamageTrail();
            if (damageWidth > 0.35F) {
                RenderServices.shapes().rounded(barX, barY, barX + damageWidth, barY + barH, barRadius,
                        multiplyAlpha(NIGHT_BLOOM.getHealthDamageTrail(), 0.92F));
            }
            float healthWidth = barW * motion.getHealth();
            if (healthWidth > 0.35F) {
                RenderServices.shapes().rounded(barX, barY, barX + healthWidth, barY + barH, barRadius,
                        multiplyAlpha(healthColor, 1.0F));
            }
        } finally {
            GlStateManager.popMatrix();
        }
        HudDrag.drawDockHint("health_display", pos[0], pos[1], boxW * uiScale, boxH * uiScale,
                NIGHT_BLOOM_RADIUS * uiScale);
        HudDrag.handleScroll("health_display", scale, pos[0], pos[1], boxW * uiScale, boxH * uiScale, 0.65F, 2.0F);
    }

    private void drawVapeHealth(ScaledResolution sr) {
        float uiScale = scale.getValue().floatValue();
        float current = Math.max(0.0f, mc.thePlayer.getHealth());
        float max = Math.max(1.0f, mc.thePlayer.getMaxHealth());
        float healthRatio = Math.max(0.0f, Math.min(1.0f, current / max));
        String value = Math.round(current) + "/" + Math.round(max);
        float boxW = Math.max(84.0f, FontLoaders.C14.getStringWidth(value) + 42.0f);
        float boxH = 22.0f;
        float[] pos = HudDrag.update("health_display", xPosition, yPosition, scale,
                sr.getScaledWidth() / 2.0f - boxW / 2.0f, sr.getScaledHeight() / 2.0f - 16.0f,
                boxW * uiScale, boxH * uiScale, sr);
        int accent = ColorUtils.interpolate(0xFFFF5A5A, 0xFFFFD35C, healthRatio);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(pos[0], pos[1], 0.0f);
            GlStateManager.scale(uiScale, uiScale, 1.0f);
            GlStateManager.translate(-pos[0], -pos[1], 0.0f);
            RenderServices.shapes().rect(pos[0], pos[1], pos[0] + boxW, pos[1] + boxH, RenderUtil.applyAlpha(0xFF050505, 142));
            RenderServices.shapes().horizontalGradient(pos[0], pos[1], pos[0] + boxW, pos[1] + 7.0f,
                    RenderUtil.applyAlpha(0xFFFFFFFF, 16), RenderUtil.applyAlpha(0xFF000000, 0));
            RenderServices.shapes().rect(pos[0], pos[1], pos[0] + 2.0f, pos[1] + boxH, RenderUtil.applyAlpha(accent, 190));
            RenderServices.shapes().rect(pos[0] + 7.0f, pos[1] + 5.0f, pos[0] + 21.0f, pos[1] + 17.0f,
                    RenderUtil.applyAlpha(0xFF111111, 170));
            FontLoaders.I14.drawString(FontLoaders.ICON_HEARTBEAT, pos[0] + 10.0f, pos[1] + 7.0f,
                    RenderUtil.applyAlpha(accent, 245));
            FontLoaders.C14.drawString(value, pos[0] + 27.0f, pos[1] + 4.0f, 0xFFFFFFFF);
            RenderServices.shapes().rect(pos[0] + 27.0f, pos[1] + 16.0f, pos[0] + boxW - 6.0f, pos[1] + 18.0f,
                    RenderUtil.applyAlpha(0xFF242424, 170));
            RenderServices.shapes().rect(pos[0] + 27.0f, pos[1] + 16.0f,
                    pos[0] + 27.0f + (boxW - 33.0f) * healthRatio, pos[1] + 18.0f,
                    RenderUtil.applyAlpha(accent, 210));
        } finally {
            GlStateManager.popMatrix();
        }
        HudDrag.drawHint("health_display", pos[0], pos[1], boxW * uiScale, boxH * uiScale, 2.0f * uiScale);
        HudDrag.handleScroll("health_display", scale, pos[0], pos[1], boxW * uiScale, boxH * uiScale, 0.65f, 2.0f);
    }

    @Override
    public void disable() {
        HudDrag.unregisterDocked("health_display");
    }

    private static int multiplyAlpha(int color, float alpha) {
        int sourceAlpha = color >>> 24 & 255;
        int resolvedAlpha = Math.round(sourceAlpha * Math.max(0.0F, Math.min(1.0F, alpha)));
        return color & 0x00FFFFFF | resolvedAlpha << 24;
    }
}
