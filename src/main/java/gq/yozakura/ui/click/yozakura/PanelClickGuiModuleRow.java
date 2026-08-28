package gq.yozakura.ui.click.yozakura;

/** Geometry model for a single Panel module-list row. */
public final class PanelClickGuiModuleRow {
    public static final float HEIGHT = 48.0f;
    public static final float TITLE_SCALE = 0.70f;
    public static final float DESCRIPTION_SCALE = 0.52f;

    private PanelClickGuiModuleRow() {
    }

    public static float titleY(float rowHeight, float titleHeight) {
        return Math.max(8.0f, Math.round((rowHeight - titleHeight) * 0.5f - 6.0f));
    }

    public static float descriptionY(float rowHeight, float descriptionHeight) {
        return Math.min(rowHeight - descriptionHeight - 7.0f,
                rowHeight * 0.5f + 2.0f);
    }
}
