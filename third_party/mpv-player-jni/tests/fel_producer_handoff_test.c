// Compile actual producer handoff + GPU completion/cache-hit function bodies.
// The device has one available output; this is a contract model, not a claim
// about the tested TV's measured hardware DPB capacity or GPU performance.
#include "filters/f_android_fel.h"
#include "filters/f_android_fel_perf.h"
#include <libavutil/buffer.h>
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#define MP_TIME_MS_TO_NS(ms) ((ms) * 1000000LL)
#define MPMAX(a, b) ((a) > (b) ? (a) : (b))
#define MP_INFO(...) ((void)0)
#define MP_ERR(...) ((void)0)
#define mp_warn(...) ((void)0)
#define mp_info(...) ((void)0)
#define talloc_array(ctx, type, count) ((type *)calloc(count, sizeof(type)))
#define talloc_free free
#define POOL_LOG_INTERVAL 120
enum { MP_FRAME_VIDEO = 1, MP_FRAME_EOF = 2 };
enum { IMGFMT_MEDIACODEC = 1, IMGFMT_YUV420P10 = 2 };
enum { VO_ERROR = -1, VO_NOTIMPL = -3, VO_FALSE = 0, VO_TRUE = 1 };
enum { VK_SUCCESS = 0, VK_NOT_READY = 1, VK_TIMEOUT = 2, VK_TRUE = 1 };
enum { VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO = 8, VK_QUERY_TYPE_TIMESTAMP = 2,
       VK_QUERY_RESULT_64_BIT = 1 };
enum { VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO = 3,
       VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
       VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR, VK_STRUCTURE_TYPE_SUBMIT_INFO,
       VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT = 1,
       VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT = 2, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT = 4 };
#define VK_NULL_HANDLE 0
typedef int VkResult;
typedef int VkCommandBuffer;
typedef unsigned VkQueryPool;
typedef struct { unsigned timestampValidBits; } VkQueueFamilyProperties;
typedef struct { struct { float timestampPeriod; } limits; } VkPhysicalDeviceProperties;
typedef struct { int sType, queryType; unsigned queryCount; } VkQueryPoolCreateInfo;
typedef unsigned VkPipelineStageFlags;
struct fake_semaphore { bool signal; };
typedef struct fake_semaphore *VkSemaphore;
typedef struct { int sType; unsigned handleTypes; } VkExportSemaphoreCreateInfo;
typedef struct { int sType; const void *pNext; } VkSemaphoreCreateInfo;
typedef struct { int sType; VkSemaphore semaphore; int handleType; } VkSemaphoreGetFdInfoKHR;
typedef struct {
    int sType;
    unsigned waitSemaphoreCount, commandBufferCount, signalSemaphoreCount;
    const VkSemaphore *pWaitSemaphores, *pSignalSemaphores;
    const VkPipelineStageFlags *pWaitDstStageMask;
    const int *pCommandBuffers;
} VkSubmitInfo;
struct fake_fence { int64_t ready_at; bool reset; };
typedef struct fake_fence *VkFence;
struct fake_image { bool returned; };
struct mp_image {
    int imgfmt;
    double pts;
    void *planes[4];
    AVBufferRef *android_fel_staging;
};
struct mp_frame { int type; struct mp_image *data; };
struct vk_input { int users; bool removed; };
struct vk_output {
    uint64_t fel_serial;
    int fel_input_slot;
    bool fel_query_recorded;
    VkFence fence;
    VkSemaphore available, acquire, ready, source_release;
    VkCommandBuffer command, active_command;
    struct fake_image *source_image;
    struct mp_image *source_frame, **source_aliases;
    int num_source_aliases;
    struct vk_input *input;
    bool pending;
    void *ratex;
};
struct mapper { void *tex[4]; };
struct fake_vk {
    void (*lock_queue)(struct fake_vk *, unsigned, unsigned);
    void (*unlock_queue)(struct fake_vk *, unsigned, unsigned);
    int phys_device;
    struct { unsigned index; int count; } queue_compute, queue_graphics;
};
struct aimagereader_vk_stable {
    struct { unsigned count; } fel_map_window;
    bool android_fel, release_sync_fd;
    int device, output_count;
    int queue;
    unsigned queue_family;
    struct fake_vk *vk;
    VkResult (*GetSemaphoreFdKHR)(int, const VkSemaphoreGetFdInfoKHR *, int *);
    uint64_t fence_timeouts, submitted_outputs, completed_outputs, reclaimed_outputs;
    uint64_t fel_async_returns, fel_release_failures;
    struct { void (*AImage_delete)(struct fake_image *);
             void (*AImage_deleteAsync)(struct fake_image *, int); } api;
    struct mapper *mapper;
    struct vk_output outputs[1];
    bool fel_profile, fel_profile_warm, fel_query_failed;
    int output_capacity;
    int64_t fel_map_started, fel_map_cpu_started, fel_map_checkpoint;
    struct mp_fel_perf_stat fel_map_warm, fel_map_cold, fel_map_cpu;
    struct mp_fel_perf_stat fel_map_stages[MP_FEL_PERF_COUNT], fel_gpu_copy;
    VkQueryPool fel_query_pool;
    unsigned fel_timestamp_bits;
    double fel_timestamp_period;
    uint64_t fel_query_unavailable;
};
struct vo { struct aimagereader_vk_stable *gpu; };
struct priv {
    bool android_fel, software_el;
    void *queue;
    struct { struct vo *dr_vo; } stream_info;
    unsigned long long fel_staged, fel_stage_retries;
    int64_t fel_stage_ns, fel_stage_max_ns, fel_stage_started;
    double fel_stage_log_at;
};

