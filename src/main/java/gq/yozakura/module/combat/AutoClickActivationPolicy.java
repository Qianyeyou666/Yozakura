package gq.yozakura.module.combat;

final class AutoClickActivationPolicy {
    private AutoClickActivationPolicy() {
    }

    static boolean isActive(boolean holdToClick, boolean attackButtonDown) {
        return !holdToClick || attackButtonDown;
    }

    static boolean hasValidTarget(boolean triggerMode, boolean entityHovered) {
        return !triggerMode || entityHovered;
    }
}
