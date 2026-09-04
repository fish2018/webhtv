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

#ifndef LIBPLACEBO_RENDERER_H_
#define LIBPLACEBO_RENDERER_H_

#include <libplacebo/config.h>
#include <libplacebo/colorspace.h>
#include <libplacebo/filters.h>
#include <libplacebo/gpu.h>
#include <libplacebo/shaders/colorspace.h>
#include <libplacebo/shaders/deinterlacing.h>
#include <libplacebo/shaders/dithering.h>
#include <libplacebo/shaders/film_grain.h>
#include <libplacebo/shaders/icc.h>
#include <libplacebo/shaders/lut.h>
#include <libplacebo/shaders/sampling.h>
#include <libplacebo/shaders/custom.h>
#include <libplacebo/swapchain.h>

PL_API_BEGIN

// Thread-safety: Unsafe
typedef struct pl_renderer_t *pl_renderer;

// Enum values used in pl_renderer_errors_t as a bit positions for error flags
enum pl_render_error {
    PL_RENDER_ERR_NONE              = 0,
    PL_RENDER_ERR_FBO               = 1 << 0,
    PL_RENDER_ERR_SAMPLING          = 1 << 1,
    PL_RENDER_ERR_DEBANDING         = 1 << 2,
    PL_RENDER_ERR_BLENDING          = 1 << 3,
    PL_RENDER_ERR_OVERLAY           = 1 << 4,
    PL_RENDER_ERR_PEAK_DETECT       = 1 << 5,
    PL_RENDER_ERR_FILM_GRAIN        = 1 << 6,
    PL_RENDER_ERR_FRAME_MIXING      = 1 << 7,
    PL_RENDER_ERR_DEINTERLACING     = 1 << 8,
    PL_RENDER_ERR_ERROR_DIFFUSION   = 1 << 9,
    PL_RENDER_ERR_HOOKS             = 1 << 10,
    PL_RENDER_ERR_CONTRAST_RECOVERY = 1 << 11,
    PL_RENDER_ERR_BLUR              = 1 << 12,
};

// Struct describing current renderer state, including internal processing errors,
// as well as list of signatures of disabled hooks.
struct pl_render_errors {
    enum pl_render_error errors;
    // List containing signatures of disabled hooks
    const uint64_t *disabled_hooks;
    int num_disabled_hooks;
};

// Creates a new renderer object, which is backed by a GPU context. This is a
// high-level object that takes care of the rendering chain as a whole, from
// the source textures to the finished frame.
PL_API pl_renderer pl_renderer_create(pl_log log, pl_gpu gpu);
PL_API void pl_renderer_destroy(pl_renderer *rr);

// Returns current renderer state, see pl_render_errors.
PL_API struct pl_render_errors pl_renderer_get_errors(pl_renderer rr);

// Clears errors state of renderer. If `errors` is NULL, all render errors will
// be cleared. Otherwise only selected errors/hooks will be cleared.
// If `PL_RENDER_ERR_HOOKS` is set and `num_disabled_hooks` is 0, clear all hooks.
// Otherwise only selected hooks will be cleard based on `disabled_hooks` array.
PL_API void pl_renderer_reset_errors(pl_renderer rr,
                                     const struct pl_render_errors *errors);

enum pl_lut_type {
    PL_LUT_UNKNOWN = 0,
    PL_LUT_NATIVE,      // applied to raw image contents (after fixing bit depth)
    PL_LUT_NORMALIZED,  // applied to normalized (HDR) RGB values
    PL_LUT_CONVERSION,  // LUT fully replaces color conversion

    // Note: When using a PL_LUT_CONVERSION to replace the YUV->RGB conversion,
    // `pl_render_params.color_adjustment` is no longer applied. Similarly,
    // when using a PL_LUT_CONVERSION to replace the image->target color space
    // conversion, `pl_render_params.color_map_params` are ignored.
    //
    // Note: For LUTs attached to the output frame, PL_LUT_CONVERSION should
    // instead perform the inverse (RGB->native) conversion.
    //
    // Note: PL_LUT_UNKNOWN tries inferring the meaning of the LUT from the
    // LUT's tagged metadata, and otherwise falls back to PL_LUT_NATIVE.
};

enum pl_clear_mode {
    PL_CLEAR_COLOR = 0, // set texture to a solid color
    PL_CLEAR_TILES,     // set texture to a tiled pattern
    PL_CLEAR_SKIP,      // skip the clearing pass (no-op)
    PL_CLEAR_BLUR,      // clear with a blurred copy of the image (border only)
    PL_CLEAR_MODE_COUNT,
};

enum pl_render_stage {
    PL_RENDER_STAGE_FRAME,  // full frame redraws, for fresh/uncached frames
    PL_RENDER_STAGE_BLEND,  // the output blend pass (only for pl_render_image_mix)
    PL_RENDER_STAGE_COUNT,
};

struct pl_render_info {
    const struct pl_dispatch_info *pass;    // information about the shader
    enum pl_render_stage stage;             // the associated render stage

