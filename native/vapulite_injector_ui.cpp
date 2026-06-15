#include <windows.h>
#include <windowsx.h>
#include <tlhelp32.h>
#include <shlwapi.h>
#include <d3d11.h>
#include <d3dcompiler.h>
#include <dwmapi.h>
#include <wincodec.h>
#include <tchar.h>

#include <cmath>
#include <cwchar>
#include <cwctype>
#include <cstring>
#include <string>
#include <vector>

#define IMGUI_DEFINE_MATH_OPERATORS
#include "imgui.h"
#include "backends/imgui_impl_dx11.h"
#include "backends/imgui_impl_win32.h"
#include "ui_fonts.h"

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "d3dcompiler.lib")
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
const int kWindowWidth = 940;
const int kWindowHeight = 600;

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
    float resultAlpha = 0.0f;
    DWORD injectStarted = 0;
    DWORD injectFinished = 0;
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
ID3D11ShaderResourceView* g_versionTextures[3] = {};
ImVec2 g_versionTextureSizes[3] = {};

struct BlurConstants {
    float texelSize[2];
    float direction[2];
};

struct BlurResources {
    int width = 0;
    int height = 0;
    ID3D11Texture2D* textureA = nullptr;
    ID3D11Texture2D* textureB = nullptr;
    ID3D11RenderTargetView* rtvA = nullptr;
    ID3D11RenderTargetView* rtvB = nullptr;
    ID3D11ShaderResourceView* srvA = nullptr;
    ID3D11ShaderResourceView* srvB = nullptr;
    ID3D11VertexShader* vertexShader = nullptr;
    ID3D11PixelShader* blurShader = nullptr;
    ID3D11SamplerState* sampler = nullptr;
    ID3D11Buffer* constants = nullptr;
    bool ready = false;
};

BlurResources g_blur;

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

void currentDateText(char* out, size_t outSize) {
    SYSTEMTIME now = {};
    GetLocalTime(&now);
    sprintf_s(out, outSize, "%02u.%02u.%04u",
              static_cast<unsigned>(now.wDay),
              static_cast<unsigned>(now.wMonth),
              static_cast<unsigned>(now.wYear));
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
        L"\\VapuLiteLoader-x64.dll",
        L"\\build\\libs\\VapuLiteLoader-x64.dll",
        L"\\VapuLiteLoader.dll",
        L"\\build\\libs\\VapuLiteLoader.dll",
        L"\\VapuLiteReobf-x64.dll",
        L"\\build\\libs\\VapuLiteReobf-x64.dll",
        L"\\VapuLiteReobf.dll"
    };
    for (int i = 0; i < 7; ++i) {
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
    int profile = 0;
    DWORD pid = 0;
    wchar_t title[256] = {};
    wchar_t commandLine[1024] = {};
    int score = 1000;
};

enum TargetProfile {
    TARGET_FORGE = 0,
    TARGET_VANILLA = 1,
    TARGET_LUNAR = 2
};

const wchar_t* targetProfileNameW(int profile) {
    switch (profile) {
        case TARGET_VANILLA:
            return L"Vanilla";
        case TARGET_LUNAR:
            return L"Lunar";
        case TARGET_FORGE:
        default:
            return L"Forge";
    }
}

const char* targetProfileNameA(int profile) {
    switch (profile) {
        case TARGET_VANILLA:
            return "Vanilla";
        case TARGET_LUNAR:
            return "Lunar";
        case TARGET_FORGE:
        default:
            return "Forge";
    }
}

int selectedTargetProfile() {
    if (g_app.selectedVersion == TARGET_VANILLA || g_app.selectedVersion == TARGET_LUNAR) {
        return g_app.selectedVersion;
    }
    return TARGET_FORGE;
}

std::wstring lowerText(const wchar_t* text) {
    std::wstring value = text ? text : L"";
    for (size_t i = 0; i < value.size(); ++i) {
        value[i] = static_cast<wchar_t>(towlower(value[i]));
    }
    return value;
}

bool containsText(const std::wstring& text, const wchar_t* needle) {
    return needle && text.find(needle) != std::wstring::npos;
}

bool containsAny(const std::wstring& a, const std::wstring& b, const wchar_t* needle) {
    return containsText(a, needle) || containsText(b, needle);
}

std::wstring readProcessCommandLine(DWORD pid) {
    typedef LONG NTSTATUS;
    typedef NTSTATUS (NTAPI* NtQueryInformationProcessFn)(HANDLE, ULONG, PVOID, ULONG, PULONG);

    struct ProcessBasicInformationLite {
        PVOID Reserved1;
        PVOID PebBaseAddress;
        PVOID Reserved2[2];
        ULONG_PTR UniqueProcessId;
        PVOID Reserved3;
    };

    struct RemoteUnicodeString {
        USHORT Length;
        USHORT MaximumLength;
        PWSTR Buffer;
    };

    HMODULE ntdll = GetModuleHandleW(L"ntdll.dll");
    if (!ntdll) {
        return L"";
    }
    NtQueryInformationProcessFn queryInformationProcess =
        reinterpret_cast<NtQueryInformationProcessFn>(GetProcAddress(ntdll, "NtQueryInformationProcess"));
    if (!queryInformationProcess) {
        return L"";
    }

    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pid);
    if (!process) {
        return L"";
    }

    std::wstring result;
    ProcessBasicInformationLite basicInfo = {};
    if (queryInformationProcess(process, 0, &basicInfo, sizeof(basicInfo), nullptr) >= 0 && basicInfo.PebBaseAddress) {
#if defined(_WIN64)
        const SIZE_T processParametersOffset = 0x20;
        const SIZE_T commandLineOffset = 0x70;
#else
        const SIZE_T processParametersOffset = 0x10;
        const SIZE_T commandLineOffset = 0x40;
#endif
        BYTE* processParameters = nullptr;
        if (ReadProcessMemory(process,
                              reinterpret_cast<BYTE*>(basicInfo.PebBaseAddress) + processParametersOffset,
                              &processParameters,
                              sizeof(processParameters),
                              nullptr)
                && processParameters) {
            RemoteUnicodeString commandLine = {};
            if (ReadProcessMemory(process,
                                  processParameters + commandLineOffset,
                                  &commandLine,
                                  sizeof(commandLine),
                                  nullptr)
                    && commandLine.Buffer
                    && commandLine.Length > 0
                    && commandLine.Length < 65534) {
                result.assign(commandLine.Length / sizeof(wchar_t), L'\0');
                if (!ReadProcessMemory(process, commandLine.Buffer, &result[0], commandLine.Length, nullptr)) {
                    result.clear();
                }
            }
        }
    }

    CloseHandle(process);
    return result;
}

bool isIgnoredLauncher(const std::wstring& title, const std::wstring& commandLine) {
    return containsText(title, L"home - lunar client")
        || containsText(title, L"hello minecraft! launcher")
        || containsText(title, L"badlion chat")
        || containsText(commandLine, L"org.gradle.launcher.daemon")
        || containsText(commandLine, L"-jar \"hmcl")
        || containsText(commandLine, L"-jar hmcl");
}

int minecraftTargetScore(int profile, const wchar_t* titleRaw, const wchar_t* commandLineRaw) {
    std::wstring title = lowerText(titleRaw);
    std::wstring commandLine = lowerText(commandLineRaw);
    if (isIgnoredLauncher(title, commandLine)) {
        return 1000;
    }

    bool titleMinecraft = containsText(title, L"minecraft");
    bool titleVersion = containsText(title, L"1.8.9");
    bool commandVersion = containsText(commandLine, L"--version 1.8.9")
        || containsText(commandLine, L"versions\\1.8.9")
        || containsText(commandLine, L"versions/1.8.9")
        || containsText(commandLine, L" 1.8.9");
    bool version189 = titleVersion || commandVersion;
    bool lunar = containsAny(title, commandLine, L"lunar")
        || containsText(commandLine, L".lunarclient")
        || containsText(commandLine, L"moonsworth")
        || containsText(commandLine, L"com.moonsworth.lunar.genesis")
        || containsText(commandLine, L"ichor.");
    bool badlion = containsAny(title, commandLine, L"badlion");
    bool forge = containsText(commandLine, L"net.minecraft.launchwrapper.launch")
        || containsText(commandLine, L"net.minecraftforge")
        || containsText(commandLine, L"--tweakclass cpw.mods.fml")
        || containsText(commandLine, L"--tweakclass net.minecraftforge")
        || containsText(commandLine, L"fmltweaker");
    bool vanillaMain = containsText(commandLine, L"net.minecraft.client.main.main");

    if (profile == TARGET_LUNAR) {
        if (!lunar || badlion) {
            return 1000;
        }
        if (titleMinecraft && version189) {
            return 0;
        }
        if (version189) {
            return 1;
        }
        return 2;
    }

    if (lunar || badlion) {
        return 1000;
    }

    if (profile == TARGET_VANILLA) {
        if (forge) {
            return 1000;
        }
        if (vanillaMain && version189) {
            return 0;
        }
        if (vanillaMain) {
            return 1;
        }
        if (titleMinecraft && version189 && commandLine.empty()) {
            return 3;
        }
        if (titleMinecraft && commandLine.empty()) {
            return 5;
        }
        return 1000;
    }

    if (forge && version189) {
        return 0;
    }
    if (forge) {
        return 1;
    }
    if (titleMinecraft && version189 && !vanillaMain) {
        return 4;
    }
    if (titleMinecraft && commandLine.empty()) {
        return 6;
    }
    return 1000;
}

