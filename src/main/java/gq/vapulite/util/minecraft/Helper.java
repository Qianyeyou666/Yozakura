package gq.vapulite.util.minecraft;

import gq.vapulite.core.Client;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class Helper {
    public static Minecraft mc = Minecraft.getMinecraft();

    public static void sendMessage(String message) {
        message = "["+Client.name+"] " + message;
        new ChatUtil.ChatMessageBuilder(true, true).appendText(message).setColor(EnumChatFormatting.LIGHT_PURPLE).build().displayClientSided();
    }

    public static boolean onServer(String server) {
        return server != null
                && !mc.isSingleplayer()
                && Helper.mc.getCurrentServerData() != null
                && Helper.mc.getCurrentServerData().serverIP != null
                && Helper.mc.getCurrentServerData().serverIP.toLowerCase().contains(server.toLowerCase());
    }

    public static void sendMessageWithoutPrefix(String string) {
        if (Minecraft.getMinecraft().ingameGUI != null && Minecraft.getMinecraft().ingameGUI.getChatGUI() != null) {
            Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(string));
        }
    }

}
