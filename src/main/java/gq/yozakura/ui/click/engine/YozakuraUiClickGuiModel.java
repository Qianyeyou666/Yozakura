package gq.yozakura.ui.click.engine;

import gq.yozakura.core.ClientLanguage;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.ui.engine.dom.AttributeMap;
import gq.yozakura.ui.engine.dom.DomNode;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.dom.TextNode;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Direct DOM bridge for the HTML/CSS ClickGUI; it has no QML or browser dependency. */
public final class YozakuraUiClickGuiModel {
    private final ElementNode root;
    private final ElementNode categories;
    private final ElementNode modules;
    private final ElementNode title;
    private final ElementNode language;
    private final ElementNode searchElement;
    private final ElementNode scrollThumb;
    private final ElementNode globalPalette;
    private ModuleType activeCategory = ModuleType.Combat;
    private String expandedModule = "";
    private String openModeModule = "";
    private String openModeValue = "";
    private String openColorModule = "";
    private String openColorValue = "";
    private String search = "";
    private float scrollOffset;
    private long lastSignature;
    private boolean localChangePending;
    private final Map<String, ModuleNodes> moduleNodes = new HashMap<String, ModuleNodes>();
    private final Map<String, SettingNodes> settingNodes = new HashMap<String, SettingNodes>();
    private final Map<String, ElementNode> popupNodes = new HashMap<String, ElementNode>();

    public YozakuraUiClickGuiModel(ElementNode root) {
        if (root == null) throw new IllegalArgumentException("root must not be null");
        this.root = root;
        this.categories = requireId(root, "categories");
        this.modules = requireId(root, "modules");
        this.title = requireId(root, "category-title");
        this.language = requireId(root, "language");
        this.searchElement = requireId(root, "search");
        this.scrollThumb = requireId(root, "scroll-thumb");
        this.globalPalette = requireId(root, "global-palette");
        rebuild();
    }

    public void rebuild() {
        rebuildCategories();
        rebuildModules();
        setText(title, categoryName(activeCategory));
        setText(language, isChinese() ? "EN" : "中");
        setText(searchElement, search.isEmpty() ? (isChinese() ? "搜索模块" : "Search modules") : search);
    }

    public void selectCategory(String category) {
        ModuleType resolved = parseCategory(category);
        if (resolved == null) return;
        activeCategory = resolved;
        expandedModule = "";
        scrollOffset = 0.0F;
        closeGlobalPalette();
        rebuild();
    }

    public void toggleModule(String moduleName) {
        Module module = ModuleManager.getModule(moduleName);
        if (module != null && !"ClickGUI".equalsIgnoreCase(module.getName())) {
            module.toggle();
            updateModuleState(module);
            localChangePending = true;
        }
    }

    public boolean moduleState(String moduleName) {
        Module module = ModuleManager.getModule(moduleName);
        return module != null && module.getState();
    }

    public void setKeybind(String moduleName, int keyCode) {
        Module module = ModuleManager.getModule(moduleName);
        if (module == null) return;
        module.setKey(keyCode);
        rebuildModules();
    }

    public boolean setModuleToggleProgress(String moduleName, float progress) {
        ModuleNodes nodes = moduleNodes.get(normalize(moduleName));
        if (nodes == null || nodes.toggleKnob == null) return false;
        float p = Math.max(0.0F, Math.min(1.0F, progress));
        nodes.toggleKnob.withInlineStyle("left: " + formatPercent((2.0F + 16.0F * p) / 40.0F) + "%;");
        return true;
    }

    ElementNode moduleToggleKnob(String moduleName) {
        ModuleNodes nodes = moduleNodes.get(normalize(moduleName));
        return nodes == null ? null : nodes.toggleKnob;
    }

    ElementNode moduleAccent(String moduleName) {
        ModuleNodes nodes = moduleNodes.get(normalize(moduleName));
        return nodes == null ? null : nodes.accent;
    }

    ElementNode settingsElement(String moduleName) {
        ModuleNodes nodes = moduleNodes.get(normalize(moduleName));
        return nodes == null ? null : nodes.settings;
    }

    String expandedModuleName() { return expandedModule; }

    ElementNode modeMenuElement(String moduleName, String valueName) {
        return popupNodes.get(settingKey(moduleName, valueName));
    }

    ElementNode globalPaletteElement() { return globalPalette; }

    List<ElementNode> visibleModuleCards() {
        List<ElementNode> result = new ArrayList<ElementNode>();
        List<Module> visible = filteredModules();
        for (int i = 0; i < visible.size(); i++) {
            ModuleNodes nodes = moduleNodes.get(normalize(visible.get(i).getName()));
            if (nodes != null) result.add(nodes.card);
        }
        return result;
    }

    List<ElementNode> visibleModuleGroups() {
        List<ElementNode> result = new ArrayList<ElementNode>();
        List<Module> visible = filteredModules();
        for (int i = 0; i < visible.size(); i++) {
            ModuleNodes nodes = moduleNodes.get(normalize(visible.get(i).getName()));
            if (nodes != null) result.add(nodes.group);
        }
        return result;
    }

    public void toggleSettings(String moduleName) {
        Module module = ModuleManager.getModule(moduleName);
        if (module == null) return;
        String previous = expandedModule;
        expandedModule = normalize(previous).equals(normalize(moduleName)) ? "" : module.getName();
        openModeModule = "";
        openModeValue = "";
        openColorModule = "";
        openColorValue = "";
        if (!previous.isEmpty()) updateExpandedState(ModuleManager.getModule(previous), false);
        if (!expandedModule.isEmpty()) updateExpandedState(module, true);
        clampScroll();
        updateScrollThumb();
    }

    public void toggleModeDropdown(String moduleName, String valueName) {
        Value value = findValue(ModuleManager.getModule(moduleName), valueName);
        if (!(value instanceof Mode) && !(value instanceof ModeProperty)) return;
        boolean same = normalize(openModeModule).equals(normalize(moduleName))
                && normalize(openModeValue).equals(normalize(valueName));
        openModeModule = same ? "" : moduleName;
        openModeValue = same ? "" : valueName;
        openColorModule = "";
        openColorValue = "";
        rebuildSettings(ModuleManager.getModule(moduleName));
    }

