package gq.yozakura.util.notification;

/**
 * Compact layout and lifetime math for the default HUD notification card.
 */
final class ModernNotificationLayout {
    static final float PANEL_RADIUS = 7.0F;
    private static final float SIDE_INSET = 8.0F;
    private static final float ACCENT_WIDTH = 2.0F;
    private static final float ACCENT_GAP = 5.0F;
    private static final float ICON_SIZE = 22.0F;
    private static final float TEXT_GAP = 8.0F;
    private static final float MIN_PANEL_WIDTH = 188.0F;
    private static final float MAX_PANEL_WIDTH = 264.0F;
    private static final float HORIZONTAL_CHROME = 58.0F;

    private ModernNotificationLayout() {
    }

    static float panelWidth(float titleWidth, float messageWidth) {
        float contentWidth = Math.max(Math.max(0.0F, titleWidth), Math.max(0.0F, messageWidth));
        return clamp(contentWidth + HORIZONTAL_CHROME, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH);
    }

    static float panelHeight(boolean hasMessage) {
        return hasMessage ? 42.0F : 34.0F;
    }

    static float progressForLifetime(long elapsedMillis, long stayMillis) {
        if (stayMillis <= 0L) {
            return 0.0F;
        }
        return 1.0F - clamp(elapsedMillis / (float) stayMillis, 0.0F, 1.0F);
    }

    static Layout create(float left, float top, float right, float bottom) {
        float accentLeft = left + SIDE_INSET;
        float iconLeft = accentLeft + ACCENT_WIDTH + ACCENT_GAP;
        float textX = iconLeft + ICON_SIZE + TEXT_GAP;
        return new Layout(accentLeft, ACCENT_WIDTH, iconLeft, top + (bottom - top - ICON_SIZE) * 0.5F,
                ICON_SIZE, textX, top + 7.0F, top + 21.0F,
                textX, bottom - 3.6F, right - SIDE_INSET, bottom - 2.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Layout {
        private final float accentLeft;
        private final float accentWidth;
        private final float iconLeft;
        private final float iconTop;
        private final float iconSize;
        private final float textX;
        private final float titleY;
        private final float messageY;
        private final float progressLeft;
        private final float progressTop;
        private final float progressRight;
        private final float progressBottom;

        private Layout(float accentLeft, float accentWidth, float iconLeft, float iconTop, float iconSize,
                       float textX, float titleY, float messageY, float progressLeft, float progressTop,
                       float progressRight, float progressBottom) {
            this.accentLeft = accentLeft;
            this.accentWidth = accentWidth;
            this.iconLeft = iconLeft;
            this.iconTop = iconTop;
            this.iconSize = iconSize;
            this.textX = textX;
            this.titleY = titleY;
            this.messageY = messageY;
            this.progressLeft = progressLeft;
            this.progressTop = progressTop;
            this.progressRight = progressRight;
            this.progressBottom = progressBottom;
        }

        float getAccentLeft() {
            return accentLeft;
        }

        float getAccentWidth() {
            return accentWidth;
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

        float getTextX() {
            return textX;
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