void considerTarget(FindWindowContext* ctx, DWORD pid, const wchar_t* title) {
    std::wstring commandLine = readProcessCommandLine(pid);
    int score = minecraftTargetScore(ctx->profile, title, commandLine.c_str());
    if (score >= 1000) {
        return;
    }
    bool currentHasTitle = ctx->title[0] != 0;
    bool nextHasTitle = title && title[0] != 0;
    if (ctx->pid != 0 && (score > ctx->score || (score == ctx->score && currentHasTitle && !nextHasTitle))) {
        return;
    }
    ctx->pid = pid;
    ctx->score = score;
    if (nextHasTitle) {
        wcsncpy_s(ctx->title, title, _TRUNCATE);
    } else {
        swprintf_s(ctx->title, L"%s Java PID %lu", targetProfileNameW(ctx->profile), pid);
    }
    wcsncpy_s(ctx->commandLine, commandLine.c_str(), _TRUNCATE);
}

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
    FindWindowContext* ctx = reinterpret_cast<FindWindowContext*>(param);
    considerTarget(ctx, pid, title);
    if (ctx->score == 0) {
        return FALSE;
    }
    return TRUE;
}

void scanJavaProcesses(FindWindowContext* ctx) {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) {
        return;
    }
    PROCESSENTRY32W entry = {};
    entry.dwSize = sizeof(entry);
    if (Process32FirstW(snap, &entry)) {
        do {
            if (_wcsicmp(entry.szExeFile, L"java.exe") == 0 || _wcsicmp(entry.szExeFile, L"javaw.exe") == 0) {
                considerTarget(ctx, entry.th32ProcessID, L"");
            }
        } while (Process32NextW(snap, &entry));
    }
    CloseHandle(snap);
}

