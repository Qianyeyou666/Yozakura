#pragma once

#include <jni.h>

bool registerYozakuraNativeAuth(JNIEnv* env, jobject loader);
void signalYozakuraNativeAuthShutdown();
