package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class SpeedMineContractTest {
    @Test
    public void speedMineUsesARealStartBoundaryAndSendsOneMatchingStopPacket() throws IOException {
        String speedMine = source("src/main/java/gq/yozakura/module/world/SpeedMine.java");
        String accessor = source("src/main/java/gq/yozakura/bridge/MinecraftAccessor.java");
        String manager = source("src/main/java/gq/yozakura/manager/ModuleManager.java");

        assertTrue(speedMine.contains("SpeedMinePolicy.extraDamage"));
        assertTrue(speedMine.contains("SpeedMinePolicy.shouldFinish"));
        assertTrue(speedMine.contains("MinecraftAccessor.setBlockHitDelay(mc.playerController, 0)"));
        assertTrue(speedMine.contains("MinecraftAccessor.setCurrentBlockDamage"));
        assertTrue(speedMine.contains("block.getPlayerRelativeBlockHardness"));
        assertTrue(speedMine.contains("C07PacketPlayerDigging.Action.START_DESTROY_BLOCK"));
        assertTrue(speedMine.contains("C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK"));
        assertTrue(speedMine.contains("C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK"));
        assertTrue(speedMine.contains("PacketUtil.sendPacket(new C07PacketPlayerDigging("));
        assertTrue(speedMine.contains("digSession.finish("));

        assertTrue(accessor.contains("public static void setBlockHitDelay"));
        assertTrue(accessor.contains("public static void setCurrentBlockDamage"));
        assertTrue(manager.contains("addModule(\"SpeedMine\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
