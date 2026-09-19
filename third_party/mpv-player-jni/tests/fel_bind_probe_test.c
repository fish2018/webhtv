/* Compiles the actual diagnostic implementation with strict Vulkan spies. */
#include <assert.h>
#include <inttypes.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>
#include "filters/f_android_fel.h"
#include "video/out/fel_bind_probe.h"

#define MP_ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))
#define MPMAX(a, b) ((a) > (b) ? (a) : (b))
#define MPMIN(a, b) ((a) < (b) ? (a) : (b))
#define FEL_LATENCY_SAMPLES 128
#define H(type, n) ((type)(uintptr_t)(n))
struct fel_latency_window { uint64_t ns[FEL_LATENCY_SAMPLES]; unsigned count, next; };
static void fel_latency_add(struct fel_latency_window *, uint64_t);
static int fel_latency_compare(const void *, const void *);
static void fel_latency_summary(const struct fel_latency_window *, uint64_t[3]);
struct vo {
    struct { void (*wakeup_cb)(void *); void *wakeup_ctx; bool android_dovi_fel; } extra;
    struct mp_fel_bind_probe fel_bind_probe;
    bool config_ok;
};
typedef struct MPContext {
    struct { bool pause; } *opts;
    bool paused_for_cache, paused;
    struct vo *video_out;
    void *vo_chain;
    int video_status;
} MPContext;
struct m_property { const char *name; };
enum { M_PROPERTY_SET, M_PROPERTY_GET, M_PROPERTY_OK = 1,
       M_PROPERTY_ERROR = -1, M_PROPERTY_UNAVAILABLE = -2, STATUS_PLAYING = 4 };
static bool get_internal_paused(struct MPContext *);
static void handle_fel_bind_probe(struct MPContext *);
static int mp_property_android_fel_bind_probe(void *, struct m_property *, int, void *);
static int64_t now_ns, cpu_ns;
static int notifications, wakes;
static int64_t mp_time_ns(void) { return now_ns += 1000; }
static int64_t mp_fel_thread_cpu_ns(void) { return cpu_ns += 1000; }
static void mp_sleep_ns(int64_t n) { now_ns += n; }
static void mp_notify_property(MPContext *ctx, const char *name) {
    (void)ctx; assert(!strcmp(name, "android-fel-bind-probe")); notifications++;
}
static void mp_wakeup_core(MPContext *ctx) { (void)ctx; wakes++; }
static void update_internal_pause_state(MPContext *ctx) { ctx->paused = get_internal_paused(ctx); }
static void mp_set_timeout(MPContext *ctx, double t) { (void)ctx; assert(t == 0.05); }
static int m_property_strdup_ro(int action, void *arg, const char *s) {
    assert(action == M_PROPERTY_GET); snprintf(arg, 64, "%s", s); return M_PROPERTY_OK;
}
struct pl_vulkan_queue { uint32_t index, count; };
typedef struct fake_vulkan *pl_vulkan;
struct fake_vulkan {
    VkPhysicalDevice phys_device;
    struct pl_vulkan_queue queue_graphics, queue_compute, queue_transfer;
    void (*lock_queue)(pl_vulkan, uint32_t, uint32_t);
    void (*unlock_queue)(pl_vulkan, uint32_t, uint32_t);
};
struct vk_input { VkImageView view; };
struct test_ra_ctx { struct vo *vo; };
struct test_owner { struct test_ra_ctx *ra_ctx; };
struct test_mapper { struct test_owner *owner; };
struct mp_image { struct { unsigned char *data; } *android_fel_staging; };
struct aimagereader_vk_stable {
    void *log, *gpu;
    struct test_mapper *mapper;
    pl_vulkan vk;
    VkDevice device;
    uint32_t queue_family, sampler_descriptors;
    VkSampler sampler;
    VkFormat output_format, source_format;
    uint64_t external_format;
    int width, height;
    VkFence fel_probe_drains[8];
    unsigned fel_probe_drain_count;
    bool fel_probe_drain_failed;
};
enum { R_IMAGE, R_MEMORY, R_VIEW, R_COMMAND_POOL, R_POOL, R_LAYOUT, R_PIPELINE_LAYOUT, R_FENCE, R_COUNT };
static unsigned made[R_COUNT], freed[R_COUNT], next_handle, tags[4096];
static unsigned resets, begins, ends, binds, submits, writes, cancel_at_bind;
static bool command_live, queue_locked, never_signal, device_lost, ack_pause, fail_memory;
static VkDeviceSize target_bytes;
static int acquire_state, fail_reset;
static MPContext *current_core;
static struct mp_fel_bind_probe *current_probe;
static char logs[12000];
static unsigned make(unsigned kind) { made[kind]++; return ++next_handle; }
static void mp_info(void *log, const char *format, ...) {
    (void)log; va_list args; va_start(args, format);
    size_t n = strlen(logs); vsnprintf(logs + n, sizeof(logs) - n, format, args); va_end(args);
}
static void pl_gpu_flush(void *gpu) { (void)gpu; assert(current_core->paused); }
static uint32_t find_memory_type(struct aimagereader_vk_stable *p, uint32_t bits, VkMemoryPropertyFlags flags) {
    (void)p; assert(bits == 1 && flags == VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT); return 0;
}
static int fel_probe_acquire_fence(int fd) { (void)fd; return acquire_state; }
static void lock_queue(pl_vulkan vk, uint32_t family, uint32_t idx) {
    (void)vk; (void)family; (void)idx; assert(!queue_locked); queue_locked = true;
}
static void unlock_queue(pl_vulkan vk, uint32_t family, uint32_t idx) {
    (void)vk; (void)family; (void)idx; assert(queue_locked); queue_locked = false;
}

