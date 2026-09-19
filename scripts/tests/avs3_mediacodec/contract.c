/* MediaCodec platform fakes for the unmodified production AVS3 entry points. */
#include <assert.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define AVERROR(e) (-(e))
#define AVERROR_INVALIDDATA (-1094995529)
#define AVERROR_EXTERNAL (-542398533)
#define AVERROR_DECODER_NOT_FOUND (-1128613112)
#define AV_HWACCEL_FLAG_ALLOW_PROFILE_MISMATCH 1
#define AV_HWDEVICE_TYPE_MEDIACODEC 1
#define AV_CODEC_ID_AVS3 1
#define AV_CODEC_ID_HEVC 2
#define AV_CODEC_ID_AAC 3
#define AV_LOG_ERROR 0
#define AV_LOG_WARNING 1
#define AV_LOG_INFO 2
#define AV_LOG_DEBUG 3
#define av_log(...) ((void)0)
#define AV_RB16(p) ((uint16_t)(((uint16_t)(p)[0] << 8) | (p)[1]))
#define AV_RB32(p) (((uint32_t)AV_RB16(p) << 16) | AV_RB16((p) + 2))

enum AVPixelFormat { AV_PIX_FMT_NONE = -1, AV_PIX_FMT_MEDIACODEC = 1 };
typedef struct { void *data; } AVBufferRef;
typedef struct { void *surface, *native_window; } AVMediaCodecDeviceContext;
typedef struct { int type; void *hwctx; } AVHWDeviceContext;
typedef struct { void *surface; } AVMediaCodecContext;
typedef struct {
    uint8_t *extradata;
    int extradata_size, codec_id, hwaccel_flags;
    void *hwaccel_context;
    AVBufferRef *hw_device_ctx;
} AVCodecContext;
typedef struct { int unused; } FFAMediaCodec;
typedef struct {
    void *surface;
    int use_ndk_codec;
    char *codec_name;
    FFAMediaCodec *codec;
} MediaCodecDecContext;
typedef struct {
    const uint8_t *csd;
    size_t size;
    int buffers, profile_present, profile;
    const char *mime;
} FFAMediaFormat;

static int have_hardware, have_software, missing_jvm, create_fails;
static int expected_hardware_only, lookups, by_name, by_type, profile_mismatch;
static int surface_refs;
static FFAMediaCodec component;

static char *av_strdup(const char *s)
{
    size_t size = strlen(s) + 1;
    char *copy = malloc(size);
    assert(copy);
    memcpy(copy, s, size);
    return copy;
}

static void ff_AMediaFormat_setBuffer(FFAMediaFormat *f, const char *key, void *p, size_t size)
{
    assert(!strcmp(key, "csd-0"));
    f->csd = p;
    f->size = size;
    f->buffers++;
}

static int ff_AMediaFormat_getInt32(FFAMediaFormat *f, const char *key, int32_t *value)
{
    assert(!strcmp(key, "profile"));
    if (f->profile_present)
        *value = f->profile;
    return f->profile_present;
}

static void ff_AMediaFormat_setString(FFAMediaFormat *f, const char *key, const char *value)
{
    assert(!strcmp(key, "mime"));
    f->mime = value;
}

static enum AVPixelFormat ff_get_format(AVCodecContext *avctx, const enum AVPixelFormat *formats)
{
    (void)avctx;
    assert(formats[0] == AV_PIX_FMT_MEDIACODEC);
    assert(formats[1] == AV_PIX_FMT_NONE);
    return formats[0];
}

static void *ff_mediacodec_surface_ref(void *surface, void *window, void *log_ctx)
{
    (void)log_ctx;
    surface_refs++;
    return surface ? surface : window;
}

static int ff_AMediaCodecProfile_getProfileFromAVCodecContext(AVCodecContext *avctx)
{
    return avctx->codec_id == AV_CODEC_ID_AVS3 ? -1 : 2;
}

static char *ff_AMediaCodecList_getCodecNameByType(const char *mime, int profile,
        int encoder, int hardware_only, const char **codec_mime, void *log_ctx)
{
    (void)log_ctx;
    assert(!encoder);
    assert(hardware_only == expected_hardware_only);
    lookups++;
    *codec_mime = mime;
    if (missing_jvm || (profile_mismatch && profile >= 0))
        return NULL;
    if (have_hardware)
        return av_strdup("c2.vendor.avs3.decoder");
    if (have_software && !hardware_only)
        return av_strdup("c2.android.software.decoder");
    return NULL;
}

