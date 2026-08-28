package gq.yozakura.util.module;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeamUtilTest {
    @Test
    public void requiresBothTabListTeamsAndComparesTheirPrefixes() throws IOException {
        String source = source();
        int begin = source.indexOf("public static boolean isSameTeam(EntityPlayer player)");
        int end = source.indexOf("public static boolean hasTeamColor", begin);
        String comparison = source.substring(begin, end);

        assertTrue(comparison.contains("if (selfInfo == null)"));
        assertTrue(comparison.contains("if (selfTeam == null)"));
        assertTrue(comparison.contains("if (targetInfo == null)"));
        assertTrue(comparison.contains("if (targetTeam == null)"));
        assertTrue(comparison.contains("selfTeam.getColorPrefix().equals(targetTeam.getColorPrefix())"));
        assertFalse(comparison.contains("formattedDisplayName("));
        assertFalse(comparison.contains("getRegisteredName()"));
    }

    @Test
    public void bothCombatModulesUseTheSharedTeamComparison() throws IOException {
        String selector = source("src/main/java/gq/yozakura/module/combat/aim/AimAssistTargetSelector.java");
        String killAura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");

        assertTrue(selector.contains("settings.ignoreTeammates && TeamUtil.isSameTeam(player)"));
        assertTrue(killAura.contains("!TeamUtil.isSameTeam((EntityPlayer) entityLivingBase)"));
    }

    private static String source() throws IOException {
        return source("src/main/java/gq/yozakura/util/module/TeamUtil.java");
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
