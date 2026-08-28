#include <windows.h>
#include <jni.h>

#include <cstring>
#include <cwchar>
#include <cstdio>
#include <string>
#include <vector>

#include "yozakura_native_auth.h"
#include "yozakura_webview2.h"
#include "yozakura_string_obfuscation.h"

#define IDR_YOZAKURA_JAR 101

#ifndef JAR_TO_DLL_CLIENT_CLASS
#define JAR_TO_DLL_CLIENT_CLASS "gq.yozakura.YozakuraBootstrap"
#endif

#ifndef JAR_TO_DLL_TEMP_PREFIX
#define JAR_TO_DLL_TEMP_PREFIX L"j8c"
#endif

class ProcessInjectionGuard {
public:
    explicit ProcessInjectionGuard(HANDLE handle) : handle_(handle), retained_(false) {
    }

    ~ProcessInjectionGuard() {
        if (handle_ && !retained_) {
            CloseHandle(handle_);
        }
    }

    void retainForProcessLifetime() {
        retained_ = true;
    }

private:
    HANDLE handle_;
    bool retained_;
};

static HANDLE acquireProcessInjectionGuard() {
    wchar_t guardName[256] = {};
    swprintf_s(guardName,
               L"Local\\%ls-%lu",
               JAR_TO_DLL_TEMP_PREFIX,
               GetCurrentProcessId());

    SetLastError(ERROR_SUCCESS);
    HANDLE guard = CreateMutexW(nullptr, FALSE, guardName);
    DWORD createError = GetLastError();
    if (!guard) {
        return nullptr;
    }
    if (createError == ERROR_ALREADY_EXISTS) {
        CloseHandle(guard);
        return nullptr;
    }

    return guard;
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

static void clearJavaException(JNIEnv* env) {
    if (env && env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

static bool writeEmbeddedJar(HMODULE module, const std::wstring& path) {
    HRSRC res = FindResourceW(module, MAKEINTRESOURCEW(IDR_YOZAKURA_JAR), MAKEINTRESOURCEW(10));
    if (!res) {
        return false;
    }

    HGLOBAL loaded = LoadResource(module, res);
    DWORD size = SizeofResource(module, res);
    void* data = LockResource(loaded);
    if (!loaded || !size || !data) {
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
        return false;
    }

    DWORD written = 0;
    BOOL ok = WriteFile(file, data, size, &written, nullptr);
    CloseHandle(file);

    if (!ok || written != size) {
        return false;
    }

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
    jclass threadClass = env->FindClass("java/lang/Thread");
    jmethodID getAllStackTraces = env->GetStaticMethodID(threadClass, "getAllStackTraces", "()Ljava/util/Map;");
    jmethodID getName = env->GetMethodID(threadClass, "getName", "()Ljava/lang/String;");
    jmethodID getContextClassLoader = env->GetMethodID(threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    std::string minecraftClass = YOZAKURA_PROTECTED_STRING("net.minecraft.client.Minecraft");
    std::string forgeClass = YOZAKURA_PROTECTED_STRING("net.minecraftforge.common.MinecraftForge");
    std::string modLauncherClass = YOZAKURA_PROTECTED_STRING("cpw.mods.modlauncher.Launcher");
    std::string obfuscatedMinecraftClass = YOZAKURA_PROTECTED_STRING("ave");
    jstring minecraftName = env->NewStringUTF(minecraftClass.c_str());
    jstring forgeName = env->NewStringUTF(forgeClass.c_str());
    jstring modLauncherName = env->NewStringUTF(modLauncherClass.c_str());
    jstring obfuscatedMinecraftName = env->NewStringUTF(obfuscatedMinecraftClass.c_str());

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
        bool isClientThread = nameChars
            && (strcmp(nameChars, "Client thread") == 0
                || strcmp(nameChars, "Render thread") == 0
                || strcmp(nameChars, "Minecraft main thread") == 0
                || strstr(nameChars, "Minecraft") != nullptr);
        env->ReleaseStringUTFChars(name, nameChars);

        jobject loader = env->CallObjectMethod(thread, getContextClassLoader);

        if (isClientThread && loader) {
            env->DeleteLocalRef(thread);
            return loader;
        }

        if (loader && !minecraftLoader) {
            jobject minecraftClass = env->CallObjectMethod(loader, loadClass, minecraftName);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                minecraftClass = env->CallObjectMethod(loader, loadClass, forgeName);
            }
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                minecraftClass = env->CallObjectMethod(loader, loadClass, modLauncherName);
            }
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                minecraftClass = env->CallObjectMethod(loader, loadClass, obfuscatedMinecraftName);
            }
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            } else if (minecraftClass) {
                minecraftLoader = env->NewGlobalRef(loader);
            }
        }

        env->DeleteLocalRef(thread);
    }

    return minecraftLoader;
}

