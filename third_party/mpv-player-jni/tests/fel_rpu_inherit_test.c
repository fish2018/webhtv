/* Compile the actual inheritance helper with real AVBuffer ownership. */
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <libavutil/buffer.h>

struct hdr {
    float min_luma, max_luma, max_pq_y, avg_pq_y;
};
struct mp_image {
    AVBufferRef *dovi;
    struct {
        bool no_dovi;
        struct { int sys; void *dovi; int levels, bits; } repr;
        struct { int primaries, transfer; struct hdr hdr; } color;
    } params;
};
static void inherit_dovi_from_el(struct mp_image *bl, struct mp_image *el);

int main(void)
{
    struct mp_image el = {0}, bl = {0};
    el.dovi = av_buffer_alloc(256);
    assert(el.dovi);
    memset(el.dovi->data, 0xa5, el.dovi->size);
    el.params.repr.sys = 7;
    el.params.color.primaries = 9;
    el.params.color.transfer = 16;
    el.params.color.hdr = (struct hdr){0.005f, 1000.0f, 0.72f, 0.34f};
    bl.params.repr.levels = 1;
    bl.params.repr.bits = 10;
    inherit_dovi_from_el(&bl, &el);
    assert(bl.dovi && av_buffer_get_ref_count(bl.dovi) == 2);
    assert(bl.params.repr.dovi == el.dovi->data);
    assert(bl.params.repr.sys == 7);
    assert(bl.params.repr.levels == 1 && bl.params.repr.bits == 10);
    assert(!memcmp(&bl.params.color, &el.params.color, sizeof(el.params.color)));
    av_buffer_unref(&el.dovi);
    assert(av_buffer_get_ref_count(bl.dovi) == 1 && bl.dovi->data[255] == 0xa5);

    el.dovi = av_buffer_alloc(256);
    assert(el.dovi);
    uint8_t *original = bl.dovi->data;
    inherit_dovi_from_el(&bl, &el);
    assert(bl.dovi->data == original); // existing BL metadata has priority
    av_buffer_unref(&bl.dovi);
    bl.params.no_dovi = true;
    inherit_dovi_from_el(&bl, &el);
    assert(!bl.dovi); // preserve explicit metadata suppression
    av_buffer_unref(&el.dovi);
    bl.params.no_dovi = false;
    inherit_dovi_from_el(&bl, &el);
    assert(!bl.dovi); // missing EL metadata cannot fabricate FEL
    puts("PASS: actual EL-to-BL RPU inheritance, color fields, pixel range/depth and AVBuffer lifetime");
    return 0;
}
