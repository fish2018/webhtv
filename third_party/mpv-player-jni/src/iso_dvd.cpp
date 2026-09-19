#include <jni.h>
#include <limits.h>
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <atomic>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include <mpv/client.h>
#include <mpv/stream_cb.h>
#include <dvdnav/dvdnav.h>
#include <dvdnav/dvdnav_events.h>
#include <libbluray/bluray.h>

#include "globals.h"
#include "jni_utils.h"
#include "log.h"

namespace {

constexpr int DVD_BLOCK = 2048;
constexpr double BLURAY_TIMEBASE = 90000.0;
constexpr const char *PROTOCOL = "webhtv-dvdiso";

jclass manager_class;
jmethodID manager_length;
jmethodID manager_read;
jmethodID manager_close;
jmethodID manager_prepare_track_metadata;

enum class IsoKind { NONE, RAW, DVD, BLURAY };

struct DvdStream {
    int64_t session_id = -1;
    int64_t iso_offset = 0;
    int64_t output_offset = 0;
    int64_t output_size = -1;
    dvdnav_t *nav = nullptr;
    BLURAY *bluray = nullptr;
    IsoKind kind = IsoKind::NONE;
    uint64_t bluray_duration_ticks = 0;
    uint32_t bluray_playlist = 0;
    uint32_t bluray_title_count = 0;
    bool owns_java_session = true;
    std::atomic<bool> cancelled{false};
    std::mutex lock;
    std::unordered_map<int, std::string> track_languages;
    std::vector<uint64_t> chapter_times;
    uint8_t block[DVD_BLOCK];
    int block_offset = DVD_BLOCK;
    int block_size = 0;
};

std::mutex streams_lock;
std::unordered_map<int64_t, DvdStream *> active_streams;

JNIEnv *env_for_thread() {
    JNIEnv *env = nullptr;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
    if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    return env;
}

int64_t parse_session(const char *uri) {
    if (!uri) return -1;
    const char *start = strstr(uri, "://");
    start = start ? start + 3 : uri;
    char *end = nullptr;
    int64_t id = strtoll(start, &end, 10);
    return end == start || id <= 0 ? -1 : id;
}

int java_read(DvdStream *stream, int64_t offset, void *buffer, int length) {
    if (!stream || stream->cancelled || length <= 0) return -1;
    JNIEnv *env = env_for_thread();
    if (!env) return -1;
    jobject direct = env->NewDirectByteBuffer(buffer, length);
    if (!direct) return -1;
    jint read = env->CallStaticIntMethod(manager_class, manager_read, stream->session_id, offset, direct, length);
    env->DeleteLocalRef(direct);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return -1;
    }
    return read;
}

int dvd_seek(void *opaque, uint64_t position) {
    auto *stream = static_cast<DvdStream *>(opaque);
    if (!stream || stream->cancelled) return -1;
    stream->iso_offset = static_cast<int64_t>(position);
    return 0;
}

int dvd_read(void *opaque, void *buffer, int length) {
    auto *stream = static_cast<DvdStream *>(opaque);
    int read = java_read(stream, stream->iso_offset, buffer, length);
    if (read > 0) stream->iso_offset += read;
    return read;
}

int dvd_readv(void *, void *, int) {
    return -1;
}

int bluray_read_blocks(void *opaque, void *buffer, int lba, int blocks) {
    auto *stream = static_cast<DvdStream *>(opaque);
    if (!stream || lba < 0 || blocks <= 0) return -1;
    int wanted = blocks * DVD_BLOCK;
    int read = java_read(stream, static_cast<int64_t>(lba) * DVD_BLOCK, buffer, wanted);
    if (read < 0) return -1;
    return read / DVD_BLOCK;
}

void dvd_log(void *, dvdnav_logger_level_t level, const char *format, va_list args) {
    char message[512];
    vsnprintf(message, sizeof(message), format, args);
    if (level == DVDNAV_LOGGER_LEVEL_ERROR) ALOGE("iso dvdnav: %s", message);
    else ALOGV("iso dvdnav: %s", message);
}

