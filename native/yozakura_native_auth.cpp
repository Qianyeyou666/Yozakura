#include "yozakura_native_auth.h"

#include <windows.h>
#include <winhttp.h>

#include <algorithm>
#include <cctype>
#include <cstdio>
#include <string>
#include <vector>

namespace {

const jlong kRuntimeId = 0x594F5A414B555241LL;
const LONGLONG kVerificationGraceMillis = 6LL * 60LL * 1000LL;
const DWORD kRequestTimeoutMillis = 15000;
const size_t kMaxResponseBytes = 64 * 1024;

SRWLOCK stateLock = SRWLOCK_INIT;
volatile LONG verified = 0;
volatile LONG heartbeatStarted = 0;
volatile LONG64 heartbeatSequence = 0;
volatile LONG64 lastVerifiedMillis = 0;
HANDLE stopEvent = nullptr;
std::string serviceBaseUrl;
std::string sessionToken;
std::string verifiedUsername;
std::string verifiedRole;
std::string verifiedExpiry;
std::string clientBuild;
std::string clientFingerprint;
std::string machineFingerprint;

struct HttpHandle {
    HINTERNET value;

    explicit HttpHandle(HINTERNET handle = nullptr) : value(handle) {
    }

    ~HttpHandle() {
        if (value) {
            WinHttpCloseHandle(value);
        }
    }

    operator HINTERNET() const {
        return value;
    }
};

struct ParsedUrl {
    bool secure;
    INTERNET_PORT port;
    std::wstring host;
    std::wstring target;
};

struct HttpResponse {
    DWORD status;
    std::string body;
};

enum class HeartbeatResult {
    Success,
    TransientFailure,
    Rejected
};

LONGLONG currentTimeMillis() {
    FILETIME fileTime = {};
    GetSystemTimeAsFileTime(&fileTime);
    ULARGE_INTEGER value = {};
    value.LowPart = fileTime.dwLowDateTime;
    value.HighPart = fileTime.dwHighDateTime;
    return static_cast<LONGLONG>(value.QuadPart / 10000ULL - 11644473600000ULL);
}

std::wstring utf8ToWide(const std::string& input) {
    if (input.empty()) {
        return std::wstring();
    }
    int length = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, input.data(),
                                     static_cast<int>(input.size()), nullptr, 0);
    if (length <= 0) {
        return std::wstring();
    }
    std::wstring output(static_cast<size_t>(length), L'\0');
    MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, input.data(),
                        static_cast<int>(input.size()), &output[0], length);
    return output;
}

std::string jniUtf8(JNIEnv* env, jstring value) {
    if (!value) {
        return std::string();
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) {
        return std::string();
    }
    std::string output(chars);
    env->ReleaseStringUTFChars(value, chars);
    return output;
}

std::string jniUtf8(JNIEnv* env, jcharArray value) {
    if (!value) {
        return std::string();
    }
    jsize length = env->GetArrayLength(value);
    if (length <= 0 || length > 256) {
        return std::string();
    }
    std::vector<jchar> chars(static_cast<size_t>(length));
    env->GetCharArrayRegion(value, 0, length, chars.data());
    if (env->ExceptionCheck()) {
        return std::string();
    }
    int bytes = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS,
            reinterpret_cast<const wchar_t*>(chars.data()), length,
            nullptr, 0, nullptr, nullptr);
    if (bytes <= 0) {
        SecureZeroMemory(chars.data(), chars.size() * sizeof(jchar));
        return std::string();
    }
    std::string output(static_cast<size_t>(bytes), '\0');
    WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS,
            reinterpret_cast<const wchar_t*>(chars.data()), length,
            &output[0], bytes, nullptr, nullptr);
    SecureZeroMemory(chars.data(), chars.size() * sizeof(jchar));
    return output;
}

bool equalsIgnoreCase(const std::wstring& left, const wchar_t* right) {
    return _wcsicmp(left.c_str(), right) == 0;
}

bool isLoopbackHost(const std::wstring& host) {
    return equalsIgnoreCase(host, L"localhost")
        || equalsIgnoreCase(host, L"127.0.0.1")
        || equalsIgnoreCase(host, L"::1")
        || equalsIgnoreCase(host, L"[::1]")
        || equalsIgnoreCase(host, L"0:0:0:0:0:0:0:1");
}

