#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string>

#include <mpv/client.h>

#include "jni_utils.h"
#include "log.h"
#include "globals.h"

extern "C" {
    jni_func(jint, setOptionString, jstring option, jstring value);

    jni_func(jobject, getPropertyInt, jstring property);
    jni_func(jint, setPropertyInt, jstring property, jint value);
    jni_func(jobject, getPropertyDouble, jstring property);
    jni_func(jint, setPropertyDouble, jstring property, jdouble value);
    jni_func(jobject, getPropertyBoolean, jstring property);
    jni_func(jint, setPropertyBoolean, jstring property, jboolean value);
    jni_func(jstring, getPropertyString, jstring jproperty);
    jni_func(jint, setPropertyString, jstring jproperty, jstring jvalue);
    jni_func(jbyteArray, getPropertyByteArray, jstring jproperty);
    jni_func(void, dumpTrackList);

    jni_func(jint, observeProperty, jstring property, jint format);
}

static int get_utf8(JNIEnv *env, jstring string, std::string *value)
{
    if (!string)
        return MPV_ERROR_INVALID_PARAMETER;
    if (jstring_to_utf8(env, string, value))
        return MPV_ERROR_SUCCESS;
    return env->ExceptionCheck() ? MPV_ERROR_NOMEM : MPV_ERROR_INVALID_PARAMETER;
}

static void log_node(const char *path, const mpv_node &node) {
    if (node.format == MPV_FORMAT_NODE_ARRAY || node.format == MPV_FORMAT_NODE_MAP) {
        mpv_node_list *list = node.u.list;
        if (!list) return;
        for (int i = 0; i < list->num; ++i) {
            char child[256];
            const char *key = node.format == MPV_FORMAT_NODE_MAP && list->keys ? list->keys[i] : nullptr;
            if (key) snprintf(child, sizeof(child), "%s/%s", path, key);
            else snprintf(child, sizeof(child), "%s/%d", path, i);
            log_node(child, list->values[i]);
        }
        return;
    }
    switch (node.format) {
        case MPV_FORMAT_STRING: ALOGV("mpv-node %s string=%s", path, node.u.string ? node.u.string : ""); break;
        case MPV_FORMAT_FLAG: ALOGV("mpv-node %s flag=%d", path, node.u.flag); break;
        case MPV_FORMAT_INT64: ALOGV("mpv-node %s int=%lld", path, static_cast<long long>(node.u.int64)); break;
        case MPV_FORMAT_DOUBLE: ALOGV("mpv-node %s double=%f", path, node.u.double_); break;
        case MPV_FORMAT_NONE: ALOGV("mpv-node %s none", path); break;
        default: ALOGV("mpv-node %s format=%d", path, node.format); break;
    }
}

jni_func(void, dumpTrackList) {
    if (!check_mpv_initialized())
        return;
    mpv_node node{};
    int result = mpv_get_property(g_mpv, "track-list", MPV_FORMAT_NODE, &node);
    if (result < 0) {
        ALOGE("mpv track-list node failed: %s", mpv_error_string(result));
        return;
    }
    log_node("track-list", node);
    mpv_free_node_contents(&node);
}

jni_func(jint, setOptionString, jstring joption, jstring jvalue) {
    if (!check_mpv_initialized())
        return MPV_ERROR_UNINITIALIZED;

    std::string option;
    std::string value;

    int result = get_utf8(env, joption, &option);
    if (result >= 0)
        result = get_utf8(env, jvalue, &value);
    if (result >= 0)
        result = mpv_set_option_string(g_mpv, option.c_str(), value.c_str());

    return result;
}

static int common_get_property(JNIEnv *env, jstring jproperty, mpv_format format, void *output)
{
    if (!check_mpv_initialized())
        return MPV_ERROR_UNINITIALIZED;

    std::string prop;
    int result = get_utf8(env, jproperty, &prop);
    if (result < 0)
        return result;

    result = mpv_get_property(g_mpv, prop.c_str(), format, output);
    if (result == MPV_ERROR_PROPERTY_UNAVAILABLE)
        ALOGV("mpv_get_property(%s) format %d was unavailable", prop.c_str(), format);
    else if (result < 0)
        ALOGE("mpv_get_property(%s) format %d returned error %s", prop.c_str(), format, mpv_error_string(result));

    return result;
}

