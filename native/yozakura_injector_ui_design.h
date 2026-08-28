#pragma once

#include "yozakura_injector_ui_state.h"

#include <cstdint>

namespace yozakura::injector::ui {

struct Rgba {
    std::uint8_t r;
    std::uint8_t g;
    std::uint8_t b;
    std::uint8_t a;
};

struct RectF {
    float minX;
    float minY;
    float maxX;
    float maxY;

    float width() const { return maxX - minX; }
    float height() const { return maxY - minY; }
};

struct TerminalMetrics {
    float outerBorder;
    float titleBarHeight;
    float contentPaddingX;
    float contentPaddingTop;
    float lineHeight;
    float scrollbarWidth;
    float cornerRadius;
};

struct TerminalLayout {
    RectF window;
    RectF titleBar;
    RectF content;
    RectF scrollTrack;
};

enum class TerminalPhase {
    Boot,
    Waiting,
    Injecting,
    Success,
    Failure
};

struct MotionValue {
    float from = 0.0f;
    float to = 0.0f;
    float current = 0.0f;
    DWORD started = 0;
    DWORD duration = 0;
};

const TerminalMetrics& terminalMetrics();
TerminalLayout calculateTerminalLayout(float width, float height);
TerminalPhase terminalPhaseForState(UiState state);

namespace terminalColor {

Rgba background(std::uint8_t alpha = 255);
Rgba titleBar(std::uint8_t alpha = 255);
Rgba border(std::uint8_t alpha = 255);
Rgba text(std::uint8_t alpha = 255);
Rgba muted(std::uint8_t alpha = 255);
Rgba accent(std::uint8_t alpha = 255);
Rgba accentDim(std::uint8_t alpha = 255);
Rgba success(std::uint8_t alpha = 255);
Rgba failure(std::uint8_t alpha = 255);
Rgba warning(std::uint8_t alpha = 255);
Rgba track(std::uint8_t alpha = 255);
Rgba thumb(std::uint8_t alpha = 255);

} // namespace terminalColor

float easeOutCubic(float value);
float sampleMotion(MotionValue& motion, DWORD now);
void redirectMotion(MotionValue& motion, float target, DWORD now, DWORD duration);

} // namespace yozakura::injector::ui