    // This specifies the chronological index of this pass within the frame and
    // stage (starting at `index == 0`).
    int index;

    // For PL_RENDER_STAGE_BLEND, this specifies the number of frames
    // being blended (since that results in a different shader).
    int count;
};

// Represents the options used for rendering. These affect the quality of
// the result.
struct pl_render_params {
    // Configures the algorithms used for upscaling and downscaling,
    // respectively. If left as NULL, then libplacebo will only use inexpensive
    // sampling (bilinear or nearest neighbour depending on the capabilities
    // of the hardware / texture).
    //
    // Note: Setting `downscaler` to NULL also implies `skip_anti_aliasing`,
    // since the built-in GPU sampling algorithms can't anti-alias.
    //
    // Note: If set to the same address as the built-in `pl_filter_bicubic`,
    // `pl_filter_nearest` etc.; libplacebo will also use the more efficient
    // direct sampling algorithm where possible without quality loss.
    const struct pl_filter_config *upscaler;
    const struct pl_filter_config *downscaler;

    // If set, this overrides the value of `upscaler`/`downscaling` for
    // subsampled (chroma) planes. These scalers are used whenever the size of
    // multiple different `pl_plane`s in a single `pl_frame` differ, requiring
    // adaptation when converting to/from RGB. Note that a value of NULL simply
    // means "no override". To force built-in scaling explicitly, set this to
    // `&pl_filter_bilinear`.
    const struct pl_filter_config *plane_upscaler;
    const struct pl_filter_config *plane_downscaler;

    // The anti-ringing strength to apply to filters. See the equivalent option
    // in `pl_sample_filter_params` for more information.
    float antiringing_strength;

    // Configures the algorithm used for frame mixing (when using
    // `pl_render_image_mix`). Ignored otherwise. As a special requirement,
    // this must be a filter config with `polar` set to false, since it's only
    // used for 1D mixing and thus only 1D filters are compatible.
    //
    // If set to NULL, frame mixing is disabled, in which case
    // `pl_render_image_mix` will use nearest-neighbour semantics. (Note that
    // this still goes through the redraw cache, unless you also enable
    // `skip_caching_single_frame`)
    const struct pl_filter_config *frame_mixer;

    // Configures the settings used to deband source textures. Leaving this as
    // NULL disables debanding.
    //
    // Note: The `deband_params.grain` setting is automatically adjusted to
    // prevent blowing up on HDR sources. The user need not account for this.
    const struct pl_deband_params *deband_params;

    // Configures the settings used to sigmoidize the image before upscaling.
    // This is not always used. If NULL, disables sigmoidization.
    const struct pl_sigmoid_params *sigmoid_params;

    // Configures the color adjustment parameters used to decode the color.
    // This can be used to apply additional artistic settings such as
    // desaturation, etc. If NULL, defaults to &pl_color_adjustment_neutral.
    const struct pl_color_adjustment *color_adjustment;

    // Configures the settings used to detect the peak of the source content,
    // for HDR sources. Has no effect on SDR content. If NULL, peak detection
    // is disabled.
    const struct pl_peak_detect_params *peak_detect_params;

    // Configures the settings used to tone map from HDR to SDR, or from higher
    // gamut to standard gamut content. If NULL, defaults to
    // `&pl_color_map_default_params`.
    const struct pl_color_map_params *color_map_params;

    // Configures the settings used to dither to the output depth. Leaving this
    // as NULL disables dithering.
    const struct pl_dither_params *dither_params;

    // Configures the error diffusion kernel to use for error diffusion
    // dithering. If set, this will be used instead of `dither_params` whenever
    // possible. Leaving this as NULL disables error diffusion.
    const struct pl_error_diffusion_kernel *error_diffusion;

    // Configures the settings used to simulate color blindness, if desired.
    // If NULL, this feature is disabled.
    const struct pl_cone_params *cone_params;

    // Configures output blending. When rendering to the final target, the
    // framebuffer contents will be blended using this blend mode. Requires
    // that the target format has PL_FMT_CAP_BLENDABLE. NULL disables blending.
    const struct pl_blend_params *blend_params;

    // Configures the settings used to deinterlace frames (see
    // `pl_frame.field`), if required.. If NULL, deinterlacing is "disabled",
    // meaning interlaced frames are rendered as weaved frames instead.
    //
    // Note: As a consequence of how `pl_frame` represents individual fields,
    // and especially when using the `pl_queue`, this will still result in
    // frames being redundantly rendered twice. As such, it's highly
    // recommended to, instead, fully disable deinterlacing by not marking
    // source frames as interlaced in the first place.
    const struct pl_deinterlace_params *deinterlace_params;

    // If set, applies an extra distortion matrix to the image, after
    // scaling and before presenting it to the screen. Can be used for e.g.
    // fractional rotation.
    //
    // Note: The distortion canvas will be set to the size of `target->crop`,
    // so this cannot effectively draw outside the specified target area,
    // nor change the aspect ratio of the image.
    const struct pl_distort_params *distort_params;

