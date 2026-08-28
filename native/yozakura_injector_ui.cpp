#include <windows.h>
#include <windowsx.h>
#include <shlwapi.h>
#include <d3d11.h>
#include <dwmapi.h>

#include <cstdio>
#include <cwchar>
#include <string>

#include "imgui.h"
#include "backends/imgui_impl_dx11.h"
#include "backends/imgui_impl_win32.h"
#include "injector_core.h"
#include "yozakura_injector_ui_app.h"
#include "yozakura_injector_ui_state.h"
#include "yozakura_injector_ui_views.h"

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "dxgi.lib")
#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "dwmapi.lib")

extern IMGUI_IMPL_API LRESULT ImGui_ImplWin32_WndProcHandler(
    HWND hwnd,
    UINT message,
    WPARAM wParam,
    LPARAM lParam
);

namespace yozakura::injector::ui {

const wchar_t* kWindowClassName = L"YozakuraInjectorImGuiWindow";
const UINT kInjectDoneMessage = WM_APP + 1;
const UINT kTargetDetectedMessage = WM_APP + 2;

namespace {

constexpr int kWindowWidth = 620;
constexpr int kWindowHeight = 430;
constexpr DWORD kBootHoldMs = 520;
constexpr DWORD kScanRetryMs = 1100;

struct ScanResult {
    DetectedTarget target;
};

struct InjectionWork {
    HWND window = nullptr;
    DWORD pid = 0;
    TargetProfile profile = TargetProfile::Forge189;
    wchar_t title[256] = {};
    wchar_t dllPath[MAX_PATH] = {};
};

struct InjectResult {
    bool ok = false;
    wchar_t message[512] = {};
};

AppState g_app;
UiFonts g_fonts;
InjectorViewState g_view;
bool g_scanRunning = false;
bool g_closeRequested = false;
DWORD g_nextScanAt = 0;
ID3D11Device* g_device = nullptr;
ID3D11DeviceContext* g_context = nullptr;
IDXGISwapChain* g_swapChain = nullptr;
ID3D11RenderTargetView* g_renderTarget = nullptr;

std::string wideToUtf8(const wchar_t* text) {
    if (!text) {
        return "";
    }
    const int bytes = WideCharToMultiByte(CP_UTF8, 0, text, -1, nullptr, 0, nullptr, nullptr);
    if (bytes <= 1) {
        return "";
    }
    std::string output(static_cast<size_t>(bytes), '\0');
    if (WideCharToMultiByte(CP_UTF8, 0, text, -1, &output[0], bytes, nullptr, nullptr) <= 0) {
        return "";
    }
    output.pop_back();
    return output;
}

std::wstring executableDirectory() {
    wchar_t path[MAX_PATH] = {};
    GetModuleFileNameW(nullptr, path, MAX_PATH);
    PathRemoveFileSpecW(path);
    return std::wstring(path);
}

std::wstring resolveAssetPath(const wchar_t* fileName) {
    const std::wstring executable = executableDirectory();
    const std::wstring candidates[] = {
        executable + L"\\" + fileName,
        executable + L"\\assets\\" + fileName,
        executable + L"\\native\\assets\\" + fileName
    };
    for (const std::wstring& candidate : candidates) {
        if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
            return candidate;
        }
    }
    wchar_t workingDirectory[MAX_PATH] = {};
    if (GetCurrentDirectoryW(MAX_PATH, workingDirectory)) {
        const std::wstring candidate = std::wstring(workingDirectory)
            + L"\\native\\assets\\" + fileName;
        if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
            return candidate;
        }
    }
    return L"";
}

bool resolveDllPath(wchar_t* output, DWORD outputCharacters) {
    const std::wstring directory = executableDirectory();
    const wchar_t* names[] = {
        L"\\YozakuraLoader-x64.dll",
        L"\\build\\libs\\YozakuraLoader-x64.dll",
        L"\\YozakuraLoader.dll",
        L"\\build\\libs\\YozakuraLoader.dll",
        L"\\YozakuraReobf-x64.dll",
        L"\\build\\libs\\YozakuraReobf-x64.dll",
        L"\\YozakuraReobf.dll"
    };
    for (const wchar_t* name : names) {
        const std::wstring candidate = directory + name;
        if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
            wcsncpy_s(output, outputCharacters, candidate.c_str(), _TRUNCATE);
            return true;
        }
    }
    return false;
}

void applyRoundedRegion(HWND window, int width, int height) {
    HRGN region = CreateRoundRectRgn(0, 0, width + 1, height + 1, 16, 16);
    if (region && !SetWindowRgn(window, region, TRUE)) {
        DeleteObject(region);
    }
}

