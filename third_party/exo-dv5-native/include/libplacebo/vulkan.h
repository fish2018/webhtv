/*
 * This file is part of libplacebo.
 *
 * libplacebo is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * libplacebo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with libplacebo.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef LIBPLACEBO_VULKAN_H_
#define LIBPLACEBO_VULKAN_H_

#include <vulkan/vulkan.h>
#include <libplacebo/gpu.h>
#include <libplacebo/swapchain.h>

PL_API_BEGIN

#define PL_VK_MIN_VERSION VK_API_VERSION_1_2

// Structure representing a VkInstance. Using this is not required.
typedef const struct pl_vk_inst_t {
    VkInstance instance;

    // The Vulkan API version supported by this VkInstance.
    uint32_t api_version;

    // The associated vkGetInstanceProcAddr pointer.
    PFN_vkGetInstanceProcAddr get_proc_addr;

    // The instance extensions that were successfully enabled, including
    // extensions enabled by libplacebo internally. May contain duplicates.
    const char * const *extensions;
    int num_extensions;

    // The instance layers that were successfully enabled, including
    // layers enabled by libplacebo internally. May contain duplicates.
    const char * const *layers;
    int num_layers;
} *pl_vk_inst;

struct pl_vk_inst_params {
    // If set, enable the debugging and validation layers. These should
    // generally be lightweight and relatively harmless to enable.
    bool debug;

    // If set, also enable GPU-assisted verification and best practices
    // layers. (Note: May cause substantial slowdown and/or result in lots of
    // false positive spam)
    bool debug_extra;

    // If nonzero, restricts the Vulkan API version to be at most this. This
    // is only really useful for explicitly testing backwards compatibility.
    uint32_t max_api_version;

    // Pointer to a user-provided `vkGetInstanceProcAddr`. If this is NULL,
    // libplacebo will use the directly linked version (if available).
    PFN_vkGetInstanceProcAddr get_proc_addr;

    // Enables extra instance extensions. Instance creation will fail if these
    // extensions are not all supported. The user may use this to enable e.g.
    // windowing system integration.
    const char * const *extensions;
    int num_extensions;

    // Enables extra optional instance extensions. These are opportunistically
    // enabled if supported by the device, but otherwise skipped.
    const char * const *opt_extensions;
    int num_opt_extensions;

    // Enables extra layers. Instance creation will fail if these layers are
    // not all supported.
    //
    // NOTE: Layers needed for required/optional extensions are automatically
    // enabled. The user does not specifically need to enable layers related
    // to extension support.
    const char * const *layers;
    int num_layers;

    // Enables extra optional layers. These are opportunistically enabled if
    // supported by the platform, but otherwise skipped.
    const char * const *opt_layers;
    int num_opt_layers;
};

#define pl_vk_inst_params(...) (&(struct pl_vk_inst_params) { __VA_ARGS__ })
PL_API extern const struct pl_vk_inst_params pl_vk_inst_default_params;

// Helper function to simplify instance creation. The user could also bypass
// these helpers and do it manually, but this function is provided as a
// convenience. It also sets up a debug callback which forwards all vulkan
// messages to the `pl_log` callback.
PL_API pl_vk_inst pl_vk_inst_create(pl_log log, const struct pl_vk_inst_params *params);
PL_API void pl_vk_inst_destroy(pl_vk_inst *inst);

struct pl_vulkan_queue {
    uint32_t index; // Queue family index
    uint32_t count; // Queue family count
    VkDeviceQueueCreateFlags flags; // Queue family flags
};

// Structure representing the actual vulkan device and associated GPU instance
typedef const struct pl_vulkan_t *pl_vulkan;
struct pl_vulkan_t {
    pl_gpu gpu;

    // The vulkan objects in use. The user may use this for their own purposes,
    // but please note that the lifetime is tied to the lifetime of the
    // pl_vulkan object, and must not be destroyed by the user. Note that the
    // created vulkan device may have any number of queues and queue family
    // assignments; so using it for queue submission commands is ill-advised.
    VkInstance instance;
    VkPhysicalDevice phys_device;
    VkDevice device;

    // The associated vkGetInstanceProcAddr pointer.
    PFN_vkGetInstanceProcAddr get_proc_addr;

    // The Vulkan API version supported by this VkPhysicalDevice.
    uint32_t api_version;

    // The device extensions that were successfully enabled, including
    // extensions enabled by libplacebo internally. May contain duplicates.
    const char * const *extensions;
    int num_extensions;

    // The device features that were enabled at device creation time.
    //
    // Note: Whenever a feature flag is ambiguious between several alternative
    // locations, for completeness' sake, we include both.
    const VkPhysicalDeviceFeatures2 *features;

    // The explicit queue families we are using to provide a given capability.
    struct pl_vulkan_queue queue_graphics; // provides VK_QUEUE_GRAPHICS_BIT
    struct pl_vulkan_queue queue_compute;  // provides VK_QUEUE_COMPUTE_BIT
    struct pl_vulkan_queue queue_transfer; // provides VK_QUEUE_TRANSFER_BIT

    // Functions for locking a queue. These must be used to lock VkQueues for
    // submission or other related operations when sharing the VkDevice between
    // multiple threads, Using this on queue families or indices not contained
    // in `queues` is undefined behavior.
    void (*lock_queue)(pl_vulkan vk, uint32_t qf, uint32_t qidx);
    void (*unlock_queue)(pl_vulkan vk, uint32_t qf, uint32_t qidx);

    // --- Deprecated fields

    // These are the same active queue families and their queue counts in list
    // form. This list does not contain duplicates, nor any extra queues
    // enabled at device creation time. Deprecated in favor of querying
    // `vkGetPhysicalDeviceQueueFamilyProperties` directly.
    PL_DEPRECATED_IN(v6.271) const struct pl_vulkan_queue *queues;
    PL_DEPRECATED_IN(v6.271) int num_queues;
};

struct pl_vulkan_params {
    // The vulkan instance. Optional, if NULL then libplacebo will internally
    // create a VkInstance with the settings from `instance_params`.
    //
    // Note: The VkInstance provided by the user *MUST* be created with a
    // VkApplicationInfo.apiVersion of PL_VK_MIN_VERSION or higher.
    VkInstance instance;

    // Pointer to `vkGetInstanceProcAddr`. If this is NULL, libplacebo will
    // use the directly linked version (if available).
    //
    // Note: This overwrites the same value from `instance_params`.
    PFN_vkGetInstanceProcAddr get_proc_addr;

    // Configures the settings used for creating an internal vulkan instance.
    // May be NULL. Ignored if `instance` is set.
    const struct pl_vk_inst_params *instance_params;

    // When choosing the device, rule out all devices that don't support
    // presenting to this surface. When creating a device, enable all extensions
    // needed to ensure we can present to this surface. Optional. Only legal
    // when specifying an existing VkInstance to use.
    VkSurfaceKHR surface;

    // --- Physical device selection options

    // The vulkan physical device. May be set by the caller to indicate the
    // physical device to use. Otherwise, libplacebo will pick the "best"
    // available GPU, based on the advertised device type. (i.e., it will
    // prefer discrete GPUs over integrated GPUs). Only legal when specifying
    // an existing VkInstance to use.
    VkPhysicalDevice device;

    // When choosing the device, only choose a device with this exact name.
    // This overrides `allow_software`. No effect if `device` is set. Note: A
    // list of devices and their names are logged at level PL_LOG_INFO.
    const char *device_name;

    // When choosing the device, only choose a device with this exact UUID.
    // This overrides `allow_software` and `device_name`. No effect if `device`
    // is set.
    uint8_t device_uuid[16];

    // When choosing the device, controls whether or not to also allow software
    // GPUs. No effect if `device` or `device_name` are set.
    bool allow_software;

    // --- Logical device creation options

    // Controls whether or not to allow asynchronous transfers, using transfer
    // queue families, if supported by the device. This can be significantly
    // faster and more power efficient, and also allows streaming uploads in
    // parallel with rendering commands. Enabled by default.
    bool async_transfer;

    // Controls whether or not to allow asynchronous compute, using dedicated
    // compute queue families, if supported by the device. On some devices,
    // these can allow the GPU to schedule compute shaders in parallel with
    // fragment shaders. Enabled by default.
    bool async_compute;

    // Limits the number of queues to use. If left as 0, libplacebo will use as
    // many queues as the device supports. Multiple queues can result in
    // improved efficiency when submitting multiple commands that can entirely
    // or partially execute in parallel. Defaults to 1, since using more queues
    // can actually decrease performance.
    //
    // Note: libplacebo will always *create* logical devices with all available
    // queues for a given QF enabled, regardless of this setting.
    int queue_count;

    // Disables the use of compute shaders. Some devices/drivers perform better
    // without them. This may also help prevent image corruption in cases where
    // the driver is misbehaving. Some features may be disabled if this is set.
    bool no_compute;

    // Bitmask of extra queue families to enable. If set, then *all* queue
    // families matching *any* of these flags will be enabled at device
    // creation time. Setting this to VK_QUEUE_FLAG_BITS_MAX_ENUM effectively
    // enables all queue families supported by the device.
    VkQueueFlags extra_queues;

    // Enables extra device extensions. Device creation will fail if these
    // extensions are not all supported. The user may use this to enable e.g.
    // interop extensions.
    const char * const *extensions;
    int num_extensions;

    // Enables extra optional device extensions. These are opportunistically
    // enabled if supported by the device, but otherwise skipped.
    const char * const *opt_extensions;
    int num_opt_extensions;

    // Optional extra features to enable at device creation time. These are
    // opportunistically enabled if supported by the physical device, but
    // otherwise kept disabled.
    const VkPhysicalDeviceFeatures2 *features;

    // --- Misc/debugging options

    // Restrict specific features to e.g. work around driver bugs, or simply
    // for testing purposes
    int max_glsl_version;       // limit the maximum GLSL version
    uint32_t max_api_version;   // limit the maximum vulkan API version
};

// Default/recommended parameters. Should generally be safe and efficient.
#define PL_VULKAN_DEFAULTS                              \
    .async_transfer = true,                             \
    .async_compute  = true,                             \
    /* enabling multiple queues often decreases perf */ \
    .queue_count    = 1,

