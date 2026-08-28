package gq.yozakura.module.combat;

import java.util.Locale;

/** Dependency-free Hypixel player classification used by every target consumer. */
public final class HypixelBotPolicy {
    private HypixelBotPolicy() {
    }

    public static final class Snapshot {
        private final boolean hypixel;
        private final boolean dead;
        private final String profileName;
        private final String displayName;
        private final boolean invisible;
        private final boolean tabListed;
        private final int ping;
        private final String teamName;
        private final String teamPrefix;
        private final float health;
        private final int maxHurtTime;
        private final boolean sleeping;
        private final int ticksExisted;

        public Snapshot(boolean hypixel, boolean dead, String profileName, String displayName,
                        boolean invisible, boolean tabListed, int ping, String teamName,
                        String teamPrefix, float health, int maxHurtTime, boolean sleeping,
                        int ticksExisted) {
            this.hypixel = hypixel;
            this.dead = dead;
            this.profileName = safe(profileName);
            this.displayName = safe(displayName);
            this.invisible = invisible;
            this.tabListed = tabListed;
            this.ping = ping;
            this.teamName = safe(teamName);
            this.teamPrefix = safe(teamPrefix);
            this.health = health;
            this.maxHurtTime = maxHurtTime;
            this.sleeping = sleeping;
            this.ticksExisted = ticksExisted;
        }

        public boolean isBot() {
            String lowerDisplay = displayName.toLowerCase(Locale.ROOT);
            String lowerProfile = profileName.toLowerCase(Locale.ROOT);
            if (dead || profileName.trim().isEmpty()) {
                return true;
            }
            if (lowerDisplay.contains("[npc]") || lowerDisplay.contains(" npc")
                    || lowerProfile.startsWith("npc")) {
                return true;
            }
            if (!hypixel) {
                return false;
            }
            if (!tabListed || profileName.indexOf(' ') >= 0) {
                return true;
            }
            boolean redIdentity = startsWithRed(displayName) || startsWithRed(teamPrefix);
            if (invisible && (redIdentity || ticksExisted < 10)) {
                return true;
            }
            if (ping <= 0 && (invisible || redIdentity || teamName.isEmpty())) {
                return true;
            }
            if (maxHurtTime == 0) {
                String plainDisplay = stripFormatting(displayName);
                if (health == 20.0F && plainDisplay.length() == 10 && !displayName.startsWith("§")) {
                    return true;
                }
                if (health == 20.0F && sleeping && plainDisplay.length() == 10) {
                    return true;
                }
                if (health != 20.0F && invisible && redIdentity) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean startsWithRed(String value) {
        return value.length() >= 2 && value.charAt(0) == '§'
                && Character.toLowerCase(value.charAt(1)) == 'c';
    }

    private static String stripFormatting(String value) {
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
