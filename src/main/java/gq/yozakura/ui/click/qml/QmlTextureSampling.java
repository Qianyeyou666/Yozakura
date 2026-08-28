package gq.yozakura.ui.click.qml;

final class QmlTextureSampling {
    private QmlTextureSampling() {
    }

    static boolean isPixelPerfect(float contentScale, int guiScaleFactor) {
        return Math.abs(contentScale * Math.max(1, guiScaleFactor) - 1.0F) < 0.001F;
    }
}
