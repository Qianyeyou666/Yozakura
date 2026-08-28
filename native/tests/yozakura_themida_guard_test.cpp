#include "../yozakura_themida_guard.h"

#include <cassert>

int main() {
    assert(yozakuraThemidaAcceptLogin(200, 0, 1) == 1);
    assert(yozakuraThemidaAcceptLogin(500, 0, 1) == 0);
    assert(yozakuraThemidaAcceptLogin(200, 103, 1) == 0);
    assert(yozakuraThemidaAcceptLogin(200, 0, 0) == 0);

    assert(yozakuraThemidaAcceptHeartbeat(200, 0, 9, 9, 100000, 100050) == 1);
    assert(yozakuraThemidaAcceptHeartbeat(200, 0, 9, 8, 100000, 100050) == 0);
    assert(yozakuraThemidaAcceptHeartbeat(200, 0, 9, 9, 100000, 190001) == 0);

    assert(yozakuraThemidaAcceptRegistration(0, 9) == 1);
    assert(yozakuraThemidaAcceptRegistration(-1, 9) == 0);
    assert(yozakuraThemidaAcceptRegistration(0, 8) == 0);

    assert(yozakuraThemidaAcceptDebuggerState(0, 1, 0) == 1);
    assert(yozakuraThemidaAcceptDebuggerState(1, 1, 0) == 0);
    assert(yozakuraThemidaAcceptDebuggerState(0, 1, 1) == 0);
    assert(yozakuraThemidaAcceptDebuggerState(0, 0, 0) == 1);
    return 0;
}
