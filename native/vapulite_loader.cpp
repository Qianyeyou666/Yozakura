#include <windows.h>
#include <jni.h>

#include <cstring>
#include <cwchar>
#include <cstdio>
#include <string>
#include <vector>

#define IDR_VAPULITE_JAR 101

#ifndef JAR_TO_DLL_CLIENT_CLASS
#define JAR_TO_DLL_CLIENT_CLASS "gq.vapulite.Vapu.Client"
#endif

#ifndef JAR_TO_DLL_LOG_NAME
#define JAR_TO_DLL_LOG_NAME "JarToDllLoader.log"
#endif

#ifndef JAR_TO_DLL_TEMP_PREFIX
#define JAR_TO_DLL_TEMP_PREFIX L"JarToDll"
#endif

static void debug(const char* message) {
    OutputDebugStringA("[JarToDllLoader] ");
    OutputDebugStringA(message);
    OutputDebugStringA("\n");

    char tempPath[MAX_PATH] = {};
    if (GetTempPathA(MAX_PATH, tempPath)) {
        std::string logPath = std::string(tempPath) + JAR_TO_DLL_LOG_NAME;
        HANDLE file = CreateFileA(logPath.c_str(), FILE_APPEND_DATA, FILE_SHARE_READ, nullptr, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (file != INVALID_HANDLE_VALUE) {
            DWORD written = 0;
            SYSTEMTIME time = {};
            GetLocalTime(&time);
            char line[1024] = {};
            sprintf_s(line, "[%04u-%02u-%02u %02u:%02u:%02u] %s\r\n",
                time.wYear, time.wMonth, time.wDay, time.wHour, time.wMinute, time.wSecond, message);
            WriteFile(file, line, static_cast<DWORD>(strlen(line)), &written, nullptr);
            CloseHandle(file);
        }
    }
}

static void debugLastError(const char* message) {
    char line[512] = {};
    sprintf_s(line, "%s GetLastError=%lu", message, GetLastError());
    debug(line);
}

static std::wstring tempJarPath() {
    wchar_t tempPath[MAX_PATH] = {};
    GetTempPathW(MAX_PATH, tempPath);

    DWORD pid = GetCurrentProcessId();
    DWORD tid = GetCurrentThreadId();
    ULONGLONG tick = GetTickCount64();
    wchar_t fileName[MAX_PATH] = {};
    swprintf_s(fileName, MAX_PATH, L"%s%s-%lu-%lu-%llu.jar", tempPath, JAR_TO_DLL_TEMP_PREFIX, pid, tid, tick);
    return std::wstring(fileName);
}

static void logJavaException(JNIEnv* env, const char* context) {
    if (!env->ExceptionCheck()) {
        return;
    }

    jthrowable throwable = env->ExceptionOccurred();
    env->ExceptionClear();

    jclass stringWriterClass = env->FindClass("java/io/StringWriter");
    jmethodID stringWriterCtor = env->GetMethodID(stringWriterClass, "<init>", "()V");
    jobject stringWriter = env->NewObject(stringWriterClass, stringWriterCtor);

    jclass printWriterClass = env->FindClass("java/io/PrintWriter");
    jmethodID printWriterCtor = env->GetMethodID(printWriterClass, "<init>", "(Ljava/io/Writer;)V");
    jobject printWriter = env->NewObject(printWriterClass, printWriterCtor, stringWriter);

    jclass throwableClass = env->FindClass("java/lang/Throwable");
    jmethodID printStackTrace = env->GetMethodID(throwableClass, "printStackTrace", "(Ljava/io/PrintWriter;)V");
    env->CallVoidMethod(throwable, printStackTrace, printWriter);

    jmethodID toString = env->GetMethodID(stringWriterClass, "toString", "()Ljava/lang/String;");
    jstring stack = static_cast<jstring>(env->CallObjectMethod(stringWriter, toString));
    const char* stackChars = env->GetStringUTFChars(stack, nullptr);

    debug(context);
    if (stackChars) {
        debug(stackChars);
        env->ReleaseStringUTFChars(stack, stackChars);
    }

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

static bool writeEmbeddedJar(HMODULE module, const std::wstring& path) {
    HRSRC res = FindResourceW(module, MAKEINTRESOURCEW(IDR_VAPULITE_JAR), MAKEINTRESOURCEW(10));
    if (!res) {
        debug("embedded jar resource not found");
        return false;
    }

    HGLOBAL loaded = LoadResource(module, res);
    DWORD size = SizeofResource(module, res);
    void* data = LockResource(loaded);
    if (!loaded || !size || !data) {
        debug("failed to read embedded jar resource");
        return false;
    }

    HANDLE file = CreateFileW(
        path.c_str(),
        GENERIC_WRITE,
        FILE_SHARE_READ,
        nullptr,
        CREATE_ALWAYS,
        FILE_ATTRIBUTE_TEMPORARY,
        nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        debugLastError("failed to create temp jar");
        return false;
    }

    DWORD written = 0;
    BOOL ok = WriteFile(file, data, size, &written, nullptr);
    CloseHandle(file);

    if (!ok || written != size) {
        debugLastError("failed to write complete temp jar");
        return false;
    }

    debug("embedded jar written");
    return true;
}

static std::string jniString(JNIEnv* env, const std::wstring& value) {
    if (value.empty()) {
        return std::string();
    }

    int required = WideCharToMultiByte(CP_UTF8, 0, value.c_str(), -1, nullptr, 0, nullptr, nullptr);
    std::string result(required - 1, '\0');
    WideCharToMultiByte(CP_UTF8, 0, value.c_str(), -1, &result[0], required, nullptr, nullptr);
    return result;
}

static jobject findClientThreadClassLoader(JNIEnv* env) {
    debug("searching Minecraft classloader");
    jclass threadClass = env->FindClass("java/lang/Thread");
    jmethodID getAllStackTraces = env->GetStaticMethodID(threadClass, "getAllStackTraces", "()Ljava/util/Map;");
    jmethodID getName = env->GetMethodID(threadClass, "getName", "()Ljava/lang/String;");
    jmethodID getContextClassLoader = env->GetMethodID(threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring minecraftName = env->NewStringUTF("net.minecraft.client.Minecraft");

    jobject traces = env->CallStaticObjectMethod(threadClass, getAllStackTraces);
    jclass mapClass = env->FindClass("java/util/Map");
    jmethodID keySet = env->GetMethodID(mapClass, "keySet", "()Ljava/util/Set;");
    jobject threads = env->CallObjectMethod(traces, keySet);

    jclass setClass = env->FindClass("java/util/Set");
    jmethodID toArray = env->GetMethodID(setClass, "toArray", "()[Ljava/lang/Object;");
    jobjectArray threadArray = static_cast<jobjectArray>(env->CallObjectMethod(threads, toArray));

    jsize count = env->GetArrayLength(threadArray);
    jobject minecraftLoader = nullptr;
    for (jsize i = 0; i < count; ++i) {
        jobject thread = env->GetObjectArrayElement(threadArray, i);
        jstring name = static_cast<jstring>(env->CallObjectMethod(thread, getName));
        const char* nameChars = env->GetStringUTFChars(name, nullptr);
        bool isClientThread = nameChars && strcmp(nameChars, "Client thread") == 0;
        env->ReleaseStringUTFChars(name, nameChars);

        jobject loader = env->CallObjectMethod(thread, getContextClassLoader);

        if (isClientThread) {
            env->DeleteLocalRef(thread);
            debug("Client thread classloader found by thread name");
            return loader;
        }

        if (loader && !minecraftLoader) {
            jobject minecraftClass = env->CallObjectMethod(loader, loadClass, minecraftName);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            } else if (minecraftClass) {
                minecraftLoader = env->NewGlobalRef(loader);
                debug("Minecraft classloader found by loadClass probe");
            }
        }

        env->DeleteLocalRef(thread);
    }

    return minecraftLoader;
}

static bool addJarToClassLoader(JNIEnv* env, jobject loader, const std::wstring& jarPath) {
    debug("adding jar to classloader");
    std::string pathUtf8 = jniString(env, jarPath);

    jclass fileClass = env->FindClass("java/io/File");
    jmethodID fileCtor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
    jstring path = env->NewStringUTF(pathUtf8.c_str());
    jobject file = env->NewObject(fileClass, fileCtor, path);

    jmethodID toURI = env->GetMethodID(fileClass, "toURI", "()Ljava/net/URI;");
    jobject uri = env->CallObjectMethod(file, toURI);

    jclass uriClass = env->FindClass("java/net/URI");
    jmethodID toURL = env->GetMethodID(uriClass, "toURL", "()Ljava/net/URL;");
    jobject url = env->CallObjectMethod(uri, toURL);

    jclass loaderClass = env->GetObjectClass(loader);
    jclass urlClass = env->FindClass("java/net/URL");
    jclass classClass = env->FindClass("java/lang/Class");
    jmethodID getMethod = env->GetMethodID(classClass, "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");

    jobjectArray params = env->NewObjectArray(1, classClass, urlClass);
    jstring addUrlName = env->NewStringUTF("addURL");
    jobject method = env->CallObjectMethod(loaderClass, getMethod, addUrlName, params);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        debug("classloader does not expose public addURL");
        return false;
    }

    jclass methodClass = env->FindClass("java/lang/reflect/Method");
    jmethodID invoke = env->GetMethodID(methodClass, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    jobjectArray args = env->NewObjectArray(1, env->FindClass("java/lang/Object"), url);
    env->CallObjectMethod(method, invoke, loader, args);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        debug("addURL invocation failed");
        return false;
    }

    debug("jar added to classloader");
    return true;
}

static bool instantiateClient(JNIEnv* env, jobject loader) {
    debug("loading client entry class");
    jclass threadClass = env->FindClass("java/lang/Thread");
    jmethodID currentThread = env->GetStaticMethodID(threadClass, "currentThread", "()Ljava/lang/Thread;");
    jmethodID setContextClassLoader = env->GetMethodID(threadClass, "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
    jobject thread = env->CallStaticObjectMethod(threadClass, currentThread);
    env->CallVoidMethod(thread, setContextClassLoader, loader);

    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring clientName = env->NewStringUTF(JAR_TO_DLL_CLIENT_CLASS);
    jclass clientClass = static_cast<jclass>(env->CallObjectMethod(loader, loadClass, clientName));
    if (env->ExceptionCheck() || !clientClass) {
        logJavaException(env, "failed to load client class");
        debug("failed to load client class");
        return false;
    }

    jmethodID ctor = env->GetMethodID(clientClass, "<init>", "()V");
    jobject client = env->NewObject(clientClass, ctor);
    if (env->ExceptionCheck() || !client) {
        logJavaException(env, "failed to instantiate client exception");
        debug("failed to instantiate client");
        return false;
    }

    debug("client instantiated");
    return true;
}

static DWORD WINAPI loaderThread(LPVOID param) {
    debug("loader thread started");
    HMODULE self = static_cast<HMODULE>(param);
    std::wstring jarPath = tempJarPath();
    if (!writeEmbeddedJar(self, jarPath)) {
        return 1;
    }

    HMODULE jvmModule = GetModuleHandleW(L"jvm.dll");
    if (!jvmModule) {
        debug("jvm.dll is not loaded in this process");
        return 1;
    }

    typedef jint(JNICALL* GetCreatedJavaVMsFn)(JavaVM**, jsize, jsize*);
    GetCreatedJavaVMsFn getCreatedJavaVMs = reinterpret_cast<GetCreatedJavaVMsFn>(
        GetProcAddress(jvmModule, "JNI_GetCreatedJavaVMs"));
    if (!getCreatedJavaVMs) {
        debug("JNI_GetCreatedJavaVMs not found");
        return 1;
    }

    JavaVM* vm = nullptr;
    jsize vmCount = 0;
    if (getCreatedJavaVMs(&vm, 1, &vmCount) != JNI_OK || vmCount == 0 || !vm) {
        debug("no running JVM found");
        return 1;
    }

    JNIEnv* env = nullptr;
    bool attached = false;
    jint envStatus = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8);
    if (envStatus == JNI_EDETACHED) {
        debug("attaching native thread to JVM");
        if (vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) {
            debug("failed to attach native thread to JVM");
            return 1;
        }
        attached = true;
    } else if (envStatus != JNI_OK) {
        debug("failed to get JNI environment");
        return 1;
    } else {
        debug("native thread already attached to JVM");
    }

    jobject loader = findClientThreadClassLoader(env);
    if (!loader) {
        debug("client thread classloader not found");
    } else if (addJarToClassLoader(env, loader, jarPath) && instantiateClient(env, loader)) {
        debug("client loaded");
    }

    if (attached) {
        vm->DetachCurrentThread();
    }
    return 0;
}

static void startLoader(HMODULE module) {
    static volatile LONG started = 0;
    if (InterlockedExchange(&started, 1) != 0) {
        debug("loader already started");
        return;
    }

    HANDLE thread = CreateThread(nullptr, 0, loaderThread, module, 0, nullptr);
    if (thread) {
        CloseHandle(thread);
    } else {
        debugLastError("failed to create loader thread");
    }
}

extern "C" __declspec(dllexport) void VapuLiteInject() {
    HMODULE module = nullptr;
    GetModuleHandleExW(
        GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
        reinterpret_cast<LPCWSTR>(&VapuLiteInject),
        &module);
    if (module) {
        startLoader(module);
    }
}

extern "C" __declspec(dllexport) void JarToDllInject() {
    VapuLiteInject();
}

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(module);
        startLoader(module);
    }
    return TRUE;
}
