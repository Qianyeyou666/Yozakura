package gq.yozakura.util.minecraft;

import gq.yozakura.core.YozakuraClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class Helper {
    public static Minecraft mc = Minecraft.getMinecraft();

    public static void sendMessage(String message) {
        try {
            message = "[" + YozakuraClientState.getName() + "] " + message;
            new ChatUtil.ChatMessageBuilder(true, true)
                    .appendText(message)
                    .setColor(EnumChatFormatting.LIGHT_PURPLE)
                    .build()
                    .displayClientSided();
        } catch (Throwable throwable) {
            logChatFailure(message, throwable);
        }
    }

    public static boolean onServer(String server) {
        return server != null
                && !mc.isSingleplayer()
                && Helper.mc.getCurrentServerData() != null
                && Helper.mc.getCurrentServerData().serverIP != null
                && Helper.mc.getCurrentServerData().serverIP.toLowerCase().contains(server.toLowerCase());
    }

    public static void sendMessageWithoutPrefix(String string) {
        try {
            if (Minecraft.getMinecraft().ingameGUI != null && Minecraft.getMinecraft().ingameGUI.getChatGUI() != null) {
                Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(string));
            }
        } catch (Throwable throwable) {
            logChatFailure(string, throwable);
        }
    }

    private static void logChatFailure(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraChat.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                throwable.printStackTrace(writer);
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

}