static int stage_fel_before_publish(struct priv *, struct mp_frame);
static bool finish_output(struct aimagereader_vk_stable *, struct vk_output *, uint64_t);
static VkSemaphore create_fel_release_semaphore(struct aimagereader_vk_stable *);
static bool release_fel_source_async(struct aimagereader_vk_stable *, struct vk_output *);
static bool submit_conversion(struct aimagereader_vk_stable *, struct vk_output *, bool, bool);
static void fel_perf_checkpoint(struct aimagereader_vk_stable *, enum mp_fel_perf_stage);
static void fel_perf_finish_map(struct aimagereader_vk_stable *);
// Latency distribution arithmetic is exercised by fel_vk_cache_test. This
// fixture observes only whether producer/copy completion records a sample.
static void fel_latency_add(void *window, uint64_t ns)
{ (void)ns; (*(unsigned *)window)++; }
static void init_fel_gpu_timer(struct aimagereader_vk_stable *);
static void collect_fel_gpu_time(struct aimagereader_vk_stable *, struct vk_output *);
bool aimagereader_vk_stable_reuse(struct aimagereader_vk_stable *, struct mp_image *);
static int64_t now_ns, gpu_delay_ns;
static int64_t prepare_started_ns;
static int prepare_calls, held_outputs, returned_outputs, injected_result;
static struct fake_image source_image;
static struct fake_fence fence;
static struct vk_input input;
static struct mapper mapper;
static struct aimagereader_vk_stable gpu;
static struct vo vo;
static struct priv producer;
static struct fake_semaphore ready_sem, source_sem, available_sem, acquire_sem;
static int create_result, export_result, exported_fd, fd_transfers, queue_locked;
static unsigned submit_waits, submit_signals;
static int query_calls, query_result, query_create_result, query_create_calls;
static unsigned query_bits = 36;
static float query_period = 1.0f;
static uint64_t query_values[2];

