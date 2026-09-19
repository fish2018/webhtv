// WebHTV Exo ASS presenter. The owning Java worker serializes every entry point.
#include <jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <ass/ass.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdarg>
#include <cstring>
#include <memory>
#include <vector>
#include "mask_copy.h"

namespace {
constexpr size_t MAX_SCRIPT = 8 * 1024 * 1024;
constexpr size_t MAX_MASK_PIXELS = 8 * 1024 * 1024;
constexpr int MAX_IMAGES = 256;
constexpr int MAX_SIDE = 4096;
constexpr int MAX_EVENTS = 20000;
constexpr int MAX_STYLES = 512;
constexpr int GLYPH_CACHE_LIMIT = 512;
constexpr int BITMAP_CACHE_MIB = 8;

int64_t micros() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}

struct Texture { GLuint id = 0; int width = 0; int height = 0; };

struct Session {
    ASS_Library *library = nullptr;
    ASS_Renderer *renderer = nullptr;
    ASS_Track *track = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLConfig config = nullptr;
    ANativeWindow *window = nullptr;
    GLuint program = 0;
    GLint position = -1, texcoord = -1, color = -1;
    std::vector<Texture> textures;
    std::vector<uint8_t> packed;
    int width = 0, height = 0;
    int storage_width = 0, storage_height = 0;
    double pixel_aspect = 0;
    int color_space = 0, color_range = 0;
    bool force = true;
    bool packetized = false;

    void detach() {
        if (display != EGL_NO_DISPLAY && context != EGL_NO_CONTEXT
                && surface != EGL_NO_SURFACE
                && eglMakeCurrent(display, surface, surface, context)) {
            for (auto &texture : textures) glDeleteTextures(1, &texture.id);
            if (program) glDeleteProgram(program);
        }
        textures.clear();
        packed.clear();
        program = 0;
        if (display != EGL_NO_DISPLAY) {
            eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
            if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
            eglTerminate(display);
            eglReleaseThread();
        }
        surface = EGL_NO_SURFACE;
        context = EGL_NO_CONTEXT;
        display = EGL_NO_DISPLAY;
        if (window) ANativeWindow_release(window);
        window = nullptr;
        force = true;
    }

    ~Session() {
        detach();
        if (track) ass_free_track(track);
        if (renderer) ass_renderer_done(renderer);
        if (library) ass_library_done(library);
    }
};

Session *session(jlong handle) {
    return reinterpret_cast<Session *>(static_cast<uintptr_t>(handle));
}

GLuint shader(GLenum type, const char *source) {
    GLuint id = glCreateShader(type);
    if (!id) return 0;
    glShaderSource(id, 1, &source, nullptr);
    glCompileShader(id);
    GLint ok = GL_FALSE;
    glGetShaderiv(id, GL_COMPILE_STATUS, &ok);
    if (!ok) { glDeleteShader(id); return 0; }
    return id;
}

bool init_gl(Session &s) {
    const char *vertex =
            "attribute vec2 aPosition; attribute vec2 aTexCoord; varying vec2 uv;"
            "void main(){gl_Position=vec4(aPosition,0.,1.);uv=aTexCoord;}";
    const char *fragment =
            "precision mediump float; uniform sampler2D mask; uniform vec4 color;"
            "varying vec2 uv; void main(){float a=texture2D(mask,uv).a*color.a;"
            "gl_FragColor=vec4(color.rgb*a,a);}";
    GLuint v = shader(GL_VERTEX_SHADER, vertex), f = shader(GL_FRAGMENT_SHADER, fragment);
    if (!v || !f) {
        if (v) glDeleteShader(v);
        if (f) glDeleteShader(f);
        return false;
    }
    s.program = glCreateProgram();
    glAttachShader(s.program, v);
    glAttachShader(s.program, f);
    glLinkProgram(s.program);
    glDeleteShader(v);
    glDeleteShader(f);
    GLint ok = GL_FALSE;
    glGetProgramiv(s.program, GL_LINK_STATUS, &ok);
    if (!ok) return false;
    s.position = glGetAttribLocation(s.program, "aPosition");
    s.texcoord = glGetAttribLocation(s.program, "aTexCoord");
    s.color = glGetUniformLocation(s.program, "color");
    glUseProgram(s.program);
    glUniform1i(glGetUniformLocation(s.program, "mask"), 0);
    glActiveTexture(GL_TEXTURE0);
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    return glGetError() == GL_NO_ERROR;
}

