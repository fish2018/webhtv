/* Private FFmpeg adapter, included after uavs3d_context in libuavs3d.c.
 * Keep the decoder registration stable for existing nextlib/mpv consumers. */
#ifndef WEBHTV_HPM_FFMPEG_H
#define WEBHTV_HPM_FFMPEG_H

static int libhpm_error(AVCodecContext *avctx, int result)
{
    uavs3d_context *h = avctx->priv_data;
    av_log(avctx, AV_LOG_ERROR, "AVS3 HPM decode failed: %s\n", webhtv_hpm_error(h->hpm));
    return result == WEBHPM_NOMEM ? AVERROR(ENOMEM) :
           result == WEBHPM_UNSUPPORTED ? AVERROR_PATCHWELCOME : AVERROR_INVALIDDATA;
}

static int libhpm_update_info(AVCodecContext *avctx)
{
    uavs3d_context *h = avctx->priv_data;
    const WebHpmInfo *info = webhtv_hpm_info(h->hpm);
    int ret;
    if (!info->width)
        return 0;
    if ((ret = ff_set_dimensions(avctx, info->width, info->height)) < 0)
        return ret;
    avctx->profile = info->profile;
    avctx->level = info->level;
    avctx->pix_fmt = AV_PIX_FMT_YUV420P10;
    avctx->bits_per_raw_sample = info->bit_depth;
    avctx->has_b_frames = info->reorder_delay;
    /* AVS3 codes 2..4 describe the display ratio, while AVFrame carries the
     * pixel ratio. Match Media3 Avs3Config for non-square-pixel pictures. */
    if (info->aspect_ratio == 1) {
        avctx->sample_aspect_ratio = (AVRational){1, 1};
    } else if (info->aspect_ratio >= 2 && info->aspect_ratio <= 4) {
        const AVRational display[] = {{4, 3}, {16, 9}, {221, 100}};
        avctx->sample_aspect_ratio = av_div_q(display[info->aspect_ratio - 2],
                                            (AVRational){info->width, info->height});
    }
    if (info->frame_rate_code > 0 && info->frame_rate_code < FF_ARRAY_ELEMS(ff_avs3_frame_rate_tab))
        avctx->framerate = ff_avs3_frame_rate_tab[info->frame_rate_code];
    if (info->color_present) {
        avctx->color_range = info->color_range ? AVCOL_RANGE_JPEG : AVCOL_RANGE_MPEG;
        if (info->color_primaries >= 0 && info->color_primaries < FF_ARRAY_ELEMS(ff_avs3_color_primaries_tab))
            avctx->color_primaries = ff_avs3_color_primaries_tab[info->color_primaries];
        if (info->color_trc >= 0 && info->color_trc < FF_ARRAY_ELEMS(ff_avs3_color_transfer_tab))
            avctx->color_trc = ff_avs3_color_transfer_tab[info->color_trc];
        if (info->colorspace >= 0 && info->colorspace < FF_ARRAY_ELEMS(ff_avs3_color_matrix_tab))
            avctx->colorspace = ff_avs3_color_matrix_tab[info->colorspace];
    }
    h->got_seqhdr = 1;
    return 0;
}

static int libhpm_decode_frame(AVCodecContext *avctx, AVFrame *frame,
                              int *got_frame, const AVPacket *packet)
{
    uavs3d_context *h = avctx->priv_data;
    WebHpmFrame picture;
    int consumed, ret;
    *got_frame = 0;
    ret = webhtv_hpm_decode(h->hpm, packet->data, packet->size,
                            packet->pts, packet->dts, &consumed, &picture);
    if (ret < 0)
        return libhpm_error(avctx, ret);
    int has_picture = ret;
    if ((ret = libhpm_update_info(avctx)) < 0)
        return ret;
    if (!has_picture)
        return consumed;
    if ((ret = ff_get_buffer(avctx, frame, 0)) < 0)
        return ret;
    for (int plane = 0; plane < 3; plane++) {
        int width = (picture.info.width + (plane != 0)) >> (plane != 0);
        int height = (picture.info.height + (plane != 0)) >> (plane != 0);
        if (picture.stride[plane] < width * 2 || frame->linesize[plane] < width * 2) {
            av_frame_unref(frame);
            return AVERROR_INVALIDDATA;
        }
        for (int y = 0; y < height; y++)
            memcpy(frame->data[plane] + y * frame->linesize[plane],
                   picture.data[plane] + y * picture.stride[plane], width * 2);
    }
    frame->pts = picture.pts;
    frame->pkt_dts = picture.dts;
    frame->pict_type = picture.pict_type == 1 ? AV_PICTURE_TYPE_I :
                       picture.pict_type == 2 ? AV_PICTURE_TYPE_P : AV_PICTURE_TYPE_B;
    if (frame->pict_type == AV_PICTURE_TYPE_I)
        frame->flags |= AV_FRAME_FLAG_KEY;
    *got_frame = 1;
    return consumed;
}
#endif
