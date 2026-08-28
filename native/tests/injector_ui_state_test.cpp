#include "../yozakura_injector_ui_state.h"

#include <cstdio>
#include <cstring>

namespace {

bool require(bool condition, const char* message) {
    if (condition) {
        return true;
    }
    std::fprintf(stderr, "%s\n", message);
    return false;
}

} // namespace

int main() {
    using namespace yozakura::injector::ui;

    AppState state;
    bool ok = true;
    ok &= require(state.uiState == UiState::Boot, "initial state must be Boot");

    beginExpansion(state, 100);
    ok &= require(state.uiState == UiState::Expanding && state.expandStarted == 100,
                  "beginExpansion must capture the monotonic timestamp");
    finishExpansion(state);
    ok &= require(state.uiState == UiState::Ready, "finishExpansion must enter Ready");

    selectProfile(state, 2, "Target profile selected");
    ok &= require(state.selectedVersion == 2 && std::strcmp(state.status, "Target profile selected") == 0,
                  "selectProfile must update selection and status together");

    beginInjection(state, 200, "Searching Lunar target...");
    ok &= require(state.uiState == UiState::Injecting
                      && state.injectStarted == 200
                      && state.injectFinished == 0,
                  "beginInjection must reset result timing");

    completeInjection(state, true, 250, "Injected");
    ok &= require(state.uiState == UiState::Success
                      && state.injectFinished == 250
                      && std::strcmp(state.status, "Injected") == 0,
                  "completeInjection must publish success atomically");

    resetToReady(state);
    ok &= require(state.uiState == UiState::Ready
                      && state.injectStarted == 0
                      && state.injectFinished == 0
                      && std::strcmp(state.status, "Ready") == 0,
                  "resetToReady must clear the injection lifecycle");

    failInjectionStart(state, 300, "loader missing");
    ok &= require(state.uiState == UiState::Failed
                      && state.injectStarted == 300
                      && state.injectFinished == 300,
                  "failInjectionStart must produce an immediate terminal state");

    if (!ok) {
        return 1;
    }
    std::puts("[OK] injector UI state contracts passed.");
    return 0;
}
