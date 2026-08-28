package gq.yozakura.ui.click.timewarp;

import gq.yozakura.module.Module;

/** Retains outgoing page data until its visual exit has completed. */
public final class TimewarpClickGuiPageTransition {
    public enum Page {
        MODULES,
        DETAIL,
        CONFIGS,
        SETTINGS
    }

    private Page current = Page.MODULES;
    private Page outgoing;
    private Module detailModule;
    private Module outgoingModule;
    private float progress = 1.0f;

    public void navigate(Page target, Module module) {
        Page resolved = target == null ? Page.MODULES : target;
        if (resolved == current && (resolved != Page.DETAIL || module == detailModule)) {
            return;
        }
        outgoing = current;
        outgoingModule = detailModule;
        current = resolved;
        detailModule = resolved == Page.DETAIL ? module : null;
        progress = 0.0f;
    }

    public void advance(float frameScale) {
        progress += (1.0f - progress) * Math.min(1.0f, 0.24f * Math.max(0.0f, frameScale));
        if (progress >= 0.995f) {
            progress = 1.0f;
            outgoing = null;
            outgoingModule = null;
        }
    }

    public Page current() { return current; }
    public Page outgoing() { return outgoing; }
    public Module detailModule() { return detailModule; }
    public Module outgoingModule() { return outgoingModule; }
    public float progress() { return TimewarpClickGuiAnimation.easeOutCubic(progress); }
    public boolean isTransitioning() { return outgoing != null; }
}