DWORD findMinecraftTarget(int profile, wchar_t* title, DWORD titleChars) {
    FindWindowContext ctx;
    ctx.profile = profile;
    EnumWindows(enumWindowsProc, reinterpret_cast<LPARAM>(&ctx));
    if (ctx.score > 0) {
        scanJavaProcesses(&ctx);
    }
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

std::wstring stagedDllCopy(const wchar_t* dllPath) {
    wchar_t tempDir[MAX_PATH] = {};
    if (!GetTempPathW(MAX_PATH, tempDir)) {
        return L"";
    }
    wchar_t staged[MAX_PATH] = {};
    swprintf_s(staged,
               L"%sVapuLiteLoader-%lu-%llu.dll",
               tempDir,
               GetCurrentProcessId(),
               GetTickCount64());
    if (!CopyFileW(dllPath, staged, FALSE)) {
        return L"";
    }
    return staged;
}

bool injectDll(DWORD pid, const wchar_t* dllPath, std::wstring& error) {
    wchar_t fullPath[MAX_PATH] = {};
    if (!GetFullPathNameW(dllPath, MAX_PATH, fullPath, nullptr)) {
        error = L"DLL path resolve failed";
        return false;
    }
    std::wstring loadPath = fullPath;
    if (moduleAlreadyLoaded(pid, fullPath)) {
        loadPath = stagedDllCopy(fullPath);
        if (loadPath.empty()) {
            error = L"DLL is already loaded and staging a fresh copy failed";
            return false;
        }
    }

    HANDLE process = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION | PROCESS_VM_OPERATION
                                 | PROCESS_VM_WRITE | PROCESS_VM_READ, FALSE, pid);
    if (!process) {
        error = L"OpenProcess failed, try running as administrator";
        return false;
    }

    SIZE_T bytes = (loadPath.length() + 1) * sizeof(wchar_t);
    LPVOID remotePath = VirtualAllocEx(process, nullptr, bytes, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remotePath) {
        CloseHandle(process);
        error = L"VirtualAllocEx failed";
        return false;
    }

    bool ok = false;
    if (!WriteProcessMemory(process, remotePath, loadPath.c_str(), bytes, nullptr)) {
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
    int profile = selectedTargetProfile();
    Sleep(950);

    InjectResult* result = new InjectResult();
    result->ok = false;
    result->message[0] = 0;

    wchar_t gameTitle[256] = {};
    DWORD pid = findMinecraftTarget(profile, gameTitle, 256);
    if (pid == 0) {
        swprintf_s(result->message,
                   L"Injection failed: %s target was not found",
                   targetProfileNameW(profile));
        PostMessageW(hwnd, kInjectDone, 0, reinterpret_cast<LPARAM>(result));
        return 0;
    }

    std::wstring error;
    bool ok = injectDll(pid, g_app.dllPath, error);
    result->ok = ok;
    if (ok) {
        swprintf_s(result->message, L"Injected into %s (%s)", gameTitle[0] ? gameTitle : L"Minecraft", targetProfileNameW(profile));
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

template <typename T>
void releaseDx(T*& object) {
    if (object) {
        object->Release();
        object = nullptr;
    }
}

void releaseBlurTargets() {
    releaseDx(g_blur.textureA);
    releaseDx(g_blur.textureB);
    releaseDx(g_blur.rtvA);
    releaseDx(g_blur.rtvB);
    releaseDx(g_blur.srvA);
    releaseDx(g_blur.srvB);
    g_blur.ready = false;
    g_blur.width = 0;
    g_blur.height = 0;
}

void cleanupBlurResources() {
    releaseBlurTargets();
    releaseDx(g_blur.vertexShader);
    releaseDx(g_blur.blurShader);
    releaseDx(g_blur.sampler);
    releaseDx(g_blur.constants);
}

bool createBlurShaders() {
    if (g_blur.vertexShader && g_blur.blurShader) {
        return true;
    }
    const char* shader = R"(
        cbuffer BlurConstants : register(b0) {
            float2 texelSize;
            float2 direction;
        };
        Texture2D sourceTexture : register(t0);
        SamplerState sourceSampler : register(s0);

        struct VSOut {
            float4 pos : SV_POSITION;
            float2 uv : TEXCOORD0;
        };

        VSOut VSMain(uint id : SV_VertexID) {
            VSOut output;
            float2 pos[3] = { float2(-1.0, -1.0), float2(-1.0, 3.0), float2(3.0, -1.0) };
            float2 uv[3] = { float2(0.0, 1.0), float2(0.0, -1.0), float2(2.0, 1.0) };
            output.pos = float4(pos[id], 0.0, 1.0);
            output.uv = uv[id];
            return output;
        }

        float4 PSMain(VSOut input) : SV_TARGET {
            float2 step = texelSize * direction;
            float4 color = sourceTexture.Sample(sourceSampler, input.uv) * 0.1964825502;
            color += sourceTexture.Sample(sourceSampler, input.uv + step * 1.4117647059) * 0.2969069647;
            color += sourceTexture.Sample(sourceSampler, input.uv - step * 1.4117647059) * 0.2969069647;
            color += sourceTexture.Sample(sourceSampler, input.uv + step * 3.2941176471) * 0.0944703979;
            color += sourceTexture.Sample(sourceSampler, input.uv - step * 3.2941176471) * 0.0944703979;
            color += sourceTexture.Sample(sourceSampler, input.uv + step * 5.1764705882) * 0.0103813624;
            color += sourceTexture.Sample(sourceSampler, input.uv - step * 5.1764705882) * 0.0103813624;
            return color;
        }
    )";

    ID3DBlob* vsBlob = nullptr;
    ID3DBlob* psBlob = nullptr;
    ID3DBlob* errors = nullptr;
    HRESULT hr = D3DCompile(shader, strlen(shader), nullptr, nullptr, nullptr, "VSMain", "vs_4_0", 0, 0, &vsBlob, &errors);
    if (FAILED(hr)) {
        releaseDx(errors);
        return false;
    }
    hr = D3DCompile(shader, strlen(shader), nullptr, nullptr, nullptr, "PSMain", "ps_4_0", 0, 0, &psBlob, &errors);
    if (FAILED(hr)) {
        releaseDx(vsBlob);
        releaseDx(errors);
        return false;
    }
    hr = g_device->CreateVertexShader(vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), nullptr, &g_blur.vertexShader);
    if (SUCCEEDED(hr)) {
        hr = g_device->CreatePixelShader(psBlob->GetBufferPointer(), psBlob->GetBufferSize(), nullptr, &g_blur.blurShader);
    }
    releaseDx(vsBlob);
    releaseDx(psBlob);
    releaseDx(errors);
    if (FAILED(hr)) {
        return false;
    }

    D3D11_SAMPLER_DESC sampler = {};
    sampler.Filter = D3D11_FILTER_MIN_MAG_MIP_LINEAR;
    sampler.AddressU = D3D11_TEXTURE_ADDRESS_CLAMP;
    sampler.AddressV = D3D11_TEXTURE_ADDRESS_CLAMP;
    sampler.AddressW = D3D11_TEXTURE_ADDRESS_CLAMP;
    sampler.ComparisonFunc = D3D11_COMPARISON_ALWAYS;
    sampler.MaxLOD = D3D11_FLOAT32_MAX;
    if (FAILED(g_device->CreateSamplerState(&sampler, &g_blur.sampler))) {
        return false;
    }

    D3D11_BUFFER_DESC cb = {};
    cb.ByteWidth = sizeof(BlurConstants);
    cb.Usage = D3D11_USAGE_DYNAMIC;
    cb.BindFlags = D3D11_BIND_CONSTANT_BUFFER;
    cb.CPUAccessFlags = D3D11_CPU_ACCESS_WRITE;
    return SUCCEEDED(g_device->CreateBuffer(&cb, nullptr, &g_blur.constants));
}

bool ensureBlurTargets(int width, int height) {
    if (width <= 0 || height <= 0 || !createBlurShaders()) {
        return false;
    }
    if (g_blur.ready && g_blur.width == width && g_blur.height == height) {
        return true;
    }
    releaseBlurTargets();
    g_blur.width = width;
    g_blur.height = height;

    D3D11_TEXTURE2D_DESC desc = {};
    desc.Width = width;
    desc.Height = height;
    desc.MipLevels = 1;
    desc.ArraySize = 1;
    desc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    desc.SampleDesc.Count = 1;
    desc.Usage = D3D11_USAGE_DEFAULT;
    desc.BindFlags = D3D11_BIND_RENDER_TARGET | D3D11_BIND_SHADER_RESOURCE;
    if (FAILED(g_device->CreateTexture2D(&desc, nullptr, &g_blur.textureA)) ||
        FAILED(g_device->CreateTexture2D(&desc, nullptr, &g_blur.textureB)) ||
        FAILED(g_device->CreateRenderTargetView(g_blur.textureA, nullptr, &g_blur.rtvA)) ||
        FAILED(g_device->CreateRenderTargetView(g_blur.textureB, nullptr, &g_blur.rtvB)) ||
        FAILED(g_device->CreateShaderResourceView(g_blur.textureA, nullptr, &g_blur.srvA)) ||
        FAILED(g_device->CreateShaderResourceView(g_blur.textureB, nullptr, &g_blur.srvB))) {
        releaseBlurTargets();
        return false;
    }
    g_blur.ready = true;
    return true;
}

void blurPass(ID3D11ShaderResourceView* source, ID3D11RenderTargetView* target, float dx, float dy) {
    BlurConstants constants = {};
    constants.texelSize[0] = 1.0f / static_cast<float>(g_blur.width);
    constants.texelSize[1] = 1.0f / static_cast<float>(g_blur.height);
    constants.direction[0] = dx;
    constants.direction[1] = dy;
    D3D11_MAPPED_SUBRESOURCE mapped = {};
    if (SUCCEEDED(g_context->Map(g_blur.constants, 0, D3D11_MAP_WRITE_DISCARD, 0, &mapped))) {
        memcpy(mapped.pData, &constants, sizeof(constants));
        g_context->Unmap(g_blur.constants, 0);
    }

    D3D11_VIEWPORT viewport = {};
    viewport.Width = static_cast<float>(g_blur.width);
    viewport.Height = static_cast<float>(g_blur.height);
    viewport.MinDepth = 0.0f;
    viewport.MaxDepth = 1.0f;
    D3D11_RECT scissor = {0, 0, static_cast<LONG>(g_blur.width), static_cast<LONG>(g_blur.height)};
    float blendFactor[4] = {0, 0, 0, 0};
    g_context->RSSetViewports(1, &viewport);
    g_context->RSSetScissorRects(1, &scissor);
    g_context->OMSetRenderTargets(1, &target, nullptr);
    g_context->OMSetBlendState(nullptr, blendFactor, 0xffffffff);
    g_context->OMSetDepthStencilState(nullptr, 0);
    g_context->IASetInputLayout(nullptr);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    g_context->VSSetShader(g_blur.vertexShader, nullptr, 0);
    g_context->PSSetShader(g_blur.blurShader, nullptr, 0);
    g_context->PSSetConstantBuffers(0, 1, &g_blur.constants);
    g_context->PSSetSamplers(0, 1, &g_blur.sampler);
    g_context->PSSetShaderResources(0, 1, &source);
    g_context->Draw(3, 0);
    ID3D11ShaderResourceView* nullSrv = nullptr;
    g_context->PSSetShaderResources(0, 1, &nullSrv);
}

void updateGaussianBlurTexture() {
    if (!g_swapChain) {
        return;
    }
    DXGI_SWAP_CHAIN_DESC sd = {};
    g_swapChain->GetDesc(&sd);
    if (!ensureBlurTargets(sd.BufferDesc.Width, sd.BufferDesc.Height)) {
        return;
    }
    ID3D11Texture2D* backBuffer = nullptr;
    if (FAILED(g_swapChain->GetBuffer(0, IID_PPV_ARGS(&backBuffer)))) {
        return;
    }
    g_context->CopyResource(g_blur.textureA, backBuffer);
    backBuffer->Release();

    blurPass(g_blur.srvA, g_blur.rtvB, 1.85f, 0.0f);
    blurPass(g_blur.srvB, g_blur.rtvA, 0.0f, 1.85f);
}

void cleanupDeviceD3D() {
    cleanupBlurResources();
    cleanupRenderTarget();
    if (g_logoTexture) {
        g_logoTexture->Release();
        g_logoTexture = nullptr;
    }
    for (int i = 0; i < 3; ++i) {
        if (g_versionTextures[i]) {
            g_versionTextures[i]->Release();
            g_versionTextures[i] = nullptr;
        }
        g_versionTextureSizes[i] = ImVec2(0.0f, 0.0f);
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

void drawSakuraPetal(ImDrawList* draw, ImVec2 center, float length, float width, float rotation, ImU32 fill, ImU32 edge, ImU32 vein) {
    ImVec2 points[16];
    int count = 0;
    float cs = cosf(rotation);
    float sn = sinf(rotation);
    auto transform = [&](float x, float y) {
        return ImVec2(center.x + x * cs - y * sn, center.y + x * sn + y * cs);
    };

    for (int i = 0; i < 8; ++i) {
        float u = static_cast<float>(i) / 7.0f;
        float y = -length * 0.54f + u * length;
        float taper = sinf(u * kPi);
        float bulge = 0.72f + 0.34f * (1.0f - u);
        float x = taper * width * bulge;
        points[count++] = transform(x, y);
    }
    for (int i = 7; i >= 0; --i) {
        float u = static_cast<float>(i) / 7.0f;
        float y = -length * 0.54f + u * length;
        float taper = sinf(u * kPi);
        float bulge = 0.72f + 0.34f * (1.0f - u);
        float x = -taper * width * bulge;
        points[count++] = transform(x, y);
    }

    draw->AddConvexPolyFilled(points, count, fill);
    draw->AddPolyline(points, count, edge, ImDrawFlags_Closed, 0.75f);
    draw->AddLine(transform(0.0f, -length * 0.40f), transform(0.0f, length * 0.34f), vein, 0.65f);
    draw->AddCircleFilled(transform(0.0f, -length * 0.38f), width * 0.20f, colAlpha(255, 245, 255, 0.16f), 10);
}

void drawSakuraPetals(ImDrawList* draw, ImVec2 size, float alpha) {
    DWORD tick = GetTickCount();
    for (int i = 0; i < 42; ++i) {
        float seed = static_cast<float>(i);
        float speed = 0.026f + (i % 7) * 0.0065f;
        float wave = tick * 0.0010f + seed * 1.37f;
        float drift = sinf(wave) * (24.0f + (i % 5) * 7.0f);
        float sway = sinf(tick * 0.0018f + seed * 2.11f) * 7.0f;
        float x = fmodf(34.0f + seed * 83.0f + drift, size.x + 120.0f) - 60.0f;
        float y = fmodf(-110.0f + seed * 61.0f + tick * speed, size.y + 190.0f) - 92.0f;
        bool foreground = (i % 9) == 0;
        float length = foreground ? 16.0f + (i % 3) * 3.0f : 8.5f + (i % 5) * 1.2f;
        float width = foreground ? length * 0.34f : length * 0.30f;
        float rot = 0.45f + sinf(tick * 0.0015f + seed) * 0.80f + (i % 4) * 0.34f;
        float petalAlpha = (foreground ? 0.34f : 0.18f + (i % 5) * 0.025f) * alpha;
        ImVec2 c(x + sway, y);
        ImU32 fill = colAlpha(255, 162 + (i % 3) * 12, 224 + (i % 2) * 16, petalAlpha);
        ImU32 edge = colAlpha(255, 232, 250, (foreground ? 0.28f : 0.18f) * alpha);
        ImU32 vein = colAlpha(255, 245, 255, (foreground ? 0.24f : 0.13f) * alpha);
        drawSakuraPetal(draw, c, length, width, rot, fill, edge, vein);
    }
}

int currentFailureStep() {
    if (g_app.uiState != STATE_FAILED) {
        return -1;
    }
    if (strstr(g_app.status, "loader DLL not found") != nullptr ||
        strstr(g_app.status, "worker thread") != nullptr) {
        return 1;
    }
    if (strstr(g_app.status, "target was not found") != nullptr) {
        return 2;
    }
    return 3;
}

void drawLoadingStep(ImDrawList* draw, ImVec2 pos, const char* icon, const char* text, int index, float progress, int failureStep, float alpha) {
    float stepPoint = 0.18f + index * 0.22f;
    bool failed = failureStep == index;
    bool blockedByFailure = failureStep >= 0 && index > failureStep;
    bool done = !failed && !blockedByFailure && progress >= stepPoint + 0.12f;
    bool active = !failed && !blockedByFailure && progress >= stepPoint - 0.08f;
    ImU32 textCol = done ? colAlpha(255, 255, 255, 0.76f * alpha)
                         : failed ? colAlpha(255, 148, 166, 0.92f * alpha)
                                  : active ? colAlpha(245, 205, 255, 0.90f * alpha)
                                           : colAlpha(255, 255, 255, 0.34f * alpha);
    ImU32 iconCol = done ? colAlpha(210, 255, 230, 0.90f * alpha)
                         : failed ? colAlpha(255, 132, 154, 0.95f * alpha)
                                  : active ? colAlpha(230, 170, 255, 0.95f * alpha)
                                           : colAlpha(255, 255, 255, 0.38f * alpha);
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, pos, iconCol, icon);
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, pos + ImVec2(38.0f, -2.0f), textCol, text);

    ImVec2 status(pos.x + 510.0f, pos.y - 1.0f);
    if (failed) {
        ImVec2 c = status + ImVec2(8.0f, 8.0f);
        draw->AddCircleFilled(c, 10.0f, colAlpha(255, 92, 120, 0.085f * alpha), 24);
        draw->AddLine(c + ImVec2(-5.0f, -5.0f), c + ImVec2(5.0f, 5.0f),
                      colAlpha(255, 132, 154, 0.94f * alpha), 1.8f);
        draw->AddLine(c + ImVec2(5.0f, -5.0f), c + ImVec2(-5.0f, 5.0f),
                      colAlpha(255, 132, 154, 0.94f * alpha), 1.8f);
    } else if (done) {
        ImVec2 c = status + ImVec2(8.0f, 8.0f);
        draw->AddCircleFilled(c, 10.0f, colAlpha(132, 255, 205, 0.075f * alpha), 24);
        draw->AddLine(c + ImVec2(-4.5f, -0.5f), c + ImVec2(-1.0f, 3.5f),
                      colAlpha(210, 255, 230, 0.92f * alpha), 1.8f);
        draw->AddLine(c + ImVec2(-1.0f, 3.5f), c + ImVec2(5.5f, -5.0f),
                      colAlpha(210, 255, 230, 0.92f * alpha), 1.8f);
    } else {
        float pulse = active ? 0.45f + 0.55f * sinf(GetTickCount() * 0.010f) : 0.0f;
        draw->AddCircle(status + ImVec2(8.0f, 8.0f), 8.0f, colAlpha(220, 150, 255, (active ? 0.82f : 0.18f) * alpha), 32, 2.0f);
        if (active) {
            draw->AddCircleFilled(status + ImVec2(8.0f, 8.0f), 4.0f + pulse * 2.0f, colAlpha(230, 170, 255, 0.20f * alpha), 32);
        }
    }
}

void drawLiquidGlassEdges(ImDrawList* draw, ImVec2 min, ImVec2 max, float rounding, float alpha) {
    ImVec2 size = max - min;
    ImVec2 center = (min + max) * 0.5f;
    float pulse = 0.5f + 0.5f * sinf(GetTickCount() * 0.0022f);

    draw->AddRect(min + ImVec2(0.5f, 0.5f), max - ImVec2(0.5f, 0.5f),
                  colAlpha(255, 255, 255, 0.15f * alpha), rounding, 0, 1.0f);

    ImU32 cornerGlow = colAlpha(255, 202, 255, (0.10f + pulse * 0.04f) * alpha);
    draw->AddCircleFilled(min + ImVec2(rounding, rounding), 38.0f, cornerGlow, 48);
    draw->AddCircleFilled(ImVec2(max.x - rounding, min.y + rounding), 38.0f, cornerGlow, 48);
    draw->AddCircleFilled(ImVec2(min.x + rounding, max.y - rounding), 32.0f, colAlpha(170, 120, 255, 0.050f * alpha), 48);
    draw->AddCircleFilled(max - ImVec2(rounding, rounding), 32.0f, colAlpha(170, 120, 255, 0.055f * alpha), 48);

    draw->AddBezierCubic(min + ImVec2(size.x * 0.09f, 5.0f),
                         min + ImVec2(size.x * 0.22f, -2.0f),
                         min + ImVec2(size.x * 0.38f, 9.0f),
                         min + ImVec2(size.x * 0.50f, 4.0f),
                         colAlpha(255, 238, 255, 0.30f * alpha), 1.15f, 34);
    draw->AddBezierCubic(min + ImVec2(size.x * 0.58f, 5.0f),
                         min + ImVec2(size.x * 0.67f, 10.0f),
                         min + ImVec2(size.x * 0.80f, -1.0f),
                         min + ImVec2(size.x * 0.91f, 7.0f),
                         colAlpha(255, 210, 255, 0.18f * alpha), 0.95f, 28);
    draw->AddBezierCubic(min + ImVec2(8.0f, 35.0f),
                         min + ImVec2(0.0f, 58.0f),
                         min + ImVec2(18.0f, 86.0f),
                         min + ImVec2(9.0f, 116.0f),
                         colAlpha(255, 230, 255, 0.15f * alpha), 0.85f, 22);
    draw->AddBezierCubic(ImVec2(max.x - 10.0f, min.y + 42.0f),
                         ImVec2(max.x - 2.0f, min.y + 68.0f),
                         ImVec2(max.x - 18.0f, min.y + 96.0f),
                         ImVec2(max.x - 9.0f, min.y + 126.0f),
                         colAlpha(210, 178, 255, 0.12f * alpha), 0.85f, 22);

    draw->AddCircleFilled(center + ImVec2(0.0f, -size.y * 0.20f), size.x * 0.14f,
                          colAlpha(255, 180, 255, 0.030f * alpha), 64);
}

void resetToReady();

bool drawInjectionLoadingOverlay(ImDrawList* draw, ImVec2 size, float alpha) {
    DWORD now = GetTickCount();
    float elapsed = g_app.injectStarted ? (now - g_app.injectStarted) / 1000.0f : 0.0f;
    bool hasResult = g_app.uiState == STATE_SUCCESS || g_app.uiState == STATE_FAILED;
    float progress = 0.10f + elapsed * 0.21f + sinf(elapsed * 2.8f) * 0.015f;
    if (hasResult) {
        float finishElapsed = g_app.injectFinished ? (now - g_app.injectFinished) / 1000.0f : 0.0f;
        progress = 0.96f + finishElapsed * 0.16f;
    } else if (progress > 0.92f) {
        progress = 0.92f + sinf(elapsed * 3.4f) * 0.018f;
    }
    if (progress < 0.0f) {
        progress = 0.0f;
    } else if (progress > 1.0f) {
        progress = 1.0f;
    }
    float resultFade = g_app.resultAlpha;
    if (resultFade < 0.0f) {
        resultFade = 0.0f;
    } else if (resultFade > 1.0f) {
        resultFade = 1.0f;
    }
    float loadingPageAlpha = alpha * (1.0f - resultFade);

    draw->AddRectFilled(ImVec2(0, 0), size, colAlpha(11, 10, 18, 0.18f * alpha), 16.0f);
    drawSakuraPetals(draw, size, alpha);
    draw->AddCircleFilled(ImVec2(size.x * 0.50f, size.y * 0.30f), 260.0f, colAlpha(95, 110, 195, 0.085f * alpha), 96);
    draw->AddCircleFilled(ImVec2(size.x * 0.50f, size.y * 0.70f), 320.0f, colAlpha(185, 95, 235, 0.050f * alpha), 96);

    ImVec2 cardSize(820.0f, 450.0f);
    ImVec2 cardMin((size.x - cardSize.x) * 0.5f, (size.y - cardSize.y) * 0.5f + 6.0f);
    ImVec2 cardMax = cardMin + cardSize;
    draw->AddRectFilled(cardMin - ImVec2(18.0f, 18.0f), cardMax + ImVec2(18.0f, 18.0f),
                        colAlpha(190, 115, 255, 0.045f * alpha), 28.0f);
    if (g_blur.ready && g_blur.srvA) {
        ImVec2 uvMin(cardMin.x / size.x, cardMin.y / size.y);
        ImVec2 uvMax(cardMax.x / size.x, cardMax.y / size.y);
        draw->AddImageRounded(g_blur.srvA, cardMin, cardMax, uvMin, uvMax,
                              colAlpha(255, 255, 255, 0.48f * alpha), 22.0f);
    }
    draw->AddRectFilled(cardMin, cardMax, colAlpha(64, 66, 112, 0.055f * alpha), 22.0f);
    draw->AddRectFilled(cardMin + ImVec2(1, 1), cardMax - ImVec2(1, 1),
                        colAlpha(255, 255, 255, 0.022f * alpha), 21.0f);
    draw->AddRect(cardMin, cardMax, colAlpha(235, 232, 255, 0.10f * alpha), 22.0f, 0, 1.0f);
    draw->AddBezierCubic(cardMin + ImVec2(38.0f, 11.0f),
                         cardMin + ImVec2(180.0f, 2.0f),
                         cardMin + ImVec2(360.0f, 18.0f),
                         cardMin + ImVec2(520.0f, 8.0f),
                         colAlpha(255, 255, 255, 0.055f * alpha), 0.85f, 40);
    draw->AddBezierCubic(ImVec2(cardMax.x - 260.0f, cardMin.y + 8.0f),
                         ImVec2(cardMax.x - 180.0f, 18.0f + cardMin.y),
                         ImVec2(cardMax.x - 90.0f, 0.0f + cardMin.y),
                         ImVec2(cardMax.x - 38.0f, 10.0f + cardMin.y),
                         colAlpha(255, 220, 255, 0.050f * alpha), 0.85f, 32);
    drawLiquidGlassEdges(draw, cardMin, cardMax, 22.0f, alpha);

    draw->AddCircleFilled(cardMin + ImVec2(34.0f, 46.0f), 4.0f, colAlpha(235, 165, 255, 0.95f * loadingPageAlpha), 16);
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, cardMin + ImVec2(50.0f, 36.0f), colAlpha(255, 255, 255, 0.82f * loadingPageAlpha), "VapuLite");
    draw->AddText(fontOrDefault(g_fonts.medium), 14.0f, cardMin + ImVec2(50.0f, 68.0f), colAlpha(255, 255, 255, 0.36f * loadingPageAlpha), "Liquid Glass");
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, cardMax - ImVec2(74.0f, 394.0f), colAlpha(255, 255, 255, 0.52f * loadingPageAlpha), "v2.5.0");
    draw->AddText(fontOrDefault(g_fonts.medium), 14.0f, cardMax - ImVec2(146.0f, 362.0f), colAlpha(255, 255, 255, 0.42f * loadingPageAlpha), "Sakura Theme");
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, cardMax - ImVec2(48.0f, 360.0f), colAlpha(255, 185, 242, 0.90f * loadingPageAlpha), "*");

    ImVec2 logoCenter(cardMin.x + cardSize.x * 0.5f, cardMin.y + 120.0f);
    draw->AddCircleFilled(logoCenter, 72.0f, colAlpha(205, 115, 255, 0.11f * loadingPageAlpha), 72);
    draw->AddCircleFilled(logoCenter, 42.0f, colAlpha(244, 178, 255, 0.12f * loadingPageAlpha), 72);
    drawLogo(draw, logoCenter, 96.0f, loadingPageAlpha);

    draw->AddText(fontOrDefault(g_fonts.title), 38.0f, cardMin + ImVec2(330.0f, 164.0f), colAlpha(255, 220, 255, 0.94f * loadingPageAlpha), "VapuLite");
    draw->AddText(fontOrDefault(g_fonts.medium), 20.0f, cardMin + ImVec2(306.0f, 220.0f), colAlpha(255, 255, 255, 0.45f * loadingPageAlpha), "Loading your experience...");

    ImVec2 barMin(cardMin.x + 194.0f, cardMin.y + 272.0f);
    ImVec2 barMax(cardMin.x + 660.0f, cardMin.y + 280.0f);
    float barW = barMax.x - barMin.x;
    float fillW = barW * progress;
    draw->AddRectFilled(barMin, barMax, colAlpha(255, 255, 255, 0.085f * loadingPageAlpha), 5.0f);
    draw->AddRectFilled(barMin - ImVec2(5.0f, 5.0f), ImVec2(barMin.x + fillW + 5.0f, barMax.y + 5.0f),
                        colAlpha(232, 116, 255, 0.075f * loadingPageAlpha), 9.0f);
    draw->AddRectFilled(barMin, ImVec2(barMin.x + fillW, barMax.y), colAlpha(243, 152, 255, 0.92f * loadingPageAlpha), 5.0f);
    draw->AddRectFilledMultiColor(barMin, ImVec2(barMin.x + fillW, barMax.y),
                                  colAlpha(255, 185, 255, 0.90f * loadingPageAlpha),
                                  colAlpha(200, 120, 255, 0.90f * loadingPageAlpha),
                                  colAlpha(200, 120, 255, 0.90f * loadingPageAlpha),
                                  colAlpha(255, 185, 255, 0.90f * loadingPageAlpha));
    ImVec2 knob(barMin.x + fillW, (barMin.y + barMax.y) * 0.5f);
    draw->AddCircleFilled(knob, 9.5f, colAlpha(255, 195, 255, 0.95f * loadingPageAlpha), 32);
    draw->AddCircleFilled(knob, 20.0f, colAlpha(235, 130, 255, 0.075f * loadingPageAlpha), 40);
    char percent[16] = {};
    sprintf_s(percent, "%d%%", static_cast<int>(progress * 100.0f));
    draw->AddText(fontOrDefault(g_fonts.medium), 17.0f, barMax + ImVec2(20.0f, -9.0f), colAlpha(255, 220, 255, 0.90f * loadingPageAlpha), percent);

    ImVec2 list(cardMin.x + 204.0f, cardMin.y + 308.0f);
    int failureStep = currentFailureStep();
    drawLoadingStep(draw, list + ImVec2(0.0f, 0.0f), "B", "Initializing modules", 0, progress, failureStep, loadingPageAlpha);
    drawLoadingStep(draw, list + ImVec2(0.0f, 26.0f), "W", "Loading configuration", 1, progress, failureStep, loadingPageAlpha);
    drawLoadingStep(draw, list + ImVec2(0.0f, 52.0f), "V", "Connecting to Minecraft", 2, progress, failureStep, loadingPageAlpha);
    drawLoadingStep(draw, list + ImVec2(0.0f, 78.0f), "A", "Finalizing injection", 3, progress, failureStep, loadingPageAlpha);

    const char* quote = "\"In the silence of the rain, I find my focus.\"";
    ImVec2 quoteSize = ImGui::CalcTextSize(quote);
    draw->AddText(fontOrDefault(g_fonts.medium), 14.0f,
                  ImVec2(cardMin.x + (cardSize.x - quoteSize.x) * 0.5f, cardMax.y - 42.0f),
                  colAlpha(255, 255, 255, 0.34f * loadingPageAlpha), quote);
    float dotsX = cardMin.x + cardSize.x * 0.5f - 42.0f;
    int activeDot = static_cast<int>(progress * 4.99f);
    for (int i = 0; i < 5; ++i) {
        bool passed = i <= activeDot;
        bool current = i == activeDot;
        float radius = current ? 5.4f : 4.2f;
        ImU32 dotColor = passed ? colAlpha(255, 165, 245, (current ? 0.95f : 0.56f) * loadingPageAlpha)
                                : colAlpha(255, 255, 255, 0.15f * loadingPageAlpha);
        draw->AddCircleFilled(ImVec2(dotsX + i * 21.0f, cardMax.y - 18.0f), radius, dotColor, 18);
    }

    if (g_app.resultAlpha > 0.01f) {
        float ra = g_app.resultAlpha * alpha;
        bool success = g_app.uiState == STATE_SUCCESS;
        draw->AddRectFilled(cardMin + ImVec2(1, 1), cardMax - ImVec2(1, 1),
                            colAlpha(22, 18, 32, 0.42f * ra), 21.0f);
        ImVec2 resultCenter(cardMin.x + cardSize.x * 0.5f, cardMin.y + cardSize.y * 0.5f - 18.0f);
        ImU32 glow = success ? colAlpha(120, 255, 185, 0.12f * ra) : colAlpha(255, 90, 130, 0.13f * ra);
        ImU32 accent = success ? colAlpha(128, 255, 190, 0.95f * ra) : colAlpha(255, 112, 140, 0.95f * ra);
        ImVec2 markCenter = resultCenter + ImVec2(0.0f, -58.0f);
        draw->AddCircleFilled(markCenter, 82.0f, glow, 72);
        draw->AddCircleFilled(markCenter, 43.0f, success ? colAlpha(120, 255, 185, 0.070f * ra)
                                                        : colAlpha(255, 90, 130, 0.075f * ra), 64);
        draw->AddCircle(markCenter, 34.0f, accent, 56, 2.2f);
        if (success) {
            draw->AddLine(markCenter + ImVec2(-13.0f, -1.0f), markCenter + ImVec2(-4.0f, 10.0f), accent, 3.0f);
            draw->AddLine(markCenter + ImVec2(-4.0f, 10.0f), markCenter + ImVec2(16.0f, -15.0f), accent, 3.0f);
        } else {
            draw->AddLine(markCenter + ImVec2(-12.0f, -12.0f), markCenter + ImVec2(12.0f, 12.0f), accent, 2.8f);
            draw->AddLine(markCenter + ImVec2(12.0f, -12.0f), markCenter + ImVec2(-12.0f, 12.0f), accent, 2.8f);
        }
        const char* title = success ? "Injection Complete" : "Injection Failed";
        const char* subtitle = success ? "VapuLite is now attached to the selected client." : g_app.status;
        ImFont* titleFont = fontOrDefault(g_fonts.title);
        ImFont* bodyFont = fontOrDefault(g_fonts.medium);
        ImVec2 titleSize = titleFont->CalcTextSizeA(30.0f, 10000.0f, 0.0f, title);
        draw->AddText(fontOrDefault(g_fonts.title), 30.0f,
                      resultCenter + ImVec2(-titleSize.x * 0.5f, 62.0f),
                      colAlpha(255, 235, 255, 0.95f * ra), title);
        ImVec2 subSize = bodyFont->CalcTextSizeA(16.0f, 10000.0f, 0.0f, subtitle);
        float subX = resultCenter.x - subSize.x * 0.5f;
        if (subX < cardMin.x + 72.0f) {
            subX = cardMin.x + 72.0f;
        }
        draw->AddText(bodyFont, 16.0f,
                      ImVec2(subX, resultCenter.y + 108.0f),
                      colAlpha(255, 255, 255, 0.52f * ra), subtitle);
        ImVec2 buttonMin(resultCenter.x - 74.0f, resultCenter.y + 154.0f);
        ImVec2 buttonMax(resultCenter.x + 74.0f, resultCenter.y + 188.0f);
        ImGui::SetCursorScreenPos(buttonMin);
        ImGui::InvisibleButton("result_back_button", buttonMax - buttonMin);
        bool buttonHovered = ImGui::IsItemHovered();
        bool resetClicked = ImGui::IsItemClicked();
        draw->AddRectFilled(buttonMin, buttonMax, success ? colAlpha(120, 255, 185, 0.16f * ra)
                                                          : colAlpha(255, 112, 140, (buttonHovered ? 0.26f : 0.16f) * ra), 9.0f);
        draw->AddRect(buttonMin, buttonMax, success ? colAlpha(145, 255, 205, 0.42f * ra)
                                                    : colAlpha(255, 150, 170, (buttonHovered ? 0.62f : 0.42f) * ra), 9.0f);
        const char* buttonText = success ? "Done" : "Back";
        ImVec2 btnSize = ImGui::CalcTextSize(buttonText);
        draw->AddText(fontOrDefault(g_fonts.medium), 15.0f,
                      ImVec2(resultCenter.x - btnSize.x * 0.5f, buttonMin.y + 8.0f),
                      colAlpha(255, 255, 255, 0.78f * ra), buttonText);
        if (resetClicked) {
            return true;
        }
    }
    return false;
}