bool select_longest_title(DvdStream *stream) {
    int32_t count = 0;
    if (dvdnav_get_number_of_titles(stream->nav, &count) != DVDNAV_STATUS_OK || count <= 0) return false;
    int32_t selected = 1;
    uint64_t longest = 0;
    for (int32_t title = 1; title <= count; ++title) {
        uint64_t *chapters = nullptr;
        uint64_t duration = 0;
        dvdnav_describe_title_chapters(stream->nav, title, &chapters, &duration);
        free(chapters);
        if (duration > longest) {
            longest = duration;
            selected = title;
        }
    }
    if (dvdnav_title_play(stream->nav, selected) != DVDNAV_STATUS_OK) return false;
    dvdnav_set_readahead_flag(stream->nav, 0);
    dvdnav_set_PGC_positioning_flag(stream->nav, 1);
    uint32_t position = 0, length = 0;
    if (dvdnav_get_position(stream->nav, &position, &length) == DVDNAV_STATUS_OK && length > 0) {
        stream->output_size = static_cast<int64_t>(length) * DVD_BLOCK;
    }
    ALOGV("iso selected title=%d count=%d duration=%llu size=%lld", selected, count,
          static_cast<unsigned long long>(longest), static_cast<long long>(stream->output_size));
    return true;
}

int next_block(DvdStream *stream) {
    while (!stream->cancelled) {
        int32_t event = 0;
        int32_t length = 0;
        if (dvdnav_get_next_block(stream->nav, stream->block, &event, &length) != DVDNAV_STATUS_OK) return -1;
        if (event == DVDNAV_BLOCK_OK && length > 0) {
            stream->block_offset = 0;
            stream->block_size = length;
            return length;
        }
        if (event == DVDNAV_STOP) return 0;
        if (event == DVDNAV_WAIT) dvdnav_wait_skip(stream->nav);
        if (event == DVDNAV_STILL_FRAME) dvdnav_still_skip(stream->nav);
    }
    return -1;
}

int64_t stream_read(void *opaque, char *buffer, uint64_t requested) {
    auto *stream = static_cast<DvdStream *>(opaque);
    if (!stream || !buffer || stream->cancelled) return -1;
    std::lock_guard<std::mutex> guard(stream->lock);
    if (stream->kind == IsoKind::RAW) {
        if (stream->iso_offset >= stream->output_size || requested == 0) return 0;
        int wanted = requested > INT32_MAX ? INT32_MAX : static_cast<int>(requested);
        int64_t remaining = stream->output_size - stream->iso_offset;
        if (wanted > remaining) wanted = static_cast<int>(remaining);
        int read = java_read(stream, stream->iso_offset, buffer, wanted);
        if (read > 0) {
            stream->iso_offset += read;
            stream->output_offset += read;
        }
        return read;
    }
    if (stream->kind == IsoKind::BLURAY) {
        int wanted = requested > INT32_MAX ? INT32_MAX : static_cast<int>(requested);
        int read = bd_read(stream->bluray, reinterpret_cast<unsigned char *>(buffer), wanted);
        if (read > 0) stream->output_offset += read;
        return read;
    }
    uint64_t written = 0;
    while (written < requested) {
        if (stream->block_offset >= stream->block_size) {
            int result = next_block(stream);
            if (result <= 0) return written > 0 ? static_cast<int64_t>(written) : result;
        }
        int available = stream->block_size - stream->block_offset;
        int copy = static_cast<int>(requested - written < static_cast<uint64_t>(available) ? requested - written : available);
        memcpy(buffer + written, stream->block + stream->block_offset, copy);
        stream->block_offset += copy;
        stream->output_offset += copy;
        written += copy;
    }
    return static_cast<int64_t>(written);
}

int64_t stream_seek(void *opaque, int64_t offset) {
    auto *stream = static_cast<DvdStream *>(opaque);
    if (!stream || offset < 0 || stream->cancelled) return MPV_ERROR_GENERIC;
    std::lock_guard<std::mutex> guard(stream->lock);
    if (stream->kind == IsoKind::RAW) {
        if (stream->output_size >= 0 && offset > stream->output_size)
            return MPV_ERROR_GENERIC;
        stream->iso_offset = offset;
        stream->output_offset = offset;
        return offset;
    }
    if (stream->kind == IsoKind::BLURAY) {
        int64_t result = bd_seek(stream->bluray, static_cast<uint64_t>(offset));
        if (result < 0) return MPV_ERROR_GENERIC;
        stream->output_offset = result;
        return result;
    }
    int64_t sector = offset / DVD_BLOCK;
    if (dvdnav_sector_search(stream->nav, sector, SEEK_SET) != DVDNAV_STATUS_OK) return MPV_ERROR_GENERIC;
    stream->block_offset = DVD_BLOCK;
    stream->block_size = 0;
    stream->output_offset = sector * DVD_BLOCK;
    return stream->output_offset;
}

