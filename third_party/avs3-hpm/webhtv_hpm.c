/*
 * HPM 15.0 player boundary. Only this file exposes a stable decoder interface.
 * The pinned reference implementation and its notice are in vendor/.
 */
#include "webhtv_hpm.h"
#include "hpm_guard.h"
#include "dec_def.h"
#include "com_tbl.h"
#include "com_util.h"
#include <pthread.h>
#include <setjmp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define HPM_MEMORY_LIMIT ((size_t)1024 * 1024 * 1024)
#define HPM_PACKET_LIMIT (64 * 1024 * 1024)
#define HPM_ALLOC_HEADER 64

typedef struct HpmAllocation {
    struct HpmAllocation *prev, *next;
    size_t size;
    WebHpm *owner;
} HpmAllocation;
_Static_assert(sizeof(HpmAllocation) <= HPM_ALLOC_HEADER, "allocation header");

/* HPM's global workspace is serialized. Dynamic table pointers and the DOI
 * history are swapped on every entry, including create/flush/close. Scratch
 * coefficient tables are used entirely inside one entry; fixed lookup tables
 * are initialized deterministically while holding the same lock. */
typedef struct HpmGlobals {
    __typeof__(com_scan_tbl) scan;
#if USE_SP
    __typeof__(com_tbl_raster2trav) raster;
    __typeof__(com_tbl_trav2raster) traversal;
#endif
    int doi_prev, doi_cycle;
    u8 compatible_back;
} HpmGlobals;

struct WebHpm {
    DEC_CTX *ctx;
    LibVCData libvc;
    HpmGlobals globals;
    HpmAllocation *allocations;
    size_t bytes;
    uint8_t *rbsp;
    size_t rbsp_capacity;
    COM_IMGB *output;
    WebHpmInfo info;
    int64_t picture_pts, picture_dts;
    int picture_pending, draining, failed;
    char error[192];
    jmp_buf escape;
};

/* These two variables belonged to app_decoder.c, not DecoderLib. */
int g_CountDOICyCleTime;
int g_DOIPrev;
static pthread_mutex_t hpm_mutex = PTHREAD_MUTEX_INITIALIZER;
static WebHpm *active;

static void activate(WebHpm *h)
{
    active = h;
    memcpy(com_scan_tbl, h->globals.scan, sizeof(com_scan_tbl));
#if USE_SP
    memcpy(com_tbl_raster2trav, h->globals.raster, sizeof(com_tbl_raster2trav));
    memcpy(com_tbl_trav2raster, h->globals.traversal, sizeof(com_tbl_trav2raster));
#endif
    g_DOIPrev = h->globals.doi_prev;
    g_CountDOICyCleTime = h->globals.doi_cycle;
    g_compatible_back = h->globals.compatible_back;
}

static void deactivate(WebHpm *h)
{
    memcpy(h->globals.scan, com_scan_tbl, sizeof(com_scan_tbl));
#if USE_SP
    memcpy(h->globals.raster, com_tbl_raster2trav, sizeof(com_tbl_raster2trav));
    memcpy(h->globals.traversal, com_tbl_trav2raster, sizeof(com_tbl_trav2raster));
#endif
    h->globals.doi_prev = g_DOIPrev;
    h->globals.doi_cycle = g_CountDOICyCleTime;
    h->globals.compatible_back = g_compatible_back;
    active = NULL;
}

void webhtv_hpm_fail(int code, const char *reason, int line)
{
    /* Every upstream entry is protected by this error boundary. No signal
     * handlers, aborts or C++ exception unwinding are used in the App. */
    active->failed = code < 0 ? code : WEBHPM_INVALID;
    snprintf(active->error, sizeof(active->error), "%s (line %d)", reason, line);
    longjmp(active->escape, 1);
}

/* Private bounds instrumentation for reference-code fixed arrays. The handler
 * is hidden in each separate codec library and needs no sanitizer runtime. */
__attribute__((visibility("hidden")))
void __ubsan_handle_out_of_bounds(void *data, uintptr_t index)
{
    const struct { const char *file; uint32_t line, column; } *location = data;
    char reason[144];
    const char *file = strrchr(location->file, '/');
    snprintf(reason, sizeof(reason), "array index %llu out of bounds in %s",
             (unsigned long long)index, file ? file + 1 : location->file);
    webhtv_hpm_fail(WEBHPM_INVALID, reason, location->line);
}

void *webhtv_hpm_aligned_alloc(size_t alignment, size_t size)
{
    HpmAllocation *node = NULL;
    HPM_REQUIRE(active && alignment <= HPM_ALLOC_HEADER);
    if (size > HPM_MEMORY_LIMIT || active->bytes > HPM_MEMORY_LIMIT - size)
        webhtv_hpm_fail(WEBHPM_NOMEM, "decoder memory limit exceeded", 0);
    if (posix_memalign((void **)&node, HPM_ALLOC_HEADER,
                       HPM_ALLOC_HEADER + (size ? size : 1)))
        webhtv_hpm_fail(WEBHPM_NOMEM, "decoder allocation failed", 0);
    node->prev = NULL;
    node->next = active->allocations;
    node->size = size;
    node->owner = active;
    if (node->next)
        node->next->prev = node;
    active->allocations = node;
    active->bytes += size;
    return (uint8_t *)node + HPM_ALLOC_HEADER;
}

