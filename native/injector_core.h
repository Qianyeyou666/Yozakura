#pragma once

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#include <cstddef>
#include <string>

namespace yozakura::injector {

enum class TargetProfile {
    Forge189 = 0,
    Vanilla189 = 1,
    Lunar189 = 2,
    Forge1201 = 3
};

struct TargetProcess {
    DWORD pid = 0;
    std::wstring title;
    std::wstring commandLine;
    int score = 1000;
};

struct DetectedTarget {
    TargetProfile profile = TargetProfile::Forge189;
    TargetProcess process;
    int confidence = 1000;
};

enum class LoadedModuleState {
    NotLoaded,
    Loaded,
    InspectionFailed
};

struct InjectionResult {
    bool ok = false;
    std::wstring message;
    DWORD remoteModule = 0;
};

const wchar_t* targetProfileName(TargetProfile profile);
const char* targetProfileNameUtf8(TargetProfile profile);
int targetScore(TargetProfile profile, const wchar_t* title, const wchar_t* commandLine);
int automaticTargetConfidence(TargetProfile profile, int profileScore);
TargetProcess findMinecraftTarget(TargetProfile profile);
DetectedTarget chooseBestMinecraftTarget(
    const TargetProcess* candidates,
    const TargetProfile* profiles,
    std::size_t count
);
DetectedTarget findBestMinecraftTarget();
LoadedModuleState inspectLoadedYozakuraModule(
    DWORD pid,
    const wchar_t* dllPath,
    std::wstring& loadedPath
);
InjectionResult injectLibrary(DWORD pid, const wchar_t* dllPath, DWORD timeoutMillis = 12000);

} // namespace yozakura::injector
