package gq.vapulite.module.combat;

import gq.vapulite.core.Client;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import static net.minecraft.realms.RealmsMth.sqrt;
import static net.minecraft.realms.RealmsMth.wrapDegrees;

public class AutoBlock extends Module {
    public AutoBlock() {
        super("AutoBlock", Keyboard.KEY_NONE, ModuleType.Combat);
        Chinese="自动格挡";
    }


    @Override
    public void enable(){
        Client.AutoBlock = true;
    }

    @Override
    public void disable(){
        Client.AutoBlock = false;
    }

}
