// Compile the production probe bodies with deterministic kernel/Vulkan inputs.
// These tests verify observation and failure isolation, not television timing.
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <poll.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <unistd.h>
#ifndef RUSAGE_THREAD
#define RUSAGE_THREAD 1
#endif

#define MPMAX(a, b) ((a) > (b) ? (a) : (b))
#define MP_TIME_MS_TO_NS(v) ((v) * INT64_C(1000000))
#define MSGL_INFO 40
struct fel_api_clock { int64_t wall, cpu; };
struct fel_wait_snapshot {
    uint64_t runtime, runqueue, slices, voluntary, involuntary;
    int sched_error, usage_error, acquire;
};
struct fel_wait_sample {
    bool active;
    struct fel_api_clock clock;
    struct fel_wait_snapshot before;
    int64_t probe_ns;
};
typedef const struct test_vulkan {
    VkInstance instance;
    VkPhysicalDevice phys_device;
    PFN_vkGetInstanceProcAddr get_proc_addr;
    uint32_t api_version;
    const char *const *extensions;
    int num_extensions;
} *pl_vulkan;
struct aimagereader_vk_stable {
    void *log;
    bool android_fel, fel_profile, fel_profile_warm, push_descriptors;
    int64_t fel_wait_sample_at;
    int fel_sched_disabled_error, fel_acquire_fence;
    double fel_active_pts;
    int fel_active_input, fel_active_output;
    pl_vulkan vk;
};
static int fel_parse_schedstat(const char *, struct fel_wait_snapshot *);
static int fel_probe_acquire_fence(int);
static int64_t fel_wait_delta(uint64_t, uint64_t, bool);
static struct fel_wait_sample fel_wait_probe_begin(struct aimagereader_vk_stable *);
static void fel_wait_probe_end(struct aimagereader_vk_stable *, const struct fel_wait_sample *);
static void log_fel_descriptor_capabilities(struct aimagereader_vk_stable *);

static int64_t now_ns = INT64_C(1000000000), cpu_ns = INT64_C(1000000);
static bool info_enabled = true, oom, enumerate_available = true, advertise_push;
static int open_error, usage_error, poll_error, poll_result;
static short poll_events;
static unsigned opens, reads, closes, polls, usages, allocations;
static long voluntary, involuntary;
static const char *sched_text = "1000000 2000000 10\n";
static uint32_t extension_count = 1;
static VkResult enum_result = VK_SUCCESS;
static char message[2048];
static int64_t mp_time_ns(void) { return now_ns; }
static int64_t mp_fel_thread_cpu_ns(void) { return cpu_ns; }
static bool mp_msg_test(void *log, int level) { assert(level == 40); return info_enabled; }
static void mp_info(void *log, const char *fmt, ...)
{
    va_list args;
    va_start(args, fmt);
    vsnprintf(message, sizeof(message), fmt, args);
    va_end(args);
}
static pid_t fake_gettid(void) { return 123; }
static int fake_open(const char *path, int flags)
{
    assert(!strcmp(path, "/proc/self/task/123/schedstat"));
    assert(flags == (O_RDONLY | O_CLOEXEC));
    opens++;
    now_ns += 1000;
    if (open_error) { errno = open_error; return -1; }
    return 99;
}
static ssize_t fake_read(int fd, void *buffer, size_t size)
{
    assert(fd == 99 && size == 159);
    reads++;
    now_ns += 1000;
    size_t length = strlen(sched_text);
    if (length > size) length = size;
    memcpy(buffer, sched_text, length);
    return length;
}
static int fake_close(int fd) { assert(fd == 99); closes++; return 0; }
static int fake_poll(struct pollfd *fds, nfds_t count, int timeout)
{
    assert(count == 1 && timeout == 0 && fds->fd == 7 && fds->events == POLLIN);
    polls++;
    now_ns += 1000;
    if (poll_error) { errno = poll_error; return -1; }
    fds->revents = poll_events;
    return poll_result;
}
static int fake_getrusage(int who, struct rusage *usage)
{
    assert(who == RUSAGE_THREAD);
    usages++;
    now_ns += 1000;
    if (usage_error) { errno = usage_error; return -1; }
    memset(usage, 0, sizeof(*usage));
    usage->ru_nvcsw = voluntary;
    usage->ru_nivcsw = involuntary;
    return 0;
}
static void *fake_calloc(size_t count, size_t size)
{
    allocations++;
    assert(count <= 512);
    return oom ? NULL : calloc(count, size);
}
static VKAPI_ATTR VkResult VKAPI_CALL enumerate_extensions(
    VkPhysicalDevice dev, const char *layer, uint32_t *count, VkExtensionProperties *out)
{
    if (!out) { *count = extension_count; return VK_SUCCESS; }
    assert(*count >= 1);
    snprintf(out[0].extensionName, sizeof(out[0].extensionName), "%s",
             advertise_push ? VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME : "VK_OTHER");
    *count = 1;
    return enum_result;
}
static VKAPI_ATTR void VKAPI_CALL physical_properties(VkPhysicalDevice dev,
                                                       VkPhysicalDeviceProperties *props)
{
    props->apiVersion = VK_MAKE_VERSION(1, 1, 128);
    snprintf(props->deviceName, sizeof(props->deviceName), "GPU\nname");
}
static VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL get_proc(VkInstance instance, const char *name)
{
    if (!strcmp(name, "vkEnumerateDeviceExtensionProperties"))
        return enumerate_available ? (PFN_vkVoidFunction)enumerate_extensions : NULL;
    if (!strcmp(name, "vkGetPhysicalDeviceProperties"))
        return (PFN_vkVoidFunction)physical_properties;
    return NULL;
}
static struct aimagereader_vk_stable fresh(void)
{
    return (struct aimagereader_vk_stable){
        .android_fel = true, .fel_profile = true, .fel_profile_warm = true,
        .fel_acquire_fence = 7,
    };
}

