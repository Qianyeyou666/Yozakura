package gq.vapulite.module.render;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;

public class FullBright extends Module {
    private float old;
    public FullBright() {
        super("FullBright", Keyboard.KEY_NONE, ModuleType.Render,"Make the bright for night and dark");
        Chinese="夜视";
    }

    @Override
    public void enable() {
        this.old = mc.gameSettings.gammaSetting;
    }

    @SubscribeEvent
    public void onUpdate(TickEvent.ClientTickEvent event) {
        Minecraft.getMinecraft().gameSettings.gammaSetting = 300;
    }


    @Override
    public void disable() {
        Minecraft.getMinecraft().gameSettings.gammaSetting = this.old;
    }
}
