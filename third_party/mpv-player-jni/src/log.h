#pragma once

#include <android/log.h>
#include <jni.h>

#define DEBUG 1

#define LOG_TAG "mpv"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#if DEBUG
#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#else
#define ALOGV(...) (void)0
#endif

void throw_java_exception(JNIEnv *env, const char *msg);
bool check_mpv_initialized();
bool require_mpv_initialized(JNIEnv *env);