    // List of custom user shaders / hooks.
    // See <libplacebo/shaders/custom.h> for more information.
    const struct pl_hook * const *hooks;
    int num_hooks;

    // Color mapping LUT. If present, this will be applied as part of the
    // image being rendered, in normalized RGB space.
    //
    // Note: In this context, PL_LUT_NATIVE means "gamma light" and
    // PL_LUT_NORMALIZED means "linear light". For HDR signals, normalized LUTs
    // are scaled so 1.0 corresponds to the `pl_color_transfer_nominal_peak`.
    //
    // Note: A PL_LUT_CONVERSION fully replaces the color adaptation from
    // `image` to `target`, including any tone-mapping (if necessary) and ICC
    // profiles. It has the same representation as PL_LUT_NATIVE, so in this
    // case the input and output are (respectively) non-linear light RGB.
    const struct pl_custom_lut *lut;
    enum pl_lut_type lut_type;

    // Controls how the image background is drawn for transparent images.
    enum pl_clear_mode background;

    // Controls how the remaining empty space in the target is filled up, when
    // the image does not span the entire framebuffer.
    enum pl_clear_mode border;

    // The color to use for PL_CLEAR_COLOR.
    //
    // Note: Despite the name, this also affects `border = PL_CLEAR_COLOR`.
    float background_color[3];
    float background_transparency; // 0.0 for opaque, 1.0 for fully transparent

    // The color and size to use for PL_CLEAR_TILES
    float tile_colors[2][3];
    int tile_size;

    // The blur radius (in pixels) to use for PL_CLEAR_BLUR
    float blur_radius;

    // If set to a value above 0.0, the output will be rendered with rounded
    // corners, as if an alpha transparency mask had been applied. The value
    // indicates the relative fraction of the side length to round - a value
    // of 1.0 rounds the corners as much as possible.
    float corner_rounding;

    // --- Performance / quality trade-off options:
    // These should generally be left off where quality is desired, as they can
    // degrade the result quite noticeably; but may be useful for older or
    // slower hardware. Note that libplacebo will automatically disable
    // advanced features on hardware where they are unsupported, regardless of
    // these settings. So only enable them if you need a performance bump.

    // Disables anti-aliasing on downscaling. This will result in moiré
    // artifacts and nasty, jagged pixels when downscaling, except for some
    // very limited special cases (e.g. bilinear downsampling to exactly 0.5x).
    //
    // Significantly speeds up downscaling with high downscaling ratios.
    bool skip_anti_aliasing;

    // Normally, when the size of the `target` used with `pl_render_image_mix`
    // changes, or the render parameters are updated, the internal cache of
    // mixed frames must be discarded in order to re-render all required
    // frames. Setting this option to `true` will skip the cache invalidation
    // and instead re-use the existing frames (with bilinear scaling to the new
    // size if necessary), which comes at a quality loss shortly after a
    // resize, but should make it much more smooth.
    bool preserve_mixing_cache;

    // --- Performance tuning / debugging options
    // These may affect performance or may make debugging problems easier,
    // but shouldn't have any effect on the quality.

    // Normally, `pl_render_image_mix` will also push single frames through the
    // mixer cache, in order to speed up re-draws. Enabling this option
    // disables that logic, causing single frames to bypass the cache. (Though
    // it will still read from, if they happen to already be cached)
    bool skip_caching_single_frame;

    // Disables linearization / sigmoidization before scaling. This might be
    // useful when tracking down unexpected image artifacts or excessing
    // ringing, but it shouldn't normally be necessary.
    bool disable_linear_scaling;

    // Forces the use of the "general" scaling algorithms even when using the
    // special-cased built-in presets like `pl_filter_bicubic`. Basically, this
    // disables the more efficient implementations in favor of the slower,
    // general-purpose ones.
    bool disable_builtin_scalers;

    // Forces correction of subpixel offsets (using the configured `upscaler`).
    bool correct_subpixel_offsets;

    // Forces the use of dithering, even when rendering to 16-bit FBOs. This is
    // generally pretty pointless because most 16-bit FBOs have high enough
    // depth that rounding errors are below the human perception threshold,
    // but this can be used to test the dither code.
    bool force_dither;

    // Disables the gamma-correct dithering logic which normally applies when
    // dithering to low bit depths. No real use, outside of testing.
    bool disable_dither_gamma_correction;

    // Completely overrides the use of FBOs, as if there were no renderable
    // texture format available. This disables most features.
    bool disable_fbos;

    // Use only low-bit-depth FBOs (8 bits). Note that this also implies
    // disabling linear scaling and sigmoidization.
    bool force_low_bit_depth_fbos;

    // If this is true, all shaders will be generated as "dynamic" shaders,
    // with any compile-time constants being replaced by runtime-adjustable
    // values. This is generally a performance loss, but has the advantage of
    // being able to freely change parameters without triggering shader
    // recompilations.
    //
    // It's a good idea to enable while presenting configurable settings to the
    // user, but it should be set to false once those values are "dialed in".
    bool dynamic_constants;

