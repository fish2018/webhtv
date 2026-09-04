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

#ifndef LIBPLACEBO_GPU_H_
#define LIBPLACEBO_GPU_H_

#include <stddef.h>
#include <stdbool.h>
#include <stdint.h>

#include <libplacebo/common.h>
#include <libplacebo/cache.h>
#include <libplacebo/log.h>

PL_API_BEGIN

// These are not memory managed, and should represent compile-time constants
typedef const char *pl_debug_tag;
#define PL_DEBUG_TAG (__FILE__ ":" PL_TOSTRING(__LINE__))

// Type of a shader input descriptor.
enum pl_desc_type {
    PL_DESC_INVALID = 0,
    PL_DESC_SAMPLED_TEX,    // C: pl_tex*    GLSL: combined texture sampler
                            // (`pl_tex->params.sampleable` must be set)
    PL_DESC_STORAGE_IMG,    // C: pl_tex*    GLSL: storage image
                            // (`pl_tex->params.storable` must be set)
    PL_DESC_BUF_UNIFORM,    // C: pl_buf*    GLSL: uniform buffer
                            // (`pl_buf->params.uniform` must be set)
    PL_DESC_BUF_STORAGE,    // C: pl_buf*    GLSL: storage buffer
                            // (`pl_buf->params.storable` must be set)
    PL_DESC_BUF_TEXEL_UNIFORM,// C: pl_buf*  GLSL: uniform samplerBuffer
                              // (`pl_buf->params.uniform` and `format` must be set)
    PL_DESC_BUF_TEXEL_STORAGE,// C: pl_buf*  GLSL: uniform imageBuffer
                              // (`pl_buf->params.uniform` and `format` must be set)
    PL_DESC_TYPE_COUNT
};

// This file contains the definition of an API which is designed to abstract
// away from platform-specific APIs like the various OpenGL variants, Direct3D
// and Vulkan in a common way. It is a much more limited API than those APIs,
// since it tries targeting a very small common subset of features that is
// needed to implement libplacebo's rendering.
//
// NOTE: Most, but not all, parameter conditions (phrases such as "must" or
// "valid usage" are explicitly tested and result in error messages followed by
// graceful failure. Exceptions are noted where they exist.

// Structure which wraps metadata describing GLSL capabilities.
struct pl_glsl_version {
    int version;        // GLSL version (e.g. 450), for #version
    bool gles;          // GLSL ES semantics (ESSL)
    bool vulkan;        // GL_KHR_vulkan_glsl semantics

    // Compute shader support and limits. If `compute` is false, then all
    // of the remaining fields in this section are {0}.
    bool compute;
    size_t max_shmem_size;      // maximum compute shader shared memory size
    uint32_t max_group_threads; // maximum number of local threads per work group
    uint32_t max_group_size[3]; // maximum work group size per dimension

    // If nonzero, signals availability of shader subgroups. This guarantess
    // availability of all of the following extensions:
    // - GL_KHR_shader_subgroup_basic
    // - GL_KHR_shader_subgroup_vote
    // - GL_KHR_shader_subgroup_arithmetic
    // - GL_KHR_shader_subgroup_ballot
    // - GL_KHR_shader_subgroup_shuffle
    uint32_t subgroup_size;

    // Miscellaneous shader limits
    int16_t min_gather_offset;  // minimum `textureGatherOffset` offset
    int16_t max_gather_offset;  // maximum `textureGatherOffset` offset
};

// Backwards compatibility alias
#define pl_glsl_desc pl_glsl_version

// Structure defining the physical limits and capabilities of this GPU
// instance. If a limit is given as 0, that means that feature is unsupported.
struct pl_gpu_limits {
    // --- pl_gpu
    bool thread_safe;           // `pl_gpu` calls are thread-safe
    bool callbacks;             // supports asynchronous GPU callbacks

    // --- pl_buf
    size_t max_buf_size;        // maximum size of any buffer
    size_t max_ubo_size;        // maximum size of a `uniform` buffer
    size_t max_ssbo_size;       // maximum size of a `storable` buffer
    size_t max_vbo_size;        // maximum size of a `drawable` buffer
    size_t max_mapped_size;     // maximum size of a `host_mapped` buffer
    uint64_t max_buffer_texels; // maximum number of texels in a texel buffer
    bool host_cached;           // if true, PL_BUF_MEM_HOST buffers are cached
    bool host_ptr_slow;         // if true, PL_HANDLE_HOST_PTR import is slow
    size_t max_mapped_vram;     // maximum (known) size of a `host_mapped`
                                // PL_BUF_MEM_DEVICE buffer, or 0 if this
                                // combination is not supported

    // Required alignment for PL_HANDLE_HOST_PTR imports. This is provided
    // merely as a hint to the user. If the host pointer being imported is
    // misaligned, libplacebo will internally round (over-map) the region.
    size_t align_host_ptr;

    // --- pl_tex
    uint32_t max_tex_1d_dim;    // maximum width for a 1D texture
    uint32_t max_tex_2d_dim;    // maximum width/height for a 2D texture (required)
    uint32_t max_tex_3d_dim;    // maximum width/height/depth for a 3D texture
    bool blittable_1d_3d;       // supports blittable 1D/3D textures
    bool buf_transfer;          // supports `pl_tex_transfer_params.buf`

    // These don't represent hard limits but indicate performance hints for
    // optimal alignment. For best performance, the corresponding field
    // should be aligned to a multiple of these. They will always be a power
    // of two.
    size_t align_tex_xfer_pitch;    // optimal `pl_tex_transfer_params.row_pitch`
    size_t align_tex_xfer_offset;   // optimal `pl_tex_transfer_params.buf_offset`

    // --- pl_pass
    size_t max_variable_comps;  // maximum components passed in variables
    size_t max_constants;       // maximum `pl_pass_params.num_constants`
    bool array_size_constants;  // push constants can be used to size arrays
    size_t max_pushc_size;      // maximum `push_constants_size`
    size_t align_vertex_stride; // alignment of `pl_pass_params.vertex_stride`
    uint32_t max_dispatch[3];   // maximum dispatch size per dimension

    // Note: At least one of `max_variable_comps` or `max_ubo_size` is
    // guaranteed to be nonzero.

    // As a performance hint, the GPU may signal the number of command queues
    // it has for fragment and compute shaders, respectively. Users may use
    // this information to decide the appropriate type of shader to dispatch.
    uint32_t fragment_queues;
    uint32_t compute_queues;
};

// Backwards compatibility aliases
#define max_xfer_size max_buf_size
#define align_tex_xfer_stride align_tex_xfer_pitch

// Some `pl_gpu` operations allow sharing GPU resources with external APIs -
// examples include interop with other graphics APIs such as CUDA, and also
// various hardware decoding APIs. This defines the mechanism underpinning the
// communication of such an interoperation.
typedef uint64_t pl_handle_caps;
enum pl_handle_type {
    PL_HANDLE_FD        = (1 << 0), // `int fd` for POSIX-style APIs
    PL_HANDLE_WIN32     = (1 << 1), // `HANDLE` for win32 API
    PL_HANDLE_WIN32_KMT = (1 << 2), // `HANDLE` for pre-Windows-8 win32 API
    PL_HANDLE_DMA_BUF   = (1 << 3), // 'int fd' for a dma_buf fd
    PL_HANDLE_HOST_PTR  = (1 << 4), // `void *` for a host-allocated pointer
    PL_HANDLE_MTL_TEX   = (1 << 5), // `MTLTexture*` for Apple platforms
    PL_HANDLE_IOSURFACE = (1 << 6), // `IOSurfaceRef` for Apple platforms
};

