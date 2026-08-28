#include "../yozakura_injector_ui_design.h"

#include <cmath>
#include <cstdio>

namespace {

bool require(bool condition, const char* message) {
    if (condition) {
        return true;
    }
    std::fprintf(stderr, "%s\n", message);
    return false;
}

bool nearlyEqual(float actual, float expected, float epsilon = 0.01f) {
    return std::fabs(actual - expected) <= epsilon;
}

} // namespace

int main() {
    using namespace yozakura::injector::ui;

    bool ok = true;
    const TerminalMetrics& metrics = terminalMetrics();
    ok &= require(nearlyEqual(metrics.titleBarHeight, 34.0f)
                      && nearlyEqual(metrics.contentPaddingX, 25.0f)
                      && nearlyEqual(metrics.lineHeight, 18.0f)
                      && nearlyEqual(metrics.cornerRadius, 8.0f),
                  "Terminal UI must preserve compact native-loader geometry");

    const TerminalLayout layout = calculateTerminalLayout(620.0f, 430.0f);
    ok &= require(nearlyEqual(layout.titleBar.maxY, 34.0f)
                      && nearlyEqual(layout.content.minX, 25.0f)
                      && nearlyEqual(layout.content.minY, 56.0f)
                      && nearlyEqual(layout.scrollTrack.maxX, 613.0f),
                  "Terminal layout must derive title, content and scroll rail from shared tokens");

    ok &= require(terminalPhaseForState(UiState::Boot) == TerminalPhase::Boot,
                  "Boot must map to the terminal boot phase");
    ok &= require(terminalPhaseForState(UiState::Ready) == TerminalPhase::Waiting,
                  "Ready must map to automatic process scanning");
    ok &= require(terminalPhaseForState(UiState::Injecting) == TerminalPhase::Injecting,
                  "Injecting must map to the locked terminal phase");
    ok &= require(terminalPhaseForState(UiState::Success) == TerminalPhase::Success,
                  "Success must map to the terminal completion phase");
    ok &= require(terminalPhaseForState(UiState::Failed) == TerminalPhase::Failure,
                  "Failure must map to the terminal error phase");

    MotionValue motion;
    redirectMotion(motion, 1.0f, 100, 150);
    const float halfway = sampleMotion(motion, 175);
    ok &= require(halfway > 0.0f && halfway < 1.0f,
                  "Terminal phase transitions must use monotonic time");
    redirectMotion(motion, 0.0f, 175, 150);
    ok &= require(nearlyEqual(motion.from, halfway),
                  "Reversing a phase transition must start at its sampled value");
    ok &= require(nearlyEqual(sampleMotion(motion, 325), 0.0f),
                  "Redirected terminal motion must reach the new target");

    if (!ok) {
        return 1;
    }
    std::puts("[OK] injector terminal UI design contracts passed.");
    return 0;
}
