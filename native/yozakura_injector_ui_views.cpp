#include "yozakura_injector_ui_views.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>

namespace yozakura::injector::ui {
namespace {

constexpr DWORD kPhaseTransitionMs = 150;
constexpr float kPi = 3.1415926535f;

ImU32 color(Rgba value, float opacity = 1.0f) {
    const float clamped = (std::max)(0.0f, (std::min)(1.0f, opacity));
    return IM_COL32(value.r, value.g, value.b,
                    static_cast<int>(static_cast<float>(value.a) * clamped));
}

ImFont* fontOrDefault(ImFont* font) {
    return font ? font : ImGui::GetFont();
}

void addText(ImDrawList* draw, ImFont* font, float size, ImVec2 position,
             ImU32 tint, const char* text) {
    draw->AddText(fontOrDefault(font), size, position, tint, text ? text : "");
}

void drawCloseButton(ImDrawList* draw, const TerminalLayout& layout,
                     const InjectorViewInput& input, InjectorViewOutput& output) {
    const bool enabled = input.app.uiState != UiState::Injecting;
    const ImVec2 min(layout.window.maxX - 38.0f, 7.0f);
    const ImVec2 max(layout.window.maxX - 9.0f, 28.0f);
    ImGui::SetCursorScreenPos(min);
    ImGui::InvisibleButton("terminal_close", max - min);
    if (enabled && ImGui::IsItemClicked(ImGuiMouseButton_Left)) {
        output.closeRequested = true;
    }
    const bool hovered = enabled && ImGui::IsItemHovered();
    const float target = hovered ? 1.0f : 0.0f;
    if (input.visual.closeHover.to != target) {
        redirectMotion(input.visual.closeHover, target, input.now, 100);
    }
    const float hover = sampleMotion(input.visual.closeHover, input.now);
    if (hover > 0.001f) {
        draw->AddRectFilled(min, max, color(terminalColor::border(), hover * 0.72f), 4.0f);
    }
    const ImU32 tint = color(enabled ? terminalColor::muted() : terminalColor::border(), 1.0f);
    const ImVec2 center = (min + max) * 0.5f;
    draw->AddLine(center + ImVec2(-3.7f, -3.7f), center + ImVec2(3.7f, 3.7f), tint, 1.15f);
    draw->AddLine(center + ImVec2(3.7f, -3.7f), center + ImVec2(-3.7f, 3.7f), tint, 1.15f);
}

void drawTitleBar(ImDrawList* draw, const TerminalLayout& layout,
                  const InjectorViewInput& input, InjectorViewOutput& output) {
    draw->AddRectFilled(ImVec2(layout.titleBar.minX, layout.titleBar.minY),
                        ImVec2(layout.titleBar.maxX, layout.titleBar.maxY),
                        color(terminalColor::titleBar()), terminalMetrics().cornerRadius,
                        ImDrawFlags_RoundCornersTop);
    draw->AddLine(ImVec2(0.0f, layout.titleBar.maxY),
                  ImVec2(layout.window.maxX, layout.titleBar.maxY),
                  color(terminalColor::border(135)), 1.0f);
    draw->AddCircleFilled(ImVec2(16.0f, 17.0f), 4.0f, color(terminalColor::accent()), 16);
    draw->AddCircle(ImVec2(16.0f, 17.0f), 7.0f, color(terminalColor::accentDim()), 24, 1.0f);
    addText(draw, input.fonts.monoMedium, 11.0f, ImVec2(31.0f, 10.0f),
            color(terminalColor::text()), "Yozakura Native Loader");
    addText(draw, input.fonts.mono, 9.0f, ImVec2(181.0f, 11.0f),
            color(terminalColor::muted()), "AUTO DETECT / X64");
    drawCloseButton(draw, layout, input, output);
}

void drawAsciiBrand(ImDrawList* draw, const TerminalLayout& layout,
                    const InjectorViewInput& input, float opacity) {
    static const char* lines[] = {
        " __   __  ___   _____    _    _  ___   _   _  ____      _    ",
        " \\ \\ / / / _ \\ |__  /   / \\  | |/ / | | | ||  _ \\    / \\   ",
        "  \\ V / | | | |  / /   / _ \\ | ' /  | | | || |_) |  / _ \\  ",
        "   | |  | |_| | / /_  / ___ \\| . \\  | |_| ||  _ <  / ___ \\ ",
        "   |_|   \\___/ /____|/_/   \\_\\_|\\_\\  \\___/ |_| \\_\\/_/   \\_\\"
    };
    float y = layout.content.minY;
    for (const char* line : lines) {
        addText(draw, input.fonts.monoMedium, 12.0f,
                ImVec2(layout.content.minX, y), color(terminalColor::accent(), opacity), line);
        y += 14.0f;
    }
    addText(draw, input.fonts.mono, 9.5f, ImVec2(layout.content.minX + 2.0f, y + 5.0f),
            color(terminalColor::muted(), opacity),
            "Minecraft native module bootstrap / automatic process selection");
}

const char* phaseLabel(TerminalPhase phase) {
    switch (phase) {
        case TerminalPhase::Boot:
            return "INITIALIZING";
        case TerminalPhase::Waiting:
            return "SCANNING";
        case TerminalPhase::Injecting:
            return "INJECTING";
        case TerminalPhase::Success:
            return "COMPLETE";
        case TerminalPhase::Failure:
        default:
            return "FAILED";
    }
}

Rgba phaseColor(TerminalPhase phase) {
    switch (phase) {
        case TerminalPhase::Success:
            return terminalColor::success();
        case TerminalPhase::Failure:
            return terminalColor::failure();
        case TerminalPhase::Waiting:
            return terminalColor::warning();
        default:
            return terminalColor::accent();
    }
}

void drawPromptLine(ImDrawList* draw, const InjectorViewInput& input,
                    float x, float y, const char* prefix, Rgba prefixColor,
                    const char* text, float opacity) {
    addText(draw, input.fonts.monoMedium, 11.0f, ImVec2(x, y),
            color(prefixColor, opacity), prefix);
    const float prefixWidth = fontOrDefault(input.fonts.monoMedium)
        ->CalcTextSizeA(11.0f, 10000.0f, 0.0f, prefix).x;
    addText(draw, input.fonts.mono, 11.0f, ImVec2(x + prefixWidth + 9.0f, y),
            color(terminalColor::text(), opacity), text);
}

void drawSession(ImDrawList* draw, const TerminalLayout& layout,
                 const InjectorViewInput& input, TerminalPhase phase, float opacity) {
    float y = layout.content.minY + 92.0f;
    const float x = layout.content.minX + 2.0f;
    drawPromptLine(draw, input, x, y, "[BOOT]", terminalColor::accent(),
                   "Native runtime initialized", opacity);
    y += terminalMetrics().lineHeight;
    drawPromptLine(draw, input, x, y, "[MODE]", terminalColor::accentDim(),
                   "Automatic Minecraft process discovery", opacity);
    y += terminalMetrics().lineHeight;
    drawPromptLine(draw, input, x, y, "[SCAN]", terminalColor::warning(),
                   "Forge 1.8.9 / Vanilla 1.8.9 / Lunar 1.8.9 / Forge 1.20.1", opacity);
    y += terminalMetrics().lineHeight;

    if (phase == TerminalPhase::Boot) {
        drawPromptLine(draw, input, x, y, "[WAIT]", terminalColor::muted(),
                       "Starting detector...", opacity);
    } else if (phase == TerminalPhase::Waiting) {
        drawPromptLine(draw, input, x, y, "[WAIT]", terminalColor::warning(),
                       input.app.status, opacity);
        y += terminalMetrics().lineHeight;
        drawPromptLine(draw, input, x, y, "[INFO]", terminalColor::muted(),
                       "Start Minecraft; detection retries automatically", opacity);
    } else {
        char target[196] = {};
        sprintf_s(target, "%s  PID %lu",
                  input.app.detectedProfile[0] ? input.app.detectedProfile : "Minecraft",
                  static_cast<unsigned long>(input.app.detectedPid));
        drawPromptLine(draw, input, x, y, "[FOUND]", terminalColor::success(), target, opacity);
        y += terminalMetrics().lineHeight;
        if (phase == TerminalPhase::Injecting) {
            drawPromptLine(draw, input, x, y, "[LOAD]", terminalColor::accent(),
                           "Inspecting modules and attaching YozakuraLoader-x64.dll", opacity);
            y += terminalMetrics().lineHeight;
            drawPromptLine(draw, input, x, y, "[LOCK]", terminalColor::muted(),
                           "Window close is disabled until LoadLibraryW completes", opacity);
        } else if (phase == TerminalPhase::Success) {
            drawPromptLine(draw, input, x, y, "[ OK ]", terminalColor::success(),
                           input.app.status, opacity);
            y += terminalMetrics().lineHeight;
            drawPromptLine(draw, input, x, y, "[DONE]", terminalColor::success(),
                           "You may close this loader and return to Minecraft", opacity);
        } else {
            drawPromptLine(draw, input, x, y, "[ERR ]", terminalColor::failure(),
                           input.app.status, opacity);
            y += terminalMetrics().lineHeight;
            drawPromptLine(draw, input, x, y, "[STOP]", terminalColor::failure(),
                           "Injection stopped; close and restart the loader to retry", opacity);
        }
    }

    const float footerY = layout.window.maxY - 32.0f;
    draw->AddLine(ImVec2(layout.content.minX, footerY - 8.0f),
                  ImVec2(layout.content.maxX, footerY - 8.0f),
                  color(terminalColor::border(105), opacity), 1.0f);
    const char* spinner = "|/-\\";
    char state[64] = {};
    const char frame = spinner[(input.now / 110) % 4];
    sprintf_s(state, "%c  %s", frame, phaseLabel(phase));
    addText(draw, input.fonts.monoMedium, 10.0f, ImVec2(layout.content.minX, footerY),
            color(phaseColor(phase), opacity), state);
    addText(draw, input.fonts.mono, 9.0f, ImVec2(layout.content.maxX - 133.0f, footerY + 1.0f),
            color(terminalColor::muted(), opacity), "Yozakura 2.5 / win64");
}

void drawScrollRail(ImDrawList* draw, const TerminalLayout& layout, TerminalPhase phase) {
    draw->AddRectFilled(ImVec2(layout.scrollTrack.minX, layout.scrollTrack.minY),
                        ImVec2(layout.scrollTrack.maxX, layout.scrollTrack.maxY),
                        color(terminalColor::track()), 2.0f);
    float progress = 0.12f;
    if (phase == TerminalPhase::Waiting) {
        progress = 0.35f;
    } else if (phase == TerminalPhase::Injecting) {
        progress = 0.68f;
    } else if (phase == TerminalPhase::Success || phase == TerminalPhase::Failure) {
        progress = 1.0f;
    }
    const float trackHeight = layout.scrollTrack.height();
    const float thumbHeight = (std::max)(34.0f, trackHeight * 0.18f);
    const float travel = trackHeight - thumbHeight;
    const float minY = layout.scrollTrack.minY + travel * progress;
    draw->AddRectFilled(ImVec2(layout.scrollTrack.minX, minY),
                        ImVec2(layout.scrollTrack.maxX, minY + thumbHeight),
                        color(terminalColor::thumb()), 2.0f);
}

void initializeVisual(InjectorViewState& visual, TerminalPhase phase, DWORD now) {
    visual.renderedPhase = phase;
    visual.phaseOpacity.from = 0.0f;
    visual.phaseOpacity.to = 1.0f;
    visual.phaseOpacity.current = 0.0f;
    visual.phaseOpacity.started = now;
    visual.phaseOpacity.duration = kPhaseTransitionMs;
    visual.initialized = true;
}

} // namespace

void applyTerminalImGuiStyle() {
    ImGuiStyle& style = ImGui::GetStyle();
    style.WindowRounding = terminalMetrics().cornerRadius;
    style.WindowBorderSize = 0.0f;
    style.WindowPadding = ImVec2(0.0f, 0.0f);
    style.ItemSpacing = ImVec2(0.0f, 0.0f);
    style.Colors[ImGuiCol_WindowBg] = ImVec4(5.0f / 255.0f, 5.0f / 255.0f, 7.0f / 255.0f, 1.0f);
    style.Colors[ImGuiCol_Text] = ImVec4(205.0f / 255.0f, 202.0f / 255.0f, 211.0f / 255.0f, 1.0f);
}

InjectorViewOutput drawTerminalApplication(const InjectorViewInput& input) {
    InjectorViewOutput output;
    const TerminalPhase phase = terminalPhaseForState(input.app.uiState);
    if (!input.visual.initialized) {
        initializeVisual(input.visual, phase, input.now);
    } else if (input.visual.renderedPhase != phase) {
        initializeVisual(input.visual, phase, input.now);
    }
    const float opacity = sampleMotion(input.visual.phaseOpacity, input.now);

    ImGui::SetNextWindowPos(ImVec2(0.0f, 0.0f));
    ImGui::SetNextWindowSize(ImVec2(input.width, input.height));
    ImGui::Begin("Yozakura Terminal Loader", nullptr,
                 ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoMove
                 | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoSavedSettings);
    ImDrawList* draw = ImGui::GetWindowDrawList();
    const TerminalLayout layout = calculateTerminalLayout(input.width, input.height);
    draw->AddRectFilled(ImVec2(0.0f, 0.0f), ImVec2(input.width, input.height),
                        color(terminalColor::background()), terminalMetrics().cornerRadius);
    draw->AddRect(ImVec2(0.5f, 0.5f), ImVec2(input.width - 0.5f, input.height - 0.5f),
                  color(terminalColor::border()), terminalMetrics().cornerRadius, 0, 1.0f);
    drawTitleBar(draw, layout, input, output);
    drawAsciiBrand(draw, layout, input, opacity);
    drawSession(draw, layout, input, phase, opacity);
    drawScrollRail(draw, layout, phase);
    ImGui::End();
    return output;
}

} // namespace yozakura::injector::ui