void *webhtv_hpm_alloc(size_t size)
{
    return webhtv_hpm_aligned_alloc(HPM_ALLOC_HEADER, size);
}

void *webhtv_hpm_calloc(size_t count, size_t size)
{
    if (size && count > HPM_MEMORY_LIMIT / size)
        webhtv_hpm_fail(WEBHPM_NOMEM, "decoder allocation overflow", 0);
    size *= count;
    void *ptr = webhtv_hpm_alloc(size);
    memset(ptr, 0, size);
    return ptr;
}

void webhtv_hpm_free(void *ptr)
{
    if (!ptr)
        return;
    HpmAllocation *node = (HpmAllocation *)((uint8_t *)ptr - HPM_ALLOC_HEADER);
    HPM_REQUIRE(node->owner == active);
    if (node->prev)
        node->prev->next = node->next;
    else
        active->allocations = node->next;
    if (node->next)
        node->next->prev = node->prev;
    active->bytes -= node->size;
    free(node);
}

static void clear_decoder(WebHpm *h)
{
    /* All HPM resources are C allocations. Bulk ownership also covers partial
     * initialization and reference-code error paths which omit local cleanup.
     * Never traverse a damaged decoder or release another instance's tables. */
    HpmAllocation *node = h->allocations;
    while (node) {
        HpmAllocation *next = node->next;
        free(node);
        node = next;
    }
    memset(h, 0, sizeof(*h));
}

WebHpm *webhtv_hpm_create(void)
{
    return calloc(1, sizeof(WebHpm));
}

void webhtv_hpm_flush(WebHpm *h)
{
    if (!h)
        return;
    pthread_mutex_lock(&hpm_mutex);
    clear_decoder(h);
    pthread_mutex_unlock(&hpm_mutex);
}

void webhtv_hpm_close(WebHpm *h)
{
    webhtv_hpm_flush(h);
    free(h);
}

const WebHpmInfo *webhtv_hpm_info(const WebHpm *h) { return &h->info; }
const char *webhtv_hpm_error(const WebHpm *h) { return h->error; }
size_t webhtv_hpm_memory(const WebHpm *h) { return h->bytes; }
const char *webhtv_hpm_version(void)
{
    return "WebHTV HPM 15.0 / 0c7ac42edfac6d18b92b58a5ef43bca58526ca7a / AVS3 0x32";
}

void webhtv_hpm_set_color(int range, int primaries, int trc, int matrix)
{
    active->info.color_present = 1;
    active->info.color_range = range;
    active->info.color_primaries = primaries;
    active->info.color_trc = trc;
    active->info.colorspace = matrix;
}

static void ensure_decoder(WebHpm *h)
{
    if (h->ctx)
        return;
    DEC_CDSC cdsc = {0};
    h->ctx = (DEC_CTX *)dec_create(&cdsc, NULL);
    HPM_REQUIRE(h->ctx);
    init_libvcdata(&h->libvc);
    h->ctx->dpm.libvc_data = &h->libvc;
}

static void update_info(WebHpm *h)
{
    const COM_SQH *sqh = &h->ctx->info.sqh;
    h->info.width = sqh->horizontal_size;
    h->info.height = sqh->vertical_size;
    h->info.bit_depth = h->ctx->info.bit_depth_internal;
    h->info.profile = sqh->profile_id;
    h->info.level = sqh->level_id;
    h->info.frame_rate_code = sqh->frame_rate_code;
    h->info.reorder_delay = sqh->output_reorder_delay;
    h->info.aspect_ratio = sqh->aspect_ratio;
}

static int pull_picture(WebHpm *h, WebHpmFrame *frame, int drain)
{
    COM_IMGB *image = NULL;
    if (!h->ctx || !h->info.width)
        return 0;
    int result = dec_pull_frm(h->ctx, &image, drain);
    if (!image) {
        HPM_REQUIRE(result == COM_OK_FRM_DELAYED || result == COM_ERR_UNEXPECTED);
        return 0;
    }
    HPM_REQUIRE(result >= 0 && image->np == 3);
    h->output = image;
    for (int i = 0; i < 3; i++) {
        frame->data[i] = image->addr_plane[i];
        frame->stride[i] = image->stride[i];
    }
    frame->pts = image->ts[0];
    frame->dts = image->ts[1];
    frame->pict_type = (int)image->ts[2];
    frame->poc = (int)image->ts[3];
    frame->info = h->info;
    return 1;
}

static int chunk_boundary(const uint8_t *p)
{
    return p[0] == 0 && p[1] == 0 && p[2] == 1 &&
           (p[3] == 0xb0 || p[3] == 0xb1 || p[3] == 0xb3 ||
            p[3] == 0xb6 || p[3] == 0x00);
}

