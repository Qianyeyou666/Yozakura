package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;

public class KillAuraJumpInputContractTest {
    @Test
    public void autoBlockDoesNotOverrideTheSampledJumpInput() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/KillAura.java")), StandardCharsets.UTF_8);

        assertFalse("AutoBlock must not consume the player's sampled jump input",
                source.contains("movementInput.jump ="));
    }
}
