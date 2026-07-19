#include "yozakura_native_auth.h"
#include "yozakura_string_obfuscation.h"
#include "yozakura_themida_guard.h"

#include <windows.h>
#include <winhttp.h>
#include <wincrypt.h>

#include <algorithm>
#include <cctype>
#include <cstring>
#include <cstdio>
#include <string>
#include <vector>

namespace {

const LONGLONG kVerificationGraceMillis = 6LL * 60LL * 1000LL;
const DWORD kRequestTimeoutMillis = 15000;
const size_t kMaxResponseBytes = 64 * 1024;
SRWLOCK stateLock = SRWLOCK_INIT;
volatile LONG verified = 0;
volatile LONG heartbeatStarted = 0;
volatile LONG lastRequestFailure = 0;
volatile LONG64 heartbeatSequence = 0;
volatile LONG64 lastVerifiedMillis = 0;
LONGLONG sessionServerTimeMillis = 0;
LONGLONG sessionServerMonotonicMillis = 0;
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

LONGLONG monotonicTimeMillis() {
    return static_cast<LONGLONG>(GetTickCount64());
}

#if defined(YOZAKURA_NATIVE_DIAGNOSTICS)
void authDebug(const char* message) {
    OutputDebugStringA("[YozakuraNativeAuth] ");
    OutputDebugStringA(message);
    OutputDebugStringA("\n");

    char tempPath[MAX_PATH] = {};
    if (GetTempPathA(MAX_PATH, tempPath)) {
        std::string logPath = std::string(tempPath) + "JarToDllLoader.log";
        HANDLE file = CreateFileA(logPath.c_str(), FILE_APPEND_DATA, FILE_SHARE_READ,
                                  nullptr, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (file != INVALID_HANDLE_VALUE) {
            DWORD written = 0;
            SYSTEMTIME time = {};
            GetLocalTime(&time);
            char line[1024] = {};
            sprintf_s(line, "[%04u-%02u-%02u %02u:%02u:%02u] native auth: %s\r\n",
                      time.wYear, time.wMonth, time.wDay, time.wHour,
                      time.wMinute, time.wSecond, message);
            WriteFile(file, line, static_cast<DWORD>(std::strlen(line)), &written, nullptr);
            CloseHandle(file);
        }
    }
}

void authDebugCode(const char* message, LONGLONG value) {
    char line[256] = {};
    sprintf_s(line, "%s %lld", message, value);
    authDebug(line);
}
#else
#define authDebug(...) ((void)0)
#define authDebugCode(...) ((void)0)
#endif

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

bool isConfiguredIpEndpoint(const std::wstring& host);

std::string base64Encode(const BYTE* data, DWORD length) {
    if (!data || length == 0) {
        return std::string();
    }
    DWORD outputLength = 0;
    if (!CryptBinaryToStringA(data, length, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF,
                              nullptr, &outputLength)) {
        return std::string();
    }
    std::string output(outputLength, '\0');
    if (!CryptBinaryToStringA(data, length, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF,
                              &output[0], &outputLength)) {
        return std::string();
    }
    output.resize(outputLength);
    return output;
}

std::string hexLower(const BYTE* data, DWORD length) {
    static const char digits[] = "0123456789abcdef";
    std::string output;
    output.reserve(static_cast<size_t>(length) * 2);
    for (DWORD index = 0; index < length; ++index) {
        output.push_back(digits[data[index] >> 4]);
        output.push_back(digits[data[index] & 0x0f]);
    }
    return output;
}

bool appendClassBytes(JNIEnv* env, jobject classLoader, jmethodID getResource,
                      const char* resourceName, std::vector<BYTE>* aggregate) {
    jstring resource = env->NewStringUTF(resourceName);
    jobject stream = env->CallObjectMethod(classLoader, getResource, resource);
    env->DeleteLocalRef(resource);
    if (env->ExceptionCheck() || !stream) {
        env->ExceptionClear();
        return false;
    }
    jclass streamClass = env->GetObjectClass(stream);
    jmethodID read = env->GetMethodID(streamClass, "read", "([B)I");
    jmethodID close = env->GetMethodID(streamClass, "close", "()V");
    jbyteArray buffer = env->NewByteArray(4096);
    if (!read || !close || !buffer) {
        env->ExceptionClear();
        env->DeleteLocalRef(streamClass);
        env->DeleteLocalRef(stream);
        return false;
    }
    const size_t nameLength = std::strlen(resourceName);
    aggregate->insert(aggregate->end(), resourceName, resourceName + nameLength);
    aggregate->push_back(0);
    for (;;) {
        jint count = env->CallIntMethod(stream, read, buffer);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            env->CallVoidMethod(stream, close);
            env->DeleteLocalRef(buffer);
            env->DeleteLocalRef(streamClass);
            env->DeleteLocalRef(stream);
            return false;
        }
        if (count < 0) {
            break;
        }
        if (count == 0) {
            break;
        }
        jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
        if (!bytes) {
            break;
        }
        aggregate->insert(aggregate->end(), reinterpret_cast<BYTE*>(bytes),
                          reinterpret_cast<BYTE*>(bytes) + count);
        env->ReleaseByteArrayElements(buffer, bytes, JNI_ABORT);
    }
    env->CallVoidMethod(stream, close);
    env->DeleteLocalRef(buffer);
    env->DeleteLocalRef(streamClass);
    env->DeleteLocalRef(stream);
    return true;
}

std::string nativeClassDigest(JNIEnv* env, jclass bridge) {
    if (!env || !bridge) {
        return std::string();
    }
    jclass classClass = env->FindClass("java/lang/Class");
    jclass loaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID getClassLoader = env->GetMethodID(classClass, "getClassLoader",
                                                "()Ljava/lang/ClassLoader;");
    jmethodID getResource = env->GetMethodID(loaderClass, "getResourceAsStream",
                                             "(Ljava/lang/String;)Ljava/io/InputStream;");
    jobject classLoader = getClassLoader ? env->CallObjectMethod(bridge, getClassLoader) : nullptr;
    if (env->ExceptionCheck() || !classLoader || !getResource) {
        env->ExceptionClear();
        return std::string();
    }
    static const char* resources[] = {
        "gq/yozakura/auth/NativeAuthBridge.class",
        "gq/yozakura/auth/YozakuraAuthGate.class",
        "gq/yozakura/auth/vendor/tech/skidonion/obfuscator/inline/Wrapper.class",
        "gq/yozakura/core/Client.class",
        "gq/yozakura/core/StandaloneClient.class",
        "gq/yozakura/core/ModernForgeClient.class",
        "gq/yozakura/module/Module.class",
        "gq/yozakura/event/bus/EventManager.class",
        "gq/yozakura/event/api/EventManager.class",
        "gq/yozakura/bridge/MovementInputBridge.class",
        "gq/yozakura/ui/click/yozakura/YozakuraClickGui.class"
    };
    std::vector<BYTE> aggregate;
    for (const char* resource : resources) {
        if (!appendClassBytes(env, classLoader, getResource, resource, &aggregate)) {
            env->DeleteLocalRef(classLoader);
            return std::string();
        }
    }
    HCRYPTPROV provider = 0;
    HCRYPTHASH hash = 0;
    BYTE digest[32] = {};
    DWORD digestSize = sizeof(digest);
    std::string result;
    if (CryptAcquireContextA(&provider, nullptr, nullptr, PROV_RSA_AES, CRYPT_VERIFYCONTEXT)
            && CryptCreateHash(provider, CALG_SHA_256, 0, 0, &hash)
            && CryptHashData(hash, aggregate.data(), static_cast<DWORD>(aggregate.size()), 0)
            && CryptGetHashParam(hash, HP_HASHVAL, digest, &digestSize, 0)) {
        result = hexLower(digest, digestSize);
    }
    if (hash) CryptDestroyHash(hash);
    if (provider) CryptReleaseContext(provider, 0);
    env->DeleteLocalRef(classLoader);
    return result;
}

bool verifyPinnedSpki(HINTERNET requestHandle, const std::wstring& host) {
    if (!isConfiguredIpEndpoint(host)) {
        return true;
    }
    PCCERT_CONTEXT certificate = nullptr;
    DWORD certificateSize = sizeof(certificate);
    if (!WinHttpQueryOption(requestHandle, WINHTTP_OPTION_SERVER_CERT_CONTEXT,
                            &certificate, &certificateSize) || !certificate) {
        return false;
    }
    DWORD encodedSize = 0;
    if (!CryptEncodeObject(X509_ASN_ENCODING | PKCS_7_ASN_ENCODING,
                           X509_PUBLIC_KEY_INFO,
                           &certificate->pCertInfo->SubjectPublicKeyInfo,
                           nullptr, &encodedSize)) {
        CertFreeCertificateContext(certificate);
        return false;
    }
    std::vector<BYTE> encoded(encodedSize);
    if (!CryptEncodeObject(X509_ASN_ENCODING | PKCS_7_ASN_ENCODING,
                           X509_PUBLIC_KEY_INFO,
                           &certificate->pCertInfo->SubjectPublicKeyInfo,
                           encoded.data(), &encodedSize)) {
        CertFreeCertificateContext(certificate);
        return false;
    }
    BYTE digest[32] = {};
    DWORD digestSize = sizeof(digest);
    bool hashed = CryptHashCertificate(static_cast<HCRYPTPROV_LEGACY>(0), CALG_SHA_256, 0,
                                       encoded.data(), encodedSize,
                                       digest, &digestSize) != FALSE;
    std::string actual = hashed ? base64Encode(digest, digestSize) : std::string();
    CertFreeCertificateContext(certificate);
    return actual == YOZAKURA_PROTECTED_STRING("rpPZwAaOIUEB9s/OYIqe0jynKHexoupUNmshaDq8F5g=");
}

bool equalsIgnoreCase(const std::wstring& left, const wchar_t* right) {
    return _wcsicmp(left.c_str(), right) == 0;
}

bool isConfiguredIpEndpoint(const std::wstring& host) {
    return equalsIgnoreCase(host, L"49.235.166.227");
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
    if (!parsed->secure) {
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
    // 1 = transport/protocol failure, 2 = pinned certificate mismatch.
    // Login consumes this immediately so the UI can distinguish the two cases.
    InterlockedExchange(&lastRequestFailure, 1);
    ParsedUrl parsed = {};
    if (!parseSecureUrl(url, &parsed) || !response || body.size() > 16 * 1024
            || !parsed.secure || !isConfiguredIpEndpoint(parsed.host)
            || parsed.port != INTERNET_DEFAULT_HTTPS_PORT) {
        return false;
    }

    // Authentication traffic must not be routed through a user-configured proxy.
    // This prevents a local HTTP proxy from substituting the verification server.
    HttpHandle session(WinHttpOpen(L"Mozilla/5.0",
            WINHTTP_ACCESS_TYPE_NO_PROXY, WINHTTP_NO_PROXY_NAME,
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
    if (parsed.secure && isConfiguredIpEndpoint(parsed.host)) {
        // The deployment intentionally uses a self-signed certificate with an IP SAN.
        // Keep this exception limited to the configured endpoint; all other hosts use
        // the normal WinHTTP certificate chain, name and date validation.
        DWORD securityFlags = SECURITY_FLAG_IGNORE_UNKNOWN_CA;
        WinHttpSetOption(requestHandle, WINHTTP_OPTION_SECURITY_FLAGS,
                         &securityFlags, sizeof(securityFlags));
    }

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
    if (parsed.secure && !verifyPinnedSpki(requestHandle, parsed.host)) {
        InterlockedExchange(&lastRequestFailure, 2);
        authDebug("TLS certificate SPKI pin mismatch");
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
    InterlockedExchange(&lastRequestFailure, 0);
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
    sessionServerTimeMillis = 0;
    sessionServerMonotonicMillis = 0;
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
    LONGLONG serverTimeAnchor = 0;
    LONGLONG monotonicTimeAnchor = 0;
    AcquireSRWLockShared(&stateLock);
    base = serviceBaseUrl;
    token = sessionToken;
    build = clientBuild;
    fingerprint = clientFingerprint;
    hardware = machineFingerprint;
    serverTimeAnchor = sessionServerTimeMillis;
    monotonicTimeAnchor = sessionServerMonotonicMillis;
    ReleaseSRWLockShared(&stateLock);
    LONGLONG monotonicNow = monotonicTimeMillis();
    if (base.empty() || token.empty() || serverTimeAnchor <= 0
            || monotonicTimeAnchor <= 0 || monotonicNow < monotonicTimeAnchor) {
        authDebug("heartbeat rejected locally: session clock anchor is unavailable");
        return HeartbeatResult::Rejected;
    }

    LONGLONG sequence = InterlockedIncrement64(&heartbeatSequence);
    // Use the authenticated login response as the wall-clock anchor. The elapsed
    // component is monotonic, so an incorrect or adjusted Windows clock cannot
    // make an otherwise valid client fail the server's heartbeat skew check.
    LONGLONG now = serverTimeAnchor + (monotonicNow - monotonicTimeAnchor);
    std::string body;
    addFormField(&body, "client_time", std::to_string(now));
    addFormField(&body, "sequence", std::to_string(sequence));
    addFormField(&body, "build", build);
    addFormField(&body, "fp", fingerprint);
    addFormField(&body, "hw", hardware);

    HttpResponse response = {};
    std::string heartbeatPath = YOZAKURA_PROTECTED_STRING("api/v2/verify/heartbeat");
    if (!request(appendApiPath(base, heartbeatPath.c_str()), body, token, &response)) {
        return HeartbeatResult::TransientFailure;
    }
    LONGLONG code = -1;
    if (!jsonInteger(response.body, "code", &code)) {
        authDebug("heartbeat rejected: response did not contain a numeric code");
        return HeartbeatResult::Rejected;
    }
    LONGLONG serverTime = 0;
    LONGLONG acknowledged = -1;
    if (!jsonInteger(response.body, "server_time", &serverTime)
            || !jsonInteger(response.body, "sequence", &acknowledged)) {
        authDebug("heartbeat rejected: server time or sequence check failed");
        return HeartbeatResult::Rejected;
    }
    if (!yozakuraThemidaAcceptHeartbeat(response.status, code, sequence, acknowledged,
                                        now, serverTime)) {
        if (response.status != 200 || code != 0) {
            authDebugCode("heartbeat rejected: server returned code", code);
        } else {
            authDebug("heartbeat rejected: server time or sequence check failed");
        }
        return HeartbeatResult::Rejected;
    }
    InterlockedExchange64(&lastVerifiedMillis, monotonicTimeMillis());
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

bool sessionCurrent() {
    if (InterlockedCompareExchange(&verified, 0, 0) == 0) {
        return false;
    }
    LONGLONG last = InterlockedCompareExchange64(&lastVerifiedMillis, 0, 0);
    if (last <= 0 || monotonicTimeMillis() - last > kVerificationGraceMillis) {
        InterlockedExchange(&verified, 0);
        return false;
    }
    return true;
}

unsigned long long foldPermit(unsigned long long value) {
    value ^= value >> 30;
    value *= 0xBF58476D1CE4E5B9ULL;
    value ^= value >> 27;
    value *= 0x94D049BB133111EBULL;
    value ^= value >> 31;
    return value | 1ULL;
}

jlong JNICALL nativePrimaryPermit(JNIEnv*, jclass, jlong probe) {
    if (!sessionCurrent()) {
        return 0;
    }
    unsigned long long freshness = static_cast<unsigned long long>(
        InterlockedCompareExchange64(&lastVerifiedMillis, 0, 0));
    unsigned long long material = static_cast<unsigned long long>(probe)
        ^ freshness ^ (static_cast<unsigned long long>(GetCurrentProcessId()) << 19);
    return static_cast<jlong>(foldPermit(material));
}

jlong JNICALL nativeChannelPermit(JNIEnv*, jclass, jint channel, jlong probe) {
    switch (channel) {
        case 3:
        case 7:
        case 11:
        case 17:
        case 23:
        case 29:
            break;
        default:
            return 0;
    }
    if (!sessionCurrent()) {
        return 0;
    }
    unsigned long long freshness = static_cast<unsigned long long>(
        InterlockedCompareExchange64(&lastVerifiedMillis, 0, 0));
    unsigned long long material = static_cast<unsigned long long>(probe)
        ^ freshness ^ (static_cast<unsigned long long>(channel) << 47)
        ^ (static_cast<unsigned long long>(GetCurrentThreadId()) << 13);
    return static_cast<jlong>(foldPermit(material));
}

jint JNICALL nativeLogin(JNIEnv* env, jclass bridge, jstring usernameValue,
                         jcharArray passwordValue, jstring buildValue,
                         jstring fingerprintValue, jstring hardwareValue) {
    std::string base = YOZAKURA_PROTECTED_STRING("https://49.235.166.227/");
    std::string username = jniUtf8(env, usernameValue);
    std::string password = jniUtf8(env, passwordValue);
    std::string build = jniUtf8(env, buildValue);
    std::string fingerprint = nativeClassDigest(env, bridge);
    std::string hardware = jniUtf8(env, hardwareValue);
    ParsedUrl parsed = {};
    bool urlValid = parseSecureUrl(base, &parsed);
    if (!urlValid || username.size() < 3 || username.size() > 64
            || password.size() < 1 || password.size() > 256 || build.empty()
            || fingerprint.size() != 64 || hardware.empty()) {
        secureClear(&password);
        authDebug("login rejected locally: invalid endpoint, credentials, build, or fingerprint");
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
    std::string loginPath = YOZAKURA_PROTECTED_STRING("api/v2/verify/login");
    if (!request(appendApiPath(base, loginPath.c_str()), body, std::string(), &response)) {
        secureClear(&body);
        authDebug("login failed: request could not be completed");
        return InterlockedCompareExchange(&lastRequestFailure, 0, 0) == 2 ? -10 : -1;
    }
    secureClear(&body);
    LONGLONG code = -1;
    if (!jsonInteger(response.body, "code", &code)) {
        secureClear(&response.body);
        authDebug("login failed: response did not contain a numeric code");
        return -1;
    }
    if (!yozakuraThemidaAcceptLogin(response.status, code, 1)) {
        secureClear(&response.body);
        authDebugCode("login rejected: server returned code", code);
        return static_cast<jint>(code);
    }
    std::string token;
    std::string role;
    std::string expiry;
    LONGLONG loginServerTime = 0;
    bool validEntity = jsonString(response.body, "jwt", &token)
            && token.size() >= 24 && token.size() <= 512
            && jsonInteger(response.body, "server_time", &loginServerTime)
            && loginServerTime > 0;
    if (!jsonString(response.body, "rank_name", &role) || role.empty() || role.size() > 64) {
        role = "verified";
    }
    if (!jsonString(response.body, "expired_date", &expiry) || expiry.size() > 32) {
        expiry = "unknown";
    }
    secureClear(&response.body);
    if (!yozakuraThemidaAcceptLogin(response.status, code, validEntity ? 1 : 0)) {
        secureClear(&token);
        authDebug("login failed: response did not contain a valid session token");
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
    sessionServerTimeMillis = loginServerTime;
    sessionServerMonotonicMillis = monotonicTimeMillis();
    InterlockedExchange64(&heartbeatSequence, 0);
    ReleaseSRWLockExclusive(&stateLock);
    secureClear(&token);

    if (heartbeatOnce() != HeartbeatResult::Success) {
        clearSession();
        authDebug("login failed: initial heartbeat was not accepted");
        return -11;
    }
    if (!ensureHeartbeatThread()) {
        clearSession();
        authDebug("login failed: heartbeat thread could not be started");
        return -1;
    }
    authDebug("login accepted and heartbeat started");
    return 0;
}

jint JNICALL nativeRedeemLicense(JNIEnv* env, jclass, jstring licenseValue,
                                 jstring usernameValue,
                                 jcharArray passwordValue) {
    std::string base = YOZAKURA_PROTECTED_STRING("https://49.235.166.227/");
    std::string license = jniUtf8(env, licenseValue);
    std::string username = jniUtf8(env, usernameValue);
    std::string password = jniUtf8(env, passwordValue);
    ParsedUrl parsed = {};
    if (!parseSecureUrl(base, &parsed) || license.size() < 20 || license.size() > 64
            || username.size() < 3 || username.size() > 64
            || password.size() < 8 || password.size() > 128) {
        secureClear(&password);
        secureClear(&license);
        return 4;
    }
    std::string body;
    addFormField(&body, "license_key", license);
    addFormField(&body, "username", username);
    addFormField(&body, "password", password);
    secureClear(&password);
    secureClear(&license);
    HttpResponse response = {};
    std::string redeemPath = YOZAKURA_PROTECTED_STRING("api/v2/licenses/redeem");
    if (!request(appendApiPath(base, redeemPath.c_str()), body,
                 std::string(), &response)) {
        secureClear(&body);
        return -1;
    }
    secureClear(&body);
    bool accepted = response.status == 201 && response.body.find("\"ok\":true") != std::string::npos;
    bool alreadyRedeemed = response.body.find("already redeemed") != std::string::npos;
    bool userExists = response.body.find("user already exists") != std::string::npos;
    bool keyNotFound = response.body.find("license key was not found") != std::string::npos;
    DWORD status = response.status;
    secureClear(&response.body);
    if (accepted) {
        return 0;
    }
    if (status == 422 || status == 409) {
        if (alreadyRedeemed) {
            return 5;
        }
        if (userExists) {
            return 6;
        }
        return keyNotFound ? 4 : 7;
    }
    return -1;
}

void JNICALL nativeLogout(JNIEnv*, jclass) {
    std::string base;
    std::string token;
    AcquireSRWLockShared(&stateLock);
    base = serviceBaseUrl;
    token = sessionToken;
    ReleaseSRWLockShared(&stateLock);
    if (!base.empty() && !token.empty()) {
        HttpResponse response = {};
        std::string logoutPath = YOZAKURA_PROTECTED_STRING("api/v2/verify/logout");
        request(appendApiPath(base, logoutPath.c_str()), std::string(), token, &response);
        secureClear(&response.body);
    }
    secureClear(&token);
    clearSession();
}

jint JNICALL nativeLoginKeyed(JNIEnv* env, jclass bridge, jstring,
                              jstring usernameValue, jcharArray passwordValue, jstring buildValue,
                              jstring fingerprintValue, jstring hardwareValue) {
    return nativeLogin(env, bridge, usernameValue, passwordValue, buildValue,
                       fingerprintValue, hardwareValue);
}

jint JNICALL nativeRedeemLicenseKeyed(JNIEnv* env, jclass bridge, jstring,
                                      jstring licenseValue, jstring usernameValue,
                                      jcharArray passwordValue) {
    return nativeRedeemLicense(env, bridge, licenseValue, usernameValue, passwordValue);
}

bool hasStaticMethod(JNIEnv* env, jclass type, const char* name, const char* signature) {
    jmethodID method = env->GetStaticMethodID(type, name, signature);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    return method != nullptr;
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
    std::string loaderClassName = YOZAKURA_PROTECTED_STRING("java/lang/ClassLoader");
    std::string loadClassName = YOZAKURA_PROTECTED_STRING("loadClass");
    std::string loadClassSignature =
        YOZAKURA_PROTECTED_STRING("(Ljava/lang/String;)Ljava/lang/Class;");
    std::string bridgeClassName =
        YOZAKURA_PROTECTED_STRING("gq.yozakura.auth.NativeAuthBridge");
    jclass loaderClass = env->FindClass(loaderClassName.c_str());
    jmethodID loadClass = env->GetMethodID(loaderClass, loadClassName.c_str(),
                                           loadClassSignature.c_str());
    jstring name = env->NewStringUTF(bridgeClassName.c_str());
    jclass bridge = static_cast<jclass>(env->CallObjectMethod(loader, loadClass, name));
    if (env->ExceptionCheck() || !bridge) {
        env->ExceptionClear();
        return false;
    }

    std::string primaryPermitName = YOZAKURA_PROTECTED_STRING("q0");
    std::string channelPermitName = YOZAKURA_PROTECTED_STRING("q1");
    std::string loginName = YOZAKURA_PROTECTED_STRING("login0");
    std::string redeemName = YOZAKURA_PROTECTED_STRING("redeemLicense0");
    std::string logoutName = YOZAKURA_PROTECTED_STRING("logout0");
    std::string usernameName = YOZAKURA_PROTECTED_STRING("username0");
    std::string roleName = YOZAKURA_PROTECTED_STRING("role0");
    std::string expiryName = YOZAKURA_PROTECTED_STRING("expiry0");
    std::string primaryPermitSignature = YOZAKURA_PROTECTED_STRING("(J)J");
    std::string channelPermitSignature = YOZAKURA_PROTECTED_STRING("(IJ)J");
    std::string voidSignature = YOZAKURA_PROTECTED_STRING("()V");
    std::string stringSignature = YOZAKURA_PROTECTED_STRING("()Ljava/lang/String;");
    std::string loginSignatureStorage = YOZAKURA_PROTECTED_STRING(
        "(Ljava/lang/String;[CLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)I");
    std::string redeemSignatureStorage =
        YOZAKURA_PROTECTED_STRING("(Ljava/lang/String;Ljava/lang/String;[C)I");
    const char* loginSignature = loginSignatureStorage.c_str();
    const char* redeemSignature = redeemSignatureStorage.c_str();
    void* loginFunction = reinterpret_cast<void*>(&nativeLogin);
    void* redeemFunction = reinterpret_cast<void*>(&nativeRedeemLicense);
    if (!hasStaticMethod(env, bridge, loginName.c_str(), loginSignature)
            || !hasStaticMethod(env, bridge, redeemName.c_str(), redeemSignature)) {
        loginSignatureStorage = YOZAKURA_PROTECTED_STRING(
            "(Ljava/lang/String;Ljava/lang/String;[CLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)I");
        redeemSignatureStorage = YOZAKURA_PROTECTED_STRING(
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[C)I");
        loginSignature = loginSignatureStorage.c_str();
        redeemSignature = redeemSignatureStorage.c_str();
        if (!hasStaticMethod(env, bridge, loginName.c_str(), loginSignature)
                || !hasStaticMethod(env, bridge, redeemName.c_str(), redeemSignature)) {
            return false;
        }
        loginFunction = reinterpret_cast<void*>(&nativeLoginKeyed);
        redeemFunction = reinterpret_cast<void*>(&nativeRedeemLicenseKeyed);
    }

    JNINativeMethod methods[] = {
        { const_cast<char*>(primaryPermitName.c_str()),
          const_cast<char*>(primaryPermitSignature.c_str()),
          reinterpret_cast<void*>(&nativePrimaryPermit) },
        { const_cast<char*>(channelPermitName.c_str()),
          const_cast<char*>(channelPermitSignature.c_str()),
          reinterpret_cast<void*>(&nativeChannelPermit) },
        { const_cast<char*>(loginName.c_str()), const_cast<char*>(loginSignature), loginFunction },
        { const_cast<char*>(redeemName.c_str()), const_cast<char*>(redeemSignature), redeemFunction },
        { const_cast<char*>(logoutName.c_str()), const_cast<char*>(voidSignature.c_str()),
          reinterpret_cast<void*>(&nativeLogout) },
        { const_cast<char*>(usernameName.c_str()), const_cast<char*>(stringSignature.c_str()),
          reinterpret_cast<void*>(&nativeUsername) },
        { const_cast<char*>(roleName.c_str()), const_cast<char*>(stringSignature.c_str()),
          reinterpret_cast<void*>(&nativeRole) },
        { const_cast<char*>(expiryName.c_str()), const_cast<char*>(stringSignature.c_str()),
          reinterpret_cast<void*>(&nativeExpiry) }
    };
    const jint methodCount = static_cast<jint>(sizeof(methods) / sizeof(methods[0]));
    const jint result = env->RegisterNatives(bridge, methods, methodCount);
    return yozakuraThemidaAcceptRegistration(result, methodCount) != 0;
}

void signalYozakuraNativeAuthShutdown() {
    if (stopEvent) {
        SetEvent(stopEvent);
    }
}