    // This callback is invoked for every pass successfully executed in the
    // process of rendering a frame. Optional.
    //
    // Note: `info` is only valid until this function returns.
    void (*info_callback)(void *priv, const struct pl_render_info *info);
    void *info_priv;

    // --- Deprecated/removed fields
    PL_DEPRECATED_IN(v6.254) bool allow_delayed_peak_detect; // moved to pl_peak_detect_params
    PL_DEPRECATED_IN(v6.327) const struct pl_icc_params *icc_params; // use pl_frame.icc
    PL_DEPRECATED_IN(v6.328) bool ignore_icc_profiles; // non-functional, just set pl_frame.icc to NULL
    PL_DEPRECATED_IN(v6.335) int lut_entries; // hard-coded as 256
    PL_DEPRECATED_IN(v6.335) float polar_cutoff; // hard-coded as 1e-3
    PL_DEPRECATED_IN(v7.346) bool skip_target_clearing; // `border = PL_CLEAR_SKIP`
    PL_DEPRECATED_IN(v7.346) bool blend_against_tiles; // `background = PL_CLEAR_TILES`
};

// Bare minimum parameters, with no features enabled. This is the fastest
// possible configuration, and should therefore be fine on any system.
#define PL_RENDER_DEFAULTS                              \
    .color_map_params   = &pl_color_map_default_params, \
    .color_adjustment   = &pl_color_adjustment_neutral, \
    .tile_colors        = {{0.93, 0.93, 0.93},          \
                           {0.87, 0.87, 0.87}},         \
    .tile_size          = 32,                           \
    .blur_radius        = 16.0,

#define pl_render_params(...) (&(struct pl_render_params) { PL_RENDER_DEFAULTS __VA_ARGS__ })
PL_API extern const struct pl_render_params pl_render_fast_params;

// This contains the default/recommended options for reasonable image quality,
// while also not being too terribly slow. All of the *_params structs are
// defaulted to the corresponding *_default_params, except for deband_params,
// which is disabled by default.
//
// This should be fine on most integrated GPUs, but if it's too slow,
// consider using `pl_render_fast_params` instead.
PL_API extern const struct pl_render_params pl_render_default_params;

// This contains a higher quality preset for better image quality at the cost
// of quite a bit of performance. In addition to the settings implied by
// `pl_render_default_params`, it enables debanding, sets the upscaler to
// `pl_filter_ewa_lanczossharp`, and uses pl_*_high_quality_params structs where
// available. This should only really be used with a discrete GPU and where
// maximum image quality is desired.
PL_API extern const struct pl_render_params pl_render_high_quality_params;

#define PL_MAX_PLANES 4

// High level description of a single slice of an image. This basically
// represents a single 2D plane, with any number of components
struct pl_plane {
    // The texture underlying this plane. The texture must be 2D, and must
    // have specific parameters set depending on what the plane is being used
    // for (see `pl_render_image`).
    pl_tex texture;

    // The preferred behaviour when sampling outside of this texture. Optional,
    // since the default (PL_TEX_ADDRESS_CLAMP) is very reasonable.
    enum pl_tex_address_mode address_mode;

    // Controls whether or not the `texture` will be considered flipped
    // vertically with respect to the overall image dimensions. It's generally
    // preferable to flip planes using this setting instead of the crop in
    // cases where the flipping is the result of e.g. negative plane strides or
    // flipped framebuffers (OpenGL).
    //
    // Note that any planar padding (due to e.g. size mismatch or misalignment
    // of subsampled planes) is always at the physical end of the texture
    // (highest y coordinate) - even if this bool is true. However, any
    // subsampling shift (`shift_y`) is applied with respect to the flipped
    // direction. This ensures the correct interpretation when e.g. vertically
    // flipping 4:2:0 sources by flipping all planes.
    bool flipped;

    // Describes the number and interpretation of the components in this plane.
    // This defines the mapping from component index to the canonical component
    // order (RGBA, YCbCrA or XYZA). It's worth pointing out that this is
    // completely separate from `texture->format.sample_order`. The latter is
    // essentially irrelevant/transparent for the API user, since it just
    // determines which order the texture data shows up as inside the GLSL
    // shader; whereas this field controls the actual meaning of the component.
    //
    // Example; if the user has a plane with just {Y} and a plane with just
    // {Cb Cr}, and a GPU that only supports bgra formats, you would still
    // specify the component mapping as {0} and {1 2} respectively, even though
    // the GPU is sampling the data in the order BGRA. Use -1 for "ignored"
    // components.
    int components;           // number of relevant components
    int component_mapping[4]; // semantic index of each component

