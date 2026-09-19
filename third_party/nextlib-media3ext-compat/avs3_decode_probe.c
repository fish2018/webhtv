/* Decode real AVS3 access units through the shipped FFmpeg ABI; repeat after flush. */
#include <libavcodec/avcodec.h>
#include <libavutil/mem.h>
#include <libavutil/pixdesc.h>
#include <libavutil/sha.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int reject_header(const AVCodec *codec, const uint8_t *header, int size,
                         int container, int expected) {
    AVCodecContext *ctx = avcodec_alloc_context3(codec);
    if (!ctx) return -1;
    ctx->thread_count = 2;
    int ret;
    if (container < 2) {
        ctx->extradata_size = size + (container ? 4 : 0);
        ctx->extradata = av_mallocz(ctx->extradata_size + AV_INPUT_BUFFER_PADDING_SIZE);
        if (!ctx->extradata) { avcodec_free_context(&ctx); return -1; }
        if (container) {
            ctx->extradata[0] = 1;
            ctx->extradata[1] = size >> 8;
            ctx->extradata[2] = size;
        }
        memcpy(ctx->extradata + (container ? 3 : 0), header, size);
        ret = avcodec_open2(ctx, codec, NULL);
    } else {
        ret = avcodec_open2(ctx, codec, NULL);
        if (ret >= 0) {
            AVPacket *pkt = av_packet_alloc();
            if (!pkt || av_new_packet(pkt, size) < 0) {
                av_packet_free(&pkt);
                avcodec_free_context(&ctx);
                return -1;
            }
            memcpy(pkt->data, header, size);
            ret = avcodec_send_packet(ctx, pkt);
            if (ret >= 0) {
                AVFrame *frame = av_frame_alloc();
                if (!frame) { av_packet_free(&pkt); avcodec_free_context(&ctx); return -1; }
                ret = avcodec_receive_frame(ctx, frame);
                av_frame_free(&frame);
            }
            av_packet_free(&pkt);
        }
    }
    avcodec_free_context(&ctx);
    if (ret != expected) {
        fprintf(stderr, "header rejection failed: container=%d size=%d ret=%d expected=%d\n",
                container, size, ret, expected);
        return -1;
    }
    return 0;
}

static int check_unsupported_profiles(const AVCodec *codec) {
    uint8_t header[] = {0, 0, 1, 0xb0, 0x30, 0x55};
    for (int container = 0; container < 3; container++) {
        for (int profile = 0x30; profile <= 0x32; profile += 2) {
            header[4] = profile;
            if (reject_header(codec, header, sizeof(header), container, AVERROR_PATCHWELCOME)) return -1;
        }
        if (reject_header(codec, header, 4, container, AVERROR_INVALIDDATA)) return -1;
    }
    puts("unsupported profiles: raw/av3c/in-band 0x30/0x32 and truncated headers rejected");
    return 0;
}

static int drain(AVCodecContext *ctx, AVFrame *frame, struct AVSHA *sha, int *frames) {
    int ret;
    while ((ret = avcodec_receive_frame(ctx, frame)) >= 0) {
        const AVPixFmtDescriptor *desc = av_pix_fmt_desc_get(frame->format);
        if (!desc || (desc->comp[0].depth != 8 && desc->comp[0].depth != 10)) return -1;
        if (frame->width != 256 || frame->height != 144) return -1;
        int scale = desc->comp[0].depth > 8 ? 2 : 1;
        for (int p = 0; p < 3; p++) {
            int w = p ? frame->width / 2 : frame->width;
            int h = p ? frame->height / 2 : frame->height;
            for (int y = 0; y < h; y++)
                av_sha_update(sha, frame->data[p] + y * frame->linesize[p], w * scale);
        }
        (*frames)++;
        av_frame_unref(frame);
    }
    return ret == AVERROR(EAGAIN) || ret == AVERROR_EOF ? 0 : ret;
}

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    FILE *file = fopen(argv[1], "rb");
    if (!file) return 2;
    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    rewind(file);
    if (size <= 0 || size > 4 * 1024 * 1024) return 2;
    uint8_t *data = av_mallocz(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!data || fread(data, 1, size, file) != (size_t) size) return 2;
    fclose(file);
    const AVCodec *codec = avcodec_find_decoder_by_name("libuavs3d");
    AVCodecContext *ctx = avcodec_alloc_context3(codec);
    if (!codec || !ctx) return 3;
    if (check_unsupported_profiles(codec)) return 11;
    ctx->thread_count = 2;
    int ret = avcodec_open2(ctx, codec, NULL);
    if (ret < 0) return 4;
    AVPacket *packet = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    struct AVSHA *sha = av_sha_alloc();
    if (!packet || !frame || !sha) return 4;
    for (int pass = 0; pass < 2; pass++) {
        AVCodecParserContext *parser = av_parser_init(AV_CODEC_ID_AVS3);
        if (!parser) return 5;
        av_sha_init(sha, 256);
        int frames = 0;
        int offset = 0;
        while (offset < size) {
            int consumed = av_parser_parse2(parser, ctx, &packet->data, &packet->size,
                data + offset, size - offset, AV_NOPTS_VALUE, AV_NOPTS_VALUE, offset);
            if (consumed < 0 || (!consumed && !packet->size)) return 6;
            offset += consumed;
            if (!packet->size) continue;
            if (avcodec_send_packet(ctx, packet) < 0 || drain(ctx, frame, sha, &frames) < 0) return 7;
        }
        av_parser_parse2(parser, ctx, &packet->data, &packet->size, NULL, 0,
            AV_NOPTS_VALUE, AV_NOPTS_VALUE, offset);
        if (packet->size && (avcodec_send_packet(ctx, packet) < 0 || drain(ctx, frame, sha, &frames) < 0)) return 8;
        if (avcodec_send_packet(ctx, NULL) < 0 || drain(ctx, frame, sha, &frames) < 0) return 9;
        uint8_t digest[32];
        av_sha_final(sha, digest);
        printf("pass=%d frames=%d sha256=", pass, frames);
        for (int i = 0; i < 32; i++) printf("%02x", digest[i]);
        printf("\n");
        if (frames != 16) return 10;
        av_parser_close(parser);
        avcodec_flush_buffers(ctx);
    }
    av_free(sha);
    av_frame_free(&frame);
    // packet data is owned by the parser/input, never by this AVPacket.
    packet->data = NULL;
    packet->size = 0;
    av_packet_free(&packet);
    avcodec_free_context(&ctx);
    av_free(data);
    return 0;
}
