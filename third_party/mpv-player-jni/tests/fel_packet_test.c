#include "video/decode/android_fel_packet.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <libavformat/avformat.h>
#include <libavutil/dovi_meta.h>
#include <libavutil/intreadwrite.h>

struct nal_view { const uint8_t *data; int size; };

static int nals(const AVPacket *p, int length_size, struct nal_view *out)
{
    int count = 0;
    for (int pos = 0; pos < p->size;) {
        int size = 0;
        if (length_size) {
            assert(p->size - pos >= length_size);
            for (int j = 0; j < length_size; j++)
                size = (size << 8) | p->data[pos++];
        } else {
            assert(p->size - pos >= 4 && AV_RB32(p->data + pos) == 1);
            pos += 4;
            size = p->size - pos;
            for (int end = pos; end + 3 < p->size; end++) {
                if (AV_RB32(p->data + end) == 1) {
                    size = end - pos;
                    break;
                }
            }
        }
        assert(size >= 2 && size <= p->size - pos && count < 128);
        out[count++] = (struct nal_view){p->data + pos, size};
        pos += size;
    }
    return count;
}

static int compare_bl(const AVPacket *input, const AVPacket *output,
                      int length_size)
{
    struct nal_view src[128], dst[128];
    int ni = nals(input, length_size, src);
    int no = nals(output, length_size, dst);
    int kept = 0, removed = 0;
    for (int i = 0; i < ni; i++) {
        int type = (src[i].data[0] >> 1) & 63;
        if (type == 62 || type == 63) {
            removed++;
            continue;
        }
        bool size_ok = kept < no && (src[i].size == dst[kept].size ||
                       (!length_size && src[i].size < dst[kept].size));
        if (!size_ok) {
            fprintf(stderr, "NAL mismatch length-size=%d input-count=%d output-count=%d "
                    "input-index=%d type=%d size=%d output-index=%d size=%d\n",
                    length_size, ni, no, i, type, src[i].size, kept,
                    kept < no ? dst[kept].size : -1);
        }
        assert(size_ok);
        assert(!memcmp(src[i].data, dst[kept].data, src[i].size));
        // Annex-B permits trailing_zero_8bits. FFmpeg's three-byte start-code
        // scanner retains the first zero of the next four-byte start code.
        // Allow only added zero padding, never altered/truncated payload bytes.
        for (int extra = src[i].size; extra < dst[kept].size; extra++)
            assert(dst[kept].data[extra] == 0);
        kept++;
    }
    assert(kept == no);
    assert(input->pts == output->pts && input->dts == output->dts);
    assert(input->duration == output->duration && input->flags == output->flags);
    int props = 0;
    for (int i = 0; i < input->side_data_elems; i++) {
        const AVPacketSideData *sd = &input->side_data[i];
        size_t size = 0;
        uint8_t *data = av_packet_get_side_data(output, sd->type, &size);
        if (sd->type == AV_PKT_DATA_DOVI_CONF || sd->type == AV_PKT_DATA_HEVC_CONF) {
            assert(!data);
            continue;
        }
        assert(data && size == sd->size && !memcmp(data, sd->data, size));
        props++;
    }
    assert(props == output->side_data_elems);
    return removed;
}

static AVCodecContext *context(int length_size)
{
    AVCodecContext *c = avcodec_alloc_context3(NULL);
    assert(c);
    c->codec_type = AVMEDIA_TYPE_VIDEO;
    c->codec_id = AV_CODEC_ID_HEVC;
    c->width = 3840;
    c->height = 2160;
    c->pkt_timebase = (AVRational){1, 1000};
    if (length_size) {
        c->extradata = av_mallocz(23 + AV_INPUT_BUFFER_PADDING_SIZE);
        assert(c->extradata);
        c->extradata_size = 23;
        c->extradata[0] = 1;
        c->extradata[21] = length_size - 1;
    }
    AVPacketSideData *sd = av_packet_side_data_new(&c->coded_side_data,
        &c->nb_coded_side_data, AV_PKT_DATA_DOVI_CONF,
        sizeof(AVDOVIDecoderConfigurationRecord), 0);
    assert(sd);
    AVDOVIDecoderConfigurationRecord *cfg = (void *)sd->data;
    *cfg = (AVDOVIDecoderConfigurationRecord){.dv_profile = 7,
        .bl_present_flag = 1, .el_present_flag = 1, .rpu_present_flag = 1};
    return c;
}