    // Controls the sample offset, relative to the "reference" dimensions. For
    // an example of what to set here, see `pl_chroma_location_offset`. Note
    // that this is given in unit of reference pixels. For a graphical example,
    // imagine you have a 2x2 image with a 1x1 (subsampled) plane. Without any
    // shift (0.0), the situation looks like this:
    //
    // X-------X  X = reference pixel
    // |       |  P = plane pixel
    // |   P   |
    // |       |
    // X-------X
    //
    // For 4:2:0 subsampling, this corresponds to PL_CHROMA_CENTER. If the
    // shift_x was instead set to -0.5, the `P` pixel would be offset to the
    // left by half the separation between the reference (`X` pixels), resulting
    // in the following:
    //
    // X-------X  X = reference pixel
    // |       |  P = plane pixel
    // P       |
    // |       |
    // X-------X
    //
    // For 4:2:0 subsampling, this corresponds to PL_CHROMA_LEFT.
    //
    // Note: It's recommended to fill this using `pl_chroma_location_offset` on
    // the chroma planes.
    float shift_x, shift_y;
};

enum pl_overlay_mode {
    PL_OVERLAY_NORMAL = 0, // treat the texture as a normal, full-color texture
    PL_OVERLAY_MONOCHROME, // treat the texture as a single-component alpha map
    PL_OVERLAY_MODE_COUNT,
};

enum pl_overlay_coords {
    PL_OVERLAY_COORDS_AUTO = 0,  // equal to SRC/DST_FRAME, respectively
    PL_OVERLAY_COORDS_SRC_FRAME, // relative to the raw src frame
    PL_OVERLAY_COORDS_SRC_CROP,  // relative to the src frame crop
    PL_OVERLAY_COORDS_DST_FRAME, // relative to the raw dst frame
    PL_OVERLAY_COORDS_DST_CROP,  // relative to the dst frame crop
    PL_OVERLAY_COORDS_COUNT,

    // Note on rotations: If there is an end-to-end rotation between `src` and
    // `dst`, then any overlays relative to SRC_FRAME or SRC_CROP will be
    // rotated alongside the image, while overlays relative to DST_FRAME or
    // DST_CROP will not.
};

struct pl_overlay_part {
    pl_rect2df src; // source coordinate with respect to `pl_overlay.tex`
    pl_rect2df dst; // target coordinates with respect to `pl_overlay.coords`

    // If `mode` is PL_OVERLAY_MONOCHROME, then this specifies the color of
    // this overlay part. The color is multiplied into the sampled texture's
    // first channel.
    float color[4];
};

// A struct representing an image overlay (e.g. for subtitles or on-screen
// status messages, controls, ...)
struct pl_overlay {
    // The texture containing the backing data for overlay parts. Must have
    // `params.sampleable` set.
    pl_tex tex;

    // This controls the coloring mode of this overlay.
    enum pl_overlay_mode mode;

    // Controls which coordinates this overlay is addressed relative to.
    enum pl_overlay_coords coords;

    // This controls the colorspace information for this overlay. The contents
    // of the texture / the value of `color` are interpreted according to this.
    struct pl_color_repr repr;
    struct pl_color_space color;

    // The number of parts for this overlay.
    const struct pl_overlay_part *parts;
    int num_parts;
};

// High-level description of a complete frame, including metadata and planes
struct pl_frame {
    // Each frame is split up into some number of planes, each of which may
    // carry several components and be of any size / offset.
    int num_planes;
    struct pl_plane planes[PL_MAX_PLANES];

    // Optional enhancement layer for layered video formats. When set, points
    // to a fully-described pl_frame whose planes are composed onto this
    // (base layer) frame. Composition math is driven by metadata on the base
    // frame. Currently used only for Dolby Vision Profile 7 FEL, gated by
    // `repr.dovi->nlq_active`; ignored otherwise.
    const struct pl_frame *enhancement_layer;

    // For interlaced frames. If set, this `pl_frame` corresponds to a single
    // field of the underlying source textures. `first_field` indicates which
    // of these fields is ordered first in time. `prev` and `next` should point
    // to the previous/next frames in the file, or NULL if there are none.
    //
    // Note: Setting these fields on the render target has no meaning and will
    // be ignored.
    enum pl_field field;
    enum pl_field first_field;
    const struct pl_frame *prev, *next;

    // If set, will be called immediately before GPU access to this frame. This
    // function *may* be used to, for example, perform synchronization with
    // external APIs (e.g. `pl_vulkan_hold/release`). If your mapping requires
    // a memcpy of some sort (e.g. pl_tex_transfer), users *should* instead do
    // the memcpy up-front and avoid the use of these callbacks - because they
    // might be called multiple times on the same frame.
    //
    // This function *may* arbitrarily mutate the `pl_frame`, but it *should*
    // ideally only update `planes` - in particular, color metadata and so
    // forth should be provided up-front as best as possible. Note that changes
    // here will not be reflected back to the structs provided in the original
    // `pl_render_*` call (e.g. via `pl_frame_mix`).
    //
    // Note: Unless dealing with interlaced frames, only one frame will ever be
    // acquired at a time per `pl_render_*` call. So users *can* safely use
    // this with, for example, hwdec mappers that can only map a single frame
    // at a time. When using this with, for example, `pl_render_image_mix`,
    // each frame to be blended is acquired and release in succession, before
    // moving on to the next frame. For interlaced frames, the previous and
    // next frames must also be acquired simultaneously.
    bool (*acquire)(pl_gpu gpu, struct pl_frame *frame);

