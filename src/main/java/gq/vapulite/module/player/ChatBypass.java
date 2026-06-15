package gq.vapulite.module.player;

import gq.vapulite.core.Client;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.util.minecraft.Helper;
import org.lwjgl.input.Keyboard;

public class ChatBypass extends Module {
    public ChatBypass() {
        super("ChatBypass", Keyboard.KEY_NONE, ModuleType.Player,"");
        Chinese="聊天绕过";
    }

    @Override
    public void enable() {
        Helper.sendMessage("使用/cp <Message>发送ChatBypassed消息");
        Client.ChatBypass = true;
    }

    @Override
    public void disable() {
        Client.ChatBypass = false;
    }



}
