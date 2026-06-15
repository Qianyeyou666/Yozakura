package gq.yozakura.ui.click.yozakura;

import gq.yozakura.module.ModuleType;
import gq.yozakura.engine.font.FontLoaders;

/**
 * ClickGUI 顶部导航标签页枚举。
 * <p>
 * 每个标签页对应一组 {@link ModuleType}，用于在模块列表中按类别筛选模块。
 * 标签页包含标题、图标及其关联的模块类型数组。
 */
enum GuiTab {
    /** 战斗类模块标签页 */
    COMBAT("Combat", FontLoaders.ICON_SWORDS, ModuleType.Combat),
    /** 移动类模块标签页 */
    MOVEMENT("Movement", FontLoaders.ICON_RUN, ModuleType.Movement),
    /** 视觉/渲染类模块标签页 */
    VISUAL("Visual", FontLoaders.ICON_EYE, ModuleType.Render),
    /** 工具/配置类模块标签页 */
    UTILITY("Utility", FontLoaders.ICON_SETTINGS, ModuleType.Config),
    /** 世界类模块标签页 */
    WORLD("World", FontLoaders.ICON_GLOBE, ModuleType.World),
    /** 杂项类模块标签页 */
    MISC("Misc", FontLoaders.ICON_LIST, ModuleType.Other),
    /** 玩家/配置文件标签页 */
    PLAYER("Profiles", FontLoaders.ICON_USER, ModuleType.Player);

    /** 标签页显示标题 */
    final String title;
    /** 标签页图标字符 */
    final String icon;
    /** 该标签页包含的模块类型列表 */
    private final ModuleType[] types;

    /**
     * 构造一个标签页。
     *
     * @param title 显示标题
     * @param icon  图标字符
     * @param types 关联的模块类型（可变参数）
     */
    GuiTab(String title, String icon, ModuleType... types) {
        this.title = title;
        this.icon = icon;
        this.types = types;
    }

    /**
     * 判断给定的模块类型是否属于此标签页。
     *
     * @param type 要检查的模块类型
     * @return 如果此标签页包含该类型则返回 true
     */
    boolean contains(ModuleType type) {
        for (ModuleType moduleType : types) {
            if (moduleType == type) {
                return true;
            }
        }
        return false;
    }
}
