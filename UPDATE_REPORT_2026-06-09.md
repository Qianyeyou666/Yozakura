# Yozakura Update Report - 2026-06-09

## Summary

This update focuses on rendering quality, ClickGUI interaction, reusable UI components, and event dispatch performance.

## Rendering

- Reworked `RenderUtil` to prefer shader-backed drawing for solid rectangles, gradients, rounded rectangles, rounded borders, and soft shadows.
- Added `ShaderRenderer` with lazy cached GLSL 1.20 shader programs and GL11 fallback behavior.
- Fixed rounded shadow rendering so shadow layers no longer expand from mutated temporary rectangle state.
- Updated 2D render state to disable alpha test and culling during UI rendering, improving rounded-edge antialiasing.

## ClickGUI

- Rebuilt the ClickGUI layout toward a Material/Vape-style three-zone interface:
  - left module browser
  - center module settings panel
  - optional right-side user/stat/module summary panel
- Added adaptive layout sizing for smaller and wider screens.
- Added fixed-height module rows, cleaner selected-state styling, improved module summary, stats cards, bottom profile/key hints, and category navigation.
- Moved settings rendering into the center detail panel instead of expanding every module card inline.
- Updated list/settings scroll handling so module list and selected-module settings scroll independently.

## Reusable UI Components

Added reusable OOP UI primitives under `gq.yozakura.ui`:

- `UiComponent`
- `UiBounds`
- `UiTheme`
- `UiPanel`
- `UiToggle`
- `UiSlider`
- `UiSelect`
- `UiTextField`

These components encapsulate bounds, hover checks, theme colors, alpha, rendering, and basic input hooks so future GUI screens can reuse controls without copying ClickGUI-specific rendering code.

## Event System

- Reduced reflective event invocation overhead by using cached method handles in `EventManager`.

## Verification

- `.\gradlew.bat --no-daemon compileJava`
- `.\gradlew.bat --no-daemon build`
- `.\gradlew.bat --no-daemon runClient --dry-run`
- `git diff --check`

All verification commands passed. `git diff --check` only reported CRLF conversion warnings from Git.