static int prepare_rbsp(WebHpm *h, const uint8_t *src, int size)
{
    if (h->rbsp_capacity < (size_t)size + 64) {
        webhtv_hpm_free(h->rbsp);
        h->rbsp_capacity = (size_t)size + 64;
        h->rbsp = webhtv_hpm_alloc(h->rbsp_capacity);
    }
    /* AVS3 removes two stuffing bits following 00 00 02, not an H.264 03 byte.
     * Equivalent to HPM's initParsingConvertPayloadToRBSP, with exact length
     * and initialized, bounded lookahead instead of the CLI's stale tail. */
    unsigned zeros = 0, shift = 0;
    int written = 0;
    for (int i = 0; i < size; i++) {
        unsigned current = src[i];
        unsigned value;
        if (zeros >= 2 && current == 2) {
            value = (current >> 2) << (shift + 2);
            shift += 2;
            zeros = 0;
            if (shift == 8) {
                shift = 0;
                continue;
            }
        } else if (zeros >= 2 && current == 1) {
            shift = 0;
            value = current;
        } else {
            value = current << shift;
        }
        if (i + 1 < size)
            value |= src[i + 1] >> (8 - shift);
        h->rbsp[written++] = value;
        zeros = current == 0 ? zeros + 1 : 0;
    }
    memset(h->rbsp + written, 0, 64);
    h->rbsp[written + 2] = 1;
    return written;
}

static int decode_locked(WebHpm *h, const uint8_t *data, int size,
                         int64_t pts, int64_t dts, int *consumed, WebHpmFrame *frame)
{
    if (h->output) {
        h->output->release(h->output);
        h->output = NULL;
    }
    if (!size)
        return pull_picture(h, frame, 1);
    if (h->draining) {
        if (pull_picture(h, frame, 1))
            return 1;
        h->draining = 0;
    }
    ensure_decoder(h);
    while (*consumed < size) {
        const uint8_t *chunk = data + *consumed;
        int length = size - *consumed;
        HPM_REQUIRE(length >= 4 && chunk_boundary(chunk));
        for (int i = 4; i + 4 <= length; i++) {
            if (chunk_boundary(chunk + i)) {
                length = i;
                break;
            }
        }
        int type = chunk[3];
        if (type == 0xb0) {
            HPM_REQUIRE(length >= 6);
            if (chunk[4] != 0x32)
                webhtv_hpm_fail(WEBHPM_UNSUPPORTED, "HPM backend requires AVS3 0x32", 0);
            h->picture_pending = 0;
        } else if (type == 0xb3 || type == 0xb6) {
            HPM_REQUIRE(h->ctx->init_flag);
            h->picture_pts = pts;
            h->picture_dts = dts;
            h->picture_pending = 1;
        } else if (type == 0x00) {
            HPM_REQUIRE(h->picture_pending && h->ctx->init_flag);
        }
        COM_BITB bitb = {0};
        DEC_STAT stat = {0};
        bitb.ssize = prepare_rbsp(h, chunk, length);
        bitb.bsize = (int)h->rbsp_capacity;
        bitb.addr = h->rbsp;
        int result = dec_cnk(h->ctx, &bitb, &stat);
        HPM_REQUIRE(result >= 0);
        *consumed += length;
        if (type == 0xb0)
            update_info(h);
        if (type == 0xb1) {
            h->draining = 1;
            if (pull_picture(h, frame, 1))
                return 1;
            h->draining = 0;
        } else if (type == 0x00) {
            HPM_REQUIRE(h->ctx->pic && h->ctx->pic->imgb && stat.fnum >= 0);
            COM_IMGB *image = h->ctx->pic->imgb;
            image->ts[0] = h->picture_pts;
            image->ts[1] = h->picture_dts;
            image->ts[2] = h->ctx->info.pic_header.slice_type;
            image->ts[3] = h->ctx->ptr;
            h->picture_pending = 0;
            if (pull_picture(h, frame, 0))
                return 1;
        }
    }
    return 0;
}

int webhtv_hpm_decode(WebHpm *h, const uint8_t *data, int size,
                      int64_t pts, int64_t dts, int *consumed, WebHpmFrame *frame)
{
    if (!h || !consumed || !frame || size < 0 || size > HPM_PACKET_LIMIT || (size && !data))
        return WEBHPM_INVALID;
    *consumed = 0;
    memset(frame, 0, sizeof(*frame));
    pthread_mutex_lock(&hpm_mutex);
    if (h->failed) {
        int result = h->failed;
        pthread_mutex_unlock(&hpm_mutex);
        return result;
    }
    activate(h);
    int result;
    if (setjmp(h->escape))
        result = h->failed;
    else
        result = decode_locked(h, data, size, pts, dts, consumed, frame);
    deactivate(h);
    pthread_mutex_unlock(&hpm_mutex);
    return result;
}
