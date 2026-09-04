#define UTIL_EXTERN
#include "jni_utils.h"

#include <jni.h>
#include <stdint.h>
#include <string.h>

// JNI string helpers use Modified UTF-8, while libmpv expects standard UTF-8.
// Reject embedded NUL on input and replace malformed Unicode on output.
static constexpr uint32_t UNICODE_REPLACEMENT_CHARACTER = 0xFFFD;

static void append_utf8(std::string &utf8, uint32_t code_point)
{
    if (code_point <= 0x7F) {
        utf8.push_back(static_cast<char>(code_point));
    } else if (code_point <= 0x7FF) {
        utf8.push_back(static_cast<char>(0xC0 | (code_point >> 6)));
        utf8.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
    } else if (code_point <= 0xFFFF) {
        utf8.push_back(static_cast<char>(0xE0 | (code_point >> 12)));
        utf8.push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3F)));
        utf8.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
    } else {
        utf8.push_back(static_cast<char>(0xF0 | (code_point >> 18)));
        utf8.push_back(static_cast<char>(0x80 | ((code_point >> 12) & 0x3F)));
        utf8.push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3F)));
        utf8.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
    }
}

bool jstring_to_utf8(JNIEnv *env, jstring value, std::string *utf8)
{
    if (!value || !utf8)
        return false;

    const jchar *chars = env->GetStringChars(value, NULL);
    if (!chars)
        return false;

    jsize length = env->GetStringLength(value);
    utf8->clear();
    utf8->reserve(static_cast<size_t>(length) * 3);
    bool has_no_embedded_nul = true;
    for (jsize i = 0; i < length; i++) {
        uint32_t code_point = chars[i];
        if (code_point == 0) {
            has_no_embedded_nul = false;
            break;
        }
        if (code_point >= 0xD800 && code_point <= 0xDBFF) {
            if (i + 1 < length && chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
                code_point = 0x10000 + ((code_point - 0xD800) << 10)
                    + (chars[++i] - 0xDC00);
            } else {
                code_point = UNICODE_REPLACEMENT_CHARACTER;
            }
        } else if (code_point >= 0xDC00 && code_point <= 0xDFFF) {
            code_point = UNICODE_REPLACEMENT_CHARACTER;
        }
        append_utf8(*utf8, code_point);
    }
    env->ReleaseStringChars(value, chars);
    if (!has_no_embedded_nul)
        utf8->clear();
    return has_no_embedded_nul;
}

static bool is_continuation(unsigned char value)
{
    return (value & 0xC0) == 0x80;
}

jstring utf8_to_jstring(JNIEnv *env, const char *value)
{
    if (!value)
        return NULL;

    const unsigned char *bytes = reinterpret_cast<const unsigned char *>(value);
    size_t length = strlen(value);
    std::u16string utf16;
    utf16.reserve(length);
    for (size_t i = 0; i < length;) {
        uint32_t code_point = UNICODE_REPLACEMENT_CHARACTER;
        size_t consumed = 1;
        unsigned char first = bytes[i];
        if (first <= 0x7F) {
            code_point = first;
        } else if (first >= 0xC2 && first <= 0xDF && i + 1 < length
                && is_continuation(bytes[i + 1])) {
            code_point = ((first & 0x1F) << 6) | (bytes[i + 1] & 0x3F);
            consumed = 2;
        } else if (first >= 0xE0 && first <= 0xEF && i + 2 < length
                && is_continuation(bytes[i + 1]) && is_continuation(bytes[i + 2])) {
            uint32_t candidate = ((first & 0x0F) << 12)
                | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F);
            if (candidate >= 0x800 && !(candidate >= 0xD800 && candidate <= 0xDFFF)) {
                code_point = candidate;
                consumed = 3;
            }
        } else if (first >= 0xF0 && first <= 0xF4 && i + 3 < length
                && is_continuation(bytes[i + 1]) && is_continuation(bytes[i + 2])
                && is_continuation(bytes[i + 3])) {
            uint32_t candidate = ((first & 0x07) << 18)
                | ((bytes[i + 1] & 0x3F) << 12)
                | ((bytes[i + 2] & 0x3F) << 6) | (bytes[i + 3] & 0x3F);
            if (candidate >= 0x10000 && candidate <= 0x10FFFF) {
                code_point = candidate;
                consumed = 4;
            }
        }

        i += consumed;
        if (code_point <= 0xFFFF) {
            utf16.push_back(static_cast<char16_t>(code_point));
        } else {
            code_point -= 0x10000;
            utf16.push_back(static_cast<char16_t>(0xD800 + (code_point >> 10)));
            utf16.push_back(static_cast<char16_t>(0xDC00 + (code_point & 0x3FF)));
        }
    }

    return env->NewString(reinterpret_cast<const jchar *>(utf16.data()),
                          static_cast<jsize>(utf16.size()));
}

