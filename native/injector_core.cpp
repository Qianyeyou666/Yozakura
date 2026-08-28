#include "injector_core.h"

#include <tlhelp32.h>

#include <cwchar>
#include <cwctype>
#include <string>

namespace yozakura::injector {
namespace {

constexpr int kRejectedScore = 1000;

class Handle final {
public:
    explicit Handle(HANDLE value = nullptr) : value_(value) {
    }

    ~Handle() {
        reset();
    }

    Handle(const Handle&) = delete;
    Handle& operator=(const Handle&) = delete;

    HANDLE get() const {
        return value_;
    }

    explicit operator bool() const {
        return value_ && value_ != INVALID_HANDLE_VALUE;
    }

    void reset(HANDLE value = nullptr) {
        if (value_ && value_ != INVALID_HANDLE_VALUE) {
            CloseHandle(value_);
        }
        value_ = value;
    }

private:
    HANDLE value_;
};

std::wstring win32Error(const wchar_t* operation, DWORD error) {
    return std::wstring(operation) + L" failed (" + std::to_wstring(error) + L")";
}

std::wstring fullPath(const wchar_t* path) {
    if (!path || !path[0]) {
        return L"";
    }

    DWORD required = GetFullPathNameW(path, 0, nullptr, nullptr);
    if (required == 0) {
        return L"";
    }

    std::wstring value(required, L'\0');
    DWORD written = GetFullPathNameW(path, required, &value[0], nullptr);
    if (written == 0 || written >= required) {
        return L"";
    }
    value.resize(written);
    return value;
}

const wchar_t* fileNamePart(const wchar_t* path) {
    const wchar_t* slash = path ? wcsrchr(path, L'\\') : nullptr;
    const wchar_t* forwardSlash = path ? wcsrchr(path, L'/') : nullptr;
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

    const size_t moduleLength = wcslen(moduleName);
    if (moduleLength < 4 || _wcsicmp(moduleName + moduleLength - 4, L".dll") != 0) {
        return false;
    }

    const wchar_t* prefixes[] = {L"YozakuraLoader", L"YozakuraReobf"};
    for (const wchar_t* prefix : prefixes) {
        const size_t prefixLength = wcslen(prefix);
        if (moduleLength >= prefixLength + 4
            && _wcsnicmp(moduleName, prefix, prefixLength) == 0) {
            return true;
        }
    }
    return false;
}

std::wstring lowerText(const wchar_t* text) {
    std::wstring value = text ? text : L"";
    for (wchar_t& character : value) {
        character = static_cast<wchar_t>(towlower(character));
    }
    return value;
}

bool containsText(const std::wstring& text, const wchar_t* needle) {
    return needle && text.find(needle) != std::wstring::npos;
}

bool containsAny(const std::wstring& first, const std::wstring& second, const wchar_t* needle) {
    return containsText(first, needle) || containsText(second, needle);
}

bool isIgnoredLauncher(const std::wstring& title, const std::wstring& commandLine) {
    return containsText(title, L"home - lunar client")
        || containsText(title, L"hello minecraft! launcher")
        || containsText(title, L"badlion chat")
        || containsText(commandLine, L"org.gradle.launcher.daemon")
        || containsText(commandLine, L"-jar \"hmcl")
        || containsText(commandLine, L"-jar hmcl");
}

std::wstring readProcessCommandLine(DWORD pid) {
    using NtStatus = LONG;
    using NtQueryInformationProcessFn = NtStatus (NTAPI*)(HANDLE, ULONG, PVOID, ULONG, PULONG);

    struct ProcessBasicInformationLite {
        PVOID reserved1;
        PVOID pebBaseAddress;
        PVOID reserved2[2];
        ULONG_PTR uniqueProcessId;
        PVOID reserved3;
    };

    struct RemoteUnicodeString {
        USHORT length;
        USHORT maximumLength;
        PWSTR buffer;
    };

    HMODULE ntdll = GetModuleHandleW(L"ntdll.dll");
    NtQueryInformationProcessFn queryInformationProcess = ntdll
        ? reinterpret_cast<NtQueryInformationProcessFn>(GetProcAddress(ntdll, "NtQueryInformationProcess"))
        : nullptr;
    if (!queryInformationProcess) {
        return L"";
    }

    Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pid));
    if (!process) {
        return L"";
    }

