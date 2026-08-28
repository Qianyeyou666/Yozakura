package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AntiBotConsumerContractTest {
    @Test
    public void combatAndRenderTargetConsumersUseTheCentralAntiBotEntryPoint() throws IOException {
        String killAura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        String aimAssist = source("src/main/java/gq/yozakura/module/combat/aim/AimAssistTargetSelector.java");
        String targetHud = source("src/main/java/gq/yozakura/module/render/TargetHUD.java");
        String nameTags = source("src/main/java/gq/yozakura/module/render/NameTags.java");
        String esp = source("src/main/java/gq/yozakura/module/render/ESP.java");

        assertTrue(killAura.contains("AntiBot.isServerBot(entityLivingBase)"));
        assertTrue(aimAssist.contains("AntiBot.isServerBot(player)"));
        assertTrue(targetHud.contains("AntiBot.isServerBot(living)"));
        assertTrue(nameTags.contains("AntiBot.isServerBot(player)"));
        assertTrue(esp.contains("AntiBot.isServerBot(entity)"));
        assertFalse(killAura.contains("TeamUtil.isBot("));
        assertFalse(aimAssist.contains("TeamUtil.isBot("));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
