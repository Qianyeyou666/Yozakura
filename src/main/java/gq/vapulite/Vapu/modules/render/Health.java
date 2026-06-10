package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.ColorUtils;
import gq.vapulite.Vapu.utils.HudDrag;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.font.CFontRenderer;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public class Health extends Module {
    int fuck = 0;
    private int width;
    private final Numbers<Double> xPosition = new Numbers<Double>("X", "X", -1.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> yPosition = new Numbers<Double>("Y", "Y", -1.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0, 0.65, 2.0, 0.05);

    public Health() {
        super("Health", Keyboard.KEY_NONE, ModuleType.Render,"show your health on your screen");
        Chinese="血量显示";
        this.addValues(xPosition, yPosition, scale);
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text event) {
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
            RenderUtil.drawRect(pos[0], pos[1], pos[0] + boxW, pos[1] + boxH, RenderUtil.applyAlpha(0xFF050505, 142));
            RenderUtil.drawHorizontalGradientRect(pos[0], pos[1], pos[0] + boxW, pos[1] + 7.0f,
                    RenderUtil.applyAlpha(0xFFFFFFFF, 16), RenderUtil.applyAlpha(0xFF000000, 0));
            RenderUtil.drawRect(pos[0], pos[1], pos[0] + 2.0f, pos[1] + boxH, RenderUtil.applyAlpha(accent, 190));
            RenderUtil.drawRect(pos[0] + 7.0f, pos[1] + 5.0f, pos[0] + 21.0f, pos[1] + 17.0f,
                    RenderUtil.applyAlpha(0xFF111111, 170));
            FontLoaders.I14.drawString(FontLoaders.ICON_HEARTBEAT, pos[0] + 10.0f, pos[1] + 7.0f,
                    RenderUtil.applyAlpha(accent, 245));
            FontLoaders.C14.drawString(value, pos[0] + 27.0f, pos[1] + 4.0f, 0xFFFFFFFF);
            RenderUtil.drawRect(pos[0] + 27.0f, pos[1] + 16.0f, pos[0] + boxW - 6.0f, pos[1] + 18.0f,
                    RenderUtil.applyAlpha(0xFF242424, 170));
            RenderUtil.drawRect(pos[0] + 27.0f, pos[1] + 16.0f,
                    pos[0] + 27.0f + (boxW - 33.0f) * healthRatio, pos[1] + 18.0f,
                    RenderUtil.applyAlpha(accent, 210));
        } finally {
            GlStateManager.popMatrix();
        }
        HudDrag.drawHint("health_display", pos[0], pos[1], boxW * uiScale, boxH * uiScale, 2.0f * uiScale);
    }
}
