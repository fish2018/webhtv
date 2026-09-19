/* WebHTV AVS3 High 10-bit adapter. Upstream HPM retains its own license. */
#ifndef WEBHTV_HPM_H
#define WEBHTV_HPM_H

#include <stddef.h>
#include <stdint.h>

typedef struct WebHpm WebHpm;

typedef struct WebHpmInfo {
    int width, height, bit_depth, profile, level;
    int frame_rate_code, reorder_delay, aspect_ratio;
    int color_present, color_range, color_primaries, color_trc, colorspace;
} WebHpmInfo;

typedef struct WebHpmFrame {
    const uint8_t *data[3];
    int stride[3];
    int64_t pts, dts;
    int pict_type, poc;
    WebHpmInfo info;
} WebHpmFrame;

enum { WEBHPM_INVALID = -1, WEBHPM_NOMEM = -2, WEBHPM_UNSUPPORTED = -3 };

WebHpm *webhtv_hpm_create(void);
void webhtv_hpm_close(WebHpm *decoder);
/* Discards references and all pending pictures. The next input needs a SQH. */
void webhtv_hpm_flush(WebHpm *decoder);
/* Returns 1 for a picture, 0 for more input/EOS, or a negative error.
 * consumed may be zero while draining delayed pictures. size == 0 drains EOS.
 * Picture planes remain valid until the next call on this decoder. */
int webhtv_hpm_decode(WebHpm *decoder, const uint8_t *data, int size,
                      int64_t pts, int64_t dts, int *consumed, WebHpmFrame *frame);
const WebHpmInfo *webhtv_hpm_info(const WebHpm *decoder);
const char *webhtv_hpm_error(const WebHpm *decoder);
const char *webhtv_hpm_version(void);
size_t webhtv_hpm_memory(const WebHpm *decoder);

#endif