    public void toggleColorPalette(String moduleName, String redName) {
        Value value = findValue(ModuleManager.getModule(moduleName), redName);
        if (!(value instanceof Numbers)) return;
        boolean same = normalize(openColorModule).equals(normalize(moduleName))
                && normalize(openColorValue).equals(normalize(redName));
        openColorModule = same ? "" : moduleName;
        openColorValue = same ? "" : redName;
        openModeModule = "";
        openModeValue = "";
        rebuildSettings(ModuleManager.getModule(moduleName));
    }

    public boolean closePopups() {
        if (!globalPalette.children().isEmpty()) {
            closeGlobalPalette();
            return true;
        }
        if (openModeModule.isEmpty() && openColorModule.isEmpty()) return false;
        Module module = ModuleManager.getModule(!openModeModule.isEmpty()
                ? openModeModule : openColorModule);
        openModeModule = "";
        openModeValue = "";
        openColorModule = "";
        openColorValue = "";
        rebuildSettings(module);
        return true;
    }

    public void toggleGlobalPalette() {
        if (!globalPalette.children().isEmpty()) {
            closeGlobalPalette();
            return;
        }
        Module module = ModuleManager.getModule("ClickGUI");
        if (module == null) return;
        List<Value> values = module.getValues();
        for (int i = 0; i < values.size(); i++) {
            String prefix = colorPrefix(values.get(i).getName());
            if (prefix == null) continue;
            Numbers red = asNumber(findValue(values, prefix + "Red"));
            Numbers green = asNumber(findValue(values, prefix + "Green"));
            Numbers blue = asNumber(findValue(values, prefix + "Blue"));
            if (red == null || green == null || blue == null) continue;
            if (!"Accent".equalsIgnoreCase(prefix) && !globalPalette.children().isEmpty()) continue;
            openColorModule = module.getName();
            openColorValue = red.getName();
            globalPalette.clearChildren();
            globalPalette.withClasses(Arrays.asList("global-palette", "open"));
            globalPalette.appendChild(buildColorPalette(module,
                    SettingEntry.color(colorLabel(prefix), red, green, blue),
                    HsvColor.fromRgb(colorChannel(red), colorChannel(green), colorChannel(blue)),
                    registerGlobalColorNodes(module.getName(), red.getName())));
            if ("Accent".equalsIgnoreCase(prefix)) return;
        }
    }

    private ColorNodes registerGlobalColorNodes(String moduleName, String redName) {
        ElementNode trigger = element("span", "global-color-anchor");
        ElementNode triggerHex = element("span", "global-color-anchor-text");
        ColorNodes nodes = new ColorNodes(trigger, triggerHex);
        settingNodes.put(settingKey(moduleName, redName), SettingNodes.color(nodes));
        return nodes;
    }

    private void closeGlobalPalette() {
        globalPalette.clearChildren();
        globalPalette.withClasses(Arrays.asList("global-palette"));
        openColorModule = "";
        openColorValue = "";
    }

    public void selectModeOption(String moduleName, String valueName, String option) {
        Value value = findValue(ModuleManager.getModule(moduleName), valueName);
        if (value instanceof Mode) {
            Mode mode = (Mode) value;
            Enum[] values = (Enum[]) mode.getModes();
            for (int i = 0; i < values.length; i++) {
                if (values[i].name().equalsIgnoreCase(option)) {
                    mode.setValue(values[i]);
                    break;
                }
            }
        } else if (value instanceof ModeProperty) {
            ModeProperty mode = (ModeProperty) value;
            String[] values = mode.getModes();
            for (int i = 0; i < values.length; i++) {
                if (values[i].equalsIgnoreCase(option)) {
                    mode.setValue(i);
                    break;
                }
            }
        }
        openModeModule = "";
        openModeValue = "";
        rebuildSettings(ModuleManager.getModule(moduleName));
        localChangePending = true;
    }

    public void activateSetting(String moduleName, String valueName, int direction) {
        Module module = ModuleManager.getModule(moduleName);
        Value value = findValue(module, valueName);
        if (value == null) return;
        int step = direction < 0 ? -1 : 1;
        if (value instanceof Mode) {
            Mode mode = (Mode) value;
            Enum[] options = (Enum[]) mode.getModes();
            int current = indexOf(options, (Enum) mode.getValue());
            mode.setValue(options[(current + step + options.length) % options.length]);
        } else if (value instanceof ModeProperty) {
            ModeProperty mode = (ModeProperty) value;
            String[] options = mode.getModes();
            int current = mode.getValue();
            mode.setValue((current + step + options.length) % options.length);
        } else if (value instanceof Option && value.getValue() instanceof Boolean) {
            ((Option) value).setValue(!((Boolean) value.getValue()));
        } else if (value instanceof Numbers) {
            Numbers number = (Numbers) value;
            double current = ((Number) number.getValue()).doubleValue();
            double increment = ((Number) number.getIncrement()).doubleValue();
            double minimum = ((Number) number.getMinimum()).doubleValue();
            double maximum = ((Number) number.getMaximum()).doubleValue();
            number.setNumberValue(Math.max(minimum, Math.min(maximum, current + step * increment)));
        }
        rebuildSettings(module);
        localChangePending = true;
    }

    public boolean setNumberRatio(String moduleName, String valueName, double ratio) {
        Value value = findValue(ModuleManager.getModule(moduleName), valueName);
        if (!(value instanceof Numbers) || value instanceof ModeProperty) return false;
        Numbers number = (Numbers) value;
        double minimum = ((Number) number.getMinimum()).doubleValue();
        double maximum = ((Number) number.getMaximum()).doubleValue();
        double increment = Math.abs(((Number) number.getIncrement()).doubleValue());
        double next = minimum + (maximum - minimum) * Math.max(0.0, Math.min(1.0, ratio));
        if (increment > 0.0) next = minimum + Math.round((next - minimum) / increment) * increment;
        Object before = number.getValue();
        number.setNumberValue(Math.max(minimum, Math.min(maximum, next)));
        if (before == null ? number.getValue() == null : before.equals(number.getValue())) return false;
        updateNumberSetting(moduleName, valueName, number);
        localChangePending = true;
        return true;
    }

