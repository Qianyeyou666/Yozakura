#include "yozakura_webview2.h"

#include <windows.h>
#include <wrl.h>
#include <wrl/event.h>
#include <atomic>
#include <algorithm>
#include <mutex>
#include <string>

#include <WebView2.h>

using Microsoft::WRL::Callback;
using Microsoft::WRL::ComPtr;

namespace {

ComPtr<ICoreWebView2Environment> environment;
ComPtr<ICoreWebView2Controller> controller;
ComPtr<ICoreWebView2> webview;
HWND minecraftWindow = nullptr;
std::wstring pendingUrl;
std::wstring lastErrorText;
std::mutex stateMutex;
std::atomic<bool> closeRequested(false);
std::atomic<bool> environmentPending(false);
std::atomic<bool> pageReady(false);
std::atomic<bool> visibilityRequested(false);
RECT overlayBounds = {};
bool overlayBoundsInitialized = false;
RECT interactionStartBounds = {};
POINT interactionStartCursor = {};
std::wstring interactionMode;
WNDPROC originalMinecraftWndProc = nullptr;
HWND cursorHookWindow = nullptr;
HCURSOR minecraftCursor = nullptr;
HCURSOR panelCursor = nullptr;
std::atomic<bool> panelCursorVisible(false);

void setError(const std::wstring& message) {
    std::lock_guard<std::mutex> guard(stateMutex);
    lastErrorText = message;
}

HCURSOR createMinecraftCursor() {
    const int size = 20;
    BITMAPV5HEADER header = {};
    header.bV5Size = sizeof(header);
    header.bV5Width = size;
    header.bV5Height = -size;
    header.bV5Planes = 1;
    header.bV5BitCount = 32;
    header.bV5Compression = BI_BITFIELDS;
    header.bV5RedMask = 0x00FF0000;
    header.bV5GreenMask = 0x0000FF00;
    header.bV5BlueMask = 0x000000FF;
    header.bV5AlphaMask = 0xFF000000;
    void* pixels = nullptr;
    HDC screen = GetDC(nullptr);
    HBITMAP color = CreateDIBSection(screen, reinterpret_cast<BITMAPINFO*>(&header), DIB_RGB_COLORS, &pixels, nullptr, 0);
    HDC memory = CreateCompatibleDC(screen);
    HGDIOBJ previous = SelectObject(memory, color);
    POINT arrow[] = {{2,1},{3,15},{6,12},{9,18},{11,17},{8,11},{13,11}};
    HBRUSH brush = CreateSolidBrush(RGB(17,19,24));
    HPEN pen = CreatePen(PS_SOLID, 1, RGB(229,231,235));
    HGDIOBJ oldBrush = SelectObject(memory, brush);
    HGDIOBJ oldPen = SelectObject(memory, pen);
    Polygon(memory, arrow, static_cast<int>(_countof(arrow)));
    POINT accent[] = {{3,3},{4,11},{6,10}};
    HBRUSH accentBrush = CreateSolidBrush(RGB(240,140,175));
    SelectObject(memory, accentBrush);
    SelectObject(memory, GetStockObject(NULL_PEN));
    Polygon(memory, accent, static_cast<int>(_countof(accent)));
    DWORD* data = static_cast<DWORD*>(pixels);
    for (int i = 0; i < size * size; ++i) {
        if ((data[i] & 0x00FFFFFF) != 0) data[i] |= 0xFF000000;
    }
    SelectObject(memory, oldBrush);
    SelectObject(memory, oldPen);
    SelectObject(memory, previous);
    DeleteObject(accentBrush);
    DeleteObject(brush);
    DeleteObject(pen);
    DeleteDC(memory);
    ReleaseDC(nullptr, screen);
    HBITMAP mask = CreateBitmap(size, size, 1, 1, nullptr);
    ICONINFO info = {};
    info.fIcon = FALSE;
    info.xHotspot = 2;
    info.yHotspot = 2;
    info.hbmMask = mask;
    info.hbmColor = color;
    HCURSOR cursor = static_cast<HCURSOR>(CreateIconIndirect(&info));
    DeleteObject(mask);
    DeleteObject(color);
    return cursor;
}

LRESULT CALLBACK minecraftCursorWndProc(HWND window, UINT message, WPARAM wParam, LPARAM lParam) {
    if (message == WM_SETCURSOR && panelCursorVisible.load() && panelCursor) {
        SetCursor(panelCursor);
        return TRUE;
    }
    if (message == WM_SETCURSOR && visibilityRequested.load()) {
        if (!minecraftCursor) minecraftCursor = createMinecraftCursor();
        if (minecraftCursor) {
            SetCursor(minecraftCursor);
            return TRUE;
        }
    }
    return originalMinecraftWndProc
        ? CallWindowProcW(originalMinecraftWndProc, window, message, wParam, lParam)
        : DefWindowProcW(window, message, wParam, lParam);
}

HCURSOR createPanelCursor(const jint* argb, jint width, jint height, jint hotspotX, jint hotspotY) {
    if (!argb || width <= 0 || height <= 0 || hotspotX < 0 || hotspotY < 0
            || hotspotX >= width || hotspotY >= height) {
        return nullptr;
    }
    BITMAPV5HEADER header = {};
    header.bV5Size = sizeof(header);
    header.bV5Width = width;
    header.bV5Height = -height;
    header.bV5Planes = 1;
    header.bV5BitCount = 32;
    header.bV5Compression = BI_BITFIELDS;
    header.bV5RedMask = 0x00FF0000;
    header.bV5GreenMask = 0x0000FF00;
    header.bV5BlueMask = 0x000000FF;
    header.bV5AlphaMask = 0xFF000000;

    HDC screen = GetDC(nullptr);
    void* pixels = nullptr;
    HBITMAP color = screen ? CreateDIBSection(screen, reinterpret_cast<BITMAPINFO*>(&header),
            DIB_RGB_COLORS, &pixels, nullptr, 0) : nullptr;
    if (screen) {
        ReleaseDC(nullptr, screen);
    }
    if (!color || !pixels) {
        if (color) {
            DeleteObject(color);
        }
        return nullptr;
    }
    memcpy(pixels, argb, static_cast<size_t>(width) * static_cast<size_t>(height) * sizeof(jint));
    HBITMAP mask = CreateBitmap(width, height, 1, 1, nullptr);
    if (!mask) {
        DeleteObject(color);
        return nullptr;
    }
    ICONINFO info = {};
    info.fIcon = FALSE;
    info.xHotspot = static_cast<DWORD>(hotspotX);
    info.yHotspot = static_cast<DWORD>(hotspotY);
    info.hbmMask = mask;
    info.hbmColor = color;
    HCURSOR cursor = static_cast<HCURSOR>(CreateIconIndirect(&info));
    DeleteObject(mask);
    DeleteObject(color);
    return cursor;
}

void unhookMinecraftCursor();

void hookMinecraftCursor(HWND window) {
    if (!window) return;
    if (cursorHookWindow == window && originalMinecraftWndProc) return;
    unhookMinecraftCursor();
    SetLastError(ERROR_SUCCESS);
    originalMinecraftWndProc = reinterpret_cast<WNDPROC>(SetWindowLongPtrW(window, GWLP_WNDPROC,
        reinterpret_cast<LONG_PTR>(&minecraftCursorWndProc)));
    if (originalMinecraftWndProc || GetLastError() == ERROR_SUCCESS) {
        cursorHookWindow = window;
    }
}

void unhookMinecraftCursor() {
    if (cursorHookWindow && originalMinecraftWndProc && IsWindow(cursorHookWindow)) {
        SetWindowLongPtrW(cursorHookWindow, GWLP_WNDPROC, reinterpret_cast<LONG_PTR>(originalMinecraftWndProc));
    }
    cursorHookWindow = nullptr;
    originalMinecraftWndProc = nullptr;
}

std::wstring jstringToWide(JNIEnv* env, jstring value) {
    if (!env || !value) {
        return std::wstring();
    }
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (!chars) {
        return std::wstring();
    }
    jsize length = env->GetStringLength(value);
    std::wstring result(reinterpret_cast<const wchar_t*>(chars), static_cast<size_t>(length));
    env->ReleaseStringChars(value, chars);
    return result;
}

BOOL CALLBACK findProcessWindow(HWND window, LPARAM parameter) {
    DWORD processId = 0;
    GetWindowThreadProcessId(window, &processId);
    if (processId != GetCurrentProcessId() || !IsWindowVisible(window) || GetWindow(window, GW_OWNER)) {
        return TRUE;
    }
    wchar_t className[128] = {};
    GetClassNameW(window, className, 127);
    if (wcsstr(className, L"LWJGL") || wcsstr(className, L"GLFW") || wcsstr(className, L"SunAwtFrame")) {
        *reinterpret_cast<HWND*>(parameter) = window;
        return FALSE;
    }
    return TRUE;
}

HWND findMinecraftWindow() {
    HWND result = nullptr;
    EnumWindows(findProcessWindow, reinterpret_cast<LPARAM>(&result));
    if (!result) {
        HWND foreground = GetForegroundWindow();
        DWORD processId = 0;
        GetWindowThreadProcessId(foreground, &processId);
        if (processId == GetCurrentProcessId()) {
            result = foreground;
        }
    }
    return result;
}

void updateBounds() {
    if (!controller || !minecraftWindow) {
        return;
    }
    RECT client = {};
    if (GetClientRect(minecraftWindow, &client)) {
        int clientWidth = client.right - client.left;
        int clientHeight = client.bottom - client.top;
        if (!overlayBoundsInitialized) {
            int width = (std::min)(960, (std::max)(320, clientWidth - 24));
            int height = (std::min)(640, (std::max)(240, clientHeight - 24));
            overlayBounds.left = (clientWidth - width) / 2;
            overlayBounds.top = (clientHeight - height) / 2;
            overlayBounds.right = overlayBounds.left + width;
            overlayBounds.bottom = overlayBounds.top + height;
            overlayBoundsInitialized = true;
        }
        int width = (std::min)(static_cast<int>(overlayBounds.right - overlayBounds.left), clientWidth);
        int height = (std::min)(static_cast<int>(overlayBounds.bottom - overlayBounds.top), clientHeight);
        overlayBounds.left = (std::max)(0, (std::min)(static_cast<int>(overlayBounds.left), clientWidth - width));
        overlayBounds.top = (std::max)(0, (std::min)(static_cast<int>(overlayBounds.top), clientHeight - height));
        overlayBounds.right = overlayBounds.left + width;
        overlayBounds.bottom = overlayBounds.top + height;
        controller->put_Bounds(overlayBounds);
        controller->NotifyParentWindowPositionChanged();
    }
}

void updateOverlayInteraction(int screenX, int screenY) {
    if (!controller || interactionMode.empty() || !minecraftWindow) {
        return;
    }
    POINT cursor = { screenX, screenY };
    int dx = cursor.x - interactionStartCursor.x;
    int dy = cursor.y - interactionStartCursor.y;
    RECT next = interactionStartBounds;
    int minWidth = 680;
    int minHeight = 440;
    if (interactionMode == L"move") {
        OffsetRect(&next, dx, dy);
    } else {
        if (interactionMode.find(L'e') != std::wstring::npos) next.right = (std::max)(next.left + minWidth, interactionStartBounds.right + dx);
        if (interactionMode.find(L's') != std::wstring::npos) next.bottom = (std::max)(next.top + minHeight, interactionStartBounds.bottom + dy);
        if (interactionMode.find(L'w') != std::wstring::npos) next.left = (std::min)(next.right - minWidth, interactionStartBounds.left + dx);
        if (interactionMode.find(L'n') != std::wstring::npos) next.top = (std::min)(next.bottom - minHeight, interactionStartBounds.top + dy);
    }
    overlayBounds = next;
    overlayBoundsInitialized = true;
    updateBounds();
}

void navigatePending() {
    std::wstring url;
    {
        std::lock_guard<std::mutex> guard(stateMutex);
        url = pendingUrl;
    }
    if (webview && !url.empty()) {
        webview->Navigate(url.c_str());
    }
}

HRESULT createController() {
    if (!environment || !minecraftWindow) {
        return E_FAIL;
    }
    HRESULT result = environment->CreateCoreWebView2Controller(
        minecraftWindow,
        Callback<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler>(
            [](HRESULT result, ICoreWebView2Controller* createdController) -> HRESULT {
                environmentPending.store(false);
                if (FAILED(result) || !createdController) {
                    setError(L"CreateCoreWebView2Controller failed");
                    return result;
                }
                controller = createdController;
                controller->get_CoreWebView2(&webview);
                if (!webview) {
                    setError(L"CoreWebView2 instance was not created");
                    return E_FAIL;
                }
                ComPtr<ICoreWebView2Controller2> transparentController;
                if (SUCCEEDED(controller.As(&transparentController)) && transparentController) {
                    COREWEBVIEW2_COLOR transparent = { 0, 0, 0, 0 };
                    transparentController->put_DefaultBackgroundColor(transparent);
                }
                updateBounds();
                controller->put_IsVisible(FALSE);

                EventRegistrationToken token = {};
                webview->add_WebMessageReceived(
                    Callback<ICoreWebView2WebMessageReceivedEventHandler>(
                        [](ICoreWebView2*, ICoreWebView2WebMessageReceivedEventArgs* args) -> HRESULT {
                            LPWSTR message = nullptr;
                            if (args && SUCCEEDED(args->TryGetWebMessageAsString(&message)) && message) {
                                if (_wcsicmp(message, L"close") == 0) {
                                    closeRequested.store(true);
                                } else if (_wcsicmp(message, L"ready") == 0) {
                                    pageReady.store(true);
                                    updateBounds();
                                    if (controller && visibilityRequested.load()) {
                                        controller->put_IsVisible(TRUE);
                                        controller->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);
                                    }
                                } else if (wcsncmp(message, L"window-start:", 13) == 0) {
                                    wchar_t mode[16] = {};
                                    int x = 0, y = 0;
                                    if (swscanf_s(message + 13, L"%15[^:]:%d:%d", mode, static_cast<unsigned>(_countof(mode)), &x, &y) == 3) {
                                        interactionMode = mode;
                                        interactionStartCursor = { x, y };
                                        interactionStartBounds = overlayBounds;
                                    }
                                } else if (wcsncmp(message, L"window-move:", 12) == 0) {
                                    int x = 0, y = 0;
                                    if (swscanf_s(message + 12, L"%d:%d", &x, &y) == 2) {
                                        updateOverlayInteraction(x, y);
                                    }
                                } else if (_wcsicmp(message, L"window-end") == 0) {
                                    interactionMode.clear();
                                }
                                CoTaskMemFree(message);
                            }
                            return S_OK;
                        }).Get(),
                    &token);
                navigatePending();
                return S_OK;
            }).Get());
    if (FAILED(result)) {
        environmentPending.store(false);
    }
    return result;
}