struct pl_gpu_handle_caps {
    pl_handle_caps tex;  // supported handles for `pl_tex` + `pl_shared_mem`
    pl_handle_caps buf;  // supported handles for `pl_buf` + `pl_shared_mem`
    pl_handle_caps sync; // supported handles for semaphores
};

// Wrapper for the handle used to communicate a shared resource externally.
// This handle is owned by the `pl_gpu` - if a user wishes to use it in a way
// that takes over ownership (e.g. importing into some APIs), they must clone
// the handle before doing so (e.g. using `dup` for fds). It is important to
// read the external API documentation _very_ carefully as different handle
// types may be managed in different ways. (eg: CUDA takes ownership of an fd,
// but does not take ownership of a win32 handle).
union pl_handle {
    int fd;         // PL_HANDLE_FD / PL_HANDLE_DMA_BUF
    void *handle;   // PL_HANDLE_WIN32 / PL_HANDLE_WIN32_KMT / PL_HANDLE_MTL_TEX / PL_HANDLE_IOSURFACE
    void *ptr;      // PL_HANDLE_HOST_PTR
};

// Structure encapsulating memory that is shared between libplacebo and the
// user. This memory can be imported into external APIs using the handle.
//
// If the object a `pl_shared_mem` belongs to is destroyed (e.g. via
// `pl_buf_destroy`), the handle becomes undefined, as do the contents of the
// memory it points to, as well as any external API objects imported from it.
struct pl_shared_mem {
    union pl_handle handle;
    size_t size;   // the total size of the memory referenced by this handle
    size_t offset; // the offset of the object within the referenced memory

    // Note: `size` is optional for some APIs and handle types, in particular
    // when importing DMABUFs or D3D11 textures.

    // For PL_HANDLE_DMA_BUF, this specifies the DRM format modifier that
    // describes this resource. Note that when importing `pl_buf`, this must
    // be DRM_FORMAT_MOD_LINEAR. For importing `pl_tex`, it can be any
    // format modifier supported by the implementation.
    uint64_t drm_format_mod;

    // When importing a `pl_tex` of type PL_HANDLE_DMA_BUF, this can be used to
    // set the image stride (AKA pitch) in memory. If left as 0, defaults to
    // the image width/height.
    size_t stride_w;
    size_t stride_h;

    // When importing a `pl_tex` of type PL_HANDLE_MTL_TEX, this determines
    // which plane is imported (0 - 2).
    unsigned plane;
};

// Structure grouping PCI bus address fields for GPU devices
struct pl_gpu_pci_address {
    uint32_t domain;
    uint32_t bus;
    uint32_t device;
    uint32_t function;
};

typedef const struct pl_fmt_t *pl_fmt;

// Abstract device context which wraps an underlying graphics context and can
// be used to dispatch rendering commands.
//
// Thread-safety: Depends on `pl_gpu_limits.thread_safe`
typedef const struct pl_gpu_t {
    pl_log log;

    struct pl_glsl_version glsl; // GLSL features supported by this GPU
    struct pl_gpu_limits limits; // physical device limits and capabilities

    // Fields relevant to external API interop. If the underlying device does
    // not support interop with other APIs, these will all be {0}.
    struct pl_gpu_handle_caps export_caps; // supported handles for exporting
    struct pl_gpu_handle_caps import_caps; // supported handles for importing
    uint8_t uuid[16];                      // underlying device UUID

    // Supported texture formats, in preference order. (If there are multiple
    // similar formats, the "better" ones come first)
    pl_fmt *formats;
    int num_formats;

    // PCI Bus address of the underlying device, to help with interop.
    // This will only be filled in if interop is supported.
    struct pl_gpu_pci_address pci;
} *pl_gpu;

// Attach a pl_cache object to this GPU instance. This cache will be
// used to cache all compiled shaders, as well as several other shader objects
// (e.g. cached 3DLUTs). Calling this with `cache = NULL` disables the cache.
//
// Note: Calling this after shaders have already been compiled will not
// retroactively add those shaders to the cache, so it's recommended to set
// this early, before creating any passes.
PL_API void pl_gpu_set_cache(pl_gpu gpu, pl_cache cache);

enum pl_fmt_type {
    PL_FMT_UNKNOWN = 0, // also used for inconsistent multi-component formats
    PL_FMT_UNORM,       // unsigned, normalized integer format (sampled as float)
    PL_FMT_SNORM,       // signed, normalized integer format (sampled as float)
    PL_FMT_UINT,        // unsigned integer format (sampled as integer)
    PL_FMT_SINT,        // signed integer format (sampled as integer)
    PL_FMT_FLOAT,       // (signed) float formats, any bit size
    PL_FMT_TYPE_COUNT,
};

enum pl_fmt_caps {
    PL_FMT_CAP_SAMPLEABLE    = 1 << 0,  // may be sampled from (PL_DESC_SAMPLED_TEX)
    PL_FMT_CAP_STORABLE      = 1 << 1,  // may be used as storage image (PL_DESC_STORAGE_IMG)
    PL_FMT_CAP_LINEAR        = 1 << 2,  // may be linearly samplied from (PL_TEX_SAMPLE_LINEAR)
    PL_FMT_CAP_RENDERABLE    = 1 << 3,  // may be rendered to (pl_pass_params.target_fmt)
    PL_FMT_CAP_BLENDABLE     = 1 << 4,  // may be blended to (pl_pass_params.enable_blend)
    PL_FMT_CAP_BLITTABLE     = 1 << 5,  // may be blitted from/to (pl_tex_blit)
    PL_FMT_CAP_VERTEX        = 1 << 6,  // may be used as a vertex attribute
    PL_FMT_CAP_TEXEL_UNIFORM = 1 << 7,  // may be used as a texel uniform buffer
    PL_FMT_CAP_TEXEL_STORAGE = 1 << 8,  // may be used as a texel storage buffer
    PL_FMT_CAP_HOST_READABLE = 1 << 9,  // may be used with `host_readable` textures
    PL_FMT_CAP_READWRITE     = 1 << 10, // may be used with PL_DESC_ACCESS_READWRITE

    // Notes:
    // - PL_FMT_CAP_LINEAR also implies PL_FMT_CAP_SAMPLEABLE
    // - PL_FMT_CAP_STORABLE also implies `pl_gpu.glsl.compute`
    // - PL_FMT_CAP_BLENDABLE implies PL_FMT_CAP_RENDERABLE
    // - PL_FMT_CAP_VERTEX implies that the format is non-opaque
    // - PL_FMT_CAP_HOST_READABLE implies that the format is non-opaque
};

struct pl_fmt_plane {
    // Underlying format of this particular sub-plane. This describes the
    // components, texel size and host representation for the purpose of
    // e.g. transfers, blits, and sampling.
    pl_fmt format;

    // X/Y subsampling shift factor for this plane.
    uint8_t shift_x, shift_y;
};

// Structure describing a texel/vertex format.
struct pl_fmt_t {
    const char *name;       // symbolic name for this format (e.g. rgba32f)
    uint64_t signature;     // unique but stable signature (for pass reusability)

    enum pl_fmt_type type;  // the format's data type and interpretation
    enum pl_fmt_caps caps;  // the features supported by this format
    int num_components;     // number of components for this format
    int component_depth[4]; // meaningful bits per component, texture precision
    size_t internal_size;   // internal texel size (for blit compatibility)

    // For planar formats, this provides a description of each sub-plane.
    //
    // Note on planar formats: Planar formats are always opaque and typically
    // support only a limit subset of capabilities (or none at all). Access
    // should be done via sub-planes. (See `pl_tex.planes`)
    struct pl_fmt_plane planes[4];
    int num_planes;         // or 0 for non-planar textures