static AVPacket *packet(int length_size, bool only_el)
{
    static const uint8_t payload[][6] = {
        {0x02, 0x01, 0xa5, 0x33, 0x51, 0x80}, // BL
        {0x7c, 0x01, 0x11, 0x22, 0x31, 0x80}, // verbatim RPU
        {0x7e, 0x01, 0x02, 0x01, 0x44, 0x80}, // wrapped EL
    };
    AVPacket *p = av_packet_alloc();
    assert(p);
    int prefix = length_size ? length_size : 4;
    assert(av_new_packet(p, (prefix + 6) * (only_el ? 1 : 3)) == 0);
    uint8_t *dst = p->data;
    for (int i = only_el ? 2 : 0; i < 3; i++) {
        for (int j = 0; j < prefix; j++)
            *dst++ = (length_size ? 6U : 1U) >> (8 * (prefix - 1 - j));
        memcpy(dst, payload[i], 6);
        dst += 6;
    }
    p->pts = 19810;
    p->dts = 19726;
    p->duration = 42;
    p->flags = AV_PKT_FLAG_KEY;
    uint8_t *sd = av_packet_new_side_data(p, AV_PKT_DATA_STRINGS_METADATA, 4);
    assert(sd);
    memcpy(sd, "x\0y\0", 4);
    sd = av_packet_new_side_data(p, AV_PKT_DATA_DOVI_CONF,
                                 sizeof(AVDOVIDecoderConfigurationRecord));
    assert(sd);
    *(AVDOVIDecoderConfigurationRecord *)sd =
        (AVDOVIDecoderConfigurationRecord){.dv_profile = 7,
            .bl_present_flag = 1, .el_present_flag = 1, .rpu_present_flag = 1};
    sd = av_packet_new_side_data(p, AV_PKT_DATA_HEVC_CONF, 4);
    assert(sd);
    memcpy(sd, "hvcE", 4);
    return p;
}

static void policy_test(void)
{
    for (int fel = 0; fel < 2; fel++) {
        for (int sw = 0; sw < 2; sw++) {
            for (int mc = 0; mc < 2; mc++) {
                for (int profile = 5; profile <= 8; profile++) {
                    AVCodecContext *c = context(0);
                    struct mp_android_fel_packet_filter f = {0};
                    assert(mp_android_fel_packet_init(&f, c, fel, sw, mc, profile) == 0);
                    bool active = fel && !sw && mc && profile == 7;
                    assert(!!f.bsf == active && !!f.packet == active);
                    if (!active) {
                        assert(c->nb_coded_side_data == 1);
                        AVPacket *p = packet(0, false);
                        const AVPacket *out = NULL;
                        assert(mp_android_fel_packet_prepare(&f, p, &out) == 1);
                        assert(out == p); // unchanged, no packet allocation or scan
                        av_packet_free(&p);
                    }
                    mp_android_fel_packet_reset(&f);
                    mp_android_fel_packet_uninit(&f);
                    avcodec_free_context(&c);
                }
            }
        }
    }
    AVCodecContext *c = context(0);
    c->codec_id = AV_CODEC_ID_H264;
    struct mp_android_fel_packet_filter f = {0};
    assert(mp_android_fel_packet_init(&f, c, true, false, true, 7) == 0);
    assert(!f.bsf && !f.packet);
    avcodec_free_context(&c);
}

