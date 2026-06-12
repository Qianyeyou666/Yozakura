#include <windows.h>
#include <windowsx.h>
#include <tlhelp32.h>
#include <shlwapi.h>
#include <d3d11.h>
#include <dwmapi.h>
#include <wincodec.h>
#include <tchar.h>

#include <cmath>
#include <cwchar>
#include <string>
#include <vector>

#define IMGUI_DEFINE_MATH_OPERATORS
#include "imgui.h"
#include "backends/imgui_impl_dx11.h"
#include "backends/imgui_impl_win32.h"
#include "ui_fonts.h"

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "dxgi.lib")
#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "dwmapi.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "windowscodecs.lib")

extern IMGUI_IMPL_API LRESULT ImGui_ImplWin32_WndProcHandler(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);

namespace {

const wchar_t* kClassName = L"VapuLiteInjectorImGuiWindow";
const UINT kInjectDone = WM_APP + 1;
const float kPi = 3.1415926535f;
const int kBootWidth = 430;
const int kBootHeight = 270;
const int kWindowWidth = 1024;
const int kWindowHeight = 544;

enum UiState {
    STATE_BOOT,
    STATE_EXPANDING,
    STATE_READY,
    STATE_INJECTING,
    STATE_SUCCESS,
    STATE_FAILED
};

struct InjectResult {
    bool ok;
    wchar_t message[512];
};

struct AppState {
    UiState uiState = STATE_BOOT;
    DWORD started = 0;
    DWORD expandStarted = 0;
    int selectedVersion = 0;
    float stageAlpha = 0.0f;
    float contentAlpha = 0.0f;
    float loadingAlpha = 0.0f;
    DWORD injectStarted = 0;
    char status[512] = "Ready";
    wchar_t dllPath[MAX_PATH] = {};
};

struct UiFonts {
    ImFont* regular = nullptr;
    ImFont* medium = nullptr;
    ImFont* semiBold = nullptr;
    ImFont* title = nullptr;
    ImFont* icon8 = nullptr;
    ImFont* icon12 = nullptr;
};

AppState g_app;
UiFonts g_fonts;
ID3D11Device* g_device = nullptr;
ID3D11DeviceContext* g_context = nullptr;
IDXGISwapChain* g_swapChain = nullptr;
ID3D11RenderTargetView* g_renderTarget = nullptr;
ID3D11ShaderResourceView* g_logoTexture = nullptr;
ImVec2 g_logoSize(0.0f, 0.0f);

enum AccentState {
    ACCENT_DISABLED = 0,
    ACCENT_ENABLE_GRADIENT = 1,
    ACCENT_ENABLE_TRANSPARENTGRADIENT = 2,
    ACCENT_ENABLE_BLURBEHIND = 3,
    ACCENT_ENABLE_ACRYLICBLURBEHIND = 4
};

struct AccentPolicy {
    int accentState;
    int accentFlags;
    int gradientColor;
    int animationId;
};

enum WindowCompositionAttribute {
    WCA_ACCENT_POLICY = 19
};

struct WindowCompositionAttributeData {
    WindowCompositionAttribute attribute;
    void* data;
    size_t sizeOfData;
};

using SetWindowCompositionAttributeFn = BOOL(WINAPI*)(HWND, WindowCompositionAttributeData*);

void applyRoundedRegion(HWND hwnd, int width, int height) {
    HRGN region = CreateRoundRectRgn(0, 0, width + 1, height + 1, 32, 32);
    if (region && !SetWindowRgn(hwnd, region, TRUE)) {
        DeleteObject(region);
    }
}

void enableGlassBackground(HWND hwnd) {
    HMODULE user32 = GetModuleHandleW(L"user32.dll");
    SetWindowCompositionAttributeFn setComposition = user32
        ? reinterpret_cast<SetWindowCompositionAttributeFn>(GetProcAddress(user32, "SetWindowCompositionAttribute"))
        : nullptr;
    if (setComposition) {
        AccentPolicy accent = {};
        accent.accentState = ACCENT_ENABLE_ACRYLICBLURBEHIND;
        accent.accentFlags = 2;
        accent.gradientColor = 0x990F0D0E;
        WindowCompositionAttributeData data = {};
        data.attribute = WCA_ACCENT_POLICY;
        data.data = &accent;
        data.sizeOfData = sizeof(accent);
        setComposition(hwnd, &data);
    }

    const MARGINS margins = {-1, -1, -1, -1};
    DwmExtendFrameIntoClientArea(hwnd, &margins);

    const DWORD cornerPreference = 2;
    DwmSetWindowAttribute(hwnd, 33, &cornerPreference, sizeof(cornerPreference));

    const DWORD backdropType = 3;
    DwmSetWindowAttribute(hwnd, 38, &backdropType, sizeof(backdropType));
}

std::string wideToUtf8(const wchar_t* text) {
    if (!text) {
        return "";
    }
    int size = WideCharToMultiByte(CP_UTF8, 0, text, -1, nullptr, 0, nullptr, nullptr);
    if (size <= 1) {
        return "";
    }
    std::string out(size - 1, '\0');
    WideCharToMultiByte(CP_UTF8, 0, text, -1, &out[0], size, nullptr, nullptr);
    return out;
}

void setStatus(const char* text) {
    strncpy_s(g_app.status, text ? text : "", _TRUNCATE);
}

std::wstring exeDirectory() {
    wchar_t path[MAX_PATH] = {};
    GetModuleFileNameW(nullptr, path, MAX_PATH);
    PathRemoveFileSpecW(path);
    return std::wstring(path);
}

std::wstring resolveAssetPath(const wchar_t* fileName) {
    std::wstring dir = exeDirectory();
    std::wstring candidate = dir + L"\\" + fileName;
    if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
        return candidate;
    }
    candidate = dir + L"\\native\\assets\\" + fileName;
    if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
        return candidate;
    }
    wchar_t cwd[MAX_PATH] = {};
    if (GetCurrentDirectoryW(MAX_PATH, cwd)) {
        candidate = std::wstring(cwd) + L"\\native\\assets\\" + fileName;
        if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
            return candidate;
        }
    }
    return L"";
}

bool resolveDllPath(wchar_t* output, DWORD outputChars) {
    std::wstring dir = exeDirectory();
    const wchar_t* names[] = {
        L"\\VapuLiteReobf-x64.dll",
        L"\\build\\libs\\VapuLiteReobf-x64.dll",
        L"\\VapuLiteReobf.dll"
    };
    for (int i = 0; i < 3; ++i) {
        std::wstring candidate = dir + names[i];
        if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
            wcsncpy_s(output, outputChars, candidate.c_str(), _TRUNCATE);
            return true;
        }
    }
    return false;
}