    // If set, will be called after a plane is done being used by the GPU,
    // *including* after any errors (e.g. `acquire` returning false).
    void (*release)(pl_gpu gpu, struct pl_frame *frame);

    // Color representation / encoding / semantics of this frame.
    struct pl_color_repr repr;
    struct pl_color_space color;

    // Optional ICC profile associated with this frame.
    pl_icc_object icc;

    // Alternative to `icc`, this can be used in cases where allocating and
    // tracking an pl_icc_object externally may be inconvenient. The resulting
    // profile will be managed internally by the pl_renderer.
    struct pl_icc_profile profile;

    // Optional LUT associated with this frame.
    const struct pl_custom_lut *lut;
    enum pl_lut_type lut_type;

    // The logical crop / rectangle containing the valid information, relative
    // to the reference plane's dimensions (e.g. luma). Pixels outside of this
    // rectangle will ostensibly be ignored, but note that this is not a hard
    // guarantee. In particular, scaler filters may end up sampling outside of
    // this crop. This rect may be flipped, and may be partially or wholly
    // outside the bounds of the underlying textures. (Optional)
    //
    // Note that `pl_render_image` will map the input crop directly to the
    // output crop, stretching and scaling as needed. If you wish to preserve
    // the aspect ratio, use a dedicated function like pl_rect2df_aspect_copy.
    pl_rect2df crop;

    // Logical rotation of the image, with respect to the underlying planes.
    // For example, if this is PL_ROTATION_90, then the image will be rotated
    // to the right by 90° when mapping to `crop`. The actual position on-screen
    // is unaffected, so users should ensure that the (rotated) aspect ratio
    // matches the source. (Or use a helper like `pl_rect2df_aspect_set_rot`)
    //
    // Note: For `target` frames, this corresponds to a rotation of the
    // display, for `image` frames, this corresponds to a rotation of the
    // camera.
    //
    // So, as an example, target->rotation = PL_ROTATE_90 means the end user
    // has rotated the display to the right by 90° (meaning rendering will be
    // rotated 90° to the *left* to compensate), and image->rotation =
    // PL_ROTATE_90 means the video provider has rotated the camera to the
    // right by 90° (so rendering will be rotated 90° to the *right* to
    // compensate).
    pl_rotation rotation;

    // The pixel aspect ratio of this frame. This is informational only, since
    // the effective DAR is already given by the relative ratio between the
    // frame crop and the target crop. If left unset, defaults to 1.0.
    //
    // Note that this is relative to the raw (encoded) pixels, i.e. before
    // application of any `rotation`.
    float pixel_aspect_ratio;

    // A list of additional overlays associated with this frame. Note that will
    // be rendered directly onto intermediate/cache frames, so changing any of
    // these overlays may require flushing the renderer cache.
    const struct pl_overlay *overlays;
    int num_overlays;

    // Note on subsampling and plane correspondence: All planes belonging to
    // the same frame will only be stretched by an integer multiple (or inverse
    // thereof) in order to match the reference dimensions of this image. For
    // example, suppose you have an 8x4 image. A valid plane scaling would be
    // 4x2 -> 8x4 or 4x4 -> 4x4, but not 6x4 -> 8x4. So if a 6x4 plane is
    // given, then it would be treated like a cropped 8x4 plane (since 1.0 is
    // the closest scaling ratio to the actual ratio of 1.3).
    //
    // For an explanation of why this makes sense, consider the relatively
    // common example of a subsampled, oddly sized (e.g. jpeg) image. In such
    // cases, for example a 35x23 image, the 4:2:0 subsampled chroma plane
    // would have to end up as 17.5x11.5, which gets rounded up to 18x12 by
    // implementations. So in this example, the 18x12 chroma plane would get
    // treated by libplacebo as an oversized chroma plane - i.e. the plane
    // would get sampled as if it was 17.5 pixels wide and 11.5 pixels large.

    // Associated film grain data (see <libplacebo/shaders/film_grain.h>).
    //
    // Note: This is ignored for the `target` of `pl_render_image`, since
    // un-applying grain makes little sense.
    struct pl_film_grain_data film_grain;

    // Ignored by libplacebo. May be useful for users.
    void *user_data;
};

// Helper function to infer the chroma location offset for each plane in a
// frame. This is equivalent to calling `pl_chroma_location_offset` on all
// subsampled planes' shift_x/shift_y variables.
PL_API void pl_frame_set_chroma_location(struct pl_frame *frame,
                                         enum pl_chroma_location chroma_loc);