static jobject findAddUrlMethod(JNIEnv* env, jclass loaderClass, jobjectArray params) {
    jclass classClass = env->FindClass("java/lang/Class");
    jstring addUrlName = env->NewStringUTF("addURL");
    jmethodID getMethod = env->GetMethodID(classClass, "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
    jobject method = env->CallObjectMethod(loaderClass, getMethod, addUrlName, params);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    } else if (method) {
        return method;
    }

    jmethodID getDeclaredMethod = env->GetMethodID(classClass, "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
    jmethodID getSuperclass = env->GetMethodID(classClass, "getSuperclass", "()Ljava/lang/Class;");
    jclass current = loaderClass;
    while (current) {
        method = env->CallObjectMethod(current, getDeclaredMethod, addUrlName, params);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        } else if (method) {
            jclass methodClass = env->FindClass("java/lang/reflect/Method");
            jmethodID setAccessible = env->GetMethodID(methodClass, "setAccessible", "(Z)V");
            env->CallVoidMethod(method, setAccessible, JNI_TRUE);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            return method;
        }
        current = static_cast<jclass>(env->CallObjectMethod(current, getSuperclass));
    }
    return nullptr;
}

static bool addJarToClassLoader(JNIEnv* env, jobject loader, const std::wstring& jarPath) {
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

    jobjectArray params = env->NewObjectArray(1, classClass, urlClass);
    jobject method = findAddUrlMethod(env, loaderClass, params);
    if (!method) {
        return false;
    }

    jclass methodClass = env->FindClass("java/lang/reflect/Method");
    jmethodID invoke = env->GetMethodID(methodClass, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    jobjectArray args = env->NewObjectArray(1, env->FindClass("java/lang/Object"), url);
    env->CallObjectMethod(method, invoke, loader, args);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        return false;
    }

    return true;
}

static bool canLoadClass(JNIEnv* env, jobject loader, const char* className) {
    if (!loader || !className) {
        return false;
    }
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = env->NewStringUTF(className);
    jobject loaded = env->CallObjectMethod(loader, loadClass, name);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    return loaded != nullptr;
}

