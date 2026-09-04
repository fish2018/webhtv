#include <jni.h>

#include <mpv/client.h>

#include "jni_utils.h"
#include "log.h"
#include "request.h"

extern "C" {
    jni_func(void, attachSurface, jobject surface);
    jni_func(void, replaceSurface, jobject surface);
    jni_func(void, detachSurface);
    jni_func(void, attachOsdSurface, jobject surface);
    jni_func(void, replaceOsdSurface, jobject surface);
    jni_func(void, detachOsdSurface);
    jni_func(jint, enqueueOsdSurface, jlong request_id, jobject surface);
};

static void enqueue_surface_or_throw(JNIEnv *env, SurfaceTarget target,
                                     jobject surface) {
    int result = enqueue_surface(env, target, surface);
    if (result < 0 && !env->ExceptionCheck())
        throw_java_exception(env, "failed to queue mpv surface update");
}

static void update_surface(JNIEnv *env, SurfaceTarget target, jobject surface) {
    if (!require_mpv_initialized(env))
        return;
    if (!surface) {
        throw_java_exception(env, "invalid surface provided");
        return;
    }
    enqueue_surface_or_throw(env, target, surface);
}

static void detach_surface(JNIEnv *env, SurfaceTarget target) {
    if (!require_mpv_initialized(env))
        return;
    enqueue_surface_or_throw(env, target, NULL);
}

jni_func(void, attachSurface, jobject surface) {
    update_surface(env, SurfaceTarget::VIDEO, surface);
}

jni_func(void, replaceSurface, jobject surface) {
    update_surface(env, SurfaceTarget::VIDEO, surface);
}

jni_func(void, detachSurface) {
    detach_surface(env, SurfaceTarget::VIDEO);
}

jni_func(void, attachOsdSurface, jobject surface) {
    update_surface(env, SurfaceTarget::OSD, surface);
}

jni_func(void, replaceOsdSurface, jobject surface) {
    update_surface(env, SurfaceTarget::OSD, surface);
}

jni_func(void, detachOsdSurface) {
    detach_surface(env, SurfaceTarget::OSD);
}

jni_func(jint, enqueueOsdSurface, jlong request_id, jobject surface) {
    if (!require_mpv_initialized(env))
        return MPV_ERROR_UNINITIALIZED;
    return enqueue_surface_async(env, static_cast<uint64_t>(request_id),
                                 SurfaceTarget::OSD, surface);
}
