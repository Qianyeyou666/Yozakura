package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LunarForgeListenerDedupContractTest {
    @Test
    public void velocityRegistersOnlyOneAttackEntityListenerAfterForgeRemapping() throws IOException {
        String velocity = source("src/main/java/gq/yozakura/module/combat/Velocity.java");

        assertTrue("The canonical Forge listener is remapped to the standalone shim on Lunar",
                velocity.contains("public void onForgeAttack(AttackEntityEvent event)"));
        assertFalse("A second explicit shim listener would process every Lunar attack twice",
                velocity.contains("onStandaloneAttack(gq.yozakura.bridge.forge.AttackEntityEvent"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
