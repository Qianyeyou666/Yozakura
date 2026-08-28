#include "yozakura_injector_ui_design.h"

#include <algorithm>

namespace yozakura::injector::ui {
namespace {

const TerminalMetrics kMetrics = {
    1.0f,
    34.0f,
    25.0f,
    22.0f,
    18.0f,
    5.0f,
    8.0f
};

Rgba rgba(int r, int g, int b, std::uint8_t alpha) {
    return Rgba{
        static_cast<std::uint8_t>(r),
        static_cast<std::uint8_t>(g),
        static_cast<std::uint8_t>(b),
        alpha
    };
}

} // namespace

const TerminalMetrics& terminalMetrics() {
    return kMetrics;
}

TerminalLayout calculateTerminalLayout(float width, float height) {
    TerminalLayout layout = {};
    layout.window = RectF{0.0f, 0.0f, width, height};
    layout.titleBar = RectF{
        kMetrics.outerBorder,
        kMetrics.outerBorder,
        width - kMetrics.outerBorder,
        kMetrics.titleBarHeight
    };
    layout.content = RectF{
        kMetrics.contentPaddingX,
        kMetrics.titleBarHeight + kMetrics.contentPaddingTop,
        width - kMetrics.contentPaddingX - kMetrics.scrollbarWidth - 8.0f,
        height - 18.0f
    };
    layout.scrollTrack = RectF{
        width - 12.0f,
        kMetrics.titleBarHeight + 4.0f,
        width - 7.0f,
        height - 7.0f
    };
    return layout;
}

TerminalPhase terminalPhaseForState(UiState state) {
    switch (state) {
        case UiState::Boot:
        case UiState::Expanding:
            return TerminalPhase::Boot;
        case UiState::Ready:
            return TerminalPhase::Waiting;
        case UiState::Injecting:
            return TerminalPhase::Injecting;
        case UiState::Success:
            return TerminalPhase::Success;
        case UiState::Failed:
        default:
            return TerminalPhase::Failure;
    }
}

namespace terminalColor {

Rgba background(std::uint8_t alpha) { return rgba(5, 5, 7, alpha); }
Rgba titleBar(std::uint8_t alpha) { return rgba(12, 12, 16, alpha); }
Rgba border(std::uint8_t alpha) { return rgba(46, 43, 53, alpha); }
Rgba text(std::uint8_t alpha) { return rgba(205, 202, 211, alpha); }
Rgba muted(std::uint8_t alpha) { return rgba(102, 99, 110, alpha); }
Rgba accent(std::uint8_t alpha) { return rgba(157, 132, 183, alpha); }
Rgba accentDim(std::uint8_t alpha) { return rgba(87, 70, 104, alpha); }
Rgba success(std::uint8_t alpha) { return rgba(119, 179, 137, alpha); }
Rgba failure(std::uint8_t alpha) { return rgba(199, 101, 101, alpha); }
Rgba warning(std::uint8_t alpha) { return rgba(188, 158, 103, alpha); }
Rgba track(std::uint8_t alpha) { return rgba(20, 20, 25, alpha); }
Rgba thumb(std::uint8_t alpha) { return rgba(52, 49, 60, alpha); }

} // namespace terminalColor

float easeOutCubic(float value) {
    const float clamped = (std::max)(0.0f, (std::min)(1.0f, value));
    const float inverse = 1.0f - clamped;
    return 1.0f - inverse * inverse * inverse;
}

float sampleMotion(MotionValue& motion, DWORD now) {
    if (motion.duration == 0) {
        motion.current = motion.to;
        return motion.current;
    }
    if (now <= motion.started) {
        motion.current = motion.from;
        return motion.current;
    }
    const DWORD elapsed = now - motion.started;
    if (elapsed >= motion.duration) {
        motion.current = motion.to;
        return motion.current;
    }
    const float progress = easeOutCubic(
        static_cast<float>(elapsed) / static_cast<float>(motion.duration)
    );
    motion.current = motion.from + (motion.to - motion.from) * progress;
    return motion.current;
}

void redirectMotion(MotionValue& motion, float target, DWORD now, DWORD duration) {
    const float sampled = sampleMotion(motion, now);
    motion.from = sampled;
    motion.to = target;
    motion.current = sampled;
    motion.started = now;
    motion.duration = duration;
}

} // namespace yozakura::injector::ui