void configureWindowComposition(HWND window) {
    const DWORD cornerPreference = 2;
    DwmSetWindowAttribute(window, 33, &cornerPreference, sizeof(cornerPreference));
    const BOOL darkMode = TRUE;
    DwmSetWindowAttribute(window, 20, &darkMode, sizeof(darkMode));
}

void centerWindow(HWND window) {
    const int screenWidth = GetSystemMetrics(SM_CXSCREEN);
    const int screenHeight = GetSystemMetrics(SM_CYSCREEN);
    SetWindowPos(window, nullptr,
                 (screenWidth - kWindowWidth) / 2,
                 (screenHeight - kWindowHeight) / 2,
                 kWindowWidth,
                 kWindowHeight,
                 SWP_NOZORDER | SWP_NOACTIVATE);
    applyRoundedRegion(window, kWindowWidth, kWindowHeight);
}

bool isDragArea(HWND window, LPARAM lParam) {
    POINT point = {GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)};
    RECT client = {};
    GetClientRect(window, &client);
    return point.y >= 0 && point.y < 34 && point.x >= 0 && point.x < client.right - 45;
}

void beginWindowDrag(HWND window) {
    g_app.dragging = true;
    GetCursorPos(&g_app.dragMouse);
    RECT rectangle = {};
    GetWindowRect(window, &rectangle);
    g_app.dragWindow = {rectangle.left, rectangle.top};
    SetCapture(window);
}

void updateWindowDrag(HWND window) {
    if (!g_app.dragging) {
        return;
    }
    POINT cursor = {};
    GetCursorPos(&cursor);
    SetWindowPos(window, nullptr,
                 g_app.dragWindow.x + cursor.x - g_app.dragMouse.x,
                 g_app.dragWindow.y + cursor.y - g_app.dragMouse.y,
                 0,
                 0,
                 SWP_NOZORDER | SWP_NOSIZE | SWP_NOACTIVATE);
}

void endWindowDrag(HWND window, bool releaseCapture) {
    if (!g_app.dragging) {
        return;
    }
    g_app.dragging = false;
    if (releaseCapture) {
        ReleaseCapture();
    }
    applyRoundedRegion(window, kWindowWidth, kWindowHeight);
}

DWORD WINAPI scanThread(LPVOID windowParameter) {
    HWND window = reinterpret_cast<HWND>(windowParameter);
    ScanResult* result = new ScanResult();
    result->target = findBestMinecraftTarget();
    if (!PostMessageW(window, kTargetDetectedMessage, 0, reinterpret_cast<LPARAM>(result))) {
        delete result;
    }
    return 0;
}

void startScan(HWND window) {
    if (g_scanRunning || g_app.uiState != UiState::Ready) {
        return;
    }
    g_scanRunning = true;
    setStatus(g_app, "Scanning for a running Minecraft process...");
    HANDLE thread = CreateThread(nullptr, 0, scanThread, window, 0, nullptr);
    if (!thread) {
        g_scanRunning = false;
        setStatus(g_app, "Detector thread could not be started; retrying...");
        g_nextScanAt = GetTickCount() + kScanRetryMs;
        return;
    }
    CloseHandle(thread);
}

DWORD WINAPI injectThread(LPVOID parameter) {
    InjectionWork* work = reinterpret_cast<InjectionWork*>(parameter);
    InjectResult* result = new InjectResult();
    const InjectionResult injection = injectLibrary(work->pid, work->dllPath);
    result->ok = injection.ok;
    if (injection.ok) {
        swprintf_s(result->message, L"Attached to %s (%s, PID %lu).",
                   work->title[0] ? work->title : L"Minecraft",
                   targetProfileName(work->profile),
                   static_cast<unsigned long>(work->pid));
    } else {
        swprintf_s(result->message, L"%s",
                   injection.message.empty() ? L"Unknown injection error." : injection.message.c_str());
    }
    HWND window = work->window;
    delete work;
    if (!PostMessageW(window, kInjectDoneMessage,
                      injection.ok ? 1 : 0,
                      reinterpret_cast<LPARAM>(result))) {
        delete result;
    }
    return 0;
}

void startDetectedInjection(HWND window, const DetectedTarget& detected) {
    const DWORD now = GetTickCount();
    if (!resolveDllPath(g_app.dllPath, MAX_PATH)) {
        failInjectionStart(g_app, now,
                           "Loader DLL was not found beside this executable.");
        return;
    }

    g_app.selectedVersion = static_cast<int>(detected.profile);
    g_app.detectedPid = detected.process.pid;
    strncpy_s(g_app.detectedProfile, targetProfileNameUtf8(detected.profile), _TRUNCATE);
    beginInjection(g_app, now, "Target locked; attaching native loader...");

    InjectionWork* work = new InjectionWork();
    work->window = window;
    work->pid = detected.process.pid;
    work->profile = detected.profile;
    wcsncpy_s(work->title, detected.process.title.c_str(), _TRUNCATE);
    wcsncpy_s(work->dllPath, g_app.dllPath, _TRUNCATE);
    HANDLE thread = CreateThread(nullptr, 0, injectThread, work, 0, nullptr);
    if (!thread) {
        delete work;
        completeInjection(g_app, false, GetTickCount(),
                          "The background injection task could not be started.");
        return;
    }
    CloseHandle(thread);
}

