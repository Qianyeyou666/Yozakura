package gq.yozakura.module.player;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class ChestStealerTest {
    @Test
    public void onlyARecentlyInteractedPhysicalChestCanAuthorizeTheWindow() throws IOException {
        String source = source();

        assertTrue(source.contains("event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK"));
        assertTrue(source.contains("block instanceof BlockChest || block instanceof BlockEnderChest"));
        assertTrue(source.contains("chestAccess.authorizeWindow(chest.inventorySlots.windowId"));
    }

    @Test
    public void inventoryNameRemainsAnAdditionalOptionalCheck() throws IOException {
        String source = source();

        assertTrue(source.contains("if (!Boolean.TRUE.equals(nameCheck.getValue()))"));
        assertTrue(source.contains("name.contains(\"Chest\")"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/player/ChestStealer.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
