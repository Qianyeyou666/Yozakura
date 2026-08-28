package gq.yozakura.ui.click.yozakura;

/** Shared draw and hit-test bounds for Epsilon Panel controls. */
public final class EpsilonPanelGeometry {
    public static final float RAIL_ITEM_INSET = 5.0f;
    public static final float RAIL_MENU_X = 6.0f;
    public static final float RAIL_MENU_Y = 4.0f;
    public static final float RAIL_MENU_SIZE = 28.0f;

    public static final float HEADER_TOP = 34.0f;
    public static final float HEADER_HEIGHT = 36.0f;
    public static final float HEADER_OUTER_INSET = 3.0f;
    public static final float HEADER_CONTENT_INSET = 8.0f;
    public static final float HEADER_CONTROL_HEIGHT = 18.0f;
    public static final float KEYBIND_WIDTH = 18.0f;
    public static final float SEGMENT_WIDTH = 72.0f;
    public static final float CONTROL_GAP = 6.0f;

    public static final float MODULE_SWITCH_WIDTH = 26.0f;
    public static final float MODULE_SWITCH_HEIGHT = 16.0f;
    public static final float MODULE_SWITCH_TRAILING_INSET = 10.0f;
    public static final float MODULE_SETTINGS_SIZE = 20.0f;
    public static final float MODULE_ACTION_GAP = 8.0f;
    public static final float DETAIL_CLOSE_SIZE = 22.0f;

    public static final float ROW_TRAILING_INSET = 5.0f;
    public static final float NUMBER_TRACK_LEADING_DISTANCE = 116.0f;
    public static final float NUMBER_TRACK_WIDTH = 72.0f;
    public static final float NUMBER_TRACK_HEIGHT = 6.0f;
    public static final float NUMBER_FIELD_WIDTH = 40.0f;
    public static final float NUMBER_FIELD_HEIGHT = 18.0f;

    private EpsilonPanelGeometry() {
    }

    public static PanelClickGuiLayout.Rect railMenuButton(PanelClickGuiLayout.Rect rail) {
        return new PanelClickGuiLayout.Rect(rail.x() + RAIL_MENU_X, rail.y() + RAIL_MENU_Y,
                RAIL_MENU_SIZE, RAIL_MENU_SIZE);
    }

