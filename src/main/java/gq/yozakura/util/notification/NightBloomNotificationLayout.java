package gq.yozakura.util.notification;

/**
 * Layout and timing math for the Night Bloom notification theme.
 */
final class NightBloomNotificationLayout {
    static final float PANEL_RADIUS = 4.0F;
    private static final float SIDE_INSET = 9.0F;
    private static final float ICON_SIZE = 22.0F;
    private static final float TEXT_GAP = 8.0F;
    private static final float MIN_PANEL_WIDTH = 190.0F;
    private static final float MAX_PANEL_WIDTH = 248.0F;
    private static final float HORIZONTAL_CHROME = 58.0F;

    private NightBloomNotificationLayout() {
    }

    static float alphaForSlide(float slide) {
        float clamped = clamp(slide, 0.0F, 1.0F);
        float smooth = clamped * clamped * (3.0F - 2.0F * clamped);
        return 1.0F - smooth;
    }

    static float progressForLifetime(long elapsedMillis, long stayMillis) {
        if (stayMillis <= 0L) {
            return 0.0F;
        }
        return 1.0F - clamp(elapsedMillis / (float) stayMillis, 0.0F, 1.0F);
    }

    static float panelWidth(float titleWidth, float messageWidth) {
        float contentWidth = Math.max(Math.max(0.0F, titleWidth), Math.max(0.0F, messageWidth));
        return clamp(contentWidth + HORIZONTAL_CHROME, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH);
    }

    static float panelHeight(boolean hasMessage) {
        return hasMessage ? 46.0F : 38.0F;
    }

    static Layout create(float left, float top, float right, float bottom) {
        float iconLeft = left + SIDE_INSET;
        float iconTop = top + SIDE_INSET;
        float textX = iconLeft + ICON_SIZE + TEXT_GAP;
        float progressLeft = left + SIDE_INSET;
        float progressRight = right - SIDE_INSET;
        return new Layout(iconLeft, iconTop, ICON_SIZE, textX, top + 7.0F, top + 22.0F,
                progressLeft, bottom - 3.8F, progressRight, bottom - 2.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Layout {
        private final float iconLeft;
        private final float iconTop;
        private final float iconSize;
        private final float titleX;
        private final float titleY;
        private final float messageY;
        private final float progressLeft;
        private final float progressTop;
        private final float progressRight;
        private final float progressBottom;

        private Layout(float iconLeft, float iconTop, float iconSize, float titleX, float titleY, float messageY,
                       float progressLeft, float progressTop, float progressRight, float progressBottom) {
            this.iconLeft = iconLeft;
            this.iconTop = iconTop;
            this.iconSize = iconSize;
            this.titleX = titleX;
            this.titleY = titleY;
            this.messageY = messageY;
            this.progressLeft = progressLeft;
            this.progressTop = progressTop;
            this.progressRight = progressRight;
            this.progressBottom = progressBottom;
        }

        float getIconLeft() {
            return iconLeft;
        }

        float getIconTop() {
            return iconTop;
        }

        float getIconSize() {
            return iconSize;
        }

        float getTitleX() {
            return titleX;
        }

        float getTitleY() {
            return titleY;
        }

        float getMessageY() {
            return messageY;
        }

        float getProgressLeft() {
            return progressLeft;
        }

        float getProgressTop() {
            return progressTop;
        }

        float getProgressRight() {
            return progressRight;
        }

        float getProgressBottom() {
            return progressBottom;
        }
    }
}
