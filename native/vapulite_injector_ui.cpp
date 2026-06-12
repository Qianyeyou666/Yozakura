#include <windows.h>
#include <tlhelp32.h>
#include <shlwapi.h>

#include <cmath>
#include <cwchar>
#include <string>

#pragma comment(lib, "shlwapi.lib")

namespace {

const wchar_t* kClassName = L"VapuLiteInjectorUiWindow";
const UINT_PTR kTimerId = 1;
const UINT kInjectDone = WM_APP + 1;

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
    UiState uiState;
    DWORD started;
    DWORD expandStarted;
    int frame;
    int selectedVersion;
    bool mouseDown;
    bool dragging;
    POINT dragStart;
    RECT lastClose;
    RECT lastStart;
    RECT versionRects[3];
    HFONT titleFont;
    HFONT textFont;
    HFONT smallFont;
    HFONT tinyFont;
    wchar_t dllPath[MAX_PATH];
    wchar_t lastMessage[512];
};

AppState g_app = {};

COLORREF rgb(int r, int g, int b) {
    return RGB(r, g, b);
}

void setMessage(const wchar_t* text) {
    wcsncpy_s(g_app.lastMessage, text, _TRUNCATE);
}

bool ptInRect(RECT rc, int x, int y) {
    return x >= rc.left && x < rc.right && y >= rc.top && y < rc.bottom;
}

std::wstring exeDirectory() {
    wchar_t path[MAX_PATH] = {};
    GetModuleFileNameW(nullptr, path, MAX_PATH);
    PathRemoveFileSpecW(path);
    return std::wstring(path);
}

