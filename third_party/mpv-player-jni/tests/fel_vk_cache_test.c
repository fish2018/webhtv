// Included before the actual stable mapper function bodies, not a second
// implementation of the cache. Vulkan calls below model resource validation;
// call-count results are not a device throughput or pixel-quality benchmark.
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include "filters/f_android_fel_perf.h"
#include <assert.h>
#include <inttypes.h>
#include <math.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define INPUT_CACHE_SIZE 8
#define FEL_INPUT_CACHE_SIZE 32
#define FEL_RECORD_CACHE_SIZE 128
#define FEL_ORDER_CAPACITY 8
#define FEL_LATENCY_SAMPLES 128
#define STABLE_WORKGROUP_X 16
#define STABLE_WORKGROUP_Y 8
#define MPMAX(a, b) ((a) > (b) ? (a) : (b))
#define MPMIN(a, b) ((a) < (b) ? (a) : (b))
#define MP_TIME_MS_TO_NS(v) ((v) * INT64_C(1000000))
#define MP_ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))
#define MP_ALIGN_UP(n, a) (((n) + (a) - 1) / (a) * (a))
#define mp_err(...) ((void)0)
#define HANDLE(type, n) ((type)(uintptr_t)(n))
#define ID(h) ((unsigned)(uintptr_t)(h))

typedef struct { unsigned id; } AHardwareBuffer;
typedef struct { uint32_t width, height; } AHardwareBuffer_Desc;
typedef struct { int32_t left, top, right, bottom; } AImageCropRect;
struct conversion_push_constants {
    float uv_offset[2], uv_scale[2];
    int32_t output_size[2];
};
struct vk_input {
    AHardwareBuffer *buffer;
    VkImage image;
    VkDeviceMemory memory;
    VkImageView view;
    uint64_t last_used;
    int users;
    bool initialized, removed;
};
struct vk_output {
    VkImage image;
    VkImageView view;
    VkDescriptorSet descriptor;
    VkCommandBuffer command, active_command;
    bool pending, written, fel_query_recorded;
};
struct vk_recording {
    VkCommandBuffer command;
    VkDescriptorSet descriptor;
    struct vk_input *input;
    struct vk_output *output;
    VkImageView input_view, output_view;
    AImageCropRect crop;
    uint32_t width, height;
    uint64_t last_used;
    bool valid, timestamps;
};
enum fel_api_op {
    FEL_API_DESCRIBE, FEL_API_PROPERTIES, FEL_API_IMAGE, FEL_API_MEMORY,
    FEL_API_BIND, FEL_API_VIEW, FEL_API_DESCRIPTOR, FEL_API_RESET,
    FEL_API_BEGIN, FEL_API_RECORD, FEL_API_END, FEL_API_BARRIER_IN,
    FEL_API_PIPELINE, FEL_API_DESCRIPTORS, FEL_API_PUSH, FEL_API_DISPATCH,
    FEL_API_BARRIER_OUT, FEL_API_PUSH_DESCRIPTORS, FEL_API_COUNT,
};
struct fel_api_clock { int64_t wall, cpu; };
struct fel_latency_window {
    uint64_t ns[FEL_LATENCY_SAMPLES];
    unsigned count, next;
};
struct fel_order_event {
    uint64_t serial;
    double requested_pts, mapped_pts;
    int output, input;
    char action;
};
typedef const struct test_vulkan {
    VkInstance instance;
    VkPhysicalDevice phys_device;
    PFN_vkGetInstanceProcAddr get_proc_addr;
    const char *const *extensions;
    int num_extensions;
} *pl_vulkan;
struct aimagereader_vk_stable {
    void *log;
    bool android_fel, fel_profile, fel_profile_warm, fel_query_failed;
    VkDevice device;
    pl_vulkan vk;
    bool push_descriptors;
    PFN_vkCmdPushDescriptorSetKHR CmdPushDescriptorSetKHR;
    uint32_t queue_family, sampler_descriptors;
    VkPipeline pipeline;
    VkPipelineLayout pipeline_layout;
    VkCommandPool command_pool;
    VkDescriptorSetLayout descriptor_layout;
    VkDescriptorPool recording_pool;
    VkQueryPool fel_query_pool;
    int width, height, num_inputs, num_recordings;
    uint64_t input_serial;
    struct vk_input inputs[FEL_INPUT_CACHE_SIZE];
    struct vk_output outputs[5];
    struct vk_recording recordings[FEL_RECORD_CACHE_SIZE];
    bool recording_cache_disabled;
    uint64_t recording_serial, recording_hits, recording_misses;
    uint64_t recording_evictions, recording_invalidations, recording_fallbacks;
    uint64_t recording_cold;
    uint64_t descriptor_content_hits, descriptor_writes, descriptor_rebinds;
    uint64_t input_hits, input_misses, input_evictions, input_removals;
    struct mp_fel_perf_stat fel_api_wall[FEL_API_COUNT], fel_api_cpu[FEL_API_COUNT];
    struct fel_latency_window fel_bind_window, fel_record_window, fel_map_window;
    int64_t fel_map_started, fel_map_cpu_started, fel_map_checkpoint;
    struct mp_fel_perf_stat fel_map_warm, fel_map_cold, fel_map_cpu;
    struct mp_fel_perf_stat fel_map_stages[MP_FEL_PERF_COUNT];
    uint64_t submitted_outputs, fresh_commands, fel_order_count, fel_pts_differences;
    struct fel_order_event fel_order[FEL_ORDER_CAPACITY], fel_last_pts_difference;
    double fel_active_pts;
    int fel_active_input, fel_active_output;
    int64_t fel_slow_window;
    unsigned fel_slow_count;
};