bool invisibleHit(const char* id, ImVec2 pos, ImVec2 size) {
    ImGui::SetCursorScreenPos(pos);
    ImGui::InvisibleButton(id, size);
    return ImGui::IsItemClicked();
}

void resetToReady() {
    g_app.uiState = STATE_READY;
    g_app.injectStarted = 0;
    g_app.injectFinished = 0;
    g_app.resultAlpha = 0.0f;
    setStatus("Ready");
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
        setStatus("Target profile selected");
    } else if (clicked) {
        setStatus("This profile is not available now");
    }
    ImGui::PopID();
}

void drawVersionIcon(ImDrawList* draw, ImVec2 center, int type, float alpha, bool enabled) {
    float a = enabled ? alpha : alpha * 0.48f;
    if (type >= 0 && type < 3 && g_versionTextures[type] && g_versionTextureSizes[type].x > 0.0f && g_versionTextureSizes[type].y > 0.0f) {
        float maxSize = type == 0 ? 46.0f : 42.0f;
        float aspect = g_versionTextureSizes[type].x / g_versionTextureSizes[type].y;
        ImVec2 size(maxSize, maxSize);
        if (aspect > 1.0f) {
            size.y = maxSize / aspect;
        } else {
            size.x = maxSize * aspect;
        }
        ImVec2 min = center - size * 0.5f;
        ImVec2 max = center + size * 0.5f;
        draw->AddImage(g_versionTextures[type], min, max, ImVec2(0, 0), ImVec2(1, 1),
                       colAlpha(255, 255, 255, a));
        return;
    }
    ImVec2 p = center - ImVec2(21.0f, 21.0f);
    if (type == 0) {
        ImVec2 top[4] = {
            p + ImVec2(7.0f, 2.0f), p + ImVec2(34.0f, 7.0f),
            p + ImVec2(24.0f, 18.0f), p + ImVec2(0.0f, 12.0f)
        };
        ImVec2 left[4] = {
            p + ImVec2(0.0f, 12.0f), p + ImVec2(24.0f, 18.0f),
            p + ImVec2(24.0f, 39.0f), p + ImVec2(0.0f, 32.0f)
        };
        ImVec2 right[4] = {
            p + ImVec2(24.0f, 18.0f), p + ImVec2(34.0f, 7.0f),
            p + ImVec2(34.0f, 28.0f), p + ImVec2(24.0f, 39.0f)
        };
        draw->AddQuadFilled(top[0], top[1], top[2], top[3], colAlpha(126, 210, 112, 0.82f * a));
        draw->AddQuadFilled(left[0], left[1], left[2], left[3], colAlpha(116, 78, 50, 0.90f * a));
        draw->AddQuadFilled(right[0], right[1], right[2], right[3], colAlpha(82, 58, 42, 0.92f * a));
        draw->AddLine(top[0], top[1], colAlpha(225, 255, 210, 0.24f * a), 1.0f);
        draw->AddLine(left[2], left[3], colAlpha(255, 210, 180, 0.14f * a), 1.0f);
    } else if (type == 1) {
        ImVec2 boxMin = center - ImVec2(17.0f, 17.0f);
        ImVec2 boxMax = center + ImVec2(17.0f, 17.0f);
        draw->AddRectFilled(boxMin, boxMax, colAlpha(120, 112, 132, 0.42f * a), 5.0f);
        draw->AddRect(boxMin, boxMax, colAlpha(255, 235, 255, 0.18f * a), 5.0f, 0, 1.0f);
        draw->AddLine(center - ImVec2(10.0f, 4.0f), center + ImVec2(10.0f, -4.0f), colAlpha(210, 196, 224, 0.52f * a), 2.0f);
        draw->AddLine(center - ImVec2(7.0f, 8.0f), center + ImVec2(12.0f, 9.0f), colAlpha(130, 118, 148, 0.66f * a), 2.0f);
        draw->AddCircleFilled(center + ImVec2(-6.0f, 8.0f), 3.0f, colAlpha(255, 208, 255, 0.46f * a), 12);
    } else {
        draw->AddRectFilled(center - ImVec2(13.0f, 12.0f), center + ImVec2(13.0f, 14.0f),
                            colAlpha(106, 76, 56, 0.70f * a), 4.0f);
        for (int i = 0; i < 5; ++i) {
            float angle = -1.25f + i * 0.62f;
            ImVec2 tip(center.x + cosf(angle) * 22.0f, center.y - 8.0f + sinf(angle) * 19.0f);
            ImVec2 baseA(center.x + cosf(angle - 0.18f) * 8.0f, center.y + sinf(angle - 0.18f) * 7.0f);
            ImVec2 baseB(center.x + cosf(angle + 0.18f) * 8.0f, center.y + sinf(angle + 0.18f) * 7.0f);
            draw->AddTriangleFilled(tip, baseA, baseB, colAlpha(255, 154, 220, 0.74f * a));
        }
        draw->AddCircleFilled(center, 6.0f, colAlpha(255, 218, 245, 0.70f * a), 18);
    }
}

