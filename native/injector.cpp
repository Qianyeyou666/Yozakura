#include <windows.h>
#include <tlhelp32.h>

#include <cwchar>
#include <cstdio>

enum class LoadedModuleState {
    NotLoaded,
    Loaded,
    InspectionFailed
};

const wchar_t* fileNamePart(const wchar_t* path) {
    const wchar_t* slash = wcsrchr(path, L'\\');
    const wchar_t* forwardSlash = wcsrchr(path, L'/');
    if (!slash || (forwardSlash && forwardSlash > slash)) {
        slash = forwardSlash;
    }
    return slash ? slash + 1 : path;
}

bool isYozakuraLoaderName(const wchar_t* moduleName, const wchar_t* requestedName) {
    if (!moduleName || !requestedName) {
        return false;
    }
    if (_wcsicmp(moduleName, requestedName) == 0) {
        return true;
    }

    size_t moduleLength = wcslen(moduleName);
    if (moduleLength < 4 || _wcsicmp(moduleName + moduleLength - 4, L".dll") != 0) {
        return false;
    }
    const wchar_t* prefixes[] = {L"YozakuraLoader", L"YozakuraReobf"};
    for (const wchar_t* prefix : prefixes) {
        size_t prefixLength = wcslen(prefix);
        if (moduleLength >= prefixLength + 4
            && _wcsnicmp(moduleName, prefix, prefixLength) == 0) {
            return true;
        }
    }
    return false;
}

LoadedModuleState inspectLoadedYozakuraModule(DWORD pid,
                                               const wchar_t* dllPath,
                                               wchar_t* loadedPath,
                                               size_t loadedPathChars) {
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return LoadedModuleState::InspectionFailed;
    }

    const wchar_t* requestedName = fileNamePart(dllPath);
    MODULEENTRY32W module = {};
    module.dwSize = sizeof(module);
    LoadedModuleState state = LoadedModuleState::NotLoaded;
    if (Module32FirstW(snapshot, &module)) {
        while (true) {
            if (_wcsicmp(module.szExePath, dllPath) == 0
                || isYozakuraLoaderName(module.szModule, requestedName)) {
                wcsncpy_s(loadedPath, loadedPathChars, module.szExePath, _TRUNCATE);
                state = LoadedModuleState::Loaded;
                break;
            }
            if (!Module32NextW(snapshot, &module)) {
                DWORD enumerationError = GetLastError();
                if (enumerationError != ERROR_NO_MORE_FILES) {
                    state = LoadedModuleState::InspectionFailed;
                }
                break;
            }
        }
    } else if (GetLastError() != ERROR_NO_MORE_FILES) {
        state = LoadedModuleState::InspectionFailed;
    }

    CloseHandle(snapshot);
    return state;
}

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

    wchar_t loadedPath[MAX_PATH] = {};
    LoadedModuleState moduleState = inspectLoadedYozakuraModule(
        pid, fullPath, loadedPath, MAX_PATH);
    if (moduleState == LoadedModuleState::InspectionFailed) {
        fwprintf(stderr,
                 L"Unable to inspect modules in pid %lu; refusing injection to avoid loading Yozakura twice.\n",
                 pid);
        return 1;
    }
    if (moduleState == LoadedModuleState::Loaded) {
        fwprintf(stderr,
                 L"Yozakura is already injected into pid %lu (%s). Restart Minecraft before injecting again.\n",
                 pid,
                 loadedPath);
        return 3;
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
    if (!loadLibrary) {
        fwprintf(stderr, L"GetProcAddress(LoadLibraryW) failed: %lu\n", GetLastError());
        VirtualFreeEx(process, remotePath, 0, MEM_RELEASE);
        CloseHandle(process);
        return 1;
    }

    HANDLE thread = CreateRemoteThread(process, nullptr, 0, loadLibrary, remotePath, 0, nullptr);
    if (!thread) {
        fwprintf(stderr, L"CreateRemoteThread failed: %lu\n", GetLastError());
        VirtualFreeEx(process, remotePath, 0, MEM_RELEASE);
        CloseHandle(process);
        return 1;
    }

    DWORD waitResult = WaitForSingleObject(thread, 10000);
    DWORD waitError = waitResult == WAIT_FAILED ? GetLastError() : ERROR_SUCCESS;
    DWORD exitCode = 0;
    bool exitCodeRead = waitResult == WAIT_OBJECT_0 && GetExitCodeThread(thread, &exitCode);
    DWORD exitCodeError = waitResult == WAIT_OBJECT_0 && !exitCodeRead
        ? GetLastError()
        : ERROR_SUCCESS;

    CloseHandle(thread);
    if (waitResult == WAIT_OBJECT_0) {
        VirtualFreeEx(process, remotePath, 0, MEM_RELEASE);
    }
    CloseHandle(process);

    if (waitResult != WAIT_OBJECT_0) {
        if (waitResult == WAIT_TIMEOUT) {
            fwprintf(stderr,
                     L"Timed out waiting for LoadLibraryW; remote path memory was retained for the active thread.\n");
        } else {
            fwprintf(stderr, L"WaitForSingleObject failed: %lu\n", waitError);
        }
        return 1;
    }
    if (!exitCodeRead) {
        fwprintf(stderr, L"GetExitCodeThread failed: %lu\n", exitCodeError);
        return 1;
    }

    if (exitCode == 0) {
        fwprintf(stderr, L"LoadLibraryW failed in target process.\n");
        return 1;
    }

    wprintf(L"Injected %s into pid %lu\n", fullPath, pid);
    return 0;
}