// The independent wait-probe test compiles the real diagnostics. These cache
// scenarios keep optional sampling disabled and retain their Vulkan assertions.
struct fel_wait_sample { bool active; };
static struct fel_wait_sample fel_wait_probe_begin(struct aimagereader_vk_stable *p)
{
    return (struct fel_wait_sample){0};
}
static void fel_wait_probe_end(struct aimagereader_vk_stable *p,
                               const struct fel_wait_sample *sample) {}

static struct fel_api_clock fel_api_begin(struct aimagereader_vk_stable *);
static void fel_api_end(struct aimagereader_vk_stable *, enum fel_api_op, struct fel_api_clock);
static void fel_latency_add(struct fel_latency_window *, uint64_t);
static int fel_latency_compare(const void *, const void *);
static void fel_latency_summary(const struct fel_latency_window *, uint64_t [3]);
static void fel_perf_checkpoint(struct aimagereader_vk_stable *, enum mp_fel_perf_stage);
static void fel_perf_finish_map(struct aimagereader_vk_stable *);
static void trace_fel_frame_order(struct aimagereader_vk_stable *, char, int, int, uint64_t, double, double);
static void format_fel_frame_order(struct aimagereader_vk_stable *, char *, size_t);
static void invalidate_input_recordings(struct aimagereader_vk_stable *, struct vk_input *);
static void destroy_input(struct aimagereader_vk_stable *, struct vk_input *);
static void destroy_recording_cache(struct aimagereader_vk_stable *);
static struct vk_input *find_input(struct aimagereader_vk_stable *, AHardwareBuffer *);
static void purge_removed_inputs(struct aimagereader_vk_stable *);
static struct vk_input *select_input_slot(struct aimagereader_vk_stable *);
static bool recording_pending(const struct vk_recording *);
static bool recording_matches(const struct vk_recording *, struct vk_output *, struct vk_input *,
                              const AHardwareBuffer_Desc *, const AImageCropRect *, bool);
static struct vk_recording *select_recording(struct aimagereader_vk_stable *, struct vk_output *,
                                             struct vk_input *, const AHardwareBuffer_Desc *,
                                             const AImageCropRect *, bool *);
static bool allocate_recording(struct aimagereader_vk_stable *, struct vk_recording *);
static bool has_extension(pl_vulkan, const char *);
static bool create_conversion_descriptor_layout(struct aimagereader_vk_stable *,
    const VkDescriptorSetLayoutCreateInfo *, uint32_t);
static void update_conversion_descriptor(struct aimagereader_vk_stable *, VkCommandBuffer, VkDescriptorSet,
                                         struct vk_input *, struct vk_output *, bool);
static bool record_conversion(struct aimagereader_vk_stable *, struct vk_output *, struct vk_input *,
                              const AHardwareBuffer_Desc *, const AImageCropRect *, struct vk_recording *);
static bool prepare_conversion(struct aimagereader_vk_stable *, struct vk_output *, struct vk_input *,
                               const AHardwareBuffer_Desc *, const AImageCropRect *);
void aimagereader_vk_stable_buffer_removed(struct aimagereader_vk_stable *, AHardwareBuffer *);

struct command_state {
    bool pending, executable, freed, allocated;
    VkCommandBufferUsageFlags flags;
    VkDescriptorSet descriptor;
    bool pushed;
    VkImageView pushed_views[2];
    unsigned barriers, dispatch[3], timestamps, resets;
    VkImageMemoryBarrier acquire[2], release[2];
    struct conversion_push_constants push;
};
static struct command_state cmds[512];
static VkImageView bindings[512][2];
static unsigned command_next, descriptor_next;
static unsigned update_calls, reset_calls, begin_calls, end_calls;
static unsigned pool_calls, descriptor_calls, command_calls, command_frees, pool_frees;
static unsigned removed_views, imports;
static int fail_allocation, fail_record;
static int64_t wall_clock;
static unsigned info_logs;
static char last_info[512];
static unsigned push_calls, bind_calls, dispatch_calls, layout_calls, property_calls;
static uint32_t max_push;
static int missing_entry;
static bool fail_push_layout, fail_set_layout;
static VkDescriptorSetLayoutCreateFlags last_layout_flags;
static struct test_vulkan test_vk;

static void mp_info(void *log, const char *format, ...)
{
    (void)log;
    va_list args;
    va_start(args, format);
    vsnprintf(last_info, sizeof(last_info), format, args);
    va_end(args);
    info_logs++;
}

static int64_t mp_time_ns(void) { return ++wall_clock * 1000; }
static bool vk_success(struct aimagereader_vk_stable *p, VkResult result, const char *action)
{ (void)p; (void)action; return result == VK_SUCCESS; }
static struct command_state *command(VkCommandBuffer cmd)
{ assert(ID(cmd) > 0 && ID(cmd) < MP_ARRAY_SIZE(cmds)); return &cmds[ID(cmd)]; }