// Fills in a `pl_frame` based on a swapchain frame's FBO and metadata.
PL_API void pl_frame_from_swapchain(struct pl_frame *out_frame,
                                    const struct pl_swapchain_frame *frame);

// Helper function to determine if a frame is logically cropped or not. In
// particular, this is useful in determining whether or not an output frame
// needs to be cleared before rendering or not.
PL_API bool pl_frame_is_cropped(const struct pl_frame *frame);

// Helper function to reset a frame to a given RGB color. If the frame's
// color representation is something other than RGB, the clear color will
// be adjusted accordingly. `clear_color` should be non-premultiplied.
PL_API void pl_frame_clear_rgba(pl_gpu gpu, const struct pl_frame *frame,
                                const float clear_color[4]);

// Like `pl_frame_clear_rgba` but without an alpha channel.
static inline void pl_frame_clear(pl_gpu gpu, const struct pl_frame *frame,
                                  const float clear_color[3])
{
    const float clear_color_rgba[4] = { clear_color[0], clear_color[1], clear_color[2], 1.0 };
    pl_frame_clear_rgba(gpu, frame, clear_color_rgba);
}

// Helper function to clear a frame to a fully tiled background.
PL_API void pl_frame_clear_tiles(pl_gpu gpu, const struct pl_frame *frame,
                                 const float tile_colors[2][3], int tile_size);

// Helper functions to return the fixed/inferred pl_frame parameters used
// for rendering internally. Mutates `image` and `target` in-place to hold
// the modified values, which are what will actually be used for rendering.
//
// This currently includes:
// - Defaulting all missing pl_color_space/repr parameters
// - Coalescing all rotation to the target
// - Rounding and clamping the target crop to pixel boundaries and adjusting the
//   image crop correspondingly
//
// Note: This is idempotent and does not generally alter the effects of a
// subsequent `pl_render_image` on the same pl_frame pair. (But see the
// following warning)
//
// Warning: This does *not* call pl_frame.acquire/release, and so the returned
// metadata *may* be incorrect if the acquire callback mutates the pl_frame in
// nontrivial ways, in particular the crop and color space fields.
PL_API void pl_frames_infer(pl_renderer rr, struct pl_frame *image,
                            struct pl_frame *target);


// Render a single image to a target using the given parameters. This is
// fully dynamic, i.e. the params can change at any time. libplacebo will
// internally detect and flush whatever caches are invalidated as a result of
// changing colorspace, size etc.
//
// Required plane capabilities:
// - Planes in `image` must be `sampleable`
// - Planes in `target` must be `renderable`
//
// Recommended plane capabilities: (Optional, but good for performance)
// - Planes in `image` should have `sample_mode` PL_TEX_SAMPLE_LINEAR
// - Planes in `target` should be `storable`
// - Planes in `target` should have `blit_dst`
//
// Note on lifetime: Once this call returns, the passed structures may be
// freely overwritten or discarded by the caller, even the referenced
// `pl_tex` objects may be freely reused.
//
// Note: `image` may be NULL, in which case `target.overlays` will still be
// rendered, but nothing else.
PL_API bool pl_render_image(pl_renderer rr, const struct pl_frame *image,
                            const struct pl_frame *target,
                            const struct pl_render_params *params);

// Flushes the internal state of this renderer. This is normally not needed,
// even if the image parameters, colorspace or target configuration change,
// since libplacebo will internally detect such circumstances and recreate
// outdated resources automatically. Doing this explicitly *may* be useful to
// purge some state related to things like HDR peak detection or frame mixing,
// so calling it is a good idea if the content source is expected to change
// dramatically (e.g. when switching to a different file).
PL_API void pl_renderer_flush_cache(pl_renderer rr);

// Mirrors `pl_get_detected_hdr_metadata`, giving you the current internal peak
// detection HDR metadata (when peak detection is active). Returns false if no
// information is available (e.g. not HDR source, peak detection disabled).
PL_API bool pl_renderer_get_hdr_metadata(pl_renderer rr,
                                         struct pl_hdr_metadata *metadata);

// Represents a mixture of input frames, distributed temporally.
//
// NOTE: Frames must be sorted by timestamp, i.e. `timestamps` must be
// monotonically increasing.
struct pl_frame_mix {
    // The number of frames in this mixture. The number of frames should be
    // sufficient to meet the needs of the configured frame mixer. See the
    // section below for more information.
    //
    // If the number of frames is 0, this call will be equivalent to
    // `pl_render_image` with `image == NULL`.
    int num_frames;

    // A list of the frames themselves. The frames can have different
    // colorspaces, configurations of planes, or even sizes.
    //
    // Note: This is a list of pointers, to avoid users having to copy
    // around `pl_frame` structs when re-organizing this array.
    const struct pl_frame **frames;

    // A list of unique signatures, one for each frame. These are used to
    // identify frames across calls to this function, so it's crucial that they
    // be both unique per-frame but also stable across invocations of
    // `pl_render_frame_mix`.
    const uint64_t *signatures;

