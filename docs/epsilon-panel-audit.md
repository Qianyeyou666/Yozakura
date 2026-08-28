# Epsilon Panel Mode 复刻审计

参考版本：`NekoyaHouse/Epsilon` 分支 `26.1.x`，提交
`874e4b98849fa38853d152f2da461fd80c851c9d`。结论来自源码，不来自 README
或截图。Epsilon 源文件路径以下均相对于该提交的仓库根目录。

## 关键校正

Epsilon 26.1.x 的 Panel Mode 是一个固定居中的单窗口三列布局：category
rail、module list、module detail。它不是多个分类浮动面板；源码中没有分类
panel 拖动、分类 panel z-order、模块右键展开设置或多 panel 独立窗口状态。
后续提示词中要求的这些行为不能标记为“Epsilon 已确认行为”。

## 1. 源文件到功能映射

| 源文件 | 功能 |
|---|---|
| `modules/impl/ClientSetting.java:33-36` | `GuiMode.Dropdown/Panel` 枚举 |
| `modules/impl/ClientSetting.java:94-100` | GUI keybind、模式切换；`Panel` 直接打开 `PanelScreen.INSTANCE` |
| `gui/panel/PanelScreen.java:39-48` | Panel 状态、scene、popup、输入 router 和子视图 |
| `gui/panel/PanelScreen.java:79-174` | render target、布局、坐标换算、绘制和 popup flush |
| `gui/panel/PanelScreen.java:223-355` | 左/右/中键、滚轮、拖动、键盘、关闭和 transient reset |
| `gui/panel/PanelLayout.java:11-34` | 面板尺寸、居中位置和三列几何 |
| `gui/panel/PanelState.java:30-54` | 分类、模块、搜索、popup、binding、滚动和 Client Settings 状态 |
| `gui/panel/input/PanelInputRouter.java` | popup 优先的输入路由 |
| `modules/Category.java:7-31` | Epsilon 分类枚举、翻译名称和图标 |
| `gui/panel/view/CategoryRailPanel.java:26-44` | rail 尺寸、分类 item 尺寸、rail 动画 |
| `gui/panel/view/CategoryRailPanel.java:185-210` | rail 左键命中和分类选择 |
| `gui/panel/view/ModuleListPanel.java:82-137` | 模块列表内容、viewport、滚动、行动画 |
| `gui/panel/view/ModuleListPanel.java:184-252` | module row、switch 和 scrollbar 输入 |
| `gui/panel/component/ModuleRow.java:20-24` | 模块行高度、keybind clip 和开关间距 |
| `gui/panel/component/ModuleRow.java:73-111` | 模块文字、颜色、基线、背景和 marquee |
| `gui/panel/view/ModuleDetailPanel.java:153-310` | detail header、module keybind、Toggle/Hold、Visible/Hidden 和 setting 输入 |
| `gui/panel/adapter/SettingViewFactory.java` | Setting 类型到 SettingRow 的映射 |
| `gui/panel/adapter/SettingListController.java:29-32` | setting group 几何常量 |
| `gui/panel/adapter/SettingListController.java:167-229` | setting 点击、slider 捕获和 popup 创建 |
| `gui/panel/component/setting/*.java` | Boolean、Enum、Int、Double、Color、Keybind、String、List、Button |
| `gui/panel/popup/*.java` | Enum、Color、StringList、RegistryList 和消息 popup |
| `gui/theme/MD3Theme.java:10-125` | palette、圆角、阴影、间距和控件度量 |
| `graphics/text/StaticFontLoader.java:21-31` | `font.ttf`、`icons.ttf`、Jura、Osaka |
| `graphics/renderers/TextRenderer.java:50-82` | 字体高度/宽度测量和实际 baseline 计算输入 |
| `graphics/schedulers/render2d/Render2DScissor.java` | 逻辑 viewport 到 scissor 的边界转换 |
| `utils/render/animation/Animation.java:21-56` | 时间驱动动画、当前值重定向和反向 |
| `utils/render/animation/Easing.java:7-37` | easing 函数公式 |

## 2. 分类、顺序和入口

Epsilon 分类顺序是：

```text
COMBAT -> PLAYER -> MOVEMENT -> RENDER
```

来源：`modules/Category.java:7-12`。每项包含 icon 和翻译 key；rail 绘制
使用 `Category.values()`，来源：`gui/panel/view/CategoryRailPanel.java:118-175`。

GUI 模式枚举为 `Dropdown`、`Panel`，来源：
`modules/impl/ClientSetting.java:33-36`。模式回调在
`modules/impl/ClientSetting.java:96-99` 中选择 `PanelScreen.INSTANCE`。