    public boolean isNumberSetting(String moduleName, String valueName) {
        Value value = findValue(ModuleManager.getModule(moduleName), valueName);
        return value instanceof Numbers && !(value instanceof ModeProperty);
    }

    public boolean isBooleanSetting(String moduleName, String valueName) {
        Value value = findValue(ModuleManager.getModule(moduleName), valueName);
        return value instanceof Option && value.getValue() instanceof Boolean;
    }

    public boolean booleanSettingState(String moduleName, String valueName) {
        Value value = findValue(ModuleManager.getModule(moduleName), valueName);
        return value instanceof Option && Boolean.TRUE.equals(value.getValue());
    }

    ElementNode settingToggleKnob(String moduleName, String valueName) {
        SettingNodes nodes = settingNodes.get(settingKey(moduleName, valueName));
        return nodes == null ? null : nodes.toggleKnob;
    }

    public void toggleLanguage() {
        ClickGUI.setLanguage(isChinese() ? ClientLanguage.ENGLISH : ClientLanguage.CHINESE);
        rebuild();
    }

    public void setSearch(String search) {
        this.search = search == null ? "" : search;
        scrollOffset = 0.0F;
        rebuildModules();
    }

    public String search() { return search; }

    public void scroll(float delta) {
        float maximum = maximumScroll();
        scrollOffset = Math.max(0.0F, Math.min(maximum, scrollOffset - delta * 28.0F));
        updateModuleScrollStyles();
        updateScrollThumb();
    }

    public void setScrollRatio(float ratio) {
        float maximum = maximumScroll();
        scrollOffset = maximum * Math.max(0.0F, Math.min(1.0F, ratio));
        updateModuleScrollStyles();
        updateScrollThumb();
    }

    private void rebuildCategories() {
        categories.clearChildren();
        for (ModuleType type : ModuleType.values()) {
            ElementNode category = element("button", type == activeCategory ? "category active" : "category")
                    .withAttributes(attributes("data-action", "category", "data-category", type.name()));
            if (type == activeCategory) category.appendChild(element("span", "category-accent"));
            category.appendChild(element("span", "category-icon").appendChild(TextNode.of(categoryIcon(type))));
            category.appendChild(element("span", "category-name").appendChild(TextNode.of(categoryName(type))));
            categories.appendChild(category);
        }
    }

    private void rebuildModules() {
        modules.clearChildren();
        moduleNodes.clear();
        settingNodes.clear();
        popupNodes.clear();
        List<Module> filtered = filteredModules();
        for (int i = 0; i < filtered.size(); i++) {
            Module module = filtered.get(i);
            boolean expanded = normalize(expandedModule).equals(normalize(module.getName()));
            ElementNode group = element("div", moduleGroupClasses(expanded));
            ElementNode card = element("div", moduleCardClasses(module.getState(), expanded))
                    .withAttributes(attributes("data-action", "toggle-module", "data-module", module.getName()));
            ElementNode accent = element("span", "module-accent");
            card.appendChild(accent);
            card.appendChild(element("span", "module-icon").appendChild(
                    TextNode.of(moduleIcon(module))));
            ElementNode info = element("div", "module-info");
            info.appendChild(element("span", "module-name").appendChild(
                    TextNode.of(isChinese() ? module.getChinese() : module.getName())));
            info.appendChild(element("span", "module-description").appendChild(
                    TextNode.of(safe(module.getDescription()))));
            card.appendChild(info);
            ElementNode actions = element("div", "module-actions");
            if (module.getKey() != Keyboard.KEY_NONE) {
                actions.appendChild(element("span", "bind").appendChild(
                        TextNode.of(Keyboard.getKeyName(module.getKey()))));
            }
            List<Value> values = visibleValues(module);
            actions.appendChild(element("button", "settings-button")
                    .withAttributes(attributes("data-action", "settings-module",
                    "data-module", module.getName())).appendChild(TextNode.of(settingsIcon())));
            ElementNode toggle = element("button", module.getState() ? "toggle on" : "toggle")
                    .withAttributes(attributes("data-action", "toggle-module",
                            "data-module", module.getName()));
            toggle.appendChild(element("span", "toggle-knob"));
            actions.appendChild(toggle);
            card.appendChild(actions);
            group.appendChild(card);
            if (expanded) {
                group.appendChild(buildSettings(module, values));
            }
            modules.appendChild(group);
            moduleNodes.put(normalize(module.getName()), new ModuleNodes(group, card, toggle,
                    (ElementNode) toggle.child(0), accent));
        }
        lastSignature = liveSignature();
        localChangePending = false;
        clampScroll();
        updateScrollThumb();
    }

    private void updateModuleScrollStyles() {
        modules.withInlineStyle("top: -" + formatPixels(scrollOffset) + "px;");
    }

    private void clampScroll() {
        scrollOffset = Math.max(0.0F, Math.min(maximumScroll(), scrollOffset));
        updateModuleScrollStyles();
    }

    private float maximumScroll() {
        List<Module> filtered = filteredModules();
        float settingsHeight = 0.0F;
        for (int i = 0; i < filtered.size(); i++) {
            Module module = filtered.get(i);
            if (normalize(expandedModule).equals(normalize(module.getName()))) {
                settingsHeight = estimatedSettingsHeight(module);
            }
        }
        return maximumScroll(filtered.size(), settingsHeight);
    }

    static float moduleContentHeight(int moduleCount, float settingsHeight) {
        int count = Math.max(0, moduleCount);
        return count * 56.0F + Math.max(0, count - 1) * 8.0F + Math.max(0.0F, settingsHeight);
    }

    static float maximumScroll(int moduleCount, float settingsHeight) {
        return Math.max(0.0F, moduleContentHeight(moduleCount, settingsHeight) - 497.0F);
    }

    static float scrollThumbHeight(float contentHeight) {
        if (contentHeight <= 0.0F) return 0.0F;
        return Math.max(34.0F, 488.0F * 497.0F / contentHeight);
    }

