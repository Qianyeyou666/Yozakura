package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectileWarningContractTest {
    @Test
    public void moduleOnlyWiresFireballAndBedWarnings() throws IOException {
        String module = source("src/main/java/gq/yozakura/module/render/ProjectileWarning.java");
        String manager = source("src/main/java/gq/yozakura/manager/ModuleManager.java");

        assertTrue(manager.contains("addModule(\"ProjectileWarning\""));
        assertFalse(module.contains("\"Projectile Ray\""));
        assertFalse(module.contains("instanceof EntityArrow"));
        assertFalse(module.contains("instanceof EntityThrowable"));
        assertTrue(module.contains("instanceof EntityFireball"));
        assertTrue(module.contains("instanceof EntityWitherSkull"));
        assertTrue(module.contains("ProjectileWarningPolicy.referenceRayEnd("));
        assertTrue(module.contains("ProjectileWarningPolicy.hasReferenceFireballMotion("));
        assertTrue(module.contains("ProjectileWarningPolicy.isInsideReferenceWarningBox("));
        assertTrue(module.contains("ProjectileWarningPolicy.referenceEtaSeconds("));
        assertTrue(module.contains("ProjectileWarningPolicy.referenceDistanceColor("));
        assertTrue(module.contains("playerDistance < nearestImpactDistance"));
        assertTrue(module.contains("RenderUtil.drawLine3D("));
        assertTrue(module.contains("ProjectileWarningPolicy.inferredExplosionStrength("));
        assertTrue(module.contains("ProjectileWarningPolicy.isPredictedDestroyedBlock("));
        assertTrue(module.contains("renderPredictedDestroyedBlocks("));
        assertTrue(module.contains("block.getExplosionResistance("));
        assertTrue(module.contains("RenderUtil.drawBox("));
        assertTrue(module.contains("RenderUtil.drawOutlinedBox("));
        assertTrue(module.contains("ProjectileWarningPolicy.selectNearestBedThreat("));
        assertTrue(module.contains("ProjectileWarningPolicy.bedWarsStatus("));
        assertTrue(module.contains("ProjectileWarningPolicy.bedAlarmProgress("));
        assertTrue(module.contains("BlockBed.EnumPartType.FOOT"));
        assertTrue(module.contains("Mouse.isButtonDown(2)"));
        assertTrue(module.contains("teamWhitelist.contains(player.getUniqueID())"));
        assertTrue(module.contains("TeamUtil.isSameTeam(player)"));
        assertTrue(module.contains("progressSmooth += (target - progressSmooth) * 0.15F"));
        assertTrue(module.contains("new ScaledResolution(mc)"));
        assertTrue(module.contains("bedWarningY(resolution.getScaledHeight())"));
        assertFalse(module.contains("resolution.getScaledHeight() * 0.5F + 15.0F"));
        assertFalse("BedAlarm adaptation must not auto-send taunt chat messages",
                module.contains("sendChatMessage("));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
