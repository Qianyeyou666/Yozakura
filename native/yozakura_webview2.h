#pragma once

#include <jni.h>

bool registerYozakuraWebView2(JNIEnv* env, jobject loader);
bool registerYozakuraPanelClickGuiCursor(JNIEnv* env, jobject loader);
void shutdownYozakuraWebView2();
