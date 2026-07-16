package gq.yozakura.util.module;

/** Pure scoreboard identity comparison shared by combat target filters. */
public final class TeamIdentity {
    private TeamIdentity() {
    }

    public static boolean isSameTeam(String selfTeamName, String selfPrefix,
                                     String targetTeamName, String targetPrefix) {
        String normalizedSelfName = normalizeName(selfTeamName);
        String normalizedTargetName = normalizeName(targetTeamName);
        if (normalizedSelfName != null || normalizedTargetName != null) {
            return normalizedSelfName != null && normalizedSelfName.equals(normalizedTargetName);
        }

        Character selfColor = formattingColor(selfPrefix);
        Character targetColor = formattingColor(targetPrefix);
        return selfColor != null && selfColor.equals(targetColor);
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Character formattingColor(String prefix) {
        if (prefix == null) {
            return null;
        }
        Character color = null;
        for (int index = 0; index + 1 < prefix.length(); index++) {
            if (prefix.charAt(index) != '\u00A7') {
                continue;
            }
            char code = Character.toLowerCase(prefix.charAt(++index));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                color = code;
            } else if (code == 'r') {
                color = null;
            }
        }
        return color;
    }
}
