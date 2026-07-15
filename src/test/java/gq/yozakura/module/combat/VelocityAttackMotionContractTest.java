package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VelocityAttackMotionContractTest {
    @Test
    public void attackSlowdownOnlyRunsForAConfirmedSprintKnockback() throws IOException {
        String source = source("src/main/java/gq/yozakura/util/module/PlayerUtil.java");
        int methodStart = source.indexOf("    private static void applyAttackMotion(Entity target, boolean applySprintSlowdown) {");
        int methodEnd = source.indexOf("    public static void applyAttackSprint(Entity target) {", methodStart);
        String attackMotion = source.substring(methodStart, methodEnd);

        assertEquals("A failed local hit must not apply attacker motion", 1,
                occurrences(source, "applyAttackMotion(target, knockbackLevel > 0);"));
        int sprintGuard = attackMotion.indexOf("if (!applySprintSlowdown)");
        int velocitySlowdown = attackMotion.indexOf("Velocity.applyAttackSlowdown(target)");
        assertTrue("The normal sprint-knockback guard must run before Velocity can mutate motion",
                sprintGuard >= 0 && sprintGuard < velocitySlowdown);
    }

    @Test
    public void attackModeUsesTheVanillaSprintSlowdownInsteadOfReduceSettings() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");
        int methodStart = source.indexOf("    private void applyAttackSlowdownMotion() {");
        int methodEnd = source.indexOf("    private void acceptCustomAttack(Entity target) {", methodStart);
        String attackMotion = source.substring(methodStart, methodEnd);
        int horizontalDeclaration = source.indexOf("new PercentProperty(\"Horizontal\"");
        int verticalDeclaration = source.indexOf("new PercentProperty(\"Vertical\"", horizontalDeclaration);
        String horizontal = source.substring(horizontalDeclaration, verticalDeclaration);

        assertTrue(attackMotion.contains("VANILLA_SPRINT_SLOWDOWN"));
        assertFalse(attackMotion.contains("horizontal.getValue()"));
        assertTrue("Legacy Reduce settings stay persisted but hidden after the compatibility conversion",
                horizontal.contains("() -> false"));
    }

    @Test
    public void forgePreAttackOnlyPreparesTheConfirmedAttackSlowdown() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");
        int methodStart = source.indexOf("    private void acceptExternalAttack(Entity target) {");
        int methodEnd = source.indexOf("    private boolean canAcceptAttack(Entity target) {", methodStart);
        String externalAttack = source.substring(methodStart, methodEnd);

        assertTrue(externalAttack.contains("pendingAttackTarget = target;"));
        assertFalse("Forge AttackEntityEvent runs before the local hit result is known",
                externalAttack.contains("applyAttackSlowdownMotion()"));
        assertFalse("Only the confirmed local attack hook may consume the pending slowdown",
                externalAttack.contains("consumePendingAttackSlowdown()"));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
