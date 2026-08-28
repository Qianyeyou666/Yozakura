#include "yozakura_injector_ui_state.h"

#include <cstring>

namespace yozakura::injector::ui {

void setStatus(AppState& state, const char* text) {
    strncpy_s(state.status, text ? text : "", _TRUNCATE);
}

void selectProfile(AppState& state, int profile, const char* status) {
    state.selectedVersion = profile;
    setStatus(state, status);
}

void beginExpansion(AppState& state, DWORD now) {
    state.uiState = UiState::Expanding;
    state.expandStarted = now;
}

void finishExpansion(AppState& state) {
    state.uiState = UiState::Ready;
}

void beginInjection(AppState& state, DWORD now, const char* status) {
    state.uiState = UiState::Injecting;
    state.injectStarted = now;
    state.injectFinished = 0;
    setStatus(state, status);
}

void completeInjection(AppState& state, bool ok, DWORD now, const char* status) {
    state.uiState = ok ? UiState::Success : UiState::Failed;
    state.injectFinished = now;
    setStatus(state, status);
}

void failInjectionStart(AppState& state, DWORD now, const char* status) {
    state.uiState = UiState::Failed;
    state.injectStarted = now;
    state.injectFinished = now;
    setStatus(state, status);
}

void resetToReady(AppState& state) {
    state.uiState = UiState::Ready;
    state.injectStarted = 0;
    state.injectFinished = 0;
    setStatus(state, "Ready");
}

} // namespace yozakura::injector::ui