void cleanupRenderTarget() {
    if (g_renderTarget) {
        g_renderTarget->Release();
        g_renderTarget = nullptr;
    }
}

void createRenderTarget() {
    ID3D11Texture2D* backBuffer = nullptr;
    if (SUCCEEDED(g_swapChain->GetBuffer(0, IID_PPV_ARGS(&backBuffer))) && backBuffer) {
        g_device->CreateRenderTargetView(backBuffer, nullptr, &g_renderTarget);
        backBuffer->Release();
    }
}

bool createDeviceD3D(HWND window) {
    DXGI_SWAP_CHAIN_DESC description = {};
    description.BufferCount = 2;
    description.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    description.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    description.OutputWindow = window;
    description.SampleDesc.Count = 1;
    description.Windowed = TRUE;
    description.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;
    D3D_FEATURE_LEVEL featureLevel = D3D_FEATURE_LEVEL_11_0;
    const D3D_FEATURE_LEVEL levels[] = {
        D3D_FEATURE_LEVEL_11_0,
        D3D_FEATURE_LEVEL_10_0
    };
    HRESULT result = D3D11CreateDeviceAndSwapChain(
        nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0, levels, 2,
        D3D11_SDK_VERSION, &description, &g_swapChain, &g_device,
        &featureLevel, &g_context
    );
    if (result == DXGI_ERROR_UNSUPPORTED) {
        result = D3D11CreateDeviceAndSwapChain(
            nullptr, D3D_DRIVER_TYPE_WARP, nullptr, 0, levels, 2,
            D3D11_SDK_VERSION, &description, &g_swapChain, &g_device,
            &featureLevel, &g_context
        );
    }
    if (FAILED(result)) {
        return false;
    }
    createRenderTarget();
    return g_renderTarget != nullptr;
}

void cleanupDeviceD3D() {
    cleanupRenderTarget();
    if (g_swapChain) {
        g_swapChain->Release();
        g_swapChain = nullptr;
    }
    if (g_context) {
        g_context->Release();
        g_context = nullptr;
    }
    if (g_device) {
        g_device->Release();
        g_device = nullptr;
    }
}

ImFont* addFontFile(const wchar_t* fileName, float size) {
    const std::wstring path = resolveAssetPath(fileName);
    if (path.empty()) {
        return nullptr;
    }
    const std::string utf8Path = wideToUtf8(path.c_str());
    ImFontConfig config;
    config.OversampleH = 2;
    config.OversampleV = 2;
    config.PixelSnapH = true;
    return ImGui::GetIO().Fonts->AddFontFromFileTTF(
        utf8Path.c_str(), size, &config, ImGui::GetIO().Fonts->GetGlyphRangesDefault()
    );
}

void loadUiFonts() {
    ImGuiIO& io = ImGui::GetIO();
    io.Fonts->Clear();
    g_fonts.mono = addFontFile(L"JetBrainsMono.ttf", 13.0f);
    g_fonts.monoMedium = addFontFile(L"JetBrainsMono.ttf", 13.0f);
    if (!g_fonts.mono) {
        g_fonts.mono = io.Fonts->AddFontDefault();
    }
    if (!g_fonts.monoMedium) {
        g_fonts.monoMedium = g_fonts.mono;
    }
    io.FontDefault = g_fonts.mono;
}

void renderFrame(HWND window, DWORD now) {
    ImGui_ImplDX11_NewFrame();
    ImGui_ImplWin32_NewFrame();
    ImGui::NewFrame();
    ImGuiIO& io = ImGui::GetIO();
    InjectorViewInput input = {
        g_app, g_fonts, g_view, window, now, io.DisplaySize.x, io.DisplaySize.y
    };
    const InjectorViewOutput output = drawTerminalApplication(input);
    if (output.closeRequested) {
        PostMessageW(window, WM_CLOSE, 0, 0);
    }
    ImGui::Render();
    const float clearColor[4] = {5.0f / 255.0f, 5.0f / 255.0f, 7.0f / 255.0f, 1.0f};
    g_context->OMSetRenderTargets(1, &g_renderTarget, nullptr);
    g_context->ClearRenderTargetView(g_renderTarget, clearColor);
    ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
    g_swapChain->Present(1, 0);
}

} // namespace

