package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.font.FontLoaders;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.awt.*;


public class Test extends Module {
    public Test() {
        super("Test", Keyboard.KEY_NONE, ModuleType.Render, "Test");
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text renderGameOverlayEvent) {
        FontLoaders.C16.drawString("this is a test font", 0f, 0f, new Color(255,255,255).getRGB());
    }
}