bool attach(Session &s, JNIEnv *env, jobject surface, int test_width, int test_height) {
    s.detach();
    if (!surface && !test_width) return true;
    s.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (s.display == EGL_NO_DISPLAY || !eglInitialize(s.display, nullptr, nullptr)) return false;
    const EGLint attributes[] = { EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8, EGL_SURFACE_TYPE, surface ? EGL_WINDOW_BIT : EGL_PBUFFER_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE };
    EGLint count = 0;
    if (!eglChooseConfig(s.display, attributes, &s.config, 1, &count) || !count) return false;
    const EGLint context_attributes[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    s.context = eglCreateContext(s.display, s.config, EGL_NO_CONTEXT, context_attributes);
    if (s.context == EGL_NO_CONTEXT) return false;
    if (surface) {
        s.window = ANativeWindow_fromSurface(env, surface);
        if (!s.window) return false;
        EGLint format = 0;
        eglGetConfigAttrib(s.display, s.config, EGL_NATIVE_VISUAL_ID, &format);
        if (ANativeWindow_setBuffersGeometry(s.window, 0, 0, format) != 0) return false;
        s.surface = eglCreateWindowSurface(s.display, s.config, s.window, nullptr);
    } else {
        const EGLint pbuffer[] = { EGL_WIDTH, test_width, EGL_HEIGHT, test_height, EGL_NONE };
        s.surface = eglCreatePbufferSurface(s.display, s.config, pbuffer);
    }
    if (s.surface == EGL_NO_SURFACE
            || !eglMakeCurrent(s.display, s.surface, s.surface, s.context)) return false;
    // Swap may wait for the compositor, but only this subtitle worker can be delayed.
    eglSwapInterval(s.display, 1);
    return init_gl(s);
}

struct Matrix { double kr, kb; bool limited; };

Matrix subtitle_matrix(ASS_YCbCrMatrix matrix) {
    switch (matrix) {
        case YCBCR_BT709_TV: return { .2126, .0722, true };
        case YCBCR_BT709_PC: return { .2126, .0722, false };
        case YCBCR_SMPTE240M_TV: return { .212, .087, true };
        case YCBCR_SMPTE240M_PC: return { .212, .087, false };
        case YCBCR_FCC_TV: return { .30, .11, true };
        case YCBCR_FCC_PC: return { .30, .11, false };
        case YCBCR_BT601_PC: return { .299, .114, false };
        default: return { .299, .114, true };
    }
}

// ASS describes the historical RGB -> subtitle YCbCr -> video RGB round trip.
// Apply it to straight color, before mask coverage and premultiplication.
void rgba(Session &s, uint32_t color, GLfloat *out) {
    double rgb[] = { (color >> 24) / 255.0, ((color >> 16) & 255) / 255.0,
                     ((color >> 8) & 255) / 255.0 };
    // color_space == 0 is AssNative.COLOR_SPACE_SDR_RGB: HDR/wide-gamut video
    // uses an independent SDR subtitle layer, not a video YCbCr round trip.
    if (s.color_space != 0 && s.track->YCbCrMatrix != YCBCR_NONE) {
        Matrix src = subtitle_matrix(s.track->YCbCrMatrix);
        Matrix dst = s.color_space == 2 ? Matrix{.299, .114, s.color_range != 1}
                                       : Matrix{.2126, .0722, s.color_range != 1};
        double y = src.kr * rgb[0] + (1 - src.kr - src.kb) * rgb[1] + src.kb * rgb[2];
        double cb = (rgb[2] - y) / (2 * (1 - src.kb));
        double cr = (rgb[0] - y) / (2 * (1 - src.kr));
        y = (src.limited ? y * 219 + 16 : y * 255) / 255;
        cb *= src.limited ? 224.0 / 255 : 1;
        cr *= src.limited ? 224.0 / 255 : 1;
        if (dst.limited) { y = (y * 255 - 16) / 219; cb *= 255.0 / 224; cr *= 255.0 / 224; }
        rgb[0] = y + 2 * (1 - dst.kr) * cr;
        rgb[2] = y + 2 * (1 - dst.kb) * cb;
        rgb[1] = (y - dst.kr * rgb[0] - dst.kb * rgb[2]) / (1 - dst.kr - dst.kb);
    }
    for (int i = 0; i < 3; ++i) out[i] = static_cast<GLfloat>(std::clamp(rgb[i], 0., 1.));
    out[3] = (255 - (color & 255)) / 255.f;
}

bool valid_size(int width, int height) {
    return width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE
            && static_cast<size_t>(width) * height <= MAX_MASK_PIXELS;
}

int render(Session &s, int64_t time_ms, int width, int height, int storage_width,
           int storage_height, double pixel_aspect, int color_space, int color_range,
           bool force, jlong *stats) {
    if (!s.track || !s.renderer || s.surface == EGL_NO_SURFACE || !valid_size(width, height)
            || storage_width <= 0 || storage_height <= 0 || !std::isfinite(pixel_aspect)
            || pixel_aspect <= 0) return -1;
    bool geometry = s.width != width || s.height != height || s.storage_width != storage_width
            || s.storage_height != storage_height || s.pixel_aspect != pixel_aspect;
    if (geometry) {
        ass_set_frame_size(s.renderer, width, height);
        ass_set_storage_size(s.renderer, storage_width, storage_height);
        ass_set_pixel_aspect(s.renderer, pixel_aspect);
        ass_set_margins(s.renderer, 0, 0, 0, 0);
        ass_set_use_margins(s.renderer, 0);
    }
    force |= s.force || geometry || color_space != s.color_space || color_range != s.color_range;
    s.width = width; s.height = height;
    s.storage_width = storage_width; s.storage_height = storage_height;
    s.pixel_aspect = pixel_aspect; s.color_space = color_space; s.color_range = color_range;
    const int64_t start = micros();
    int change = 0;
    ASS_Image *images = ass_render_frame(s.renderer, s.track, time_ms, &change);
    stats[0] = micros() - start;
    stats[5] = change;
    if (!force && !change) return 0;
    size_t pixels = 0;
    int count = 0;
    GLint max_texture = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &max_texture);
    for (ASS_Image *img = images; img; img = img->next) {
        if (!img->w || !img->h) continue;
        if (img->w < 0 || img->h < 0 || img->stride < img->w || !img->bitmap
                || img->w > max_texture || img->h > max_texture || ++count > MAX_IMAGES)
            return -2;
        size_t area = static_cast<size_t>(img->w) * img->h;
        if (area > MAX_MASK_PIXELS || pixels > MAX_MASK_PIXELS - area) return -2;
        pixels += area;
    }
    stats[3] = count; stats[4] = static_cast<jlong>(pixels);
    const int64_t upload = micros();
    glViewport(0, 0, width, height);
    glClearColor(0, 0, 0, 0);
    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(s.program);
    glEnableVertexAttribArray(s.position);
    glEnableVertexAttribArray(s.texcoord);
    if (s.textures.size() < static_cast<size_t>(count)) s.textures.resize(count);
    int index = 0;
    for (ASS_Image *img = images; img; img = img->next) {
        if (img->w <= 0 || img->h <= 0) continue;
        Texture &texture = s.textures[index++];
        if (!texture.id) glGenTextures(1, &texture.id);
        glBindTexture(GL_TEXTURE_2D, texture.id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        const uint8_t *mask = img->bitmap;
        if (img->stride != img->w) {
            if (!exo_ass::copy_mask(s.packed, img->bitmap, img->w, img->h,
                                     img->stride, MAX_MASK_PIXELS)) return -2;
            mask = s.packed.data();
        }
        if (texture.width != img->w || texture.height != img->h) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, img->w, img->h, 0,
                         GL_ALPHA, GL_UNSIGNED_BYTE, mask);
            texture.width = img->w; texture.height = img->h;
        } else {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, img->w, img->h,
                            GL_ALPHA, GL_UNSIGNED_BYTE, mask);
        }
        GLfloat left = 2.f * img->dst_x / width - 1.f;
        GLfloat top = 1.f - 2.f * img->dst_y / height;
        GLfloat right = left + 2.f * img->w / width;
        GLfloat bottom = top - 2.f * img->h / height;
        const GLfloat vertices[] = { left, top, right, top, left, bottom, right, bottom };
        const GLfloat uv[] = { 0, 0, 1, 0, 0, 1, 1, 1 };
        GLfloat color[4];
        rgba(s, img->color, color);
        glUniform4fv(s.color, 1, color);
        glVertexAttribPointer(s.position, 2, GL_FLOAT, GL_FALSE, 0, vertices);
        glVertexAttribPointer(s.texcoord, 2, GL_FLOAT, GL_FALSE, 0, uv);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }
    // Delete excess textures so a simpler subsequent frame releases GPU storage.
    for (size_t i = count; i < s.textures.size(); ++i) glDeleteTextures(1, &s.textures[i].id);
    s.textures.resize(count);
    if (glGetError() != GL_NO_ERROR) return -1;
    stats[1] = micros() - upload;
    const int64_t swap = micros();
    if (s.window) {
        if (!eglSwapBuffers(s.display, s.surface)) return -1;
    } else {
        glFlush();
    }
    stats[2] = micros() - swap;
    s.force = false;
    return count ? 1 : 2;
}
void font_message(int level, const char *format, va_list args, void *) {
    // Info/warnings identify provider failures and selected faces; verbose event dumps stay off.
    if (level <= 4)
        __android_log_vprint(ANDROID_LOG_DEBUG, "ExoAssNative", format, args);
}