LRESULT WINAPI windowProcedure(HWND window, UINT message, WPARAM wParam, LPARAM lParam) {
    if (ImGui_ImplWin32_WndProcHandler(window, message, wParam, lParam)) {
        return true;
    }
    switch (message) {
        case WM_SIZE:
            if (wParam != SIZE_MINIMIZED && g_swapChain) {
                cleanupRenderTarget();
                g_swapChain->ResizeBuffers(0, LOWORD(lParam), HIWORD(lParam), DXGI_FORMAT_UNKNOWN, 0);
                createRenderTarget();
                applyRoundedRegion(window, LOWORD(lParam), HIWORD(lParam));
            }
            return 0;
        case WM_LBUTTONDOWN:
            if (isDragArea(window, lParam)) {
                beginWindowDrag(window);
                return 0;
            }
            break;
        case WM_MOUSEMOVE:
            updateWindowDrag(window);
            break;
        case WM_LBUTTONUP:
            endWindowDrag(window, true);
            break;
        case WM_CAPTURECHANGED:
            if (reinterpret_cast<HWND>(lParam) != window) {
                endWindowDrag(window, false);
            }
            break;
        case WM_CLOSE:
            if (g_app.uiState == UiState::Injecting) {
                return 0;
            }
            if (g_scanRunning) {
                g_closeRequested = true;
                ShowWindow(window, SW_HIDE);
                return 0;
            }
            DestroyWindow(window);
            return 0;
        case kTargetDetectedMessage: {
            ScanResult* result = reinterpret_cast<ScanResult*>(lParam);
            g_scanRunning = false;
            if (g_closeRequested) {
                delete result;
                DestroyWindow(window);
                return 0;
            }
            if (result && result->target.process.pid != 0) {
                startDetectedInjection(window, result->target);
            } else {
                setStatus(g_app, "No supported Minecraft process found; retrying...");
                g_nextScanAt = GetTickCount() + kScanRetryMs;
            }
            delete result;
            return 0;
        }
        case kInjectDoneMessage: {
            InjectResult* result = reinterpret_cast<InjectResult*>(lParam);
            const bool ok = result && result->ok;
            const std::string messageText = result
                ? wideToUtf8(result->message)
                : "Unknown injection error.";
            completeInjection(g_app, ok, GetTickCount(), messageText.c_str());
            delete result;
            return 0;
        }
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
        default:
            break;
    }
    return DefWindowProcW(window, message, wParam, lParam);
}

int runInjectorApplication(HINSTANCE instance) {
    WNDCLASSEXW windowClass = {
        sizeof(windowClass), CS_CLASSDC, windowProcedure, 0L, 0L, instance,
        nullptr, nullptr, nullptr, nullptr, kWindowClassName, nullptr
    };
    RegisterClassExW(&windowClass);
    HWND window = CreateWindowW(
        kWindowClassName, L"Yozakura Native Loader", WS_POPUP,
        100, 100, kWindowWidth, kWindowHeight,
        nullptr, nullptr, instance, nullptr
    );
    if (!window || !createDeviceD3D(window)) {
        cleanupDeviceD3D();
        if (window) {
            DestroyWindow(window);
        }
        UnregisterClassW(kWindowClassName, instance);
        return 1;
    }

    configureWindowComposition(window);
    centerWindow(window);
    ShowWindow(window, SW_SHOWDEFAULT);
    UpdateWindow(window);

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = nullptr;
    loadUiFonts();
    applyTerminalImGuiStyle();
    ImGui_ImplWin32_Init(window);
    ImGui_ImplDX11_Init(g_device, g_context);

    g_app.started = GetTickCount();
    setStatus(g_app, "Initializing automatic detector...");

    bool finished = false;
    while (!finished) {
        MSG message;
        while (PeekMessageW(&message, nullptr, 0U, 0U, PM_REMOVE)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
            if (message.message == WM_QUIT) {
                finished = true;
            }
        }
        if (finished) {
            break;
        }

        const DWORD now = GetTickCount();
        if (g_app.uiState == UiState::Boot && now - g_app.started >= kBootHoldMs) {
            g_app.uiState = UiState::Ready;
            g_nextScanAt = now;
        }
        if (g_app.uiState == UiState::Ready
            && !g_scanRunning
            && static_cast<LONG>(now - g_nextScanAt) >= 0) {
            startScan(window);
        }
        renderFrame(window, now);
    }

    ImGui_ImplDX11_Shutdown();
    ImGui_ImplWin32_Shutdown();
    ImGui::DestroyContext();
    cleanupDeviceD3D();
    if (IsWindow(window)) {
        DestroyWindow(window);
    }
    UnregisterClassW(kWindowClassName, instance);
    return 0;
}

} // namespace yozakura::injector::ui
