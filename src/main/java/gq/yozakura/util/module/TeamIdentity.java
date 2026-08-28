package gq.yozakura.util.module;

/** Pure tab-list team-prefix comparison shared by combat target filters. */
public final class TeamIdentity {
    private TeamIdentity() {
    }

    public static boolean isSameTeam(String selfTeamName, String selfPrefix,
                                     String targetTeamName, String targetPrefix) {
        return samePrefix(selfPrefix, targetPrefix);
    }

    public static boolean isSameTeam(String selfTeamName, String selfPrefix, String selfDisplayName,
                                     String targetTeamName, String targetPrefix, String targetDisplayName) {
        return samePrefix(selfPrefix, targetPrefix);
    }

    private static boolean samePrefix(String selfPrefix, String targetPrefix) {
        String normalizedSelfPrefix = normalizePrefix(selfPrefix);
        String normalizedTargetPrefix = normalizePrefix(targetPrefix);
        return normalizedSelfPrefix != null && normalizedSelfPrefix.equals(normalizedTargetPrefix);
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return null;
        }
        String trimmed = prefix.trim();
        if (trimmed.isEmpty() || "\u00A7r".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return prefix;
    }
}