    private float estimatedSettingsHeight(Module module) {
        int rows = settingEntries(module, visibleValues(module)).size() + 1;
        if (rows <= 3) return 34.0F + rows * 44.0F;
        return 66.0F + rows * 44.0F;
    }

    private void updateScrollThumb() {
        float maximum = maximumScroll();
        if (maximum <= 0.0F) {
            scrollThumb.withInlineStyle("display: none;");
            return;
        }
        float content = maximum + 497.0F;
        float height = scrollThumbHeight(content);
        float top = (488.0F - height) * scrollOffset / maximum;
        scrollThumb.withInlineStyle("display: block; height: " + formatPixels(height)
                + "px; top: " + formatPixels(top) + "px;");
    }

    static String moduleGroupClasses(boolean expanded) {
        return expanded ? "module-group expanded" : "module-group";
    }

    static String moduleCardClasses(boolean enabled, boolean expanded) {
        String classes = enabled ? "module-card enabled" : "module-card";
        return expanded ? classes + " expanded" : classes;
    }

    /** Pulls module/value changes made by keybinds or other game systems without reloading resources. */
    public boolean sync() {
        long signature = liveSignature();
        if (localChangePending) {
            lastSignature = signature;
            localChangePending = false;
            return false;
        }
        if (signature == lastSignature) return false;
        List<Module> visible = filteredModules();
        for (int i = 0; i < visible.size(); i++) updateModuleState(visible.get(i));
        if (!expandedModule.isEmpty()) rebuildSettings(ModuleManager.getModule(expandedModule));
        lastSignature = signature;
        return true;
    }

    private void updateModuleState(Module module) {
        if (module == null) return;
        ModuleNodes nodes = moduleNodes.get(normalize(module.getName()));
        if (nodes == null) return;
        boolean expanded = normalize(expandedModule).equals(normalize(module.getName()));
        nodes.card.withClasses(Arrays.asList(moduleCardClasses(module.getState(), expanded).split("\\s+")));
        nodes.toggle.withClasses(Arrays.asList((module.getState() ? "toggle on" : "toggle").split("\\s+")));
    }

    private void updateExpandedState(Module module, boolean expanded) {
        if (module == null) return;
        ModuleNodes nodes = moduleNodes.get(normalize(module.getName()));
        if (nodes == null) return;
        nodes.group.withClasses(Arrays.asList(moduleGroupClasses(expanded).split("\\s+")));
        updateModuleState(module);
        if (nodes.settings != null) {
            nodes.group.removeChild(nodes.settings);
            nodes.settings = null;
        }
        removeSettingNodes(module.getName());
        if (expanded) {
            nodes.settings = buildSettings(module, visibleValues(module));
            nodes.group.appendChild(nodes.settings);
        }
    }

    private void rebuildSettings(Module module) {
        if (module == null || !normalize(expandedModule).equals(normalize(module.getName()))) return;
        ModuleNodes nodes = moduleNodes.get(normalize(module.getName()));
        if (nodes == null) return;
        if (nodes.settings != null) nodes.group.removeChild(nodes.settings);
        removeSettingNodes(module.getName());
        nodes.settings = buildSettings(module, visibleValues(module));
        nodes.group.appendChild(nodes.settings);
    }

    private void removeSettingNodes(String moduleName) {
        String prefix = normalize(moduleName) + "\u0000";
        java.util.Iterator<String> iterator = settingNodes.keySet().iterator();
        while (iterator.hasNext()) if (iterator.next().startsWith(prefix)) iterator.remove();
        java.util.Iterator<String> popupIterator = popupNodes.keySet().iterator();
        while (popupIterator.hasNext()) if (popupIterator.next().startsWith(prefix)) popupIterator.remove();
    }

    private static String settingKey(String moduleName, String valueName) {
        return normalize(moduleName) + "\u0000" + normalize(valueName);
    }

    private void updateNumberSetting(String moduleName, String valueName, Numbers number) {
        SettingNodes nodes = settingNodes.get(settingKey(moduleName, valueName));
        if (nodes == null) return;
        double ratio = numberRatio(number);
        nodes.fill.withInlineStyle("width: " + formatPixels(510.0 * ratio) + "px;");
        nodes.knob.withInlineStyle("left: " + formatPixels(2.0 + 500.0 * ratio) + "px;");
        setText(nodes.valueText, valueText(number));
    }

    private static long liveSignature() {
        long result = 1125899906842597L;
        List<Module> all = ModuleManager.getModules();
        for (int i = 0; i < all.size(); i++) {
            Module module = all.get(i);
            result = result * 31 + (module.getState() ? 1 : 0);
            result = result * 31 + module.getKey();
            List<Value> values = module.getValues();
            for (int v = 0; v < values.size(); v++) {
                Object value = values.get(v).getValue();
                result = result * 31 + (value == null ? 0 : value.hashCode());
                result = result * 31 + (values.get(v).isVisible() ? 1 : 0);
            }
        }
        return result;
    }

    private ElementNode buildSettings(Module module, List<Value> values) {
        ElementNode settings = element("div", "settings");
        List<SettingEntry> entries = settingEntries(module, values);
        ElementNode title = element("div", "settings-title");
        title.appendChild(element("span", "settings-title-accent"));
        title.appendChild(element("span", "settings-title-text").appendChild(
                TextNode.of(isChinese() ? "设置" : "SETTINGS")));
        settings.appendChild(title);

        ElementNode keybindRow = element("div", "setting-row keybind-row");
        keybindRow.appendChild(element("span", "setting-name").appendChild(
                TextNode.of(isChinese() ? "按键" : "Keybind")));
        keybindRow.appendChild(element("button", "keybind-control")
                .withAttributes(attributes("data-action", "setting-keybind",
                        "data-module", module.getName()))
                .appendChild(TextNode.of(module.getKey() == Keyboard.KEY_NONE
                        ? "NONE" : Keyboard.getKeyName(module.getKey()))));
        settings.appendChild(keybindRow);

        int modeIndex = firstModeIndex(entries);
        if (entries.size() <= 2) {
            appendSettingRows(settings, module, entries, 0, entries.size());
        } else if (modeIndex >= 0) {
            ElementNode general = settingSection(isChinese() ? "常规" : "GENERAL");
            general.appendChild(buildSettingRow(module, entries.get(modeIndex)));
            for (int i = 0, added = 0; i < entries.size() && added < 1; i++) {
                if (i != modeIndex) {
                    general.appendChild(buildSettingRow(module, entries.get(i)));
                    added++;
                }
            }
            settings.appendChild(general);
            ElementNode advanced = settingSection(isChinese() ? "高级" : "ADVANCED");
            int skippedOther = 0;
            for (int i = 0; i < entries.size(); i++) {
                if (i == modeIndex) continue;
                if (skippedOther++ == 0) continue;
                advanced.appendChild(buildSettingRow(module, entries.get(i)));
            }
            if (advanced.childCount() > 1) settings.appendChild(advanced);
        } else {
            ElementNode section = settingSection(isChinese() ? "设置" : "SETTINGS");
            appendSettingRows(section, module, entries, 0, entries.size());
            settings.appendChild(section);
        }
        return settings;
    }

