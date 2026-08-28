# YozakuraUI Agent Guidelines

## Mission

Build a lightweight retained-mode Minecraft UI engine that lets developers author interfaces with an HTML/CSS-like syntax. The target is Minecraft Forge 1.8.9 on Java 8 and LWJGL 2/OpenGL 2.1.

The engine is purpose-built for ClickGUI, HUD, configuration panels, notifications, inventories, and similar in-game interfaces. It is not a general web browser.

## Repository Context

Yozakura Java sources live under `src/main/java/gq/yozakura`. Runtime resources live under `src/main/resources`. Tests live under `src/test/java/gq/yozakura`.

Place the engine under focused packages such as:

```text
src/main/java/gq/yozakura/ui/engine/
  api/          Public engine and document APIs
  dom/          HTML document model and parser
  css/          CSS parser, selectors, cascade, computed styles
  layout/       Box model, flex and absolute layout
  paint/        Paint tree and retained command lists
  render/       OpenGL renderer, batching, textures and clipping
  text/         Fonts, glyph layout, fallback and atlases
  input/        Hit testing, focus, pointer and keyboard events
  animation/    Monotonic animation clock and transitions
  binding/      Observable data and repeaters
  minecraft/    Minecraft host, resources, input and GL state
```

HTML, CSS, fonts, icons, shaders and UI assets belong under:

```text
src/main/resources/assets/yozakura/ui/
```

Do not edit vendored code under `third_party/` unless explicitly tasked.

## Non-Negotiable Architecture

Use this retained pipeline:

```text
HTML -> DOM
CSS -> Stylesheet
DOM + Stylesheet -> ComputedStyle
ComputedStyle -> LayoutBox tree
LayoutBox tree -> PaintCommandList
PaintCommandList -> batched OpenGL rendering
```

Keep parsing, style resolution, layout, painting, rendering, input, animation and Minecraft integration separate.

Do not:

- implement the engine as one large class;
- render the whole UI into a CPU bitmap every frame;
- upload a full-screen texture every frame;
- rebuild the DOM for hover, toggle or setting changes;
- recompute all styles or all layout for a local property change;
- use WebView2, Chromium, CEF, JavaFX WebView, QML or an external browser;
- silently switch to another renderer when initialization fails.

## Supported Scope

The MVP supports only the documented Minecraft-oriented subset.

HTML priorities:

- `ui`, `div`, `span`, `p`, `button`, `label`, `input`, `img`;
- class, id, inline style and `data-*` attributes;
- controlled repeaters/templates for module and setting lists.

CSS priorities:

- type, class, id, descendant and direct-child selectors;
- `:hover`, `:active`, `:focus` and `:checked`;
- cascade, specificity, inheritance and custom variables;
- block, none, flex and absolute/relative positioning;
- box model, min/max constraints, gaps and overflow clipping;
- colors, borders, radius, opacity, shadows and simple gradients;
- typography, translate/scale transforms and transitions;
- `px`, `%`, `vw`, `vh`, `em`, `rem` and `auto`.

Explicitly outside the MVP:

- full browser compatibility;
- network page loading and iframe;
- general browser JavaScript APIs;
- CSS Grid;
- arbitrary CSS filters;
- full SVG, audio, video, WebGL or browser accessibility APIs.

Reject scope creep inside an implementation stage. Record a later task instead.

## Working Protocol

Before implementation:

1. Read the complete repository `AGENTS.md` and the approved YozakuraUI specification.
2. Inspect existing ClickGUI, QML, WebView, font, resource, module/value and OpenGL utilities.
3. Identify files already modified by the user and preserve them.
4. State the current stage, files likely to change and explicit non-goals.

For every behavior change:

1. Add a focused failing test.
2. Run it and confirm the expected failure.
3. Implement the smallest complete behavior.
4. Run the focused test again.
5. Refactor only while the test remains green.
6. Run the relevant engine test slice.
7. Inspect the final diff and run `git diff --check` for touched files.

Do not begin the next architectural stage until the current stage meets its acceptance criteria.

Do not run `runClient` or `runServer` unless the user explicitly requests it.

## Java Rules

- Use UTF-8 and four-space indentation.
- Preserve Java 8 source and bytecode compatibility.
- Public types use `PascalCase`; fields and methods use `camelCase`.
- Prefer immutable value objects for parsed CSS values, selectors and paint commands.
- Avoid per-frame streams, reflection, boxing and temporary collections.
- Use explicit lifecycle methods for native and OpenGL resources.
- Resource disposal must be idempotent.
- Do not rely on finalizers for OpenGL cleanup.
- Comments explain constraints and algorithms, not obvious syntax.
- Avoid adding dependencies without first explaining compatibility, size and purpose.

## Dirty-State Model

Keep distinct invalidation flags:

- `STYLE_DIRTY`: selector state, inherited property or CSS variable changed;
- `LAYOUT_DIRTY`: dimensions, position, font metrics or child geometry changed;
- `PAINT_DIRTY`: color, opacity, border, shadow or visual content changed;
- `COMMANDS_DIRTY`: paint command list must be regenerated;
- `RESOURCE_DIRTY`: image, glyph or atlas region must be uploaded.

