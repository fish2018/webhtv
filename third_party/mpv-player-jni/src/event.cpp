#include <jni.h>

#include <mpv/client.h>

#include "globals.h"
#include "jni_utils.h"
#include "log.h"
#include "request.h"

static void sendPropertyUpdateToJava(JNIEnv *env, mpv_event_property *prop)
{
    jstring jprop = utf8_to_jstring(env, prop->name);
    jstring jvalue = NULL;
    switch (prop->format) {
    case MPV_FORMAT_NONE:
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_S, jprop);
        break;
    case MPV_FORMAT_FLAG:
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_Sb, jprop,
            (jboolean) (*(int*)prop->data != 0));
        break;
    case MPV_FORMAT_INT64:
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_Sl, jprop,
            (jlong) *(int64_t*)prop->data);
        break;
    case MPV_FORMAT_DOUBLE:
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_Sd, jprop,
            (jdouble) *(double*)prop->data);
        break;
    case MPV_FORMAT_STRING:
        jvalue = utf8_to_jstring(env, *(const char**)prop->data);
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_SS, jprop, jvalue);
        break;
    default:
        ALOGV("sendPropertyUpdateToJava: Unknown property update format received in callback: %d!", prop->format);
        break;
    }
    if (jprop)
        env->DeleteLocalRef(jprop);
    if (jvalue)
        env->DeleteLocalRef(jvalue);
}

static void sendEventToJava(JNIEnv *env, int event)
{
    env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_event, event);
}

static void sendEndFileEventToJava(JNIEnv *env, mpv_event_end_file *event)
{
    int reason = event ? event->reason : MPV_END_FILE_REASON_EOF;
    int error = event ? event->error : MPV_ERROR_SUCCESS;
    jstring jerror = NULL;
    if (error < MPV_ERROR_SUCCESS) {
        const char *error_string = mpv_error_string(error);
        if (error_string)
            jerror = utf8_to_jstring(env, error_string);
    }

    env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventEndFile_iiS,
        (jint) reason, (jint) error, jerror);

    if (jerror)
        env->DeleteLocalRef(jerror);
}

static void sendLogMessageToJava(JNIEnv *env, mpv_event_log_message *msg)
{
    jstring jprefix = utf8_to_jstring(env, msg->prefix);
    jstring jtext = utf8_to_jstring(env, msg->text);

    env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_logMessage_SiS,
        jprefix, (jint) msg->log_level, jtext);

    if (jprefix)
        env->DeleteLocalRef(jprefix);
    if (jtext)
        env->DeleteLocalRef(jtext);
}

static void clearJavaCallbackException(JNIEnv *env)
{
    if (!env->ExceptionCheck())
        return;
    ALOGE("java event callback raised an exception");
    env->ExceptionDescribe();
    env->ExceptionClear();
}

static void finishShutdown(JNIEnv *env, bool force)
{
    mpv_handle *context = g_mpv.exchange(NULL);
    if (context) {
        if (force)
            mpv_terminate_destroy(context);
        else
            mpv_destroy(context);
        release_requests(env);
    }

    g_force_shutdown = false;
    g_event_thread_started = false;
    sendEventToJava(env, MPV_EVENT_SHUTDOWN);
    clearJavaCallbackException(env);
    g_shutdown_requested = false;
}

void *event_thread(void *arg)
{
    JNIEnv *env = NULL;
    acquire_jni_env(g_vm, &env);
    if (!env) {
        ALOGE("failed to acquire java env");
        return NULL;
    }

    while (1) {
        mpv_event *mp_event;
        mpv_event_property *mp_property = NULL;
        mpv_event_log_message *msg = NULL;

        mp_event = mpv_wait_event(g_mpv, -1.0);

        if (g_force_shutdown) {
            finishShutdown(env, true);
            break;
        }

        if (mp_event->event_id == MPV_EVENT_NONE)
            continue;

        switch (mp_event->event_id) {
        case MPV_EVENT_SHUTDOWN:
            ALOGV("event: %s\n", mpv_event_name(mp_event->event_id));
            g_shutdown_requested = true;
            finishShutdown(env, false);
            goto done;
        case MPV_EVENT_LOG_MESSAGE:
            msg = (mpv_event_log_message*)mp_event->data;
            ALOGV("[%s:%s] %s", msg->prefix, msg->level, msg->text);
            sendLogMessageToJava(env, msg);
            break;
        case MPV_EVENT_PROPERTY_CHANGE:
            mp_property = (mpv_event_property*)mp_event->data;
            sendPropertyUpdateToJava(env, mp_property);
            break;
        case MPV_EVENT_SET_PROPERTY_REPLY:
        case MPV_EVENT_COMMAND_REPLY:
            handle_request_reply(env, mp_event);
            break;
        case MPV_EVENT_END_FILE:
            ALOGV("event: %s\n", mpv_event_name(mp_event->event_id));
            sendEndFileEventToJava(env, (mpv_event_end_file*)mp_event->data);
            break;
        default:
            ALOGV("event: %s\n", mpv_event_name(mp_event->event_id));
            sendEventToJava(env, mp_event->event_id);
            break;
        }
        clearJavaCallbackException(env);
    }

done:
    g_vm->DetachCurrentThread();

    return NULL;
}