    public static PanelClickGuiLayout.Rect railCategoryItem(PanelClickGuiLayout.Rect rail, int index) {
        return new PanelClickGuiLayout.Rect(rail.x() + RAIL_ITEM_INSET,
                rail.y() + EpsilonPanelMetrics.CATEGORY_START_Y
                        + index * EpsilonPanelMetrics.CATEGORY_ITEM_STEP,
                rail.width() - RAIL_ITEM_INSET * 2.0f,
                EpsilonPanelMetrics.CATEGORY_ITEM_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect railSettingsItem(PanelClickGuiLayout.Rect rail) {
        return new PanelClickGuiLayout.Rect(rail.x() + RAIL_ITEM_INSET,
                rail.bottom() - EpsilonPanelMetrics.CATEGORY_ITEM_HEIGHT - RAIL_ITEM_INSET,
                rail.width() - RAIL_ITEM_INSET * 2.0f,
                EpsilonPanelMetrics.CATEGORY_ITEM_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect railConfigManagerItem(PanelClickGuiLayout.Rect rail) {
        PanelClickGuiLayout.Rect settings = railSettingsItem(rail);
        return new PanelClickGuiLayout.Rect(settings.x(),
                settings.y() - EpsilonPanelMetrics.CATEGORY_ITEM_STEP,
                settings.width(), settings.height());
    }

    public static DetailHeader detailHeader(PanelClickGuiLayout.Rect panel) {
        PanelClickGuiLayout.Rect background = new PanelClickGuiLayout.Rect(
                panel.x() + HEADER_OUTER_INSET,
                panel.y() + HEADER_TOP,
                panel.width() - HEADER_OUTER_INSET * 2.0f,
                HEADER_HEIGHT);
        float controlY = background.y() + (background.height() - HEADER_CONTROL_HEIGHT) * 0.5f;
        float x = background.x() + HEADER_CONTENT_INSET;
        PanelClickGuiLayout.Rect keybind = new PanelClickGuiLayout.Rect(
                x, controlY, KEYBIND_WIDTH, HEADER_CONTROL_HEIGHT);
        PanelClickGuiLayout.Rect bindMode = new PanelClickGuiLayout.Rect(
                keybind.right() + CONTROL_GAP, controlY, SEGMENT_WIDTH, HEADER_CONTROL_HEIGHT);
        PanelClickGuiLayout.Rect hidden = new PanelClickGuiLayout.Rect(
                bindMode.right() + CONTROL_GAP, controlY, SEGMENT_WIDTH, HEADER_CONTROL_HEIGHT);
        return new DetailHeader(background, keybind, bindMode, hidden);
    }

    public static PanelClickGuiLayout.Rect moduleSwitch(PanelClickGuiLayout.Rect row) {
        return new PanelClickGuiLayout.Rect(
                row.right() - MODULE_SWITCH_TRAILING_INSET - MODULE_SWITCH_WIDTH,
                row.y() + (row.height() - MODULE_SWITCH_HEIGHT) * 0.5f,
                MODULE_SWITCH_WIDTH,
                MODULE_SWITCH_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect moduleSettingsButton(PanelClickGuiLayout.Rect row) {
        PanelClickGuiLayout.Rect toggle = moduleSwitch(row);
        return new PanelClickGuiLayout.Rect(
                toggle.x() - MODULE_ACTION_GAP - MODULE_SETTINGS_SIZE,
                row.y() + (row.height() - MODULE_SETTINGS_SIZE) * 0.5f,
                MODULE_SETTINGS_SIZE,
                MODULE_SETTINGS_SIZE);
    }

    public static PanelClickGuiLayout.Rect detailCloseButton(PanelClickGuiLayout.Rect content) {
        return new PanelClickGuiLayout.Rect(
                content.right() - 10.0f - DETAIL_CLOSE_SIZE,
                content.y() + 9.0f,
                DETAIL_CLOSE_SIZE,
                DETAIL_CLOSE_SIZE);
    }

    public static PanelClickGuiLayout.Rect optionSwitch(PanelClickGuiLayout.Rect row) {
        return new PanelClickGuiLayout.Rect(
                row.right() - ROW_TRAILING_INSET - MODULE_SWITCH_WIDTH,
                row.y() + (row.height() - MODULE_SWITCH_HEIGHT) * 0.5f,
                MODULE_SWITCH_WIDTH,
                MODULE_SWITCH_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect numberTrack(PanelClickGuiLayout.Rect row) {
        return new PanelClickGuiLayout.Rect(
                row.right() - ROW_TRAILING_INSET - NUMBER_TRACK_LEADING_DISTANCE,
                row.y() + 12.0f,
                NUMBER_TRACK_WIDTH,
                NUMBER_TRACK_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect numberField(PanelClickGuiLayout.Rect row) {
        return new PanelClickGuiLayout.Rect(
                row.right() - ROW_TRAILING_INSET - NUMBER_FIELD_WIDTH,
                row.y() + 4.0f,
                NUMBER_FIELD_WIDTH,
                NUMBER_FIELD_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect numberInteractive(PanelClickGuiLayout.Rect row) {
        PanelClickGuiLayout.Rect track = numberTrack(row);
        return new PanelClickGuiLayout.Rect(track.x(), track.y() - 6.0f,
                track.width(), track.height() + 12.0f);
    }

    public static final class DetailHeader {
        private final PanelClickGuiLayout.Rect background;
        private final PanelClickGuiLayout.Rect keybind;
        private final PanelClickGuiLayout.Rect bindMode;
        private final PanelClickGuiLayout.Rect hidden;

        private DetailHeader(PanelClickGuiLayout.Rect background,
                             PanelClickGuiLayout.Rect keybind,
                             PanelClickGuiLayout.Rect bindMode,
                             PanelClickGuiLayout.Rect hidden) {
            this.background = background;
            this.keybind = keybind;
            this.bindMode = bindMode;
            this.hidden = hidden;
        }

        public PanelClickGuiLayout.Rect background() { return background; }
        public PanelClickGuiLayout.Rect keybind() { return keybind; }
        public PanelClickGuiLayout.Rect bindMode() { return bindMode; }
        public PanelClickGuiLayout.Rect hidden() { return hidden; }
    }
}