Panel 是单例 screen 宿主；`PanelState` 在 screen 生命周期中保存选中分类、
模块、搜索、popup、binding、滚动和 Client Settings tab，来源：
`gui/panel/PanelState.java:30-54`。

## 3. 可确认的几何常量

### 面板和列

| 项目 | 值/公式 | 来源 |
|---|---:|---|
| panel width | `min(screenWidth * 0.56, 584)`，再至少 `528` | `gui/panel/PanelLayout.java:11-15` |
| panel height | `min(screenHeight * 0.56, 324)`，再至少 `300` | `gui/panel/PanelLayout.java:11-15` |
| 初始位置 | `(screen - panel) / 2` | `gui/panel/PanelLayout.java:17-18` |
| outer padding | `5` | `gui/theme/MD3Theme.java:59` |
| section gap | `3` | `gui/theme/MD3Theme.java:60` |
| module width | `min(164, panelWidth * 0.292)` | `gui/panel/PanelLayout.java:25-27` |
| rail width | collapsed `42`，expanded `120` | `gui/theme/MD3Theme.java:67-68` |
| title inset | `6` | `gui/theme/MD3Theme.java:63` |
| viewport inset | `3` | `gui/theme/MD3Theme.java:64` |
| row content/trailing inset | `5 / 5` | `gui/theme/MD3Theme.java:65-66` |

源码没有分类 panel 的独立坐标、拖动偏移、z-order 或 panel header hitbox。

### 圆角、阴影、控件

| 项目 | 值 | 来源 |
|---|---:|---|
| panel/section/card radius | `17 / 13 / 9` | `gui/theme/MD3Theme.java:48-50` |
| chip radius | `999` | `gui/theme/MD3Theme.java:51` |
| panel shadow blur/alpha | `24 / 96` | `gui/theme/MD3Theme.java:52-53` |
| popup shadow blur/alpha | `14 / 112` | `gui/theme/MD3Theme.java:54-55` |
| floating label shadow blur/alpha | `12 / 96` | `gui/theme/MD3Theme.java:56-57` |
| control height/radius | `18 / 7` | `gui/theme/MD3Theme.java:69-70` |
| compact chip height | `16` | `gui/theme/MD3Theme.java:71` |
| switch | `26 x 16` | `gui/theme/MD3Theme.java:72-73` |
| switch handle off/on | `8 / 12` | `gui/theme/MD3Theme.java:74-75` |
| handle inset off/on | `4 / 2` | `gui/theme/MD3Theme.java:76-77` |
| switch state layer | `20` | `gui/theme/MD3Theme.java:78` |
| icon button | `20 x 20` | `gui/panel/component/PanelElements.java:20-22` |

源码没有 panel border width 常量；外框是 shadow + surface，不应推导为固定
`1px` border。

### 行、文字和基线

| 项目 | 值/公式 | 来源 |
|---|---:|---|
| category item | 高 `34`，步距 `38`，起点 `40` | `gui/panel/view/CategoryRailPanel.java:26-30` |
| module row | 高 `34` | `gui/panel/component/ModuleRow.java:20` |
| module keybind clip | 最大可见宽 `40` | `gui/panel/component/ModuleRow.java:22` |
| keybind/switch gap | `8` | `gui/panel/component/ModuleRow.java:24` |
| setting row | 高 `28` | `gui/panel/component/SettingRow.java:28-30` |
| setting group header | `30` | `gui/panel/adapter/SettingListController.java:29-32` |
| setting group child inset | `4` | `gui/panel/adapter/SettingListController.java:29-32` |
| module title/subtitle/keybind scale | `0.70 / 0.60 / 0.60` | `gui/panel/component/ModuleRow.java:73-80` |
| module title/subtitle gap | `2` | `gui/panel/component/ModuleRow.java:80-84` |
| setting label scale | `0.68` | `gui/panel/component/setting/BoolSettingRow.java:29-36` |
| enum chip text scale | `0.60` | `gui/panel/component/setting/EnumSettingRow.java:35-44` |
| enum chip | 最大宽 `96`、padding `8`、trailing slot `10` | `gui/panel/component/PanelElements.java:74-78` |
| keybind chip | `56 x 18`、radius `8`、text scale `0.52` | `gui/panel/component/setting/KeybindSettingRow.java:54-84` |
| color swatch | `12 x 12`、radius `5` | `gui/panel/component/setting/ColorSettingRow.java:18-32` |

