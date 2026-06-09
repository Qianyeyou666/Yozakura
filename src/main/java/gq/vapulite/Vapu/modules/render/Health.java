package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.ColorUtils;
import gq.vapulite.Vapu.utils.HudDrag;
import gq.vapulite.Vapu.value.Numbers;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;

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
        String text = "♥" + MathHelper.ceiling_float_int(mc.thePlayer.getHealth());
        float uiScale = scale.getValue().floatValue();
        float boxW = mc.fontRendererObj.getStringWidth(text) + 6.0f;
        float boxH = 12.0f;
        float[] pos = HudDrag.update("health_display", xPosition, yPosition, scale,
                sr.getScaledWidth() / 2.0f - this.width, sr.getScaledHeight() / 2.0f - 15.0f,
                boxW * uiScale, boxH * uiScale, sr);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(pos[0], pos[1], 0.0f);
            GlStateManager.scale(uiScale, uiScale, 1.0f);
            GlStateManager.translate(-pos[0], -pos[1], 0.0f);
            mc.fontRendererObj.drawStringWithShadow(text, pos[0] + 3.0f, pos[1] + 2.0f, -1);
        } finally {
            GlStateManager.popMatrix();
        }
        HudDrag.drawHint("health_display", pos[0], pos[1], boxW * uiScale, boxH * uiScale, 3.0f * uiScale);
    }

}
