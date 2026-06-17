package gq.yozakura.ui.click.web;

public final class ModernWebClickGuiState {
    private ModernWebClickGuiState() {
    }

    public static boolean isEnabled(String moduleName) {
        return ModernWebClickGuiController.isEnabled(moduleName);
    }

    public static double numberValue(String moduleName, String valueName, double fallback) {
        return ModernWebClickGuiController.numberValue(moduleName, valueName, fallback);
    }

    public static boolean booleanValue(String moduleName, String valueName, boolean fallback) {
        return ModernWebClickGuiController.booleanValue(moduleName, valueName, fallback);
    }

    public static String modeValue(String moduleName, String valueName, String fallback) {
        return ModernWebClickGuiController.modeValue(moduleName, valueName, fallback);
    }

    public static void setNumberValue(String moduleName, String valueName, double next) {
        ModernWebClickGuiController.setNumberValue(moduleName, valueName, next);
    }
}