Epsilon 没有固定像素 baseline。文字 Y 由 `TextRenderer.getHeight(scale)` 计算，
例如模块标题公式在 `ModuleRow.java:77-84`；因此基准模型应保存 scale 和
垂直计算公式，而不是伪造一个统一 baseline offset。

### Slider 与滚动条

Int/Double slider 的 track 是 `72 x 6`，位于行右侧，数字 field 为 `40 x 18`，
来源：`IntSettingRow.java:97-102`、`DoubleSettingRow.java:97-102`。
pointer hitbox 在 track 上下各扩大 `6`，来源：`IntSettingRow.java:228-230`。
整数/浮点值均使用 `min + round((raw - min) / step) * step`，来源：
`IntSettingRow.java:214-220`、`DoubleSettingRow.java:214-220`。

滚动条常量：宽 `2`、right inset `2.5`、最小 thumb 高 `10`、hit width `10`、
hover width `2.5`，来源：`graphics/.../UiScrollBar.java:18-23`。thumb 高度公式
在 `UiScrollBar.java:99-111`。滚轮冲量为 `-scrollY * 24`，每帧速度乘 `0.86`，
低于 `0.3` 停止，来源：`ModuleListPanel.java:82-86`、`248-252`。

## 4. 主题颜色、字体和图标

默认暗色 MD3 palette：

| token | RGBA |
|---|---|
| shadow | `(0,0,0,96)` |
| surface | `(20,18,24,238)` |
| surface dim | `(15,13,19,232)` |
| container low/normal/high/highest | `(29,27,32,240)` / `(33,31,38,244)` / `(43,41,48,248)` / `(54,52,59,252)` |
| outline / outline soft | `(147,143,153,180)` / `(147,143,153,96)` |
| primary | `(208,188,255,255)` |
| on-primary | `(56,30,114,255)` |
| primary container | `(79,55,139,236)` |
| on-primary container | `(234,221,255,255)` |
| secondary | `(204,194,220,255)` |
| secondary container | `(74,68,88,236)` |
| on-secondary container | `(232,222,248,255)` |
| tertiary | `(239,184,200,255)` |
| tertiary container | `(99,59,72,236)` |
| text primary/secondary/muted | `(230,224,233,255)` / `(202,196,208,255)` / `(147,143,153,255)` |
| error | `(242,184,181,255)` |

来源：`gui/theme/MD3Theme.java:10-43`。这些是默认 TonalSpot Dark，而不是
永恒常量；`themePreset x themeMode` 会在 `MD3Theme.java:83-120` 动态替换。

字体资源和 loader：`assets/epsilon/fonts/font.ttf`、`icons.ttf`、
`jura-light.ttf`、`osakachips.ttf`，来源：`graphics/text/StaticFontLoader.java:21-31`。
普通文字走 default font，图标走 `StaticFontLoader.ICONS`；Category 图标来自
`modules/Category.java:9-12`。实际 glyph baseline、CJK fallback 和抗锯齿仍需
运行截图确认。

## 5. 输入状态机

| 状态 | 输入 | 条件 | 结果 |
|---|---|---|---|
| normal | 左键 rail menu | menu 命中 | 切换 sidebar expanded |
| normal | 左键 category | category item 命中 | 退出 Client Settings，选分类，module scroll 清零 |
| normal | 左键 settings | settings item 命中 | 进入 Client Settings |
| normal | 左键 module switch | switch 区命中 | `module.toggle()` |
| normal | 左键 module row | 非 switch 区 | 选择模块，detail 显示其设置 |
| normal | 右键/中键 module row | 任意 | 无普通列表行为 |
| normal | 左键 module keybind | header control 命中 | 进入 module binding |
| module binding | Escape | — | 取消 |
| module binding | Backspace/Delete | — | 写入 `-1`，结束 binding |
| module binding | 其他键 | — | 写入键码，结束 binding |
| module binding | 鼠标键 | binding 状态 | 写入 mouse-button encoding |
| setting binding | 键/鼠标 | 单一 focus owner | 写入 KeybindSetting，结束 binding |
| slider | 左键 track | expanded hitbox | 捕获 pointer，按 min/max/step 更新 |
| slider dragging | drag/release | 捕获存在 | 离开原组件仍更新，release 清除捕获 |
| enum | 左键 row | — | 打开 EnumSelectPopup |
| popup active | 普通 panel 输入 | — | popup 优先，底层 hover 坐标失效 |
| color popup | 左键 channel track | — | 捕获 RGB 或 RGBA channel |
| text focus | key/char/IME | — | 编辑缓冲和 caret |
| removed | GUI 关闭 | — | 清 popup、drag、binding、IME transient state |

