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

#ifndef LIBPLACEBO_FILTER_KERNELS_H_
#define LIBPLACEBO_FILTER_KERNELS_H_

#include <stdbool.h>
#include <libplacebo/log.h>

PL_API_BEGIN

#define PL_FILTER_MAX_PARAMS 2

// Invocation parameters for a given kernel
struct pl_filter_ctx {
    float radius;
    float params[PL_FILTER_MAX_PARAMS];
};

// Represents a single filter function, i.e. kernel or windowing function.
struct pl_filter_function {
    // The cosmetic name associated with this filter function.
    const char *name;

    // The radius of the filter function. For resizable filters, this gives
    // the radius needed to represent a single filter lobe (tap).
    float radius;

    // If true, the filter function is resizable (see pl_filter_config.radius)
    bool resizable;

    // If true, the filter function is tunable (see pl_filter_config.params)
    bool tunable[PL_FILTER_MAX_PARAMS];

    // If the relevant parameter is tunable, this contains the default values.
    float params[PL_FILTER_MAX_PARAMS];

    // The underlying filter function itself: Computes the weight as a function
    // of the offset. All filter functions must be normalized such that x=0 is
    // the center point, and in particular weight(0) = 1.0. The functions may
    // be undefined for values of x outside [0, radius].
    double (*weight)(const struct pl_filter_ctx *f, double x);

    // If true, this filter represents an opaque placeholder for a more
    // sophisticated filter function which does not fit into the pl_filter
    // framework. `weight()` will always return 0.0.
    bool opaque;
};

// Deprecated function, merely checks a->weight == b->weight
PL_DEPRECATED_IN(v6.303) PL_API bool
pl_filter_function_eq(const struct pl_filter_function *a,
                      const struct pl_filter_function *b);

// Box filter: Entirely 1.0 within the radius, entirely 0.0 outside of it.
// This is also sometimes called a Dirichlet window
PL_API extern const struct pl_filter_function pl_filter_function_box;

// Triangle filter: Linear transitions from 1.0 at x=0 to 0.0 at x=radius.
// This is also sometimes called a Bartlett window.
PL_API extern const struct pl_filter_function pl_filter_function_triangle;

// Cosine filter: Ordinary cosine function, single lobe.
PL_API extern const struct pl_filter_function pl_filter_function_cosine;

// Hann function: Cosine filter named after Julius von Hann. Also commonly
// mislabeled as a "Hanning" function, due to its similarly to the Hamming
// function.
PL_API extern const struct pl_filter_function pl_filter_function_hann;

// Hamming function: Cosine filter named after Richard Hamming.
PL_API extern const struct pl_filter_function pl_filter_function_hamming;

// Welch filter: Polynomial function consisting of a single parabolic section.
PL_API extern const struct pl_filter_function pl_filter_function_welch;

// Kaiser filter: Approximation of the DPSS window using Bessel functions.
// Also sometimes called a Kaiser-Bessel window.
// Parameter [0]: Shape (alpha). Determines the trade-off between the main lobe
//                and the side lobes.
PL_API extern const struct pl_filter_function pl_filter_function_kaiser;

// Blackman filter: Cosine filter named after Ralph Beebe Blackman.
// Parameter [0]: Scale (alpha). Influences the shape. The defaults result in
//                zeros at the third and fourth sidelobes.
PL_API extern const struct pl_filter_function pl_filter_function_blackman;

// Bohman filter: 2nd order Cosine filter.
PL_API extern const struct pl_filter_function pl_filter_function_bohman;

// Gaussian function: Similar to the Gaussian distribution, this defines a
// bell curve function.
// Parameter [0]: Scale (t), increasing makes the result blurrier.
PL_API extern const struct pl_filter_function pl_filter_function_gaussian;

// Quadratic function: 2nd order approximation of the gaussian function. Also
// sometimes called a "quadric" window.
PL_API extern const struct pl_filter_function pl_filter_function_quadratic;

// Sinc function: Widely used for both kernels and windows, sinc(x) = sin(x)/x.
PL_API extern const struct pl_filter_function pl_filter_function_sinc;

// Jinc function: Similar to sinc, but extended to the 2D domain. Widely
// used as the kernel of polar (EWA) filters. Also sometimes called a Sombrero
// function.
PL_API extern const struct pl_filter_function pl_filter_function_jinc;

// Sphinx function: Similar to sinc and jinx, but extended to the 3D domain.
// The name is derived from "spherical" sinc. Can be used to filter 3D signals
// in theory.
PL_API extern const struct pl_filter_function pl_filter_function_sphinx;