bool resolveDllPath(wchar_t* output, DWORD outputChars) {
    std::wstring dir = exeDirectory();
    std::wstring candidate = dir + L"\\VapuLiteReobf-x64.dll";
    if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
        wcsncpy_s(output, outputChars, candidate.c_str(), _TRUNCATE);
        return true;
    }
    candidate = dir + L"\\build\\libs\\VapuLiteReobf-x64.dll";
    if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
        wcsncpy_s(output, outputChars, candidate.c_str(), _TRUNCATE);
        return true;
    }
    candidate = dir + L"\\VapuLiteReobf.dll";
    if (GetFileAttributesW(candidate.c_str()) != INVALID_FILE_ATTRIBUTES) {
        wcsncpy_s(output, outputChars, candidate.c_str(), _TRUNCATE);
        return true;
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
    DWORD pid;
    wchar_t title[256];
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
    FindWindowContext ctx = {};
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
    Sleep(900);

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

void createFonts() {
    g_app.titleFont = CreateFontW(30, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE,
                                  DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                                  CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
    g_app.textFont = CreateFontW(16, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE,
                                 DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                                 CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
    g_app.smallFont = CreateFontW(13, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                                  DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                                  CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
    g_app.tinyFont = CreateFontW(11, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                                 DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                                 CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
}

void drawText(HDC dc, const wchar_t* text, RECT rect, HFONT font, COLORREF color, UINT flags) {
    HFONT old = reinterpret_cast<HFONT>(SelectObject(dc, font));
    SetTextColor(dc, color);
    SetBkMode(dc, TRANSPARENT);
    DrawTextW(dc, text, -1, &rect, flags);
    SelectObject(dc, old);
}

void fillRound(HDC dc, RECT rc, int radius, COLORREF color, COLORREF border) {
    HBRUSH brush = CreateSolidBrush(color);
    HPEN pen = CreatePen(PS_SOLID, 1, border);
    HGDIOBJ oldBrush = SelectObject(dc, brush);
    HGDIOBJ oldPen = SelectObject(dc, pen);
    RoundRect(dc, rc.left, rc.top, rc.right, rc.bottom, radius, radius);
    SelectObject(dc, oldPen);
    SelectObject(dc, oldBrush);
    DeleteObject(pen);
    DeleteObject(brush);
}

void drawLine(HDC dc, int x1, int y1, int x2, int y2, COLORREF color, int width) {
    HPEN pen = CreatePen(PS_SOLID, width, color);
    HGDIOBJ old = SelectObject(dc, pen);
    MoveToEx(dc, x1, y1, nullptr);
    LineTo(dc, x2, y2);
    SelectObject(dc, old);
    DeleteObject(pen);
}

void drawSpinner(HDC dc, int cx, int cy, int radius, COLORREF color) {
    for (int i = 0; i < 12; ++i) {
        double a = (g_app.frame + i) * 0.52;
        int alpha = 42 + i * 17;
        COLORREF c = rgb(
            (GetRValue(color) * alpha + 18 * (255 - alpha)) / 255,
            (GetGValue(color) * alpha + 20 * (255 - alpha)) / 255,
            (GetBValue(color) * alpha + 26 * (255 - alpha)) / 255);
        HBRUSH b = CreateSolidBrush(c);
        HGDIOBJ old = SelectObject(dc, b);
        int x = cx + static_cast<int>(std::cos(a) * radius);
        int y = cy + static_cast<int>(std::sin(a) * radius);
        Ellipse(dc, x - 3, y - 3, x + 3, y + 3);
        SelectObject(dc, old);
        DeleteObject(b);
    }
}

void drawAbstractBackground(HDC dc, RECT client) {
    HBRUSH bg = CreateSolidBrush(rgb(10, 12, 16));
    FillRect(dc, &client, bg);
    DeleteObject(bg);

    RECT glow1 = {48, 70, 330, 215};
    fillRound(dc, glow1, 110, rgb(14, 21, 25), rgb(14, 21, 25));
    RECT glow2 = {430, 232, 700, 390};
    fillRound(dc, glow2, 120, rgb(13, 19, 23), rgb(13, 19, 23));

    HPEN dash = CreatePen(PS_DASH, 1, rgb(27, 45, 60));
    HGDIOBJ old = SelectObject(dc, dash);
    POINT pts[7] = {
        {92, 216}, {142, 90}, {286, 70}, {404, 108}, {525, 78}, {596, 42}, {662, 58}
    };
    Polyline(dc, pts, 7);
    SelectObject(dc, old);
    DeleteObject(dash);
}

void drawPill(HDC dc, RECT rc, const wchar_t* text, bool selected, bool enabled) {
    COLORREF fill = selected ? rgb(45, 52, 64) : rgb(15, 17, 22);
    COLORREF border = selected ? rgb(85, 96, 112) : rgb(31, 36, 45);
    COLORREF textColor = enabled ? rgb(222, 229, 236) : rgb(98, 104, 114);
    fillRound(dc, rc, 18, fill, border);
    drawText(dc, text, rc, g_app.tinyFont, textColor, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
}

void drawVersionCard(HDC dc, RECT rc, const wchar_t* title, const wchar_t* meta, bool selected, bool enabled) {
    COLORREF fill = selected ? rgb(28, 31, 39) : rgb(18, 20, 26);
    COLORREF border = selected ? rgb(107, 80, 137) : rgb(35, 38, 48);
    fillRound(dc, rc, 10, fill, border);
    RECT image = {rc.left + 8, rc.top + 8, rc.right - 8, rc.top + 94};
    fillRound(dc, image, 7, enabled ? rgb(36, 55, 70) : rgb(33, 30, 35), rgb(42, 45, 55));
    for (int i = 0; i < 9; ++i) {
        int x = image.left + 12 + i * 18;
        drawLine(dc, x, image.top + 8, x + 24, image.bottom - 8,
                 enabled ? rgb(47, 95, 116) : rgb(57, 50, 55), 2);
    }
    if (!enabled) {
        RECT nr = image;
        drawText(dc, L"⊗ Not Released", nr, g_app.textFont, rgb(122, 124, 132), DT_CENTER | DT_VCENTER | DT_SINGLELINE);
    }

    RECT titleRc = {rc.left + 10, rc.bottom - 48, rc.right - 44, rc.bottom - 28};
    drawText(dc, title, titleRc, g_app.textFont, enabled ? rgb(232, 236, 241) : rgb(140, 144, 152), DT_LEFT | DT_SINGLELINE);
    RECT metaRc = {rc.left + 10, rc.bottom - 27, rc.right - 44, rc.bottom - 9};
    drawText(dc, meta, metaRc, g_app.smallFont, enabled ? rgb(190, 202, 210) : rgb(114, 118, 126), DT_LEFT | DT_SINGLELINE);
    RECT action = {rc.right - 36, rc.bottom - 36, rc.right - 10, rc.bottom - 10};
    fillRound(dc, action, 5, selected ? rgb(93, 66, 126) : rgb(31, 33, 40), selected ? rgb(154, 122, 188) : rgb(45, 48, 57));
    drawText(dc, selected ? L"▶" : L"≡", action, g_app.smallFont, enabled ? rgb(244, 247, 255) : rgb(96, 100, 110),
             DT_CENTER | DT_VCENTER | DT_SINGLELINE);
}

void drawRightRail(HDC dc, int w, int h) {
    RECT close = {w - 34, 12, w - 14, 32};
    g_app.lastClose = close;
    drawText(dc, L"×", close, g_app.textFont, rgb(190, 198, 206), DT_CENTER | DT_VCENTER | DT_SINGLELINE);

    RECT orb = {w - 64, h / 2 - 18, w - 38, h / 2 + 8};
    fillRound(dc, orb, 20, rgb(55, 82, 132), rgb(71, 106, 168));
    drawText(dc, L"✦", orb, g_app.textFont, rgb(232, 242, 255), DT_CENTER | DT_VCENTER | DT_SINGLELINE);

    const wchar_t* labels[3] = {L"Login", L"Loader", L"Dash"};
    for (int i = 0; i < 3; ++i) {
        RECT r = {w - 72, h / 2 + 22 + i * 34, w - 14, h / 2 + 48 + i * 34};
        bool loader = i == 1;
        if (loader) {
            g_app.lastStart = r;
        }
        fillRound(dc, r, 13, loader ? rgb(23, 28, 38) : rgb(15, 17, 22),
                  loader ? rgb(62, 75, 98) : rgb(34, 38, 48));
        drawText(dc, labels[i], r, g_app.tinyFont, loader ? rgb(226, 238, 250) : rgb(170, 178, 188),
                 DT_CENTER | DT_VCENTER | DT_SINGLELINE);
    }
}

void drawMainUi(HDC dc, RECT client) {
    drawAbstractBackground(dc, client);
    int w = client.right;
    int h = client.bottom;

    RECT logo = {24, 18, 56, 44};
    drawText(dc, L"⌄", logo, g_app.titleFont, rgb(176, 133, 255), DT_CENTER | DT_VCENTER | DT_SINGLELINE);
    RECT invite = {64, 20, 330, 42};
    drawText(dc, L"⋮ discord.gg/xWVR45bahE", invite, g_app.textFont, rgb(228, 233, 240), DT_LEFT | DT_SINGLELINE);
    RECT date = {w - 166, 21, w - 64, 42};
    drawText(dc, L"▣ 22.08.2025", date, g_app.smallFont, rgb(145, 151, 162), DT_RIGHT | DT_SINGLELINE);
    RECT gear = {w - 52, 16, w - 18, 48};
    fillRound(dc, gear, 8, rgb(31, 33, 42), rgb(39, 42, 52));
    drawText(dc, L"⚙", gear, g_app.smallFont, rgb(153, 160, 172), DT_CENTER | DT_VCENTER | DT_SINGLELINE);
    drawLine(dc, 18, 56, w - 18, 56, rgb(28, 31, 40), 1);

    RECT title = {0, 108, w, 142};
    drawText(dc, L"Choose One", title, g_app.titleFont, rgb(225, 230, 237), DT_CENTER | DT_SINGLELINE);

    g_app.versionRects[0] = {150, 156, 292, 184};
    g_app.versionRects[1] = {304, 156, 446, 184};
    g_app.versionRects[2] = {458, 156, 600, 184};
    drawPill(dc, g_app.versionRects[0], L"Minecraft 1.8", g_app.selectedVersion == 0, true);
    drawPill(dc, g_app.versionRects[1], L"Minecraft 1.20.1", g_app.selectedVersion == 1, false);
    drawPill(dc, g_app.versionRects[2], L"Minecraft 26.1", g_app.selectedVersion == 2, false);

    RECT section = {22, 220, 130, 240};
    drawText(dc, L"⌘ MINECRAFT", section, g_app.tinyFont, rgb(132, 140, 154), DT_LEFT | DT_SINGLELINE);
    RECT c0 = {22, 248, 236, 384};
    RECT c1 = {250, 248, 464, 384};
    RECT c2 = {478, 248, 692, 384};
    drawVersionCard(dc, c0, L"Minecraft 1.8.9", L"▸ Available", true, true);
    drawVersionCard(dc, c1, L"Minecraft 1.20.1", L"▸ Not Released", false, false);
    drawVersionCard(dc, c2, L"Minecraft 26.1", L"▸ Not Released", false, false);

    drawRightRail(dc, w, h);

    RECT status = {24, h - 28, w - 84, h - 10};
    drawText(dc, g_app.lastMessage, status, g_app.tinyFont,
             g_app.uiState == STATE_SUCCESS ? rgb(115, 255, 176) :
             g_app.uiState == STATE_FAILED ? rgb(255, 112, 128) : rgb(142, 151, 166),
             DT_LEFT | DT_SINGLELINE);

    if (g_app.uiState == STATE_INJECTING) {
        RECT overlay = {0, 0, w, h};
        HBRUSH b = CreateSolidBrush(rgb(8, 10, 14));
        FillRect(dc, &overlay, b);
        DeleteObject(b);
        RECT loading = {0, h / 2 - 44, w, h / 2 - 12};
        drawText(dc, L"Loading", loading, g_app.titleFont, rgb(231, 243, 252), DT_CENTER | DT_SINGLELINE);
        RECT msg = {0, h / 2 - 8, w, h / 2 + 18};
        drawText(dc, L"Injecting into Minecraft 1.8.9", msg, g_app.smallFont, rgb(144, 166, 190), DT_CENTER | DT_SINGLELINE);
        drawSpinner(dc, w / 2, h / 2 + 54, 22, rgb(119, 218, 255));
    }
}

void paint(HWND hwnd) {
    PAINTSTRUCT ps;
    HDC screen = BeginPaint(hwnd, &ps);
    RECT client;
    GetClientRect(hwnd, &client);
    HDC dc = CreateCompatibleDC(screen);
    HBITMAP bitmap = CreateCompatibleBitmap(screen, client.right, client.bottom);
    HGDIOBJ oldBitmap = SelectObject(dc, bitmap);

    if (g_app.uiState == STATE_BOOT || g_app.uiState == STATE_EXPANDING) {
        drawAbstractBackground(dc, client);
        int centerY = client.bottom / 2;
        RECT title = {0, centerY - 48, client.right, centerY - 8};
        drawText(dc, L"VapuLite", title, g_app.titleFont, rgb(235, 248, 255), DT_CENTER | DT_SINGLELINE);
        RECT sub = {0, centerY - 4, client.right, centerY + 24};
        drawText(dc, g_app.uiState == STATE_EXPANDING ? L"Opening interface" : L"Loading injector",
                 sub, g_app.smallFont, rgb(146, 174, 196), DT_CENTER | DT_SINGLELINE);
        drawSpinner(dc, client.right / 2, centerY + 58, 20, rgb(110, 230, 255));
    } else {
        drawMainUi(dc, client);
    }

    BitBlt(screen, 0, 0, client.right, client.bottom, dc, 0, 0, SRCCOPY);
    SelectObject(dc, oldBitmap);
    DeleteObject(bitmap);
    DeleteDC(dc);
    EndPaint(hwnd, &ps);
}

void startInject(HWND hwnd) {
    if (g_app.uiState == STATE_INJECTING) {
        return;
    }
    if (!resolveDllPath(g_app.dllPath, MAX_PATH)) {
        g_app.uiState = STATE_FAILED;
        setMessage(L"Injection failed: VapuLiteReobf-x64.dll not found");
        InvalidateRect(hwnd, nullptr, FALSE);
        return;
    }
    g_app.uiState = STATE_INJECTING;
    setMessage(L"Searching Minecraft 1.8.9...");
    HANDLE thread = CreateThread(nullptr, 0, injectThread, hwnd, 0, nullptr);
    if (thread) {
        CloseHandle(thread);
    } else {
        g_app.uiState = STATE_FAILED;
        setMessage(L"Injection failed: worker thread was not created");
    }
    InvalidateRect(hwnd, nullptr, FALSE);
}

void centerWindow(HWND hwnd, int w, int h) {
    int sw = GetSystemMetrics(SM_CXSCREEN);
    int sh = GetSystemMetrics(SM_CYSCREEN);
    SetWindowPos(hwnd, nullptr, (sw - w) / 2, (sh - h) / 2, w, h, SWP_NOZORDER | SWP_NOACTIVATE);
}

LRESULT CALLBACK wndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_CREATE:
        g_app.uiState = STATE_BOOT;
        g_app.started = GetTickCount();
        g_app.selectedVersion = 0;
        createFonts();
        setMessage(L"Ready");
        SetTimer(hwnd, kTimerId, 16, nullptr);
        return 0;
    case WM_ERASEBKGND:
        return 1;
    case WM_TIMER:
        g_app.frame++;
        if (g_app.uiState == STATE_BOOT && GetTickCount() - g_app.started > 850) {
            g_app.uiState = STATE_EXPANDING;
            g_app.expandStarted = GetTickCount();
        }
        if (g_app.uiState == STATE_EXPANDING) {
            float t = (GetTickCount() - g_app.expandStarted) / 460.0f;
            if (t > 1.0f) {
                t = 1.0f;
            }
            t = 1.0f - static_cast<float>(std::pow(1.0f - t, 3.0f));
            int w = 430 + static_cast<int>((760 - 430) * t);
            int h = 270 + static_cast<int>((430 - 270) * t);
            centerWindow(hwnd, w, h);
            if (t >= 1.0f) {
                g_app.uiState = STATE_READY;
            }
        }
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    case WM_LBUTTONDOWN: {
        int x = GET_X_LPARAM(lParam);
        int y = GET_Y_LPARAM(lParam);
        g_app.mouseDown = true;
        SetCapture(hwnd);
        if (ptInRect(g_app.lastClose, x, y)) {
            DestroyWindow(hwnd);
            return 0;
        }
        if (g_app.uiState == STATE_READY || g_app.uiState == STATE_SUCCESS || g_app.uiState == STATE_FAILED) {
            if (ptInRect(g_app.lastStart, x, y)) {
                startInject(hwnd);
                return 0;
            }
            if (ptInRect(g_app.versionRects[0], x, y)) {
                g_app.selectedVersion = 0;
                setMessage(L"Minecraft 1.8.9 selected");
                InvalidateRect(hwnd, nullptr, FALSE);
                return 0;
            }
            if (ptInRect(g_app.versionRects[1], x, y) || ptInRect(g_app.versionRects[2], x, y)) {
                setMessage(L"Only Minecraft 1.8.9 is available now");
                InvalidateRect(hwnd, nullptr, FALSE);
                return 0;
            }
        }
        g_app.dragging = y < 64;
        POINT p;
        GetCursorPos(&p);
        RECT rc;
        GetWindowRect(hwnd, &rc);
        g_app.dragStart.x = p.x - rc.left;
        g_app.dragStart.y = p.y - rc.top;
        return 0;
    }
    case WM_MOUSEMOVE:
        if (g_app.dragging && (wParam & MK_LBUTTON)) {
            POINT p;
            GetCursorPos(&p);
            SetWindowPos(hwnd, nullptr, p.x - g_app.dragStart.x, p.y - g_app.dragStart.y, 0, 0,
                         SWP_NOZORDER | SWP_NOSIZE);
            return 0;
        }
        break;
    case WM_LBUTTONUP:
        g_app.mouseDown = false;
        g_app.dragging = false;
        ReleaseCapture();
        return 0;
    case kInjectDone: {
        InjectResult* result = reinterpret_cast<InjectResult*>(lParam);
        g_app.uiState = result && result->ok ? STATE_SUCCESS : STATE_FAILED;
        setMessage(result ? result->message : L"Injection failed: unknown error");
        delete result;
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_PAINT:
        paint(hwnd);
        return 0;
    case WM_DESTROY:
        KillTimer(hwnd, kTimerId);
        DeleteObject(g_app.titleFont);
        DeleteObject(g_app.textFont);
        DeleteObject(g_app.smallFont);
        DeleteObject(g_app.tinyFont);
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

} // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int showCmd) {
    WNDCLASSEXW wc = {};
    wc.cbSize = sizeof(wc);
    wc.hInstance = instance;
    wc.lpfnWndProc = wndProc;
    wc.lpszClassName = kClassName;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hIcon = LoadIcon(nullptr, IDI_APPLICATION);
    RegisterClassExW(&wc);

    HWND hwnd = CreateWindowExW(WS_EX_APPWINDOW, kClassName, L"VapuLite Injector",
                                WS_POPUP,
                                CW_USEDEFAULT, CW_USEDEFAULT, 430, 270,
                                nullptr, nullptr, instance, nullptr);
    if (!hwnd) {
        return 1;
    }
    centerWindow(hwnd, 430, 270);
    ShowWindow(hwnd, showCmd);
    UpdateWindow(hwnd);

    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    return static_cast<int>(msg.wParam);
}