static void VKAPI_CALL fake_push_descriptors(VkCommandBuffer cmd, VkPipelineBindPoint point,
    VkPipelineLayout layout, uint32_t set, uint32_t count, const VkWriteDescriptorSet *writes)
{
    assert(point == VK_PIPELINE_BIND_POINT_COMPUTE && set == 0 && count == 2);
    struct command_state *c = command(cmd);
    assert(!c->pending && !c->descriptor && !c->executable && !c->pushed);
    for (unsigned n = 0; n < count; n++) {
        assert(writes[n].dstSet == VK_NULL_HANDLE && writes[n].dstBinding == n);
        assert(writes[n].descriptorCount == 1 && writes[n].dstArrayElement == 0);
        assert(writes[n].descriptorType == (n ? VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
                                             : VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        assert(writes[n].pImageInfo->imageLayout == (n ? VK_IMAGE_LAYOUT_GENERAL
                                             : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
        assert(!writes[n].pImageInfo->sampler); // immutable sampler comes from layout
        c->pushed_views[n] = writes[n].pImageInfo->imageView;
    }
    c->pushed = true;
    push_calls++;
}
static void VKAPI_CALL fake_properties(VkPhysicalDevice device, VkPhysicalDeviceProperties2 *props)
{
    VkPhysicalDevicePushDescriptorPropertiesKHR *push = props->pNext;
    assert(push->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PUSH_DESCRIPTOR_PROPERTIES_KHR);
    push->maxPushDescriptors = max_push;
    property_calls++;
}
static PFN_vkVoidFunction VKAPI_CALL fake_device_proc(VkDevice device, const char *name)
{
    assert(!strcmp(name, "vkCmdPushDescriptorSetKHR"));
    return missing_entry == 1 ? NULL : (PFN_vkVoidFunction)fake_push_descriptors;
}
static PFN_vkVoidFunction VKAPI_CALL fake_instance_proc(VkInstance instance, const char *name)
{
    if (!strcmp(name, "vkGetDeviceProcAddr"))
        return missing_entry == 2 ? NULL : (PFN_vkVoidFunction)fake_device_proc;
    assert(!strcmp(name, "vkGetPhysicalDeviceProperties2") ||
           !strcmp(name, "vkGetPhysicalDeviceProperties2KHR"));
    return missing_entry == 3 ? NULL : (PFN_vkVoidFunction)fake_properties;
}
static VkResult vkCreateDescriptorSetLayout(VkDevice device, const VkDescriptorSetLayoutCreateInfo *info,
    const VkAllocationCallbacks *alloc, VkDescriptorSetLayout *layout)
{
    layout_calls++;
    last_layout_flags = info->flags;
    assert(info->bindingCount == 2 && info->pBindings[0].pImmutableSamplers);
    assert(info->pBindings[0].descriptorType == VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
    bool push = info->flags & VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR;
    if ((push && fail_push_layout) || (!push && fail_set_layout)) {
        *layout = HANDLE(VkDescriptorSetLayout, 511); // invalid on failure; never publish it
        return VK_ERROR_OUT_OF_HOST_MEMORY;
    }
    *layout = HANDLE(VkDescriptorSetLayout, 2);
    return VK_SUCCESS;
}

static VkResult vkCreateDescriptorPool(VkDevice device, const VkDescriptorPoolCreateInfo *info,
                                      const VkAllocationCallbacks *alloc, VkDescriptorPool *pool)
{
    pool_calls++;
    assert(!info->flags && info->maxSets == FEL_RECORD_CACHE_SIZE);
    assert(info->pPoolSizes[0].descriptorCount == 4 * FEL_RECORD_CACHE_SIZE);
    if (fail_allocation == 1) { *pool = HANDLE(VkDescriptorPool, 511); return VK_ERROR_OUT_OF_HOST_MEMORY; }
    *pool = HANDLE(VkDescriptorPool, 1);
    return VK_SUCCESS;
}
static VkResult vkAllocateDescriptorSets(VkDevice device, const VkDescriptorSetAllocateInfo *info,
                                         VkDescriptorSet *set)
{
    descriptor_calls++;
    assert(info->descriptorSetCount == 1 && info->descriptorPool);
    if (fail_allocation == 2) { *set = HANDLE(VkDescriptorSet, 511); return VK_ERROR_OUT_OF_HOST_MEMORY; }
    *set = HANDLE(VkDescriptorSet, descriptor_next++);
    return VK_SUCCESS;
}
static VkResult vkAllocateCommandBuffers(VkDevice device, const VkCommandBufferAllocateInfo *info,
                                         VkCommandBuffer *cmd)
{
    command_calls++;
    assert(info->commandBufferCount == 1 && info->level == VK_COMMAND_BUFFER_LEVEL_PRIMARY);
    if (fail_allocation == 3) { *cmd = HANDLE(VkCommandBuffer, 511); return VK_ERROR_OUT_OF_HOST_MEMORY; }
    *cmd = HANDLE(VkCommandBuffer, command_next++);
    command(*cmd)->allocated = true;
    return VK_SUCCESS;
}
static void vkUpdateDescriptorSets(VkDevice device, uint32_t count,
                                    const VkWriteDescriptorSet *writes,
                                    uint32_t copies, const VkCopyDescriptorSet *copy)
{
    update_calls++;
    assert(!copies && (count == 1 || count == 2));
    for (unsigned w = 0; w < count; w++) {
        unsigned ds = ID(writes[w].dstSet);
        assert(ds < MP_ARRAY_SIZE(bindings) && writes[w].dstBinding < 2);
        for (unsigned i = 1; i < MP_ARRAY_SIZE(cmds); i++) {
            if (cmds[i].descriptor != writes[w].dstSet) continue;
            assert(!cmds[i].pending);
            cmds[i].executable = false; // Vulkan invalidates an old binding.
        }
        bindings[ds][writes[w].dstBinding] = writes[w].pImageInfo->imageView;
    }
}
static VkResult vkResetCommandBuffer(VkCommandBuffer cmd, VkCommandBufferResetFlags flags)
{
    struct command_state *c = command(cmd);
    assert(!c->pending && !c->freed && !flags);
    if (fail_record == 1) return VK_ERROR_OUT_OF_HOST_MEMORY;
    *c = (struct command_state){.allocated = c->allocated};
    reset_calls++;
    return VK_SUCCESS;
}
static VkResult vkBeginCommandBuffer(VkCommandBuffer cmd, const VkCommandBufferBeginInfo *info)
{
    if (fail_record == 2) return VK_ERROR_OUT_OF_HOST_MEMORY;
    command(cmd)->flags = info->flags;
    begin_calls++;
    return VK_SUCCESS;
}
static VkResult vkEndCommandBuffer(VkCommandBuffer cmd)
{
    end_calls++;
    if (fail_record == 3) return VK_ERROR_OUT_OF_HOST_MEMORY;
    command(cmd)->executable = true;
    return VK_SUCCESS;
}
static void vkCmdResetQueryPool(VkCommandBuffer cmd, VkQueryPool pool, uint32_t first, uint32_t count)
{ assert(pool && count == 2); command(cmd)->resets++; }
static void vkCmdWriteTimestamp(VkCommandBuffer cmd, VkPipelineStageFlagBits stage,
                                VkQueryPool pool, uint32_t query)
{ assert(pool); command(cmd)->timestamps++; }
static void vkCmdPipelineBarrier(VkCommandBuffer cmd, VkPipelineStageFlags src,
                                 VkPipelineStageFlags dst, VkDependencyFlags flags,
                                 uint32_t nm, const VkMemoryBarrier *m,
                                 uint32_t nb, const VkBufferMemoryBarrier *b,
                                 uint32_t ni, const VkImageMemoryBarrier *images)
{
    struct command_state *c = command(cmd);
    assert(ni == 2 && !nm && !nb && c->barriers < 2);
    memcpy(c->barriers++ ? c->release : c->acquire, images, sizeof(c->acquire));
}
static void vkCmdBindPipeline(VkCommandBuffer cmd, VkPipelineBindPoint point, VkPipeline pipeline)
{ assert(point == VK_PIPELINE_BIND_POINT_COMPUTE); }
static void vkCmdBindDescriptorSets(VkCommandBuffer cmd, VkPipelineBindPoint point,
                                    VkPipelineLayout layout, uint32_t first, uint32_t count,
                                    const VkDescriptorSet *sets, uint32_t offsets, const uint32_t *values)
{ assert(count == 1 && !offsets && *sets); command(cmd)->descriptor = *sets; bind_calls++; }
static void vkCmdPushConstants(VkCommandBuffer cmd, VkPipelineLayout layout,
                               VkShaderStageFlags stages, uint32_t offset, uint32_t size, const void *data)
{ assert(size == sizeof(command(cmd)->push)); memcpy(&command(cmd)->push, data, size); }
static void vkCmdDispatch(VkCommandBuffer cmd, uint32_t x, uint32_t y, uint32_t z)
{ command(cmd)->dispatch[0] = x; command(cmd)->dispatch[1] = y; command(cmd)->dispatch[2] = z; dispatch_calls++; }
static void vkFreeCommandBuffers(VkDevice device, VkCommandPool pool, uint32_t count,
                                 const VkCommandBuffer *buffers)
{
    for (unsigned i = 0; i < count; i++) {
        struct command_state *c = command(buffers[i]);
        assert(!c->pending && !c->freed && c->allocated);
        c->freed = true;
        command_frees++;
    }
}
static void vkDestroyDescriptorPool(VkDevice device, VkDescriptorPool pool, const VkAllocationCallbacks *a)
{ assert(pool == HANDLE(VkDescriptorPool, 1)); pool_frees++; }
static void vkDestroyImageView(VkDevice device, VkImageView view, const VkAllocationCallbacks *a)
{
    for (unsigned i = 1; i < MP_ARRAY_SIZE(cmds); i++) {
        unsigned ds = ID(cmds[i].descriptor);
        VkImageView *views = cmds[i].pushed ? cmds[i].pushed_views : bindings[ds];
        if (views[0] != view && views[1] != view) continue;
        assert(!cmds[i].pending);
        cmds[i].executable = false;
    }
    removed_views++;
}
static void vkDestroyImage(VkDevice device, VkImage image, const VkAllocationCallbacks *a) {}
static void vkFreeMemory(VkDevice device, VkDeviceMemory memory, const VkAllocationCallbacks *a) {}

static const AHardwareBuffer_Desc desc = {3840, 2160};
static const AImageCropRect full = {0, 0, 3840, 2160};
static AHardwareBuffer buffers[40];

static void reset(struct aimagereader_vk_stable *p, bool fel)
{
    memset(p, 0, sizeof(*p)); memset(cmds, 0, sizeof(cmds)); memset(bindings, 0, sizeof(bindings));
    update_calls = reset_calls = begin_calls = end_calls = 0;
    pool_calls = descriptor_calls = command_calls = command_frees = pool_frees = 0;
    imports = removed_views = 0; fail_allocation = fail_record = 0;
    push_calls = bind_calls = dispatch_calls = layout_calls = property_calls = 0;
    max_push = 32; missing_entry = 0; fail_push_layout = fail_set_layout = false;
    last_layout_flags = 0;
    static const char *const extensions[] = {VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME};
    test_vk = (struct test_vulkan){.get_proc_addr = fake_instance_proc,
        .extensions = extensions, .num_extensions = 1};
    p->vk = &test_vk;
    command_next = descriptor_next = 100;
    p->android_fel = fel; p->sampler_descriptors = 4;
    p->width = 3840; p->height = 2160; p->queue_family = 2;
    for (unsigned i = 0; i < MP_ARRAY_SIZE(p->outputs); i++) {
        p->outputs[i].command = HANDLE(VkCommandBuffer, i + 1);
        command(p->outputs[i].command)->allocated = true;
        p->outputs[i].descriptor = HANDLE(VkDescriptorSet, i + 1);
        p->outputs[i].image = HANDLE(VkImage, i + 1);
        p->outputs[i].view = HANDLE(VkImageView, i + 1);
        bindings[i + 1][1] = p->outputs[i].view;
    }
}
static struct vk_input *get_input(struct aimagereader_vk_stable *p, unsigned b)
{
    struct vk_input *input = find_input(p, &buffers[b]);
    if (!input) {
        input = select_input_slot(p); assert(input);
        if (input == &p->inputs[p->num_inputs]) p->num_inputs++;
        imports++;
        *input = (struct vk_input){.buffer = &buffers[b],
            .image = HANDLE(VkImage, imports + 100), .view = HANDLE(VkImageView, imports + 100),
            .memory = HANDLE(VkDeviceMemory, imports + 100)};
    }
    input->last_used = ++p->input_serial;
    return input;
}
static VkCommandBuffer active(struct vk_output *output)
{ return output->active_command ? output->active_command : output->command; }
static void complete_frame(struct vk_output *output, struct vk_input *input)
{
    struct command_state *c = command(active(output));
    assert(c->executable && !c->pending);
    assert(c->barriers == 2 && c->dispatch[0] == 240 && c->dispatch[1] == 270 && c->dispatch[2] == 1);
    assert(c->acquire[0].srcQueueFamilyIndex == VK_QUEUE_FAMILY_FOREIGN_EXT);
    assert(c->release[0].dstQueueFamilyIndex == VK_QUEUE_FAMILY_FOREIGN_EXT);
    assert(c->acquire[0].dstAccessMask == VK_ACCESS_SHADER_READ_BIT);
    assert(c->release[1].oldLayout == VK_IMAGE_LAYOUT_GENERAL);
    VkImageView *views = c->pushed ? c->pushed_views : bindings[ID(c->descriptor)];
    assert(views[0] == input->view);
    assert(views[1] == output->view);
    input->initialized = output->written = true;
}

static void test_rotation_and_default(void)
{
    struct aimagereader_vk_stable p;
    unsigned base_imports = 0, base_updates = 0;
    for (int fel = 0; fel <= 1; fel++) {
        reset(&p, fel);
        for (unsigned n = 0; n < 1200; n++) {
            struct vk_input *input = get_input(&p, n % 12);
            struct vk_output *output = &p.outputs[n % 5];
            assert(prepare_conversion(&p, output, input, &desc, &full));
            complete_frame(output, input);
        }
        if (!fel) {
            base_imports = imports; base_updates = update_calls;
            assert(imports == 1200 && update_calls == 1200 && !p.num_recordings && !pool_calls);
            assert(p.num_inputs == INPUT_CACHE_SIZE);
            assert(command(p.outputs[0].command)->flags == VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        } else {
            assert(imports == 12 && update_calls == 72 && end_calls == 1200);
            assert(p.recording_hits == 1128 && p.fresh_commands == 1200);
            assert(p.descriptor_content_hits == 1128 && p.descriptor_writes == 72);
            assert(bind_calls == 1200 && dispatch_calls == 1200 && p.descriptor_rebinds == 1200);
            assert(!p.fel_bind_window.count && !p.fel_record_window.count);
            assert(p.num_inputs == 12 && p.num_recordings == 60 && !p.input_evictions);
            assert(pool_calls == 1 && command_calls == 60 && descriptor_calls == 60);
            printf("PASS: 12-input/5-output, 1200 frames: imports %u -> %u; descriptor writes %u -> %u; 1200 fresh binds/recordings/dispatches; content hits=%llu, no replay (call counts only)\n",
                   base_imports, imports, base_updates, update_calls, (unsigned long long)p.recording_hits);
            destroy_recording_cache(&p);
            assert(command_frees == 60 && pool_frees == 1 && !p.num_recordings);
        }
    }
}

static void test_keys_pending_and_removal(void)
{
    struct aimagereader_vk_stable p; reset(&p, true);
    struct vk_input *input = get_input(&p, 0);
    struct vk_output *output = &p.outputs[0];
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(!output->active_command && p.recording_cold == 1);
    assert(command(active(output))->acquire[0].oldLayout == VK_IMAGE_LAYOUT_UNDEFINED);
    complete_frame(output, input);
    assert(prepare_conversion(&p, output, input, &desc, &full));
    VkCommandBuffer first = active(output);
    assert(command(first)->flags == VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
    assert(command(first)->acquire[0].oldLayout == VK_IMAGE_LAYOUT_GENERAL);
    unsigned calls = update_calls;
    unsigned records = end_calls;
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(active(output) == first && update_calls == calls && end_calls == records + 1);
    assert(p.recording_hits == 1 && command(first)->flags == VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
    calls = update_calls;

    output->pending = command(first)->pending = true; input->users = 1;
    assert(!prepare_conversion(&p, output, input, &desc, &full));
    assert(active(output) == first && update_calls == calls);
    aimagereader_vk_stable_buffer_removed(&p, input->buffer);
    assert(input->removed && input->view && !removed_views && p.recordings[0].valid);
    command(first)->pending = output->pending = false; input->users = 0;
    purge_removed_inputs(&p);
    assert(removed_views == 1 && !p.recordings[0].valid && !p.recordings[0].input);
    assert(!command(first)->executable && p.recording_invalidations == 1);
    input = get_input(&p, 0); // Same AHB pointer and slot, new Vulkan objects.
    input->initialized = true;
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(update_calls == calls + 1 && command(active(output))->executable);

    AImageCropRect crop = {4, 8, 1920, 1080};
    assert(prepare_conversion(&p, output, input, &desc, &crop));
    assert(fabsf(command(active(output))->push.uv_offset[0] - 4.0f / 3840) < 1e-7f);
    AHardwareBuffer_Desc different = {4096, 2176};
    calls = update_calls;
    assert(prepare_conversion(&p, output, input, &different, &crop));
    assert(update_calls == calls + 1);
    p.fel_query_pool = HANDLE(VkQueryPool, 1);
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(output->fel_query_recorded && command(active(output))->timestamps == 2);
    calls = update_calls;
    output->fel_query_recorded = false; // The prior frame's query was collected.
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(output->fel_query_recorded && update_calls == calls);
    p.fel_query_failed = true;
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(!output->fel_query_recorded && !command(active(output))->timestamps);
    destroy_recording_cache(&p);
    puts("PASS: cold/warm layouts, pending guard, deferred removal/ABA, crop/geometry and query invalidation");
}

static void test_descriptor_lifetimes(void)
{
    struct aimagereader_vk_stable p; reset(&p, true);
    struct vk_input *input = get_input(&p, 0); input->initialized = true;
    struct vk_input *slot = input;
    VkImageView original_view = input->view;
    struct vk_output *output = &p.outputs[0]; output->written = true;
    assert(prepare_conversion(&p, output, input, &desc, &full));
    unsigned calls = update_calls;
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(update_calls == calls && p.descriptor_content_hits == 1);

    // A stable C slot does not make a replaced Vulkan view equivalent.
    input->view = HANDLE(VkImageView, 300);
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(update_calls == ++calls);
    complete_frame(output, input);
    output->view = HANDLE(VkImageView, 301);
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(update_calls == ++calls);
    complete_frame(output, input);

    // Destruction invalidates even when AHB, C slot and Vulkan handle all recur.
    destroy_input(&p, input);
    input = get_input(&p, 0);
    assert(input == slot);
    input->view = original_view;
    input->initialized = true;
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(update_calls == ++calls && p.descriptor_content_hits == 1);
    complete_frame(output, input);

    // Output, immutable sampler, YCbCr and layout rebuilds own a new cache life.
    destroy_recording_cache(&p);
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(update_calls == ++calls && p.num_recordings == 1);
    complete_frame(output, input);
    destroy_recording_cache(&p);
    puts("PASS: exact view identity, recycled AHB/slot/view invalidation and new cache lifetime force descriptor writes");
}

static void test_limits_and_fallback(void)
{
    struct aimagereader_vk_stable p; reset(&p, true);
    for (unsigned n = 0; n < 40; n++) get_input(&p, n);
    assert(p.num_inputs == FEL_INPUT_CACHE_SIZE && p.input_evictions == 8);
    struct vk_input *input = get_input(&p, 0); input->initialized = true;
    struct vk_output *output = &p.outputs[0]; output->written = true;
    AImageCropRect crop = full;
    for (int n = 0; n < FEL_RECORD_CACHE_SIZE; n++) {
        crop.left = n;
        assert(prepare_conversion(&p, output, input, &desc, &crop));
    }
    assert(p.num_recordings == FEL_RECORD_CACHE_SIZE && !p.recording_evictions);
    crop.left++;
    assert(prepare_conversion(&p, output, input, &desc, &crop));
    assert(p.recording_evictions == 1 && command_calls == FEL_RECORD_CACHE_SIZE);
    struct vk_output pending[FEL_RECORD_CACHE_SIZE]; memset(pending, 0, sizeof(pending));
    for (int n = 0; n < FEL_RECORD_CACHE_SIZE; n++) {
        pending[n].pending = true; pending[n].active_command = p.recordings[n].command;
        p.recordings[n].output = &pending[n];
    }
    unsigned calls = update_calls;
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(!output->active_command && update_calls == calls + 1 && p.recording_fallbacks == 1);
    assert(p.recording_evictions == 1); // No pending entry was reset or evicted.
    destroy_recording_cache(&p);

    for (int failure = 1; failure <= 3; failure++) {
        reset(&p, true); input = get_input(&p, 0); input->initialized = true;
        output = &p.outputs[0]; output->written = true; fail_allocation = failure;
        assert(prepare_conversion(&p, output, input, &desc, &full));
        assert(p.recording_cache_disabled && !output->active_command);
        unsigned attempts = pool_calls + command_calls + descriptor_calls;
        fail_allocation = 0;
        assert(prepare_conversion(&p, output, input, &desc, &full));
        assert(attempts == pool_calls + command_calls + descriptor_calls && p.recording_fallbacks == 2);
        destroy_recording_cache(&p);
    }
    for (int failure = 1; failure <= 3; failure++) {
        reset(&p, true); input = get_input(&p, 0); input->initialized = true;
        output = &p.outputs[0]; output->written = true; fail_record = failure;
        assert(!prepare_conversion(&p, output, input, &desc, &full));
        assert(!p.recordings[0].valid && !output->active_command);
        fail_record = 0;
        assert(prepare_conversion(&p, output, input, &desc, &full));
        assert(!p.recording_hits && p.num_recordings == 1);
        fail_record = failure; // Failed fresh recording cannot select old commands.
        assert(!prepare_conversion(&p, output, input, &desc, &full));
        assert(!p.recordings[0].valid && !output->active_command && !p.recording_hits);
        calls = update_calls;
        fail_record = 0;
        assert(prepare_conversion(&p, output, input, &desc, &full));
        assert(update_calls == calls + 1 && !p.descriptor_content_hits);
        complete_frame(output, input);
        destroy_recording_cache(&p);
    }
    puts("PASS: bounded LRU/imports, in-flight exhaustion, optional allocation fallback and failed recording not reusable");
}

static void test_api_measurements(void)
{
    struct aimagereader_vk_stable p; reset(&p, true);
    struct vk_input *input = get_input(&p, 0); input->initialized = true;
    struct vk_output *output = &p.outputs[0]; output->written = true;
    p.fel_profile = true; p.fel_profile_warm = false;
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(!p.fel_api_wall[FEL_API_DESCRIPTOR].count);
    p.fel_profile_warm = true;
    AImageCropRect crop = full; crop.left = 1;
    assert(prepare_conversion(&p, output, input, &desc, &crop));
    assert(p.fel_api_wall[FEL_API_DESCRIPTOR].count == 1 && p.fel_api_wall[FEL_API_END].count == 1);
    assert(prepare_conversion(&p, output, input, &desc, &crop));
    assert(p.fel_api_wall[FEL_API_DESCRIPTOR].count == 1 && p.fel_api_wall[FEL_API_RESET].count == 2);
    for (int op = FEL_API_BARRIER_IN; op < FEL_API_PUSH_DESCRIPTORS; op++)
        assert(p.fel_api_wall[op].count == 2);
    assert(!p.fel_api_wall[FEL_API_PUSH_DESCRIPTORS].count);
    assert(p.fel_bind_window.count == 2 && p.fel_record_window.count == 2);
    destroy_recording_cache(&p);
    puts("PASS: API diagnostics exclude cold initialization; content hits skip writes but measure every fresh bind/record");
}

static void test_latency_windows(void)
{
    struct fel_latency_window window = {0};
    uint64_t summary[3];
    fel_latency_summary(&window, summary);
    assert(!summary[0] && !summary[1] && !summary[2]);
    for (uint64_t n = 1; n <= 256; n++)
        fel_latency_add(&window, n * 1000);
    struct fel_latency_window saved = window;
    fel_latency_summary(&window, summary);
    assert(window.count == 128 && window.next == 0);
    assert(summary[0] == 192 && summary[1] == 250 && summary[2] == 256);
    assert(!memcmp(&window, &saved, sizeof(window)));

    struct aimagereader_vk_stable p; reset(&p, true);
    fel_perf_finish_map(&p); // profiling disabled: no clock or sample
    assert(!p.fel_map_window.count && !p.fel_map_cold.count);
    p.fel_map_started = p.fel_map_checkpoint = mp_time_ns();
    fel_perf_finish_map(&p);
    assert(!p.fel_map_window.count && p.fel_map_cold.count == 1);
    p.fel_profile_warm = true;
    p.fel_map_started = p.fel_map_checkpoint = mp_time_ns();
    fel_perf_finish_map(&p);
    assert(p.fel_map_window.count == 1 && p.fel_map_warm.count == 1);
    puts("PASS: bounded 128-call latency window, nearest-rank quantiles, warm-only map samples and non-mutating summaries");
}

static void test_bounded_diagnostics(void)
{
    struct aimagereader_vk_stable p; reset(&p, false);
    trace_fel_frame_order(&p, 'M', 0, 0, 1, 1, 1);
    assert(!p.fel_order_count);
    p.android_fel = true;
    trace_fel_frame_order(&p, 'M', 0, 0, 1, 1, 1);
    assert(!p.fel_order_count); // Disabled diagnostics add no frame history.
    p.fel_profile = p.fel_profile_warm = true;
    for (int n = 1; n <= 12; n++)
        trace_fel_frame_order(&p, 'M', n % 5, n % 12, n, n, n);
    trace_fel_frame_order(&p, 'R', 1, 11, 11, 11, 11);
    trace_fel_frame_order(&p, 'R', 2, 0, 12, 12.5, 12);
    trace_fel_frame_order(&p, 'A', 2, 0, 12, 13, 12);
    assert(p.fel_order_count == 15 && p.fel_pts_differences == 1);
    char order[1024]; format_fel_frame_order(&p, order, sizeof(order));
    assert(strstr(order, "M#8/o3") && !strstr(order, "M#7/"));
    assert(strstr(order, "R#12/o2/i0:12.500000>12.000000"));
    assert(strstr(order, "R#11/") < strstr(order, "A#12/"));
    char tiny[4] = {'x', 'x', 'x', 'z'};
    format_fel_frame_order(&p, tiny, 3);
    assert(tiny[2] == '\0' && tiny[3] == 'z');
    format_fel_frame_order(&p, tiny, 0);
    assert(tiny[3] == 'z');
    for (int n = 16; n < 40; n++)
        trace_fel_frame_order(&p, 'M', 0, 0, n, n, n);
    format_fel_frame_order(&p, order, sizeof(order));
    assert(strstr(order, "last-diff=#12/o2/i0:12.500000>12.000000"));
    assert(!strstr(order, " R#12/")); // A difference survives ring rollover.

    info_logs = 0; wall_clock = 200000;
    p.fel_active_pts = 16.225; p.fel_active_input = 8; p.fel_active_output = -1;
    for (int n = 0; n < 20; n++)
        fel_api_end(&p, FEL_API_MEMORY, (struct fel_api_clock){1, 0});
    assert(info_logs == 4 && strstr(last_info, "op=memory pts=16.225000 input=8 output=-1"));
    wall_clock = 4000000;
    fel_api_end(&p, FEL_API_RECORD, (struct fel_api_clock){1, 0});
    assert(info_logs == 4); // Do not double-log aggregate and nested call.
    fel_api_end(&p, FEL_API_DISPATCH, (struct fel_api_clock){1, 0});
    assert(info_logs == 5 && strstr(last_info, "op=dispatch"));
    struct fel_api_clock start = fel_api_begin(&p);
    fel_api_end(&p, FEL_API_END, start);
    assert(info_logs == 5); // Normal short calls never emit per-frame logs.
    puts("PASS: bounded chronological frame-identity ring, safe truncation, diagnostic gating and slow-API rate limit");
}

static bool create_test_layout(struct aimagereader_vk_stable *p)
{
    VkSampler immutable = HANDLE(VkSampler, 1);
    VkDescriptorSetLayoutBinding bindings[] = {
        {.binding = 0, .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
         .descriptorCount = 1, .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
         .pImmutableSamplers = &immutable},
        {.binding = 1, .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
         .descriptorCount = 1, .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT},
    };
    VkDescriptorSetLayoutCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
        .bindingCount = 2, .pBindings = bindings,
    };
    return create_conversion_descriptor_layout(p, &info, p->sampler_descriptors);
}

static void test_push_capabilities_and_frames(void)
{
    struct aimagereader_vk_stable p;
    reset(&p, false);
    assert(create_test_layout(&p) && !p.push_descriptors && !property_calls && !last_layout_flags);
    reset(&p, true);
    test_vk.num_extensions = 0; // A callable symbol alone is not enabled support.
    assert(create_test_layout(&p) && !p.push_descriptors && !property_calls);
    assert(strstr(last_info, "extension-not-enabled"));
    for (int absent = 1; absent <= 3; absent++) {
        reset(&p, true); missing_entry = absent;
        assert(create_test_layout(&p) && !p.push_descriptors && !property_calls);
        assert(strstr(last_info, "entrypoint-unavailable"));
    }
    reset(&p, true); max_push = 4;
    assert(create_test_layout(&p) && !p.push_descriptors && !last_layout_flags);
    assert(strstr(last_info, "descriptor-limit"));
    reset(&p, true); fail_push_layout = true;
    assert(create_test_layout(&p) && !p.push_descriptors && layout_calls == 2 && !last_layout_flags);
    assert(strstr(last_info, "push-layout-failed"));
    reset(&p, true); fail_push_layout = fail_set_layout = true;
    assert(!create_test_layout(&p) && !p.push_descriptors && !p.descriptor_layout);

    reset(&p, true);
    assert(create_test_layout(&p) && p.push_descriptors);
    assert(last_layout_flags == VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR);
    assert(strstr(last_info, "mode=push reason=enabled max-push=32"));
    p.fel_profile = p.fel_profile_warm = true;
    for (unsigned n = 0; n < 1200; n++) {
        struct vk_input *input = get_input(&p, n % 12);
        struct vk_output *output = &p.outputs[n % 5];
        assert(prepare_conversion(&p, output, input, &desc, &full));
        assert(command(active(output))->flags == VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        complete_frame(output, input);
    }
    assert(push_calls == 1200 && end_calls == 1200 && !update_calls && !bind_calls);
    assert(!pool_calls && !descriptor_calls && command_calls == 60);
    assert(p.fel_api_wall[FEL_API_PUSH_DESCRIPTORS].count == 1200);
    assert(!p.fel_api_wall[FEL_API_DESCRIPTORS].count && !p.fel_api_wall[FEL_API_DESCRIPTOR].count);
    struct vk_input *input = get_input(&p, 0);
    struct vk_output *output = &p.outputs[0];
    assert(prepare_conversion(&p, output, input, &desc, &full));
    VkCommandBuffer cmd = active(output);
    output->pending = command(cmd)->pending = true; input->users = 1;
    assert(!prepare_conversion(&p, output, input, &desc, &full));
    aimagereader_vk_stable_buffer_removed(&p, input->buffer);
    assert(input->view && input->removed && !removed_views);
    output->pending = command(cmd)->pending = false; input->users = 0;
    purge_removed_inputs(&p);
    assert(!command(cmd)->executable && removed_views == 1);
    destroy_recording_cache(&p);
    assert(command_frees == 60 && !pool_frees);

    reset(&p, true);
    assert(create_test_layout(&p) && p.push_descriptors);
    input = get_input(&p, 0); input->initialized = true;
    output = &p.outputs[0]; output->written = true;
    fail_allocation = 3;
    assert(prepare_conversion(&p, output, input, &desc, &full));
    assert(p.recording_cache_disabled && !output->active_command);
    complete_frame(output, input);
    assert(push_calls == 1 && !update_calls && !descriptor_calls && !pool_calls);
    destroy_recording_cache(&p);
    puts("PASS: enabled-extension/entry/YCbCr limit gates and layout fallback; 1200 fresh full pushes, no descriptor sets, unchanged barriers/lifetime and command-allocation fallback");
}

int main(void)
{
    test_rotation_and_default();
    test_keys_pending_and_removal();
    test_descriptor_lifetimes();
    test_limits_and_fallback();
    test_api_measurements();
    test_latency_windows();
    test_bounded_diagnostics();
    test_push_capabilities_and_frames();
    return 0;
}