bool isJavaProcess(DWORD pid) {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) {
        return true;
    }
    PROCESSENTRY32W entry = {};
    entry.dwSize = sizeof(entry);
    bool ok = false;
    if (Process32FirstW(snap, &entry)) {
        do {
            if (entry.th32ProcessID == pid) {
                ok = _wcsicmp(entry.szExeFile, L"java.exe") == 0 || _wcsicmp(entry.szExeFile, L"javaw.exe") == 0;
                break;
            }
        } while (Process32NextW(snap, &entry));
    }
    CloseHandle(snap);
    return ok;
}

struct FindWindowContext {
    DWORD pid = 0;
    wchar_t title[256] = {};
};

BOOL CALLBACK enumWindowsProc(HWND hwnd, LPARAM param) {
    if (!IsWindowVisible(hwnd)) {
        return TRUE;
    }
    wchar_t title[256] = {};
    GetWindowTextW(hwnd, title, 256);
    if (wcslen(title) == 0) {
        return TRUE;
    }
    DWORD pid = 0;
    GetWindowThreadProcessId(hwnd, &pid);
    if (pid == 0 || !isJavaProcess(pid)) {
        return TRUE;
    }
    if (wcsstr(title, L"Minecraft 1.8.9") != nullptr) {
        FindWindowContext* ctx = reinterpret_cast<FindWindowContext*>(param);
        ctx->pid = pid;
        wcsncpy_s(ctx->title, title, _TRUNCATE);
        return FALSE;
    }
    return TRUE;
}

DWORD findMinecraft189(wchar_t* title, DWORD titleChars) {
    FindWindowContext ctx;
    EnumWindows(enumWindowsProc, reinterpret_cast<LPARAM>(&ctx));
    if (ctx.pid != 0) {
        wcsncpy_s(title, titleChars, ctx.title, _TRUNCATE);
    }
    return ctx.pid;
}

bool moduleAlreadyLoaded(DWORD pid, const wchar_t* dllPath) {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
    if (snap == INVALID_HANDLE_VALUE) {
        return false;
    }
    wchar_t fullDll[MAX_PATH] = {};
    GetFullPathNameW(dllPath, MAX_PATH, fullDll, nullptr);
    MODULEENTRY32W module = {};
    module.dwSize = sizeof(module);
    bool loaded = false;
    if (Module32FirstW(snap, &module)) {
        do {
            if (_wcsicmp(module.szExePath, fullDll) == 0) {
                loaded = true;
                break;
            }
        } while (Module32NextW(snap, &module));
    }
    CloseHandle(snap);
    return loaded;
}

bool injectDll(DWORD pid, const wchar_t* dllPath, std::wstring& error) {
    wchar_t fullPath[MAX_PATH] = {};
    if (!GetFullPathNameW(dllPath, MAX_PATH, fullPath, nullptr)) {
        error = L"DLL path resolve failed";
        return false;
    }
    if (moduleAlreadyLoaded(pid, fullPath)) {
        return true;
    }

    HANDLE process = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION | PROCESS_VM_OPERATION
                                 | PROCESS_VM_WRITE | PROCESS_VM_READ, FALSE, pid);
    if (!process) {
        error = L"OpenProcess failed, try running as administrator";
        return false;
    }

    SIZE_T bytes = (wcslen(fullPath) + 1) * sizeof(wchar_t);
    LPVOID remotePath = VirtualAllocEx(process, nullptr, bytes, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remotePath) {
        CloseHandle(process);
        error = L"VirtualAllocEx failed";
        return false;
    }

    bool ok = false;
    if (!WriteProcessMemory(process, remotePath, fullPath, bytes, nullptr)) {
        error = L"WriteProcessMemory failed";
    } else {
        HMODULE kernel32 = GetModuleHandleW(L"kernel32.dll");
        LPTHREAD_START_ROUTINE loadLibrary = reinterpret_cast<LPTHREAD_START_ROUTINE>(
            GetProcAddress(kernel32, "LoadLibraryW"));
        HANDLE thread = CreateRemoteThread(process, nullptr, 0, loadLibrary, remotePath, 0, nullptr);
        if (!thread) {
            error = L"CreateRemoteThread failed";
        } else {
            WaitForSingleObject(thread, 12000);
            DWORD exitCode = 0;
            GetExitCodeThread(thread, &exitCode);
            CloseHandle(thread);
            if (exitCode == 0) {
                error = L"LoadLibraryW returned NULL";
            } else {
                ok = true;
            }
        }
    }

    VirtualFreeEx(process, remotePath, 0, MEM_RELEASE);
    CloseHandle(process);
    return ok;
}

DWORD WINAPI injectThread(LPVOID hwndParam) {
    HWND hwnd = reinterpret_cast<HWND>(hwndParam);
    Sleep(950);

    InjectResult* result = new InjectResult();
    result->ok = false;
    result->message[0] = 0;

    wchar_t gameTitle[256] = {};
    DWORD pid = findMinecraft189(gameTitle, 256);
    if (pid == 0) {
        wcsncpy_s(result->message, L"Injection failed: Minecraft 1.8.9 was not found", _TRUNCATE);
        PostMessageW(hwnd, kInjectDone, 0, reinterpret_cast<LPARAM>(result));
        return 0;
    }

    std::wstring error;
    bool ok = injectDll(pid, g_app.dllPath, error);
    result->ok = ok;
    if (ok) {
        swprintf_s(result->message, L"Injected into %s", gameTitle[0] ? gameTitle : L"Minecraft 1.8.9");
    } else {
        swprintf_s(result->message, L"Injection failed: %s", error.empty() ? L"unknown error" : error.c_str());
    }
    PostMessageW(hwnd, kInjectDone, ok ? 1 : 0, reinterpret_cast<LPARAM>(result));
    return 0;
}

void cleanupRenderTarget() {
    if (g_renderTarget) {
        g_renderTarget->Release();
        g_renderTarget = nullptr;
    }
}

void createRenderTarget() {
    ID3D11Texture2D* backBuffer = nullptr;
    g_swapChain->GetBuffer(0, IID_PPV_ARGS(&backBuffer));
    if (backBuffer) {
        g_device->CreateRenderTargetView(backBuffer, nullptr, &g_renderTarget);
        backBuffer->Release();
    }
}

bool createDeviceD3D(HWND hwnd) {
    DXGI_SWAP_CHAIN_DESC sd = {};
    sd.BufferCount = 2;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = hwnd;
    sd.SampleDesc.Count = 1;
    sd.Windowed = TRUE;
    sd.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;
    sd.Flags = DXGI_SWAP_CHAIN_FLAG_ALLOW_MODE_SWITCH;

    D3D_FEATURE_LEVEL featureLevel;
    const D3D_FEATURE_LEVEL featureLevelArray[2] = {D3D_FEATURE_LEVEL_11_0, D3D_FEATURE_LEVEL_10_0};
    HRESULT hr = D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0,
                                               featureLevelArray, 2, D3D11_SDK_VERSION, &sd, &g_swapChain,
                                               &g_device, &featureLevel, &g_context);
    if (hr == DXGI_ERROR_UNSUPPORTED) {
        hr = D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_WARP, nullptr, 0,
                                           featureLevelArray, 2, D3D11_SDK_VERSION, &sd, &g_swapChain,
                                           &g_device, &featureLevel, &g_context);
    }
    if (FAILED(hr)) {
        return false;
    }
    createRenderTarget();
    return true;
}