bool versionCard(const char* title, const char* meta, bool selected, bool enabled, ImVec2 size) {
    ImGui::BeginGroup();
    ImVec2 p = ImGui::GetCursorScreenPos();
    ImDrawList* draw = ImGui::GetWindowDrawList();
    ImGui::InvisibleButton(title, size);
    bool clickedCard = ImGui::IsItemClicked() && enabled;
    bool hovered = ImGui::IsItemHovered() && enabled;
    float a = enabled ? 1.0f : 0.56f;
    ImU32 bg = colAlpha(48, 48, 82, selected ? 0.18f : 0.075f);
    ImU32 border = selected ? colAlpha(235, 190, 255, hovered ? 0.68f : 0.42f)
                            : colAlpha(255, 255, 255, hovered ? 0.20f : 0.10f);
    draw->AddRectFilled(p, p + size, bg, 9.0f);
    draw->AddRect(p, p + size, border, 9.0f, 0, selected ? 1.3f : 1.0f);
    draw->AddBezierCubic(p + ImVec2(18.0f, 5.0f), p + ImVec2(size.x * 0.28f, -1.0f),
                         p + ImVec2(size.x * 0.52f, 10.0f), p + ImVec2(size.x * 0.74f, 5.0f),
                         colAlpha(255, 230, 255, selected ? 0.20f : 0.075f), 0.85f, 28);
    ImVec2 imgMin(p.x + 12.0f, p.y + 12.0f);
    ImVec2 imgMax(p.x + size.x - 12.0f, p.y + 138.0f);
    draw->AddRectFilled(imgMin, imgMax, enabled ? colAlpha(45, 82, 102, 0.55f) : colAlpha(34, 31, 38, 0.48f), 6.0f);
    draw->AddRect(imgMin, imgMax, colAlpha(255, 255, 255, enabled ? 0.13f : 0.06f), 6.0f, 0, 1.0f);
    draw->AddCircleFilled((imgMin + imgMax) * 0.5f, 88.0f, colAlpha(199, 149, 237, enabled ? 0.040f : 0.018f), 64);
    for (int i = 0; i < 12; ++i) {
        float x = imgMin.x + 10.0f + i * 20.0f;
        draw->AddLine(ImVec2(x, imgMin.y + 6.0f), ImVec2(x + 36.0f, imgMax.y - 6.0f),
                      enabled ? colAlpha(220, 160, 255, 0.20f) : colAlpha(120, 90, 100, 0.11f), 2.0f);
    }
    if (!enabled) {
        draw->AddRectFilled(imgMin, imgMax, col(14, 13, 15, 148), 5.0f);
        draw->AddText(fontOrDefault(g_fonts.semiBold), 13.0f, ImVec2(imgMin.x + size.x * 0.25f, imgMin.y + 52.0f), col(255, 255, 255, 72), "Not Released");
    }
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, ImVec2(p.x + 12.0f, p.y + size.y - 54.0f), colAlpha(255, 255, 255, 0.90f * a), title);
    draw->AddText(fontOrDefault(g_fonts.icon8), 8.0f, ImVec2(p.x + 12.0f, p.y + size.y - 23.0f), colAlpha(255, 255, 255, 0.48f * a), "E");
    draw->AddText(fontOrDefault(g_fonts.medium), 14.0f, ImVec2(p.x + 28.0f, p.y + size.y - 31.0f), colAlpha(255, 255, 255, 0.62f * a), meta);
    ImVec2 bmin(p.x + size.x - 46.0f, p.y + size.y - 46.0f);
    ImVec2 bmax(p.x + size.x - 12.0f, p.y + size.y - 12.0f);
    draw->AddRectFilled(bmin, bmax, enabled ? colAlpha(199, 149, 237, selected ? 0.88f : 0.42f) : colAlpha(255, 255, 255, 0.06f), 7.0f);
    draw->AddRect(bmin, bmax, enabled ? colAlpha(255, 255, 255, 0.44f) : colAlpha(255, 255, 255, 0.08f), 7.0f);
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
        g_app.injectStarted = GetTickCount();
        g_app.injectFinished = g_app.injectStarted;
        g_app.loadingAlpha = 0.0f;
        g_app.resultAlpha = 0.0f;
        setStatus("Injection failed: loader DLL not found");
        return;
    }
    g_app.uiState = STATE_INJECTING;
    g_app.injectStarted = GetTickCount();
    g_app.injectFinished = 0;
    g_app.loadingAlpha = 0.0f;
    g_app.resultAlpha = 0.0f;
    char status[128] = {};
    sprintf_s(status, "Searching %s target...", targetProfileNameA(selectedTargetProfile()));
    setStatus(status);
    HANDLE thread = CreateThread(nullptr, 0, injectThread, hwnd, 0, nullptr);
    if (thread) {
        CloseHandle(thread);
    } else {
        g_app.uiState = STATE_FAILED;
        g_app.injectFinished = GetTickCount();
        setStatus("Injection failed: worker thread was not created");
    }
}

