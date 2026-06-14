package gq.yozakura.ui.click;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.engine.font.FontLoaders;

/**
 * ClickGUI 模块图标映射工具类。
 * <p>
 * 根据模块名称关键词或模块类型，返回对应的图标字符。
 * 同时提供图标视觉偏移量，用于微调图标在界面中的渲染位置。
 * <p>
 * 此类为纯静态工具类，不可实例化。
 */
public final class ClickGuiIcons {
    /** 私有构造器，防止实例化 */
    private ClickGuiIcons() {
    }

    /**
     * 根据模块名称获取对应的图标。
     * <p>
     * 优先通过模块名称的关键词匹配返回特定图标，
     * 若未匹配到则回退到按模块类型获取图标。
     *
     * @param module 目标模块，可为 null
     * @return 对应的图标字符；若 module 为 null 则返回默认列表图标
     */
    public static String forModule(Module module) {
        if (module == null) {
            return FontLoaders.ICON_LIST;
        }
        // 标准化模块名称（去除空格、横线、下划线，转小写）后进行关键词匹配
        String name = normalize(module.getName());

        if (name.contains("clickgui")) {
            return FontLoaders.ICON_SETTINGS;
        }
        if (name.contains("targethud")) {
            return FontLoaders.ICON_HEARTBEAT;
        }
        if (name.contains("targetesp")) {
            return FontLoaders.ICON_FOCUS;
        }
        if (name.contains("keyboard") || name.contains("hud")) {
            return FontLoaders.ICON_LIST;
        }
        if (name.contains("state") || name.contains("message")) {
            return FontLoaders.ICON_INFO;
        }
        if (name.contains("fullbright")) {
            return FontLoaders.ICON_SUN;
        }
        if (name.contains("storage")) {
            return FontLoaders.ICON_FOLDER;
        }
        if (name.contains("chams")) {
            return FontLoaders.ICON_SPARK;
        }
        if (name.contains("esp") || name.contains("render")) {
            return FontLoaders.ICON_EYE;
        }
        if (name.contains("health")) {
            return FontLoaders.ICON_HEARTBEAT;
        }

        if (name.contains("autotools")) {
            return FontLoaders.ICON_PICKAXE;
        }
        if (name.contains("save")) {
            return FontLoaders.ICON_SAVE;
        }
        if (name.contains("load")) {
            return FontLoaders.ICON_DOWNLOAD;
        }
        if (name.contains("copy") || name.contains("ign")) {
            return FontLoaders.ICON_EDIT;
        }
        if (name.contains("uninject")) {
            return FontLoaders.ICON_TRASH;
        }
        if (name.contains("chat")) {
            return FontLoaders.ICON_SCRIPT;
        }
        if (name.contains("timer")) {
            return FontLoaders.ICON_CLOCK;
        }
        if (name.contains("ltap") || name.contains("iq")) {
            return FontLoaders.ICON_USER;
        }

        if (name.contains("murder") || name.contains("server")) {
            return FontLoaders.ICON_WARNING;
        }
        if (name.contains("mlg") || name.contains("nofall")) {
            return FontLoaders.ICON_ARROW_DOWN;
        }
        if (name.contains("fastplace")) {
            return FontLoaders.ICON_CUBE;
        }
        if (name.contains("scaffold")) {
            return FontLoaders.ICON_CUBE;
        }
        if (name.contains("bridge")) {
            return FontLoaders.ICON_CUBE;
        }
        if (name.contains("clutch")) {
            return FontLoaders.ICON_SHIELD;
        }

        if (name.contains("antibot")) {
            return FontLoaders.ICON_BUG;
        }
        if (name.contains("autoblock")) {
            return FontLoaders.ICON_SHIELD;
        }
        if (name.contains("blockhit")) {
            return FontLoaders.ICON_SHIELD;
        }
        if (name.contains("backtrack")) {
            return FontLoaders.ICON_CLOCK;
        }
        if (name.contains("fakelag")) {
            return FontLoaders.ICON_SHUFFLE;
        }
        if (name.contains("knockback")) {
            return FontLoaders.ICON_SHIELD;
        }
        if (name.contains("nojump")) {
            return FontLoaders.ICON_ARROW_DOWN;
        }
        if (name.contains("wtap")) {
            return FontLoaders.ICON_RUN;
        }
        if (name.contains("hitselect")) {
            return FontLoaders.ICON_FOCUS;
        }
        if (name.contains("hitbox")) {
            return FontLoaders.ICON_FOCUS;
        }
        if (name.contains("velocity")) {
            return FontLoaders.ICON_SHUFFLE;
        }
        if (name.contains("crit")) {
            return FontLoaders.ICON_SPARK;
        }
        if (name.contains("aim") || name.contains("bow")) {
            return FontLoaders.ICON_CROSSHAIR;
        }
        if (name.contains("aura") || name.contains("clicker") || name.contains("reach")) {
            return FontLoaders.ICON_SWORDS;
        }
        if (name.contains("speed") || name.contains("sprint") || name.contains("fly") || name.contains("step")
                || name.contains("strafe") || name.contains("slow") || name.contains("invmove")
                || name.contains("spider")) {
            return FontLoaders.ICON_RUN;
        }
        if (name.contains("config") || name.contains("save") || name.contains("load")) {
            return FontLoaders.ICON_SETTINGS;
        }
        // 无匹配时，按模块类型返回默认图标
        return forType(module.getCategory());
    }

    /**
     * 根据模块类型获取默认图标。
     *
     * @param type 模块类型
     * @return 对应的图标字符
     */
    public static String forType(ModuleType type) {
        if (type == ModuleType.Combat) {
            return FontLoaders.ICON_SWORDS;
        }
        if (type == ModuleType.Movement) {
            return FontLoaders.ICON_RUN;
        }
        if (type == ModuleType.Render) {
            return FontLoaders.ICON_EYE;
        }
        if (type == ModuleType.Player) {
            return FontLoaders.ICON_USER;
        }
        if (type == ModuleType.World) {
            return FontLoaders.ICON_GLOBE;
        }
        if (type == ModuleType.Config) {
            return FontLoaders.ICON_SETTINGS;
        }
        return FontLoaders.ICON_LIST;
    }

    /**
     * 获取图标在 X 轴上的视觉偏移量，用于微调居中效果。
     *
     * @param icon 图标字符
     * @return X 轴偏移像素值
     */
    public static float visualOffsetX(String icon) {
        if (FontLoaders.ICON_BOMB.equals(icon)) {
            return -0.5f;
        }
        if (FontLoaders.ICON_MOVEMENT.equals(icon) || FontLoaders.ICON_SETTINGS.equals(icon)) {
            return 0.5f;
        }
        return 0.0f;
    }

    /**
     * 获取图标在 Y 轴上的视觉偏移量，用于微调居中效果。
     *
     * @param icon 图标字符
     * @return Y 轴偏移像素值
     */
    public static float visualOffsetY(String icon) {
        if (FontLoaders.ICON_INFO.equals(icon) || FontLoaders.ICON_CHECKMARK.equals(icon)
                || FontLoaders.ICON_XMARK.equals(icon)) {
            return -0.5f;
        }
        if (FontLoaders.ICON_BOMB.equals(icon) || FontLoaders.ICON_MOVEMENT.equals(icon)) {
            return 0.5f;
        }
        return 0.0f;
    }

    /**
     * 标准化名称字符串：去除空格、横线和下划线，转为小写。
     *
     * @param name 原始名称
     * @return 标准化后的字符串
     */
    private static String normalize(String name) {
        return name == null ? "" : name.replace(" ", "").replace("-", "").replace("_", "").toLowerCase();
    }
}