#define pl_vulkan_params(...) (&(struct pl_vulkan_params) { PL_VULKAN_DEFAULTS __VA_ARGS__ })
PL_API extern const struct pl_vulkan_params pl_vulkan_default_params;

// Creates a new vulkan device based on the given parameters and initializes
// a new GPU. If `params` is left as NULL, it defaults to
// &pl_vulkan_default_params.
//
// Thread-safety: Safe
PL_API pl_vulkan pl_vulkan_create(pl_log log, const struct pl_vulkan_params *params);

// Destroys the vulkan device and all associated objects, except for the
// VkInstance provided by the user.
//
// Note that all resources allocated from this vulkan object (e.g. via the
// `vk->ra` or using `pl_vulkan_create_swapchain`) *must* be explicitly
// destroyed by the user before calling this.
//
// Also note that this function will block until all in-flight GPU commands are
// finished processing. You can avoid this by manually calling `pl_gpu_finish`
// before `pl_vulkan_destroy`.
PL_API void pl_vulkan_destroy(pl_vulkan *vk);

// For a `pl_gpu` backed by `pl_vulkan`, this function can be used to retrieve
// the underlying `pl_vulkan`. Returns NULL for any other type of `gpu`.
PL_API pl_vulkan pl_vulkan_get(pl_gpu gpu);