int main(void)
{
    struct fel_wait_snapshot parsed = {0};
    assert(!fel_parse_schedstat(" 123 456 7\n", &parsed));
    assert(parsed.runtime == 123 && parsed.runqueue == 456 && parsed.slices == 7);
    assert(fel_parse_schedstat("0 0 0\n", &parsed) == ENODATA);
    assert(fel_parse_schedstat("-1 0 2", &parsed) == EINVAL);
    assert(fel_parse_schedstat("1 2", &parsed) == EINVAL);
    assert(fel_parse_schedstat("1 2 3 extra", &parsed) == EINVAL);
    assert(fel_parse_schedstat("18446744073709551616 0 1", &parsed) == EOVERFLOW);
    assert(fel_wait_delta(100, 90, true) == -1);
    assert(fel_wait_delta(0, UINT64_MAX, true) == -1);
    assert(fel_wait_delta(1, 2, false) == -1);
    assert(fel_wait_delta(1, 2, true) == 1);

    struct aimagereader_vk_stable p = fresh();
    errno = EBUSY;
    struct fel_wait_sample sample = fel_wait_probe_begin(&p);
    assert(sample.active && errno == EBUSY && opens == 1 && closes == 1);
    now_ns += 33000000;
    cpu_ns += 1000000;
    sched_text = "2000000 32000000 12\n";
    involuntary = 2;
    poll_result = 1;
    poll_events = POLLIN;
    fel_wait_probe_end(&p, &sample);
    assert(errno == EBUSY && opens == 2 && closes == 2 && polls == 2);
    assert(strstr(message, "wall-us=33000 thread-cpu-us=1000 runqueue-us=30000"));
    assert(strstr(message, "slices=2 voluntary=0 involuntary=2"));
    assert(strstr(message, "acquire=1/2 probe-us=8"));
    assert(!fel_wait_probe_begin(&p).active && opens == 2);

    // Active sleeping: low runqueue delay, voluntary switch, already-ready fd.
    now_ns += 3000000000;
    sample = fel_wait_probe_begin(&p);
    now_ns += 33000000; cpu_ns += 1000000;
    sched_text = "3000000 32010000 13\n";
    voluntary = 1;
    fel_wait_probe_end(&p, &sample);
    assert(strstr(message, "runqueue-us=10"));
    assert(strstr(message, "voluntary=1 involuntary=0"));
    assert(strstr(message, "acquire=2/2"));

    // Disabled paths perform no proc, rusage, fence or allocation operations.
    unsigned previous = opens + polls + usages + allocations;
    p = fresh(); p.android_fel = false; assert(!fel_wait_probe_begin(&p).active);
    p = fresh(); p.fel_profile = false; assert(!fel_wait_probe_begin(&p).active);
    p = fresh(); p.fel_profile_warm = false; assert(!fel_wait_probe_begin(&p).active);
    p = fresh(); info_enabled = false; assert(!fel_wait_probe_begin(&p).active);
    log_fel_descriptor_capabilities(&p);
    info_enabled = true;
    assert(previous == opens + polls + usages + allocations);

    // A denied interface stays unknown, is not reopened repeatedly, and never
    // affects playback/fence ownership. A missing usage counter is independent.
    p = fresh(); open_error = EACCES; usage_error = ENOSYS;
    previous = opens;
    sample = fel_wait_probe_begin(&p); now_ns += 35000000;
    fel_wait_probe_end(&p, &sample);
    assert(opens == previous + 1 && strstr(message, "runqueue-us=-1"));
    assert(strstr(message, "voluntary=-1 involuntary=-1"));
    now_ns += 3000000000; sample = fel_wait_probe_begin(&p);
    fel_wait_probe_end(&p, &sample); assert(opens == previous + 1);
    open_error = usage_error = 0;
    p = fresh(); sched_text = "0 0 0\n";
    sample = fel_wait_probe_begin(&p); fel_wait_probe_end(&p, &sample);
    assert(p.fel_sched_disabled_error == ENODATA && strstr(message, "runqueue-us=-1"));

    // Truncated proc input and regressing counters cannot become valid zeros.
    char oversized[200]; memset(oversized, '1', sizeof(oversized) - 1);
    oversized[sizeof(oversized) - 1] = 0;
    p = fresh(); sched_text = oversized;
    sample = fel_wait_probe_begin(&p); fel_wait_probe_end(&p, &sample);
    assert(strstr(message, "runqueue-us=-1"));
    p = fresh(); sched_text = "100 100 10";
    sample = fel_wait_probe_begin(&p); sched_text = "50 50 5";
    fel_wait_probe_end(&p, &sample); assert(strstr(message, "runqueue-us=-1"));
    now_ns = 1; assert(fel_wait_probe_begin(&p).active); // Clock discontinuity.

    previous = closes;
    assert(fel_probe_acquire_fence(-1) == 0);
    poll_events = POLLERR; assert(fel_probe_acquire_fence(7) == -EINVAL);
    poll_events = POLLNVAL; assert(fel_probe_acquire_fence(7) == -EINVAL);
    poll_events = POLLHUP; assert(fel_probe_acquire_fence(7) == -EIO);
    poll_error = EINTR; assert(fel_probe_acquire_fence(7) == -EINTR);
    assert(closes == previous); // Never close or consume the observed sync fd.

    struct test_vulkan vk = {.get_proc_addr = get_proc, .api_version = VK_MAKE_VERSION(1, 1, 0)};
    p = fresh(); p.vk = &vk;
    log_fel_descriptor_capabilities(&p);
    assert(strstr(message, "push-supported=0 push-enabled=0"));
    assert(strstr(message, "gpu=\"GPU?name\""));
    advertise_push = true; log_fel_descriptor_capabilities(&p);
    assert(strstr(message, "push-supported=1 push-enabled=0"));
    const char *enabled[] = {VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME};
    vk.extensions = enabled; vk.num_extensions = 1;
    log_fel_descriptor_capabilities(&p);
    assert(strstr(message, "push-supported=1 push-enabled=1"));
    enumerate_available = false; log_fel_descriptor_capabilities(&p);
    assert(strstr(message, "push-supported=-1"));
    enumerate_available = true; oom = true; log_fel_descriptor_capabilities(&p);
    assert(strstr(message, "push-supported=-1"));
    oom = false; extension_count = 513; previous = allocations;
    log_fel_descriptor_capabilities(&p);
    assert(strstr(message, "push-supported=-1") && allocations == previous);
    extension_count = 1; enum_result = VK_INCOMPLETE; advertise_push = false;
    log_fel_descriptor_capabilities(&p); assert(strstr(message, "push-supported=-1"));
    advertise_push = true; log_fel_descriptor_capabilities(&p);
    assert(strstr(message, "push-supported=1"));
    assert(reads == closes);
    puts("PASS: FEL wait sampling, disabled/denied/invalid counters, fence ownership and Vulkan capabilities");
    return 0;
}

#define gettid fake_gettid
#define open fake_open
#define read fake_read
#define close fake_close
#define poll fake_poll
#define getrusage fake_getrusage
#define calloc fake_calloc
