package gq.yozakura.ui.click.yozakura;

import java.util.Locale;

/** Search box geometry and case-insensitive module filtering. */
public final class EpsilonPanelSearchModel {
    private EpsilonPanelSearchModel() {
    }

    public static PanelClickGuiLayout.Rect bounds(PanelClickGuiLayout.Rect modulePanel) {
        return new PanelClickGuiLayout.Rect(
                modulePanel.right() - 6.0f - EpsilonPanelMetrics.SEARCH_WIDTH,
                modulePanel.y() + 8.0f,
                EpsilonPanelMetrics.SEARCH_WIDTH,
                EpsilonPanelMetrics.SEARCH_HEIGHT);
    }

    public static boolean matches(String moduleName, String query) {
        return matches(moduleName, null, query);
    }

    public static boolean matches(String moduleName, String chineseName, String query) {
        String normalizedQuery = normalize(query == null ? "" : query.trim());
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        return normalize(moduleName).contains(normalizedQuery)
                || normalize(chineseName).contains(normalizedQuery);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
