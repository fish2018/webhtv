/* Exercise the actual demuxer -> AVPacket -> AVFrame boundary, including seek.
 * This native command uses the player's codec libraries, never a helper APK. */
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/pixdesc.h>
#include <libavutil/sha.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int headers(void)
{
    const AVCodec *codec = avcodec_find_decoder_by_name("libuavs3d");
    if (!codec) return 1;
    for (int container = 0; container < 3; container++) {
        for (int sample = 0; sample < 3; sample++) {
            uint8_t header[] = {0, 0, 1, 0xb0, 0x32, 0x55};
            header[4] = sample == 0 ? 0x30 : sample == 1 ? 0x7f : 0x32;
            int expected = sample == 2 ? AVERROR_INVALIDDATA : AVERROR_PATCHWELCOME;
            AVCodecContext *ctx = avcodec_alloc_context3(codec);
            if (!ctx) return 1;
            ctx->thread_count = 2;
            if (container < 2) {
                int offset = container ? 3 : 0;
                ctx->extradata_size = sizeof(header) + (container ? 4 : 0);
                ctx->extradata = av_mallocz(ctx->extradata_size + AV_INPUT_BUFFER_PADDING_SIZE);
                if (!ctx->extradata) { avcodec_free_context(&ctx); return 1; }
                if (container) { ctx->extradata[0] = 1; ctx->extradata[2] = sizeof(header); }
                memcpy(ctx->extradata + offset, header, sizeof(header));
            }
            int ret = avcodec_open2(ctx, codec, NULL);
            if (container == 2 && ret >= 0) {
                AVPacket *packet = av_packet_alloc();
                AVFrame *frame = av_frame_alloc();
                if (!packet || !frame || av_new_packet(packet, sizeof(header)) < 0) {
                    av_frame_free(&frame);
                    av_packet_free(&packet);
                    avcodec_free_context(&ctx);
                    return 1;
                }
                memcpy(packet->data, header, sizeof(header));
                ret = avcodec_send_packet(ctx, packet);
                if (ret >= 0) ret = avcodec_receive_frame(ctx, frame);
                av_frame_free(&frame);
                av_packet_free(&packet);
            }
            avcodec_free_context(&ctx);
            if (ret != expected) {
                fprintf(stderr, "header error mismatch: container=%d profile=%x ret=%d expected=%d\n",
                        container, header[4], ret, expected);
                return 1;
            }
        }
    }
    puts("PASS unknown 0x30/0x7f and truncated 0x32: raw/av3c/in-band errors and cleanup");
    return 0;
}

static int metadata(const char *path)
{
    FILE *input = fopen(path, "rb");
    if (!input) return 1;
    uint8_t data[65536];
    size_t size = fread(data, 1, sizeof(data), input);
    fclose(input);
    if (!size || size == sizeof(data)) return 1;
    const AVCodec *codec = avcodec_find_decoder_by_name("libuavs3d");
    AVCodecContext *ctx = avcodec_alloc_context3(codec);
    if (!codec || !ctx) return 1;
    ctx->thread_count = 2;
    ctx->extradata = av_mallocz(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!ctx->extradata) { avcodec_free_context(&ctx); return 1; }
    memcpy(ctx->extradata, data, size);
    ctx->extradata_size = size;
    int result = avcodec_open2(ctx, codec, NULL);
    if (result >= 0)
        printf("metadata size=%dx%d sar=%d/%d range=%d depth=%d\n", ctx->width, ctx->height,
               ctx->sample_aspect_ratio.num, ctx->sample_aspect_ratio.den,
               ctx->color_range, ctx->bits_per_raw_sample);
    avcodec_free_context(&ctx);
    return result < 0;
}

static int receive(AVCodecContext *ctx, AVFrame *frame, int pass, int *frames,
                   int limit, int check_pts, int64_t *last_pts, struct AVSHA *sha)
{
    int ret;
    while (*frames < limit && (ret = avcodec_receive_frame(ctx, frame)) >= 0) {
        const AVPixFmtDescriptor *desc = av_pix_fmt_desc_get(frame->format);
        if (!desc || (desc->comp[0].depth != 8 && desc->comp[0].depth != 10))
            return AVERROR_INVALIDDATA;
        if (check_pts && frame->pts != AV_NOPTS_VALUE) {
            if (*last_pts != AV_NOPTS_VALUE && frame->pts <= *last_pts)
                return AVERROR_INVALIDDATA;
            *last_pts = frame->pts;
        }
        int bytes = desc->comp[0].depth > 8 ? 2 : 1;
        uint64_t hash = UINT64_C(14695981039346656037);
        for (int c = 0; c < 3; c++) {
            int width = (frame->width + (c != 0)) >> (c != 0);
            int height = (frame->height + (c != 0)) >> (c != 0);
            for (int y = 0; y < height; y++) {
                const uint8_t *p = frame->data[c] + y * frame->linesize[c];
                av_sha_update(sha, p, width * bytes);
                for (int x = 0; x < width * bytes; x++)
                    hash = (hash ^ p[x]) * UINT64_C(1099511628211);
            }
        }
        printf("pass=%d frame=%d pts=%" PRId64 " size=%dx%d depth=%d hash=%016" PRIx64 "\n",
               pass, (*frames)++, frame->pts, frame->width, frame->height,
               desc->comp[0].depth, hash);
        av_frame_unref(frame);
    }
    return *frames == limit || ret == AVERROR(EAGAIN) || ret == AVERROR_EOF ? 0 : ret;
}