    ProcessBasicInformationLite basicInfo = {};
    if (queryInformationProcess(process.get(), 0, &basicInfo, sizeof(basicInfo), nullptr) < 0
        || !basicInfo.pebBaseAddress) {
        return L"";
    }

#if defined(_WIN64)
    constexpr SIZE_T kProcessParametersOffset = 0x20;
    constexpr SIZE_T kCommandLineOffset = 0x70;
#else
    constexpr SIZE_T kProcessParametersOffset = 0x10;
    constexpr SIZE_T kCommandLineOffset = 0x40;
#endif

    BYTE* processParameters = nullptr;
    if (!ReadProcessMemory(
            process.get(),
            reinterpret_cast<BYTE*>(basicInfo.pebBaseAddress) + kProcessParametersOffset,
            &processParameters,
            sizeof(processParameters),
            nullptr)
        || !processParameters) {
        return L"";
    }

    RemoteUnicodeString commandLine = {};
    if (!ReadProcessMemory(
            process.get(),
            processParameters + kCommandLineOffset,
            &commandLine,
            sizeof(commandLine),
            nullptr)
        || !commandLine.buffer
        || commandLine.length == 0
        || commandLine.length >= 65534) {
        return L"";
    }

    std::wstring result(commandLine.length / sizeof(wchar_t), L'\0');
    if (!ReadProcessMemory(
            process.get(),
            commandLine.buffer,
            &result[0],
            commandLine.length,
            nullptr)) {
        return L"";
    }
    return result;
}

bool isJavaProcess(DWORD pid) {
    Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid));
    if (!process) {
        return true;
    }

    wchar_t imagePath[MAX_PATH] = {};
    DWORD imagePathChars = MAX_PATH;
    if (!QueryFullProcessImageNameW(process.get(), 0, imagePath, &imagePathChars)) {
        return true;
    }

    const wchar_t* fileName = fileNamePart(imagePath);
    return fileName
        && (_wcsicmp(fileName, L"java.exe") == 0 || _wcsicmp(fileName, L"javaw.exe") == 0);
}

void considerTarget(TargetProfile profile, DWORD pid, const wchar_t* title, TargetProcess& best) {
    const std::wstring commandLine = readProcessCommandLine(pid);
    const int score = targetScore(profile, title, commandLine.c_str());
    if (score >= kRejectedScore) {
        return;
    }

    const bool currentHasTitle = !best.title.empty();
    const bool nextHasTitle = title && title[0];
    if (best.pid != 0 && (score > best.score || (score == best.score && currentHasTitle && !nextHasTitle))) {
        return;
    }

    best.pid = pid;
    best.score = score;
    best.commandLine = commandLine;
    if (nextHasTitle) {
        best.title = title;
    } else {
        best.title = std::wstring(targetProfileName(profile)) + L" Java PID " + std::to_wstring(pid);
    }
}

struct FindWindowContext {
    TargetProfile profile;
    TargetProcess* best;
};

BOOL CALLBACK enumWindowsProc(HWND window, LPARAM parameter) {
    if (!IsWindowVisible(window)) {
        return TRUE;
    }

    wchar_t title[256] = {};
    GetWindowTextW(window, title, 256);
    if (!title[0]) {
        return TRUE;
    }

    DWORD pid = 0;
    GetWindowThreadProcessId(window, &pid);
    if (pid == 0 || !isJavaProcess(pid)) {
        return TRUE;
    }

    FindWindowContext* context = reinterpret_cast<FindWindowContext*>(parameter);
    considerTarget(context->profile, pid, title, *context->best);
    return context->best->score == 0 ? FALSE : TRUE;
}