来源：`PanelScreen.java:223-355`、`ModuleDetailPanel.java:153-310`、
`SettingListController.java:167-275`。Epsilon 没有 panel drag 或 panel z-order
状态机。

## 6. 设置支持矩阵

| Epsilon 类型 | 交互 | VapuLite 现有对应 |
|---|---|---|
| BoolSetting | 左键反转，elastic switch | `Option<Boolean>` |
| EnumSetting | 左键 popup，最多 5 项，popup 滚轮 `20` | `Mode<Enum>` |
| IntSetting | slider、文本编辑、step 量化 | `Numbers<Integer>` |
| DoubleSetting | slider、文本编辑、step 量化 | `Numbers<Double/Float>` |
| ColorSetting | RGB/RGBA channel popup、拖动、数字输入 | 无真实 Color Value；当前为 RGB `Numbers` |
| KeybindSetting | 单键盘焦点、Escape、Backspace/Delete、鼠标键 | 无通用 Value Keybind |
| StringSetting | 字符串、caret、IME、最大 256 字符 | 无真实 Text/String Value |
| StringListSetting | 列表 popup | 无 |
| RegistryListSetting | registry popup | 无 |
| ButtonSetting | 左键 action | 无 |
| SettingGroup | 可折叠 section、count chip | 无直接对应 |

VapuLite Value 源码：`src/main/java/gq/yozakura/value/Value.java:11-56`、
`Mode.java:3-34`、`Numbers.java:3-71`、`Option.java:3-8`。不能用 UI-only
临时值冒充真实双向配置同步；缺少的 Value 类型必须单独建模并接入现有持久化。

## 7. 绘制层级与裁剪

```text
render target clear/resize
└─ scene.beginFrame
   ├─ CHROME -20: panel shadow -> panel surface -> rail/modules/detail surface
   ├─ CONTENT -20: category rail
   ├─ CONTENT 0: module list viewport
   ├─ CONTENT 20: module detail viewport
   ├─ scene.flush + content buffers
   ├─ POPUP layer
   └─ popup buffer + IME overlay
render target -> Minecraft GUI framebuffer
```

来源：`PanelScreen.java:79-174`、`176-219`。module list、detail setting list、
enum popup 各自使用 viewport/content buffer；popup 激活时普通 panel 不再接收
hover。嵌套 scissor 由 render/viewport 层负责，不能把物理 framebuffer 坐标散落
到组件中。

## 8. 当前 VapuLite 对应接口和差异

| 项目 | 当前 VapuLite | 差异 |
|---|---|---|
| 入口 | `ClickGUI.Renderer.OPENGL_PANEL`，`new YozakuraPanelClickGui()` | Epsilon 使用 `GuiMode.Panel` + singleton screen |
| 分类 | `Combat, Render, Movement, Player, World, Other, Config` | Epsilon 只有 `Combat, Player, Movement, Render` |
| Panel geometry | 基本复制 528–584 / 300–324，并加 `16px` margin | 小屏结果不同；margin 非 Epsilon 来源 |
| window drag | 有 `windowX/windowY` | Epsilon Panel 无窗口拖动 |
| module row | `34px` | 高度一致，字体/基线/keybind 规则不同 |
| setting row | 当前 `34px` | Epsilon 明确是 `28px` |
| scrollbar | `4px`，最小 thumb `18` | Epsilon `2px`，最小 thumb `10`，hit width `10` |
| scroll | target + approach speed `72` | Epsilon velocity `24` + `0.86` 衰减 |
| Value | `Mode`、`Numbers`、`Option` | 缺 Color/Keybind/Text/List/Button |
| 主题 | Night Bloom/Sakura/Ocean/Graphite/Custom | Epsilon MD3 preset/mode 动态 palette |
| GUI close | 清理键盘 repeat、scrollbar drag、binding | 需继续核对持久化与 focus 完整性 |

VapuLite 来源：`src/main/java/gq/yozakura/module/render/ClickGUI.java:15-164`、
`src/main/java/gq/yozakura/module/ModuleType.java:5-33`、
`src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java:29-100`、
`PanelClickGuiLayout.java:8-40`、`PanelClickGuiScroll.java:5-36`、
`ClickGuiValueRenderer.java:52-81`。

## 9. 缺失能力

