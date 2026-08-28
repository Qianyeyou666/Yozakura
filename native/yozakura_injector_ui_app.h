#pragma once

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

namespace yozakura::injector::ui {

extern const wchar_t* kWindowClassName;
extern const UINT kInjectDoneMessage;

LRESULT WINAPI windowProcedure(HWND window, UINT message, WPARAM wParam, LPARAM lParam);
int runInjectorApplication(HINSTANCE instance);

} // namespace yozakura::injector::ui