void cleanupDeviceD3D() {
    cleanupRenderTarget();
    if (g_logoTexture) {
        g_logoTexture->Release();
        g_logoTexture = nullptr;
    }
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

void removeWhiteMatte(std::vector<unsigned char>& pixels) {
    for (size_t i = 0; i + 3 < pixels.size(); i += 4) {
        unsigned char r = pixels[i + 0];
        unsigned char g = pixels[i + 1];
        unsigned char b = pixels[i + 2];
        unsigned char& a = pixels[i + 3];
        if (a == 0) {
            continue;
        }
        int whiteness = min(static_cast<int>(r), min(static_cast<int>(g), static_cast<int>(b)));
        if (whiteness > 250 && a == 255) {
            a = 0;
        } else if (whiteness > 242 && a == 255) {
            float keep = (250.0f - static_cast<float>(whiteness)) / 8.0f;
            if (keep < 0.0f) {
                keep = 0.0f;
            }
            a = static_cast<unsigned char>(static_cast<float>(a) * keep);
        }
    }
}

bool loadTextureFromFile(const wchar_t* path, ID3D11ShaderResourceView** outView, ImVec2* outSize) {
    IWICImagingFactory* factory = nullptr;
    IWICBitmapDecoder* decoder = nullptr;
    IWICBitmapFrameDecode* frame = nullptr;
    IWICFormatConverter* converter = nullptr;

    HRESULT hr = CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
                                  IID_PPV_ARGS(&factory));
    if (SUCCEEDED(hr)) {
        hr = factory->CreateDecoderFromFilename(path, nullptr, GENERIC_READ, WICDecodeMetadataCacheOnLoad, &decoder);
    }
    if (SUCCEEDED(hr)) {
        hr = decoder->GetFrame(0, &frame);
    }
    UINT width = 0;
    UINT height = 0;
    if (SUCCEEDED(hr)) {
        hr = frame->GetSize(&width, &height);
    }
    if (SUCCEEDED(hr)) {
        hr = factory->CreateFormatConverter(&converter);
    }
    if (SUCCEEDED(hr)) {
        hr = converter->Initialize(frame, GUID_WICPixelFormat32bppRGBA, WICBitmapDitherTypeNone,
                                   nullptr, 0.0, WICBitmapPaletteTypeCustom);
    }

    std::vector<unsigned char> pixels;
    if (SUCCEEDED(hr)) {
        pixels.resize(static_cast<size_t>(width) * static_cast<size_t>(height) * 4);
        hr = converter->CopyPixels(nullptr, width * 4, static_cast<UINT>(pixels.size()), pixels.data());
    }
    if (SUCCEEDED(hr)) {
        bool hasTransparency = false;
        for (size_t i = 3; i < pixels.size(); i += 4) {
            if (pixels[i] < 250) {
                hasTransparency = true;
                break;
            }
        }
        if (!hasTransparency) {
            removeWhiteMatte(pixels);
        }
        D3D11_TEXTURE2D_DESC desc = {};
        desc.Width = width;
        desc.Height = height;
        desc.MipLevels = 1;
        desc.ArraySize = 1;
        desc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
        desc.SampleDesc.Count = 1;
        desc.Usage = D3D11_USAGE_DEFAULT;
        desc.BindFlags = D3D11_BIND_SHADER_RESOURCE;

        D3D11_SUBRESOURCE_DATA sub = {};
        sub.pSysMem = pixels.data();
        sub.SysMemPitch = width * 4;

        ID3D11Texture2D* texture = nullptr;
        hr = g_device->CreateTexture2D(&desc, &sub, &texture);
        if (SUCCEEDED(hr)) {
            D3D11_SHADER_RESOURCE_VIEW_DESC viewDesc = {};
            viewDesc.Format = desc.Format;
            viewDesc.ViewDimension = D3D11_SRV_DIMENSION_TEXTURE2D;
            viewDesc.Texture2D.MipLevels = 1;
            hr = g_device->CreateShaderResourceView(texture, &viewDesc, outView);
            texture->Release();
        }
    }

    if (SUCCEEDED(hr) && outSize) {
        *outSize = ImVec2(static_cast<float>(width), static_cast<float>(height));
    }
    if (converter) converter->Release();
    if (frame) frame->Release();
    if (decoder) decoder->Release();
    if (factory) factory->Release();
    return SUCCEEDED(hr);
}

void setDarkStyle() {
    ImGuiStyle& style = ImGui::GetStyle();
    style.WindowRounding = 16.0f;
    style.FrameRounding = 8.0f;
    style.PopupRounding = 8.0f;
    style.ScrollbarRounding = 8.0f;
    style.WindowBorderSize = 0.0f;
    style.FrameBorderSize = 1.0f;
    style.ItemSpacing = ImVec2(10.0f, 8.0f);
    ImVec4* colors = style.Colors;
    colors[ImGuiCol_WindowBg] = ImVec4(14.0f / 255.0f, 13.0f / 255.0f, 15.0f / 255.0f, 1.0f);
    colors[ImGuiCol_Text] = ImVec4(1.0f, 1.0f, 1.0f, 1.0f);
    colors[ImGuiCol_TextDisabled] = ImVec4(0.42f, 0.45f, 0.50f, 1.0f);
    colors[ImGuiCol_FrameBg] = ImVec4(27.0f / 255.0f, 24.0f / 255.0f, 30.0f / 255.0f, 1.0f);
    colors[ImGuiCol_FrameBgHovered] = ImVec4(0.16f, 0.13f, 0.19f, 1.0f);
    colors[ImGuiCol_FrameBgActive] = ImVec4(0.28f, 0.20f, 0.34f, 1.0f);
    colors[ImGuiCol_Button] = ImVec4(27.0f / 255.0f, 24.0f / 255.0f, 30.0f / 255.0f, 1.0f);
    colors[ImGuiCol_ButtonHovered] = ImVec4(0.23f, 0.17f, 0.28f, 1.0f);
    colors[ImGuiCol_ButtonActive] = ImVec4(199.0f / 255.0f, 149.0f / 255.0f, 237.0f / 255.0f, 1.0f);
    colors[ImGuiCol_Border] = ImVec4(1.0f, 1.0f, 1.0f, 0.08f);
}

ImFont* fontOrDefault(ImFont* font) {
    return font ? font : ImGui::GetFont();
}

void addUiFont(ImFont*& out, std::vector<unsigned char>& data, float size) {
    ImFontConfig cfg;
    cfg.FontDataOwnedByAtlas = false;
    cfg.OversampleH = 3;
    cfg.OversampleV = 2;
    out = ImGui::GetIO().Fonts->AddFontFromMemoryTTF(data.data(), static_cast<int>(data.size()), size, &cfg,
                                                     ImGui::GetIO().Fonts->GetGlyphRangesCyrillic());
}