void drawMainUi(HWND hwnd) {
    easing(g_app.stageAlpha, 1.0f, 6.0f);
    easing(g_app.contentAlpha, 1.0f, 6.0f);
    bool showInjectionOverlay = g_app.uiState == STATE_INJECTING || g_app.uiState == STATE_SUCCESS || g_app.uiState == STATE_FAILED;
    easing(g_app.loadingAlpha, showInjectionOverlay ? 1.0f : 0.0f, 8.0f);
    bool showResult = (g_app.uiState == STATE_SUCCESS || g_app.uiState == STATE_FAILED) &&
                      g_app.injectFinished != 0 && GetTickCount() - g_app.injectFinished > 650;
    easing(g_app.resultAlpha, showResult ? 1.0f : 0.0f, 7.0f);

    ImGuiIO& io = ImGui::GetIO();
    ImGui::SetNextWindowPos(ImVec2(0, 0));
    ImGui::SetNextWindowSize(io.DisplaySize);
    ImGui::Begin("VapuLite Injector", nullptr,
                 ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoMove |
                 ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoSavedSettings);

    ImDrawList* draw = ImGui::GetWindowDrawList();
    ImVec2 s = io.DisplaySize;
    float a = g_app.stageAlpha;
    ImVec2 windowMin(0.0f, 0.0f);
    ImVec2 windowMax(s.x, s.y);
    if (g_blur.ready && g_blur.srvA) {
        draw->AddImageRounded(g_blur.srvA, windowMin, windowMax, ImVec2(0.0f, 0.0f), ImVec2(1.0f, 1.0f),
                              colAlpha(255, 255, 255, 0.36f * a), 16.0f);
    }
    draw->AddRectFilled(windowMin, windowMax, colAlpha(15, 13, 22, 0.46f), 16.0f);
    draw->AddRectFilled(ImVec2(1.0f, 1.0f), ImVec2(s.x - 1.0f, s.y - 1.0f), colAlpha(255, 255, 255, 0.018f * a), 16.0f);
    draw->AddRect(ImVec2(0.5f, 0.5f), ImVec2(s.x - 0.5f, s.y - 0.5f), colAlpha(255, 230, 255, 0.12f * a), 16.0f);
    draw->AddRect(ImVec2(2.0f, 2.0f), ImVec2(s.x - 2.0f, s.y - 2.0f), colAlpha(199, 149, 237, 0.075f * a), 14.0f);
    draw->AddBezierCubic(ImVec2(30.0f, 4.0f), ImVec2(174.0f, -2.0f),
                         ImVec2(386.0f, 12.0f), ImVec2(548.0f, 5.0f),
                         colAlpha(255, 238, 255, 0.13f * a), 0.8f, 34);
    draw->AddBezierCubic(ImVec2(s.x - 318.0f, 5.0f), ImVec2(s.x - 222.0f, 12.0f),
                         ImVec2(s.x - 112.0f, -1.0f), ImVec2(s.x - 36.0f, 7.0f),
                         colAlpha(210, 178, 255, 0.085f * a), 0.8f, 28);
    draw->AddBezierCubic(ImVec2(5.0f, 44.0f), ImVec2(-2.0f, 82.0f),
                         ImVec2(12.0f, 132.0f), ImVec2(5.0f, 196.0f),
                         colAlpha(255, 230, 255, 0.08f * a), 0.7f, 24);
    draw->AddBezierCubic(ImVec2(s.x - 5.0f, 44.0f), ImVec2(s.x + 2.0f, 82.0f),
                         ImVec2(s.x - 12.0f, 132.0f), ImVec2(s.x - 5.0f, 196.0f),
                         colAlpha(210, 178, 255, 0.07f * a), 0.7f, 24);
    drawSakuraPetals(draw, s, 0.85f * a);
    draw->AddCircleFilled(ImVec2(205, 148), 112.0f, colAlpha(199, 149, 237, 0.080f * a), 64);
    draw->AddCircleFilled(ImVec2(788, 416), 120.0f, colAlpha(199, 149, 237, 0.065f * a), 64);
    draw->AddBezierCubic(ImVec2(96, 250), ImVec2(168, 78), ImVec2(348, 86), ImVec2(474, 125), colAlpha(80, 135, 165, 0.28f * a), 1.0f, 32);
    draw->AddBezierCubic(ImVec2(474, 125), ImVec2(596, 162), ImVec2(680, 54), ImVec2(842, 66), colAlpha(80, 135, 165, 0.22f * a), 1.0f, 32);

    if (showInjectionOverlay) {
        if (g_app.loadingAlpha > 0.01f && drawInjectionLoadingOverlay(draw, s, g_app.loadingAlpha)) {
            resetToReady();
        }
        ImGui::End();
        return;
    }

    ImVec2 pad(16.0f, 16.0f);
    ImVec2 topMin = pad;
    ImVec2 topMax(s.x - 68.0f, pad.y + 42.0f);
    if (g_blur.ready && g_blur.srvA) {
        ImVec2 uvMin(topMin.x / s.x, topMin.y / s.y);
        ImVec2 uvMax(topMax.x / s.x, topMax.y / s.y);
        draw->AddImageRounded(g_blur.srvA, topMin, topMax, uvMin, uvMax,
                              colAlpha(255, 255, 255, 0.34f * a), 11.0f);
    }
    draw->AddRectFilled(topMin, topMax, colAlpha(42, 35, 68, 0.105f * a), 11.0f);
    draw->AddRectFilled(topMin + ImVec2(1.0f, 1.0f), topMax - ImVec2(1.0f, 1.0f),
                        colAlpha(255, 255, 255, 0.014f * a), 10.0f);
    draw->AddRect(topMin, topMax, colAlpha(255, 230, 255, 0.125f * a), 11.0f, 0, 1.0f);
    draw->AddBezierCubic(topMin + ImVec2(28.0f, 4.0f), topMin + ImVec2(168.0f, -3.0f),
                         topMin + ImVec2(376.0f, 9.0f), topMin + ImVec2(552.0f, 3.0f),
                         colAlpha(255, 238, 255, 0.115f * a), 0.85f, 34);
    draw->AddBezierCubic(ImVec2(topMax.x - 250.0f, topMin.y + 4.0f),
                         ImVec2(topMax.x - 168.0f, topMin.y + 9.0f),
                         ImVec2(topMax.x - 96.0f, topMin.y - 2.0f),
                         ImVec2(topMax.x - 34.0f, topMin.y + 6.0f),
                         colAlpha(210, 178, 255, 0.075f * a), 0.8f, 26);
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, ImVec2(topMin.x + 16.0f, topMin.y + 16.0f), col(199, 149, 237, static_cast<int>(255 * a)), "B");
    draw->AddCircleFilled(ImVec2(topMin.x + 50.0f, topMin.y + 22.0f), 1.6f, colAlpha(255, 255, 255, 0.12f * a), 12);
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, ImVec2(topMin.x + 64.0f, topMin.y + 12.0f), col(255, 255, 255, static_cast<int>(235 * a)), "vape.gg");
    draw->AddText(fontOrDefault(g_fonts.icon12), 12.0f, ImVec2(topMax.x - 148.0f, topMin.y + 16.0f), colAlpha(255, 255, 255, 0.24f * a), "V");
    char dateText[16] = {};
    currentDateText(dateText, sizeof(dateText));
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, ImVec2(topMax.x - 124.0f, topMin.y + 12.0f), colAlpha(255, 255, 255, 0.48f * a), dateText);

    ImVec2 closeMin(s.x - 42.0f, 16.0f);
    if (invisibleHit("close", closeMin, ImVec2(26.0f, 26.0f))) {
        PostMessage(hwnd, WM_CLOSE, 0, 0);
    }
    draw->AddRectFilled(closeMin, closeMin + ImVec2(26.0f, 26.0f), colAlpha(255, 255, 255, ImGui::IsItemHovered() ? 0.10f : 0.04f), 8.0f);
    draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, closeMin + ImVec2(8.0f, 4.0f), colAlpha(255, 255, 255, 0.62f), "x");

    float ca = g_app.contentAlpha * g_app.stageAlpha;
    ImVec2 shellMin(36.0f, 82.0f);
    ImVec2 shellMax(s.x - 36.0f, s.y - 34.0f);
    if (g_blur.ready && g_blur.srvA) {
        ImVec2 uvMin(shellMin.x / s.x, shellMin.y / s.y);
        ImVec2 uvMax(shellMax.x / s.x, shellMax.y / s.y);
        draw->AddImageRounded(g_blur.srvA, shellMin, shellMax, uvMin, uvMax,
                              colAlpha(255, 255, 255, 0.42f * ca), 24.0f);
    }
    draw->AddRectFilled(shellMin, shellMax, colAlpha(42, 35, 68, 0.155f * ca), 24.0f);
    draw->AddRectFilled(shellMin + ImVec2(1.0f, 1.0f), shellMax - ImVec2(1.0f, 1.0f),
                        colAlpha(255, 255, 255, 0.018f * ca), 23.0f);
    draw->AddRect(shellMin, shellMax, colAlpha(255, 230, 255, 0.16f * ca), 24.0f, 0, 1.0f);
    draw->AddBezierCubic(shellMin + ImVec2(42.0f, 10.0f), shellMin + ImVec2(168.0f, -4.0f),
                         shellMin + ImVec2(430.0f, 22.0f), shellMin + ImVec2(680.0f, 8.0f),
                         colAlpha(255, 230, 255, 0.13f * ca), 0.9f, 40);
    drawLiquidGlassEdges(draw, shellMin, shellMax, 24.0f, ca);

    ImVec2 logoCenter(shellMin.x + 72.0f, shellMin.y + 56.0f);
    draw->AddCircleFilled(logoCenter, 46.0f, colAlpha(199, 149, 237, 0.10f * ca), 48);
    drawLogo(draw, logoCenter, 86.0f, ca);
    draw->AddText(fontOrDefault(g_fonts.title), 30.0f, shellMin + ImVec2(122.0f, 35.0f),
                  colAlpha(255, 230, 255, 0.96f * ca), "VapuLite");
    draw->AddText(fontOrDefault(g_fonts.medium), 15.0f, shellMin + ImVec2(124.0f, 72.0f),
                  colAlpha(255, 255, 255, 0.42f * ca), "Sakura Injector");
    draw->AddText(fontOrDefault(g_fonts.medium), 15.0f, shellMax - ImVec2(98.0f, shellMax.y - shellMin.y - 68.0f),
                  colAlpha(255, 255, 255, 0.46f * ca), "v2.5.0");

    ImVec2 sideMin(shellMin.x + 22.0f, shellMin.y + 124.0f);
    ImVec2 sideMax(shellMin.x + 302.0f, shellMax.y - 28.0f);
    draw->AddRectFilled(sideMin, sideMax, colAlpha(18, 16, 32, 0.18f * ca), 18.0f);
    draw->AddRect(sideMin, sideMax, colAlpha(255, 255, 255, 0.075f * ca), 18.0f);
    const char* stepTitle[4] = {"Select Version", "Check Environment", "Prepare Injection", "Launch Client"};
    const char* stepSub[4] = {"Choose target game version", "Verify Minecraft process", "Setting up modules", "Starting Minecraft client"};
    for (int i = 0; i < 4; ++i) {
        ImVec2 p(sideMin.x + 28.0f, sideMin.y + 34.0f + i * 82.0f);
        bool active = i == 0;
        if (i < 3) {
            draw->AddLine(p + ImVec2(7.0f, 20.0f), p + ImVec2(7.0f, 66.0f),
                          colAlpha(255, 255, 255, 0.070f * ca), 1.0f);
        }
        draw->AddCircleFilled(p + ImVec2(7.0f, 7.0f), active ? 15.0f : 11.0f,
                              active ? colAlpha(213, 130, 255, 0.20f * ca) : colAlpha(255, 255, 255, 0.025f * ca), 28);
        draw->AddCircle(p + ImVec2(7.0f, 7.0f), active ? 9.0f : 8.0f,
                        active ? colAlpha(255, 188, 255, 0.82f * ca) : colAlpha(255, 255, 255, 0.24f * ca), 28, 1.5f);
        if (active) {
            draw->AddCircleFilled(p + ImVec2(7.0f, 7.0f), 4.2f, colAlpha(255, 202, 255, 0.96f * ca), 18);
        }
        draw->AddText(fontOrDefault(g_fonts.medium), 16.0f, p + ImVec2(34.0f, -4.0f),
                      colAlpha(255, 255, 255, (active ? 0.90f : 0.58f) * ca), stepTitle[i]);
        draw->AddText(fontOrDefault(g_fonts.medium), 13.0f, p + ImVec2(34.0f, 22.0f),
                      colAlpha(255, 255, 255, (active ? 0.46f : 0.30f) * ca), stepSub[i]);
    }
    ImVec2 contentMin(shellMin.x + 330.0f, shellMin.y + 132.0f);
    ImVec2 contentMax(shellMax.x - 36.0f, shellMax.y - 30.0f);
    draw->AddText(fontOrDefault(g_fonts.title), 29.0f, contentMin,
                  colAlpha(255, 235, 255, 0.94f * ca), "Select Target Version");
    draw->AddText(fontOrDefault(g_fonts.medium), 15.0f, contentMin + ImVec2(0.0f, 42.0f),
                  colAlpha(255, 255, 255, 0.44f * ca), "Choose the Minecraft version you want to inject");

    struct VersionCardData {
        const char* title;
        const char* tag;
        const char* meta;
        int iconType;
        bool enabled;
    };
    VersionCardData cards[3] = {
        {"1.8.9", "Forge", "FML target", 0, true},
        {"1.8.9", "Vanilla", "Clean target", 1, true},
        {"1.8.9", "Lunar", "Client target", 2, true},
    };
    ImVec2 cardSize(154.0f, 128.0f);
    ImVec2 cardsStart(contentMin.x, contentMin.y + 82.0f);
    for (int i = 0; i < 3; ++i) {
        ImVec2 cardMin = cardsStart + ImVec2(i * (cardSize.x + 14.0f), 0.0f);
        ImVec2 cardMax = cardMin + cardSize;
        bool selected = g_app.selectedVersion == i;
        bool clicked = invisibleHit(cards[i].title, cardMin, cardSize);
        bool hovered = ImGui::IsItemHovered() && cards[i].enabled;
        if (clicked && cards[i].enabled) {
            g_app.selectedVersion = i;
            setStatus("Target profile selected");
        } else if (clicked) {
            setStatus("This profile is not available now");
        }
        float cardAlpha = cards[i].enabled ? 1.0f : 0.55f;
        draw->AddRectFilled(cardMin, cardMax,
                            selected ? colAlpha(92, 58, 136, 0.36f * ca) : colAlpha(255, 255, 255, (hovered ? 0.075f : 0.046f) * ca),
                            14.0f);
        draw->AddRect(cardMin, cardMax,
                      selected ? colAlpha(255, 180, 255, 0.70f * ca) : colAlpha(255, 255, 255, (hovered ? 0.20f : 0.095f) * ca),
                      14.0f, 0, selected ? 1.4f : 1.0f);
        if (selected) {
            draw->AddRectFilled(cardMin - ImVec2(6.0f, 6.0f), cardMax + ImVec2(6.0f, 6.0f),
                                colAlpha(210, 110, 255, 0.045f * ca), 18.0f);
            ImVec2 badgeCenter(cardMax.x - 24.0f, cardMin.y + 24.0f);
            draw->AddCircleFilled(badgeCenter, 15.0f, colAlpha(232, 158, 255, 0.13f * ca), 28);
            draw->AddCircleFilled(badgeCenter, 10.5f,
                                  colAlpha(232, 158, 255, 0.84f * ca), 28);
            draw->AddLine(badgeCenter + ImVec2(-4.0f, 0.0f), badgeCenter + ImVec2(-1.0f, 4.0f),
                          colAlpha(255, 255, 255, 0.95f * ca), 1.7f);
            draw->AddLine(badgeCenter + ImVec2(-1.0f, 4.0f), badgeCenter + ImVec2(5.0f, -5.0f),
                          colAlpha(255, 255, 255, 0.95f * ca), 1.7f);
        }
        ImVec2 iconCenter(cardMin.x + 48.0f, cardMin.y + 34.0f);
        draw->AddCircleFilled(iconCenter, 24.0f, colAlpha(255, 164, 238, (cards[i].enabled ? 0.12f : 0.045f) * ca), 36);
        drawVersionIcon(draw, iconCenter + ImVec2(1.0f, 1.0f), cards[i].iconType, ca, cards[i].enabled);
        draw->AddText(fontOrDefault(g_fonts.title), 23.0f, cardMin + ImVec2(28.0f, 62.0f),
                      colAlpha(255, 255, 255, 0.88f * cardAlpha * ca), cards[i].title);
        draw->AddRectFilled(cardMin + ImVec2(84.0f, 69.0f), cardMin + ImVec2(140.0f, 96.0f),
                            colAlpha(255, 255, 255, 0.045f * cardAlpha * ca), 7.0f);
        draw->AddText(fontOrDefault(g_fonts.medium), 13.0f, cardMin + ImVec2(96.0f, 75.0f),
                      colAlpha(255, 255, 255, 0.58f * cardAlpha * ca), cards[i].tag);
        draw->AddText(fontOrDefault(g_fonts.medium), 13.0f, cardMin + ImVec2(28.0f, 104.0f),
                      colAlpha(255, 255, 255, 0.45f * cardAlpha * ca), cards[i].meta);
        if (!cards[i].enabled) {
            draw->AddRectFilled(cardMin, cardMax, colAlpha(12, 10, 18, 0.16f * ca), 14.0f);
        }
    }

    ImVec2 launchMin(contentMin.x + 74.0f, shellMax.y - 76.0f);
    ImVec2 launchMax(contentMax.x - 74.0f, shellMax.y - 24.0f);
    bool launchClicked = invisibleHit("launch_selected_version", launchMin, launchMax - launchMin);
    bool launchHovered = ImGui::IsItemHovered();
    draw->AddRectFilled(launchMin - ImVec2(10.0f, 10.0f), launchMax + ImVec2(10.0f, 10.0f),
                        colAlpha(214, 107, 255, (launchHovered ? 0.095f : 0.060f) * ca), 18.0f);
    draw->AddRectFilled(launchMin, launchMax,
                        colAlpha(145, 78, 196, (launchHovered ? 0.70f : 0.55f) * ca), 14.0f);
    draw->AddRect(launchMin, launchMax, colAlpha(255, 190, 255, 0.46f * ca), 14.0f, 0, 1.2f);
    draw->AddBezierCubic(launchMin + ImVec2(20.0f, 6.0f), launchMin + ImVec2(122.0f, -2.0f),
                         launchMax - ImVec2(140.0f, 48.0f), launchMax - ImVec2(24.0f, 48.0f),
                         colAlpha(255, 238, 255, 0.18f * ca), 0.85f, 30);
    ImVec2 launchTextSize = fontOrDefault(g_fonts.medium)->CalcTextSizeA(21.0f, 10000.0f, 0.0f, "Inject VapuLite");
    drawLogo(draw, ImVec2(launchMin.x + 68.0f, (launchMin.y + launchMax.y) * 0.5f), 46.0f, ca);
    draw->AddText(fontOrDefault(g_fonts.medium), 21.0f,
                  ImVec2((launchMin.x + launchMax.x - launchTextSize.x) * 0.5f, launchMin.y + 15.0f),
                  colAlpha(255, 255, 255, 0.92f * ca), "Inject VapuLite");
    draw->AddText(fontOrDefault(g_fonts.medium), 30.0f, launchMax - ImVec2(48.0f, 43.0f),
                  colAlpha(255, 255, 255, 0.72f * ca), ">");
    if (launchClicked) {
        startInject(hwnd);
    }

    ImU32 statusColor = g_app.uiState == STATE_SUCCESS ? col(115, 255, 176, 230)
                       : g_app.uiState == STATE_FAILED ? col(255, 112, 128, 230)
                                                       : colAlpha(255, 255, 255, 0.48f);
    if (!showInjectionOverlay) {
        draw->AddText(fontOrDefault(g_fonts.medium), 14.0f, ImVec2(18.0f, s.y - 32.0f), statusColor, g_app.status);
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
    if (g_blur.ready && g_blur.srvA) {
        draw->AddImageRounded(g_blur.srvA, ImVec2(0, 0), s, ImVec2(0, 0), ImVec2(1, 1),
                              colAlpha(255, 255, 255, 0.32f), 16.0f);
    }
    draw->AddRectFilled(ImVec2(0, 0), s, colAlpha(15, 13, 22, 0.48f), 16.0f);
    draw->AddRectFilled(ImVec2(1.0f, 1.0f), ImVec2(s.x - 1.0f, s.y - 1.0f), colAlpha(255, 255, 255, 0.018f), 16.0f);
    draw->AddRect(ImVec2(0.5f, 0.5f), ImVec2(s.x - 0.5f, s.y - 0.5f), colAlpha(255, 230, 255, 0.12f), 16.0f);
    draw->AddRect(ImVec2(2.0f, 2.0f), ImVec2(s.x - 2.0f, s.y - 2.0f), colAlpha(199, 149, 237, 0.075f), 14.0f);
    draw->AddBezierCubic(ImVec2(26.0f, 4.0f), ImVec2(114.0f, -2.0f),
                         ImVec2(236.0f, 12.0f), ImVec2(344.0f, 5.0f),
                         colAlpha(255, 238, 255, 0.13f), 0.8f, 26);
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
        g_app.injectFinished = GetTickCount();
        g_app.resultAlpha = 0.0f;
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
    const wchar_t* versionAssets[3] = {
        L"minecraft_grass_block.png",
        L"minecraft_furnace_block.png",
        L"minecraft_cherry_block.png"
    };
    for (int i = 0; i < 3; ++i) {
        std::wstring assetPath = resolveAssetPath(versionAssets[i]);
        if (!assetPath.empty()) {
            loadTextureFromFile(assetPath.c_str(), &g_versionTextures[i], &g_versionTextureSizes[i]);
        }
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
        updateGaussianBlurTexture();
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