int main(int argc, char **argv)
{
    if (argc == 2 && !strcmp(argv[1], "--headers")) return headers();
    if (argc == 3 && !strcmp(argv[1], "--metadata")) return metadata(argv[2]);
    if (argc < 3 || argc > 5) {
        fprintf(stderr, "usage: ffmpeg_contract INPUT FRAME_LIMIT [PASSES] [SEEK_US]\n");
        return 2;
    }
    int limit = atoi(argv[2]), passes = argc >= 4 ? atoi(argv[3]) : 2;
    int64_t seek_us = argc == 5 ? strtoll(argv[4], NULL, 10) : 0;
    if (limit < 1 || limit > 3000 || passes < 1 || passes > 2 || seek_us < 0) return 2;
    AVFormatContext *format = NULL;
    AVCodecContext *ctx = NULL;
    AVPacket *packet = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    struct AVSHA *sha = av_sha_alloc();
    int result = 1, ret = 0;
    if (!packet || !frame || !sha || (ret = avformat_open_input(&format, argv[1], NULL, NULL)) < 0)
        goto done;
    /* Stream discovery must not decode dozens of 4K pictures merely to probe.
     * The MP4 supplies complete codec parameters; raw streams need the parser. */
    if (!format->nb_streams && (ret = avformat_find_stream_info(format, NULL)) < 0)
        goto done;
    int stream = -1;
    for (unsigned i = 0; i < format->nb_streams; i++)
        if (format->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) { stream = i; break; }
    if (stream < 0) goto done;
    /* Raw ES contains no container timestamps; its demuxer synthesizes packet
     * times before frame reordering. Pixel/flush checks still apply to it. */
    int raw = !strcmp(format->iformat->name, "avs3");
    const AVCodec *codec = avcodec_find_decoder_by_name("libuavs3d");
    if (!codec || !(ctx = avcodec_alloc_context3(codec))) goto done;
    if ((ret = avcodec_parameters_to_context(ctx, format->streams[stream]->codecpar)) < 0) goto done;
    ctx->thread_count = 2;
    ctx->pkt_timebase = format->streams[stream]->time_base;
    if ((ret = avcodec_open2(ctx, codec, NULL)) < 0) goto done;
    uint8_t first_hash[32] = {0};
    for (int pass = 0; pass < passes; pass++) {
        int frames = 0;
        int64_t last_pts = AV_NOPTS_VALUE;
        if (pass || seek_us) {
            if (raw) {
                if (avio_seek(format->pb, 0, SEEK_SET) < 0) goto done;
                avformat_flush(format);
            } else if ((ret = av_seek_frame(format, stream,
                av_rescale_q(seek_us, AV_TIME_BASE_Q, ctx->pkt_timebase), AVSEEK_FLAG_BACKWARD)) < 0) goto done;
            avcodec_flush_buffers(ctx);
        }
        av_sha_init(sha, 256);
        while (frames < limit && (ret = av_read_frame(format, packet)) >= 0) {
            if (packet->stream_index == stream) {
                if ((ret = avcodec_send_packet(ctx, packet)) < 0) goto done;
                if ((ret = receive(ctx, frame, pass, &frames, limit, !raw, &last_pts, sha)) < 0) goto done;
            }
            av_packet_unref(packet);
        }
        if (frames < limit) {
            if (ret != AVERROR_EOF || (ret = avcodec_send_packet(ctx, NULL)) < 0) goto done;
            if ((ret = receive(ctx, frame, pass, &frames, limit, !raw, &last_pts, sha)) < 0) goto done;
        }
        if (frames != limit) goto done;
        uint8_t digest[32];
        av_sha_final(sha, digest);
        printf("pass=%d frames=%d sha256=", pass, frames);
        for (int i = 0; i < 32; i++) printf("%02x", digest[i]);
        printf(" time_base=%d/%d\n", ctx->pkt_timebase.num, ctx->pkt_timebase.den);
        if (!pass) memcpy(first_hash, digest, 32);
        else if (memcmp(first_hash, digest, 32)) goto done;
    }
    result = 0;
done:
    if (result) {
        char message[AV_ERROR_MAX_STRING_SIZE];
        av_strerror(ret, message, sizeof(message));
        fprintf(stderr, "FFmpeg contract failed: %s\n", message);
    }
    av_free(sha);
    av_frame_free(&frame);
    av_packet_free(&packet);
    avcodec_free_context(&ctx);
    avformat_close_input(&format);
    return result;
}