    // A list of relative timestamps for each frame. These are relative to the
    // time of the vsync being drawn, i.e. this function will render the frame
    // that will be made visible at timestamp 0.0. The values are expected to
    // be normalized such that a separation of 1.0 corresponds to roughly one
    // nominal source frame duration. So a constant framerate video file will
    // always have timestamps like e.g. {-2.3, -1.3, -0.3, 0.7, 1.7, 2.7},
    // using an example radius of 3.
    //
    // In cases where the framerate is variable (e.g. VFR video), the choice of
    // what to scale to use can be difficult to answer. A typical choice would
    // be either to use the canonical (container-tagged) framerate, or the
    // highest momentary framerate, as a reference. If all else fails, you
    // could also use the display's framerate.
    //
    // Note: This function assumes zero-order-hold semantics, i.e. the frame at
    // timestamp 0.7 is intended to remain visible until timestamp 1.7, when
    // the next frame replaces it.
    const float *timestamps;

    // The duration for which the vsync being drawn will be held, using the
    // same scale as `timestamps`. If the display has an unknown or variable
    // frame-rate (e.g. Adaptive Sync), then you're probably better off not
    // using this function and instead just painting the frames directly using
    // `pl_render_frame` at the correct PTS.
    //
    // As an example, if `vsync_duration` is 0.4, then it's assumed that the
    // vsync being painted is visible for the period [0.0, 0.4].
    float vsync_duration;

    // Explanation of the frame mixing radius: The algorithm chosen in
    // `pl_render_params.frame_mixer` has a canonical radius equal to
    // `pl_filter_config.kernel->radius`. This means that the frame mixing
    // algorithm will (only) need to consult all of the frames that have a
    // distance within the interval [-radius, radius]. As such, the user should
    // include all such frames in `frames`, but may prune or omit frames that
    // lie outside it.
    //
    // The built-in frame mixing (`pl_render_params.frame_mixer == NULL`) has
    // no concept of radius, it just always needs access to the "current" and
    // "next" frames.
};

// Helper function to calculate the base frame mixing radius.
//
// Note: When the source FPS exceeds the display FPS, this radius must be
// increased by the corresponding ratio.
static inline float pl_frame_mix_radius(const struct pl_render_params *params)
{
    // For backwards compatibility, allow !frame_mixer->kernel
    if (!params->frame_mixer || !params->frame_mixer->kernel)
        return 0.0;

    return params->frame_mixer->kernel->radius;
}

// Find closest frame to current PTS by zero-order hold semantics, or NULL.
PL_API const struct pl_frame *pl_frame_mix_current(const struct pl_frame_mix *mix);

// Find closest frame to current PTS by nearest neighbour semantics, or NULL.
PL_API const struct pl_frame *pl_frame_mix_nearest(const struct pl_frame_mix *mix);

// Render a mixture of images to the target using the given parameters. This
// functions much like a generalization of `pl_render_image`, for when the API
// user has more control over the frame queue / vsync loop, and can provide a
// few frames from the past and future + timestamp information.
//
// This allows libplacebo to perform rudimentary frame mixing / interpolation,
// in order to eliminate judder artifacts typically associated with
// source/display frame rate mismatch.
PL_API bool pl_render_image_mix(pl_renderer rr, const struct pl_frame_mix *images,
                                const struct pl_frame *target,
                                const struct pl_render_params *params);

// Analog of `pl_frame_infer` corresponding to `pl_render_image_mix`. This
// function will *not* mutate the frames contained in `mix`, and instead
// return an adjusted copy of the "reference" frame for that image mix in
// `out_refimage`, or {0} if the mix is empty.
PL_API void pl_frames_infer_mix(pl_renderer rr, const struct pl_frame_mix *mix,
                                struct pl_frame *target, struct pl_frame *out_ref);

// Backwards compatibility with old filters API, may be deprecated.
// Redundant with pl_filter_configs and masking `allowed` for
// PL_FILTER_SCALING and PL_FILTER_FRAME_MIXING respectively.

// A list of recommended frame mixer presets, terminated by {0}
PL_API extern const struct pl_filter_preset pl_frame_mixers[];
PL_API extern const int pl_num_frame_mixers; // excluding trailing {0}

// A list of recommended scaler presets, terminated by {0}. This is almost
// equivalent to `pl_filter_presets` with the exception of including extra
// built-in filters that don't map to the `pl_filter` architecture.
PL_API extern const struct pl_filter_preset pl_scale_filters[];
PL_API extern const int pl_num_scale_filters; // excluding trailing {0}

// Deprecated in favor of `pl_cache_save/pl_cache_load` on the `pl_cache`
// associated with the `pl_gpu` this renderer is using.
PL_DEPRECATED_IN(v6.323) PL_API size_t pl_renderer_save(pl_renderer rr, uint8_t *out_cache);
PL_DEPRECATED_IN(v6.323) PL_API void pl_renderer_load(pl_renderer rr, const uint8_t *cache);

PL_API_END

#endif // LIBPLACEBO_RENDERER_H_