    // This controls the relationship between the data as seen by the host and
    // the way it's interpreted by the texture. The host representation is
    // always tightly packed (no padding bits in between each component).
    //
    // This representation assumes little endian ordering, i.e. components
    // being ordered from LSB to MSB in memory. Note that for oddly packed
    // formats like rgb10a2 or rgb565, this is inconsistent with the naming.
    // (That is to say, rgb565 has sample order {2, 1, 0} under this convention
    // - because rgb565 treats the R channel as the *most* significant bits)
    //
    // If `opaque` is true, then there's no meaningful correspondence between
    // the two, and all of the remaining fields in this section are unset.
    //
    // If `emulated` is true, then this format doesn't actually exist on the
    // GPU as an uploadable texture format - and any apparent support is being
    // emulated (typically using compute shaders in the upload path).
    bool opaque;
    bool emulated;
    size_t texel_size;      // total size in bytes per texel
    size_t texel_align;     // texel alignment requirements (bytes)
    int host_bits[4];       // number of meaningful bits in host memory
    int sample_order[4];    // sampled index for each component, e.g.
                            // {2, 1, 0, 3} for BGRA textures

    // For sampleable formats, this bool indicates whether or not the format
    // is compatible with `textureGather()`
    bool gatherable;

    // If usable as a vertex or texel buffer format, this gives the GLSL type
    // corresponding to the data. (e.g. vec4)
    const char *glsl_type;

    // If usable as a storage image or texel storage buffer
    // (PL_FMT_CAP_STORABLE / PL_FMT_CAP_TEXEL_STORAGE), this gives the GLSL
    // texel format corresponding to the format (e.g. rgba16ui), if any. This
    // field may be NULL, in which case the format modifier may be left
    // unspecified.
    const char *glsl_format;

    // If available, this gives the fourcc associated with the host
    // representation. In particular, this is intended for use with
    // PL_HANDLE_DMA_BUF, where this field will match the DRM format from
    // <drm_fourcc.h>. May be 0, for formats without matching DRM fourcc.
    uint32_t fourcc;

    // If `fourcc` is set, this contains the list of supported drm format
    // modifiers for this format.
    const uint64_t *modifiers;
    int num_modifiers;
};

// Returns whether or not a pl_fmt's components are ordered sequentially
// in memory in the order RGBA.
PL_API bool pl_fmt_is_ordered(pl_fmt fmt);

// Returns whether or not a pl_fmt is sampled as a float (e.g. UNORM)
PL_API bool pl_fmt_is_float(pl_fmt fmt);

// Returns whether or not a pl_fmt supports a given DRM modifier.
PL_API bool pl_fmt_has_modifier(pl_fmt fmt, uint64_t modifier);

// Helper function to find a format with a given number of components and
// minimum effective precision per component. If `host_bits` is set, then the
// format will always be non-opaque, unpadded, ordered and have exactly this
// bit depth for each component. Finally, all `caps` must be supported.
PL_API pl_fmt pl_find_fmt(pl_gpu gpu, enum pl_fmt_type type, int num_components,
                          int min_depth, int host_bits, enum pl_fmt_caps caps);

// Finds a vertex format for a given configuration. The resulting vertex will
// have a component depth equivalent to the sizeof() the equivalent host type.
// (e.g. PL_FMT_FLOAT will always have sizeof(float))
PL_API pl_fmt pl_find_vertex_fmt(pl_gpu gpu, enum pl_fmt_type type, int num_components);

// Find a format based on its name.
PL_API pl_fmt pl_find_named_fmt(pl_gpu gpu, const char *name);

// Find a format based on its fourcc.
PL_API pl_fmt pl_find_fourcc(pl_gpu gpu, uint32_t fourcc);

// A generic 'timer query' object. These can be used to measure an
// approximation of the GPU execution time of a given operation. Due to the
// highly asynchronous nature of GPUs, the actual results of any individual
// timer query may be delayed by quite a bit. As such, users should avoid
// trying to pair any particular GPU command with any particular timer query
// result, and only reuse `pl_timer` objects with identical operations. The
// results of timer queries are guaranteed to be in-order, but individual
// queries may be dropped, and some operations might not record timer results
// at all. (For example, if the underlying hardware does not support timer
// queries for a given operation type)
//
// Thread-safety: Unsafe
typedef struct pl_timer_t *pl_timer;

// Creates a new timer object. This may return NULL, for example if the
// implementation does not support timers, but since passing NULL to
// `pl_timer_destroy` and `pl_timer_query` is safe, users generally need not
// concern themselves with handling this.
PL_API pl_timer pl_timer_create(pl_gpu gpu);
PL_API void pl_timer_destroy(pl_gpu gpu, pl_timer *);

// Queries any results that have been measured since the last execution of
// `pl_timer_query`. There may be more than one result, in which case the user
// should simply call the function again to get the subsequent values. This
// function returns a value of 0 in the event that there are no more
// unprocessed results.
//
// The results are reported in nanoseconds, but the actual precision of the
// timestamp queries may be significantly lower.
//
// Note: Results do not queue up indefinitely. Generally, the implementation
// will only keep track of a small, fixed number of results internally. Make
// sure to include this function as part of your main rendering loop to process
// all of its results, or older results will be overwritten by newer ones.
PL_API uint64_t pl_timer_query(pl_gpu gpu, pl_timer);

enum pl_buf_mem_type {
    PL_BUF_MEM_AUTO = 0, // use whatever seems most appropriate
    PL_BUF_MEM_HOST,     // try allocating from host memory (RAM)
    PL_BUF_MEM_DEVICE,   // try allocating from device memory (VRAM)
    PL_BUF_MEM_TYPE_COUNT,

    // Note: This distinction only matters for discrete GPUs
};

// Structure describing a buffer.
struct pl_buf_params {
    size_t size;        // size in bytes (must be <= `pl_gpu_limits.max_buf_size`)
    bool host_writable; // contents may be updated via pl_buf_write()
    bool host_readable; // contents may be read back via pl_buf_read()
    bool host_mapped;   // create a persistent, RW mapping (pl_buf.data)

    // May be used as PL_DESC_BUF_UNIFORM or PL_DESC_BUF_TEXEL_UNIFORM.
    // Requires `size <= pl_gpu_limits.max_ubo_size`
    bool uniform;

    // May be used as PL_DESC_BUF_STORAGE or PL_DESC_BUF_TEXEL_STORAGE.
    // Requires `size <= pl_gpu_limits.max_ssbo_size`
    bool storable;

    // May be used as the source of vertex data for `pl_pass_run`.
    bool drawable;

    // Provide a hint for the memory type you want to use when allocating
    // this buffer's memory.
    //
    // Note: Restrictions may apply depending on the usage flags. In
    // particular, allocating buffers with `uniform` or `storable` enabled from
    // non-device memory will almost surely fail.
    enum pl_buf_mem_type memory_type;

    // Setting this to a format with the `PL_FMT_CAP_TEXEL_*` capability allows
    // this buffer to be used as a `PL_DESC_BUF_TEXEL_*`, when `uniform` and
    // `storage` are respectively also enabled.
    pl_fmt format;

    // At most one of `export_handle` and `import_handle` can be set for a
    // buffer.

    // Setting this indicates that the memory backing this buffer should be
    // shared with external APIs, If so, this must be exactly *one* of
    // `pl_gpu.export_caps.buf`.
    enum pl_handle_type export_handle;

    // Setting this indicates that the memory backing this buffer will be
    // imported from an external API. If so, this must be exactly *one* of
    // `pl_gpu.import_caps.buf`.
    enum pl_handle_type import_handle;

    // If the shared memory is being imported, the import handle must be
    // specified here. Otherwise, this is ignored.
    struct pl_shared_mem shared_mem;

    // If non-NULL, the buffer will be created with these contents. Otherwise,
    // the initial data is undefined. Using this does *not* require setting
    // host_writable.
    const void *initial_data;

    // Arbitrary user data. libplacebo does not use this at all.
    void *user_data;

