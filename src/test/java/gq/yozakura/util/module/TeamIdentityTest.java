package gq.yozakura.util.module;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeamIdentityTest {
    @Test
    public void matchesPlayersWithTheSameRegisteredTeam() {
        assertTrue(TeamIdentity.isSameTeam("red", "\u00A7c[R] ", "red", "\u00A7c[R] "));
    }

    @Test
    public void matchesBedWarsTeammatesByExactTabListPrefixEvenWhenRegisteredNamesDiffer() {
        assertTrue(TeamIdentity.isSameTeam("red-one", "\u00A7c[R] ", "red-two", "\u00A7c[R] "));
    }

    @Test
    public void matchesWhenOnlyTheExactPrefixesAreAvailable() {
        assertTrue(TeamIdentity.isSameTeam(null, "\u00A79[B] ", null, "\u00A79[B] "));
    }

    @Test
    public void doesNotMatchDifferentTeamPrefixes() {
        assertFalse(TeamIdentity.isSameTeam("red", "\u00A7c[R] ", "blue", "\u00A79[B] "));
    }

    @Test
    public void comparesTheFullPrefixRatherThanOnlyItsColorCode() {
        assertFalse(TeamIdentity.isSameTeam(
                "red-one", "\u00A7c[R] ", "\u00A7cOwner",
                "red-two", "\u00A7c[B] ", "\u00A7cOther"));
    }

    @Test
    public void doesNotInferTeamsFromDisplayNamesWhenTabListTeamsAreMissing() {
        assertFalse(TeamIdentity.isSameTeam(
                null, null, "\u00A7cOwner",
                null, null, "\u00A7cTeammate"));
    }

    @Test
    public void doesNotTreatBlankOrResetPrefixesAsATeam() {
        assertFalse(TeamIdentity.isSameTeam(null, "", null, ""));
        assertFalse(TeamIdentity.isSameTeam(null, "\u00A7r", null, "\u00A7r"));
    }
}