struct pl_vulkan_device_params {
    // The instance to use. Required!
    //
    // Note: The VkInstance provided by the user *must* be created with a
    // VkApplicationInfo.apiVersion of PL_VK_MIN_VERSION or higher.
    VkInstance instance;

    // Mirrored from `pl_vulkan_params`. All of these fields are optional.
    PFN_vkGetInstanceProcAddr get_proc_addr;
    VkSurfaceKHR surface;
    const char *device_name;
    uint8_t device_uuid[16];
    bool allow_software;
};

#define pl_vulkan_device_params(...) (&(struct pl_vulkan_device_params) { __VA_ARGS__ })

// Helper function to choose the best VkPhysicalDevice, given a VkInstance.
// This uses the same logic as `pl_vulkan_create` uses internally. If no
// matching device was found, this returns VK_NULL_HANDLE.
PL_API VkPhysicalDevice pl_vulkan_choose_device(pl_log log,
                              const struct pl_vulkan_device_params *params);

struct pl_vulkan_swapchain_params {
    // The surface to use for rendering. Required, the user is in charge of
    // creating this. Must belong to the same VkInstance as `vk->instance`.
    VkSurfaceKHR surface;

    // The preferred presentation mode. See the vulkan documentation for more
    // information about these. If the device/surface combination does not
    // support this mode, libplacebo will fall back to VK_PRESENT_MODE_FIFO_KHR.
    //
    // Warning: Leaving this zero-initialized is the same as having specified
    // VK_PRESENT_MODE_IMMEDIATE_KHR, which is probably not what the user
    // wants!
    VkPresentModeKHR present_mode;

