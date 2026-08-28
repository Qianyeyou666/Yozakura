package gq.yozakura.module.player;

import java.util.Locale;

/** Prevents inventory automation from treating server navigation GUIs as storage. */
final class ServerContainerPolicy {
    private static final String[] SERVER_MENU_MARKERS = {
            "game menu", "select a mode", "selector", "quick play", "quickplay",
            "shop", "cosmetic", "profile", "collectible", "achievement", "quest",
            "teleporter", "delivery man", "play bed wars", "play skywars", "play duels"
    };

    private ServerContainerPolicy() {
    }

    static boolean canStealFrom(String title, boolean allowCustomChest) {
        if (title == null || isServerMenu(title)) {
            return false;
        }
        return allowCustomChest || ChestStealerPolicy.isStandardChestTitle(title);
    }

    static boolean isPlayerInventoryContext(boolean controllerAvailable, boolean cursorEmpty,
                                             boolean mouseDown, boolean screenIsContainer,
                                             boolean screenIsPlayerInventory,
                                             boolean ownInventoryContainerOpen) {
        return controllerAvailable && cursorEmpty && !mouseDown
                && (!screenIsContainer || screenIsPlayerInventory)
                && ownInventoryContainerOpen;
    }

    private static boolean isServerMenu(String title) {
        String normalized = title.trim().toLowerCase(Locale.ROOT);
        for (String marker : SERVER_MENU_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