bool add_fonts(JNIEnv *env, ASS_Library *library, jobjectArray names, jobjectArray fonts) {
    if (!names || !fonts) return false;
    jsize count = env->GetArrayLength(names);
    if (count > 64 || env->GetArrayLength(fonts) != count) return false;
    size_t total = 0;
    for (jsize i = 0; i < count; ++i) {
        auto name = static_cast<jstring>(env->GetObjectArrayElement(names, i));
        auto data = static_cast<jbyteArray>(env->GetObjectArrayElement(fonts, i));
        if (!name || !data || env->GetStringLength(name) > 512) return false;
        jsize length = env->GetArrayLength(data);
        if (length <= 0 || length > 16 * 1024 * 1024) return false;
        total += static_cast<size_t>(length);
        if (total > 32 * 1024 * 1024) return false;
        std::vector<char> bytes(length);
        env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes.data()));
        if (env->ExceptionCheck()) return false;
        const char *label = env->GetStringUTFChars(name, nullptr);
        if (!label) return false;
        ass_add_font(library, label, bytes.data(), length);
        env->ReleaseStringUTFChars(name, label);
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(data);
    }
    return true;
}
} // namespace

#define JNI_METHOD(name) Java_com_fongmi_android_tv_player_exo_ass_AssNative_##name

extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(create)(JNIEnv *env, jclass, jstring font_config,
        jobjectArray names, jobjectArray fonts) {
    if (!font_config) return 0;
    try {
        auto s = std::make_unique<Session>();
        s->library = ass_library_init();
        if (!s->library) return 0;
        ass_set_message_cb(s->library, font_message, nullptr);
        ass_set_extract_fonts(s->library, 0);
        if (!add_fonts(env, s->library, names, fonts)) return 0;
        s->renderer = ass_renderer_init(s->library);
        if (!s->renderer) return 0;
        ass_set_cache_limits(s->renderer, GLYPH_CACHE_LIMIT, BITMAP_CACHE_MIB);
        const char *config = env->GetStringUTFChars(font_config, nullptr);
        if (!config) return 0;
        ass_set_fonts(s->renderer, nullptr, "sans-serif", ASS_FONTPROVIDER_FONTCONFIG, config, 1);
        env->ReleaseStringUTFChars(font_config, config);
        return static_cast<jlong>(reinterpret_cast<uintptr_t>(s.release()));
    } catch (...) { return 0; }
}

extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(load)(JNIEnv *env, jclass, jlong handle, jbyteArray bytes) {
    Session *s = session(handle);
    if (!s || !bytes) return JNI_FALSE;
    jsize length = env->GetArrayLength(bytes);
    if (length <= 0 || static_cast<size_t>(length) > MAX_SCRIPT) return JNI_FALSE;
    try {
        std::vector<char> script(static_cast<size_t>(length) + 1, 0);
        env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(script.data()));
        if (env->ExceptionCheck()) return JNI_FALSE;
        ASS_Track *track = ass_read_memory(s->library, script.data(), length, nullptr);
        if (!track) return JNI_FALSE;
        if (track->n_events > MAX_EVENTS || track->n_styles > MAX_STYLES) {
            ass_free_track(track);
            return JNI_FALSE;
        }
        if (s->track) ass_free_track(s->track);
        s->track = track;
        s->packetized = false;
        s->force = true;
        return JNI_TRUE;
    } catch (...) { return JNI_FALSE; }
}

extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(loadHeader)(JNIEnv *env, jclass, jlong handle, jbyteArray bytes) {
    Session *s = session(handle);
    if (!s || !bytes) return JNI_FALSE;
    jsize length = env->GetArrayLength(bytes);
    if (length <= 0 || static_cast<size_t>(length) > MAX_SCRIPT) return JNI_FALSE;
    try {
        std::vector<char> header(static_cast<size_t>(length) + 1, 0);
        env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(header.data()));
        if (env->ExceptionCheck()) return JNI_FALSE;
        std::unique_ptr<ASS_Track, decltype(&ass_free_track)> track(ass_new_track(s->library), ass_free_track);
        if (!track) return JNI_FALSE;
        ass_process_codec_private(track.get(), header.data(), length);
        if (!track->event_format || track->n_events != 0 || track->n_styles > MAX_STYLES) return JNI_FALSE;
        if (s->track) ass_free_track(s->track);
        s->track = track.release();
        s->packetized = true;
        s->force = true;
        return JNI_TRUE;
    } catch (...) { return JNI_FALSE; }
}

extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(chunk)(JNIEnv *env, jclass, jlong handle,
        jbyteArray bytes, jlong start_ms, jlong duration_ms) {
    Session *s = session(handle);
    if (!s || !s->track || !s->packetized || !bytes || duration_ms < 0
            || start_ms > INT64_MAX - duration_ms || s->track->n_events >= MAX_EVENTS) return JNI_FALSE;
    jsize length = env->GetArrayLength(bytes);
    if (length <= 0 || length > 4 * 1024 * 1024) return JNI_FALSE;
    try {
        std::vector<char> packet(static_cast<size_t>(length) + 1, 0);
        env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(packet.data()));
        if (env->ExceptionCheck()) return JNI_FALSE;
        // libass owns event parsing and ReadOrder duplicate handling. Do not mix this with
        // ass_process_data or edit its event array; the worker serializes this with rendering.
        ass_process_chunk(s->track, packet.data(), length, start_ms, duration_ms);
        return s->track->n_events <= MAX_EVENTS ? JNI_TRUE : JNI_FALSE;
    } catch (...) { return JNI_FALSE; }
}

extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(setSurface)(JNIEnv *env, jclass, jlong handle, jobject surface) {
    Session *s = session(handle);
    if (!s) return JNI_FALSE;
    try { return attach(*s, env, surface, 0, 0) ? JNI_TRUE : JNI_FALSE; }
    catch (...) { return JNI_FALSE; }
}

extern "C" JNIEXPORT jint JNICALL JNI_METHOD(render)(JNIEnv *env, jclass, jlong handle,
        jlong time_ms, jint width, jint height, jint storage_width, jint storage_height,
        jdouble pixel_aspect, jint color_space, jint color_range, jboolean force, jlongArray stats) {
    Session *s = session(handle);
    if (!s || !stats || env->GetArrayLength(stats) < 6) return -1;
    jlong values[6] = {};
    int result;
    try { result = render(*s, time_ms, width, height, storage_width, storage_height,
                          pixel_aspect, color_space, color_range, force, values); }
    catch (...) { result = -2; }
    env->SetLongArrayRegion(stats, 0, 6, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(destroy)(JNIEnv *, jclass, jlong handle) {
    delete session(handle);
}

// Offscreen readback is used only by the prototype's instrumentation tests.
extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(createTestFonts)(JNIEnv *env, jclass, jobjectArray names, jobjectArray fonts) {
    if (!names || !fonts) return 0;
    jsize count = env->GetArrayLength(names);
    if (count <= 0 || count > 8 || env->GetArrayLength(fonts) != count) return 0;
    try {
        auto s = std::make_unique<Session>();
        s->library = ass_library_init();
        if (!s->library) return 0;
        if (!add_fonts(env, s->library, names, fonts)) return 0;
        s->renderer = ass_renderer_init(s->library);
        if (!s->renderer) return 0;
        ass_set_cache_limits(s->renderer, GLYPH_CACHE_LIMIT, BITMAP_CACHE_MIB);
        ass_set_fonts(s->renderer, nullptr, "sans-serif", ASS_FONTPROVIDER_NONE, nullptr, 1);
        return static_cast<jlong>(reinterpret_cast<uintptr_t>(s.release()));
    } catch (...) { return 0; }
}

extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(testSurface)(JNIEnv *env, jclass, jlong handle, jint width, jint height) {
    Session *s = session(handle);
    if (!s || !valid_size(width, height)) return JNI_FALSE;
    try { return attach(*s, env, nullptr, width, height) ? JNI_TRUE : JNI_FALSE; }
    catch (...) { return JNI_FALSE; }
}

extern "C" JNIEXPORT jbyteArray JNICALL JNI_METHOD(readPixels)(JNIEnv *env, jclass, jlong handle) {
    Session *s = session(handle);
    if (!s || s->surface == EGL_NO_SURFACE || !valid_size(s->width, s->height)) return nullptr;
    try {
        size_t length = static_cast<size_t>(s->width) * s->height * 4;
        std::vector<uint8_t> pixels(length);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, s->width, s->height, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
        if (glGetError() != GL_NO_ERROR) return nullptr;
        jbyteArray out = env->NewByteArray(static_cast<jsize>(length));
        if (!out) return nullptr;
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(length), reinterpret_cast<jbyte *>(pixels.data()));
        return out;
    } catch (...) { return nullptr; }
}