bool parseSecureUrl(const std::string& raw, ParsedUrl* parsed) {
    if (!parsed || raw.empty() || raw.size() > 2048) {
        return false;
    }
    std::wstring url = utf8ToWide(raw);
    if (url.empty()) {
        return false;
    }

    URL_COMPONENTS parts = {};
    parts.dwStructSize = sizeof(parts);
    parts.dwSchemeLength = static_cast<DWORD>(-1);
    parts.dwHostNameLength = static_cast<DWORD>(-1);
    parts.dwUrlPathLength = static_cast<DWORD>(-1);
    parts.dwExtraInfoLength = static_cast<DWORD>(-1);
    parts.dwUserNameLength = static_cast<DWORD>(-1);
    parts.dwPasswordLength = static_cast<DWORD>(-1);
    if (!WinHttpCrackUrl(url.c_str(), static_cast<DWORD>(url.size()), 0, &parts)
            || parts.dwHostNameLength == 0 || parts.dwUserNameLength != 0
            || parts.dwPasswordLength != 0) {
        return false;
    }

    parsed->secure = parts.nScheme == INTERNET_SCHEME_HTTPS;
    parsed->host.assign(parts.lpszHostName, parts.dwHostNameLength);
    if (!parsed->secure && (parts.nScheme != INTERNET_SCHEME_HTTP
            || !isLoopbackHost(parsed->host))) {
        return false;
    }
    parsed->port = parts.nPort;
    parsed->target.assign(parts.lpszUrlPath, parts.dwUrlPathLength);
    if (parsed->target.empty()) {
        parsed->target = L"/";
    }
    if (parts.dwExtraInfoLength != 0) {
        parsed->target.append(parts.lpszExtraInfo, parts.dwExtraInfoLength);
    }
    return true;
}

std::string appendApiPath(const std::string& base, const char* path) {
    if (base.empty()) {
        return std::string();
    }
    std::string output(base);
    if (output.back() != '/') {
        output.push_back('/');
    }
    while (*path == '/') {
        ++path;
    }
    output.append(path);
    return output;
}

