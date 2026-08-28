#include "injector_core.h"

#include <cerrno>
#include <climits>
#include <cstdio>
#include <cstdlib>

namespace {

bool parseProcessId(const wchar_t* text, DWORD& pid) {
    if (!text || !text[0] || text[0] == L'-') {
        return false;
    }

    errno = 0;
    wchar_t* end = nullptr;
    const unsigned long value = wcstoul(text, &end, 10);
    if (errno == ERANGE || !end || *end != L'\0' || value == 0 || value > MAXDWORD) {
        return false;
    }

    pid = static_cast<DWORD>(value);
    return true;
}

} // namespace

int wmain(int argc, wchar_t** argv) {
    if (argc != 3) {
        fwprintf(stderr, L"Usage: YozakuraInjectorCli.exe <pid> <dll-path>\n");
        return 2;
    }

    DWORD pid = 0;
    if (!parseProcessId(argv[1], pid)) {
        fwprintf(stderr, L"Invalid process id: %s\n", argv[1]);
        return 2;
    }

    const yozakura::injector::InjectionResult result =
        yozakura::injector::injectLibrary(pid, argv[2], 10000);
    if (!result.ok) {
        fwprintf(stderr, L"Injection failed for pid %lu: %s\n", pid, result.message.c_str());
        return result.message.find(L"already injected") != std::wstring::npos ? 3 : 1;
    }

    wprintf(L"Injected %s into pid %lu (module 0x%08lx)\n", argv[2], pid, result.remoteModule);
    return 0;
}