// B/C-tunable Spline function: This is a family of commonly used spline
// functions with two tunable parameters. Does not need to be windowed.
// Parameter [0]: "B"
// Parameter [1]: "C"
// Some popular variants of this function are:
// B = 1.0,  C = 0.0:  "base" Cubic (blurry)
// B = 0.0,  C = 0.0:  Hermite filter (blocky)
// B = 0.0,  C = 0.5:  Catmull-Rom filter (sharp)
// B = 1/3,  C = 1/3:  Mitchell-Netravali filter (soft, doesn't ring)
// B ≈ 0.37, C ≈ 0.31: Robidoux filter (used by ImageMagick)
// B ≈ 0.26, C ≈ 0.37: RobidouxSharp filter (sharper variant of Robidoux)
PL_API extern const struct pl_filter_function pl_filter_function_cubic;
PL_API extern const struct pl_filter_function pl_filter_function_hermite;

// Deprecated aliases of pl_filter_function_cubic (see the table above)
PL_DEPRECATED_IN(v6.341) PL_API extern const struct pl_filter_function pl_filter_function_bicubic;
PL_DEPRECATED_IN(v6.341) PL_API extern const struct pl_filter_function pl_filter_function_bcspline;
PL_DEPRECATED_IN(v6.341) PL_API extern const struct pl_filter_function pl_filter_function_catmull_rom;
PL_DEPRECATED_IN(v6.341) PL_API extern const struct pl_filter_function pl_filter_function_mitchell;
PL_DEPRECATED_IN(v6.341) PL_API extern const struct pl_filter_function pl_filter_function_robidoux;
PL_DEPRECATED_IN(v6.341) PL_API extern const struct pl_filter_function pl_filter_function_robidouxsharp;

// Cubic splines with 2/3/4 taps. Referred to as "spline16", "spline36", and
// "spline64" mainly for historical reasons, based on the number of pixels in
// their window when using them as 2D orthogonal filters. Do not need to be
// windowed.
PL_API extern const struct pl_filter_function pl_filter_function_spline16;
PL_API extern const struct pl_filter_function pl_filter_function_spline36;
PL_API extern const struct pl_filter_function pl_filter_function_spline64;

// Special filter function for the built-in oversampling algorithm. This is an
// opaque filter with no meaningful representation. though it has one tunable
// parameter controlling the threshold at which to switch back to ordinary
// nearest neighbour sampling. (See `pl_shader_sample_oversample`)
PL_API extern const struct pl_filter_function pl_filter_function_oversample;

// A list of built-in filter functions, terminated by NULL
//
// Note: May contain extra aliases for the above functions.
PL_API extern const struct pl_filter_function * const pl_filter_functions[];
PL_API extern const int pl_num_filter_functions; // excluding trailing NULL

// Find the filter function with the given name, or NULL on failure.
PL_API const struct pl_filter_function *pl_find_filter_function(const char *name);

// Backwards compatibility with the older configuration API. Redundant with
// `pl_filter_function.name`. May be formally deprecated in the future.

struct pl_filter_function_preset {
    const char *name;
    const struct pl_filter_function *function;
};

// A list of built-in filter function presets, terminated by {0}
PL_API extern const struct pl_filter_function_preset pl_filter_function_presets[];
PL_API extern const int pl_num_filter_function_presets; // excluding trailing {0}

// Find the filter function preset with the given name, or NULL on failure.
PL_API const struct pl_filter_function_preset *pl_find_filter_function_preset(const char *name);

// Different usage domains for a filter
enum pl_filter_usage {
    PL_FILTER_UPSCALING    = (1 << 0),
    PL_FILTER_DOWNSCALING  = (1 << 1),
    PL_FILTER_FRAME_MIXING = (1 << 2),

    PL_FILTER_SCALING = PL_FILTER_UPSCALING | PL_FILTER_DOWNSCALING,
    PL_FILTER_ALL     = PL_FILTER_SCALING | PL_FILTER_FRAME_MIXING,
};

// Represents a tuned combination of filter functions, plus parameters
struct pl_filter_config {
    // The cosmetic name associated with this filter config. Optional for
    // user-provided configs, but always set by built-in configurations.
    const char *name;

    // Longer / friendly name. Always set for built-in configurations,
    // except for names which are merely aliases of other filters.
    const char *description;

    // Allowed and recommended usage domains (respectively)
    //
    // When it is desired to maintain a simpler user interface, it may be
    // recommended to include only scalers whose recommended usage domains
    // includes the relevant context in which it will be used.
    enum pl_filter_usage allowed;
    enum pl_filter_usage recommended;

