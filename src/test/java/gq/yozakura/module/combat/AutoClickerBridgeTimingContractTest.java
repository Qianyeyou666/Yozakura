package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoClickerBridgeTimingContractTest {
    @Test
    public void attackSchedulingUsesTheSharedMotionPreBoundary() throws IOException {
        String source = source();

        assertTrue(source.contains("import gq.yozakura.event.bridge.UpdateEvent;"));
        assertTrue(source.contains("public void onUpdate(UpdateEvent event)"));
        assertTrue(source.contains("event.getType() == EventType.PRE"));
        assertTrue(source.contains("handleAttackClickOnce();"));
    }

    @Test
    public void syntheticStandaloneTickDoesNotScheduleAttacksAfterTheRealC03() throws IOException {
        String source = source();
        String bridgeTick = method(source, "public void onBridgeTick(", "private void handleAttackClickOnce()");

        assertFalse(bridgeTick.contains("EventType.PRE"));
        assertTrue(bridgeTick.contains("EventType.POST"));
        assertTrue(bridgeTick.contains("handleInventoryClick();"));
    }

    @Test
    public void clickerUsesOnlyTheVanillaClickPathWithoutRandomOrCatchUpBursts() throws IOException {
        String source = source();

        assertTrue(source.contains("MinecraftAccessor.clickMouse(mc)"));
        assertFalse(source.contains("mc.playerController.attackEntity"));
        assertFalse(source.contains("mc.thePlayer.swingItem()"));
        assertFalse(source.contains("new Random"));
        assertFalse(source.contains("while (inventoryNextClickTime <= now)"));
        assertFalse(source.contains("for (int i = 0; i < clicks; i++)"));
        assertFalse(source.contains("keyBindUseItem"));
        assertTrue(source.contains("if (isPointingAtBlock()"
                + " && !Boolean.TRUE.equals(breakBlocks.getValue()))"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/AutoClicker.java")), StandardCharsets.UTF_8);
    }

    private static String method(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin);
        return source.substring(begin, finish);
    }
}