void loadUiFonts() {
    ImGuiIO& io = ImGui::GetIO();
    io.Fonts->Clear();
    addUiFont(g_fonts.regular, suisse_intl_regular_data, 14.0f);
    addUiFont(g_fonts.medium, suisse_intl_medium_data, 16.0f);
    addUiFont(g_fonts.semiBold, suisse_intl_semi_bold_data, 13.0f);
    addUiFont(g_fonts.title, suisse_intl_medium_data, 28.0f);
    addUiFont(g_fonts.icon8, icons_data, 8.0f);
    addUiFont(g_fonts.icon12, icons_data, 12.0f);
    io.FontDefault = fontOrDefault(g_fonts.regular);
}

ImU32 col(int r, int g, int b, int a = 255) {
    return IM_COL32(r, g, b, a);
}

ImU32 colAlpha(int r, int g, int b, float a) {
    if (a < 0.0f) {
        a = 0.0f;
    } else if (a > 1.0f) {
        a = 1.0f;
    }
    return IM_COL32(r, g, b, static_cast<int>(a * 255.0f));
}

void easing(float& value, float target, float speed) {
    float dt = ImGui::GetIO().DeltaTime;
    float step = 1.0f - expf(-speed * dt);
    value += (target - value) * step;
    if (fabsf(value - target) < 0.001f) {
        value = target;
    }
}

void spinner(ImDrawList* draw, ImVec2 center, float radius, ImU32 color) {
    float t = static_cast<float>(GetTickCount() % 5000) / 1000.0f;
    for (int i = 0; i < 12; ++i) {
        float a = t * 6.0f + i * kPi * 2.0f / 12.0f;
        float alpha = (40.0f + i * 17.0f) / 255.0f;
        ImU32 c = (color & 0x00ffffff) | (static_cast<int>(alpha * 255.0f) << 24);
        draw->AddCircleFilled(ImVec2(center.x + cosf(a) * radius, center.y + sinf(a) * radius), 3.0f, c, 12);
    }
}

void drawLogo(ImDrawList* draw, ImVec2 center, float width, float alpha) {
    if (!g_logoTexture || g_logoSize.x <= 0.0f || g_logoSize.y <= 0.0f) {
        draw->AddCircleFilled(center, width * 0.28f, colAlpha(199, 149, 237, 0.18f * alpha), 48);
        draw->AddText(fontOrDefault(g_fonts.title), 28.0f, center - ImVec2(52.0f, 16.0f),
                      colAlpha(255, 255, 255, alpha), "VapuLite");
        return;
    }

    float height = width * (g_logoSize.y / g_logoSize.x);
    ImVec2 size(width, height);
    ImVec2 min = center - size * 0.5f;
    ImVec2 max = center + size * 0.5f;

    draw->AddCircleFilled(center + ImVec2(0.0f, height * 0.08f), width * 0.52f,
                          colAlpha(199, 149, 237, 0.055f * alpha), 72);
    draw->AddCircleFilled(center + ImVec2(0.0f, height * 0.09f), width * 0.36f,
                          colAlpha(210, 126, 255, 0.090f * alpha), 72);
    draw->AddCircleFilled(center + ImVec2(0.0f, height * 0.10f), width * 0.22f,
                          colAlpha(255, 218, 255, 0.075f * alpha), 72);

    draw->AddImage(g_logoTexture, min, max, ImVec2(0, 0), ImVec2(1, 1),
                   colAlpha(255, 255, 255, alpha));
}

void drawRainBackdrop(ImDrawList* draw, ImVec2 size, float alpha) {
    DWORD tick = GetTickCount();
    for (int i = 0; i < 46; ++i) {
        float x = fmodf(i * 73.0f + 19.0f, size.x);
        float y = fmodf(i * 119.0f + tick * (0.055f + (i % 5) * 0.009f), size.y + 120.0f) - 80.0f;
        float len = 34.0f + (i % 7) * 11.0f;
        float lineAlpha = (0.055f + (i % 4) * 0.018f) * alpha;
        draw->AddLine(ImVec2(x, y), ImVec2(x + 4.0f, y + len), colAlpha(178, 190, 255, lineAlpha), 1.0f);
    }
    for (int i = 0; i < 10; ++i) {
        float pulse = 0.55f + 0.45f * sinf((tick * 0.0021f) + i * 1.7f);
        ImVec2 p(fmodf(92.0f + i * 113.0f, size.x), 388.0f + (i % 3) * 46.0f);
        draw->AddCircleFilled(p, 3.0f + pulse * 2.0f, colAlpha(245, 132, 255, (0.18f + pulse * 0.18f) * alpha), 24);
        draw->AddCircleFilled(p, 13.0f + pulse * 5.0f, colAlpha(199, 149, 237, 0.035f * alpha), 32);
    }
}

void drawLoadingStep(ImDrawList* draw, ImVec2 pos, const char* icon, const char* text, int index, float progress, float alpha) {
    float stepPoint = 0.18f + index * 0.22f;
    bool done = progress >= stepPoint + 0.12f;
    bool active = !done && progress >= stepPoint - 0.08f;
    ImU32 textCol = done ? colAlpha(255, 255, 255, 0.76f * alpha)
                         : active ? colAlpha(245, 205, 255, 0.90f * alpha)
                                  : colAlpha(255, 255, 255, 0.34f * alpha);
    ImU32 iconCol = done ? colAlpha(210, 255, 230, 0.90f * alpha)
                         : active ? colAlpha(230, 170, 255, 0.95f * alpha)
                                  : colAlpha(255, 255, 255, 0.38f * alpha);
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, pos, iconCol, icon);
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, pos + ImVec2(38.0f, -2.0f), textCol, text);

    ImVec2 status(pos.x + 510.0f, pos.y - 1.0f);
    if (done) {
        draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, status, colAlpha(210, 225, 255, 0.88f * alpha), "v");
    } else {
        float pulse = active ? 0.45f + 0.55f * sinf(GetTickCount() * 0.010f) : 0.0f;
        draw->AddCircle(status + ImVec2(8.0f, 8.0f), 8.0f, colAlpha(220, 150, 255, (active ? 0.82f : 0.18f) * alpha), 32, 2.0f);
        if (active) {
            draw->AddCircleFilled(status + ImVec2(8.0f, 8.0f), 4.0f + pulse * 2.0f, colAlpha(230, 170, 255, 0.20f * alpha), 32);
        }
    }
}

