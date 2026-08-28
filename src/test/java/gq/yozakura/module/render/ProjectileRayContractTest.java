package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectileRayContractTest {
    @Test
    public void projectileRayIsIndependentHeldItemAimAssist() throws IOException {
        String ray = source("src/main/java/gq/yozakura/module/render/ProjectileRay.java");
        String manager = source("src/main/java/gq/yozakura/manager/ModuleManager.java");

        assertTrue(manager.contains("addModule(\"ProjectileRay\""));
        assertTrue(ray.contains("super(\"ProjectileRay\""));
        assertTrue(ray.contains("ModuleType.Render"));
        assertTrue(ray.contains("mc.thePlayer.inventory.getCurrentItem()"));
        assertTrue(ray.contains("instanceof ItemBow"));
        assertTrue(ray.contains("Items.snowball"));
        assertTrue(ray.contains("Items.egg"));
        assertTrue(ray.contains("Items.ender_pearl"));
        assertTrue(ray.contains("mc.thePlayer.getItemInUseDuration()"));
        assertTrue(ray.contains("mc.thePlayer.rotationYaw"));
        assertTrue(ray.contains("mc.thePlayer.rotationPitch"));
        assertTrue(ray.contains("ProjectileRayPolicy.launchSpec("));
        assertTrue(ray.contains("mc.theWorld.rayTraceBlocks("));
        assertTrue(ray.contains("calculateIntercept("));
        assertTrue(ray.contains("drawLandingMarker("));
        assertFalse(ray.contains("BedThreat"));
        assertFalse(ray.contains("FireballRisk"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