    // The kernel function and (optionally) windowing function.
    const struct pl_filter_function *kernel;
    const struct pl_filter_function *window;

    // The radius. Ignored if !kernel->resizable. Optional, defaults to
    // kernel->radius if unset.
    float radius;

    // Parameters for the respective filter function. Ignored if not tunable.
    float params[PL_FILTER_MAX_PARAMS];
    float wparams[PL_FILTER_MAX_PARAMS];

    // Represents a clamping coefficient for negative weights. A value of 0.0
    // (the default) represents no clamping. A value of 1.0 represents full
    // clamping, i.e. all negative weights will be clamped to 0. Values in
    // between will be linearly scaled.
    float clamp;

    // Additional blur coefficient. This effectively stretches the kernel,
    // without changing the effective radius of the filter radius. Setting this
    // to a value of 0.0 is equivalent to disabling it. Values significantly
    // below 1.0 may seriously degrade the visual output, and should be used
    // with care.
    float blur;

    // Additional taper coefficient. This essentially flattens the function's
    // center. The values within [-taper, taper] will return 1.0, with the
    // actual function being squished into the remainder of [taper, radius].
    // Defaults to 0.0.
    float taper;

    // If true, this filter is intended to be used as a polar/2D filter (EWA)
    // instead of a separable/1D filter. Does not affect the actual sampling,
    // but provides information about how the results are to be interpreted.
    bool polar;

    // Antiringing strength. A value of 0.0 disables antiringing, and a value
    // of 1.0 enables full-strength antiringing. Defaults to 0.0 if
    // unspecified.
    //
    // Note: This is only included in `pl_filter_config` for convenience. Does
    // not affect the actual filter sampling, but provides information to the
    // downstream consumer of the `pl_filter`.
    float antiring;
};

PL_API bool pl_filter_config_eq(const struct pl_filter_config *a,
                                const struct pl_filter_config *b);

// Samples a given filter configuration at a given x coordinate, while
// respecting all parameters of the configuration.
PL_API double pl_filter_sample(const struct pl_filter_config *c, double x);

// A list of built-in filter configurations. Since they are just combinations
// of the above filter functions, they are not described in much further
// detail.
PL_API extern const struct pl_filter_config pl_filter_spline16;    // 2 taps
PL_API extern const struct pl_filter_config pl_filter_spline36;    // 3 taps
PL_API extern const struct pl_filter_config pl_filter_spline64;    // 4 taps
PL_API extern const struct pl_filter_config pl_filter_nearest;
PL_API extern const struct pl_filter_config pl_filter_box;
PL_API extern const struct pl_filter_config pl_filter_bilinear;
PL_API extern const struct pl_filter_config pl_filter_gaussian;
// Sinc family (all configured to 3 taps):
PL_API extern const struct pl_filter_config pl_filter_sinc;        // unwindowed
PL_API extern const struct pl_filter_config pl_filter_lanczos;     // sinc-sinc
PL_API extern const struct pl_filter_config pl_filter_ginseng;     // sinc-jinc
PL_API extern const struct pl_filter_config pl_filter_ewa_jinc;    // unwindowed
PL_API extern const struct pl_filter_config pl_filter_ewa_lanczos; // jinc-jinc
PL_API extern const struct pl_filter_config pl_filter_ewa_lanczossharp;
PL_API extern const struct pl_filter_config pl_filter_ewa_lanczos4sharpest;
PL_API extern const struct pl_filter_config pl_filter_ewa_ginseng; // jinc-sinc
PL_API extern const struct pl_filter_config pl_filter_ewa_hann;    // jinc-hann
// Spline family
PL_API extern const struct pl_filter_config pl_filter_bicubic;
PL_API extern const struct pl_filter_config pl_filter_hermite;
PL_API extern const struct pl_filter_config pl_filter_catmull_rom;
PL_API extern const struct pl_filter_config pl_filter_mitchell;
PL_API extern const struct pl_filter_config pl_filter_mitchell_clamp; // clamp = 1.0
PL_API extern const struct pl_filter_config pl_filter_robidoux;
PL_API extern const struct pl_filter_config pl_filter_robidouxsharp;
PL_API extern const struct pl_filter_config pl_filter_ewa_robidoux;
PL_API extern const struct pl_filter_config pl_filter_ewa_robidouxsharp;
// Special/opaque filters
PL_API extern const struct pl_filter_config pl_filter_oversample;

// Backwards compatibility
#define pl_filter_triangle          pl_filter_bilinear
#define pl_oversample_frame_mixer   pl_filter_oversample

