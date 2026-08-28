package gq.yozakura.ui.click.yozakura;

/**
 * Shared draw and hit-test geometry for the Panel config manager page.
 */
public final class PanelConfigManagerGeometry {
    private static final float HORIZONTAL_INSET = 14.0f;
    private static final float CONTROL_GAP = 6.0f;
    private static final float SAVE_WIDTH = 48.0f;
    private static final float LOAD_WIDTH = 48.0f;
    private static final float REFRESH_WIDTH = 58.0f;
    private static final float FOLDER_WIDTH = 48.0f;
    private static final float MIN_NAME_WIDTH = 88.0f;
    private static final float CONTROL_HEIGHT = 22.0f;

    private PanelConfigManagerGeometry() {
    }

    public static PanelClickGuiLayout.Rect profileList(PanelClickGuiLayout.Rect bounds) {
        return new PanelClickGuiLayout.Rect(bounds.x() + HORIZONTAL_INSET,
                bounds.y() + 76.0f,
                bounds.width() - HORIZONTAL_INSET * 2.0f,
                Math.max(72.0f, bounds.height() - 170.0f));
    }

    public static PanelClickGuiLayout.Rect nameField(PanelClickGuiLayout.Rect bounds) {
        float reservedActions = SAVE_WIDTH + LOAD_WIDTH + REFRESH_WIDTH + FOLDER_WIDTH
                + CONTROL_GAP * 4.0f;
        float availableWidth = bounds.width() - HORIZONTAL_INSET * 2.0f;
        float fieldWidth = Math.max(MIN_NAME_WIDTH, availableWidth - reservedActions);
        return new PanelClickGuiLayout.Rect(bounds.x() + HORIZONTAL_INSET,
                bounds.bottom() - 82.0f, fieldWidth, CONTROL_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect saveButton(PanelClickGuiLayout.Rect bounds) {
        PanelClickGuiLayout.Rect field = nameField(bounds);
        return after(field, SAVE_WIDTH);
    }

    public static PanelClickGuiLayout.Rect loadButton(PanelClickGuiLayout.Rect bounds) {
        return after(saveButton(bounds), LOAD_WIDTH);
    }

    public static PanelClickGuiLayout.Rect refreshButton(PanelClickGuiLayout.Rect bounds) {
        return after(loadButton(bounds), REFRESH_WIDTH);
    }

    public static PanelClickGuiLayout.Rect folderButton(PanelClickGuiLayout.Rect bounds) {
        return after(refreshButton(bounds), FOLDER_WIDTH);
    }

    private static PanelClickGuiLayout.Rect after(PanelClickGuiLayout.Rect previous, float width) {
        return new PanelClickGuiLayout.Rect(previous.right() + CONTROL_GAP,
                previous.y(), width, previous.height());
    }
}