static FFAMediaCodec *ff_AMediaCodec_createCodecByName(const char *name, int ndk)
{
    (void)ndk;
    assert(name);
    by_name++;
    return create_fails ? NULL : &component;
}

static FFAMediaCodec *ff_AMediaCodec_createDecoderByType(const char *mime, int ndk)
{
    assert(mime && ndk);
    by_type++;
    return &component;
}

static char *ff_AMediaCodec_getName(FFAMediaCodec *codec)
{
    assert(codec);
    return av_strdup("c2.mime-selected.decoder");
}

#include "production.inc"

static void reset_platform(int hardware_only)
{
    have_hardware = have_software = missing_jvm = create_fails = 0;
    lookups = by_name = by_type = profile_mismatch = surface_refs = 0;
    expected_hardware_only = hardware_only;
}

static void check_csd(void)
{
    uint8_t raw[] = {0, 0, 1, 0xb0, 0x32, 0x55, 0x8f, 0, 0x43, 0x86, 0xa0, 0x42};
    uint8_t saved[sizeof(raw)];
    uint8_t record[sizeof(raw) + 4] = {1, 0, sizeof(raw)};
    memcpy(saved, raw, sizeof(raw));
    memcpy(record + 3, raw, sizeof(raw));
    record[sizeof(record) - 1] = 0xfc;
    AVCodecContext ctx = { .extradata = raw, .extradata_size = sizeof(raw) };
    FFAMediaFormat fmt = {0};
    assert(!avs3_set_extradata(&ctx, &fmt));
    assert(fmt.buffers == 1 && fmt.csd == raw && fmt.size == sizeof(raw));
    assert(!fmt.profile_present && !memcmp(raw, saved, sizeof(raw)));

    ctx.extradata = record;
    ctx.extradata_size = sizeof(record);
    fmt = (FFAMediaFormat){0};
    assert(!avs3_set_extradata(&ctx, &fmt));
    assert(fmt.buffers == 1 && fmt.csd == record + 3 && fmt.size == sizeof(raw));
    assert(!memcmp(fmt.csd, saved, sizeof(raw)) && !fmt.profile_present);

    /* Hardware capability is not restricted to the software backend profiles. */
    for (int profile = 0x20; profile <= 0x32; profile += 2) {
        raw[4] = (uint8_t)profile;
        ctx = (AVCodecContext){ .extradata = raw, .extradata_size = sizeof(raw) };
        fmt = (FFAMediaFormat){0};
        assert(!avs3_set_extradata(&ctx, &fmt));
        assert(fmt.csd[4] == profile);
    }
    for (size_t size = 1; size < sizeof(record); size++) {
        uint8_t *short_record = malloc(size);
        assert(short_record);
        memcpy(short_record, record, size);
        ctx = (AVCodecContext){ .extradata = short_record, .extradata_size = (int)size };
        fmt = (FFAMediaFormat){0};
        assert(avs3_set_extradata(&ctx, &fmt) == AVERROR_INVALIDDATA);
        assert(!fmt.buffers);
        free(short_record);
    }
    ctx = (AVCodecContext){0};
    fmt = (FFAMediaFormat){0};
    assert(!avs3_set_extradata(&ctx, &fmt) && !fmt.buffers);
    ctx.extradata_size = 6;
    assert(avs3_set_extradata(&ctx, &fmt) == AVERROR_INVALIDDATA);
    ctx = (AVCodecContext){ .extradata = record, .extradata_size = sizeof(record) };
    record[1] = 0xff;
    assert(avs3_set_extradata(&ctx, &fmt) == AVERROR_INVALIDDATA);
    record[1] = record[2] = 0;
    assert(avs3_set_extradata(&ctx, &fmt) == AVERROR_INVALIDDATA);
    ctx.extradata = raw;
    ctx.extradata_size = sizeof(raw);
    raw[3] = 0xb3;
    assert(avs3_set_extradata(&ctx, &fmt) == AVERROR_INVALIDDATA);
    ctx.extradata_size = -1;
    assert(avs3_set_extradata(&ctx, &fmt) == AVERROR_INVALIDDATA);
}