static int common_set_property(JNIEnv *env, jstring jproperty, mpv_format format, void *value)
{
    if (!check_mpv_initialized())
        return MPV_ERROR_UNINITIALIZED;

    std::string prop;
    int result = get_utf8(env, jproperty, &prop);
    if (result < 0)
        return result;

    result = mpv_set_property(g_mpv, prop.c_str(), format, value);
    if (result < 0)
        ALOGE("mpv_set_property(%s, %p) format %d returned error %s", prop.c_str(), value, format, mpv_error_string(result));

    return result;
}

static jbyteArray new_byte_array(JNIEnv *env, const struct mpv_byte_array *bytes)
{
    if (!bytes || bytes->size > static_cast<size_t>(INT32_MAX) ||
            (bytes->size > 0 && !bytes->data))
        return NULL;

    jsize size = static_cast<jsize>(bytes->size);
    jbyteArray result = env->NewByteArray(size);
    if (!result || size == 0)
        return result;

    env->SetByteArrayRegion(result, 0, size, reinterpret_cast<const jbyte *>(bytes->data));
    return result;
}

jni_func(jobject, getPropertyInt, jstring jproperty) {
    int64_t value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_INT64, &value) < 0)
        return NULL;
    return env->NewObject(java_Integer, java_Integer_init, (jint)value);
}

jni_func(jobject, getPropertyDouble, jstring jproperty) {
    double value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_DOUBLE, &value) < 0)
        return NULL;
    return env->NewObject(java_Double, java_Double_init, (jdouble)value);
}

jni_func(jobject, getPropertyBoolean, jstring jproperty) {
    int value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_FLAG, &value) < 0)
        return NULL;
    return env->NewObject(java_Boolean, java_Boolean_init, (jboolean)value);
}

jni_func(jstring, getPropertyString, jstring jproperty) {
    char *value;
    if (common_get_property(env, jproperty, MPV_FORMAT_STRING, &value) < 0)
        return NULL;
    jstring jvalue = utf8_to_jstring(env, value);
    mpv_free(value);
    return jvalue;
}

jni_func(jbyteArray, getPropertyByteArray, jstring jproperty) {
    mpv_node node{};
    if (common_get_property(env, jproperty, MPV_FORMAT_NODE, &node) < 0)
        return NULL;
    jbyteArray result = node.format == MPV_FORMAT_BYTE_ARRAY ?
            new_byte_array(env, node.u.ba) : NULL;
    mpv_free_node_contents(&node);
    return result;
}

jni_func(jint, setPropertyInt, jstring jproperty, jint jvalue) {
    int64_t value = static_cast<int64_t>(jvalue);
    return common_set_property(env, jproperty, MPV_FORMAT_INT64, &value);
}

jni_func(jint, setPropertyDouble, jstring jproperty, jdouble jvalue) {
    double value = static_cast<double>(jvalue);
    return common_set_property(env, jproperty, MPV_FORMAT_DOUBLE, &value);
}

jni_func(jint, setPropertyBoolean, jstring jproperty, jboolean jvalue) {
    int value = jvalue == JNI_TRUE ? 1 : 0;
    return common_set_property(env, jproperty, MPV_FORMAT_FLAG, &value);
}

jni_func(jint, setPropertyString, jstring jproperty, jstring jvalue) {
    std::string value;
    int result = get_utf8(env, jvalue, &value);
    const char *value_chars = value.c_str();
    if (result >= 0)
        result = common_set_property(env, jproperty, MPV_FORMAT_STRING, &value_chars);
    return result;
}

jni_func(jint, observeProperty, jstring property, jint format) {
    if (!check_mpv_initialized())
        return MPV_ERROR_UNINITIALIZED;
    std::string prop;
    int result = get_utf8(env, property, &prop);
    if (result < 0)
        return result;

    result = mpv_observe_property(g_mpv, 0, prop.c_str(), (mpv_format)format);
    if (result < 0)
        ALOGE("mpv_observe_property(%s) format %d returned error %s", prop.c_str(), format, mpv_error_string(result));
    return result;
}