void drawInjectionLoadingOverlay(ImDrawList* draw, ImVec2 size, float alpha) {
    DWORD now = GetTickCount();
    float elapsed = g_app.injectStarted ? (now - g_app.injectStarted) / 1000.0f : 0.0f;
    float progress = 0.10f + elapsed * 0.21f;
    progress += sinf(elapsed * 2.8f) * 0.015f;
    if (progress > 0.92f) {
        progress = 0.92f + sinf(elapsed * 3.4f) * 0.018f;
    }
    if (progress < 0.0f) {
        progress = 0.0f;
    } else if (progress > 0.96f) {
        progress = 0.96f;
    }

    draw->AddRectFilled(ImVec2(0, 0), size, colAlpha(11, 10, 18, 0.76f * alpha), 16.0f);
    drawRainBackdrop(draw, size, alpha);
    draw->AddCircleFilled(ImVec2(size.x * 0.50f, size.y * 0.30f), 260.0f, colAlpha(95, 110, 195, 0.085f * alpha), 96);
    draw->AddCircleFilled(ImVec2(size.x * 0.50f, size.y * 0.70f), 320.0f, colAlpha(185, 95, 235, 0.050f * alpha), 96);

    ImVec2 cardSize(820.0f, 450.0f);
    ImVec2 cardMin((size.x - cardSize.x) * 0.5f, (size.y - cardSize.y) * 0.5f + 6.0f);
    ImVec2 cardMax = cardMin + cardSize;
    draw->AddRectFilled(cardMin - ImVec2(18.0f, 18.0f), cardMax + ImVec2(18.0f, 18.0f),
                        colAlpha(190, 115, 255, 0.045f * alpha), 28.0f);
    draw->AddRectFilled(cardMin, cardMax, colAlpha(64, 66, 112, 0.47f * alpha), 22.0f);
    draw->AddRectFilled(cardMin + ImVec2(1, 1), cardMax - ImVec2(1, 1),
                        colAlpha(255, 255, 255, 0.035f * alpha), 21.0f);
    draw->AddRect(cardMin, cardMax, colAlpha(235, 232, 255, 0.22f * alpha), 22.0f, 0, 1.2f);
    draw->AddLine(cardMin + ImVec2(26, 1), cardMin + ImVec2(cardSize.x - 26, 1),
                  colAlpha(255, 255, 255, 0.18f * alpha), 1.0f);

    draw->AddCircleFilled(cardMin + ImVec2(34.0f, 46.0f), 4.0f, colAlpha(235, 165, 255, 0.95f * alpha), 16);
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, cardMin + ImVec2(50.0f, 36.0f), colAlpha(255, 255, 255, 0.82f * alpha), "VapuLite");
    draw->AddText(fontOrDefault(g_fonts.medium), 14.0f, cardMin + ImVec2(50.0f, 68.0f), colAlpha(255, 255, 255, 0.36f * alpha), "Liquid Glass");
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, cardMax - ImVec2(74.0f, 394.0f), colAlpha(255, 255, 255, 0.52f * alpha), "v2.5.0");
    draw->AddText(fontOrDefault(g_fonts.medium), 14.0f, cardMax - ImVec2(146.0f, 362.0f), colAlpha(255, 255, 255, 0.42f * alpha), "Sakura Theme");
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, cardMax - ImVec2(48.0f, 360.0f), colAlpha(255, 185, 242, 0.90f * alpha), "*");

    ImVec2 logoCenter(cardMin.x + cardSize.x * 0.5f, cardMin.y + 120.0f);
    draw->AddCircleFilled(logoCenter, 72.0f, colAlpha(205, 115, 255, 0.11f * alpha), 72);
    draw->AddCircleFilled(logoCenter, 42.0f, colAlpha(244, 178, 255, 0.12f * alpha), 72);
    drawLogo(draw, logoCenter, 96.0f, alpha);

    draw->AddText(fontOrDefault(g_fonts.title), 38.0f, cardMin + ImVec2(330.0f, 164.0f), colAlpha(255, 220, 255, 0.94f * alpha), "VapuLite");
    draw->AddText(fontOrDefault(g_fonts.medium), 20.0f, cardMin + ImVec2(306.0f, 220.0f), colAlpha(255, 255, 255, 0.45f * alpha), "Loading your experience...");

    ImVec2 barMin(cardMin.x + 194.0f, cardMin.y + 272.0f);
    ImVec2 barMax(cardMin.x + 660.0f, cardMin.y + 280.0f);
    float barW = barMax.x - barMin.x;
    float fillW = barW * progress;
    draw->AddRectFilled(barMin, barMax, colAlpha(255, 255, 255, 0.085f * alpha), 5.0f);
    draw->AddRectFilled(barMin - ImVec2(5.0f, 5.0f), ImVec2(barMin.x + fillW + 5.0f, barMax.y + 5.0f),
                        colAlpha(232, 116, 255, 0.075f * alpha), 9.0f);
    draw->AddRectFilled(barMin, ImVec2(barMin.x + fillW, barMax.y), colAlpha(243, 152, 255, 0.92f * alpha), 5.0f);
    draw->AddRectFilledMultiColor(barMin, ImVec2(barMin.x + fillW, barMax.y),
                                  colAlpha(255, 185, 255, 0.90f * alpha),
                                  colAlpha(200, 120, 255, 0.90f * alpha),
                                  colAlpha(200, 120, 255, 0.90f * alpha),
                                  colAlpha(255, 185, 255, 0.90f * alpha));
    ImVec2 knob(barMin.x + fillW, (barMin.y + barMax.y) * 0.5f);
    draw->AddCircleFilled(knob, 9.5f, colAlpha(255, 195, 255, 0.95f * alpha), 32);
    draw->AddCircleFilled(knob, 20.0f, colAlpha(235, 130, 255, 0.075f * alpha), 40);
    char percent[16] = {};
    sprintf_s(percent, "%d%%", static_cast<int>(progress * 100.0f));
    draw->AddText(fontOrDefault(g_fonts.medium), 17.0f, barMax + ImVec2(20.0f, -9.0f), colAlpha(255, 220, 255, 0.90f * alpha), percent);

    ImVec2 list(cardMin.x + 204.0f, cardMin.y + 318.0f);
    drawLoadingStep(draw, list + ImVec2(0.0f, 0.0f), "B", "Initializing modules", 0, progress, alpha);
    drawLoadingStep(draw, list + ImVec2(0.0f, 28.0f), "W", "Loading configuration", 1, progress, alpha);
    drawLoadingStep(draw, list + ImVec2(0.0f, 56.0f), "V", "Connecting to Minecraft", 2, progress, alpha);
    drawLoadingStep(draw, list + ImVec2(0.0f, 84.0f), "A", "Finalizing injection", 3, progress, alpha);

    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, cardMin + ImVec2(288.0f, 414.0f),
                  colAlpha(255, 255, 255, 0.38f * alpha), "\"In the silence of the rain, I find my focus.\"");
    float dotsX = cardMin.x + cardSize.x * 0.5f - 44.0f;
    for (int i = 0; i < 5; ++i) {
        float phase = fmodf(elapsed * 1.8f + i * 0.18f, 1.0f);
        float dotAlpha = (phase < 0.28f ? 0.92f : 0.25f) * alpha;
        draw->AddCircleFilled(ImVec2(dotsX + i * 22.0f, cardMax.y - 22.0f), 5.0f,
                              i == static_cast<int>(progress * 5.0f) % 5 ? colAlpha(255, 165, 245, 0.95f * alpha)
                                                                         : colAlpha(255, 255, 255, dotAlpha * 0.45f), 18);
    }
}