    // Arbitrary identifying tag. Used only for debugging purposes.
    pl_debug_tag debug_tag;
};

#define pl_buf_params(...) (&(struct pl_buf_params) {   \
        .debug_tag = PL_DEBUG_TAG,                      \
        __VA_ARGS__                                     \
    })

// A generic buffer, which can be used for multiple purposes (texture transfer,
// storage buffer, uniform buffer, etc.)
//
// Note on efficiency: A pl_buf does not necessarily represent a true "buffer"
// object on the underlying graphics API. It may also refer to a sub-slice of
// a larger buffer, depending on the implementation details of the GPU. The
// bottom line is that users do not need to worry about the efficiency of using
// many small pl_buf objects. Having many small pl_bufs, even lots of few-byte
// vertex buffers, is designed to be completely fine.
//
// Thread-safety: Unsafe
typedef const struct pl_buf_t {
    struct pl_buf_params params;
    uint8_t *data; // for persistently mapped buffers, points to the first byte

    // If `params.handle_type` is set, this structure references the shared
    // memory backing this buffer, via the requested handle type.
    //
    // While this buffer is not in an "exported" state, the contents of the
    // memory are undefined. (See: `pl_buf_export`)
    struct pl_shared_mem shared_mem;
} *pl_buf;

// Create a buffer. The type of buffer depends on the parameters. The buffer
// parameters must adhere to the restrictions imposed by the pl_gpu_limits.
// Returns NULL on failure.
//
// For buffers with shared memory, the buffer is considered to be in an
// "exported" state by default, and may be used directly by the external API
// after being created (until the first libplacebo operation on the buffer).
PL_API pl_buf pl_buf_create(pl_gpu gpu, const struct pl_buf_params *params);
PL_API void pl_buf_destroy(pl_gpu gpu, pl_buf *buf);

// This behaves like `pl_buf_create`, but if the buffer already exists and has
// incompatible parameters, it will get destroyed first. A buffer is considered
// "compatible" if it has the same buffer type and texel format, a size greater
// than or equal to the requested size, and it has a superset of the features
// the user requested. After this operation, the contents of the buffer are
// undefined.
//
// Note: Due to its unpredictability, it's not allowed to use this with
// `params->initial_data` being set. Similarly, it's not allowed on a buffer
// with `params->export_handle`. since this may invalidate the corresponding
// external API's handle. Conversely, it *is* allowed on a buffer with
// `params->host_mapped`, and the corresponding `buf->data` pointer *may*
// change as a result of doing so.
//
// Note: If the `user_data` alone changes, this does not trigger a buffer
// recreation. In theory, this can be used to detect when the buffer ended
// up being recreated.
PL_API bool pl_buf_recreate(pl_gpu gpu, pl_buf *buf, const struct pl_buf_params *params);

// Update the contents of a buffer, starting at a given offset (must be a
// multiple of 4) and up to a given size, with the contents of *data.
//
// This function will block until the buffer is no longer in use. Use
// `pl_buf_poll` to perform non-blocking queries of buffer availability.
//
// Note: This function can incur synchronization overhead, so it shouldn't be
// used in tight loops. If you do need to loop (e.g. to perform a strided
// write), consider using host-mapped buffers, or fixing the memory in RAM,
// before calling this function.
PL_API void pl_buf_write(pl_gpu gpu, pl_buf buf, size_t buf_offset,
                         const void *data, size_t size);

// Read back the contents of a buffer, starting at a given offset, storing the
// data into *dest. Returns whether successful.
//
// This function will block until the buffer is no longer in use. Use
// `pl_buf_poll` to perform non-blocking queries of buffer availability.
PL_API bool pl_buf_read(pl_gpu gpu, pl_buf buf, size_t buf_offset,
                        void *dest, size_t size);

// Copy `size` bytes from one buffer to another, reading from and writing to
// the respective offsets.
PL_API void pl_buf_copy(pl_gpu gpu, pl_buf dst, size_t dst_offset,
                        pl_buf src, size_t src_offset, size_t size);

// Initiates a buffer export operation, allowing a buffer to be accessed by an
// external API. This is only valid for buffers with `params.handle_type`.
// Calling this twice in a row is a harmless no-op. Returns whether successful.
//
// There is no corresponding "buffer import" operation, the next libplacebo
// operation that touches the buffer (e.g. pl_tex_upload, but also pl_buf_write
// and pl_buf_read) will implicitly import the buffer back to libplacebo. Users
// must ensure that all pending operations made by the external API are fully
// completed before using it in libplacebo again. (Otherwise, the behaviour
// is undefined)
//
// Please note that this function returning does not mean the memory is
// immediately available as such. In general, it will mark a buffer as "in use"
// in the same way any other buffer operation would, and it is the user's
// responsibility to wait until `pl_buf_poll` returns false before accessing
// the memory from the external API.
//
// In terms of the access performed by this operation, it is not considered a
// "read" or "write" and therefore does not technically conflict with reads or
// writes to the buffer performed by the host (via mapped memory - any use of
// `pl_buf_read` or `pl_buf_write` would defeat the purpose of the export).
// However, restrictions made by the external API may apply that prevent this.
//
// The recommended use pattern is something like this:
//
// while (loop) {
//    pl_buf buf = get_free_buffer(); // or block on pl_buf_poll
//    // write to the buffer using the external API
//    pl_tex_upload(gpu, /* ... buf ... */); // implicitly imports
//    pl_buf_export(gpu, buf);
// }
//
// i.e. perform an external API operation, then use and immediately export the
// buffer in libplacebo, and finally wait until `pl_buf_poll` is false before
// re-using it in the external API. (Or get a new buffer in the meantime)
PL_API bool pl_buf_export(pl_gpu gpu, pl_buf buf);

// Returns whether or not a buffer is currently "in use". This can either be
// because of a pending read operation, a pending write operation or a pending
// buffer export operation. Any access to the buffer by external APIs or via
// the host pointer (for host-mapped buffers) is forbidden while a buffer is
// "in use". The only exception to this rule is multiple reads, for example
// reading from a buffer with `pl_tex_upload` while simultaneously reading from
// it using mapped memory.
//
// The `timeout`, specified in nanoseconds, indicates how long to block for
// before returning. If set to 0, this function will never block, and only
// returns the current status of the buffer. The actual precision of the
// timeout may be significantly longer than one nanosecond, and has no upper
// bound. This function does not provide hard latency guarantees. This function
// may also return at any time, even if the buffer is still in use. If the user
// wishes to block until the buffer is definitely no longer in use, the
// recommended usage is:
//
// while (pl_buf_poll(gpu, buf, UINT64_MAX))
//      ; // do nothing
//
// Note: libplacebo operations on buffers are always internally synchronized,
// so this is only needed for host-mapped or externally exported buffers.
// However, it may be used to do non-blocking queries before calling blocking
// functions such as `pl_buf_read`.
//
// Note: If `pl_gpu_limits.thread_safe` is set, this function is implicitly
// synchronized, meaning it can safely be called on a `pl_buf` that is in use
// by another thread.
PL_API bool pl_buf_poll(pl_gpu gpu, pl_buf buf, uint64_t timeout);

enum pl_tex_sample_mode {
    PL_TEX_SAMPLE_NEAREST,  // nearest neighbour sampling
    PL_TEX_SAMPLE_LINEAR,   // linear filtering, requires PL_FMT_CAP_LINEAR
    PL_TEX_SAMPLE_MODE_COUNT,
};

enum pl_tex_address_mode {
    PL_TEX_ADDRESS_CLAMP,  // clamp the nearest edge texel
    PL_TEX_ADDRESS_REPEAT, // repeat (tile) the texture
    PL_TEX_ADDRESS_MIRROR, // repeat (mirror) the texture
    PL_TEX_ADDRESS_MODE_COUNT,
};

