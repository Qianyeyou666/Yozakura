package gq.yozakura.module.player;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoToolsContractTest {
    @Test
    public void usesTheUnifiedTickBridgeAndSynchronizesOwnedSlotChanges() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/player/AutoTools.java");

        assertTrue(source.contains("import gq.yozakura.event.bridge.TickEvent;"));
        assertTrue(source.contains("event.getType() != EventType.PRE"));
        assertTrue(source.contains("MinecraftAccessor.syncCurrentPlayItem(mc.playerController)"));
        assertTrue(source.contains("AutoToolsPolicy.bestSlot"));
        assertTrue(source.contains("Enchantment.efficiency.effectId"));
        assertTrue(source.contains("AutoToolsPolicy.shouldRestore"));
        assertTrue(source.contains("public void onLeftClick(LeftClickMouseEvent event)"));
        assertTrue(source.contains("public void onAttack(AttackEvent event)"));
        assertTrue(source.contains("@EventTarget(Priority.HIGHEST)"));
        assertTrue(source.contains("MovingObjectPosition.MovingObjectType.ENTITY"));
        assertTrue(source.contains("Enchantment.sharpness.effectId"));
        assertTrue(source.contains("getAttributeModifiers()"));
        assertFalse(source.contains("net.minecraftforge"));
        assertFalse(source.contains("SubscribeEvent"));
        assertFalse(source.contains("BlockUtils.updateTool"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