static void check_hardware_selection(void)
{
    for (int ndk = 0; ndk <= 1; ndk++) {
        reset_platform(1);
        have_hardware = 1;
        AVCodecContext software_retry = { .codec_id = AV_CODEC_ID_AVS3 };
        MediaCodecDecContext unrequested = { .use_ndk_codec = ndk };
        FFAMediaFormat no_format = {0};
        assert(mediacodec_dec_get_video_codec(&software_retry, &unrequested,
               "video/avs3", &no_format) == AVERROR(EINVAL));
        assert(!lookups && !by_name && !by_type);
        AVHWDeviceContext wrong_device = { .type = 99 };
        AVBufferRef wrong_ref = { .data = &wrong_device };
        software_retry.hw_device_ctx = &wrong_ref;
        assert(mediacodec_dec_get_video_codec(&software_retry, &unrequested,
               "video/avs3", &no_format) == AVERROR(EINVAL));
        assert(!lookups && !by_name && !by_type);
        for (int no_jvm = 0; no_jvm <= 1; no_jvm++) {
            reset_platform(1);
            missing_jvm = no_jvm;
            have_software = 1;
            AVMediaCodecContext requested = {0};
            AVCodecContext ctx = { .codec_id = AV_CODEC_ID_AVS3, .hwaccel_context = &requested };
            MediaCodecDecContext dec = { .use_ndk_codec = ndk };
            FFAMediaFormat fmt = {0};
            assert(mediacodec_dec_get_video_codec(&ctx, &dec, "video/avs3", &fmt)
                   == AVERROR_DECODER_NOT_FOUND);
            assert(lookups == 1 && !by_name && !by_type && !dec.codec);
        }
        reset_platform(1);
        have_hardware = have_software = 1;
        int surface;
        AVMediaCodecDeviceContext mc = { .surface = &surface };
        AVHWDeviceContext hw = { .type = AV_HWDEVICE_TYPE_MEDIACODEC, .hwctx = &mc };
        AVBufferRef ref = { .data = &hw };
        AVCodecContext ctx = { .codec_id = AV_CODEC_ID_AVS3, .hw_device_ctx = &ref };
        MediaCodecDecContext dec = { .use_ndk_codec = ndk };
        FFAMediaFormat fmt = {0};
        assert(!mediacodec_dec_get_video_codec(&ctx, &dec, "video/avs3", &fmt));
        assert(lookups == 1 && by_name == 1 && !by_type && dec.codec == &component);
        assert(dec.surface == &surface && surface_refs == 1 && !strcmp(fmt.mime, "video/avs3"));
        free(dec.codec_name);

        reset_platform(1);
        have_hardware = create_fails = 1;
        dec = (MediaCodecDecContext){ .use_ndk_codec = ndk };
        assert(mediacodec_dec_get_video_codec(&ctx, &dec, "video/avs3", &fmt) == AVERROR_EXTERNAL);
        assert(by_name == 1 && !by_type);
        free(dec.codec_name);

        reset_platform(1);
        have_hardware = profile_mismatch = 1;
        ctx.hwaccel_flags = AV_HWACCEL_FLAG_ALLOW_PROFILE_MISMATCH;
        fmt = (FFAMediaFormat){ .profile_present = 1, .profile = 123 };
        dec = (MediaCodecDecContext){ .use_ndk_codec = ndk };
        assert(!mediacodec_dec_get_video_codec(&ctx, &dec, "video/avs3", &fmt));
        assert(lookups == 2 && by_name == 1 && !by_type);
        free(dec.codec_name);
    }
}

static void check_existing_codecs(void)
{
    /* Preserve the pre-existing HEVC NDK/JVM behavior and audio policy. */
    reset_platform(0);
    missing_jvm = 1;
    AVCodecContext ctx = { .codec_id = AV_CODEC_ID_HEVC };
    MediaCodecDecContext dec = { .use_ndk_codec = 1 };
    FFAMediaFormat fmt = {0};
    assert(!mediacodec_dec_get_video_codec(&ctx, &dec, "video/hevc", &fmt));
    assert(by_type == 1 && !by_name);
    free(dec.codec_name);
    reset_platform(1);
    have_software = 1;
    ctx.codec_id = AV_CODEC_ID_AAC;
    dec = (MediaCodecDecContext){ .use_ndk_codec = 1 };
    assert(mediacodec_dec_get_audio_codec(&ctx, &dec, "audio/mp4a-latm", &fmt) == AVERROR_EXTERNAL);
    assert(!by_type && !by_name);
    have_hardware = 1;
    assert(!mediacodec_dec_get_audio_codec(&ctx, &dec, "audio/mp4a-latm", &fmt));
    assert(by_name == 1 && !by_type);
    free(dec.codec_name);
}

int main(void)
{
    check_csd();
    check_hardware_selection();
    check_existing_codecs();
    puts("PASS AVS3 CSD bounds/profile preservation, explicit JNI/NDK hardware selection, Surface forwarding, HEVC/audio neighbors (ASan/UBSan)");
    return 0;
}
