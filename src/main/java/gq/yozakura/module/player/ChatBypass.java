package gq.yozakura.module.player;

import gq.yozakura.core.Client;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.minecraft.Helper;
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
