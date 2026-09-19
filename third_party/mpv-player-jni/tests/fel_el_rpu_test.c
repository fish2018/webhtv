/* Validate the RPU source used by pure-BL Android FEL with a real decoder. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <libavcodec/avcodec.h>
#include <libavcodec/bsf.h>
#include <libavformat/avformat.h>
#include <libavutil/dovi_meta.h>
#include <libavutil/intreadwrite.h>
#include <libavutil/opt.h>

#define SAMPLE_FRAMES 120
struct rpu_record {
    int64_t pts;
    uint8_t *data;
    size_t size;
    int seen;
};

static void save_rpu(struct rpu_record *r, const AVPacket *packet, int length_size)
{
    r->pts = packet->pts;
    for (int offset = 0; offset < packet->size;) {
        assert(packet->size - offset >= length_size);
        unsigned length = 0;
        for (int i = 0; i < length_size; i++)
            length = (length << 8) | packet->data[offset++];
        assert(length >= 2 && length <= (unsigned)(packet->size - offset));
        const uint8_t *nal = packet->data + offset;
        if ((nal[0] >> 1 & 63) == 62) {
            assert(!r->data && length > 2);
            r->size = length - 2;
            r->data = av_memdup(nal + 2, r->size);
            assert(r->data);
        }
        offset += length;
    }
    assert(r->data);
}

static int drain_frames(AVCodecContext *decoder, AVFrame *frame,
                         struct rpu_record *records, int count, int *decoded)
{
    int ret;
    while ((ret = avcodec_receive_frame(decoder, frame)) >= 0) {
        int found = -1;
        for (int i = 0; i < count; i++) {
            if (records[i].pts == frame->pts) {
                assert(found < 0);
                found = i;
            }
        }
        assert(found >= 0);
        struct rpu_record *r = &records[found];
        assert(!r->seen++);
        const AVFrameSideData *raw =
            av_frame_get_side_data(frame, AV_FRAME_DATA_DOVI_RPU_BUFFER);
        const AVFrameSideData *sd =
            av_frame_get_side_data(frame, AV_FRAME_DATA_DOVI_METADATA);
        assert(raw && raw->size == r->size && !memcmp(raw->data, r->data, r->size));
        assert(sd);
        const AVDOVIMetadata *metadata = (const void *)sd->data;
        const AVDOVIRpuDataHeader *header = av_dovi_get_header(metadata);
        const AVDOVIDataMapping *mapping = av_dovi_get_mapping(metadata);
        assert(!header->disable_residual_flag);
        assert(header->bl_bit_depth == 10 && header->el_bit_depth == 10);
        assert(mapping->nlq_method_idc == AV_DOVI_NLQ_LINEAR_DZ);
        assert(frame->width == 1920 && frame->height == 1080);
        assert(frame->format == AV_PIX_FMT_YUV420P10LE);
        (*decoded)++;
        av_frame_unref(frame);
    }
    assert(ret == AVERROR(EAGAIN) || ret == AVERROR_EOF);
    return ret;
}

static void sample_segment(AVFormatContext *format, int stream, int64_t seek_us)
{
    AVStream *st = format->streams[stream];
    int64_t seek_pts = av_rescale_q(seek_us, AV_TIME_BASE_Q, st->time_base);
    assert(av_seek_frame(format, stream, seek_pts, AVSEEK_FLAG_BACKWARD) >= 0);
    AVBSFContext *el = NULL;
    assert(av_bsf_alloc(av_bsf_get_by_name("dovi_split"), &el) == 0);
    assert(avcodec_parameters_copy(el->par_in, st->codecpar) == 0);
    el->time_base_in = st->time_base;
    assert(av_opt_set(el, "mode", "el_rpu", AV_OPT_SEARCH_CHILDREN) == 0);
    assert(av_bsf_init(el) == 0);
    const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_HEVC);
    AVCodecContext *decoder = avcodec_alloc_context3(codec);
    assert(decoder && avcodec_parameters_to_context(decoder, el->par_out) == 0);
    decoder->pkt_timebase = el->time_base_out;
    decoder->thread_count = 2;
    assert(avcodec_open2(decoder, codec, NULL) == 0);
    AVFrame *frame = av_frame_alloc();
    AVPacket *packet = av_packet_alloc(), *filtered = av_packet_alloc();
    assert(frame && packet && filtered);
    struct rpu_record records[SAMPLE_FRAMES] = {0};
    int count = 0, decoded = 0;
    assert(st->codecpar->extradata_size >= 23 && st->codecpar->extradata[0] == 1);
    int length_size = (st->codecpar->extradata[21] & 3) + 1;
    while (count < SAMPLE_FRAMES && av_read_frame(format, packet) >= 0) {
        if (packet->stream_index == stream) {
            save_rpu(&records[count++], packet, length_size);
            assert(av_bsf_send_packet(el, packet) == 0);
            assert(av_bsf_receive_packet(el, filtered) == 0);
            int ret = avcodec_send_packet(decoder, filtered);
            if (ret == AVERROR(EAGAIN)) {
                drain_frames(decoder, frame, records, count, &decoded);
                ret = avcodec_send_packet(decoder, filtered);
            }
            assert(ret == 0);
            av_packet_unref(filtered);
            drain_frames(decoder, frame, records, count, &decoded);
        }
        av_packet_unref(packet);
    }
    assert(count == SAMPLE_FRAMES);
    assert(avcodec_send_packet(decoder, NULL) == 0);
    assert(drain_frames(decoder, frame, records, count, &decoded) == AVERROR_EOF);
    assert(decoded == count);
    for (int i = 0; i < count; i++) {
        assert(records[i].seen == 1);
        av_free(records[i].data);
    }
    printf("PASS EL/RPU: seek-us=%lld decoded=%d; exact PTS/raw-RPU, active FEL NLQ, 1080p10 software frames\n",
           (long long)seek_us, decoded);
    av_frame_free(&frame);
    av_packet_free(&packet);
    av_packet_free(&filtered);
    avcodec_free_context(&decoder);
    av_bsf_free(&el);
}

int main(int argc, char **argv)
{
    if (argc < 2) {
        puts("SKIP EL/RPU sample decode: supply the GIJoe Profile 7 sample as argument 2 to the test script");
        return 0;
    }
    AVFormatContext *format = NULL;
    assert(avformat_open_input(&format, argv[1], NULL, NULL) == 0);
    assert(avformat_find_stream_info(format, NULL) >= 0);
    int video = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    assert(video >= 0);
    sample_segment(format, video, 0);
    sample_segment(format, video, 3212000);
    sample_segment(format, video, 33575000);
    avformat_close_input(&format);
    return 0;
}