// Structure describing a texture.
struct pl_tex_params {
    int w, h, d;            // physical dimension; unused dimensions must be 0
    pl_fmt format;

    // The following bools describe what operations can be performed. The
    // corresponding pl_fmt capability must be set for every enabled
    // operation type.
    //
    // Note: For planar formats, it is also possible to set capabilities only
    // supported by sub-planes. In this case, the corresponding functionality
    // will be available for the sub-plane, but not the planar texture itself.
    bool sampleable;    // usable as a PL_DESC_SAMPLED_TEX
    bool renderable;    // usable as a render target (pl_pass_run)
                        // (must only be used with 2D textures)
    bool storable;      // usable as a storage image (PL_DESC_IMG_*)
    bool blit_src;      // usable as a blit source
    bool blit_dst;      // usable as a blit destination
    bool host_writable; // may be updated with pl_tex_upload()
    bool host_readable; // may be fetched with pl_tex_download()

    // Note: For `blit_src`, `blit_dst`, the texture must either be
    // 2-dimensional or `pl_gpu_limits.blittable_1d_3d` must be set.

    // At most one of `export_handle` and `import_handle` can be set for a
    // texture.

    // Setting this indicates that the memory backing this texture should be
    // shared with external APIs, If so, this must be exactly *one* of
    // `pl_gpu.export_caps.tex`.
    enum pl_handle_type export_handle;

    // Setting this indicates that the memory backing this texture will be
    // imported from an external API. If so, this must be exactly *one* of
    // `pl_gpu.import_caps.tex`. Mutually exclusive with `initial_data`.
    enum pl_handle_type import_handle;

    // If the shared memory is being imported, the import handle must be
    // specified here. Otherwise, this is ignored.
    struct pl_shared_mem shared_mem;

    // If non-NULL, the texture will be created with these contents (tightly
    // packed). Using this does *not* require setting host_writable. Otherwise,
    // the initial data is undefined. Mutually exclusive with `import_handle`.
    const void *initial_data;

    // Arbitrary user data. libplacebo does not use this at all.
    void *user_data;

    // Arbitrary identifying tag. Used only for debugging purposes.
    pl_debug_tag debug_tag;
};

#define pl_tex_params(...) (&(struct pl_tex_params) {   \
        .debug_tag = PL_DEBUG_TAG,                      \
        __VA_ARGS__                                     \
    })

static inline int pl_tex_params_dimension(const struct pl_tex_params params)
{
    return params.d ? 3 : params.h ? 2 : 1;
}

enum pl_sampler_type {
    PL_SAMPLER_NORMAL,      // gsampler2D, gsampler3D etc.
    PL_SAMPLER_RECT,        // gsampler2DRect
    PL_SAMPLER_EXTERNAL,    // gsamplerExternalOES
    PL_SAMPLER_EXTERNAL_YUV, // __samplerExternal2DY2YEXT (raw YUV)
    PL_SAMPLER_TYPE_COUNT,
};

// Conflates the following typical GPU API concepts:
// - texture itself
// - sampler state
// - staging buffers for texture upload
// - framebuffer objects
// - wrappers for swapchain framebuffers
// - synchronization needed for upload/rendering/etc.
//
// Essentially a pl_tex can be anything ranging from a normal texture, a wrapped
// external/real framebuffer, a framebuffer object + texture pair, a mapped
// texture (via pl_hwdec), or other sorts of things that can be sampled from
// and/or rendered to.
//
// Thread-safety: Unsafe
typedef const struct pl_tex_t *pl_tex;
struct pl_tex_t {
    struct pl_tex_params params;

    // If `params.format` is a planar format, this contains `pl_tex` handles
    // encapsulating individual texture planes. Conversely, if this is a
    // sub-plane of a planar texture, `parent` points to the planar texture.
    //
    // Note: Calling `pl_tex_destroy` on sub-planes is undefined behavior.
    pl_tex planes[4];
    pl_tex parent;

    // If `params.export_handle` is set, this structure references the shared
    // memory backing this buffer, via the requested handle type.
    //
    // While this texture is not in an "exported" state, the contents of the
    // memory are undefined. (See: `pl_tex_export`)
    //
    // Note: Due to vulkan driver limitations, `shared_mem.drm_format_mod` will
    // currently always be set to DRM_FORMAT_MOD_INVALID. No guarantee can be
    // made about the cross-driver compatibility of textures exported this way.
    struct pl_shared_mem shared_mem;

    // If `params.sampleable` is true, this indicates the correct sampler type
    // to use when sampling from this texture.
    enum pl_sampler_type sampler_type;

    // Backend-specific sampler identity. A non-zero value means render passes
    // must be specialized for the immutable sampler attached to this texture.
    uint64_t sampler_signature;
};

// Create a texture (with undefined contents). Returns NULL on failure. This is
// assumed to be an expensive/rare operation, and may need to perform memory
// allocation or framebuffer creation.
PL_API pl_tex pl_tex_create(pl_gpu gpu, const struct pl_tex_params *params);
PL_API void pl_tex_destroy(pl_gpu gpu, pl_tex *tex);

// This works like `pl_tex_create`, but if the texture already exists and has
// incompatible texture parameters, it will get destroyed first. A texture is
// considered "compatible" if it has the same texture format and sample/address
// mode and it supports a superset of the features the user requested.
//
// Even if the texture is not recreated, calling this function will still
// invalidate the contents of the texture. (Note: Because of this,
// `initial_data` may not be used with `pl_tex_recreate`. Doing so is an error)
//
// Note: If the `user_data` alone changes, this does not trigger a texture
// recreation. In theory, this can be used to detect when the texture ended
// up being recreated.
PL_API bool pl_tex_recreate(pl_gpu gpu, pl_tex *tex, const struct pl_tex_params *params);

// Invalidates the contents of a texture. After this, the contents are fully
// undefined.
PL_API void pl_tex_invalidate(pl_gpu gpu, pl_tex tex);

union pl_clear_color {
    float f[4];
    int32_t i[4];
    uint32_t u[4];
};

// Clear the dst texture with the given color (rgba). This is functionally
// identical to a blit operation, which means `dst->params.blit_dst` must be
// set.
PL_API void pl_tex_clear_ex(pl_gpu gpu, pl_tex dst, const union pl_clear_color color);

// Wrapper for `pl_tex_clear_ex` which only works for floating point textures.
PL_API void pl_tex_clear(pl_gpu gpu, pl_tex dst, const float color[4]);

struct pl_tex_blit_params {
    // The texture to blit from. Must have `params.blit_src` enabled.
    pl_tex src;

    // The texture to blit to. Must have `params.blit_dst` enabled, and a
    // format that is loosely compatible with `src`. This essentially means
    // that they must have the same `internal_size`. Additionally, UINT
    // textures can only be blitted to other UINT textures, and SINT textures
    // can only be blitted to other SINT textures.
    pl_tex dst;

    // The region of the source texture to blit. Must be within the texture
    // bounds of `src`. May be flipped. (Optional)
    pl_rect3d src_rc;

    // The region of the destination texture to blit into. Must be within the
    // texture bounds of `dst`. May be flipped. Areas outside of `dst_rc` in
    // `dst` are preserved. (Optional)
    pl_rect3d dst_rc;

    // If `src_rc` and `dst_rc` have different sizes, the texture will be
    // scaled using the given texture sampling mode.
    enum pl_tex_sample_mode sample_mode;
};

#define pl_tex_blit_params(...) (&(struct pl_tex_blit_params) { __VA_ARGS__ })

// Copy a sub-rectangle from one texture to another.
PL_API void pl_tex_blit(pl_gpu gpu, const struct pl_tex_blit_params *params);