std::string urlEncode(const std::string& input) {
    static const char hex[] = "0123456789ABCDEF";
    std::string output;
    output.reserve(input.size() * 3);
    for (unsigned char value : input) {
        if ((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9') || value == '-' || value == '_'
                || value == '.' || value == '~') {
            output.push_back(static_cast<char>(value));
        } else {
            output.push_back('%');
            output.push_back(hex[value >> 4]);
            output.push_back(hex[value & 15]);
        }
    }
    return output;
}

void addFormField(std::string* body, const char* name, const std::string& value) {
    if (!body->empty()) {
        body->push_back('&');
    }
    body->append(name);
    body->push_back('=');
    body->append(urlEncode(value));
}

void secureClear(std::string* value) {
    if (value && !value->empty()) {
        SecureZeroMemory(&(*value)[0], value->size());
        value->clear();
    }
}

bool request(const std::string& url, const std::string& body,
             const std::string& token, HttpResponse* response) {
    ParsedUrl parsed = {};
    if (!parseSecureUrl(url, &parsed) || !response || body.size() > 16 * 1024) {
        return false;
    }

    HttpHandle session(WinHttpOpen(L"YozakuraNativeAuth/1.0",
            WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, WINHTTP_NO_PROXY_NAME,
            WINHTTP_NO_PROXY_BYPASS, 0));
    if (!session) {
        return false;
    }
    WinHttpSetTimeouts(session, kRequestTimeoutMillis, kRequestTimeoutMillis,
                       kRequestTimeoutMillis, kRequestTimeoutMillis);

    HttpHandle connection(WinHttpConnect(session, parsed.host.c_str(), parsed.port, 0));
    if (!connection) {
        return false;
    }
    DWORD flags = parsed.secure ? WINHTTP_FLAG_SECURE : 0;
    HttpHandle requestHandle(WinHttpOpenRequest(connection, L"POST", parsed.target.c_str(),
            nullptr, WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, flags));
    if (!requestHandle) {
        return false;
    }
    DWORD disabledFeatures = WINHTTP_DISABLE_REDIRECTS;
    WinHttpSetOption(requestHandle, WINHTTP_OPTION_DISABLE_FEATURE,
                     &disabledFeatures, sizeof(disabledFeatures));

    std::wstring headers = L"Content-Type: application/x-www-form-urlencoded\r\n"
                           L"Accept: application/json\r\n";
    if (!token.empty()) {
        std::wstring wideToken = utf8ToWide(token);
        if (wideToken.empty() || wideToken.find_first_of(L"\r\n") != std::wstring::npos) {
            return false;
        }
        headers.append(L"verify-token: ");
        headers.append(wideToken);
        headers.append(L"\r\n");
    }

    if (!WinHttpSendRequest(requestHandle, headers.c_str(), static_cast<DWORD>(-1),
            body.empty() ? WINHTTP_NO_REQUEST_DATA : const_cast<char*>(body.data()),
            static_cast<DWORD>(body.size()), static_cast<DWORD>(body.size()), 0)
            || !WinHttpReceiveResponse(requestHandle, nullptr)) {
        return false;
    }

    DWORD status = 0;
    DWORD statusSize = sizeof(status);
    if (!WinHttpQueryHeaders(requestHandle, WINHTTP_QUERY_STATUS_CODE
            | WINHTTP_QUERY_FLAG_NUMBER, WINHTTP_HEADER_NAME_BY_INDEX,
            &status, &statusSize, WINHTTP_NO_HEADER_INDEX)) {
        return false;
    }

    std::string responseBody;
    for (;;) {
        DWORD available = 0;
        if (!WinHttpQueryDataAvailable(requestHandle, &available)) {
            return false;
        }
        if (available == 0) {
            break;
        }
        if (responseBody.size() + available > kMaxResponseBytes) {
            return false;
        }
        size_t offset = responseBody.size();
        responseBody.resize(offset + available);
        DWORD read = 0;
        if (!WinHttpReadData(requestHandle, &responseBody[offset], available, &read)) {
            return false;
        }
        responseBody.resize(offset + read);
    }
    response->status = status;
    response->body.swap(responseBody);
    return true;
}

size_t jsonValueStart(const std::string& json, const char* name) {
    std::string key = std::string("\"") + name + "\"";
    size_t position = json.find(key);
    if (position == std::string::npos) {
        return position;
    }
    position = json.find(':', position + key.size());
    if (position == std::string::npos) {
        return position;
    }
    do {
        ++position;
    } while (position < json.size()
            && std::isspace(static_cast<unsigned char>(json[position])));
    return position;
}

bool jsonInteger(const std::string& json, const char* name, LONGLONG* value) {
    size_t position = jsonValueStart(json, name);
    if (position == std::string::npos || position >= json.size()) {
        return false;
    }
    size_t end = position;
    if (json[end] == '-') {
        ++end;
    }
    size_t digits = end;
    while (end < json.size() && std::isdigit(static_cast<unsigned char>(json[end]))) {
        ++end;
    }
    if (digits == end) {
        return false;
    }
    std::string number = json.substr(position, end - position);
    char* parsedEnd = nullptr;
    LONGLONG parsed = _strtoi64(number.c_str(), &parsedEnd, 10);
    if (!parsedEnd || *parsedEnd != '\0') {
        return false;
    }
    *value = parsed;
    return true;
}

bool jsonString(const std::string& json, const char* name, std::string* value) {
    size_t position = jsonValueStart(json, name);
    if (position == std::string::npos || position >= json.size() || json[position] != '"') {
        return false;
    }
    ++position;
    std::string output;
    while (position < json.size()) {
        char current = json[position++];
        if (current == '"') {
            value->swap(output);
            return true;
        }
        if (current == '\\') {
            if (position >= json.size()) {
                return false;
            }
            char escaped = json[position++];
            if (escaped == '"' || escaped == '\\' || escaped == '/') {
                output.push_back(escaped);
            } else if (escaped == 'b') {
                output.push_back('\b');
            } else if (escaped == 'f') {
                output.push_back('\f');
            } else if (escaped == 'n') {
                output.push_back('\n');
            } else if (escaped == 'r') {
                output.push_back('\r');
            } else if (escaped == 't') {
                output.push_back('\t');
            } else {
                return false;
            }
        } else if (static_cast<unsigned char>(current) < 0x20) {
            return false;
        } else {
            output.push_back(current);
        }
        if (output.size() > 4096) {
            return false;
        }
    }
    return false;
}

void clearSession() {
    AcquireSRWLockExclusive(&stateLock);
    secureClear(&sessionToken);
    verifiedUsername.clear();
    verifiedRole.clear();
    verifiedExpiry.clear();
    serviceBaseUrl.clear();
    clientBuild.clear();
    clientFingerprint.clear();
    machineFingerprint.clear();
    InterlockedExchange(&verified, 0);
    InterlockedExchange64(&heartbeatSequence, 0);
    InterlockedExchange64(&lastVerifiedMillis, 0);
    ReleaseSRWLockExclusive(&stateLock);
}

HeartbeatResult heartbeatOnce() {
    std::string base;
    std::string token;
    std::string build;
    std::string fingerprint;
    std::string hardware;
    AcquireSRWLockShared(&stateLock);
    base = serviceBaseUrl;
    token = sessionToken;
    build = clientBuild;
    fingerprint = clientFingerprint;
    hardware = machineFingerprint;
    ReleaseSRWLockShared(&stateLock);
    if (base.empty() || token.empty()) {
        return HeartbeatResult::Rejected;
    }

    LONGLONG sequence = InterlockedIncrement64(&heartbeatSequence);
    LONGLONG now = currentTimeMillis();
    std::string body;
    addFormField(&body, "client_time", std::to_string(now));
    addFormField(&body, "sequence", std::to_string(sequence));
    addFormField(&body, "build", build);
    addFormField(&body, "fp", fingerprint);
    addFormField(&body, "hw", hardware);

    HttpResponse response = {};
    if (!request(appendApiPath(base, "api/v2/verify/heartbeat"), body, token, &response)) {
        return HeartbeatResult::TransientFailure;
    }
    LONGLONG code = -1;
    if (!jsonInteger(response.body, "code", &code)) {
        return HeartbeatResult::Rejected;
    }
    if (response.status != 200 || code != 0) {
        return HeartbeatResult::Rejected;
    }
    LONGLONG serverTime = 0;
    LONGLONG acknowledged = -1;
    if (!jsonInteger(response.body, "server_time", &serverTime)
            || !jsonInteger(response.body, "sequence", &acknowledged)
            || acknowledged != sequence || std::llabs(now - serverTime) > 90000LL) {
        return HeartbeatResult::Rejected;
    }
    InterlockedExchange64(&lastVerifiedMillis, currentTimeMillis());
    InterlockedExchange(&verified, 1);
    return HeartbeatResult::Success;
}

DWORD WINAPI heartbeatThread(LPVOID) {
    for (;;) {
        DWORD wait = WaitForSingleObject(stopEvent, 60 * 1000);
        if (wait != WAIT_TIMEOUT) {
            break;
        }
        HeartbeatResult result = heartbeatOnce();
        if (result == HeartbeatResult::Rejected) {
            clearSession();
            break;
        }
    }
    InterlockedExchange(&heartbeatStarted, 0);
    return 0;
}

bool ensureHeartbeatThread() {
    if (InterlockedCompareExchange(&heartbeatStarted, 1, 0) != 0) {
        return true;
    }
    stopEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!stopEvent) {
        InterlockedExchange(&heartbeatStarted, 0);
        return false;
    }
    HANDLE thread = CreateThread(nullptr, 0, heartbeatThread, nullptr, 0, nullptr);
    if (thread) {
        CloseHandle(thread);
        return true;
    } else {
        CloseHandle(stopEvent);
        stopEvent = nullptr;
        InterlockedExchange(&heartbeatStarted, 0);
        return false;
    }
}