    private void appendSettingRows(ElementNode parent, Module module,
                                   List<SettingEntry> values, int from, int to) {
        for (int i = from; i < to; i++) parent.appendChild(buildSettingRow(module, values.get(i)));
    }

    private static ElementNode settingSection(String title) {
        ElementNode section = element("div", "setting-section");
        section.appendChild(element("div", "setting-section-title").appendChild(TextNode.of(title)));
        return section;
    }

    private ElementNode buildSettingRow(Module module, SettingEntry entry) {
        ElementNode row = element("div", "setting-row");
        row.appendChild(element("span", "setting-name").appendChild(
                TextNode.of(entry.label)));
        if (entry.isColor()) {
            row.appendChild(buildColorControl(module, entry));
            return row;
        }
        Value value = entry.value;
        String moduleName = module.getName();
        if (value instanceof Numbers && !(value instanceof ModeProperty)) {
            Numbers number = (Numbers) value;
            double ratio = numberRatio(number);
            ElementNode control = element("div", settingControlClasses(value));
            ElementNode slider = element("button", "setting-slider")
                    .withAttributes(attributes("data-action", "setting",
                            "data-module", moduleName, "data-value", value.getName()));
            slider.appendChild(element("span", "setting-slider-rail"));
            slider.appendChild(element("span", "setting-slider-fill")
                    .withInlineStyle("width: " + formatPixels(510.0 * ratio) + "px;"));
            slider.appendChild(element("span", "setting-slider-knob")
                    .withInlineStyle("left: " + formatPixels(2.0 + 500.0 * ratio) + "px;"));
            control.appendChild(slider);
            ElementNode fill = (ElementNode) slider.child(1);
            ElementNode knob = (ElementNode) slider.child(2);
            ElementNode valueLabel = element("span", "setting-number-value").appendChild(
                    TextNode.of(valueText(value)));
            control.appendChild(valueLabel);
            row.appendChild(control);
            settingNodes.put(settingKey(moduleName, value.getName()),
                    SettingNodes.number(fill, knob, valueLabel));
        } else if (value instanceof Option && value.getValue() instanceof Boolean) {
            ElementNode control = element("button", settingControlClasses(value))
                    .withAttributes(attributes("data-action", "setting",
                            "data-module", moduleName, "data-value", value.getName()));
            ElementNode toggle = element("span", Boolean.TRUE.equals(value.getValue())
                    ? "setting-toggle on" : "setting-toggle");
            toggle.appendChild(element("span", "setting-toggle-knob"));
            control.appendChild(toggle);
            row.appendChild(control);
            settingNodes.put(settingKey(moduleName, value.getName()),
                    SettingNodes.toggle((ElementNode) toggle.child(0)));
        } else {
            boolean open = normalize(openModeModule).equals(normalize(moduleName))
                    && normalize(openModeValue).equals(normalize(value.getName()));
            ElementNode dropdown = element("div", open ? "mode-dropdown open" : "mode-dropdown");
            ElementNode control = element("button", settingControlClasses(value))
                    .withAttributes(attributes("data-action", "setting-mode",
                            "data-module", moduleName, "data-value", value.getName()));
            control.appendChild(element("span", "setting-mode-value").appendChild(
                    TextNode.of(valueText(value))));
            control.appendChild(element("span", "setting-mode-arrow").appendChild(
                    TextNode.of(open ? "⌃" : "⌄")));
            dropdown.appendChild(control);
            if (open) dropdown.appendChild(buildModeMenu(moduleName, value));
            row.appendChild(dropdown);
        }
        return row;
    }

    private ElementNode buildModeMenu(String moduleName, Value value) {
        ElementNode menu = element("div", "mode-menu");
        popupNodes.put(settingKey(moduleName, value.getName()), menu);
        String current = valueText(value);
        String[] options = modeOptions(value);
        for (int i = 0; i < options.length; i++) {
            String option = options[i];
            menu.appendChild(element("button", current.equalsIgnoreCase(option)
                    ? "mode-option selected" : "mode-option")
                    .withAttributes(attributes("data-action", "setting-mode-option",
                            "data-module", moduleName, "data-value", value.getName(),
                            "data-option", option))
                    .appendChild(TextNode.of(option)));
        }
        return menu;
    }

    private static String[] modeOptions(Value value) {
        if (value instanceof Mode) {
            Enum[] values = (Enum[]) ((Mode) value).getModes();
            String[] result = new String[values.length];
            for (int i = 0; i < values.length; i++) result[i] = values[i].name();
            return result;
        }
        if (value instanceof ModeProperty) return ((ModeProperty) value).getModes();
        return new String[0];
    }