    // Allow up to N in-flight frames. This essentially controls how many
    // rendering commands may be queued up at the same time. See the
    // documentation for `pl_swapchain_get_latency` for more information. For
    // vulkan specifically, we are only able to wait until the GPU has finished
    // rendering a frame - we are unable to wait until the display has actually
    // finished displaying it. So this only provides a rough guideline.
    // Optional, defaults to 3.
    int swapchain_depth;

    // This suppresses automatic recreation of the swapchain when any call
    // returns VK_SUBOPTIMAL_KHR. Normally, libplacebo will recreate the
    // swapchain internally on the next `pl_swapchain_start_frame`. If enabled,
    // clients are assumed to take care of swapchain recreations themselves, by
    // calling `pl_swapchain_resize` as appropriate. libplacebo will tolerate
    // the "suboptimal" status indefinitely.
    bool allow_suboptimal;

    // Do not request VK_IMAGE_USAGE_STORAGE_BIT for swapchain images. This
    // may allow the presentation engine to choose a more compositable image
    // layout at the cost of preventing direct compute shader output.
    bool disable_storage;

    // Disable high-bit (10 or more) SDR formats. May help work around buggy
    // drivers which don't dither properly when outputting high bit depth
    // SDR backbuffers to 8-bit screens.
    bool disable_10bit_sdr;

    // Minimum number of alpha bits requested in the swapchain format. In
    // practice, this can be used to prefer formats with higher alpha bit depth
    // (e.g. rgba8/rgba16 over rgb10a2), potentially trading 2 bits per color
    // channel for increased alpha precision. This is a hint, if no matching
    // format is found, lower bit depths will be accepted.
    uint8_t alpha_bits;

    // Minimum number of color bits requested in the swapchain format. This can
    // be used to prefer formats with higher color bit depth. This is a hint,
    // if no matching format is found, lower bit depths will be accepted.
    uint8_t color_bits;
};

#define pl_vulkan_swapchain_params(...) (&(struct pl_vulkan_swapchain_params) { __VA_ARGS__ })

// Creates a new vulkan swapchain based on an existing VkSurfaceKHR. Using this
// function requires that the vulkan device was created with the
// VK_KHR_swapchain extension. The easiest way of accomplishing this is to set
// the `pl_vulkan_params.surface` explicitly at creation time.
PL_API pl_swapchain pl_vulkan_create_swapchain(pl_vulkan vk,
                              const struct pl_vulkan_swapchain_params *params);

// This will return true if the vulkan swapchain is internally detected
// as being suboptimal (VK_SUBOPTIMAL_KHR). This might be of use to clients
// who have `params->allow_suboptimal` enabled.
PL_API bool pl_vulkan_swapchain_suboptimal(pl_swapchain sw);

// Vulkan interop API, for sharing a single VkDevice (and associated vulkan
// resources) directly with the API user. The use of this API is a bit sketchy
// and requires careful communication of Vulkan API state.

struct pl_vulkan_import_params {
    // The vulkan instance. Required.
    //
    // Note: The VkInstance provided by the user *must* be created with a
    // VkApplicationInfo.apiVersion of PL_VK_MIN_VERSION or higher.
    VkInstance instance;

    // Pointer to `vkGetInstanceProcAddr`. If this is NULL, libplacebo will
    // use the directly linked version (if available).
    PFN_vkGetInstanceProcAddr get_proc_addr;

    // The physical device selected by the user. Required.
    VkPhysicalDevice phys_device;