int64_t stream_size(void *opaque) {
    auto *stream = static_cast<DvdStream *>(opaque);
    return stream ? stream->output_size : MPV_ERROR_UNSUPPORTED;
}

int stream_control_cb(void *opaque, int command, void *arg) {
    auto *stream = static_cast<DvdStream *>(opaque);
    if (!stream || !arg || stream->cancelled || stream->kind != IsoKind::BLURAY || !stream->bluray) {
        return MPV_STREAM_CB_CONTROL_UNSUPPORTED;
    }
    std::lock_guard<std::mutex> guard(stream->lock);
    switch (command) {
        case MPV_STREAM_CB_CTRL_GET_TIME_LENGTH:
            if (!stream->bluray_duration_ticks) return MPV_STREAM_CB_CONTROL_ERROR;
            *static_cast<double *>(arg) = stream->bluray_duration_ticks / BLURAY_TIMEBASE;
            return MPV_STREAM_CB_CONTROL_OK;
        case MPV_STREAM_CB_CTRL_GET_CURRENT_TIME:
            *static_cast<double *>(arg) = bd_tell_time(stream->bluray) / BLURAY_TIMEBASE;
            return MPV_STREAM_CB_CONTROL_OK;
        case MPV_STREAM_CB_CTRL_SEEK_TO_TIME: {
            double seconds = *static_cast<double *>(arg);
            if (!isfinite(seconds) || seconds < 0 || !stream->bluray_duration_ticks) {
                return MPV_STREAM_CB_CONTROL_ERROR;
            }
            uint64_t target_ticks;
            double duration_seconds = stream->bluray_duration_ticks / BLURAY_TIMEBASE;
            if (seconds >= duration_seconds) {
                target_ticks = stream->bluray_duration_ticks - 1;
            } else {
                target_ticks = static_cast<uint64_t>(seconds * BLURAY_TIMEBASE);
            }
            int64_t position = bd_seek_time(stream->bluray, target_ticks);
            if (position < 0) return MPV_STREAM_CB_CONTROL_ERROR;
            stream->output_offset = position;
            uint64_t actual_ticks = bd_tell_time(stream->bluray);
            ALOGV("iso-bluray timeline seek session=%lld requestedMs=%lld actualMs=%lld byte=%lld",
                  static_cast<long long>(stream->session_id),
                  static_cast<long long>(target_ticks / 90),
                  static_cast<long long>(actual_ticks / 90),
                  static_cast<long long>(position));
            return MPV_STREAM_CB_CONTROL_OK;
        }
        case MPV_STREAM_CB_CTRL_GET_NUM_CHAPTERS:
            *static_cast<int *>(arg) = stream->chapter_times.size() > static_cast<size_t>(INT_MAX)
                    ? INT_MAX : static_cast<int>(stream->chapter_times.size());
            return MPV_STREAM_CB_CONTROL_OK;
        case MPV_STREAM_CB_CTRL_GET_CHAPTER_TIME: {
            double index_value = *static_cast<double *>(arg);
            if (!isfinite(index_value) || index_value < 0) return MPV_STREAM_CB_CONTROL_ERROR;
            size_t index = static_cast<size_t>(index_value);
            if (index >= stream->chapter_times.size()) return MPV_STREAM_CB_CONTROL_ERROR;
            *static_cast<double *>(arg) = stream->chapter_times[index] / BLURAY_TIMEBASE;
            return MPV_STREAM_CB_CONTROL_OK;
        }
        case MPV_STREAM_CB_CTRL_GET_LANG: {
            auto *request = static_cast<mpv_stream_cb_lang_req *>(arg);
            if (request->type != MPV_STREAM_CB_TRACK_AUDIO &&
                request->type != MPV_STREAM_CB_TRACK_SUBTITLE) {
                return MPV_STREAM_CB_CONTROL_UNSUPPORTED;
            }
            auto language = stream->track_languages.find(request->id);
            if (language == stream->track_languages.end()) return MPV_STREAM_CB_CONTROL_ERROR;
            snprintf(request->name, sizeof(request->name), "%s", language->second.c_str());
            return MPV_STREAM_CB_CONTROL_OK;
        }
        default:
            return MPV_STREAM_CB_CONTROL_UNSUPPORTED;
    }
}