    private ElementNode buildColorControl(Module module, SettingEntry entry) {
        String moduleName = module.getName();
        String key = settingKey(moduleName, entry.red.getName());
        HsvColor hsv = HsvColor.fromRgb(colorChannel(entry.red), colorChannel(entry.green),
                colorChannel(entry.blue));
        boolean open = normalize(openColorModule).equals(normalize(moduleName))
                && normalize(openColorValue).equals(normalize(entry.red.getName()));
        ElementNode dropdown = element("div", open ? "color-dropdown open" : "color-dropdown");
        ElementNode trigger = element("button", "color-trigger")
                .withAttributes(attributes("data-action", "setting-color-open",
                        "data-module", moduleName, "data-red", entry.red.getName()))
                .withInlineStyle("background-color: " + hsv.toHex() + ";");
        ElementNode triggerHex = element("span", "color-trigger-hex").appendChild(
                TextNode.of(hsv.toHex()));
        trigger.appendChild(triggerHex);
        dropdown.appendChild(trigger);
        ColorNodes nodes = new ColorNodes(trigger, triggerHex);
        settingNodes.put(key, SettingNodes.color(nodes));
        if (open) dropdown.appendChild(buildColorPalette(module, entry, hsv, nodes));
        return dropdown;
    }

    private static ElementNode buildColorPalette(Module module, SettingEntry entry,
                                                   HsvColor hsv, ColorNodes nodes) {
        ElementNode palette = element("div", "color-palette");
        ElementNode heading = element("div", "color-palette-heading");
        heading.appendChild(element("span", "color-palette-title").appendChild(
                TextNode.of(isChinese() ? "颜色选择器" : "COLOR PICKER")));
        heading.appendChild(element("span", "color-palette-subtitle").appendChild(
                TextNode.of(isChinese() ? "自定义颜色" : "CUSTOM COLOR")));
        palette.appendChild(heading);

        ElementNode body = element("div", "color-palette-body");
        ElementNode sv = element("button", "color-sv")
                .withAttributes(colorAction("setting-color-sv", module, entry))
                .setAttribute("data-hue", Float.toString(hsv.hue()))
                .withInlineStyle("background: linear-gradient(90deg, #ffffff, "
                        + HsvColor.of(hsv.hue(), 1.0F, 1.0F).toHex() + ");");
        ElementNode svCursor = element("span", "color-sv-cursor")
                .withInlineStyle("left: " + formatPercent(hsv.saturation()) + "%; top: "
                        + formatPercent(1.0F - hsv.value()) + "%;");
        sv.appendChild(svCursor);
        body.appendChild(sv);

        ElementNode hue = element("button", "color-hue")
                .withAttributes(colorAction("setting-color-hue", module, entry));
        ElementNode hueCursor = element("span", "color-hue-cursor")
                .withInlineStyle("top: " + formatPercent(hsv.hue()) + "%;");
        hue.appendChild(hueCursor);
        body.appendChild(hue);

        ElementNode controls = element("div", "color-palette-controls");
        ElementNode preview = element("span", "color-preview")
                .withInlineStyle("background-color: " + hsv.toHex() + ";");
        ElementNode hex = element("span", "color-hex").appendChild(TextNode.of(hsv.toHex()));
        controls.appendChild(preview);
        controls.appendChild(hex);
        ElementNode presetRow = element("div", "color-presets");
        String[] presetColors = {"#f08bb0", "#06b6d4", "#f43f5e", "#10b981",
                "#f59e0b", "#0ea5e9", "#ec4899", "#f97316"};
        String current = hsv.toHex();
        for (int i = 0; i < presetColors.length; i++) {
            String color = presetColors[i];
            presetRow.appendChild(element("button", current.equalsIgnoreCase(color)
                    ? "color-swatch active" : "color-swatch")
                    .withAttributes(attributes("data-action", "setting-color",
                            "data-module", module.getName(), "data-red", entry.red.getName(),
                            "data-green", entry.green.getName(), "data-blue", entry.blue.getName(),
                            "data-color", color))
                    .withInlineStyle("background-color: " + color + ";"));
        }
        controls.appendChild(presetRow);
        body.appendChild(controls);
        palette.appendChild(body);
        nodes.sv = sv;
        nodes.svCursor = svCursor;
        nodes.hueCursor = hueCursor;
        nodes.preview = preview;
        nodes.hex = hex;
        return palette;
    }

    private static AttributeMap colorAction(String action, Module module, SettingEntry entry) {
        return attributes("data-action", action, "data-module", module.getName(),
                "data-red", entry.red.getName(), "data-green", entry.green.getName(),
                "data-blue", entry.blue.getName());
    }

    static String settingControlClasses(Value value) {
        if (value instanceof Numbers && !(value instanceof ModeProperty)) {
            return "setting-control number-control";
        }
        if (value instanceof Option && value.getValue() instanceof Boolean) {
            return "setting-control toggle-control";
        }
        return "setting-control mode-control";
    }

    static double numberRatio(Numbers number) {
        double minimum = ((Number) number.getMinimum()).doubleValue();
        double maximum = ((Number) number.getMaximum()).doubleValue();
        if (maximum <= minimum) return 0.0;
        double value = ((Number) number.getValue()).doubleValue();
        return Math.max(0.0, Math.min(1.0, (value - minimum) / (maximum - minimum)));
    }

    private static int firstModeIndex(List<SettingEntry> values) {
        for (int i = 0; i < values.size(); i++) {
            Value value = values.get(i).value;
            if (value instanceof Mode || value instanceof ModeProperty) return i;
        }
        return -1;
    }

    private static List<SettingEntry> settingEntries(Module module, List<Value> values) {
        List<SettingEntry> result = new ArrayList<SettingEntry>();
        Set<String> consumed = new HashSet<String>();
        boolean groupColors = module != null;
        for (int i = 0; i < values.size(); i++) {
            Value value = values.get(i);
            if (consumed.contains(value.getName())) continue;
            String prefix = groupColors ? colorPrefix(value.getName()) : null;
            if (prefix != null) {
                Numbers red = asNumber(findValue(values, prefix + "Red"));
                Numbers green = asNumber(findValue(values, prefix + "Green"));
                Numbers blue = asNumber(findValue(values, prefix + "Blue"));
                if (red != null && green != null && blue != null) {
                    consumed.add(red.getName());
                    consumed.add(green.getName());
                    consumed.add(blue.getName());
                    result.add(SettingEntry.color(colorLabel(prefix), red, green, blue));
                    continue;
                }
            }
            consumed.add(value.getName());
            result.add(SettingEntry.value(value));
        }
        return result;
    }

    static String colorPrefix(String valueName) {
        if (valueName == null || !valueName.endsWith("Red") || valueName.length() <= 3) return null;
        return valueName.substring(0, valueName.length() - 3);
    }

