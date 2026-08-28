package gq.yozakura.module.render.hud;

import gq.yozakura.module.Module;

public final class ModuleListEntry {
    public final Module module;
    public final ModuleListLabel label;
    public final int labelWidth;
    public final int nameWidth;
    public final String sideText;
    public final int sideWidth;
    public final String fullText;

    public ModuleListEntry(Module module, ModuleListLabel label, int labelWidth, int nameWidth,
                           String sideText, int sideWidth, String fullText) {
        this.module = module;
        this.label = label;
        this.labelWidth = labelWidth;
        this.nameWidth = nameWidth;
        this.sideText = sideText == null ? "" : sideText;
        this.sideWidth = sideWidth;
        this.fullText = fullText == null ? "" : fullText;
    }
}