bool invisibleHit(const char* id, ImVec2 pos, ImVec2 size) {
    ImGui::SetCursorScreenPos(pos);
    ImGui::InvisibleButton(id, size);
    return ImGui::IsItemClicked();
}

void drawVersionPill(const char* label, int index, bool enabled, ImVec2 pos) {
    ImGui::PushID(index);
    bool selected = g_app.selectedVersion == index;
    ImVec2 size(142.0f, 30.0f);
    bool clicked = invisibleHit("version_pill", pos, size);
    bool hovered = ImGui::IsItemHovered();
    ImDrawList* draw = ImGui::GetWindowDrawList();
    ImU32 fill = selected ? col(199, 149, 237, 255) : col(255, 255, 255, hovered && enabled ? 18 : 10);
    ImU32 border = selected ? col(255, 255, 255, 122) : col(255, 255, 255, hovered && enabled ? 34 : 15);
    draw->AddRectFilled(pos, pos + size, fill, 8.0f);
    draw->AddRect(pos, pos + size, border, 8.0f, 0, 1.0f);
    ImVec2 textSize = ImGui::CalcTextSize(label);
    draw->AddText(fontOrDefault(g_fonts.semiBold), 13.0f,
                  pos + ImVec2((size.x - textSize.x) * 0.5f, (size.y - textSize.y) * 0.5f - 1.0f),
                  enabled ? col(255, 255, 255, selected ? 255 : 190) : col(255, 255, 255, 80), label);
    if (clicked && enabled) {
        g_app.selectedVersion = index;
        setStatus("Minecraft 1.8.9 selected");
    } else if (clicked) {
        setStatus("Only Minecraft 1.8.9 is available now");
    }
    ImGui::PopID();
}

bool versionCard(const char* title, const char* meta, bool selected, bool enabled, ImVec2 size) {
    ImGui::BeginGroup();
    ImVec2 p = ImGui::GetCursorScreenPos();
    ImDrawList* draw = ImGui::GetWindowDrawList();
    ImGui::InvisibleButton(title, size);
    bool clickedCard = ImGui::IsItemClicked() && enabled;
    bool hovered = ImGui::IsItemHovered() && enabled;
    ImU32 bg = col(255, 255, 255, selected ? 15 : 8);
    ImU32 border = selected ? col(199, 149, 237, hovered ? 180 : 122) : col(255, 255, 255, hovered ? 28 : 10);
    draw->AddRectFilled(p, p + size, bg, 8.0f);
    draw->AddRect(p, p + size, border, 8.0f, 0, 1.0f);
    ImVec2 imgMin(p.x + 12.0f, p.y + 12.0f);
    ImVec2 imgMax(p.x + size.x - 12.0f, p.y + 138.0f);
    draw->AddRectFilled(imgMin, imgMax, enabled ? col(34, 58, 70, 235) : col(34, 31, 38, 235), 5.0f);
    draw->AddRect(imgMin, imgMax, col(255, 255, 255, 24), 5.0f, 0, 1.0f);
    for (int i = 0; i < 12; ++i) {
        float x = imgMin.x + 10.0f + i * 20.0f;
        draw->AddLine(ImVec2(x, imgMin.y + 6.0f), ImVec2(x + 36.0f, imgMax.y - 6.0f),
                      enabled ? col(199, 149, 237, 56) : col(120, 90, 100, 36), 2.0f);
    }
    if (!enabled) {
        draw->AddRectFilled(imgMin, imgMax, col(14, 13, 15, 148), 5.0f);
        draw->AddText(fontOrDefault(g_fonts.semiBold), 13.0f, ImVec2(imgMin.x + size.x * 0.25f, imgMin.y + 52.0f), col(255, 255, 255, 72), "Not Released");
    }
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, ImVec2(p.x + 12.0f, p.y + size.y - 54.0f), enabled ? col(255, 255, 255, 230) : col(255, 255, 255, 120), title);
    draw->AddText(fontOrDefault(g_fonts.icon8), 8.0f, ImVec2(p.x + 12.0f, p.y + size.y - 23.0f), enabled ? col(255, 255, 255, 122) : col(255, 255, 255, 62), "E");
    draw->AddText(fontOrDefault(g_fonts.medium), 14.0f, ImVec2(p.x + 28.0f, p.y + size.y - 31.0f), enabled ? col(255, 255, 255, 156) : col(255, 255, 255, 86), meta);
    ImVec2 bmin(p.x + size.x - 46.0f, p.y + size.y - 46.0f);
    ImVec2 bmax(p.x + size.x - 12.0f, p.y + size.y - 12.0f);
    draw->AddRectFilled(bmin, bmax, enabled ? col(199, 149, 237, selected ? 245 : 120) : col(255, 255, 255, 12), 5.0f);
    draw->AddRect(bmin, bmax, enabled ? col(255, 255, 255, 122) : col(255, 255, 255, 16), 5.0f);
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, bmin + ImVec2(11.0f, 10.0f), enabled ? col(255, 255, 255, 235) : col(255, 255, 255, 70), enabled ? "E" : "F");
    ImGui::EndGroup();
    return clickedCard;
}

void startInject(HWND hwnd) {
    if (g_app.uiState == STATE_INJECTING) {
        return;
    }
    if (!resolveDllPath(g_app.dllPath, MAX_PATH)) {
        g_app.uiState = STATE_FAILED;
        setStatus("Injection failed: VapuLiteReobf-x64.dll not found");
        return;
    }
    g_app.uiState = STATE_INJECTING;
    g_app.injectStarted = GetTickCount();
    g_app.loadingAlpha = 0.0f;
    setStatus("Searching Minecraft 1.8.9...");
    HANDLE thread = CreateThread(nullptr, 0, injectThread, hwnd, 0, nullptr);
    if (thread) {
        CloseHandle(thread);
    } else {
        g_app.uiState = STATE_FAILED;
        setStatus("Injection failed: worker thread was not created");
    }
}

