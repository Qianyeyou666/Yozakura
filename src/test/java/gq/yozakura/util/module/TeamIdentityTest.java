package gq.yozakura.util.module;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeamIdentityTest {
    @Test
    public void matchesPlayersWithTheSameRegisteredTeam() {
        assertTrue(TeamIdentity.isSameTeam("red", "§c[R] ", "red", "§c[R] "));
    }

    @Test
    public void doesNotMatchDifferentRegisteredTeamsThatShareAColor() {
        assertFalse(TeamIdentity.isSameTeam("red-one", "§c[R] ", "red-two", "§c[R] "));
    }

    @Test
    public void doesNotUseColorWhenOnlyOneRegisteredTeamIsKnown() {
        assertFalse(TeamIdentity.isSameTeam("red", "§c[R] ", null, "§cUnknown "));
    }

    @Test
    public void fallsBackToAFormattingColorWhenTeamNamesAreUnavailable() {
        assertTrue(TeamIdentity.isSameTeam(null, "§9[B] ", null, "§9Blue "));
    }

    @Test
    public void doesNotTreatBlankOrResetPrefixesAsATeam() {
        assertFalse(TeamIdentity.isSameTeam(null, "", null, ""));
        assertFalse(TeamIdentity.isSameTeam(null, "§r", null, "§r"));
    }
}