// Structure describing a texture transfer operation.
struct pl_tex_transfer_params {
    // Texture to transfer to/from. Depending on the type of the operation,
    // this must have params.host_writable (uploads) or params.host_readable
    // (downloads) set, respectively.
    pl_tex tex;

    // Note: Superfluous parameters are ignored, i.e. for a 1D texture, the y
    // and z fields of `rc`, as well as the corresponding pitches, are ignored.
    // In all other cases, the pitch must be large enough to contain the
    // corresponding dimension of `rc`, and the `rc` must be normalized and
    // fully contained within the image dimensions. Missing fields in the `rc`
    // are inferred from the image size. If unset, the pitch is inferred
    // from `rc` (that is, it's assumed that the data is tightly packed in the
    // buffer). Otherwise, `row_pitch` *must* be a multiple of
    // `tex->params.format->texel_align`, and `depth_pitch` must be a multiple
    // of `row_pitch`.
    pl_rect3d rc;       // region of the texture to transfer
    size_t row_pitch;   // the number of bytes separating image rows
    size_t depth_pitch; // the number of bytes separating image planes

    // An optional timer to report the approximate duration of the texture
    // transfer to. Note that this is only an approximation, since the actual
    // texture transfer may happen entirely in the background (in particular,
    // for implementations with asynchronous transfer capabilities). It's also
    // not guaranteed that all GPUs support this.
    pl_timer timer;

    // An optional callback to fire after the operation completes. If this is
    // specified, then the operation is performed asynchronously. Note that
    // transfers to/from buffers are always asynchronous, even without, this
    // field, so it's more useful for `ptr` transfers. (Though it can still be
    // helpful to avoid having to manually poll buffers all the time)
    //
    // When this is *not* specified, uploads from `ptr` are either synchronous,
    // or internally asynchronous but with an extra host memcpy, while
    // downloads from `ptr` are blocking. As such, it's recommended to always
    // try using asynchronous texture transfers wherever possible.
    //
    // Note: Requires `pl_gpu_limits.callbacks`
    //
    // Note: Callbacks are implicitly synchronized, meaning that callbacks are
    // guaranteed to never execute concurrently with other callbacks. However,
    // they may execute from any thread that the `pl_gpu` is used on.
    void (*callback)(void *priv);
    void *priv; // arbitrary user data

    // For the data source/target of a transfer operation, there are two valid
    // options:
    //
    // 1. Transferring to/from a buffer: (requires `pl_gpu_limits.buf_transfer`)
    pl_buf buf;         // buffer to use
    size_t buf_offset;  // offset of data within buffer, should be a
                        // multiple of `tex->params.format->texel_size`
    // 2. Transferring to/from host memory directly:
    void *ptr;          // address of data
    bool no_import;     // always use memcpy, bypassing host ptr import

    // Note: The contents of the memory region / buffer must exactly match the
    // texture format; i.e. there is no explicit conversion between formats.
};

#define pl_tex_transfer_params(...) (&(struct pl_tex_transfer_params) { __VA_ARGS__ })

// Upload data to a texture. Returns whether successful.
PL_API bool pl_tex_upload(pl_gpu gpu, const struct pl_tex_transfer_params *params);

// Download data from a texture. Returns whether successful.
PL_API bool pl_tex_download(pl_gpu gpu, const struct pl_tex_transfer_params *params);

// Returns whether or not a texture is currently "in use". This can either be
// because of a pending read operation, a pending write operation or a pending
// texture export operation. Note that this function's usefulness is extremely
// limited under ordinary circumstances. In practically all cases, textures do
// not need to be directly synchronized by the user, except when interfacing
// with external libraries. This function should NOT, however, be used as a
// crutch to avoid having to implement semaphore-based synchronization. Use
// the API-specific functions such as `pl_vulkan_hold/release` for that.
//
// A good example of a use case in which this function is required is when
// interoperating with external memory management that needs to know when an
// imported texture is safe to free / reclaim internally, in which case
// semaphores are insufficient because memory management is a host operation.
//
// The `timeout`, specified in nanoseconds, indicates how long to block for
// before returning. If set to 0, this function will never block, and only
// returns the current status of the texture. The actual precision of the
// timeout may be significantly longer than one nanosecond, and has no upper
// bound. This function does not provide hard latency guarantees. This function
// may also return at any time, even if the texture is still in use. If the
// user wishes to block until the texture is definitely no longer in use, the
// recommended usage is:
//
// while (pl_tex_poll(gpu, buf, UINT64_MAX))
//      ; // do nothing
//
// Note: If `pl_gpu_limits.thread_safe` is set, this function is implicitly
// synchronized, meaning it can safely be called on a `pl_tex` that is in use
// by another thread.
PL_API bool pl_tex_poll(pl_gpu gpu, pl_tex tex, uint64_t timeout);

// Data type of a shader input variable (e.g. uniform, or UBO member)
enum pl_var_type {
    PL_VAR_INVALID = 0,
    PL_VAR_SINT,        // C: int           GLSL: int/ivec
    PL_VAR_UINT,        // C: unsigned int  GLSL: uint/uvec
    PL_VAR_FLOAT,       // C: float         GLSL: float/vec/mat
    PL_VAR_TYPE_COUNT
};

// Returns the host size (in bytes) of a pl_var_type.
PL_API size_t pl_var_type_size(enum pl_var_type type);

// Represents a shader input variable (concrete data, e.g. vector, matrix)
struct pl_var {
    const char *name;       // name as used in the shader
    enum pl_var_type type;
    // The total number of values is given by dim_v * dim_m. For example, a
    // vec2 would have dim_v = 2 and dim_m = 1. A mat3x4 would have dim_v = 4
    // and dim_m = 3.
    int dim_v;              // vector dimension
    int dim_m;              // matrix dimension (number of columns, see below)
    int dim_a;              // array dimension
};

// Helper functions for constructing the most common pl_vars, with names
// corresponding to their corresponding GLSL built-in types.
PL_API struct pl_var pl_var_float(const char *name);
PL_API struct pl_var pl_var_vec2(const char *name);
PL_API struct pl_var pl_var_vec3(const char *name);
PL_API struct pl_var pl_var_vec4(const char *name);
PL_API struct pl_var pl_var_mat2(const char *name);
PL_API struct pl_var pl_var_mat2x3(const char *name);
PL_API struct pl_var pl_var_mat2x4(const char *name);
PL_API struct pl_var pl_var_mat3(const char *name);
PL_API struct pl_var pl_var_mat3x4(const char *name);
PL_API struct pl_var pl_var_mat4x2(const char *name);
PL_API struct pl_var pl_var_mat4x3(const char *name);
PL_API struct pl_var pl_var_mat4(const char *name);
PL_API struct pl_var pl_var_int(const char *name);
PL_API struct pl_var pl_var_ivec2(const char *name);
PL_API struct pl_var pl_var_ivec3(const char *name);
PL_API struct pl_var pl_var_ivec4(const char *name);
PL_API struct pl_var pl_var_uint(const char *name);
PL_API struct pl_var pl_var_uvec2(const char *name);
PL_API struct pl_var pl_var_uvec3(const char *name);
PL_API struct pl_var pl_var_uvec4(const char *name);

struct pl_named_var {
    const char *glsl_name;
    struct pl_var var;
};

// The same list as above, tagged by name and terminated with a {0} entry.
PL_API extern const struct pl_named_var pl_var_glsl_types[];

// Efficient helper function for performing a lookup in the above array.
// Returns NULL if the variable is not legal. Note that the array dimension is
// ignored, since it's usually part of the variable name and not the type name.
PL_API const char *pl_var_glsl_type_name(struct pl_var var);

