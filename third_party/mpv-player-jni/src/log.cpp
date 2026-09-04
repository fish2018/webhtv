#include "log.h"

#include <mpv/client.h>

#include "globals.h"

void throw_java_exception(JNIEnv *env, const char *msg)
{
    ALOGE("%s", msg);
    if (!env || env->ExceptionCheck())
        return;

    jclass exception_class = env->FindClass("java/lang/IllegalStateException");
    if (!exception_class)
        return;
    env->ThrowNew(exception_class, msg);
    env->DeleteLocalRef(exception_class);
}

bool check_mpv_initialized()
{
    if (__builtin_expect(g_mpv != NULL && !g_shutdown_requested, 1))
        return true;
    ALOGE("libmpv is not initialized or is shutting down");
    return false;
}

bool require_mpv_initialized(JNIEnv *env)
{
    if (__builtin_expect(g_mpv != NULL && !g_shutdown_requested, 1))
        return true;
    throw_java_exception(env, "libmpv is not initialized or is shutting down");
    return false;
}
