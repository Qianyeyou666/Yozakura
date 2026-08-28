package gq.yozakura.util.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class ServerUtil {
    private ServerUtil() {
    }

    public static boolean isHypixel() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return false;
        }
        ServerData server = minecraft.getCurrentServerData();
        return server != null && isHypixelAddress(server.serverIP);
    }

    static boolean isHypixelAddress(String address) {
        if (address == null) {
            return false;
        }
        String host = address.trim().toLowerCase(java.util.Locale.ROOT);
        if (host.isEmpty()) {
            return false;
        }
        if (host.charAt(0) == '[') {
            int closing = host.indexOf(']');
            if (closing < 0) {
                return false;
            }
            host = host.substring(1, closing);
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0 && colon == host.lastIndexOf(':')) {
                host = host.substring(0, colon);
            }
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
}
