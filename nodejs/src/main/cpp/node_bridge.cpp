// nodejs-mobile 的 libnode.so 只导出 C++ 修饰名 node::Start(int, char**)，
// 没有 C 入口，所以 Java 侧没法直接 JNI 过去。这里用 dlopen/dlsym 取到它再转调。
//
// libnode.so 不打进 APK：体积 60MB 量级，改成运行时按设备架构下载到应用私有目录，
// 因此只能在运行时按绝对路径 dlopen，不能在 CMake 里做链接期依赖。

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <pthread.h>
#include <unistd.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#define TAG "cnode"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

// Itanium C++ ABI 修饰名，arm64 与 armv7 一致
constexpr const char *kStartSymbol = "_ZN4node5StartEiPPc";

using NodeStart = int (*)(int, char **);

void *g_handle = nullptr;
NodeStart g_start = nullptr;
int g_pipe[2] = {-1, -1};

// Node 的 console.log/error 走 stdout/stderr，Android 上默认被丢弃。
// 接到管道再转 logcat，否则 bundle 里的报错完全看不到。
void *pumpStdio(void *) {
    char buf[2048];
    ssize_t n;
    while ((n = read(g_pipe[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[n - 1] == '\n') --n;
        buf[n] = '\0';
        __android_log_write(ANDROID_LOG_INFO, "cnode-js", buf);
    }
    return nullptr;
}

void redirectStdio() {
    if (g_pipe[0] != -1) return;
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    if (pipe(g_pipe) != 0) return;
    dup2(g_pipe[1], STDOUT_FILENO);
    dup2(g_pipe[1], STDERR_FILENO);
    pthread_t thread;
    if (pthread_create(&thread, nullptr, pumpStdio, nullptr) == 0) pthread_detach(thread);
}

std::string jstr(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *raw = env->GetStringUTFChars(s, nullptr);
    std::string out(raw == nullptr ? "" : raw);
    if (raw != nullptr) env->ReleaseStringUTFChars(s, raw);
    return out;
}

}  // namespace

// 必须按「本 so 自身」的架构去挑 libnode，而不是设备支持列表：
// x86_64 模拟器跑 arm64 包时 SUPPORTED_ABIS 首项是 x86_64，但调用方是 arm64，
// dlopen 一个 x86_64 的 libnode 会直接失败（unexpected e_machine）。
extern "C" JNIEXPORT jstring JNICALL
Java_com_fongmi_android_tv_nodejs_NodeBridge_nativeAbi(JNIEnv *env, jclass) {
#if defined(__aarch64__)
    return env->NewStringUTF("arm64-v8a");
#elif defined(__arm__)
    return env->NewStringUTF("armeabi-v7a");
#elif defined(__x86_64__)
    return env->NewStringUTF("x86_64");
#elif defined(__i386__)
    return env->NewStringUTF("x86");
#else
    return env->NewStringUTF("");
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_fongmi_android_tv_nodejs_NodeBridge_nativeLoad(JNIEnv *env, jclass, jstring libPath) {
    if (g_start != nullptr) return nullptr;
    std::string path = jstr(env, libPath);
    if (path.empty()) return env->NewStringUTF("empty libnode path");

    // RTLD_GLOBAL：Node 的内置扩展要能反查到 libnode 自己的符号
    g_handle = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (g_handle == nullptr) {
        const char *err = dlerror();
        LOGE("dlopen failed: %s", err == nullptr ? "unknown" : err);
        return env->NewStringUTF(err == nullptr ? "dlopen failed" : err);
    }

    g_start = reinterpret_cast<NodeStart>(dlsym(g_handle, kStartSymbol));
    if (g_start == nullptr) {
        const char *err = dlerror();
        LOGE("dlsym %s failed: %s", kStartSymbol, err == nullptr ? "unknown" : err);
        dlclose(g_handle);
        g_handle = nullptr;
        return env->NewStringUTF(err == nullptr ? "node::Start not found" : err);
    }
    LOGI("libnode loaded: %s", path.c_str());
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_fongmi_android_tv_nodejs_NodeBridge_nativeStart(JNIEnv *env, jclass, jobjectArray argv) {
    if (g_start == nullptr) {
        LOGE("nativeStart before nativeLoad");
        return -1;
    }
    redirectStdio();
    jsize count = argv == nullptr ? 0 : env->GetArrayLength(argv);
    if (count <= 0) return -1;

    // node::Start 会就地改写 argv，所以给它可写副本
    std::vector<std::string> owned;
    owned.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto item = reinterpret_cast<jstring>(env->GetObjectArrayElement(argv, i));
        owned.push_back(jstr(env, item));
        if (item != nullptr) env->DeleteLocalRef(item);
    }

    std::vector<char *> raw;
    raw.reserve(owned.size() + 1);
    for (auto &s : owned) raw.push_back(const_cast<char *>(s.c_str()));
    raw.push_back(nullptr);

    LOGI("node::Start argc=%d script=%s", count, count > 1 ? raw[1] : "");
    // 阻塞直到 Node 事件循环结束；调用方负责放到独立线程
    int code = g_start(static_cast<int>(owned.size()), raw.data());
    LOGI("node::Start returned %d", code);
    return code;
}