void drawMainUi(HWND hwnd) {
    easing(g_app.stageAlpha, 1.0f, 6.0f);
    easing(g_app.contentAlpha, 1.0f, 6.0f);
    easing(g_app.loadingAlpha, g_app.uiState == STATE_INJECTING ? 1.0f : 0.0f, 8.0f);

    ImGuiIO& io = ImGui::GetIO();
    ImGui::SetNextWindowPos(ImVec2(0, 0));
    ImGui::SetNextWindowSize(io.DisplaySize);
    ImGui::Begin("VapuLite Injector", nullptr,
                 ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoMove |
                 ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoSavedSettings);

    ImDrawList* draw = ImGui::GetWindowDrawList();
    ImVec2 s = io.DisplaySize;
    float a = g_app.stageAlpha;
    draw->AddRectFilled(ImVec2(0, 0), s, colAlpha(14, 13, 15, 0.74f), 16.0f);
    draw->AddRectFilled(ImVec2(1.0f, 1.0f), ImVec2(s.x - 1.0f, s.y - 1.0f), colAlpha(255, 255, 255, 0.026f * a), 16.0f);
    draw->AddRect(ImVec2(0.5f, 0.5f), ImVec2(s.x - 0.5f, s.y - 0.5f), colAlpha(255, 255, 255, 0.12f * a), 16.0f);
    draw->AddRect(ImVec2(1.5f, 1.5f), ImVec2(s.x - 1.5f, s.y - 1.5f), colAlpha(199, 149, 237, 0.10f * a), 15.0f);
    draw->AddCircleFilled(ImVec2(205, 148), 112.0f, colAlpha(199, 149, 237, 0.080f * a), 64);
    draw->AddCircleFilled(ImVec2(788, 416), 120.0f, colAlpha(199, 149, 237, 0.065f * a), 64);
    draw->AddBezierCubic(ImVec2(96, 250), ImVec2(168, 78), ImVec2(348, 86), ImVec2(474, 125), colAlpha(80, 135, 165, 0.28f * a), 1.0f, 32);
    draw->AddBezierCubic(ImVec2(474, 125), ImVec2(596, 162), ImVec2(680, 54), ImVec2(842, 66), colAlpha(80, 135, 165, 0.22f * a), 1.0f, 32);

    ImVec2 pad(16.0f, 16.0f);
    ImVec2 topMin = pad;
    ImVec2 topMax(s.x - 68.0f, pad.y + 44.0f);
    draw->AddRectFilled(topMin, topMax, colAlpha(255, 255, 255, 0.02f * a), 8.0f);
    draw->AddRect(topMin, topMax, colAlpha(255, 255, 255, 0.04f * a), 8.0f);
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, ImVec2(topMin.x + 16.0f, topMin.y + 16.0f), col(199, 149, 237, static_cast<int>(255 * a)), "B");
    draw->AddCircleFilled(ImVec2(topMin.x + 50.0f, topMin.y + 22.0f), 1.6f, colAlpha(255, 255, 255, 0.12f * a), 12);
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, ImVec2(topMin.x + 64.0f, topMin.y + 12.0f), col(255, 255, 255, static_cast<int>(235 * a)), "discord.gg/xWVR45bahE");
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, ImVec2(topMax.x - 148.0f, topMin.y + 16.0f), colAlpha(255, 255, 255, 0.24f * a), "V");
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, ImVec2(topMax.x - 124.0f, topMin.y + 12.0f), colAlpha(255, 255, 255, 0.48f * a), "22.08.2025");

    ImVec2 closeMin(s.x - 42.0f, 16.0f);
    if (invisibleHit("close", closeMin, ImVec2(26.0f, 26.0f))) {
        PostMessage(hwnd, WM_CLOSE, 0, 0);
    }
    draw->AddRectFilled(closeMin, closeMin + ImVec2(26.0f, 26.0f), colAlpha(255, 255, 255, ImGui::IsItemHovered() ? 0.10f : 0.04f), 8.0f);
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, closeMin + ImVec2(8.0f, 4.0f), colAlpha(255, 255, 255, 0.62f), "x");

    float ca = g_app.contentAlpha * g_app.stageAlpha;
    drawLogo(draw, ImVec2(s.x * 0.5f, 112.0f), 168.0f, ca);
    draw->AddText(fontOrDefault(g_fonts.title), 28.0f, ImVec2(s.x * 0.5f - 82.0f, 164.0f), col(255, 255, 255, static_cast<int>(230 * ca)), "Choose One");

    float pillsX = s.x * 0.5f - 227.0f;
    drawVersionPill("Minecraft 1.8.9", 0, true, ImVec2(pillsX, 210.0f));
    drawVersionPill("Minecraft 1.20.1", 1, false, ImVec2(pillsX + 154.0f, 210.0f));
    drawVersionPill("Minecraft 26.1", 2, false, ImVec2(pillsX + 308.0f, 210.0f));

    draw->AddText(fontOrDefault(g_fonts.semiBold), 13.0f, ImVec2(16.0f, 260.0f), colAlpha(255, 255, 255, 0.48f * ca), "MINECRAFT");
    ImVec2 cardSize((s.x - 16.0f * 2.0f - 8.0f * 2.0f) / 3.0f, 196.0f);
    ImGui::SetCursorScreenPos(ImVec2(16.0f, 288.0f));
    if (versionCard("Minecraft 1.8.9", "Available", true, true, cardSize)) {
        startInject(hwnd);
    }
    ImGui::SetCursorScreenPos(ImVec2(16.0f + cardSize.x + 8.0f, 288.0f));
    versionCard("Minecraft 1.20.1", "Not Released", false, false, cardSize);
    ImGui::SetCursorScreenPos(ImVec2(16.0f + (cardSize.x + 8.0f) * 2.0f, 288.0f));
    versionCard("Minecraft 26.1", "Not Released", false, false, cardSize);

    ImVec2 railIcon(s.x - 64.0f, s.y * 0.5f - 18.0f);
    draw->AddRectFilled(railIcon, railIcon + ImVec2(30.0f, 30.0f), col(199, 149, 237, static_cast<int>(230 * ca)), 15.0f);
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, railIcon + ImVec2(10.0f, 9.0f), col(255, 255, 255, static_cast<int>(255 * ca)), "A");
    const char* rail[3] = {"Login", "Loader", "Dash"};
    for (int i = 0; i < 3; ++i) {
        ImVec2 r(s.x - 78.0f, s.y * 0.5f + 24.0f + i * 34.0f);
        bool clicked = invisibleHit(rail[i], r, ImVec2(62.0f, 26.0f));
        bool hovered = ImGui::IsItemHovered();
        draw->AddRectFilled(r, r + ImVec2(62.0f, 26.0f), colAlpha(255, 255, 255, hovered ? 0.08f : 0.04f), 8.0f);
        draw->AddRect(r, r + ImVec2(62.0f, 26.0f), colAlpha(255, 255, 255, hovered ? 0.12f : 0.06f), 8.0f);
        ImVec2 ts = ImGui::CalcTextSize(rail[i]);
        draw->AddText(fontOrDefault(g_fonts.semiBold), 13.0f, r + ImVec2((62.0f - ts.x) * 0.5f, 6.0f), colAlpha(255, 255, 255, i == 1 ? 0.90f : 0.62f), rail[i]);
        if (clicked && i == 1) {
            startInject(hwnd);
        }
    }

    ImU32 statusColor = g_app.uiState == STATE_SUCCESS ? col(115, 255, 176, 230)
                       : g_app.uiState == STATE_FAILED ? col(255, 112, 128, 230)
                                                       : colAlpha(255, 255, 255, 0.48f);
    draw->AddText(fontOrDefault(g_fonts.medium), 14.0f, ImVec2(18.0f, s.y - 32.0f), statusColor, g_app.status);

    if (g_app.loadingAlpha > 0.01f) {
        drawInjectionLoadingOverlay(draw, s, g_app.loadingAlpha);
    }

    ImGui::End();
}

