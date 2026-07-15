package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards FastPlace's block-placement boundary during refactors.
 */
public class FastPlaceContractTest {
    @Test
    public void onlyConfiguresCooldownForBlockPlacementByDefault() throws IOException {
        String fastPlace = source("src/main/java/gq/yozakura/module/world/FastPlace.java");

        assertTrue(fastPlace.contains("new Numbers<Integer>(\"Delay\", \"Delay\", 0, 0, 4, 1);"));
        assertTrue(fastPlace.contains("new Option<Boolean>(\"Only Blocks\", \"OnlyBlocks\", true);"));
        assertTrue(fastPlace.contains("this.addValues(delay, onlyBlocks);"));
        assertTrue(fastPlace.contains("mc.gameSettings.keyBindUseItem.isKeyDown()"));
        assertTrue(fastPlace.contains("itemStack.stackSize > 0"));
        assertTrue(fastPlace.contains("itemStack.getItem() instanceof ItemBlock"));
        assertTrue(fastPlace.contains("FastPlacePolicy.shouldCapCooldown"));
        assertTrue(fastPlace.contains("FastPlacePolicy.normalizeDelayTicks(delay.getValue())"));
        assertTrue(fastPlace.contains("MinecraftAccessor.capRightClickDelayTimer(mc, getDelayTicks())"));
        assertTrue(fastPlace.contains("reportAccessFailure(exception)"));
        assertFalse(fastPlace.contains("ThreadLocalRandom"));
        assertFalse(fastPlace.contains("TimerUtil"));
    }

    @Test
    public void keepsOneCustomPreTickOwnerWithoutSynthesizingUseInput() throws IOException {
        String fastPlace = source("src/main/java/gq/yozakura/module/world/FastPlace.java");

        assertTrue(fastPlace.contains("import gq.yozakura.event.bridge.TickEvent;"));
        assertTrue(fastPlace.contains("import gq.yozakura.event.bus.EventTarget;"));
        assertTrue(fastPlace.contains("public void onTick(TickEvent event)"));
        assertTrue(fastPlace.contains("@EventTarget"));
        assertTrue(fastPlace.contains("event.getType() != EventType.PRE"));
        assertFalse(fastPlace.contains("net.minecraftforge"));
        assertFalse(fastPlace.contains("gq.yozakura.bridge.forge"));
        assertFalse(fastPlace.contains("SubscribeEvent"));
        assertFalse(fastPlace.contains("KeyBinding.onTick"));
        assertFalse(fastPlace.contains("swingItem()"));
    }

    @Test
    public void accessorResolvesTheRightClickDelayAcrossRuntimeMappings() throws IOException {
        String accessor = source("src/main/java/gq/yozakura/bridge/MinecraftAccessor.java");

        assertTrue(accessor.contains("public static void capRightClickDelayTimer"));
        assertTrue(accessor.contains("findField(Minecraft.class, \"rightClickDelayTimer\", \"field_71467_ac\", \"ap\")"));
        assertTrue(accessor.contains("Unable to resolve Minecraft right-click delay"));
        assertTrue(accessor.contains("rightClickDelayTimerFailure"));
        assertTrue(accessor.contains("if (field.getInt(minecraft) > maximumDelay)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
