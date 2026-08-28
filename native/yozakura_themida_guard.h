#pragma once

#ifdef __cplusplus
extern "C" {
#endif

int yozakuraThemidaAcceptLogin(unsigned long status, long long code, int tokenValid);
int yozakuraThemidaAcceptHeartbeat(unsigned long status, long long code,
                                   long long sentSequence, long long acknowledgedSequence,
                                   long long clientTime, long long serverTime);
int yozakuraThemidaAcceptRegistration(int registerResult, int methodCount);
int yozakuraThemidaAcceptDebuggerState(int localDebuggerPresent,
                                       int remoteQuerySucceeded,
                                       int remoteDebuggerPresent);

#ifdef __cplusplus
}
#endif