void drawBootUi() {
    ImGuiIO& io = ImGui::GetIO();
    ImGui::SetNextWindowPos(ImVec2(0, 0));
    ImGui::SetNextWindowSize(io.DisplaySize);
    ImGui::Begin("Boot", nullptr, ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoMove |
                              ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoSavedSettings);
    ImDrawList* draw = ImGui::GetWindowDrawList();
    ImVec2 s = io.DisplaySize;
    draw->AddRectFilled(ImVec2(0, 0), s, colAlpha(14, 13, 15, 0.74f), 16.0f);
    draw->AddRectFilled(ImVec2(1.0f, 1.0f), ImVec2(s.x - 1.0f, s.y - 1.0f), colAlpha(255, 255, 255, 0.026f), 16.0f);
    draw->AddRect(ImVec2(0.5f, 0.5f), ImVec2(s.x - 0.5f, s.y - 0.5f), col(255, 255, 255, 32), 16.0f);
    draw->AddCircleFilled(ImVec2(s.x * 0.48f, s.y * 0.42f), 76.0f, col(199, 149, 237, 46), 48);
    ImVec2 c(s.x * 0.5f, s.y * 0.5f);
    drawLogo(draw, ImVec2(c.x, c.y - 36.0f), 176.0f, 1.0f);
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, ImVec2(c.x - 56, c.y + 26.0f), col(255, 255, 255, 126),
                  g_app.uiState == STATE_EXPANDING ? "Opening interface" : "Loading injector");
    spinner(draw, ImVec2(c.x, c.y + 82), 22.0f, col(199, 149, 237));
    ImGui::End();
}

void centerWindow(HWND hwnd, int w, int h) {
    int sw = GetSystemMetrics(SM_CXSCREEN);
    int sh = GetSystemMetrics(SM_CYSCREEN);
    SetWindowPos(hwnd, nullptr, (sw - w) / 2, (sh - h) / 2, w, h, SWP_NOZORDER | SWP_NOACTIVATE);
    applyRoundedRegion(hwnd, w, h);
}

LRESULT WINAPI wndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (ImGui_ImplWin32_WndProcHandler(hwnd, msg, wParam, lParam)) {
        return true;
    }
    switch (msg) {
    case WM_SIZE:
        if (wParam != SIZE_MINIMIZED && g_swapChain) {
            cleanupRenderTarget();
            g_swapChain->ResizeBuffers(0, LOWORD(lParam), HIWORD(lParam), DXGI_FORMAT_UNKNOWN, 0);
            createRenderTarget();
            applyRoundedRegion(hwnd, LOWORD(lParam), HIWORD(lParam));
        }
        return 0;
    case WM_NCHITTEST: {
        LRESULT hit = DefWindowProc(hwnd, msg, wParam, lParam);
        if (hit == HTCLIENT) {
            POINT p = {GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)};
            ScreenToClient(hwnd, &p);
            if (p.y < 60 && p.x < kWindowWidth - 64) {
                return HTCAPTION;
            }
        }
        return hit;
    }
    case kInjectDone: {
        InjectResult* result = reinterpret_cast<InjectResult*>(lParam);
        g_app.uiState = result && result->ok ? STATE_SUCCESS : STATE_FAILED;
        std::string msgText = result ? wideToUtf8(result->message) : "Injection failed: unknown error";
        setStatus(msgText.c_str());
        delete result;
        return 0;
    }
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProc(hwnd, msg, wParam, lParam);
}

} // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int) {
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);

    WNDCLASSEXW wc = {sizeof(wc), CS_CLASSDC, wndProc, 0L, 0L, instance, nullptr, nullptr, nullptr, nullptr, kClassName, nullptr};
    RegisterClassExW(&wc);
    HWND hwnd = CreateWindowW(kClassName, L"VapuLite Injector", WS_POPUP, 100, 100, kBootWidth, kBootHeight, nullptr, nullptr, instance, nullptr);
    if (!createDeviceD3D(hwnd)) {
        cleanupDeviceD3D();
        UnregisterClassW(kClassName, instance);
        CoUninitialize();
        return 1;
    }

    std::wstring logoPath = resolveAssetPath(L"vapu_logo.png");
    if (!logoPath.empty()) {
        loadTextureFromFile(logoPath.c_str(), &g_logoTexture, &g_logoSize);
    }
    enableGlassBackground(hwnd);
    centerWindow(hwnd, kBootWidth, kBootHeight);
    ShowWindow(hwnd, SW_SHOWDEFAULT);
    UpdateWindow(hwnd);

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = nullptr;
    io.ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;
    loadUiFonts();
    setDarkStyle();
    ImGui_ImplWin32_Init(hwnd);
    ImGui_ImplDX11_Init(g_device, g_context);

    g_app.started = GetTickCount();
    setStatus("Ready");

    bool done = false;
    while (!done) {
        MSG msg;
        while (PeekMessage(&msg, nullptr, 0U, 0U, PM_REMOVE)) {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
            if (msg.message == WM_QUIT) {
                done = true;
            }
        }
        if (done) {
            break;
        }

        DWORD now = GetTickCount();
        if (g_app.uiState == STATE_BOOT && now - g_app.started > 850) {
            g_app.uiState = STATE_EXPANDING;
            g_app.expandStarted = now;
        }
        if (g_app.uiState == STATE_EXPANDING) {
            float t = (now - g_app.expandStarted) / 460.0f;
            if (t > 1.0f) {
                t = 1.0f;
            }
            t = 1.0f - powf(1.0f - t, 3.0f);
            int w = kBootWidth + static_cast<int>((kWindowWidth - kBootWidth) * t);
            int h = kBootHeight + static_cast<int>((kWindowHeight - kBootHeight) * t);
            centerWindow(hwnd, w, h);
            if (t >= 1.0f) {
                g_app.uiState = STATE_READY;
            }
        }

        ImGui_ImplDX11_NewFrame();
        ImGui_ImplWin32_NewFrame();
        ImGui::NewFrame();

        if (g_app.uiState == STATE_BOOT || g_app.uiState == STATE_EXPANDING) {
            drawBootUi();
        } else {
            drawMainUi(hwnd);
        }

        ImGui::Render();
        const float clear[4] = {0.0f, 0.0f, 0.0f, 0.0f};
        g_context->OMSetRenderTargets(1, &g_renderTarget, nullptr);
        g_context->ClearRenderTargetView(g_renderTarget, clear);
        ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
        g_swapChain->Present(1, 0);
    }

    ImGui_ImplDX11_Shutdown();
    ImGui_ImplWin32_Shutdown();
    ImGui::DestroyContext();
    cleanupDeviceD3D();
    DestroyWindow(hwnd);
    UnregisterClassW(kClassName, instance);
    CoUninitialize();
    return 0;
}