Invalidation propagates only as far as required. A module toggle, hover state or color change must not automatically rebuild the entire document.

Static frames may replay retained GPU commands, but must not reparse HTML/CSS, relayout the tree or upload the full UI texture.

## OpenGL Rules

All OpenGL operations occur on Minecraft's render thread.

Batch compatible rectangles, glyphs and images by shader, texture and clipping state. Minimize draw calls without changing visual order.

Every rendering path, including exceptions, must restore:

- framebuffer;
- viewport;
- projection and model-view matrices;
- current shader program;
- active texture unit and texture bindings;
- blend state and blend functions;
- alpha test;
- depth test and depth mask;
- scissor and stencil state;
- current color.

Nested clipping must use a verified scissor/stencil stack. Rounded clipping, shadows and FBO effects must stay bounded to the UI element; never blur or cover the full Minecraft framebuffer accidentally.

Do not create GPU objects on one context/thread and dispose of them from a Java reference/finalizer thread.

## Text Rules

Use a cached glyph-based renderer, not whole-string CPU textures rebuilt each frame.

The font system must account for:

- baseline, ascent, descent and line height;
- kerning and glyph advances;
- regular and bold faces;
- English and CJK fallback;
- Minecraft GUI Scale and physical-pixel alignment;
- non-integer UI scale;
- atlas growth and bounded cache behavior.

New glyph uploads update only the changed atlas region. Default-size UI text should map cleanly to physical pixels. Visual verification must include small text at common GUI scales.

## Input and Coordinates

Rendering, layout and hit testing share one logical coordinate space. Minecraft scaled coordinates and physical framebuffer coordinates are converted only in the host/viewport layer.

Pointer events carry explicit values for left, right and middle buttons. Do not treat every click as a left click.

Support pointer capture for sliders, dragging, resizing and scroll gestures. A captured pointer continues receiving move/up events outside the original element.

Focus, text editing and keyboard routing must have one owner. Closing the UI restores cursor, repeat-key and focus state.

## Animation

Use one monotonic clock. Animation progress is time-based, not frame-count-based.

Transitions must support interruption and reversal. Separate paint-only transitions from layout transitions. Keep rendering active while an animation is running, then return to the static retained path.

## Performance Targets

Use a reference scene of 960x640 logical pixels, approximately 500 visible nodes and 100 modules.

Targets after warm-up:

- no HTML/CSS parsing on steady frames;
- no full-tree layout on steady frames;
- no complete CPU framebuffer upload;
- no large steady-frame allocation stream;
- ordinary incremental layout below 2 ms on the development machine;
- paint-command replay below 2 ms where hardware permits;
- local hover/toggle updates limited to affected nodes;
- cached glyphs and images;
- visible performance counters for style, layout, paint, draw calls and uploads.

Optimization must be evidence-based. Add counters or benchmarks before introducing complex caches.

## Testing

Place tests under `src/test/java/gq/yozakura/ui/engine` and name them `*Test`.

At minimum, cover:

- HTML parsing and useful syntax errors;
- CSS parsing, specificity, cascade and inheritance;
- custom variables and `var()` resolution;
- box model and unit resolution;
- flex row/column and constraints;
- absolute positioning and overflow;
- paint order and z-index;
- dirty-state propagation;
- text measurement and fallback selection;
- left/right/middle pointer events;
- hover, active, focus and capture;
- transition timing and interruption;
- Minecraft GUI Scale coordinate conversion;
- GL state guard contracts;
- resource disposal and repeated shutdown.

Prefer deterministic numeric and command-list assertions. Screenshots may supplement tests but do not replace them.

## Commands

Focused tests:

```powershell
.\gradlew.bat test --tests "gq.yozakura.ui.engine.*" --offline --no-daemon
```

All tests:

```powershell
.\gradlew.bat test --offline --no-daemon
```

Build without rerunning tests after a successful test gate:

```powershell
.\gradlew.bat build -x test --offline --no-daemon
```

Do not treat compilation as proof of in-game rendering correctness.

## Dependencies

Before adding a dependency, report:

- exact artifact and version;
- Java 8 compatibility;
- transitive dependencies;
- runtime and JAR size impact;
- why a focused internal implementation is insufficient;
- license and resource-loading implications.

Pin accepted versions in `build.gradle`. Do not download or commit arbitrary binaries.

## Fallback Policy

Do not add silent fallbacks. A parser, shader, font, texture or renderer failure must identify the resource and root cause. Do not quietly switch from the custom engine to QML or WebView2.

## Definition of Done

A stage is complete only when:

- its approved acceptance criteria are met;
- new behavior has focused tests;
- relevant tests pass;
- the project builds;
- no unsupported fallback was introduced;
- no unrelated user change was overwritten;
- modified files and known limitations are reported;
- any unverified Minecraft/OpenGL behavior is explicitly listed for manual testing.

The final engine is complete only when a real Minecraft ClickGUI can be authored from separate HTML-like and CSS files and supports transparent rendering, real module data, toggles, right-click settings, search, scrolling, text input, dragging, resizing, animation, font fallback and configuration persistence without WebView2 or full-frame CPU texture uploads.