void scanJavaProcesses(TargetProfile profile, TargetProcess& best) {
    Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0));
    if (!snapshot) {
        return;
    }

    PROCESSENTRY32W entry = {};
    entry.dwSize = sizeof(entry);
    if (!Process32FirstW(snapshot.get(), &entry)) {
        return;
    }

    do {
        if (_wcsicmp(entry.szExeFile, L"java.exe") == 0
            || _wcsicmp(entry.szExeFile, L"javaw.exe") == 0) {
            considerTarget(profile, entry.th32ProcessID, L"", best);
        }
    } while (Process32NextW(snapshot.get(), &entry));
}

} // namespace

const wchar_t* targetProfileName(TargetProfile profile) {
    switch (profile) {
        case TargetProfile::Forge1201:
            return L"Forge 1.20.1";
        case TargetProfile::Vanilla189:
            return L"Vanilla 1.8.9";
        case TargetProfile::Lunar189:
            return L"Lunar 1.8.9";
        case TargetProfile::Forge189:
        default:
            return L"Forge 1.8.9";
    }
}

const char* targetProfileNameUtf8(TargetProfile profile) {
    switch (profile) {
        case TargetProfile::Forge1201:
            return "Forge 1.20.1";
        case TargetProfile::Vanilla189:
            return "Vanilla 1.8.9";
        case TargetProfile::Lunar189:
            return "Lunar 1.8.9";
        case TargetProfile::Forge189:
        default:
            return "Forge 1.8.9";
    }
}

