package gq.vapulite.util.animation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Keyed animation progress storage for retained-mode UI widgets.
 */
public final class AnimationState {
    private static final int EXPECTED_UI_ANIMATION_KEYS = 512;

    private final Map<String, Float> values = new HashMap<String, Float>(EXPECTED_UI_ANIMATION_KEYS);
    private final Map<String, Integer> updatedFrames = new HashMap<String, Integer>(EXPECTED_UI_ANIMATION_KEYS);

    public float animate(String key, float target, float speed, float frameScale) {
        return animateFrom(key, target, speed, frameScale, target);
    }

    public float animateFrom(String key, float target, float speed, float frameScale, float initialValue) {
        Float current = values.get(key);
        float next = AnimationUtil.approach(current == null ? initialValue : current.floatValue(), target, speed, frameScale);
        values.put(key, next);
        return next;
    }

    public float animateFrom(String key, float target, float speed, float frameScale, float initialValue, int frameId) {
        Float current = values.get(key);
        Integer updatedFrame = updatedFrames.get(key);
        if (current != null && updatedFrame != null && updatedFrame.intValue() == frameId) {
            return current.floatValue();
        }
        float next = AnimationUtil.approach(current == null ? initialValue : current.floatValue(), target, speed, frameScale);
        values.put(key, next);
        updatedFrames.put(key, frameId);
        return next;
    }

    public float eased(String key, float target, float speed, float frameScale, float initialValue,
                       AnimationUtil.Ease ease) {
        return AnimationUtil.ease(animateFrom(key, target, speed, frameScale, initialValue), ease);
    }

    public float eased(String key, float target, float speed, float frameScale, float initialValue,
                       AnimationUtil.Ease ease, int frameId) {
        return AnimationUtil.ease(animateFrom(key, target, speed, frameScale, initialValue, frameId), ease);
    }

    public void snap(String key, float value) {
        values.put(key, value);
    }

    public void ensure(String key, float value) {
        if (!values.containsKey(key)) {
            values.put(key, value);
        }
    }

    public void clear() {
        values.clear();
        updatedFrames.clear();
    }

    public void clearPrefix(String prefix) {
        Iterator<String> iterator = values.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(prefix)) {
                iterator.remove();
            }
        }
        Iterator<String> frameIterator = updatedFrames.keySet().iterator();
        while (frameIterator.hasNext()) {
            if (frameIterator.next().startsWith(prefix)) {
                frameIterator.remove();
            }
        }
    }
}