    // The logical device created by the user. Required.
    VkDevice device;

    // --- Logical device parameters

    // List of all device-level extensions that were enabled. (Instance-level
    // extensions need not be re-specified here, since it's guaranteed that any
    // instance-level extensions that device-level extensions depend on were
    // enabled at the instance level)
    const char * const *extensions;
    int num_extensions;

    // Enabled queue families. At least `queue_graphics` is required.
    //
    // It's okay for multiple queue families to be specified with the same
    // index, e.g. in the event that a dedicated compute queue also happens to
    // be the dedicated transfer queue.
    //
    // It's also okay to leave the queue struct as {0} in the event that no
    // dedicated queue exists for a given operation type. libplacebo will
    // automatically fall back to using e.g. the graphics queue instead.
    struct pl_vulkan_queue queue_graphics; // must support VK_QUEUE_GRAPHICS_BIT
    struct pl_vulkan_queue queue_compute;  // must support VK_QUEUE_COMPUTE_BIT
    struct pl_vulkan_queue queue_transfer; // must support VK_QUEUE_TRANSFER_BIT

    // Enabled VkPhysicalDeviceFeatures. The device *must* be created with
    // all of the features in `pl_vulkan_required_features` enabled.
    const VkPhysicalDeviceFeatures2 *features;

    // Functions for locking a queue. If set, these will be used instead of
    // libplacebo's internal functions for `pl_vulkan.(un)lock_queue`.
    // If not set, libplacebo will use its own internal locking, unless the
    // device was created with VK_KHR_internally_synchronized_queues enabled
    // (as indicated by `features`), in which case locking is skipped.
    void (*lock_queue)(void *ctx, uint32_t qf, uint32_t qidx);
    void (*unlock_queue)(void *ctx, uint32_t qf, uint32_t qidx);
    void *queue_ctx;

    // --- Misc/debugging options

    // Restrict specific features to e.g. work around driver bugs, or simply
    // for testing purposes. See `pl_vulkan_params` for a description of these.
    bool no_compute;
    int max_glsl_version;
    uint32_t max_api_version;
};

#define pl_vulkan_import_params(...) (&(struct pl_vulkan_import_params) { __VA_ARGS__ })

// For purely informative reasons, this contains a list of extensions and
// device features that libplacebo *can* make use of. These are all strictly
// optional, but provide a hint to the API user as to what might be worth
// enabling at device creation time.
//
// Note: This also includes physical device features provided by extensions.
// They are all provided using extension-specific features structs, rather
// than the more general purpose VkPhysicalDeviceVulkan11Features etc.
PL_API extern const char * const pl_vulkan_recommended_extensions[];
PL_API extern const int pl_vulkan_num_recommended_extensions;
PL_API extern const VkPhysicalDeviceFeatures2 pl_vulkan_recommended_features;

// A list of device features that are required by libplacebo. These
// *must* be provided by imported Vulkan devices.
//
// Note: `pl_vulkan_recommended_features` does not include this list.
PL_API extern const VkPhysicalDeviceFeatures2 pl_vulkan_required_features;

// Import an existing VkDevice instead of creating a new one, and wrap it into
// a `pl_vulkan` abstraction. It's safe to `pl_vulkan_destroy` this, which will
// destroy application state related to libplacebo but leave the underlying
// VkDevice intact.
PL_API pl_vulkan pl_vulkan_import(pl_log log, const struct pl_vulkan_import_params *params);

struct pl_vulkan_ycbcr_params;

struct pl_vulkan_wrap_params {
    // The image itself. It *must* be usable concurrently by all of the queue
    // family indices listed in `pl_vulkan->queues`. Note that this requires
    // the use of VK_SHARING_MODE_CONCURRENT if `pl_vulkan->num_queues` is
    // greater than 1. If this is difficult to achieve for the user, then
    // `async_transfer` / `async_compute` should be turned off, which
    // guarantees the use of only one queue family.
    VkImage image;