// A list of built-in filter configs, terminated by NULL
PL_API extern const struct pl_filter_config * const pl_filter_configs[];
PL_API extern const int pl_num_filter_configs; // excluding trailing NULL

// Find the filter config with the given name, or NULL on failure.
// `usage` restricts the valid usage (based on `pl_filter_config.allowed`).
PL_API const struct pl_filter_config *
pl_find_filter_config(const char *name, enum pl_filter_usage usage);

// Backward compatibility with the previous filter configuration API. Redundant
// with pl_filter_config.name/description. May be deprecated in the future.
struct pl_filter_preset {
    const char *name;
    const struct pl_filter_config *filter;

    // Longer / friendly name, or NULL for aliases
    const char *description;
};

// A list of built-in filter presets, terminated by {0}
PL_API extern const struct pl_filter_preset pl_filter_presets[];
PL_API extern const int pl_num_filter_presets; // excluding trailing {0}

// Find the filter preset with the given name, or NULL on failure.
PL_API const struct pl_filter_preset *pl_find_filter_preset(const char *name);

// Parameters for filter generation.
struct pl_filter_params {
    // The particular filter configuration to be sampled. config.kernel must
    // be set to a valid pl_filter_function.
    struct pl_filter_config config;

    // The precision of the resulting LUT. A value of 64 should be fine for
    // most practical purposes, but higher or lower values may be justified
    // depending on the use case. This value must be set to something > 0.
    int lut_entries;

    // --- Polar filers only (config.polar)

    // As a micro-optimization, all samples below this cutoff value will be
    // ignored when updating the cutoff radius. Setting it to a value of 0.0
    // disables this optimization.
    float cutoff;

    // --- Separable filters only (!config.polar)

    // Indicates the maximum row size that is supported by the calling code, or
    // 0 for no limit.
    int max_row_size;

    // Indicates the row stride alignment. For some use cases (e.g. uploading
    // the weights as a texture), there are certain alignment requirements for
    // each row. The chosen row_size will always be a multiple of this value.
    // Specifying 0 indicates no alignment requirements.
    int row_stride_align;

    // --- Deprecated options
    PL_DEPRECATED_IN(v6.316) float filter_scale; // no effect, use `config.blur` instead
};

#define pl_filter_params(...) (&(struct pl_filter_params) { __VA_ARGS__ })

// Represents an initialized instance of a particular filter, with a
// precomputed LUT. The interpretation of the LUT depends on the type of the
// filter (polar or separable).
typedef const struct pl_filter_t {
    // Deep copy of the parameters, for convenience.
    struct pl_filter_params params;

    // Contains the true radius of the computed filter. This may be
    // smaller than the configured radius depending on the exact filter
    // parameters used. Mainly relevant for polar filters, since
    // it affects the value range of *weights.
    float radius;

    // Radius of the first zero crossing (main lobe size).
    float radius_zero;

    // The computed look-up table (LUT). For polar filters, this is interpreted
    // as a 1D array with dimensions [lut_entries] containing the raw filter
    // samples on the scale [0, radius]. For separable (non-polar) filters,
    // this is interpreted as a 2D array with dimensions
    // [lut_entries][row_stride]. The inner rows contain the `row_size` samples
    // to convolve with the corresponding input pixels. The outer coordinate is
    // used to very the fractional offset (phase). So for example, if the
    // sample position to reconstruct is directly aligned with the source
    // texels, you would use the values from weights[0]. If the sample position
    // to reconstruct is exactly half-way between two source texels (180° out
    // of phase), you would use the values from weights[lut_entries/2].
    const float *weights;

    // --- Separable filters only (!params.config.polar)

    // The number of source texels to convolve over for each row. This value
    // will never exceed the given `max_row_size`. If the filter ends up
    // cut off because of this, the bool `insufficient` will be set to true.
    int row_size;
    bool insufficient;

    // The separation (in *weights) between each row of the filter. Always
    // a multiple of params.row_stride_align.
    int row_stride;

    // --- Deprecated / removed fields
    PL_DEPRECATED_IN(v6.336) float radius_cutoff; // identical to `radius`
} *pl_filter;

// Generate (compute) a filter instance based on a given filter configuration.
// The resulting pl_filter must be freed with `pl_filter_free` when no longer
// needed. Returns NULL if filter generation fails due to invalid parameters
// (i.e. missing a required parameter).
PL_API pl_filter pl_filter_generate(pl_log log, const struct pl_filter_params *params);
PL_API void pl_filter_free(pl_filter *filter);

PL_API_END

#endif // LIBPLACEBO_FILTER_KERNELS_H_
