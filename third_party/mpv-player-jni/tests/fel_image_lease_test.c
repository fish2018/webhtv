// Compile mp_image's actual ref/destructor/dummy/unref-data bodies with real
// AVBufferRef ownership. Fake only the small talloc allocation interface.
#include <libavutil/buffer.h>
#include <assert.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MP_MAX_PLANES 4
#define MP_HANDLE_OOM(p) assert(p)
struct mp_ff_side_data { AVBufferRef *buf; };
typedef struct mp_image {
    uint8_t *planes[MP_MAX_PLANES];
    int stride[MP_MAX_PLANES];
    AVBufferRef *bufs[MP_MAX_PLANES], *hwctx, *icc_profile, *a53_cc, *dovi, *film_grain;
    struct mp_ff_side_data *ff_side_data;
    int num_ff_side_data;
    struct mp_image *enhancement_layer;
    AVBufferRef *android_fel_staging;
    uint64_t android_fel_prepared;
} mp_image_t;
struct allocation { void (*destroy)(void *); max_align_t alignment; };
static int allocations;
static void *allocate(size_t size)
{
    struct allocation *a = calloc(1, sizeof(*a) + size);
    assert(a);
    allocations++;
    return a + 1;
}
static void talloc_set_destructor(void *p, void (*destroy)(void *))
{ ((struct allocation *)p - 1)->destroy = destroy; }
static void talloc_free(void *p)
{
    if (!p)
        return;
    struct allocation *a = (struct allocation *)p - 1;
    if (a->destroy)
        a->destroy(p);
    free(a);
    allocations--;
}
#define talloc_ptrtype(ctx, ptr) allocate(sizeof(*(ptr)))
static void *talloc_memdup(void *ctx, const void *src, size_t size)
{
    (void)ctx;
    if (!size)
        return NULL;
    void *p = allocate(size);
    memcpy(p, src, size);
    return p;
}
static void mp_image_destructor(void *p);
static void mp_image_unrefp(struct mp_image **p) { talloc_free(*p); *p = NULL; }
struct mp_image *mp_image_new_ref(struct mp_image *img);
struct mp_image *mp_image_new_dummy_ref(struct mp_image *img);
void mp_image_unref_data(struct mp_image *img);
static struct mp_image *mp_image_new_copy(struct mp_image *img)
{ (void)img; abort(); } // all tested sources own a real frame buffer

static void free_lease(void *opaque, uint8_t *data)
{
    (*(int *)opaque)++;
    free(data);
}
static struct mp_image *new_image(void)
{
    struct mp_image *img = allocate(sizeof(*img));
    talloc_set_destructor(img, mp_image_destructor);
    img->bufs[0] = av_buffer_alloc(16);
    assert(img->bufs[0]);
    img->planes[0] = img->bufs[0]->data;
    return img;
}
int main(void)
{
    int destroyed = 0;
    struct mp_image *src = new_image();
    src->enhancement_layer = new_image();
    src->android_fel_staging = av_buffer_create(malloc(1), 1, free_lease, &destroyed, 0);
    assert(src->android_fel_staging);
    src->android_fel_prepared = 123;
    struct mp_image *ref = mp_image_new_ref(src);
    assert(av_buffer_get_ref_count(src->android_fel_staging) == 2);
    assert(av_buffer_get_ref_count(src->bufs[0]) == 2);
    assert(ref->android_fel_prepared == 123);
    assert(ref->enhancement_layer != src->enhancement_layer);
    struct mp_image *dummy = mp_image_new_dummy_ref(src);
    assert(!dummy->android_fel_staging && !dummy->android_fel_prepared);
    talloc_free(dummy);
    assert(!destroyed && av_buffer_get_ref_count(src->android_fel_staging) == 2);
    mp_image_unref_data(ref);
    assert(!ref->android_fel_staging && !ref->android_fel_prepared);
    assert(!ref->bufs[0] && !ref->planes[0]);
    assert(av_buffer_get_ref_count(src->android_fel_staging) == 1);
    talloc_free(ref);
    assert(!destroyed);
    talloc_free(src);
    assert(destroyed == 1 && !allocations);
    puts("PASS: actual mp_image FEL lease ownership: clone, EL refs, dummy, "
         "unref-data, generation reset and final destructor (ASan/UBSan)");
    return 0;
}