    private static String colorLabel(String prefix) {
        if ("Canvas".equals(prefix)) return "UI Canvas";
        if ("Surface".equals(prefix)) return "UI Panels";
        if ("Accent".equals(prefix)) return "UI Accent";
        if ("AccentAlt".equals(prefix)) return "UI Alt Accent";
        if ("Danger".equals(prefix)) return "Hurt / Low HP";
        if ("EnderChest".equals(prefix)) return "Ender Chest";
        return prefix;
    }

    private static Value findValue(List<Value> values, String name) {
        for (int i = 0; i < values.size(); i++) {
            if (name.equals(values.get(i).getName())) return values.get(i);
        }
        return null;
    }

    private static Numbers asNumber(Value value) {
        return value instanceof Numbers && !(value instanceof ModeProperty) ? (Numbers) value : null;
    }

    private static String colorHex(Numbers red, Numbers green, Numbers blue) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                colorChannel(red), colorChannel(green), colorChannel(blue));
    }

    private static int colorChannel(Numbers number) {
        return Math.max(0, Math.min(255, ((Number) number.getValue()).intValue()));
    }

    public boolean setColor(String moduleName, String redName, String greenName,
                            String blueName, String hex) {
        Module module = ModuleManager.getModule(moduleName);
        Numbers red = asNumber(findValue(module, redName));
        Numbers green = asNumber(findValue(module, greenName));
        Numbers blue = asNumber(findValue(module, blueName));
        if (red == null || green == null || blue == null || hex == null
                || !hex.matches("#[0-9a-fA-F]{6}")) return false;
        red.setNumberValue(Integer.parseInt(hex.substring(1, 3), 16));
        green.setNumberValue(Integer.parseInt(hex.substring(3, 5), 16));
        blue.setNumberValue(Integer.parseInt(hex.substring(5, 7), 16));
        updateColorSetting(moduleName, redName, red, green, blue);
        localChangePending = true;
        return true;
    }

    public boolean setColorHsv(String moduleName, String redName, String greenName,
                               String blueName, float hue, float saturation, float value) {
        Module module = ModuleManager.getModule(moduleName);
        Numbers red = asNumber(findValue(module, redName));
        Numbers green = asNumber(findValue(module, greenName));
        Numbers blue = asNumber(findValue(module, blueName));
        if (red == null || green == null || blue == null) return false;
        HsvColor color = HsvColor.of(hue, saturation, value);
        red.setNumberValue(color.red());
        green.setNumberValue(color.green());
        blue.setNumberValue(color.blue());
        updateColorSetting(moduleName, redName, red, green, blue);
        localChangePending = true;
        return true;
    }

    public HsvColor color(String moduleName, String redName, String greenName, String blueName) {
        Module module = ModuleManager.getModule(moduleName);
        Numbers red = asNumber(findValue(module, redName));
        Numbers green = asNumber(findValue(module, greenName));
        Numbers blue = asNumber(findValue(module, blueName));
        return red == null || green == null || blue == null ? null
                : HsvColor.fromRgb(colorChannel(red), colorChannel(green), colorChannel(blue));
    }

    private void updateColorSetting(String moduleName, String redName, Numbers red,
                                    Numbers green, Numbers blue) {
        SettingNodes setting = settingNodes.get(settingKey(moduleName, redName));
        if (setting == null || setting.color == null) return;
        HsvColor hsv = HsvColor.fromRgb(colorChannel(red), colorChannel(green), colorChannel(blue));
        ColorNodes nodes = setting.color;
        nodes.trigger.withInlineStyle("background-color: " + hsv.toHex() + ";");
        setText(nodes.triggerHex, hsv.toHex());
        if (nodes.sv != null) {
            nodes.sv.setAttribute("data-hue", Float.toString(hsv.hue()));
            nodes.sv.withInlineStyle("background: linear-gradient(90deg, #ffffff, "
                    + HsvColor.of(hsv.hue(), 1.0F, 1.0F).toHex() + ");");
        }
        if (nodes.svCursor != null) nodes.svCursor.withInlineStyle("left: "
                + formatPercent(hsv.saturation()) + "%; top: "
                + formatPercent(1.0F - hsv.value()) + "%;");
        if (nodes.hueCursor != null) nodes.hueCursor.withInlineStyle("top: "
                + formatPercent(hsv.hue()) + "%;");
        if (nodes.preview != null) nodes.preview.withInlineStyle("background-color: "
                + hsv.toHex() + ";");
        if (nodes.hex != null) setText(nodes.hex, hsv.toHex());
    }

    private static final class ModuleNodes {
        private final ElementNode group;
        private final ElementNode card;
        private final ElementNode toggle;
        private final ElementNode toggleKnob;
        private final ElementNode accent;
        private ElementNode settings;

        private ModuleNodes(ElementNode group, ElementNode card, ElementNode toggle,
                            ElementNode toggleKnob, ElementNode accent) {
            this.group = group;
            this.card = card;
            this.toggle = toggle;
            this.toggleKnob = toggleKnob;
            this.accent = accent;
            if (group.childCount() > 1 && group.child(1) instanceof ElementNode) {
                this.settings = (ElementNode) group.child(1);
            }
        }
    }

    private static final class SettingNodes {
        private final ElementNode fill;
        private final ElementNode knob;
        private final ElementNode valueText;
        private final ColorNodes color;
        private final ElementNode toggleKnob;

        private SettingNodes(ElementNode fill, ElementNode knob, ElementNode valueText,
                             ColorNodes color, ElementNode toggleKnob) {
            this.fill = fill;
            this.knob = knob;
            this.valueText = valueText;
            this.color = color;
            this.toggleKnob = toggleKnob;
        }

        private static SettingNodes number(ElementNode fill, ElementNode knob, ElementNode valueText) {
            return new SettingNodes(fill, knob, valueText, null, null);
        }

        private static SettingNodes color(ColorNodes color) {
            return new SettingNodes(null, null, null, color, null);
        }

        private static SettingNodes toggle(ElementNode knob) {
            return new SettingNodes(null, null, null, null, knob);
        }
    }

    private static final class ColorNodes {
        private final ElementNode trigger;
        private final ElementNode triggerHex;
        private ElementNode sv;
        private ElementNode svCursor;
        private ElementNode hueCursor;
        private ElementNode preview;
        private ElementNode hex;

        private ColorNodes(ElementNode trigger, ElementNode triggerHex) {
            this.trigger = trigger;
            this.triggerHex = triggerHex;
        }
    }

    private static String formatPercent(double ratio) {
        return String.format(Locale.ROOT, "%.2f", ratio * 100.0);
    }

    private static String formatPixels(double pixels) {
        return String.format(Locale.ROOT, "%.2f", pixels);
    }

    private static final class SettingEntry {
        private final String label;
        private final Value value;
        private final Numbers red;
        private final Numbers green;
        private final Numbers blue;

        private SettingEntry(String label, Value value, Numbers red, Numbers green, Numbers blue) {
            this.label = label;
            this.value = value;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        static SettingEntry value(Value value) {
            return new SettingEntry(safe(value.getDisplayName()), value, null, null, null);
        }

        static SettingEntry color(String label, Numbers red, Numbers green, Numbers blue) {
            return new SettingEntry(label, null, red, green, blue);
        }

        boolean isColor() { return red != null; }
    }

    private List<Module> filteredModules() {
        List<Module> source = ModuleManager.getModulesInType(activeCategory);
        java.util.ArrayList<Module> result = new java.util.ArrayList<Module>();
        String query = normalize(search);
        for (int i = 0; i < source.size(); i++) {
            Module module = source.get(i);
            if (query.isEmpty() || normalize(module.getName()).contains(query)
                    || normalize(module.getChinese()).contains(query)
                    || normalize(module.getDescription()).contains(query)) {
                result.add(module);
            }
        }
        return result;
    }

    private static List<Value> visibleValues(Module module) {
        java.util.ArrayList<Value> result = new java.util.ArrayList<Value>();
        if (module != null) {
            List<Value> values = module.getValues();
            for (int i = 0; i < values.size(); i++) {
                Value value = values.get(i);
                if (value != null && value.isVisible()) result.add(value);
            }
        }
        return result;
    }

    private static Value findValue(Module module, String name) {
        if (module == null) return null;
        List<Value> values = module.getValues();
        for (int i = 0; i < values.size(); i++) {
            Value value = values.get(i);
            if (normalize(value.getName()).equals(normalize(name))) return value;
        }
        return null;
    }

    private static String valueText(Value value) {
        if (value instanceof Mode) return ((Mode) value).getModeAsString();
        if (value instanceof ModeProperty) return ((ModeProperty) value).getModeString();
        if (value instanceof Option) return Boolean.TRUE.equals(value.getValue()) ? "ON" : "OFF";
        if (value instanceof Numbers) {
            double number = ((Number) value.getValue()).doubleValue();
            return Math.abs(number - Math.rint(number)) < 0.0001
                    ? Long.toString(Math.round(number)) : String.format(Locale.ROOT, "%.2f", number);
        }
        return safe(String.valueOf(value.getValue()));
    }

    private static int indexOf(Enum[] values, Enum current) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return i;
        return 0;
    }

    private static ElementNode requireId(ElementNode root, String id) {
        ElementNode result = findId(root, id);
        if (result == null) throw new IllegalStateException("ClickGUI resource missing #" + id);
        return result;
    }

    private static ElementNode findId(ElementNode element, String id) {
        if (id.equals(element.id())) return element;
        for (int i = 0; i < element.childCount(); i++) {
            DomNode child = element.child(i);
            if (child instanceof ElementNode) {
                ElementNode result = findId((ElementNode) child, id);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static ElementNode element(String tag, String classes) {
        return ElementNode.create(tag).withClasses(Arrays.asList(classes.split("\\s+")));
    }

    private static AttributeMap attributes(String... pairs) {
        AttributeMap.AttributeMapBuilder builder = AttributeMap.builder();
        for (int i = 0; i + 1 < pairs.length; i += 2) builder.set(pairs[i], pairs[i + 1]);
        return builder.build();
    }

    private static void setText(ElementNode element, String text) {
        element.clearChildren();
        element.appendChild(TextNode.of(text));
    }

    private static ModuleType parseCategory(String name) {
        for (ModuleType type : ModuleType.values()) {
            if (type.name().equalsIgnoreCase(name)) return type;
        }
        return null;
    }

    private static String categoryName(ModuleType type) {
        if (!isChinese()) return type.toString();
        switch (type) {
            case Combat: return "战斗";
            case Movement: return "移动";
            case Player: return "玩家";
            case Render: return "视觉";
            default: return type.toString();
        }
    }

    private static String categoryIcon(ModuleType type) {
        if (type == ModuleType.Combat) return "H";
        if (type == ModuleType.Movement) return "D";
        if (type == ModuleType.Render) return "B";
        if (type == ModuleType.Player) return "A";
        if (type == ModuleType.World) return "C";
        if (type == ModuleType.Config) return "E";
        return "F";
    }

    private static String moduleIcon(Module module) {
        String name = normalize(module == null ? "" : module.getName());
        if (name.contains("antibot") || name.contains("blockhit")
                || name.contains("autoblock") || name.contains("shield")) return "I";
        if (name.contains("backtrack") || name.contains("fakelag")
                || name.contains("timer") || name.contains("wtap")) return "D";
        if (name.contains("aim") || name.contains("bow") || name.contains("target")
                || name.contains("hitselect") || name.contains("hitbox")) return "J";
        if (name.contains("crit") || name.contains("chams")
                || name.contains("fullbright")) return "G";
        if (name.contains("aura") || name.contains("clicker")
                || name.contains("reach")) return "H";
        if (name.contains("render") || name.contains("esp")) return "B";
        if (name.contains("config") || name.contains("storage")
                || name.contains("save") || name.contains("load")) return "E";
        return categoryIcon(module == null ? null : module.getCategory());
    }

    static String settingsIcon() {
        return "F";
    }

    private static boolean isChinese() { return ClickGUI.getLanguage().isChinese(); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String normalize(String value) {
        return safe(value).replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
    }
}
