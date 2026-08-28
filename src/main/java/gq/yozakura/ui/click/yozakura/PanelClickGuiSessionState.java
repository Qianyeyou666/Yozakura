package gq.yozakura.ui.click.yozakura;

import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;

final class PanelClickGuiSessionState {
    private ModuleType selectedCategory = ModuleType.Combat;
    private String selectedModuleName = "";
    private boolean moduleDetailOpen;
    private boolean clientSettingsMode;
    private boolean configManagerMode;
    private boolean cloudConfigMode;
    private boolean sidebarExpanded = true;
    private float moduleScroll;
    private float detailScroll;
    private int configProfileScroll;
    private int cloudConfigScroll;
    private String searchText = "";

    synchronized void capture(ModuleType category, Module selectedModule,
                              boolean moduleDetailOpen, boolean clientSettingsMode,
                              boolean configManagerMode, boolean cloudConfigMode,
                              boolean sidebarExpanded, float moduleScroll, float detailScroll,
                              int configProfileScroll, int cloudConfigScroll, String searchText) {
        this.selectedCategory = category == null ? ModuleType.Combat : category;
        this.selectedModuleName = selectedModule == null ? "" : selectedModule.getName();
        this.moduleDetailOpen = moduleDetailOpen && selectedModule != null;
        this.clientSettingsMode = clientSettingsMode;
        this.configManagerMode = configManagerMode;
        this.cloudConfigMode = cloudConfigMode;
        this.sidebarExpanded = sidebarExpanded;
        this.moduleScroll = Math.max(0.0f, moduleScroll);
        this.detailScroll = Math.max(0.0f, detailScroll);
        this.configProfileScroll = Math.max(0, configProfileScroll);
        this.cloudConfigScroll = Math.max(0, cloudConfigScroll);
        this.searchText = searchText == null ? "" : searchText;
    }

    synchronized Snapshot restore() {
        Module selectedModule = selectedModuleName.isEmpty()
                ? null : ModuleManager.getModule(selectedModuleName);
        return new Snapshot(selectedCategory, selectedModule,
                moduleDetailOpen && selectedModule != null,
                clientSettingsMode, configManagerMode, cloudConfigMode,
                sidebarExpanded, moduleScroll, detailScroll,
                configProfileScroll, cloudConfigScroll, searchText);
    }

    static final class Snapshot {
        final ModuleType selectedCategory;
        final Module selectedModule;
        final boolean moduleDetailOpen;
        final boolean clientSettingsMode;
        final boolean configManagerMode;
        final boolean cloudConfigMode;
        final boolean sidebarExpanded;
        final float moduleScroll;
        final float detailScroll;
        final int configProfileScroll;
        final int cloudConfigScroll;
        final String searchText;

        Snapshot(ModuleType selectedCategory, Module selectedModule,
                 boolean moduleDetailOpen, boolean clientSettingsMode,
                 boolean configManagerMode, boolean cloudConfigMode,
                 boolean sidebarExpanded, float moduleScroll, float detailScroll,
                 int configProfileScroll, int cloudConfigScroll, String searchText) {
            this.selectedCategory = selectedCategory;
            this.selectedModule = selectedModule;
            this.moduleDetailOpen = moduleDetailOpen;
            this.clientSettingsMode = clientSettingsMode;
            this.configManagerMode = configManagerMode;
            this.cloudConfigMode = cloudConfigMode;
            this.sidebarExpanded = sidebarExpanded;
            this.moduleScroll = moduleScroll;
            this.detailScroll = detailScroll;
            this.configProfileScroll = configProfileScroll;
            this.cloudConfigScroll = cloudConfigScroll;
            this.searchText = searchText;
        }
    }
}
