package gq.yozakura.ui.click.yozakura;

import gq.yozakura.util.animation.AnimationUtil;

import java.util.HashMap;
import java.util.Map;

/** Fixed-duration animations used by the pinned Epsilon Panel implementation. */
public final class EpsilonPanelAnimation {
    public static final long RAIL_CONTENT_MS = 180L;
    public static final long RAIL_HEADER_TITLE_MS = 220L;
    public static final long RAIL_HEADER_SUBTITLE_MS = 260L;
    public static final long RAIL_HEADER_DIVIDER_MS = 220L;
    public static final long RAIL_MENU_HOVER_MS = 120L;
    public static final long RAIL_SELECTION_MS = 180L;
    public static final long RAIL_HOVER_POSITION_MS = 160L;
    public static final long RAIL_HOVER_ALPHA_MS = 100L;
    public static final long MODULE_HOVER_MS = 120L;
    public static final long MODULE_SELECTION_MS = 160L;
    public static final long SEARCH_HOVER_MS = 120L;
    public static final long SEARCH_FOCUS_MS = 120L;
    public static final long SETTING_HOVER_MS = 120L;
    public static final long ENUM_CHEVRON_MS = 180L;
    public static final long TOGGLE_MS = 620L;
    public static final long TOGGLE_HOVER_MS = 120L;
    public static final long SEGMENT_SELECTION_MS = 180L;
    public static final long SEGMENT_HOVER_MS = 120L;
    public static final long KEYBIND_HOVER_MS = 120L;
    public static final long KEYBIND_FOCUS_MS = 150L;
    public static final long SLIDER_HOVER_MS = 150L;
    public static final long SLIDER_PRESS_MS = 120L;
    public static final long SLIDER_INDICATOR_MS = 150L;
    public static final long POPUP_OPEN_MS = 140L;

    private EpsilonPanelAnimation() {
    }

    public static final class State {
        private final Map<String, Value> values = new HashMap<String, Value>();

        public float value(String key, float target, long now, long duration, AnimationUtil.Ease easing) {
            Value value = values.get(key);
            if (value == null) {
                value = new Value(target);
                values.put(key, value);
            }
            return value.valueAt(target, now, duration, easing);
        }

        public void snap(String key, float value) {
            values.put(key, new Value(value));
        }

        public void clear() {
            values.clear();
        }
    }

    public static final class Value {
        private float from;
        private float value;
        private float target;
        private long startTime;
        private boolean initialized;

        public Value(float initialValue) {
            from = initialValue;
            value = initialValue;
            target = initialValue;
        }

        public float valueAt(float requestedTarget, long now, long duration, AnimationUtil.Ease easing) {
            if (!initialized) {
                initialized = true;
                startTime = now;
            }
            value = sample(now, duration, easing);
            if (Float.compare(requestedTarget, target) != 0) {
                from = value;
                target = requestedTarget;
                startTime = now;
            }
            value = sample(now, duration, easing);
            return value;
        }

        private float sample(long now, long duration, AnimationUtil.Ease easing) {
            if (duration <= 0L || Float.compare(from, target) == 0) {
                return target;
            }
            float progress = Math.max(0.0f, Math.min(1.0f, (now - startTime) / (float) duration));
            if (progress >= 1.0f) {
                from = target;
                return target;
            }
            float eased = AnimationUtil.ease(progress, easing);
            return from + (target - from) * eased;
        }
    }
}