    // Which aspect of `image` to wrap. Only useful for wrapping individual
    // sub-planes of planar images. If left as 0, it defaults to the entire
    // image (i.e. the union of VK_IMAGE_ASPECT_PLANE_N_BIT for planar formats,
    // and VK_IMAGE_ASPECT_COLOR_BIT otherwise). YCbCr conversion requires 0
    // or VK_IMAGE_ASPECT_COLOR_BIT.
    VkImageAspectFlags aspect;

    // The image's dimensions (unused dimensions must be 0)
    int width;
    int height;
    int depth;

    // The image's format. libplacebo will try to map this to an equivalent
    // pl_fmt. If no compatible pl_fmt is found, wrapping will fail. When
    // `ycbcr` is set, this may be a concrete multi-planar format or
    // VK_FORMAT_UNDEFINED for an Android external format.
    VkFormat format;

    // The usage flags the image was created with. libplacebo will set the
    // pl_tex capabilities to include whatever it can, as determined by the set
    // of enabled usage flags. For YCbCr images this must exactly match the
    // image creation usage so libplacebo can query descriptor requirements.
    VkImageUsageFlags usage;

    // See `pl_tex_params`
    void *user_data;
    pl_debug_tag debug_tag;

    // Optional YCbCr sampling configuration. When set, the wrapped image is
    // exposed as a normalized four-component texture backed by an immutable
    // VkSamplerYcbcrConversion sampler. The selected conversion model
    // determines whether shaders receive converted RGB or reconstructed source
    // components. The pointed-to data is consumed during this call and does
    // not need to remain alive afterwards.
    const struct pl_vulkan_ycbcr_params *ycbcr;
};

struct pl_vulkan_ycbcr_params {
    // Android external-format identifier. Must be non-zero exactly when the
    // wrapped image format is VK_FORMAT_UNDEFINED.
    uint64_t external_format;
    VkComponentMapping components;
    VkSamplerYcbcrModelConversion model;
    VkSamplerYcbcrRange range;
    VkChromaLocation x_chroma_offset;
    VkChromaLocation y_chroma_offset;
    VkFilter chroma_filter;
    bool separate_reconstruction_filter;
    bool force_explicit_reconstruction;

    // Effective component precision of the samples returned to shaders.
    int sample_depth;
};

#define pl_vulkan_wrap_params(...) (&(struct pl_vulkan_wrap_params) {   \
        .debug_tag = PL_DEBUG_TAG,                                      \
        __VA_ARGS__                                                     \
    })

// Wraps an external VkImage into a pl_tex abstraction. By default, the image
// is considered "held" by the user and must be released before calling any
// pl_tex_* API calls on it (see `pl_vulkan_release_ex`).
//
// This wrapper can be destroyed by simply calling `pl_tex_destroy` on it,
// which will not destroy the underlying VkImage. If a pl_tex wrapper is
// destroyed while an image is not currently being held by the user, that
// image is left in an undefined state.
//
// Wrapping the same VkImage multiple times is undefined behavior, as is trying
// to wrap an image belonging to a different VkDevice than the one in use by
// `gpu`.
//
// This function may fail, in which case it returns NULL.
PL_API pl_tex pl_vulkan_wrap(pl_gpu gpu, const struct pl_vulkan_wrap_params *params);

// Analogous to `pl_vulkan_wrap`, this function takes any `pl_tex` (including
// ones created by `pl_tex_create`) and unwraps it to expose the underlying
// VkImage to the user. Unlike `pl_vulkan_wrap`, this `pl_tex` is *not*
// considered held after calling this function - the user must explicitly
// `pl_vulkan_hold_ex` before accessing the VkImage.
//
// `out_format` and `out_flags` will be updated to hold the VkImage's
// format and usage flags. (Optional)
PL_API VkImage pl_vulkan_unwrap(pl_gpu gpu, pl_tex tex,
                                VkFormat *out_format, VkImageUsageFlags *out_flags);

// Represents a vulkan semaphore/value pair (for compatibility with timeline
// semaphores). When using normal, binary semaphores, `value` may be ignored.
typedef struct pl_vulkan_sem {
    VkSemaphore sem;
    uint64_t value;
} pl_vulkan_sem;

struct pl_vulkan_hold_params {
    // The Vulkan image to hold. It will be marked as held. Attempting to
    // perform any pl_tex_* operation (except pl_tex_destroy) on a held image
    // is undefined behavior.
    pl_tex tex;

