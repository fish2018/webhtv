/* Test-fixture muxer: obtain display order from the decoder before assigning PTS.
 * Input: the committed 16-frame independently verified AVS3 fixture.
 * Usage: avs3-mux-fixture input.avs3 output.{mp4,mkv,ts} [repetitions] [movflags]
 */
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CHECK(call) do { int r = (call); if (r < 0) { \
    char error[AV_ERROR_MAX_STRING_SIZE]; av_strerror(r, error, sizeof(error)); \
    fprintf(stderr, "%s: %s\n", #call, error); return 1; } } while (0)

static int collect(AVCodecContext *decoder, AVFrame *frame, int order[16], int *count) {
    int ret;
    while ((ret = avcodec_receive_frame(decoder, frame)) >= 0) {
        if (frame->pts < 0 || frame->pts >= 16 || *count >= 16) return AVERROR_INVALIDDATA;
        order[frame->pts] = (*count)++;
        av_frame_unref(frame);
    }
    return ret == AVERROR(EAGAIN) || ret == AVERROR_EOF ? 0 : ret;
}

int main(int argc, char **argv) {
    if (argc < 3 || argc > 5) return 2;
    int repeats = argc >= 4 ? atoi(argv[3]) : 1;
    if (repeats < 1 || repeats > 1000) return 2;
    AVFormatContext *input = NULL, *output = NULL;
    CHECK(avformat_open_input(&input, argv[1], NULL, NULL));
    CHECK(avformat_find_stream_info(input, NULL));
    const AVCodec *codec = avcodec_find_decoder_by_name("libuavs3d");
    AVCodecContext *decoder = avcodec_alloc_context3(codec);
    if (!decoder || input->nb_streams != 1) return 3;
    CHECK(avcodec_parameters_to_context(decoder, input->streams[0]->codecpar));
    decoder->thread_count = 2;
    CHECK(avcodec_open2(decoder, codec, NULL));
    AVPacket *packets[16] = {0}, *packet = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    int count = 0, frames = 0, order[16];
    for (int i = 0; i < 16; i++) order[i] = -1;
    int ret;
    while ((ret = av_read_frame(input, packet)) >= 0) {
        if (count == 16) return 4;
        packet->pts = packet->dts = count;
        packets[count++] = av_packet_clone(packet);
        if (!packets[count - 1]) return 4;
        CHECK(avcodec_send_packet(decoder, packet));
        CHECK(collect(decoder, frame, order, &frames));
        av_packet_unref(packet);
    }
    if (ret != AVERROR_EOF) return 4;
    CHECK(avcodec_send_packet(decoder, NULL));
    CHECK(collect(decoder, frame, order, &frames));
    if (count != 16 || frames != 16) return 4;
    int delay = 0;
    for (int i = 0; i < 16; i++) {
        if (order[i] < 0) return 4;
        if (i - order[i] > delay) delay = i - order[i];
    }
    CHECK(avformat_alloc_output_context2(&output, NULL, NULL, argv[2]));
    AVStream *stream = avformat_new_stream(output, NULL);
    if (!stream) return 5;
    CHECK(avcodec_parameters_copy(stream->codecpar, input->streams[0]->codecpar));
    stream->codecpar->codec_tag = 0;
    // The raw demuxer does not synthesize codecpar extradata. MOV requires an
    // av3c record; its muxer wraps this raw sequence header in that record.
    int sequence_size = 0;
    for (int i = 4; i + 3 < packets[0]->size; i++) {
        const uint8_t *p = packets[0]->data + i;
        if (!p[0] && !p[1] && p[2] == 1 && p[3] == 0xb3) {
            sequence_size = i;
            break;
        }
    }
    if (sequence_size < 13 || sequence_size > 65535) return 5;
    av_freep(&stream->codecpar->extradata);
    stream->codecpar->extradata = av_mallocz(sequence_size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!stream->codecpar->extradata) return 5;
    memcpy(stream->codecpar->extradata, packets[0]->data, sequence_size);
    stream->codecpar->extradata_size = sequence_size;
    stream->time_base = (AVRational){1, 25};
    stream->avg_frame_rate = (AVRational){25, 1};
    CHECK(avio_open(&output->pb, argv[2], AVIO_FLAG_WRITE));
    // Write the final fragmented MP4 directly: a second remux through a demuxer
    // without av3c support would discard this sequence configuration.
    AVDictionary *options = NULL;
    if (argc == 5) CHECK(av_dict_set(&options, "movflags", argv[4], 0));
    CHECK(avformat_write_header(output, &options));
    av_dict_free(&options);
    for (int loop = 0; loop < repeats; loop++) {
        for (int i = 0; i < 16; i++) {
            CHECK(av_packet_ref(packet, packets[i]));
            packet->pts = loop * 16 + order[i];
            packet->dts = loop * 16 + i - delay;
            packet->duration = 1;
            packet->pos = -1;
            av_packet_rescale_ts(packet, (AVRational){1, 25}, stream->time_base);
            CHECK(av_interleaved_write_frame(output, packet));
        }
    }
    CHECK(av_write_trailer(output));
    CHECK(avio_closep(&output->pb));
    avformat_free_context(output);
    for (int i = 0; i < 16; i++) av_packet_free(&packets[i]);
    av_packet_free(&packet);
    av_frame_free(&frame);
    avcodec_free_context(&decoder);
    avformat_close_input(&input);
    printf("Muxed %d frames with display-order PTS (reorder delay %d).\n", repeats * 16, delay);
    return 0;
}