jlong JNICALL nativeRuntimeId(JNIEnv*, jclass) {
    return kRuntimeId;
}

jint JNICALL nativeLogin(JNIEnv* env, jclass, jstring baseValue, jstring usernameValue,
                         jcharArray passwordValue, jstring buildValue,
                         jstring fingerprintValue, jstring hardwareValue) {
    std::string base = jniUtf8(env, baseValue);
    std::string username = jniUtf8(env, usernameValue);
    std::string password = jniUtf8(env, passwordValue);
    std::string build = jniUtf8(env, buildValue);
    std::string fingerprint = jniUtf8(env, fingerprintValue);
    std::string hardware = jniUtf8(env, hardwareValue);
    ParsedUrl parsed = {};
    if (!parseSecureUrl(base, &parsed) || username.size() < 3 || username.size() > 64
            || password.size() < 1 || password.size() > 256 || build.empty()
            || fingerprint.empty() || hardware.empty()) {
        secureClear(&password);
        return -1;
    }

    clearSession();
    std::string body;
    addFormField(&body, "username", username);
    addFormField(&body, "password", password);
    addFormField(&body, "software_id", "183");
    addFormField(&body, "e", "true");
    addFormField(&body, "build", build);
    addFormField(&body, "fp", fingerprint);
    addFormField(&body, "hw", hardware);
    secureClear(&password);

    HttpResponse response = {};
    if (!request(appendApiPath(base, "api/v2/verify/login"), body, std::string(), &response)) {
        secureClear(&body);
        return -1;
    }
    secureClear(&body);
    LONGLONG code = -1;
    if (!jsonInteger(response.body, "code", &code)) {
        secureClear(&response.body);
        return -1;
    }
    if (response.status != 200 || code != 0) {
        secureClear(&response.body);
        return static_cast<jint>(code);
    }
    std::string token;
    std::string role;
    std::string expiry;
    bool validEntity = jsonString(response.body, "jwt", &token)
            && token.size() >= 24 && token.size() <= 512
            && jsonString(response.body, "rank_name", &role) && !role.empty() && role.size() <= 64
            && jsonString(response.body, "expired_date", &expiry)
            && expiry.size() >= 16 && expiry.size() <= 32;
    secureClear(&response.body);
    if (!validEntity) {
        secureClear(&token);
        return -1;
    }

    AcquireSRWLockExclusive(&stateLock);
    serviceBaseUrl = base;
    sessionToken = token;
    verifiedUsername = username;
    verifiedRole = role;
    verifiedExpiry = expiry;
    clientBuild = build;
    clientFingerprint = fingerprint;
    machineFingerprint = hardware;
    InterlockedExchange64(&heartbeatSequence, 0);
    ReleaseSRWLockExclusive(&stateLock);
    secureClear(&token);

    if (heartbeatOnce() != HeartbeatResult::Success) {
        clearSession();
        return -1;
    }
    if (!ensureHeartbeatThread()) {
        clearSession();
        return -1;
    }
    return 0;
}

