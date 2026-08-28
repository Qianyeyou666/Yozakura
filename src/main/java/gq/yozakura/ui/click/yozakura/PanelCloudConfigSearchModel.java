package gq.yozakura.ui.click.yozakura;

import gq.yozakura.club.ClubConfigSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PanelCloudConfigSearchModel {
    private PanelCloudConfigSearchModel() {
    }

    public static List<ClubConfigSummary> filter(List<ClubConfigSummary> configs, String query) {
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return configs;
        }
        List<ClubConfigSummary> matches = new ArrayList<ClubConfigSummary>();
        for (ClubConfigSummary config : configs) {
            if (config != null && (contains(config.getName(), normalized)
                    || contains(config.getOwner(), normalized))) {
                matches.add(config);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public static ClubConfigSummary findById(List<ClubConfigSummary> configs, String id) {
        if (configs == null || id == null || id.isEmpty()) {
            return null;
        }
        for (ClubConfigSummary config : configs) {
            if (config != null && id.equals(config.getId())) {
                return config;
            }
        }
        return null;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