VkResult vkCreateFence(VkDevice d, const VkFenceCreateInfo *i, const VkAllocationCallbacks *a, VkFence *v) {
    (void)d; (void)a; assert(!i->flags); *v = H(VkFence, make(R_FENCE)); return VK_SUCCESS;
}
void vkDestroyFence(VkDevice d, VkFence v, const VkAllocationCallbacks *a) {
    (void)d; (void)v; (void)a; freed[R_FENCE]++;
}
VkResult vkGetFenceStatus(VkDevice d, VkFence v) {
    (void)d; (void)v; return device_lost ? VK_ERROR_DEVICE_LOST : never_signal ? VK_NOT_READY : VK_SUCCESS;
}
VkResult vkWaitForFences(VkDevice d, uint32_t n, const VkFence *v, VkBool32 all, uint64_t timeout) {
    (void)d; (void)v; assert(n == 1 && all && timeout == UINT64_MAX); return VK_SUCCESS;
}
void vkGetDeviceQueue(VkDevice d, uint32_t family, uint32_t idx, VkQueue *v) {
    (void)d; *v = H(VkQueue, 100 + family + idx);
}
VkResult vkQueueSubmit(VkQueue q, uint32_t n, const VkSubmitInfo *i, VkFence f) {
    (void)q; assert(queue_locked && !n && !i && f && current_core->paused); submits++; return VK_SUCCESS;
}
VkResult vkCreateImage(VkDevice d, const VkImageCreateInfo *i, const VkAllocationCallbacks *a, VkImage *v) {
    (void)d; (void)a; assert(i->format == VK_FORMAT_A2B10G10R10_UNORM_PACK32);
    assert(i->extent.width == 3840 && i->extent.height == 2160 && i->extent.depth == 1);
    assert(i->usage == (VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT));
    *v = H(VkImage, make(R_IMAGE)); return VK_SUCCESS;
}
void vkGetImageMemoryRequirements(VkDevice d, VkImage v, VkMemoryRequirements *r) {
    (void)d; (void)v; *r = (VkMemoryRequirements){.size = target_bytes, .memoryTypeBits = 1};
}
VkResult vkAllocateMemory(VkDevice d, const VkMemoryAllocateInfo *i, const VkAllocationCallbacks *a, VkDeviceMemory *v) {
    (void)d; (void)a; assert(i->allocationSize <= MP_FEL_PROBE_MEMORY_LIMIT);
    if (fail_memory) return VK_ERROR_OUT_OF_DEVICE_MEMORY;
    *v = H(VkDeviceMemory, make(R_MEMORY)); return VK_SUCCESS;
}
VkResult vkBindImageMemory(VkDevice d, VkImage image, VkDeviceMemory memory, VkDeviceSize offset) {
    (void)d; assert(image && memory && !offset); return VK_SUCCESS;
}
VkResult vkCreateImageView(VkDevice d, const VkImageViewCreateInfo *i, const VkAllocationCallbacks *a, VkImageView *v) {
    (void)d; (void)a; assert(i->image && i->format == VK_FORMAT_A2B10G10R10_UNORM_PACK32);
    *v = H(VkImageView, make(R_VIEW)); return VK_SUCCESS;
}
void vkDestroyImageView(VkDevice d, VkImageView v, const VkAllocationCallbacks *a) {
    (void)d; (void)v; (void)a; assert(!command_live && made[R_POOL] == freed[R_POOL]); freed[R_VIEW]++;
}
void vkDestroyImage(VkDevice d, VkImage v, const VkAllocationCallbacks *a) {
    (void)d; (void)v; (void)a; assert(made[R_VIEW] == freed[R_VIEW]); freed[R_IMAGE]++;
}
void vkFreeMemory(VkDevice d, VkDeviceMemory v, const VkAllocationCallbacks *a) {
    (void)d; (void)v; (void)a; assert(made[R_IMAGE] == freed[R_IMAGE]); freed[R_MEMORY]++;
}
VkResult vkCreateCommandPool(VkDevice d, const VkCommandPoolCreateInfo *i, const VkAllocationCallbacks *a, VkCommandPool *v) {
    (void)d; (void)a; assert(i->flags == VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
    assert(!command_live); command_live = true; *v = H(VkCommandPool, make(R_COMMAND_POOL)); return VK_SUCCESS;
}
VkResult vkAllocateCommandBuffers(VkDevice d, const VkCommandBufferAllocateInfo *i, VkCommandBuffer *v) {
    (void)d; assert(i->commandBufferCount == 1 && i->level == VK_COMMAND_BUFFER_LEVEL_PRIMARY);
    *v = H(VkCommandBuffer, ++next_handle); return VK_SUCCESS;
}
void vkDestroyCommandPool(VkDevice d, VkCommandPool v, const VkAllocationCallbacks *a) {
    (void)d; (void)v; (void)a; command_live = false; freed[R_COMMAND_POOL]++;
}
VkResult vkCreateDescriptorSetLayout(VkDevice d, const VkDescriptorSetLayoutCreateInfo *i, const VkAllocationCallbacks *a, VkDescriptorSetLayout *v) {
    (void)d; (void)a; assert(!i->flags && i->bindingCount >= 1 && i->bindingCount <= 2);
    unsigned kind = 0;
    for (unsigned j = 0; j < i->bindingCount; j++) {
        const VkDescriptorSetLayoutBinding *b = &i->pBindings[j];
        assert(b->descriptorCount == 1 && b->stageFlags == VK_SHADER_STAGE_COMPUTE_BIT);
        if (!b->binding) { assert(b->pImmutableSamplers && *b->pImmutableSamplers); kind |= 1; }
        else { assert(b->binding == 1 && !b->pImmutableSamplers); kind |= 2; }
    }
    unsigned h = make(R_LAYOUT); tags[h] = kind; *v = H(VkDescriptorSetLayout, h); return VK_SUCCESS;
}
VkResult vkCreatePipelineLayout(VkDevice d, const VkPipelineLayoutCreateInfo *i, const VkAllocationCallbacks *a, VkPipelineLayout *v) {
    (void)d; (void)a; assert(i->setLayoutCount == 1);
    unsigned h = make(R_PIPELINE_LAYOUT); tags[h] = tags[(uintptr_t)*i->pSetLayouts];
    *v = H(VkPipelineLayout, h); return VK_SUCCESS;
}
VkResult vkCreateDescriptorPool(VkDevice d, const VkDescriptorPoolCreateInfo *i, const VkAllocationCallbacks *a, VkDescriptorPool *v) {
    (void)d; (void)a; assert(i->maxSets == 1 && !i->flags);
    for (unsigned j = 0; j < i->poolSizeCount; j++)
        assert(i->pPoolSizes[j].descriptorCount ==
            (i->pPoolSizes[j].type == VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER ? 4 : 1));
    *v = H(VkDescriptorPool, make(R_POOL)); return VK_SUCCESS;
}
VkResult vkAllocateDescriptorSets(VkDevice d, const VkDescriptorSetAllocateInfo *i, VkDescriptorSet *v) {
    (void)d; assert(i->descriptorSetCount == 1);
    unsigned h = ++next_handle; tags[h] = tags[(uintptr_t)*i->pSetLayouts];
    *v = H(VkDescriptorSet, h); return VK_SUCCESS;
}
void vkUpdateDescriptorSets(VkDevice d, uint32_t n, const VkWriteDescriptorSet *w, uint32_t copies, const VkCopyDescriptorSet *c) {
    (void)d; assert(n >= 1 && n <= 2 && !copies && !c); unsigned bits = 0;
    for (unsigned j = 0; j < n; j++) {
        assert(w[j].descriptorCount == 1 && w[j].pImageInfo->imageView);
        bits |= w[j].dstBinding == 0 ? 1 : 2;
    }
    assert(tags[(uintptr_t)w[0].dstSet] == bits); writes++;
}
void vkDestroyDescriptorPool(VkDevice d, VkDescriptorPool v, const VkAllocationCallbacks *a) {
    (void)d; (void)v; (void)a; assert(!command_live); freed[R_POOL]++;
}
void vkDestroyPipelineLayout(VkDevice d, VkPipelineLayout v, const VkAllocationCallbacks *a) {
    (void)d; (void)v; (void)a; assert(!command_live); freed[R_PIPELINE_LAYOUT]++;
}
void vkDestroyDescriptorSetLayout(VkDevice d, VkDescriptorSetLayout v, const VkAllocationCallbacks *a) {
    (void)d; (void)v; (void)a; assert(!command_live); freed[R_LAYOUT]++;
}
VkResult vkResetCommandBuffer(VkCommandBuffer c, VkCommandBufferResetFlags flags) {
    assert(c && !flags); resets++; return fail_reset == (int)resets ? VK_ERROR_OUT_OF_HOST_MEMORY : VK_SUCCESS;
}
VkResult vkBeginCommandBuffer(VkCommandBuffer c, const VkCommandBufferBeginInfo *i) {
    assert(c && i->flags == VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT); begins++; return VK_SUCCESS;
}
void vkCmdBindDescriptorSets(VkCommandBuffer c, VkPipelineBindPoint point, VkPipelineLayout layout,
    uint32_t first, uint32_t count, const VkDescriptorSet *sets, uint32_t dynamic, const uint32_t *offsets) {
    assert(c && point == VK_PIPELINE_BIND_POINT_COMPUTE && !first && count == 1 && !dynamic && !offsets);
    assert(tags[(uintptr_t)layout] == tags[(uintptr_t)*sets]); binds++;
    now_ns += 35000000; cpu_ns += 1000000;
    if (binds == cancel_at_bind) mp_fel_probe_cancel(current_probe);
}
VkResult vkEndCommandBuffer(VkCommandBuffer c) { assert(c); ends++; return VK_SUCCESS; }
void vkGetPhysicalDeviceProperties(VkPhysicalDevice d, VkPhysicalDeviceProperties *p) {
    (void)d; memset(p, 0, sizeof(*p)); strcpy(p->deviceName, "contract-spy");
}

#include "video/out/hwdec/hwdec_aimagereader_vk_probe.inc"

static void wake_core(void *ctx) { wakes++; if (ack_pause) handle_fel_bind_probe(ctx); }
static void reset(void) {
    memset(made, 0, sizeof(made)); memset(freed, 0, sizeof(freed)); memset(tags, 0, sizeof(tags));
    next_handle = resets = begins = ends = binds = submits = writes = cancel_at_bind = 0;
    command_live = queue_locked = never_signal = device_lost = fail_memory = false;
    ack_pause = true; acquire_state = fail_reset = notifications = wakes = 0;
    now_ns = cpu_ns = 1; target_bytes = UINT64_C(3840) * 2160 * 4; logs[0] = 0;
}
static void assert_clean(void) { for (unsigned i = 0; i < R_COUNT; i++) assert(made[i] == freed[i]); }

int main(void) {
    struct vo vo = {.config_ok = true};
    typeof(*((MPContext *)0)->opts) opts = {0};
    MPContext core = {.opts = &opts, .video_out = &vo, .vo_chain = &vo, .video_status = STATUS_PLAYING};
    struct test_ra_ctx ra = {.vo = &vo}; struct test_owner owner = {.ra_ctx = &ra};
    struct test_mapper mapper = {.owner = &owner};
    struct fake_vulkan vk = {.queue_graphics = {0, 1}, .queue_compute = {0, 1}, .queue_transfer = {1, 1},
        .lock_queue = lock_queue, .unlock_queue = unlock_queue};
    unsigned char lease = 0;
    typeof(*((struct mp_image *)0)->android_fel_staging) ref = {.data = &lease};
    struct mp_image frame = {.android_fel_staging = &ref};
    struct vk_input input = {.view = H(VkImageView, 1000)};
    struct aimagereader_vk_stable p = {.mapper = &mapper, .vk = &vk, .device = H(VkDevice, 1000),
        .sampler = H(VkSampler, 1000), .sampler_descriptors = 4, .width = 3840, .height = 2160,
        .output_format = VK_FORMAT_A2B10G10R10_UNORM_PACK32, .external_format = 0xf0};
    current_core = &core; current_probe = &vo.fel_bind_probe;
    vo.extra.wakeup_cb = wake_core; vo.extra.wakeup_ctx = &core; vo.extra.android_dovi_fel = true;
    reset();
    assert(!fel_run_bind_probe(&p, &input, &frame, -1)); assert(!wakes && now_ns == 1); assert_clean();
    assert(mp_fel_probe_start(current_probe, now_ns)); assert(!mp_fel_probe_start(current_probe, now_ns));
    assert(fel_run_bind_probe(&p, &input, &frame, -1));
    assert(mp_fel_probe_state(current_probe) == MP_FEL_PROBE_DONE);
    assert(!core.paused && !opts.pause && lease == 48);
    assert(resets == 160 && begins == 160 && ends == 160 && binds == 120 && writes == 3 && submits == 2);
    assert(strstr(logs, "samples=32 complete=1") && strstr(logs, "group=storage-only") && strstr(logs, "groups=4/4"));
    assert_clean();

    reset(); lease = 0; assert(mp_fel_probe_start(current_probe, now_ns)); cancel_at_bind = 3;
    assert(fel_run_bind_probe(&p, &input, &frame, -1));
    assert(mp_fel_probe_state(current_probe) == MP_FEL_PROBE_CANCELLED && !core.paused); assert_clean();

    reset(); lease = 0; assert(mp_fel_probe_start(current_probe, now_ns)); opts.pause = true;
    assert(fel_run_bind_probe(&p, &input, &frame, -1)); assert(core.paused && opts.pause); assert_clean();
    opts.pause = false; update_internal_pause_state(&core);

    reset(); assert(mp_fel_probe_start(current_probe, now_ns)); target_bytes = MP_FEL_PROBE_MEMORY_LIMIT + 1;
    assert(fel_run_bind_probe(&p, &input, &frame, -1));
    assert(!made[R_MEMORY] && !binds && mp_fel_probe_state(current_probe) == MP_FEL_PROBE_FAILED); assert_clean();

    reset(); assert(mp_fel_probe_start(current_probe, now_ns)); fail_memory = true;
    assert(fel_run_bind_probe(&p, &input, &frame, -1)); assert_clean();

    reset(); assert(mp_fel_probe_start(current_probe, now_ns)); fail_reset = 44;
    assert(fel_run_bind_probe(&p, &input, &frame, -1));
    assert(mp_fel_probe_state(current_probe) == MP_FEL_PROBE_FAILED); assert_clean();

    reset(); assert(mp_fel_probe_start(current_probe, now_ns)); never_signal = true;
    assert(fel_run_bind_probe(&p, &input, &frame, -1));
    assert(p.fel_probe_drain_count == 2 && !made[R_IMAGE] && !freed[R_FENCE] && !core.paused);
    fel_probe_retire_drains(&p, true); assert_clean();

    reset(); assert(mp_fel_probe_start(current_probe, now_ns)); device_lost = true;
    assert(fel_run_bind_probe(&p, &input, &frame, -1)); assert(!made[R_IMAGE]); assert_clean();

    reset(); assert(mp_fel_probe_start(current_probe, now_ns)); acquire_state = 1;
    assert(fel_run_bind_probe(&p, &input, &frame, 7)); assert(!submits && !made[R_IMAGE]); assert_clean();

    reset(); assert(mp_fel_probe_start(current_probe, now_ns)); ack_pause = false;
    assert(fel_run_bind_probe(&p, &input, &frame, -1)); assert(!submits); assert_clean();

    reset(); assert(mp_fel_probe_start(current_probe, now_ns)); current_probe->deadline = now_ns;
    assert(fel_run_bind_probe(&p, &input, &frame, -1));
    assert(mp_fel_probe_state(current_probe) == MP_FEL_PROBE_TIMED_OUT); assert_clean();

    reset(); current_probe->supported = false;
    struct m_property property = {.name = "android-fel-bind-probe"}; char *action = "start";
    assert(mp_property_android_fel_bind_probe(&core, &property, M_PROPERTY_SET, &action) == M_PROPERTY_UNAVAILABLE);
    current_probe->supported = true; opts.pause = true; update_internal_pause_state(&core);
    assert(mp_property_android_fel_bind_probe(&core, &property, M_PROPERTY_SET, &action) == M_PROPERTY_UNAVAILABLE);
    opts.pause = false; update_internal_pause_state(&core);
    assert(mp_property_android_fel_bind_probe(&core, &property, M_PROPERTY_SET, &action) == M_PROPERTY_OK);
    action = "cancel";
    assert(mp_property_android_fel_bind_probe(&core, &property, M_PROPERTY_SET, &action) == M_PROPERTY_OK);
    assert(!mp_fel_probe_active(current_probe));
    char status[64]; mp_property_android_fel_bind_probe(&core, &property, M_PROPERTY_GET, status);
    assert(strstr(status, ":cancelled"));
    core.paused_for_cache = true; assert(get_internal_paused(&core));
    puts("PASS FEL bind probe: real recording/ownership/budgets/cancel/pause/native property contracts");
    return 0;
}