bool beginEnvironment() {
    if (environmentPending.exchange(true)) {
        return true;
    }
    HRESULT initialized = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    if (FAILED(initialized) && initialized != RPC_E_CHANGED_MODE) {
        environmentPending.store(false);
        setError(L"COM initialization failed");
        return false;
    }
    HRESULT result = CreateCoreWebView2EnvironmentWithOptions(
        nullptr,
        nullptr,
        nullptr,
        Callback<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>(
            [](HRESULT status, ICoreWebView2Environment* createdEnvironment) -> HRESULT {
                if (FAILED(status) || !createdEnvironment) {
                    environmentPending.store(false);
                    setError(L"WebView2 Runtime is not installed or failed to initialize");
                    return status;
                }
                environment = createdEnvironment;
                return createController();
            }).Get());
    if (FAILED(result)) {
        environmentPending.store(false);
        setError(L"CreateCoreWebView2EnvironmentWithOptions failed");
        return false;
    }
    return true;
}

jboolean JNICALL nativeShow(JNIEnv* env, jclass, jstring url) {
    std::wstring nextUrl = jstringToWide(env, url);
    if (nextUrl.empty()) {
        setError(L"WebView2 URL is empty");
        return JNI_FALSE;
    }
    HWND nextWindow = findMinecraftWindow();
    if (!nextWindow) {
        setError(L"Minecraft window was not found");
        return JNI_FALSE;
    }
    if (controller && minecraftWindow && nextWindow != minecraftWindow) {
        controller->Close();
        webview.Reset();
        controller.Reset();
        pageReady.store(false);
        overlayBoundsInitialized = false;
    }
    minecraftWindow = nextWindow;
    hookMinecraftCursor(minecraftWindow);
    bool sameUrl = false;
    {
        std::lock_guard<std::mutex> guard(stateMutex);
        sameUrl = pendingUrl == nextUrl;
        pendingUrl = nextUrl;
        lastErrorText.clear();
    }
    closeRequested.store(false);
    visibilityRequested.store(true);
    if (controller && webview) {
        updateBounds();
        if (sameUrl && pageReady.load()) {
            controller->put_IsVisible(TRUE);
            controller->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);
            webview->PostWebMessageAsString(L"open");
            return JNI_TRUE;
        }
        pageReady.store(false);
        controller->put_IsVisible(FALSE);
        navigatePending();
        return JNI_TRUE;
    }
    if (environment) {
        if (environmentPending.exchange(true)) {
            return JNI_TRUE;
        }
        HRESULT result = createController();
        if (FAILED(result)) {
            setError(L"Failed to create WebView2 controller for the current Minecraft window");
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
    return beginEnvironment() ? JNI_TRUE : JNI_FALSE;
}

jboolean JNICALL nativePrewarm(JNIEnv* env, jclass type, jstring url) {
    jboolean result = nativeShow(env, type, url);
    visibilityRequested.store(false);
    if (controller) {
        controller->put_IsVisible(FALSE);
    }
    return result;
}

void JNICALL nativeHide(JNIEnv*, jclass) {
    visibilityRequested.store(false);
    if (controller) {
        controller->put_IsVisible(FALSE);
    }
}

jboolean JNICALL nativeConsumeClose(JNIEnv*, jclass) {
    return closeRequested.exchange(false) ? JNI_TRUE : JNI_FALSE;
}

jboolean JNICALL nativeInstallPanelCursor(JNIEnv* env, jclass, jint width, jint height,
                                           jint hotspotX, jint hotspotY, jintArray pixels) {
    if (!env || !pixels || env->GetArrayLength(pixels) != width * height) {
        return JNI_FALSE;
    }
    HWND window = findMinecraftWindow();
    if (!window) {
        return JNI_FALSE;
    }
    jint* argb = env->GetIntArrayElements(pixels, nullptr);
    if (!argb) {
        return JNI_FALSE;
    }
    HCURSOR replacement = createPanelCursor(argb, width, height, hotspotX, hotspotY);
    env->ReleaseIntArrayElements(pixels, argb, JNI_ABORT);
    if (!replacement) {
        return JNI_FALSE;
    }
    hookMinecraftCursor(window);
    if (!cursorHookWindow) {
        DestroyCursor(replacement);
        return JNI_FALSE;
    }
    HCURSOR previous = panelCursor;
    panelCursor = replacement;
    panelCursorVisible.store(true);
    SetCursor(panelCursor);
    if (previous) {
        DestroyCursor(previous);
    }
    return JNI_TRUE;
}

void JNICALL nativeRestorePanelCursor(JNIEnv*, jclass) {
    panelCursorVisible.store(false);
    if (panelCursor) {
        DestroyCursor(panelCursor);
        panelCursor = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_gq_yozakura_ui_click_yozakura_PanelClickGuiWindowsAlphaCursor_install0(
        JNIEnv* env, jclass type, jint width, jint height, jint hotspotX, jint hotspotY,
        jintArray pixels) {
    return nativeInstallPanelCursor(env, type, width, height, hotspotX, hotspotY, pixels);
}

extern "C" JNIEXPORT void JNICALL
Java_gq_yozakura_ui_click_yozakura_PanelClickGuiWindowsAlphaCursor_restore0(
        JNIEnv* env, jclass type) {
    nativeRestorePanelCursor(env, type);
}

void JNICALL nativeSyncBounds(JNIEnv*, jclass) {
    HWND currentWindow = findMinecraftWindow();
    if (currentWindow && currentWindow != minecraftWindow) {
        minecraftWindow = currentWindow;
        if (controller) {
            controller->Close();
            webview.Reset();
            controller.Reset();
            pageReady.store(false);
            overlayBoundsInitialized = false;
        }
        if (environment && !environmentPending.exchange(true)) {
            HRESULT result = createController();
            if (FAILED(result)) {
                setError(L"Failed to rebind WebView2 to the fullscreen window");
            }
        }
    }
    updateBounds();
    if (pageReady.load() && visibilityRequested.load() && controller) {
        controller->put_IsVisible(TRUE);
    }
}

jstring JNICALL nativeLastError(JNIEnv* env, jclass) {
    std::wstring message;
    {
        std::lock_guard<std::mutex> guard(stateMutex);
        message = lastErrorText;
    }
    if (message.empty()) {
        message = L"WebView2 initialization is pending";
    }
    return env->NewString(reinterpret_cast<const jchar*>(message.data()),
                          static_cast<jsize>(message.size()));
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_gq_yozakura_ui_click_web_WebView2Bridge_show0(JNIEnv* env, jclass type, jstring url) {
    return nativeShow(env, type, url);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_gq_yozakura_ui_click_web_WebView2Bridge_prewarm0(JNIEnv* env, jclass type, jstring url) {
    return nativePrewarm(env, type, url);
}

extern "C" JNIEXPORT void JNICALL
Java_gq_yozakura_ui_click_web_WebView2Bridge_hide0(JNIEnv* env, jclass type) {
    nativeHide(env, type);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_gq_yozakura_ui_click_web_WebView2Bridge_consumeCloseRequest0(JNIEnv* env, jclass type) {
    return nativeConsumeClose(env, type);
}

extern "C" JNIEXPORT void JNICALL
Java_gq_yozakura_ui_click_web_WebView2Bridge_syncBounds0(JNIEnv* env, jclass type) {
    nativeSyncBounds(env, type);
}

extern "C" JNIEXPORT jstring JNICALL
Java_gq_yozakura_ui_click_web_WebView2Bridge_lastError0(JNIEnv* env, jclass type) {
    return nativeLastError(env, type);
}

bool registerYozakuraPanelClickGuiCursor(JNIEnv* env, jobject loader) {
    if (!env || !loader) {
        return false;
    }
    jclass loaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = env->NewStringUTF("gq.yozakura.ui.click.yozakura.PanelClickGuiWindowsAlphaCursor");
    jclass bridge = static_cast<jclass>(env->CallObjectMethod(loader, loadClass, name));
    env->DeleteLocalRef(name);
    if (env->ExceptionCheck() || !bridge) {
        env->ExceptionClear();
        return false;
    }
    JNINativeMethod methods[] = {
        { const_cast<char*>("install0"), const_cast<char*>("(IIII[I)Z"),
          reinterpret_cast<void*>(&nativeInstallPanelCursor) },
        { const_cast<char*>("restore0"), const_cast<char*>("()V"),
          reinterpret_cast<void*>(&nativeRestorePanelCursor) }
    };
    return env->RegisterNatives(bridge, methods,
            static_cast<jint>(sizeof(methods) / sizeof(methods[0]))) == JNI_OK;
}

bool registerYozakuraWebView2(JNIEnv* env, jobject loader) {
    if (!env || !loader) {
        return false;
    }
    jclass loaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = env->NewStringUTF("gq.yozakura.ui.click.web.WebView2Bridge");
    jclass bridge = static_cast<jclass>(env->CallObjectMethod(loader, loadClass, name));
    env->DeleteLocalRef(name);
    if (env->ExceptionCheck() || !bridge) {
        env->ExceptionClear();
        return false;
    }
    JNINativeMethod methods[] = {
        { const_cast<char*>("show0"), const_cast<char*>("(Ljava/lang/String;)Z"), reinterpret_cast<void*>(&nativeShow) },
        { const_cast<char*>("prewarm0"), const_cast<char*>("(Ljava/lang/String;)Z"), reinterpret_cast<void*>(&nativePrewarm) },
        { const_cast<char*>("hide0"), const_cast<char*>("()V"), reinterpret_cast<void*>(&nativeHide) },
        { const_cast<char*>("consumeCloseRequest0"), const_cast<char*>("()Z"), reinterpret_cast<void*>(&nativeConsumeClose) },
        { const_cast<char*>("syncBounds0"), const_cast<char*>("()V"), reinterpret_cast<void*>(&nativeSyncBounds) },
        { const_cast<char*>("lastError0"), const_cast<char*>("()Ljava/lang/String;"), reinterpret_cast<void*>(&nativeLastError) }
    };
    return env->RegisterNatives(bridge, methods, static_cast<jint>(sizeof(methods) / sizeof(methods[0]))) == JNI_OK;
}

void shutdownYozakuraWebView2() {
    panelCursorVisible.store(false);
    if (panelCursor) {
        DestroyCursor(panelCursor);
        panelCursor = nullptr;
    }
    unhookMinecraftCursor();
    if (minecraftCursor) {
        DestroyCursor(minecraftCursor);
        minecraftCursor = nullptr;
    }
    if (controller) {
        controller->Close();
    }
    webview.Reset();
    controller.Reset();
    environment.Reset();
    environmentPending.store(false);
    closeRequested.store(false);
    pageReady.store(false);
    visibilityRequested.store(false);
    overlayBoundsInitialized = false;
}