void close_java_session(int64_t id) {
    JNIEnv *env = env_for_thread();
    if (!env) return;
    env->CallStaticVoidMethod(manager_class, manager_close, id);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

void stream_cancel(void *opaque) {
    auto *stream = static_cast<DvdStream *>(opaque);
    if (stream) stream->cancelled = true;
}

void stream_close(void *opaque) {
    auto *stream = static_cast<DvdStream *>(opaque);
    if (!stream) return;
    stream->cancelled = true;
    {
        std::lock_guard<std::mutex> guard(streams_lock);
        active_streams.erase(stream->session_id);
    }
    if (stream->nav) dvdnav_close(stream->nav);
    if (stream->bluray) bd_close(stream->bluray);
    // A raw ISO callback is owned by MPV's nested Blu-ray stream. Keep the
    // Java session alive across Blu-ray->DVD probing and let MpvPlayer close
    // it with the public ISO URI when playback ends.
    if (stream->owns_java_session) close_java_session(stream->session_id);
    delete stream;
}

bool open_dvd(DvdStream *stream) {
    dvdnav_stream_cb callbacks = {dvd_seek, dvd_read, dvd_readv};
    dvdnav_logger_cb logger = {dvd_log};
    if (dvdnav_open_stream2(&stream->nav, stream, &logger, &callbacks) != DVDNAV_STATUS_OK) return false;
    if (!select_longest_title(stream)) {
        dvdnav_close(stream->nav);
        stream->nav = nullptr;
        return false;
    }
    stream->kind = IsoKind::DVD;
    ALOGV("iso-native opened DVD image");
    return true;
}

bool open_bluray(DvdStream *stream) {
    stream->bluray = bd_init();
    if (!stream->bluray || !bd_open_stream(stream->bluray, stream, bluray_read_blocks)) {
        if (stream->bluray) bd_close(stream->bluray);
        stream->bluray = nullptr;
        return false;
    }
    uint32_t count = bd_get_titles(stream->bluray, TITLES_RELEVANT, 0);
    uint64_t longest = 0;
    uint32_t playlist = 0;
    for (uint32_t i = 0; i < count; ++i) {
        BLURAY_TITLE_INFO *title = bd_get_title_info(stream->bluray, i, 0);
        if (title && title->duration > longest) {
            longest = title->duration;
            playlist = title->playlist;
        }
        if (title) bd_free_title_info(title);
    }
    if (!count || !longest || !bd_select_playlist(stream->bluray, playlist)) {
        bd_close(stream->bluray);
        stream->bluray = nullptr;
        return false;
    }
    BLURAY_TITLE_INFO *info = bd_get_playlist_info(stream->bluray, playlist, 0);
    if (info) {
        stream->bluray_duration_ticks = info->duration ? info->duration : longest;
        stream->chapter_times.reserve(info->chapter_count);
        for (uint32_t chapter = 0; chapter < info->chapter_count; ++chapter) {
            stream->chapter_times.push_back(info->chapters[chapter].start);
        }
        for (uint32_t clip_index = 0; clip_index < info->clip_count; ++clip_index) {
            BLURAY_CLIP_INFO &clip = info->clips[clip_index];
            auto collect = [&](BLURAY_STREAM_INFO *streams, uint8_t stream_count, const char *kind) {
                if (!streams) return;
                for (uint8_t i = 0; i < stream_count; ++i) {
                    BLURAY_STREAM_INFO &item = streams[i];
                    ALOGV("iso-bluray clip=%u kind=%s index=%u pid=%u coding=%u format=%u rate=%u char=%u lang=%.3s",
                          clip_index, kind, i,
                          item.pid, item.coding_type, item.format, item.rate, item.char_code, item.lang);
                    if (!item.pid || !item.lang[0]) continue;
                    std::string language(reinterpret_cast<const char *>(item.lang), 3);
                    if (language.empty()) continue;
                    stream->track_languages.emplace(item.pid, language);
                }
            };
            collect(clip.audio_streams, clip.audio_stream_count, "audio");
            collect(clip.pg_streams, clip.pg_stream_count, "subtitle");
        }
        bd_free_title_info(info);
    }
    if (!stream->bluray_duration_ticks) stream->bluray_duration_ticks = longest;
    stream->bluray_playlist = playlist;
    stream->bluray_title_count = count;
    stream->output_size = static_cast<int64_t>(bd_get_title_size(stream->bluray));
    if (stream->output_size <= 0) {
        bd_close(stream->bluray);
        stream->bluray = nullptr;
        return false;
    }
    JNIEnv *env = env_for_thread();
    if (env && manager_prepare_track_metadata) {
        env->CallStaticVoidMethod(manager_class, manager_prepare_track_metadata, stream->session_id,
                                  static_cast<jint>(playlist));
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    stream->kind = IsoKind::BLURAY;
    ALOGV("iso-native opened Blu-ray image session=%lld playlist=%u titles=%u durationMs=%llu chapters=%zu size=%lld",
          static_cast<long long>(stream->session_id), stream->bluray_playlist,
          stream->bluray_title_count,
          static_cast<unsigned long long>(stream->bluray_duration_ticks / 90),
          stream->chapter_times.size(), static_cast<long long>(stream->output_size));
    return true;
}

int stream_open(void *, char *uri, mpv_stream_cb_info *info) {
    int64_t id = parse_session(uri);
    if (id <= 0 || !info) return MPV_ERROR_LOADING_FAILED;
    auto *stream = new DvdStream();
    stream->session_id = id;
    JNIEnv *env = env_for_thread();
    jlong length = env ? env->CallStaticLongMethod(manager_class, manager_length, id) : -1;
    if (!env || length <= 0) {
        delete stream;
        return MPV_ERROR_LOADING_FAILED;
    }
    const char *raw_marker = strstr(uri, "/raw");
    if (raw_marker && raw_marker[4] == '\0') {
        stream->kind = IsoKind::RAW;
        stream->owns_java_session = false;
        stream->output_size = length;
        ALOGV("iso-native opened raw Blu-ray ISO callback session=%lld size=%lld",
              static_cast<long long>(id), static_cast<long long>(stream->output_size));
    } else {
        // Probe Blu-ray first. libdvdread may scan a very large UDF image
        // sequentially while looking for VIDEO_TS, which makes BD ISO startup
        // appear hung and wastes remote bandwidth.
        if (!open_bluray(stream) && !open_dvd(stream)) {
            close_java_session(id);
            delete stream;
            return MPV_ERROR_LOADING_FAILED;
        }
    }
    {
        std::lock_guard<std::mutex> guard(streams_lock);
        active_streams[stream->session_id] = stream;
    }
    info->cookie = stream;
    info->read_fn = stream_read;
    info->seek_fn = stream_seek;
    info->size_fn = stream_size;
    info->close_fn = stream_close;
    info->cancel_fn = stream_cancel;
    if (stream->kind == IsoKind::BLURAY) {
        info->control_fn = stream_control_cb;
        info->demuxer = "+disc";
    }
    return 0;
}

} // namespace

bool register_iso_protocol(JNIEnv *env) {
    jclass local = env->FindClass("com/fongmi/android/tv/player/iso/IsoSessionManager");
    if (!local) return false;
    manager_class = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    manager_length = env->GetStaticMethodID(manager_class, "length", "(J)J");
    manager_read = env->GetStaticMethodID(manager_class, "readAt", "(JJLjava/nio/ByteBuffer;I)I");
    manager_close = env->GetStaticMethodID(manager_class, "close", "(J)V");
    manager_prepare_track_metadata = env->GetStaticMethodID(manager_class, "prepareTrackMetadata", "(JI)V");
    if (!manager_length || !manager_read || !manager_close || !manager_prepare_track_metadata) return false;
    int result = mpv_stream_cb_add_ro(g_mpv, PROTOCOL, nullptr, stream_open);
    if (result < 0) {
        ALOGE("iso protocol registration failed: %s", mpv_error_string(result));
        return false;
    }
    return true;
}