void send_command_reply_to_java(JNIEnv *env, uint64_t request_id, int error)
{
    env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventCommandReply_Ji,
                              static_cast<jlong>(request_id),
                              static_cast<jint>(error));
}

bool acquire_jni_env(JavaVM *vm, JNIEnv **env)
{
    int ret = vm->GetEnv((void**) env, JNI_VERSION_1_6);
    if (ret == JNI_EDETACHED)
        return vm->AttachCurrentThread(env, NULL) == 0;
    else
        return ret == JNI_OK;
}

// Apparently it's considered slow to FindClass and GetMethodID every time we need them,
// so let's have a nice cache here.

static bool cache_global_class(JNIEnv *env, jclass *cached_class, const char *name)
{
    if (*cached_class)
        return true;
    jclass local_class = env->FindClass(name);
    if (!local_class)
        return false;
    *cached_class = reinterpret_cast<jclass>(env->NewGlobalRef(local_class));
    env->DeleteLocalRef(local_class);
    return *cached_class != NULL;
}

static bool cache_method(JNIEnv *env, jmethodID *cached_method, jclass clazz,
                         const char *name, const char *signature)
{
    if (!*cached_method)
        *cached_method = env->GetMethodID(clazz, name, signature);
    return *cached_method != NULL;
}

static bool cache_static_method(JNIEnv *env, jmethodID *cached_method, jclass clazz,
                                const char *name, const char *signature)
{
    if (!*cached_method)
        *cached_method = env->GetStaticMethodID(clazz, name, signature);
    return *cached_method != NULL;
}

static bool cache_static_field(JNIEnv *env, jfieldID *cached_field, jclass clazz,
                               const char *name, const char *signature)
{
    if (!*cached_field)
        *cached_field = env->GetStaticFieldID(clazz, name, signature);
    return *cached_field != NULL;
}

bool init_methods_cache(JNIEnv *env)
{
    static bool methods_initialized = false;
    if (methods_initialized)
        return true;

    bool success =
        cache_global_class(env, &java_Integer, "java/lang/Integer") &&
        cache_method(env, &java_Integer_init, java_Integer, "<init>", "(I)V") &&
        cache_global_class(env, &java_Double, "java/lang/Double") &&
        cache_method(env, &java_Double_init, java_Double, "<init>", "(D)V") &&
        cache_global_class(env, &java_Boolean, "java/lang/Boolean") &&
        cache_method(env, &java_Boolean_init, java_Boolean, "<init>", "(Z)V") &&
        cache_global_class(env, &android_graphics_Bitmap, "android/graphics/Bitmap") &&
        cache_global_class(env, &android_graphics_Bitmap_Config,
                           "android/graphics/Bitmap$Config") &&
        cache_static_method(
            env, &android_graphics_Bitmap_createBitmap, android_graphics_Bitmap,
            "createBitmap",
            "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;") &&
        cache_static_field(
            env, &android_graphics_Bitmap_Config_ARGB_8888, android_graphics_Bitmap_Config,
            "ARGB_8888", "Landroid/graphics/Bitmap$Config;") &&
        cache_global_class(env, &mpv_MPVLib, "is/xyz/mpv/MPVLib") &&
        cache_static_method(env, &mpv_MPVLib_eventProperty_S, mpv_MPVLib,
                            "eventProperty", "(Ljava/lang/String;)V") &&
        cache_static_method(env, &mpv_MPVLib_eventProperty_Sb, mpv_MPVLib,
                            "eventProperty", "(Ljava/lang/String;Z)V") &&
        cache_static_method(env, &mpv_MPVLib_eventProperty_Sl, mpv_MPVLib,
                            "eventProperty", "(Ljava/lang/String;J)V") &&
        cache_static_method(env, &mpv_MPVLib_eventProperty_Sd, mpv_MPVLib,
                            "eventProperty", "(Ljava/lang/String;D)V") &&
        cache_static_method(
            env, &mpv_MPVLib_eventProperty_SS, mpv_MPVLib,
            "eventProperty", "(Ljava/lang/String;Ljava/lang/String;)V") &&
        cache_static_method(env, &mpv_MPVLib_event, mpv_MPVLib,
                            "event", "(I)V") &&
        cache_static_method(env, &mpv_MPVLib_eventCommandReply_Ji, mpv_MPVLib,
                            "eventCommandReply", "(JI)V") &&
        cache_static_method(
            env, &mpv_MPVLib_eventEndFile_iiS, mpv_MPVLib,
            "eventEndFile", "(IILjava/lang/String;)V") &&
        cache_static_method(
            env, &mpv_MPVLib_logMessage_SiS, mpv_MPVLib,
            "logMessage", "(Ljava/lang/String;ILjava/lang/String;)V");
    if (!success)
        return false;

    methods_initialized = true;
    return true;
}
