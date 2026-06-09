package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.utils.ColorUtils;
import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HUD extends Module {
    public HUD() {
        super("HUD", Keyboard.KEY_H, ModuleType.Render,"Show " + Client.name + " HUD Screen");
        Chinese="HUD界面";
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text event) {
        ScaledResolution s = new ScaledResolution(mc);
        int width = new ScaledResolution(mc).getScaledWidth();
        int height = new ScaledResolution(mc).getScaledHeight();
        int y = 1;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiMainMenu)) return;
        FontLoaders.C18.drawStringWithShadow(Client.name,2,2, ColorUtils.rainbow(1));
        List<Module> modules = Client.instance.moduleManager.getEnabledModules();
        modules.sort(new Comparator<Module>() {
            @Override
            public int compare(Module o1, Module o2) {
                return FontLoaders.C18.getStringWidth(getDisplayName(o2)) - FontLoaders.C18.getStringWidth(getDisplayName(o1));
            }
        });
        int i = 0;
        for (Module m : modules) {
            if (m != null) {
                String displayName = getDisplayName(m);
                int moduleWidth = FontLoaders.C18.getStringWidth(displayName);
                FontLoaders.C18.drawString(displayName, width - moduleWidth - 1, y, ColorUtils.rainbow(2)+i);
                y += FontLoaders.C18.getHeight();
            }
        }
    }

    private static String getDisplayName(Module module) {
        return Client.CHINESE ? module.getChinese() : module.getName();
    }
}