// Converts a pl_fmt to an "equivalent" pl_var. Equivalent in this sense means
// that the pl_var's type will be the same as the vertex's sampled type (e.g.
// PL_FMT_UNORM gets turned into PL_VAR_FLOAT).
PL_API struct pl_var pl_var_from_fmt(pl_fmt fmt, const char *name);

// Describes the memory layout of a variable, relative to some starting location
// (typically the offset within a uniform/storage/pushconstant buffer)
//
// Note on matrices: All GPUs expect column major matrices, for both buffers and
// input variables. Care needs to be taken to avoid trying to use e.g. a
// pl_matrix3x3 (which is row major) directly as a pl_var_update.data!
//
// In terms of the host layout, a column-major matrix (e.g. matCxR) with C
// columns and R rows is treated like an array vecR[C]. The `stride` here refers
// to the separation between these array elements, i.e. the separation between
// the individual columns.
//
// Visualization of a mat4x3:
//
//       0   1   2   3  <- columns
// 0  [ (A) (D) (G) (J) ]
// 1  [ (B) (E) (H) (K) ]
// 2  [ (C) (F) (I) (L) ]
// ^ rows
//
// Layout in GPU memory: (stride=16, size=60)
//
// [ A B C ] X <- column 0, offset +0
// [ D E F ] X <- column 1, offset +16
// [ G H I ] X <- column 2, offset +32
// [ J K L ]   <- column 3, offset +48
//
// Note the lack of padding on the last column in this example.
// In general: size <= stride * dim_m
//
// C representation: (stride=12, size=48)
//
// { { A, B, C },
//   { D, E, F },
//   { G, H, I },
//   { J, K, L } }
//
// Note on arrays: `stride` represents both the stride between elements of a
// matrix, and the stride between elements of an array. That is, there is no
// distinction between the columns of a matrix and the rows of an array. For
// example, a mat2[10] and a vec2[20] share the same pl_var_layout - the stride
// would be sizeof(vec2) and the size would be sizeof(vec2) * 2 * 10.
//
// For non-array/matrix types, `stride` is equal to `size`.

struct pl_var_layout {
    size_t offset; // the starting offset of the first byte
    size_t stride; // the delta between two elements of an array/matrix
    size_t size;   // the total size of the input
};

// Returns the host layout of an input variable as required for a
// tightly-packed, byte-aligned C data type, given a starting offset.
PL_API struct pl_var_layout pl_var_host_layout(size_t offset, const struct pl_var *var);

// Returns the GLSL std140 layout of an input variable given a current buffer
// offset, as required for a buffer descriptor of type PL_DESC_BUF_UNIFORM
//
// The normal way to use this function is when calculating the size and offset
// requirements of a uniform buffer in an incremental fashion, to calculate the
// new offset of the next variable in this buffer.
PL_API struct pl_var_layout pl_std140_layout(size_t offset, const struct pl_var *var);

// Returns the GLSL std430 layout of an input variable given a current buffer
// offset, as required for a buffer descriptor of type PL_DESC_BUF_STORAGE, and
// for push constants.
PL_API struct pl_var_layout pl_std430_layout(size_t offset, const struct pl_var *var);

// Convenience definitions / friendly names for these
#define pl_buf_uniform_layout pl_std140_layout
#define pl_buf_storage_layout pl_std430_layout
#define pl_push_constant_layout pl_std430_layout

// Like memcpy, but copies bytes from `src` to `dst` in a manner governed by
// the stride and size of `dst_layout` as well as `src_layout`. Also takes
// into account the respective `offset`.
PL_API void memcpy_layout(void *dst, struct pl_var_layout dst_layout,
                          const void *src, struct pl_var_layout src_layout);

// Represents a compile-time constant.
struct pl_constant {
    enum pl_var_type type;  // constant data type
    uint32_t id;            // GLSL `constant_id`
    size_t offset;          // byte offset in `constant_data`
};

// Represents a vertex attribute.
struct pl_vertex_attrib {
    const char *name;   // name as used in the shader
    pl_fmt fmt;         // data format (must have PL_FMT_CAP_VERTEX)
    size_t offset;      // byte offset into the vertex struct
    int location;       // vertex location (as used in the shader)
};

// Returns an abstract namespace index for a given descriptor type. This will
// always be a value >= 0 and < PL_DESC_TYPE_COUNT. Implementations can use
// this to figure out which descriptors may share the same value of `binding`.
// Bindings must only be unique for all descriptors within the same namespace.
PL_API int pl_desc_namespace(pl_gpu gpu, enum pl_desc_type type);

// Access mode of a shader input descriptor.
enum pl_desc_access {
    PL_DESC_ACCESS_READWRITE,
    PL_DESC_ACCESS_READONLY,
    PL_DESC_ACCESS_WRITEONLY,
    PL_DESC_ACCESS_COUNT,
};

// Returns the GLSL syntax for a given access mode (e.g. "readonly").
PL_API const char *pl_desc_access_glsl_name(enum pl_desc_access mode);

// Represents a shader descriptor (e.g. texture or buffer binding)
struct pl_desc {
    const char *name;       // name as used in the shader
    enum pl_desc_type type;

    // The binding of this descriptor, as used in the shader. All bindings
    // within a namespace must be unique. (see: pl_desc_namespace)
    int binding;

    // For storage images and storage buffers, this can be used to restrict
    // the type of access that may be performed on the descriptor. Ignored for
    // the other descriptor types (uniform buffers and sampled textures are
    // always read-only).
    enum pl_desc_access access;

    // Static sampler state used while creating sampled-texture descriptors.
    // `sampler_signature` is zero for ordinary dynamically bound samplers.
    uint64_t sampler_signature;
    enum pl_tex_address_mode address_mode;
    enum pl_tex_sample_mode sample_mode;
};

// Framebuffer blending mode (for raster passes)
enum pl_blend_mode {
    PL_BLEND_ZERO,
    PL_BLEND_ONE,
    PL_BLEND_SRC_ALPHA,
    PL_BLEND_ONE_MINUS_SRC_ALPHA,
    PL_BLEND_MODE_COUNT,
};

struct pl_blend_params {
    enum pl_blend_mode src_rgb;
    enum pl_blend_mode dst_rgb;
    enum pl_blend_mode src_alpha;
    enum pl_blend_mode dst_alpha;
};

#define pl_blend_params(...) (&(struct pl_blend_params) { __VA_ARGS__ })

// Typical alpha compositing
PL_API extern const struct pl_blend_params pl_alpha_overlay;

enum pl_prim_type {
    PL_PRIM_TRIANGLE_LIST,
    PL_PRIM_TRIANGLE_STRIP,
    PL_PRIM_TYPE_COUNT,
};

enum pl_index_format {
    PL_INDEX_UINT16 = 0,
    PL_INDEX_UINT32,
    PL_INDEX_FORMAT_COUNT,
};

enum pl_pass_type {
    PL_PASS_INVALID = 0,
    PL_PASS_RASTER,  // vertex+fragment shader
    PL_PASS_COMPUTE, // compute shader (requires `pl_gpu.glsl.compute`)
    PL_PASS_TYPE_COUNT,
};

// Description of a rendering pass. It conflates the following:
//  - GLSL shader(s) and its list of inputs
//  - target parameters (for raster passes)
struct pl_pass_params {
    enum pl_pass_type type;

    // Input variables.
    struct pl_var *variables;
    int num_variables;

    // Input descriptors.
    struct pl_desc *descriptors;
    int num_descriptors;

    // Compile-time specialization constants.
    struct pl_constant *constants;
    int num_constants;

    // Initial data for the specialization constants. Optional. If NULL,
    // specialization constants receive the values from the shader text.
    void *constant_data;

    // Push constant region. Must be be a multiple of 4 <= limits.max_pushc_size
    size_t push_constants_size;

    // The shader text in GLSL. For PL_PASS_RASTER, this is interpreted
    // as a fragment shader. For PL_PASS_COMPUTE, this is interpreted as
    // a compute shader.
    const char *glsl_shader;