    // The layout to transition the image to when holding. Alternatively, a
    // pointer to receive the current image layout. If `out_layout` is
    // provided, `layout` is ignored.
    VkImageLayout layout;
    VkImageLayout *out_layout;

    // The queue family index to transition the image to. This can be used with
    // VK_QUEUE_FAMILY_EXTERNAL to transition the image to an external API. As
    // a special case, if set to VK_QUEUE_FAMILY_IGNORED, libplacebo will not
    // transition the image, even if this image was not set up for concurrent
    // usage. Ignored for concurrent images.
    uint32_t qf;

    // The semaphore to fire when the image is available for use. (Required)
    pl_vulkan_sem semaphore;
};

#define pl_vulkan_hold_params(...) (&(struct pl_vulkan_hold_params) { __VA_ARGS__ })

// "Hold" a shared image, transferring control over the image to the user.
// Returns whether successful.
PL_API bool pl_vulkan_hold_ex(pl_gpu gpu, const struct pl_vulkan_hold_params *params);

struct pl_vulkan_release_params {
    // The image to be released. It must be marked as "held". Performing any
    // operation on the VkImage underlying this `pl_tex` while it is not being
    // held by the user is undefined behavior.
    pl_tex tex;

    // The current layout of the image at the point in time when `semaphore`
    // fires, or if no semaphore is specified, at the time of call.
    VkImageLayout layout;

    // The queue family index to transition the image to. This can be used with
    // VK_QUEUE_FAMILY_EXTERNAL to transition the image rom an external API. As
    // a special case, if set to VK_QUEUE_FAMILY_IGNORED, libplacebo will not
    // transition the image, even if this image was not set up for concurrent
    // usage. Ignored for concurrent images.
    uint32_t qf;

    // The semaphore to wait on before libplacebo will actually use or modify
    // the image. (Optional)
    //
    // Note: the lifetime of `semaphore` is indeterminate, and destroying it
    // while the texture is still depending on that semaphore is undefined
    // behavior.
    //
    // Technically, the only way to be sure that it's safe to free is to use
    // `pl_gpu_finish()` or similar (e.g. `pl_vulkan_destroy` or
    // `vkDeviceWaitIdle`) after another operation involving `tex` has been
    // emitted (or the texture has been destroyed).
    //
    //
    // Warning: If `tex` is a planar image (`pl_fmt.num_planes > 0`), and
    // `semaphore` is specified, it *must* be a timeline semaphore! Failure to
    // respect this will result in undefined behavior. This warning does not
    // apply to individual planes (as exposed by `pl_tex.planes`).
    pl_vulkan_sem semaphore;
};

#define pl_vulkan_release_params(...) (&(struct pl_vulkan_release_params) { __VA_ARGS__ })

// "Release" a shared image, transferring control to libplacebo.
PL_API void pl_vulkan_release_ex(pl_gpu gpu, const struct pl_vulkan_release_params *params);

struct pl_vulkan_sem_params {
    // The type of semaphore to create.
    VkSemaphoreType type;

    // For VK_SEMAPHORE_TYPE_TIMELINE, sets the initial timeline value.
    uint64_t initial_value;

    // If set, exports this VkSemaphore to the handle given in `out_handle`.
    // The user takes over ownership, and should manually close it before
    // destroying this VkSemaphore (via `pl_vulkan_sem_destroy`).
    enum pl_handle_type export_handle;
    union pl_handle *out_handle;

    // Optional debug tag to identify this semaphore.
    pl_debug_tag debug_tag;
};

#define pl_vulkan_sem_params(...) (&(struct pl_vulkan_sem_params) {     \
        .debug_tag = PL_DEBUG_TAG,                                      \
        __VA_ARGS__                                                     \
    })

// Helper functions to create and destroy vulkan semaphores. Returns
// VK_NULL_HANDLE on failure.
PL_API VkSemaphore pl_vulkan_sem_create(pl_gpu gpu, const struct pl_vulkan_sem_params *params);
PL_API void pl_vulkan_sem_destroy(pl_gpu gpu, VkSemaphore *semaphore);

PL_API_END

#endif // LIBPLACEBO_VULKAN_H_