static void queue_lock(struct fake_vk *vk, unsigned family, unsigned index)
{ (void)vk; (void)family; assert(index == 0 && !queue_locked); queue_locked = 1; }
static void queue_unlock(struct fake_vk *vk, unsigned family, unsigned index)
{ (void)vk; (void)family; assert(index == 0 && queue_locked); queue_locked = 0; }
static struct fake_vk vk = {.lock_queue = queue_lock, .unlock_queue = queue_unlock};
static void vkGetPhysicalDeviceQueueFamilyProperties(int device, unsigned *count,
                                                     VkQueueFamilyProperties *families)
{
    (void)device;
    if (families) { assert(*count == 1); families[0].timestampValidBits = query_bits; }
    *count = 1;
}
static void vkGetPhysicalDeviceProperties(int device, VkPhysicalDeviceProperties *props)
{ (void)device; props->limits.timestampPeriod = query_period; }
static VkResult vkCreateQueryPool(int device, const VkQueryPoolCreateInfo *info,
                                  void *allocator, VkQueryPool *pool)
{
    (void)device;
    assert(!allocator && info->queryCount == 2 && info->queryType == VK_QUERY_TYPE_TIMESTAMP);
    query_create_calls++;
    if (query_create_result == VK_SUCCESS) *pool = 7;
    return query_create_result;
}
static VkResult vkGetQueryPoolResults(int device, VkQueryPool pool, unsigned first,
    unsigned count, size_t size, void *data, size_t stride, unsigned flags)
{
    (void)device;
    assert(pool == 7 && first == 0 && count == 2 && size == sizeof(query_values));
    assert(stride == sizeof(uint64_t) && flags == VK_QUERY_RESULT_64_BIT); // Never WAIT.
    assert(!fence.reset && now_ns >= fence.ready_at); // Read only this submission's results.
    query_calls++;
    if (query_result == VK_SUCCESS) {
        uint64_t *values = data;
        values[0] = query_values[0]; values[1] = query_values[1];
    }
    return query_result;
}
static VkResult vkCreateSemaphore(int device, const VkSemaphoreCreateInfo *info,
                                  const void *allocator, VkSemaphore *sem)
{
    (void)device; assert(!allocator && info->sType == VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
    const VkExportSemaphoreCreateInfo *export = info->pNext;
    assert(export && export->handleTypes == VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT);
    if (create_result != VK_SUCCESS) return create_result;
    *sem = &source_sem;
    return VK_SUCCESS;
}
static VkResult vkQueueSubmit(int queue, unsigned count, const VkSubmitInfo *info, VkFence f)
{
    (void)queue; assert(queue_locked && count == 1 && f == &fence);
    assert(info->commandBufferCount == 1 && info->signalSemaphoreCount >= 1);
    assert(*info->pCommandBuffers == (gpu.outputs[0].active_command
        ? gpu.outputs[0].active_command : gpu.outputs[0].command));
    assert(info->pSignalSemaphores[0] == &ready_sem);
    submit_signals = info->signalSemaphoreCount;
    submit_waits = info->waitSemaphoreCount;
    if (submit_signals == 2) {
        assert(info->pSignalSemaphores[1] == &source_sem && !source_sem.signal);
        source_sem.signal = true; // Pending signal; not yet GPU-completed.
    }
    return VK_SUCCESS;
}
static VkResult export_fd(int device, const VkSemaphoreGetFdInfoKHR *info, int *fd)
{
    (void)device;
    assert(info->semaphore == &source_sem && source_sem.signal);
    assert(info->handleType == VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT);
    if (export_result != VK_SUCCESS) return export_result;
    source_sem.signal = false; // SYNC_FD copy transference consumes this signal.
    *fd = exported_fd;
    return VK_SUCCESS;
}

static int64_t mp_time_ns(void) { return now_ns; }
static double mp_time_sec(void) { return now_ns / 1e9; }
static bool vk_success(struct aimagereader_vk_stable *p, int result, const char *what)
{ (void)p; (void)what; return result == VK_SUCCESS; }
// Frame trace formatting/rate limits are exercised with the real functions in
// fel_vk_cache_test. Here logging must not change the source/fence contract.
static void trace_fel_frame_order(struct aimagereader_vk_stable *p, char action,
                                  int output, int input, uint64_t serial, double requested, double mapped)
{ (void)p; (void)action; (void)output; (void)input; (void)serial; (void)requested; (void)mapped; }
static VkResult vkGetFenceStatus(int device, VkFence f)
{ (void)device; return !f->reset && now_ns >= f->ready_at ? VK_SUCCESS : VK_NOT_READY; }
static VkResult vkWaitForFences(int device, int count, VkFence *f, int all, uint64_t timeout)
{
    assert(count == 1 && all && timeout);
    if (vkGetFenceStatus(device, *f) == VK_SUCCESS) return VK_SUCCESS;
    now_ns += timeout;
    return vkGetFenceStatus(device, *f) == VK_SUCCESS ? VK_SUCCESS : VK_TIMEOUT;
}
static VkResult vkResetFences(int device, int count, VkFence *f)
{ (void)device; assert(count == 1); (*f)->reset = true; return VK_SUCCESS; }
static void destroy_input(struct aimagereader_vk_stable *p, struct vk_input *i)
{ (void)p; assert(i->users == 0); }
static void delete_image(struct fake_image *img)
{
    assert(img == &source_image && !img->returned);
    assert(now_ns >= fence.ready_at); // Never return source before GPU completes.
    struct mp_image *src = gpu.outputs[0].source_frame;
    assert(!mp_android_fel_staging_ready(src->android_fel_staging->data));
    img->returned = true;
    held_outputs--;
    returned_outputs++;
}
static void delete_image_async(struct fake_image *img, int fd)
{
    assert(img == &source_image && !img->returned && fd == exported_fd);
    assert(!mp_android_fel_staging_ready(gpu.outputs[0].source_frame->android_fel_staging->data));
    if (fd == -1) assert(now_ns >= fence.ready_at); // Already signaled, not export failure.
    // This transfers AImage/fd ownership; actual reuse remains fence-gated.
    img->returned = true;
    held_outputs--;
    returned_outputs++;
    fd_transfers++;
}
static void clear_cached_frame(void)
{
    struct vk_output *out = &gpu.outputs[0];
    if (out->source_frame) {
        av_buffer_unref(&out->source_frame->android_fel_staging);
        free(out->source_frame);
        out->source_frame = NULL;
    }
}
static int vo_prepare_fel_frame(struct vo *v, struct mp_image *image)
{
    assert(v == &vo);
    prepare_calls++;
    if (injected_result != VO_TRUE) return injected_result;
    if (!prepare_started_ns) prepare_started_ns = now_ns;
    if (now_ns - prepare_started_ns >= MP_ANDROID_FEL_FRAME_TIMEOUT_NS)
        return VO_ERROR;
    if (aimagereader_vk_stable_reuse(v->gpu, image))
        return mp_android_fel_staging_ready(image->android_fel_staging->data)
            ? VO_TRUE : VO_FALSE;
    clear_cached_frame();
    image->android_fel_staging = av_buffer_allocz(1);
    assert(image->android_fel_staging);
    struct mp_image *ref = malloc(sizeof(*ref));
    assert(ref);
    *ref = *image;
    ref->android_fel_staging = av_buffer_ref(image->android_fel_staging);
    assert(ref->android_fel_staging);
    fence = (struct fake_fence){.ready_at = now_ns + gpu_delay_ns};
    input = (struct vk_input){.users = 1};
    gpu.outputs[0] = (struct vk_output){.fence = &fence,
        .source_image = &source_image, .source_frame = ref, .input = &input,
        .pending = true, .ratex = &gpu, .ready = &ready_sem,
        .available = &available_sem, .acquire = &acquire_sem,
        .source_release = create_fel_release_semaphore(&gpu)};
    assert(submit_conversion(&gpu, &gpu.outputs[0], false, false));
    release_fel_source_async(&gpu, &gpu.outputs[0]);
    return VO_FALSE; // Submitted is not completed, even if the handle exists.
}
static void reset(void)
{
    clear_cached_frame();
    now_ns = 1000000000LL;
    prepare_started_ns = 0;
    prepare_calls = held_outputs = returned_outputs = 0;
    gpu_delay_ns = MP_TIME_MS_TO_NS(12);
    injected_result = VO_TRUE;
    create_result = export_result = VK_SUCCESS;
    query_calls = query_create_calls = 0;
    query_result = query_create_result = VK_SUCCESS;
    query_bits = 36;
    query_period = 1.0f;
    exported_fd = 37;
    fd_transfers = queue_locked = 0;
    ready_sem = source_sem = available_sem = acquire_sem = (struct fake_semaphore){0};
    gpu = (struct aimagereader_vk_stable){.android_fel = true, .output_count = 1,
        .api = {delete_image, delete_image_async}, .mapper = &mapper,
        .vk = &vk, .GetSemaphoreFdKHR = export_fd};
    gpu.output_capacity = 1;
    vo = (struct vo){.gpu = &gpu};
    producer = (struct priv){.android_fel = true, .queue = &producer,
        .stream_info.dr_vo = &vo};
}
static struct mp_image make_frame(int id)
{
    assert(held_outputs == 0); // Calling the codec here would stall otherwise.
    held_outputs++;
    prepare_started_ns = 0;
    source_image = (struct fake_image){0};
    return (struct mp_image){.imgfmt = IMGFMT_MEDIACODEC, .pts = id / 24.0,
        .planes[3] = (void *)(uintptr_t)(id + 1)};
}
static bool await_handoff(struct mp_image *image)
{
    int result;
    // Model separate dispatch ticks: each production call must return without
    // sleeping. VO phase/deadline behavior is tested with its real bodies in
    // fel_core_preload_test; these fakes only supply the GPU/codec contract.
    do {
        int64_t before = now_ns;
        result = stage_fel_before_publish(&producer,
                    (struct mp_frame){MP_FRAME_VIDEO, image});
        assert(now_ns == before);
        if (result == VO_FALSE) now_ns += MP_TIME_MS_TO_NS(2);
    } while (result == VO_FALSE);
    return result == VO_TRUE;
}
static void test_handoff(void)
{
    reset();
    // Old publish-then-decode order still owns this output at its next call.
    struct mp_image image = make_frame(0);
    assert(held_outputs == 1 && !image.android_fel_staging);
    // The actual new handoff closes that window without changing the codec.
    for (int id = 0; id < 120; id++) {
        if (id) image = make_frame(id);
        assert(await_handoff(&image));
        assert(held_outputs == 0 && source_image.returned && input.users == 0);
        assert(mp_android_fel_staging_ready(image.android_fel_staging->data));
        assert(!gpu.outputs[0].pending && gpu.completed_outputs == (unsigned)id + 1);
        assert(image.pts == id / 24.0); // Pairing timestamp is unchanged.
        av_buffer_unref(&image.android_fel_staging);
    }
    assert(producer.fel_staged == 120 && returned_outputs == 120);
    assert(producer.fel_stage_max_ns == gpu_delay_ns);
    clear_cached_frame();
}
static void test_timeout_and_isolation(void)
{
    reset();
    struct mp_image image = make_frame(0);
    gpu_delay_ns = MP_TIME_MS_TO_NS(2000);
    int64_t started = now_ns;
    assert(!await_handoff(&image));
    assert(now_ns - started == MP_TIME_MS_TO_NS(750));
    assert(held_outputs == 1 && !producer.fel_staged);
    assert(!mp_android_fel_staging_ready(image.android_fel_staging->data));
    now_ns = fence.ready_at; // Cleanup remains GPU-safe after a timed-out caller.
    assert(finish_output(&gpu, &gpu.outputs[0], 0));
    assert(held_outputs == 0 && returned_outputs == 1);
    av_buffer_unref(&image.android_fel_staging);
    clear_cached_frame();

    for (int kind = 0; kind < 7; kind++) {
        reset();
        image = (struct mp_image){.imgfmt = IMGFMT_MEDIACODEC};
        struct mp_frame frame = {MP_FRAME_VIDEO, &image};
        if (kind == 0) producer.android_fel = false;
        if (kind == 1) producer.software_el = true;
        if (kind == 2) producer.queue = NULL;
        if (kind == 3) frame.type = MP_FRAME_EOF;
        if (kind == 4) image.imgfmt = IMGFMT_YUV420P10;
        if (kind == 5) injected_result = VO_NOTIMPL;
        if (kind == 6) injected_result = VO_ERROR;
        assert(stage_fel_before_publish(&producer, frame) == (kind == 6 ? VO_ERROR : VO_TRUE));
        assert(!image.android_fel_staging && !producer.fel_staged);
        assert(prepare_calls == (kind >= 5));
    }
}
static void test_fenced_release(void)
{
    reset();
    gpu.release_sync_fd = true;
    for (int id = 0; id < 120; id++) {
        struct mp_image image = make_frame(id);
        int64_t started = now_ns;
        assert(await_handoff(&image));
        assert(now_ns - started == MP_TIME_MS_TO_NS(2)); // No per-frame GPU wait.
        assert(held_outputs == 0 && returned_outputs == id + 1 && submit_signals == 2);
        assert(mp_android_fel_staging_ready(image.android_fel_staging->data));
        assert(!mp_android_fel_staging_gpu_complete(image.android_fel_staging->data));
        assert(gpu.outputs[0].pending && input.users == 1 && now_ns < fence.ready_at);
        assert(!release_fel_source_async(&gpu, &gpu.outputs[0])); // No double fd transfer.
        // Kernel/consumer reuse must still wait for this fence. The GPU input
        // cannot be freed even though AImage ownership has already returned.
        assert(!finish_output(&gpu, &gpu.outputs[0], 0) && input.users == 1);
        now_ns = fence.ready_at;
        assert(finish_output(&gpu, &gpu.outputs[0], 0));
        assert(input.users == 0 && mp_android_fel_staging_gpu_complete(image.android_fel_staging->data));
        assert(gpu.completed_outputs == (unsigned)id + 1 && fd_transfers == id + 1);
        av_buffer_unref(&image.android_fel_staging);
    }
    assert(!source_sem.signal && gpu.fel_async_returns == 120);
    clear_cached_frame();

    // Unsupported/allocation/export failures retain CPU-fence protection.
    for (int kind = 0; kind < 3; kind++) {
        reset();
        gpu.release_sync_fd = kind != 0;
        if (kind == 1) create_result = -7;
        if (kind == 2) export_result = -7;
        struct mp_image image = make_frame(0);
        int64_t started = now_ns;
        assert(await_handoff(&image));
        assert(now_ns - started == gpu_delay_ns && fd_transfers == 0);
        assert(!gpu.release_sync_fd && gpu.fel_release_failures == (unsigned)(kind != 0));
        assert(mp_android_fel_staging_gpu_complete(image.android_fel_staging->data));
        av_buffer_unref(&image.android_fel_staging);
        // A failed export leaves a signal unconsumed. Disabling future export
        // must also stop future submissions from signaling that semaphore.
        gpu.outputs[0].active_command = 73; // Cached commands use the same per-frame semaphores.
        assert(submit_conversion(&gpu, &gpu.outputs[0], true, true));
        assert(submit_signals == 1 && submit_waits == 2 && !queue_locked);
        clear_cached_frame();
    }

    reset();
    gpu.release_sync_fd = true;
    exported_fd = -1;
    gpu_delay_ns = 0;
    struct mp_image image = make_frame(0);
    assert(await_handoff(&image));
    assert(fd_transfers == 1 && gpu.release_sync_fd && gpu.fel_async_returns == 1);
    assert(finish_output(&gpu, &gpu.outputs[0], 0));
    av_buffer_unref(&image.android_fel_staging);
    clear_cached_frame();

    reset();
    gpu.android_fel = false;
    gpu.release_sync_fd = true;
    assert(!create_fel_release_semaphore(&gpu));
    assert(!release_fel_source_async(&gpu, &gpu.outputs[0]));
    assert(!gpu.fel_async_returns && !fd_transfers);
}
static void test_performance_queries(void)
{
    uint64_t ns = 99;
    assert(mp_fel_gpu_duration(100, 120, 64, 2.5, &ns) && ns == 50);
    assert(mp_fel_gpu_duration(UINT64_MAX - 4, 5, 64, 1, &ns) && ns == 10);
    assert(mp_fel_gpu_duration((UINT64_C(1) << 36) - 4, 7, 36, 1, &ns) && ns == 11);
    assert(!mp_fel_gpu_duration(0, 1, 0, 1, &ns));
    assert(!mp_fel_gpu_duration(0, 1, 65, 1, &ns));
    assert(!mp_fel_gpu_duration(0, 1, 64, NAN, &ns));
    assert(!mp_fel_gpu_duration(0, 1, 64, 0, &ns));
    assert(!mp_fel_gpu_duration(0, UINT64_MAX, 64, 2, &ns));

    for (int kind = 0; kind < 6; kind++) {
        reset();
        gpu.fel_profile = kind != 0;
        if (kind == 1) query_bits = 0;
        if (kind == 2) query_period = 0;
        if (kind == 3) query_period = NAN;
        if (kind == 4) query_create_result = -7;
        init_fel_gpu_timer(&gpu);
        assert(query_create_calls == (kind >= 4));
        assert((gpu.fel_query_pool != 0) == (kind == 5));
    }

    for (int result = 0; result < 3; result++) {
        reset();
        gpu.release_sync_fd = gpu.fel_profile = true;
        init_fel_gpu_timer(&gpu);
        struct mp_image image = make_frame(0);
        assert(await_handoff(&image));
        struct vk_output *out = &gpu.outputs[0];
        out->fel_query_recorded = true;
        query_result = result == 0 ? VK_SUCCESS : result == 1 ? VK_NOT_READY : -7;
        query_values[0] = (UINT64_C(1) << 36) - 4;
        query_values[1] = 7;
        assert(!finish_output(&gpu, out, 0) && !query_calls && out->fel_query_recorded);
        now_ns = fence.ready_at;
        assert(finish_output(&gpu, out, 0));
        assert(query_calls == 1 && !out->fel_query_recorded && !out->pending);
        assert(gpu.fel_gpu_copy.count == (unsigned)(result == 0));
        assert(gpu.fel_gpu_copy.total == (unsigned)(result == 0 ? 11 : 0));
        assert(gpu.fel_query_unavailable == (unsigned)(result != 0));
        assert(gpu.fel_query_failed == (result == 2));
        assert(finish_output(&gpu, out, 0) && query_calls == 1); // No stale double sample.
        assert(mp_android_fel_staging_gpu_complete(image.android_fel_staging->data));
        av_buffer_unref(&image.android_fel_staging);
        clear_cached_frame();
    }

    reset();
    gpu.fel_map_started = gpu.fel_map_checkpoint = now_ns;
    now_ns += 100;
    fel_perf_checkpoint(&gpu, MP_FEL_PERF_IMPORT);
    now_ns += 50;
    fel_perf_finish_map(&gpu);
    assert(gpu.fel_map_cold.count == 1 && gpu.fel_map_cold.total == 150);
    assert(!gpu.fel_map_stages[MP_FEL_PERF_IMPORT].count);
    gpu.fel_profile_warm = true;
    gpu.fel_map_started = gpu.fel_map_checkpoint = now_ns;
    now_ns += 20;
    fel_perf_checkpoint(&gpu, MP_FEL_PERF_SUBMIT);
    now_ns += 10;
    fel_perf_finish_map(&gpu);
    assert(gpu.fel_map_warm.count == 1 && gpu.fel_map_warm.total == 30);
    assert(gpu.fel_map_stages[MP_FEL_PERF_SUBMIT].total == 20);
    assert(gpu.fel_map_stages[MP_FEL_PERF_OTHER].total == 10);
    assert(!gpu.fel_map_cpu.count && !gpu.fel_map_started && !gpu.fel_map_checkpoint);
    fel_perf_finish_map(&gpu); // No active profiling cannot change counters.
    assert(gpu.fel_map_warm.count == 1);
}

int main(void)
{
    assert(!mp_android_fel_staging_ready(NULL));
    mp_android_fel_staging_complete(NULL);
    test_handoff();
    test_timeout_and_isolation();
    test_fenced_release();
    test_performance_queries();
    puts("PASS: actual producer/GPU handoff: 120 CPU-fence + 120 async-fence frames, independent render/source signals, fd ownership/reuse/failure, bounded timeout, opt-in isolation");
    puts("PASS: real FEL timing helpers: cold/warm CPU stages, unsupported/failed timers, wrap/overflow, fence-before-query, no WAIT, unavailable results preserve playback");
    return 0;
}
