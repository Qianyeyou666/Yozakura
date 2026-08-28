#pragma once

#ifndef NOMINMAX
#define NOMINMAX
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#include <cstddef>

namespace yozakura::injector::ui {

enum class UiState {
    Boot,
    Expanding,
    Ready,
    Injecting,
    Success,
    Failed
};

struct AppState {
    UiState uiState = UiState::Boot;
    DWORD started = 0;
    DWORD expandStarted = 0;
    int selectedVersion = 0;
    DWORD detectedPid = 0;
    char detectedProfile[64] = {};
    DWORD injectStarted = 0;
    DWORD injectFinished = 0;
    bool dragging = false;
    POINT dragMouse = {};
    POINT dragWindow = {};
    char status[512] = "Ready";
    wchar_t dllPath[MAX_PATH] = {};
};

void setStatus(AppState& state, const char* text);
void selectProfile(AppState& state, int profile, const char* status);
void beginExpansion(AppState& state, DWORD now);
void finishExpansion(AppState& state);
void beginInjection(AppState& state, DWORD now, const char* status);
void completeInjection(AppState& state, bool ok, DWORD now, const char* status);
void failInjectionStart(AppState& state, DWORD now, const char* status);
void resetToReady(AppState& state);

} // namespace yozakura::injector::ui
