#include "yozakura_native_auth.h"
#include "yozakura_string_obfuscation.h"
#include "yozakura_themida_guard.h"

#include <windows.h>
#include <winhttp.h>
#include <wincrypt.h>
#include <bcrypt.h>
#include <ncrypt.h>

#include <algorithm>
#include <cctype>
#include <cwctype>
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
volatile LONG64 cloudProofSequence = 0;
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
std::string sessionId;
NCRYPT_PROV_HANDLE deviceProvider = 0;
NCRYPT_KEY_HANDLE deviceKey = 0;
JavaVM* authVm = nullptr;
jclass authBridgeClass = nullptr;
volatile LONG debuggerTrusted = 0;
volatile LONG64 lastDebuggerCheckMillis = 0;

const wchar_t* kDeviceKeyName = L"Yozakura.Device.Pop.P256.v1";
const char* kPopVersion = "YOZAKURA-POP-1";
const LONGLONG kDebuggerCheckIntervalMillis = 1000;
// SHA-256 over the SubjectPublicKeyInfo for auth.yozakura.wtf's P-256 certificate.
// Roll certificates with an overlapping pin release; a trust-store certificate alone
// must never authorize a verification response.
const BYTE kAuthServerSpkiSha256[32] = {
    0x66, 0x34, 0x48, 0x68, 0x22, 0x68, 0x24, 0x36,
    0xf1, 0xf5, 0x6d, 0xfb, 0x35, 0xe8, 0xf1, 0xe6,
    0x78, 0xdd, 0x0c, 0xd2, 0x72, 0x8b, 0x33, 0x2a,
    0x66, 0xa1, 0xdd, 0xc4, 0x94, 0xb3, 0xb1, 0xbf
};

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

bool commandLineHasDebuggerAgent() {
    const wchar_t* raw = GetCommandLineW();
    if (!raw) {
        return true;
    }
    std::wstring command(raw);
    std::transform(command.begin(), command.end(), command.begin(), [](wchar_t value) {
        return static_cast<wchar_t>(std::towlower(value));
    });
    std::string agentOption = YOZAKURA_PROTECTED_STRING("-agentlib:jdwp");
    std::string legacyOption = YOZAKURA_PROTECTED_STRING("-xrunjdwp");
    std::wstring agent = utf8ToWide(agentOption);
    std::wstring legacy = utf8ToWide(legacyOption);
    return agent.empty() || legacy.empty()
            || command.find(agent) != std::wstring::npos
            || command.find(legacy) != std::wstring::npos;
}

