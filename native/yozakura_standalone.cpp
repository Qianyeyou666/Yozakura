#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>

#include <string>

namespace {

struct Payload {
    int resourceId;
    const wchar_t* fileName;
};

constexpr Payload kPayloads[] = {
    {101, L"YozakuraLoader.dll"},
    {102, L"YozakuraInjector.exe"},
    {103, L"minecraft_cherry_block.png"},
    {104, L"minecraft_furnace_block.png"},
    {105, L"minecraft_grass_block.png"},
    {106, L"yozakura_logo.png"}
};

std::wstring joinPath(const std::wstring& directory, const wchar_t* fileName) {
    if (!directory.empty() && directory.back() == L'\\') {
        return directory + fileName;
    }
    return directory + L"\\" + fileName;
}

bool ensureDirectory(const std::wstring& path) {
    if (CreateDirectoryW(path.c_str(), nullptr)) {
        return true;
    }
    return GetLastError() == ERROR_ALREADY_EXISTS
        && (GetFileAttributesW(path.c_str()) & FILE_ATTRIBUTE_DIRECTORY) != 0;
}

bool createExtractionDirectory(std::wstring& output) {
    wchar_t tempPath[MAX_PATH] = {};
    DWORD length = GetTempPathW(MAX_PATH, tempPath);
    if (length == 0 || length >= MAX_PATH) {
        return false;
    }

    for (unsigned int attempt = 0; attempt < 100; ++attempt) {
        wchar_t directoryName[128] = {};
        swprintf_s(
            directoryName,
            L"YozakuraStandalone-%lu-%llu-%u",
            GetCurrentProcessId(),
            GetTickCount64(),
            attempt
        );
        std::wstring candidate = joinPath(tempPath, directoryName);
        if (CreateDirectoryW(candidate.c_str(), nullptr)) {
            output = candidate;
            return true;
        }
        if (GetLastError() != ERROR_ALREADY_EXISTS) {
            return false;
        }
    }
    return false;
}

bool extractResource(HINSTANCE instance, const Payload& payload, const std::wstring& directory) {
    HRSRC resource = FindResourceW(instance, MAKEINTRESOURCEW(payload.resourceId), RT_RCDATA);
    if (!resource) {
        return false;
    }

    HGLOBAL loaded = LoadResource(instance, resource);
    const void* data = loaded ? LockResource(loaded) : nullptr;
    DWORD size = SizeofResource(instance, resource);
    if (!data || size == 0) {
        return false;
    }

    std::wstring outputPath = joinPath(directory, payload.fileName);
    HANDLE file = CreateFileW(
        outputPath.c_str(),
        GENERIC_WRITE,
        0,
        nullptr,
        CREATE_NEW,
        FILE_ATTRIBUTE_NORMAL,
        nullptr
    );
    if (file == INVALID_HANDLE_VALUE) {
        return false;
    }

    const BYTE* cursor = static_cast<const BYTE*>(data);
    DWORD remaining = size;
    bool ok = true;
    while (remaining > 0) {
        DWORD written = 0;
        if (!WriteFile(file, cursor, remaining, &written, nullptr) || written == 0) {
            ok = false;
            break;
        }
        cursor += written;
        remaining -= written;
    }
    CloseHandle(file);

    if (!ok) {
        DeleteFileW(outputPath.c_str());
    }
    return ok;
}

int extractPayloads(HINSTANCE instance, const std::wstring& directory) {
    if (!ensureDirectory(directory)) {
        return 10;
    }
    for (const Payload& payload : kPayloads) {
        if (!extractResource(instance, payload, directory)) {
            return 11;
        }
    }
    return 0;
}

int launchInjector(const std::wstring& directory) {
    std::wstring injectorPath = joinPath(directory, L"YozakuraInjector.exe");
    STARTUPINFOW startupInfo = {};
    startupInfo.cb = sizeof(startupInfo);
    PROCESS_INFORMATION processInfo = {};

    if (!CreateProcessW(
            injectorPath.c_str(),
            nullptr,
            nullptr,
            nullptr,
            FALSE,
            0,
            nullptr,
            directory.c_str(),
            &startupInfo,
            &processInfo)) {
        return 20;
    }

    WaitForSingleObject(processInfo.hProcess, INFINITE);
    DWORD exitCode = 21;
    GetExitCodeProcess(processInfo.hProcess, &exitCode);
    CloseHandle(processInfo.hThread);
    CloseHandle(processInfo.hProcess);
    return static_cast<int>(exitCode);
}

} // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int) {
    int argumentCount = 0;
    LPWSTR* arguments = CommandLineToArgvW(GetCommandLineW(), &argumentCount);
    bool extractOnly = arguments
        && argumentCount == 3
        && wcscmp(arguments[1], L"--extract-only") == 0;

    std::wstring extractionDirectory;
    if (extractOnly) {
        extractionDirectory = arguments[2];
    } else if (!createExtractionDirectory(extractionDirectory)) {
        if (arguments) {
            LocalFree(arguments);
        }
        MessageBoxW(nullptr, L"无法创建 Yozakura 临时释放目录。", L"Yozakura", MB_OK | MB_ICONERROR);
        return 1;
    }
    if (arguments) {
        LocalFree(arguments);
    }

    int extractResult = extractPayloads(instance, extractionDirectory);
    if (extractResult != 0) {
        if (!extractOnly) {
            MessageBoxW(nullptr, L"无法释放 Yozakura 注入器资源。", L"Yozakura", MB_OK | MB_ICONERROR);
        }
        return extractResult;
    }

    if (extractOnly) {
        return 0;
    }

    int injectorResult = launchInjector(extractionDirectory);
    if (injectorResult == 20) {
        MessageBoxW(nullptr, L"无法启动 YozakuraInjector.exe。", L"Yozakura", MB_OK | MB_ICONERROR);
    }
    return injectorResult;
}