jboolean JNICALL nativeIsVerified(JNIEnv*, jclass) {
    if (InterlockedCompareExchange(&verified, 0, 0) == 0) {
        return JNI_FALSE;
    }
    LONGLONG last = InterlockedCompareExchange64(&lastVerifiedMillis, 0, 0);
    if (last <= 0 || currentTimeMillis() - last > kVerificationGraceMillis) {
        InterlockedExchange(&verified, 0);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

jstring newJniString(JNIEnv* env, const std::string& value) {
    std::wstring wide = utf8ToWide(value);
    if (wide.empty() && !value.empty()) {
        return nullptr;
    }
    return env->NewString(reinterpret_cast<const jchar*>(wide.data()),
                          static_cast<jsize>(wide.size()));
}

jstring JNICALL nativeUsername(JNIEnv* env, jclass) {
    std::string value;
    AcquireSRWLockShared(&stateLock);
    value = verifiedUsername;
    ReleaseSRWLockShared(&stateLock);
    return newJniString(env, value);
}

jstring JNICALL nativeRole(JNIEnv* env, jclass) {
    std::string value;
    AcquireSRWLockShared(&stateLock);
    value = verifiedRole;
    ReleaseSRWLockShared(&stateLock);
    return newJniString(env, value);
}

jstring JNICALL nativeExpiry(JNIEnv* env, jclass) {
    std::string value;
    AcquireSRWLockShared(&stateLock);
    value = verifiedExpiry;
    ReleaseSRWLockShared(&stateLock);
    return newJniString(env, value);
}

} // namespace

bool registerYozakuraNativeAuth(JNIEnv* env, jobject loader) {
    if (!env || !loader) {
        return false;
    }
    jclass loaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(loaderClass, "loadClass",
                                           "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = env->NewStringUTF("gq.yozakura.auth.NativeAuthBridge");
    jclass bridge = static_cast<jclass>(env->CallObjectMethod(loader, loadClass, name));
    if (env->ExceptionCheck() || !bridge) {
        env->ExceptionClear();
        return false;
    }

    JNINativeMethod methods[] = {
        { const_cast<char*>("runtimeId0"), const_cast<char*>("()J"),
          reinterpret_cast<void*>(&nativeRuntimeId) },
        { const_cast<char*>("login0"),
          const_cast<char*>("(Ljava/lang/String;Ljava/lang/String;[CLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)I"),
          reinterpret_cast<void*>(&nativeLogin) },
        { const_cast<char*>("isVerified0"), const_cast<char*>("()Z"),
          reinterpret_cast<void*>(&nativeIsVerified) },
        { const_cast<char*>("username0"), const_cast<char*>("()Ljava/lang/String;"),
          reinterpret_cast<void*>(&nativeUsername) },
        { const_cast<char*>("role0"), const_cast<char*>("()Ljava/lang/String;"),
          reinterpret_cast<void*>(&nativeRole) },
        { const_cast<char*>("expiry0"), const_cast<char*>("()Ljava/lang/String;"),
          reinterpret_cast<void*>(&nativeExpiry) }
    };
    return env->RegisterNatives(bridge, methods,
            static_cast<jint>(sizeof(methods) / sizeof(methods[0]))) == JNI_OK;
}

void signalYozakuraNativeAuthShutdown() {
    if (stopEvent) {
        SetEvent(stopEvent);
    }
}