int targetScore(TargetProfile profile, const wchar_t* titleRaw, const wchar_t* commandLineRaw) {
    const std::wstring title = lowerText(titleRaw);
    const std::wstring commandLine = lowerText(commandLineRaw);
    if (isIgnoredLauncher(title, commandLine)) {
        return kRejectedScore;
    }

    const bool titleMinecraft = containsText(title, L"minecraft");
    const bool titleVersion189 = containsText(title, L"1.8.9");
    const bool commandVersion189 = containsText(commandLine, L"--version 1.8.9")
        || containsText(commandLine, L"versions\\1.8.9")
        || containsText(commandLine, L"versions/1.8.9")
        || containsText(commandLine, L" 1.8.9");
    const bool version189 = titleVersion189 || commandVersion189;
    const bool titleVersion1201 = containsText(title, L"1.20.1");
    const bool commandVersion1201 = containsText(commandLine, L"--version 1.20.1")
        || containsText(commandLine, L"versions\\1.20.1")
        || containsText(commandLine, L"versions/1.20.1")
        || containsText(commandLine, L" 1.20.1");
    const bool version1201 = titleVersion1201 || commandVersion1201;
    const bool lunar = containsAny(title, commandLine, L"lunar")
        || containsText(commandLine, L".lunarclient")
        || containsText(commandLine, L"moonsworth")
        || containsText(commandLine, L"com.moonsworth.lunar.genesis")
        || containsText(commandLine, L"ichor.");
    const bool badlion = containsAny(title, commandLine, L"badlion");
    const bool forge = containsText(commandLine, L"net.minecraft.launchwrapper.launch")
        || containsText(commandLine, L"net.minecraftforge")
        || containsText(commandLine, L"--tweakclass cpw.mods.fml")
        || containsText(commandLine, L"--tweakclass net.minecraftforge")
        || containsText(commandLine, L"fmltweaker")
        || containsText(commandLine, L"cpw.mods.bootstraplauncher")
        || containsText(commandLine, L"modlauncher")
        || containsText(commandLine, L"forgeclient");
    const bool vanillaMain = containsText(commandLine, L"net.minecraft.client.main.main");

    if (profile == TargetProfile::Lunar189) {
        if (!lunar || badlion) {
            return kRejectedScore;
        }
        if (titleMinecraft && version189) {
            return 0;
        }
        return version189 ? 1 : 2;
    }

    if (lunar || badlion) {
        return kRejectedScore;
    }

    if (profile == TargetProfile::Vanilla189) {
        if (forge) {
            return kRejectedScore;
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
        return titleMinecraft && commandLine.empty() ? 5 : kRejectedScore;
    }

    if (profile == TargetProfile::Forge1201) {
        if (forge && version1201) {
            return 0;
        }
        if (forge && containsText(commandLine, L"1.20.1")) {
            return 1;
        }
        return titleMinecraft && version1201 && !vanillaMain ? 4 : kRejectedScore;
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
    return titleMinecraft && commandLine.empty() ? 6 : kRejectedScore;
}

int automaticTargetConfidence(TargetProfile profile, int profileScore) {
    if (profileScore >= kRejectedScore) {
        return kRejectedScore;
    }

    int profilePriority = 0;
    switch (profile) {
        case TargetProfile::Lunar189:
            profilePriority = 0;
            break;
        case TargetProfile::Forge1201:
            profilePriority = 1;
            break;
        case TargetProfile::Vanilla189:
            profilePriority = 2;
            break;
        case TargetProfile::Forge189:
        default:
            profilePriority = 3;
            break;
    }
    return profileScore * 10 + profilePriority;
}

TargetProcess findMinecraftTarget(TargetProfile profile) {
    TargetProcess best;
    FindWindowContext context = {profile, &best};
    EnumWindows(enumWindowsProc, reinterpret_cast<LPARAM>(&context));
    if (best.score > 0) {
        scanJavaProcesses(profile, best);
    }
    return best;
}

DetectedTarget chooseBestMinecraftTarget(
    const TargetProcess* candidates,
    const TargetProfile* profiles,
    std::size_t count
) {
    DetectedTarget best;
    for (std::size_t index = 0; index < count; ++index) {
        const TargetProcess& candidate = candidates[index];
        if (candidate.pid == 0) {
            continue;
        }
        const int confidence = automaticTargetConfidence(profiles[index], candidate.score);
        if (confidence >= kRejectedScore) {
            continue;
        }
        if (best.process.pid != 0
            && (confidence > best.confidence
                || (confidence == best.confidence && candidate.pid >= best.process.pid))) {
            continue;
        }
        best.profile = profiles[index];
        best.process = candidate;
        best.confidence = confidence;
    }
    return best;
}

DetectedTarget findBestMinecraftTarget() {
    const TargetProfile profiles[] = {
        TargetProfile::Lunar189,
        TargetProfile::Forge1201,
        TargetProfile::Vanilla189,
        TargetProfile::Forge189
    };
    TargetProcess candidates[sizeof(profiles) / sizeof(profiles[0])];
    for (std::size_t index = 0; index < sizeof(profiles) / sizeof(profiles[0]); ++index) {
        candidates[index] = findMinecraftTarget(profiles[index]);
    }
    return chooseBestMinecraftTarget(
        candidates,
        profiles,
        sizeof(profiles) / sizeof(profiles[0])
    );
}

LoadedModuleState inspectLoadedYozakuraModule(
    DWORD pid,
    const wchar_t* dllPath,
    std::wstring& loadedPath
) {
    Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid));
    if (!snapshot) {
        return LoadedModuleState::InspectionFailed;
    }

    const std::wstring requestedPath = fullPath(dllPath);
    if (requestedPath.empty()) {
        return LoadedModuleState::InspectionFailed;
    }
    const wchar_t* requestedName = fileNamePart(requestedPath.c_str());

    MODULEENTRY32W module = {};
    module.dwSize = sizeof(module);
    LoadedModuleState state = LoadedModuleState::NotLoaded;
    if (Module32FirstW(snapshot.get(), &module)) {
        while (true) {
            if (_wcsicmp(module.szExePath, requestedPath.c_str()) == 0
                || isYozakuraLoaderName(module.szModule, requestedName)) {
                loadedPath = module.szExePath;
                state = LoadedModuleState::Loaded;
                break;
            }
            if (!Module32NextW(snapshot.get(), &module)) {
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
    return state;
}

InjectionResult injectLibrary(DWORD pid, const wchar_t* dllPath, DWORD timeoutMillis) {
    InjectionResult result;
    const std::wstring requestedPath = fullPath(dllPath);
    if (pid == 0) {
        result.message = L"Target process id is invalid";
        return result;
    }
    if (requestedPath.empty()) {
        result.message = L"DLL path resolve failed";
        return result;
    }

    std::wstring loadedPath;
    const LoadedModuleState moduleState = inspectLoadedYozakuraModule(pid, requestedPath.c_str(), loadedPath);
    if (moduleState == LoadedModuleState::InspectionFailed) {
        result.message = L"Unable to inspect target modules; refusing injection to avoid loading Yozakura twice";
        return result;
    }
    if (moduleState == LoadedModuleState::Loaded) {
        result.message = L"Yozakura is already injected into this process";
        if (!loadedPath.empty()) {
            result.message += L" (" + loadedPath + L")";
        }
        result.message += L". Restart Minecraft before injecting again";
        return result;
    }

    const DWORD access = PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION
        | PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ;
    Handle process(OpenProcess(access, FALSE, pid));
    if (!process) {
        result.message = win32Error(L"OpenProcess", GetLastError()) + L"; try running as administrator";
        return result;
    }

    const SIZE_T bytes = (requestedPath.size() + 1) * sizeof(wchar_t);
    LPVOID remotePath = VirtualAllocEx(
        process.get(),
        nullptr,
        bytes,
        MEM_COMMIT | MEM_RESERVE,
        PAGE_READWRITE
    );
    if (!remotePath) {
        result.message = win32Error(L"VirtualAllocEx", GetLastError());
        return result;
    }

    bool releaseRemotePath = true;
    if (!WriteProcessMemory(process.get(), remotePath, requestedPath.c_str(), bytes, nullptr)) {
        result.message = win32Error(L"WriteProcessMemory", GetLastError());
    } else {
        HMODULE kernel32 = GetModuleHandleW(L"kernel32.dll");
        LPTHREAD_START_ROUTINE loadLibrary = kernel32
            ? reinterpret_cast<LPTHREAD_START_ROUTINE>(GetProcAddress(kernel32, "LoadLibraryW"))
            : nullptr;
        if (!loadLibrary) {
            result.message = win32Error(L"GetProcAddress(LoadLibraryW)", GetLastError());
        } else {
            Handle thread(CreateRemoteThread(
                process.get(),
                nullptr,
                0,
                loadLibrary,
                remotePath,
                0,
                nullptr
            ));
            if (!thread) {
                result.message = win32Error(L"CreateRemoteThread", GetLastError());
            } else {
                DWORD waitResult = WaitForSingleObject(thread.get(), timeoutMillis);
                DWORD waitError = waitResult == WAIT_FAILED ? GetLastError() : ERROR_SUCCESS;
                DWORD exitCode = 0;
                bool exitCodeRead = waitResult == WAIT_OBJECT_0
                    && GetExitCodeThread(thread.get(), &exitCode);
                DWORD exitCodeError = waitResult == WAIT_OBJECT_0 && !exitCodeRead
                    ? GetLastError()
                    : ERROR_SUCCESS;

                if (waitResult != WAIT_OBJECT_0) {
                    releaseRemotePath = false;
                    result.message = waitResult == WAIT_TIMEOUT
                        ? L"Timed out waiting for LoadLibraryW; the remote thread is still active"
                        : win32Error(L"WaitForSingleObject", waitError);
                } else if (!exitCodeRead) {
                    result.message = win32Error(L"GetExitCodeThread", exitCodeError);
                } else if (exitCode == 0) {
                    result.message = L"LoadLibraryW returned NULL";
                } else {
                    result.ok = true;
                    result.remoteModule = exitCode;
                }
            }
        }
    }

    if (releaseRemotePath) {
        VirtualFreeEx(process.get(), remotePath, 0, MEM_RELEASE);
    }
    return result;
}

} // namespace yozakura::injector