    // --- type==PL_PASS_RASTER only

    // Describes the interpretation and layout of the vertex data.
    enum pl_prim_type vertex_type;
    struct pl_vertex_attrib *vertex_attribs;
    int num_vertex_attribs;
    size_t vertex_stride; // must be a multiple of limits.align_vertex_stride

    // The vertex shader itself.
    const char *vertex_shader;

    // Target format. The format must support PL_FMT_CAP_RENDERABLE. The
    // resulting pass may only be used on textures that have a format with a
    // `pl_fmt.signature` compatible to this format.
    pl_fmt target_format;

    // Target blending mode. If this is NULL, blending is disabled. Otherwise,
    // the `target_format` must also support PL_FMT_CAP_BLENDABLE.
    const struct pl_blend_params *blend_params;

    // If false, the target's existing contents will be discarded before the
    // pass is run. (Semantically equivalent to calling pl_tex_invalidate
    // before every pl_pass_run, but slightly more efficient)
    //
    // Specifying `blend_params` requires `load_target` to be true.
    bool load_target;

    // --- Deprecated / removed fields.
    PL_DEPRECATED_IN(v6.322) const uint8_t *cached_program; // Non-functional
    PL_DEPRECATED_IN(v6.322) size_t cached_program_len;
};

#define pl_pass_params(...) (&(struct pl_pass_params) { __VA_ARGS__ })

// Conflates the following typical GPU API concepts:
// - various kinds of shaders
// - rendering pipelines
// - descriptor sets, uniforms, other bindings
// - all synchronization necessary
// - the current values of all inputs
//
// Thread-safety: Unsafe
typedef const struct pl_pass_t {
    struct pl_pass_params params;
} *pl_pass;

// Compile a shader and create a render pass. This is a rare/expensive
// operation and may take a significant amount of time, even if a cached
// program is used. Returns NULL on failure.
PL_API pl_pass pl_pass_create(pl_gpu gpu, const struct pl_pass_params *params);
PL_API void pl_pass_destroy(pl_gpu gpu, pl_pass *pass);

struct pl_desc_binding {
    const void *object; // pl_* object with type corresponding to pl_desc_type

    // For PL_DESC_SAMPLED_TEX, this can be used to configure the sampler.
    enum pl_tex_address_mode address_mode;
    enum pl_tex_sample_mode sample_mode;
};

struct pl_var_update {
    int index;        // index into params.variables[]
    const void *data; // pointer to raw byte data corresponding to pl_var_host_layout()
};

struct pl_pass_run_params {
    pl_pass pass;

    // If present, the shader will be re-specialized with the new constants
    // provided. This is a significantly cheaper operation than recompiling a
    // brand new shader, but should still be avoided if possible.
    //
    // Leaving it as NULL re-uses the existing specialization values. Ignored
    // if the shader has no specialization constants. Guaranteed to be a no-op
    // if the values have not changed since the last invocation.
    void *constant_data;

    // This list only contains descriptors/variables which have changed
    // since the previous invocation. All non-mentioned variables implicitly
    // preserve their state from the last invocation.
    struct pl_var_update *var_updates;
    int num_var_updates;

    // This list contains all descriptors used by this pass. It must
    // always be filled, even if the descriptors haven't changed. The order
    // must match that of pass->params.descriptors
    struct pl_desc_binding *desc_bindings;

    // The push constants for this invocation. This must always be set and
    // fully defined for every invocation if params.push_constants_size > 0.
    void *push_constants;

    // An optional timer to report the approximate runtime of this shader pass
    // invocation to. Note that this is only an approximation, since shaders
    // may overlap their execution times and contend for GPU time.
    pl_timer timer;

    // --- pass->params.type==PL_PASS_RASTER only

    // Target must be a 2D texture, `target->params.renderable` must be true,
    // and `target->params.format->signature` must match the signature provided
    // in `pass->params.target_format`.
    //
    // If the viewport or scissors are left blank, they are inferred from
    // target->params.
    //
    // WARNING: Rendering to a *target that is being read from by the same
    // shader is undefined behavior. In general, trying to bind the same
    // resource multiple times to the same shader is undefined behavior.
    pl_tex target;
    pl_rect2d viewport; // screen space viewport (must be normalized)
    pl_rect2d scissors; // target render scissors (must be normalized)

    // Number of vertices to render
    int vertex_count;

    // Vertex data may be provided in one of two forms:
    //
    // 1. Drawing from host memory directly
    const void *vertex_data;
    // 2. Drawing from a vertex buffer (requires `vertex_buf->params.drawable`)
    pl_buf vertex_buf;
    size_t buf_offset;

    // (Optional) Index data may be provided in the form given by `index_fmt`.
    // These will be used for instanced rendering. Similar to vertex data, this
    // can be provided in two forms:
    // 1. From host memory
    const void *index_data;
    enum pl_index_format index_fmt;
    // 2. From an index buffer (requires `index_buf->params.drawable`)
    pl_buf index_buf;
    size_t index_offset;
    // Note: Drawing from an index buffer requires vertex data to also be
    // present in buffer form, i.e. it's forbidden to mix `index_buf` with
    // `vertex_data` (though vice versa is allowed).

    // --- pass->params.type==PL_PASS_COMPUTE only

    // Number of work groups to dispatch per dimension (X/Y/Z). Must be <= the
    // corresponding index of limits.max_dispatch
    int compute_groups[3];
};

#define pl_pass_run_params(...) (&(struct pl_pass_run_params) { __VA_ARGS__ })

// Execute a render pass.
PL_API void pl_pass_run(pl_gpu gpu, const struct pl_pass_run_params *params);

// This is semantically a no-op, but it provides a hint that you want to flush
// any partially queued up commands and begin execution. There is normally no
// need to call this, because queued commands will always be implicitly flushed
// whenever necessary to make forward progress on commands like `pl_buf_poll`,
// or when submitting a frame to a swapchain for display. In fact, calling this
// function can negatively impact performance, because some GPUs rely on being
// able to re-order and modify queued commands in order to enable optimizations
// retroactively.
//
// The only time this might be beneficial to call explicitly is if you're doing
// lots of offline processing, i.e. you aren't rendering to a swapchain but to
// textures that you download from again. In that case you should call this
// function after each "work item" to ensure good parallelism between them.
//
// It's worth noting that this function may block if you're over-feeding the
// GPU without waiting for existing results to finish.
PL_API void pl_gpu_flush(pl_gpu gpu);

// This is like `pl_gpu_flush` but also blocks until the GPU is fully idle
// before returning. Using this in your rendering loop is seriously disadvised,
// and almost never the right solution. The intended use case is for deinit
// logic, where users may want to force the all pending GPU operations to
// finish so they can clean up their state more easily.
//
// After this operation is called, it's guaranteed that all pending buffer
// operations are complete - i.e. `pl_buf_poll` is guaranteed to return false.
// It's also guaranteed that any outstanding timer query results are available.
//
// Note: If you only care about buffer operations, you can accomplish this more
// easily by using `pl_buf_poll` with the timeout set to `UINT64_MAX`. But if
// you have many buffers it may be more convenient to call this function
// instead. The difference is that this function will also affect e.g. renders
// to a `pl_swapchain`.
PL_API void pl_gpu_finish(pl_gpu gpu);

// Returns true if the GPU is considered to be in a "failed" state, which
// during normal operation is typically the result of things like the device
// being lost (due to e.g. power management).
//
// If this returns true, users *should* destroy and recreate the `pl_gpu`,
// including all associated resources, via the appropriate mechanism.
PL_API bool pl_gpu_is_failed(pl_gpu gpu);

PL_API_END

#endif // LIBPLACEBO_GPU_H_