1. 集中的不可变 Epsilon theme/metrics 模型。
2. Epsilon 的 `28px` setting row 与 SettingGroup 布局。
3. 独立 SettingRow、controller、popup host 和 pointer capture。
4. Color、Keybind、Text、StringList、RegistryList、Button 的真实 Value 数据源。
5. popup-first 输入路由及底层 hover 屏蔽。
6. 单一 keyboard/IME focus owner。
7. Epsilon velocity scrolling 和 `2px/10px` scrollbar。
8. 字体实际 metrics 驱动的 baseline、CJK fallback 和 keybind marquee。
9. `ThemePreset x ThemeMode` palette 同步。
10. content signature、局部 dirty 和静态 retained replay。
11. host/viewport 边界内的 GUI Scale 与物理 scissor 转换。
12. render target、GL state、资源 disposal 契约测试。

以下项目不属于 Epsilon 26.1.x 复刻，应单独标记为 VapuLite 扩展：分类 panel
拖动、分类 panel z-order、多 panel 独立滚动、module 右键展开和 animated
expanded module height。

## 10. 分阶段文件修改清单

### 阶段 A：度量和主题模型

```text
src/main/java/gq/yozakura/ui/click/yozakura/EpsilonPanelMetrics.java
src/main/java/gq/yozakura/ui/click/yozakura/EpsilonPanelPalette.java
src/test/java/gq/yozakura/ui/click/yozakura/EpsilonPanelMetricsTest.java
```

只集中确定性数值、颜色和字体 scale；逻辑像素保留为 float，物理像素换算只
放在 host/viewport 边界。

### 阶段 B：Panel host 分解

```text
YozakuraPanelClickGui.java
PanelClickGuiLayout.java
panel/EpsilonPanelState.java
panel/EpsilonPanelInputRouter.java
panel/EpsilonPanelScene.java
panel/view/EpsilonCategoryRail.java
panel/view/EpsilonModuleList.java
panel/view/EpsilonModuleDetail.java
```

先决定是否保留 VapuLite 专属 window drag；不能将它标为 Epsilon 行为。

### 阶段 C：模块行

```text
panel/component/EpsilonModuleRow.java
panel/adapter/ModuleViewModel.java
```

覆盖 switch/row 命中分离、左/右/中键、selection、enabled、hover、marquee 和
局部 dirty；不实现 module expanded。

### 阶段 D：Setting 基础设施

```text
panel/component/SettingRow.java
panel/adapter/SettingViewFactory.java
panel/adapter/SettingListController.java
panel/popup/PanelPopupHost.java
panel/input/PointerCapture.java
panel/input/FocusOwner.java
```

先接现有 `Option`、`Mode`、`Numbers`，不创建平行配置存储。

### 阶段 E：缺失 Value 类型

若允许扩展 Value 层，再添加 `ColorValue`、`KeybindValue`、`TextValue`、
`StringListValue`、`ActionValue`，并接入已有配置序列化；否则这些控件必须保持
“缺少真实数据源”的未完成状态。

### 阶段 F：滚动、裁剪和动画

替换/调整 `PanelClickGuiMotion.java`、`PanelClickGuiScroll.java`，加入确定性
clock、velocity scroll、capture、nested clip balance、GUI Scale 1/2/3/4 和
非整数 scale 测试。

### 阶段 G：像素验收

固定 `960x640` logical viewport，分别验证 GUI Scale 1/2/3/4；生成参考截图、
当前截图、alpha overlay、absolute difference，并分别记录 geometry、color、
baseline、clip、icon 差异。不得整体缩放截图、覆盖参考图或预渲染完整 GUI。

## 11. 必须截图验证的项目

- `font.ttf` 在 GUI Scale 1/2/3/4 下的 glyph raster、hinting 和抗锯齿；
- `0.60/0.62/0.68/0.70/0.78` scale 的实际可见字号；
- visual-height normalization 对 baseline 的影响；
- shadow shader 的边缘扩散和 alpha 合成；
-半透明 surface 在世界背景上的最终颜色；
- category icon 的视觉边界和光学居中；
- elastic switch 的可见 overshoot；
- popup shadow 是否被 viewport 或 render target 截断；
- 非整数 render scale 的 subpixel positioning；
- Minecraft GUI Scale 与 Epsilon renderScale 叠加后的比例；
- CJK fallback 的宽度、baseline 和行高；
- marquee 的具体时间相位；
- IME preedit overlay 对齐；
- Light theme 和各 ThemePreset 的最终 palette；
- render-target blit 的半像素、采样和颜色空间偏差。

## 12. 当前工作树与验证边界

本审计文档写入不代表任何 Java 行为已经实现。后续每个行为切片必须遵守：

1. 先添加确定性失败测试并确认 RED；
2. 实现最小行为并确认 GREEN；
3. 运行相关 Panel/UI 测试；
4. 运行 build 和 `git diff --check`；
5. 明确未验证的 Minecraft/OpenGL、字体和截图项目。

