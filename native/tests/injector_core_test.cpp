#include "../injector_core.h"

#include <cstdio>
#include <string>

namespace {

bool expectScore(
    const char* label,
    yozakura::injector::TargetProfile profile,
    const wchar_t* title,
    const wchar_t* commandLine,
    int expected
) {
    const int actual = yozakura::injector::targetScore(profile, title, commandLine);
    if (actual == expected) {
        return true;
    }
    std::fprintf(stderr, "%s: expected %d, got %d\n", label, expected, actual);
    return false;
}

} // namespace

int main() {
    using yozakura::injector::DetectedTarget;
    using yozakura::injector::TargetProcess;
    using yozakura::injector::TargetProfile;
    using yozakura::injector::chooseBestMinecraftTarget;

    bool ok = true;
    ok &= std::wstring(yozakura::injector::targetProfileName(TargetProfile::Forge189))
        == L"Forge 1.8.9";
    ok &= std::wstring(yozakura::injector::targetProfileName(TargetProfile::Vanilla189))
        == L"Vanilla 1.8.9";
    ok &= std::wstring(yozakura::injector::targetProfileName(TargetProfile::Lunar189))
        == L"Lunar 1.8.9";
    ok &= std::string(yozakura::injector::targetProfileNameUtf8(TargetProfile::Forge189))
        == "Forge 1.8.9";
    ok &= std::string(yozakura::injector::targetProfileNameUtf8(TargetProfile::Vanilla189))
        == "Vanilla 1.8.9";
    ok &= std::string(yozakura::injector::targetProfileNameUtf8(TargetProfile::Lunar189))
        == "Lunar 1.8.9";
    ok &= expectScore(
        "forge 1.8.9",
        TargetProfile::Forge189,
        L"Minecraft 1.8.9",
        L"javaw net.minecraft.launchwrapper.Launch --version 1.8.9 --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker",
        0
    );
    ok &= expectScore(
        "vanilla 1.8.9",
        TargetProfile::Vanilla189,
        L"Minecraft 1.8.9",
        L"javaw net.minecraft.client.main.Main --version 1.8.9",
        0
    );
    ok &= expectScore(
        "lunar 1.8.9",
        TargetProfile::Lunar189,
        L"Lunar Client - Minecraft 1.8.9",
        L"javaw com.moonsworth.lunar.genesis 1.8.9",
        0
    );
    ok &= expectScore(
        "badlion rejection",
        TargetProfile::Lunar189,
        L"Badlion Minecraft 1.8.9",
        L"javaw badlion lunar 1.8.9",
        1000
    );
    ok &= expectScore(
        "forge 1.20.1",
        TargetProfile::Forge1201,
        L"Minecraft 1.20.1",
        L"javaw cpw.mods.bootstraplauncher modlauncher forgeclient --version 1.20.1",
        0
    );
    ok &= expectScore(
        "launcher rejection",
        TargetProfile::Forge189,
        L"Hello Minecraft! Launcher",
        L"javaw -jar HMCL.jar",
        1000
    );

    TargetProcess candidates[4];
    candidates[0].pid = 410;
    candidates[0].score = 1;
    candidates[1].pid = 220;
    candidates[1].score = 0;
    candidates[2].pid = 330;
    candidates[2].score = 0;
    candidates[3].pid = 440;
    candidates[3].score = 0;
    const TargetProfile profiles[4] = {
        TargetProfile::Forge189,
        TargetProfile::Lunar189,
        TargetProfile::Vanilla189,
        TargetProfile::Forge1201
    };
    const DetectedTarget detected = chooseBestMinecraftTarget(candidates, profiles, 4);
    const bool exactTargetPreferred = detected.process.pid == 220
        && detected.profile == TargetProfile::Lunar189
        && detected.confidence == 0;
    ok &= exactTargetPreferred;
    if (!exactTargetPreferred) {
        std::fprintf(stderr, "automatic target selection must prefer the most specific exact match\n");
    }

    TargetProcess duplicateCandidates[2];
    duplicateCandidates[0].pid = 777;
    duplicateCandidates[0].score = 0;
    duplicateCandidates[1].pid = 777;
    duplicateCandidates[1].score = 0;
    const TargetProfile duplicateProfiles[2] = {
        TargetProfile::Forge189,
        TargetProfile::Vanilla189
    };
    const DetectedTarget duplicate = chooseBestMinecraftTarget(
        duplicateCandidates,
        duplicateProfiles,
        2
    );
    const bool duplicateCollapsed = duplicate.process.pid == 777
        && duplicate.profile == TargetProfile::Vanilla189;
    ok &= duplicateCollapsed;
    if (!duplicateCollapsed) {
        std::fprintf(stderr, "one PID matched by multiple profiles must produce one deterministic target\n");
    }

    if (!ok) {
        return 1;
    }
    std::puts("[OK] injector target scoring contracts passed.");
    return 0;
}
