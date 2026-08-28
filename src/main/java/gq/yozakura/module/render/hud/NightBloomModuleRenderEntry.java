package gq.yozakura.module.render.hud;

import gq.yozakura.module.render.NightBloomModuleRowMotion;

public final class NightBloomModuleRenderEntry {
    public final ModuleListEntry entry;
    public final NightBloomModuleRowMotion.Snapshot snapshot;

    public NightBloomModuleRenderEntry(ModuleListEntry entry, NightBloomModuleRowMotion.Snapshot snapshot) {
        this.entry = entry;
        this.snapshot = snapshot;
    }
}
