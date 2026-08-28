package gq.yozakura.module.combat.aim;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistTargetSelectorTest {
    @Test
    public void usesTheSameBotCheckAsKillAura() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/aim/AimAssistTargetSelector.java");

        assertTrue(source.contains("settings.botCheck && TeamUtil.isBot(player)"));
        assertFalse(source.contains("AntiBot.isServerBot(player)"));
    }

    @Test
    public void exposesATeamsToggleWhileKeepingTheLegacyConfigKey() throws IOException {
        String aimbot = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        String selector = source("src/main/java/gq/yozakura/module/combat/aim/AimAssistTargetSelector.java");

        assertTrue(aimbot.contains("new Option<Boolean>(\"Teams\", \"IgnoreTeammates\", true)"));
        assertTrue(selector.contains("settings.ignoreTeammates && TeamUtil.isSameTeam(player)"));
    }

    @Test
    public void givesTheVisibleLevelBodyPartAbsoluteAimPointPriority() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/aim/AimAssistTargetSelector.java");

        int levelY = source.indexOf("double levelY = preferredLevelHeight(eye.yCoord, box);");
        int levelPoint = source.indexOf("Vec3 preferredLevelPoint = createPoint(box,", levelY);
        int levelVisibility = source.indexOf(
                "isAimPointAllowed(minecraft, preferredLevelPoint, target, settings, blockers)", levelPoint);
        int levelReturn = source.indexOf("return preferredLevelPoint;", levelVisibility);
        int chestPoint = source.indexOf("Vec3 primary = createPoint(box,", levelReturn);
        int nearestFallback = source.indexOf("Vec3 nearest = createPoint(box,", levelReturn);
        int multipointFallback = source.indexOf("List<Vec3> alternatives", levelReturn);

        assertTrue("The first anchor should use the player's level-view body height", levelY >= 0);
        assertTrue("The preferred level point must be checked before every fallback",
                levelPoint > levelY && levelVisibility > levelPoint && levelReturn > levelVisibility
                        && chestPoint > levelReturn && nearestFallback > levelReturn
                        && multipointFallback > levelReturn);
        assertTrue(source.contains("preferredLevelHeight(eyeY, box.minY, box.maxY)"));
        assertTrue(source.contains("Math.max(minY + 0.08D, Math.min(maxY - 0.04D, eyeY))"));
    }

    @Test
    public void levelAimHeightUsesEyeHeightInsideTheSafeTargetBody() {
        assertEquals(1.62D, AimAssistTargetSelector.preferredLevelHeight(
                1.62D, 0.0D, 1.8D), 0.0001D);
    }

    @Test
    public void levelAimHeightClampsToTheSafeBodyEdges() {
        assertEquals(0.08D, AimAssistTargetSelector.preferredLevelHeight(
                -1.0D, 0.0D, 1.8D), 0.0001D);
        assertEquals(1.76D, AimAssistTargetSelector.preferredLevelHeight(
                3.0D, 0.0D, 1.8D), 0.0001D);
    }

    @Test
    public void keepsCurrentTargetDuringMinimumLockWindow() {
        assertFalse(AimAssistTargetLock.shouldSwitch(20.0D, 2.0D, 2.0D, 149L, 150L));
    }

    @Test
    public void keepsCurrentTargetWhenChallengerDoesNotBeatHysteresisMargin() {
        assertFalse(AimAssistTargetLock.shouldSwitch(10.0D, 8.5D, 2.0D, 300L, 150L));
    }

    @Test
    public void switchesWhenChallengerClearlyBeatsCurrentTarget() {
        assertTrue(AimAssistTargetLock.shouldSwitch(10.0D, 7.5D, 2.0D, 300L, 150L));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