bool debuggerEnvironmentTrusted() {
    LONGLONG now = monotonicTimeMillis();
    LONGLONG last = InterlockedCompareExchange64(&lastDebuggerCheckMillis, 0, 0);
    if (last > 0 && now >= last && now - last <= kDebuggerCheckIntervalMillis) {
        return InterlockedCompareExchange(&debuggerTrusted, 0, 0) != 0;
    }

    BOOL remoteDebuggerPresent = FALSE;
    BOOL remoteQuerySucceeded = CheckRemoteDebuggerPresent(
            GetCurrentProcess(), &remoteDebuggerPresent);
    int debuggerSignalPresent = IsDebuggerPresent() || commandLineHasDebuggerAgent() ? 1 : 0;
    int trusted = yozakuraThemidaAcceptDebuggerState(
            debuggerSignalPresent,
            remoteQuerySucceeded ? 1 : 0,
            remoteDebuggerPresent ? 1 : 0);
    InterlockedExchange(&debuggerTrusted, trusted != 0 ? 1 : 0);
    InterlockedExchange64(&lastDebuggerCheckMillis, now);
    return trusted != 0;
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

bool isConfiguredAuthEndpoint(const std::wstring& host);

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

std::string base64UrlEncode(const BYTE* data, DWORD length) {
    std::string output = base64Encode(data, length);
    while (!output.empty() && output.back() == '=') {
        output.pop_back();
    }
    std::replace(output.begin(), output.end(), '+', '-');
    std::replace(output.begin(), output.end(), '/', '_');
    return output;
}

bool sha256(const BYTE* data, DWORD length, BYTE digest[32]) {
    BCRYPT_ALG_HANDLE algorithm = nullptr;
    BCRYPT_HASH_HANDLE hash = nullptr;
    DWORD objectLength = 0;
    DWORD resultLength = 0;
    std::vector<BYTE> object;
    NTSTATUS status = BCryptOpenAlgorithmProvider(&algorithm, BCRYPT_SHA256_ALGORITHM,
                                                   nullptr, 0);
    if (status >= 0) {
        status = BCryptGetProperty(algorithm, BCRYPT_OBJECT_LENGTH,
                reinterpret_cast<PUCHAR>(&objectLength), sizeof(objectLength),
                &resultLength, 0);
    }
    if (status >= 0) {
        object.resize(objectLength);
        status = BCryptCreateHash(algorithm, &hash, object.data(), objectLength,
                                  nullptr, 0, 0);
    }
    if (status >= 0) {
        status = BCryptHashData(hash, const_cast<PUCHAR>(data), length, 0);
    }
    if (status >= 0) {
        status = BCryptFinishHash(hash, digest, 32, 0);
    }
    if (hash) BCryptDestroyHash(hash);
    if (algorithm) BCryptCloseAlgorithmProvider(algorithm, 0);
    return status >= 0;
}

bool constantTimeEqualsBytes(const BYTE* left, const BYTE* right, DWORD length) {
    if (!left || !right) {
        return false;
    }
    BYTE difference = 0;
    for (DWORD index = 0; index < length; ++index) {
        difference |= static_cast<BYTE>(left[index] ^ right[index]);
    }
    return difference == 0;
}

bool verifyAuthServerCertificate(HINTERNET requestHandle) {
    PCCERT_CONTEXT certificate = nullptr;
    DWORD certificateSize = sizeof(certificate);
    if (!requestHandle || !WinHttpQueryOption(requestHandle,
            WINHTTP_OPTION_SERVER_CERT_CONTEXT, &certificate, &certificateSize)
            || !certificate || !certificate->pCertInfo) {
        return false;
    }

    BYTE* subjectPublicKeyInfo = nullptr;
    DWORD subjectPublicKeyInfoSize = 0;
    BOOL encoded = CryptEncodeObjectEx(X509_ASN_ENCODING | PKCS_7_ASN_ENCODING,
            X509_PUBLIC_KEY_INFO, &certificate->pCertInfo->SubjectPublicKeyInfo,
            CRYPT_ENCODE_ALLOC_FLAG, nullptr, &subjectPublicKeyInfo, &subjectPublicKeyInfoSize);
    BYTE digest[32] = {};
    bool trusted = encoded && subjectPublicKeyInfo && subjectPublicKeyInfoSize > 0
            && sha256(subjectPublicKeyInfo, subjectPublicKeyInfoSize, digest)
            && constantTimeEqualsBytes(digest, kAuthServerSpkiSha256, sizeof(digest));
    if (subjectPublicKeyInfo) {
        LocalFree(subjectPublicKeyInfo);
    }
    CertFreeCertificateContext(certificate);
    return trusted;
}

void closeDeviceKey() {
    if (deviceKey) {
        NCryptFreeObject(deviceKey);
        deviceKey = 0;
    }
    if (deviceProvider) {
        NCryptFreeObject(deviceProvider);
        deviceProvider = 0;
    }
}

bool openDeviceKeyWithProvider(const wchar_t* providerName, bool create) {
    NCRYPT_PROV_HANDLE provider = 0;
    NCRYPT_KEY_HANDLE key = 0;
    SECURITY_STATUS status = NCryptOpenStorageProvider(&provider, providerName, 0);
    if (status != ERROR_SUCCESS) {
        return false;
    }
    status = NCryptOpenKey(provider, &key, kDeviceKeyName, 0, 0);
    bool created = false;
    if (status != ERROR_SUCCESS && create) {
        status = NCryptCreatePersistedKey(provider, &key, NCRYPT_ECDSA_P256_ALGORITHM,
                                          kDeviceKeyName, 0, 0);
        created = status == ERROR_SUCCESS;
        if (status == ERROR_SUCCESS) {
            DWORD exportPolicy = 0;
            status = NCryptSetProperty(key, NCRYPT_EXPORT_POLICY_PROPERTY,
                    reinterpret_cast<PBYTE>(&exportPolicy), sizeof(exportPolicy),
                    NCRYPT_PERSIST_FLAG);
        }
        if (status == ERROR_SUCCESS) {
            status = NCryptFinalizeKey(key, 0);
        }
    }
    if (status != ERROR_SUCCESS) {
        if (created && key) {
            NCryptDeleteKey(key, 0);
            key = 0;
        }
        if (key) NCryptFreeObject(key);
        NCryptFreeObject(provider);
        return false;
    }
    closeDeviceKey();
    deviceProvider = provider;
    deviceKey = key;
    return true;
}

bool ensureDeviceKey() {
    if (deviceKey) {
        return true;
    }
    if (openDeviceKeyWithProvider(MS_PLATFORM_CRYPTO_PROVIDER, false)
            || openDeviceKeyWithProvider(MS_KEY_STORAGE_PROVIDER, false)
            || openDeviceKeyWithProvider(MS_PLATFORM_CRYPTO_PROVIDER, true)
            || openDeviceKeyWithProvider(MS_KEY_STORAGE_PROVIDER, true)) {
        return true;
    }
    return false;
}

bool exportDevicePublicKey(std::string* x, std::string* y, std::string* thumbprint) {
    if (!x || !y || !thumbprint || !ensureDeviceKey()) {
        return false;
    }
    DWORD size = 0;
    SECURITY_STATUS status = NCryptExportKey(deviceKey, 0, BCRYPT_ECCPUBLIC_BLOB,
                                              nullptr, nullptr, 0, &size, 0);
    std::vector<BYTE> blob(size);
    if (status != ERROR_SUCCESS || size < sizeof(BCRYPT_ECCKEY_BLOB)
            || NCryptExportKey(deviceKey, 0, BCRYPT_ECCPUBLIC_BLOB, nullptr,
                               blob.data(), size, &size, 0) != ERROR_SUCCESS) {
        return false;
    }
    const BCRYPT_ECCKEY_BLOB* header =
        reinterpret_cast<const BCRYPT_ECCKEY_BLOB*>(blob.data());
    if (header->dwMagic != BCRYPT_ECDSA_PUBLIC_P256_MAGIC || header->cbKey != 32
            || size != sizeof(BCRYPT_ECCKEY_BLOB) + 64) {
        return false;
    }
    const BYTE* coordinates = blob.data() + sizeof(BCRYPT_ECCKEY_BLOB);
    *x = base64UrlEncode(coordinates, 32);
    *y = base64UrlEncode(coordinates + 32, 32);
    std::string jwk = std::string("{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"")
            + *x + "\",\"y\":\"" + *y + "\"}";
    BYTE digest[32] = {};
    if (!sha256(reinterpret_cast<const BYTE*>(jwk.data()),
                static_cast<DWORD>(jwk.size()), digest)) {
        return false;
    }
    *thumbprint = base64UrlEncode(digest, sizeof(digest));
    return thumbprint->size() == 43;
}

bool signDeviceProof(const std::string& canonical, std::string* signature) {
    if (!signature || !ensureDeviceKey()) {
        return false;
    }
    BYTE digest[32] = {};
    if (!sha256(reinterpret_cast<const BYTE*>(canonical.data()),
                static_cast<DWORD>(canonical.size()), digest)) {
        return false;
    }
    DWORD signatureSize = 0;
    SECURITY_STATUS status = NCryptSignHash(deviceKey, nullptr, digest, sizeof(digest),
                                             nullptr, 0, &signatureSize, 0);
    std::vector<BYTE> bytes(signatureSize);
    if (status != ERROR_SUCCESS
            || NCryptSignHash(deviceKey, nullptr, digest, sizeof(digest), bytes.data(),
                              signatureSize, &signatureSize, 0) != ERROR_SUCCESS
            || signatureSize != 64) {
        return false;
    }
    *signature = base64UrlEncode(bytes.data(), signatureSize);
    return !signature->empty();
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
        "gq/yozakura/k/A.class",
        "gq/yozakura/k/B.class",
        "gq/yozakura/k/vendor/tech/skidonion/obfuscator/inline/C.class",
        "gq/yozakura/core/Client.class",
        "gq/yozakura/core/StandaloneClient.class",
        "gq/yozakura/core/ModernForgeClient.class",
        "gq/yozakura/module/Module.class",
        "gq/yozakura/event/bus/EventManager.class",
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

bool runtimeClassDigest(std::string* digest) {
    if (!digest || !authVm || !authBridgeClass) {
        return false;
    }
    JNIEnv* env = nullptr;
    bool attached = false;
    jint status = authVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8);
    if (status == JNI_EDETACHED) {
        if (authVm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) {
            return false;
        }
        attached = true;
    } else if (status != JNI_OK || !env) {
        return false;
    }
    *digest = nativeClassDigest(env, authBridgeClass);
    if (attached) {
        authVm->DetachCurrentThread();
    }
    return digest->size() == 64;
}

bool constantTimeEquals(const std::string& left, const std::string& right) {
    size_t maximum = (std::max)(left.size(), right.size());
    unsigned int difference = static_cast<unsigned int>(left.size() ^ right.size());
    for (size_t index = 0; index < maximum; ++index) {
        unsigned char leftByte = index < left.size()
                ? static_cast<unsigned char>(left[index]) : 0;
        unsigned char rightByte = index < right.size()
                ? static_cast<unsigned char>(right[index]) : 0;
        difference |= static_cast<unsigned int>(leftByte ^ rightByte);
    }
    return difference == 0;
}

bool equalsIgnoreCase(const std::wstring& left, const wchar_t* right) {
    return _wcsicmp(left.c_str(), right) == 0;
}

bool isConfiguredAuthEndpoint(const std::wstring& host) {
    return equalsIgnoreCase(host, L"auth.yozakura.wtf");
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

bool recordRequestFailure(const char* operation) {
    DWORD error = GetLastError();
    if (error == ERROR_SUCCESS) {
        error = ERROR_GEN_FAILURE;
    }
    InterlockedExchange(&lastRequestFailure, static_cast<LONG>(error));
    return false;
}

jint requestFailureLoginCode() {
    LONG error = InterlockedCompareExchange(&lastRequestFailure, 0, 0);
    switch (error) {
        case ERROR_WINHTTP_AUTODETECTION_FAILED:
        case ERROR_WINHTTP_UNABLE_TO_DOWNLOAD_SCRIPT:
            return -13;
        case ERROR_WINHTTP_SECURE_FAILURE:
        case ERROR_WINHTTP_CLIENT_AUTH_CERT_NEEDED:
            return -14;
        case ERROR_WINHTTP_TIMEOUT:
        case ERROR_WINHTTP_CANNOT_CONNECT:
        case ERROR_WINHTTP_CONNECTION_ERROR:
        case ERROR_WINHTTP_NAME_NOT_RESOLVED:
            return -12;
        default:
            return -1;
    }
}

bool request(const std::string& url, const std::string& body,
             const std::string& token, HttpResponse* response) {
    InterlockedExchange(&lastRequestFailure, ERROR_INVALID_PARAMETER);
    ParsedUrl parsed = {};
    if (!parseSecureUrl(url, &parsed) || !response || body.size() > 16 * 1024
            || !parsed.secure || !isConfiguredAuthEndpoint(parsed.host)
            || parsed.port != INTERNET_DEFAULT_HTTPS_PORT) {
        return false;
    }

    // Honor the Windows proxy configuration so authentication works on networks
    // that require an HTTP CONNECT proxy. The destination remains fail-closed:
    // HTTPS, auth.yozakura.wtf:443, normal certificate validation, no redirects.
    HttpHandle session(WinHttpOpen(L"Mozilla/5.0",
            WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY, WINHTTP_NO_PROXY_NAME,
            WINHTTP_NO_PROXY_BYPASS, 0));
    if (!session) {
        return recordRequestFailure("WinHttpOpen failed with error");
    }
    WinHttpSetTimeouts(session, kRequestTimeoutMillis, kRequestTimeoutMillis,
                       kRequestTimeoutMillis, kRequestTimeoutMillis);

    HttpHandle connection(WinHttpConnect(session, parsed.host.c_str(), parsed.port, 0));
    if (!connection) {
        return recordRequestFailure("WinHttpConnect failed with error");
    }
    DWORD flags = parsed.secure ? WINHTTP_FLAG_SECURE : 0;
    HttpHandle requestHandle(WinHttpOpenRequest(connection, L"POST", parsed.target.c_str(),
            nullptr, WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, flags));
    if (!requestHandle) {
        return recordRequestFailure("WinHttpOpenRequest failed with error");
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
        headers.append(L"Authorization: Bearer ");
        headers.append(wideToken);
        headers.append(L"\r\n");
    }

    if (!WinHttpSendRequest(requestHandle, headers.c_str(), static_cast<DWORD>(-1),
            body.empty() ? WINHTTP_NO_REQUEST_DATA : const_cast<char*>(body.data()),
            static_cast<DWORD>(body.size()), static_cast<DWORD>(body.size()), 0)) {
        return recordRequestFailure("WinHttpSendRequest failed with error");
    }
    if (!WinHttpReceiveResponse(requestHandle, nullptr)) {
        return recordRequestFailure("WinHttpReceiveResponse failed with error");
    }
    if (!verifyAuthServerCertificate(requestHandle)) {
        SetLastError(ERROR_WINHTTP_SECURE_FAILURE);
        return recordRequestFailure("The authentication server certificate pin did not match");
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
    sessionId.clear();
    sessionServerTimeMillis = 0;
    sessionServerMonotonicMillis = 0;
    InterlockedExchange(&verified, 0);
    InterlockedExchange64(&heartbeatSequence, 0);
    InterlockedExchange64(&cloudProofSequence, 0);
    InterlockedExchange64(&lastVerifiedMillis, 0);
    ReleaseSRWLockExclusive(&stateLock);
}

HeartbeatResult heartbeatOnce() {
    std::string base;
    std::string token;
    std::string build;
    std::string fingerprint;
    std::string sid;
    LONGLONG serverTimeAnchor = 0;
    LONGLONG monotonicTimeAnchor = 0;
    AcquireSRWLockShared(&stateLock);
    base = serviceBaseUrl;
    token = sessionToken;
    build = clientBuild;
    fingerprint = clientFingerprint;
    sid = sessionId;
    serverTimeAnchor = sessionServerTimeMillis;
    monotonicTimeAnchor = sessionServerMonotonicMillis;
    ReleaseSRWLockShared(&stateLock);
    LONGLONG monotonicNow = monotonicTimeMillis();
    if (base.empty() || token.empty() || sid.empty() || serverTimeAnchor <= 0
            || monotonicTimeAnchor <= 0 || monotonicNow < monotonicTimeAnchor) {
        return HeartbeatResult::Rejected;
    }
    if (!debuggerEnvironmentTrusted()) {
        return HeartbeatResult::Rejected;
    }
    std::string runtimeDigest;
    if (!runtimeClassDigest(&runtimeDigest)
            || !constantTimeEquals(runtimeDigest, fingerprint)) {
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
    std::string heartbeatCanonicalPrefix =
        YOZAKURA_PROTECTED_STRING("\nPOST\n/api/v2/verify/heartbeat\n");
    std::string canonical = std::string(kPopVersion) + heartbeatCanonicalPrefix + sid
            + "\n" + std::to_string(sequence)
            + "\n" + std::to_string(now)
            + "\n" + build + "\n" + fingerprint;
    std::string proof;
    if (!signDeviceProof(canonical, &proof)) {
        return HeartbeatResult::Rejected;
    }
    addFormField(&body, "pop_signature", proof);
    secureClear(&proof);

    HttpResponse response = {};
    std::string heartbeatPath = YOZAKURA_PROTECTED_STRING("api/v2/verify/heartbeat");
    if (!request(appendApiPath(base, heartbeatPath.c_str()), body, token, &response)) {
        return HeartbeatResult::TransientFailure;
    }
    LONGLONG code = -1;
    if (!jsonInteger(response.body, "code", &code)) {
        return HeartbeatResult::Rejected;
    }
    LONGLONG serverTime = 0;
    LONGLONG acknowledged = -1;
    if (!jsonInteger(response.body, "server_time", &serverTime)
            || !jsonInteger(response.body, "sequence", &acknowledged)) {
        return HeartbeatResult::Rejected;
    }
    if (!yozakuraThemidaAcceptHeartbeat(response.status, code, sequence, acknowledged,
                                        now, serverTime)) {
        if (response.status != 200 || code != 0) {
        } else {
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
    if (!debuggerEnvironmentTrusted()) {
        InterlockedExchange(&verified, 0);
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
    std::string base = YOZAKURA_PROTECTED_STRING("https://auth.yozakura.wtf/");
    if (!debuggerEnvironmentTrusted()) {
        return -20;
    }
    std::string username = jniUtf8(env, usernameValue);
    std::string password = jniUtf8(env, passwordValue);
    std::string build = jniUtf8(env, buildValue);
    std::string fingerprint = nativeClassDigest(env, bridge);
    ParsedUrl parsed = {};
    bool urlValid = parseSecureUrl(base, &parsed);
    if (!urlValid || username.size() < 3 || username.size() > 64
            || password.size() < 6 || password.size() > 128 || build.empty()) {
        secureClear(&password);
        return -15;
    }
    if (fingerprint.size() != 64) {
        secureClear(&password);
        return -16;
    }
    if (!ensureDeviceKey()) {
        secureClear(&password);
        return -17;
    }

    clearSession();
    std::string publicX;
    std::string publicY;
    std::string thumbprint;
    if (!exportDevicePublicKey(&publicX, &publicY, &thumbprint)) {
        secureClear(&password);
        return -17;
    }

    std::string challengeBody;
    addFormField(&challengeBody, "build", build);
    addFormField(&challengeBody, "fp", fingerprint);
    HttpResponse challengeResponse = {};
    std::string challengePath = YOZAKURA_PROTECTED_STRING("api/v2/verify/challenge");
    if (!request(appendApiPath(base, challengePath.c_str()), challengeBody,
                 std::string(), &challengeResponse)) {
        secureClear(&password);
        secureClear(&challengeBody);
        return requestFailureLoginCode();
    }
    secureClear(&challengeBody);
    std::string challengeId;
    std::string nonce;
    LONGLONG challengeCode = -1;
    bool hasChallengeCode = jsonInteger(challengeResponse.body, "code", &challengeCode);
    bool validChallenge = challengeResponse.status == 200
            && hasChallengeCode
            && challengeCode == 0
            && jsonString(challengeResponse.body, "challenge_id", &challengeId)
            && challengeId.size() == 32
            && jsonString(challengeResponse.body, "nonce", &nonce)
            && nonce.size() == 43;
    secureClear(&challengeResponse.body);
    if (!validChallenge) {
        secureClear(&password);
        if (hasChallengeCode && challengeCode >= 0 && challengeCode <= 1000) {
            return static_cast<jint>(challengeCode);
        }
        return -18;
    }
    std::string loginCanonicalPrefix =
        YOZAKURA_PROTECTED_STRING("\nLOGIN\n/api/v2/verify/login\n");
    std::string canonical = std::string(kPopVersion) + loginCanonicalPrefix + challengeId
            + "\n" + nonce + "\n" + username + "\n" + build + "\n" + fingerprint;
    std::string challengeSignature;
    if (!signDeviceProof(canonical, &challengeSignature)) {
        secureClear(&password);
        return -17;
    }

    std::string body;
    addFormField(&body, "username", username);
    addFormField(&body, "password", password);
    addFormField(&body, "software_id", "183");
    addFormField(&body, "e", "true");
    addFormField(&body, "build", build);
    addFormField(&body, "fp", fingerprint);
    addFormField(&body, "challenge_id", challengeId);
    addFormField(&body, "device_key_x", publicX);
    addFormField(&body, "device_key_y", publicY);
    addFormField(&body, "device_key_thumbprint", thumbprint);
    addFormField(&body, "challenge_signature", challengeSignature);
    secureClear(&password);
    secureClear(&challengeSignature);

    HttpResponse response = {};
    std::string loginPath = YOZAKURA_PROTECTED_STRING("api/v2/verify/login");
    if (!request(appendApiPath(base, loginPath.c_str()), body, std::string(), &response)) {
        secureClear(&body);
        return requestFailureLoginCode();
    }
    secureClear(&body);
    LONGLONG code = -1;
    if (!jsonInteger(response.body, "code", &code)) {
        secureClear(&response.body);
        return -18;
    }
    if (!yozakuraThemidaAcceptLogin(response.status, code, 1)) {
        secureClear(&response.body);
        return static_cast<jint>(code);
    }
    std::string token;
    std::string loginSessionId;
    std::string role;
    std::string expiry;
    LONGLONG loginServerTime = 0;
    bool validEntity = jsonString(response.body, "jwt", &token)
            && token.size() >= 64 && token.size() <= 2048
            && jsonString(response.body, "session_id", &loginSessionId)
            && loginSessionId.size() == 32
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
        return -18;
    }

    AcquireSRWLockExclusive(&stateLock);
    serviceBaseUrl = base;
    sessionToken = token;
    verifiedUsername = username;
    verifiedRole = role;
    verifiedExpiry = expiry;
    clientBuild = build;
    clientFingerprint = fingerprint;
    sessionId = loginSessionId;
    sessionServerTimeMillis = loginServerTime;
    sessionServerMonotonicMillis = monotonicTimeMillis();
    InterlockedExchange64(&heartbeatSequence, 0);
    ReleaseSRWLockExclusive(&stateLock);
    secureClear(&token);

    if (heartbeatOnce() != HeartbeatResult::Success) {
        clearSession();
        return -11;
    }
    if (!ensureHeartbeatThread()) {
        clearSession();
        return -19;
    }
    return 0;
}

jint JNICALL nativeRedeemLicense(JNIEnv* env, jclass, jstring licenseValue,
                                 jstring usernameValue,
                                 jcharArray passwordValue) {
    std::string base = YOZAKURA_PROTECTED_STRING("https://auth.yozakura.wtf/");
    std::string license = jniUtf8(env, licenseValue);
    std::string username = jniUtf8(env, usernameValue);
    std::string password = jniUtf8(env, passwordValue);
    ParsedUrl parsed = {};
    if (!parseSecureUrl(base, &parsed) || license.size() < 20 || license.size() > 64
            || username.size() < 3 || username.size() > 64
            || password.size() < 6 || password.size() > 128) {
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

jstring JNICALL nativeSessionProof(JNIEnv* env, jclass) {
    if (!sessionCurrent()) {
        return nullptr;
    }
    std::string token;
    std::string sid;
    LONGLONG serverTimeAnchor = 0;
    LONGLONG monotonicTimeAnchor = 0;
    AcquireSRWLockShared(&stateLock);
    token = sessionToken;
    sid = sessionId;
    serverTimeAnchor = sessionServerTimeMillis;
    monotonicTimeAnchor = sessionServerMonotonicMillis;
    ReleaseSRWLockShared(&stateLock);
    LONGLONG monotonicNow = monotonicTimeMillis();
    if (token.empty() || sid.empty() || serverTimeAnchor <= 0
            || monotonicTimeAnchor <= 0 || monotonicNow < monotonicTimeAnchor) {
        secureClear(&token);
        return nullptr;
    }
    LONGLONG timestamp = serverTimeAnchor + (monotonicNow - monotonicTimeAnchor);
    LONGLONG sequence = InterlockedIncrement64(&cloudProofSequence);
    std::string canonicalPrefix = YOZAKURA_PROTECTED_STRING(
        "\nPOST\n/api/v2/verify/introspect\n");
    std::string canonical = std::string(kPopVersion) + canonicalPrefix + sid
            + "\n" + std::to_string(sequence) + "\n" + std::to_string(timestamp);
    std::string signature;
    if (!signDeviceProof(canonical, &signature)) {
        secureClear(&token);
        return nullptr;
    }
    std::string value = token + "." + std::to_string(timestamp) + "."
            + std::to_string(sequence) + "." + signature;
    secureClear(&token);
    secureClear(&signature);
    jstring result = newJniString(env, value);
    secureClear(&value);
    return result;
}

} // namespace

bool registerYozakuraNativeAuth(JNIEnv* env, jobject loader) {
    if (!env || !loader || !debuggerEnvironmentTrusted()) {
        return false;
    }
    std::string loaderClassName = YOZAKURA_PROTECTED_STRING("java/lang/ClassLoader");
    std::string loadClassName = YOZAKURA_PROTECTED_STRING("loadClass");
    std::string loadClassSignature =
        YOZAKURA_PROTECTED_STRING("(Ljava/lang/String;)Ljava/lang/Class;");
    std::string bridgeClassName =
        YOZAKURA_PROTECTED_STRING("gq.yozakura.k.A");
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
    std::string sessionProofName = YOZAKURA_PROTECTED_STRING("sessionProof0");
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
          reinterpret_cast<void*>(&nativeExpiry) },
        { const_cast<char*>(sessionProofName.c_str()), const_cast<char*>(stringSignature.c_str()),
          reinterpret_cast<void*>(&nativeSessionProof) }
    };
    const jint methodCount = static_cast<jint>(sizeof(methods) / sizeof(methods[0]));
    const jint result = env->RegisterNatives(bridge, methods, methodCount);
    if (yozakuraThemidaAcceptRegistration(result, methodCount) == 0) {
        return false;
    }
    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK || !vm) {
        env->UnregisterNatives(bridge);
        return false;
    }
    jclass globalBridge = static_cast<jclass>(env->NewGlobalRef(bridge));
    if (!globalBridge) {
        env->UnregisterNatives(bridge);
        return false;
    }
    authVm = vm;
    authBridgeClass = globalBridge;
    return true;
}

void signalYozakuraNativeAuthShutdown() {
    if (stopEvent) {
        SetEvent(stopEvent);
    }
    closeDeviceKey();
}
