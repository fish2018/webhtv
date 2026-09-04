#include <jni.h>

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <libplacebo/config.h>
#include <libplacebo/vulkan.h>
#include <libplacebo/renderer.h>
#include <libdovi/rpu_parser.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cinttypes>
#include <cstring>
#include <deque>
#include <map>
#include <mutex>
#include <new>
#include <vector>

namespace {

constexpr int kMaxImages = 4;
constexpr size_t kMaxExpectedFrames = 256;
constexpr size_t kMaxPendingRpus = 256;
constexpr int64_t kTimestampMatchToleranceNs = 2'000'000;

constexpr jint kCapabilityImageReader = 1 << 0;
constexpr jint kCapabilityVulkan12 = 1 << 1;
constexpr jint kCapabilityAhbImport = 1 << 2;
constexpr jint kCapabilityYcbcrConversion = 1 << 3;
constexpr jint kCapabilityForeignQueue = 1 << 4;
constexpr jint kCapabilityLibplacebo375 = 1 << 5;
constexpr jint kCapabilityLibdovi = 1 << 6;

struct ExpectedFrame {
    int64_t imageTimestampNs;
    int64_t presentationTimeUs;
};

struct RpuMetadata {
    int64_t presentationTimeUs;
    pl_dovi_metadata dovi;
    bool hasMapping = false;
    bool hasColor = false;
    bool fullRange = true;
    bool hasSourceLuma = false;
    float sourceMinPq = 0.0f;
    float sourceMaxPq = 0.0f;
    bool updatesSceneLuma = false;
    bool hasSceneLuma = false;
    float sceneMaxPq = 0.0f;
    float sceneAvgPq = 0.0f;
};

struct Renderer {
    AImageReader *reader = nullptr;
    ANativeWindow *outputWindow = nullptr;
    pl_log log = nullptr;
    pl_vk_inst vkInstance = nullptr;
    pl_vulkan vulkan = nullptr;
    pl_swapchain swapchain = nullptr;
    pl_renderer renderer = nullptr;
    VkSurfaceKHR outputSurface = VK_NULL_HANDLE;
    int outputWidth = 0;
    int outputHeight = 0;
    std::mutex renderMutex;
    std::mutex callbackMutex;
    std::mutex expectedMutex;
    std::mutex rpuMutex;
    std::deque<ExpectedFrame> expectedFrames;
    std::atomic<int64_t> acquiredFrames{0};
    std::atomic<int64_t> ahbFrames{0};
    std::atomic<int64_t> sampledUsageFrames{0};
    std::atomic<int64_t> highDepthFrames{0};
    std::atomic<int64_t> matchedFrames{0};
    std::atomic<int64_t> unmatchedFrames{0};
    std::atomic<int64_t> expectedQueueDrops{0};
    std::atomic<int64_t> lastImageTimestampNs{0};
    std::atomic<int64_t> lastPresentationTimeUs{0};
    std::atomic<int64_t> lastAhbFormat{0};
    std::atomic<int64_t> parsedRpus{0};
    std::atomic<int64_t> malformedRpus{0};
    std::atomic<int64_t> rpuQueueDrops{0};
    std::atomic<int64_t> metadataLogs{0};
    std::atomic<int> inputFormatLogs{0};
    std::atomic<int64_t> renderedFrames{0};
    std::atomic<int64_t> renderFailures{0};
    std::atomic<int> colorPipelineLogs{0};
    std::deque<RpuMetadata> pendingRpus;
    bool hasLastDovi = false;
    pl_dovi_metadata lastDovi{};
    bool lastFullRange = true;
    bool hasSourceLuma = false;
    float sourceMinPq = 0.0f;
    float sourceMaxPq = 0.0f;
    bool hasSceneLuma = false;
    float sceneMaxPq = 0.0f;
    float sceneAvgPq = 0.0f;
};

void placeboLog(void *, enum pl_log_level level, const char *message) {
    int priority = ANDROID_LOG_INFO;
    if (level <= PL_LOG_ERR) priority = ANDROID_LOG_ERROR;
    else if (level == PL_LOG_WARN) priority = ANDROID_LOG_WARN;
    __android_log_print(priority, "ExoDv5", "%s", message == nullptr ? "" : message);
}

bool destroyVulkan(Renderer *renderer);
void resetDoviMetadata(pl_dovi_metadata *metadata);

bool ensureVulkan(Renderer *renderer) {
    if (renderer->swapchain != nullptr && renderer->renderer != nullptr) return true;
    if (renderer->outputWindow == nullptr) return false;
    std::lock_guard<std::mutex> renderLock(renderer->renderMutex);
    if (renderer->swapchain != nullptr && renderer->renderer != nullptr) {
        return true;
    }
    pl_log_params logParams{
            .log_cb = placeboLog,
            .log_priv = nullptr,
            .log_level = PL_LOG_ERR,
    };
    renderer->log = pl_log_create(PL_API_VER, &logParams);
    if (renderer->log == nullptr) return false;
    const char *instanceExtensions[] = {VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    pl_vk_inst_params instanceParams{};
    instanceParams.max_api_version = VK_API_VERSION_1_2;
    instanceParams.get_proc_addr = vkGetInstanceProcAddr;
    instanceParams.extensions = instanceExtensions;
    instanceParams.num_extensions = 1;
    renderer->vkInstance = pl_vk_inst_create(renderer->log, &instanceParams);
    if (renderer->vkInstance == nullptr) {
        pl_log_destroy(&renderer->log);
        return false;
    }
    auto createSurface = reinterpret_cast<PFN_vkCreateAndroidSurfaceKHR>(
            vkGetInstanceProcAddr(renderer->vkInstance->instance,
                                  "vkCreateAndroidSurfaceKHR"));
    if (createSurface == nullptr) {
        pl_vk_inst_destroy(&renderer->vkInstance);
        pl_log_destroy(&renderer->log);
        return false;
    }
    VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
    surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surfaceInfo.window = renderer->outputWindow;
    if (createSurface(renderer->vkInstance->instance, &surfaceInfo, nullptr,
                      &renderer->outputSurface) != VK_SUCCESS) {
        pl_vk_inst_destroy(&renderer->vkInstance);
        pl_log_destroy(&renderer->log);
        return false;
    }
    pl_vulkan_params vulkanParams = pl_vulkan_default_params;
    vulkanParams.instance = renderer->vkInstance->instance;
    vulkanParams.get_proc_addr = vkGetInstanceProcAddr;
    vulkanParams.surface = renderer->outputSurface;
    // AHardwareBuffer import is used for every decoded DV5 frame. The
    // capability probe enables these extensions explicitly; the real device
    // must use the same contract or vkGetAndroidHardwareBufferPropertiesANDROID
    // is unavailable and rendering fails before image import.
    const char *deviceExtensions[] = {
            VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
            VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME,
    };
    vulkanParams.extensions = deviceExtensions;
    vulkanParams.num_extensions = std::size(deviceExtensions);
    vulkanParams.async_transfer = false;
    vulkanParams.async_compute = false;
    vulkanParams.queue_count = 1;
    renderer->vulkan = pl_vulkan_create(renderer->log, &vulkanParams);
    if (renderer->vulkan == nullptr) {
        vkDestroySurfaceKHR(renderer->vkInstance->instance,
                            renderer->outputSurface, nullptr);
        renderer->outputSurface = VK_NULL_HANDLE;
        pl_vk_inst_destroy(&renderer->vkInstance);
        pl_log_destroy(&renderer->log);
        return false;
    }
    pl_vulkan_swapchain_params swapchainParams{};
    swapchainParams.surface = renderer->outputSurface;
    swapchainParams.present_mode = VK_PRESENT_MODE_FIFO_KHR;
    swapchainParams.swapchain_depth = 2;
    swapchainParams.color_bits = 10;
    renderer->swapchain = pl_vulkan_create_swapchain(
            renderer->vulkan, &swapchainParams);
    renderer->renderer = pl_renderer_create(renderer->log, renderer->vulkan->gpu);
    if (renderer->swapchain == nullptr || renderer->renderer == nullptr) {
        if (renderer->renderer != nullptr) pl_renderer_destroy(&renderer->renderer);
        if (renderer->swapchain != nullptr) pl_swapchain_destroy(&renderer->swapchain);
        pl_vulkan_destroy(&renderer->vulkan);
        vkDestroySurfaceKHR(renderer->vkInstance->instance,
                            renderer->outputSurface, nullptr);
        renderer->outputSurface = VK_NULL_HANDLE;
        pl_vk_inst_destroy(&renderer->vkInstance);
        pl_log_destroy(&renderer->log);
        return false;
    }
    renderer->outputWidth = 0;
    renderer->outputHeight = 0;
    return true;
}

uint32_t findMemoryType(pl_vulkan vulkan, uint32_t typeBits) {
    VkPhysicalDeviceMemoryProperties properties{};
    vkGetPhysicalDeviceMemoryProperties(vulkan->phys_device, &properties);
    for (uint32_t i = 0; i < properties.memoryTypeCount; i++) {
        if ((typeBits & (uint32_t{1} << i)) != 0
                && (properties.memoryTypes[i].propertyFlags
                    & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) return i;
    }
    for (uint32_t i = 0; i < properties.memoryTypeCount; i++) {
        if ((typeBits & (uint32_t{1} << i)) != 0) return i;
    }
    return UINT32_MAX;
}

bool renderImage(Renderer *renderer, AImage *image, int64_t ptsUs) {
    if (!ensureVulkan(renderer)) {
        __android_log_print(ANDROID_LOG_ERROR, "ExoDv5",
                            "render failed stage=ensure-vulkan ptsUs=%" PRId64,
                            ptsUs);
        return false;
    }
    AHardwareBuffer *buffer = nullptr;
    if (AImage_getHardwareBuffer(image, &buffer) != AMEDIA_OK || buffer == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "ExoDv5",
                            "render failed stage=get-ahb ptsUs=%" PRId64,
                            ptsUs);
        return false;
    }
    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(buffer, &desc);
    VkAndroidHardwareBufferFormatPropertiesANDROID formatProps{};
    formatProps.sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID;
    VkAndroidHardwareBufferPropertiesANDROID bufferProps{};
    bufferProps.sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID;
    bufferProps.pNext = &formatProps;
    auto getProperties = reinterpret_cast<PFN_vkGetAndroidHardwareBufferPropertiesANDROID>(
            vkGetDeviceProcAddr(renderer->vulkan->device,
                                "vkGetAndroidHardwareBufferPropertiesANDROID"));
    VkResult vkResult = getProperties == nullptr
            ? VK_ERROR_EXTENSION_NOT_PRESENT
            : getProperties(renderer->vulkan->device, buffer, &bufferProps);
    if (vkResult != VK_SUCCESS) {
        __android_log_print(ANDROID_LOG_ERROR, "ExoDv5",
                            "render failed stage=get-ahb-properties result=%d "
                            "ptsUs=%" PRId64 " ahbFormat=%u usage=0x%" PRIx64,
                            vkResult, ptsUs, desc.format, desc.usage);
        return false;
    }
    if (formatProps.format == VK_FORMAT_UNDEFINED && formatProps.externalFormat == 0) {
        __android_log_print(ANDROID_LOG_ERROR, "ExoDv5",
                            "render failed stage=missing-vulkan-format ptsUs=%" PRId64
                            " ahbFormat=%u usage=0x%" PRIx64,
                            ptsUs, desc.format, desc.usage);
        return false;
    }
    const int inputFormatLog = renderer->inputFormatLogs.fetch_add(
            1, std::memory_order_relaxed);
    if (inputFormatLog < 2) {
        __android_log_print(
                ANDROID_LOG_INFO, "ExoDv5",
                "input format ahb=%u usage=0x%" PRIx64
                " vkFormat=%d external=%" PRIu64
                " model=%d range=%d components=%d/%d/%d/%d"
                " chroma=%d/%d features=0x%" PRIx64
                " sourceMap=%d/%d/%d",
                desc.format, desc.usage, formatProps.format,
                formatProps.externalFormat,
                static_cast<int>(formatProps.suggestedYcbcrModel),
                static_cast<int>(formatProps.suggestedYcbcrRange),
                static_cast<int>(formatProps.samplerYcbcrConversionComponents.r),
                static_cast<int>(formatProps.samplerYcbcrConversionComponents.g),
                static_cast<int>(formatProps.samplerYcbcrConversionComponents.b),
                static_cast<int>(formatProps.samplerYcbcrConversionComponents.a),
                static_cast<int>(formatProps.suggestedXChromaOffset),
                static_cast<int>(formatProps.suggestedYChromaOffset),
                static_cast<uint64_t>(formatProps.formatFeatures),
                PL_CHANNEL_CR, PL_CHANNEL_Y, PL_CHANNEL_CB);
    }
    VkExternalFormatANDROID externalFormat{
            .sType = VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID,
            .pNext = nullptr,
            .externalFormat = formatProps.externalFormat,
    };
    VkExternalMemoryImageCreateInfo externalMemory{
            .sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
            .pNext = formatProps.externalFormat != 0 ? &externalFormat : nullptr,
            .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID,
    };
    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.pNext = &externalMemory;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.format = formatProps.externalFormat != 0
            ? VK_FORMAT_UNDEFINED : formatProps.format;
    imageInfo.extent = {desc.width, desc.height, 1};
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = VK_IMAGE_USAGE_SAMPLED_BIT;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    VkImage vkImage = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    pl_tex texture = nullptr;
    bool frameStarted = false;
    bool submitted = false;
    pl_swapchain_frame swapFrame{};
    pl_frame target{};
    pl_frame source{};
    pl_vulkan_ycbcr_params ycbcr{};
    pl_vulkan_wrap_params wrapParams{};
    pl_vulkan_release_params releaseParams{};
    pl_vulkan_hold_params holdParams{};
    VkSemaphore releaseSemaphore = VK_NULL_HANDLE;
    pl_vulkan_sem_params semaphoreParams{};
    VkImportAndroidHardwareBufferInfoANDROID importInfo{};
    VkMemoryDedicatedAllocateInfo dedicatedInfo{};
    VkMemoryAllocateInfo allocInfo{};
    uint32_t memoryType = UINT32_MAX;
    int width = static_cast<int>(desc.width);
    int height = static_cast<int>(desc.height);
    bool rendered = false;
    bool imageReleased = false;
    bool imageHeld = false;
    int colorLog = 0;
    size_t pendingRpuCount = 0;
    const char *failureStage = "vk-create-image";
    vkResult = vkCreateImage(renderer->vulkan->device, &imageInfo, nullptr, &vkImage);
    if (vkResult != VK_SUCCESS) goto cleanup;
    failureStage = "find-memory-type";
    memoryType = findMemoryType(renderer->vulkan, bufferProps.memoryTypeBits);
    if (memoryType == UINT32_MAX) goto cleanup;
    importInfo.sType = VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    importInfo.buffer = buffer;
    dedicatedInfo.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    dedicatedInfo.pNext = &importInfo;
    dedicatedInfo.image = vkImage;
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.pNext = &dedicatedInfo;
    allocInfo.allocationSize = bufferProps.allocationSize;
    allocInfo.memoryTypeIndex = memoryType;
    failureStage = "vk-allocate-memory";
    vkResult = vkAllocateMemory(renderer->vulkan->device, &allocInfo, nullptr, &memory);
    if (vkResult != VK_SUCCESS) goto cleanup;
    failureStage = "vk-bind-image-memory";
    vkResult = vkBindImageMemory(renderer->vulkan->device, vkImage, memory, 0);
    if (vkResult != VK_SUCCESS) goto cleanup;
    failureStage = "sampled-format-feature";
    if ((formatProps.formatFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) == 0) {
        goto cleanup;
    }
    failureStage = "create-release-semaphore";
    semaphoreParams.type = VK_SEMAPHORE_TYPE_BINARY;
    releaseSemaphore = pl_vulkan_sem_create(
            renderer->vulkan->gpu, &semaphoreParams);
    if (releaseSemaphore == VK_NULL_HANDLE) goto cleanup;
    ycbcr.external_format = formatProps.externalFormat;
    ycbcr.components = formatProps.samplerYcbcrConversionComponents;
    ycbcr.model = VK_SAMPLER_YCBCR_MODEL_CONVERSION_RGB_IDENTITY;
    ycbcr.range = VK_SAMPLER_YCBCR_RANGE_ITU_FULL;
    ycbcr.x_chroma_offset = formatProps.suggestedXChromaOffset;
    ycbcr.y_chroma_offset = formatProps.suggestedYChromaOffset;
    ycbcr.chroma_filter =
            (formatProps.formatFeatures
             & VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_LINEAR_FILTER_BIT)
                    != 0
            ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
    ycbcr.separate_reconstruction_filter =
            (formatProps.formatFeatures
             & VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_SEPARATE_RECONSTRUCTION_FILTER_BIT)
                    != 0;
    ycbcr.sample_depth = 10;
    wrapParams.image = vkImage;
    wrapParams.width = static_cast<int>(desc.width);
    wrapParams.height = static_cast<int>(desc.height);
    wrapParams.format = formatProps.externalFormat != 0
            ? VK_FORMAT_UNDEFINED : formatProps.format;
    wrapParams.usage = VK_IMAGE_USAGE_SAMPLED_BIT;
    wrapParams.ycbcr = &ycbcr;
    failureStage = "pl-vulkan-wrap";
    texture = pl_vulkan_wrap(renderer->vulkan->gpu, &wrapParams);
    if (texture == nullptr) goto cleanup;
    releaseParams.tex = texture;
    releaseParams.layout = VK_IMAGE_LAYOUT_GENERAL;
    releaseParams.qf = VK_QUEUE_FAMILY_FOREIGN_EXT;
    pl_vulkan_release_ex(renderer->vulkan->gpu, &releaseParams);
    imageReleased = true;
    {
        std::lock_guard<std::mutex> rpuLock(renderer->rpuMutex);
        while (!renderer->pendingRpus.empty()
                && renderer->pendingRpus.front().presentationTimeUs <= ptsUs) {
            const RpuMetadata &metadata = renderer->pendingRpus.front();
            if (!renderer->hasLastDovi && !metadata.hasMapping) {
                renderer->pendingRpus.pop_front();
                continue;
            }
            if (!renderer->hasLastDovi) {
                resetDoviMetadata(&renderer->lastDovi);
                // Profile 5 uses the fixed IPT-PQ conversion below when an
                // RPU carries no explicit (or carries compressed) DM block.
                renderer->lastDovi.nonlinear.m[0][0] = 8192.0f / 8192.0f;
                renderer->lastDovi.nonlinear.m[0][1] = 799.0f / 8192.0f;
                renderer->lastDovi.nonlinear.m[0][2] = 1681.0f / 8192.0f;
                renderer->lastDovi.nonlinear.m[1][0] = 8192.0f / 8192.0f;
                renderer->lastDovi.nonlinear.m[1][1] = -933.0f / 8192.0f;
                renderer->lastDovi.nonlinear.m[1][2] = 1091.0f / 8192.0f;
                renderer->lastDovi.nonlinear.m[2][0] = 8192.0f / 8192.0f;
                renderer->lastDovi.nonlinear.m[2][1] = 267.0f / 8192.0f;
                renderer->lastDovi.nonlinear.m[2][2] = -5545.0f / 8192.0f;
                renderer->lastDovi.linear.m[0][0] = 17081.0f / 16384.0f;
                renderer->lastDovi.linear.m[0][1] = -349.0f / 16384.0f;
                renderer->lastDovi.linear.m[0][2] = -349.0f / 16384.0f;
                renderer->lastDovi.linear.m[1][0] = -349.0f / 16384.0f;
                renderer->lastDovi.linear.m[1][1] = 17081.0f / 16384.0f;
                renderer->lastDovi.linear.m[1][2] = -349.0f / 16384.0f;
                renderer->lastDovi.linear.m[2][0] = -349.0f / 16384.0f;
                renderer->lastDovi.linear.m[2][1] = -349.0f / 16384.0f;
                renderer->lastDovi.linear.m[2][2] = 17081.0f / 16384.0f;
                renderer->lastDovi.nonlinear_offset[0] = 0.0f;
                renderer->lastDovi.nonlinear_offset[1] = 0.5f;
                renderer->lastDovi.nonlinear_offset[2] = 0.5f;
                renderer->hasLastDovi = true;
                renderer->hasSourceLuma = true;
                renderer->sourceMinPq = 62.0f / 4095.0f;
                renderer->sourceMaxPq = 3696.0f / 4095.0f;
            }
            if (metadata.hasMapping) {
                for (int c = 0; c < 3; c++) {
                    renderer->lastDovi.comp[c] = metadata.dovi.comp[c];
                }
            }
            if (metadata.hasColor) {
                memcpy(renderer->lastDovi.nonlinear_offset,
                       metadata.dovi.nonlinear_offset,
                       sizeof(renderer->lastDovi.nonlinear_offset));
                renderer->lastDovi.nonlinear = metadata.dovi.nonlinear;
                renderer->lastDovi.linear = metadata.dovi.linear;
            }
            renderer->lastFullRange = metadata.fullRange;
            if (metadata.hasSourceLuma) {
                renderer->hasSourceLuma = true;
                renderer->sourceMinPq = metadata.sourceMinPq;
                renderer->sourceMaxPq = metadata.sourceMaxPq;
            }
            if (metadata.updatesSceneLuma) {
                renderer->hasSceneLuma = metadata.hasSceneLuma;
                renderer->sceneMaxPq = metadata.sceneMaxPq;
                renderer->sceneAvgPq = metadata.sceneAvgPq;
            }
            renderer->pendingRpus.pop_front();
        }
        pendingRpuCount = renderer->pendingRpus.size();
    }
    failureStage = "missing-rpu";
    if (!renderer->hasLastDovi) goto cleanup;
    source.num_planes = 1;
    source.planes[0].texture = texture;
    source.planes[0].components = 3;
    source.planes[0].component_mapping[0] = PL_CHANNEL_CR;
    source.planes[0].component_mapping[1] = PL_CHANNEL_Y;
    source.planes[0].component_mapping[2] = PL_CHANNEL_CB;
    source.repr.sys = PL_COLOR_SYSTEM_DOLBYVISION;
    source.repr.levels = renderer->lastFullRange
            ? PL_COLOR_LEVELS_FULL : PL_COLOR_LEVELS_LIMITED;
    source.repr.bits.sample_depth = 10;
    source.repr.bits.color_depth = 10;
    source.repr.dovi = &renderer->lastDovi;
    source.color.primaries = PL_COLOR_PRIM_BT_2020;
    source.color.transfer = PL_COLOR_TRC_PQ;
    if (renderer->hasSourceLuma) {
        source.color.hdr.min_luma = pl_hdr_rescale(
                PL_HDR_PQ, PL_HDR_NITS, renderer->sourceMinPq);
        source.color.hdr.max_luma = pl_hdr_rescale(
                PL_HDR_PQ, PL_HDR_NITS, renderer->sourceMaxPq);
    }
    if (renderer->hasSceneLuma) {
        source.color.hdr.max_pq_y = renderer->sceneMaxPq;
        source.color.hdr.avg_pq_y = renderer->sceneAvgPq;
    }
    source.crop = {0, 0, static_cast<float>(desc.width), static_cast<float>(desc.height)};
    // Keep this compatibility path on an explicit SDR swapchain. Advertising
    // the 4000-nit source as the target bypasses libplacebo output tone mapping
    // and leaves the result dependent on Android's child-surface HDR handling.
    // Apply the per-frame DV reshape above, then tone-map into the stable
    // sRGB/BT.709 output contract here.
    pl_swapchain_colorspace_hint(renderer->swapchain, &pl_color_space_srgb);
    failureStage = "swapchain-resize";
    if (!pl_swapchain_resize(renderer->swapchain, &width, &height)) goto cleanup;
    failureStage = "swapchain-start-frame";
    if (!pl_swapchain_start_frame(renderer->swapchain, &swapFrame)) goto cleanup;
    frameStarted = true;
    pl_frame_from_swapchain(&target, &swapFrame);
    colorLog = renderer->colorPipelineLogs.fetch_add(
            1, std::memory_order_relaxed);
    if (colorLog < 4) {
        __android_log_print(
                ANDROID_LOG_INFO, "ExoDv5",
                "color pipeline source=%d/%d %.3f/%.1f target=%d/%d %.3f/%.1f bits=%d",
                source.color.primaries, source.color.transfer,
                source.color.hdr.min_luma, source.color.hdr.max_luma,
                target.color.primaries, target.color.transfer,
                target.color.hdr.min_luma, target.color.hdr.max_luma,
                target.repr.bits.color_depth);
    }
    failureStage = "pl-render-image";
    rendered = pl_render_image(renderer->renderer, &source, &target, nullptr);
    if (!rendered) goto cleanup;
    failureStage = "swapchain-submit-frame";
    submitted = pl_swapchain_submit_frame(renderer->swapchain);
    frameStarted = false;
    if (!submitted) goto cleanup;
    if (submitted) pl_swapchain_swap_buffers(renderer->swapchain);
    pl_gpu_finish(renderer->vulkan->gpu);
    holdParams.tex = texture;
    holdParams.layout = VK_IMAGE_LAYOUT_GENERAL;
    holdParams.qf = VK_QUEUE_FAMILY_FOREIGN_EXT;
    holdParams.semaphore = pl_vulkan_sem{.sem = releaseSemaphore, .value = 0};
    failureStage = "pl-vulkan-hold";
    if (!pl_vulkan_hold_ex(renderer->vulkan->gpu, &holdParams)) goto cleanup;
    imageHeld = true;
    // The semaphore is consumed by the hold operation. Wait for the queue
    // transition before returning the AImage to its BufferQueue.
    pl_gpu_finish(renderer->vulkan->gpu);
    pl_vulkan_sem_destroy(renderer->vulkan->gpu, &releaseSemaphore);
    pl_tex_destroy(renderer->vulkan->gpu, &texture);
    vkDestroyImage(renderer->vulkan->device, vkImage, nullptr);
    vkFreeMemory(renderer->vulkan->device, memory, nullptr);
    return submitted;

cleanup:
    __android_log_print(
            ANDROID_LOG_ERROR, "ExoDv5",
            "render failed stage=%s result=%d ptsUs=%" PRId64
            " ahb=%ux%u format=%u usage=0x%" PRIx64
            " vkFormat=%d externalFormat=%" PRIu64
            " formatFeatures=0x%" PRIx64
            " hasRpu=%d parsedRpu=%" PRId64
            " malformedRpu=%" PRId64 " pendingRpu=%zu",
            failureStage, vkResult, ptsUs, desc.width, desc.height,
            desc.format, desc.usage, formatProps.format,
            formatProps.externalFormat,
            static_cast<uint64_t>(formatProps.formatFeatures),
            renderer->hasLastDovi ? 1 : 0,
            renderer->parsedRpus.load(std::memory_order_relaxed),
            renderer->malformedRpus.load(std::memory_order_relaxed),
            pendingRpuCount);
    if (frameStarted) {
        pl_swapchain_submit_frame(renderer->swapchain);
        pl_gpu_finish(renderer->vulkan->gpu);
    }
    if (texture != nullptr) {
        if (imageReleased && !imageHeld) {
            // Reclaim ownership before destroying a wrapper that was handed
            // to libplacebo. The normal path always succeeds here; retaining
            // the explicit attempt keeps failure cleanup deterministic.
            holdParams.tex = texture;
            holdParams.layout = VK_IMAGE_LAYOUT_GENERAL;
            holdParams.qf = VK_QUEUE_FAMILY_FOREIGN_EXT;
            holdParams.semaphore = pl_vulkan_sem{
                    .sem = releaseSemaphore,
                    .value = 0,
            };
            if (pl_vulkan_hold_ex(renderer->vulkan->gpu, &holdParams)) {
                imageHeld = true;
                pl_gpu_finish(renderer->vulkan->gpu);
            }
        }
        pl_tex_destroy(renderer->vulkan->gpu, &texture);
    }
    if (releaseSemaphore != VK_NULL_HANDLE) {
        pl_gpu_finish(renderer->vulkan->gpu);
        pl_vulkan_sem_destroy(renderer->vulkan->gpu, &releaseSemaphore);
    }
    if (vkImage != VK_NULL_HANDLE) vkDestroyImage(renderer->vulkan->device, vkImage, nullptr);
    if (memory != VK_NULL_HANDLE) vkFreeMemory(renderer->vulkan->device, memory, nullptr);
    return false;
}

bool destroyVulkan(Renderer *renderer) {
    std::lock_guard<std::mutex> lock(renderer->renderMutex);
    if (renderer->vulkan != nullptr) pl_gpu_finish(renderer->vulkan->gpu);
    if (renderer->renderer != nullptr) pl_renderer_destroy(&renderer->renderer);
    if (renderer->swapchain != nullptr) pl_swapchain_destroy(&renderer->swapchain);
    if (renderer->vulkan != nullptr) pl_vulkan_destroy(&renderer->vulkan);
    if (renderer->vkInstance != nullptr && renderer->outputSurface != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(renderer->vkInstance->instance,
                            renderer->outputSurface, nullptr);
    }
    renderer->outputSurface = VK_NULL_HANDLE;
    if (renderer->vkInstance != nullptr) pl_vk_inst_destroy(&renderer->vkInstance);
    if (renderer->log != nullptr) pl_log_destroy(&renderer->log);
    return true;
}

void resetDoviMetadata(pl_dovi_metadata *metadata) {
    memset(metadata, 0, sizeof(*metadata));
    metadata->nonlinear = pl_matrix3x3_identity;
    metadata->linear = pl_matrix3x3_identity;
}

float fixedCoefficient(int64_t integer, uint64_t fraction, uint64_t denom) {
    const double scale = denom >= 63
            ? 9223372036854775808.0
            : static_cast<double>(uint64_t{1} << denom);
    return static_cast<float>(static_cast<double>(integer)
            + static_cast<double>(fraction) / scale);
}

void mapRpuCurve(pl_dovi_metadata::pl_reshape_data *dst,
                 const DoviReshapingCurve &src,
                 const DoviRpuDataHeader &header) {
    const size_t pivotCount = std::min<size_t>(9, src.pivots.len);
    dst->num_pivots = static_cast<uint8_t>(pivotCount);
    uint64_t blBits = std::min<uint64_t>(23, header.bl_bit_depth_minus8 + 8);
    const uint64_t maxPivot = (uint64_t{1} << blBits) - 1;
    const float pivotScale = 1.0f / static_cast<float>(maxPivot);
    uint64_t pivot = 0;
    for (size_t i = 0; i < pivotCount; i++) {
        // RPU pred_pivot_value entries are delta-coded. libdovi exposes the
        // encoded values, while libplacebo expects absolute normalized pivot
        // positions, matching FFmpeg's DOVIContext mapping.
        pivot = std::min(maxPivot, pivot + src.pivots.data[i]);
        dst->pivots[i] = pivotScale * static_cast<float>(pivot);
    }
    const size_t pieceCount = pivotCount > 0 ? pivotCount - 1 : 0;
    for (size_t i = 0; i < pieceCount && i < 8; i++) {
        dst->method[i] = src.mapping_idc;
        if (src.mapping_idc == 0 && src.polynomial != nullptr) {
            const DoviPolynomialCurve *poly = src.polynomial;
            if (i < poly->poly_coef_int.len && poly->poly_coef_int.list != nullptr
                    && poly->poly_coef_int.list[i] != nullptr) {
                const DoviI64Data *coeff = poly->poly_coef_int.list[i];
                for (size_t k = 0; k < 3 && k < coeff->len; k++) {
                    uint64_t fraction = 0;
                    if (i < poly->poly_coef.len && poly->poly_coef.list != nullptr
                            && poly->poly_coef.list[i] != nullptr
                            && k < poly->poly_coef.list[i]->len) {
                        fraction = poly->poly_coef.list[i]->data[k];
                    }
                    dst->poly_coeffs[i][k] = fixedCoefficient(
                            coeff->data[k], fraction,
                            header.coefficient_log2_denom);
                }
            }
        } else if (src.mapping_idc == 1 && src.mmr != nullptr) {
            const DoviMMRCurve *mmr = src.mmr;
            if (i < mmr->mmr_order_minus1.len
                    && mmr->mmr_order_minus1.data != nullptr) {
                dst->mmr_order[i] = static_cast<uint8_t>(
                        std::min<uint64_t>(3, mmr->mmr_order_minus1.data[i] + 1));
            }
            if (i < mmr->mmr_constant_int.len) {
                const uint64_t fraction = i < mmr->mmr_constant.len
                        ? mmr->mmr_constant.data[i] : 0;
                dst->mmr_constant[i] = fixedCoefficient(
                        mmr->mmr_constant_int.data[i], fraction,
                        header.coefficient_log2_denom);
            }
            if (i < mmr->mmr_coef_int.len && mmr->mmr_coef_int.list != nullptr
                    && mmr->mmr_coef_int.list[i] != nullptr) {
                const DoviI64Data2D *rows = mmr->mmr_coef_int.list[i];
                for (size_t row = 0; row < 3 && row < rows->len; row++) {
                    const DoviI64Data *coeff = rows->list[row];
                    if (coeff == nullptr) continue;
                    for (size_t k = 0; k < 7 && k < coeff->len; k++) {
                        uint64_t fraction = 0;
                        if (i < mmr->mmr_coef.len && mmr->mmr_coef.list != nullptr
                                && mmr->mmr_coef.list[i] != nullptr
                                && row < mmr->mmr_coef.list[i]->len
                                && mmr->mmr_coef.list[i]->list[row] != nullptr
                                && k < mmr->mmr_coef.list[i]->list[row]->len) {
                            fraction = mmr->mmr_coef.list[i]->list[row]->data[k];
                        }
                        dst->mmr_coeffs[i][row][k] = fixedCoefficient(
                                coeff->data[k], fraction,
                                header.coefficient_log2_denom);
                    }
                }
            }
        }
    }
}

bool mapRpuMetadata(const DoviRpuDataHeader *header,
                    const DoviRpuDataMapping *mapping,
                    const DoviVdrDmData *dm,
                    pl_dovi_metadata *out) {
    if (header == nullptr || out == nullptr
            || header->rpu_type != 2) return false;
    resetDoviMetadata(out);
    if (mapping != nullptr) {
        for (int c = 0; c < 3; c++) {
            mapRpuCurve(&out->comp[c], mapping->curves[c], *header);
        }
    }
    // Compressed DM carries only dynamic extension blocks. Its static color
    // fields are intentionally zero and must inherit the previous DM state.
    if (dm != nullptr && !dm->compressed) {
        const float yccScale = 1.0f / 8192.0f;
        const float lmsScale = 1.0f / 16384.0f;
        const int16_t ycc[] = {
                dm->ycc_to_rgb_coef0, dm->ycc_to_rgb_coef1, dm->ycc_to_rgb_coef2,
                dm->ycc_to_rgb_coef3, dm->ycc_to_rgb_coef4, dm->ycc_to_rgb_coef5,
                dm->ycc_to_rgb_coef6, dm->ycc_to_rgb_coef7, dm->ycc_to_rgb_coef8};
        const int16_t lms[] = {
                dm->rgb_to_lms_coef0, dm->rgb_to_lms_coef1, dm->rgb_to_lms_coef2,
                dm->rgb_to_lms_coef3, dm->rgb_to_lms_coef4, dm->rgb_to_lms_coef5,
                dm->rgb_to_lms_coef6, dm->rgb_to_lms_coef7, dm->rgb_to_lms_coef8};
        for (int i = 0; i < 9; i++) {
            out->nonlinear.m[i / 3][i % 3] = yccScale * ycc[i];
            out->linear.m[i / 3][i % 3] = lmsScale * lms[i];
        }
        out->nonlinear_offset[0] = dm->ycc_to_rgb_offset0 / 268435456.0f;
        out->nonlinear_offset[1] = dm->ycc_to_rgb_offset1 / 268435456.0f;
        out->nonlinear_offset[2] = dm->ycc_to_rgb_offset2 / 268435456.0f;
    }
    if (mapping != nullptr && mapping->nlq != nullptr && !header->disable_residual_flag
            && mapping->nlq_method_idc == 0) {
        out->nlq_active = false; // Profile 5 is single-layer; never compose FEL.
    }
    return mapping != nullptr || dm != nullptr || header->use_prev_vdr_rpu_flag;
}

bool hasExtension(const std::vector<VkExtensionProperties> &extensions,
                  const char *name) {
    return std::any_of(extensions.begin(), extensions.end(),
                       [name](const VkExtensionProperties &extension) {
                           return strcmp(extension.extensionName, name) == 0;
                       });
}

bool isHighDepthFormat(uint32_t format) {
    switch (format) {
        case AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT:
        case AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM:
#ifdef AHARDWAREBUFFER_FORMAT_YCbCr_P010
        case AHARDWAREBUFFER_FORMAT_YCbCr_P010:
#endif
#ifdef AHARDWAREBUFFER_FORMAT_YCbCr_P210
        case AHARDWAREBUFFER_FORMAT_YCbCr_P210:
#endif
            return true;
        default:
            return false;
    }
}

jint probeVulkanDevice(VkInstance instance, VkPhysicalDevice device) {
    VkPhysicalDeviceProperties properties{};
    vkGetPhysicalDeviceProperties(device, &properties);
    if (VK_VERSION_MAJOR(properties.apiVersion) < 1
            || (VK_VERSION_MAJOR(properties.apiVersion) == 1
            && VK_VERSION_MINOR(properties.apiVersion) < 2)) {
        return 0;
    }

    uint32_t extensionCount = 0;
    if (vkEnumerateDeviceExtensionProperties(
                device, nullptr, &extensionCount, nullptr) != VK_SUCCESS) {
        return 0;
    }
    std::vector<VkExtensionProperties> extensions(extensionCount);
    if (vkEnumerateDeviceExtensionProperties(
                device, nullptr, &extensionCount, extensions.data()) != VK_SUCCESS) {
        return 0;
    }
    if (!hasExtension(extensions,
            VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME)
            || !hasExtension(extensions,
            VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME)) {
        return kCapabilityVulkan12;
    }

    auto getFeatures2 = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(
            vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceFeatures2"));
    if (getFeatures2 == nullptr) return kCapabilityVulkan12;
    VkPhysicalDeviceVulkan11Features features11{};
    features11.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES;
    VkPhysicalDeviceFeatures2 features2{};
    features2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
    features2.pNext = &features11;
    getFeatures2(device, &features2);
    if (features11.samplerYcbcrConversion != VK_TRUE) {
        return kCapabilityVulkan12 | kCapabilityAhbImport
                | kCapabilityForeignQueue;
    }

    uint32_t queueCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(device, &queueCount, nullptr);
    std::vector<VkQueueFamilyProperties> queues(queueCount);
    vkGetPhysicalDeviceQueueFamilyProperties(device, &queueCount, queues.data());
    uint32_t queueFamily = UINT32_MAX;
    for (uint32_t index = 0; index < queueCount; index++) {
        if (queues[index].queueCount > 0
                && (queues[index].queueFlags
                & (VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT)) != 0) {
            queueFamily = index;
            break;
        }
    }
    if (queueFamily == UINT32_MAX) return kCapabilityVulkan12;

    float priority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo{};
    queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueInfo.queueFamilyIndex = queueFamily;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &priority;
    const char *enabledExtensions[] = {
            VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
            VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME,
    };
    VkPhysicalDeviceVulkan11Features enabledFeatures11{};
    enabledFeatures11.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES;
    enabledFeatures11.samplerYcbcrConversion = VK_TRUE;
    VkDeviceCreateInfo deviceInfo{};
    deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceInfo.pNext = &enabledFeatures11;
    deviceInfo.queueCreateInfoCount = 1;
    deviceInfo.pQueueCreateInfos = &queueInfo;
    deviceInfo.enabledExtensionCount = std::size(enabledExtensions);
    deviceInfo.ppEnabledExtensionNames = enabledExtensions;
    VkDevice logicalDevice = VK_NULL_HANDLE;
    if (vkCreateDevice(device, &deviceInfo, nullptr, &logicalDevice) != VK_SUCCESS) {
        return kCapabilityVulkan12;
    }
    auto getAhbProperties = reinterpret_cast<
            PFN_vkGetAndroidHardwareBufferPropertiesANDROID>(
            vkGetDeviceProcAddr(
                    logicalDevice,
                    "vkGetAndroidHardwareBufferPropertiesANDROID"));
    vkDestroyDevice(logicalDevice, nullptr);
    return getAhbProperties == nullptr ? kCapabilityVulkan12
            : kCapabilityVulkan12 | kCapabilityAhbImport
            | kCapabilityYcbcrConversion | kCapabilityForeignQueue;
}

jint probeVulkan() {
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "WebHTV Exo DV5 probe";
    appInfo.applicationVersion = 1;
    appInfo.pEngineName = "WebHTV";
    appInfo.engineVersion = 1;
    appInfo.apiVersion = VK_API_VERSION_1_2;
    VkInstanceCreateInfo instanceInfo{};
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &appInfo;
    VkInstance instance = VK_NULL_HANDLE;
    if (vkCreateInstance(&instanceInfo, nullptr, &instance) != VK_SUCCESS) return 0;

    jint result = 0;
    uint32_t deviceCount = 0;
    if (vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr) == VK_SUCCESS
            && deviceCount > 0) {
        std::vector<VkPhysicalDevice> devices(deviceCount);
        if (vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data()) == VK_SUCCESS) {
            for (VkPhysicalDevice device : devices) {
                jint deviceResult = probeVulkanDevice(instance, device);
                if (deviceResult == (kCapabilityVulkan12
                        | kCapabilityAhbImport
                        | kCapabilityYcbcrConversion
                        | kCapabilityForeignQueue)) {
                    result = deviceResult;
                    break;
                }
            }
        }
    }
    vkDestroyInstance(instance, nullptr);
    return result;
}

bool probeImageReader(JNIEnv *env) {
    AImageReader *reader = nullptr;
    media_status_t status = AImageReader_newWithUsage(
            16,
            16,
            AIMAGE_FORMAT_PRIVATE,
            AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE,
            2,
            &reader);
    if (status != AMEDIA_OK || reader == nullptr) return false;
    ANativeWindow *window = nullptr;
    status = AImageReader_getWindow(reader, &window);
    jobject surface = status == AMEDIA_OK && window != nullptr
            ? ANativeWindow_toSurface(env, window) : nullptr;
    bool available = surface != nullptr;
    if (surface != nullptr) env->DeleteLocalRef(surface);
    AImageReader_delete(reader);
    return available;
}

bool matchTimestamp(Renderer *renderer, int64_t timestampNs,
                    int64_t *presentationTimeUs) {
    std::lock_guard<std::mutex> lock(renderer->expectedMutex);
    auto match = renderer->expectedFrames.end();
    int64_t bestDifferenceNs = kTimestampMatchToleranceNs + 1;
    for (auto frame = renderer->expectedFrames.begin();
         frame != renderer->expectedFrames.end(); ++frame) {
        const int64_t differenceNs = frame->imageTimestampNs >= timestampNs
                ? frame->imageTimestampNs - timestampNs
                : timestampNs - frame->imageTimestampNs;
        if (differenceNs < bestDifferenceNs) {
            bestDifferenceNs = differenceNs;
            match = frame;
            if (differenceNs == 0) break;
        }
    }

    if (match == renderer->expectedFrames.end()) {
        renderer->unmatchedFrames.fetch_add(1, std::memory_order_relaxed);
        return false;
    }

    *presentationTimeUs = match->presentationTimeUs;
    renderer->lastPresentationTimeUs.store(
            *presentationTimeUs, std::memory_order_relaxed);
    renderer->expectedFrames.erase(renderer->expectedFrames.begin(),
                                   std::next(match));
    renderer->matchedFrames.fetch_add(1, std::memory_order_relaxed);
    return true;
}

void onImageAvailable(void *context, AImageReader *reader) {
    auto *renderer = static_cast<Renderer *>(context);
    if (renderer == nullptr) return;
    std::lock_guard<std::mutex> callbackLock(renderer->callbackMutex);
    for (;;) {
        AImage *image = nullptr;
        media_status_t status = AImageReader_acquireNextImage(reader, &image);
        if (status != AMEDIA_OK || image == nullptr) break;

        int64_t timestampNs = 0;
        int64_t presentationTimeUs = 0;
        bool timestampMatched = false;
        if (AImage_getTimestamp(image, &timestampNs) == AMEDIA_OK) {
            renderer->lastImageTimestampNs.store(
                    timestampNs, std::memory_order_relaxed);
            timestampMatched = matchTimestamp(
                    renderer, timestampNs, &presentationTimeUs);
        }

        renderer->acquiredFrames.fetch_add(1, std::memory_order_relaxed);
        if (!timestampMatched) {
            AImage_delete(image);
            continue;
        }
        AHardwareBuffer *buffer = nullptr;
        if (AImage_getHardwareBuffer(image, &buffer) == AMEDIA_OK
                && buffer != nullptr) {
            AHardwareBuffer_Desc desc{};
            AHardwareBuffer_describe(buffer, &desc);
            renderer->ahbFrames.fetch_add(1, std::memory_order_relaxed);
            renderer->lastAhbFormat.store(desc.format, std::memory_order_relaxed);
            if ((desc.usage & AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE) != 0) {
                renderer->sampledUsageFrames.fetch_add(1, std::memory_order_relaxed);
            }
            if (isHighDepthFormat(desc.format)) {
                renderer->highDepthFrames.fetch_add(1, std::memory_order_relaxed);
            }
        }
        if (renderImage(renderer, image, presentationTimeUs)) {
            renderer->renderedFrames.fetch_add(1, std::memory_order_relaxed);
        } else {
            renderer->renderFailures.fetch_add(1, std::memory_order_relaxed);
        }
        AImage_delete(image);
    }
}

Renderer *fromHandle(jlong handle) {
    return reinterpret_cast<Renderer *>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeProbeCapabilities(
        JNIEnv *env, jclass) {
    jint result = probeVulkan();
    if (probeImageReader(env)) result |= kCapabilityImageReader;
    const char *placeboVersion = pl_version();
    if (PL_API_VER == 375 && placeboVersion != nullptr
            && placeboVersion[0] != '\0') {
        result |= kCapabilityLibplacebo375;
    }
    // Parse a deliberately invalid byte so every ABI verifies both the
    // vendored parser link and its failure-containment contract.
    const uint8_t invalidRpu = 0;
    DoviRpuOpaque *rpu = dovi_parse_unspec62_nalu(&invalidRpu, 1);
    if (rpu != nullptr) {
        if (dovi_rpu_get_error(rpu) != nullptr) result |= kCapabilityLibdovi;
        dovi_rpu_free(rpu);
    }
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeCreate(
        JNIEnv *, jclass, jint width, jint height) {
    if (width <= 0 || height <= 0) return 0;
    auto *renderer = new (std::nothrow) Renderer();
    if (renderer == nullptr) return 0;
    media_status_t status = AImageReader_newWithUsage(
            width,
            height,
            AIMAGE_FORMAT_PRIVATE,
            AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE,
            kMaxImages,
            &renderer->reader);
    if (status != AMEDIA_OK || renderer->reader == nullptr) {
        delete renderer;
        return 0;
    }
    AImageReader_ImageListener listener{
            .context = renderer,
            .onImageAvailable = onImageAvailable,
    };
    if (AImageReader_setImageListener(renderer->reader, &listener) != AMEDIA_OK) {
        AImageReader_delete(renderer->reader);
        delete renderer;
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(renderer));
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeGetInputSurface(
        JNIEnv *env, jclass, jlong handle) {
    Renderer *renderer = fromHandle(handle);
    if (renderer == nullptr || renderer->reader == nullptr) return nullptr;
    ANativeWindow *window = nullptr;
    if (AImageReader_getWindow(renderer->reader, &window) != AMEDIA_OK
            || window == nullptr) {
        return nullptr;
    }
    return ANativeWindow_toSurface(env, window);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeQueueFrame(
        JNIEnv *, jclass, jlong handle, jlong imageTimestampNs,
        jlong presentationTimeUs) {
    Renderer *renderer = fromHandle(handle);
    if (renderer == nullptr) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(renderer->expectedMutex);
    if (renderer->expectedFrames.size() >= kMaxExpectedFrames) {
        renderer->expectedFrames.pop_front();
        renderer->expectedQueueDrops.fetch_add(1, std::memory_order_relaxed);
    }
    renderer->expectedFrames.push_back(
            ExpectedFrame{imageTimestampNs, presentationTimeUs});
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeClearFrames(
        JNIEnv *, jclass, jlong handle) {
    Renderer *renderer = fromHandle(handle);
    if (renderer == nullptr) return;
    {
        std::lock_guard<std::mutex> lock(renderer->expectedMutex);
        renderer->expectedFrames.clear();
    }
    {
        std::lock_guard<std::mutex> lock(renderer->rpuMutex);
        renderer->pendingRpus.clear();
        renderer->hasLastDovi = false;
        resetDoviMetadata(&renderer->lastDovi);
        renderer->lastFullRange = true;
        renderer->hasSourceLuma = false;
        renderer->sourceMinPq = 0.0f;
        renderer->sourceMaxPq = 0.0f;
        renderer->hasSceneLuma = false;
        renderer->sceneMaxPq = 0.0f;
        renderer->sceneAvgPq = 0.0f;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeQueueRpu(
        JNIEnv *env, jclass, jlong handle, jlong presentationTimeUs,
        jbyteArray rpuArray) {
    Renderer *renderer = fromHandle(handle);
    if (renderer == nullptr || rpuArray == nullptr) return JNI_FALSE;
    const jsize length = env->GetArrayLength(rpuArray);
    if (length < 2) return JNI_FALSE;
    jbyte *bytes = env->GetByteArrayElements(rpuArray, nullptr);
    if (bytes == nullptr) return JNI_FALSE;
    DoviRpuOpaque *rpu = dovi_parse_unspec62_nalu(
            reinterpret_cast<const uint8_t *>(bytes), static_cast<size_t>(length));
    env->ReleaseByteArrayElements(rpuArray, bytes, JNI_ABORT);
    if (rpu == nullptr) {
        renderer->malformedRpus.fetch_add(1, std::memory_order_relaxed);
        return JNI_FALSE;
    }
    const DoviRpuDataHeader *header = dovi_rpu_get_header(rpu);
    const DoviRpuDataMapping *mapping = dovi_rpu_get_data_mapping(rpu);
    const DoviVdrDmData *dm = dovi_rpu_get_vdr_dm_data(rpu);
    RpuMetadata metadata{};
    metadata.presentationTimeUs = presentationTimeUs;
    metadata.hasMapping = mapping != nullptr;
    metadata.hasColor = dm != nullptr && !dm->compressed;
    metadata.fullRange = header != nullptr && header->bl_video_full_range_flag;
    if (metadata.hasColor) {
        metadata.hasSourceLuma = dm->source_max_pq > dm->source_min_pq;
        metadata.sourceMinPq = dm->source_min_pq / 4095.0f;
        metadata.sourceMaxPq = dm->source_max_pq / 4095.0f;
    }
    if (dm != nullptr) {
        metadata.updatesSceneLuma = true;
        const DoviExtMetadataBlockLevel1 *level1 = dm->dm_data.level1;
        metadata.hasSceneLuma = level1 != nullptr && level1->max_pq > 0;
        if (metadata.hasSceneLuma) {
            metadata.sceneMaxPq = level1->max_pq / 4095.0f;
            metadata.sceneAvgPq = level1->avg_pq / 4095.0f;
        }
    }
    const int64_t metadataLog = renderer->metadataLogs.fetch_add(
            1, std::memory_order_relaxed);
    if (metadataLog < 4) {
        __android_log_print(
                ANDROID_LOG_INFO, "ExoDv5",
                "rpu metadata ptsUs=%" PRId64
                " profile=%u usePrev=%d mapping=%d dm=%d compressed=%d"
                " fullRange=%d sourcePq=%.4f/%.4f scenePq=%.4f/%.4f",
                static_cast<int64_t>(presentationTimeUs),
                header == nullptr ? 0 : header->guessed_profile,
                header != nullptr && header->use_prev_vdr_rpu_flag ? 1 : 0,
                metadata.hasMapping ? 1 : 0, dm != nullptr ? 1 : 0,
                dm != nullptr && dm->compressed ? 1 : 0,
                metadata.fullRange ? 1 : 0,
                metadata.sourceMinPq, metadata.sourceMaxPq,
                metadata.sceneMaxPq, metadata.sceneAvgPq);
    }
    const bool valid = mapRpuMetadata(header, mapping, dm, &metadata.dovi);
    if (dm != nullptr) dovi_rpu_free_vdr_dm_data(dm);
    if (mapping != nullptr) dovi_rpu_free_data_mapping(mapping);
    if (header != nullptr) dovi_rpu_free_header(header);
    const char *error = dovi_rpu_get_error(rpu);
    if (!valid || error != nullptr) {
        renderer->malformedRpus.fetch_add(1, std::memory_order_relaxed);
        dovi_rpu_free(rpu);
        return JNI_FALSE;
    }
    dovi_rpu_free(rpu);
    std::lock_guard<std::mutex> lock(renderer->rpuMutex);
    // Matroska BlockAdditional delivery can batch RPU NALs out of decode order.
    // Keep enough ordered metadata for codec pre-roll. If the producer ever
    // exceeds the bound, preserve the earliest entries needed by output next.
    auto insertAt = std::upper_bound(
            renderer->pendingRpus.begin(), renderer->pendingRpus.end(),
            metadata.presentationTimeUs,
            [](int64_t pts, const RpuMetadata &item) {
                return pts < item.presentationTimeUs;
            });
    renderer->pendingRpus.insert(insertAt, metadata);
    if (renderer->pendingRpus.size() > kMaxPendingRpus) {
        renderer->pendingRpus.pop_back();
        renderer->rpuQueueDrops.fetch_add(1, std::memory_order_relaxed);
    }
    renderer->parsedRpus.fetch_add(1, std::memory_order_relaxed);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeSetOutputSurface(
        JNIEnv *env, jclass, jlong handle, jobject surface) {
    Renderer *renderer = fromHandle(handle);
    if (renderer == nullptr) return;
    ANativeWindow *newWindow = surface == nullptr
            ? nullptr : ANativeWindow_fromSurface(env, surface);
    std::lock_guard<std::mutex> lock(renderer->callbackMutex);
    destroyVulkan(renderer);
    if (renderer->outputWindow != nullptr) {
        ANativeWindow_release(renderer->outputWindow);
        renderer->outputWindow = nullptr;
    }
    renderer->outputWindow = newWindow;
    if (newWindow != nullptr) ensureVulkan(renderer);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeGetStats(
        JNIEnv *env, jclass, jlong handle) {
    Renderer *renderer = fromHandle(handle);
    if (renderer == nullptr) return nullptr;
    int64_t pending = 0;
    {
        std::lock_guard<std::mutex> lock(renderer->expectedMutex);
        pending = static_cast<int64_t>(renderer->expectedFrames.size());
    }
    int64_t pendingRpus = 0;
    {
        std::lock_guard<std::mutex> lock(renderer->rpuMutex);
        pendingRpus = static_cast<int64_t>(renderer->pendingRpus.size());
    }
    jlong values[] = {
            renderer->acquiredFrames.load(std::memory_order_relaxed),
            renderer->ahbFrames.load(std::memory_order_relaxed),
            renderer->sampledUsageFrames.load(std::memory_order_relaxed),
            renderer->highDepthFrames.load(std::memory_order_relaxed),
            renderer->matchedFrames.load(std::memory_order_relaxed),
            renderer->unmatchedFrames.load(std::memory_order_relaxed),
            renderer->expectedQueueDrops.load(std::memory_order_relaxed),
            renderer->lastImageTimestampNs.load(std::memory_order_relaxed),
            renderer->lastPresentationTimeUs.load(std::memory_order_relaxed),
            renderer->lastAhbFormat.load(std::memory_order_relaxed),
            pending,
            renderer->parsedRpus.load(std::memory_order_relaxed),
            renderer->malformedRpus.load(std::memory_order_relaxed),
            renderer->rpuQueueDrops.load(std::memory_order_relaxed),
            pendingRpus,
            renderer->renderedFrames.load(std::memory_order_relaxed),
            renderer->renderFailures.load(std::memory_order_relaxed),
    };
    jlongArray result = env->NewLongArray(std::size(values));
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, std::size(values), values);
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeRelease(
        JNIEnv *, jclass, jlong handle) {
    Renderer *renderer = fromHandle(handle);
    if (renderer == nullptr) return;
    if (renderer->reader != nullptr) {
        AImageReader_setImageListener(renderer->reader, nullptr);
        std::lock_guard<std::mutex> callbackLock(renderer->callbackMutex);
        AImageReader_delete(renderer->reader);
        renderer->reader = nullptr;
    }
    destroyVulkan(renderer);
    if (renderer->outputWindow != nullptr) {
        ANativeWindow_release(renderer->outputWindow);
        renderer->outputWindow = nullptr;
    }
    delete renderer;
}
