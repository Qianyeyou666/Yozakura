#include "yozakura_themida_guard.h"
#include "yozakura_themida_markers.h"

extern "C" __declspec(noinline)
int yozakuraThemidaAcceptLogin(unsigned long status, long long code, int tokenValid) {
    volatile int accepted = 0;
    YOZAKURA_AUTH_VM_START
    accepted = status == 200 && code == 0 && tokenValid == 1 ? 1 : 0;
    YOZAKURA_AUTH_VM_END
    return accepted;
}

extern "C" __declspec(noinline)
int yozakuraThemidaAcceptHeartbeat(unsigned long status, long long code,
                                   long long sentSequence, long long acknowledgedSequence,
                                   long long clientTime, long long serverTime) {
    volatile int accepted = 0;
    YOZAKURA_AUTH_VM_START
    if (status == 200 && code == 0 && sentSequence == acknowledgedSequence
            && clientTime >= 0 && serverTime >= 0) {
        unsigned long long delta = clientTime >= serverTime
                ? static_cast<unsigned long long>(clientTime - serverTime)
                : static_cast<unsigned long long>(serverTime - clientTime);
        accepted = delta <= 90000ULL ? 1 : 0;
    }
    YOZAKURA_AUTH_VM_END
    return accepted;
}

extern "C" __declspec(noinline)
int yozakuraThemidaAcceptRegistration(int registerResult, int methodCount) {
    volatile int accepted = 0;
    YOZAKURA_AUTH_VM_START
    accepted = registerResult == 0 && methodCount == 8 ? 1 : 0;
    YOZAKURA_AUTH_VM_END
    return accepted;
}
