#include <windows.h>

#include <cwchar>
#include <cstdio>

int wmain(int argc, wchar_t** argv) {
    if (argc != 3) {
        fwprintf(stderr, L"Usage: YozakuraInjector.exe <pid> <dll-path>\n");
        return 2;
    }

    DWORD pid = wcstoul(argv[1], nullptr, 10);
    wchar_t fullPath[MAX_PATH] = {};
    if (!GetFullPathNameW(argv[2], MAX_PATH, fullPath, nullptr)) {
        fwprintf(stderr, L"GetFullPathNameW failed: %lu\n", GetLastError());
        return 1;
    }

    HANDLE process = OpenProcess(
        PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION | PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ,
        FALSE,
        pid);
    if (!process) {
        fwprintf(stderr, L"OpenProcess failed: %lu\n", GetLastError());
        return 1;
    }

    SIZE_T bytes = (wcslen(fullPath) + 1) * sizeof(wchar_t);
    LPVOID remotePath = VirtualAllocEx(process, nullptr, bytes, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remotePath) {
        fwprintf(stderr, L"VirtualAllocEx failed: %lu\n", GetLastError());
        CloseHandle(process);
        return 1;
    }

    if (!WriteProcessMemory(process, remotePath, fullPath, bytes, nullptr)) {
        fwprintf(stderr, L"WriteProcessMemory failed: %lu\n", GetLastError());
        VirtualFreeEx(process, remotePath, 0, MEM_RELEASE);
        CloseHandle(process);
        return 1;
    }

    HMODULE kernel32 = GetModuleHandleW(L"kernel32.dll");
    LPTHREAD_START_ROUTINE loadLibrary = reinterpret_cast<LPTHREAD_START_ROUTINE>(
        GetProcAddress(kernel32, "LoadLibraryW"));

    HANDLE thread = CreateRemoteThread(process, nullptr, 0, loadLibrary, remotePath, 0, nullptr);
    if (!thread) {
        fwprintf(stderr, L"CreateRemoteThread failed: %lu\n", GetLastError());
        VirtualFreeEx(process, remotePath, 0, MEM_RELEASE);
        CloseHandle(process);
        return 1;
    }

    WaitForSingleObject(thread, 10000);
    DWORD exitCode = 0;
    GetExitCodeThread(thread, &exitCode);

    CloseHandle(thread);
    VirtualFreeEx(process, remotePath, 0, MEM_RELEASE);
    CloseHandle(process);

    if (exitCode == 0) {
        fwprintf(stderr, L"LoadLibraryW failed in target process.\n");
        return 1;
    }

    wprintf(L"Injected %s into pid %lu\n", fullPath, pid);
    return 0;
}