static jobject createChildClassLoader(JNIEnv* env, jobject parent, const std::wstring& jarPath) {
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

    jclass urlClass = env->FindClass("java/net/URL");
    jobjectArray urls = env->NewObjectArray(1, urlClass, url);

    jclass urlClassLoaderClass = env->FindClass("java/net/URLClassLoader");
    jmethodID ctor = env->GetMethodID(urlClassLoaderClass, "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    jobject child = env->NewObject(urlClassLoaderClass, ctor, urls, parent);
    if (env->ExceptionCheck() || !child) {
        clearJavaException(env);
        return nullptr;
    }
    return child;
}

static jobject createIsolatedClassLoader(JNIEnv* env, jobject parent, const std::wstring& jarPath) {
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

    jclass urlClass = env->FindClass("java/net/URL");
    jobjectArray urls = env->NewObjectArray(1, urlClass, url);

    jclass urlClassLoaderClass = env->FindClass("java/net/URLClassLoader");
    jmethodID urlClassLoaderCtor = env->GetMethodID(urlClassLoaderClass, "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    jobject helperLoader = env->NewObject(urlClassLoaderClass, urlClassLoaderCtor, urls, parent);
    if (env->ExceptionCheck() || !helperLoader) {
        clearJavaException(env);
        return nullptr;
    }

    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    std::string isolatedClassName =
        YOZAKURA_PROTECTED_STRING("gq.yozakura.bridge.IsolatedClientClassLoader");
    jstring isolatedName = env->NewStringUTF(isolatedClassName.c_str());
    jclass isolatedClass = static_cast<jclass>(env->CallObjectMethod(helperLoader, loadClass, isolatedName));
    if (env->ExceptionCheck() || !isolatedClass) {
        clearJavaException(env);
        return nullptr;
    }

    jmethodID isolatedCtor = env->GetMethodID(isolatedClass, "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    jobject isolatedLoader = env->NewObject(isolatedClass, isolatedCtor, urls, parent);
    if (env->ExceptionCheck() || !isolatedLoader) {
        clearJavaException(env);
        return nullptr;
    }

    return isolatedLoader;
}

static bool instantiateClient(JNIEnv* env, jobject loader, bool* constructorAttempted) {
    jclass threadClass = env->FindClass("java/lang/Thread");
    jmethodID currentThread = env->GetStaticMethodID(threadClass, "currentThread", "()Ljava/lang/Thread;");
    jmethodID setContextClassLoader = env->GetMethodID(threadClass, "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
    jobject thread = env->CallStaticObjectMethod(threadClass, currentThread);
    env->CallVoidMethod(thread, setContextClassLoader, loader);

    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    std::string clientClassName = YOZAKURA_PROTECTED_STRING(JAR_TO_DLL_CLIENT_CLASS);
    jstring clientName = env->NewStringUTF(clientClassName.c_str());
    jclass clientClass = static_cast<jclass>(env->CallObjectMethod(loader, loadClass, clientName));
    if (env->ExceptionCheck() || !clientClass) {
        clearJavaException(env);
        return false;
    }

    jmethodID ctor = env->GetMethodID(clientClass, "<init>", "()V");
    if (env->ExceptionCheck() || !ctor) {
        clearJavaException(env);
        return false;
    }
    if (constructorAttempted) {
        *constructorAttempted = true;
    }
    jobject client = env->NewObject(clientClass, ctor);
    if (env->ExceptionCheck() || !client) {
        clearJavaException(env);
        return false;
    }

    return true;
}

static DWORD WINAPI loaderThread(LPVOID param) {
    HANDLE guardHandle = acquireProcessInjectionGuard();
    if (!guardHandle) {
        return ERROR_ALREADY_EXISTS;
    }
    ProcessInjectionGuard injectionGuard(guardHandle);

    HMODULE self = static_cast<HMODULE>(param);
    std::wstring jarPath = tempJarPath();
    if (!writeEmbeddedJar(self, jarPath)) {
        return 1;
    }

    HMODULE jvmModule = GetModuleHandleW(L"jvm.dll");
    if (!jvmModule) {
        return 1;
    }

    typedef jint(JNICALL* GetCreatedJavaVMsFn)(JavaVM**, jsize, jsize*);
    GetCreatedJavaVMsFn getCreatedJavaVMs = reinterpret_cast<GetCreatedJavaVMsFn>(
        GetProcAddress(jvmModule, "JNI_GetCreatedJavaVMs"));
    if (!getCreatedJavaVMs) {
        return 1;
    }

    JavaVM* vm = nullptr;
    jsize vmCount = 0;
    if (getCreatedJavaVMs(&vm, 1, &vmCount) != JNI_OK || vmCount == 0 || !vm) {
        return 1;
    }

    JNIEnv* env = nullptr;
    bool attached = false;
    jint envStatus = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8);
    if (envStatus == JNI_EDETACHED) {
        if (vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) {
            return 1;
        }
        attached = true;
    } else if (envStatus != JNI_OK) {
        return 1;
    } else {
    }

    bool clientLoaded = false;
    bool constructorAttempted = false;
    jobject loader = findClientThreadClassLoader(env);
    if (!loader) {
    } else {
        jobject entryLoader = nullptr;
        if (canLoadClass(env, loader, "net.minecraftforge.common.MinecraftForge")) {
            if (addJarToClassLoader(env, loader, jarPath)) {
                entryLoader = loader;
            } else {
                entryLoader = createIsolatedClassLoader(env, loader, jarPath);
                if (!entryLoader) {
                    entryLoader = createChildClassLoader(env, loader, jarPath);
                }
            }
        } else {
            entryLoader = createIsolatedClassLoader(env, loader, jarPath);
            if (!entryLoader && !addJarToClassLoader(env, loader, jarPath)) {
                entryLoader = createChildClassLoader(env, loader, jarPath);
            }
        }
        if (entryLoader && !registerYozakuraNativeAuth(env, entryLoader)) {
            clearJavaException(env);
            entryLoader = nullptr;
        }
        if (entryLoader && !registerYozakuraWebView2(env, entryLoader)) {
            entryLoader = nullptr;
        }
        if (entryLoader && !registerYozakuraPanelClickGuiCursor(env, entryLoader)) {
            entryLoader = nullptr;
        }
        if (entryLoader && instantiateClient(env, entryLoader, &constructorAttempted)) {
            clientLoaded = true;
        }
    }

    if (attached) {
        vm->DetachCurrentThread();
    }
    if (clientLoaded || constructorAttempted) {
        injectionGuard.retainForProcessLifetime();
        return clientLoaded ? 0 : 1;
    }
    return 1;
}

static void startLoader(HMODULE module) {
    static volatile LONG started = 0;
    if (InterlockedExchange(&started, 1) != 0) {
        return;
    }

    HANDLE thread = CreateThread(nullptr, 0, loaderThread, module, 0, nullptr);
    if (thread) {
        CloseHandle(thread);
    } else {
    }
}

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(module);
        startLoader(module);
    } else if (reason == DLL_PROCESS_DETACH) {
        shutdownYozakuraWebView2();
        signalYozakuraNativeAuthShutdown();
    }
    return TRUE;
}
