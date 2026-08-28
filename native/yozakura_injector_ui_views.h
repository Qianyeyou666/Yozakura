#pragma once

#define IMGUI_DEFINE_MATH_OPERATORS
#include "imgui.h"
#include "yozakura_injector_ui_design.h"
#include "yozakura_injector_ui_state.h"

#ifndef NOMINMAX
#define NOMINMAX
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

namespace yozakura::injector::ui {

struct UiFonts {
    ImFont* mono = nullptr;
    ImFont* monoMedium = nullptr;
};

struct InjectorViewState {
    TerminalPhase renderedPhase = TerminalPhase::Boot;
    MotionValue phaseOpacity;
    MotionValue closeHover;
    float scrollOffset = 0.0f;
    bool initialized = false;
};

struct InjectorViewInput {
    AppState& app;
    UiFonts& fonts;
    InjectorViewState& visual;
    HWND window;
    DWORD now;
    float width;
    float height;
};

struct InjectorViewOutput {
    bool closeRequested = false;
};

void applyTerminalImGuiStyle();
InjectorViewOutput drawTerminalApplication(const InjectorViewInput& input);

} // namespace yozakura::injector::ui