static void format_test(int length_size)
{
    AVCodecContext *c = context(length_size);
    struct mp_android_fel_packet_filter f = {0};
    assert(mp_android_fel_packet_init(&f, c, true, false, true, 7) == 0);
    assert(c->nb_coded_side_data == 0);
    const AVDOVIDecoderConfigurationRecord *cfg =
        (const void *)f.bsf->par_in->coded_side_data[0].data;
    assert(cfg->dv_profile == 7 && cfg->el_present_flag && cfg->rpu_present_flag);
    AVPacket *p = packet(length_size, false);
    AVPacket borrowed = *p; // same ownership contract as mp_set_av_packet
    const AVPacket *out = NULL;
    for (int retry = 0; retry < 4; retry++) {
        assert(mp_android_fel_packet_prepare(&f, &borrowed, &out) == 1);
        assert(compare_bl(p, out, length_size) == 2);
        assert(av_buffer_get_ref_count(p->buf) == 1);
        AVPacket *accepted = av_packet_clone(out);
        assert(accepted);
        mp_android_fel_packet_reset(&f);
        assert(compare_bl(p, accepted, length_size) == 2);
        av_packet_free(&accepted);
        // The decoder could have returned EAGAIN. The borrowed packet must
        // still be available for exactly the same retry, including side data.
        assert(borrowed.data == p->data && borrowed.buf == p->buf);
    }
    // Foreign demux input without an AVBufferRef must also stay borrowed.
    borrowed.buf = NULL;
    assert(mp_android_fel_packet_prepare(&f, &borrowed, &out) == 1);
    assert(compare_bl(p, out, length_size) == 2);
    AVPacket *el = packet(length_size, true);
    assert(mp_android_fel_packet_prepare(&f, el, &out) == 0 && !out);
    assert(mp_android_fel_packet_prepare(&f, p, &out) == 1);
    assert(compare_bl(p, out, length_size) == 2);
    assert(mp_android_fel_packet_prepare(&f, NULL, &out) == 1 && !out);
    mp_android_fel_packet_reset(&f);
    if (length_size) {
        AVPacket *bad = av_packet_clone(p);
        assert(bad && av_packet_make_writable(bad) == 0);
        memset(bad->data, 0, length_size);
        bad->data[length_size - 1] = 127; // length exceeds the actual packet
        assert(mp_android_fel_packet_prepare(&f, bad, &out) < 0 && !out);
        av_packet_free(&bad);
        assert(mp_android_fel_packet_prepare(&f, p, &out) == 1);
        assert(compare_bl(p, out, length_size) == 2);
    }
    av_packet_free(&el);
    av_packet_free(&p);
    mp_android_fel_packet_uninit(&f);
    avcodec_free_context(&c);
}

static void sample_test(const char *path)
{
    AVFormatContext *format = NULL;
    assert(avformat_open_input(&format, path, NULL, NULL) == 0);
    assert(avformat_find_stream_info(format, NULL) >= 0);
    int video = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    assert(video >= 0);
    AVStream *st = format->streams[video];
    AVCodecContext *c = avcodec_alloc_context3(NULL);
    assert(c && avcodec_parameters_to_context(c, st->codecpar) == 0);
    c->pkt_timebase = st->time_base;
    struct mp_android_fel_packet_filter f = {0};
    assert(mp_android_fel_packet_init(&f, c, true, false, true, 7) == 0);
    assert(st->codecpar->extradata_size >= 23 && st->codecpar->extradata[0] == 1);
    int length_size = (st->codecpar->extradata[21] & 3) + 1;
    AVPacket *p = av_packet_alloc();
    assert(p);
    int packets = 0, removed = 0;
    long long input_bytes = 0, output_bytes = 0;
    while (packets < 120 && av_read_frame(format, p) >= 0) {
        if (p->stream_index == video) {
            const AVPacket *out = NULL;
            assert(mp_android_fel_packet_prepare(&f, p, &out) == 1);
            removed += compare_bl(p, out, length_size);
            packets++;
            input_bytes += p->size;
            output_bytes += out->size;
        }
        av_packet_unref(p);
    }
    assert(packets == 120 && removed > 0 && input_bytes > output_bytes);
    printf("PASS sample: packets=%d removed-DV-NALs=%d input-bytes=%lld "
           "output-bytes=%lld; every BL payload and timestamp identical\n",
           packets, removed, input_bytes, output_bytes);
    av_packet_free(&p);
    mp_android_fel_packet_uninit(&f);
    avcodec_free_context(&c);
    avformat_close_input(&format);
}

int main(int argc, char **argv)
{
    policy_test();
    for (int length_size = 0; length_size <= 4; length_size++)
        format_test(length_size);
    if (argc > 1)
        sample_test(argv[1]);
    puts("PASS: FEL-only pure-BL isolation, 5 packet formats, ownership/retry, "
         "reset/drain, empty/malformed packets and private decoder config isolation");
    return 0;
}
